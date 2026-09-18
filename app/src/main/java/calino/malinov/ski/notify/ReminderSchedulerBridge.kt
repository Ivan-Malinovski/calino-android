package calino.malinov.ski.notify

import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.state.CalinoPreferenceStore
import java.io.Closeable
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the durable schedule level with the repository.
 *
 * `CalDavRepository.publish()` is the one funnel every change passes through --
 * an optimistic edit, a queue replay, a sync reconciliation -- so observing the
 * repository is already "after a sync that changes a scheduled record" and no
 * new hook is needed. What it is not is *quiet*: a single reload publishes
 * several times, so emissions are conflated and only the latest is planned.
 */
class ReminderSchedulerBridge(
    private val preferences: CalinoPreferenceStore,
    private val store: ReminderScheduleStore,
    private val scheduler: ReminderScheduler,
    /**
     * The calendars currently projected into `CalendarContract`, or empty
     * when nothing is.
     *
     * Read fresh on every plan rather than captured: projection can be turned
     * off between one sync and the next, and a stale set would leave a
     * calendar with nobody reminding for it.
     */
    private val projectedCalendarIds: () -> Set<String> = { emptySet() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = { Instant.now() },
) {

    private val snapshots = MutableSharedFlow<CalinoSnapshot>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var subscription: Closeable? = null

    /**
     * Follow [repository] until the returned handle is closed.
     *
     * `collectLatest` is doing the conflation: a burst of publishes cancels the
     * half-finished plan and re-plans from the newest snapshot, which is both
     * cheaper and more correct than planning each one.
     */
    fun attach(repository: CalinoRepository, scope: CoroutineScope): Closeable {
        detach()
        val job = scope.launch {
            snapshots.collectLatest { snapshot -> refresh(snapshot) }
        }
        val handle = repository.observe { snapshot -> snapshots.tryEmit(snapshot) }
        subscription = Closeable {
            handle.close()
            job.cancel()
        }
        return Closeable { detach() }
    }

    fun detach() {
        subscription?.close()
        subscription = null
    }

    /** Re-plan from [snapshot] and re-arm. Safe to call from anywhere. */
    fun refresh(snapshot: CalinoSnapshot) {
        val at = now()
        val currentZone = zone()
        val firings = ReminderPlanner.plan(
            snapshot = snapshot,
            now = at,
            zone = currentZone,
            options = ReminderPlanOptions(
                eventRemindersEnabled = preferences.loadEventRemindersEnabled(),
                taskRemindersEnabled = preferences.loadTaskRemindersEnabled(),
                // Only when the person has handed delivery over. Both halves
                // are required: a projected calendar with the setting off is
                // still Calino's to remind for, and the setting on with
                // nothing projected changes nothing.
                providerOwnedCalendarIds = if (preferences.loadProviderRemindersEnabled()) {
                    projectedCalendarIds()
                } else {
                    emptySet()
                },
            ),
        )
        store.replace(firings, at, currentZone)
        scheduler.syncNextAlarm(at)
    }

    /** Re-plan from whatever was last observed; used when a preference changes. */
    fun refresh() {
        snapshots.replayCache.lastOrNull()?.let(::refresh)
    }

    /**
     * Drop everything scheduled: no account, or reminders turned off entirely.
     * Notifications already in the shade are left alone -- they are the user's
     * to dismiss, and withdrawing one they have not read is worse than leaving
     * it.
     */
    fun clear() {
        store.replace(emptyList(), now(), zone())
        scheduler.cancel()
    }
}
