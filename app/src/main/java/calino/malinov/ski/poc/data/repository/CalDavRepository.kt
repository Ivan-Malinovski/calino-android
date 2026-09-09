package calino.malinov.ski.poc.data.repository

import calino.malinov.ski.poc.data.caldav.CachedCalendar
import calino.malinov.ski.poc.data.caldav.CalDavFetcher
import calino.malinov.ski.poc.data.caldav.CalendarCache
import calino.malinov.ski.poc.data.caldav.CalendarResource
import calino.malinov.ski.poc.data.caldav.DavCredentials
import calino.malinov.ski.poc.data.caldav.DiscoveredCalendar
import calino.malinov.ski.poc.data.caldav.ICalMapper
import calino.malinov.ski.poc.data.caldav.calDavErrorForThrowable
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.NewEvent
import calino.malinov.ski.poc.data.model.NewJournal
import calino.malinov.ski.poc.data.model.NewTask
import java.io.Closeable
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One calendar to read, with the credentials that reach it. */
data class CalDavSource(
    val calendar: DiscoveredCalendar,
    val credentials: DavCredentials,
    val accountId: String,
)

/**
 * A [CalinoRepository] backed by real CalDAV collections.
 *
 * Read-only against the server. The interface still carries the app's write
 * methods and live UI paths call them, so those apply to an in-memory overlay
 * layered on top of the fetched data. Nothing is sent to the server, and a
 * refetch discards the overlay. That keeps the editor and task completion
 * working without pretending an edit synced -- the accounts surface says as
 * much in plain words.
 *
 * Reads are cached. Each successful fetch writes the server's own resource
 * text to [cache], and a launch maps that back before any request is made, so
 * a connected calendar renders immediately and stays readable with no network.
 * Only the read side is cached: [overlay] still lives and dies with the
 * process, because an edit that cannot sync must not look durable.
 */
class CalDavRepository(
    private val fetcher: CalDavFetcher,
    private val scope: CoroutineScope,
    /** Last successful read per calendar, so a cold start is not a blank one. */
    private val cache: CalendarCache = CalendarCache.None,
    private val mapper: ICalMapper = ICalMapper(),
    private val today: () -> LocalDate = { LocalDate.now() },
    private val windowMonths: Long = DefaultWindowMonths,
    /** Injected so tests can drive the fetch on their own scheduler. */
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : CalinoRepository {

    private val listeners = CopyOnWriteArrayList<(CalinoSnapshot) -> Unit>()

    /** What the server last gave us. */
    private var fetched = FetchedData()

    /** Local, unsynced edits layered over [fetched]. */
    private val overlay = LocalOverlay()

    private var sources: List<CalDavSource> = emptyList()
    private var syncState: SyncState = SyncState.Idle

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

    override fun snapshot(): CalinoSnapshot = current

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
     * nothing.
     */
    fun setSources(sources: List<CalDavSource>) {
        if (sources.map { it.calendar.url } == this.sources.map { it.calendar.url }) {
            this.sources = sources
            return
        }
        this.sources = sources
        if (sources.isEmpty()) {
            generation++
            fetched = FetchedData()
            lastReadAt = null
            syncState = SyncState.Idle
            cache.evictExcept(emptySet())
            publish()
        } else {
            cache.evictExcept(sources.map { it.calendar.url }.toSet())
            reload(useCache = true)
        }
    }

    /** Refetches without going back to the cache; the data on screen stays put. */
    fun refresh() = reload(useCache = false)

    private fun reload(useCache: Boolean) {
        if (sources.isEmpty()) return
        val token = ++generation
        val start = today().withDayOfMonth(1).minusMonths(windowMonths)
        val end = today().withDayOfMonth(1).plusMonths(windowMonths)
        syncState = SyncState.Loading(cachedAt = lastReadAt.takeUnless { fetched.isEmpty() })
        publish()
        scope.launch {
            if (useCache) {
                val cached = withContext(ioDispatcher) { loadCache(start, end) }
                // Only if nothing newer has arrived, and only if the cache
                // actually held something -- publishing an empty cache would
                // blank a calendar that a concurrent refresh is filling.
                if (token == generation && cached != null && !cached.data.isEmpty()) {
                    fetched = cached.data
                    lastReadAt = cached.fetchedAt
                    syncState = SyncState.Loading(cachedAt = cached.fetchedAt)
                    publish()
                }
            }
            runCatching { loadAll(start, end) }
                .onSuccess { loaded ->
                    if (token != generation) return@onSuccess
                    fetched = loaded.data
                    lastReadAt = Instant.now()
                    // A refetch is the server's answer, so local-only edits
                    // made against the previous answer no longer apply.
                    overlay.clear()
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

        // The oldest read is the honest one to report: saying "read a minute
        // ago" when one collection's copy is a week old would overstate it.
        return oldest?.let { CacheLoad(FetchedData(events, tasks, journals), it) }
    }

    private data class Loaded(val data: FetchedData, val warnings: List<String>)

    private suspend fun loadAll(start: LocalDate, end: LocalDate): Loaded = withContext(ioDispatcher) {
        val events = mutableListOf<CalEvent>()
        val tasks = mutableListOf<CalTask>()
        val journals = mutableListOf<JournalEntry>()
        val warnings = mutableListOf<String>()
        var anySucceeded = false
        var lastError: Throwable? = null

        sources.forEach { source ->
            val name = source.calendar.displayName
            runCatching { fetcher.fetch(source.calendar, source.credentials, start, end) }
                .onSuccess { result ->
                    anySucceeded = true
                    val parsed = mapper.mapAll(
                        resources = result.resources,
                        calendarId = source.calendar.url,
                        color = source.calendar.color,
                        windowStart = start,
                        windowEnd = end,
                    )
                    events += parsed.events
                    tasks += parsed.tasks
                    journals += parsed.journals
                    result.failures.forEach { warnings += "$name -- ${it.describe()}" }
                    // A partial read is still worth keeping: it is what the
                    // next launch would otherwise have to wait for. The
                    // warnings ride along in the sync state either way.
                    saveCache(source, result.resources, start, end)
                }
                .onFailure { error ->
                    lastError = error
                    warnings += "$name -- ${calDavErrorForThrowable(error, source.calendar.url).message}"
                }
        }

        // One unreachable calendar should not blank the others.
        if (!anySucceeded) throw (lastError ?: IllegalStateException("No calendars could be read."))
        Loaded(FetchedData(events, tasks, journals), warnings)
    }

    private fun saveCache(
        source: CalDavSource,
        resources: List<CalendarResource>,
        start: LocalDate,
        end: LocalDate,
    ) {
        cache.save(
            CachedCalendar(
                calendarUrl = source.calendar.url,
                fetchedAt = Instant.now(),
                windowStart = start,
                windowEnd = end,
                resources = resources,
            ),
        )
    }

    // --- writes: local overlay only -------------------------------------------

    override fun addEvent(input: NewEvent): CalEvent =
        overlay.addEvent(input).also { publish() }

    override fun updateEvent(id: String, input: NewEvent): CalEvent =
        overlay.updateEvent(id, input, events()).also { publish() }

    override fun addTask(input: NewTask): CalTask =
        overlay.addTask(input).also { publish() }

    override fun updateTask(id: String, input: NewTask, done: Boolean): CalTask =
        overlay.updateTask(id, input, done, tasks()).also { publish() }

    override fun addJournal(input: NewJournal): JournalEntry =
        overlay.addJournal(input).also { publish() }

    override fun updateJournal(id: String, input: NewJournal): JournalEntry =
        overlay.updateJournal(id, input, journals()).also { publish() }

    override fun deleteJournal(id: String) {
        overlay.deleteJournal(id)
        publish()
    }

    override fun setTaskDone(id: String, done: Boolean): UndoableChange {
        val before = tasks().firstOrNull { it.id == id } ?: error("Unknown task: $id")
        val after = overlay.putTask(before.copy(done = done))
        publish()
        return UndoableChange(
            description = if (done) "Completed ${before.title}" else "Reopened ${before.title}",
            kind = ChangeKind.Task,
            id = id,
            before = ChangeValue.Task(before),
            after = ChangeValue.Task(after),
        )
    }

    override fun rescheduleTask(id: String, due: LocalDate?): UndoableChange {
        val before = tasks().firstOrNull { it.id == id } ?: error("Unknown task: $id")
        val after = overlay.putTask(before.copy(due = due))
        publish()
        return UndoableChange(
            description = "Rescheduled ${before.title}",
            kind = ChangeKind.Task,
            id = id,
            before = ChangeValue.Task(before),
            after = ChangeValue.Task(after),
        )
    }

    override fun undo(change: UndoableChange): Boolean {
        val before = (change.before as? ChangeValue.Task)?.value ?: return false
        val expected = (change.after as? ChangeValue.Task)?.value ?: return false
        // Refuse if the task moved on since; undoing would clobber the newer edit.
        if (tasks().firstOrNull { it.id == change.id } != expected) return false
        overlay.putTask(before)
        publish()
        return true
    }

    // --- composition ----------------------------------------------------------

    private fun compose(): CalinoSnapshot {
        val calendars = sources.map { source ->
            CalinoCalendar(
                id = source.calendar.url,
                name = source.calendar.displayName,
                color = source.calendar.color,
            )
        }
        val events = overlay.applyToEvents(fetched.events)
        val tasks = overlay.applyToTasks(fetched.tasks)
        val journals = overlay.applyToJournals(fetched.journals)
        return CalinoSnapshot(
            events = events,
            tasks = tasks,
            journals = journals,
            revision = current.revisionOrZero() + 1,
            calendars = calendars.ifEmpty { FixtureCalendars },
            categories = (FixtureCategories + events.flatMap { it.categories }).distinct(),
            sync = syncState,
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
    ) {
        fun isEmpty(): Boolean = events.isEmpty() && tasks.isEmpty() && journals.isEmpty()
    }

    companion object {
        /**
         * Months either side of today to request.
         *
         * Bounded because expansion materialises every occurrence, and a rule
         * like `FREQ=DAILY` with no UNTIL is infinite. The window is what
         * bounds it -- see `ICalMapper.parse`.
         */
        const val DefaultWindowMonths = 6L
    }
}
