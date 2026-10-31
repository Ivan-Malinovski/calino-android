package calino.malinov.ski

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.util.CalinoThemeChoice
import org.junit.Test

/** Temporarily copy into androidTest to capture real pill/card transition frames. */
abstract class UnsavedChangesCapture(private val suffix: String, theme: CalinoThemeChoice) : CalinoUiTest(theme = theme) {
    private fun frame(name: String) {
        compose.waitForIdle()
        // Allow SurfaceFlinger to present the Compose frame before screencap.
        // With mainClock paused this does not advance the animation sample.
        Thread.sleep(80)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/dismiss-$suffix-$name.png").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }
    private fun step(name: String, millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        frame(name)
    }
    @Test fun editorAtCurrentWindowSize() {
        compose.openRoute("Tasks")
        compose.onNodeWithContentDescription("New task. Swipe up for views").performClick()
        compose.onNodeWithContentDescription("Title, task").performTextInput("A draft worth keeping")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Cancel editor").performClick()
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        frame("editor-adaptive")
        compose.onNodeWithText("Keep editing").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Title, task").assertTextContains("A draft worth keeping")
    }
    @Test fun review() {
        compose.openRoute("Tasks")
        compose.onNodeWithTag("task-list").performScrollToNode(hasContentDescription("Open task: Buy flowers"))
        compose.onNodeWithContentDescription("Open task: Buy flowers").performClick()
        if (compose.hasDescribedNode("Edit Buy flowers")) {
            compose.onNodeWithContentDescription("Edit Buy flowers").performClick()
        }
        compose.onNodeWithContentDescription("Task title").performTextReplacement("Buy flowers for dinner")
        Espresso.closeSoftKeyboard()
        frame("task-edited")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Cancel task editing").performClick()
        step("task-080", 80)
        step("task-160", 80)
        step("task-rest", 700)
        compose.onNodeWithText("Save your changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        step("task-return-080",80)
        step("task-return-rest",700)
        compose.mainClock.autoAdvance = true
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Cancel task editing").performClick()
        compose.onNodeWithText("Save your changes?").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Discard changes and close").performClick()
        step("discard-080",80)
        step("discard-160",80)
        compose.mainClock.autoAdvance = true
        awaitNoDescribed("Cancel task editing")
        compose.onNodeWithContentDescription("New task. Swipe up for views").performClick()
        compose.onNodeWithContentDescription("Title, task").performTextInput("Pick up a little something")
        Espresso.closeSoftKeyboard()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Cancel editor").performClick()
        step("editor-080",80)
        step("editor-rest",700)
        compose.mainClock.autoAdvance = true
    }
}
class UnsavedChangesLightCapture : UnsavedChangesCapture("light", CalinoThemeChoice.Light)
class UnsavedChangesDarkCapture : UnsavedChangesCapture("dark", CalinoThemeChoice.Dark)
