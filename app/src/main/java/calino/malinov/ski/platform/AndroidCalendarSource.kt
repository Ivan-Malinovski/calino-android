package calino.malinov.ski.platform

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.repository.CalinoCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads the calendars the device already holds -- Google, Exchange, whatever
 * else is installed -- so Calino can show them beside its own.
 *
 * The inverse of [CalendarProjection], and deliberately much smaller. There
 * are no ETags to honour, no conditional writes, no rebase and no queue: the
 * provider is local and always available, so a read is just a read. And
 * `Instances` returns occurrences already expanded, which is the same shape
 * `ICalMapper` produces, so no recurrence rule is ever parsed here.
 *
 * Nothing in this file writes. There is no `CALLER_IS_SYNCADAPTER` anywhere in
 * it, and that is the point -- these rows belong to other apps, and Calino is
 * a viewer of them. See the "Reading the device's calendars" section of
 * `docs/calendar-provider.md`.
 */
object AndroidCalendarSource {

    /** A calendar on the device that Calino could show, once asked to. */
    data class ImportableCalendar(
        /** Calino's id for it, already prefixed. */
        val id: String,
        val rowId: Long,
        /** The owning account, shown so a person knows whose calendar this is. */
        val accountName: String,
        val accountType: String,
        val name: String,
        val color: Long,
        val accessLevel: Int = CalendarContract.Calendars.CAL_ACCESS_READ,
        val ownerAccount: String = accountName,
        val hasWritePermission: Boolean = true,
    ) {
        val canWrite: Boolean
            get() = providerWriteCapability(accessLevel, hasWritePermission)
    }

    /** Modeled provider fields used to reject stale writes instead of overwriting them. */
    data class ProviderBaseline(
        val calendarRowId: Long,
        val title: String?,
        val description: String?,
        val location: String?,
        val start: Long?,
        val end: Long?,
        val duration: String?,
        val allDay: Boolean,
        val availability: Int?,
        val recurrenceRule: String?,
        val status: Int?,
        val timeZone: String?,
        val deleted: Boolean,
        val alertReminders: List<Int>,
    )

    /** In-memory routing metadata. It is never confused with CalDAV identity. */
    data class ProviderEvent(
        val eventRowId: Long,
        val masterRowId: Long,
        val originalInstanceTime: Long,
        val instanceBegin: Long,
        val instanceEnd: Long?,
        val originalAllDay: Boolean,
        val recurring: Boolean,
        val existingException: Boolean,
        val calendarId: String,
        val calendarRowId: Long,
        val accountName: String,
        val accountType: String,
        val ownerAccount: String,
        val color: Long,
        val baseline: ProviderBaseline,
        val masterBaseline: ProviderBaseline,
    )

    /** What one read produced: the calendars asked for, their occurrences and write routes. */
    data class Import(
        val calendars: List<CalinoCalendar> = emptyList(),
        val events: List<CalEvent> = emptyList(),
        val routes: Map<String, ProviderEvent> = emptyMap(),
        val sources: List<ImportableCalendar> = emptyList(),
    ) {
        val isEmpty: Boolean get() = calendars.isEmpty() && events.isEmpty()
    }

    /**
     * One `Instances` row, reduced to the fields the mapping reads.
     *
     * Exists so [toEvent] can be a pure function. The same reasoning keeps
     * [ProviderIdentity] free of `Context`: a wrong all-day anchor moves a
     * birthday by a day for anyone outside UTC, and that is worth catching in
     * a unit test rather than on a device.
     */
    data class InstanceRow(
        val eventRowId: Long,
        val beginMillis: Long,
        val endMillis: Long?,
        val duration: String?,
        val title: String?,
        val description: String?,
        val location: String?,
        val allDay: Boolean,
        val availability: Int?,
    )

    private const val DayMillis = 24L * 60 * 60 * 1000

    private val CalendarColumns = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.ACCOUNT_NAME,
        CalendarContract.Calendars.ACCOUNT_TYPE,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.CALENDAR_COLOR,
        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        CalendarContract.Calendars.OWNER_ACCOUNT,
    )

    private val EventColumns = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.CALENDAR_ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.DURATION,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.AVAILABILITY,
        CalendarContract.Events.RRULE,
        CalendarContract.Events.STATUS,
        CalendarContract.Events.ORIGINAL_ID,
        CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
        CalendarContract.Events.ORIGINAL_ALL_DAY,
        CalendarContract.Events.EVENT_TIMEZONE,
        CalendarContract.Events.DELETED,
    )

    private val InstanceColumns = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.CALENDAR_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.DESCRIPTION,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.AVAILABILITY,
        CalendarContract.Instances.DURATION,
        CalendarContract.Instances.HAS_ALARM,
    )

    /**
     * Excludes cancelled meetings.
     *
     * Applied as a selection rather than read as a column, because the
     * `Instances` view does not expose `STATUS` in a projection -- asking for
     * it makes the whole query throw. The name has to be qualified, since the
     * view joins `Events` and only that side has the column.
     */
    private val NotCancelled =
        " AND (Events.${CalendarContract.Events.STATUS} IS NULL" +
            " OR Events.${CalendarContract.Events.STATUS} !=" +
            " ${CalendarContract.Events.STATUS_CANCELED})"

    /**
     * Every calendar on the device that is not Calino's own.
     *
     * The `!=` on account type is the whole of the loop prevention, and the
     * reason it is worth a sentence: Calino *publishes* calendars into this
     * same provider. Reading them back would import its own projection, which
     * the projection would then re-project, and every event would multiply on
     * each pass. One predicate stands between the app and that, so it is
     * tested directly rather than only implied by a larger test.
     *
     * The type comes from [CalinoAccounts.accountType], not a literal: the
     * debug build owns a suffixed type and installs beside release, and a
     * hard-coded string would make a debug build import its release sibling's
     * calendars.
     *
     * Returns empty when the permission is missing. That is a state the
     * surface explains, not an error to throw from.
     */
    fun availableCalendars(context: Context): List<ImportableCalendar> =
        runCatching {
            val ours = CalinoAccounts.accountType(context)
            val hasWritePermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED
            val found = mutableListOf<ImportableCalendar>()
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                CalendarColumns,
                "${CalendarContract.Calendars.ACCOUNT_TYPE} != ?",
                arrayOf(ours),
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(0)
                    val accountType = cursor.getString(2).orEmpty()
                    // Belt and braces. A provider that ignores the selection,
                    // or a row whose type differs only by case, must not get
                    // through: the cost of being wrong here is an echo, not a
                    // missing calendar.
                    if (accountType.equals(ours, ignoreCase = true)) continue
                    found += ImportableCalendar(
                        id = AndroidCalendarId.calendar(rowId),
                        rowId = rowId,
                        accountName = cursor.getString(1).orEmpty(),
                        accountType = accountType,
                        name = cursor.getString(3)?.takeIf { it.isNotBlank() }
                            ?: cursor.getString(1).orEmpty(),
                        // Forced opaque. The provider stores CALENDAR_COLOR
                        // as 0xRRGGBB with no alpha channel, so carrying the
                        // value across verbatim yields alpha 0 -- an
                        // invisible dot and a colourless event, which looks
                        // like a layout bug rather than a missing byte.
                        color = ((cursor.takeIf { !it.isNull(4) }?.getInt(4) ?: DefaultColor)
                            .toLong() and 0xffffffL) or 0xff000000L,
                        accessLevel = if (cursor.isNull(5)) CalendarContract.Calendars.CAL_ACCESS_NONE else cursor.getInt(5),
                        ownerAccount = cursor.getString(6).orEmpty(),
                        hasWritePermission = hasWritePermission,
                    )
                }
            }
            found
        }.getOrDefault(emptyList())

    /**
     * The calendars in [calendarIds] and their occurrences in the window.
     *
     * The window is the projection's own ([ProviderIdentity.PastDays] /
     * [ProviderIdentity.FutureDays]), so both directions agree on how much of
     * the calendar exists. Anything else would make an event visible in one
     * direction and absent in the other at the same date.
     *
     * Reading `Instances` rather than `Events` is what keeps this short. It
     * expands recurrence for us and omits deleted rows, so there is no RRULE
     * parser and no `DELETED` filter here. Fossify Calendar reads `Events`
     * and expands rules itself, but it maintains its own database of them;
     * a viewer that holds nothing does not need to.
     */
    fun read(
        context: Context,
        calendarIds: Set<String>,
        writableCalendarIds: Set<String> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): Import {
        if (calendarIds.isEmpty()) return Import()
        return runCatching {
            val wanted = availableCalendars(context).filter { it.id in calendarIds }
            if (wanted.isEmpty()) return@runCatching Import()

            val calendars = wanted.map { calendar ->
                CalinoCalendar(
                    id = calendar.id,
                    name = calendar.name,
                    color = calendar.color,
                    // Import and write consent are independent. Provider
                    // access alone never makes a foreign calendar editable.
                    readOnly = calendar.id !in writableCalendarIds || !calendar.canWrite,
                    // The provider has no table for tasks or journal entries,
                    // so saying VEVENT is not a restriction, it is the truth.
                    components = setOf("VEVENT"),
                    visible = true,
                )
            }
            val byRow = wanted.associateBy { it.rowId }
            val (events, routes) = readEvents(context, byRow, zone, now)
            Import(calendars, events, routes, wanted)
        }.getOrDefault(Import())
    }

    private fun readEvents(
        context: Context,
        byRow: Map<Long, ImportableCalendar>,
        zone: ZoneId,
        now: Instant,
    ): Pair<List<CalEvent>, Map<String, ProviderEvent>> {
        val start = now.toEpochMilli() - ProviderIdentity.PastDays * DayMillis
        val end = now.toEpochMilli() + ProviderIdentity.FutureDays * DayMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()

        val selection = byRow.keys.joinToString(
            separator = ",",
            prefix = "${CalendarContract.Instances.CALENDAR_ID} IN (",
            postfix = ")",
        )
        val events = mutableListOf<CalEvent>()
        val routes = mutableMapOf<String, ProviderEvent>()
        val metadata = mutableMapOf<Long, EventMetadata?>()
        val baselines = mutableMapOf<Long, ProviderBaseline?>()
        // Reminder rows hang off the event, not the instance, so every
        // occurrence of one series shares them. Reading them once per event
        // rather than once per occurrence turns ~840 queries for a daily
        // series into one.
        val remindersByEvent = mutableMapOf<Long, List<Reminder>>()

        // A cancelled meeting still has instance rows -- Exchange and Google
        // both keep them so an organiser's recall reaches everyone -- and a
        // read-only viewer with no way to strike one out should not draw a
        // meeting that is not happening.
        //
        // The fallback is the point of this being two attempts rather than
        // one. A provider that rejects the qualified column name would
        // otherwise throw, be swallowed by the catch above, and silently
        // empty the whole import. Losing the niceness beats losing the
        // feature.
        val cursor = runCatching {
            context.contentResolver.query(uri, InstanceColumns, selection + NotCancelled, null, null)
        }.getOrNull()
            ?: context.contentResolver.query(uri, InstanceColumns, selection, null, null)

        cursor
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calendar = byRow[cursor.getLong(3)] ?: continue
                    if (cursor.isNull(1)) continue
                    val eventRowId = cursor.getLong(0)
                    val reminders = if (cursor.getInt(10) == 1) {
                        remindersByEvent.getOrPut(eventRowId) { remindersFor(context, eventRowId) }
                    } else emptyList()
                    val row = cursor.row(eventRowId)
                    val meta = metadata.getOrPut(eventRowId) { eventMetadata(context, eventRowId) }
                    val recurring = meta?.recurrenceRule != null || meta?.originalId != null
                    val slot = meta?.originalInstanceTime ?: row.beginMillis
                    val master = meta?.originalId ?: eventRowId
                    val id = AndroidCalendarId.event(master, slot)
                    val event = toEvent(row, calendar, reminders, zone, id, recurring) ?: continue
                    events += event
                    val baseline = baselines.getOrPut(eventRowId) { providerBaseline(context, eventRowId) } ?: continue
                    routes[id] = ProviderEvent(
                        eventRowId = eventRowId,
                        masterRowId = master,
                        originalInstanceTime = slot,
                        instanceBegin = row.beginMillis,
                        instanceEnd = row.endMillis,
                        originalAllDay = meta?.originalAllDay ?: row.allDay,
                        recurring = recurring,
                        existingException = meta?.originalId != null,
                        calendarId = calendar.id,
                        calendarRowId = calendar.rowId,
                        accountName = calendar.accountName,
                        accountType = calendar.accountType,
                        ownerAccount = calendar.ownerAccount,
                        color = calendar.color,
                        baseline = baseline,
                        masterBaseline = if (master == eventRowId) baseline else {
                            baselines.getOrPut(master) { providerBaseline(context, master) } ?: continue
                        },
                    )
                }
            }
        return events to routes
    }

    private fun Cursor.row(eventRowId: Long) = InstanceRow(
        eventRowId = eventRowId,
        beginMillis = getLong(1),
        endMillis = if (isNull(2)) null else getLong(2),
        duration = getString(9),
        title = getString(4),
        description = getString(5),
        location = getString(6),
        allDay = getInt(7) == 1,
        availability = if (isNull(8)) null else getInt(8),
    )

    /**
     * One instance row as a Calino event.
     *
     * The mapping is the inverse of [ProviderIdentity.projectEvent], and is
     * meant to be read next to it. The two contracts that matter, both of
     * which are silent-corruption bugs when got wrong:
     *
     * - An all-day row is anchored at **UTC** midnight regardless of the
     *   device's zone, so it must be read back in UTC. Reading it locally
     *   moves a birthday by a day for anyone east or west of Greenwich.
     * - The provider's `END` is **exclusive**; [CalEvent.endDate] is
     *   **inclusive**. One day is subtracted here, once, exactly as the
     *   projection adds one on the way out.
     */
    fun toEvent(
        row: InstanceRow,
        calendar: ImportableCalendar,
        reminders: List<Reminder>,
        zone: ZoneId,
        id: String = AndroidCalendarId.event(row.eventRowId, row.beginMillis),
        providerRecurring: Boolean = false,
    ): CalEvent? {
        val begin = row.beginMillis
        val finish = row.endMillis
            ?: (begin + (CalendarIngest.durationMillis(row.duration) ?: 0L))

        val date = if (row.allDay) utcDate(begin) else null
        val endDate = if (date != null) {
            // Exclusive to inclusive. A single-day event has END exactly one
            // day after BEGIN, which lands back on the start date.
            utcDate(finish).minusDays(1).coerceAtLeast(date)
        } else {
            null
        }

        return CalEvent(
            id = id,
            title = row.title?.takeIf { it.isNotBlank() } ?: "(no title)",
            color = calendar.color,
            start = if (row.allDay) {
                null
            } else {
                Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
            },
            durationMinutes = if (row.allDay) {
                null
            } else {
                ((finish - begin) / 60_000L).coerceAtLeast(0L).toInt()
            },
            allDay = row.allDay,
            location = row.location?.takeIf { it.isNotBlank() },
            notes = row.description?.takeIf { it.isNotBlank() },
            calendarId = calendar.id,
            date = date,
            endDate = endDate?.takeIf { it != date },
            availability = if (
                row.availability == CalendarContract.Instances.AVAILABILITY_FREE
            ) {
                Availability.Free
            } else {
                Availability.Busy
            },
            reminders = reminders,
            // No CalDAV identity, deliberately. A provider row has none, and a
            // synthetic uid or href would make it indistinguishable from a
            // real CalDAV record to every write path downstream -- which is
            // exactly the code that must never touch one of these.
            uid = null,
            href = null,
            etag = null,
            recurrenceId = null,
            sequence = null,
            providerRecurring = providerRecurring,
        )
    }

    private data class EventMetadata(
        val recurrenceRule: String?,
        val originalId: Long?,
        val originalInstanceTime: Long?,
        val originalAllDay: Boolean?,
    )

    private fun eventMetadata(context: Context, eventRowId: Long): EventMetadata? = runCatching {
        context.contentResolver.query(
            android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventRowId),
            arrayOf(
                CalendarContract.Events.RRULE,
                CalendarContract.Events.ORIGINAL_ID,
                CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                CalendarContract.Events.ORIGINAL_ALL_DAY,
            ),
            null, null, null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else EventMetadata(
                recurrenceRule = cursor.getString(0)?.takeIf { it.isNotBlank() },
                originalId = if (cursor.isNull(1)) null else cursor.getLong(1),
                originalInstanceTime = if (cursor.isNull(2)) null else cursor.getLong(2),
                originalAllDay = if (cursor.isNull(3)) null else cursor.getInt(3) == 1,
            )
        }
    }.getOrNull()

    /** Re-reads exactly the modeled baseline used by the optimistic write guard. */
    fun providerBaseline(context: Context, eventRowId: Long): ProviderBaseline? = runCatching {
        context.contentResolver.query(
            android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventRowId),
            EventColumns,
            null, null, null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else ProviderBaseline(
                calendarRowId = cursor.getLong(1),
                title = cursor.getString(2),
                description = cursor.getString(3),
                location = cursor.getString(4),
                start = if (cursor.isNull(5)) null else cursor.getLong(5),
                end = if (cursor.isNull(6)) null else cursor.getLong(6),
                duration = cursor.getString(7),
                allDay = cursor.getInt(8) == 1,
                availability = if (cursor.isNull(9)) null else cursor.getInt(9),
                recurrenceRule = cursor.getString(10),
                status = if (cursor.isNull(11)) null else cursor.getInt(11),
                timeZone = cursor.getString(15),
                deleted = cursor.getInt(16) == 1,
                alertReminders = alertReminderMinutes(context, eventRowId),
            )
        }
    }.getOrNull()

    private fun alertReminderMinutes(context: Context, eventRowId: Long): List<Int> {
        val result = mutableListOf<Int>()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ? AND ${CalendarContract.Reminders.METHOD} = ? AND " +
                "${CalendarContract.Reminders.MINUTES} >= 0",
            arrayOf(eventRowId.toString(), CalendarContract.Reminders.METHOD_ALERT.toString()),
            null,
        )?.use { cursor -> while (cursor.moveToNext()) result += cursor.getInt(0) }
        return result.distinct().sorted()
    }

    fun availableCalendarsFor(import: Import, calendarId: String): ImportableCalendar? =
        import.sources.firstOrNull { it.id == calendarId }

    private fun remindersFor(context: Context, eventRowId: Long): List<Reminder> {
        val minutes = mutableListOf<Int>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.MINUTES),
                "${CalendarContract.Reminders.EVENT_ID} = ? AND ${CalendarContract.Reminders.METHOD} = ?",
                arrayOf(eventRowId.toString(), CalendarContract.Reminders.METHOD_ALERT.toString()),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(0)) minutes += cursor.getInt(0)
                }
            }
        }
        // Negative minutes mean "after the start", which Calino does not
        // model. Dropping them is the same call the projection makes.
        return minutes.filter { it >= 0 }.distinct().sorted().map { Reminder(it) }
    }

    private fun utcDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    /** Access metadata and runtime permission are both required for editing. */
    fun providerWriteCapability(accessLevel: Int, hasWritePermission: Boolean): Boolean =
        hasWritePermission && accessLevel >= CalendarContract.Calendars.CAL_ACCESS_EDITOR

    /** Used when a provider row carries no colour of its own. */
    private const val DefaultColor = 0x5B8DEF
}
