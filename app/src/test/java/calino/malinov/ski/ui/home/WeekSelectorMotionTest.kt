package calino.malinov.ski.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [selectorColumnForDayTravel] is the mapping for the surfaces whose row stays
 * put under the pill: the month grid and the compact month row. The week strip
 * pages a whole week's width during the same gesture and has its own rule --
 * see `WeekStripSelectorTest`.
 */
class WeekSelectorMotionTest {

    @Test fun sundayToMondayTraversesTheMonthRowDirectly() {
        assertColumn(6f, selectedColumn = 6, liveOffset = 0f)
        assertColumn(4.5f, selectedColumn = 6, liveOffset = -.25f)
        assertColumn(3f, selectedColumn = 6, liveOffset = -.5f)
        assertColumn(1.5f, selectedColumn = 6, liveOffset = -.75f)
        assertColumn(0f, selectedColumn = 6, liveOffset = -1f)
    }

    @Test fun mondayToSundayTraversesTheMonthRowDirectly() {
        assertColumn(0f, selectedColumn = 0, liveOffset = 0f)
        assertColumn(1.5f, selectedColumn = 0, liveOffset = .25f)
        assertColumn(3f, selectedColumn = 0, liveOffset = .5f)
        assertColumn(4.5f, selectedColumn = 0, liveOffset = .75f)
        assertColumn(6f, selectedColumn = 0, liveOffset = 1f)
    }

    @Test fun ordinaryAdjacentDayTravelIsUnchanged() {
        assertColumn(2.5f, selectedColumn = 2, liveOffset = -.5f)
        assertColumn(1.5f, selectedColumn = 2, liveOffset = .5f)
    }

    private fun assertColumn(expected: Float, selectedColumn: Int, liveOffset: Float) {
        assertEquals(expected, selectorColumnForDayTravel(selectedColumn, liveOffset), .001f)
    }
}
