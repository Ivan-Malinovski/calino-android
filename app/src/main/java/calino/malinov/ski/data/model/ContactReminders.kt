package calino.malinov.ski.data.model

import java.time.LocalDate

const val ContactBirthdayPrefix = "calino:contact:"

fun contactEventMarker(contactId: String, anniversary: Boolean = false): String =
    "$ContactBirthdayPrefix$contactId${if (anniversary) ":anniversary" else ""}"

fun hasContactEvent(contactId: String, events: List<CalEvent>, anniversary: Boolean = false): Boolean =
    events.any { it.url == contactEventMarker(contactId, anniversary) }

fun contactReminderEvent(
    contact: Contact,
    date: LocalDate,
    calendarId: String,
    anniversary: Boolean = false,
): NewEvent = NewEvent(
    title = if (anniversary) "💍 ${contact.derivedDisplayName()}'s anniversary" else "🎂 ${contact.derivedDisplayName()}'s birthday",
    date = date,
    allDay = true,
    calendarId = calendarId,
    notes = if (anniversary) "Anniversary of ${contact.derivedDisplayName()}" else "Birthday of ${contact.derivedDisplayName()}",
    recurrence = "FREQ=YEARLY;INTERVAL=1",
    categories = listOf(if (anniversary) "anniversary" else "birthday"),
    url = contactEventMarker(contact.id, anniversary),
)
