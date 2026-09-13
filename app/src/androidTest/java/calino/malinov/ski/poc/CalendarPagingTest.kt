package calino.malinov.ski.poc

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.poc.CalinoTestActions.DayPager
import calino.malinov.ski.poc.CalinoTestActions.FixtureDate
import calino.malinov.ski.poc.CalinoTestActions.MonthPager
import calino.malinov.ski.poc.CalinoTestActions.WeekPager
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Day/week/month paging and cancellation.
 *
 * These must be real touch gestures. Every settle collector in `HomeScreen` is
 * gated on `isUserSettle`, which is fed only by a `DragInteraction.Start` on the
 * pager's interaction source -- a programmatic `scrollToPage` moves the pager
 * without ever committing a date, so a test driving it that way would pass
 * while proving nothing.
 */
@RunWith(AndroidJUnit4::class)
class CalendarPagingTest : CalinoUiTest() {

    @Test fun monthSwipeAdvancesTheCommittedMonth() {
        zoomTo(2)
        compose.onNodeWithTag(MonthPager).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        // The settle preserves the day of month: 18 May -> 18 June.
        compose.assertDaySelected(MonthPager, FixtureDate.plusMonths(1))
    }

    @Test fun monthSwipeBackReturnsToTheStartingMonth() {
        zoomTo(2)
        compose.onNodeWithTag(MonthPager).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag(MonthPager).performTouchInput { swipeRight() }
        compose.waitForIdle()

        compose.assertDaySelected(MonthPager, FixtureDate)
    }

    /**
     * A drag too short to pass the pager's snap threshold settles back on the
     * page it started on, so nothing is committed. This is the case the app's
     * own comment calls out: "a cancelled tap/drag can return to the current
     * week without changing settledPage".
     */
    @Test fun cancelledMonthSwipeLeavesTheDateAlone() {
        zoomTo(2)
        compose.onNodeWithTag(MonthPager).performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - (width * CancelledSwipeFraction))
        }
        compose.waitForIdle()

        compose.assertDaySelected(MonthPager, FixtureDate)
    }

    @Test fun weekSwipeAdvancesOneWeekAndKeepsTheWeekday() {
        compose.onNodeWithTag(WeekPager).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.assertDaySelected(WeekPager, FixtureDate.plusWeeks(1))
    }

    @Test fun cancelledWeekSwipeLeavesTheDateAlone() {
        compose.onNodeWithTag(WeekPager).performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - (width * CancelledSwipeFraction))
        }
        compose.waitForIdle()

        compose.assertDaySelected(WeekPager, FixtureDate)
    }

    @Test fun daySwipeAdvancesOneDay() {
        compose.onNodeWithTag(DayPager).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.assertDaySelected(WeekPager, FixtureDate.plusDays(1))
    }

    @Test fun mondayToSundayDaySwipeCrossesTheWeekBoundary() {
        compose.onNodeWithTag(DayPager).performTouchInput { swipeRight() }
        compose.waitForIdle()

        compose.assertDaySelected(WeekPager, FixtureDate.minusDays(1))
    }

    @Test fun sundayToMondayDaySwipeCrossesTheWeekBoundary() {
        compose.onNodeWithTag(DayPager).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag(DayPager).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.assertDaySelected(WeekPager, FixtureDate)
    }

    private companion object {
        /** Well under the pager's snap threshold, so the page springs back. */
        const val CancelledSwipeFraction = .12f
    }
}
