package calino.malinov.ski.ui.year

import calino.malinov.ski.state.SplitPaneWidthDp
import calino.malinov.ski.state.shouldSplit
import calino.malinov.ski.util.EventDateIndex
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** The pager is centred on the year the page was opened on, and reaches this far either way. */
internal const val YearPagerCenter = 400
internal const val YearPagerPageCount = YearPagerCenter * 2 + 1

/** The year a pager page shows, given the [base] year the pager is centred on. */
internal fun yearForPage(base: Int, page: Int): Int = base + (page - YearPagerCenter)

internal fun pageForYear(base: Int, year: Int): Int = YearPagerCenter + (year - base)

/** [date] moved into [year], keeping the month and day; 29 February lands on the 28th in a common year. */
internal fun clampDayInYear(date: LocalDate, year: Int): LocalDate = date.withYear(year)

/** Event counts at which a day steps up to the next heat level. */
private val HeatThresholds = intArrayOf(0, 1, 2, 4, 6)

internal const val YearHeatLevels = 5

/** 0 for a quiet day up to 4 for a busy one: 0, 1, 2-3, 4-5 and 6+ events. */
internal fun yearHeatLevel(count: Int): Int {
    var level = 0
    for (step in 1 until HeatThresholds.size) if (count >= HeatThresholds[step]) level = step
    return level
}

private val LightHeatAlpha = floatArrayOf(0f, .10f, .22f, .38f, .55f)
private val DarkHeatAlpha = floatArrayOf(0f, .14f, .26f, .40f, .55f)

/** The accent opacity of a heat [level]; dark palettes need a little more to read on the darker paper. */
internal fun yearHeatAlpha(level: Int, isDark: Boolean): Float =
    (if (isDark) DarkHeatAlpha else LightHeatAlpha)[level.coerceIn(0, YearHeatLevels - 1)]

/** How the twelve month tiles are laid out in a window, and whether a day pane sits beside them. */
internal data class YearGridShape(val columns: Int, val rows: Int, val showPane: Boolean)

/** Narrowest grid that still carries four columns of mini-months. */
internal const val YearFourColumnMinWidthDp = 560

internal fun yearGridShape(widthDp: Int, heightDp: Int): YearGridShape = when {
    shouldSplit(widthDp, heightDp) -> {
        val columns = if (widthDp - SplitPaneWidthDp >= YearFourColumnMinWidthDp) 4 else 3
        YearGridShape(columns, 12 / columns, showPane = true)
    }
    widthDp > heightDp -> YearGridShape(6, 2, showPane = false)
    else -> YearGridShape(3, 4, showPane = false)
}

/** Events on every day of [year], indexed by day of the year minus one. */
internal fun yearDayCounts(index: EventDateIndex, year: Int): IntArray {
    val first = LocalDate.of(year, 1, 1)
    return IntArray(first.lengthOfYear()) { index.eventsOn(first.plusDays(it.toLong())).size }
}

internal fun yearMonthTotal(counts: IntArray, month: YearMonth): Int {
    val start = month.atDay(1).dayOfYear - 1
    return (start until start + month.lengthOfMonth()).sumOf { counts.getOrElse(it) { 0 } }
}

/** The tile's spoken and test label: "May 2026, 12 events". */
internal fun yearTileLabel(month: YearMonth, count: Int, locale: Locale = Locale.getDefault()): String =
    "${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}, " +
        if (count == 1) "1 event" else "$count events"
