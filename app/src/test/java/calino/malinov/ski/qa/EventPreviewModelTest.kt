package calino.malinov.ski.qa

import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.ui.surfaces.eventPreviewDraft
import calino.malinov.ski.ui.surfaces.toNewEvent
import calino.malinov.ski.ui.surfaces.validationError
import calino.malinov.ski.ui.surfaces.finishDate
import calino.malinov.ski.ui.surfaces.withEndDate
import calino.malinov.ski.ui.surfaces.withStartDate
import calino.malinov.ski.ui.surfaces.withEndTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
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

    @Test fun movingTimedEndDateKeepsStartAndEndClockTime() {
        val start = LocalDateTime.of(2026, 10, 8, 15, 15)
        val originalEnd = LocalDateTime.of(2026, 10, 18, 5, 15)
        val spanning = event.copy(start = start, durationMinutes = Duration.between(start, originalEnd).toMinutes().toInt())
        val draft = eventPreviewDraft(spanning)

        assertEquals(LocalDate.of(2026, 10, 18), draft.finishDate)
        val changed = draft.withEndDate(LocalDate.of(2026, 10, 17))
        val saved = changed.toNewEvent(spanning, RecurrenceEditScope.All)
        assertEquals(LocalDate.of(2026, 10, 8), saved.date)
        assertEquals(LocalTime.of(15, 15), saved.startTime)
        assertEquals(Duration.between(start, LocalDateTime.of(2026, 10, 17, 5, 15)).toMinutes().toInt(), saved.durationMinutes)
        assertEquals(LocalDate.of(2026, 10, 17), changed.finishDate)
        val clockChanged = draft.withEndTime(LocalTime.of(6, 15))
        assertEquals(LocalDate.of(2026, 10, 18), clockChanged.finishDate)
        assertEquals(Duration.between(start, LocalDateTime.of(2026, 10, 18, 6, 15)).toMinutes().toInt(), clockChanged.durationMinutes)
    }

    @Test fun movingAllDayEndDateWritesInclusiveLastDay() {
        val spanning = event.copy(start = null, allDay = true, date = LocalDate.of(2026, 5, 24), endDate = LocalDate.of(2026, 5, 26))
        val draft = eventPreviewDraft(spanning)
        assertEquals(LocalDate.of(2026, 5, 26), draft.finishDate)

        val changed = draft.withEndDate(LocalDate.of(2026, 5, 25))
        assertEquals(LocalDate.of(2026, 5, 25), changed.toNewEvent(spanning, RecurrenceEditScope.All).endDate)
        assertEquals(LocalDate.of(2026, 5, 27), draft.withStartDate(LocalDate.of(2026, 5, 25)).finishDate)
        assertEquals("End date must be on or after start date.", draft.withEndDate(LocalDate.of(2026, 5, 23)).validationError())
    }
}
