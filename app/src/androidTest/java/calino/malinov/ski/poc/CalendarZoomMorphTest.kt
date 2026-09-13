package calino.malinov.ski.poc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.poc.CalinoTestActions.FixtureDate
import calino.malinov.ski.poc.CalinoTestActions.MonthPager
import calino.malinov.ski.poc.CalinoTestActions.WeekPager
import calino.malinov.ski.poc.CalinoTestActions.zoomHandleLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Month-to-week morph target selection.
 *
 * The zoom handle is used rather than the vertical drag on purpose: the drag
 * settles on a velocity-dependent level, which is not something a test should
 * assert on. The drag's *outcome space* is the same three levels, and those are
 * what these tests pin.
 */
@RunWith(AndroidJUnit4::class)
class CalendarZoomMorphTest : CalinoUiTest() {

    @Test fun opensOnTheWeekLevel() {
        // CalinoDefaultView.Default is Week, which is zoom 0 -- level 1 of 3.
        assertEquals(0, currentZoomLevel())
    }

    @Test fun handleStepsUpThroughEveryLevel() {
        assertEquals(0, currentZoomLevel())
        compose.onNodeWithContentDescription(zoomHandleLabel(0)).performClick()
        compose.waitForIdle()
        assertEquals(1, currentZoomLevel())
        compose.onNodeWithContentDescription(zoomHandleLabel(1)).performClick()
        compose.waitForIdle()
        assertEquals(2, currentZoomLevel())
    }

    /** From the top the handle wraps back to the middle rather than to the week strip. */
    @Test fun handleWrapsFromTheMonthLevel() {
        zoomTo(2)
        compose.onNodeWithContentDescription(zoomHandleLabel(2)).performClick()
        compose.waitForIdle()

        assertEquals(1, currentZoomLevel())
    }

    /**
     * The week strip is unmounted above the blend threshold and the month grid
     * takes the lane. Both being mounted at once would mean two grids competing
     * for the same date semantics.
     */
    @Test fun weekStripIsMountedOnlyAtTheWeekLevel() {
        assertTrue("week strip missing at zoom 0", compose.exists(hasTestTag(WeekPager)))
        zoomTo(2)
        assertFalse("week strip still mounted at zoom 2", compose.exists(hasTestTag(WeekPager)))
    }

    @Test fun monthGridBecomesInteractiveWhenZoomedIn() {
        zoomTo(2)
        compose.dayCellIn(MonthPager, FixtureDate.plusDays(3)).assertIsDisplayed()
    }

    @Test fun splitMonthKeepsTheSelectedDateAndOwnsTheCalendarSurface() {
        zoomTo(1)

        assertFalse("week strip still mounted at zoom 1", compose.exists(hasTestTag(WeekPager)))
        compose.dayCellIn(MonthPager, FixtureDate).assertIsDisplayed()
        compose.assertDaySelected(MonthPager, FixtureDate)
    }

    /**
     * The morph target is the week containing the committed date, not the week
     * that happened to be on screen. Selecting a day two weeks out on the month
     * grid and collapsing must land on *that* week.
     */
    @Test fun morphTargetIsTheWeekOfTheSelectedDate() {
        zoomTo(2)
        // Two weeks back, so the target week is unambiguously not the one the
        // week strip was showing before the zoom.
        val otherWeek = FixtureDate.minusDays(14)
        compose.selectDayIn(MonthPager, otherWeek)
        compose.waitForIdle()

        zoomTo(0)

        compose.assertDaySelected(WeekPager, otherWeek)
        // ...and the whole week came with it, not just the one cell.
        compose.dayCellIn(WeekPager, otherWeek.plusDays(2)).assertIsDisplayed()
        compose.dayCellIn(WeekPager, otherWeek.plusDays(6)).assertIsDisplayed()
    }
}
