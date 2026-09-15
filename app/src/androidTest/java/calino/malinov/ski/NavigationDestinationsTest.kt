package calino.malinov.ski

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation destinations and their accessibility bounds.
 *
 * `AGENTS.md` calls this "dock indicator destinations"; the app has no dock --
 * navigation is the sidebar, and these are its rows. That wording predates the
 * sidebar and is corrected alongside this suite.
 *
 * Reaching a destination is asserted through the sidebar's own selection state
 * rather than through a landmark string per surface: the row reporting itself
 * selected *is* the app's answer to "where am I", and it is the same check for
 * every route.
 */
@RunWith(AndroidJUnit4::class)
class NavigationDestinationsTest : CalinoUiTest() {

    @Test fun everyRootDestinationIsReachable() {
        // Journal and Contacts need no preference first: the fixture data
        // contains records, and `featureAvailabilityAfter` turns those surfaces
        // on once records exist.
        Destinations.forEach { label ->
            compose.openRoute(label)
            compose.waitForIdle()

            compose.onNodeWithContentDescription("Open navigation").performClick()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("$label, selected").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Dismiss").performClick()
            compose.waitForIdle()
        }
    }

    @Test fun theCalendarRootIsSelectedOnLaunch() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Month, selected").assertIsDisplayed()
    }

    /** The surfaces whose landmarks are worth pinning by content, not just by route. */
    @Test fun destinationsShowTheirOwnContent() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open task: Buy flowers").assertIsDisplayed()

        compose.openRoute("Journal")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open journal entry A clear Monday").assertIsDisplayed()

        compose.openRoute("Agenda")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add on May 18, 2026").assertIsDisplayed()
    }

    /**
     * Every navigation row keeps the 44dp touch lane the UI requirements ask
     * for, even where the painted row is more compact.
     */
    @Test fun navigationRowsKeepTheMinimumTouchLane() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()

        Destinations.forEach { label ->
            compose.onNodeWithContentDescription(label)
                .performScrollTo()
                .assertHeightIsAtLeast(MinimumTouchLane)
        }
    }

    /** The header controls are held to the same lane. */
    @Test fun headerControlsKeepTheMinimumTouchLane() {
        compose.onNodeWithContentDescription("Open navigation")
            .assertHeightIsAtLeast(MinimumTouchLane)
            .assertWidthIsAtLeast(MinimumTouchLane)
        compose.onNodeWithContentDescription(CalinoTestActions.zoomHandleLabel(0))
            .assertHeightIsAtLeast(MinimumTouchLane)
    }

    @Test fun miniCalendarControlsKeepTheMinimumTouchLane() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Calendar in sidebar").performClick()
        compose.waitForIdle()

        listOf("Previous month in sidebar", "Next month in sidebar", "Go to today in sidebar").forEach { description ->
            compose.onNodeWithContentDescription(description)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(MinimumTouchLane)
        }
        compose.onNodeWithContentDescription("Monday, May 18, 2026")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(MinimumTouchLane)
    }

    @Test fun miniCalendarNavigatesMonthsAndReturnsToToday() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Calendar in sidebar").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Previous month in sidebar").performClick()
        compose.onNodeWithText("April 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month in sidebar").performClick()
        compose.onNodeWithText("May 2026").assertIsDisplayed()

        compose.onNodeWithContentDescription("Go to today in sidebar").performClick()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Monday, September 14, 2026").assertIsDisplayed()
    }

    @Test fun miniCalendarDefaultsCollapsedAndRemembersExpansion() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Calendar in sidebar")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(MinimumTouchLane)
        assertFalse(compose.hasDescribedNode("Previous month in sidebar"))

        compose.onNodeWithContentDescription("Calendar in sidebar").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Previous month in sidebar").assertIsDisplayed()

        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Previous month in sidebar").assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Previous month in sidebar").assertIsDisplayed()

        compose.onNodeWithContentDescription("Collapse calendar in sidebar").performClick()
        compose.waitForIdle()
        assertFalse(compose.hasDescribedNode("Previous month in sidebar"))
    }

    @Test fun returningToTheCalendarRestoresIt() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.openRoute("Month")
        compose.waitForIdle()

        assertTrue(compose.exists(hasContentDescription(CalinoTestActions.zoomHandleLabel(0))))
        compose.assertDaySelected(CalinoTestActions.WeekPager, CalinoTestActions.FixtureDate)
    }

    @Test fun openingAnUpcomingTaskDismissesTheSidebar() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Upcoming tasks in sidebar").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Buy flowers, Personal")
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Save task").assertIsDisplayed()
        assertFalse(compose.hasDescribedNode("Dismiss"))
    }

    @Test fun calendarManagementMovesFromTheSidebarToSettings() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()

        assertFalse("Calendars should not be a navigation row", compose.hasDescribedNode("Calendars"))
        compose.onNodeWithContentDescription("Show Personal")
            .performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithContentDescription("Dismiss").performClick()
        // Let the sidebar's AnimatedVisibility exit finish before asking the
        // same host to open it again for the Settings navigation click.
        compose.waitForIdle()
        compose.openRoute("Settings")
        compose.waitForIdle()
        compose.onNodeWithTag("Settings section rail")
            .performScrollToIndex(SettingsSyncSectionIndex)
        compose.onNodeWithContentDescription("Sync settings").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open calendars and accounts")
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Calendars").assertIsDisplayed()
    }

    private companion object {
        val MinimumTouchLane = 44.dp
        const val SettingsSyncSectionIndex = 6

        val Destinations = listOf("Tasks", "Journal", "Agenda", "Contacts", "Settings", "Range")
    }
}
