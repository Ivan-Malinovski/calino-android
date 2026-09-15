package calino.malinov.ski.ui.surfaces

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import java.time.LocalDate
import java.time.LocalTime

data class EventPreviewDraft(
    val title: String,
    val date: LocalDate,
    val startTime: LocalTime?,
    val durationMinutes: Int?,
    val location: String,
    val description: String,
)

fun eventPreviewDraft(event: CalEvent) = EventPreviewDraft(
    title = event.title,
    date = event.start?.toLocalDate() ?: event.date ?: error("Event ${event.id} has no date"),
    startTime = event.start?.toLocalTime(),
    durationMinutes = event.durationMinutes,
    location = event.location.orEmpty(),
    description = event.notes.orEmpty(),
)

fun EventPreviewDraft.validationError(): String? = when {
    title.isBlank() -> "Enter an event title."
    startTime != null && (durationMinutes ?: 0) <= 0 -> "End time must be after start time."
    else -> null
}

fun EventPreviewDraft.toNewEvent(event: CalEvent, scope: RecurrenceEditScope): NewEvent = NewEvent(
    title = title.trim(),
    date = date,
    startTime = startTime,
    durationMinutes = if (startTime == null) null else durationMinutes,
    allDay = startTime == null,
    color = event.color,
    recurrence = event.recurrence,
    location = location.trim().ifEmpty { null },
    notes = description.trim().ifEmpty { null },
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
    recurrenceId = event.recurrenceId,
    recurrenceDate = event.recurrenceDate,
    sequence = event.sequence,
    recurrenceChanged = false,
    recurrenceScope = scope,
)
