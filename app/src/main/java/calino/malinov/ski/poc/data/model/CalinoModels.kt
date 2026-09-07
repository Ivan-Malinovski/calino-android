package calino.malinov.ski.poc.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Visual-POC data contract; persistence and sync are intentionally deferred. */
data class Attendee(val name: String, val email: String)

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
)

/** Date-aware event matching shared by calendar and day-modal renderers. */
fun CalEvent.occursOn(day: LocalDate): Boolean {
    val anchor = placementDate() ?: return false
    if (anchor == day) return true
    val fields = recurrence?.uppercase(Locale.US)?.split(';')?.mapNotNull { part ->
        part.indexOf('=').takeIf { it > 0 }?.let { separator -> part.substring(0, separator) to part.substring(separator + 1) }
    }?.toMap() ?: return false
    if (fields["FREQ"] != "WEEKLY" || day.isBefore(anchor)) return false
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
    val defaultDay = anchor.dayOfWeek
    val weekdays = fields["BYDAY"]?.split(',')?.mapNotNull(::dayOfWeekForCode)?.toSet().orEmpty().ifEmpty { setOf(defaultDay) }
    return day.dayOfWeek in weekdays
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
)

data class JournalEntry(val id: String, val date: LocalDate, val title: String, val body: String)

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
)

data class NewTask(
    val title: String,
    val due: LocalDate? = null,
    val color: Long = 0xFF5D9A78,
    val category: String? = null,
)

data class NewJournal(
    val date: LocalDate,
    val title: String,
    val body: String,
)
