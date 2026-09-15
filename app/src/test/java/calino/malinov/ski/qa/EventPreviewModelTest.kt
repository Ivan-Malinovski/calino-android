package calino.malinov.ski.qa

import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.ui.surfaces.eventPreviewDraft
import calino.malinov.ski.ui.surfaces.toNewEvent
import calino.malinov.ski.ui.surfaces.validationError
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventPreviewModelTest {
    private val event = CalEvent(
        id = "event", title = "Climbing", color = 0xff448866,
        start = LocalDateTime.of(2026, 5, 19, 10, 0), durationMinutes = 90,
        recurrence = "FREQ=WEEKLY", location = "Wall", notes = "Bring shoes",
        attendees = listOf(Attendee("Alex", "alex@example.com")), calendarId = "work",
        availability = Availability.Free, categories = listOf("sport"),
        reminders = listOf(Reminder(15)), travelTimeMinutes = 20, relatedTo = listOf("task"),
        url = "calino:local", uid = "uid", href = "https://dav/event.ics", etag = "etag", sequence = 4,
    )

    @Test fun conversionPreservesUntouchedFieldsAndScope() {
        val input = eventPreviewDraft(event).copy(title = "Bouldering").toNewEvent(event, RecurrenceEditScope.Future)
        assertEquals("Bouldering", input.title)
        assertEquals(event.recurrence, input.recurrence)
        assertEquals(event.attendees, input.attendees)
        assertEquals(event.reminders, input.reminders)
        assertEquals(event.calendarId, input.calendarId)
        assertEquals(event.categories, input.categories)
        assertEquals(event.uid, input.uid)
        assertEquals(event.href, input.href)
        assertEquals(event.etag, input.etag)
        assertEquals(RecurrenceEditScope.Future, input.recurrenceScope)
    }

    @Test fun draftDirtyComparisonAndValidationAreValueBased() {
        val draft = eventPreviewDraft(event)
        assertEquals(draft, eventPreviewDraft(event))
        assertEquals("Enter an event title.", draft.copy(title = " ").validationError())
        assertEquals("End time must be after start time.", draft.copy(durationMinutes = 0).validationError())
        assertNull(draft.validationError())
    }
}
