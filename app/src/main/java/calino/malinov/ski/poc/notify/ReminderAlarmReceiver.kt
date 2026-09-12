package calino.malinov.ski.poc.notify

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
        store.due(now, Grace).forEach { firing ->
            notifier.post(firing)
            store.markDelivered(firing.key, now)
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
    }
}
