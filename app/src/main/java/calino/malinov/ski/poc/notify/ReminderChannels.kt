package calino.malinov.ski.poc.notify

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
            NotificationChannel(Events, "Events reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A quiet nudge before an event begins."
                // Deliberately silent: the app's whole posture is calm, and a
                // person who wants a sound can set one per channel in Android.
                setSound(null, null)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(Tasks, "Tasks due", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A reminder when a task reaches its date."
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
                ReminderChannelState(Events, "Events reminders", appEnabled, "Default"),
                ReminderChannelState(Tasks, "Tasks due", appEnabled, "Default"),
            )
        }
        return listOf(Events, Tasks).mapNotNull { id ->
            val channel = compat.getNotificationChannel(id) ?: return@mapNotNull null
            ReminderChannelState(
                id = id,
                name = channel.name?.toString() ?: id,
                enabled = appEnabled && channel.importance != NotificationManager.IMPORTANCE_NONE,
                importanceLabel = importanceLabel(channel.importance),
            )
        }
    }

    private fun importanceLabel(importance: Int): String = when {
        importance == NotificationManager.IMPORTANCE_NONE -> "Off"
        importance <= NotificationManager.IMPORTANCE_MIN -> "Minimum"
        importance <= NotificationManager.IMPORTANCE_LOW -> "Low importance"
        importance <= NotificationManager.IMPORTANCE_DEFAULT -> "Default"
        else -> "High · may peek"
    }
}

data class ReminderChannelState(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val importanceLabel: String,
)
