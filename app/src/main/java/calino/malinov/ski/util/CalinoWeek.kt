package calino.malinov.ski.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * The day a week begins on. Every grid, strip and pager that has to know where
 * a week starts reads it from here, so one preference moves all of them rather
 * than each renderer carrying its own `with(DayOfWeek.MONDAY)`.
 */
enum class CalinoWeekStart(val label: String, val dayOfWeek: DayOfWeek) {
    Monday("Monday", DayOfWeek.MONDAY),
    Sunday("Sunday", DayOfWeek.SUNDAY),
    ;

    companion object {
        val Default = Monday

        fun fromName(name: String?): CalinoWeekStart =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/** The days washed as weekend, whichever day the week is set to begin on. */
private val WeekendDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private val WeekdayLetterByDay = mapOf(
    DayOfWeek.MONDAY to "M",
    DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "T",
    DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "S",
)

/**
 * The first day of the week containing this date.
 *
 * Deliberately not `with(weekStart.dayOfWeek)`. `LocalDate.with(DayOfWeek)`
 * moves within the *ISO* week, which always runs Monday..Sunday, so it means
 * "previous or same" only when the target is Monday. Ask it for a Sunday and it
 * moves forward instead: Monday 2026-05-18 becomes 2026-05-24, six days later,
 * and a grid built on it would begin a week after the date it is meant to show.
 */
fun LocalDate.startOfWeek(weekStart: CalinoWeekStart): LocalDate =
    with(TemporalAdjusters.previousOrSame(weekStart.dayOfWeek))

/**
 * Which column 0..6 this date occupies in a grid beginning on [weekStart].
 *
 * `floorMod` rather than `%`: under a Sunday start the subtraction is negative
 * for every day but Sunday, and Kotlin's `%` keeps the sign.
 */
fun LocalDate.weekdayColumn(weekStart: CalinoWeekStart): Int =
    Math.floorMod(dayOfWeek.value - weekStart.dayOfWeek.value, 7)

/** The day of the week drawn in [column] of a grid beginning on [weekStart]. */
fun dayOfWeekForColumn(column: Int, weekStart: CalinoWeekStart): DayOfWeek =
    DayOfWeek.of(Math.floorMod(weekStart.dayOfWeek.value - 1 + column, 7) + 1)

/**
 * The column headings, in the order they are drawn.
 *
 * Keyed on the day rather than rotating a fixed list of letters. The letters
 * contain two Ts and two Ss, so a rotation reads correct whether or not it is:
 * this way both the code and its test say which day each column holds.
 */
fun weekdayLetters(weekStart: CalinoWeekStart): List<String> =
    List(7) { column -> WeekdayLetterByDay.getValue(dayOfWeekForColumn(column, weekStart)) }

/**
 * The ISO week number of the grid row beginning on [rowStart].
 *
 * Always ISO 8601, whichever day the week is set to begin on. Under a Sunday
 * start the row's own first day sits in the *previous* ISO week, so the number
 * is taken from the Monday inside the row: one row, one number, and it agrees
 * with what every other calendar prints for those weekdays. For a Monday start
 * the adjuster is a no-op, so one expression covers both.
 */
fun isoWeekNumber(rowStart: LocalDate): Int =
    rowStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        .get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

/**
 * The columns the weekend occupies. Contiguous at the end of the row for a
 * Monday start; split across both edges for a Sunday one, which is why the
 * weekend wash cannot assume a single band.
 */
fun weekendColumns(weekStart: CalinoWeekStart): Set<Int> =
    (0..6).filter { dayOfWeekForColumn(it, weekStart) in WeekendDays }.toSet()

/** The date in the top-left cell of this month's grid. */
fun YearMonth.gridStart(weekStart: CalinoWeekStart): LocalDate =
    atDay(1).startOfWeek(weekStart)

/** How many borrowed cells from the previous month precede the 1st. */
fun YearMonth.leadingCells(weekStart: CalinoWeekStart): Int =
    atDay(1).weekdayColumn(weekStart)

/** What a wash region is washing: the weekend, or days borrowed from a neighbouring month. */
enum class WashKind { Weekend, OutsideMonth }

/**
 * One region of a month grid's structural wash, in column space.
 *
 * [row] is null for a band running the whole height of the grid. The corner
 * flags say where the region meets paper: a corner that touches another region
 * stays square, so the two read as one continuous shape rather than as two
 * pills with a pinch between them.
 */
data class WashRegion(
    val kind: WashKind,
    val columns: IntRange,
    val row: Int?,
    val roundTopLeft: Boolean,
    val roundTopRight: Boolean,
    val roundBottomLeft: Boolean,
    val roundBottomRight: Boolean,
)

/**
 * The month's structural wash, as regions that abut rather than overlap.
 *
 * Two translucent washes stacked on one cell compound into a patch far darker
 * than either, so the weekend columns are subtracted from the borrowed runs
 * rather than drawn over them. This is also what makes a Sunday start work:
 * the weekend is then columns 0 and 6, two separate bands with the leading run
 * squeezed between them, and the same subtraction handles it without the
 * caller knowing.
 *
 * [leadingCells] is how many borrowed cells precede the 1st, [trailingIndex]
 * the flat cell index just past the last day of the month.
 */
fun monthWashPlan(
    weekStart: CalinoWeekStart,
    leadingCells: Int,
    trailingIndex: Int,
    rows: Int,
): List<WashRegion> {
    if (rows <= 0) return emptyList()
    val weekend = weekendColumns(weekStart)
    val lastRow = rows - 1

    val leadingColumns = (0 until leadingCells.coerceIn(0, 7)).toSet()
    val trailingInGrid = trailingIndex in 1 until rows * 7
    val trailingRow = if (trailingInGrid) trailingIndex / 7 else -1
    val trailingColumns = if (trailingInGrid) (trailingIndex % 7..6).toSet() else emptySet()

    val regions = mutableListOf<WashRegion>()

    // The weekend, as one band per contiguous run of weekend columns.
    contiguousRuns(weekend).forEach { run ->
        val meetsLeading = run.first > 0 && (run.first - 1) in leadingColumns
        val meetsLeadingRight = run.last < 6 && (run.last + 1) in leadingColumns
        val trailingTouches = trailingRow == lastRow
        val meetsTrailing = trailingTouches && run.first > 0 && (run.first - 1) in trailingColumns
        val meetsTrailingRight = trailingTouches && run.last < 6 && (run.last + 1) in trailingColumns
        regions += WashRegion(
            kind = WashKind.Weekend,
            columns = run,
            row = null,
            roundTopLeft = !meetsLeading,
            roundTopRight = !meetsLeadingRight,
            roundBottomLeft = !meetsTrailing,
            roundBottomRight = !meetsTrailingRight,
        )
    }

    // The borrowed runs, with the weekend taken out of them.
    fun addBorrowed(columns: Set<Int>, row: Int) {
        contiguousRuns(columns - weekend).forEach { run ->
            val roundLeft = run.first == 0 || (run.first - 1) !in weekend
            val roundRight = run.last == 6 || (run.last + 1) !in weekend
            regions += WashRegion(
                kind = WashKind.OutsideMonth,
                columns = run,
                row = row,
                roundTopLeft = roundLeft,
                roundTopRight = roundRight,
                roundBottomLeft = roundLeft,
                roundBottomRight = roundRight,
            )
        }
    }
    if (leadingColumns.isNotEmpty()) addBorrowed(leadingColumns, 0)
    if (trailingInGrid) addBorrowed(trailingColumns, trailingRow)

    return regions
}

/** Splits a set of columns into maximal contiguous runs, left to right. */
private fun contiguousRuns(columns: Set<Int>): List<IntRange> {
    val sorted = columns.sorted()
    val runs = mutableListOf<IntRange>()
    var start: Int? = null
    var previous: Int? = null
    sorted.forEach { column ->
        if (start == null) {
            start = column
        } else if (previous != null && column != previous!! + 1) {
            runs += start!!..previous!!
            start = column
        }
        previous = column
    }
    if (start != null && previous != null) runs += start!!..previous!!
    return runs
}
