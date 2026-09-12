package calino.malinov.ski.poc.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant

/**
 * Re-arm after the things that silently throw alarms away.
 *
 * A reboot clears every alarm; a package replacement does too; a clock or
 * timezone change moves what the alarm should have been set for.
 *
 * There is no repository here, so this cannot re-plan -- only re-arm what the
 * app last wrote down. After a timezone change that matters: the stored anchors
 * were computed in the old zone, so the alarm is re-armed on the stored instant
 * and the plan is left marked stale for the next foreground to correct. That is
 * a real, documented limitation, not an oversight: re-planning would mean
 * constructing the data layer and going to the network from a broadcast.
 *
 * The receiver is exported because system broadcasts require it, so it accepts
 * only the actions it recognises.
 */
class ReminderSystemReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in Handled) return

        val app = context.applicationContext
        ReminderChannels.ensure(app)
        Reminders.scheduler(app).syncNextAlarm(Instant.now())

    }

    private companion object {
        val Handled = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
