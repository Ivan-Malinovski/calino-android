package calino.malinov.ski.platform

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
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
    )

    /** What one read produced: the calendars asked for, and their occurrences. */
    data class Import(
        val calendars: List<CalinoCalendar> = emptyList(),
        val events: List<CalEvent> = emptyList(),
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
        CalendarContract.Instances.STATUS,
    )

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
                        color = (cursor.takeIf { !it.isNull(4) }?.getInt(4) ?: DefaultColor)
                            .toLong() and 0xffffffffL,
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
                    // The owning app is authoritative for every one of these
                    // rows, and v1 never writes one. Marking it read-only is
                    // what keeps the editor from offering an edit that the
                    // repository would then have to refuse.
                    readOnly = true,
                    // The provider has no table for tasks or journal entries,
                    // so saying VEVENT is not a restriction, it is the truth.
                    components = setOf("VEVENT"),
                    visible = true,
                )
            }
            val byRow = wanted.associateBy { it.rowId }
            Import(calendars, readEvents(context, byRow, zone, now))
        }.getOrDefault(Import())
    }

    private fun readEvents(
        context: Context,
        byRow: Map<Long, ImportableCalendar>,
        zone: ZoneId,
        now: Instant,
    ): List<CalEvent> {
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
        // Reminder rows hang off the event, not the instance, so every
        // occurrence of one series shares them. Reading them once per event
        // rather than once per occurrence turns ~840 queries for a daily
        // series into one.
        val remindersByEvent = mutableMapOf<Long, List<Reminder>>()

        context.contentResolver.query(uri, InstanceColumns, selection, null, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calendar = byRow[cursor.getLong(3)] ?: continue
                    if (cursor.isNull(1)) continue
                    // A cancelled meeting still has instance rows -- Exchange
                    // and Google both keep them so the organiser's recall
                    // reaches everyone. Fossify keeps them and marks them;
                    // a read-only viewer that cannot mark them is better off
                    // not drawing a meeting that is not happening.
                    if (!cursor.isNull(11) &&
                        cursor.getInt(11) == CalendarContract.Instances.STATUS_CANCELED
                    ) {
                        continue
                    }
                    val eventRowId = cursor.getLong(0)
                    val reminders = if (cursor.getInt(10) == 1) {
                        remindersByEvent.getOrPut(eventRowId) {
                            remindersFor(context, eventRowId)
                        }
                    } else {
                        emptyList()
                    }
                    events += toEvent(cursor.row(eventRowId), calendar, reminders, zone)
                        ?: continue
                }
            }
        return events
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
            id = AndroidCalendarId.event(row.eventRowId, begin),
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
        )
    }

    private fun remindersFor(context: Context, eventRowId: Long): List<Reminder> {
        val minutes = mutableListOf<Int>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.MINUTES),
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventRowId.toString()),
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

    /** Used when a provider row carries no colour of its own. */
    private const val DefaultColor = 0xFF5B8DEF.toInt()
}
