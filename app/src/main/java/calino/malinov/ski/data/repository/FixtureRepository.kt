package calino.malinov.ski.data.repository

import androidx.compose.runtime.mutableStateOf
import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.ContactAddressBook
import calino.malinov.ski.data.model.ContactEmail
import calino.malinov.ski.data.model.ContactPhone
import calino.malinov.ski.data.model.ContactPhoneType
import calino.malinov.ski.data.model.ContactType
import calino.malinov.ski.data.model.NewContact
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.NewJournal
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.placementDate
import calino.malinov.ski.data.model.upcomingOccurrences
import java.io.Closeable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** A writable calendar the editor can file a record under. */
data class CalinoCalendar(
    val id: String,
    val name: String,
    val color: Long,
    /**
     * True when the server grants no write privilege on the collection, or when
     * it is a subscription. Surfaced so the editor can decline before a PUT is
     * attempted rather than after it is refused. Fixtures are always writable.
     */
    val readOnly: Boolean = false,
    /**
     * The component kinds the collection accepts, upper-case (`VEVENT`,
     * `VTODO`, `VJOURNAL`). Empty means the server did not say, which RFC 4791
     * defines as "all of them". Writing a VTODO into a VEVENT-only collection is
     * a 403 on some servers and silent data loss on others.
     */
    val components: Set<String> = emptySet(),
    /** Display visibility is independent from whether the collection is synced. */
    val visible: Boolean = true,
    /** Whether VTODOs from this collection appear on calendar views. */
    val showTasksInViews: Boolean = true,
    /**
     * Whether this collection's VALARMs may fire. Writable CalDAV defaults
     * to true. Webcal subscriptions default to false: an overlay is for
     * seeing events, not being paged by the publisher's alarms.
     */
    val notifyReminders: Boolean = true,
)

/**
 * The ids whose records a calendar surface may show.
 *
 * Written out identically in four places before this existed -- the calendar
 * root, the agenda, the reminder planner and the widget -- and the two that are
 * off by a rule are exactly the kind of divergence that makes a reminder fire
 * for something the grid does not draw. The Compose call sites additionally
 * subtract the fixture-only in-memory hidden sets, which are not durable and so
 * cannot be part of a rule a receiver or a widget also has to apply.
 */
fun visibleCalendarIds(calendars: List<CalinoCalendar>): Set<String> =
    calendars.asSequence().filter { it.visible }.map { it.id }.toSet()

/** Visible calendars whose alarms the user has opted to hear. */
fun reminderCalendarIds(calendars: List<CalinoCalendar>): Set<String> =
    calendars.asSequence().filter { it.visible && it.notifyReminders }.map { it.id }.toSet()

/** As [visibleCalendarIds], further restricted to collections that show tasks. */
fun taskCalendarIds(calendars: List<CalinoCalendar>): Set<String> =
    calendars.asSequence().filter { it.visible && it.showTasksInViews }.map { it.id }.toSet()

/** Whether this collection will accept a component of [component]. */
fun CalinoCalendar.accepts(component: String): Boolean =
    components.isEmpty() || component.uppercase() in components

/**
 * How current the snapshot is.
 *
 * The fixture repository is always [Idle]: it has nothing to load and nothing
 * that can fail. Only the CalDAV-backed repository moves through the other
 * states, so every existing consumer can ignore this field.
 */
sealed interface SyncState {
    data object Idle : SyncState
    /**
     * A read is in flight. [cachedAt] is when the copy currently on screen was
     * read from the server, or null when there is nothing to show yet -- the
     * difference between "refreshing" and "blank until this returns".
     */
    data class Loading(val cachedAt: java.time.Instant? = null) : SyncState
    /**
     * [warnings] name what could not be read, in words a person can act on.
     * A read that is short of the whole calendar must say which part is
     * missing -- reporting it as complete is the worse failure.
     */
    data class Ready(
        val fetchedAt: java.time.Instant,
        val warnings: List<String> = emptyList(),
    ) : SyncState {
        val partial: Boolean get() = warnings.isNotEmpty()
    }
    data class Failed(val message: String, val hadPreviousData: Boolean = false) : SyncState
}

data class CalinoSnapshot(
    val events: List<CalEvent>,
    val tasks: List<CalTask>,
    val journals: List<JournalEntry>,
    val contacts: List<Contact> = emptyList(),
    val addressBooks: List<ContactAddressBook> = emptyList(),
    val revision: Long = 0,
    val calendars: List<CalinoCalendar> = FixtureCalendars,
    val categories: List<String> = FixtureCategories,
    val sync: SyncState = SyncState.Idle,
    /** Per-record state for writes that are waiting for, or failed against, DAV. */
    val writeStatus: Map<String, RecordWriteStatus> = emptyMap(),
)

enum class RecordWriteState { Pending, Failed }

data class RecordWriteStatus(
    val state: RecordWriteState,
    val reason: String? = null,
)

/** The fixture calendar set. Settings and the editor read the same list. */
val FixtureCalendars: List<CalinoCalendar> = listOf(
    CalinoCalendar("personal", "Personal", 0xFFC2697F),
    CalinoCalendar("work", "Work", 0xFF5B7FB5),
    CalinoCalendar("travel", "Travel", 0xFFBF944E),
)

val FixtureCategories: List<String> = listOf("Work", "Personal", "Travel", "Admin", "Health", "Friends", "Sports")

interface CalinoRepository {
    fun snapshot(): CalinoSnapshot
    fun events(): List<CalEvent> = snapshot().events
    fun tasks(): List<CalTask> = snapshot().tasks
    fun journals(): List<JournalEntry> = snapshot().journals
    fun contacts(): List<Contact> = snapshot().contacts
    fun observe(listener: (CalinoSnapshot) -> Unit): Closeable
    suspend fun addEvent(input: NewEvent): WriteResult<CalEvent>
    suspend fun updateEvent(id: String, input: NewEvent): WriteResult<CalEvent>
    /**
     * [occurrenceDate] is the occurrence the person was actually looking at,
     * and it is what makes [RecurrenceEditScope.This] and
     * [RecurrenceEditScope.Future] answerable at all: a fixture-backed series
     * is one record drawn on many days, so the id alone names the whole
     * series and nothing narrower. Null falls back to the series anchor.
     */
    suspend fun deleteEvent(
        id: String,
        scope: RecurrenceEditScope = RecurrenceEditScope.All,
        occurrenceDate: LocalDate? = null,
    ): WriteResult<Unit>
    suspend fun addTask(input: NewTask): WriteResult<CalTask>
    suspend fun updateTask(id: String, input: NewTask, done: Boolean): WriteResult<CalTask>
    suspend fun deleteTask(id: String, scope: RecurrenceEditScope = RecurrenceEditScope.All): WriteResult<Unit>
    suspend fun addJournal(input: NewJournal): WriteResult<JournalEntry>
    suspend fun updateJournal(id: String, input: NewJournal): WriteResult<JournalEntry>
    suspend fun deleteJournal(id: String): WriteResult<Unit>
    suspend fun addContact(input: NewContact): WriteResult<Contact>
    suspend fun updateContact(id: String, input: NewContact): WriteResult<Contact>
    suspend fun deleteContact(id: String): WriteResult<Unit>
    /** Contact-derived reminder events intentionally remain local-only in v1. */
    fun addLocalEvent(input: NewEvent): CalEvent
    suspend fun setTaskDone(id: String, done: Boolean): WriteResult<UndoableChange>
    suspend fun rescheduleTask(id: String, due: LocalDate?): WriteResult<UndoableChange>
    suspend fun undo(change: UndoableChange): WriteResult<Unit>
}

/** Outcome of a repository mutation; the live DAV repository may return [Queued]. */
sealed interface WriteResult<out T> {
    data class Applied<T>(val record: T) : WriteResult<T>
    data class Queued<T>(val record: T) : WriteResult<T>
    data class Rejected(val reason: String) : WriteResult<Nothing>
}

enum class ChangeKind { Task }

sealed interface ChangeValue {
    data class Task(val value: CalTask) : ChangeValue
    data object Missing : ChangeValue
}

/** A one-step reversible local mutation, intentionally not a production history model. */
data class UndoableChange internal constructor(
    val description: String,
    internal val kind: ChangeKind,
    internal val id: String,
    internal val before: ChangeValue,
    internal val after: ChangeValue,
)

/** Fixture-only local repository. It has no CalDAV, accounts, or network path. */
class FixtureRepository : CalinoRepository {
    companion object {
        val FixtureDate: LocalDate = LocalDate.of(2026, 5, 18)
    }

    private val state = mutableStateOf(
        CalinoSnapshot(
            events = fixtureEvents(),
            tasks = fixtureTasks(),
            journals = fixtureJournals(),
            contacts = fixtureContacts(),
            addressBooks = FixtureAddressBooks,
        ),
    )
    private val listeners = CopyOnWriteArrayList<(CalinoSnapshot) -> Unit>()
    private var nextEventId = 1
    private var nextTaskId = 1
    private var nextJournalId = 1
    private var nextContactId = 1

    override fun snapshot(): CalinoSnapshot = state.value

    override fun observe(listener: (CalinoSnapshot) -> Unit): Closeable {
        listeners += listener
        listener(snapshot())
        return Closeable { listeners.remove(listener) }
    }

    override suspend fun addEvent(input: NewEvent): WriteResult<CalEvent> {
        val event = eventFromInput("local-event-${nextEventId++}", input)
        update { it.copy(events = it.events + event) }
        return WriteResult.Applied(event)
    }

    override suspend fun updateEvent(id: String, input: NewEvent): WriteResult<CalEvent> {
        snapshot().events.firstOrNull { it.id == id } ?: error("Unknown fixture event: $id")
        val event = eventFromInput(id, input)
        update { current ->
            current.copy(events = current.events.map { if (it.id == id) event else it })
        }
        return WriteResult.Applied(event)
    }

    override suspend fun deleteEvent(
        id: String,
        scope: RecurrenceEditScope,
        occurrenceDate: LocalDate?,
    ): WriteResult<Unit> {
        val current = snapshot().events.firstOrNull { it.id == id } ?: return WriteResult.Applied(Unit)
        val rewritten = when {
            // A one-off has no narrower reading of "delete this", and neither
            // does a series the caller asked to remove entirely.
            current.recurrence == null || scope == RecurrenceEditScope.All -> null
            scope == RecurrenceEditScope.This -> current.withoutOccurrence(occurrenceDate)
            else -> current.endedBefore(occurrenceDate)
        }
        update { snapshot ->
            snapshot.copy(
                events = if (rewritten == null) {
                    snapshot.events.filterNot { it.id == id }
                } else {
                    snapshot.events.map { if (it.id == id) rewritten else it }
                },
            )
        }
        return WriteResult.Applied(Unit)
    }

    private fun eventFromInput(id: String, input: NewEvent): CalEvent = CalEvent(
            id = id,
            title = input.title,
            color = input.color,
            start = if (input.allDay) null else input.startTime?.let { input.date.atTime(it) },
            durationMinutes = if (input.allDay) null else input.durationMinutes ?: 60,
            allDay = input.allDay,
            recurrence = input.recurrence,
            location = input.location,
            notes = input.notes,
            attendees = input.attendees,
            calendarId = input.calendarId,
            date = if (input.allDay) input.date else null,
            endDate = if (input.allDay) input.endDate else null,
            availability = input.availability,
            categories = input.categories,
            reminders = input.reminders,
            travelTimeMinutes = input.travelTimeMinutes,
            relatedTo = input.relatedTo,
            url = input.url,
            uid = input.uid,
        )

    override suspend fun addTask(input: NewTask): WriteResult<CalTask> {
        recurringTaskValidation(input, tasks())?.let { return WriteResult.Rejected(it) }
        val task = taskFromInput("local-task-${nextTaskId++}", input, done = false)
        update { it.copy(tasks = it.tasks + task) }
        return WriteResult.Applied(task)
    }

    override suspend fun updateTask(id: String, input: NewTask, done: Boolean): WriteResult<CalTask> {
        recurringTaskValidation(input, tasks(), id)?.let { return WriteResult.Rejected(it) }
        task(id)
        val updated = taskFromInput(id, input, done)
        replaceTask(updated)
        return WriteResult.Applied(updated)
    }

    override suspend fun deleteTask(id: String, scope: RecurrenceEditScope): WriteResult<Unit> {
        if (snapshot().tasks.none { it.id == id }) return WriteResult.Applied(Unit)
        update { current -> current.copy(tasks = current.tasks.filterNot { it.id == id }) }
        return WriteResult.Applied(Unit)
    }

    private fun taskFromInput(id: String, input: NewTask, done: Boolean): CalTask = CalTask(
        id = id,
        title = input.title,
        color = input.color,
        due = input.due,
        done = done || input.percentComplete >= 100 || input.status.equals("COMPLETED", ignoreCase = true),
        priority = input.priority,
        percentComplete = if (done || input.percentComplete >= 100 || input.status.equals("COMPLETED", ignoreCase = true)) 100 else input.percentComplete.coerceIn(0, 99),
        status = if (done || input.percentComplete >= 100 || input.status.equals("COMPLETED", ignoreCase = true)) "COMPLETED" else input.status ?: if (input.percentComplete > 0) "IN-PROCESS" else "NEEDS-ACTION",
        completedAt = if (done || input.percentComplete >= 100 || input.status.equals("COMPLETED", ignoreCase = true)) input.completedAt ?: java.time.Instant.now() else null,
        category = input.category,
        dueTime = input.dueTime,
        notes = input.notes,
        reminder = input.reminder,
        calendarId = input.calendarId,
        parentTaskId = input.parentTaskId,
        recurrence = input.recurrence,
        recurrenceId = input.recurrenceId,
        recurrenceDate = input.recurrenceDate,
        sequence = input.sequence,
        recurrenceChanged = input.recurrenceChanged,
        recurrenceScope = input.recurrenceScope,
    )

    override suspend fun addJournal(input: NewJournal): WriteResult<JournalEntry> {
        val journal = JournalEntry(
            id = "local-journal-${nextJournalId++}",
            date = input.date,
            title = input.title,
            body = input.body,
        )
        update { it.copy(journals = it.journals + journal) }
        return WriteResult.Applied(journal)
    }

    override suspend fun updateJournal(id: String, input: NewJournal): WriteResult<JournalEntry> {
        snapshot().journals.firstOrNull { it.id == id } ?: error("Unknown fixture journal: $id")
        val journal = JournalEntry(id = id, date = input.date, title = input.title, body = input.body)
        update { current -> current.copy(journals = current.journals.map { if (it.id == id) journal else it }) }
        return WriteResult.Applied(journal)
    }

    override suspend fun deleteJournal(id: String): WriteResult<Unit> {
        if (snapshot().journals.none { it.id == id }) return WriteResult.Applied(Unit)
        update { current -> current.copy(journals = current.journals.filterNot { it.id == id }) }
        return WriteResult.Applied(Unit)
    }

    override suspend fun addContact(input: NewContact): WriteResult<Contact> {
        val contact = contactFrom("local-contact-${nextContactId++}", input)
        update { it.copy(contacts = it.contacts + contact) }
        return WriteResult.Applied(contact)
    }

    override suspend fun updateContact(id: String, input: NewContact): WriteResult<Contact> {
        val current = snapshot().contacts.firstOrNull { it.id == id } ?: error("Unknown contact: $id")
        val contact = contactFrom(id, input).copy(uid = current.uid, href = current.href, etag = current.etag)
        update { state -> state.copy(contacts = state.contacts.map { if (it.id == id) contact else it }) }
        return WriteResult.Applied(contact)
    }

    override suspend fun deleteContact(id: String): WriteResult<Unit> {
        if (snapshot().contacts.none { it.id == id }) return WriteResult.Applied(Unit)
        val markers = setOf("calino:contact:$id", "calino:contact:$id:anniversary")
        update { state ->
            state.copy(
                contacts = state.contacts.filterNot { it.id == id },
                events = state.events.filterNot { it.url in markers },
            )
        }
        return WriteResult.Applied(Unit)
    }

    override fun addLocalEvent(input: NewEvent): CalEvent {
        val event = eventFromInput("local-event-${nextEventId++}", input)
        update { it.copy(events = it.events + event) }
        return event
    }

    private fun contactFrom(id: String, input: NewContact): Contact = Contact(
        id = id,
        addressBookId = input.addressBookId,
        displayName = input.displayName.trim(),
        givenName = input.givenName.trim(),
        familyName = input.familyName.trim(),
        organization = input.organization.trim(),
        department = input.department.trim(),
        title = input.title.trim(),
        nickname = input.nickname.trim(),
        emails = input.emails,
        phones = input.phones,
        urls = input.urls,
        birthday = input.birthday,
        anniversary = input.anniversary,
        note = input.note.trim(),
        categories = input.categories,
    )

    override suspend fun setTaskDone(id: String, done: Boolean): WriteResult<UndoableChange> {
        val current = task(id)
        val changed = current.copy(
            done = done,
            percentComplete = if (done) 100 else 0,
            status = if (done) "COMPLETED" else "NEEDS-ACTION",
            completedAt = if (done) java.time.Instant.now() else null,
        )
        val change = UndoableChange(
            description = if (done) "Completed ${current.title}" else "Reopened ${current.title}",
            kind = ChangeKind.Task,
            id = id,
            before = ChangeValue.Task(current),
            after = ChangeValue.Task(changed),
        )
        replaceTask(changed)
        return WriteResult.Applied(change)
    }

    override suspend fun rescheduleTask(id: String, due: LocalDate?): WriteResult<UndoableChange> {
        val current = task(id)
        val changed = current.copy(due = due)
        val destination = due?.toString() ?: "no date"
        val change = UndoableChange(
            description = "Rescheduled ${current.title} to $destination",
            kind = ChangeKind.Task,
            id = id,
            before = ChangeValue.Task(current),
            after = ChangeValue.Task(changed),
        )
        replaceTask(changed)
        return WriteResult.Applied(change)
    }

    override suspend fun undo(change: UndoableChange): WriteResult<Unit> {
        if (change.kind != ChangeKind.Task) return WriteResult.Rejected("That change cannot be undone.")
        val current = snapshot().tasks.firstOrNull { it.id == change.id }
            ?: return WriteResult.Rejected("That task is no longer available.")
        val expected = (change.after as? ChangeValue.Task)?.value
            ?: return WriteResult.Rejected("That change cannot be undone.")
        if (current != expected) return WriteResult.Rejected("That task changed, so the change was not undone.")
        val before = (change.before as? ChangeValue.Task)?.value
            ?: return WriteResult.Rejected("That change cannot be undone.")
        replaceTask(before)
        return WriteResult.Applied(Unit)
    }

    private fun task(id: String): CalTask = snapshot().tasks.firstOrNull { it.id == id }
        ?: error("Unknown fixture task: $id")

    private fun replaceTask(task: CalTask) = update { current ->
        current.copy(tasks = current.tasks.map { if (it.id == task.id) task else it })
    }

    /**
     * Restore the frozen May 2026 dataset.
     *
     * The container is process-scoped and instrumented tests share one process,
     * so a test that completes a task or files an event would otherwise be
     * visible to every test that runs after it. This is the seam the device
     * harness resets through; nothing in the app calls it.
     *
     * The revision keeps climbing rather than returning to zero: observers key
     * off it, and handing them a number they have already seen would let a
     * stale snapshot look current.
     */
    fun resetToFixtures() {
        nextEventId = 1
        nextTaskId = 1
        nextJournalId = 1
        nextContactId = 1
        update {
            CalinoSnapshot(
                events = fixtureEvents(),
                tasks = fixtureTasks(),
                journals = fixtureJournals(),
                contacts = fixtureContacts(),
                addressBooks = FixtureAddressBooks,
                revision = it.revision,
            )
        }
    }

    private fun update(transform: (CalinoSnapshot) -> CalinoSnapshot) {
        val current = snapshot()
        val next = transform(current).copy(revision = current.revision + 1)
        state.value = next
        listeners.forEach { it(next) }
    }
}

private const val Rose = 0xFFC2697F
private const val Blue = 0xFF5B7FB5
private const val Green = 0xFF5D9A78
private const val Amber = 0xFFBF944E
private const val Plum = 0xFF8A6AA8

val FixtureAddressBooks: List<ContactAddressBook> = listOf(
    ContactAddressBook(
        id = "fixture-contacts",
        accountId = "fixture",
        url = "fixture://contacts",
        name = "Neighbors",
        description = "A few people nearby",
    ),
)

private fun timed(
    id: String,
    title: String,
    date: LocalDate,
    color: Long,
    time: LocalTime,
    durationMinutes: Int,
    recurrence: String? = null,
    location: String? = null,
    notes: String? = null,
    attendees: List<Attendee> = emptyList(),
    calendarId: String = "personal",
) = CalEvent(
    id = id,
    title = title,
    color = color,
    start = LocalDateTime.of(date, time),
    durationMinutes = durationMinutes,
    recurrence = recurrence,
    location = location,
    notes = notes,
    attendees = attendees,
    calendarId = calendarId,
)

private fun allDay(
    id: String,
    title: String,
    date: LocalDate,
    color: Long,
    calendarId: String = "personal",
    endDate: LocalDate? = null,
    recurrence: String? = null,
) = CalEvent(
    id = id,
    title = title,
    color = color,
    start = null,
    durationMinutes = null,
    allDay = true,
    calendarId = calendarId,
    date = date,
    endDate = endDate,
    recurrence = recurrence,
)

private fun fixtureEvents(): List<CalEvent> {
    val may = { day: Int -> LocalDate.of(2026, 5, day) }
    val april = { day: Int -> LocalDate.of(2026, 4, day) }
    val june = { day: Int -> LocalDate.of(2026, 6, day) }
    val weeklyGym = "FREQ=WEEKLY;BYDAY=TU;UNTIL=20260630T235959Z"
    val weeklyCall = "FREQ=WEEKLY;BYDAY=TH;UNTIL=20260630T235959Z"
    return listOf(
        // Existing POC identity and May 18 records stay stable.
        timed(
            "evt-design", "Design review", FixtureRepository.FixtureDate, Blue, LocalTime.of(10, 0), 60,
            "FREQ=WEEKLY;BYDAY=MO;UNTIL=20260630T235959Z", "Studio",
            notes = """## Review checklist

- [x] Share the prototype
- [ ] Capture decisions
- [ ] Send the follow-up

Open the [design brief](https://example.com/calino-design-brief) before the meeting.""",
            attendees = listOf(Attendee("Maya", "maya@example.com"), Attendee("Ivo", "ivo@example.com")),
            calendarId = "work",
        ),
        timed(
            "evt-lunch", "Lunch with Maya", FixtureRepository.FixtureDate, Rose, LocalTime.of(12, 30), 90,
            location = "Café Lumen",
            notes = "**Booking:** [View the reservation](https://example.com/reservation)",
            calendarId = "personal",
        ),
        allDay("evt-flight", "Flight to Berlin", may(24), Amber, "travel"),

        // Adjacent dates make the horizontal month pager useful at its edges.
        timed("evt-apr-planning", "April planning", april(27), Plum, LocalTime.of(11, 0), 60, calendarId = "work"),
        timed("evt-apr-retro", "April retrospective", april(30), Rose, LocalTime.of(15, 0), 90, location = "Studio", calendarId = "work"),
        timed("evt-jun-kickoff", "June kickoff", june(1), Blue, LocalTime.of(9, 0), 60, calendarId = "work"),
        timed("evt-jun-call", "Client Call · Acme Corp", june(4), Rose, LocalTime.of(11, 0), 30, weeklyCall, "Google Meet", calendarId = "work"),
        allDay("evt-jun-holiday", "Summer holiday", june(5), Amber, "personal"),
        timed("evt-jun-brunch", "Brunch with Friends", june(6), Blue, LocalTime.of(12, 0), 120, location = "Cafe Rouge"),

        // The resolved May handoff dataset. Only the first event in a recurring
        // series owns the RRULE; already-materialized records remain one-offs so
        // the series anchor cannot render them a second time.
        timed("evt-may-kickoff", "March Kickoff Meeting", may(1), Rose, LocalTime.of(9, 0), 60, calendarId = "work"),
        timed("evt-dentist", "Dentist Appointment", may(5), Rose, LocalTime.of(8, 30), 60, location = "North Clinic"),
        timed("evt-gym-05", "Gym Session", may(5), Blue, LocalTime.of(7, 0), 60, weeklyGym),
        timed("evt-client-05-07", "Client Call · Acme Corp", may(7), Rose, LocalTime.of(11, 0), 30, location = "Google Meet", calendarId = "work"),
        allDay("evt-daylight-saving", "Daylight Saving Time", may(8), Amber),
        timed("evt-dinner", "Dinner with Sarah", may(10), Rose, LocalTime.of(19, 0), 120, location = "Café Lumen"),
        allDay("evt-birthday", "Tom's Birthday", may(12), Rose),
        timed("evt-retreat", "Work Retreat", may(14), Rose, LocalTime.of(9, 0), 480, location = "Harbor House", calendarId = "work"),
        timed("evt-client-05-14", "Client Call · Acme Corp", may(14), Rose, LocalTime.of(11, 0), 30, location = "Google Meet", calendarId = "work"),
        timed("evt-gym-05-14", "Gym Session", may(14), Blue, LocalTime.of(7, 0), 60),
        timed("evt-lunch-mom", "Lunch with Mom", may(15), Rose, LocalTime.of(12, 30), 90, location = "The Garden"),
        allDay("evt-design-sprint", "Design Sprint", may(16), Plum, "work", endDate = may(17)),
        // A recurring multi-day all-day span: the emulator's coverage of the
        // day-2..n-of-a-later-occurrence bug that `occurrenceStartCovering`
        // fixes. Its master is April 24; the band must still show May 22-23.
        allDay(
            "evt-studio-residency", "Studio Residency", april(24), Plum, "work",
            endDate = april(25), recurrence = "FREQ=WEEKLY;BYDAY=FR;UNTIL=20260630T235959Z",
        ),
        timed("evt-st-patricks-lunch", "St. Patrick's Lunch", may(17), Amber, LocalTime.of(12, 0), 90, location = "The Green Room"),
        // A CONFERENCE link only: it adds a Join action without changing any
        // text the calendar surfaces show.
        timed("evt-code-review", "Code Review Session", FixtureRepository.FixtureDate, Rose, LocalTime.of(14, 0), 60, calendarId = "work")
            .copy(conferenceUrl = "https://meet.google.com/cal-inoc-rev"),
        allDay("evt-national-day", "National Day · No Work", may(20), Amber, "work"),
        timed("evt-project-review", "Project Review", may(21), Rose, LocalTime.of(14, 0), 60, location = "Conference Room C", calendarId = "work"),
        timed("evt-client-05-21", "Client Call · Acme Corp", may(21), Rose, LocalTime.of(11, 0), 30, location = "Google Meet", calendarId = "work"),
        timed("evt-gym-05-21", "Gym Session", may(21), Blue, LocalTime.of(7, 0), 60),
        timed("evt-planning-workshop", "Product Planning Workshop", may(22), Plum, LocalTime.of(10, 0), 120, location = "Studio", calendarId = "work"),
        allDay("evt-family-vacation", "Family Vacation", may(24), Amber, "travel", endDate = may(26)),
        timed("evt-doctor", "Doctor Checkup", may(24), Rose, LocalTime.of(9, 30), 60, location = "North Clinic"),
        timed("evt-brunch", "Brunch with Friends", may(27), Blue, LocalTime.of(12, 0), 120, location = "Cafe Rouge"),
        timed("evt-yoga", "Yoga Class", may(27), Blue, LocalTime.of(18, 0), 60, "FREQ=WEEKLY;BYDAY=WE;UNTIL=20260630T235959Z"),
        timed("evt-client-05-28", "Client Call · Acme Corp", may(28), Rose, LocalTime.of(11, 0), 30, location = "Google Meet", calendarId = "work"),
        timed("evt-gym-05-28", "Gym Session", may(28), Blue, LocalTime.of(7, 0), 60),
        timed("evt-manager", "One-on-One with Manager", may(29), Rose, LocalTime.of(16, 0), 30, "FREQ=WEEKLY;BYDAY=FR;UNTIL=20260630T235959Z", calendarId = "work"),
        timed("evt-retrospective", "Q1 Retrospective", may(30), Rose, LocalTime.of(15, 0), 90, location = "Conference Room C", calendarId = "work"),
        timed("evt-report", "Monthly Report Deadline", may(31), Rose, LocalTime.of(11, 0), 60, calendarId = "work"),
    )
}

private fun fixtureTasks(): List<CalTask> {
    val day = FixtureRepository.FixtureDate
    return listOf(
        CalTask(
            "task-inbox", "Review calendar notes", Green, day, category = "Work",
            notes = "Read the [CalDAV notes](https://example.com/caldav-notes) and add comments.",
        ),
        CalTask("task-overdue", "Send itinerary", Amber, day.minusDays(2), category = "Travel", priority = 1),
        CalTask(
            "task-buy", "Buy flowers", Rose, null, category = "Personal",
            notes = """### Groceries

- [ ] Tulips
- [ ] Oat milk
- [x] Coffee beans""",
        ),
        CalTask("task-done", "Book accommodation", Blue, day.minusDays(1), done = true, category = "Travel"),
        CalTask("task-renew", "Renew car insurance", Rose, LocalDate.of(2026, 5, 28), category = "Admin", priority = 6),
        CalTask("task-dentist", "Schedule dentist appointment", Blue, LocalDate.of(2026, 5, 25)),
        CalTask("task-documentation", "Update documentation", Green, LocalDate.of(2026, 5, 20), done = true, category = "Work"),
        CalTask("task-weekend", "Plan weekend trip", Green, day, done = true),
        CalTask("task-goals", "Review Q1 goals", Plum, LocalDate.of(2026, 5, 15), category = "Work"),
        CalTask("task-expense", "Submit expense report", Rose, null, category = "Finance"),
        // Two days after their parent, so the sample carries the case the
        // stand-in parent row exists for: subtasks on a day the parent is not.
        CalTask("task-ferry", "Book the ferry", Green, day.plusDays(2), category = "Travel", parentTaskId = "task-weekend"),
        CalTask("task-pack", "Pack the wetsuits", Green, day.plusDays(2), parentTaskId = "task-weekend"),
    )
}

private fun fixtureJournals(): List<JournalEntry> {
    fun entry(id: String, date: LocalDate, title: String, body: String) = JournalEntry(id, date, title, body)
    return listOf(
        entry("journal-1", FixtureRepository.FixtureDate, "A clear Monday", "A small, useful beginning to the week."),
        entry("journal-may-08", LocalDate.of(2026, 5, 8), "", "A quiet day to reset and notice what is working."),
        entry("journal-may-15", LocalDate.of(2026, 5, 15), "Lunch with Mom", "Good food, familiar stories, and time away from the screen."),
        entry("journal-may-16", LocalDate.of(2026, 5, 16), "Design Sprint — Day 1", "The first sketches made the problem feel smaller."),
        entry("journal-may-22", LocalDate.of(2026, 5, 22), "Product Planning Workshop", "The team left with three decisions and one useful question."),
        entry("journal-may-27", LocalDate.of(2026, 5, 27), "Brunch with Friends", "A sunny table and no rush to leave."),
        entry("journal-may-30", LocalDate.of(2026, 5, 30), "Q1 Retrospective", "Keep the small rituals; remove the needless handoffs."),
    )
}

private fun fixtureContacts(): List<Contact> = listOf(
    Contact(
        id = "contact-ada",
        uid = "contact-ada",
        addressBookId = "fixture-contacts",
        displayName = "Ada Lovelace",
        givenName = "Ada",
        familyName = "Lovelace",
        organization = "Analytical Neighbors",
        emails = listOf(ContactEmail("ada@example.com", ContactType.Work, isPrimary = true)),
        phones = listOf(ContactPhone("+45 20 26 18 15", ContactPhoneType.Cell, isPrimary = true)),
        birthday = LocalDate.of(1988, 5, 24),
        categories = listOf("Design", "Neighbors"),
        note = "Brings excellent cake to the courtyard table.",
    ),
    Contact(
        id = "contact-chen",
        uid = "contact-chen",
        addressBookId = "fixture-contacts",
        displayName = "Chen Wei",
        givenName = "Chen",
        familyName = "Wei",
        organization = "North Block Co-op",
        emails = listOf(ContactEmail("chen@example.com", ContactType.Home, isPrimary = true)),
        phones = listOf(ContactPhone("+45 31 44 08 77", ContactPhoneType.Home)),
        categories = listOf("Neighbors"),
    ),
    Contact(
        id = "contact-rooftop",
        uid = "contact-rooftop",
        addressBookId = "fixture-contacts",
        displayName = "# Rooftop group",
        isGroup = true,
        memberUids = listOf("contact-ada", "contact-chen"),
        categories = listOf("Neighbors"),
    ),
    Contact(
        id = "contact-no-photo",
        uid = "contact-no-photo",
        addressBookId = "fixture-contacts",
        givenName = "Mira",
        familyName = "Sol",
        displayName = "",
        organization = "Garden House",
        emails = listOf(ContactEmail("mira@example.com", ContactType.Other, isPrimary = true)),
    ),
)

/**
 * The same series with one occurrence taken out of it.
 *
 * Two different edits, because an occurrence is excluded two different ways.
 * Any occurrence after the first is an EXDATE, which the shared recurrence
 * engine already understands inside the rule text. The *first* one cannot be:
 * `occurrenceStartCovering` answers "yes" for the anchor date before it ever
 * consults the rule, so an EXDATE on the anchor is drawn anyway. That one is
 * removed by moving the series onto its next occurrence instead.
 *
 * Null means the series has nothing left and the record should simply go.
 */
private fun CalEvent.withoutOccurrence(occurrenceDate: LocalDate?): CalEvent? {
    val rule = recurrence ?: return null
    val anchor = placementDate() ?: return null
    val target = occurrenceDate ?: anchor
    if (target != anchor) {
        val stamp = if (allDay) {
            target.format(FixtureExceptionDate)
        } else {
            target.atTime(start?.toLocalTime() ?: LocalTime.MIDNIGHT).format(FixtureExceptionDateTime)
        }
        return copy(recurrence = "$rule;EXDATE=$stamp")
    }
    val next = upcomingOccurrences(anchor, limit = 1).firstOrNull() ?: return null
    val shift = ChronoUnit.DAYS.between(anchor, next)
    return copy(
        start = start?.plusDays(shift),
        date = date?.plusDays(shift),
        endDate = endDate?.plusDays(shift),
    )
}

/**
 * The same series, stopped before [occurrenceDate].
 *
 * Cutting at the anchor leaves no occurrence at all, so that is a deletion
 * rather than a rule with an impossible UNTIL. Any existing UNTIL is replaced:
 * two of them in one rule is not a narrower series, it is a broken one.
 */
private fun CalEvent.endedBefore(occurrenceDate: LocalDate?): CalEvent? {
    val rule = recurrence ?: return null
    val anchor = placementDate() ?: return null
    val target = occurrenceDate ?: anchor
    if (!target.isAfter(anchor)) return null
    val until = target.minusDays(1).format(FixtureExceptionDate)
    val kept = rule.split(';')
        .filter { it.isNotBlank() }
        .filterNot { it.uppercase(Locale.US).startsWith("UNTIL=") }
    return copy(recurrence = (kept + "UNTIL=${until}T235959Z").joinToString(";"))
}

private val FixtureExceptionDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
private val FixtureExceptionDateTime: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)
