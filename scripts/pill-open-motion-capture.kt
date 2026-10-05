package calino.malinov.ski

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.util.CalinoThemeChoice
import org.junit.Test

/** Opt-in rendered frames of the preview-to-editor pill handoff. */
abstract class PillOpenCapture(
    private val suffix: String,
    menu: Boolean,
    private val dock: Boolean,
    theme: CalinoThemeChoice,
) : CalinoUiTest(menuPill = menu, theme = theme) {
    private fun frame(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pill-open-$suffix-$name.png").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }
    private fun step(name: String, millis: Long) {
        var remaining = millis
        while (remaining > 0) {
            val slice = minOf(16L, remaining)
            compose.mainClock.advanceTimeBy(slice)
            compose.waitForIdle()
            remaining -= slice
        }
        frame(name)
    }
    @Test fun openKeepsTheActionPillAndLeavesOnlyTheDownArrow() {
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) {
            compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        }
        if (dock) {
            compose.onNodeWithContentDescription("Views, current: Month").performTouchInput { longClick() }
            compose.waitForIdle()
        }
        compose.onNodeWithContentDescription("Lunch with Maya, 12:30 PM, Café Lumen").performClick()
        awaitDescribed("Open event")
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        frame("preview")
        compose.onNodeWithContentDescription("Open event").performClick()
        step("016",16); step("048",32); step("096",48)
        step("160",64); step("320",160); step("640",320)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cancel editor").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open event").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save editor").assertDoesNotExist()
        frame("editor")
    }
}
class PillOpenExtendedLightTest : PillOpenCapture("extended-light", true, false, CalinoThemeChoice.Light)
class PillOpenExtendedDarkTest : PillOpenCapture("extended-dark", true, false, CalinoThemeChoice.Dark)
class PillOpenDockLightTest : PillOpenCapture("dock-light", true, true, CalinoThemeChoice.Light)
class PillOpenDockDarkTest : PillOpenCapture("dock-dark", true, true, CalinoThemeChoice.Dark)
class PillOpenClassicLightTest : PillOpenCapture("classic-light", false, false, CalinoThemeChoice.Light)
class PillOpenClassicDarkTest : PillOpenCapture("classic-dark", false, false, CalinoThemeChoice.Dark)
