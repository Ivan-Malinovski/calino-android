package calino.malinov.ski.poc.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Instant
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Visual-POC data contract; server persistence and sync live in the DAV layer. */
data class Attendee(val name: String, val email: String)

/** A single alarm expressed as lead time before the record's start. */
data class Reminder(val minutesBefore: Int)

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
)

/** Date-aware event matching shared by calendar and day-modal renderers. */
fun CalEvent.occursOn(day: LocalDate): Boolean {
    val anchor = placementDate() ?: return false
    if (anchor == day) return true
    // A multi-day all-day span covers every day through its inclusive end.
    endDate?.let { last ->
        if (!day.isBefore(anchor) && !day.isAfter(last)) return true
    }
    val fields = recurrence?.uppercase(Locale.US)?.split(';')?.mapNotNull { part ->
        part.indexOf('=').takeIf { it > 0 }?.let { separator -> part.substring(0, separator) to part.substring(separator + 1) }
    }?.toMap() ?: return false
    if (day.isBefore(anchor)) return false
    val until = fields["UNTIL"]?.removeSuffix("Z")?.let { value ->
        runCatching {
            when (value.length) {
                8 -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                15 -> LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)).toLocalDate()
                else -> null
            }
        }.getOrNull()
    }
    if (until != null && day.isAfter(until)) return false
    return when (fields["FREQ"]) {
        "DAILY" -> true
        "WEEKLY" -> {
            val weekdays = fields["BYDAY"]?.split(',')?.mapNotNull(::dayOfWeekForCode)?.toSet().orEmpty()
                .ifEmpty { setOf(anchor.dayOfWeek) }
            day.dayOfWeek in weekdays
        }
        // A day-of-month anchor past the length of a shorter month falls on
        // that month's last day rather than skipping the month entirely.
        "MONTHLY" -> day.dayOfMonth == anchor.dayOfMonth.coerceAtMost(day.lengthOfMonth())
        "YEARLY" -> day.monthValue == anchor.monthValue &&
            day.dayOfMonth == anchor.dayOfMonth.coerceAtMost(day.lengthOfMonth())
        else -> false
    }
}

private fun dayOfWeekForCode(code: String): DayOfWeek? = when (code.trim()) {
    "MO" -> DayOfWeek.MONDAY
    "TU" -> DayOfWeek.TUESDAY
    "WE" -> DayOfWeek.WEDNESDAY
    "TH" -> DayOfWeek.THURSDAY
    "FR" -> DayOfWeek.FRIDAY
    "SA" -> DayOfWeek.SATURDAY
    "SU" -> DayOfWeek.SUNDAY
    else -> null
}

fun CalEvent.placementDate(): LocalDate? = if (allDay) date else start?.toLocalDate()

data class CalTask(
    val id: String,
    val title: String,
    val color: Long,
    val due: LocalDate?,
    val done: Boolean = false,
    val category: String? = null,
    val dueTime: LocalTime? = null,
    val notes: String? = null,
    val reminder: Reminder? = null,
    /** iCalendar UID. Null for records created locally. */
    val uid: String? = null,
    /** Absolute CalDAV resource URL. Null for records created locally. */
    val href: String? = null,
    val etag: String? = null,
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
    val notes: String? = null,
    val reminder: Reminder? = null,
    /** iCalendar UID. Null for records created locally. */
    val uid: String? = null,
    /** Absolute CalDAV resource URL. Null for records created locally. */
    val href: String? = null,
    val etag: String? = null,
)

data class NewJournal(
    val date: LocalDate,
    val title: String,
    val body: String,
)
