package calino.malinov.ski

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.util.CalinoThemeChoice
import org.junit.Test

abstract class PillAuditCapture(private val suffix: String, private val menu: Boolean, theme: CalinoThemeChoice) : CalinoUiTest(menuPill = menu, theme = theme) {
    private fun frame(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/pill-$suffix-$name.png").use {
            ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }
    private fun step(name: String, millis: Long) {
        // Let externally timed write state reach composition before sampling its animation.
        var remaining = millis
        while (remaining > 0) {
            val slice = minOf(16L, remaining)
            compose.mainClock.advanceTimeBy(slice)
            compose.waitForIdle()
            remaining -= slice
        }
        frame(name)
    }
    private fun settle() { compose.mainClock.autoAdvance = true; compose.waitForIdle() }
    @Test fun review() {
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        frame("root")
        if (menu) {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Views, current: Month").performClick()
            step("menu-080", 80); step("menu-160", 80); step("menu-rest", 650)
            Espresso.pressBack()
            step("menu-close-080", 80); step("menu-close-160", 80); step("menu-closed", 650)
            settle()
            compose.onNodeWithContentDescription("Views, current: Month").performTouchInput { longClick() }
            compose.mainClock.advanceTimeBy(800)
            frame("dock")
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Add").performClick()
            step("types-080",80); step("types-rest",650)
            compose.onNodeWithContentDescription("New task").performClick()
        } else {
            compose.openRoute("Tasks")
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("New task. Swipe up to search").performClick()
        }
        step("editor-enter-080",80); step("editor-enter-rest",650)
        settle()
        compose.onNodeWithContentDescription("Title, task").performTextInput("Pill motion review")
        Espresso.closeSoftKeyboard()
        frame("editor")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Save editor").performClick()
        step("saving-exit-080",80); step("saving-exit-160",80); step("saving-exit-224",64)
        Thread.sleep(260)
        step("saving-root",16)
        compose.waitUntil(3_000) { compose.mainClock.advanceTimeByFrame(); compose.hasDescribedNode("Saved") }
        step("saved-080",80); step("saved-160",80); step("saved-ring",260); step("saved-fade",420)
        settle()
        awaitNoDescribed("Saved")
        frame("saved-return-rest")
        if (!menu) compose.openRoute("Month")
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Lunch with Maya, 12:30 PM, Café Lumen").performClick()
        step("event-enter-080",80); step("event-enter-rest",650)
        val titles = compose.onAllNodesWithContentDescription("Event title")
        val visibleTitle = titles.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.left >= 0f && it.boundsInRoot.width > 0f }
        titles[visibleTitle].performTextInput(" revised")
        Espresso.closeSoftKeyboard()
        step("event-dirty-080",80); step("event-dirty-rest",650)
        compose.onNodeWithContentDescription("Save event changes").performClick()
        compose.waitUntil(3_000) { compose.mainClock.advanceTimeByFrame(); compose.hasDescribedNode("Saved") }
        step("inline-saved-080",80); step("inline-saved-ring",340); step("inline-saved-fade",420)
        settle()
        awaitNoDescribed("Saved")
        frame("inline-restored")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Delete event").performClick()
        step("delete-080",80); step("delete-160",80); step("delete-ring",1_200)
        compose.mainClock.advanceTimeBy(2_500)
        step("delete-expire-080",80); step("delete-expired",650)
        compose.onNodeWithContentDescription("Delete event").performClick()
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithContentDescription("Confirm Delete event").performClick()
        step("remove-exit-080",80); step("remove-exit-224",144)
        Thread.sleep(260)
        step("removing-root",16)
        compose.waitUntil(3_000) { compose.mainClock.advanceTimeByFrame(); compose.hasDescribedNode("Removed") }
        step("removed-080",80); step("removed-ring",340); step("removed-fade",420)
        settle()
        awaitNoDescribed("Removed")
        frame("removed-return-rest")
        compose.openRoute("Journal")
        compose.onNodeWithContentDescription("Open journal entry A clear Monday").performScrollTo().performClick()
        awaitDescribed("Edit journal entry")
        frame("journal-read")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Edit journal entry").performClick()
        step("journal-edit-080",80); step("journal-edit-rest",650)
        compose.onNodeWithContentDescription("Journal title").performTextInput(" revised")
        Espresso.closeSoftKeyboard()
        step("journal-dirty-080",80); step("journal-dirty-rest",650)
        compose.onNodeWithContentDescription("Save journal entry").performClick()
        compose.mainClock.advanceTimeBy(250)
        Thread.sleep(600)
        compose.mainClock.advanceTimeBy(16)
        settle()
        compose.openRoute("Tasks")
        compose.onNodeWithTag("task-list").performScrollToNode(hasContentDescription("Open task: Buy flowers"))
        compose.onNodeWithContentDescription("Open task: Buy flowers").performClick()
        awaitDescribed("Cancel task editing")
        frame("task-clean")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Task title").performTextInput(" revised")
        Espresso.closeSoftKeyboard()
        step("task-dirty-080",80); step("task-dirty-rest",650)
        compose.onNodeWithContentDescription("Save task").performClick()
        compose.mainClock.advanceTimeBy(250)
        Thread.sleep(600)
        compose.mainClock.advanceTimeBy(16)
        settle()
        compose.openRoute("Contacts")
        compose.onNodeWithContentDescription("Open contact Ada Lovelace").performScrollTo().performClick()
        awaitDescribed("Edit contact")
        frame("contact-read")
        compose.onNodeWithContentDescription("Edit contact").performClick()
        awaitDescribed("Cancel contact editing")
        frame("contact-edit")
    }
}
class PillAuditMenuLightTest : PillAuditCapture("menu-light", true, CalinoThemeChoice.Light)
class PillAuditMenuDarkTest : PillAuditCapture("menu-dark", true, CalinoThemeChoice.Dark)
class PillAuditClassicLightTest : PillAuditCapture("classic-light", false, CalinoThemeChoice.Light)
class PillAuditClassicDarkTest : PillAuditCapture("classic-dark", false, CalinoThemeChoice.Dark)
