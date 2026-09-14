package calino.malinov.ski.data.model

import biweekly.Biweekly
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The one recurrence engine.
 *
 * Calino used to carry two: `ICalMapper` expanded server series with biweekly's
 * RFC 5545 iterator, while [CalEvent.occursOn] re-implemented a subset of the
 * grammar by hand and disagreed with it -- an unbounded `FREQ=WEEKLY;INTERVAL=2`
 * rendered every week. Both now evaluate rules here, with the same library, so
 * the grid, the reminder planner and the widget cannot drift from the expander.
 *
 * A rule is evaluated by handing biweekly a minimal VEVENT and walking its date
 * iterator, which is what the expander does. Results are memoised per rule,
 * anchor and year: `occursOn` is called once per visible event per visible day.
 */
internal object RecurrenceRules {

    /** A pathological rule must not walk forever inside one year. */
    private const val MaxOccurrencesPerYear = 750

    /** Enough for a year of daily events across a handful of visible series. */
    private const val MaxCachedYears = 64

    private data class Key(val rule: String, val anchor: LocalDateTime, val allDay: Boolean, val year: Int)

    private val cache = object : LinkedHashMap<Key, Set<LocalDate>>(MaxCachedYears, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Set<LocalDate>>) =
            size > MaxCachedYears
    }

    fun occursOn(rule: String, anchor: LocalDateTime, allDay: Boolean, day: LocalDate): Boolean {
        if (day.isBefore(anchor.toLocalDate())) return false
        val key = Key(rule.trim(), anchor, allDay, day.year)
        val dates = synchronized(cache) { cache[key] } ?: datesIn(key).also {
            synchronized(cache) { cache[key] = it }
        }
        return day in dates
    }

    private fun datesIn(key: Key): Set<LocalDate> {
        val event = runCatching {
            Biweekly.parse(icalText(key.rule, key.anchor, key.allDay)).first()?.events?.firstOrNull()
        }.getOrNull() ?: return emptySet()
        val zone = ZoneId.systemDefault()
        val from = LocalDate.of(key.year, 1, 1).atStartOfDay(zone).toInstant()
        val until = LocalDate.of(key.year + 1, 1, 1).atStartOfDay(zone).toInstant()

        val dates = mutableSetOf<LocalDate>()
        val iterator = runCatching { event.getDateIterator(TimeZone.getDefault()) }.getOrNull() ?: return emptySet()
        // advanceTo skips the years before the queried one without materialising
        // them, the same way the CalDAV expander reaches its window.
        runCatching { iterator.advanceTo(Date.from(from)) }.getOrElse { return emptySet() }
        while (iterator.hasNext() && dates.size < MaxOccurrencesPerYear) {
            val instant = runCatching { iterator.next().toInstant() }.getOrNull() ?: break
            if (instant >= until) break
            dates += instant.atZone(zone).toLocalDate()
        }
        return dates
    }

    /**
     * Wraps a rule in the smallest calendar object that can express it.
     *
     * `CalEvent.recurrence` holds RRULE text, but a locally written rule may
     * carry `EXDATE=` inside that same string. Those parts are lifted out into
     * real properties, because an EXDATE is not an RRULE part and biweekly
     * would otherwise discard the whole rule.
     */
    private fun icalText(rule: String, anchor: LocalDateTime, allDay: Boolean): String {
        val parts = rule.split(';').filter { it.isNotBlank() }
        val exceptions = parts.filter { it.uppercase(Locale.US).startsWith("EXDATE=") }
            .map { it.substringAfter('=') }
            .flatMap { it.split(',') }
            .filter { it.isNotBlank() }
        val recurrence = parts.filterNot { it.uppercase(Locale.US).startsWith("EXDATE=") }.joinToString(";")

        return buildString {
            append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Calino//Recurrence//EN\r\n")
            append("BEGIN:VEVENT\r\nUID:calino-recurrence\r\n")
            if (allDay) {
                append("DTSTART;VALUE=DATE:").append(anchor.format(DateStamp)).append("\r\n")
            } else {
                append("DTSTART:").append(anchor.format(DateTimeStamp)).append("\r\n")
            }
            if (recurrence.isNotEmpty()) append("RRULE:").append(recurrence).append("\r\n")
            exceptions.forEach { value ->
                if (value.length == 8) append("EXDATE;VALUE=DATE:") else append("EXDATE:")
                append(value).append("\r\n")
            }
            append("END:VEVENT\r\nEND:VCALENDAR\r\n")
        }
    }

    private val DateStamp = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
    private val DateTimeStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)
}
