package calino.malinov.ski.ui.range

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.util.CalinoRangeMode
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.startOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

internal const val RangePagerCenter = 10_000
internal const val RangePagerPageCount = RangePagerCenter * 2 + 1

/**
 * The first day shown for [anchor]. A seven-day window snaps to the week
 * start unless [weekAligned] is off, which is how a window stepped a day at a
 * time by the date bar keeps its new first day.
 */
fun rangeStart(
    anchor: LocalDate,
    mode: CalinoRangeMode,
    weekStart: CalinoWeekStart,
    weekAligned: Boolean = true,
): LocalDate =
    if (mode == CalinoRangeMode.SevenDay && weekAligned) anchor.startOfWeek(weekStart) else anchor

fun rangeDays(
    anchor: LocalDate,
    mode: CalinoRangeMode,
    weekStart: CalinoWeekStart,
    weekAligned: Boolean = true,
): List<LocalDate> {
    val start = rangeStart(anchor, mode, weekStart, weekAligned)
    return List(mode.dayCount) { start.plusDays(it.toLong()) }
}

fun rangeAnchorForPage(base: LocalDate, page: Int, mode: CalinoRangeMode): LocalDate =
    base.plusDays((page - RangePagerCenter).toLong() * mode.dayCount)

/**
 * Whole days a date-bar swipe moves the window: the distance scrolled plus a
 * short projection of the release velocity, in columns of [columnStep] pixels.
 * Positive is later. A swipe never moves more than a full window, so the bar
 * is a finer control than the page swipe rather than a faster one.
 */
internal fun rangeHeaderDaySteps(scrolled: Float, velocity: Float, columnStep: Float, dayCount: Int): Int {
    if (columnStep <= 0f) return 0
    val projected = scrolled + velocity * RangeHeaderFlingSeconds
    return (projected / columnStep).roundToInt().coerceIn(-dayCount, dayCount)
}

private const val RangeHeaderFlingSeconds = .12f

/** Pinch inward reveals more days; spreading outward reveals fewer. */
internal fun rangeModeAfterHorizontalPinch(mode: CalinoRangeMode, horizontalScale: Float): CalinoRangeMode {
    val index = CalinoRangeMode.entries.indexOf(mode)
    return when {
        horizontalScale <= .82f -> CalinoRangeMode.entries[(index + 1).coerceAtMost(CalinoRangeMode.entries.lastIndex)]
        horizontalScale >= 1.18f -> CalinoRangeMode.entries[(index - 1).coerceAtLeast(0)]
        else -> mode
    }
}

internal fun rangeEdgeDirection(pointerX: Float, width: Int, edge: Float): Int = when {
    width <= 0 || edge <= 0f -> 0
    pointerX < edge -> -1
    pointerX > width - edge -> 1
    else -> 0
}

internal fun rangeDropDay(pointerX: Float, width: Int, gutter: Float, days: List<LocalDate>): LocalDate? {
    if (width <= 0 || days.isEmpty()) return null
    val safeGutter = gutter.coerceIn(0f, width.toFloat())
    val laneWidth = (width - safeGutter).coerceAtLeast(1f)
    val index = (((pointerX - safeGutter) / laneWidth) * days.size).toInt().coerceIn(0, days.lastIndex)
    return days[index]
}

/** The live, quarter-hour destination shared by the preview and final write. */
internal fun rangeDropTarget(
    start: LocalDateTime,
    day: LocalDate,
    dragY: Float,
    scrollDelta: Int,
    hourHeight: Float,
): LocalDateTime? {
    if (hourHeight <= 0f) return null
    val minuteDelta = (((dragY + scrollDelta) / hourHeight) * 4f).roundToInt() * 15
    val startMinute = start.hour * 60 + start.minute
    val targetMinute = (startMinute + minuteDelta).coerceIn(0, 23 * 60 + 45)
    return day.atStartOfDay().plusMinutes(targetMinute.toLong())
}

/** The shortest an event can be dragged to by its end edge. */
internal const val RangeMinResizeMinutes = 15

/**
 * Whether [event]'s end edge can be dragged on [day]'s rail: a timed event
 * with a known length that starts and ends within that day, and is not part
 * of a series. A card clipped at midnight is showing an end that is not its
 * own.
 */
internal fun rangeCanResize(event: CalEvent, day: LocalDate): Boolean {
    val start = event.start ?: return false
    val duration = event.durationMinutes ?: return false
    if (event.allDay || start.toLocalDate() != day) return false
    // A single occurrence cannot be written back on its own yet; offering its
    // edge would promise a change the write is going to refuse.
    if (event.recurrence != null || event.recurrenceId != null || event.recurrenceDate != null) return false
    return start.hour * 60 + start.minute + duration <= 24 * 60
}

/**
 * Whether a lift at [pointerY] grabbed the card's end edge rather than its
 * body. The edge lane is at most [edge] tall and never more than a third of
 * the card, so a short card can still be picked up and moved.
 */
internal fun rangeInResizeEdge(pointerY: Float, cardTop: Float, cardBottom: Float, edge: Float): Boolean {
    val height = cardBottom - cardTop
    if (height <= 0f) return false
    val lane = minOf(edge, height / 3f)
    return pointerY >= cardBottom - lane && pointerY <= cardBottom
}

/** The length a held end edge gives, on the quarter-hour grid moves use. */
internal fun rangeResizeDuration(
    start: LocalDateTime,
    durationMinutes: Int,
    dragY: Float,
    scrollDelta: Int,
    hourHeight: Float,
): Int {
    if (hourHeight <= 0f) return durationMinutes
    val minuteDelta = (((dragY + scrollDelta) / hourHeight) * 4f).roundToInt() * 15
    val latest = 24 * 60 - (start.hour * 60 + start.minute)
    return (durationMinutes + minuteDelta).coerceIn(minOf(RangeMinResizeMinutes, durationMinutes), latest)
}
