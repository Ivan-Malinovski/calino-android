package calino.malinov.ski.poc.ui.range

import calino.malinov.ski.poc.util.CalinoRangeMode
import calino.malinov.ski.poc.util.CalinoWeekStart
import calino.malinov.ski.poc.util.startOfWeek
import java.time.LocalDate

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
