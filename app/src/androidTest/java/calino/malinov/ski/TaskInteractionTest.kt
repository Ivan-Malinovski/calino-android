package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
 * [pillUndoExpiresOnItsOwn] actually waits one out.
 */
@RunWith(AndroidJUnit4::class)
class TaskInteractionTest : CalinoUiTest() {

    @Test fun completingATaskMarksItCompleted() {
        compose.openRoute("Tasks")
        compose.waitForIdle()

        compose.onNodeWithTag("task-list").performScrollToNode(hasContentDescription("Complete $Task"))
        compose.onNodeWithContentDescription("Complete $Task").performClick()
        compose.waitForIdle()

        // All previews only five completed tasks; the Completed filter shows
        // the entire history, including this newly completed record.
        compose.onNodeWithContentDescription("Task filter: Completed").performClick()
        val completed = "$Task, Personal, completed"
        awaitDescribed(completed)
        compose.onNodeWithTag("task-list")
            .performScrollToNode(hasContentDescription(completed))
        compose.onNodeWithContentDescription(completed).assertIsDisplayed()
    }

    @Test fun completingATaskOffersAnUndo() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithTag("task-list").performScrollToNode(hasContentDescription("Complete $Task"))
        compose.onNodeWithContentDescription("Complete $Task").performClick()
        compose.waitForIdle()

        awaitPillUndo()
        compose.onNodeWithContentDescription("Undo: Completed $Task").assertIsDisplayed()
    }

    @Test fun undoReopensTheTask() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithTag("task-list").performScrollToNode(hasContentDescription("Complete $Task"))
        compose.onNodeWithContentDescription("Complete $Task").performClick()
        awaitPillUndo()

        compose.onNodeWithContentDescription("Undo: Completed $Task").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Complete $Task").assertIsDisplayed()
        assertFalse(compose.hasDescribedNode("$Task, completed"))
    }

    /**
     * The window closes on its own after five seconds. Waited out rather than
     * asserted away, because "the pill eventually drops its undo" is the behaviour
     * that keeps a stale undo from being offered indefinitely.
     */
    @Test fun pillUndoExpiresOnItsOwn() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithTag("task-list").performScrollToNode(hasContentDescription("Complete $Task"))
        compose.onNodeWithContentDescription("Complete $Task").performClick()
        awaitPillUndo()
        assertTrue(compose.hasDescribedNode("Undo: Completed $Task"))

        compose.waitUntil(UndoExpiryTimeoutMillis) {
            !compose.hasDescribedNode("Undo: Completed $Task")
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
        compose.onNodeWithContentDescription("Today, Set due date").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)

        compose.onNodeWithContentDescription("Tomorrow, Set due date").performScrollTo().performClick()
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
        compose.onNodeWithContentDescription("Tomorrow, Set due date").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Cancel task editing").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("$DatedTask, due May 18, Work").assertIsDisplayed()
    }

    /** The detail's own completion action reaches the same undoable write. */
    @Test fun theDetailCanCompleteATask() {
        openTaskDetail()

        compose.onNodeWithContentDescription("Mark task as done").performClick()
        awaitNoDescribed("Cancel task editing")
        compose.onNodeWithContentDescription("Task filter: Completed").performClick()
        val completed = "$DatedTask, due May 18, Work, completed"
        awaitDescribed(completed)
        compose.onNodeWithTag("task-list").performScrollToNode(hasContentDescription(completed))
        compose.onNodeWithContentDescription(completed).assertIsDisplayed()
    }

    @Test fun taskDetailExposesPriorityAndPartialProgressControls() {
        openTaskDetail()

        compose.onNodeWithContentDescription("Priority: None").performScrollTo().assertIsDisplayed()
        // Bring the end of the form above the floating pill before selecting
        // a priority; the pill clearance is intentionally scrollable content.
        compose.onNodeWithContentDescription("Task progress, 0 percent").performScrollTo()
        compose.onNodeWithContentDescription("Priority: High").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Priority: High").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Task progress, 0 percent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Save task").performClick()
        val savedDescription = "$DatedTask, due May 18, Work, priority 1"
        awaitDescribed(savedDescription)
        compose.onNodeWithContentDescription(savedDescription)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test fun taskDetailExposesDeleteInTheActionPill() {
        openTaskDetail()

        compose.onNodeWithContentDescription("Delete task").assertIsDisplayed()
    }

    private fun openTaskDetail() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open task: $DatedTask").performTouchInput { longClick() }
        compose.waitForIdle()
        // Save only occupies its lane once the draft is dirty, so it cannot
        // stand for "the detail opened". Cancel is always on the pill.
        compose.onNodeWithContentDescription("Cancel task editing").assertIsDisplayed()
    }

    @Test fun theTaskFilterHidesCompletedWork() {
        compose.openRoute("Tasks")
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Task filter: Active").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("task-list").performScrollToNode(hasContentDescription("Complete $Task"))
        assertTrue(compose.hasDescribedNode("Complete $Task"))
        assertFalse(compose.hasDescribedNode("$DoneTask, completed"))
    }

    private companion object {
        const val Task = "Buy flowers"
        const val DatedTask = "Review calendar notes"
        const val DoneTask = "Book accommodation"
        const val UndoExpiryTimeoutMillis = 9_000L
    }

    /** The pill names the change once its "Saved" beat has passed. */
    private fun awaitPillUndo() {
        compose.waitUntil(UndoExpiryTimeoutMillis) { compose.hasDescribedNode("Undo: Completed $Task") }
    }
}
