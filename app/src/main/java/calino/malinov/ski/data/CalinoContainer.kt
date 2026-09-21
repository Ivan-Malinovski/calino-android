package calino.malinov.ski.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
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
import calino.malinov.ski.data.model.WebcalForm
import calino.malinov.ski.data.repository.CalDavAccountStore
import calino.malinov.ski.data.repository.CalDavClient
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.data.repository.FilePendingChangeStore
import calino.malinov.ski.data.repository.FixtureRepository
import calino.malinov.ski.data.repository.ImportingRepository
import calino.malinov.ski.data.repository.SharedPreferencesWebcalPersistence
import calino.malinov.ski.data.repository.WebcalSubscriptionStore
import calino.malinov.ski.data.repository.WriteResult
import calino.malinov.ski.data.repository.visibleCalendarIds
import calino.malinov.ski.data.webcal.FileWebcalCache
import calino.malinov.ski.data.webcal.WebcalFetcher
import calino.malinov.ski.data.webcal.WebcalManager
import calino.malinov.ski.notify.ReminderActions
import calino.malinov.ski.notify.ReminderSchedulerBridge
import calino.malinov.ski.notify.Reminders
import calino.malinov.ski.platform.AndroidCalendarSource
import calino.malinov.ski.platform.AndroidCalendarWriter
import calino.malinov.ski.platform.CalendarProjection
import calino.malinov.ski.platform.CalendarProjectionBridge
import calino.malinov.ski.platform.CalinoAccounts
import calino.malinov.ski.state.CalinoPreferenceStore
import calino.malinov.ski.state.SharedPreferencesPreferenceStore
import calino.malinov.ski.widget.CalinoWidgetBridge
import java.io.File
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun writableImportsAfterSelection(
    previousImported: Set<String>,
    selectedImported: Set<String>,
    currentlyWritable: Set<String>,
    providerWritable: Set<String>,
): Set<String> =
    (currentlyWritable intersect selectedImported) +
        ((selectedImported - previousImported) intersect providerWritable)

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

    val webcalStore = WebcalSubscriptionStore(SharedPreferencesWebcalPersistence(application))

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

    private val webcal = WebcalManager(
        store = webcalStore,
        repository = calDavRepository,
        fetcher = WebcalFetcher(http),
        cache = FileWebcalCache(File(application.filesDir, "webcal-cache")),
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

    val hasLiveData: Boolean
        get() = hasAccounts || webcalStore.subscriptions().isNotEmpty()

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
     * The device's own calendars Calino shows, and the wrapper that shows
     * them.
     *
     * The wrapper exists only while the set is non-empty. That is the point:
     * someone who never opts in gets the bare repository, no content
     * observer, and no provider query at all.
     */
    private var importedCalendarIds: Set<String> = emptySet()

    var writableImportedCalendarIds: Set<String> = emptySet()
        private set

    private val androidCalendarWriter = AndroidCalendarWriter(application)
    private val importGeneration = AtomicLong(0)

    private var importing: ImportingRepository? = null

    private var importObserver: ContentObserver? = null

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
        importedCalendarsRemindedElsewhere = { importedCalendarsRemindedElsewhere() },
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
        webcal.restore()
        restoreImport()
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
        webcal.restoreFromCache()
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
            if (hasLiveData) reminderBridge.attach(repository, scope) else reminderBridge.clear()
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

    suspend fun addWebcalSubscription(form: WebcalForm) =
        webcal.add(form).also { updateActiveRepository() }

    fun removeWebcalSubscription(id: String) {
        webcal.remove(id)
        updateActiveRepository()
    }

    fun onWebcalVisibilityChanged(id: String, visible: Boolean) = webcal.setVisible(id, visible)

    fun onWebcalNotifyRemindersChanged(id: String, notify: Boolean) =
        webcal.setNotifyReminders(id, notify)

    fun onWebcalRenamed(id: String, name: String) = webcal.setName(id, name)

    fun onWebcalRenamedByCalendarId(calendarId: String, name: String) =
        webcal.setNameByCalendarId(calendarId, name)

    fun onWebcalColorChanged(id: String, color: Long) = webcal.setColor(id, color)

    suspend fun syncWebcal(id: String) = webcal.sync(id)

    suspend fun syncWebcalAll() = webcal.syncAll()

    /**
     * The calendars imported from the device, and the ones Calino also
     * reminds for.
     */
    val importedCalendars: Set<String> get() = importedCalendarIds

    var importedReminderCalendarIds: Set<String> = emptySet()
        private set

    /**
     * Choose which of the device's calendars Calino shows.
     *
     * Wraps or unwraps the repository as the set becomes non-empty or empty,
     * so the composite is in the graph only while it has something to add.
     */
    fun setImportedCalendars(ids: Set<String>) {
        if (ids == importedCalendarIds) return
        val previousImported = importedCalendarIds
        preferenceStore.saveImportedCalendarIds(ids)
        importedCalendarIds = ids
        // A calendar that is no longer imported cannot keep a reminder
        // preference; leaving one behind would silently re-apply if it were
        // ever imported again.
        setImportedReminderCalendars(importedReminderCalendarIds intersect ids)
        val providerWritable = AndroidCalendarSource.availableCalendars(application)
            .filter { it.canWrite }
            .map { it.id }
            .toSet()
        setWritableImportedCalendars(
            writableImportsAfterSelection(
                previousImported = previousImported,
                selectedImported = ids,
                currentlyWritable = writableImportedCalendarIds,
                providerWritable = providerWritable,
            ),
        )
        updateActiveRepository()
        refreshImport()
    }

    /** Independently allow writes only for imported, provider-writable calendars. */
    fun setWritableImportedCalendars(ids: Set<String>) {
        val eligible = AndroidCalendarSource.availableCalendars(application)
            .filter { it.canWrite }
            .map { it.id }
            .toSet()
        // A permission revoke removes current capability, not remembered
        // consent. Existing choices survive while removals still work;
        // newly enabling a calendar requires capability right now.
        val retained = writableImportedCalendarIds intersect ids intersect importedCalendarIds
        val additions = (ids - writableImportedCalendarIds) intersect importedCalendarIds intersect eligible
        val next = retained + additions
        if (next == writableImportedCalendarIds) return
        preferenceStore.saveWritableImportedCalendarIds(next)
        writableImportedCalendarIds = next
        refreshImport()
    }

    /** Choose which imported calendars Calino delivers reminders for. */
    fun setImportedReminderCalendars(ids: Set<String>) {
        val next = ids intersect importedCalendarIds
        if (next == importedReminderCalendarIds) return
        preferenceStore.saveImportedReminderCalendarIds(next)
        importedReminderCalendarIds = next
        reminderBridge.refresh()
    }

    /**
     * The imported calendars whose reminders somebody else delivers.
     *
     * The inverse of the projection's rule, and the reason it is worded this
     * way round: an imported calendar's own app is already notifying for it,
     * so Calino stays quiet unless asked. It cannot silence that app, which
     * is why opting in is the person's deliberate choice.
     */
    fun importedCalendarsRemindedElsewhere(): Set<String> =
        importedCalendarIds - importedReminderCalendarIds

    private fun restoreImport() {
        if (importedCalendarIds.isNotEmpty()) return
        importedCalendarIds = preferenceStore.loadImportedCalendarIds()
        importedReminderCalendarIds =
            preferenceStore.loadImportedReminderCalendarIds() intersect importedCalendarIds
        writableImportedCalendarIds = if (preferenceStore.hasWritableImportedCalendarPreference()) {
            preferenceStore.loadWritableImportedCalendarIds() intersect importedCalendarIds
        } else {
            // 0.4 introduces write-back. Existing imports have never had a
            // write preference to preserve, so writable provider calendars
            // adopt the new default once; a deliberately saved empty set must
            // remain off on every later launch.
            AndroidCalendarSource.availableCalendars(application)
                .filter { it.canWrite && it.id in importedCalendarIds }
                .map { it.id }
                .toSet()
                .also(preferenceStore::saveWritableImportedCalendarIds)
        }
    }

    /**
     * Re-read the device's calendars off the main thread and republish.
     *
     * A daily series over the projection window is hundreds of instance rows,
     * so this is not work for a frame.
     */
    /** Revalidate provider access and runtime permissions on foreground entry. */
    fun refreshImportedCalendars() = refreshImport()

    private fun refreshImport() {
        val target = importing ?: return
        val wanted = importedCalendarIds
        val writable = writableImportedCalendarIds
        val generation = importGeneration.incrementAndGet()
        scope.launch {
            val read = AndroidCalendarSource.read(application, wanted, writable)
            // Both opt-in sets and the generation must still match. A read
            // started before a provider write must never overwrite the
            // canonical post-write refresh, even when both used the same ids.
            if (generation == importGeneration.get() && wanted == importedCalendarIds &&
                writable == writableImportedCalendarIds
            ) target.setImported(read)
        }
    }

    /**
     * Follow changes another app makes to its own calendars.
     *
     * Registered only while something is imported. The provider notifies
     * generously -- a Google sync touches the URI repeatedly -- so the work
     * this triggers stays a read and a republish, never a write.
     */
    private fun updateImportObserver() {
        val wanted = importedCalendarIds.isNotEmpty()
        val existing = importObserver
        if (wanted == (existing != null)) return
        if (!wanted) {
            existing?.let { application.contentResolver.unregisterContentObserver(it) }
            importObserver = null
            return
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refreshImport()
        }
        runCatching {
            application.contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI,
                true,
                observer,
            )
            importObserver = observer
        }
    }

    private fun updateActiveRepository() {
        // hasLiveData, not hasAccounts: a subscription with no account
        // behind it is still live data, and fixtures would hide it.
        val base: CalinoRepository = if (hasLiveData) calDavRepository else fixtureRepository
        val next: CalinoRepository = if (importedCalendarIds.isEmpty()) {
            importing?.close()
            importing = null
            base
        } else {
            val existing = importing
            // Rebuilt rather than re-pointed when the primary swaps: the
            // wrapper holds one subscription to the primary for its lifetime,
            // and moving that is more machinery than dropping the wrapper.
            if (existing != null && existing.observes(base)) {
                existing
            } else {
                // Carry the last read across a rebuild, so swapping fixture
                // for CalDAV does not blank the imported events for as long
                // as the re-read takes.
                ImportingRepository(
                    primary = base,
                    imported = existing?.imported ?: AndroidCalendarSource.Import(),
                    writer = androidCalendarWriter,
                    writableCalendarIds = { writableImportedCalendarIds },
                    refreshImport = ::refreshImport,
                ).also {
                        existing?.close()
                        importing = it
                        refreshImport()
                    }
            }
        }
        updateImportObserver()
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
