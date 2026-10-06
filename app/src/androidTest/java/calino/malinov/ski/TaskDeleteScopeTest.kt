package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.surfaces.TaskDetailSurface
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A repeating task asks how much of the series to remove; a one-off does not. */
@RunWith(AndroidJUnit4::class)
class TaskDeleteScopeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val occurrence = CalTask(
        id = "gym@2026-03-10", title = "Exercise", color = 0L, due = LocalDate.of(2026, 3, 10),
        recurrence = "FREQ=WEEKLY;BYDAY=TU", recurrenceDate = LocalDate.of(2026, 3, 10),
        recurrenceScope = RecurrenceEditScope.This,
    )

    private fun show(task: CalTask, deleted: AtomicReference<RecurrenceEditScope?>) {
        compose.setContent {
            CalinoTheme {
                TaskDetailSurface(task = task, onDelete = { scope -> deleted.set(scope); true })
            }
        }
    }

    private fun openSheet() {
        compose.onNodeWithContentDescription("Delete task").performClick()
        compose.waitUntil(5_000L) { compose.onAllNodesWithTag("task-delete-this").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun deletingARepeatingTaskOffersEveryScopeAsItsOwnAction() {
        val deleted = AtomicReference<RecurrenceEditScope?>()
        show(occurrence, deleted)

        openSheet()
        compose.onNodeWithTag("task-delete-this").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("task-delete-future").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("task-delete-all").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        assertNull(deleted.get())
    }

    private fun tapScope(tag: String): RecurrenceEditScope? {
        val deleted = AtomicReference<RecurrenceEditScope?>()
        show(occurrence, deleted)
        openSheet()
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(5_000L) { deleted.get() != null }
        return deleted.get()
    }

    @Test fun thisTaskDeletesOnlyThatOccurrence() = assertEquals(RecurrenceEditScope.This, tapScope("task-delete-this"))

    @Test fun thisAndFutureDeletesFromHere() = assertEquals(RecurrenceEditScope.Future, tapScope("task-delete-future"))

    @Test fun entireSeriesDeletesEverything() = assertEquals(RecurrenceEditScope.All, tapScope("task-delete-all"))

    @Test fun cancellingTheScopeSheetDeletesNothing() {
        val deleted = AtomicReference<RecurrenceEditScope?>()
        show(occurrence, deleted)

        openSheet()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertNull(deleted.get())
        compose.onNodeWithContentDescription("Delete task").assertIsDisplayed()
    }
}
