package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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

    private val isChosen = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected")

    private fun show(task: CalTask, deleted: AtomicReference<RecurrenceEditScope?>) {
        compose.setContent {
            CalinoTheme {
                TaskDetailSurface(task = task, onDelete = { scope -> deleted.set(scope); true })
            }
        }
    }

    @Test fun deletingARepeatingTaskOffersTheSeriesAndDefaultsToOneOccurrence() {
        val deleted = AtomicReference<RecurrenceEditScope?>()
        show(occurrence, deleted)

        compose.onNodeWithContentDescription("Delete task").performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000L) { compose.hasDescribedNode("This task, Delete scope This task") }
        compose.onNodeWithContentDescription("This task, Delete scope This task").assertIsDisplayed().assert(isChosen)
        compose.onNodeWithContentDescription("This and future, Delete scope This and future").assertIsDisplayed()
        compose.onNodeWithContentDescription("Entire series, Delete scope Entire series").assertIsDisplayed()

        compose.onNodeWithTag("task-delete-confirm").performClick()
        compose.waitUntil(5_000L) { deleted.get() != null }
        assertEquals(RecurrenceEditScope.This, deleted.get())
    }

    @Test fun theChosenScopeReachesTheDelete() {
        val deleted = AtomicReference<RecurrenceEditScope?>()
        show(occurrence, deleted)

        compose.onNodeWithContentDescription("Delete task").performClick()
        compose.waitUntil(5_000L) { compose.hasDescribedNode("This task, Delete scope This task") }
        compose.onNodeWithContentDescription("Entire series, Delete scope Entire series").performClick()
        compose.onNodeWithContentDescription("Entire series, Delete scope Entire series").assert(isChosen)
        compose.onNodeWithTag("task-delete-confirm").performClick()
        compose.waitUntil(5_000L) { deleted.get() != null }
        assertEquals(RecurrenceEditScope.All, deleted.get())
    }

    @Test fun cancellingTheScopeSheetDeletesNothing() {
        val deleted = AtomicReference<RecurrenceEditScope?>()
        show(occurrence, deleted)

        compose.onNodeWithContentDescription("Delete task").performClick()
        compose.waitUntil(5_000L) { compose.hasDescribedNode("This task, Delete scope This task") }
        compose.onNodeWithContentDescription("This and future, Delete scope This and future").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertNull(deleted.get())
        compose.onNodeWithContentDescription("Delete task").assertIsDisplayed()
    }
}
