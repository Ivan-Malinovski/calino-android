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

    override val glanceAppWidget: GlanceAppWidget = CalinoLedgerWidget()

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

/**
 * The cards widget's provider.
 *
 * A separate receiver because it is a separate entry in the launcher's picker,
 * which is how the two layouts are chosen between. It wants the same date and
 * time broadcasts, so [CalinoWidgetReceiver] handles those for both -- a
 * broadcast reaches every matching receiver, and [CalinoWidgets.update] already
 * redraws both providers, so declaring the filter twice would only mean doing
 * the same work twice.
 */
class CalinoCardsWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CalinoCardsWidget()
}

/** Redrawing the widgets from anywhere, including a process with no Activity. */
object CalinoWidgets {

    /**
     * No-ops for a widget the user has not placed, which is the common case:
     * a bound-id lookup is cheaper than a render nobody sees, and it keeps the
     * sync bridge from doing work on every publish for nothing. Both providers
     * are checked separately -- having placed the ledger is no reason to render
     * the cards.
     */
    suspend fun update(context: Context) {
        val app = context.applicationContext
        // Every update path goes through here, so this is the one place that
        // has to notice the day changed.
        WidgetClock.refresh()
        val manager = GlanceAppWidgetManager(app)
        if (manager.getGlanceIds(CalinoLedgerWidget::class.java).isNotEmpty()) {
            CalinoLedgerWidget().updateAll(app)
        }
        if (manager.getGlanceIds(CalinoCardsWidget::class.java).isNotEmpty()) {
            CalinoCardsWidget().updateAll(app)
        }
    }
}
