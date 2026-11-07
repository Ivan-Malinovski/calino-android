package calino.malinov.ski.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.Instant

/**
 * The alarm landed: post whatever is owed, then arm the next one.
 *
 * It posts everything [ReminderScheduleStore.due] reports, not only the firing
 * the alarm was set for. A device that was asleep, or that missed the exact
 * moment, can owe more than one; the grace window is what stops a machine that
 * was off for a week from emptying a fortnight of reminders into the shade at
 * once.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ActionFire) return
        val app = context.applicationContext
        val now = Instant.now()
        val store = Reminders.scheduleStore(app)
        val notifier = Reminders.notifier(app)

        ReminderChannels.ensure(app)
        val (nags, owed) = store.due(now, Grace).partition { it.nag }
        owed.forEach { firing ->
            notifier.post(firing)
            store.markDelivered(firing.key, now)
        }
        // Nags are the repeats of a reminder that was already missed, so a
        // slot shows the longest-overdue few and folds the rest into one line
        // instead of filling the shade.
        if (nags.isNotEmpty()) {
            val ordered = nags.sortedWith(compareBy({ it.anchor }, { it.key }))
            ordered.take(MaxNagsPerSlot).forEach { notifier.post(it) }
            val folded = ordered.size - MaxNagsPerSlot
            if (folded > 0) notifier.postNagSummary(folded) else notifier.cancelNagSummary()
            nags.forEach { store.markDelivered(it.key, now) }
        }
        Reminders.scheduler(app).syncNextAlarm(now)
    }

    companion object {
        const val ActionFire = "calino.reminder.FIRE"

        /**
         * How stale a missed reminder may be and still be worth posting. Long
         * enough to survive Doze and a slow boot; short enough that yesterday's
         * meeting does not announce itself this morning.
         */
        private val Grace: Duration = Duration.ofHours(2)

        /** Overdue tasks posted individually in one slot; the rest are summarised. */
        const val MaxNagsPerSlot = 3
    }
}
