package calino.malinov.ski.notify

import android.content.Context
import java.io.File

/**
 * The reminder subsystem's process-wide parts.
 *
 * Receivers cannot be constructed with dependencies, so they ask here. Every
 * piece is cheap and file-backed: getting hold of the scheduler does not put
 * the process on the network or wake the data layer.
 */
object Reminders {

    private const val ScheduleFileName = "reminder-schedule.json"
    private const val ActionFileName = "reminder-actions.json"

    @Volatile
    private var scheduleStore: ReminderScheduleStore? = null

    @Volatile
    private var actionQueue: ReminderActionQueue? = null

    fun scheduleStore(context: Context): ReminderScheduleStore =
        scheduleStore ?: synchronized(this) {
            scheduleStore ?: FileReminderScheduleStore(
                File(context.applicationContext.filesDir, ScheduleFileName),
            ).also { scheduleStore = it }
        }

    fun actionQueue(context: Context): ReminderActionQueue =
        actionQueue ?: synchronized(this) {
            actionQueue ?: ReminderActionQueue(
                File(context.applicationContext.filesDir, ActionFileName),
            ).also { actionQueue = it }
        }

    fun scheduler(context: Context): ReminderScheduler =
        ReminderScheduler(context.applicationContext, scheduleStore(context))

    fun notifier(context: Context): ReminderNotifier =
        ReminderNotifier(context.applicationContext)
}
