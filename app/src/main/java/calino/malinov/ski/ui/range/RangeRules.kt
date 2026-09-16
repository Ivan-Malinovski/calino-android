package calino.malinov.ski.ui.range

import calino.malinov.ski.util.CalinoRangeMode
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.startOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

internal const val RangePagerCenter = 10_000
internal const val RangePagerPageCount = RangePagerCenter * 2 + 1

fun rangeStart(anchor: LocalDate, mode: CalinoRangeMode, weekStart: CalinoWeekStart): LocalDate =
    if (mode == CalinoRangeMode.SevenDay) anchor.startOfWeek(weekStart) else anchor

fun rangeDays(anchor: LocalDate, mode: CalinoRangeMode, weekStart: CalinoWeekStart): List<LocalDate> {
    val start = rangeStart(anchor, mode, weekStart)
    return List(mode.dayCount) { start.plusDays(it.toLong()) }
}

fun rangeAnchorForPage(base: LocalDate, page: Int, mode: CalinoRangeMode): LocalDate =
    base.plusDays((page - RangePagerCenter).toLong() * mode.dayCount)

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
