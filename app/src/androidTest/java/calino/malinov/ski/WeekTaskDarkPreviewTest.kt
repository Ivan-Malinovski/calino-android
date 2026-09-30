package calino.malinov.ski

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.util.CalinoThemeChoice
import org.junit.Test

class WeekTaskDarkPreviewTest : CalinoUiTest(theme = CalinoThemeChoice.Dark) {
    private fun frame(name: String) {
        compose.waitForIdle()
        Thread.sleep(120)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }

    @Test fun shelfAndQuickAddKeepTheirContentThroughEnterAndExit() {
        compose.openRoute("Range")
        compose.onNodeWithContentDescription("Range size in days: 7").performClick()
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        compose.onNode(hasContentDescription("Sometime this week", substring = true)).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Call the plumber").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Call the plumber").assertIsDisplayed()
        frame("week-dark")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add week task").performClick()
        compose.mainClock.advanceTimeBy(80)
        frame("week-dark-add-enter")
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription("Week task title").assertIsDisplayed()
        frame("week-dark-add")
        compose.onNodeWithContentDescription("Add week task").performClick()
        compose.mainClock.advanceTimeBy(80)
        frame("week-dark-add-exit")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Call the plumber").assertIsDisplayed()
    }
}
