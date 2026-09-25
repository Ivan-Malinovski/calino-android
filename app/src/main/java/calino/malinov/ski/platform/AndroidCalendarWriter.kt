package calino.malinov.ski.platform

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.caldav.ICalTimezones
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
 * so event updates are partial and event/reminder changes use one provider
 * transaction.
 */
class AndroidCalendarWriter(private val context: Context) {
    private val resolver get() = context.contentResolver

    suspend fun create(
        input: NewEvent,
        calendar: AndroidCalendarSource.ImportableCalendar,
        writeOptIn: Set<String>,
    ): WriteResult<CalEvent> = guarded {
        validateCalendar(calendar, input.calendarId, writeOptIn)?.let { return@guarded it }
        unsupportedFields(input)?.let { return@guarded it }
        if (input.recurrence != null || input.recurrenceChanged) {
            return@guarded rejected("Creating a repeating event in a device calendar is not supported yet.")
        }

        val modeled = input.reminders.map { it.minutesBefore }.filter { it >= 0 }.distinct()
        val values = eventValues(input).apply {
            put(CalendarContract.Events.CALENDAR_ID, calendar.rowId)
            put(CalendarContract.Events.HAS_ALARM, if (modeled.isEmpty()) 0 else 1)
        }
        val operations = arrayListOf(
            ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValues(values)
                .build(),
        )
        modeled.forEach { minutes -> operations += reminderInsertBackReference(0, minutes, AlertMethod) }
        val results = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        val uri = results.firstOrNull()?.uri
            ?: error("The calendar provider did not return the new event identity.")
        require(results.drop(1).all { it.uri != null }) { "The calendar provider did not save every reminder." }
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
        unsupportedFields(input)?.let { return@guarded it }
        if (scope == RecurrenceEditScope.Future) {
            return@guarded rejected("Device calendars support changing this event or the entire series, not future events.")
        }
        if (input.recurrenceChanged || input.recurrence != null) {
            return@guarded rejected("Changing an existing device calendar recurrence rule is not supported yet.")
        }

        if (route.recurring && scope == RecurrenceEditScope.This && !route.existingException) {
            existingException(route)?.let {
                return@guarded rejected("That occurrence already changed in its owning app. Refresh the calendar and try again.")
            }
            insertExceptionAtomic(route, input, canceled = false)
            return@guarded WriteResult.Applied(
                input.toEvent(AndroidCalendarId.event(route.masterRowId, route.originalInstanceTime), route.color),
            )
        }

        val target = if (route.recurring && scope == RecurrenceEditScope.All) route.masterRowId else route.eventRowId
        val baseline = if (route.recurring && scope == RecurrenceEditScope.All) route.masterBaseline else route.baseline
        val values = if (route.recurring && scope == RecurrenceEditScope.All) {
            masterEventValues(input, route)
        } else {
            eventValues(
                input = input,
                preservedTimeZone = baseline.timeZone,
                originalBegin = route.instanceBegin,
                originalAllDay = baseline.allDay,
            )
        }
        updateEventAndRemindersAtomic(target, values, input.reminders.map { it.minutesBefore })
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
                val operation = ContentProviderOperation.newUpdate(eventUri(route.eventRowId))
                    .withValue(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                    .withExpectedCount(1)
                    .build()
                resolver.applyBatch(CalendarContract.AUTHORITY, arrayListOf(operation))
            }
            else -> {
                existingException(route)?.let {
                    return@guarded rejected("That occurrence already changed in its owning app. Refresh the calendar and try again.")
                }
                insertExceptionAtomic(route, input = null, canceled = true)
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
        if (!current.canWrite) return rejected("That device calendar is read-only or calendar write permission was revoked.")
        if (current.accountType.equals(CalinoAccounts.accountType(context), ignoreCase = true)) {
            return rejected("Calino cannot import or edit its own Android projection.")
        }
        return null
    }

    /** Reject fields CalendarContract write-back deliberately does not model. */
    private fun unsupportedFields(input: NewEvent): WriteResult.Rejected? {
        val changed = buildList {
            if (input.attendees.isNotEmpty()) add("attendees")
            if (input.categories.isNotEmpty()) add("categories")
            if (input.relatedTo.isNotEmpty()) add("relationships")
            if (input.travelTimeMinutes != null) add("travel time")
            if (input.url != null) add("URL")
        }
        return changed.takeIf { it.isNotEmpty() }?.let {
            rejected("Device calendars do not support changing ${it.joinToString()}. Clear those fields and try again.")
        }
    }

    /** Any detached, canceled, or deleted row occupies the recurrence slot. */
    private fun existingException(route: AndroidCalendarSource.ProviderEvent): Long? {
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.ORIGINAL_ID} = ? AND ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} = ? AND " +
                "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(
                route.masterRowId.toString(),
                route.originalInstanceTime.toString(),
                route.calendarRowId.toString(),
            ),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }
        return null
    }

    private fun insertExceptionAtomic(
        route: AndroidCalendarSource.ProviderEvent,
        input: NewEvent?,
        canceled: Boolean,
    ): Long {
        val sourceReminders = reminderRows(route.eventRowId)
        val modeled = input?.reminders.orEmpty().map { it.minutesBefore }.filter { it >= 0 }.distinct()
        val reminders = if (input == null) sourceReminders else {
            sourceReminders.filterNot(ReminderRow::modeledAlert) + modeled.map { ReminderRow(it, AlertMethod) }
        }
        val values = if (input != null) {
            eventValues(
                input = input,
                preservedTimeZone = route.masterBaseline.timeZone,
                originalBegin = route.instanceBegin,
                originalAllDay = route.originalAllDay,
            )
        } else {
            ContentValues().apply {
                put(CalendarContract.Events.TITLE, route.baseline.title)
                put(CalendarContract.Events.DTSTART, route.instanceBegin)
                route.instanceEnd?.let { put(CalendarContract.Events.DTEND, it) }
                put(CalendarContract.Events.ALL_DAY, if (route.originalAllDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, route.masterBaseline.timeZone ?: if (route.originalAllDay) "UTC" else ZoneId.systemDefault().id)
            }
        }.apply {
            put(CalendarContract.Events.CALENDAR_ID, route.calendarRowId)
            put(CalendarContract.Events.ORIGINAL_ID, route.masterRowId)
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, route.originalInstanceTime)
            put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (route.originalAllDay) 1 else 0)
            put(CalendarContract.Events.HAS_ALARM, if (reminders.isEmpty()) 0 else 1)
            if (canceled) put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        }

        val operations = arrayListOf(
            ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(values).build(),
        )
        reminders.forEach { row -> operations += reminderInsertBackReference(0, row.minutes, row.method) }
        val results = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        val uri = results.firstOrNull()?.uri ?: error("The calendar provider did not return the exception identity.")
        require(results.drop(1).all { it.uri != null }) { "The calendar provider did not save every exception reminder." }
        return ContentUris.parseId(uri)
    }

    private fun updateEventAndRemindersAtomic(eventId: Long, values: ContentValues, minutes: List<Int>) {
        val unsupported = reminderRows(eventId).filterNot(ReminderRow::modeledAlert)
        val modeled = minutes.filter { it >= 0 }.distinct()
        val operations = arrayListOf(
            ContentProviderOperation.newUpdate(eventUri(eventId))
                .withValues(values)
                .withExpectedCount(1)
                .build(),
            ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI)
                .withSelection(
                    "${CalendarContract.Reminders.EVENT_ID} = ? AND ${CalendarContract.Reminders.METHOD} = ? AND " +
                        "${CalendarContract.Reminders.MINUTES} >= 0",
                    arrayOf(eventId.toString(), AlertMethod.toString()),
                )
                .build(),
        )
        modeled.forEach { value -> operations += reminderInsert(eventId, value, AlertMethod) }
        operations += ContentProviderOperation.newUpdate(eventUri(eventId))
            .withValue(CalendarContract.Events.HAS_ALARM, if (unsupported.isNotEmpty() || modeled.isNotEmpty()) 1 else 0)
            .withExpectedCount(1)
            .build()
        val results = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        require(modeled.indices.all { results[2 + it].uri != null }) {
            "The calendar provider did not save every reminder."
        }
    }

    private data class ReminderRow(val minutes: Int, val method: Int) {
        fun modeledAlert(): Boolean = method == AlertMethod && minutes >= 0
    }

    private fun reminderRows(eventId: Long): List<ReminderRow> {
        val rows = mutableListOf<ReminderRow>()
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) rows += ReminderRow(cursor.getInt(0), cursor.getInt(1))
        } ?: error("The calendar provider did not return reminder state.")
        return rows
    }

    private fun reminderInsert(eventId: Long, minutes: Int, method: Int) =
        ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
            .withValue(CalendarContract.Reminders.EVENT_ID, eventId)
            .withValue(CalendarContract.Reminders.MINUTES, minutes)
            .withValue(CalendarContract.Reminders.METHOD, method)
            .build()

    private fun reminderInsertBackReference(eventOperation: Int, minutes: Int, method: Int) =
        ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
            .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOperation)
            .withValue(CalendarContract.Reminders.MINUTES, minutes)
            .withValue(CalendarContract.Reminders.METHOD, method)
            .build()

    /** Apply an occurrence's date/time delta to the master instead of replacing it with that occurrence's slot. */
    private fun masterEventValues(input: NewEvent, route: AndroidCalendarSource.ProviderEvent): ContentValues {
        val editedOccurrenceStart = startMillis(input)
        val masterStart = route.masterBaseline.start ?: editedOccurrenceStart
        val shiftedMasterStart = masterStart + (editedOccurrenceStart - route.instanceBegin)
        return eventValues(
            input = input,
            preservedTimeZone = route.masterBaseline.timeZone,
            originalBegin = route.instanceBegin,
            originalAllDay = route.masterBaseline.allDay,
            startOverride = shiftedMasterStart,
        ).apply {
            remove(CalendarContract.Events.DTEND)
            val duration = if (input.allDay) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(
                    input.date,
                    (input.endDate ?: input.date).plusDays(1),
                ).coerceAtLeast(1)
                "P${days}D"
            } else "PT${(input.durationMinutes ?: 0).coerceAtLeast(0)}M"
            put(CalendarContract.Events.DURATION, duration)
        }
    }

    private fun eventValues(
        input: NewEvent,
        preservedTimeZone: String? = null,
        originalBegin: Long? = null,
        originalAllDay: Boolean? = null,
        startOverride: Long? = null,
    ) = ContentValues().apply {
        put(CalendarContract.Events.TITLE, input.title)
        put(CalendarContract.Events.DESCRIPTION, input.notes)
        put(CalendarContract.Events.EVENT_LOCATION, input.location)
        put(CalendarContract.Events.ALL_DAY, if (input.allDay) 1 else 0)
        put(CalendarContract.Events.AVAILABILITY, if (input.availability == Availability.Free) {
            CalendarContract.Events.AVAILABILITY_FREE
        } else CalendarContract.Events.AVAILABILITY_BUSY)
        val inputStart = startMillis(input)
        val start = startOverride ?: inputStart
        put(CalendarContract.Events.DTSTART, start)
        put(CalendarContract.Events.DTEND, endMillis(input, start))
        val placementUnchanged = originalBegin != null && inputStart == originalBegin && input.allDay == originalAllDay
        put(
            CalendarContract.Events.EVENT_TIMEZONE,
            when {
                input.allDay -> if (placementUnchanged && !preservedTimeZone.isNullOrBlank()) preservedTimeZone else "UTC"
                // A zone the person picked in the editor wins over the row's.
                ICalTimezones.resolve(input.zoneId) != null -> ICalTimezones.resolve(input.zoneId)!!.id
                placementUnchanged && !preservedTimeZone.isNullOrBlank() -> preservedTimeZone
                else -> ZoneId.systemDefault().id
            },
        )
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

    private fun eventUri(rowId: Long): Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, rowId)

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

    private companion object {
        const val AlertMethod = CalendarContract.Reminders.METHOD_ALERT
    }
}
