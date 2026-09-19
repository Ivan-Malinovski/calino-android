package calino.malinov.ski.qa

import calino.malinov.ski.ui.home.monthCellChipCapacity
import calino.malinov.ski.ui.home.monthCellMarkerCap
import calino.malinov.ski.ui.home.monthCellOverflowChipCapacity
import calino.malinov.ski.ui.home.monthCellShownCount
import calino.malinov.ski.util.CalinoEventDensity
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
    private val overflowHeight = 14f
    private val denseCap = CalinoEventDensity.Dense.maxItems

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
    fun `a caller with one number still pays a whole slot for the count`() {
        // The morph path measures cards and nothing else, so its "+n" can only
        // come out of a card slot.
        assertEquals(2, monthCellShownCount(4, 3))
        assertEquals(2, monthCellShownCount(40, 3))
        assertEquals(0, monthCellShownCount(2, 1))
        assertEquals(0, monthCellShownCount(2, 0))
    }

    @Test
    fun `the count line is charged at its own height, not a card's`() {
        // A cell that holds two cards and 16dp of slack: the "+n" fits in the
        // slack, so both cards stay and only the third event rolls up. This is
        // the case that used to read "one card, +2" with the cell half empty.
        val area = 58f
        val chips = capacity(area)
        val withCount = monthCellOverflowChipCapacity(area, chipHeight, chipGap, overflowHeight, denseCap)
        assertEquals(2, chips)
        assertEquals(2, withCount)
        assertEquals(2, monthCellShownCount(3, chips, withCount))
        assertEquals(2, monthCellShownCount(9, chips, withCount))
    }

    @Test
    fun `a card goes when the line genuinely does not fit beside it`() {
        // 46dp holds two cards with only 4dp left: the line cannot sit under
        // them, so it does take a card's place.
        val area = 46f
        assertEquals(2, capacity(area))
        assertEquals(1, monthCellOverflowChipCapacity(area, chipHeight, chipGap, overflowHeight, denseCap))
    }

    @Test
    fun `a rolled-up day never hides everything it counts`() {
        // The line must have something left to count: shown is capped below
        // the event total however little room the cell has.
        assertEquals(2, monthCellShownCount(3, 2, 5))
        assertEquals(0, monthCellShownCount(1, 0, 4))
        assertEquals(0, monthCellOverflowChipCapacity(20f, chipHeight, chipGap, overflowHeight, denseCap))
    }

    @Test
    fun `the density setting caps the cards beside a count line too`() {
        assertEquals(2, monthCellOverflowChipCapacity(200f, chipHeight, chipGap, overflowHeight, CalinoEventDensity.Quiet.maxItems))
        assertEquals(4, monthCellOverflowChipCapacity(200f, chipHeight, chipGap, overflowHeight, CalinoEventDensity.Balanced.maxItems))
    }
}
