package calino.malinov.ski.poc.util

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.lastCoveredDate
import calino.malinov.ski.poc.data.model.occursOn
import calino.malinov.ski.poc.data.model.placementDate
import calino.malinov.ski.poc.data.model.spanLengthDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Immutable, screen-scoped projection of events onto calendar dates.
 *
 * Concrete events and detached occurrences are indexed exactly. Recurring
 * masters can be unbounded, so they are bucketed by the coarsest useful part
 * of their rule and verified with [CalEvent.occursOn] at lookup time. Returned
 * lists always retain repository order, including when a date combines direct
 * and recurring records.
 */
class EventDateIndex private constructor(
    private val ordered: List<CalEvent>,
    private val direct: Map<LocalDate, IntArray>,
    private val daily: IntArray,
    private val weekly: Map<DayOfWeek, IntArray>,
    private val monthly: Map<Int, IntArray>,
    private val yearly: Map<Int, IntArray>,
    private val otherRecurring: IntArray,
) {
    fun eventsOn(date: LocalDate): List<CalEvent> {
        val candidates = candidatesFor(date)
        if (candidates.isEmpty()) return emptyList()
        return candidates.distinct().sorted().mapNotNull { index ->
            ordered[index].takeIf { it.occursOn(date) }
        }
    }

    internal fun candidateCount(date: LocalDate): Int = candidatesFor(date).distinct().size

    private fun candidatesFor(date: LocalDate): List<Int> = buildList {
            direct[date]?.let { addAll(it.asIterable()) }
            addAll(daily.asIterable())
            weekly[date.dayOfWeek]?.let { addAll(it.asIterable()) }
            monthly[date.dayOfMonth]?.let { addAll(it.asIterable()) }
            yearly[date.monthValue]?.let { addAll(it.asIterable()) }
            addAll(otherRecurring.asIterable())
        }

    companion object {
        fun build(events: List<CalEvent>): EventDateIndex {
            val direct = mutableMapOf<LocalDate, MutableList<Int>>()
            val daily = mutableListOf<Int>()
            val weekly = mutableMapOf<DayOfWeek, MutableList<Int>>()
            val monthly = mutableMapOf<Int, MutableList<Int>>()
            val yearly = mutableMapOf<Int, MutableList<Int>>()
            val other = mutableListOf<Int>()

            events.forEachIndexed { index, event ->
                val recurrence = event.recurrence
                if (recurrence == null) {
                    val first = event.placementDate() ?: return@forEachIndexed
                    val last = event.lastCoveredDate() ?: first
                    var date = first
                    while (!date.isAfter(last)) {
                        direct.getOrPut(date) { mutableListOf() }.add(index)
                        date = date.plusDays(1)
                    }
                    return@forEachIndexed
                }

                val fields = recurrence.uppercase(Locale.US).split(';').mapNotNull { part ->
                    val separator = part.indexOf('=')
                    if (separator > 0) part.substring(0, separator) to part.substring(separator + 1) else null
                }.toMap()
                val anchor = event.placementDate()
                // A bucket is only allowed to narrow the candidates when the
                // rule's placement is provable from its FREQ alone. Anything
                // carrying a BY* part other than a weekly BYDAY -- BYMONTHDAY,
                // BYSETPOS, BYMONTH, an ordinal BYDAY -- can land on a date the
                // anchor does not predict, so it is always a candidate and
                // RecurrenceRules decides.
                val byParts = fields.keys.filter { it.startsWith("BY") }
                val simple = byParts.isEmpty() ||
                    (fields["FREQ"] == "WEEKLY" && byParts == listOf("BYDAY"))
                // A multi-day span's later days fall on dates its own
                // occurrence start does not predict, so a bucket keyed on the
                // start alone must widen by the span length or miss them.
                val spanLength = event.spanLengthDays()
                when (fields["FREQ"].takeIf { simple }) {
                    "DAILY" -> daily += index
                    "WEEKLY" -> {
                        val days = fields["BYDAY"]?.split(',')?.mapNotNull(::dayOfWeek).orEmpty()
                            .ifEmpty { listOfNotNull(anchor?.dayOfWeek) }
                        if (days.isEmpty()) other += index
                        else days.distinct().forEach { start ->
                            for (offset in 0..spanLength) {
                                val covered = DayOfWeek.of(((start.value - 1 + offset) % 7).toInt() + 1)
                                weekly.getOrPut(covered) { mutableListOf() }.add(index)
                            }
                        }
                    }
                    // RFC 5545 skips a month too short for the anchor day
                    // rather than clamping, so the anchor day is exact -- but
                    // only when the whole span stays inside one month.
                    "MONTHLY" -> anchor?.dayOfMonth
                        ?.takeIf { spanLength == 0L }
                        ?.let { monthly.getOrPut(it) { mutableListOf() }.add(index) }
                        ?: run { other += index }
                    "YEARLY" -> anchor
                        ?.takeIf { spanLength == 0L }
                        ?.let { yearly.getOrPut(it.monthValue) { mutableListOf() }.add(index) }
                        ?: run { other += index }
                    else -> other += index
                }
            }
            return EventDateIndex(
                ordered = events.toList(),
                direct = direct.mapValues { it.value.toIntArray() },
                daily = daily.toIntArray(),
                weekly = weekly.mapValues { it.value.toIntArray() },
                monthly = monthly.mapValues { it.value.toIntArray() },
                yearly = yearly.mapValues { it.value.toIntArray() },
                otherRecurring = other.toIntArray(),
            )
        }

        private fun dayOfWeek(value: String): DayOfWeek? = when (value.trim()) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }
    }
}
