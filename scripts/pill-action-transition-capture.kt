package calino.malinov.ski

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.util.CalinoThemeChoice
import org.junit.Test

/** Copy into androidTest temporarily; sample actual Save and confirmation entrances. */
abstract class PillActionTransitionCapture(private val themeName: String, theme: CalinoThemeChoice) : CalinoUiTest(theme = theme) {
    private fun frame(name: String) {
        compose.waitForIdle()
        Thread.sleep(80) // Present the paused Compose frame to SurfaceFlinger.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pill-action-$themeName-$name.png").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }
    private fun entrance(name: String) {
        var previous = 0L
        for (millis in listOf(32L, 80L, 144L, 224L, 320L, 480L, 800L)) {
            compose.mainClock.advanceTimeBy(millis - previous)
            previous = millis
            frame("$name-$millis")
        }
    }
    @Test fun review() {
        compose.openRoute("Tasks")
        compose.onNodeWithTag("task-list").performScrollToNode(hasContentDescription("Open task: Buy flowers"))
        compose.onNodeWithContentDescription("Open task: Buy flowers").performClick()
        if (compose.hasDescribedNode("Edit Buy flowers")) compose.onNodeWithContentDescription("Edit Buy flowers").performClick()
        frame("clean")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Task title").performTextReplacement("Buy flowers for dinner")
        entrance("save")
        compose.mainClock.autoAdvance = true
        Espresso.closeSoftKeyboard()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Delete task").performClick()
        entrance("delete")
        compose.onNodeWithText("Are you sure?").assertIsDisplayed()
        // Expiry returns to the normal actions without deleting anything.
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithContentDescription("Cancel task editing").performClick()
        entrance("unsaved")
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }
}
class PillActionLightCapture : PillActionTransitionCapture("light", CalinoThemeChoice.Light)
class PillActionDarkCapture : PillActionTransitionCapture("dark", CalinoThemeChoice.Dark)
