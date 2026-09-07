package calino.malinov.ski.poc.util

import calino.malinov.ski.poc.data.model.CalEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val TimeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val RecurrenceEndFormat = DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val RecurrenceUntilDateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)

fun formatCalinoDate(date: LocalDate): String = date.format(DateFormat)
fun formatCalinoTime(time: LocalDateTime): String = time.format(TimeFormat)
fun formatEventDate(event: CalEvent): String? = event.start?.let { formatCalinoDate(it.toLocalDate()) }

/**
 * Converts the small weekly fixture vocabulary to copy a person can scan.
 * One-off events intentionally have no recurrence summary.
 */
fun formatRecurrenceSummary(event: CalEvent): String {
    val fields = recurrenceFields(event.recurrence) ?: return ""
    if (fields["FREQ"] != "WEEKLY") return "Repeating event"

    val weekdays = fields["BYDAY"]
        ?.split(',')
        ?.mapNotNull(::dayOfWeekForCode)
        ?.ifEmpty { null }
        ?: event.start?.let { listOf(it.dayOfWeek) }
    val frequency = weekdays
        ?.joinToString(", ") { it.getDisplayName(TextStyle.FULL, Locale.US) }
        ?.let { "Every $it" }
        ?: "Every week"
    return parseRecurrenceUntil(fields["UNTIL"])
        ?.let { "$frequency until ${it.format(RecurrenceEndFormat)}" }
        ?: frequency
}

/**
 * Expands only the small recurrence vocabulary used by this fixture POC.
 * Weekly BYDAY and UNTIL values are honored; one-off events return no
 * materialised occurrences.
 */
fun nextOccurrences(event: CalEvent, from: LocalDate, limit: Int = 3): List<LocalDateTime> {
    val start = event.start ?: return emptyList()
    if (limit <= 0) return emptyList()

    val fields = recurrenceFields(event.recurrence) ?: return emptyList()
    if (fields["FREQ"] != "WEEKLY") return emptyList()

    val weekdays = fields["BYDAY"]
        ?.split(',')
        ?.mapNotNull(::dayOfWeekForCode)
        ?.toSet()
        ?.ifEmpty { null }
        ?: setOf(start.dayOfWeek)
    val until = parseRecurrenceUntil(fields["UNTIL"])

    return generateSequence(start.toLocalDate()) { it.plusDays(1) }
        .takeWhile { until == null || !it.isAfter(until) }
        .filter { !it.isBefore(start.toLocalDate()) && !it.isBefore(from) && it.dayOfWeek in weekdays }
        .map { it.atTime(start.toLocalTime()) }
        .take(limit)
        .toList()
}

private fun recurrenceFields(recurrence: String?): Map<String, String>? {
    val rule = recurrence?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
    val fields = rule.split(';').associateNotNull { part ->
        val separator = part.indexOf('=')
        if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
    }.toMutableMap()
    if (fields.isEmpty() && rule == "WEEKLY") fields["FREQ"] = "WEEKLY"
    return fields.takeIf { it.isNotEmpty() }
}

private inline fun <T, R> Iterable<T>.associateNotNull(transform: (T) -> Pair<String, R>?): Map<String, R> = buildMap {
    for (item in this@associateNotNull) transform(item)?.let { (key, value) -> put(key, value) }
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

private fun parseRecurrenceUntil(value: String?): LocalDate? {
    val normalized = value?.removeSuffix("Z") ?: return null
    return runCatching {
        when (normalized.length) {
            8 -> LocalDate.parse(normalized, DateTimeFormatter.BASIC_ISO_DATE)
            15 -> LocalDateTime.parse(normalized, RecurrenceUntilDateTimeFormat).toLocalDate()
            else -> null
        }
    }.getOrNull()
}
