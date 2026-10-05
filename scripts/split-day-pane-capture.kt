package calino.malinov.ski

import android.content.pm.ActivityInfo
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.util.CalinoThemeChoice
import org.junit.After
import org.junit.Test

/** Opt-in: copy into androidTest, run on emulator-5554 at tablet size, then remove the copy. */
abstract class SplitDayPaneCapture(private val suffix: String, theme: CalinoThemeChoice) : CalinoUiTest(theme = theme) {
    private fun frame(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/split-pane-$suffix-$name.png").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }

    @Test fun captureSharedPanesAndYearPaging() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) {
            compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        }
        frame("month-rest")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views")
            .performTouchInput { swipeLeft() }
        for (step in listOf(16L, 48L, 80L)) {
            compose.mainClock.advanceTimeBy(step)
            frame("month-to-year-$step")
            compose.onAllNodesWithTag(CalinoTestActions.DayPanePager).assertCountEquals(1)
        }
        compose.mainClock.autoAdvance = true
        frame("year-rest")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views")
            .performTouchInput { swipeRight() }
        for (step in listOf(16L, 48L, 80L)) {
            compose.mainClock.advanceTimeBy(step)
            frame("year-to-month-$step")
            compose.onAllNodesWithTag(CalinoTestActions.DayPanePager).assertCountEquals(1)
        }
        compose.mainClock.autoAdvance = true
        frame("month-return")
        compose.openRoute("Year")
        awaitDescribed("Previous year")
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("May 2026, ", substring = true).performClick()
        for (step in listOf(16L, 48L, 80L)) {
            compose.mainClock.advanceTimeBy(step)
            frame("year-tile-to-month-$step")
            compose.onAllNodesWithTag(CalinoTestActions.DayPanePager).assertCountEquals(1)
        }
        compose.mainClock.autoAdvance = true
        frame("month-from-year-tile")
        androidx.test.espresso.Espresso.pressBack()
        frame("year-after-back")
        compose.onNodeWithTag(CalinoTestActions.DayPanePager).performTouchInput {
            down(percentOffset(.85f, .65f))
            moveTo(percentOffset(.45f, .65f), 600)
        }
        frame("year-mid-drag")
        awaitDescribed("Add on Mon, 18 May. Swipe up for views")
        compose.onNodeWithTag(CalinoTestActions.DayPanePager).performTouchInput { cancel() }
        frame("year-cancelled")
        compose.onNodeWithTag(CalinoTestActions.DayPanePager).performTouchInput { swipeLeft(durationMillis = 120) }
        frame("year-next-day")
        awaitDescribed("Add on Tue, 19 May. Swipe up for views")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Hide day pane").performClick()
        compose.mainClock.advanceTimeBy(80)
        frame("year-mid-collapse")
        compose.mainClock.autoAdvance = true
        frame("year-collapsed")
        compose.onNodeWithContentDescription("Show day pane").performClick()
        frame("year-reopened")
        compose.openRoute("Month")
        frame("month-next-day")
    }

    @After fun restorePortrait() {
        compose.mainClock.autoAdvance = true
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
class SplitDayPaneLightCapture : SplitDayPaneCapture("light", CalinoThemeChoice.Light)
class SplitDayPaneDarkCapture : SplitDayPaneCapture("dark", CalinoThemeChoice.Dark)
