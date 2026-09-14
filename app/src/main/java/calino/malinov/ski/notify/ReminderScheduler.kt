package calino.malinov.ski.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.time.Instant

/**
 * One alarm, re-armed after each firing.
 *
 * Not one alarm per reminder. The schedule is a single file, so this keeps a
 * single thing consistent with it; a per-reminder scheme would have to diff
 * every new plan against the previous one, and an orphaned `PendingIntent`
 * whose firing no longer exists cannot be cancelled without a second
 * bookkeeping file to remember it by. The cost is one wakeup per reminder,
 * which is what per-reminder alarms cost anyway.
 */
class ReminderScheduler(
    private val context: Context,
    private val store: ReminderScheduleStore,
) {

    private val alarms: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    /**
     * Arm for the next pending firing, or cancel when there is none.
     *
     * Safe to call as often as anything changes: it is a pure function of the
     * stored schedule.
     */
    fun syncNextAlarm(now: Instant = Instant.now()) {
        val manager = alarms ?: return
        val next = store.next(now)
        val intent = alarmIntent()
        if (next == null) {
            manager.cancel(intent)
            return
        }
        val at = store.load().let { schedule -> schedule.snoozed[next.key] ?: next.at }
        val millis = at.toEpochMilli()
        if (exactAlarmsAllowed()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            // Honest degradation: Doze can hold this back. The Notifications
            // surface says so rather than letting a reminder look broken.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    fun cancel() {
        alarms?.cancel(alarmIntent())
    }

    /**
     * Whether exact delivery is available right now.
     *
     * `SCHEDULE_EXACT_ALARM` is declared rather than `USE_EXACT_ALARM`: a
     * calendar would qualify for the latter, but it cannot be revoked, and an
     * app that asks for a permission the user cannot take back is not the
     * posture this one has everywhere else. The cost is that this can flip to
     * false at any moment, so it is asked on every arm.
     */
    fun exactAlarmsAllowed(): Boolean {
        val manager = alarms ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /** Where to send the user to grant exact alarms, or null when irrelevant. */
    fun exactAlarmSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (exactAlarmsAllowed()) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun alarmIntent(): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ActionFire)
        return PendingIntent.getBroadcast(
            context,
            AlarmRequestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        /** Fixed: there is exactly one reminder alarm in flight at a time. */
        private const val AlarmRequestCode = 0x0CA1
    }
}
