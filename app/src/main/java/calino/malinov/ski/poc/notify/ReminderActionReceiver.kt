package calino.malinov.ski.poc.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import calino.malinov.ski.poc.data.CalinoContainer
import calino.malinov.ski.poc.data.repository.WriteResult
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The buttons in the shade.
 *
 * Snooze is local and instant: it moves the firing in the durable schedule and
 * re-arms. The other two are CalDAV writes, and they may arrive in a process
 * that has no account restored and no snapshot loaded, so they go through the
 * process-wide container rather than a ViewModel, and park in
 * [ReminderActionQueue] when the record cannot be resolved in time.
 *
 * Every path replaces the notification with a line saying what happened.
 * A button in the shade that appears to do nothing reads as a bug, and
 * "queued" is a true and useful thing to say when offline.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in Handled) return
        val key = intent.getStringExtra(ReminderActions.ExtraKey) ?: return

        val app = context.applicationContext
        val store = Reminders.scheduleStore(app)
        val notifier = Reminders.notifier(app)
        val firing = store.load().firings.firstOrNull { it.key == key } ?: return
        val now = Instant.now()

        if (action == ReminderActions.Snooze) {
            store.snooze(key, now.plus(SnoozeFor))
            notifier.cancel(firing.notificationId)
            Reminders.scheduler(app).syncNextAlarm(now)
            return
        }

        // A write needs the data layer, which may have to start from cold.
        val pending = goAsync()
        val container = CalinoContainer.get(app)
        container.scope.launch {
            try {
                container.ensureConnected()
                val result = withTimeoutOrNull(WriteTimeoutMillis) {
                    applyToTask(container, action, firing)
                }
                val message = when (result) {
                    is WriteResult.Applied<*> -> appliedMessage(action)
                    is WriteResult.Queued<*> -> "${appliedMessage(action)} · will sync"
                    is WriteResult.Rejected -> result.reason
                    // Nothing resolved in time: park it rather than lose it.
                    null -> {
                        Reminders.actionQueue(app).enqueue(
                            ReminderAction(action, firing.key, firing.recordId, firing.kind, now),
                        )
                        "Saved · will apply when Calino next opens"
                    }
                }
                store.markDelivered(key, now)
                notifier.postConfirmation(firing, message)
                Reminders.scheduler(app).syncNextAlarm(Instant.now())
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun applyToTask(
        container: CalinoContainer,
        action: String,
        firing: ReminderFiring,
    ): WriteResult<*>? {
        val repository = awaitTask(container, firing.recordId) ?: return null
        return when (action) {
            ReminderActions.MarkDone -> repository.setTaskDone(firing.recordId, true)
            ReminderActions.Tomorrow -> repository.rescheduleTask(
                firing.recordId,
                LocalDate.now(ZoneId.systemDefault()).plusDays(1),
            )
            else -> null
        }
    }

    /**
     * Wait for a snapshot that actually contains the task.
     *
     * A cold process publishes the disk cache before it has finished talking to
     * the server, so this usually returns on the first poll; the loop is for the
     * case where it does not, and the caller's timeout bounds it.
     */
    private suspend fun awaitTask(
        container: CalinoContainer,
        taskId: String,
    ): calino.malinov.ski.poc.data.repository.CalinoRepository? {
        repeat(PollAttempts) {
            val repository = container.activeRepository
            if (repository.snapshot().tasks.any { it.id == taskId }) return repository
            delay(PollIntervalMillis)
        }
        return null
    }

    private fun appliedMessage(action: String): String = when (action) {
        ReminderActions.MarkDone -> "Marked done"
        ReminderActions.Tomorrow -> "Moved to tomorrow"
        else -> "Done"
    }

    private companion object {
        val Handled = setOf(ReminderActions.Snooze, ReminderActions.MarkDone, ReminderActions.Tomorrow)
        val SnoozeFor: Duration = Duration.ofMinutes(5)

        /**
         * A broadcast receiver is given about ten seconds before the system
         * considers the process stuck; this stays comfortably inside it and
         * falls back to the durable queue rather than overrunning.
         */
        const val WriteTimeoutMillis = 7_000L
        const val PollAttempts = 20
        const val PollIntervalMillis = 250L
    }
}
