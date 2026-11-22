package calino.malinov.ski

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.ui.range.RangePagerTag
import org.junit.Assert.assertTrue
import org.junit.Test

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
        compose.selectRangeDays(7)
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        compose.onNodeWithText("Call the plumber").assertIsDisplayed()
        frame("strip")
        compose.onNodeWithContentDescription("Add week task").performClick()
        compose.onNodeWithContentDescription("Week task title").assertIsDisplayed()
        frame("strip-add")
    }
}
