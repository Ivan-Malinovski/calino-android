package calino.malinov.ski.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import calino.malinov.ski.data.repository.PendingChange
import calino.malinov.ski.data.repository.PendingChangeState
import calino.malinov.ski.data.repository.displayTitle

/**
 * Tells the person, outside the app, that a saved change could not sync.
 *
 * Background sync can dead-letter a write while Calino is closed; without this
 * the only sign would be a dot on a calendar heading the next time the app is
 * opened. It posts once per newly failed change, never repeats for one it has
 * already announced, and withdraws itself when nothing needs attention. It is
 * local only: the text comes from the device's own queue.
 */
object SyncIssueNotifier {

    const val Channel = "calino.sync.issues"
    private const val NotificationId = 0x5_1A_C0
    private const val Prefs = "calino.sync.issues"
    private const val KeyAnnounced = "announced"

    /** The address a sync-problem notification opens. */
    const val DeepLink = "calino.malinov.ski://sync/issues"

    fun isDeepLink(raw: String?): Boolean =
        raw != null && raw.equals(DeepLink, ignoreCase = true)

    /**
     * Posts for changes that newly need attention when [post] is true, and
     * clears the notification once none do. The foreground app passes false:
     * it has its own in-app alert and only needs stale notifications removed.
     */
    fun reconcile(context: Context, changes: List<PendingChange>, post: Boolean) {
        val attention = changes.filter { it.state == PendingChangeState.DEAD_LETTER }
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val announced = prefs.getStringSet(KeyAnnounced, emptySet()).orEmpty()
        val manager = NotificationManagerCompat.from(context)
        if (attention.isEmpty()) {
            manager.cancel(NotificationId)
            prefs.edit().remove(KeyAnnounced).apply()
            return
        }
        val fresh = attention.filter { it.id !in announced }
        // Forget resolved ids so the set cannot grow without bound.
        prefs.edit().putStringSet(KeyAnnounced, announced intersect attention.map { it.id }.toSet()).apply()
        if (!post || fresh.isEmpty()) return

        ensureChannel(context)
        val title = if (attention.size == 1) {
            attention.single().displayTitle()?.let { context.getString(R.string.sys_sync_item, it) } ?: context.getString(R.string.sys_sync_change)
        } else {
            context.getString(R.string.sys_sync_changes, attention.size)
        }
        val body = fresh.first().lastFailure?.message ?: context.getString(R.string.sys_sync_retry)
        val builder = NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context))
            .addAction(0, context.getString(R.string.sys_review), openIntent(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(NotificationId, builder.build()) }.onSuccess {
            prefs.edit().putStringSet(KeyAnnounced, attention.map { it.id }.toSet()).apply()
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(Channel, context.getString(R.string.sys_sync_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.sys_sync_description)
                setSound(null, null)
            },
        )
    }

    private fun openIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(DeepLink)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            NotificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
