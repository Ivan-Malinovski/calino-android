package calino.malinov.ski.poc.data.repository

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.NewEvent
import calino.malinov.ski.poc.data.model.NewJournal
import calino.malinov.ski.poc.data.model.NewTask

/**
 * Unsynced local edits layered over server data.
 *
 * The app's write path predates the network layer and this step is read-only,
 * so an edit is held here rather than sent. Making the write methods throw
 * instead was rejected: the editor and the task list call them on live paths,
 * and a throw would crash rather than degrade.
 *
 * The overlay is dropped on refetch -- the server's answer supersedes edits
 * made against the previous one.
 */
internal class LocalOverlay {

    private val events = LinkedHashMap<String, CalEvent>()
    private val tasks = LinkedHashMap<String, CalTask>()
    private val journals = LinkedHashMap<String, JournalEntry>()
    private val deletedJournals = mutableSetOf<String>()

    private var nextId = 1

    fun clear() {
        events.clear()
        tasks.clear()
        journals.clear()
        deletedJournals.clear()
    }

    fun applyToEvents(base: List<CalEvent>): List<CalEvent> = merge(base, events) { it.id }

    fun applyToTasks(base: List<CalTask>): List<CalTask> = merge(base, tasks) { it.id }

    fun applyToJournals(base: List<JournalEntry>): List<JournalEntry> =
        merge(base, journals) { it.id }.filterNot { it.id in deletedJournals }

    /** Server records first, with local edits replacing matches in place. */
    private fun <T> merge(base: List<T>, edits: Map<String, T>, id: (T) -> String): List<T> {
        if (edits.isEmpty()) return base
        val replaced = base.map { edits[id(it)] ?: it }
        val seen = base.map(id).toSet()
        return replaced + edits.values.filterNot { id(it) in seen }
    }

    fun addEvent(input: NewEvent): CalEvent =
        eventFrom("local-event-${nextId++}", input).also { events[it.id] = it }

    fun updateEvent(id: String, input: NewEvent, existing: List<CalEvent>): CalEvent {
        val current = events[id] ?: existing.firstOrNull { it.id == id } ?: error("Unknown event: $id")
        return eventFrom(id, input)
            // Server identity survives a local edit so a later write path can
            // still address the resource it came from.
            .copy(uid = current.uid, href = current.href, etag = current.etag)
            .also { events[id] = it }
    }

    fun addTask(input: NewTask): CalTask =
        taskFrom("local-task-${nextId++}", input, done = false).also { tasks[it.id] = it }

    fun updateTask(id: String, input: NewTask, done: Boolean, existing: List<CalTask>): CalTask {
        val current = tasks[id] ?: existing.firstOrNull { it.id == id } ?: error("Unknown task: $id")
        return taskFrom(id, input, done)
            .copy(uid = current.uid, href = current.href, etag = current.etag)
            .also { tasks[id] = it }
    }

    fun putTask(task: CalTask): CalTask = task.also { tasks[it.id] = it }

    fun addJournal(input: NewJournal): JournalEntry =
        JournalEntry(
            id = "local-journal-${nextId++}",
            date = input.date,
            title = input.title,
            body = input.body,
        ).also { journals[it.id] = it }

    fun updateJournal(id: String, input: NewJournal, existing: List<JournalEntry>): JournalEntry {
        val current = journals[id] ?: existing.firstOrNull { it.id == id } ?: error("Unknown journal: $id")
        return current.copy(date = input.date, title = input.title, body = input.body)
            .also { journals[id] = it }
    }

    fun deleteJournal(id: String) {
        journals.remove(id)
        deletedJournals += id
    }

    private fun eventFrom(id: String, input: NewEvent): CalEvent = CalEvent(
        id = id,
        title = input.title,
        color = input.color,
        start = if (input.allDay) null else input.date.atTime(input.startTime ?: java.time.LocalTime.of(9, 0)),
        durationMinutes = if (input.allDay) null else (input.durationMinutes ?: 60),
        allDay = input.allDay,
        date = if (input.allDay) input.date else null,
        recurrence = input.recurrence,
        location = input.location,
        notes = input.notes,
        attendees = input.attendees,
        calendarId = input.calendarId,
        availability = input.availability,
        categories = input.categories,
        reminders = input.reminders,
        travelTimeMinutes = input.travelTimeMinutes,
        relatedTo = input.relatedTo,
    )

    private fun taskFrom(id: String, input: NewTask, done: Boolean): CalTask = CalTask(
        id = id,
        title = input.title,
        color = input.color,
        due = input.due,
        dueTime = input.dueTime,
        done = done,
        category = input.category,
        notes = input.notes,
        reminder = input.reminder,
    )
}
