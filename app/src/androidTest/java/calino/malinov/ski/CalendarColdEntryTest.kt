package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.CalinoTestActions.FixtureDate
import calino.malinov.ski.CalinoTestActions.MonthPager
import calino.malinov.ski.CalinoTestActions.WeekPager
import calino.malinov.ski.util.CalinoDefaultView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** The initial composition must already match the stored zoom endpoint. */
@RunWith(AndroidJUnit4::class)
class CalendarColdSplitEntryTest : CalinoUiTest(CalinoDefaultView.Day) {
    @Test fun splitMonthOwnsItsFirstIdleFrame() {
        assertEquals(1, currentZoomLevel())
        assertFalse("week strip mounted on cold split entry", compose.exists(hasTestTag(WeekPager)))
        compose.dayCellIn(MonthPager, FixtureDate).assertIsDisplayed()
        compose.assertDaySelected(MonthPager, FixtureDate)
    }
}

@RunWith(AndroidJUnit4::class)
class CalendarColdDetailedEntryTest : CalinoUiTest(CalinoDefaultView.Month) {
    @Test fun detailedMonthOwnsItsFirstIdleFrame() {
        assertEquals(2, currentZoomLevel())
        assertFalse("week strip mounted on cold detailed entry", compose.exists(hasTestTag(WeekPager)))
        compose.dayCellIn(MonthPager, FixtureDate).assertIsDisplayed()
        compose.assertDaySelected(MonthPager, FixtureDate)
    }
}

@RunWith(AndroidJUnit4::class)
class AgendaColdEntryTest : CalinoUiTest(CalinoDefaultView.Agenda) {
    @Test fun savedAgendaOpensOnAgenda() {
        compose.onNodeWithContentDescription("Agenda for Monday, May 18").assertIsDisplayed()
    }
}
