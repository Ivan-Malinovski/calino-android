package calino.malinov.ski.poc.util

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.occurrenceStartCovering
import calino.malinov.ski.poc.data.model.spanLengthDays
import calino.malinov.ski.poc.state.tasksDueOn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One thing the all-day band can draw: a multi-day event span or a task due on a day. */
sealed interface AllDayItem {
    val key: String

    data class Event(val event: CalEvent, val occurrenceStart: LocalDate) : AllDayItem {
        override val key: String get() = "event:${event.id}@$occurrenceStart"
    }

    data class Task(val task: CalTask, val day: LocalDate) : AllDayItem {
        override val key: String get() = "task:${task.id}@$day"
    }
}

/**
 * One all-day event occurrence resolved to real calendar bounds and clipped
 * to the visible window.
 *
 * [occurrenceStart] is the event's true, unclipped start -- the identity of
 * this occurrence, and what a click-through or a content description should
 * name. [start] and [endInclusive] are clipped to the days being laid out;
 * [continuesBefore] and [continuesAfter] say whether that clipping actually
 * cut something off.
 */
data class AllDaySpan(
    val event: CalEvent,
    val occurrenceStart: LocalDate,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/** Where one item sits in the band: which lane, and which day columns it spans. */
data class AllDayPlacement(
    val item: AllDayItem,
    val lane: Int,
    val startColumn: Int,
    val endColumn: Int,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

data class AllDayBandLayout(
    val placements: List<AllDayPlacement>,
    val laneCount: Int,
    val overflow: List<AllDayPlacement>,
    val overflowEventCount: Int,
    val overflowTaskCount: Int,
)

/**
 * Resolves every all-day event touching [days] into one span per occurrence,
 * clipped to that window.
 *
 * [eventsOn] is a per-date projection (an [EventDateIndex] in practice), so
 * a multi-day or recurring event is reached once from every day it covers --
 * deduplication happens here, keyed by (event id, occurrence start), which is
 * why a recurring event's separate occurrences never collide.
 */
fun resolveAllDaySpans(days: List<LocalDate>, eventsOn: (LocalDate) -> List<CalEvent>): List<AllDaySpan> {
    if (days.isEmpty()) return emptyList()
    val windowStart = days.first()
    val windowEnd = days.last()
    val seen = LinkedHashMap<String, AllDaySpan>()
    days.forEach { day ->
        eventsOn(day).forEach { event ->
            if (!event.allDay) return@forEach
            val occurrenceStart = event.occurrenceStartCovering(day) ?: return@forEach
            val key = "${event.id}@$occurrenceStart"
            if (seen.containsKey(key)) return@forEach
            val unclippedEnd = occurrenceStart.plusDays(event.spanLengthDays())
            seen[key] = AllDaySpan(
                event = event,
                occurrenceStart = occurrenceStart,
                start = maxOf(occurrenceStart, windowStart),
                endInclusive = minOf(unclippedEnd, windowEnd),
                continuesBefore = occurrenceStart.isBefore(windowStart),
                continuesAfter = unclippedEnd.isAfter(windowEnd),
            )
        }
    }
    return seen.values.toList()
}

/** An item queued for packing, already resolved to column indices into `days`. */
private data class RawBandItem(
    val item: AllDayItem,
    val startColumn: Int,
    val endColumn: Int,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/**
 * Packs [spans] and each day's due [tasks] into lanes across [days].
 *
 * Ordering is fixed, not incidental: every event is placed before any task,
 * because a span claims contiguous lanes that a single-day chip would
 * otherwise fragment. Within events, order is start date ascending, then
 * length descending, then id -- so a longer span wins the lower lane on a
 * tie. Tasks keep [tasksDueOn]'s own order, extended day by day across the
 * window. Priority never affects ordering: it is a visual stripe only, and
 * reordering on a priority change would make the band jump.
 *
 * Packing itself is greedy: each item takes the lowest lane none of whose
 * occupied columns intersect the item's own columns. An item that lands at
 * `lane >= laneLimit` is reported in [AllDayBandLayout.overflow] but still
 * occupies that lane -- it is never repacked into a lower one -- so calling
 * this again with a larger [laneLimit] (typically [Int.MAX_VALUE] once
 * expanded) never reshuffles what was already visible.
 */
fun layoutAllDayBand(
    days: List<LocalDate>,
    spans: List<AllDaySpan>,
    tasks: List<CalTask>,
    laneLimit: Int,
): AllDayBandLayout {
    if (days.isEmpty()) return AllDayBandLayout(emptyList(), 0, emptyList(), 0, 0)
    val columnOf = days.withIndex().associate { (index, date) -> date to index }

    val eventItems = spans
        .sortedWith(
            compareBy<AllDaySpan> { it.start }
                .thenByDescending { ChronoUnit.DAYS.between(it.start, it.endInclusive) }
                .thenBy { it.event.id },
        )
        .mapNotNull { span ->
            val startColumn = columnOf[span.start] ?: return@mapNotNull null
            val endColumn = columnOf[span.endInclusive] ?: return@mapNotNull null
            RawBandItem(
                item = AllDayItem.Event(span.event, span.occurrenceStart),
                startColumn = startColumn,
                endColumn = endColumn,
                continuesBefore = span.continuesBefore,
                continuesAfter = span.continuesAfter,
            )
        }

    val taskItems = days.flatMap { day ->
        val column = columnOf.getValue(day)
        tasksDueOn(tasks, day).map { task ->
            RawBandItem(
                item = AllDayItem.Task(task, day),
                startColumn = column,
                endColumn = column,
                continuesBefore = false,
                continuesAfter = false,
            )
        }
    }

    val placements = mutableListOf<AllDayPlacement>()
    val overflow = mutableListOf<AllDayPlacement>()
    // Occupied columns per lane, tracked exactly rather than as a single
    // "last end" -- events are placed before tasks regardless of date, so a
    // later-starting event must not block an earlier, non-overlapping task
    // from a lane a running-end shortcut would think was still busy.
    val laneOccupancy = mutableListOf<BooleanArray>()

    (eventItems + taskItems).forEach { raw ->
        val range = raw.startColumn..raw.endColumn
        val lane = laneOccupancy.indexOfFirst { occupied -> range.none { occupied[it] } }
            .let { found -> if (found >= 0) found else laneOccupancy.size.also { laneOccupancy += BooleanArray(days.size) } }
        range.forEach { column -> laneOccupancy[lane][column] = true }
        val placement = AllDayPlacement(raw.item, lane, raw.startColumn, raw.endColumn, raw.continuesBefore, raw.continuesAfter)
        if (lane < laneLimit) placements += placement else overflow += placement
    }

    return AllDayBandLayout(
        placements = placements,
        laneCount = placements.maxOfOrNull { it.lane + 1 } ?: 0,
        overflow = overflow,
        overflowEventCount = overflow.count { it.item is AllDayItem.Event },
        overflowTaskCount = overflow.count { it.item is AllDayItem.Task },
    )
}
