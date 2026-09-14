package calino.malinov.ski.poc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.poc.CalinoTestActions.FixtureDate
import calino.malinov.ski.poc.CalinoTestActions.WeekPager
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings state retention.
 *
 * Each test changes a preference, leaves the surface, and comes back -- the
 * round trip is the point. A preference that only holds while its own screen is
 * composed is the bug these exist to catch.
 *
 * Settings is a pager over its sections, so a section has to be opened by its
 * tab before its rows are reachable.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRetentionTest : CalinoUiTest() {

    @Test fun aToggleHoldsAcrossNavigation() {
        openSettingsSection(Calendar)
        val weekNumbers = { compose.onNodeWithContentDescription("Show week numbers toggle") }
        // Week numbers default to on, so switching it off is the change that
        // has to survive -- and the direction a preference is easiest to lose.
        weekNumbers().performScrollTo().assertIsOn()
        weekNumbers().performClick()
        compose.waitForIdle()
        weekNumbers().assertIsOff()

        compose.openRoute("Month")
        compose.waitForIdle()
        openSettingsSection(Calendar)

        weekNumbers().performScrollTo().assertIsOff()
    }

    @Test fun aSegmentedChoiceHoldsAcrossNavigation() {
        openSettingsSection(Calendar)
        compose.onNodeWithContentDescription("First day of week: Sunday").performScrollTo().performClick()
        compose.waitForIdle()

        compose.openRoute("Month")
        compose.waitForIdle()
        openSettingsSection(Calendar)

        compose.onNodeWithContentDescription("First day of week: Sunday").performScrollTo().assertIsSelected()
    }

    /** A second selection interrupts the indicator spring without delaying state. */
    @Test fun aSegmentedChoiceCanReverseWhileMoving() {
        openSettingsSection(Calendar)
        compose.onNodeWithContentDescription("First day of week: Sunday").performScrollTo().performClick()
        compose.onNodeWithContentDescription("First day of week: Monday").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("First day of week: Monday").assertIsSelected()
    }

    @Test fun aGridToggleHoldsAcrossNavigation() {
        openSettingsSection(Calendar)
        val hideCompleted = { compose.onNodeWithContentDescription("Hide completed tasks toggle") }
        hideCompleted().performScrollTo().assertIsOff()
        hideCompleted().performClick()
        compose.waitForIdle()

        compose.openRoute("Tasks")
        compose.waitForIdle()
        openSettingsSection(Calendar)

        hideCompleted().performScrollTo().assertIsOn()
    }

    /**
     * The week start is not merely stored -- it re-keys the week pager. With
     * Sunday first, 17 May joins the week holding the fixture date, which it
     * does not when the week starts on Monday.
     */
    @Test fun theWeekStartChangesTheWeekStrip() {
        openSettingsSection(Calendar)
        compose.onNodeWithContentDescription("First day of week: Sunday").performScrollTo().performClick()
        compose.waitForIdle()

        compose.openRoute("Month")
        compose.waitForIdle()

        compose.dayCellIn(WeekPager, FixtureDate.minusDays(1)).assertIsDisplayed()
        compose.assertDaySelected(WeekPager, FixtureDate)
    }

    /** Turning the Journal surface off removes its navigation row. */
    @Test fun disablingJournalRemovesItsDestination() {
        openSettingsSection(General)
        compose.onNodeWithContentDescription("Journal toggle").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForIdle()

        assertFalse("Journal row should be gone", compose.hasDescribedNode("Journal"))
    }

    /** ...and turning it back on restores it, rather than needing a relaunch. */
    @Test fun reenablingJournalRestoresItsDestination() {
        openSettingsSection(General)
        val journal = { compose.onNodeWithContentDescription("Journal toggle") }
        journal().performScrollTo().performClick()
        compose.waitForIdle()
        journal().performClick()
        compose.waitForIdle()

        compose.openRoute("Journal")
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Open journal entry A clear Monday").assertIsDisplayed()
    }

    private fun openSettingsSection(section: String) {
        compose.openRoute("Settings")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("$section settings").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private companion object {
        const val General = "General"
        const val Calendar = "Calendar"
    }
}
