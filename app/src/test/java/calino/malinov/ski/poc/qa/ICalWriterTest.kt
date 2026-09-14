package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.ICalMapper
import calino.malinov.ski.poc.data.caldav.ICalWriter
import calino.malinov.ski.poc.data.model.Attendee
import calino.malinov.ski.poc.data.model.Availability
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The writer is tested against the reader wherever possible.
 *
 * A serializer checked only against expected text drifts from the parser it is
 * supposed to mirror; asserting that a model survives write -> parse catches the
 * inversions that matter, and the text assertions are kept for the handful of
 * rules the model cannot express -- DTEND's exclusivity above all.
 */
class ICalWriterTest {

    private val zone = ZoneId.of("Europe/Copenhagen")
    private val writer = ICalWriter(zone)
    private val mapper = ICalMapper(zone)
    private val now = Instant.parse("2026-03-05T08:00:00Z")

    private fun serialize(vararg components: biweekly.component.ICalComponent) =
        writer.buildCalendar(components.toList())

    private fun reparse(ics: String) =
        mapper.parse(ics, calendarId = "cal", color = 1L, href = "https://x/e.ics")

    @Test
    fun `task priority and partial progress survive write and read`() {
        val task = CalTask(
            id = "partial", title = "Draft", color = 1L, due = LocalDate.of(2026, 3, 5),
            uid = "partial", priority = 3, percentComplete = 40, status = "IN-PROCESS",
        )
        val text = serialize(writer.writeTask(task, now = now))
        val back = reparse(text).tasks.single()

        assertEquals(3, back.priority)
        assertEquals(40, back.percentComplete)
        assertEquals("IN-PROCESS", back.status)
        assertFalse(back.done)
        assertTrue(text.contains("PRIORITY:3"))
        assertTrue(text.contains("PERCENT-COMPLETE:40"))
        assertFalse(text.contains("COMPLETED:"))
    }

    @Test
    fun `recurring task writes matching DTSTART and DUE`() {
        val task = CalTask(
            id = "repeat", title = "Exercise", color = 1L, due = LocalDate.of(2026, 3, 3),
            uid = "repeat", recurrence = "FREQ=WEEKLY;BYDAY=TU",
        )
        val text = serialize(writer.writeTask(task, now = now))
        assertTrue(text.contains("DTSTART;VALUE=DATE:20260303"))
        assertTrue(text.contains("DUE;VALUE=DATE:20260303"))
        assertTrue(text.contains("RRULE:FREQ=WEEKLY;BYDAY=TU"))
    }

    @Test
    fun `a timed event survives a write and read`() {
        val event = CalEvent(
            id = "uid-1",
            title = "Standup",
            color = 1L,
            start = LocalDateTime.of(2026, 3, 5, 9, 30),
            durationMinutes = 45,
            location = "Room 2",
            notes = "bring the laptop",
            attendees = listOf(Attendee("Ada", "ada@example.com")),
            categories = listOf("work", "team"),
            availability = Availability.Free,
            travelTimeMinutes = 75,
            calendarId = "cal",
            uid = "uid-1",
        )
        val back = reparse(serialize(writer.writeEvent(event, now = now))).events.single()

        assertEquals("Standup", back.title)
        assertEquals(LocalDateTime.of(2026, 3, 5, 9, 30), back.start)
        assertEquals(45, back.durationMinutes)
        assertEquals("Room 2", back.location)
        assertEquals("bring the laptop", back.notes)
        assertEquals(listOf("work", "team"), back.categories)
        assertEquals(Availability.Free, back.availability)
        assertEquals(75, back.travelTimeMinutes)
        assertEquals(listOf(Attendee("Ada", "ada@example.com")), back.attendees)
        assertEquals("uid-1", back.uid)
    }

    @Test
    fun `Apple travel duration is written and can be cleared`() {
        val event = CalEvent(
            id = "travel-1", title = "Appointment", color = 1L,
            start = LocalDateTime.of(2026, 3, 5, 9, 0), durationMinutes = 30,
            travelTimeMinutes = 15, calendarId = "cal", uid = "travel-1",
        )
        val component = writer.writeEvent(event, now = now)
        assertTrue(serialize(component).contains("X-APPLE-TRAVEL-DURATION:PT15M"))

        val cleared = serialize(writer.writeEvent(event.copy(travelTimeMinutes = null), component, now))
        assertFalse(cleared.contains("X-APPLE-TRAVEL-DURATION"))
    }

    @Test
    fun `an all-day span writes an exclusive DTEND and reads back inclusive`() {
        val event = CalEvent(
            id = "uid-2",
            title = "Trip",
            color = 1L,
            start = null,
            durationMinutes = null,
            allDay = true,
            date = LocalDate.of(2026, 3, 11),
            // Inclusive last day: the 13th is part of the trip.
            endDate = LocalDate.of(2026, 3, 13),
            calendarId = "cal",
            uid = "uid-2",
        )
        val ics = serialize(writer.writeEvent(event, now = now))

        assertTrue(ics, ics.contains("DTSTART;VALUE=DATE:20260311"))
        // Exclusive on the wire: the 14th, so the span covers 11th-13th.
        assertTrue(ics, ics.contains("DTEND;VALUE=DATE:20260314"))

        val back = reparse(ics).events.single()
        assertTrue(back.allDay)
        assertEquals(LocalDate.of(2026, 3, 11), back.date)
        assertEquals(LocalDate.of(2026, 3, 13), back.endDate)
    }

    @Test
    fun `a single all-day event still ends the following day`() {
        val event = CalEvent(
            id = "uid-3", title = "Holiday", color = 1L, start = null, durationMinutes = null,
            allDay = true, date = LocalDate.of(2026, 3, 11), calendarId = "cal", uid = "uid-3",
        )
        val ics = serialize(writer.writeEvent(event, now = now))
        assertTrue(ics, ics.contains("DTEND;VALUE=DATE:20260312"))
        assertNull(reparse(ics).events.single().endDate)
    }

    @Test
    fun `a date-only value is written from the digits, not from an instant`() {
        // The zone is deliberately east of UTC: converting a midnight instant
        // would slide this date to the 10th.
        val event = CalEvent(
            id = "uid-4", title = "Day", color = 1L, start = null, durationMinutes = null,
            allDay = true, date = LocalDate.of(2026, 3, 11), calendarId = "cal", uid = "uid-4",
        )
        val ics = ICalWriter(ZoneId.of("Pacific/Auckland"))
            .buildCalendar(listOf(ICalWriter(ZoneId.of("Pacific/Auckland")).writeEvent(event, now = now)))
        assertTrue(ics, ics.contains("DTSTART;VALUE=DATE:20260311"))
    }

    @Test
    fun `clearing a field removes the property rather than leaving the old value`() {
        val original = CalEvent(
            id = "uid-5", title = "Call", color = 1L,
            start = LocalDateTime.of(2026, 3, 5, 9, 0), durationMinutes = 30,
            location = "Room 2", notes = "notes", calendarId = "cal", uid = "uid-5",
        )
        val component = writer.writeEvent(original, now = now)
        assertTrue(serialize(component).contains("LOCATION:Room 2"))

        // Same component, edited: the writer is handed what it wrote before,
        // exactly as the patch path hands it the server's parsed component.
        val cleared = original.copy(location = null, notes = null)
        val ics = serialize(writer.writeEvent(cleared, component, now))

        assertFalse(ics, ics.contains("LOCATION"))
        assertFalse(ics, ics.contains("DESCRIPTION"))
    }

    @Test
    fun `a recurrence rule the editor cannot build is still written back`() {
        val event = CalEvent(
            id = "uid-6", title = "Series", color = 1L,
            start = LocalDateTime.of(2026, 3, 5, 9, 0), durationMinutes = 30,
            recurrence = "FREQ=MONTHLY;BYDAY=-1FR;INTERVAL=2", calendarId = "cal", uid = "uid-6",
        )
        val ics = serialize(writer.writeEvent(event, now = now))
        val rrule = ics.lineSequence().first { it.startsWith("RRULE:") }
        assertTrue(rrule, rrule.contains("FREQ=MONTHLY"))
        assertTrue(rrule, rrule.contains("BYDAY=-1FR"))
        assertTrue(rrule, rrule.contains("INTERVAL=2"))
    }

    @Test
    fun `dropping a recurrence removes RRULE`() {
        val event = CalEvent(
            id = "uid-7", title = "Was a series", color = 1L,
            start = LocalDateTime.of(2026, 3, 5, 9, 0), durationMinutes = 30,
            recurrence = "FREQ=WEEKLY", calendarId = "cal", uid = "uid-7",
        )
        val component = writer.writeEvent(event, now = now)
        val ics = serialize(writer.writeEvent(event.copy(recurrence = null), component, now))
        assertFalse(ics, ics.contains("RRULE"))
    }

    @Test
    fun `a recurrence edit can preserve the server rule when the occurrence model has none`() {
        val series = CalEvent(
            id = "uid-7b",
            title = "Series",
            color = 1L,
            start = LocalDateTime.of(2026, 3, 5, 9, 0),
            durationMinutes = 30,
            recurrence = "FREQ=WEEKLY;BYDAY=TH",
            calendarId = "cal",
            uid = "uid-7b",
        )
        val original = writer.writeEvent(series, now = now)
        val occurrence = series.copy(recurrence = null, title = "Edited occurrence")

        val preserved = writer.writeEvent(
            occurrence,
            original = original,
            now = now,
            preserveRecurrenceIfMissing = true,
        )

        assertTrue(serialize(preserved).contains("RRULE:FREQ=WEEKLY;BYDAY=TH"))
    }

    @Test
    fun `DTSTAMP and LAST-MODIFIED agree, and SEQUENCE is not bumped by a rewrite`() {
        val event = CalEvent(
            id = "uid-8", title = "Once", color = 1L,
            start = LocalDateTime.of(2026, 3, 5, 9, 0), durationMinutes = 30,
            calendarId = "cal", uid = "uid-8",
        )
        val component = writer.writeEvent(event, now = now)
        val first = serialize(component)
        assertTrue(first, first.contains("DTSTAMP:20260305T080000Z"))
        assertTrue(first, first.contains("LAST-MODIFIED:20260305T080000Z"))
        assertTrue(first, first.contains("SEQUENCE:0"))

        // A second save an hour later restamps, but must not touch SEQUENCE:
        // an unconditional bump turns a no-op edit into a false conflict.
        val again = serialize(writer.writeEvent(event, component, now.plusSeconds(3600)))
        assertTrue(again, again.contains("DTSTAMP:20260305T090000Z"))
        assertTrue(again, again.contains("SEQUENCE:0"))
        // CREATED is preserved from the first write, not restamped.
        assertTrue(again, again.contains("CREATED:20260305T080000Z"))
    }

    @Test
    fun `a completed task writes a consistent status, percentage and timestamp`() {
        val task = CalTask(
            id = "t-1", title = "Ship it", color = 1L,
            due = LocalDate.of(2026, 3, 5), done = true, uid = "t-1",
        )
        val ics = serialize(writer.writeTask(task, now = now))
        assertTrue(ics, ics.contains("STATUS:COMPLETED"))
        assertTrue(ics, ics.contains("PERCENT-COMPLETE:100"))
        assertTrue(ics, ics.contains("COMPLETED:20260305T080000Z"))
        assertTrue(reparse(ics).tasks.single().done)
    }

    @Test
    fun `reopening a task clears the completion timestamp`() {
        val task = CalTask(id = "t-2", title = "Ship it", color = 1L, due = null, done = true, uid = "t-2")
        val component = writer.writeTask(task, now = now)
        val ics = serialize(writer.writeTask(task.copy(done = false), component, now))
        assertTrue(ics, ics.contains("STATUS:NEEDS-ACTION"))
        assertFalse(ics, ics.contains("COMPLETED:"))
        assertFalse(ics, ics.contains("PERCENT-COMPLETE"))
    }

    @Test
    fun `a cancelled task is not resurrected by saving it`() {
        // ICalMapper reads `done` from COMPLETED or percent >= 100 only, so a
        // CANCELLED task comes back as not-done. Writing that straight back
        // would replace the cancellation with NEEDS-ACTION and bring an
        // abandoned task back to life.
        val cancelled = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Other//EN
            BEGIN:VTODO
            UID:t-3
            DTSTAMP:20260101T000000Z
            DUE;VALUE=DATE:20260305
            SUMMARY:Abandoned
            STATUS:CANCELLED
            END:VTODO
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")

        val task = reparse(cancelled).tasks.single()
        assertFalse("this reader does not treat CANCELLED as done", task.done)

        val patcher = calino.malinov.ski.poc.data.caldav.ICalPatcher(writer)
        val saved = patcher.patchTask(cancelled, task, now)!!
        assertTrue(saved, saved.contains("STATUS:CANCELLED"))
        assertFalse(saved, saved.contains("STATUS:NEEDS-ACTION"))

        // Completing it is a real act, and does overwrite the cancellation.
        val completed = patcher.patchTask(cancelled, task.copy(done = true), now)!!
        assertTrue(completed, completed.contains("STATUS:COMPLETED"))
        assertFalse(completed, completed.contains("STATUS:CANCELLED"))
    }

    @Test
    fun `a task with a time writes a date-time DUE, and one without writes a date`() {
        val timed = CalTask(
            id = "t-4", title = "Call", color = 1L,
            due = LocalDate.of(2026, 3, 5), dueTime = LocalTime.of(14, 0), uid = "t-4",
        )
        val timedIcs = serialize(writer.writeTask(timed, now = now))
        assertTrue(timedIcs, timedIcs.contains("DUE:20260305T130000Z"))
        assertEquals(LocalTime.of(14, 0), reparse(timedIcs).tasks.single().dueTime)

        val allDay = timed.copy(id = "t-5", uid = "t-5", dueTime = null)
        val allDayIcs = serialize(writer.writeTask(allDay, now = now))
        assertTrue(allDayIcs, allDayIcs.contains("DUE;VALUE=DATE:20260305"))
        assertNull(reparse(allDayIcs).tasks.single().dueTime)
    }

    @Test
    fun `a journal entry survives a write and read`() {
        val entry = JournalEntry(
            id = "j-1", date = LocalDate.of(2026, 3, 5),
            title = "Monday", body = "First line\n\nSecond line", uid = "j-1",
        )
        val ics = serialize(writer.writeJournal(entry, now = now))
        assertTrue(ics, ics.contains("DTSTART;VALUE=DATE:20260305"))

        val back = reparse(ics).journals.single()
        assertEquals("Monday", back.title)
        assertEquals("First line\n\nSecond line", back.body)
        assertEquals(LocalDate.of(2026, 3, 5), back.date)
    }

    // --- VALARM ---------------------------------------------------------------

    private fun timedEvent(reminders: List<Reminder>) = CalEvent(
        id = "uid-alarm",
        title = "Review",
        color = 1L,
        start = LocalDateTime.of(2026, 3, 5, 9, 30),
        durationMinutes = 30,
        calendarId = "cal",
        uid = "uid-alarm",
        reminders = reminders,
    )

    @Test
    fun `reminders are written as relative display alarms`() {
        val ics = serialize(writer.writeEvent(timedEvent(listOf(Reminder(30))), now = now))

        assertTrue(ics, ics.contains("BEGIN:VALARM"))
        assertTrue(ics, ics.contains("ACTION:DISPLAY"))
        assertTrue(ics, ics.contains("TRIGGER:-PT30M"))
        assertEquals(listOf(Reminder(30)), reparse(ics).events.single().reminders)
    }

    @Test
    fun `an event with no reminders writes no alarm`() {
        val ics = serialize(writer.writeEvent(timedEvent(emptyList()), now = now))

        assertFalse(ics, ics.contains("BEGIN:VALARM"))
        assertTrue(reparse(ics).events.single().reminders.isEmpty())
    }

    @Test
    fun `an at-start reminder survives a write and read`() {
        val ics = serialize(writer.writeEvent(timedEvent(listOf(Reminder(0))), now = now))

        // A zero-length prior duration. biweekly renders it "-PT0M", which is a
        // well-formed dur-value; what matters is that it reads back as at-start.
        assertTrue(ics, ics.contains("TRIGGER:-PT0M"))
        assertEquals(listOf(Reminder(0)), reparse(ics).events.single().reminders)
    }

    @Test
    fun `several reminders survive a write and read in lead-time order`() {
        val event = timedEvent(listOf(Reminder(10), Reminder(1440), Reminder(60)))
        val back = reparse(serialize(writer.writeEvent(event, now = now))).events.single()

        assertEquals(listOf(Reminder(1440), Reminder(60), Reminder(10)), back.reminders)
    }

    @Test
    fun `a task reminder survives a write and read`() {
        val task = CalTask(
            id = "task-alarm",
            title = "File taxes",
            color = 1L,
            due = LocalDate.of(2026, 3, 5),
            dueTime = LocalTime.of(9, 0),
            calendarId = "cal",
            uid = "task-alarm",
            reminder = Reminder(60),
        )
        val back = reparse(serialize(writer.writeTask(task, now = now))).tasks.single()

        assertEquals(Reminder(60), back.reminder)
    }
}
