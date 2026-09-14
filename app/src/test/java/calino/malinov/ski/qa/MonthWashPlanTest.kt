package calino.malinov.ski.qa

import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.WashKind
import calino.malinov.ski.util.WashRegion
import calino.malinov.ski.util.gridStart
import calino.malinov.ski.util.leadingCells
import calino.malinov.ski.util.monthWashPlan
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weekend wash used to be one hardcoded band over the last two columns,
 * with two booleans deciding four corner radii. A Sunday start splits the
 * weekend across both edges of the grid, so the topology is computed now.
 *
 * The rule these tests pin down: regions abut rather than overlap, because two
 * translucent washes on one cell compound into a patch far darker than either;
 * and a corner is rounded only where the region meets paper.
 */
class MonthWashPlanTest {

    private fun plan(
        weekStart: CalinoWeekStart,
        leading: Int,
        trailing: Int,
        rows: Int,
    ) = monthWashPlan(weekStart, leading, trailing, rows)

    private fun weekendBands(regions: List<WashRegion>) = regions.filter { it.kind == WashKind.Weekend }
    private fun borrowed(regions: List<WashRegion>) = regions.filter { it.kind == WashKind.OutsideMonth }

    @Test
    fun `a Monday start keeps the weekend as one band`() {
        val bands = weekendBands(plan(CalinoWeekStart.Monday, leading = 3, trailing = 34, rows = 5))
        assertEquals(1, bands.size)
        assertEquals(5..6, bands.single().columns)
        assertEquals(null, bands.single().row)
    }

    @Test
    fun `a Sunday start splits the weekend across both edges`() {
        val bands = weekendBands(plan(CalinoWeekStart.Sunday, leading = 3, trailing = 34, rows = 5))
        assertEquals(listOf(0..0, 6..6), bands.map { it.columns })
    }

    @Test
    fun `a borrowed run that reaches the band squares the corners between them`() {
        // Monday start, six borrowed cells: the run ends at column 4, right
        // against the band. This is the old `leadingMeetsBand` case.
        val regions = plan(CalinoWeekStart.Monday, leading = 6, trailing = 37, rows = 6)
        val band = weekendBands(regions).single()
        val leading = borrowed(regions).single { it.row == 0 }
        assertEquals(0..4, leading.columns)
        assertTrue("the run's outer corner meets paper", leading.roundTopLeft)
        assertTrue("the run's inner corner meets the band", !leading.roundTopRight)
        assertTrue("the band's top-left meets the run", !band.roundTopLeft)
        assertTrue("the band's top-right meets paper", band.roundTopRight)
    }

    @Test
    fun `a borrowed run short of the band leaves both corners round`() {
        val regions = plan(CalinoWeekStart.Monday, leading = 3, trailing = 34, rows = 5)
        val band = weekendBands(regions).single()
        val leading = borrowed(regions).single { it.row == 0 }
        assertEquals(0..2, leading.columns)
        assertTrue(leading.roundTopRight)
        assertTrue(band.roundTopLeft)
    }

    @Test
    fun `a month ending on the last column has no trailing run`() {
        // trailing lands exactly on the grid end, so there is nothing to wash.
        val regions = plan(CalinoWeekStart.Monday, leading = 0, trailing = 35, rows = 5)
        assertEquals(emptyList<WashRegion>(), borrowed(regions).filter { it.row == 4 })
        assertTrue(weekendBands(regions).single().roundBottomLeft)
    }

    @Test
    fun `a Sunday start clips the borrowed run out of the weekend columns`() {
        // Columns 0..2 are borrowed, but column 0 is Sunday and belongs to the
        // band. Washing both would double-darken that cell.
        val regions = plan(CalinoWeekStart.Sunday, leading = 3, trailing = 34, rows = 5)
        val leading = borrowed(regions).single { it.row == 0 }
        assertEquals(1..2, leading.columns)
        assertTrue("its left corner meets the Sunday band", !leading.roundTopLeft)
        val sundayBand = weekendBands(regions).first { it.columns == 0..0 }
        assertTrue("the band's top-right meets the run", !sundayBand.roundTopRight)
    }

    @Test
    fun `a Sunday start with one borrowed cell drops the run entirely`() {
        val regions = plan(CalinoWeekStart.Sunday, leading = 1, trailing = 32, rows = 5)
        assertEquals(emptyList<WashRegion>(), borrowed(regions).filter { it.row == 0 })
        val sundayBand = weekendBands(regions).first { it.columns == 0..0 }
        assertTrue(sundayBand.roundTopRight)
    }

    @Test
    fun `no two regions ever cover the same cell`() {
        // The compounding bug, swept across every real month and both settings.
        CalinoWeekStart.entries.forEach { weekStart ->
            (1..12).forEach { monthValue ->
                val month = YearMonth.of(2026, monthValue)
                val leading = month.leadingCells(weekStart)
                val rows = ((month.atEndOfMonth().toEpochDay() -
                    month.gridStart(weekStart).toEpochDay()) / 7 + 1).toInt()
                val regions = monthWashPlan(weekStart, leading, leading + month.lengthOfMonth(), rows)
                val covered = mutableSetOf<Pair<Int, Int>>()
                regions.forEach { region ->
                    val rowsCovered = region.row?.let { listOf(it) } ?: (0 until rows).toList()
                    rowsCovered.forEach { row ->
                        region.columns.forEach { column ->
                            val cell = row to column
                            assertTrue(
                                "$weekStart $month washes $cell twice",
                                covered.add(cell),
                            )
                        }
                    }
                }
            }
        }
    }
}
