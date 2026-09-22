package calino.malinov.ski.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import calino.malinov.ski.data.caldav.CalDavConnectionManager
import calino.malinov.ski.data.caldav.AccountRediscoveryResult
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
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.FilePendingChangeStore
import calino.malinov.ski.data.repository.FixtureRepository
import calino.malinov.ski.data.repository.ImportingRepository
import calino.malinov.ski.data.repository.SharedPreferencesWebcalPersistence
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.data.repository.WebcalSubscriptionStore
import calino.malinov.ski.data.repository.WriteResult
import calino.malinov.ski.data.repository.visibleCalendarIds
import calino.malinov.ski.data.sync.BackgroundSyncCadence
import calino.malinov.ski.data.sync.BackgroundSyncScheduler
import calino.malinov.ski.data.sync.BackgroundSyncStore
import calino.malinov.ski.data.search.CalinoAppSearchIndex
import calino.malinov.ski.data.search.appSearchRecords
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
import calino.malinov.ski.wear.PhoneWearBridge
import calino.malinov.ski.wear.isAuthoritativeWearSource
import java.io.File
import java.io.Closeable
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private data class SearchIndexRequest(
        val repository: CalinoRepository,
        val generation: Long,
        val sequence: Long,
        val snapshot: CalinoSnapshot,
        val journalsEnabled: Boolean,
        val contactsEnabled: Boolean,
    )

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

    val backgroundSyncStore = BackgroundSyncStore(application)

    private val backgroundSyncScheduler = BackgroundSyncScheduler(application)
    private val appSearchIndex = CalinoAppSearchIndex(application)

    @Volatile private var searchJournalsEnabled = preferenceStore.loadJournalEnabled()
    @Volatile private var searchContactsEnabled = preferenceStore.loadContactsEnabled()

    @Volatile private var searchIndexing = false
    private var searchRepositorySubscription: Closeable? = null
    private val searchIndexMutex = Mutex()
    private val searchIndexStateLock = Any()
    private var searchIndexRepository: CalinoRepository? = null
    private var searchIndexGeneration = 0L
    private var nextSearchIndexSequence = 0L
    private var appliedSearchIndexSequence = -1L
    private var latestSearchIndexRequest: SearchIndexRequest? = null

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

    private val syncRequestLock = Any()
    @Volatile private var sharedSync: Deferred<calino.malinov.ski.data.repository.RepositorySyncResult>? = null

    /**
     * The repository in force. With no account connected this is the frozen
     * fixture data, so the app is never an empty shell.
     */
    @Volatile var activeRepository: CalinoRepository = fixtureRepository
        private set

    val hasAccounts: Boolean get() = accountStore.accounts().isNotEmpty()

    val hasLiveData: Boolean
        get() = hasAccounts || webcalStore.subscriptions().isNotEmpty()

    @Volatile
    private var connected = false

    @Volatile
    private var cacheRestored = false

    @Volatile
    private var scheduling = false

    @Volatile
    private var widgetUpdating = false

    @Volatile
    private var projecting = false

    @Volatile private var wearing = false

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
    val wearBridge = PhoneWearBridge(application)

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
        calDavRepository.onPendingChangeEnqueued = {
            backgroundSyncScheduler.enqueueImmediate(hasAccounts, backgroundSyncStore.cadence())
        }
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
        connections.restoreFromCache()
        webcal.restore()
        restoreImport()
        updateActiveRepository()
        startSearchIndexing()
        restoreProjection()
        reconcileBackgroundSchedule()
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
        startSearchIndexing()
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

    /** Publishes only real account-backed data; fixture mode is never paired as user data. */
    fun startWearBridge() {
        if (wearing) return
        wearing = true
        observeRepository { repository ->
            if (hasAccounts) {
                wearBridge.attach(repository, scope, ::isWearDataAuthoritative)
            } else {
                wearBridge.detach()
            }
        }
    }

    /** Fixture and pre-restore placeholder snapshots must never cross to Wear. */
    fun isWearDataAuthoritative(): Boolean =
        isAuthoritativeWearSource(
            hasAccounts = hasAccounts,
            isFixtureRepository = activeRepository === fixtureRepository,
            sync = activeRepository.snapshot().sync,
        )

    fun onAccountConnected(form: CalDavForm, calendars: List<CalDavCalendar>) {
        val account = accountStore.addAccount(form, calendars)
        connected = true
        connections.onAccountConnected(account, form.password)
        updateActiveRepository()
        syncAndroidAccounts()
        reconcileBackgroundSchedule()
        enqueueImmediateBackgroundSync()
        scope.launch { syncConnectedAccounts() }
    }

    fun onAccountRemoved(accountId: String) {
        accountStore.removeAccount(accountId)
        connections.onAccountRemoved(accountId)
        updateActiveRepository()
        syncAndroidAccounts()
        reconcileBackgroundSchedule()
        if (hasAccounts) {
            enqueueImmediateBackgroundSync()
            scope.launch { syncConnectedAccounts() }
        }
    }

    /** Keep the private index in step with the feature availability controls. */
    fun setSearchAvailability(journalsEnabled: Boolean, contactsEnabled: Boolean) {
        searchJournalsEnabled = journalsEnabled
        searchContactsEnabled = contactsEnabled
        if (searchIndexing) reconcileSearchSnapshot(activeRepository, activeRepository.snapshot())
    }

    /**
     * Ensures the private index is initialized and reconciles the active
     * repository's latest snapshot. Workers can call this after sync even when
     * no Activity ever started the repository observer.
     */
    suspend fun reconcilePrivateSearchIndex() {
        startSearchIndexing()
        while (true) {
            val repository = activeRepository
            val snapshot = repository.snapshot()
            val indexable = snapshotForPrivateIndex(repository, snapshot) ?: emptySearchSnapshot()
            val request = publishSearchIndexRequest(
                repository,
                indexable,
                searchJournalsEnabled,
                searchContactsEnabled,
            ) ?: continue
            searchIndexMutex.withLock { reconcileLatestSearchIndexRequest(request) }
            val stillCurrent = isLatestSearchIndexRequest(request) && repository.snapshot().revision == request.snapshot.revision
            if (stillCurrent) return
        }
    }

    /**
     * Returns candidates for the active repository's freshest snapshot. A null
     * result is reserved for the fixture-only path, where in-memory search
     * remains authoritative and fixture records are never indexed.
     */
    suspend fun searchRecordIds(
        query: String,
        journalsEnabled: Boolean,
        contactsEnabled: Boolean,
    ): Set<String>? = searchIndexMutex.withLock {
        searchRecordIdsInLane(query, journalsEnabled, contactsEnabled)
    }

    private suspend fun searchRecordIdsInLane(
        query: String,
        journalsEnabled: Boolean,
        contactsEnabled: Boolean,
    ): Set<String>? {
        while (true) {
            val repository = activeRepository
            val repositorySnapshot = repository.snapshot()
            val indexable = snapshotForPrivateIndex(repository, repositorySnapshot) ?: emptySearchSnapshot()
            val request = publishSearchIndexRequest(repository, indexable, journalsEnabled, contactsEnabled)
            if (request == null) {
                if (!searchIndexing) return null
                continue
            }
            if (!isLatestSearchIndexRequest(request)) continue
            val requestedSnapshot = request.snapshot

            val fixtureKeys = if (hasImportedDataOverFixture(repository)) {
                appSearchRecords(fixtureRepository.snapshot(), journalsEnabled, contactsEnabled)
                    .map { it.stableId }.toSet()
            } else {
                emptySet()
            }
            val indexedCandidates = try {
                if (repository === fixtureRepository) {
                    appSearchIndex.reconcile(requestedSnapshot, journalsEnabled, contactsEnabled)
                    null
                } else {
                    appSearchIndex.searchRecords(query, requestedSnapshot, journalsEnabled, contactsEnabled)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep in-memory ranking usable if local AppSearch is temporarily
                // unavailable. A later snapshot or query retries reconciliation.
                if (repository === fixtureRepository) null
                else appSearchRecords(requestedSnapshot, journalsEnabled, contactsEnabled).map { it.stableId }.toSet()
            }

            val currentRevision = repository.snapshot().revision
            if (!isLatestSearchIndexRequest(request) || currentRevision != request.snapshot.revision) continue
            if (indexedCandidates == null) return null
            markSearchIndexRequestApplied(request)
            return indexedCandidates + fixtureKeys
        }
    }

    @Synchronized
    private fun startSearchIndexing() {
        if (searchIndexing) return
        searchIndexing = true
        searchJournalsEnabled = preferenceStore.loadJournalEnabled()
        searchContactsEnabled = preferenceStore.loadContactsEnabled()
        attachSearchObserver(activeRepository)
    }

    @Synchronized
    private fun attachSearchObserver(repository: CalinoRepository) {
        if (repository !== activeRepository) return
        searchRepositorySubscription?.close()
        synchronized(searchIndexStateLock) {
            if (searchIndexRepository !== repository) {
                searchIndexRepository = repository
                searchIndexGeneration += 1
                latestSearchIndexRequest = null
                appliedSearchIndexSequence = -1L
            }
        }
        searchRepositorySubscription = repository.observe { snapshot ->
            reconcileSearchSnapshot(repository, snapshot)
        }
    }

    private fun reconcileSearchSnapshot(repository: CalinoRepository, snapshot: CalinoSnapshot) {
        val indexable = snapshotForPrivateIndex(repository, snapshot) ?: emptySearchSnapshot()
        val journalsEnabled = searchJournalsEnabled
        val contactsEnabled = searchContactsEnabled
        val request = publishSearchIndexRequest(repository, indexable, journalsEnabled, contactsEnabled) ?: return
        scope.launch {
            runCatching {
                searchIndexMutex.withLock { reconcileLatestSearchIndexRequest(request) }
            }
        }
    }

    private fun publishSearchIndexRequest(
        repository: CalinoRepository,
        snapshot: CalinoSnapshot,
        journalsEnabled: Boolean,
        contactsEnabled: Boolean,
    ): SearchIndexRequest? = synchronized(searchIndexStateLock) {
        if (!searchIndexing || repository !== activeRepository || repository !== searchIndexRepository) return null
        val current = latestSearchIndexRequest
        if (current?.repository === repository && current.generation == searchIndexGeneration) {
            val newestSnapshot = if (snapshot.revision < current.snapshot.revision) current.snapshot else snapshot
            if (newestSnapshot.revision == current.snapshot.revision &&
                journalsEnabled == current.journalsEnabled && contactsEnabled == current.contactsEnabled
            ) return current
            return SearchIndexRequest(
                repository = repository,
                generation = searchIndexGeneration,
                sequence = ++nextSearchIndexSequence,
                snapshot = newestSnapshot,
                journalsEnabled = journalsEnabled,
                contactsEnabled = contactsEnabled,
            ).also { latestSearchIndexRequest = it }
        }
        SearchIndexRequest(
            repository = repository,
            generation = searchIndexGeneration,
            sequence = ++nextSearchIndexSequence,
            snapshot = snapshot,
            journalsEnabled = journalsEnabled,
            contactsEnabled = contactsEnabled,
        ).also { latestSearchIndexRequest = it }
    }

    private suspend fun reconcileLatestSearchIndexRequest(initial: SearchIndexRequest) {
        var request = initial
        while (true) {
            val latest = synchronized(searchIndexStateLock) { latestSearchIndexRequest } ?: return
            if (!isLatestSearchIndexRequest(latest)) return
            if (latest.sequence != request.sequence) request = latest
            if (synchronized(searchIndexStateLock) { appliedSearchIndexSequence == request.sequence }) return

            val actualRevision = request.repository.snapshot().revision
            if (actualRevision > request.snapshot.revision) {
                request = publishSearchIndexRequest(
                    request.repository,
                    snapshotForPrivateIndex(request.repository, request.repository.snapshot()) ?: emptySearchSnapshot(),
                    searchJournalsEnabled,
                    searchContactsEnabled,
                ) ?: return
                continue
            }

            appSearchIndex.reconcile(request.snapshot, request.journalsEnabled, request.contactsEnabled)
            synchronized(searchIndexStateLock) {
                if (latestSearchIndexRequest?.sequence == request.sequence) {
                    appliedSearchIndexSequence = request.sequence
                    return
                }
            }
        }
    }

    private fun isLatestSearchIndexRequest(request: SearchIndexRequest): Boolean = synchronized(searchIndexStateLock) {
        request.repository === activeRepository && request.repository === searchIndexRepository &&
            request.generation == searchIndexGeneration &&
            latestSearchIndexRequest?.sequence == request.sequence
    }

    private fun markSearchIndexRequestApplied(request: SearchIndexRequest) {
        synchronized(searchIndexStateLock) {
            if (request.repository === activeRepository && request.generation == searchIndexGeneration &&
                latestSearchIndexRequest?.sequence == request.sequence
            ) appliedSearchIndexSequence = request.sequence
        }
    }

    private fun snapshotForPrivateIndex(
        repository: CalinoRepository,
        snapshot: CalinoSnapshot,
    ): CalinoSnapshot? {
        if (repository === fixtureRepository) return null
        if (!hasImportedDataOverFixture(repository)) return snapshot
        val fixture = fixtureRepository.snapshot()
        val fixtureEventIds = fixture.events.map { it.id }.toSet()
        val fixtureTaskIds = fixture.tasks.map { it.id }.toSet()
        val fixtureJournalIds = fixture.journals.map { it.id }.toSet()
        val fixtureContactIds = fixture.contacts.map { it.id }.toSet()
        return snapshot.copy(
            events = snapshot.events.filterNot { it.id in fixtureEventIds },
            tasks = snapshot.tasks.filterNot { it.id in fixtureTaskIds },
            journals = snapshot.journals.filterNot { it.id in fixtureJournalIds },
            contacts = snapshot.contacts.filterNot { it.id in fixtureContactIds },
        )
    }

    private fun hasImportedDataOverFixture(repository: CalinoRepository) =
        repository is ImportingRepository && !hasAccounts &&
            webcalStore.subscriptions().isEmpty() && importedCalendarIds.isNotEmpty()

    private fun emptySearchSnapshot() = CalinoSnapshot(
        events = emptyList(),
        tasks = emptyList(),
        journals = emptyList(),
        contacts = emptyList(),
        addressBooks = emptyList(),
        calendars = emptyList(),
    )

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

    fun onCalendarsToggled() {
        connections.onCalendarsToggled()
        if (hasAccounts) {
            enqueueImmediateBackgroundSync()
            scope.launch { syncConnectedAccounts() }
        }
    }

    /** Persist the requested cadence and reconcile unique periodic work. */
    fun setBackgroundSyncCadence(cadence: BackgroundSyncCadence) {
        backgroundSyncStore.setCadence(cadence)
        reconcileBackgroundSchedule()
    }

    /** Attach the existing snapshot bridges before a worker performs a read. */
    fun startBackgroundSyncBridges() {
        if (!connected) {
            connected = true
            cacheRestored = true
            connections.restoreFromCache()
            webcal.restoreFromCache()
            restoreImport()
            updateActiveRepository()
            restoreProjection()
        }
        startReminderScheduling()
        startWidgetUpdates()
    }

    /**
     * One shared awaitable sync for launch, manual refresh, and WorkManager.
     * Concurrent callers await the same pass instead of rediscovering or
     * reading the same account set twice.
     */
    suspend fun syncConnectedAccounts(): calino.malinov.ski.data.repository.RepositorySyncResult {
        val request = synchronized(syncRequestLock) {
            sharedSync?.takeIf { it.isActive } ?: scope.async {
                syncConnectedAccountsOnce()
            }.also { sharedSync = it }
        }
        return try {
            request.await()
        } finally {
            synchronized(syncRequestLock) {
                if (sharedSync === request && request.isCompleted) sharedSync = null
            }
        }
    }

    private suspend fun syncConnectedAccountsOnce(): calino.malinov.ski.data.repository.RepositorySyncResult {
        val accountIds = accountStore.accounts().map { it.id }.toSet()
        backgroundSyncScheduler.reconcile(accountIds.isNotEmpty(), backgroundSyncStore.cadence())
        if (accountIds.isEmpty()) {
            return calino.malinov.ski.data.repository.RepositorySyncResult.NoSources
        }
        startBackgroundSyncBridges()
        val isCurrent = { accountStore.accounts().map { it.id }.toSet() == accountIds }
        if (!isCurrent()) return calino.malinov.ski.data.repository.RepositorySyncResult.AccountsRemoved
        when (val discovery = connections.rediscoverAccounts(accountIds, isCurrent)) {
            AccountRediscoveryResult.AccountsRemoved ->
                return calino.malinov.ski.data.repository.RepositorySyncResult.AccountsRemoved
            is AccountRediscoveryResult.CredentialsUnavailable ->
                return calino.malinov.ski.data.repository.RepositorySyncResult.Failed(
                    "Saved credentials for ${discovery.accountName} are unavailable. Reconnect this account to sync it again.",
                    retryable = false,
                )
            AccountRediscoveryResult.Ready -> Unit
        }
        if (!isCurrent()) return calino.malinov.ski.data.repository.RepositorySyncResult.AccountsRemoved
        return calDavRepository.synchronize(isCurrent)
    }

    private fun reconcileBackgroundSchedule() {
        backgroundSyncScheduler.reconcile(hasAccounts, backgroundSyncStore.cadence())
    }

    private fun enqueueImmediateBackgroundSync() {
        backgroundSyncScheduler.enqueueImmediate(hasAccounts, backgroundSyncStore.cadence())
    }

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
        if (searchIndexing) attachSearchObserver(next)
        repositoryListeners.forEach { it(next) }
    }

    companion object {
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
