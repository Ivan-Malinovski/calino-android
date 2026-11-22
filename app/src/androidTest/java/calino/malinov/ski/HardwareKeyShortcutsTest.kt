package calino.malinov.ski

import android.view.KeyEvent
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A keyboard cover on a Fold is a real keyboard. Search and a new event are one
 * chord away, and Esc closes what is open without walking out of the app.
 *
 * The events go through `Activity.dispatchKeyEvent`, which is the path a real
 * keyboard takes, not through a focused node -- so this also proves the
 * shortcuts work before anything on screen has focus.
 */
@RunWith(AndroidJUnit4::class)
class HardwareKeyShortcutsTest : CalinoUiTest() {

    @Test fun commandKOpensSearch() {
        sendKey(KeyEvent.KEYCODE_K, ctrl = true)

        compose.waitUntil(WaitMillis) { compose.hasDescribedNode("Search Calino") }
    }

    @Test fun commandNOpensANewEvent() {
        sendKey(KeyEvent.KEYCODE_N, ctrl = true)

        compose.waitUntil(WaitMillis) { compose.hasDescribedNode("Title, event") }
    }

    @Test fun escapeClosesSearchWithoutLeavingTheApp() {
        sendKey(KeyEvent.KEYCODE_K, ctrl = true)
        compose.waitUntil(WaitMillis) { compose.hasDescribedNode("Search Calino") }

        sendKey(KeyEvent.KEYCODE_ESCAPE)
        compose.waitUntil(WaitMillis) { !compose.hasDescribedNode("Search Calino") }
        // Still on the calendar, with its own chrome in place.
        compose.onNodeWithContentDescription("Agenda for Monday, May 18").assertExists()
    }

    private fun sendKey(keyCode: Int, ctrl: Boolean = false) {
        compose.activityRule.scenario.onActivity { activity ->
            val meta = if (ctrl) KeyEvent.META_CTRL_ON else 0
            activity.dispatchKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        }
        compose.waitForIdle()
    }

    private companion object {
        const val WaitMillis = 8_000L
    }
}
