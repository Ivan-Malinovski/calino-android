package calino.malinov.ski.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import calino.malinov.ski.state.CalinoPreferenceStore
import calino.malinov.ski.util.WidgetEarlierToday
import calino.malinov.ski.util.WidgetLookAhead

/**
 * The widget settings, as observable state.
 *
 * The same reason [WidgetClock] exists. `provideGlance` runs once per session and
 * an update only recomposes the scopes something observable invalidated, so a
 * preference read straight from the store inside the content lambda -- or
 * captured before `provideContent` -- is frozen at whatever it was when the
 * session started. The widget would render, and keep ignoring the setting.
 * Reading it from here makes a change an actual state write, which
 * [CalinoWidgets.update] performs before it asks the widgets to redraw.
 *
 * Process-wide and not durable: the preference store is the record, this is
 * only its mirror for the composition.
 */
internal object WidgetSettings {

    var lookAhead: WidgetLookAhead by mutableStateOf(WidgetLookAhead.Default)
        private set

    var earlierToday: WidgetEarlierToday by mutableStateOf(WidgetEarlierToday.Default)
        private set

    /** Re-read the store. Equal values are not written, so an update that changed nothing recomposes nothing. */
    fun refresh(store: CalinoPreferenceStore) {
        val look = store.loadWidgetLookAhead()
        if (look != lookAhead) lookAhead = look
        val earlier = store.loadWidgetEarlierToday()
        if (earlier != earlierToday) earlierToday = earlier
    }
}
