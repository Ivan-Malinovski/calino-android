package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.surfaces.TaskDetailSurface
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A rejected server save must not leave a dismissed modal over the app. */
@RunWith(AndroidJUnit4::class)
class TaskSaveFailureTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun taskDetailEditsServerTaskReminderAndPreservesDueTime() {
        val saved = AtomicReference<NewTask?>()
        compose.setContent {
            CalinoTheme {
                TaskDetailSurface(
                    task = CalTask(
                        id = "server-task", title = "Server task", color = 0L,
                        due = LocalDate.of(2026, 5, 20), dueTime = LocalTime.of(14, 30),
                        reminder = Reminder(30),
                    ),
                    onSave = { input, _ -> saved.set(input); true },
                )
            }
        }
        compose.onNodeWithContentDescription("Change due time").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Change task reminder").performScrollTo().performClick()
        compose.onNodeWithText("At time").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5_000L) { saved.get() != null }
        assertEquals(LocalTime.of(14, 30), saved.get()?.dueTime)
        assertEquals(Reminder(0), saved.get()?.reminder)
    }

    @Test fun rejectedTaskSaveRestoresTheEditorAndKeepsItDismissible() {
        val writes = AtomicInteger()
        val backs = AtomicInteger()
        compose.setContent {
            CalinoTheme {
                TaskDetailSurface(
                    task = CalTask(
                        id = "self-hosted-task",
                        title = "Self-hosted task",
                        color = 0L,
                        due = null,
                    ),
                    onBack = { backs.incrementAndGet() },
                    onSave = { _, _ ->
                        writes.incrementAndGet()
                        false
                    },
                )
            }
        }

        compose.onNodeWithContentDescription("High priority")
            .performScrollTo()
        // Scroll the editor farther than the minimum needed to expose the row:
        // the floating action pill otherwise overlaps the bottom-most controls.
        compose.onNode(hasScrollAction()).performTouchInput {
            swipeUp(startY = height * .78f, endY = height * .64f)
        }
        compose.onNodeWithContentDescription("High priority").performClick()
        compose.waitUntil(5_000L) { compose.hasDescribedNode("High priority, selected") }
        compose.waitUntil(5_000L) { compose.hasDescribedNode("Save task") }
        compose.onNodeWithContentDescription("Save task").performClick()

        compose.waitUntil(5_000L) {
            writes.get() == 1 && compose.hasDescribedNode("Save task")
        }
        compose.onNodeWithContentDescription("Save task").assertIsDisplayed()
        compose.onNodeWithContentDescription("High priority, selected")
            .performScrollTo()
            .assertIsDisplayed()

        // If the failed write left the old invisible host mounted, this
        // action cannot reach the restored editor or return to its caller.
        compose.onNodeWithContentDescription("Cancel task editing").performClick()
        compose.waitUntil(5_000L) { backs.get() == 1 }
        assertEquals(1, writes.get())
    }
}
