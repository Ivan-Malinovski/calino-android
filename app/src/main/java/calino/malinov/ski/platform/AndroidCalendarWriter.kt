package calino.malinov.ski.platform

import android.content.ContentProviderOperation
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
        unsupportedFields(input)?.let { return@guarded rejected(it) }
        if (input.recurrence != null) {
            return@guarded rejected("Creating a repeating event in a device calendar is not supported yet.")
        }
        val operations = arrayListOf(
            ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValues(eventValues(input, calendar.rowId, includeCalendar = true).apply {
                    put(CalendarContract.Events.HAS_ALARM, if (input.reminders.any { it.minutesBefore >= 0 }) 1 else 0)
                })
                .build(),
        )
        input.reminders.map { it.minutesBefore }.filter { it >= 0 }.distinct().forEach { minutes ->
            operations += reminderInsertBackReference(0, minutes, CalendarContract.Reminders.METHOD_ALERT)
        }
        val results = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        val uri = results.firstOrNull()?.uri
            ?: return@guarded rejected("The calendar provider did not accept the new event.")
        val rowId = ContentUris.parseId(uri)
        WriteResult.Applied(input.toEvent(AndroidCalendarId.event(rowId, startMillis(input)), calendar.color))
    }

    suspend fun update(
        route: AndroidCalendarSource.ProviderEvent,
        input: NewEvent,
        scope: RecurrenceEditScope,
        writeOptIn: Set<String>,
    ): WriteResult<CalEvent> = guarded {
        validateRoute(route, input.calendarId, scope, writeOptIn)?.let { return@guarded it }
        unsupportedFields(input)?.let { return@guarded rejected(it) }
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
            } else eventValues(input, route.calendarRowId, eventTimezone = route.baseline.eventTimezone)
            val results = applyEventAndModeledReminders(target, values, input.reminders.map { it.minutesBefore })
            if (results.firstOrNull()?.count != 1) {
                return@guarded rejected("The event changed in its owning app. Refresh and try again.")
            }
        }
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
        if (current != baseline || current.deleted) {
            return rejected("That event changed in its owning app. Refresh the calendar and try again.")
        }
        if (route.recurring && scope == RecurrenceEditScope.This && !route.existingException &&
            AndroidCalendarSource.hasException(context, route.masterRowId, route.originalInstanceTime)
        ) {
            return rejected("That occurrence changed in its owning app. Refresh the calendar and try again.")
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
                put(CalendarContract.Events.EVENT_TIMEZONE, if (route.originalAllDay) "UTC" else route.masterBaseline.eventTimezone ?: ZoneId.systemDefault().id)
            }
        }
        values.put(CalendarContract.Events.ORIGINAL_ID, route.masterRowId)
        values.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, route.originalInstanceTime)
        values.put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (route.originalAllDay) 1 else 0)
        if (canceled) values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)

        val sourceReminders = reminderRows(route.eventRowId)
        val reminders = if (input == null) {
            sourceReminders
        } else {
            sourceReminders.filterNot { (minutes, method) ->
                method == CalendarContract.Reminders.METHOD_ALERT && minutes >= 0
            } + input.reminders.map { it.minutesBefore }
                .filter { it >= 0 }
                .distinct()
                .map { it to CalendarContract.Reminders.METHOD_ALERT }
        }
        values.put(CalendarContract.Events.HAS_ALARM, if (reminders.isNotEmpty()) 1 else 0)
        val operations = arrayListOf(
            ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValues(values)
                .build(),
        )
        reminders.forEach { (minutes, method) ->
            operations += reminderInsertBackReference(0, minutes, method)
        }
        val inserted = resolver.applyBatch(CalendarContract.AUTHORITY, operations).firstOrNull()?.uri ?: return null
        return ContentUris.parseId(inserted)
    }

    /** Updates the event and its modeled reminders in one provider transaction. */
    private fun applyEventAndModeledReminders(
        eventId: Long,
        values: ContentValues,
        minutes: List<Int>,
    ): Array<android.content.ContentProviderResult> {
        val unsupportedRemain = reminderRows(eventId).any { (value, method) ->
            method != CalendarContract.Reminders.METHOD_ALERT || value < 0
        }
        val modeled = minutes.filter { it >= 0 }.distinct()
        values.put(CalendarContract.Events.HAS_ALARM, if (unsupportedRemain || modeled.isNotEmpty()) 1 else 0)
        val operations = arrayListOf(
            ContentProviderOperation.newUpdate(eventUri(eventId)).withValues(values).build(),
            ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI)
                .withSelection(
                    "${CalendarContract.Reminders.EVENT_ID} = ? AND ${CalendarContract.Reminders.METHOD} = ? AND " +
                        "${CalendarContract.Reminders.MINUTES} >= 0",
                    arrayOf(eventId.toString(), CalendarContract.Reminders.METHOD_ALERT.toString()),
                )
                .build(),
        )
        modeled.forEach { value ->
            operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                .withValue(CalendarContract.Reminders.EVENT_ID, eventId)
                .withValue(CalendarContract.Reminders.MINUTES, value)
                .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                .build()
        }
        return resolver.applyBatch(CalendarContract.AUTHORITY, operations)
    }

    private fun reminderRows(eventId: Long): List<Pair<Int, Int>> {
        val rows = mutableListOf<Pair<Int, Int>>()
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) rows += cursor.getInt(0) to cursor.getInt(1)
        }
        return rows
    }

    private fun reminderInsertBackReference(eventOperationIndex: Int, minutes: Int, method: Int) =
        ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
            .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOperationIndex)
            .withValue(CalendarContract.Reminders.MINUTES, minutes)
            .withValue(CalendarContract.Reminders.METHOD, method)
            .build()

    /** Apply an occurrence's date/time delta to the master instead of replacing it with that occurrence's slot. */
    private fun masterEventValues(
        input: NewEvent,
        route: AndroidCalendarSource.ProviderEvent,
    ): ContentValues {
        val editedOccurrenceStart = startMillis(input)
        val masterStart = route.masterBaseline.start ?: editedOccurrenceStart
        val shiftedMasterStart = masterStart + (editedOccurrenceStart - route.instanceBegin)
        return eventValues(
            input,
            route.calendarRowId,
            startOverride = shiftedMasterStart,
            eventTimezone = route.masterBaseline.eventTimezone,
        ).apply {
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
        eventTimezone: String? = null,
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
            put(CalendarContract.Events.EVENT_TIMEZONE, if (input.allDay) "UTC" else eventTimezone ?: ZoneId.systemDefault().id)
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

    private fun unsupportedFields(input: NewEvent): String? = when {
        input.attendees.isNotEmpty() -> "Attendee editing is not supported for device calendars."
        input.categories.isNotEmpty() -> "Category editing is not supported for device calendars."
        input.travelTimeMinutes != null -> "Travel-time editing is not supported for device calendars."
        input.relatedTo.isNotEmpty() -> "Related-record editing is not supported for device calendars."
        !input.url.isNullOrBlank() -> "URL editing is not supported for device calendars."
        else -> null
    }

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
