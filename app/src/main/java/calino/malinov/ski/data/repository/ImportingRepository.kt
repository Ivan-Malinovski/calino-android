package calino.malinov.ski.data.repository

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.NewContact
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.NewJournal
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.platform.AndroidCalendarId
import calino.malinov.ski.platform.AndroidCalendarSource
import java.io.Closeable
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * A repository that shows a second, read-only source beside the real one.
 *
 * Calino's repository has always been a single source: fixtures when no
 * account is connected, CalDAV when one is. Showing the device's own Google
 * and Exchange calendars needs a second origin alongside whichever of those
 * is active, and this is the seam where the two meet. Everything above it --
 * the widget bridge, the reminder bridge, the projection bridge, search, and
 * the whole UI -- consumes a [CalinoRepository] and needs no change, because
 * this is one.
 *
 * Two properties are deliberate and easy to lose in a later edit:
 *
 * - **Writes belong to the primary.** Every mutation delegates, except one
 *   naming an imported calendar, which is refused. That branch is the
 *   write-back seam: today it returns a rejection, and a later version fills
 *   it with provider operations without anything above here changing.
 * - **It is only in the graph when it is earning its place.** The container
 *   unwraps it entirely when nothing is imported, so a person who never opts
 *   in pays nothing at all -- no wrapper, no observer, no provider query.
 *
 * See the "Reading the device's calendars" section of
 * `docs/calendar-provider.md`.
 */
class ImportingRepository(
    private val primary: CalinoRepository,
    /**
     * The latest read of the device's calendars.
     *
     * Supplied rather than fetched, so this class stays testable without a
     * `Context` and so the provider is queried on the container's schedule
     * rather than on whoever happens to call [snapshot].
     */
    imported: AndroidCalendarSource.Import = AndroidCalendarSource.Import(),
) : CalinoRepository {

    private val listeners = CopyOnWriteArrayList<(CalinoSnapshot) -> Unit>()

    /**
     * Our own counter, not a sum of the two sources'.
     *
     * Every `remember(snapshot.revision)` in the UI depends on this changing
     * whenever anything changed. Adding the two inputs' revisions would not
     * do: the primary's restarts at zero when the container swaps fixture for
     * CalDAV, and a revision that goes backwards stalls recomposition on
     * exactly the frame where the data changed most.
     */
    private val revision = AtomicLong(0)

    /** The last read of the device's calendars, for a rebuild to carry over. */
    @Volatile
    var imported: AndroidCalendarSource.Import = imported
        private set

    @Volatile
    private var current: CalinoSnapshot = merge(primary.snapshot())

    private val upstream: Closeable = primary.observe { snapshot ->
        publish(merge(snapshot))
    }

    override fun snapshot(): CalinoSnapshot = current

    /**
     * Registers [listener] and hands it the current snapshot immediately.
     *
     * The synchronous first emission is the same contract [CalDavRepository]
     * documents, and for the same reason: the Compose bridge seeds its state
     * from it, and deferring it renders one empty frame.
     */
    override fun observe(listener: (CalinoSnapshot) -> Unit): Closeable {
        listeners += listener
        listener(current)
        return Closeable { listeners.remove(listener) }
    }

    /** Replaces the imported half and republishes. */
    fun setImported(next: AndroidCalendarSource.Import) {
        imported = next
        publish(merge(primary.snapshot()))
    }

    /** True when [candidate] is the primary this already wraps. */
    fun observes(candidate: CalinoRepository): Boolean = candidate === primary

    /** Detaches from the primary. Called when the container unwraps this. */
    fun close() {
        upstream.close()
        listeners.clear()
    }

    private fun merge(base: CalinoSnapshot): CalinoSnapshot {
        val extra = imported
        if (extra.isEmpty) {
            // Still our own revision: the primary's numbering is not ours to
            // pass through, and a caller comparing revisions across a change
            // in the imported set must see movement.
            return base.copy(revision = revision.incrementAndGet())
        }
        return base.copy(
            events = base.events + extra.events,
            // Imported calendars go last so the editor's "first writable
            // calendar" defaults keep picking a real one.
            calendars = base.calendars + extra.calendars,
            revision = revision.incrementAndGet(),
        )
        // tasks, journals, contacts, addressBooks, categories, sync and
        // writeStatus are the primary's untouched. The provider has no table
        // for the first four, and the last three describe the primary's
        // relationship with a server that imported rows have nothing to do
        // with.
    }

    private fun publish(next: CalinoSnapshot) {
        current = next
        listeners.forEach { it(next) }
    }

    // ------------------------------------------------------------- the seam

    /**
     * Why a write to an imported calendar is refused.
     *
     * Shown to a person, so it says what is true rather than what failed: the
     * owning app is authoritative for these rows, and Calino is looking at
     * them. In a later version this branch performs the write instead.
     */
    private fun rejection(): WriteResult.Rejected = WriteResult.Rejected(
        "That calendar belongs to another app on this device. Calino can show " +
            "it, but not change it.",
    )

    private fun isImported(id: String?) = id != null && AndroidCalendarId.isImported(id)

    /** True when [id] names a record we imported rather than one we own. */
    private fun importedRecord(id: String) =
        AndroidCalendarId.isImported(id) || current.events.any {
            it.id == id && AndroidCalendarId.isImported(it.calendarId)
        }

    override suspend fun addEvent(input: NewEvent): WriteResult<CalEvent> =
        if (isImported(input.calendarId)) rejection() else primary.addEvent(input)

    override suspend fun updateEvent(id: String, input: NewEvent): WriteResult<CalEvent> =
        if (importedRecord(id) || isImported(input.calendarId)) {
            rejection()
        } else {
            primary.updateEvent(id, input)
        }

    override suspend fun deleteEvent(
        id: String,
        scope: RecurrenceEditScope,
        occurrenceDate: LocalDate?,
    ): WriteResult<Unit> =
        if (importedRecord(id)) rejection() else primary.deleteEvent(id, scope, occurrenceDate)

    override fun addLocalEvent(input: NewEvent): CalEvent = primary.addLocalEvent(input)

    // Tasks, journals and contacts have no provider counterpart, so there is
    // nothing to intercept -- they pass straight through.

    override suspend fun addTask(input: NewTask): WriteResult<CalTask> = primary.addTask(input)

    override suspend fun updateTask(id: String, input: NewTask, done: Boolean): WriteResult<CalTask> =
        primary.updateTask(id, input, done)

    override suspend fun deleteTask(id: String, scope: RecurrenceEditScope): WriteResult<Unit> =
        primary.deleteTask(id, scope)

    override suspend fun addJournal(input: NewJournal): WriteResult<JournalEntry> =
        primary.addJournal(input)

    override suspend fun updateJournal(id: String, input: NewJournal): WriteResult<JournalEntry> =
        primary.updateJournal(id, input)

    override suspend fun deleteJournal(id: String): WriteResult<Unit> = primary.deleteJournal(id)

    override suspend fun addContact(input: NewContact): WriteResult<Contact> =
        primary.addContact(input)

    override suspend fun updateContact(id: String, input: NewContact): WriteResult<Contact> =
        primary.updateContact(id, input)

    override suspend fun deleteContact(id: String): WriteResult<Unit> = primary.deleteContact(id)

    override suspend fun setTaskDone(id: String, done: Boolean): WriteResult<UndoableChange> =
        primary.setTaskDone(id, done)

    override suspend fun rescheduleTask(id: String, due: LocalDate?): WriteResult<UndoableChange> =
        primary.rescheduleTask(id, due)

    override suspend fun undo(change: UndoableChange): WriteResult<Unit> = primary.undo(change)
}
