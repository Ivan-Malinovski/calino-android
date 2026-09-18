package calino.malinov.ski.data

import android.content.Context
import calino.malinov.ski.data.caldav.CalDavConnectionManager
import calino.malinov.ski.data.caldav.CalDavDiscovery
import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.CalDavWriter
import calino.malinov.ski.data.caldav.CardDavWriter
import calino.malinov.ski.data.caldav.CredentialStore
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.FileCalendarCache
import calino.malinov.ski.data.caldav.KeystoreCredentialStore
import calino.malinov.ski.data.caldav.SharedPreferencesAccountPersistence
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.CalDavForm
import calino.malinov.ski.data.repository.CalDavAccountStore
import calino.malinov.ski.data.repository.CalDavClient
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.data.repository.FilePendingChangeStore
import calino.malinov.ski.data.repository.FixtureRepository
import calino.malinov.ski.data.repository.WriteResult
import calino.malinov.ski.data.repository.visibleCalendarIds
import calino.malinov.ski.notify.ReminderActions
import calino.malinov.ski.notify.ReminderSchedulerBridge
import calino.malinov.ski.notify.Reminders
import calino.malinov.ski.platform.CalendarProjection
import calino.malinov.ski.platform.CalendarProjectionBridge
import calino.malinov.ski.platform.CalinoAccounts
import calino.malinov.ski.state.CalinoPreferenceStore
import calino.malinov.ski.state.SharedPreferencesPreferenceStore
import calino.malinov.ski.widget.CalinoWidgetBridge
import java.io.File
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The data layer, owned by the process rather than by the Activity.
 *
 * It used to live inside `PocRepositoryViewModel`, which was correct while the
 * only thing that read calendar data was the UI. Notification actions broke
 * that: "Mark done" arrives in a `BroadcastReceiver`, possibly in a process
 * that has no Activity at all, and it still has to write through the same
 * repository, the same durable queue and the same cache. Two instances of any
 * of those would mean two write queues racing over one file.
 *
 * So the construction moved here and the ViewModel became a Compose-state
 * facade over it. Nothing about durability changed -- every store underneath
 * was already file- or SharedPreferences-backed -- only the owner did.
 *
 * Nothing here touches the network on construction. A process woken only to
 * re-arm an alarm reads a JSON file and goes back to sleep; [ensureConnected]
 * is what a caller that genuinely needs the server calls, and it is idempotent.
 */
class CalinoContainer private constructor(context: Context) {

    private val application = context.applicationContext

    /**
     * Lives as long as the process. Deliberately not a `viewModelScope`: a
     * write started from the shade must not be cancelled because the Activity
     * that happened to be open went away.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http = DavHttp()

    val fixtureRepository = FixtureRepository()

    private val credentialStore: CredentialStore = KeystoreCredentialStore(application)

    val accountStore = CalDavAccountStore(SharedPreferencesAccountPersistence(application))

    val preferenceStore: CalinoPreferenceStore = SharedPreferencesPreferenceStore(application)

    val calDavClient: CalDavClient = CalDavDiscovery(http)

    private val calendarCache = FileCalendarCache(File(application.filesDir, "caldav-cache"))

    private val pendingChangeStore =
        FilePendingChangeStore(File(application.filesDir, "caldav-write-queue.json"))

    val calDavRepository = CalDavRepository(
        fetcher = CalDavFetcher(http),
        scope = scope,
        cache = calendarCache,
        writer = CalDavWriter(http, calendarCache),
        cardWriter = CardDavWriter(http, calendarCache),
        pendingStore = pendingChangeStore,
    )

    private val connections = CalDavConnectionManager(
        accountStore = accountStore,
        credentialStore = credentialStore,
        repository = calDavRepository,
        discovery = CalDavDiscovery(http),
        scope = scope,
    )

    private val repositoryListeners = CopyOnWriteArrayList<(CalinoRepository) -> Unit>()

    /**
     * The repository in force. With no account connected this is the frozen
     * fixture data, so the app is never an empty shell.
     */
    var activeRepository: CalinoRepository = fixtureRepository
        private set

    val hasAccounts: Boolean get() = accountStore.accounts().isNotEmpty()

    @Volatile
    private var connected = false

    @Volatile
    private var cacheRestored = false

    @Volatile
    private var draining = false

    @Volatile
    private var scheduling = false

    @Volatile
    private var widgetUpdating = false

    @Volatile
    private var projecting = false

    /**
     * Keeps the durable reminder schedule level with whatever the repository
     * is currently publishing.
     */
    /** Keeps the home screen widget level with the repository. */
    val widgetBridge = CalinoWidgetBridge(application)

    /**
     * Keeps `CalendarContract` level with the repository, while projection is
     * on. Attached by [startCalendarProjection] rather than at construction,
     * for the same reason the widget bridge is: a process woken to re-arm one
     * alarm has no business reconciling the calendar store.
     */
    val calendarProjectionBridge = CalendarProjectionBridge(
        context = application,
        accounts = { accountStore.accounts() },
        optedIn = { projectedCalendarIds },
    )

    val reminderBridge = ReminderSchedulerBridge(
        preferences = preferenceStore,
        store = Reminders.scheduleStore(application),
        scheduler = Reminders.scheduler(application),
        projectedCalendarIds = { projectedCalendars() },
    )

    init {
        updateActiveRepository()
    }

    /** Notified when the connected/fixture swap happens. */
    fun observeRepository(listener: (CalinoRepository) -> Unit): AutoCloseable {
        repositoryListeners += listener
        listener(activeRepository)
        return AutoCloseable { repositoryListeners -= listener }
    }

    /**
     * Restore persisted accounts and start talking to their servers.
     *
     * Idempotent, and never called from construction: a receiver that only
     * needs to re-arm an alarm should not put the process on the network.
     */
    fun ensureConnected() {
        if (connected) return
        connected = true
        cacheRestored = true
        connections.restore()
        updateActiveRepository()
        restoreProjection()
    }

    /**
     * Publish the disk cache, and stop there.
     *
     * For the home screen widget, which the launcher can ask to redraw in a
     * process with no Activity and no reason to be on the network. The raw
     * iCalendar cache is already the thing a cold launch renders from, so the
     * widget reads the same data through the same repository rather than
     * keeping a second copy of the agenda on disk.
     *
     * Deliberately not folded into [ensureConnected]'s flag: a widget render
     * must not stop a later foreground from actually syncing.
     */
    fun ensureCachedData() {
        if (cacheRestored || connected) return
        cacheRestored = true
        connections.restoreFromCache()
        updateActiveRepository()
    }

    /**
     * Begin planning reminders from live data, and apply anything the shade
     * queued while there was no repository to apply it to.
     *
     * Only started by the UI: fixture mode is frozen May 2026 sample data and
     * must never schedule a real alarm, and a process woken only to re-arm
     * reads the stored plan instead of recomputing one.
     */
    fun startReminderScheduling() {
        if (scheduling) return
        scheduling = true
        // Registering the observer unconditionally is what makes connecting a
        // first account start scheduling without a restart; `observeRepository`
        // calls back immediately with the current repository, so this also
        // covers the already-connected case.
        observeRepository { repository ->
            if (hasAccounts) reminderBridge.attach(repository, scope) else reminderBridge.clear()
        }
        scope.launch { drainQueuedReminderActions() }
    }

    /**
     * Shade actions that could not reach a repository at the time are applied
     * here, on the next start that has one.
     *
     * The wait matters: this runs at startup, and a repository that has only
     * published its disk cache may not know the task yet. An action whose
     * record cannot be found, or whose write is rejected, is left in the queue
     * for the next start -- the queue's own retention is what eventually drops
     * it, rather than a single unlucky moment.
     */
    private suspend fun drainQueuedReminderActions() {
        val queue = Reminders.actionQueue(application)
        if (queue.drainable().isEmpty()) return

        repeat(ActionDrainAttempts) {
            val pending = queue.drainable()
            if (pending.isEmpty()) return
            val repository = activeRepository
            val known = repository.snapshot().tasks.map { it.id }.toSet()
            pending.filter { it.recordId in known }.forEach { action ->
                val result = when (action.action) {
                    ReminderActions.MarkDone -> repository.setTaskDone(action.recordId, true)
                    // "Tomorrow" is the day after the user pressed it, not the
                    // day after it finally applied: an action queued overnight
                    // must not silently slip a day.
                    ReminderActions.Tomorrow -> repository.rescheduleTask(
                        action.recordId,
                        action.requestedAt.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1),
                    )
                    else -> null
                }
                when (result) {
                    is WriteResult.Applied<*>, is WriteResult.Queued<*> -> queue.remove(action.firingKey)
                    else -> Unit
                }
            }
            delay(ActionDrainIntervalMillis)
        }
    }

    /**
     * Begin redrawing the home screen widget when the repository publishes.
     *
     * Started by the UI only, for the same reason reminder scheduling is: a
     * process woken purely to render the widget already reads the snapshot it
     * needs, and attaching an observer there would be a bridge listening to a
     * publish that its own render caused.
     */
    fun startWidgetUpdates() {
        if (widgetUpdating) return
        widgetUpdating = true
        observeRepository { repository -> widgetBridge.attach(repository, scope) }
    }

    /** Start the periodic queue drain. Only the UI process needs this. */
    fun startWriteQueueDrain() {
        if (draining) return
        draining = true
        scope.launch {
            while (isActive) {
                calDavRepository.drainPendingWrites()
                delay(DrainIntervalMillis)
            }
        }
    }

    fun onAccountConnected(form: CalDavForm, calendars: List<CalDavCalendar>) {
        val account = accountStore.addAccount(form, calendars)
        connected = true
        connections.onAccountConnected(account, form.password)
        updateActiveRepository()
        syncAndroidAccounts()
    }

    fun onAccountRemoved(accountId: String) {
        accountStore.removeAccount(accountId)
        connections.onAccountRemoved(accountId)
        updateActiveRepository()
        syncAndroidAccounts()
    }

    /**
     * Whether Calino owns Android accounts and projects calendars into
     * `CalendarContract`.
     *
     * Off until someone opts in. An Android account appearing in Settings is
     * user-visible, so it must not be a side effect of connecting to a CalDAV
     * server -- see `docs/calendar-provider.md`. Turned on and off only
     * through [setProjectedCalendars], so the flag and the list of opted-in
     * calendars cannot disagree.
     */
    var calendarProjectionEnabled: Boolean = false
        private set(value) {
            if (field == value) return
            field = value
            syncAndroidAccounts()
        }

    /**
     * Makes the Android account list match the connected CalDAV accounts, or
     * empties it while projection is off.
     */
    private fun syncAndroidAccounts() {
        if (calendarProjectionEnabled) {
            CalinoAccounts.sync(application, accountStore.accounts())
            startCalendarProjection()
        } else {
            // Calendars first: removing the account would take its calendars
            // with it, but only after the provider had already told every
            // calendar app they vanished without explanation.
            CalendarProjection.clear(application)
            calendarProjectionBridge.detach()
            projecting = false
            CalinoAccounts.clear(application)
        }
        // Reminder ownership follows projection, so a toggle has to re-plan:
        // turning projection off must give Calino its own alarms back, and
        // the schedule is only rebuilt when something asks it to be.
        reminderBridge.refresh()
    }

    /**
     * The calendars projected into `CalendarContract`, or null while every
     * visible one is.
     *
     * Written only by [setProjectedCalendars] and [restoreProjection], so the
     * null case survives for the sync adapter's benefit rather than as a
     * state the opt-in can produce.
     */
    var projectedCalendarIds: Set<String>? = null
        private set

    /**
     * Publish exactly [ids] into the calendar provider, and persist that
     * choice.
     *
     * An empty set is how projection is turned off: there is no second switch
     * to keep in step with the list, so "nothing is opted in" and "the feature
     * is off" cannot disagree.
     *
     * The caller is responsible for holding `WRITE_CALENDAR` before opting the
     * first calendar in. Refusal is a supported state, and reaches here as an
     * unchanged set.
     */
    fun setProjectedCalendars(ids: Set<String>) {
        if (ids == projectedCalendarIds) return
        preferenceStore.saveProjectedCalendarIds(ids)
        projectedCalendarIds = ids
        val enabled = ids.isNotEmpty()
        if (enabled == calendarProjectionEnabled) {
            // The switch did not move, so its setter will not reconcile. A
            // calendar added to or dropped from the set still has to be.
            if (enabled) projectCalendars()
        } else {
            calendarProjectionEnabled = enabled
        }
    }

    /**
     * Bring back the opt-in from a previous run.
     *
     * Called from [ensureConnected] rather than construction, because
     * enabling projection touches `AccountManager` and the calendar provider:
     * a process woken to redraw a widget or re-arm an alarm must not do that.
     */
    private fun restoreProjection() {
        val stored = preferenceStore.loadProjectedCalendarIds()
        if (stored.isEmpty() || projectedCalendarIds != null) return
        projectedCalendarIds = stored
        calendarProjectionEnabled = true
    }

    /**
     * Start projecting into the calendar provider. Only the UI process needs
     * this, and only while projection is on.
     */
    fun startCalendarProjection() {
        if (projecting) return
        projecting = true
        observeRepository { repository -> calendarProjectionBridge.attach(repository, scope) }
    }

    /**
     * The calendars currently projected into `CalendarContract`.
     *
     * Empty while projection is off, which is what makes reminder ownership
     * follow the projection without a second switch to keep in step.
     */
    fun projectedCalendars(): Set<String> {
        if (!calendarProjectionEnabled) return emptySet()
        return projectedCalendarIds
            ?: visibleCalendarIds(activeRepository.snapshot().calendars)
    }

    /**
     * Reconcile the calendar provider once, now.
     *
     * For the sync adapter: after ingesting foreign edits it must put the
     * provider back in agreement with Calino, and a rejected edit publishes
     * no snapshot for the bridge to react to.
     */
    fun projectCalendars() {
        if (!calendarProjectionEnabled) return
        calendarProjectionBridge.projectNow(activeRepository.snapshot())
    }

    fun onCalendarsToggled() = connections.onCalendarsToggled()

    private fun updateActiveRepository() {
        val next: CalinoRepository = if (hasAccounts) calDavRepository else fixtureRepository
        if (next === activeRepository) return
        activeRepository = next
        repositoryListeners.forEach { it(next) }
    }

    companion object {
        private const val DrainIntervalMillis = 60_000L
        private const val ActionDrainAttempts = 10
        private const val ActionDrainIntervalMillis = 1_000L

        @Volatile
        private var instance: CalinoContainer? = null

        fun get(context: Context): CalinoContainer =
            instance ?: synchronized(this) {
                instance ?: CalinoContainer(context).also { instance = it }
            }
    }
}
