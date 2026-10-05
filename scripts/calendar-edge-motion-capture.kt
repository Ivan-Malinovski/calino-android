package calino.malinov.ski

import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.util.CalinoThemeChoice
import org.junit.Test

/** Opt-in: copy into androidTest for a pinned emulator run, then remove the copy. */
abstract class CalendarEdgeCapture(private val suffix: String, theme: CalinoThemeChoice) : CalinoUiTest(theme = theme) {
    private fun frame(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/calendar-edge-$suffix-$name.png").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }

    @Test fun captureBothSpatialReveals() {
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) {
            compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        }
        zoomTo(2)
        val month = compose.onNodeWithTag("calendar-zoom-surface").fetchSemanticsNode().boundsInRoot
        val start = Offset(month.right - 18f, month.top + month.height * .2f)
        frame("month-rest")
        compose.onRoot().performTouchInput { down(start) }
        for (distance in listOf(60f, 150f, 280f, 420f)) {
            compose.onRoot().performTouchInput { moveTo(start + Offset(0f, distance), 160) }
            frame("year-drag-${distance.toInt()}")
        }
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { advanceEventTime(160); up() }
        for (time in listOf(16L, 32L, 64L, 128L, 256L)) {
            compose.mainClock.advanceTimeBy(time)
            frame("year-settle-$time")
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        frame("year-rest")
        compose.openRoute("Month")
        zoomTo(1)
        compose.selectDayIn(CalinoTestActions.MonthPager, java.time.LocalDate.of(2026, 5, 6))
        compose.waitForIdle()
        zoomTo(0)
        val handle = compose.onNodeWithContentDescription(CalinoTestActions.zoomHandleLabel(0)).fetchSemanticsNode().boundsInRoot
        // Start well inside the calendar's day surface, where the short fixture
        // list has no more upward scroll to consume; keep every frame on-screen.
        val agendaStart = Offset(handle.right - 18f, handle.bottom + 440f)
        frame("week-rest")
        compose.onRoot().performTouchInput { down(agendaStart) }
        for (distance in listOf(60f, 150f, 280f, 420f)) {
            compose.onRoot().performTouchInput { moveTo(agendaStart - Offset(0f, distance), 160) }
            frame("agenda-drag-${distance.toInt()}")
        }
        compose.onRoot().performTouchInput { advanceEventTime(160); up() }
        compose.waitForIdle()
        frame("agenda-rest")
        compose.onNodeWithTag("agenda-month-list").assertIsDisplayed()
    }
}
class CalendarEdgeLightCapture : CalendarEdgeCapture("light", CalinoThemeChoice.Light)
class CalendarEdgeDarkCapture : CalendarEdgeCapture("dark", CalinoThemeChoice.Dark)
