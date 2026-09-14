package calino.malinov.ski.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import calino.malinov.ski.MainActivity
import calino.malinov.ski.R

/**
 * Posting a reminder, and taking it away again.
 *
 * Every notification carries the deep link that reopens the record, plus the
 * actions the shade offers. Keeping the construction in one place is what makes
 * the "confirmation" path -- the notification a completed task is replaced by
 * -- look like the original rather than like a second, unrelated notification.
 */
class ReminderNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun post(firing: ReminderFiring) {
        ReminderChannels.ensure(context)
        val builder = NotificationCompat.Builder(context, ReminderChannels.channelFor(firing.kind))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(firing.title)
            .setContentText(firing.subtitle)
            .setWhen(firing.anchor.toEpochMilli())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(
                if (firing.kind == ReminderKind.Event) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .setAutoCancel(true)
            .setContentIntent(openIntent(firing))
            .addAction(0, "Snooze 5 min", actionIntent(firing, ReminderActions.Snooze))

        if (firing.kind == ReminderKind.Task) {
            builder
                .addAction(0, "Mark done", actionIntent(firing, ReminderActions.MarkDone))
                .addAction(0, "Tomorrow", actionIntent(firing, ReminderActions.Tomorrow))
        }

        notify(firing.notificationId, builder)
    }

    /**
     * Replace a reminder with the outcome of the button the user pressed.
     *
     * The notification is not simply cancelled: a tap in the shade that appears
     * to do nothing reads as a bug, and the write may still be queued offline,
     * which is exactly the state worth saying out loud.
     */
    fun postConfirmation(firing: ReminderFiring, message: String) {
        ReminderChannels.ensure(context)
        val builder = NotificationCompat.Builder(context, ReminderChannels.channelFor(firing.kind))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(firing.title)
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(ConfirmationTimeoutMillis)
            .setAutoCancel(true)
            .setContentIntent(openIntent(firing))
        notify(firing.notificationId, builder)
    }

    fun cancel(notificationId: Int) = manager.cancel(notificationId)

    /**
     * Posting is checked rather than attempted.
     *
     * Without `POST_NOTIFICATIONS` the framework drops the notification
     * silently, so the check costs nothing and makes the "reminders scheduled
     * but not permitted" state explicit -- which is exactly the state the
     * Notifications screen reports. The catch is belt and braces for a
     * receiver: a `SecurityException` here would take the whole broadcast down.
     */
    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        // The check is inline rather than in a helper so lint can see it, and
        // it is a check rather than a try because the framework drops the post
        // silently -- making the "scheduled but not permitted" state explicit
        // is exactly what the Notifications screen reports.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return
        // Belt and braces for a receiver: a SecurityException here would take
        // the whole broadcast down with it.
        runCatching { manager.notify(id, builder.build()) }
    }

    private fun openIntent(firing: ReminderFiring): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(ReminderDeepLinks.uri(firing))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            firing.notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun actionIntent(firing: ReminderFiring, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderActions.ExtraKey, firing.key)
        }
        return PendingIntent.getBroadcast(
            context,
            // The request code must differ per action, or the extras of the
            // first one would be reused for all of them.
            (firing.notificationId + action.hashCode()) and 0x7fffffff,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val ConfirmationTimeoutMillis = 8_000L
    }
}
