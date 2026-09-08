package calino.malinov.ski.poc.data.repository

import calino.malinov.ski.poc.data.caldav.CalDavFetcher
import calino.malinov.ski.poc.data.caldav.DavCredentials
import calino.malinov.ski.poc.data.caldav.DiscoveredCalendar
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
 */
class CalDavRepository(
    private val fetcher: CalDavFetcher,
    private val scope: CoroutineScope,
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

    /** Points the repository at a new set of collections and refetches. */
    fun setSources(sources: List<CalDavSource>) {
        this.sources = sources
        if (sources.isEmpty()) {
            fetched = FetchedData()
            syncState = SyncState.Idle
            publish()
        } else {
            refresh()
        }
    }

    fun refresh() {
        if (sources.isEmpty()) return
        syncState = SyncState.Loading
        publish()
        scope.launch {
            val start = today().withDayOfMonth(1).minusMonths(windowMonths)
            val end = today().withDayOfMonth(1).plusMonths(windowMonths)
            runCatching { loadAll(start, end) }
                .onSuccess { loaded ->
                    fetched = loaded.data
                    // A refetch is the server's answer, so local-only edits
                    // made against the previous answer no longer apply.
                    overlay.clear()
                    syncState = SyncState.Ready(Instant.now(), warnings = loaded.warnings)
                    publish()
                }
                .onFailure { error ->
                    val message = calDavErrorForThrowable(error, sources.firstOrNull()?.calendar?.url.orEmpty()).message
                    // Keep whatever is already on screen. Blanking the calendar
                    // because a refresh failed is worse than showing stale data
                    // alongside a clear error.
                    syncState = SyncState.Failed(message, hadPreviousData = !fetched.isEmpty())
                    publish()
                }
        }
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
                    events += result.events
                    tasks += result.tasks
                    journals += result.journals
                    result.failures.forEach { warnings += "$name -- ${it.describe()}" }
                    if (result.expandUnsupported) {
                        warnings += "$name -- the server did not expand repeating events, " +
                            "so a repeating event shows only on its first date."
                    }
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
         * Bounded because server-side expansion materialises every occurrence:
         * an unbounded window over a daily series is unbounded rows.
         */
        const val DefaultWindowMonths = 6L
    }
}
