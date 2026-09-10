package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.ui.home.monthCellChipCapacity
import calino.malinov.ski.poc.ui.home.monthCellMarkerCap
import calino.malinov.ski.poc.ui.home.monthCellShownCount
import calino.malinov.ski.poc.util.CalinoEventDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expanded month grid used to roll every third event into a "+1" no matter
 * how tall the cell was. Cards are now counted against the space that is
 * actually there, and the "+n" line only appears once they stop fitting.
 */
class MonthCellDensityTest {

    // The metrics the grid draws with: 20dp cards, 2dp apart, at 1x density.
    private val chipHeight = 20f
    private val chipGap = 2f

    private fun capacity(areaHeight: Float) = monthCellChipCapacity(areaHeight, chipHeight, chipGap)

    @Test
    fun `a card needs no trailing gap`() {
        // Three cards occupy 3*20 + 2*2 = 64, not 3*22 = 66.
        assertEquals(3, capacity(64f))
        assertEquals(2, capacity(63f))
    }

    @Test
    fun `a tall cell holds more than two cards`() {
        // A full-height phone row leaves roughly 83dp under the date.
        assertEquals(3, capacity(83f))
        assertEquals(9, capacity(200f))
    }

    @Test
    fun `a cell with no room holds nothing`() {
        assertEquals(0, capacity(0f))
        assertEquals(0, capacity(19f))
        assertEquals(0, monthCellChipCapacity(100f, 0f, 0f))
    }

    @Test
    fun `days that fit are never rolled up`() {
        assertEquals(0, monthCellShownCount(0, 3))
        assertEquals(3, monthCellShownCount(3, 3))
    }

    @Test
    fun `the density setting only ever lowers what fits`() {
        // Dense is "everything the cell can hold", not "draw past the bottom".
        assertEquals(3, monthCellChipCapacity(83f, chipHeight, chipGap, CalinoEventDensity.Dense.maxItems))
        assertEquals(2, monthCellChipCapacity(83f, chipHeight, chipGap, CalinoEventDensity.Quiet.maxItems))
        assertEquals(3, monthCellChipCapacity(83f, chipHeight, chipGap, CalinoEventDensity.Balanced.maxItems))
        // A tablet-height cell is where Balanced starts to bite.
        assertEquals(4, monthCellChipCapacity(200f, chipHeight, chipGap, CalinoEventDensity.Balanced.maxItems))
    }

    @Test
    fun `a renderer's own limit wins over a denser setting`() {
        assertEquals(2, monthCellMarkerCap(CalinoEventDensity.Quiet, 4))
        assertEquals(3, monthCellMarkerCap(CalinoEventDensity.Dense, 3))
        assertEquals(4, monthCellMarkerCap(CalinoEventDensity.Balanced, 4))
    }

    @Test
    fun `every renderer counts its overflow the same way`() {
        // The invariant behind "+n": it is always what did not fit, never a
        // literal, so the chip path and the morph path cannot disagree.
        val events = 7
        listOf(2, 3, 4).forEach { cap ->
            val shown = monthCellShownCount(events, cap)
            assertEquals(events - shown, events - monthCellShownCount(events, cap))
            assertTrue(shown < cap || events <= cap)
        }
    }

    @Test
    fun `overrunning days give the last slot to the count`() {
        assertEquals(2, monthCellShownCount(4, 3))
        assertEquals(2, monthCellShownCount(40, 3))
        assertEquals(0, monthCellShownCount(2, 1))
        assertEquals(0, monthCellShownCount(2, 0))
    }
}
