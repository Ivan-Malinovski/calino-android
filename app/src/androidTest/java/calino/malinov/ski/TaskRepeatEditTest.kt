package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.surfaces.TaskDetailSurface
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A repeating task's rule is editable in its detail, for the whole series. */
@RunWith(AndroidJUnit4::class)
class TaskRepeatEditTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val occurrence = CalTask(
        id = "gym@2026-03-10", title = "Exercise", color = 0L, due = LocalDate.of(2026, 3, 10),
        recurrence = "FREQ=WEEKLY;BYDAY=TU", recurrenceDate = LocalDate.of(2026, 3, 10),
    )

    private fun show(task: CalTask, saved: AtomicReference<NewTask?>) {
        compose.setContent {
            CalinoTheme { TaskDetailSurface(task = task, onSave = { input, _ -> saved.set(input); true }) }
        }
    }

    @Test fun addingAWeekdayChangesTheSeriesRule() {
        val saved = AtomicReference<NewTask?>()
        show(occurrence, saved)

        compose.onNodeWithContentDescription("Change task repeat").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Thu, Repeat on Thursday").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5_000L) { saved.get() != null }

        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", saved.get()?.recurrence)
        assertTrue(saved.get()!!.recurrenceChanged)
    }

    @Test fun anUntouchedRepeatSavesAsUnchanged() {
        val saved = AtomicReference<NewTask?>()
        show(occurrence, saved)

        compose.onNodeWithContentDescription("Change task repeat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Priority: High").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5_000L) { saved.get() != null }

        assertEquals("FREQ=WEEKLY;BYDAY=TU", saved.get()?.recurrence)
        assertFalse(saved.get()!!.recurrenceChanged)
    }

    /** Scrolling a node into view can leave it behind the floating action pill; lift it clear. */
    private fun lift() {
        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.85f, endY = height * 0.72f, durationMillis = 600)
        }
        compose.waitForIdle()
    }

    private val oneOff = occurrence.copy(
        id = "insurance", title = "Renew car insurance", recurrence = null, recurrenceDate = null,
    )

    @Test fun aOneOffTaskCanStartRepeating() {
        val saved = AtomicReference<NewTask?>()
        show(oneOff, saved)

        compose.onNodeWithContentDescription("Change task repeat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Change task repeat").performClick()
        compose.onNodeWithContentDescription("Yearly, Repeat yearly").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5_000L) { saved.get() != null }

        assertEquals("FREQ=YEARLY", saved.get()?.recurrence)
        assertTrue(saved.get()!!.recurrenceChanged)
    }

    @Test fun aOneOffTaskThatIsNotMadeToRepeatSavesAsUnchanged() {
        val saved = AtomicReference<NewTask?>()
        show(oneOff, saved)

        compose.onNodeWithContentDescription("Change task repeat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Priority: High").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5_000L) { saved.get() != null }

        assertNull(saved.get()?.recurrence)
        assertFalse(saved.get()!!.recurrenceChanged)
    }

    @Test fun repeatingAnUndatedTaskGivesItADueDate() {
        val saved = AtomicReference<NewTask?>()
        show(oneOff.copy(due = null), saved)

        compose.onNodeWithContentDescription("Change task repeat").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Daily, Repeat daily").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5_000L) { saved.get() != null }

        assertEquals("FREQ=DAILY", saved.get()?.recurrence)
        assertTrue(saved.get()?.due != null)
    }

    @Test fun aSubtaskHasNoRepeatRow() {
        val saved = AtomicReference<NewTask?>()
        show(oneOff.copy(parentTaskId = "parent"), saved)

        compose.onNodeWithContentDescription("Change task reminder").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Change task repeat").assertDoesNotExist()
        assertNull(saved.get())
    }

    @Test fun theEndOfARepeatIsLabelledAndCanBeCappedByCount() {
        val saved = AtomicReference<NewTask?>()
        show(oneOff, saved)

        compose.onNodeWithContentDescription("Change task repeat").performScrollTo().performClick()
        // Nothing to end until it repeats.
        compose.onNodeWithText("Ends").assertDoesNotExist()
        compose.onNodeWithContentDescription("Weekly, Repeat weekly").performScrollTo().performClick()
        compose.onNodeWithText("Ends", ignoreCase = true).performScrollTo().assertIsDisplayed()
        lift()
        compose.onNodeWithContentDescription("After, Ends after a number of times").performClick()
        lift()
        compose.onNodeWithText("10 times").assertIsDisplayed()
        compose.onNodeWithContentDescription("+, One more time").performClick()
        compose.onNodeWithContentDescription("+, One more time").performClick()
        compose.onNodeWithContentDescription("−, One fewer time").performClick()
        compose.onNodeWithText("11 times").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5_000L) { saved.get() != null }

        assertEquals("FREQ=WEEKLY;BYDAY=TU;COUNT=11", saved.get()?.recurrence)
    }

    @Test fun anEndDateReplacesTheCount() {
        val saved = AtomicReference<NewTask?>()
        show(oneOff.copy(recurrence = "FREQ=WEEKLY;BYDAY=TU;COUNT=4", recurrenceDate = LocalDate.of(2026, 3, 10)), saved)

        compose.onNodeWithContentDescription("Change task repeat").performScrollTo().performClick()
        compose.onNodeWithText("Ends", ignoreCase = true).performScrollTo()
        lift()
        compose.onNodeWithText("4 times").assertIsDisplayed()
        compose.onNodeWithContentDescription("On a date, Ends on a date").performClick()
        compose.onNodeWithText("4 times").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5_000L) { saved.get() != null }

        val rule = saved.get()?.recurrence.orEmpty()
        assertTrue(rule, rule.contains("UNTIL=") && !rule.contains("COUNT="))
    }
}
