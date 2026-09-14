package calino.malinov.ski.poc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task completion, reschedule, and undo.
 *
 * The undo windows are real five-second `delay`s on the main dispatcher, not
 * animation-clock time, so they are not affected by Compose's frame clock and
 * a normal assertion has the whole window to run in. Only
 * [undoBannerExpiresOnItsOwn] actually waits one out.
 */
@RunWith(AndroidJUnit4::class)
class TaskInteractionTest : CalinoUiTest() {

    @Test fun completingATaskMarksItCompleted() {
        compose.openRoute("Tasks")
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Complete $Task").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("$Task, completed").assertIsDisplayed()
    }

    @Test fun completingATaskOffersAnUndo() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Complete $Task").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Undo completing $Task").assertIsDisplayed()
    }

    @Test fun undoReopensTheTask() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Complete $Task").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Undo completing $Task").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Complete $Task").assertIsDisplayed()
        assertFalse(compose.hasDescribedNode("$Task, completed"))
    }

    /** Completion feedback may still be settling when Undo reverses the state. */
    @Test fun completionCanBeReversedImmediately() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Complete $Task").performClick()
        compose.onNodeWithContentDescription("Undo completing $Task").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Complete $Task").assertIsDisplayed()
        assertFalse(compose.hasDescribedNode("$Task, completed"))
    }

    /**
     * The window closes on its own after five seconds. Waited out rather than
     * asserted away, because "the banner eventually leaves" is the behaviour
     * that keeps a stale undo from being offered indefinitely.
     */
    @Test fun undoBannerExpiresOnItsOwn() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Complete $Task").performClick()
        compose.waitForIdle()
        assertTrue(compose.hasDescribedNode("Undo completing $Task"))

        compose.waitUntil(UndoExpiryTimeoutMillis) {
            !compose.hasDescribedNode("Undo completing $Task")
        }
    }

    /**
     * Rescheduling through the task detail's due-date chips.
     *
     * A long press opens the detail editor rather than a context menu; the
     * chips there are the reschedule affordance, and they report their own
     * selection.
     */
    @Test fun theDueDateChipsRescheduleATask() {
        openTaskDetail()
        // The chip for the current due date reports itself selected first.
        compose.onNodeWithContentDescription("Today, selected").performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Set due date to Tomorrow").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save task").performClick()

        // The fixture "today" is 18 May, so tomorrow is 19 May.
        val rescheduledDescription = "$DatedTask, due May 19, Work"
        awaitDescribed(rescheduledDescription)
        compose.onNodeWithContentDescription(rescheduledDescription).assertIsDisplayed()
    }

    /** Abandoning the detail editor leaves the due date where it was. */
    @Test fun cancellingTheDetailKeepsTheDueDate() {
        openTaskDetail()
        compose.onNodeWithContentDescription("Set due date to Tomorrow").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Cancel task editing").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("$DatedTask, due May 18, Work").assertIsDisplayed()
    }

    /** The detail's own completion action reaches the same undoable write. */
    @Test fun theDetailCanCompleteATask() {
        openTaskDetail()

        compose.onNodeWithContentDescription("Mark task as done").performClick()
        awaitDescribed("$DatedTask, completed")

        compose.onNodeWithContentDescription("$DatedTask, completed").performScrollTo().assertIsDisplayed()
    }

    @Test fun taskDetailExposesPriorityAndPartialProgressControls() {
        openTaskDetail()

        compose.onNodeWithContentDescription("None priority, selected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("High priority").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("High priority, selected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Task progress, 0 percent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Save task").performClick()
        val savedDescription = "$DatedTask, due May 18, Work, priority 1"
        awaitDescribed(savedDescription)
        compose.onNodeWithContentDescription(savedDescription)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun openTaskDetail() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open task: $DatedTask").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save task").assertIsDisplayed()
    }

    @Test fun theTaskFilterHidesCompletedWork() {
        compose.openRoute("Tasks")
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Task filter: Active").performClick()
        compose.waitForIdle()

        assertTrue(compose.hasDescribedNode("Complete $Task"))
        assertFalse(compose.hasDescribedNode("$DoneTask, completed"))
    }

    private companion object {
        const val Task = "Buy flowers"
        const val DatedTask = "Review calendar notes"
        const val DoneTask = "Book accommodation"
        const val UndoExpiryTimeoutMillis = 9_000L
    }
}
