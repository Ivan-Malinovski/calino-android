package calino.malinov.ski

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.ui.range.RangePagerTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RangeSpanningEventTest : CalinoUiTest() {
    @Test fun spanningEventClipsEachDayAndOpensTheOriginalEvent() {
        val repository = CalinoContainer.get(ApplicationProvider.getApplicationContext<Context>()).fixtureRepository
        compose.runOnIdle {
            runBlocking {
                repository.addEvent(NewEvent("Spanning event", LocalDate.of(2026, 5, 18), LocalTime.of(16, 15), 49 * 60))
            }
        }
        compose.openRoute("Settings")
        compose.onNodeWithContentDescription("Events & tasks settings").performClick()
        compose.onNodeWithContentDescription("Multi-day events in Range header toggle")
            .performScrollTo().assertIsOn().performClick()
        compose.openRoute("Range")
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) {
            compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        }
        // The middle and final day begin at midnight, not at Monday's time.
        compose.onAllNodesWithContentDescription("Spanning event, 12:00 AM").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Spanning event, 4:15 PM").assertCountEquals(1)
        compose.onNodeWithText("4:15 PM · 7 h 45 min", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("12:00 AM · 1 d", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("12:00 AM · 17 h 15 min", substring = true, useUnmergedTree = true).assertExists()
        capture("range-span-morning")
        // Scroll the shared rail into the afternoon, capturing a live frame
        // as well as the settled geometry at the actual Wednesday end.
        val grid = compose.onNodeWithTag(RangePagerTag).fetchSemanticsNode().boundsInRoot
        val origin = Offset(grid.left + grid.width * .8f, grid.top + grid.height * .8f)
        compose.onRoot().performTouchInput {
            down(origin)
            moveTo(origin - Offset(0f, 40f), delayMillis = 16)
            moveTo(origin - Offset(0f, 550f), delayMillis = 300)
        }
        capture("range-span-scroll")
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        capture("range-span-afternoon")
        val continuations = compose.onAllNodesWithContentDescription("Spanning event, 12:00 AM")
        val finalDay = continuations.fetchSemanticsNodes().indices.maxBy { continuations[it].fetchSemanticsNode().boundsInRoot.left }
        continuations[finalDay].performTouchInput { click(Offset(center.x, height - 20f)) }
        awaitDescribed("Open event")
        capture("range-span-detail")
        compose.runOnIdle {
            val original = repository.events().single { it.title == "Spanning event" }
            assertEquals(LocalDate.of(2026, 5, 18).atTime(16, 15), original.start)
            assertEquals(49 * 60, original.durationMinutes)
        }
        compose.onNodeWithContentDescription("Close event preview").performClick()
        awaitNoDescribed("Close event preview")
        compose.onNodeWithContentDescription("Range dates, May 18 – May 20").assertIsDisplayed()
        // All supported widths use the same clipped projection.
        compose.selectRangeDays(1)
        compose.onAllNodesWithContentDescription("Spanning event, 4:15 PM").assertCountEquals(1)
        compose.selectRangeDays(7)
        compose.onAllNodesWithContentDescription("Spanning event, 12:00 AM").assertCountEquals(2)
        capture("range-span-seven-days")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Let Android present the Compose frame before taking a system capture.
        Thread.sleep(120)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }
}
