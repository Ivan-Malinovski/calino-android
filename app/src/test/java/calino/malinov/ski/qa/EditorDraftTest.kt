package calino.malinov.ski.qa

import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.EditorField
import calino.malinov.ski.data.model.RecurrenceFreq
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.model.applyInput
import calino.malinov.ski.data.model.blankEditorDraft
import calino.malinov.ski.data.model.editorDraftFor
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.model.recurrenceDaysOf
import calino.malinov.ski.data.model.recurrenceFreqOf
import calino.malinov.ski.data.model.recurrenceRule
import calino.malinov.ski.data.parser.PocQuickAddKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDraftTest {
    private val fixtureDate = LocalDate.of(2026, 5, 18)

    private fun eventDraft(input: String = "") =
        blankEditorDraft(PocQuickAddKind.Event, fixtureDate, input)

    @Test
    fun blankDraft_hasNoTitle_andCannotSave() {
        val draft = eventDraft()

        assertEquals("", draft.title)
        assertFalse(draft.canSave())
    }

    @Test
    fun typedLine_fillsTheFormBelowIt() {
        val draft = eventDraft("Lunch with Maya tomorrow at 12:30 for 90 min at Café Lumen")

        assertEquals("Lunch with Maya", draft.title)
        assertEquals(fixtureDate.plusDays(1), draft.date)
        assertEquals(LocalTime.of(12, 30), draft.startTime)
        assertEquals(90, draft.durationMinutes)
        assertEquals("Café Lumen", draft.location)
        assertTrue(draft.canSave())
    }

    @Test
    fun handEditedField_survivesFurtherTyping() {
        val edited = eventDraft("Standup at 9:00")
            .let { it.copy(startTime = LocalTime.of(11, 0), touched = it.touched + EditorField.Time) }

        val retyped = edited.applyInput("Standup at 9:00 with the team", fixtureDate)

        assertEquals(LocalTime.of(11, 0), retyped.startTime)
        assertEquals("Standup with the team", retyped.title)
    }

    @Test
    fun untouchedField_stillFollowsTheTypedLine() {
        val retyped = eventDraft("Standup at 9:00").applyInput("Standup at 10:00", fixtureDate)

        assertEquals(LocalTime.of(10, 0), retyped.startTime)
    }

    @Test
    fun endTime_isCarriedAsADuration() {
        val draft = eventDraft("Review at 9:00")
            .withEnd(fixtureDate, LocalTime.of(10, 30))

        assertEquals(90, draft.durationMinutes)
        assertEquals(LocalTime.of(10, 30), draft.endTime)
        assertEquals(fixtureDate, draft.endDate)
    }

    @Test
    fun endPastMidnight_movesTheEndDate() {
        val draft = eventDraft("Party at 22:00").copy(durationMinutes = 240)

        assertEquals(fixtureDate.plusDays(1), draft.endDate)
        assertEquals(LocalTime.of(2, 0), draft.endTime)
    }

    @Test
    fun zeroLengthEvent_cannotSave() {
        assertFalse(eventDraft("Review at 9:00").copy(durationMinutes = 0).canSave())
    }

    @Test
    fun toNewEvent_carriesEveryEditorField() {
        val draft = eventDraft("Design review at 10:00").copy(
            availability = Availability.Free,
            recurrence = recurrenceRule(RecurrenceFreq.Weekly, setOf(DayOfWeek.MONDAY)),
            calendarId = "work",
            categories = listOf("Work", "Travel"),
            description = "  Bring the sketches.  ",
            reminders = listOf(Reminder(10)),
            travelTimeMinutes = 15,
            relatedTo = listOf("task-inbox"),
            attendees = listOf(Attendee("Maya", "maya@example.com")),
        )

        val input = draft.toNewEvent()

        assertEquals(Availability.Free, input.availability)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", input.recurrence)
        assertEquals("work", input.calendarId)
        assertEquals(listOf("Work", "Travel"), input.categories)
        assertEquals("Bring the sketches.", input.notes)
        assertEquals(listOf(Reminder(10)), input.reminders)
        assertEquals(15, input.travelTimeMinutes)
        assertEquals(listOf("task-inbox"), input.relatedTo)
        assertEquals(listOf(Attendee("Maya", "maya@example.com")), input.attendees)
        assertFalse(input.allDay)
    }

    @Test
    fun allDayEvent_dropsItsTime() {
        val input = eventDraft("Flight to Berlin at 10:00").copy(allDay = true).toNewEvent()

        assertTrue(input.allDay)
        assertNull(input.startTime)
        assertNull(input.durationMinutes)
    }

    @Test
    fun editingAnEvent_roundTripsUnchanged() {
        val event = CalEvent(
            id = "evt-round",
            title = "Design review",
            color = 0xFF5B7FB5,
            start = fixtureDate.atTime(10, 0),
            durationMinutes = 60,
            recurrence = "FREQ=WEEKLY;BYDAY=MO",
            location = "Studio",
            notes = "Bring sketches",
            attendees = listOf(Attendee("Maya", "maya@example.com")),
            calendarId = "work",
            availability = Availability.Free,
            categories = listOf("Work"),
            reminders = listOf(Reminder(30)),
            travelTimeMinutes = 15,
            relatedTo = listOf("task-inbox"),
        )

        val draft = editorDraftFor(event)
        val input = draft.toNewEvent()

        assertEquals("evt-round", draft.editingId)
        assertEquals(event.title, input.title)
        assertEquals(fixtureDate, input.date)
        assertEquals(LocalTime.of(10, 0), input.startTime)
        assertEquals(60, input.durationMinutes)
        assertEquals(event.recurrence, input.recurrence)
        assertEquals(event.location, input.location)
        assertEquals(event.notes, input.notes)
        assertEquals(event.attendees, input.attendees)
        assertEquals(event.calendarId, input.calendarId)
        assertEquals(event.availability, input.availability)
        assertEquals(event.categories, input.categories)
        assertEquals(event.reminders, input.reminders)
        assertEquals(event.travelTimeMinutes, input.travelTimeMinutes)
        assertEquals(event.relatedTo, input.relatedTo)
    }

    @Test
    fun editingASavedRecord_keepsItsFieldsWhenTheTitleChanges() {
        val event = CalEvent(
            id = "evt-seed",
            title = "Design review",
            color = 0xFF5B7FB5,
            start = fixtureDate.atTime(10, 0),
            durationMinutes = 60,
            location = "Studio",
            calendarId = "work",
        )

        val retitled = editorDraftFor(event).applyInput("Design review round two", fixtureDate)

        assertEquals("Design review round two", retitled.title)
        assertEquals(LocalTime.of(10, 0), retitled.startTime)
        assertEquals("Studio", retitled.location)
    }

    @Test
    fun taskDraft_mapsItsOwnFields() {
        val draft = blankEditorDraft(PocQuickAddKind.Task, fixtureDate, "Send itinerary tomorrow at 9:00").copy(
            categories = listOf("Travel"),
            description = "Attach the tickets",
            reminders = listOf(Reminder(60)),
        )

        val input = draft.toNewTask()

        assertEquals("Send itinerary", input.title)
        assertEquals(fixtureDate.plusDays(1), input.due)
        assertEquals(LocalTime.of(9, 0), input.dueTime)
        assertEquals("Travel", input.category)
        assertEquals("Attach the tickets", input.notes)
        assertEquals(Reminder(60), input.reminder)
    }

    @Test
    fun editingATask_roundTripsUnchanged() {
        val task = CalTask(
            id = "task-round",
            title = "Send itinerary",
            color = 0xFFBF944E,
            due = fixtureDate,
            category = "Travel",
            dueTime = LocalTime.of(9, 0),
            notes = "Attach the tickets",
            reminder = Reminder(30),
        )

        val input = editorDraftFor(task, fixtureDate).toNewTask()

        assertEquals(task.title, input.title)
        assertEquals(task.due, input.due)
        assertEquals(task.dueTime, input.dueTime)
        assertEquals(task.category, input.category)
        assertEquals(task.notes, input.notes)
        assertEquals(task.reminder, input.reminder)
    }

    @Test
    fun journalDraft_keepsItsBodySeparateFromTheTitle() {
        val draft = blankEditorDraft(PocQuickAddKind.Journal, fixtureDate, "A clear Monday")
            .copy(body = "  A small, useful beginning.  ")

        val input = draft.toNewJournal()

        assertEquals("A clear Monday", input.title)
        assertEquals("A small, useful beginning.", input.body)
        assertEquals(fixtureDate, input.date)
    }

    @Test
    fun recurrenceRule_roundTripsThroughItsHelpers() {
        val rule = recurrenceRule(
            RecurrenceFreq.Weekly,
            setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY),
            LocalDate.of(2026, 6, 30),
        )

        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260630T235959Z", rule)
        assertEquals(RecurrenceFreq.Weekly, recurrenceFreqOf(rule))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), recurrenceDaysOf(rule))
    }

    @Test
    fun editorRecurrence_rendersOnTheDaysItNames() {
        val event = CalEvent(
            id = "evt-recur",
            title = "Gym",
            color = 0xFF5B7FB5,
            start = fixtureDate.atTime(7, 0),
            durationMinutes = 60,
            recurrence = recurrenceRule(RecurrenceFreq.Weekly, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            calendarId = "personal",
        )

        assertTrue(event.occursOn(fixtureDate))
        assertTrue(event.occursOn(LocalDate.of(2026, 5, 20)))
        assertFalse(event.occursOn(LocalDate.of(2026, 5, 21)))
    }

    @Test
    fun dailyMonthlyAndYearlyRules_expandTheWayTheEditorOffersThem() {
        fun event(rule: String) = CalEvent(
            id = "evt-freq",
            title = "Repeat",
            color = 0xFF5B7FB5,
            start = fixtureDate.atTime(9, 0),
            durationMinutes = 30,
            recurrence = rule,
            calendarId = "personal",
        )

        assertTrue(event(recurrenceRule(RecurrenceFreq.Daily)).occursOn(fixtureDate.plusDays(3)))
        assertTrue(event(recurrenceRule(RecurrenceFreq.Monthly)).occursOn(LocalDate.of(2026, 6, 18)))
        assertFalse(event(recurrenceRule(RecurrenceFreq.Monthly)).occursOn(LocalDate.of(2026, 6, 19)))
        assertTrue(event(recurrenceRule(RecurrenceFreq.Yearly)).occursOn(LocalDate.of(2027, 5, 18)))
        assertFalse(event(recurrenceRule(RecurrenceFreq.Yearly)).occursOn(LocalDate.of(2027, 6, 18)))
    }

    @Test
    fun untilBoundsEveryFrequency() {
        val rule = recurrenceRule(RecurrenceFreq.Daily, until = LocalDate.of(2026, 5, 20))
        val event = CalEvent(
            id = "evt-until",
            title = "Repeat",
            color = 0xFF5B7FB5,
            start = fixtureDate.atTime(9, 0),
            durationMinutes = 30,
            recurrence = rule,
            calendarId = "personal",
        )

        assertTrue(event.occursOn(LocalDate.of(2026, 5, 20)))
        assertFalse(event.occursOn(LocalDate.of(2026, 5, 21)))
    }
}
