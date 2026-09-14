package calino.malinov.ski.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * The widget's idea of "today", as observable state.
 *
 * A plain `LocalDate.now()` in the composition looks right and is not: Glance
 * recomposes only the scopes an update *invalidated*, so a composable that
 * reads nothing observable is skipped, and the widget would keep yesterday's
 * heading for as long as its session stayed alive. Reading the date from here
 * makes the date roll an actual state change, which is what
 * [CalinoWidgetReceiver] is listening for `DATE_CHANGED` in order to cause.
 *
 * Process-wide and deliberately not durable: if the process dies, the next
 * `provideGlance` reads the clock fresh anyway.
 */
internal object WidgetClock {

    var today: LocalDate by mutableStateOf(LocalDate.now())
        private set

    /** Re-read the wall clock. Safe from any thread. */
    fun refresh() {
        val now = LocalDate.now()
        if (now != today) today = now
    }
}
