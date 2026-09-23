package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Quick Add return-target restoration.
 *
 * The editor is an overlay that remembers where it was opened from
 * (`PocReturnTarget`, restored by `restoreDetailOrigin`). These tests assert
 * the behavioural face of that: cancel from a surface, land back on it -- not
 * on the calendar, which is the failure the return target exists to prevent.
 */
@RunWith(AndroidJUnit4::class)
class QuickAddReturnTargetTest : CalinoUiTest() {

    @Test fun cancellingFromTasksReturnsToTasks() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("New task. Swipe up for views").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Title, task").assertIsDisplayed()

        compose.onNodeWithContentDescription("Cancel editor").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Open task: Buy flowers").assertIsDisplayed()
    }

    /**
     * Journal has its own editor rather than the shared Quick Add one, so its
     * cancel is "Cancel journal editing". The return target is the same claim:
     * you land back on Journal, not on the calendar.
     */
    @Test fun cancellingFromJournalReturnsToJournal() {
        compose.openRoute("Journal")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("New entry. Swipe up for views").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Journal title").assertIsDisplayed()

        compose.onNodeWithContentDescription("Cancel journal editing").performClick()
        compose.waitForIdle()

        // The Journal header's subtitle is now a live entry total, so key the
        // return claim on the surface's own search affordance instead.
        compose.onNodeWithContentDescription("Search journal").assertIsDisplayed()
    }

    @Test fun cancellingFromTheCalendarReturnsToTheCalendar() {
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Title, event").assertIsDisplayed()

        compose.onNodeWithContentDescription("Cancel editor").performClick()
        compose.waitForIdle()

        compose.assertDaySelected(CalinoTestActions.WeekPager, CalinoTestActions.FixtureDate)
    }

    /** The editor opened from Tasks defaults to a task, not to an event. */
    @Test fun quickAddOpensInTheKindTheSurfaceImplies() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("New task. Swipe up for views").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Title, task").assertIsDisplayed()
    }

    /** Saving, rather than cancelling, also lands back on the origin surface. */
    @Test fun savingFromTasksReturnsToTasksWithTheNewTask() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("New task. Swipe up for views").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Title, task").performTextInput(NewTask)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save editor").performClick()
        compose.waitForIdle()

        assertTrue(compose.hasDescribedNode("Open task: $NewTask"))
    }

    private companion object {
        const val NewTask = "Water the plants"
    }
}
