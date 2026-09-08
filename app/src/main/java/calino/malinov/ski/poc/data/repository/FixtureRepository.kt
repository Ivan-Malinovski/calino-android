package calino.malinov.ski.poc.data.repository

import androidx.compose.runtime.mutableStateOf
import calino.malinov.ski.poc.data.model.Attendee
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.NewEvent
import calino.malinov.ski.poc.data.model.NewJournal
import calino.malinov.ski.poc.data.model.NewTask
import java.io.Closeable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList

data class CalinoSnapshot(
    val events: List<CalEvent>,
    val tasks: List<CalTask>,
    val journals: List<JournalEntry>,
    val revision: Long = 0,
)

interface CalinoRepository {
    fun snapshot(): CalinoSnapshot
    fun events(): List<CalEvent> = snapshot().events
    fun tasks(): List<CalTask> = snapshot().tasks
    fun journals(): List<JournalEntry> = snapshot().journals
    fun observe(listener: (CalinoSnapshot) -> Unit): Closeable
    fun addEvent(input: NewEvent): CalEvent
    fun updateEvent(id: String, input: NewEvent): CalEvent
    fun addTask(input: NewTask): CalTask
    fun updateTask(id: String, input: NewTask, done: Boolean): CalTask
    fun addJournal(input: NewJournal): JournalEntry
    fun updateJournal(id: String, input: NewJournal): JournalEntry
    fun deleteJournal(id: String)
    fun setTaskDone(id: String, done: Boolean): UndoableChange
    fun rescheduleTask(id: String, due: LocalDate?): UndoableChange
    fun undo(change: UndoableChange): Boolean
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
        ),
    )
    private val listeners = CopyOnWriteArrayList<(CalinoSnapshot) -> Unit>()
    private var nextEventId = 1
    private var nextTaskId = 1
    private var nextJournalId = 1

    override fun snapshot(): CalinoSnapshot = state.value

    override fun observe(listener: (CalinoSnapshot) -> Unit): Closeable {
        listeners += listener
        listener(snapshot())
        return Closeable { listeners.remove(listener) }
    }

    override fun addEvent(input: NewEvent): CalEvent {
        val event = eventFromInput("local-event-${nextEventId++}", input)
        update { it.copy(events = it.events + event) }
        return event
    }

    override fun updateEvent(id: String, input: NewEvent): CalEvent {
        snapshot().events.firstOrNull { it.id == id } ?: error("Unknown fixture event: $id")
        val event = eventFromInput(id, input)
        update { current ->
            current.copy(events = current.events.map { if (it.id == id) event else it })
        }
        return event
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
        )

    override fun addTask(input: NewTask): CalTask {
        val task = CalTask(
            id = "local-task-${nextTaskId++}",
            title = input.title,
            color = input.color,
            due = input.due,
            category = input.category,
        )
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    override fun updateTask(id: String, input: NewTask, done: Boolean): CalTask {
        task(id)
        val updated = CalTask(
            id = id,
            title = input.title,
            color = input.color,
            due = input.due,
            done = done,
            category = input.category,
        )
        replaceTask(updated)
        return updated
    }

    override fun addJournal(input: NewJournal): JournalEntry {
        val journal = JournalEntry(
            id = "local-journal-${nextJournalId++}",
            date = input.date,
            title = input.title,
            body = input.body,
        )
        update { it.copy(journals = it.journals + journal) }
        return journal
    }

    override fun updateJournal(id: String, input: NewJournal): JournalEntry {
        snapshot().journals.firstOrNull { it.id == id } ?: error("Unknown fixture journal: $id")
        val journal = JournalEntry(id = id, date = input.date, title = input.title, body = input.body)
        update { current -> current.copy(journals = current.journals.map { if (it.id == id) journal else it }) }
        return journal
    }

    override fun deleteJournal(id: String) {
        snapshot().journals.firstOrNull { it.id == id } ?: return
        update { current -> current.copy(journals = current.journals.filterNot { it.id == id }) }
    }

    override fun setTaskDone(id: String, done: Boolean): UndoableChange {
        val current = task(id)
        val changed = current.copy(done = done)
        val change = UndoableChange(
            description = if (done) "Completed ${current.title}" else "Reopened ${current.title}",
            kind = ChangeKind.Task,
            id = id,
            before = ChangeValue.Task(current),
            after = ChangeValue.Task(changed),
        )
        replaceTask(changed)
        return change
    }

    override fun rescheduleTask(id: String, due: LocalDate?): UndoableChange {
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
        return change
    }

    override fun undo(change: UndoableChange): Boolean {
        if (change.kind != ChangeKind.Task) return false
        val current = snapshot().tasks.firstOrNull { it.id == change.id } ?: return false
        val expected = (change.after as? ChangeValue.Task)?.value ?: return false
        if (current != expected) return false
        val before = (change.before as? ChangeValue.Task)?.value ?: return false
        replaceTask(before)
        return true
    }

    private fun task(id: String): CalTask = snapshot().tasks.firstOrNull { it.id == id }
        ?: error("Unknown fixture task: $id")

    private fun replaceTask(task: CalTask) = update { current ->
        current.copy(tasks = current.tasks.map { if (it.id == task.id) task else it })
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

private fun allDay(id: String, title: String, date: LocalDate, color: Long, calendarId: String = "personal") = CalEvent(
    id = id,
    title = title,
    color = color,
    start = null,
    durationMinutes = null,
    allDay = true,
    calendarId = calendarId,
    date = date,
)

private fun fixtureEvents(): List<CalEvent> {
    val may = { day: Int -> LocalDate.of(2026, 5, day) }
    val april = { day: Int -> LocalDate.of(2026, 4, day) }
    val june = { day: Int -> LocalDate.of(2026, 6, day) }
    val weeklyGym = "FREQ=WEEKLY;BYDAY=TU;UNTIL=20260630T235959Z"
    val weeklyCall = "FREQ=WEEKLY;BYDAY=TH;UNTIL=20260630T235959Z"
    return listOf(
        // Existing POC identity and May 18 records stay stable.
        timed("evt-design", "Design review", FixtureRepository.FixtureDate, Blue, LocalTime.of(10, 0), 60, "FREQ=WEEKLY;BYDAY=MO;UNTIL=20260630T235959Z", "Studio", attendees = listOf(Attendee("Maya", "maya@example.com"), Attendee("Ivo", "ivo@example.com")), calendarId = "work"),
        timed("evt-lunch", "Lunch with Maya", FixtureRepository.FixtureDate, Rose, LocalTime.of(12, 30), 90, location = "Café Lumen", calendarId = "personal"),
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
        allDay("evt-design-sprint-16", "Design Sprint", may(16), Plum, "work"),
        allDay("evt-design-sprint-17", "Design Sprint", may(17), Plum, "work"),
        timed("evt-st-patricks-lunch", "St. Patrick's Lunch", may(17), Amber, LocalTime.of(12, 0), 90, location = "The Green Room"),
        timed("evt-code-review", "Code Review Session", FixtureRepository.FixtureDate, Rose, LocalTime.of(14, 0), 60, calendarId = "work"),
        allDay("evt-national-day", "National Day · No Work", may(20), Amber, "work"),
        timed("evt-project-review", "Project Review", may(21), Rose, LocalTime.of(14, 0), 60, location = "Conference Room C", calendarId = "work"),
        timed("evt-client-05-21", "Client Call · Acme Corp", may(21), Rose, LocalTime.of(11, 0), 30, location = "Google Meet", calendarId = "work"),
        timed("evt-gym-05-21", "Gym Session", may(21), Blue, LocalTime.of(7, 0), 60),
        timed("evt-planning-workshop", "Product Planning Workshop", may(22), Plum, LocalTime.of(10, 0), 120, location = "Studio", calendarId = "work"),
        allDay("evt-family-vacation-24", "Family Vacation", may(24), Amber, "travel"),
        timed("evt-doctor", "Doctor Checkup", may(24), Rose, LocalTime.of(9, 30), 60, location = "North Clinic"),
        allDay("evt-family-vacation-25", "Family Vacation", may(25), Amber, "travel"),
        allDay("evt-family-vacation-26", "Family Vacation", may(26), Amber, "travel"),
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
        CalTask("task-inbox", "Review calendar notes", Green, day, category = "Work"),
        CalTask("task-overdue", "Send itinerary", Amber, day.minusDays(2), category = "Travel"),
        CalTask("task-buy", "Buy flowers", Rose, null, category = "Personal"),
        CalTask("task-done", "Book accommodation", Blue, day.minusDays(1), done = true, category = "Travel"),
        CalTask("task-renew", "Renew car insurance", Rose, LocalDate.of(2026, 5, 28), category = "Admin"),
        CalTask("task-dentist", "Schedule dentist appointment", Blue, LocalDate.of(2026, 5, 25)),
        CalTask("task-documentation", "Update documentation", Green, LocalDate.of(2026, 5, 20), done = true, category = "Work"),
        CalTask("task-weekend", "Plan weekend trip", Green, day, done = true),
        CalTask("task-goals", "Review Q1 goals", Plum, LocalDate.of(2026, 5, 15), category = "Work"),
        CalTask("task-expense", "Submit expense report", Rose, null, category = "Finance"),
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
