package calino.malinov.ski

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeRight
import java.time.LocalDate
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.model.NewEvent
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.CalinoTestActions.DayPanePager
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Month and Year share the landscape day pane and commit dates only after paging settles. */
@RunWith(AndroidJUnit4::class)
class SplitDayPanePagingTest : CalinoUiTest() {

    @Test
    fun dayPaneSwipeAdvancesItsSharedDayPager() {
        landscape()

        compose.onNodeWithTag(DayPanePager).assertIsDisplayed()
        compose.onNodeWithContentDescription("Day sidebar page May 18, 2026", substring = true)
            .assertIsDisplayed()

        compose.onNodeWithTag(DayPanePager).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Day sidebar page May 19, 2026", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun yearDayPaneSwipeCommitsTheDayAndMonthUsesTheSameSelection() {
        landscape()
        compose.openRoute("Year")
        compose.onNodeWithTag(DayPanePager).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertPaneDay("May 19, 2026")
        awaitDescribed("Add on Tue, 19 May. Swipe up for views")

        compose.openRoute("Month")
        assertPaneDay("May 19, 2026")
        compose.assertDaySelected(CalinoTestActions.MonthPager, LocalDate.of(2026, 5, 19))
    }

    @Test
    fun yearDayPaneSlowDragPreviewsBeforeCommitting() {
        landscape()
        compose.openRoute("Year")
        compose.onNodeWithTag(DayPanePager).performTouchInput {
            down(percentOffset(.85f, .65f))
            moveTo(percentOffset(.20f, .65f), 1_000)
        }
        awaitDescribed("Add on Mon, 18 May. Swipe up for views")
        compose.onNodeWithTag(DayPanePager).performTouchInput { advanceEventTime(200); up() }
        compose.waitForIdle()
        assertPaneDay("May 19, 2026")
        awaitDescribed("Add on Tue, 19 May. Swipe up for views")
    }

    @Test
    fun cancellingAShortYearDayDragKeepsTheCommittedDay() {
        landscape()
        compose.openRoute("Year")
        compose.onNodeWithTag(DayPanePager).performTouchInput {
            down(percentOffset(.75f, .65f))
            moveTo(percentOffset(.60f, .65f), 600)
            cancel()
        }
        compose.waitForIdle()
        assertPaneDay("May 18, 2026")
        awaitDescribed("Add on Mon, 18 May. Swipe up for views")
    }

    @Test
    fun yearStepSyncsTheDayPaneAndASwipeKeepsThatYear() {
        landscape()
        compose.openRoute("Year")
        compose.onNodeWithContentDescription("Next year").performClick()
        compose.waitForIdle()
        assertPaneDay("May 18, 2027")
        compose.onNodeWithTag(DayPanePager).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertPaneDay("May 17, 2027")
        awaitDescribed("Add on Mon, 17 May. Swipe up for views")
    }

    @Test
    fun yearDayPaneCollapsesAndReopensWithItsSelection() {
        landscape()
        compose.openRoute("Year")
        compose.onNodeWithContentDescription("Hide day pane").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(DayPanePager).assertDoesNotExist()
        compose.onNodeWithContentDescription("Show day pane").performClick()
        compose.waitForIdle()
        assertPaneDay("May 18, 2026")
        compose.onNodeWithTag(DayPanePager).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertPaneDay("May 19, 2026")
    }

    @Test
    fun yearDaySwipeAcrossNewYearUpdatesTheYearGrid() {
        landscape()
        repeat(7) {
            compose.onNodeWithContentDescription("Next month").performClick()
            compose.waitForIdle()
        }
        // December's last day is also composed in January's leading cells.
        val cells = compose.onAllNodes(
            hasAnyAncestor(hasTestTag(CalinoTestActions.MonthPager)) and
                hasContentDescription("Thursday, December 31", substring = true),
        )
        val visible = cells.fetchSemanticsNodes().indices.single { cells[it].isDisplayed() }
        cells[visible].performTouchInput { click(percentOffset(.5f, .12f)) }
        compose.waitForIdle()
        compose.openRoute("Year")
        assertPaneDay("Dec 31, 2026")
        compose.onNodeWithTag(DayPanePager).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertPaneDay("Jan 1, 2027")
        compose.waitUntil(5_000) { compose.hasTextNode("2027") && !compose.hasTextNode("2026") }
        compose.onNodeWithText("2027").assertIsDisplayed()
        awaitDescribed("Add on Fri, 1 Jan. Swipe up for views")
    }

    @Test
    fun monthYearRouteFramesKeepOnePaneAtItsRestingBounds() {
        landscape()
        val original = compose.onNodeWithTag(DayPanePager).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views")
            .performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(64)
        compose.onAllNodesWithTag(DayPanePager).assertCountEquals(1)
        assertEquals(original, compose.onNodeWithTag(DayPanePager).fetchSemanticsNode().boundsInRoot)
        assertPaneDay("May 18, 2026")
        compose.mainClock.autoAdvance = true
        awaitDescribed("Next year")

        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views")
            .performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(64)
        compose.onAllNodesWithTag(DayPanePager).assertCountEquals(1)
        assertEquals(original, compose.onNodeWithTag(DayPanePager).fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag(CalinoTestActions.MonthPager).assertIsDisplayed()
    }

    @Test
    fun sidebarScrollPositionSurvivesBothRouteChanges() = runBlocking {
        val repository = CalinoContainer.get(ApplicationProvider.getApplicationContext()).fixtureRepository
        repeat(30) { index ->
            repository.addEvent(NewEvent(
                title = "Sidebar scroll sample $index",
                date = CalinoTestActions.FixtureDate,
                startTime = LocalTime.of(8, 0).plusMinutes(index * 20L),
                durationMinutes = 15,
            ))
        }
        landscape()
        compose.onNodeWithTag(DayPanePager).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val scroll = sidebarScrollPosition()
        assertTrue("The sidebar must actually scroll before testing retention", scroll > 0f)
        compose.openRoute("Year")
        compose.waitForIdle()
        assertEquals(scroll, sidebarScrollPosition(), .5f)
        compose.openRoute("Month")
        compose.waitForIdle()
        assertEquals(scroll, sidebarScrollPosition(), .5f)
    }

    @Test
    fun collapsedSidebarStaysCollapsedAcrossMonthAndYear() {
        landscape()
        compose.onNodeWithContentDescription("Hide day pane").performClick()
        compose.waitForIdle()
        compose.openRoute("Year")
        compose.waitForIdle()
        compose.onNodeWithTag(DayPanePager).assertDoesNotExist()
        compose.onNodeWithContentDescription("Show day pane").assertIsDisplayed()
        compose.openRoute("Month")
        compose.waitForIdle()
        compose.onNodeWithTag(DayPanePager).assertDoesNotExist()
        compose.onNodeWithContentDescription("Show day pane").performClick()
        compose.waitForIdle()
        assertPaneDay("May 18, 2026")
    }

    @Test
    fun openingTheSelectedMonthFromYearAndBackKeepsTheSidebarDay() {
        landscape()
        compose.openRoute("Year")
        compose.onNodeWithTag(DayPanePager).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertPaneDay("May 19, 2026")
        compose.onNodeWithContentDescription("May 2026, ", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CalinoTestActions.MonthPager).assertIsDisplayed()
        assertPaneDay("May 19, 2026")
        Espresso.pressBack()
        awaitDescribed("Previous year")
        assertPaneDay("May 19, 2026")
    }

    @Test
    fun sharedSidebarEventDetailsReturnToYear() {
        landscape()
        compose.openRoute("Year")
        compose.onNode(
            hasAnyAncestor(hasTestTag(DayPanePager)) and hasText("Design review"),
        ).performClick()
        awaitDescribed("Open event")
        Espresso.pressBack()
        awaitDescribed("Previous year")
        assertPaneDay("May 18, 2026")
    }

    private fun sidebarScrollPosition(): Float = compose
        .onNodeWithContentDescription("Day sidebar page May 18, 2026", substring = true)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun landscape() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
    }

    private fun assertPaneDay(date: String) {
        compose.onNodeWithContentDescription("Day sidebar page $date", substring = true).assertIsDisplayed()
    }

    @After
    fun restorePortraitOrientation() {
        compose.mainClock.autoAdvance = true
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
