package calino.malinov.ski.data.repository

import calino.malinov.ski.data.caldav.CachedCalendar
import calino.malinov.ski.data.caldav.readInlineAttachment
import calino.malinov.ski.data.model.EventAttachment
import calino.malinov.ski.data.caldav.CachedAddressBook
import calino.malinov.ski.data.caldav.CardDavFetcher
import calino.malinov.ski.data.caldav.CardDavWriter
import calino.malinov.ski.data.caldav.CardResource
import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.CalDavWriter
import calino.malinov.ski.data.caldav.ICalTimezones
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.CollectionCursor
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.caldav.ICalPatcher
import calino.malinov.ski.data.caldav.ICalWriter
import calino.malinov.ski.data.caldav.RecurrenceEdit
import calino.malinov.ski.data.caldav.VCardMapper
import calino.malinov.ski.data.caldav.VCardPatcher
import calino.malinov.ski.data.caldav.DiscoveredAddressBook
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.PreparedCalendarWrite
import calino.malinov.ski.data.caldav.DavPrecondition
import calino.malinov.ski.data.caldav.WrittenCalendarResource
import calino.malinov.ski.data.caldav.PreparedCardWrite
import calino.malinov.ski.data.caldav.calDavErrorForThrowable
import calino.malinov.ski.data.caldav.normalizeEtag
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.ContactAddressBook
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.NewContact
import calino.malinov.ski.data.model.NewJournal
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.RecurrenceEditScope
import biweekly.component.ICalComponent
import biweekly.component.VEvent
import java.io.Closeable
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext

private class AccountSyncGuard(
    val isCurrent: () -> Boolean,
) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AccountSyncGuard>
}

/** A completed read/replay attempt and its retry guidance for background work. */
sealed interface RepositorySyncResult {
    data class Success(
        val warnings: List<String>,
        val retryNeeded: Boolean,
        val message: String? = null,
    ) : RepositorySyncResult

    data class Failed(val message: String, val retryable: Boolean) : RepositorySyncResult
    data object NoSources : RepositorySyncResult
    data object AccountsRemoved : RepositorySyncResult
}

private class AccountSyncAbortedException : Exception()

private class MoveDestinationConflictException(message: String) : Exception(message)

private fun CalDavException.isTransientSyncFailure(): Boolean = code in setOf(
    CalDavErrorCode.Network,
    CalDavErrorCode.Timeout,
    CalDavErrorCode.Server,
)

private fun String.isTransientSyncFailure(): Boolean {
    val normalized = lowercase()
    return "timeout" in normalized || "timed out" in normalized ||
        "could not reach" in normalized || "network" in normalized ||
        Regex("\\b5[0-9]{2}\\b").containsMatchIn(normalized)
}

object RepositorySyncRetryPolicy {
    fun retryNeeded(
        transientReadFailure: Boolean,
        pendingStates: Iterable<PendingChangeState>,
    ): Boolean = transientReadFailure || pendingStates
        .takeWhile { it != PendingChangeState.DEAD_LETTER }
        .any { it != PendingChangeState.DEAD_LETTER }

    fun pendingQueueMessage(pendingStates: Iterable<PendingChangeState>): String? {
        val states = pendingStates.toList()
        val deadLetterAt = states.indexOf(PendingChangeState.DEAD_LETTER)
        return when {
            deadLetterAt == 0 && states.drop(1).any { it != PendingChangeState.DEAD_LETTER } ->
                "A failed saved change is blocking later queued changes. Review queued changes."
            deadLetterAt >= 0 -> "A saved change needs attention. Review queued changes."
            states.isNotEmpty() -> "Some saved changes are waiting to sync."
            else -> null
        }
    }
}

/** One calendar to read, with the credentials that reach it. */
data class CalDavSource(
    val calendar: DiscoveredCalendar,
    val credentials: DavCredentials,
    val accountId: String,
    /**
     * Cursor committed before the current discovery metadata was observed.
     * Null means the calendar fields themselves are the only cursor we have;
     * in that case the fetcher must report from that token, but must not use
     * the ctag skip shortcut because it has not been freshly compared.
     */
    val committedCursor: CollectionCursor? = null,
    /** True only for metadata returned by a just-completed discovery walk. */
    val metadataFresh: Boolean = false,
    val visible: Boolean = true,
    val showTasksInViews: Boolean = true,
)

data class CardDavSource(
    val addressBook: DiscoveredAddressBook,
    val credentials: DavCredentials,
    val accountId: String,
    val committedCursor: CollectionCursor? = null,
    val metadataFresh: Boolean = false,
)

/** Read-only ICS overlay, merged at compose time so it never hits CalDAV writes. */
data class WebcalOverlay(
    val calendars: List<CalinoCalendar> = emptyList(),
    val events: List<CalEvent> = emptyList(),
)

/**
 * A [CalinoRepository] backed by real CalDAV collections.
 *
 * Reads are cached and writes are conditional, one resource at a time. The
 * local overlay makes an accepted PUT/DELETE visible immediately; an
 * authoritative fetch reconciles it with the server response. Transport and
 * transient HTTP failures are persisted in [pendingStore] and replayed in
 * FIFO order, while permanent failures remain visible as a failed record.
 *
 * Reads are cached. Each complete fetch writes the server's own resource text
 * to [cache], and a launch maps that back before any request is made, so a
 * connected calendar renders immediately and stays readable with no network.
 * Pending writes are durable, but the optimistic [overlay] is not: discarding
 * an unsynced write must be able to remove it rather than make it look like a
 * second server cache.
 */
class CalDavRepository(
    private val fetcher: CalDavFetcher,
    private val scope: CoroutineScope,
    /** Last successful read per calendar, so a cold start is not a blank one. */
    private val cache: CalendarCache = CalendarCache.None,
    private val mapper: ICalMapper = ICalMapper(),
    private val today: () -> LocalDate = { LocalDate.now() },
    windowMonths: Long = DefaultWindowMonths,
    /** Injected so tests can drive the fetch on their own scheduler. */
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
    private val cardFetcher: CardDavFetcher = CardDavFetcher(),
    private val vCardMapper: VCardMapper = VCardMapper(),
    private val writer: CalDavWriter = CalDavWriter(cache = cache),
    private val cardWriter: CardDavWriter = CardDavWriter(cache = cache),
    private val pendingStore: PendingChangeStore? = null,
) : CalinoRepository {

    private var windowMonths: Long = windowMonths

    private val listeners = CopyOnWriteArrayList<(CalinoSnapshot) -> Unit>()

    /** What the server last gave us. */
    private var fetched = FetchedData()

    /** Subscribed .ics feeds. Replaced as a whole; never written through DAV. */
    private var webcal = WebcalOverlay()

    /** Local, unsynced edits layered over [fetched]. */
    private val overlay = LocalOverlay()

    /** Serializes read-modify-write sequences so two edits cannot share an ETag. */
    private val writeMutex = Mutex()
    private val recurrenceWriter = ICalWriter()
    private val recurrencePatcher = ICalPatcher(recurrenceWriter)
    private val vCardPatcher = VCardPatcher()
    /** Queue replay and reads share one lane for startup, manual, and worker callers. */
    private val syncMutex = Mutex()

    /** Called only after a durable enqueue succeeded. */
    var onPendingChangeEnqueued: (() -> Unit)? = null

    private var writeStatuses: Map<String, RecordWriteStatus> =
        pendingStore?.snapshot()?.associate { change ->
            change.eventId to RecordWriteStatus(
                state = if (change.state == PendingChangeState.DEAD_LETTER) {
                    RecordWriteState.Failed
                } else {
                    RecordWriteState.Pending
                },
                reason = change.lastFailure?.message,
            )
        }.orEmpty()

    private var sources: List<CalDavSource> = emptyList()
    private var addressBookSources: List<CardDavSource> = emptyList()
    private var syncState: SyncState = SyncState.Idle
    private var drainJob: Job? = null

    /** Receives cursors only after the complete read they describe is accepted. */
    private var calendarCursorListener: ((String, String, CollectionCursor) -> Unit)? = null
    private var addressBookCursorListener: ((String, String, CollectionCursor) -> Unit)? = null

    /**
     * Bumped by every [setSources] and [refresh].
     *
     * A cache load and a network fetch race by construction, and the cache is
     * the older answer. This is what stops a slow disk read from overwriting a
     * fetch that already landed.
     */
    private var generation = 0L

    /**
     * When what is currently on screen was read from the server.
     *
     * Null until something has been read. It is what a `Loading` state reports,
     * so a refresh over a full calendar says it is refreshing a known copy
     * rather than implying the calendar is empty until it returns.
     */
    private var lastReadAt: Instant? = null
    private var current: CalinoSnapshot = compose()

    /**
     * Lets the account connection layer persist a cursor after a successful
     * incremental or full read. The listener is intentionally optional so the
     * repository remains usable in isolated tests and with the fixture seam.
     */
    fun setCalendarCursorListener(listener: ((String, String, CollectionCursor) -> Unit)?) {
        calendarCursorListener = listener
    }

    fun setAddressBookCursorListener(listener: ((String, String, CollectionCursor) -> Unit)?) {
        addressBookCursorListener = listener
    }

    override fun snapshot(): CalinoSnapshot = current

    /**
     * Replaces the ICS overlay. An empty overlay is how a removed subscription
     * disappears from the grid; it is not a no-op.
     */
    fun setWebcalOverlay(overlay: WebcalOverlay) {
        webcal = overlay
        publish()
    }

    /** A complete, raw export. Failure is propagated so callers never label a partial cache as a backup. */
    suspend fun exportCalendarEvents(calendarId: String): String {
        val source = sources.firstOrNull { it.calendar.url == calendarId }
            ?: throw IllegalArgumentException("That calendar is no longer connected.")
        val resources = fetcher.fetchAllEvents(source.calendar, source.credentials)
        return if (resources.isEmpty()) {
            "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Calino//Calino Android//EN\r\nEND:VCALENDAR\r\n"
        } else resources.joinToString("\r\n") { it.ics.trim() + "\r\n" }
    }

    /**
     * Registers [listener] and hands it the current snapshot immediately.
     *
     * The synchronous first emission is required, not a convenience: the
     * Compose bridge seeds its state from it, and deferring it renders one
     * empty frame before any data arrives.
     */
    override fun observe(listener: (CalinoSnapshot) -> Unit): Closeable {
        listeners += listener
        listener(current)
        return Closeable { listeners.remove(listener) }
    }

    /**
     * Points the repository at a new set of collections.
     *
     * An unchanged set is a no-op. This is called twice on a cold start --
     * once from the persisted account list, once when rediscovery confirms it
     * -- and the second call must not discard the loaded cache or refetch for
     * nothing. Persisted startup sources request immediate cache restoration
     * so Compose never observes an artificial empty snapshot first; network
     * reads and every non-startup cache reload remain asynchronous.
     */
    fun setSources(
        sources: List<CalDavSource>,
        addressBookSources: List<CardDavSource> = this.addressBookSources,
        restoreCacheImmediately: Boolean = false,
        refreshAfterSourceChange: Boolean = true,
    ) {
        // Discovery can keep the same collection URLs while changing the
        // ctag, read-only privilege, supported components, display name, or
        // credentials. Comparing only URLs would skip that fresh metadata and
        // leave incremental sync (and the editor's write guards) stale. The
        // repository-owned committed cursor is intentionally excluded: a
        // cursor commit is an internal acknowledgement, not a new fetch.
        val sameCalendars = sources.sameCalendarConfigurationAs(this.sources)
        val sameAddressBooks = addressBookSources.sameAddressBookConfigurationAs(this.addressBookSources)
        if (sameCalendars && sameAddressBooks) {
            this.sources = sources
            this.addressBookSources = addressBookSources
            restoreQueuedOverlays()
            if (refreshAfterSourceChange) drainPendingWrites()
            return
        }
        generation++
        this.sources = sources
        this.addressBookSources = addressBookSources
        restoreQueuedOverlays()
        if (sources.isEmpty() && addressBookSources.isEmpty()) {
            fetched = FetchedData()
            lastReadAt = null
            syncState = SyncState.Idle
            cache.evictExcept(emptySet())
            cache.evictAddressBooksExcept(emptySet())
            publish()
        } else {
            cache.evictExcept(sources.map { it.calendar.url }.toSet())
            cache.evictAddressBooksExcept(addressBookSources.map { it.addressBook.url }.toSet())
            if (refreshAfterSourceChange) {
                reload(useCache = true, restoreCacheImmediately = restoreCacheImmediately)
                drainPendingWrites()
            } else {
                val start = today().withDayOfMonth(1).minusMonths(windowMonths)
                val end = today().withDayOfMonth(1).plusMonths(windowMonths)
                publishCache(generation, loadCache(start, end))
            }
        }
    }

    /** Refetches without going back to the cache; the data on screen stays put. */
    fun refresh() = reload(useCache = false)

    /** Replay eligible writes, then refresh through the repository's serialized sync lane. */
    suspend fun synchronize(isCurrent: () -> Boolean = { true }): RepositorySyncResult =
        syncMutex.withLock {
            if (sources.isEmpty() && addressBookSources.isEmpty()) return@withLock RepositorySyncResult.NoSources
            if (!isCurrent()) return@withLock RepositorySyncResult.AccountsRemoved
            val token = ++generation
            val start = today().withDayOfMonth(1).minusMonths(windowMonths)
            val end = today().withDayOfMonth(1).plusMonths(windowMonths)
            syncState = SyncState.Loading(cachedAt = lastReadAt.takeUnless { fetched.isEmpty() })
            publish()
            withContext(AccountSyncGuard(isCurrent)) {
                refreshWhileLocked(token, start, end, isCurrent)
            }
        }

    /** Starts one serialized replay of durable writes, if a queue is configured. */
    fun drainPendingWrites() {
        if (pendingStore == null || drainJob?.isActive == true) return
        drainJob = scope.launch { syncMutex.withLock { drainQueue() } }
    }

    /** A snapshot view for the accounts surface and diagnostics. */
    fun pendingChanges(): List<PendingChange> = pendingStore?.snapshot().orEmpty()

    /** Makes a dead-lettered write eligible immediately, then starts replay. */
    fun retryPendingChange(id: String): Boolean {
        val store = pendingStore ?: return false
        val change = store.snapshot().firstOrNull { it.id == id } ?: return false
        val requeued = store.requeue(id) ?: return false
        if (requeued.state != PendingChangeState.PENDING) return false
        setWriteStatus(change.eventId, RecordWriteState.Pending, null)
        onPendingChangeEnqueued?.invoke()
        drainPendingWrites()
        return true
    }

    /** Discards a dead letter and removes its optimistic layer if it is alone. */
    fun discardPendingChange(id: String): Boolean {
        val store = pendingStore ?: return false
        val change = store.snapshot().firstOrNull { it.id == id } ?: return false
        if (!store.discard(id)) return false
        val hasAnotherForRecord = store.snapshot().any { it.eventId == change.eventId }
        if (!hasAnotherForRecord) {
            when (change.component.uppercase()) {
                "VEVENT" -> overlay.dropEvent(change.eventId)
                "VTODO" -> overlay.dropTask(change.eventId)
                "VJOURNAL" -> overlay.dropJournal(change.eventId)
                "VCARD" -> overlay.dropContact(change.eventId)
            }
            writeStatuses = writeStatuses - change.eventId
        }
        publish()
        return true
    }

    /** Updates the bounded event window and immediately reloads when it changed. */
    fun setWindowMonths(months: Long) {
        require(months > 0)
        if (windowMonths == months) return
        windowMonths = months
        reload(useCache = true)
    }

    /**
     * Includes a date reached through calendar navigation in the next server
     * read. The event REPORT's end is exclusive, so the upper boundary needs
     * one additional month to include a date on that boundary.
     *
     * This intentionally grows the existing, today-anchored preference rather
     * than replacing it with a narrow moving window. That keeps both sides of
     * a person's navigation readable and gives a later refresh the same full
     * snapshot/cache coverage. [loadAll] sees the wider interval and declines
     * incremental sync, because a delta cannot discover an unseen one-off.
     *
     * @return true when a wider fetch was started.
     */
    fun extendWindowToInclude(date: LocalDate): Boolean {
        val anchor = YearMonth.from(today())
        val currentStart = anchor.minusMonths(windowMonths).atDay(1)
        val currentEnd = anchor.plusMonths(windowMonths).atDay(1)
        if (date >= currentStart && date < currentEnd) return false
        val target = YearMonth.from(date)
        val requiredMonths = kotlin.math.abs(
            java.time.temporal.ChronoUnit.MONTHS.between(anchor, target),
        ) + 1
        if (requiredMonths <= windowMonths) return false
        windowMonths = requiredMonths
        reload(useCache = true)
        return true
    }

    private fun reload(useCache: Boolean, restoreCacheImmediately: Boolean = false) {
        if (sources.isEmpty() && addressBookSources.isEmpty()) return
        val token = ++generation
        val start = today().withDayOfMonth(1).minusMonths(windowMonths)
        val end = today().withDayOfMonth(1).plusMonths(windowMonths)
        syncState = SyncState.Loading(cachedAt = lastReadAt.takeUnless { fetched.isEmpty() })
        publish()
        if (useCache && restoreCacheImmediately) {
            publishCache(token, loadCache(start, end))
        }
        scope.launch {
            syncMutex.withLock {
                if (useCache && !restoreCacheImmediately) {
                    val cached = withContext(ioDispatcher) { loadCache(start, end) }
                    publishCache(token, cached)
                }
                if (token != generation) return@withLock
                drainQueue(isCurrent = { token == generation })
                if (token != generation) return@withLock
                runCatching { loadAll(start, end) { token == generation } }
                .onSuccess { loaded ->
                    if (token != generation) return@onSuccess
                    fetched = loaded.data
                    lastReadAt = Instant.now()
                    applyCursorUpdates(loaded.cursorUpdates)
                    applyAddressBookCursorUpdates(loaded.addressBookCursorUpdates)
                    // Re-apply durable group payloads against the newly read
                    // base before reconciliation. A restart or a cache load
                    // may have happened before this network answer arrived;
                    // row-level overlays alone cannot hide the old members of
                    // a FUTURE split or a queued MOVE.
                    restoreQueuedOverlays()
                    loaded.cursorUpdates.forEach { update ->
                        calendarCursorListener?.invoke(update.accountId, update.calendarUrl, update.cursor)
                    }
                    loaded.addressBookCursorUpdates.forEach { update ->
                        addressBookCursorListener?.invoke(update.accountId, update.addressBookUrl, update.cursor)
                    }
                    // A successful write may have reached the server before
                    // this REPORT. Reconcile only what the answer confirms.
                    overlay.reconcile(
                        serverEvents = loaded.data.events,
                        serverTasks = loaded.data.tasks,
                        serverJournals = loaded.data.journals,
                        serverContacts = loaded.data.contacts,
                        authoritative = loaded.authoritative,
                        guardedIds = pendingCalendarGuardedIds(),
                        guardedContactIds = pendingContactGuardedIds(),
                    )
                    syncState = SyncState.Ready(lastReadAt!!, warnings = loaded.warnings)
                    publish()
                }
                .onFailure { error ->
                    if (token != generation) return@onFailure
                    val message = calDavErrorForThrowable(error, sources.firstOrNull()?.calendar?.url.orEmpty()).message
                    // Keep whatever is already on screen -- which, after a
                    // cache load, is the last good read rather than nothing.
                    // Blanking the calendar because a refresh failed is worse
                    // than showing stale data alongside a clear error.
                    syncState = SyncState.Failed(message, hadPreviousData = !fetched.isEmpty())
                    publish()
                }
            }
        }
    }

    /** The awaitable sync reads after queue replay, so replay must not invalidate its generation. */
    private fun reloadAfterQueuedReplay(deferReloads: Boolean) {
        if (!deferReloads) reload(useCache = false)
    }

    private fun isTransientSyncFailure(error: Throwable): Boolean =
        calDavErrorForThrowable(error, sources.firstOrNull()?.calendar?.url.orEmpty())
            .isTransientSyncFailure()

    /** Caller holds [syncMutex]. */
    private suspend fun refreshWhileLocked(
        token: Long,
        start: LocalDate,
        end: LocalDate,
        isCurrent: () -> Boolean,
    ): RepositorySyncResult {
        if (token != generation || !isCurrent()) return RepositorySyncResult.AccountsRemoved
        return try {
            drainQueue(isCurrent, deferReloads = true)
            if (token != generation || !isCurrent()) return RepositorySyncResult.AccountsRemoved
            val loaded = loadAll(start, end, isCurrent)
            if (token != generation || !isCurrent()) return RepositorySyncResult.AccountsRemoved

            fetched = loaded.data
            lastReadAt = Instant.now()
            applyCursorUpdates(loaded.cursorUpdates)
            applyAddressBookCursorUpdates(loaded.addressBookCursorUpdates)
            restoreQueuedOverlays()
            loaded.cursorUpdates.forEach { update ->
                calendarCursorListener?.invoke(update.accountId, update.calendarUrl, update.cursor)
            }
            loaded.addressBookCursorUpdates.forEach { update ->
                addressBookCursorListener?.invoke(update.accountId, update.addressBookUrl, update.cursor)
            }
            overlay.reconcile(
                serverEvents = loaded.data.events,
                serverTasks = loaded.data.tasks,
                serverJournals = loaded.data.journals,
                serverContacts = loaded.data.contacts,
                authoritative = loaded.authoritative,
                guardedIds = pendingCalendarGuardedIds(),
                guardedContactIds = pendingContactGuardedIds(),
            )
            syncState = SyncState.Ready(lastReadAt!!, warnings = loaded.warnings)
            publish()

            val pendingStates = pendingStore?.snapshot().orEmpty().map { it.state }
            val retryNeeded = RepositorySyncRetryPolicy.retryNeeded(
                transientReadFailure = loaded.retryableFailure,
                pendingStates = pendingStates,
            )
            RepositorySyncResult.Success(
                warnings = loaded.warnings,
                retryNeeded = retryNeeded,
                message = loaded.warnings.firstOrNull()
                    ?: RepositorySyncRetryPolicy.pendingQueueMessage(pendingStates),
            )
        } catch (_: AccountSyncAbortedException) {
            cache.evictExcept(sources.map { it.calendar.url }.toSet())
            cache.evictAddressBooksExcept(addressBookSources.map { it.addressBook.url }.toSet())
            RepositorySyncResult.AccountsRemoved
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (token != generation || !isCurrent()) return RepositorySyncResult.AccountsRemoved
            val message = calDavErrorForThrowable(error, sources.firstOrNull()?.calendar?.url.orEmpty()).message
            syncState = SyncState.Failed(message, hadPreviousData = !fetched.isEmpty())
            publish()
            RepositorySyncResult.Failed(message, isTransientSyncFailure(error))
        }
    }

    private fun publishCache(token: Long, cached: CacheLoad?) {
        // Only if nothing newer has arrived, and only if the cache actually
        // held something -- publishing an empty cache would blank a calendar
        // that a concurrent refresh is filling.
        if (token != generation || cached == null || cached.data.isEmpty()) return
        fetched = cached.data
        lastReadAt = cached.fetchedAt
        syncState = SyncState.Loading(cachedAt = cached.fetchedAt)
        restoreQueuedOverlays()
        publish()
    }

    /**
     * Makes the next refresh start at the cursor just committed by this read.
     * The metadata is no longer fresh, however: a REPORT does not return a
     * ctag, so a later refresh must perform another REPORT rather than using
     * the discovery-only ctag skip shortcut.
     */
    private fun applyCursorUpdates(updates: List<CalendarCursorUpdate>) {
        if (updates.isEmpty()) return
        sources = sources.map { source ->
            val update = updates.firstOrNull {
                it.accountId == source.accountId && it.calendarUrl == source.calendar.url
            } ?: return@map source
            source.copy(
                calendar = source.calendar.copy(
                    ctag = update.cursor.ctag,
                    syncToken = update.cursor.syncToken,
                ),
                committedCursor = update.cursor,
                metadataFresh = false,
            )
        }
    }

    private fun applyAddressBookCursorUpdates(updates: List<AddressBookCursorUpdate>) {
        if (updates.isEmpty()) return
        addressBookSources = addressBookSources.map { source ->
            val update = updates.firstOrNull {
                it.accountId == source.accountId && it.addressBookUrl == source.addressBook.url
            } ?: return@map source
            source.copy(
                addressBook = source.addressBook.copy(
                    ctag = update.cursor.ctag,
                    syncToken = update.cursor.syncToken,
                ),
                committedCursor = update.cursor,
                metadataFresh = false,
            )
        }
    }

    private data class CacheLoad(val data: FetchedData, val fetchedAt: Instant)

    /**
     * Maps every cached calendar against the window computed from *today*, not
     * the window the resources were fetched under. That is the point of caching
     * the server's text rather than mapped occurrences: a series re-expands
     * into the current window on its own.
     */
    private fun loadCache(start: LocalDate, end: LocalDate): CacheLoad? {
        val events = mutableListOf<CalEvent>()
        val tasks = mutableListOf<CalTask>()
        val journals = mutableListOf<JournalEntry>()
        val contacts = mutableListOf<Contact>()
        var oldest: Instant? = null

        sources.forEach { source ->
            val entry = cache.load(source.calendar.url) ?: return@forEach
            val parsed = mapper.mapAll(
                resources = entry.resources,
                calendarId = source.calendar.url,
                color = source.calendar.color,
                windowStart = start,
                windowEnd = end,
            )
            events += parsed.events
            tasks += parsed.tasks
            journals += parsed.journals
            oldest = oldest?.let { minOf(it, entry.fetchedAt) } ?: entry.fetchedAt
        }

        addressBookSources.forEach { source ->
            val entry = cache.loadAddressBook(source.addressBook.url) ?: return@forEach
            entry.resources.forEach { resource ->
                vCardMapper.map(
                    vcf = resource.vcf,
                    addressBookId = source.addressBook.url,
                    accountId = source.accountId,
                    href = resource.href,
                    etag = resource.etag,
                    )?.let(contacts::add)
            }
            oldest = oldest?.let { minOf(it, entry.fetchedAt) } ?: entry.fetchedAt
        }

        // The oldest read is the honest one to report: saying "read a minute
        // ago" when one collection's copy is a week old would overstate it.
        return oldest?.let { CacheLoad(FetchedData(events, tasks, journals, contacts), it) }
    }

    private data class Loaded(
        val data: FetchedData,
        val warnings: List<String>,
        val authoritative: Boolean,
        val retryableFailure: Boolean = false,
        val cursorUpdates: List<CalendarCursorUpdate> = emptyList(),
        val addressBookCursorUpdates: List<AddressBookCursorUpdate> = emptyList(),
    )

    private data class CalendarCursorUpdate(
        val accountId: String,
        val calendarUrl: String,
        val cursor: CollectionCursor,
    )

    private data class AddressBookCursorUpdate(
        val accountId: String,
        val addressBookUrl: String,
        val cursor: CollectionCursor,
    )

    private suspend fun loadAll(
        start: LocalDate,
        end: LocalDate,
        isCurrent: () -> Boolean = { true },
    ): Loaded = withContext(ioDispatcher) {
        val events = mutableListOf<CalEvent>()
        val tasks = mutableListOf<CalTask>()
        val journals = mutableListOf<JournalEntry>()
        val contacts = mutableListOf<Contact>()
        val warnings = mutableListOf<String>()
        var anySucceeded = false
        var authoritative = true
        var retryableFailure = false
        var lastError: Throwable? = null
        val cursorUpdates = mutableListOf<CalendarCursorUpdate>()
        val addressBookCursorUpdates = mutableListOf<AddressBookCursorUpdate>()

        sources.forEach { source ->
            if (!isCurrent()) throw AccountSyncAbortedException()
            val name = source.calendar.displayName
            val cached = cache.load(source.calendar.url)
            // The resource set from a time-ranged event report is complete
            // only for the range it covered. A delta cannot discover a new
            // one-off event outside that range, so widenings use a full read.
            val cachedForIncremental = cached
                ?.takeIf { it.windowStart <= start && it.windowEnd >= end }
                ?.resources
            runCatching {
                fetcher.fetchIncremental(
                    calendar = source.calendar,
                    credentials = source.credentials,
                    cachedResources = cachedForIncremental,
                    windowStart = start,
                    windowEnd = end,
                    storedCursor = source.committedCursor.takeIf { source.metadataFresh },
                )
            }
                .onSuccess { result ->
                    if (!isCurrent()) throw AccountSyncAbortedException()
                    val fetchResult = result.fetchResult
                    anySucceeded = true
                    if (fetchResult.hadComponentFailures) authoritative = false
                    retryableFailure = retryableFailure || fetchResult.failures.any { it.error.isTransientSyncFailure() }
                    val parsed = mapper.mapAll(
                        resources = fetchResult.resources,
                        calendarId = source.calendar.url,
                        color = source.calendar.color,
                        windowStart = start,
                        windowEnd = end,
                    )
                    val usableResources = if (parsed.failedResourceHrefs.isEmpty()) {
                        fetchResult.resources
                    } else {
                        // Keep the last known-good bytes for a changed href
                        // that no longer parses. A malformed delta must not
                        // look like a deletion, and its token must not be
                        // committed below.
                        fetchResult.resources.mapNotNull { resource ->
                            if (resource.href !in parsed.failedResourceHrefs) {
                                resource
                            } else {
                                cached?.resources?.firstOrNull { it.href == resource.href }
                            }
                        }
                    }
                    val mapped = if (usableResources === fetchResult.resources) {
                        parsed
                    } else {
                        mapper.mapAll(
                            resources = usableResources,
                            calendarId = source.calendar.url,
                            color = source.calendar.color,
                            windowStart = start,
                            windowEnd = end,
                        )
                    }
                    events += mapped.events
                    tasks += mapped.tasks
                    journals += mapped.journals
                    if (parsed.failedResourceHrefs.isNotEmpty()) {
                        authoritative = false
                        warnings += "$name -- ${parsed.failedResourceHrefs.size} calendar resource(s) could not be read safely."
                    }
                    fetchResult.failures.forEach { warnings += "$name -- ${it.describe()}" }
                    // Never replace a complete cache with a partial answer:
                    // on the next launch that would turn a read failure into
                    // an apparent deletion. The partial resources are still
                    // useful for this session and the warning remains visible.
                    val cacheComplete = if (!fetchResult.hadComponentFailures && parsed.failedResourceHrefs.isEmpty()) {
                        saveCache(source, fetchResult.resources, start, end)
                    } else false
                    if (!cacheComplete && !fetchResult.hadComponentFailures && parsed.failedResourceHrefs.isEmpty()) {
                        warnings += "$name -- The complete calendar could not be cached; it will be fetched again."
                    }
                    if (cacheComplete) {
                        cursorUpdates += CalendarCursorUpdate(
                            accountId = source.accountId,
                            calendarUrl = source.calendar.url,
                            cursor = result.cursor,
                        )
                    }
                }
                .onFailure { error ->
                    if (!isCurrent()) throw AccountSyncAbortedException()
                    authoritative = false
                    lastError = error
                    retryableFailure = retryableFailure || isTransientSyncFailure(error)
                    warnings += "$name -- ${calDavErrorForThrowable(error, source.calendar.url).message}"
                }
        }

        addressBookSources.forEach { source ->
            if (!isCurrent()) throw AccountSyncAbortedException()
            val name = source.addressBook.displayName
            val cached = cache.loadAddressBook(source.addressBook.url)
            runCatching {
                cardFetcher.fetchIncremental(
                    book = source.addressBook,
                    credentials = source.credentials,
                    cachedResources = cached?.resources,
                    storedCursor = source.committedCursor.takeIf { source.metadataFresh },
                )
            }
                .onSuccess { incremental ->
                    if (!isCurrent()) throw AccountSyncAbortedException()
                    val result = incremental.fetchResult
                    anySucceeded = true
                    if (result.partialFailure) authoritative = false
                    retryableFailure = retryableFailure || result.failures.any { it.message.isTransientSyncFailure() }
                    // A partial REPORT cannot prove that a missing card was
                    // deleted. Keep the last complete book when one exists;
                    // if this is the first read, show the usable cards but do
                    // not persist the incomplete answer as the new cache.
                    val resources = if (result.isAuthoritative) {
                        result.resources
                    } else {
                        cache.loadAddressBook(source.addressBook.url)?.resources
                            ?: result.resources
                    }
                    var mappingFailure = false
                    resources.forEach { resource ->
                        val contact = vCardMapper.map(
                            vcf = resource.vcf,
                            addressBookId = source.addressBook.url,
                            accountId = source.accountId,
                            href = resource.href,
                            etag = resource.etag,
                        )
                        if (contact != null) {
                            contacts += contact
                        } else {
                            mappingFailure = true
                            // An incremental response may contain a malformed
                            // replacement for a previously valid card. Keep
                            // that last good card visible, but do not save or
                            // advance the cursor for this incomplete answer.
                            cache.loadAddressBook(source.addressBook.url)
                                ?.resources
                                ?.firstOrNull { it.href == resource.href }
                                ?.let { previous ->
                                    vCardMapper.map(
                                        vcf = previous.vcf,
                                        addressBookId = source.addressBook.url,
                                        accountId = source.accountId,
                                        href = previous.href,
                                        etag = previous.etag,
                                    )?.let(contacts::add)
                                }
                        }
                    }
                    if (result.partialFailure) warnings += "$name -- some contacts could not be read."
                    if (mappingFailure) {
                        authoritative = false
                        warnings += "$name -- one or more contact resources could not be read safely."
                    }
                    if (result.isAuthoritative && !mappingFailure) {
                        saveAddressBookCache(source, result.resources)
                        addressBookCursorUpdates += AddressBookCursorUpdate(
                            accountId = source.accountId,
                            addressBookUrl = source.addressBook.url,
                            cursor = incremental.cursor,
                        )
                    }
                }
                .onFailure { error ->
                    if (!isCurrent()) throw AccountSyncAbortedException()
                    authoritative = false
                    lastError = error
                    retryableFailure = retryableFailure || isTransientSyncFailure(error)
                    warnings += "$name -- ${calDavErrorForThrowable(error, source.addressBook.url).message}"
                }
        }

        if (!isCurrent()) throw AccountSyncAbortedException()

        // One unreachable calendar should not blank the others.
        if (!anySucceeded) throw (lastError ?: IllegalStateException("No calendars could be read."))
        Loaded(
            data = FetchedData(events, tasks, journals, contacts),
            warnings = warnings,
            authoritative = authoritative,
            retryableFailure = retryableFailure,
            cursorUpdates = cursorUpdates,
            addressBookCursorUpdates = addressBookCursorUpdates,
        )
    }

    private fun saveCache(
        source: CalDavSource,
        resources: List<CalendarResource>,
        start: LocalDate,
        end: LocalDate,
    ): Boolean =
        cache.saveComplete(
            CachedCalendar(
                calendarUrl = source.calendar.url,
                fetchedAt = Instant.now(),
                windowStart = start,
                windowEnd = end,
                resources = resources,
            ),
        )

    private fun saveAddressBookCache(source: CardDavSource, resources: List<CardResource>) {
        cache.saveAddressBook(
            CachedAddressBook(
                addressBookUrl = source.addressBook.url,
                fetchedAt = Instant.now(),
                resources = resources,
            ),
        )
    }

    /**
     * A queued recurrence write represents every occurrence in its payload,
     * not only the selected row's id. Keep every visible member of that UID
     * guarded until the server has confirmed the resource.
     */
    private fun pendingCalendarGuardedIds(): Set<String> {
        val changes = pendingStore?.snapshot()
            ?.filterNot { it.state == PendingChangeState.DEAD_LETTER }
            .orEmpty()
            .filterNot { it.component.equals("VCARD", ignoreCase = true) }
        if (changes.isEmpty()) return emptySet()
        val visible = current.events + current.tasks + current.journals
        return changes.flatMap { change ->
            val payloadUids = pendingChangeUids(change)
            val ids = visible.filter { record ->
                when (record) {
                    is CalEvent -> change.component.equals("VEVENT", ignoreCase = true) &&
                        (record.uid ?: record.id) in payloadUids
                    is CalTask -> change.component.equals("VTODO", ignoreCase = true) &&
                        (record.uid ?: record.id) in payloadUids
                    is JournalEntry -> change.component.equals("VJOURNAL", ignoreCase = true) &&
                        (record.uid ?: record.id) in payloadUids
                    else -> false
                }
            }.map {
                when (it) {
                    is CalEvent -> it.id
                    is CalTask -> it.id
                    is JournalEntry -> it.id
                    else -> change.eventId
                }
            }
            ids + change.eventId
        }.toSet()
    }

    /**
     * A recurrence split can carry both the old and freshly generated UID in
     * one queued VCALENDAR. Guard every visible member, including future
     * occurrences of the new series, until the resource has been confirmed by
     * a read. The scalar UID remains the fast path for deletes and ordinary
     * writes whose payload is absent or not parseable.
     */
    private fun pendingChangeUids(change: PendingChange): Set<String> {
        val uids = mutableSetOf<String>()
        change.uid?.takeIf(String::isNotBlank)?.let(uids::add)
        change.eventId.takeIf(String::isNotBlank)?.let(uids::add)
        val data = change.data ?: return uids
        runCatching { ICalTimezones.parse(data) }.getOrNull().orEmpty().forEach { calendar ->
            when (change.component.uppercase()) {
                "VEVENT" -> calendar.events.mapNotNull { it.uid?.value }.forEach(uids::add)
                "VTODO" -> calendar.todos.mapNotNull { it.uid?.value }.forEach(uids::add)
                "VJOURNAL" -> calendar.journals.mapNotNull { it.uid?.value }.forEach(uids::add)
            }
        }
        return uids
    }

    private fun pendingCreateFor(
        eventId: String,
        component: String,
        calendarUrl: String,
    ): PendingChange? = pendingStore?.snapshot()
        ?.asReversed()
        ?.firstOrNull { change ->
            change.type == PendingChangeType.CREATE &&
                change.state != PendingChangeState.DEAD_LETTER &&
                change.eventId == eventId &&
                change.component.equals(component, ignoreCase = true) &&
                (change.calendarUrl == calendarUrl || change.calendarId == calendarUrl)
        }

    /** Collapses an edit made before a local CREATE reaches the server. */
    private suspend fun <T> coalesceQueuedCalendarCreate(
        source: CalDavSource,
        eventId: String,
        pending: PendingChange,
        prepare: () -> PreparedCalendarWrite,
        record: (PreparedCalendarWrite) -> T,
    ): WriteResult<T> {
        val store = pendingStore ?: return WriteResult.Rejected("The pending write queue is not available.")
        val outcome = attemptWrite(source.calendar.url) {
            val prepared = prepare()
            check(
                withContext(ioDispatcher) {
                    store.replaceCreatePayload(pending.id, prepared.body, prepared.href) != null
                },
            ) { "The queued create changed before the edit could be merged." }
            prepared
        }
        val prepared = outcome.getOrNull()
        if (prepared == null) {
            val reason = "The queued create could not absorb this edit. Try the edit again."
            setWriteStatus(eventId, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        setWriteStatus(eventId, RecordWriteState.Pending, "Waiting for connection to create this item.")
        drainPendingWrites()
        return WriteResult.Queued(record(prepared))
    }

    /** Collapses an edit made before a local contact CREATE reaches CardDAV. */
    private suspend fun <T> coalesceQueuedCardCreate(
        source: CardDavSource,
        eventId: String,
        pending: PendingChange,
        prepare: () -> PreparedCardWrite,
        record: (PreparedCardWrite) -> T,
    ): WriteResult<T> {
        val store = pendingStore ?: return WriteResult.Rejected("The pending write queue is not available.")
        val outcome = attemptWrite(source.addressBook.url) {
            val prepared = prepare()
            check(
                withContext(ioDispatcher) {
                    store.replaceCreatePayload(pending.id, prepared.body, prepared.href) != null
                },
            ) { "The queued contact create changed before the edit could be merged." }
            prepared
        }
        val prepared = outcome.getOrNull()
        if (prepared == null) {
            val reason = "The queued contact create could not absorb this edit. Try the edit again."
            setWriteStatus(eventId, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        setWriteStatus(eventId, RecordWriteState.Pending, "Waiting for connection to create this contact.")
        drainPendingWrites()
        return WriteResult.Queued(record(prepared))
    }

    private fun pendingContactGuardedIds(): Set<String> = pendingStore?.snapshot()
        ?.filterNot { it.state == PendingChangeState.DEAD_LETTER }
        ?.filter { it.component.equals("VCARD", ignoreCase = true) }
        ?.flatMap { change ->
            val uid = change.uid ?: change.eventId
            contacts().filter { it.id == change.eventId || it.uid == uid || it.id == uid }.map { it.id }
                .plus(change.eventId)
        }
        ?.toSet()
        .orEmpty()

    // --- writes ---------------------------------------------------------------

    override suspend fun addEvent(input: NewEvent): WriteResult<CalEvent> {
        val source = sourceForCreate("VEVENT", input.calendarId)
        writableRejection(source, "VEVENT")?.let { return it }
        source ?: return WriteResult.Rejected("No calendar is connected.")

        val local = overlay.newEvent(input.copy(calendarId = source.calendar.url))
        val candidate = local.copy(uid = input.uid ?: local.id, calendarId = source.calendar.url)
        return when (val result = putEventOnServer(source, candidate, PendingChangeType.CREATE)) {
            is WriteResult.Applied -> result.also {
                overlay.putEvent(it.record)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.putEvent(it.record)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    override suspend fun updateEvent(id: String, input: NewEvent): WriteResult<CalEvent> {
        val current = events().firstOrNull { it.id == id }
            ?: return WriteResult.Rejected("That calendar event is no longer available.")

        // A locally-created event has no server ETag yet. Keep the edit in
        // the existing CREATE slot instead of manufacturing an UPDATE that
        // can never carry a conditional version.
        pendingCreateFor(current.id, "VEVENT", current.calendarId)?.let { pending ->
            val source = sourceForRecord(current.calendarId, current.href)
            val requestedSource = sources.firstOrNull { it.calendar.url == input.calendarId }
            if (source != null && (requestedSource == null || requestedSource.calendar.url == source.calendar.url)) {
                writableRejection(source, "VEVENT")?.let { return it }
                val uid = current.uid ?: current.id
                val candidate = overlay.newEvent(input.copy(calendarId = source.calendar.url)).keepingAttachments(input, current).copy(
                    id = current.id,
                    uid = uid,
                    href = null,
                    etag = null,
                    calendarId = source.calendar.url,
                    recurrence = if (input.recurrenceChanged) input.recurrence else current.recurrence,
                    recurrenceId = current.recurrenceId,
                    recurrenceDate = current.recurrenceDate,
                    sequence = current.sequence,
                )
                return when (val result = coalesceQueuedCalendarCreate(
                    source = source,
                    eventId = current.id,
                    pending = pending,
                    prepare = { writer.prepareEvent(source.calendar, candidate.copy(href = null, etag = null)) },
                    record = { prepared -> candidate.copy(href = prepared.href, etag = null) },
                )) {
                    is WriteResult.Queued -> result.also {
                        overlay.putEvent(it.record)
                        publish()
                    }
                    is WriteResult.Applied -> result
                    is WriteResult.Rejected -> result
                }
            }
        }
        if (current.recurrence != null || isRecurringTarget(current)) {
            val requestedSource = sources.firstOrNull { it.calendar.url == input.calendarId }
            if (requestedSource != null && requestedSource.calendar.url != current.calendarId) {
                return moveEvent(id, input)
            }
            val source = sourceForRecord(current.calendarId, current.href)
            writableRejection(source, "VEVENT")?.let { return it }
            source ?: return WriteResult.Rejected("That event's calendar is no longer connected.")
            val candidate = overlay.newEvent(input.copy(calendarId = source.calendar.url)).keepingAttachments(input, current).copy(
                id = current.id,
                uid = current.uid ?: current.id,
                href = current.href,
                etag = current.etag,
                calendarId = source.calendar.url,
                recurrence = if (input.recurrenceChanged) input.recurrence else current.recurrence,
                recurrenceId = current.recurrenceId,
                recurrenceDate = current.recurrenceDate,
                sequence = current.sequence,
            )
            return when (val result = putRecurringEventOnServer(
                source = source,
                event = candidate,
                scope = input.recurrenceScope,
                recurrenceChanged = input.recurrenceChanged,
            )) {
                is WriteResult.Applied -> result.also {
                    if (input.recurrenceScope == RecurrenceEditScope.This) {
                        overlay.putEvent(it.record)
                    } else {
                        // A wider edit can change the UID/identity of the
                        // visible occurrence (FUTURE) or replace the master
                        // behind it (ALL). Do not leave the old expanded row
                        // as a phantom while the authoritative read lands.
                        hideEventGroup(current)
                    }
                    publish()
                    reload(useCache = false)
                }
                is WriteResult.Queued -> result.also {
                    restoreQueuedEventOverlay(current.id)
                    publish()
                }
                is WriteResult.Rejected -> result
            }
        }
        val requestedSource = sources.firstOrNull { it.calendar.url == input.calendarId }
        if (requestedSource != null && requestedSource.calendar.url != current.calendarId) {
            return moveEvent(id, input)
        }
        val source = sourceForRecord(current.calendarId, current.href)
        writableRejection(source, "VEVENT")?.let { return it }
        source ?: return WriteResult.Rejected("That event's calendar is no longer connected.")

        val candidate = overlay.newEvent(input.copy(calendarId = source.calendar.url)).keepingAttachments(input, current).copy(
            id = current.id,
            uid = current.uid ?: current.id,
            href = current.href,
            etag = current.etag,
            calendarId = source.calendar.url,
            recurrenceId = current.recurrenceId,
            recurrenceDate = current.recurrenceDate,
            sequence = current.sequence,
        )
        return when (val result = putEventOnServer(source, candidate, PendingChangeType.UPDATE)) {
            is WriteResult.Applied -> result.also {
                overlay.putEvent(it.record)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.putEvent(it.record)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    /**
     * Moves an event resource to another writable calendar.
     *
     * The destination is written first, with an unconditional PUT so a retry
     * after a process death is idempotent. The old resource is removed only
     * after that PUT succeeds and is always conditional on the source ETag.
     * If source cleanup cannot be completed, a distinct DELETE_HREF queue item
     * preserves the old URL instead of pretending the move was atomic.
     */
    suspend fun moveEvent(id: String, input: NewEvent): WriteResult<CalEvent> {
        val current = events().firstOrNull { it.id == id }
            ?: return WriteResult.Rejected("That calendar event is no longer available.")
        val destination = sources.firstOrNull { it.calendar.url == input.calendarId }
            ?: return WriteResult.Rejected("That destination calendar is no longer connected.")
        val source = sourceForRecord(current.calendarId, current.href)
            ?: return WriteResult.Rejected("That event's calendar is no longer connected.")
        if (destination.calendar.url == source.calendar.url) {
            return updateEvent(id, input)
        }
        writableRejection(source, "VEVENT")?.let { return it }
        writableRejection(destination, "VEVENT")?.let { return it }

        val uid = current.uid ?: current.id
        val sourceHref = resourceHref(source.calendar.url, current.href, uid)
        var sourceResource = withContext(ioDispatcher) {
            cache.loadResource(source.calendar.url, sourceHref)
        }
        val cacheMatchesCurrent = sourceResource != null &&
            (current.etag == null || sameEtag(sourceResource?.etag, current.etag))
        if (!cacheMatchesCurrent || sourceResource?.etag == null) {
            val refreshed = attemptWrite(source.calendar.url) {
                writer.refreshResource(source.calendar, source.credentials, sourceHref, uid)
            }
            val refreshError = refreshed.exceptionOrNull()
            if (refreshError != null) {
                return rejected(refreshError, source.calendar.url)
            }
            sourceResource = refreshed.getOrThrow()
        }
        val raw = sourceResource
            ?: return WriteResult.Rejected("That event is not available in the raw calendar cache. Refresh and try again.")
        val sourceEtag = normalizeEtag(raw.etag ?: current.etag)
            ?: return WriteResult.Rejected("That event has no current server version. Refresh and try again.")

        val candidate = overlay.newEvent(input.copy(calendarId = destination.calendar.url)).copy(
            id = current.id,
            uid = uid,
            href = raw.href,
            etag = sourceEtag,
            calendarId = destination.calendar.url,
            recurrence = if (input.recurrenceChanged) input.recurrence else current.recurrence,
            recurrenceId = current.recurrenceId,
            recurrenceDate = current.recurrenceDate,
            sequence = current.sequence,
        )
        val contentChanged = candidate.copy(uid = null, href = null, etag = null, calendarId = "") !=
            current.copy(uid = null, href = null, etag = null, calendarId = "")
        val payload = if (!contentChanged) {
            raw.ics
        } else {
                buildMovePayload(
                    originalIcs = raw.ics,
                    uid = uid,
                    event = candidate,
                    scope = input.recurrenceScope,
                    recurrenceChanged = input.recurrenceChanged,
                )
                ?: return WriteResult.Rejected("That recurring event could not be moved safely. Refresh and try again.")
        }

        val members = events().filter { event ->
            (event.uid ?: event.id) == uid &&
                event.calendarId == source.calendar.url &&
                (event.href == null || CalDavWriter.resolveHref(source.calendar.url, event.href) == raw.href)
        }.ifEmpty { listOf(current) }
        val planResult = CalDavMovePlanner.plan(
            CalDavMoveRequest(
                members = members,
                target = destination.calendar,
                payload = payload,
                source = CalDavMoveSource(source.calendar, raw.href, sourceEtag),
            ),
        )
        val plan = planResult.plan ?: return WriteResult.Rejected(
            planResult.message ?: "That event cannot be moved safely.",
        )

        var destinationOutcome = attemptWrite(destination.calendar.url) {
            putMoveDestination(
                destination = destination,
                href = plan.destination.href,
                uid = plan.uid,
                body = plan.destination.payload,
            )
        }
        val destinationError = destinationOutcome.exceptionOrNull()
        if (destinationError != null) {
            if (destinationError is MoveDestinationConflictException) {
                return WriteResult.Rejected(destinationError.message ?: "The destination already has a different item.")
            }
            return queueMoveFailure(
                source = source,
                destination = destination,
                plan = plan,
                candidate = candidate.copy(href = plan.destination.href, etag = null),
                error = destinationError,
                sourceData = raw.ics,
            ).also { result ->
                if (result !is WriteResult.Rejected) {
                    val record = (result as WriteResult.Queued).record
                    applyEventResourceOverlay(
                        calendar = destination.calendar,
                        href = plan.destination.href,
                        etag = record.etag,
                        ics = plan.destination.payload,
                        affectedCalendarUrls = setOf(source.calendar.url, destination.calendar.url),
                        fallback = record,
                    )
                    publish()
                }
            }
        }

        val written = destinationOutcome.getOrThrow()
        val sourceDeletion = plan.sourceDeletion
        if (sourceDeletion != null) {
            val deleteOutcome = attemptWrite(source.calendar.url) {
                writer.delete(
                    calendar = sourceDeletion.calendar,
                    credentials = source.credentials,
                    href = sourceDeletion.href,
                    uid = sourceDeletion.uid,
                    etag = sourceDeletion.etag,
                    component = "VEVENT",
                )
            }
            val deleteError = deleteOutcome.exceptionOrNull()
            if (deleteError != null && !isGoneOrMissing(deleteError)) {
                val cleanup = queueMoveSourceCleanup(
                    source = source,
                    event = candidate,
                    href = sourceDeletion.href,
                    etag = sourceDeletion.etag,
                    data = raw.ics,
                    error = deleteError,
                )
                return when (cleanup) {
                    is WriteResult.Queued -> {
                        val moved = candidate.copy(href = written.href, etag = written.etag)
                        applyEventResourceOverlay(
                            calendar = destination.calendar,
                            href = written.href,
                            etag = written.etag,
                            ics = written.ics,
                            affectedCalendarUrls = setOf(source.calendar.url, destination.calendar.url),
                            fallback = moved,
                        )
                        publish()
                        WriteResult.Queued(moved)
                    }
                    is WriteResult.Applied -> error("source cleanup cannot return Applied")
                    is WriteResult.Rejected -> cleanup
                }
            }
        }

        val moved = candidate.copy(href = written.href, etag = written.etag)
        applyEventResourceOverlay(
            calendar = destination.calendar,
            href = written.href,
            etag = written.etag,
            ics = written.ics,
            affectedCalendarUrls = setOf(source.calendar.url, destination.calendar.url),
            fallback = moved,
        )
        publish()
        return WriteResult.Applied(moved)
    }

    // The server path identifies an occurrence from the record itself -- an
    // expanded occurrence carries its own RECURRENCE-ID -- so it has no use
    // for the caller's occurrence date.
    override suspend fun deleteEvent(
        id: String,
        scope: RecurrenceEditScope,
        occurrenceDate: LocalDate?,
    ): WriteResult<Unit> {
        val current = events().firstOrNull { it.id == id }
            ?: return WriteResult.Applied(Unit)
        val source = sourceForRecord(current.calendarId, current.href)
        writableRejection(source, "VEVENT")?.let { return it }
        source ?: return WriteResult.Rejected("That event's calendar is no longer connected.")
        if (current.recurrence != null || isRecurringTarget(current)) {
            return when (val result = deleteRecurringEventOnServer(source, current, scope)) {
                is WriteResult.Applied -> result.also {
                    if (scope == RecurrenceEditScope.This) {
                        overlay.deleteEvent(current.id)
                    } else {
                        hideEventGroup(current)
                    }
                    publish()
                    reload(useCache = false)
                }
                is WriteResult.Queued -> result.also {
                    if (scope == RecurrenceEditScope.This) {
                        overlay.deleteEvent(current.id)
                    } else {
                        hideEventGroup(current)
                    }
                    restoreQueuedEventOverlay(current.id)
                    publish()
                }
                is WriteResult.Rejected -> result
            }
        }
        return when (val result = deleteOnServer(source, current.id, current.uid ?: current.id, current.href, current.etag, "VEVENT")) {
            is WriteResult.Applied -> result.also {
                overlay.deleteEvent(current.id)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.deleteEvent(current.id)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    override suspend fun addTask(input: NewTask): WriteResult<CalTask> {
        recurringTaskValidation(input, tasks())?.let { return WriteResult.Rejected(it) }
        val source = sourceForCreate("VTODO", preferredId = input.calendarId, href = input.href)
        writableRejection(source, "VTODO")?.let { return it }
        source ?: return WriteResult.Rejected("No calendar is connected.")

        val local = overlay.newTask(input.copy(calendarId = source.calendar.url))
        val uid = input.uid ?: local.id
        val candidate = local.copy(
            id = uid,
            uid = uid,
            href = input.href,
            etag = input.etag,
        )
        return when (val result = putTaskOnServer(source, candidate, PendingChangeType.CREATE)) {
            is WriteResult.Applied -> result.also {
                overlay.putTask(it.record)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.putTask(it.record)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    override suspend fun updateTask(id: String, input: NewTask, done: Boolean): WriteResult<CalTask> {
        recurringTaskValidation(input, tasks(), id)?.let { return WriteResult.Rejected(it) }
        val current = tasks().firstOrNull { it.id == id }
            ?: return WriteResult.Rejected("That task is no longer available.")
        val source = sourceForRecord(null, current.href)
        writableRejection(source, "VTODO")?.let { return it }
        source ?: return WriteResult.Rejected("That task's calendar is no longer connected.")

        pendingCreateFor(current.id, "VTODO", source.calendar.url)?.let { pending ->
            val candidate = overlay.newTask(input.copy(calendarId = source.calendar.url)).copy(
                id = current.id,
                uid = current.uid ?: current.id,
                href = null,
                etag = null,
            ).copy(done = done)
            return when (val result = coalesceQueuedCalendarCreate(
                source = source,
                eventId = current.id,
                pending = pending,
                prepare = { writer.prepareTask(source.calendar, candidate.copy(href = null, etag = null)) },
                record = { prepared -> candidate.copy(href = prepared.href, etag = null) },
            )) {
                is WriteResult.Queued -> result.also {
                    overlay.putTask(it.record)
                    publish()
                }
                is WriteResult.Applied -> result
                is WriteResult.Rejected -> result
            }
        }

        val candidate = overlay.newTask(input.copy(calendarId = source.calendar.url)).copy(
            id = current.id,
            uid = current.uid ?: current.id,
            href = current.href,
            etag = current.etag,
        ).copy(done = done)
        return when (val result = putTaskOnServer(source, candidate, PendingChangeType.UPDATE)) {
            is WriteResult.Applied -> result.also {
                overlay.putTask(it.record)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.putTask(it.record)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    override suspend fun deleteTask(id: String, scope: RecurrenceEditScope): WriteResult<Unit> {
        val current = tasks().firstOrNull { it.id == id }
            ?: return WriteResult.Applied(Unit)
        val source = sourceForRecord(null, current.href)
        writableRejection(source, "VTODO")?.let { return it }
        source ?: return WriteResult.Rejected("That task's calendar is no longer connected.")
        if ((current.recurrence != null || current.recurrenceId != null || current.recurrenceDate != null) &&
            scope != RecurrenceEditScope.All
        ) {
            val uid = current.uid ?: current.id
            val href = resourceHref(source.calendar.url, current.href, uid)
            return when (val result = putCalendarWithQueue(
                source = source,
                changeType = PendingChangeType.UPDATE,
                eventId = current.id,
                component = "VTODO",
                uid = uid,
                etag = current.etag,
                prepare = { requestedEtag ->
                    val resource = cache.loadResource(source.calendar.url, href)
                        ?: throw CalDavException(CalDavErrorCode.PreconditionFailed, "The recurring task is not available in the raw cache.", status = 412)
                    val expected = normalizeEtag(requestedEtag) ?: normalizeEtag(resource.etag)
                        ?: throw CalDavException(CalDavErrorCode.PreconditionFailed, "The recurring task has no current server version.", status = 412)
                    val body = recurrencePatcher.deleteTaskRecurrence(resource.ics, uid, current, scope)
                        ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The recurring task could not be patched safely.")
                    PreparedCalendarWrite(href, body, calino.malinov.ski.data.caldav.DavPrecondition.Match(expected), expected)
                },
                record = { Unit },
                queuedRecord = { Unit },
            )) {
                is WriteResult.Applied -> result.also { overlay.deleteTask(current.id); publish() }
                is WriteResult.Queued -> result.also { overlay.deleteTask(current.id); publish() }
                is WriteResult.Rejected -> result
            }
        }
        return when (val result = deleteOnServer(source, current.id, current.uid ?: current.id, current.href, current.etag, "VTODO")) {
            is WriteResult.Applied -> result.also {
                overlay.deleteTask(current.id)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.deleteTask(current.id)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    override suspend fun addJournal(input: NewJournal): WriteResult<JournalEntry> {
        val source = sourceForCreate("VJOURNAL", preferredId = null)
        writableRejection(source, "VJOURNAL")?.let { return it }
        source ?: return WriteResult.Rejected("No calendar is connected.")

        val local = overlay.newJournal(input).let { it.copy(uid = it.id) }
        return when (val result = putJournalOnServer(source, local, PendingChangeType.CREATE)) {
            is WriteResult.Applied -> result.also {
                overlay.putJournal(it.record)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.putJournal(it.record)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    override suspend fun updateJournal(id: String, input: NewJournal): WriteResult<JournalEntry> {
        val current = journals().firstOrNull { it.id == id }
            ?: return WriteResult.Rejected("That journal entry is no longer available.")
        val source = sourceForRecord(null, current.href)
        writableRejection(source, "VJOURNAL")?.let { return it }
        source ?: return WriteResult.Rejected("That journal entry's calendar is no longer connected.")

        pendingCreateFor(current.id, "VJOURNAL", source.calendar.url)?.let { pending ->
            val candidate = overlay.newJournal(input).copy(
                id = current.id,
                uid = current.uid ?: current.id,
                href = null,
                etag = null,
            )
            return when (val result = coalesceQueuedCalendarCreate(
                source = source,
                eventId = current.id,
                pending = pending,
                prepare = { writer.prepareJournal(source.calendar, candidate.copy(href = null, etag = null)) },
                record = { prepared -> candidate.copy(href = prepared.href, etag = null) },
            )) {
                is WriteResult.Queued -> result.also {
                    overlay.putJournal(it.record)
                    publish()
                }
                is WriteResult.Applied -> result
                is WriteResult.Rejected -> result
            }
        }

        val candidate = overlay.newJournal(input).copy(
            id = current.id,
            uid = current.uid ?: current.id,
            href = current.href,
            etag = current.etag,
        )
        return when (val result = putJournalOnServer(source, candidate, PendingChangeType.UPDATE)) {
            is WriteResult.Applied -> result.also {
                overlay.putJournal(it.record)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.putJournal(it.record)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    override suspend fun deleteJournal(id: String): WriteResult<Unit> {
        val current = journals().firstOrNull { it.id == id }
            ?: return WriteResult.Applied(Unit)
        val source = sourceForRecord(null, current.href)
        writableRejection(source, "VJOURNAL")?.let { return it }
        source ?: return WriteResult.Rejected("That journal entry's calendar is no longer connected.")
        return when (val result = deleteOnServer(source, current.id, current.uid ?: current.id, current.href, current.etag, "VJOURNAL")) {
            is WriteResult.Applied -> result.also {
                overlay.deleteJournal(current.id)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.deleteJournal(current.id)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    override suspend fun addContact(input: NewContact): WriteResult<Contact> {
        val source = sourceForContactCreate(input.addressBookId)
            ?: return WriteResult.Rejected("No writable address book is connected.")
        val local = overlay.addContact(input.copy(addressBookId = source.addressBook.url))
        val candidate = local.copy(
            uid = local.id,
            addressBookId = source.addressBook.url,
            accountId = source.accountId,
        )
        return when (val result = putContactOnServer(source, candidate, PendingChangeType.CREATE)) {
            is WriteResult.Applied -> result.also {
                overlay.putContact(it.record)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.putContact(it.record)
                publish()
            }
            is WriteResult.Rejected -> result.also {
                // addContact inserts into the overlay before the network call
                // so an offline result can be rendered. If the write is
                // permanently rejected and no queue item exists, remove that
                // optimistic-only record again.
                overlay.dropContact(local.id)
                publish()
            }
        }
    }

    override suspend fun updateContact(id: String, input: NewContact): WriteResult<Contact> {
        val current = contacts().firstOrNull { it.id == id }
            ?: return WriteResult.Rejected("That contact is no longer available.")
        val source = sourceForContact(current)
            ?: return WriteResult.Rejected("That contact's address book is no longer connected.")
        if (source.addressBook.readOnly) return WriteResult.Rejected("${source.addressBook.displayName} is read-only.")

        pendingCreateFor(current.id, "VCARD", source.addressBook.url)?.let { pending ->
            val candidate = overlay.updateContact(
                id = id,
                input = input.copy(addressBookId = source.addressBook.url),
                existing = contacts(),
            ).copy(
                uid = current.uid ?: current.id,
                href = null,
                etag = null,
                addressBookId = source.addressBook.url,
                accountId = source.accountId,
            )
            return when (val result = coalesceQueuedCardCreate(
                source = source,
                eventId = current.id,
                pending = pending,
                prepare = { cardWriter.prepareContact(source.addressBook, candidate.copy(href = null, etag = null)) },
                record = { prepared -> candidate.copy(href = prepared.href, etag = null, rawVCard = prepared.body) },
            )) {
                is WriteResult.Queued -> result.also {
                    overlay.putContact(it.record)
                    publish()
                }
                is WriteResult.Applied -> result
                is WriteResult.Rejected -> result
            }
        }
        val candidate = overlay.updateContact(
            id = id,
            input = input.copy(addressBookId = source.addressBook.url),
            existing = contacts(),
        ).copy(
            uid = current.uid ?: current.id,
            href = current.href,
            etag = current.etag,
            addressBookId = source.addressBook.url,
            accountId = source.accountId,
        )
        overlay.putContact(candidate)
        return when (val result = putContactOnServer(source, candidate, PendingChangeType.UPDATE)) {
            is WriteResult.Applied -> result.also {
                overlay.putContact(it.record)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.putContact(it.record)
                publish()
            }
            is WriteResult.Rejected -> {
                // The editor's optimistic candidate must not survive a
                // permanent rejection. The server copy remains in fetched.
                // Restore the previous visible value as well when an older
                // queued operation still owns this record.
                overlay.putContact(current)
                publish()
                result
            }
        }
    }

    override suspend fun deleteContact(id: String): WriteResult<Unit> {
        val current = contacts().firstOrNull { it.id == id }
            ?: return WriteResult.Applied(Unit)
        val source = sourceForContact(current)
            ?: return WriteResult.Rejected("That contact's address book is no longer connected.")
        if (source.addressBook.readOnly) return WriteResult.Rejected("${source.addressBook.displayName} is read-only.")
        val result = deleteContactOnServer(source, current)
        return when (result) {
            is WriteResult.Applied -> result.also {
                overlay.deleteContact(id)
                overlay.removeContactEvents(id)
                publish()
            }
            is WriteResult.Queued -> result.also {
                overlay.deleteContact(id)
                overlay.removeContactEvents(id)
                publish()
            }
            is WriteResult.Rejected -> result
        }
    }

    override fun inlineAttachment(event: CalEvent, attachment: EventAttachment): ByteArray? {
        val source = sourceForRecord(event.calendarId, event.href) ?: return null
        val href = event.href ?: return null
        val ics = cache.loadResource(source.calendar.url, href)?.ics ?: return null
        return readInlineAttachment(ics, event.uid ?: return null, attachment)
    }

    override fun addLocalEvent(input: NewEvent): CalEvent =
        overlay.addEvent(input).also { publish() }

    override suspend fun setTaskDone(id: String, done: Boolean): WriteResult<UndoableChange> {
        val before = tasks().firstOrNull { it.id == id }
            ?: return WriteResult.Rejected("That task is no longer available.")
        return taskChange(
            before,
            before.copy(
                done = done,
                percentComplete = if (done) 100 else 0,
                status = if (done) "COMPLETED" else "NEEDS-ACTION",
                completedAt = if (done) Instant.now() else null,
            ),
            if (done) "Completed" else "Reopened",
        )
    }

    override suspend fun rescheduleTask(id: String, due: LocalDate?): WriteResult<UndoableChange> {
        val before = tasks().firstOrNull { it.id == id }
            ?: return WriteResult.Rejected("That task is no longer available.")
        return taskChange(before, before.copy(due = due), "Rescheduled")
    }

    override suspend fun undo(change: UndoableChange): WriteResult<Unit> {
        if (change.kind != ChangeKind.Task) return WriteResult.Rejected("That change cannot be undone.")
        val before = (change.before as? ChangeValue.Task)?.value
            ?: return WriteResult.Rejected("That change cannot be undone.")
        val expected = (change.after as? ChangeValue.Task)?.value
            ?: return WriteResult.Rejected("That change cannot be undone.")
        val current = tasks().firstOrNull { it.id == change.id }
            ?: return WriteResult.Rejected("That task is no longer available.")
        if (current != expected) return WriteResult.Rejected("That task changed, so the change was not undone.")
        return when (val result = putTaskOnServer(sourceForRecord(null, current.href), before.copy(
            uid = current.uid ?: current.id,
            href = current.href,
            etag = current.etag,
        ), PendingChangeType.UPDATE)) {
            is WriteResult.Applied -> {
                overlay.putTask(result.record)
                publish()
                WriteResult.Applied(Unit)
            }
            is WriteResult.Queued -> {
                overlay.putTask(result.record)
                publish()
                WriteResult.Queued(Unit)
            }
            is WriteResult.Rejected -> result
        }
    }

    private suspend fun taskChange(
        before: CalTask,
        after: CalTask,
        verb: String,
    ): WriteResult<UndoableChange> = when (val result = putTaskOnServer(sourceForRecord(null, before.href), after.copy(
        uid = before.uid ?: before.id,
        href = before.href,
        etag = before.etag,
    ), PendingChangeType.UPDATE)) {
        is WriteResult.Applied -> {
            val saved = result.record
            overlay.putTask(saved)
            publish()
            WriteResult.Applied(
                UndoableChange(
                    description = "$verb ${before.title}",
                    kind = ChangeKind.Task,
                    id = before.id,
                    before = ChangeValue.Task(before),
                    after = ChangeValue.Task(saved),
                ),
            )
        }
        is WriteResult.Queued -> WriteResult.Queued(
            UndoableChange(
                description = "$verb ${before.title}",
                kind = ChangeKind.Task,
                id = before.id,
                before = ChangeValue.Task(before),
                after = ChangeValue.Task(result.record),
            ),
        ).also {
            overlay.putTask(result.record)
            publish()
        }
        is WriteResult.Rejected -> result
    }

    private suspend fun putEventOnServer(
        source: CalDavSource,
        event: CalEvent,
        changeType: PendingChangeType,
    ): WriteResult<CalEvent> {
        val uid = event.uid ?: event.id
        return putCalendarWithQueue(
            source = source,
            changeType = changeType,
            eventId = event.id,
            component = "VEVENT",
            uid = uid,
            etag = event.etag,
            prepare = { etag ->
                writer.prepareEvent(
                    source.calendar,
                    event.copy(uid = uid, href = event.href, etag = etag),
                )
            },
            record = { written -> event.copy(uid = uid, href = written.href, etag = written.etag) },
            queuedRecord = { prepared ->
                event.copy(
                    uid = uid,
                    href = prepared?.href ?: event.href,
                    etag = prepared?.expectedEtag ?: event.etag,
                )
            },
        )
    }

    private suspend fun deleteRecurringEventOnServer(
        source: CalDavSource,
        event: CalEvent,
        scope: RecurrenceEditScope,
    ): WriteResult<Unit> {
        if (scope == RecurrenceEditScope.All) {
            return deleteOnServer(
                source = source,
                eventId = event.id,
                uid = event.uid ?: event.id,
                href = event.href,
                etag = event.etag,
                component = "VEVENT",
            )
        }
        val uid = event.uid ?: event.id
        val href = resourceHref(source.calendar.url, event.href, uid)
        val cached = cache.loadResource(source.calendar.url, href)
            ?: return WriteResult.Rejected(
                "The recurring event is not available in the raw cache. Refresh and try again.",
            )
        val calendar = runCatching { ICalTimezones.parse(cached.ics).singleOrNull() }.getOrNull()
            ?: return WriteResult.Rejected("The recurring event could not be read safely.")
        // A few servers store a detached override as its own resource. There
        // is no master rule from which FUTURE can be reconstructed, but THIS
        // and ALL can still operate on the resource that was actually read.
        val group = runCatching { RecurrenceEdit.Group.from(calendar, uid) }.getOrNull()
        if (group == null) {
            if (scope == RecurrenceEditScope.Future) {
                return WriteResult.Rejected(
                    "This detached occurrence has no series rule, so future occurrences cannot be changed.",
                )
            }
            if (calendar.events.none { raw -> raw.uid?.value == uid && rawMatchesEvent(raw, event) }) {
                return WriteResult.Rejected("That detached occurrence is no longer on the server.")
            }
            return deleteOnServer(
                source = source,
                eventId = event.id,
                uid = uid,
                href = href,
                etag = event.etag ?: cached.etag,
                component = "VEVENT",
            )
        }
        return putCalendarWithQueue(
            source = source,
            changeType = PendingChangeType.UPDATE,
            eventId = event.id,
            component = "VEVENT",
            uid = uid,
            etag = event.etag,
            prepare = { requestedEtag ->
                // The first attempt may have refreshed the cache after a 412.
                // Read the resource again here so the retry uses the same raw
                // calendar text as its validator and never patches stale data.
                val currentResource = cache.loadResource(source.calendar.url, href)
                    ?: throw CalDavException(
                        CalDavErrorCode.PreconditionFailed,
                        "The recurring event is not available in the raw cache. Refresh and try again.",
                        status = 412,
                    )
                val expectedEtag = normalizeEtag(requestedEtag) ?: normalizeEtag(currentResource.etag)
                    ?: throw CalDavException(
                        CalDavErrorCode.PreconditionFailed,
                        "The recurring event has no current server version. Refresh and try again.",
                        status = 412,
                    )
                val currentCalendar = runCatching { ICalTimezones.parse(currentResource.ics).singleOrNull() }.getOrNull()
                    ?: throw CalDavException(
                        CalDavErrorCode.NotCalDav,
                        "The recurring event could not be read safely.",
                    )
                val currentGroup = runCatching { RecurrenceEdit.Group.from(currentCalendar, uid) }.getOrNull()
                    ?: throw CalDavException(
                        CalDavErrorCode.NotCalDav,
                        "The recurring event no longer contains an editable series.",
                    )
                val target = when {
                    event.recurrenceDate != null -> RecurrenceEdit.Target.allDay(event.recurrenceDate)
                    event.recurrenceId != null -> RecurrenceEdit.Target.timed(event.recurrenceId)
                    else -> RecurrenceEdit.Target.from(currentGroup.master)
                } ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The recurring event has no editable target.")
                val result = RecurrenceEdit.delete(currentGroup, target, scope.toDavScope())
                val body = recurrencePatcher.patchRecurrence(currentResource.ics, uid, result)
                    ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The recurring event could not be patched safely.")
                PreparedCalendarWrite(
                    href = href,
                    body = body,
                    precondition = calino.malinov.ski.data.caldav.DavPrecondition.Match(expectedEtag),
                    expectedEtag = expectedEtag,
                )
            },
            record = { Unit },
            queuedRecord = { Unit },
        )
    }

    private suspend fun putRecurringEventOnServer(
        source: CalDavSource,
        event: CalEvent,
        scope: RecurrenceEditScope,
        recurrenceChanged: Boolean,
    ): WriteResult<CalEvent> {
        val uid = event.uid ?: event.id
        return putCalendarWithQueue(
            source = source,
            changeType = PendingChangeType.UPDATE,
            eventId = event.id,
            component = "VEVENT",
            uid = uid,
            etag = event.etag,
            prepare = { requestedEtag ->
                val href = resourceHref(source.calendar.url, event.href, uid)
                // Re-read on every preparation. A stale-ETag retry refreshes
                // the raw resource in the cache; using a value captured by the
                // first attempt would combine the new ETag with old iCalendar.
                val currentResource = cache.loadResource(source.calendar.url, href)
                    ?: throw CalDavException(
                        CalDavErrorCode.PreconditionFailed,
                        "The recurring event is not available in the raw cache. Refresh and try again.",
                        status = 412,
                    )
                val expectedEtag = normalizeEtag(requestedEtag) ?: normalizeEtag(currentResource.etag)
                    ?: throw CalDavException(
                        CalDavErrorCode.PreconditionFailed,
                        "The recurring event has no current server version. Refresh and try again.",
                        status = 412,
                    )
                val calendar = runCatching { ICalTimezones.parse(currentResource.ics).singleOrNull() }.getOrNull()
                    ?: throw CalDavException(
                        CalDavErrorCode.NotCalDav,
                        "The recurring event could not be read safely.",
                    )
                val currentGroup = runCatching { RecurrenceEdit.Group.from(calendar, uid) }.getOrNull()
                if (currentGroup == null) {
                    if (scope != RecurrenceEditScope.This) {
                        throw CalDavException(
                            CalDavErrorCode.NotCalDav,
                            "This detached occurrence has no series rule; only this occurrence can be edited.",
                        )
                    }
                    val body = recurrencePatcher.patchEvents(
                        currentResource.ics,
                        listOf(
                            event.copy(
                                uid = uid,
                                href = href,
                                etag = expectedEtag,
                            ),
                        ),
                        Instant.now(),
                    ) ?: throw CalDavException(
                        CalDavErrorCode.NotCalDav,
                        "The detached occurrence could not be patched safely.",
                    )
                    return@putCalendarWithQueue PreparedCalendarWrite(
                        href = href,
                        body = body,
                        precondition = calino.malinov.ski.data.caldav.DavPrecondition.Match(expectedEtag),
                        expectedEtag = expectedEtag,
                    )
                }
                val target = when {
                    event.recurrenceDate != null -> RecurrenceEdit.Target.allDay(event.recurrenceDate)
                    event.recurrenceId != null -> RecurrenceEdit.Target.timed(event.recurrenceId)
                    else -> RecurrenceEdit.Target.from(currentGroup.master)
                } ?: throw CalDavException(
                    CalDavErrorCode.NotCalDav,
                    "The recurring event has no editable recurrence target.",
                )
                val original = currentGroup.events.firstOrNull { raw ->
                    when {
                        event.recurrenceDate != null -> raw.recurrenceId?.value?.let { value ->
                            !value.hasTime() && value.rawComponents?.let {
                                runCatching { LocalDate.of(it.year, it.month, it.date) }.getOrNull()
                            } == event.recurrenceDate
                        } == true
                        event.recurrenceId != null -> raw.recurrenceId?.value?.toInstant() == event.recurrenceId
                        else -> raw.recurrenceId == null
                    }
                } ?: currentGroup.master
                val replacement = recurrenceWriter.writeEvent(
                    event.copy(
                        uid = uid,
                        href = href,
                        etag = expectedEtag,
                        recurrenceId = null,
                        recurrenceDate = null,
                    ),
                    original = original,
                    now = Instant.now(),
                    preserveRecurrenceIfMissing = !recurrenceChanged,
                )
                val result = RecurrenceEdit.edit(
                    group = currentGroup,
                    target = target,
                    scope = scope.toDavScope(),
                    replacement = replacement,
                )
                val body = recurrencePatcher.patchRecurrence(currentResource.ics, uid, result)
                    ?: throw CalDavException(
                        CalDavErrorCode.NotCalDav,
                        "The recurring event could not be patched safely.",
                    )
                PreparedCalendarWrite(
                    href = href,
                    body = body,
                    precondition = calino.malinov.ski.data.caldav.DavPrecondition.Match(expectedEtag),
                    expectedEtag = expectedEtag,
                )
            },
            record = { written -> event.copy(uid = uid, href = written.href, etag = written.etag) },
            queuedRecord = { prepared -> event.copy(uid = uid, href = prepared?.href ?: event.href, etag = prepared?.expectedEtag ?: event.etag) },
        )
    }

    private suspend fun putTaskOnServer(
        source: CalDavSource?,
        task: CalTask,
        changeType: PendingChangeType,
    ): WriteResult<CalTask> {
        writableRejection(source, "VTODO")?.let { return it }
        source ?: return WriteResult.Rejected("That task's calendar is no longer connected.")
        val uid = task.uid ?: task.id
        return putCalendarWithQueue(
            source = source,
            changeType = changeType,
            eventId = task.id,
            component = "VTODO",
            uid = uid,
            etag = task.etag,
            prepare = { etag ->
                writer.prepareTask(
                    source.calendar,
                    task.copy(uid = uid, href = task.href, etag = etag),
                )
            },
            record = { written -> task.copy(uid = uid, href = written.href, etag = written.etag) },
            queuedRecord = { prepared ->
                task.copy(
                    uid = uid,
                    href = prepared?.href ?: task.href,
                    etag = prepared?.expectedEtag ?: task.etag,
                )
            },
        )
    }

    private suspend fun putJournalOnServer(
        source: CalDavSource,
        entry: JournalEntry,
        changeType: PendingChangeType,
    ): WriteResult<JournalEntry> {
        val uid = entry.uid ?: entry.id
        return putCalendarWithQueue(
            source = source,
            changeType = changeType,
            eventId = entry.id,
            component = "VJOURNAL",
            uid = uid,
            etag = entry.etag,
            prepare = { etag ->
                writer.prepareJournal(
                    source.calendar,
                    entry.copy(uid = uid, href = entry.href, etag = etag),
                )
            },
            record = { written -> entry.copy(uid = uid, href = written.href, etag = written.etag) },
            queuedRecord = { prepared ->
                entry.copy(
                    uid = uid,
                    href = prepared?.href ?: entry.href,
                    etag = prepared?.expectedEtag ?: entry.etag,
                )
            },
        )
    }

    private suspend fun <T> putCalendarWithQueue(
        source: CalDavSource,
        changeType: PendingChangeType,
        eventId: String,
        component: String,
        uid: String,
        etag: String?,
        prepare: (String?) -> PreparedCalendarWrite,
        record: (WrittenCalendarResource) -> T,
        queuedRecord: (PreparedCalendarWrite?) -> T,
    ): WriteResult<T> {
        var requestedEtag = etag
        var prepared: PreparedCalendarWrite? = null
        var preparedBaseData: String? = null
        var outcome = attemptWrite(source.calendar.url) {
            prepare(requestedEtag).also {
                prepared = it
                preparedBaseData = cachedCalendarBase(source, it)
            }
                .let { writer.putPrepared(source.calendar, source.credentials, it) }
        }
        var error = outcome.exceptionOrNull()

        // A stale conditional update gets one safe rebase. The GET refreshes
        // both the validator and the raw component so an edit cannot be
        // replayed over another client's newly-added properties.
        if (error != null && classifyWriteError(error, changeType).disposition == WriteDisposition.StaleEtag) {
            val href = prepared?.href
            if (href != null) {
                val refreshed = attemptWrite(source.calendar.url) {
                    writer.refreshResource(source.calendar, source.credentials, href, uid)
                }
                val refreshedError = refreshed.exceptionOrNull()
                val resource = refreshed.getOrNull()
                if (refreshedError == null && resource?.etag != null) {
                    requestedEtag = resource.etag
                    outcome = attemptWrite(source.calendar.url) {
                        prepare(requestedEtag).also {
                            prepared = it
                            preparedBaseData = cachedCalendarBase(source, it)
                        }
                            .let { writer.putPrepared(source.calendar, source.credentials, it) }
                    }
                    error = outcome.exceptionOrNull()
                } else {
                    error = refreshedError ?: CalDavException(
                        CalDavErrorCode.PreconditionFailed,
                        "The server did not return a current version for that item.",
                        status = 412,
                    )
                }
            }
        }

        if (error != null) {
            return queueCalendarPutFailure(
                source = source,
                changeType = changeType,
                eventId = eventId,
                component = component,
                uid = uid,
                prepared = prepared,
                baseData = preparedBaseData,
                error = error,
                record = queuedRecord(prepared),
            )
        }

        val written = outcome.getOrThrow()
        clearWriteStatus(eventId)
        return WriteResult.Applied(record(written))
    }

    private suspend fun <T> queueCalendarPutFailure(
        source: CalDavSource,
        changeType: PendingChangeType,
        eventId: String,
        component: String,
        uid: String,
        prepared: PreparedCalendarWrite?,
        baseData: String?,
        error: Throwable,
        record: T,
    ): WriteResult<T> {
        var classification = classifyWriteError(error, changeType)
        // The immediate stale retry has already been attempted. Keeping a
        // stale disposition here would make a caller think it can retry the
        // same request forever; the durable queue will refresh on its next
        // attempt and count the failed replay.
        if (classification.disposition == WriteDisposition.StaleEtag) {
            classification = classification.copy(disposition = WriteDisposition.RetryCounted)
        }
        val disposition = classification.disposition
        if (disposition is WriteDisposition.Drop || prepared == null || pendingStore == null) {
            val reason = if (prepared == null && disposition !is WriteDisposition.Drop) {
                "The calendar item could not be prepared safely."
            } else if (pendingStore == null && disposition !is WriteDisposition.Drop) {
                "${classification.message}"
            } else {
                classification.message
            }
            setWriteStatus(eventId, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }

        if (disposition != WriteDisposition.Retry && disposition != WriteDisposition.RetryCounted) {
            setWriteStatus(eventId, RecordWriteState.Failed, classification.message)
            return WriteResult.Rejected(classification.message)
        }

        if (changeType != PendingChangeType.CREATE && baseData.isNullOrBlank()) {
            val reason = "The calendar item could not be queued safely because its original server snapshot is unavailable. Refresh and try again."
            setWriteStatus(eventId, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }

        val request = PendingChangeRequest(
            type = changeType,
            eventId = eventId,
            accountId = source.accountId,
            calendarId = source.calendar.url,
            component = component,
            calendarUrl = source.calendar.url,
            uid = uid,
            href = prepared.href,
            etag = prepared.expectedEtag,
            data = prepared.body,
            baseData = baseData,
        )
        val store = pendingStore ?: error("pending store disappeared")
        val enqueue = runCatching {
            withContext(ioDispatcher) { store.enqueue(request) }
        }.getOrElse { enqueueError ->
            val reason = "${classification.message} The pending write could not be stored: ${enqueueError.message.orEmpty()}"
            setWriteStatus(eventId, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        return when (enqueue) {
            is PendingChangeEnqueueResult.Enqueued -> {
                setWriteStatus(eventId, RecordWriteState.Pending, classification.message)
                onPendingChangeEnqueued?.invoke()
                WriteResult.Queued(record)
            }
            is PendingChangeEnqueueResult.Rejected -> {
                setWriteStatus(eventId, RecordWriteState.Failed, enqueue.reason)
                WriteResult.Rejected(enqueue.reason)
            }
        }
    }

    private suspend fun queueMoveFailure(
        source: CalDavSource,
        destination: CalDavSource,
        plan: CalDavMovePlan,
        candidate: CalEvent,
        error: Throwable,
        sourceData: String? = null,
    ): WriteResult<CalEvent> {
        val moveFailure = CalDavMoveFailureClassifier.classify(error)
        val classification = classifyWriteError(error, PendingChangeType.MOVE)
        val store = pendingStore
        if (moveFailure.kind != CalDavMoveFailureKind.Other ||
            classification.disposition is WriteDisposition.Drop ||
            store == null ||
            (classification.disposition != WriteDisposition.Retry &&
                classification.disposition != WriteDisposition.RetryCounted)
        ) {
            val reason = when (moveFailure.kind) {
                CalDavMoveFailureKind.UidConflict ->
                    "The destination already contains this event UID; the source was kept."
                CalDavMoveFailureKind.Forbidden ->
                    "The destination refused the move; the source was kept."
                CalDavMoveFailureKind.Other -> classification.message
            }
            setWriteStatus(candidate.id, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }

        val request = PendingChangeRequest(
            type = PendingChangeType.MOVE,
            eventId = candidate.id,
            accountId = destination.accountId,
            calendarId = destination.calendar.url,
            component = "VEVENT",
            calendarUrl = destination.calendar.url,
            uid = plan.uid,
            href = plan.destination.href,
            data = plan.destination.payload,
            sourceAccountId = source.accountId,
            sourceCalendarId = source.calendar.url,
            sourceCalendarUrl = source.calendar.url,
            sourceHref = plan.sourceDeletion?.href,
            sourceEtag = plan.sourceDeletion?.etag,
            sourceData = sourceData,
        )
        val enqueue = runCatching {
            withContext(ioDispatcher) { store.enqueue(request) }
        }.getOrElse { enqueueError ->
            val reason = "${classification.message} The pending move could not be stored: ${enqueueError.message.orEmpty()}"
            setWriteStatus(candidate.id, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        return when (enqueue) {
            is PendingChangeEnqueueResult.Enqueued -> {
                setWriteStatus(candidate.id, RecordWriteState.Pending, classification.message)
                onPendingChangeEnqueued?.invoke()
                WriteResult.Queued(candidate)
            }
            is PendingChangeEnqueueResult.Rejected -> {
                setWriteStatus(candidate.id, RecordWriteState.Failed, enqueue.reason)
                WriteResult.Rejected(enqueue.reason)
            }
        }
    }

    private suspend fun queueMoveSourceCleanup(
        source: CalDavSource,
        event: CalEvent,
        href: String,
        etag: String,
        data: String?,
        error: Throwable,
        beforeId: String? = null,
    ): WriteResult<Unit> {
        var classification = classifyWriteError(error, PendingChangeType.DELETE_HREF)
        // The cleanup must retain the original If-Match. Refreshing a changed
        // source after the destination has been written could delete a new
        // version created by another client.
        if (classification.disposition == WriteDisposition.StaleEtag) {
            classification = classification.copy(disposition = WriteDisposition.RetryCounted)
        }
        val store = pendingStore
        if (classification.disposition is WriteDisposition.Drop ||
            store == null ||
            (classification.disposition != WriteDisposition.Retry &&
                classification.disposition != WriteDisposition.RetryCounted)
        ) {
            val reason = "The destination copy was written, but the old source was kept. ${classification.message}"
            setWriteStatus(event.id, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        val request = PendingChangeRequest(
            type = PendingChangeType.DELETE_HREF,
            eventId = event.id,
            accountId = source.accountId,
            calendarId = source.calendar.url,
            component = "VEVENT",
            calendarUrl = source.calendar.url,
            uid = event.uid ?: event.id,
            href = href,
            etag = etag,
            data = data,
        )
        val enqueue = runCatching {
            withContext(ioDispatcher) {
                if (beforeId == null) store.enqueue(request)
                else store.enqueueBefore(beforeId, request)
            }
        }.getOrElse { enqueueError ->
            val reason = "The destination copy was written, but the old source was kept. " +
                "The cleanup could not be stored: ${enqueueError.message.orEmpty()}"
            setWriteStatus(event.id, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        return when (enqueue) {
            is PendingChangeEnqueueResult.Enqueued -> {
                setWriteStatus(event.id, RecordWriteState.Pending, classification.message)
                onPendingChangeEnqueued?.invoke()
                WriteResult.Queued(Unit)
            }
            is PendingChangeEnqueueResult.Rejected -> {
                setWriteStatus(event.id, RecordWriteState.Failed, enqueue.reason)
                WriteResult.Rejected(enqueue.reason)
            }
        }
    }

    private suspend fun deleteOnServer(
        source: CalDavSource,
        eventId: String,
        uid: String,
        href: String?,
        etag: String?,
        component: String,
    ): WriteResult<Unit> {
        val resourceHref = resourceHref(source.calendar.url, href, uid)
        val cached = withContext(ioDispatcher) { cache.loadResource(source.calendar.url, resourceHref) }
        var expectedEtag = normalizeEtag(etag) ?: cached?.etag
        var outcome = attemptWrite(source.calendar.url) {
            writer.delete(
                calendar = source.calendar,
                credentials = source.credentials,
                href = resourceHref,
                uid = uid,
                etag = expectedEtag,
                component = component,
            )
        }
        var error = outcome.exceptionOrNull()

        if (error != null && classifyWriteError(error, PendingChangeType.DELETE).disposition == WriteDisposition.StaleEtag) {
            val refreshed = attemptWrite(source.calendar.url) {
                writer.refreshResource(source.calendar, source.credentials, resourceHref, uid)
            }
            val refreshedError = refreshed.exceptionOrNull()
            val resource = refreshed.getOrNull()
            if (refreshedError == null && resource?.etag != null) {
                expectedEtag = resource.etag
                outcome = attemptWrite(source.calendar.url) {
                    writer.delete(
                        calendar = source.calendar,
                        credentials = source.credentials,
                        href = resourceHref,
                        uid = uid,
                        etag = expectedEtag,
                        component = component,
                    )
                }
                error = outcome.exceptionOrNull()
            } else {
                error = refreshedError ?: CalDavException(
                    CalDavErrorCode.PreconditionFailed,
                    "The server did not return a current version for that item.",
                    status = 412,
                )
            }
        }

        if (error != null) {
            val dav = error as? CalDavException
            if (dav?.status == 404 || dav?.status == 410 || dav?.code == CalDavErrorCode.NotFound || dav?.code == CalDavErrorCode.Gone) {
                clearWriteStatus(eventId)
                return WriteResult.Applied(Unit)
            }
            return queueDeleteFailure(
                source = source,
                eventId = eventId,
                uid = uid,
                href = resourceHref,
                etag = expectedEtag,
                component = component,
                error = error,
            )
        }

        clearWriteStatus(eventId)
        return WriteResult.Applied(Unit)
    }

    private suspend fun queueDeleteFailure(
        source: CalDavSource,
        eventId: String,
        uid: String,
        href: String,
        etag: String?,
        component: String,
        error: Throwable,
    ): WriteResult<Unit> {
        var classification = classifyWriteError(error, PendingChangeType.DELETE)
        if (classification.disposition == WriteDisposition.StaleEtag) {
            classification = classification.copy(disposition = WriteDisposition.RetryCounted)
        }
        val disposition = classification.disposition
        if (disposition is WriteDisposition.Drop || pendingStore == null ||
            (disposition != WriteDisposition.Retry && disposition != WriteDisposition.RetryCounted)
        ) {
            setWriteStatus(eventId, RecordWriteState.Failed, classification.message)
            return WriteResult.Rejected(classification.message)
        }

        val data = withContext(ioDispatcher) {
            cache.loadResource(source.calendar.url, href)?.ics
        }
        val request = PendingChangeRequest(
            type = PendingChangeType.DELETE,
            eventId = eventId,
            accountId = source.accountId,
            calendarId = source.calendar.url,
            component = component,
            calendarUrl = source.calendar.url,
            uid = uid,
            href = href,
            etag = etag,
            data = data,
        )
        val store = pendingStore ?: error("pending store disappeared")
        val enqueue = runCatching {
            withContext(ioDispatcher) { store.enqueue(request) }
        }.getOrElse { enqueueError ->
            val reason = "${classification.message} The pending delete could not be stored: ${enqueueError.message.orEmpty()}"
            setWriteStatus(eventId, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        return when (enqueue) {
            is PendingChangeEnqueueResult.Enqueued -> {
                setWriteStatus(eventId, RecordWriteState.Pending, classification.message)
                onPendingChangeEnqueued?.invoke()
                WriteResult.Queued(Unit)
            }
            is PendingChangeEnqueueResult.Rejected -> {
                setWriteStatus(eventId, RecordWriteState.Failed, enqueue.reason)
                WriteResult.Rejected(enqueue.reason)
            }
        }
    }

    private suspend fun putContactOnServer(
        source: CardDavSource,
        contact: Contact,
        changeType: PendingChangeType,
    ): WriteResult<Contact> {
        val uid = contact.uid ?: contact.id
        var requestedEtag = contact.etag
        var prepared: PreparedCardWrite? = null
        var preparedBaseData: String? = null
        var outcome = attemptWrite(source.addressBook.url) {
            cardWriter.prepareContact(
                source.addressBook,
                contact.copy(uid = uid, etag = requestedEtag),
            ).also {
                prepared = it
                preparedBaseData = cachedCardBase(source, it)
            }
                .let { cardWriter.putPrepared(source.addressBook, source.credentials, it) }
        }
        var error = outcome.exceptionOrNull()
        if (error != null && classifyWriteError(error, changeType).disposition == WriteDisposition.StaleEtag) {
            val href = prepared?.href
            if (href != null) {
                val refreshed = attemptWrite(source.addressBook.url) {
                    cardWriter.refreshResource(source.addressBook, source.credentials, href)
                }
                val resource = refreshed.getOrNull()
                val refreshedError = refreshed.exceptionOrNull()
                if (refreshedError == null && resource?.etag != null) {
                    requestedEtag = resource.etag
                    outcome = attemptWrite(source.addressBook.url) {
                        cardWriter.prepareContact(
                            source.addressBook,
                            contact.copy(uid = uid, href = href, etag = requestedEtag),
                        ).also {
                            prepared = it
                            preparedBaseData = cachedCardBase(source, it)
                        }
                            .let { cardWriter.putPrepared(source.addressBook, source.credentials, it) }
                    }
                    error = outcome.exceptionOrNull()
                } else {
                    error = refreshedError ?: CalDavException(
                        CalDavErrorCode.PreconditionFailed,
                        "The server did not return a current version for that contact.",
                        status = 412,
                    )
                }
            }
        }
        if (error != null) {
            return queueCardPutFailure(
                source = source,
                changeType = changeType,
                contact = contact.copy(uid = uid),
                prepared = prepared,
                baseData = preparedBaseData,
                error = error,
            )
        }
        val written = outcome.getOrThrow()
        clearWriteStatus(contact.id)
        return WriteResult.Applied(
            contact.copy(
                uid = uid,
                href = written.href,
                etag = written.etag,
                rawVCard = written.vcf,
                addressBookId = source.addressBook.url,
                accountId = source.accountId,
            ),
        )
    }

    private suspend fun queueCardPutFailure(
        source: CardDavSource,
        changeType: PendingChangeType,
        contact: Contact,
        prepared: PreparedCardWrite?,
        baseData: String?,
        error: Throwable,
    ): WriteResult<Contact> {
        var classification = classifyWriteError(error, changeType)
        if (classification.disposition == WriteDisposition.StaleEtag) {
            classification = classification.copy(disposition = WriteDisposition.RetryCounted)
        }
        val disposition = classification.disposition
        val store = pendingStore
        if (disposition is WriteDisposition.Drop || prepared == null || store == null ||
            (disposition != WriteDisposition.Retry && disposition != WriteDisposition.RetryCounted)
        ) {
            setWriteStatus(contact.id, RecordWriteState.Failed, classification.message)
            return WriteResult.Rejected(classification.message)
        }
        if (changeType != PendingChangeType.CREATE && baseData.isNullOrBlank()) {
            val reason = "The contact could not be queued safely because its original server snapshot is unavailable. Refresh and try again."
            setWriteStatus(contact.id, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        val request = PendingChangeRequest(
            type = changeType,
            eventId = contact.id,
            accountId = source.accountId,
            calendarId = source.addressBook.url,
            component = "VCARD",
            calendarUrl = source.addressBook.url,
            uid = contact.uid ?: contact.id,
            href = prepared.href,
            etag = prepared.expectedEtag,
            data = prepared.body,
            baseData = baseData,
        )
        val enqueue = runCatching {
            withContext(ioDispatcher) { store.enqueue(request) }
        }.getOrElse { enqueueError ->
            val reason = "${classification.message} The pending contact write could not be stored: ${enqueueError.message.orEmpty()}"
            setWriteStatus(contact.id, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        return when (enqueue) {
            is PendingChangeEnqueueResult.Enqueued -> {
                setWriteStatus(contact.id, RecordWriteState.Pending, classification.message)
                onPendingChangeEnqueued?.invoke()
                WriteResult.Queued(contact.copy(href = prepared.href, etag = prepared.expectedEtag))
            }
            is PendingChangeEnqueueResult.Rejected -> {
                setWriteStatus(contact.id, RecordWriteState.Failed, enqueue.reason)
                WriteResult.Rejected(enqueue.reason)
            }
        }
    }

    private suspend fun deleteContactOnServer(
        source: CardDavSource,
        contact: Contact,
    ): WriteResult<Unit> {
        val href = contact.href
            ?: return WriteResult.Rejected("That contact has no server resource URL.")
        var expectedEtag = contact.etag
        var outcome = attemptWrite(source.addressBook.url) {
            cardWriter.delete(source.addressBook, source.credentials, href, expectedEtag)
        }
        var error = outcome.exceptionOrNull()
        if (error != null && classifyWriteError(error, PendingChangeType.DELETE).disposition == WriteDisposition.StaleEtag) {
            val refreshed = attemptWrite(source.addressBook.url) {
                cardWriter.refreshResource(source.addressBook, source.credentials, href)
            }
            val resource = refreshed.getOrNull()
            val refreshedError = refreshed.exceptionOrNull()
            if (refreshedError == null && resource?.etag != null) {
                expectedEtag = resource.etag
                outcome = attemptWrite(source.addressBook.url) {
                    cardWriter.delete(source.addressBook, source.credentials, href, expectedEtag)
                }
                error = outcome.exceptionOrNull()
            } else {
                error = refreshedError ?: CalDavException(
                    CalDavErrorCode.PreconditionFailed,
                    "The server did not return a current version for that contact.",
                    status = 412,
                )
            }
        }
        if (error != null) {
            val dav = error as? CalDavException
            if (dav?.status == 404 || dav?.status == 410 || dav?.code == CalDavErrorCode.NotFound || dav?.code == CalDavErrorCode.Gone) {
                clearWriteStatus(contact.id)
                return WriteResult.Applied(Unit)
            }
            return queueContactDeleteFailure(source, contact, href, expectedEtag, error)
        }
        clearWriteStatus(contact.id)
        return WriteResult.Applied(Unit)
    }

    private suspend fun queueContactDeleteFailure(
        source: CardDavSource,
        contact: Contact,
        href: String,
        etag: String?,
        error: Throwable,
    ): WriteResult<Unit> {
        var classification = classifyWriteError(error, PendingChangeType.DELETE)
        if (classification.disposition == WriteDisposition.StaleEtag) {
            classification = classification.copy(disposition = WriteDisposition.RetryCounted)
        }
        val disposition = classification.disposition
        val store = pendingStore
        if (disposition is WriteDisposition.Drop || store == null ||
            (disposition != WriteDisposition.Retry && disposition != WriteDisposition.RetryCounted)
        ) {
            setWriteStatus(contact.id, RecordWriteState.Failed, classification.message)
            return WriteResult.Rejected(classification.message)
        }
        val data = withContext(ioDispatcher) {
            cache.loadAddressBook(source.addressBook.url)?.resources?.firstOrNull {
                it.href == href || CardDavWriter.resolveHref(source.addressBook.url, it.href) == href
            }?.vcf
        }
        val request = PendingChangeRequest(
            type = PendingChangeType.DELETE,
            eventId = contact.id,
            accountId = source.accountId,
            calendarId = source.addressBook.url,
            component = "VCARD",
            calendarUrl = source.addressBook.url,
            uid = contact.uid ?: contact.id,
            href = href,
            etag = etag,
            data = data,
        )
        val enqueue = runCatching {
            withContext(ioDispatcher) { store.enqueue(request) }
        }.getOrElse { enqueueError ->
            val reason = "${classification.message} The pending contact delete could not be stored: ${enqueueError.message.orEmpty()}"
            setWriteStatus(contact.id, RecordWriteState.Failed, reason)
            return WriteResult.Rejected(reason)
        }
        return when (enqueue) {
            is PendingChangeEnqueueResult.Enqueued -> {
                setWriteStatus(contact.id, RecordWriteState.Pending, classification.message)
                onPendingChangeEnqueued?.invoke()
                WriteResult.Queued(Unit)
            }
            is PendingChangeEnqueueResult.Rejected -> {
                setWriteStatus(contact.id, RecordWriteState.Failed, enqueue.reason)
                WriteResult.Rejected(enqueue.reason)
            }
        }
    }

    /** Restores the visible part of queued writes after a process restart. */
    private fun restoreQueuedOverlays() {
        val store = pendingStore ?: return
        val start = today().withDayOfMonth(1).minusMonths(windowMonths)
        val end = today().withDayOfMonth(1).plusMonths(windowMonths)
        var changed = false
        store.snapshot()
            .filter { it.state != PendingChangeState.DEAD_LETTER }
            .forEach { change ->
                if (change.component.equals("VCARD", ignoreCase = true)) {
                    val book = addressBookSources.firstOrNull {
                        it.accountId == change.accountId &&
                            (it.addressBook.url == change.calendarUrl || it.addressBook.url == change.calendarId)
                    } ?: return@forEach
                    if (change.type == PendingChangeType.DELETE || change.type == PendingChangeType.DELETE_HREF) {
                        overlay.deleteContact(change.eventId)
                        changed = true
                    } else {
                        val data = change.data ?: return@forEach
                        vCardMapper.map(
                            vcf = data,
                            addressBookId = book.addressBook.url,
                            accountId = book.accountId,
                            href = change.href,
                            etag = change.etag,
                        )?.let { contact ->
                            overlay.putContact(
                                contact.copy(
                                    id = change.eventId,
                                    uid = change.uid ?: contact.uid,
                                    href = change.href ?: contact.href,
                                    etag = change.etag ?: contact.etag,
                                    addressBookId = book.addressBook.url,
                                    accountId = book.accountId,
                                ),
                            )
                            changed = true
                        }
                    }
                    return@forEach
                }
                val source = sources.firstOrNull {
                    it.accountId == change.accountId &&
                        (it.calendar.url == change.calendarUrl || it.calendar.url == change.calendarId)
                } ?: return@forEach
                val isDelete = change.type == PendingChangeType.DELETE ||
                    change.type == PendingChangeType.DELETE_HREF
                when (change.component.uppercase()) {
                    "VEVENT" -> {
                        changed = restoreQueuedEventOverlay(change, source, start, end) || changed
                    }
                    "VTODO" -> {
                        if (isDelete) {
                            overlay.deleteTask(change.eventId)
                            changed = true
                        } else {
                            val data = change.data ?: return@forEach
                            val parsed = mapper.mapAll(
                                resources = listOf(CalendarResource(change.href.orEmpty(), change.etag, data)),
                                calendarId = source.calendar.url,
                                color = source.calendar.color,
                                windowStart = start,
                                windowEnd = end,
                            )
                            parsed.tasks.firstOrNull { it.uid == change.uid || it.id == change.uid }
                                ?.let { task ->
                                    overlay.putTask(
                                        task.copy(
                                            id = change.eventId,
                                            uid = change.uid ?: task.uid,
                                            href = change.href ?: task.href,
                                            etag = change.etag ?: task.etag,
                                        ),
                                    )
                                    changed = true
                                }
                        }
                    }
                    "VJOURNAL" -> {
                        if (isDelete) {
                            overlay.deleteJournal(change.eventId)
                            changed = true
                        } else {
                            val data = change.data ?: return@forEach
                            val parsed = mapper.mapAll(
                                resources = listOf(CalendarResource(change.href.orEmpty(), change.etag, data)),
                                calendarId = source.calendar.url,
                                color = source.calendar.color,
                                windowStart = start,
                                windowEnd = end,
                            )
                            parsed.journals.firstOrNull { it.uid == change.uid || it.id == change.uid }
                                ?.let { journal ->
                                    overlay.putJournal(
                                        journal.copy(
                                            id = change.eventId,
                                            uid = change.uid ?: journal.uid,
                                            href = change.href ?: journal.href,
                                            etag = change.etag ?: journal.etag,
                                        ),
                                    )
                                    changed = true
                            }
                        }
                    }
                }
            }
        if (changed) publish()
    }

    /**
     * Re-applies one queued VEVENT resource to the overlay.
     *
     * A recurring write is a resource operation, not a row operation: one
     * payload can contain the old series, a newly-created FUTURE series, and
     * detached overrides. Replacing only [PendingChange.eventId] loses the
     * other members after a restart (and lets the old fetched group leak back
     * into view). The queue payload is the authoritative local candidate until
     * the server confirms it, so hide the affected visible groups first and
     * then map every matching event from the payload.
     */
    private fun restoreQueuedEventOverlay(
        change: PendingChange,
        source: CalDavSource,
        start: LocalDate,
        end: LocalDate,
    ): Boolean {
        val payloadUids = pendingChangeUids(change)
        val relevantCalendars = buildSet {
            add(source.calendar.url)
            change.sourceCalendarUrl?.let(::add)
            change.sourceCalendarId?.let(::add)
        }
        val visibleEvents = (fetched.events + current.events).distinctBy { it.calendarId to it.id }
        val affected = visibleEvents.filter { event ->
            event.calendarId in relevantCalendars &&
                (event.uid ?: event.id) in payloadUids
        }

        if (change.type == PendingChangeType.DELETE || change.type == PendingChangeType.DELETE_HREF) {
            // A DELETE removes a complete resource. The raw payload lets us
            // hide every recurring member (and every component of this kind)
            // instead of only the row that opened the confirmation dialog.
            affected.forEach { overlay.deleteEvent(it.id) }
            if (affected.isEmpty()) overlay.deleteEvent(change.eventId)
            return affected.isNotEmpty() || change.eventId.isNotBlank()
        }

        val data = change.data ?: return false
        val parsed = mapper.mapAll(
            resources = listOf(CalendarResource(change.href.orEmpty(), change.etag, data)),
            calendarId = source.calendar.url,
            color = source.calendar.color,
            windowStart = start,
            windowEnd = end,
        )
        val parsedEvents = parsed.events.filter { (it.uid ?: it.id) in payloadUids }
        if (parsedEvents.isEmpty()) return false

        // Hide both the source and destination copies for a queued MOVE, and
        // hide the old group before applying a FUTURE split. putEvent clears a
        // matching tombstone for members that still exist in the payload.
        affected.forEach { overlay.deleteEvent(it.id) }
        parsedEvents.forEach { event ->
            overlay.putEvent(
                event.copy(
                    uid = event.uid ?: change.uid,
                    href = change.href ?: event.href,
                    etag = change.etag ?: event.etag,
                    calendarId = source.calendar.url,
                ),
            )
        }
        return true
    }

    /** Applies a just-written resource while replacing all visible group rows. */
    private fun applyEventResourceOverlay(
        calendar: DiscoveredCalendar,
        href: String,
        etag: String?,
        ics: String,
        affectedCalendarUrls: Set<String>,
        fallback: CalEvent,
    ) {
        val uids = runCatching {
            ICalTimezones.parse(ics).flatMap { parsed ->
                parsed.events.mapNotNull { it.uid?.value }
            }.toSet()
        }.getOrDefault(emptySet())
        val visibleEvents = (fetched.events + current.events).distinctBy { it.calendarId to it.id }
        visibleEvents
            .filter { event ->
                event.calendarId in affectedCalendarUrls &&
                    (event.uid ?: event.id) in uids
            }
            .forEach { overlay.deleteEvent(it.id) }
        val start = today().withDayOfMonth(1).minusMonths(windowMonths)
        val end = today().withDayOfMonth(1).plusMonths(windowMonths)
        val parsed = mapper.mapAll(
            resources = listOf(CalendarResource(href, etag, ics)),
            calendarId = calendar.url,
            color = calendar.color,
            windowStart = start,
            windowEnd = end,
        ).events.filter { (it.uid ?: it.id) in uids }
        if (parsed.isEmpty()) {
            overlay.putEvent(fallback)
        } else {
            parsed.forEach { event ->
                overlay.putEvent(
                    event.copy(
                        href = href,
                        etag = etag ?: event.etag,
                        calendarId = calendar.url,
                    ),
                )
            }
        }
    }

    /** Re-applies the latest queued VEVENT for a record after an in-process write. */
    private fun restoreQueuedEventOverlay(eventId: String) {
        val change = pendingStore?.snapshot()
            ?.asReversed()
            ?.firstOrNull {
                it.eventId == eventId &&
                    it.component.equals("VEVENT", ignoreCase = true) &&
                    it.state != PendingChangeState.DEAD_LETTER
            } ?: return
        val source = sources.firstOrNull {
            it.accountId == change.accountId &&
                (it.calendar.url == change.calendarUrl || it.calendar.url == change.calendarId)
        } ?: return
        val start = today().withDayOfMonth(1).minusMonths(windowMonths)
        val end = today().withDayOfMonth(1).plusMonths(windowMonths)
        restoreQueuedEventOverlay(change, source, start, end)
    }

    private suspend fun drainQueue(
        isCurrent: () -> Boolean = { true },
        deferReloads: Boolean = false,
    ) {
        val store = pendingStore ?: return
        while (true) {
            if (!isCurrent()) return
            val change = withContext(ioDispatcher) { store.ready(Instant.now()).firstOrNull() } ?: return
            if (!isCurrent()) return
            if (change.component.equals("VCARD", ignoreCase = true)) {
                val book = addressBookSources.firstOrNull {
                    it.accountId == change.accountId &&
                        (it.addressBook.url == change.calendarUrl || it.addressBook.url == change.calendarId)
                }
                if (book == null) {
                    val failure = PendingChangeFailure("That address book is no longer connected.")
                    withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
                    setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
                    return
                }
                val progressed = when (change.type) {
                    PendingChangeType.CREATE,
                    PendingChangeType.UPDATE,
                    -> replayQueuedCardPut(store, book, change, deferReloads)
                    PendingChangeType.DELETE,
                    PendingChangeType.DELETE_HREF,
                    -> replayQueuedCardDelete(store, book, change, deferReloads)
                    PendingChangeType.MOVE -> {
                        val failure = PendingChangeFailure("Moving a queued contact is not supported yet.")
                        withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
                        setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
                        false
                    }
                }
                if (!progressed) return
                continue
            }
            val source = sources.firstOrNull {
                it.accountId == change.accountId &&
                    (it.calendar.url == change.calendarUrl || it.calendar.url == change.calendarId)
            }
            if (source == null) {
                val failure = PendingChangeFailure("That calendar is no longer connected.")
                withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
                setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
                return
            }
            val progressed = when (change.type) {
                PendingChangeType.CREATE,
                PendingChangeType.UPDATE,
                -> replayQueuedPut(store, source, change)
                PendingChangeType.DELETE,
                PendingChangeType.DELETE_HREF,
                -> replayQueuedDelete(store, source, change, deferReloads)
                PendingChangeType.MOVE -> replayQueuedMove(store, source, change, deferReloads)
            }
            if (!progressed) return
        }
    }

    private suspend fun replayQueuedMove(
        store: PendingChangeStore,
        destination: CalDavSource,
        change: PendingChange,
        deferReloads: Boolean,
    ): Boolean {
        val body = change.data
        val destinationHref = change.href
        val sourceHref = change.sourceHref
        val sourceEtag = normalizeEtag(change.sourceEtag)
        if (body.isNullOrBlank() || destinationHref.isNullOrBlank()) {
            val failure = PendingChangeFailure("The queued move has no destination payload.")
            withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
            setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
            return false
        }

        val destinationOutcome = attemptWrite(destination.calendar.url) {
            putMoveDestination(destination, destinationHref, change.uid ?: change.eventId, body)
        }
        val destinationError = destinationOutcome.exceptionOrNull()
        var source = sourceForQueuedMove(change)
        if (destinationError != null) {
            if (destinationError is MoveDestinationConflictException) {
                val failure = PendingChangeFailure(
                    destinationError.message ?: "The destination already has a different item.",
                    statusCode = 412,
                )
                withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
                setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
                return false
            }
            return handleQueuedFailure(store, change, destinationError)
        }

        val written = destinationOutcome.getOrThrow()
        if (sourceHref.isNullOrBlank()) {
            restoreQueuedEventOverlay(change, destination, today().withDayOfMonth(1).minusMonths(windowMonths), today().withDayOfMonth(1).plusMonths(windowMonths))
            withContext(ioDispatcher) { store.acknowledge(change.id) }
            updateOverlayIdentity(change, written)
            clearWriteStatus(change.eventId)
            return true
        }

        if (sourceEtag == null) {
            val failure = PendingChangeFailure(
                "The destination copy was written, but the old source has no safe server version for cleanup.",
            )
            // A source DELETE without If-Match could remove a newer copy made
            // by another client. The destination half is complete, so retire
            // MOVE and leave an explicit failure rather than retrying it.
            withContext(ioDispatcher) { store.acknowledge(change.id) }
            restoreQueuedEventOverlay(
                change,
                destination,
                today().withDayOfMonth(1).minusMonths(windowMonths),
                today().withDayOfMonth(1).plusMonths(windowMonths),
            )
            updateOverlayIdentity(change, written)
            setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
            reloadAfterQueuedReplay(deferReloads)
            return true
        }

        source = source ?: sourceForQueuedMove(change)
        if (source == null) {
            val failure = PendingChangeFailure("The source calendar for this move is no longer connected.")
            // The destination PUT already succeeded. Preserve the source URL
            // as a separate cleanup operation before acknowledging MOVE, so a
            // later source-calendar reconnect can retry the conditional
            // deletion without replaying the destination PUT.
            val cleanup = enqueueQueuedMoveSourceCleanup(store, change)
            withContext(ioDispatcher) { store.acknowledge(change.id) }
            restoreQueuedEventOverlay(
                change,
                destination,
                today().withDayOfMonth(1).minusMonths(windowMonths),
                today().withDayOfMonth(1).plusMonths(windowMonths),
            )
            updateOverlayIdentity(change, written)
            when (cleanup) {
                is PendingChangeEnqueueResult.Enqueued -> setWriteStatus(
                    change.eventId,
                    RecordWriteState.Pending,
                    "${failure.message} Source cleanup is queued.",
                )
                is PendingChangeEnqueueResult.Rejected -> setWriteStatus(
                    change.eventId,
                    RecordWriteState.Failed,
                    "${failure.message} ${cleanup.reason}",
                )
            }
            reloadAfterQueuedReplay(deferReloads)
            return true
        }

        val cachedSource = withContext(ioDispatcher) {
            cache.loadResource(source.calendar.url, sourceHref)
        }
        if (cachedSource == null) {
            // A restart can leave the queue ahead of the source cache. GET is
            // safe here only as a read: writer.delete still checks the original
            // ETag before it can issue a DELETE.
            attemptWrite(source.calendar.url) {
                writer.refreshResource(source.calendar, source.credentials, sourceHref, change.uid ?: change.eventId)
            }
        }
        val deleteOutcome = attemptWrite(source.calendar.url) {
            writer.delete(
                calendar = source.calendar,
                credentials = source.credentials,
                href = sourceHref,
                uid = change.uid ?: change.eventId,
                etag = sourceEtag,
                component = "VEVENT",
            )
        }
        val deleteError = deleteOutcome.exceptionOrNull()
        if (deleteError != null && !isGoneOrMissing(deleteError)) {
            val event = events().firstOrNull { it.id == change.eventId }
            if (event == null) {
                val failure = PendingChangeFailure(
                    "The destination copy was written, but the old source could not be removed.",
                )
                // The MOVE's destination half is complete. Queue cleanup from
                // the durable source metadata even if the visible event was
                // already evicted from the overlay/fetch snapshot.
                val cleanup = enqueueQueuedMoveSourceCleanup(store, change)
                withContext(ioDispatcher) { store.acknowledge(change.id) }
                updateOverlayIdentity(change, written)
                when (cleanup) {
                    is PendingChangeEnqueueResult.Enqueued -> setWriteStatus(
                        change.eventId,
                        RecordWriteState.Pending,
                        "${failure.message} Source cleanup is queued.",
                    )
                    is PendingChangeEnqueueResult.Rejected -> setWriteStatus(
                        change.eventId,
                        RecordWriteState.Failed,
                        "${failure.message} ${cleanup.reason}",
                    )
                }
                reloadAfterQueuedReplay(deferReloads)
                return true
            }
            val cleanup = queueMoveSourceCleanup(
                source = source,
                event = event,
                href = sourceHref,
                etag = sourceEtag,
                data = cachedSource?.ics ?: change.sourceData,
                error = deleteError,
                beforeId = change.id,
            )
            // Destination success means the MOVE itself is complete. The
            // cleanup queue owns the remaining source deletion and must not
            // cause the destination PUT to run a second time.
            withContext(ioDispatcher) { store.acknowledge(change.id) }
            restoreQueuedEventOverlay(
                change,
                destination,
                today().withDayOfMonth(1).minusMonths(windowMonths),
                today().withDayOfMonth(1).plusMonths(windowMonths),
            )
            updateOverlayIdentity(change, written)
            when (cleanup) {
                is WriteResult.Queued -> setWriteStatus(
                    change.eventId,
                    RecordWriteState.Pending,
                    "The destination copy was written; source cleanup is queued.",
                )
                is WriteResult.Rejected -> setWriteStatus(change.eventId, RecordWriteState.Failed, cleanup.reason)
                is WriteResult.Applied -> Unit
            }
            reloadAfterQueuedReplay(deferReloads)
            return true
        }

        withContext(ioDispatcher) { store.acknowledge(change.id) }
        restoreQueuedEventOverlay(
            change,
            destination,
            today().withDayOfMonth(1).minusMonths(windowMonths),
            today().withDayOfMonth(1).plusMonths(windowMonths),
        )
        updateOverlayIdentity(change, written)
        clearWriteStatus(change.eventId)
        reloadAfterQueuedReplay(deferReloads)
        return true
    }

    private fun sourceForQueuedMove(change: PendingChange): CalDavSource? = sources.firstOrNull {
        it.accountId == (change.sourceAccountId ?: change.accountId) &&
            (it.calendar.url == change.sourceCalendarUrl || it.calendar.url == change.sourceCalendarId)
    }

    /**
     * Enqueues conditional source cleanup for a MOVE whose destination half is
     * already complete. It intentionally uses the source snapshot, never the
     * destination payload, because the two resources may have different
     * server-owned fields and the source cache may be unavailable after a
     * restart.
     */
    private suspend fun enqueueQueuedMoveSourceCleanup(
        store: PendingChangeStore,
        change: PendingChange,
    ): PendingChangeEnqueueResult {
        val sourceUrl = change.sourceCalendarUrl ?: change.sourceCalendarId
        val sourceHref = change.sourceHref
        val sourceEtag = normalizeEtag(change.sourceEtag)
        if (sourceUrl.isNullOrBlank() || sourceHref.isNullOrBlank() || sourceEtag == null) {
            return PendingChangeEnqueueResult.Rejected(
                "The queued move has insufficient source URL/version metadata for cleanup.",
            )
        }
        val request = PendingChangeRequest(
            type = PendingChangeType.DELETE_HREF,
            eventId = change.eventId,
            accountId = change.sourceAccountId ?: change.accountId,
            calendarId = sourceUrl,
            component = "VEVENT",
            calendarUrl = sourceUrl,
            uid = change.uid ?: change.eventId,
            href = sourceHref,
            etag = sourceEtag,
            data = change.sourceData,
        )
        return runCatching {
            withContext(ioDispatcher) { store.enqueueBefore(change.id, request) }
        }.getOrElse { error ->
            PendingChangeEnqueueResult.Rejected(
                "The source cleanup could not be stored: ${error.message.orEmpty()}",
            )
        }
    }

    private suspend fun replayQueuedPut(
        store: PendingChangeStore,
        source: CalDavSource,
        change: PendingChange,
    ): Boolean {
        val body = change.data
        val href = change.href
        if (body.isNullOrBlank() || href.isNullOrBlank()) {
            val failure = PendingChangeFailure("The queued calendar item has no resource payload.")
            withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
            setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
            return false
        }
        if (change.type != PendingChangeType.CREATE && change.baseData.isNullOrBlank()) {
            val failure = PendingChangeFailure(
                "The queued calendar update has no original server snapshot and cannot be rebased safely. Refresh and edit again.",
            )
            withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
            setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
            return false
        }
        var prepared = PreparedCalendarWrite(
            href = href,
            body = body,
            precondition = if (change.type == PendingChangeType.CREATE) {
                calino.malinov.ski.data.caldav.DavPrecondition.New
            } else {
                change.etag?.let(calino.malinov.ski.data.caldav.DavPrecondition::Match)
                    ?: run {
                        val failure = PendingChangeFailure("The queued update has no server version.")
                        withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
                        setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
                        return false
                    }
            },
            expectedEtag = change.etag,
        )
        var outcome = attemptWrite(source.calendar.url) {
            writer.putPrepared(source.calendar, source.credentials, prepared)
        }
        var error = outcome.exceptionOrNull()

        // A CREATE can be accepted by the server just before a connection
        // drops. Replaying the same If-None-Match request then returns 412,
        // which is not enough information to call it a real UID collision.
        // Read the resource once: an exact payload means the first attempt
        // already applied, while a different payload is a genuine conflict.
        // If the resource disappeared between those two requests, retry the
        // create; it is still protected by If-None-Match: *.
        if (error != null && change.type == PendingChangeType.CREATE && isPreconditionFailure(error)) {
            val refreshed = attemptWrite(source.calendar.url) {
                writer.refreshResource(source.calendar, source.credentials, href, change.uid ?: change.eventId)
            }
            val resource = refreshed.getOrNull()
            val refreshError = refreshed.exceptionOrNull()
            when {
                refreshError == null && resource != null && sameQueuedPayload(resource.ics, body) -> {
                    updateOverlayIdentity(
                        change,
                        WrittenCalendarResource(resource.href, resource.etag, resource.ics),
                    )
                    // Publish the confirmed identity before removing the
                    // durable work item. A queue observer must not see an
                    // empty queue while the visible overlay still has the
                    // pre-recovery identity.
                    withContext(ioDispatcher) { store.acknowledge(change.id) }
                    clearWriteStatus(change.eventId)
                    return true
                }
                refreshError == null && resource != null -> {
                    val failure = PendingChangeFailure(
                        "The server already has a different calendar item at this resource URL.",
                        statusCode = 412,
                    )
                    withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
                    setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
                    return false
                }
                refreshError != null && isGoneOrMissing(refreshError) -> {
                    outcome = attemptWrite(source.calendar.url) {
                        writer.putPrepared(source.calendar, source.credentials, prepared)
                    }
                    error = outcome.exceptionOrNull()
                }
                refreshError != null -> return handleQueuedFailure(store, change, refreshError)
            }
        }
        if (error != null && classifyWriteError(error, change.type).disposition == WriteDisposition.StaleEtag) {
            val refreshed = attemptWrite(source.calendar.url) {
                writer.refreshResource(source.calendar, source.credentials, href, change.uid ?: change.eventId)
            }
            val resource = refreshed.getOrNull()
            val refreshedError = refreshed.exceptionOrNull()
            if (refreshedError == null && resource?.etag != null) {
                val rebased = recurrencePatcher.rebaseResource(
                    currentIcs = resource.ics,
                    localIcs = body,
                    baseIcs = change.baseData!!,
                    component = change.component,
                    uids = pendingChangeUids(change),
                )
                if (rebased == null) {
                    error = CalDavException(
                        CalDavErrorCode.NotCalDav,
                        "The queued calendar update could not be rebased safely. Refresh and edit again.",
                    )
                } else {
                    prepared = prepared.copy(
                        body = rebased,
                        precondition = calino.malinov.ski.data.caldav.DavPrecondition.Match(resource.etag),
                        expectedEtag = resource.etag,
                    )
                    outcome = attemptWrite(source.calendar.url) {
                        writer.putPrepared(source.calendar, source.credentials, prepared)
                    }
                    error = outcome.exceptionOrNull()
                }
            } else {
                error = refreshedError ?: CalDavException(
                    CalDavErrorCode.PreconditionFailed,
                    "The server did not return a current version for that item.",
                    status = 412,
                )
            }
        }
        if (error != null) return handleQueuedFailure(store, change, error)

        val written = outcome.getOrThrow()
        withContext(ioDispatcher) { store.acknowledge(change.id) }
        updateOverlayIdentity(change, written)
        clearWriteStatus(change.eventId)
        return true
    }

    private fun isPreconditionFailure(error: Throwable): Boolean {
        val dav = error as? CalDavException ?: return false
        return dav.status == 412 || dav.code == CalDavErrorCode.PreconditionFailed
    }

    private fun sameQueuedPayload(left: String, right: String): Boolean =
        left.replace("\r\n", "\n").trim() == right.replace("\r\n", "\n").trim()

    /** Create the destination once; a retry may only accept the copy we already wrote. */
    private suspend fun putMoveDestination(
        destination: CalDavSource,
        href: String,
        uid: String,
        body: String,
    ): WrittenCalendarResource {
        val prepared = PreparedCalendarWrite(href, body, DavPrecondition.New, null)
        try {
            return writer.putPrepared(destination.calendar, destination.credentials, prepared)
        } catch (error: CalDavException) {
            if (!isPreconditionFailure(error)) throw error
        }
        val existing = try {
            writer.refreshResource(destination.calendar, destination.credentials, href, uid)
        } catch (error: CalDavException) {
            if (!isGoneOrMissing(error)) throw error
            // The competing resource vanished. The second PUT remains create-only.
            return writer.putPrepared(destination.calendar, destination.credentials, prepared)
        }
        if (!sameQueuedPayload(existing.ics, body)) {
            throw MoveDestinationConflictException(
                "The destination already has a different item; the source was kept.",
            )
        }
        return WrittenCalendarResource(existing.href, existing.etag, existing.ics)
    }

    private suspend fun replayQueuedDelete(
        store: PendingChangeStore,
        source: CalDavSource,
        change: PendingChange,
        deferReloads: Boolean,
    ): Boolean {
        val href = change.href
        val uid = change.uid ?: change.eventId
        if (href.isNullOrBlank()) {
            val failure = PendingChangeFailure("The queued delete has no resource URL.")
            withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
            setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
            return false
        }
        change.data?.let { data ->
            withContext(ioDispatcher) {
                val cached = cache.loadResource(source.calendar.url, href)
                if (cached == null || sameEtag(cached.etag, change.etag)) {
                    cache.saveResource(source.calendar.url, CalendarResource(href, change.etag, data))
                }
            }
        }
        var expectedEtag = change.etag
        var outcome = attemptWrite(source.calendar.url) {
            writer.delete(source.calendar, source.credentials, href, uid, expectedEtag, change.component)
        }
        var error = outcome.exceptionOrNull()
        if (change.type != PendingChangeType.DELETE_HREF &&
            error != null &&
            classifyWriteError(error, change.type).disposition == WriteDisposition.StaleEtag
        ) {
            val refreshed = attemptWrite(source.calendar.url) {
                writer.refreshResource(source.calendar, source.credentials, href, uid)
            }
            val resource = refreshed.getOrNull()
            val refreshedError = refreshed.exceptionOrNull()
            if (refreshedError == null && resource?.etag != null) {
                expectedEtag = resource.etag
                outcome = attemptWrite(source.calendar.url) {
                    writer.delete(source.calendar, source.credentials, href, uid, expectedEtag, change.component)
                }
                error = outcome.exceptionOrNull()
            } else {
                error = refreshedError ?: CalDavException(
                    CalDavErrorCode.PreconditionFailed,
                    "The server did not return a current version for that item.",
                    status = 412,
                )
            }
        }
        if (error != null) {
            val dav = error as? CalDavException
            if (dav?.status == 404 || dav?.status == 410 || dav?.code == CalDavErrorCode.NotFound || dav?.code == CalDavErrorCode.Gone) {
                withContext(ioDispatcher) { store.acknowledge(change.id) }
                clearWriteStatus(change.eventId)
                reloadAfterQueuedReplay(deferReloads)
                return true
            }
            return handleQueuedFailure(store, change, error)
        }
        withContext(ioDispatcher) { store.acknowledge(change.id) }
        clearWriteStatus(change.eventId)
        reloadAfterQueuedReplay(deferReloads)
        return true
    }

    private suspend fun replayQueuedCardPut(
        store: PendingChangeStore,
        source: CardDavSource,
        change: PendingChange,
        deferReloads: Boolean,
    ): Boolean {
        val body = change.data
        val href = change.href
        if (body.isNullOrBlank() || href.isNullOrBlank()) {
            val failure = PendingChangeFailure("The queued contact has no resource payload.")
            withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
            setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
            return false
        }
        if (change.type != PendingChangeType.CREATE && change.baseData.isNullOrBlank()) {
            val failure = PendingChangeFailure(
                "The queued contact update has no original server snapshot and cannot be rebased safely. Refresh and edit again.",
            )
            withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
            setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
            return false
        }
        var prepared = PreparedCardWrite(
            href = href,
            body = body,
            precondition = if (change.type == PendingChangeType.CREATE) {
                calino.malinov.ski.data.caldav.DavPrecondition.New
            } else {
                change.etag?.let(calino.malinov.ski.data.caldav.DavPrecondition::Match)
                    ?: run {
                        val failure = PendingChangeFailure("The queued contact update has no server version.")
                        withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
                        setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
                        return false
                    }
            },
            expectedEtag = change.etag,
        )
        var outcome = attemptWrite(source.addressBook.url) {
            cardWriter.putPrepared(source.addressBook, source.credentials, prepared)
        }
        var error = outcome.exceptionOrNull()
        // The first CREATE may have reached CardDAV even if its response was
        // lost. Verify a 412 before treating it as a duplicate: exact bytes
        // mean the operation is already complete, a missing resource can be
        // safely retried, and different bytes are a real collision.
        if (error != null && change.type == PendingChangeType.CREATE && isPreconditionFailure(error)) {
            val refreshed = attemptWrite(source.addressBook.url) {
                cardWriter.refreshResource(source.addressBook, source.credentials, href)
            }
            val resource = refreshed.getOrNull()
            val refreshError = refreshed.exceptionOrNull()
            when {
                refreshError == null && resource != null && sameQueuedPayload(resource.vcf, body) -> {
                    updateContactOverlayIdentity(change, resource)
                    // Publish the confirmed identity before removing the
                    // durable work item. A queue observer must not see an
                    // empty queue while the visible overlay still has the
                    // pre-recovery identity.
                    withContext(ioDispatcher) { store.acknowledge(change.id) }
                    clearWriteStatus(change.eventId)
                    return true
                }
                refreshError == null && resource != null -> {
                    val failure = PendingChangeFailure(
                        "The server already has a different contact at this resource URL.",
                        statusCode = 412,
                    )
                    withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
                    setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
                    return false
                }
                refreshError != null && isGoneOrMissing(refreshError) -> {
                    outcome = attemptWrite(source.addressBook.url) {
                        cardWriter.putPrepared(source.addressBook, source.credentials, prepared)
                    }
                    error = outcome.exceptionOrNull()
                }
                refreshError != null -> return handleQueuedFailure(store, change, refreshError)
            }
        }
        if (error != null && classifyWriteError(error, change.type).disposition == WriteDisposition.StaleEtag) {
            val refreshed = attemptWrite(source.addressBook.url) {
                cardWriter.refreshResource(source.addressBook, source.credentials, href)
            }
            val resource = refreshed.getOrNull()
            val refreshedError = refreshed.exceptionOrNull()
            if (refreshedError == null && resource?.etag != null) {
                val rebased = vCardPatcher.rebaseResource(
                    currentVcf = resource.vcf,
                    localVcf = body,
                    baseVcf = change.baseData!!,
                )
                if (rebased == null) {
                    error = CalDavException(
                        CalDavErrorCode.NotCalDav,
                        "The queued contact update could not be rebased safely. Refresh and edit again.",
                    )
                } else {
                    prepared = prepared.copy(
                        body = rebased,
                        precondition = calino.malinov.ski.data.caldav.DavPrecondition.Match(resource.etag),
                        expectedEtag = resource.etag,
                    )
                    outcome = attemptWrite(source.addressBook.url) {
                        cardWriter.putPrepared(source.addressBook, source.credentials, prepared)
                    }
                    error = outcome.exceptionOrNull()
                }
            } else {
                error = refreshedError ?: CalDavException(
                    CalDavErrorCode.PreconditionFailed,
                    "The server did not return a current version for that contact.",
                    status = 412,
                )
            }
        }
        if (error != null) return handleQueuedFailure(store, change, error)

        val written = outcome.getOrThrow()
        withContext(ioDispatcher) { store.acknowledge(change.id) }
        updateContactOverlayIdentity(change, written)
        clearWriteStatus(change.eventId)
        reloadAfterQueuedReplay(deferReloads)
        return true
    }

    private suspend fun replayQueuedCardDelete(
        store: PendingChangeStore,
        source: CardDavSource,
        change: PendingChange,
        deferReloads: Boolean,
    ): Boolean {
        val href = change.href
        if (href.isNullOrBlank()) {
            val failure = PendingChangeFailure("The queued contact delete has no resource URL.")
            withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
            setWriteStatus(change.eventId, RecordWriteState.Failed, failure.message)
            return false
        }
        change.data?.let { data ->
            withContext(ioDispatcher) { saveCardResource(source.addressBook.url, CardResource(href, change.etag, data)) }
        }
        var expectedEtag = change.etag
        if (expectedEtag == null || change.data == null) {
            val refreshed = attemptWrite(source.addressBook.url) {
                cardWriter.refreshResource(source.addressBook, source.credentials, href)
            }
            val resource = refreshed.getOrNull()
            val refreshError = refreshed.exceptionOrNull()
            if (refreshError != null) {
                val dav = refreshError as? CalDavException
                if (dav?.status == 404 || dav?.status == 410 || dav?.code == CalDavErrorCode.NotFound || dav?.code == CalDavErrorCode.Gone) {
                    withContext(ioDispatcher) { store.acknowledge(change.id) }
                    clearWriteStatus(change.eventId)
                    reloadAfterQueuedReplay(deferReloads)
                    return true
                }
                return handleQueuedFailure(store, change, refreshError)
            }
            expectedEtag = resource?.etag
        }
        var outcome = attemptWrite(source.addressBook.url) {
            cardWriter.delete(source.addressBook, source.credentials, href, expectedEtag)
        }
        var error = outcome.exceptionOrNull()
        if (error != null && classifyWriteError(error, PendingChangeType.DELETE).disposition == WriteDisposition.StaleEtag) {
            val refreshed = attemptWrite(source.addressBook.url) {
                cardWriter.refreshResource(source.addressBook, source.credentials, href)
            }
            val resource = refreshed.getOrNull()
            val refreshError = refreshed.exceptionOrNull()
            if (refreshError == null && resource?.etag != null) {
                expectedEtag = resource.etag
                outcome = attemptWrite(source.addressBook.url) {
                    cardWriter.delete(source.addressBook, source.credentials, href, expectedEtag)
                }
                error = outcome.exceptionOrNull()
            } else {
                error = refreshError ?: CalDavException(
                    CalDavErrorCode.PreconditionFailed,
                    "The server did not return a current version for that contact.",
                    status = 412,
                )
            }
        }
        if (error != null) {
            val dav = error as? CalDavException
            if (dav?.status == 404 || dav?.status == 410 || dav?.code == CalDavErrorCode.NotFound || dav?.code == CalDavErrorCode.Gone) {
                withContext(ioDispatcher) { store.acknowledge(change.id) }
                clearWriteStatus(change.eventId)
                reloadAfterQueuedReplay(deferReloads)
                return true
            }
            return handleQueuedFailure(store, change, error)
        }
        withContext(ioDispatcher) { store.acknowledge(change.id) }
        clearWriteStatus(change.eventId)
        reloadAfterQueuedReplay(deferReloads)
        return true
    }

    private suspend fun handleQueuedFailure(
        store: PendingChangeStore,
        change: PendingChange,
        error: Throwable,
    ): Boolean {
        var classification = classifyWriteError(error, change.type)
        if (classification.disposition == WriteDisposition.StaleEtag) {
            classification = classification.copy(disposition = WriteDisposition.RetryCounted)
        }
        val failure = PendingChangeFailure(
            message = classification.message,
            statusCode = classification.statusCode,
        )
        when (val disposition = classification.disposition) {
            is WriteDisposition.Drop -> {
                withContext(ioDispatcher) { store.markDeadLetter(change.id, failure) }
                setWriteStatus(change.eventId, RecordWriteState.Failed, disposition.reason)
            }
            WriteDisposition.Retry,
            WriteDisposition.RetryCounted,
            -> {
                val updated = withContext(ioDispatcher) {
                    store.markRetry(
                        id = change.id,
                        failure = failure,
                        counted = disposition == WriteDisposition.RetryCounted,
                    )
                }
                val status = if (updated?.state == PendingChangeState.DEAD_LETTER) {
                    RecordWriteState.Failed
                } else {
                    RecordWriteState.Pending
                }
                setWriteStatus(change.eventId, status, failure.message)
            }
            WriteDisposition.StaleEtag -> error("stale ETag should have been normalized")
        }
        return false
    }

    private fun updateOverlayIdentity(change: PendingChange, written: WrittenCalendarResource) {
        when (change.component.uppercase()) {
            "VEVENT" -> events().firstOrNull { it.id == change.eventId }?.let {
                overlay.putEvent(it.copy(href = written.href, etag = written.etag, uid = change.uid ?: it.uid))
            }
            "VTODO" -> tasks().firstOrNull { it.id == change.eventId }?.let {
                overlay.putTask(it.copy(href = written.href, etag = written.etag, uid = change.uid ?: it.uid))
            }
            "VJOURNAL" -> journals().firstOrNull { it.id == change.eventId }?.let {
                overlay.putJournal(it.copy(href = written.href, etag = written.etag, uid = change.uid ?: it.uid))
            }
        }
        publish()
    }

    private fun updateContactOverlayIdentity(change: PendingChange, written: CardResource) {
        contacts().firstOrNull { it.id == change.eventId }?.let { contact ->
            overlay.putContact(
                contact.copy(
                    uid = change.uid ?: contact.uid,
                    href = written.href,
                    etag = written.etag,
                    rawVCard = written.vcf,
                ),
            )
        }
        publish()
    }

    private fun saveCardResource(bookUrl: String, resource: CardResource) {
        val entry = cache.loadAddressBook(bookUrl) ?: return
        val without = entry.resources.filterNot {
            it.href == resource.href || CardDavWriter.resolveHref(bookUrl, it.href) == resource.href
        }
        cache.saveAddressBook(entry.copy(resources = without + resource))
    }

    /** The exact raw calendar snapshot used to build a conditional update. */
    private fun cachedCalendarBase(
        source: CalDavSource,
        prepared: PreparedCalendarWrite,
    ): String? {
        val expected = normalizeEtag(prepared.expectedEtag) ?: return null
        return cache.loadResource(source.calendar.url, prepared.href)
            ?.takeIf { normalizeEtag(it.etag) == expected }
            ?.ics
    }

    /** The exact raw vCard snapshot used to build a conditional update. */
    private fun cachedCardBase(
        source: CardDavSource,
        prepared: PreparedCardWrite,
    ): String? {
        val expected = normalizeEtag(prepared.expectedEtag) ?: return null
        return cache.loadAddressBook(source.addressBook.url)?.resources
            ?.firstOrNull {
                (it.href == prepared.href || CardDavWriter.resolveHref(source.addressBook.url, it.href) == prepared.href) &&
                    normalizeEtag(it.etag) == expected
            }
            ?.vcf
    }

    private fun resourceHref(collectionUrl: String, href: String?, uid: String): String {
        href?.let {
            val resolved = CalDavWriter.resolveHref(collectionUrl, it)
            if (CalDavWriter.resourceIsInCollection(resolved, collectionUrl)) return resolved
        }
        return CalDavWriter.resolveHref(collectionUrl, CalDavWriter.eventResourceFilename(uid))
    }

    /** Builds a move payload without carrying a foreign UID into the target. */
    private fun buildMovePayload(
        originalIcs: String,
        uid: String,
        event: CalEvent,
        scope: RecurrenceEditScope,
        recurrenceChanged: Boolean,
    ): String? {
        val calendar = runCatching { ICalTimezones.parse(originalIcs).singleOrNull() }.getOrNull()
            ?: return null
        // A move operates on the complete resource. Refuse a shared resource
        // containing another UID or component rather than copying/deleting an
        // unrelated task, journal, or event as a side effect.
        if (calendar.events.isEmpty() || calendar.todos.isNotEmpty() || calendar.journals.isNotEmpty()) return null
        if (calendar.events.any { it.uid?.value != uid }) return null

        val now = Instant.now()
        if (event.recurrence == null && calendar.events.size == 1 && calendar.events.single().recurrenceId == null) {
            return recurrencePatcher.patchEvents(
                originalIcs,
                listOf(event.copy(uid = uid, href = null, etag = null)),
                now,
            )
        }

        val group = runCatching { RecurrenceEdit.Group.from(calendar, uid) }.getOrNull() ?: return null
        val target = when {
            event.recurrenceDate != null -> RecurrenceEdit.Target.allDay(event.recurrenceDate)
            event.recurrenceId != null -> RecurrenceEdit.Target.timed(event.recurrenceId)
            else -> RecurrenceEdit.Target.from(group.master)
        } ?: return null
        val original = group.events.firstOrNull { raw ->
            when {
                event.recurrenceDate != null -> raw.recurrenceId?.value?.let { value ->
                    !value.hasTime() && value.rawComponents?.let {
                        runCatching { LocalDate.of(it.year, it.month, it.date) }.getOrNull()
                    } == event.recurrenceDate
                } == true
                event.recurrenceId != null -> raw.recurrenceId?.value?.let {
                    runCatching { it.toInstant() }.getOrNull() == event.recurrenceId
                } == true
                else -> raw.recurrenceId == null
            }
        } ?: group.master
        val replacement = recurrenceWriter.writeEvent(
            event.copy(
                uid = uid,
                href = null,
                etag = null,
                recurrenceId = null,
                recurrenceDate = null,
            ),
            original = original,
            now = now,
            preserveRecurrenceIfMissing = !recurrenceChanged,
        )
        val result = RecurrenceEdit.edit(
            group = group,
            target = target,
            scope = scope.toDavScope(),
            replacement = replacement,
        )
        return recurrencePatcher.patchRecurrence(originalIcs, uid, result)
    }

    private fun setWriteStatus(id: String, state: RecordWriteState, reason: String?) {
        writeStatuses = writeStatuses + (id to RecordWriteStatus(state, reason))
        publish()
    }

    private fun clearWriteStatus(id: String) {
        if (id !in writeStatuses) return
        writeStatuses = writeStatuses - id
        publish()
    }

    private fun sameEtag(left: String?, right: String?): Boolean =
        normalizeEtag(left) != null && normalizeEtag(left) == normalizeEtag(right)

    private fun isGoneOrMissing(error: Throwable): Boolean {
        val dav = error as? CalDavException
        return dav?.status == 404 || dav?.status == 410 ||
            dav?.code == CalDavErrorCode.NotFound || dav?.code == CalDavErrorCode.Gone
    }

    private suspend fun <T> attemptWrite(url: String, operation: suspend () -> T): Result<T> =
        try {
            ensureAccountSyncCurrent()
            val value = withContext(ioDispatcher) {
                ensureAccountSyncCurrent()
                writeMutex.withLock {
                    ensureAccountSyncCurrent()
                    operation()
                }
            }
            ensureAccountSyncCurrent()
            Result.success(value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (aborted: AccountSyncAbortedException) {
            throw aborted
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private suspend fun ensureAccountSyncCurrent() {
        if (currentCoroutineContext()[AccountSyncGuard]?.isCurrent?.invoke() == false) {
            throw AccountSyncAbortedException()
        }
    }

    private fun rejected(error: Throwable, url: String): WriteResult.Rejected =
        WriteResult.Rejected(calDavErrorForThrowable(error, url).message)

    private fun sourceForCreate(component: String, preferredId: String?, href: String? = null): CalDavSource? =
        sourceForRecord(null, href)
            // `personal` is the legacy NewEvent default used by the fixture
            // editor. Connected calendars have URL ids, so treat that
            // placeholder as "use the first accepting collection" while an
            // actually supplied unknown id must fail rather than silently
            // saving into the wrong calendar.
            ?: if (preferredId != null && preferredId != "personal") {
                sources.firstOrNull { it.calendar.url == preferredId }
            } else {
                sources.firstOrNull { it.calendar.acceptsComponent(component) }
            }

    private fun sourceForContactCreate(preferredId: String?): CardDavSource? =
        if (preferredId != null) {
            addressBookSources.firstOrNull {
                it.addressBook.url == preferredId || it.addressBook.displayName == preferredId
            }
        } else {
            addressBookSources.firstOrNull { !it.addressBook.readOnly }
        }

    private fun sourceForContact(contact: Contact): CardDavSource? =
        contact.href?.let { itemHref ->
            addressBookSources.firstOrNull { source ->
                CardDavWriter.resourceIsInCollection(itemHref, source.addressBook.url)
            }
        } ?: addressBookSources.firstOrNull {
            it.addressBook.url == contact.addressBookId
        }

    private fun sourceForRecord(calendarId: String?, href: String?): CalDavSource? =
        href?.let { itemHref ->
            sources.firstOrNull { source -> CalDavWriter.resourceIsInCollection(itemHref, source.calendar.url) }
        } ?: calendarId?.let { id -> sources.firstOrNull { it.calendar.url == id } }

    private fun writableRejection(source: CalDavSource?, component: String): WriteResult.Rejected? {
        if (source == null) return WriteResult.Rejected("No connected calendar accepts $component.")
        if (source.calendar.readOnly) return WriteResult.Rejected("${source.calendar.displayName} is read-only.")
        if (!source.calendar.acceptsComponent(component)) {
            return WriteResult.Rejected("${source.calendar.displayName} does not accept $component items.")
        }
        return null
    }

    private fun isRecurringTarget(event: CalEvent): Boolean =
        event.recurrenceId != null || event.recurrenceDate != null ||
            (event.uid != null && event.id != event.uid)

    private fun hideEventGroup(event: CalEvent) {
        val uid = event.uid ?: event.id
        events().filter { (it.uid ?: it.id) == uid }.forEach { overlay.deleteEvent(it.id) }
    }

    private fun rawMatchesEvent(raw: VEvent, event: CalEvent): Boolean {
        val recurrence = raw.recurrenceId?.value ?: return event.recurrenceId == null &&
            event.recurrenceDate == null
        if (event.recurrenceDate != null) {
            val components = recurrence.rawComponents ?: return false
            return !recurrence.hasTime() && runCatching {
                LocalDate.of(components.year, components.month, components.date)
            }.getOrNull() == event.recurrenceDate
        }
        return event.recurrenceId != null && runCatching { recurrence.toInstant() }.getOrNull() == event.recurrenceId
    }

    private fun RecurrenceEditScope.toDavScope(): RecurrenceEdit.Scope = when (this) {
        RecurrenceEditScope.This -> RecurrenceEdit.Scope.THIS
        RecurrenceEditScope.Future -> RecurrenceEdit.Scope.FUTURE
        RecurrenceEditScope.All -> RecurrenceEdit.Scope.ALL
    }

    private fun DiscoveredCalendar.acceptsComponent(component: String): Boolean =
        components.isEmpty() || component.uppercase() in components

    private fun List<CalDavSource>.sameCalendarConfigurationAs(other: List<CalDavSource>): Boolean =
        size == other.size && zip(other).all { (left, right) ->
            left.accountId == right.accountId &&
                left.credentials == right.credentials &&
                left.calendar == right.calendar
        }

    private fun List<CardDavSource>.sameAddressBookConfigurationAs(other: List<CardDavSource>): Boolean =
        size == other.size && zip(other).all { (left, right) ->
            left.accountId == right.accountId &&
                left.credentials == right.credentials &&
                left.addressBook == right.addressBook
        }

    // --- composition ----------------------------------------------------------

    private fun compose(): CalinoSnapshot {
        val calendars = sources.map { source ->
            CalinoCalendar(
                id = source.calendar.url,
                name = source.calendar.displayName,
                color = source.calendar.color,
                // Discovery has always worked both of these out; until writes
                // existed there was nothing downstream that needed to know.
                readOnly = source.calendar.readOnly,
                components = source.calendar.components,
                visible = source.visible,
                showTasksInViews = source.showTasksInViews,
            )
        } + webcal.calendars
        val events = overlay.applyToEvents(fetched.events + webcal.events)
        val tasks = overlay.applyToTasks(fetched.tasks)
        val journals = overlay.applyToJournals(fetched.journals)
        val contacts = overlay.applyToContacts(fetched.contacts)
        val addressBooks = addressBookSources.map { source ->
            ContactAddressBook(
                id = source.addressBook.url,
                accountId = source.accountId,
                url = source.addressBook.url,
                name = source.addressBook.displayName,
                description = source.addressBook.description,
                ctag = source.addressBook.ctag,
                readOnly = source.addressBook.readOnly,
            )
        }
        return CalinoSnapshot(
            events = events,
            tasks = tasks,
            journals = journals,
            contacts = contacts,
            addressBooks = addressBooks,
            revision = current.revisionOrZero() + 1,
            calendars = calendars.ifEmpty { FixtureCalendars },
            // Only what the account's records use: the fixture's sample names
            // belong to the sample data, not to a real calendar.
            categories = (events.flatMap { it.categories } + tasks.mapNotNull { it.category })
                .distinct()
                .sortedBy { it.lowercase() },
            sync = syncState,
            writeStatus = writeStatuses,
        )
    }

    private fun CalinoSnapshot?.revisionOrZero(): Long = this?.revision ?: 0

    private fun publish() {
        current = compose()
        listeners.forEach { it(current) }
    }

    private data class FetchedData(
        val events: List<CalEvent> = emptyList(),
        val tasks: List<CalTask> = emptyList(),
        val journals: List<JournalEntry> = emptyList(),
        val contacts: List<Contact> = emptyList(),
    ) {
        fun isEmpty(): Boolean = events.isEmpty() && tasks.isEmpty() && journals.isEmpty() && contacts.isEmpty()
    }

    companion object {
        /**
         * Months either side of today to request.
         *
         * Bounded because expansion materialises every occurrence, and a rule
         * like `FREQ=DAILY` with no UNTIL is infinite. The window is what
         * bounds it -- see `ICalMapper.parse`.
         */
        const val DefaultWindowMonths = 24L
    }
}

/** An edit that did not supply attachments shows the ones the event already had. */
private fun CalEvent.keepingAttachments(input: NewEvent, current: CalEvent): CalEvent =
    if (input.attachments == null) copy(attachments = current.attachments) else this
