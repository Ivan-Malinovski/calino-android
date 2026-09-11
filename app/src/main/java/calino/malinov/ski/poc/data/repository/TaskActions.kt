package calino.malinov.ski.poc.data.repository

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.NewEvent
import calino.malinov.ski.poc.data.model.NewTask
import calino.malinov.ski.poc.data.model.RecurrenceEditScope
import calino.malinov.ski.poc.data.model.placementDate
import java.time.LocalDateTime

/** Builds a complete task update so a hierarchy edit cannot drop task fields. */
fun CalTask.asUpdate(parentTaskId: String? = this.parentTaskId, due: java.time.LocalDate? = this.due): NewTask =
    NewTask(
        title = title,
        due = due,
        color = color,
        category = category,
        dueTime = dueTime,
        notes = notes,
        reminder = reminder,
        uid = uid,
        href = href,
        etag = etag,
        calendarId = calendarId,
        parentTaskId = parentTaskId,
    )

suspend fun CalinoRepository.reparentTask(task: CalTask, parentTaskId: String?): WriteResult<CalTask> {
    if (TaskTreeValidation.wouldCycle(tasks(), task.id, parentTaskId)) {
        return WriteResult.Rejected("A task cannot contain itself or one of its subtasks.")
    }
    if (task.parentTaskId == parentTaskId) return WriteResult.Applied(task)
    return updateTask(task.id, task.asUpdate(parentTaskId = parentTaskId), task.done)
}

suspend fun CalinoRepository.duplicateTask(task: CalTask): WriteResult<CalTask> = addTask(
    task.asUpdate(parentTaskId = task.parentTaskId).copy(title = "${task.title} (copy)", uid = null, href = null, etag = null),
)

suspend fun CalinoRepository.duplicateEvent(event: CalEvent): WriteResult<CalEvent> = addEvent(
    NewEvent(
        title = "${event.title} (copy)",
        date = event.placementDate() ?: java.time.LocalDate.now(),
        startTime = event.start?.toLocalTime(),
        durationMinutes = event.durationMinutes,
        allDay = event.allDay,
        color = event.color,
        recurrence = event.recurrence,
        location = event.location,
        notes = event.notes,
        attendees = event.attendees,
        calendarId = event.calendarId,
        availability = event.availability,
        categories = event.categories,
        reminders = event.reminders,
        travelTimeMinutes = event.travelTimeMinutes,
        relatedTo = event.relatedTo,
        url = event.url,
    ),
)

suspend fun CalinoRepository.moveEventToDate(event: CalEvent, date: java.time.LocalDate): WriteResult<CalEvent> {
    if (event.recurrence != null || event.recurrenceId != null || event.recurrenceDate != null) {
        return WriteResult.Rejected("Recurring events cannot be moved from a single occurrence.")
    }
    return updateEvent(
        event.id,
        NewEvent(
            title = event.title,
            date = date,
            startTime = event.start?.toLocalTime(),
            durationMinutes = event.durationMinutes,
            allDay = event.allDay,
            color = event.color,
            location = event.location,
            notes = event.notes,
            attendees = event.attendees,
            calendarId = event.calendarId,
            availability = event.availability,
            categories = event.categories,
            reminders = event.reminders,
            travelTimeMinutes = event.travelTimeMinutes,
            relatedTo = event.relatedTo,
            url = event.url,
            uid = event.uid,
            href = event.href,
            etag = event.etag,
        ),
    )
}

/** Moves a timed event on the day rail while preserving its duration. */
suspend fun CalinoRepository.moveEventToDateTime(event: CalEvent, start: LocalDateTime): WriteResult<CalEvent> {
    if (event.recurrence != null || event.recurrenceId != null || event.recurrenceDate != null) {
        return WriteResult.Rejected("Recurring events cannot be moved from a single occurrence.")
    }
    if (event.allDay || event.start == null) return moveEventToDate(event, start.toLocalDate())
    return updateEvent(
        event.id,
        NewEvent(
            title = event.title,
            date = start.toLocalDate(),
            startTime = start.toLocalTime(),
            durationMinutes = event.durationMinutes,
            allDay = false,
            color = event.color,
            location = event.location,
            notes = event.notes,
            attendees = event.attendees,
            calendarId = event.calendarId,
            availability = event.availability,
            categories = event.categories,
            reminders = event.reminders,
            travelTimeMinutes = event.travelTimeMinutes,
            relatedTo = event.relatedTo,
            url = event.url,
            uid = event.uid,
            href = event.href,
            etag = event.etag,
        ),
    )
}

/** Converts through the repository so fixture and CalDAV surfaces share behavior. */
suspend fun CalinoRepository.convertEventToTask(event: CalEvent): WriteResult<CalTask> {
    if (event.recurrence != null || event.recurrenceId != null || event.recurrenceDate != null) {
        return WriteResult.Rejected("Recurring events must be edited from their detail surface.")
    }
    val date = event.placementDate() ?: return WriteResult.Rejected("That event has no date to use as a due date.")
    val created = addTask(
        NewTask(
            title = event.title,
            due = date,
            color = event.color,
            category = event.categories.firstOrNull(),
            dueTime = event.start?.toLocalTime(),
            notes = event.notes,
            calendarId = event.calendarId,
        ),
    )
    if (created is WriteResult.Applied || created is WriteResult.Queued) {
        deleteEvent(event.id, RecurrenceEditScope.All)
    }
    return created
}

suspend fun CalinoRepository.convertTaskToEvent(task: CalTask): WriteResult<CalEvent> {
    val date = task.due ?: return WriteResult.Rejected("That task has no due date to use as an event date.")
    val created = addEvent(
        NewEvent(
            title = task.title,
            date = date,
            startTime = task.dueTime,
            durationMinutes = 60,
            allDay = task.dueTime == null,
            color = task.color,
            notes = task.notes,
            categories = listOfNotNull(task.category),
            calendarId = task.calendarId,
        ),
    )
    if (created is WriteResult.Applied || created is WriteResult.Queued) deleteTask(task.id)
    return created
}

object TaskTreeValidation {
    fun wouldCycle(tasks: List<CalTask>, taskId: String, parentTaskId: String?): Boolean {
        if (parentTaskId == null) return false
        if (taskId == parentTaskId) return true
        val parentById = tasks.associateBy { it.id }
        val visited = mutableSetOf<String>()
        var current: String? = parentTaskId
        while (current != null && visited.add(current)) {
            if (current == taskId) return true
            current = parentById[current]?.parentTaskId
        }
        return false
    }
}
