package calino.malinov.ski.poc.util

import calino.malinov.ski.poc.data.model.CalEvent

/**
 * Where one event sits on the day rail once its neighbours are accounted for.
 *
 * [column] and [columns] describe the horizontal slot: an event alone on the
 * rail is column 0 of 1 and spans the full width, while two events that clash
 * are columns 0 and 1 of 2 and take half each.
 */
data class DayRailSlot(
    val event: CalEvent,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columns: Int,
)

/** Anything shorter than this still gets a readable block on the rail. */
private const val MinimumBlockMinutes = 20

/**
 * Places a day's timed events into side-by-side columns.
 *
 * Every event used to be drawn at the full rail width, so two overlapping
 * meetings stacked on top of each other and the later one hid the earlier
 * one completely. Events are grouped into clusters of transitively
 * overlapping events; within a cluster each event takes the first column
 * whose previous occupant has already ended, and the whole cluster is split
 * into as many columns as it actually needs. Two events that merely touch --
 * one ending exactly when the next begins -- do not overlap and so share a
 * column at full width.
 *
 * All-day events are not on the rail and are dropped here.
 */
fun layoutDayRail(events: List<CalEvent>): List<DayRailSlot> {
    val timed = events.mapNotNull { event ->
        if (event.allDay) return@mapNotNull null
        val start = event.start ?: return@mapNotNull null
        val startMinute = start.hour * 60 + start.minute
        val span = (event.durationMinutes ?: 60).coerceAtLeast(MinimumBlockMinutes)
        Triple(event, startMinute, (startMinute + span).coerceAtMost(24 * 60))
    }.sortedWith(compareBy({ it.second }, { it.third }, { it.first.id }))

    val slots = mutableListOf<DayRailSlot>()
    // Events buffered for the cluster being built, with the column each took.
    val cluster = mutableListOf<Pair<Triple<CalEvent, Int, Int>, Int>>()
    // The end minute of the last event placed in each column.
    val columnEnds = mutableListOf<Int>()

    fun flushCluster() {
        val columns = columnEnds.size.coerceAtLeast(1)
        cluster.forEach { (entry, column) ->
            slots += DayRailSlot(entry.first, entry.second, entry.third, column, columns)
        }
        cluster.clear()
        columnEnds.clear()
    }

    timed.forEach { entry ->
        val (_, startMinute, endMinute) = entry
        // A gap with nothing still running closes the cluster, so a quiet
        // afternoon does not inherit the morning's column count.
        if (columnEnds.isNotEmpty() && columnEnds.all { it <= startMinute }) flushCluster()
        val column = columnEnds.indexOfFirst { it <= startMinute }
            .takeIf { it >= 0 }
            ?: columnEnds.size.also { columnEnds += endMinute }
        columnEnds[column] = maxOf(columnEnds[column], endMinute)
        cluster += entry to column
    }
    flushCluster()

    return slots
}
