package calino.malinov.ski

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.ui.range.RangePagerTag
import calino.malinov.ski.util.CalinoWeekShelf
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bottom-sheet preference on a portrait phone. */
class WeekTaskSheetTest : CalinoUiTest(weekShelf = CalinoWeekShelf.Sheet) {
    private fun frame(name: String) {
        compose.waitForIdle()
        Thread.sleep(150)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }

    private fun openWeek() {
        compose.openRoute("Range")
        compose.onNodeWithContentDescription("Range size in days: 7").performClick()
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
    }

    @Test fun peekExpandsAndDragFollowsTheFinger() {
        openWeek()
        compose.onNodeWithText("Sometime this week").assertIsDisplayed()
        frame("sheet-peek")
        val handle = compose.onNodeWithContentDescription("Sometime this week, sheet").fetchSemanticsNode().boundsInRoot
        val heightBefore = handle.height
        // Halfway up a slow drag the list is partly revealed, not snapped.
        compose.onRoot().performTouchInput {
            down(handle.center)
            moveTo(Offset(handle.center.x, handle.center.y - 120f), delayMillis = 400)
        }
        compose.waitForIdle()
        frame("sheet-mid-drag")
        val mid = compose.onNodeWithContentDescription("Sometime this week, sheet").fetchSemanticsNode().boundsInRoot
        assertTrue("sheet header moved up with the finger", mid.top < handle.top - 60f)
        compose.onRoot().performTouchInput {
            moveTo(Offset(handle.center.x, handle.center.y - 600f), delayMillis = 300)
            up()
        }
        compose.waitForIdle(); Thread.sleep(500)
        frame("sheet-expanded")
        compose.onNodeWithText("Call the plumber").assertIsDisplayed()
        // Tap the header to collapse again.
        compose.onNodeWithContentDescription("Sometime this week, sheet").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Call the plumber").fetchSemanticsNodes().all { false } || compose.onNodeWithContentDescription("Sometime this week, sheet").fetchSemanticsNode().boundsInRoot.height <= heightBefore + 2f }
        frame("sheet-collapsed")
    }

    /** A fast downward fling settles with velocity toward 0; the spring must not overshoot into a negative size. */
    @Test fun repeatedFlingsDoNotCrash() {
        openWeek()
        repeat(8) { i ->
            val b = compose.onNodeWithContentDescription("Sometime this week, sheet").fetchSemanticsNode().boundsInRoot
            val up = i % 2 == 0
            compose.onRoot().performTouchInput {
                val x = b.center.x
                val from = Offset(x, b.top + 20f)
                val to = Offset(x, b.top + 20f + if (up) -500f else 500f)
                swipeWithVelocity(from, to, endVelocity = if (up) 6000f else 6000f, durationMillis = 120)
            }
            compose.waitForIdle()
            Thread.sleep(40)
        }
        compose.onNodeWithContentDescription("Sometime this week, sheet").assertIsDisplayed()
    }

    /** The window's adjust mode, read on the UI thread. */
    private fun adjustMode(): Int {
        var mode = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            mode = compose.activity.window.attributes.softInputMode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        }
        return mode
    }

    // Typing into the docked sheet must leave the keyboard inset to imePadding alone:
    // with the default mode the system also pans the window, and the sheet is lifted twice.
    @Test fun composerTakesTheKeyboardInsetAndGivesItBack() {
        openWeek()
        val before = adjustMode()
        assertTrue("default mode should not already be resize", before != android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        compose.onNodeWithContentDescription("Add week task").performClick()
        compose.onNodeWithContentDescription("Week task title").assertIsDisplayed()
        compose.waitForIdle()
        assertTrue("composer open should resize", adjustMode() == android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        compose.onNodeWithContentDescription("Add week task").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Week task title").fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        val plus = compose.onNodeWithContentDescription("Add week task").fetchSemanticsNode().config
        val state = plus.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "none" }
        assertTrue("plus says $state", state == "Collapsed")
        assertTrue("closing the composer should restore the mode: before=$before now=${adjustMode()}", adjustMode() == before)
    }

    @Test fun dragFromSheetSchedulesAndGridStaysReachable() {
        openWeek()
        compose.onNodeWithContentDescription("Sometime this week, sheet").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Call the plumber").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle(); Thread.sleep(400)
        frame("sheet-open-list")
        val source = compose.onNodeWithText("Call the plumber").fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag(RangePagerTag).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { down(source.center) }
        compose.waitForIdle(); Thread.sleep(300)
        compose.onRoot().performTouchInput { moveTo(Offset(grid.left + grid.width * .4f, grid.top + 40f), delayMillis = 400) }
        compose.waitForIdle()
        frame("sheet-drag")
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }
}

/** Landscape phone: the strip. */
class WeekTaskStripTest : CalinoUiTest() {
    private fun sh(cmd: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
    }
    private fun frame(name: String) { compose.waitForIdle(); Thread.sleep(200); sh("screencap -p /sdcard/Download/$name.png") }

    @org.junit.After fun restoreRotation() { sh("settings put system user_rotation 0"); sh("settings put system accelerometer_rotation 1") }

    @Test fun stripShowsChipsAndDropsKeepWeekTasks() {
        sh("settings put system accelerometer_rotation 0"); sh("settings put system user_rotation 1")
        Thread.sleep(1500)
        compose.openRoute("Range")
        compose.onNodeWithContentDescription("Range size in days: 7").performClick()
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        compose.onNodeWithText("Call the plumber").assertIsDisplayed()
        frame("strip")
        compose.onNodeWithContentDescription("Add week task").performClick()
        compose.onNodeWithContentDescription("Week task title").assertIsDisplayed()
        frame("strip-add")
    }
}
