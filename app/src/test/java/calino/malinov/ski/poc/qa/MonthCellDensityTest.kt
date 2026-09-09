package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.ui.home.monthCellChipCapacity
import calino.malinov.ski.poc.ui.home.monthCellShownCount
import org.junit.Assert.assertEquals
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
    fun `overrunning days give the last slot to the count`() {
        assertEquals(2, monthCellShownCount(4, 3))
        assertEquals(2, monthCellShownCount(40, 3))
        assertEquals(0, monthCellShownCount(2, 1))
        assertEquals(0, monthCellShownCount(2, 0))
    }
}
