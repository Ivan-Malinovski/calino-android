package calino.malinov.ski.notify

import calino.malinov.ski.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/**
 * The two channels a reminder can arrive on.
 *
 * They are the ones the Notifications surface has always listed, minus the
 * "Daily brief" row: a daily summary is a feature nobody has built, and a
 * channel a user can configure but that never delivers anything is worse than
 * no row at all.
 *
 * Creation is idempotent and is attempted from everywhere that might be the
 * first code to run in a process -- the Activity and each receiver -- because a
 * notification posted to a channel that does not exist is silently dropped, and
 * a reboot receiver may well be the first thing to run after an update.
 */
object ReminderChannels {

    const val Events = "calino.reminders.events"
    const val Tasks = "calino.reminders.tasks"

    fun channelFor(kind: ReminderKind): String =
        if (kind == ReminderKind.Event) Events else Tasks

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(Events, context.getString(R.string.sys_events_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.sys_events_description)
                // Deliberately silent: the app's whole posture is calm, and a
                // person who wants a sound can set one per channel in Android.
                setSound(null, null)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(Tasks, context.getString(R.string.sys_tasks_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.sys_tasks_description)
                setSound(null, null)
            },
        )
    }

    /** What the Notifications surface shows, read from Android rather than guessed. */
    fun state(context: Context): List<ReminderChannelState> {
        val compat = NotificationManagerCompat.from(context)
        val appEnabled = compat.areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return listOf(
                ReminderChannelState(Events, context.getString(R.string.sys_events_channel), appEnabled, context.getString(R.string.sys_default)),
                ReminderChannelState(Tasks, context.getString(R.string.sys_tasks_channel), appEnabled, context.getString(R.string.sys_default)),
            )
        }
        return listOf(Events, Tasks).mapNotNull { id ->
            val channel = compat.getNotificationChannel(id) ?: return@mapNotNull null
            ReminderChannelState(
                id = id,
                name = channel.name?.toString() ?: id,
                enabled = appEnabled && channel.importance != NotificationManager.IMPORTANCE_NONE,
                importanceLabel = importanceLabel(context, channel.importance),
            )
        }
    }

    private fun importanceLabel(context: Context, importance: Int): String = when {
        importance == NotificationManager.IMPORTANCE_NONE -> context.getString(R.string.sys_off)
        importance <= NotificationManager.IMPORTANCE_MIN -> context.getString(R.string.sys_minimum)
        importance <= NotificationManager.IMPORTANCE_LOW -> context.getString(R.string.sys_low)
        importance <= NotificationManager.IMPORTANCE_DEFAULT -> context.getString(R.string.sys_default)
        else -> context.getString(R.string.sys_high)
    }
}

data class ReminderChannelState(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val importanceLabel: String,
)
