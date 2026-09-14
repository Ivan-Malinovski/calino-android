package calino.malinov.ski

import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.CalinoTestActions.FixtureDate
import calino.malinov.ski.CalinoTestActions.MonthPager
import calino.malinov.ski.CalinoTestActions.WeekPager
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Month cell and week-day date selection.
 *
 * The committed date is read from the day cell's own `", selected"` suffix,
 * never from the month heading -- the heading renders the pager's *preview*
 * month and mounts three of them at once.
 */
@RunWith(AndroidJUnit4::class)
class CalendarDateSelectionTest : CalinoUiTest() {

    @Test fun opensOnTheFixtureDate() {
        compose.assertDaySelected(WeekPager, FixtureDate)
    }

    @Test fun weekStripDayTapCommitsSelection() {
        val wednesday = FixtureDate.plusDays(2)
        compose.selectDayIn(WeekPager, wednesday)
        compose.waitForIdle()

        compose.assertDaySelected(WeekPager, wednesday)
        compose.assertDayNotSelected(WeekPager, FixtureDate)
    }

    @Test fun monthCellTapCommitsSelection() {
        zoomTo(2)
        val thursday = FixtureDate.plusDays(3)
        compose.selectDayIn(MonthPager, thursday)
        compose.waitForIdle()

        compose.assertDaySelected(MonthPager, thursday)
        compose.assertDayNotSelected(MonthPager, FixtureDate)
    }

    /**
     * The May grid runs from Monday 27 April, so its leading cells belong to the
     * previous month. Selecting one has to commit the previous month too, not
     * just the day.
     */
    @Test fun leadingDayFromPreviousMonthIsSelectable() {
        zoomTo(2)
        val april28 = FixtureDate.minusDays(20)
        compose.selectDayIn(MonthPager, april28)
        compose.waitForIdle()

        compose.assertDaySelected(MonthPager, april28)
    }

    /** A selection made on the month grid survives the collapse to the week strip. */
    @Test fun selectionSurvivesTheMorphToWeek() {
        zoomTo(2)
        val thursday = FixtureDate.plusDays(3)
        compose.selectDayIn(MonthPager, thursday)
        compose.waitForIdle()
        zoomTo(0)

        compose.assertDaySelected(WeekPager, thursday)
    }
}
