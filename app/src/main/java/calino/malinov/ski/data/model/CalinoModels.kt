package calino.malinov.ski.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Visual-POC data contract; server persistence and sync live in the DAV layer. */
data class Attendee(val name: String, val email: String)

/** A lead-time alarm, optionally repeated at a fixed minute interval. */
data class Reminder(
    val minutesBefore: Int,
    val repeatCount: Int = 0,
    val repeatIntervalMinutes: Int = 0,
)

/** Free/busy transparency. The POC keeps it to the two states a person picks. */
enum class Availability { Busy, Free }

/** Scope used when an edit or delete targets one occurrence of a series. */
enum class RecurrenceEditScope { This, Future, All }

data class CalEvent(
    val id: String,
    val title: String,
    val color: Long,
    val start: LocalDateTime?,
    val durationMinutes: Int?,
    val allDay: Boolean = false,
    val recurrence: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val calendarId: String,
    /** Explicit placement for all-day records; null means no all-day date. */
    val date: LocalDate? = null,
    /**
     * Last day of a multi-day all-day span, inclusive. Null for a single day.
     *
     * Inclusive on purpose: iCalendar's DTEND is exclusive, and the conversion
     * happens once at the parser rather than at every renderer.
     */
    val endDate: LocalDate? = null,
    val availability: Availability = Availability.Busy,
    val categories: List<String> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val travelTimeMinutes: Int? = null,
    /** Task ids this event was attached to in the editor. */
    val relatedTo: List<String> = emptyList(),
    /** Local contact reminder marker, or another local relation URI. */
    val url: String? = null,
    /** iCalendar UID. Null for records created locally. */
    val uid: String? = null,
    /** Absolute CalDAV resource URL. Null for records created locally. */
    val href: String? = null,
    /** Server ETag used for conditional writes and stale-resource rebasing. */
    val etag: String? = null,
    /** Original RECURRENCE-ID for a detached or expanded occurrence. */
    val recurrenceId: Instant? = null,
    /** Date-only RECURRENCE-ID; date-only values have no meaningful instant. */
    val recurrenceDate: LocalDate? = null,
    /** Server SEQUENCE, preserved for conflict-aware writes. */
    val sequence: Int? = null,
    /** Recurrence marker for CalendarContract instances; not CalDAV identity. */
    val providerRecurring: Boolean = false,
)

/** Date-aware event matching shared by calendar and day-modal renderers. */
fun CalEvent.occursOn(day: LocalDate): Boolean = occurrenceStartCovering(day) != null

/**
 * The occurrence start date whose span covers [day], or null if none does.
 *
 * For a non-recurring event this is just [placementDate], once its span (via
 * [lastCoveredDate]) is checked to actually reach [day]. For a recurring one,
 * `RecurrenceRules` only answers "is this date an occurrence start" -- but a
 * multi-day span's own days 2..n are not starts. So this re-derives the span
 * length once and walks backward from [day] looking for the occurrence start
 * that would have carried it, rather than checking [day] itself.
 */
fun CalEvent.occurrenceStartCovering(day: LocalDate): LocalDate? {
    val anchor = placementDate() ?: return null
    if (anchor == day) return anchor
    // Date-only DTEND has already been made inclusive. Timed DTEND is
    // represented as a duration; an exact midnight belongs to the previous
    // day, while any later time also occupies its ending date.
    lastCoveredDate()?.let { last ->
        if (!day.isBefore(anchor) && !day.isAfter(last)) return anchor
    }
    val rule = recurrence ?: return null
    if (day.isBefore(anchor)) return null
    // One engine, shared with the CalDAV expander. See RecurrenceRules.
    val anchorStart = start ?: anchor.atStartOfDay()
    val spanLength = spanLengthDays()
    for (offset in 0..spanLength) {
        val candidateStart = day.minusDays(offset)
        if (candidateStart.isBefore(anchor)) break
        if (RecurrenceRules.occursOn(rule, anchorStart, allDay, candidateStart)) return candidateStart
    }
    return null
}

/**
 * The next [limit] occurrence starts after [after], for a recurring event.
 *
 * Named apart from `util.nextOccurrences`, which is a separate hand-rolled
 * expander that predates the shared engine and understands a smaller grammar.
 *
 * Empty for a one-off, and for a series that has no occurrence left. [after]
 * is the occurrence being looked at rather than today, so the list reads as
 * "and then" from wherever the person is standing.
 */
fun CalEvent.upcomingOccurrences(after: LocalDate, limit: Int = 5): List<LocalDate> {
    val rule = recurrence ?: return emptyList()
    val anchor = placementDate() ?: return emptyList()
    return RecurrenceRules.nextOccurrences(rule, start ?: anchor.atStartOfDay(), allDay, after, limit)
}

fun CalEvent.placementDate(): LocalDate? = if (allDay) date else start?.toLocalDate()

/** Inclusive final date occupied by this event, or null when it stays on its start date. */
fun CalEvent.lastCoveredDate(): LocalDate? {
    val anchor = placementDate() ?: return null
    if (allDay) return endDate?.takeIf { it.isAfter(anchor) }
    val startValue = start ?: return null
    val endExclusive = durationMinutes?.takeIf { it > 0 }
        ?.let { startValue.plusMinutes(it.toLong()) }
        ?: return null
    val last = if (endExclusive.toLocalTime() == LocalTime.MIDNIGHT) {
        endExclusive.toLocalDate().minusDays(1)
    } else {
        endExclusive.toLocalDate()
    }
    return last.takeIf { it.isAfter(anchor) }
}

/**
 * Days a span covers past its placement date: 0 for a single day, else the
 * gap to [lastCoveredDate]. A recurring event's own [lastCoveredDate] is
 * relative to the *master's* placement, so this is a length to re-apply at
 * every occurrence, not a date to compare against directly.
 */
fun CalEvent.spanLengthDays(): Long {
    val anchor = placementDate() ?: return 0L
    val last = lastCoveredDate() ?: return 0L
    return ChronoUnit.DAYS.between(anchor, last).coerceAtLeast(0L)
}

data class CalTask(
    val id: String,
    val title: String,
    val color: Long,
    val due: LocalDate?,
    val done: Boolean = false,
    val category: String? = null,
    val dueTime: LocalTime? = null,
    /** Original DTSTART, retained separately from DUE for CalDAV round-tripping. */
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val notes: String? = null,
    val reminder: Reminder? = null,
    /** iCalendar UID. Null for records created locally. */
    val uid: String? = null,
    /** Absolute CalDAV resource URL. Null for records created locally. */
    val href: String? = null,
    val etag: String? = null,
    /** Calendar collection that owns this VTODO. */
    val calendarId: String = "personal",
    /** UID of the immediate parent VTODO, encoded as RELATED-TO. */
    val parentTaskId: String? = null,
    val recurrence: String? = null,
    val recurrenceId: Instant? = null,
    val recurrenceDate: LocalDate? = null,
    val sequence: Int? = null,
    /** RFC 5545 PRIORITY (0 = undefined, 1 highest, 9 lowest). */
    val priority: Int = 0,
    /** RFC 5545 progress, retained between 0 and 100 instead of flattened to done/open. */
    val percentComplete: Int = if (done) 100 else 0,
    /** Original status, including states the UI does not originate such as CANCELLED. */
    val status: String = if (done) "COMPLETED" else "NEEDS-ACTION",
    val completedAt: Instant? = null,
    /** Editor-only write intent; never serialized as a vendor property. */
    val recurrenceChanged: Boolean = false,
    val recurrenceScope: RecurrenceEditScope = RecurrenceEditScope.All,
)

data class JournalEntry(
    val id: String,
    val date: LocalDate,
    val title: String,
    val body: String,
    /** iCalendar UID. Null for records created locally. */
    val uid: String? = null,
    /** Absolute CalDAV resource URL. Null for records created locally. */
    val href: String? = null,
    val etag: String? = null,
)

data class NewEvent(
    val title: String,
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val durationMinutes: Int? = null,
    val allDay: Boolean = startTime == null,
    val color: Long = 0xFFC2697F,
    val recurrence: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val calendarId: String = "personal",
    val availability: Availability = Availability.Busy,
    val categories: List<String> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val travelTimeMinutes: Int? = null,
    val relatedTo: List<String> = emptyList(),
    val url: String? = null,
    /** Existing server identity, used by recurrence-aware updates. */
    val uid: String? = null,
    val href: String? = null,
    val etag: String? = null,
    val recurrenceId: Instant? = null,
    val recurrenceDate: LocalDate? = null,
    val sequence: Int? = null,
    /** Inclusive last date for imported multi-day all-day events. */
    val endDate: LocalDate? = null,
    /** True when the editor explicitly changed or cleared the recurrence rule. */
    val recurrenceChanged: Boolean = false,
    val recurrenceScope: RecurrenceEditScope = RecurrenceEditScope.All,
)

data class NewTask(
    val title: String,
    val due: LocalDate? = null,
    val color: Long = 0xFF5D9A78,
    val category: String? = null,
    val dueTime: LocalTime? = null,
    /** Existing DTSTART. The task UI edits DUE but must not erase a foreign start. */
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val notes: String? = null,
    val reminder: Reminder? = null,
    val priority: Int = 0,
    val percentComplete: Int = 0,
    val status: String? = null,
    val completedAt: Instant? = null,
    val recurrence: String? = null,
    /** iCalendar UID. Null for records created locally. */
    val uid: String? = null,
    /** Absolute CalDAV resource URL. Null for records created locally. */
    val href: String? = null,
    val etag: String? = null,
    val calendarId: String = "personal",
    val parentTaskId: String? = null,
    val recurrenceId: Instant? = null,
    val recurrenceDate: LocalDate? = null,
    val sequence: Int? = null,
    val recurrenceChanged: Boolean = false,
    val recurrenceScope: RecurrenceEditScope = RecurrenceEditScope.All,
)

data class NewJournal(
    val date: LocalDate,
    val title: String,
    val body: String,
)
