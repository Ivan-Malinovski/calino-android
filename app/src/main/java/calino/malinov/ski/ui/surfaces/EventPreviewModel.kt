package calino.malinov.ski.ui.surfaces

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.util.CalinoZones
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
    zoneId = event.zoneId,
    endZoneId = event.endZoneId,
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

/**
 * "09:00 – 09:30 in New York" under a detail card whose times are in the
 * device zone, or "10:00 Copenhagen → 12:00 New York" for an event whose end
 * has its own zone. Null when every end already reads the same on the device's
 * clock -- Berlin and Paris are different ids and the same numbers.
 */
fun foreignZoneCaption(
    start: LocalDateTime?,
    end: LocalDateTime?,
    zoneId: String?,
    endZoneId: String?,
    device: ZoneId,
    format: (LocalTime) -> String,
): String? {
    start ?: return null
    val startZone = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return null
    val endZone = endZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: startZone
    val startAt = start.atZone(device)
    val endAt = (end ?: start).atZone(device)
    if (CalinoZones.sameOffset(startZone, device, startAt.toInstant()) &&
        CalinoZones.sameOffset(endZone, device, endAt.toInstant())
    ) return null
    val localStart = startAt.withZoneSameInstant(startZone).toLocalTime()
    val localEnd = endAt.withZoneSameInstant(endZone).toLocalTime()
    return if (endZone == startZone) {
        "${format(localStart)} – ${format(localEnd)} in ${CalinoZones.city(startZone)}"
    } else {
        "${format(localStart)} ${CalinoZones.city(startZone)} → ${format(localEnd)} ${CalinoZones.city(endZone)}"
    }
}
