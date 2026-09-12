package calino.malinov.ski.poc.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The widget's provider, plus the date-and-time broadcasts it cares about.
 *
 * The widget shows "today", so it has to redraw when today changes. The three
 * extra actions below are on the system's exemption list for Android 8's
 * implicit-broadcast restrictions, which is what lets them be declared in the
 * manifest -- so a date roll costs one broadcast rather than a periodic
 * `updatePeriodMillis` poll, which the backlog item rules out explicitly.
 */
class CalinoWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CalinoAgendaWidget()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        CalinoWidgets.update(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }
}

/** Redrawing the widget from anywhere, including a process with no Activity. */
object CalinoWidgets {

    /**
     * No-ops when the user has not placed a widget, which is the common case:
     * a bound-id lookup is cheaper than a render nobody sees, and it keeps the
     * sync bridge from doing work on every publish for nothing.
     */
    suspend fun update(context: Context) {
        val widget = CalinoAgendaWidget()
        val app = context.applicationContext
        // Every update path goes through here, so this is the one place that
        // has to notice the day changed.
        WidgetClock.refresh()
        if (GlanceAppWidgetManager(app).getGlanceIds(CalinoAgendaWidget::class.java).isEmpty()) return
        widget.updateAll(app)
    }
}
