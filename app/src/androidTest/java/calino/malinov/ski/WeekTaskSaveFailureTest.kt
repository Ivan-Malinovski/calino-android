package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.surfaces.TaskDetailSurface
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WeekTaskSaveFailureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun rejectedDateEditKeepsDraftAndRestoresUsableEditor() {
        val saved = AtomicReference<NewTask?>()
        val backs = AtomicInteger()
        val start = LocalDate.of(2026, 5, 18)
        compose.setContent {
            CalinoTheme {
                TaskDetailSurface(task = CalTask("week", "Call plumber", 1L, start.plusDays(6), startDate = start),
                    onBack = { backs.incrementAndGet() }, onSave = { input, _ -> saved.set(input); false })
            }
        }
        compose.onNodeWithContentDescription("Remove task start date", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5000) { saved.get() != null && compose.exists(hasContentDescription("Choose task start date", substring = true)) }
        assertNull(saved.get()!!.startDate)
        assertEquals(start.plusDays(6), saved.get()!!.due)
        compose.onNodeWithContentDescription("Save task").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose task start date", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel task editing").performClick()
        compose.waitUntil(5000) { backs.get() == 1 }
    }
    @Test fun thisWeekUsesSelectedWeekInsteadOfExistingDueWeek() {
        val saved = AtomicReference<NewTask?>()
        val selected = LocalDate.of(2026, 5, 18)
        compose.setContent {
            CalinoTheme {
                TaskDetailSurface(task = CalTask("future", "Book flights", 1L, selected.plusMonths(1)), planningDate = selected,
                    onSave = { input, _ -> saved.set(input); true })
            }
        }
        compose.onNodeWithContentDescription("Set task to this week", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.waitUntil(5000) { saved.get() != null }
        assertEquals(selected, saved.get()!!.startDate)
        assertEquals(selected.plusDays(6), saved.get()!!.due)
        assertNull(saved.get()!!.dueTime)
        assertNull(saved.get()!!.startTime)
    }

}
