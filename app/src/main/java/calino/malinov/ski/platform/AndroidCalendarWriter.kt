package calino.malinov.ski.platform

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.repository.WriteResult
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Writes events owned by another CalendarContract provider.
 *
 * All operations intentionally use ordinary provider URIs. Calino is not the
 * sync adapter for these rows and must leave provider-specific columns alone,
 * so event updates are partial and reminders are reconciled separately.
 */
class AndroidCalendarWriter(private val context: Context) {
    private val resolver get() = context.contentResolver

    suspend fun create(
        input: NewEvent,
        calendar: AndroidCalendarSource.ImportableCalendar,
        writeOptIn: Set<String>,
    ): WriteResult<CalEvent> = guarded {
        validateCalendar(calendar, input.calendarId, writeOptIn)?.let { return@guarded it }
        if (input.recurrence != null) {
            return@guarded rejected("Creating a repeating event in a device calendar is not supported yet.")
        }
        val uri = resolver.insert(
            CalendarContract.Events.CONTENT_URI,
            eventValues(input, calendar.rowId, includeCalendar = true),
        ) ?: return@guarded rejected("The calendar provider did not accept the new event.")
        val rowId = ContentUris.parseId(uri)
        replaceModeledReminders(rowId, input.reminders.map { it.minutesBefore })
        WriteResult.Applied(input.toEvent(AndroidCalendarId.event(rowId, startMillis(input)), calendar.color))
    }

    suspend fun update(
        route: AndroidCalendarSource.ProviderEvent,
        input: NewEvent,
        scope: RecurrenceEditScope,
        writeOptIn: Set<String>,
    ): WriteResult<CalEvent> = guarded {
        validateRoute(route, input.calendarId, scope, writeOptIn)?.let { return@guarded it }
        if (scope == RecurrenceEditScope.Future) {
            return@guarded rejected("Device calendars support changing this event or the entire series, not future events.")
        }
        if (route.recurring && input.recurrenceChanged) {
            return@guarded rejected("Changing an existing device calendar recurrence rule is not supported yet.")
        }

        val target = when {
            !route.recurring -> route.eventRowId
            scope == RecurrenceEditScope.All -> route.masterRowId
            route.existingException -> route.eventRowId
            else -> insertException(route, input, canceled = false)
                ?: return@guarded rejected("The calendar provider did not accept the occurrence exception.")
        }
        if (!(route.recurring && scope == RecurrenceEditScope.This && !route.existingException)) {
            val values = if (route.recurring && scope == RecurrenceEditScope.All) {
                masterEventValues(input, route)
            } else eventValues(input, route.calendarRowId)
            val changed = resolver.update(eventUri(target), values, null, null)
            if (changed != 1) return@guarded rejected("The event changed in its owning app. Refresh and try again.")
        }
        replaceModeledReminders(target, input.reminders.map { it.minutesBefore })
        WriteResult.Applied(input.toEvent(AndroidCalendarId.event(route.masterRowId, route.originalInstanceTime), route.color))
    }

    suspend fun delete(
        route: AndroidCalendarSource.ProviderEvent,
        scope: RecurrenceEditScope,
        writeOptIn: Set<String>,
    ): WriteResult<Unit> = guarded {
        validateRoute(route, route.calendarId, scope, writeOptIn)?.let { return@guarded it }
        if (scope == RecurrenceEditScope.Future) {
            return@guarded rejected("Device calendars support deleting this event or the entire series, not future events.")
        }
        when {
            !route.recurring || scope == RecurrenceEditScope.All -> {
                val target = if (route.recurring) route.masterRowId else route.eventRowId
                if (resolver.delete(eventUri(target), null, null) != 1) {
                    return@guarded rejected("The event changed in its owning app. Refresh and try again.")
                }
            }
            route.existingException -> {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                }
                if (resolver.update(eventUri(route.eventRowId), values, null, null) != 1) {
                    return@guarded rejected("The occurrence changed in its owning app. Refresh and try again.")
                }
            }
            else -> if (insertException(route, null, canceled = true) == null) {
                return@guarded rejected("The calendar provider did not accept the canceled occurrence.")
            }
        }
        WriteResult.Applied(Unit)
    }

    private fun validateRoute(
        route: AndroidCalendarSource.ProviderEvent,
        destinationCalendarId: String,
        scope: RecurrenceEditScope,
        writeOptIn: Set<String>,
    ): WriteResult.Rejected? {
        if (destinationCalendarId != route.calendarId) {
            return rejected("Events cannot be moved between device calendars or between device and CalDAV calendars.")
        }
        val calendar = AndroidCalendarSource.availableCalendars(context).firstOrNull { it.id == route.calendarId }
            ?: return rejected("That device calendar is no longer available.")
        validateCalendar(calendar, route.calendarId, writeOptIn)?.let { return it }
        if (calendar.rowId != route.calendarRowId || calendar.accountName != route.accountName ||
            calendar.accountType != route.accountType || calendar.ownerAccount != route.ownerAccount
        ) return rejected("That device calendar changed ownership. Turn editing off and on again before retrying.")
        val all = route.recurring && scope == RecurrenceEditScope.All
        val baseline = if (all) route.masterBaseline else route.baseline
        val checkedRow = if (all) route.masterRowId else route.eventRowId
        val current = AndroidCalendarSource.providerBaseline(context, checkedRow)
            ?: return rejected("That event was removed by its owning app. Refresh the calendar.")
        if (current != baseline) {
            return rejected("That event changed in its owning app. Refresh the calendar and try again.")
        }
        return null
    }

    private fun validateCalendar(
        calendar: AndroidCalendarSource.ImportableCalendar,
        destinationCalendarId: String,
        writeOptIn: Set<String>,
    ): WriteResult.Rejected? {
        if (destinationCalendarId != calendar.id || calendar.id !in writeOptIn) {
            return rejected("Editing is not enabled for that device calendar.")
        }
        val current = AndroidCalendarSource.availableCalendars(context).firstOrNull { it.id == calendar.id }
            ?: return rejected("That device calendar is no longer available.")
        if (current.rowId != calendar.rowId || current.accountName != calendar.accountName ||
            current.accountType != calendar.accountType || current.ownerAccount != calendar.ownerAccount
        ) return rejected("That device calendar changed ownership. Turn editing off and on again before retrying.")
        if (!current.canWrite) return rejected("That device calendar is read-only.")
        if (current.accountType.equals(CalinoAccounts.accountType(context), ignoreCase = true)) {
            return rejected("Calino cannot import or edit its own Android projection.")
        }
        return null
    }

    private fun insertException(
        route: AndroidCalendarSource.ProviderEvent,
        input: NewEvent?,
        canceled: Boolean,
    ): Long? {
        val values = if (input != null) eventValues(input, route.calendarRowId, includeCalendar = true) else {
            ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, route.calendarRowId)
                put(CalendarContract.Events.TITLE, route.baseline.title)
                put(CalendarContract.Events.DTSTART, route.instanceBegin)
                route.instanceEnd?.let { put(CalendarContract.Events.DTEND, it) }
                put(CalendarContract.Events.ALL_DAY, if (route.originalAllDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, if (route.originalAllDay) "UTC" else ZoneId.systemDefault().id)
            }
        }
        values.put(CalendarContract.Events.ORIGINAL_ID, route.masterRowId)
        values.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, route.originalInstanceTime)
        values.put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (route.originalAllDay) 1 else 0)
        if (canceled) values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        val inserted = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val exceptionId = ContentUris.parseId(inserted)
        cloneReminders(route.eventRowId, exceptionId)
        if (input != null) replaceModeledReminders(exceptionId, input.reminders.map { it.minutesBefore })
        return exceptionId
    }

    private fun cloneReminders(sourceEventId: Long, targetEventId: Long) {
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(sourceEventId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, targetEventId)
                    put(CalendarContract.Reminders.MINUTES, cursor.getInt(0))
                    put(CalendarContract.Reminders.METHOD, cursor.getInt(1))
                })
            }
        }
    }

    /** Only Calino's modeled nonnegative alert rows are replaced. */
    private fun replaceModeledReminders(eventId: Long, minutes: List<Int>) {
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ? AND ${CalendarContract.Reminders.METHOD} = ? AND " +
                "${CalendarContract.Reminders.MINUTES} >= 0",
            arrayOf(eventId.toString(), CalendarContract.Reminders.METHOD_ALERT.toString()),
        )
        minutes.filter { it >= 0 }.distinct().forEach { value ->
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, value)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            })
        }
        var hasAny = false
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders._ID),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { hasAny = it.moveToFirst() }
        resolver.update(eventUri(eventId), ContentValues().apply {
            put(CalendarContract.Events.HAS_ALARM, if (hasAny) 1 else 0)
        }, null, null)
    }

    /** Apply an occurrence's date/time delta to the master instead of replacing it with that occurrence's slot. */
    private fun masterEventValues(
        input: NewEvent,
        route: AndroidCalendarSource.ProviderEvent,
    ): ContentValues {
        val editedOccurrenceStart = startMillis(input)
        val masterStart = route.masterBaseline.start ?: editedOccurrenceStart
        val shiftedMasterStart = masterStart + (editedOccurrenceStart - route.instanceBegin)
        return eventValues(input, route.calendarRowId, startOverride = shiftedMasterStart).apply {
            remove(CalendarContract.Events.DTEND)
            val duration = if (input.allDay) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(
                    input.date,
                    (input.endDate ?: input.date).plusDays(1),
                ).coerceAtLeast(1)
                "P${days}D"
            } else {
                "PT${(input.durationMinutes ?: 0).coerceAtLeast(0)}M"
            }
            put(CalendarContract.Events.DURATION, duration)
        }
    }

    private fun eventValues(
        input: NewEvent,
        calendarRowId: Long,
        includeCalendar: Boolean = false,
        startOverride: Long? = null,
    ) = ContentValues().apply {
            if (includeCalendar) put(CalendarContract.Events.CALENDAR_ID, calendarRowId)
            put(CalendarContract.Events.TITLE, input.title)
            put(CalendarContract.Events.DESCRIPTION, input.notes)
            put(CalendarContract.Events.EVENT_LOCATION, input.location)
            put(CalendarContract.Events.ALL_DAY, if (input.allDay) 1 else 0)
            put(CalendarContract.Events.AVAILABILITY, if (input.availability == Availability.Free) {
                CalendarContract.Events.AVAILABILITY_FREE
            } else CalendarContract.Events.AVAILABILITY_BUSY)
            val start = startOverride ?: startMillis(input)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, endMillis(input, start))
            put(CalendarContract.Events.EVENT_TIMEZONE, if (input.allDay) "UTC" else ZoneId.systemDefault().id)
        }

    private fun startMillis(input: NewEvent): Long = if (input.allDay) {
        input.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } else {
        input.date.atTime(input.startTime ?: java.time.LocalTime.MIDNIGHT)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun endMillis(input: NewEvent, start: Long): Long = if (input.allDay) {
        (input.endDate ?: input.date).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } else start + (input.durationMinutes ?: 0).coerceAtLeast(0) * 60_000L

    private fun eventUri(rowId: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, rowId)

    private inline fun <T> guarded(block: () -> WriteResult<T>): WriteResult<T> = try {
        block()
    } catch (_: SecurityException) {
        rejected("Calendar write permission is required to change that device calendar.")
    } catch (error: Exception) {
        rejected(error.message?.takeIf { it.isNotBlank() } ?: "The calendar provider rejected that change.")
    }

    private fun rejected(reason: String) = WriteResult.Rejected(reason)

    private fun NewEvent.toEvent(id: String, eventColor: Long) = CalEvent(
        id = id,
        title = title,
        color = eventColor,
        start = if (allDay) null else date.atTime(startTime ?: java.time.LocalTime.MIDNIGHT),
        durationMinutes = durationMinutes,
        allDay = allDay,
        recurrence = null,
        location = location,
        notes = notes,
        attendees = emptyList(),
        calendarId = calendarId,
        date = if (allDay) date else null,
        endDate = endDate,
        availability = availability,
        reminders = reminders,
        uid = null,
        href = null,
        etag = null,
        recurrenceId = null,
        recurrenceDate = null,
        sequence = null,
        providerRecurring = false,
    )
}
