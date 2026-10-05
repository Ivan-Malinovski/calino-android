package calino.malinov.ski

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.ui.range.RangePagerTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RangeTaskOverflowTest : CalinoUiTest() {
    @Test fun unfinishedTasksStayVisibleAndCompletingOneMakesRoomForAnEvent() {
        val repository = CalinoContainer.get(ApplicationProvider.getApplicationContext<Context>()).fixtureRepository
        val monday = LocalDate.of(2026, 5, 18)
        compose.runOnIdle {
            runBlocking {
                repository.addTask(NewTask("Finish packing", due = monday))
                repository.addEvent(NewEvent("Packing day", monday, allDay = true))
            }
        }
        compose.openRoute("Range")
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) {
            compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        }
        listOf(3, 7, 1).forEach { count ->
            compose.selectRangeDays(count)
            visible("Complete Finish packing").assertCountEquals(1)
            visible("Complete Review calendar notes").assertCountEquals(1)
            visible("Mark Plan weekend trip open").assertCountEquals(0)
            visible("Packing day", substring = true).assertCountEquals(0)
            capture("range-overflow-$count-days")
            overflow().performClick()
            visible("Mark Plan weekend trip open").assertCountEquals(1)
            visible("Packing day", substring = true).assertCountEquals(1)
            capture("range-overflow-expanded-$count-days")
            visible("Show fewer all-day items")[0].performClick()
        }
        compose.selectRangeDays(3)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            visible("Complete Finish packing")[0].performClick()
            compose.mainClock.advanceTimeBy(64)
            capture("range-overflow-completing")
            compose.mainClock.advanceTimeBy(300)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        visible("Mark Finish packing open").assertCountEquals(0)
        visible("Complete Review calendar notes").assertCountEquals(1)
        visible("Packing day", substring = true).assertCountEquals(1)
        compose.runOnIdle { assertTrue(repository.tasks().single { it.title == "Finish packing" }.done) }
        capture("range-overflow-completed")
        overflow().performClick()
        visible("Mark Finish packing open")[0].performClick()
        visible("Complete Finish packing").assertCountEquals(1)
        visible("Show fewer all-day items")[0].performClick()
        visible("Complete Finish packing").assertCountEquals(1)
        visible("Packing day", substring = true).assertCountEquals(0)
        capture("range-overflow-reopened")

        // Reordering and overflow expansion must leave the host's chip drag
        // registration intact. A cancelled drag must not reschedule the task.
        val beforeDrag = repository.tasks().single { it.title == "Finish packing" }
        val grid = compose.onNodeWithTag(RangePagerTag).fetchSemanticsNode().boundsInRoot
        val chip = compose.onAllNodesWithText("Finish packing")
            .filter(SemanticsMatcher("visible task title") { it.boundsInRoot.overlaps(grid) })[0]
            .fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { down(chip.center) }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.onRoot().performTouchInput {
            moveTo(Offset(grid.left + grid.width * .8f, chip.center.y), delayMillis = 300)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Task drop preview", substring = true).assertIsDisplayed()
        capture("range-overflow-cancel-drag")
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(beforeDrag, repository.tasks().single { it.id == beforeDrag.id })
    }

    private fun visible(description: String, substring: Boolean = false): SemanticsNodeInteractionCollection {
        val viewport = compose.onNodeWithTag(RangePagerTag).fetchSemanticsNode().boundsInRoot
        return compose.onAllNodesWithContentDescription(description, substring = substring)
            .filter(SemanticsMatcher("intersects the visible Range page") { it.boundsInRoot.overlaps(viewport) })
    }

    private fun overflow(): SemanticsNodeInteraction {
        val viewport = compose.onNodeWithTag(RangePagerTag).fetchSemanticsNode().boundsInRoot
        return compose.onAllNodesWithContentDescription("more all-day", substring = true)
            .filter(SemanticsMatcher("visible overflow row") { it.boundsInRoot.overlaps(viewport) })[0]
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(120)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }
}
