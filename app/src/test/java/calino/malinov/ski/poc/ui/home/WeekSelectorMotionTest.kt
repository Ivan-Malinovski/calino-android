package calino.malinov.ski.poc.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekSelectorMotionTest {

    @Test fun sundayToMondayFollowsPagerDirectlyAcrossTheRow() {
        assertColumn(6f, selectedColumn = 6, liveOffset = 0f)
        assertColumn(4.5f, selectedColumn = 6, liveOffset = -.25f)
        assertColumn(3f, selectedColumn = 6, liveOffset = -.5f)
        assertColumn(1.5f, selectedColumn = 6, liveOffset = -.75f)
        assertColumn(0f, selectedColumn = 6, liveOffset = -1f)
    }

    @Test fun mondayToSundayFollowsPagerDirectlyAcrossTheRow() {
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
