package calino.malinov.ski

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.CalinoTestActions.DayPager
import calino.malinov.ski.CalinoTestActions.FixtureDate
import calino.malinov.ski.CalinoTestActions.MonthPager
import calino.malinov.ski.CalinoTestActions.WeekPager
import org.junit.Assert.assertEquals
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

    private fun quickSideSwipeWithUpwardLead(tag: String, direction: Int, lead: Offset) {
        compose.onNodeWithTag(tag).performTouchInput {
            val start = Offset(width * if (direction < 0) .90f else .10f, height * .75f)
            val end = Offset(width * if (direction < 0) .10f else .90f, start.y - 65f)
            down(start)
            // Real sideways swipes can start with a short upward wobble.
            moveTo(start + Offset(lead.x * direction, lead.y), 16)
            for (step in 1..5) {
                val fraction = step / 5f
                moveTo(start + (end - start) * fraction, 16)
            }
            up()
        }
        compose.waitForIdle()
    }

    @Test fun quickWeekSwipeWithUpwardJitterKeepsPaging() {
        quickSideSwipeWithUpwardLead(WeekPager, -1, Offset(6f, -18f))
        compose.assertDaySelected(WeekPager, FixtureDate.plusWeeks(1))
        assertEquals(0, currentZoomLevel())
        compose.onNodeWithTag("agenda-month-list").assertDoesNotExist()
        quickSideSwipeWithUpwardLead(WeekPager, 1, Offset(6f, -18f))
        compose.assertDaySelected(WeekPager, FixtureDate)
        assertEquals(0, currentZoomLevel())
    }

    @Test fun quickWeekSwipeWithDiagonalUpwardLeadKeepsPaging() {
        quickSideSwipeWithUpwardLead(WeekPager, -1, Offset(20f, -50f))
        compose.assertDaySelected(WeekPager, FixtureDate.plusWeeks(1))
        assertEquals(0, currentZoomLevel())
        compose.onNodeWithTag("agenda-month-list").assertDoesNotExist()
        quickSideSwipeWithUpwardLead(WeekPager, 1, Offset(20f, -50f))
        compose.assertDaySelected(WeekPager, FixtureDate)
        assertEquals(0, currentZoomLevel())
    }

    @Test fun quickDaySwipeWithUpwardJitterKeepsPaging() {
        quickSideSwipeWithUpwardLead(DayPager, -1, Offset(6f, -18f))
        compose.assertDaySelected(WeekPager, FixtureDate.plusDays(1))
        assertEquals(0, currentZoomLevel())
        compose.onNodeWithTag("agenda-month-list").assertDoesNotExist()
        quickSideSwipeWithUpwardLead(DayPager, 1, Offset(6f, -18f))
        compose.assertDaySelected(WeekPager, FixtureDate)
        assertEquals(0, currentZoomLevel())
    }

    @Test fun quickWeekThenDaySwipesWithUpwardJitterKeepPaging() {
        quickSideSwipeWithUpwardLead(WeekPager, -1, Offset(6f, -18f))
        compose.assertDaySelected(WeekPager, FixtureDate.plusWeeks(1))
        quickSideSwipeWithUpwardLead(DayPager, -1, Offset(6f, -18f))
        compose.assertDaySelected(WeekPager, FixtureDate.plusWeeks(1).plusDays(1))
        assertEquals(0, currentZoomLevel())
        compose.onNodeWithTag("agenda-month-list").assertDoesNotExist()
    }

    @Test fun cancellingAWeekSideSwipeWithUpwardJitterKeepsTheDate() {
        compose.onNodeWithTag(WeekPager).performTouchInput {
            val start = Offset(width * .90f, height * .75f)
            down(start)
            moveTo(start + Offset(-6f, -18f), 16)
            moveTo(start + Offset(-width * .30f, -50f), 100)
            cancel()
        }
        compose.waitForIdle()
        compose.assertDaySelected(WeekPager, FixtureDate)
        assertEquals(0, currentZoomLevel())
        compose.onNodeWithTag("agenda-month-list").assertDoesNotExist()
    }

    @Test fun weekSwipeAdvancesOneWeekAndKeepsTheWeekday() {
        compose.onNodeWithTag(WeekPager).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.assertDaySelected(WeekPager, FixtureDate.plusWeeks(1))
        compose.onNodeWithContentDescription("Agenda for Monday, May 25").assertIsDisplayed()
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

    @Test fun repeatedSundayMondayDaySwipesKeepTheCommittedSelectionInSync() {
        compose.onNodeWithTag(DayPager).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag(DayPager).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.assertDaySelected(WeekPager, FixtureDate)

        compose.onNodeWithTag(DayPager).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.assertDaySelected(WeekPager, FixtureDate.minusDays(1))

        compose.onNodeWithTag(DayPager).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.assertDaySelected(WeekPager, FixtureDate)
    }

    private companion object {
        /** Well under the pager's snap threshold, so the page springs back. */
        const val CancelledSwipeFraction = .12f
    }
}
