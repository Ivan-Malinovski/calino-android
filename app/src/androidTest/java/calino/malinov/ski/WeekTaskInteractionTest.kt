package calino.malinov.ski

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.state.isWeekTask
import calino.malinov.ski.ui.range.RangePagerTag
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class WeekTaskInteractionTest : CalinoUiTest() {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val repository get() = CalinoContainer.get(context).fixtureRepository
    private fun openWeek() {
        compose.openRoute("Range")
        compose.onNodeWithContentDescription("Range size in days: 7").performClick()
        compose.onNodeWithText("Sometime this week").assertIsDisplayed()
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(120)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }

    @Test fun sampleShelfIsUniqueAndCompletionDoesNotSchedule() {
        openWeek()
        compose.onAllNodesWithText("Call the plumber").assertCountEquals(1)
        compose.onNodeWithText("Book summer flights").assertIsDisplayed()
        compose.onNodeWithText("Choose a birthday gift").assertIsDisplayed()
        capture("week-shelf")
        compose.onNodeWithContentDescription("Call the plumber, checkbox").performClick()
        compose.waitUntil(5000) { repository.tasks().first { it.id == "task-week-plumber" }.done }
        assertTrue(repository.tasks().first { it.id == "task-week-plumber" }.isWeekTask())
        compose.onNodeWithText("Call the plumber").assertIsDisplayed()
    }

    @Test fun quickAddCreatesDateOnlyRangeAndDetailsCarriesTitle() {
        openWeek()
        compose.onNodeWithContentDescription("Add week task").performClick()
        compose.onNodeWithContentDescription("Week task title").performTextInput("Check the garden")
        compose.onNodeWithText("Add", substring = false).performClick()
        compose.waitUntil(5000) { repository.tasks().any { it.title == "Check the garden" } }
        val added = repository.tasks().first { it.title == "Check the garden" }
        assertEquals(LocalDate.of(2026, 5, 18), added.startDate)
        assertEquals(LocalDate.of(2026, 5, 24), added.due)
        assertNull(added.dueTime)
        assertNull(added.startTime)
        compose.onNodeWithContentDescription("Add week task").performClick()
        compose.onNodeWithContentDescription("Week task title").performTextInput("Plan dinner")
        compose.onNodeWithText("More details").performClick()
        capture("week-more-details")
        // Quick Add owns the same prefilled draft, including its week range.
        awaitDescribed("Title, task")
        compose.onNodeWithContentDescription("Title, task").assertTextEquals("Plan dinner")
        compose.onNodeWithContentDescription("Save editor").performClick()
        compose.waitUntil(5000) { repository.tasks().any { it.title == "Plan dinner" } }
        assertTrue(repository.tasks().first { it.title == "Plan dinner" }.isWeekTask())
    }

    @Test fun dragSchedulesPointDeadlineAndCanReturnToWeekShelf() {
        openWeek()
        val source = compose.onNodeWithText("Call the plumber").fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag(RangePagerTag).fetchSemanticsNode().boundsInRoot
        val target = androidx.compose.ui.geometry.Offset(grid.left + grid.width * .22f, grid.top + grid.height * .3f)
        compose.onRoot().performTouchInput { down(source.center) }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.onRoot().performTouchInput {
            moveTo(target, delayMillis = 300)
        }
        compose.waitForIdle()
        capture("week-drag-preview")
        compose.onRoot().performTouchInput { up() }
        compose.waitUntil(5000) { !repository.tasks().first { it.id == "task-week-plumber" }.isWeekTask() }
        val scheduled = repository.tasks().first { it.id == "task-week-plumber" }
        assertNotNull(scheduled.dueTime)
        assertNull(scheduled.startDate)
        assertEquals(0, scheduled.dueTime!!.minute % 15)
        capture("week-timed-deadline")
        val marker = compose.onNodeWithContentDescription("Call the plumber, due", substring = true).fetchSemanticsNode().boundsInRoot
        val shelf = compose.onNodeWithText("Sometime this week").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { down(androidx.compose.ui.geometry.Offset(marker.right - 3f, marker.center.y)) }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.onRoot().performTouchInput {
            moveTo(shelf.center, delayMillis = 300)
        }
        compose.waitForIdle()
        capture("week-return-preview")
        compose.onRoot().performTouchInput { up() }
        compose.waitUntil(5000) { repository.tasks().first { it.id == "task-week-plumber" }.isWeekTask() }
        assertNull(repository.tasks().first { it.id == "task-week-plumber" }.dueTime)
    }

    @Test fun headerDropWinsOverUnderlyingHoursAndCancellationKeepsRange() {
        openWeek()
        val taskBefore = repository.tasks().first { it.id == "task-week-plumber" }
        val source = compose.onNodeWithText("Call the plumber").fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag(RangePagerTag).fetchSemanticsNode().boundsInRoot
        val target = androidx.compose.ui.geometry.Offset(grid.left + grid.width * .4f, grid.top + 40f)
        compose.onRoot().performTouchInput { down(source.center) }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.onRoot().performTouchInput { moveTo(target, delayMillis = 500) }
        compose.waitForIdle()
        capture("week-header-preview")
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(taskBefore, repository.tasks().first { it.id == taskBefore.id })
        Thread.sleep(100)
        val freshSource = compose.onNodeWithText("Call the plumber").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { down(freshSource.center) }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.onRoot().performTouchInput { moveTo(target, delayMillis = 60) }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { up() }
        compose.waitUntil(5000) { !repository.tasks().first { it.id == taskBefore.id }.isWeekTask() }
        val scheduled = repository.tasks().first { it.id == taskBefore.id }
        assertNull(scheduled.dueTime)
        assertNull(scheduled.startDate)
        assertEquals(LocalDate.of(2026, 5, 20), scheduled.due)
    }

    @Test fun shelfFollowsPagedWeekAndOverlappingTaskRemains() {
        openWeek()
        compose.onNodeWithTag(RangePagerTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Book summer flights").assertIsDisplayed()
        compose.onAllNodesWithText("Call the plumber").assertCountEquals(0)
    }
}
