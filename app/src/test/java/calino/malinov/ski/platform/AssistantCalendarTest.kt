package calino.malinov.ski.platform

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.notify.ReminderDeepLinks
import calino.malinov.ski.notify.ReminderKind
import calino.malinov.ski.platform.assistant.AssistantCalendar
import calino.malinov.ski.platform.search.subtitle
import java.util.Locale
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an assistant can read through Calino's app functions. */
class AssistantCalendarTest {

    /** Monday 14 September 2026. */
    private val today: LocalDate = LocalDate.of(2026, 9, 14)

    private val calendars = listOf(
        CalinoCalendar(id = "work", name = "Work", color = 0L),
        CalinoCalendar(id = "hidden", name = "Hidden", color = 0L, visible = false),
    )

    private fun event(id: String, title: String, start: java.time.LocalDateTime, calendarId: String = "work", recurrence: String? = null) =
        CalEvent(id = id, title = title, color = 0L, start = start, durationMinutes = 45, calendarId = calendarId, recurrence = recurrence, location = "Room 4")

    private fun snapshot(
        events: List<CalEvent> = emptyList(),
        tasks: List<CalTask> = emptyList(),
        journals: List<JournalEntry> = emptyList(),
    ) = CalinoSnapshot(events = events, tasks = tasks, journals = journals, calendars = calendars)

    @Test
    fun `agenda lists each day with events in time order before tasks`() {
        val days = AssistantCalendar.agenda(
            snapshot(
                events = listOf(
                    event("late", "Review", today.atTime(15, 0)),
                    event("early", "Standup", today.atTime(9, 0)),
                ),
                tasks = listOf(CalTask(id = "t", title = "Send invoice", color = 0L, due = today, dueTime = LocalTime.of(17, 0), calendarId = "work")),
            ),
            today,
            days = 2,
        )

        assertEquals(listOf(today, today.plusDays(1)), days.map { it.date })
        val items = days.first().items
        assertEquals(listOf("Standup", "Review", "Send invoice"), items.map { it.title })
        assertEquals(today.atTime(9, 45), items.first().end)
        assertEquals("Work", items.first().calendarName)
        assertEquals(LocalTime.of(17, 0), items.last().dueTime)
        assertTrue(days.last().items.isEmpty())
    }

    @Test
    fun `a recurring event reports this occurrence's start, not the series start`() {
        val weekly = event("series", "Gym", today.minusWeeks(3).atTime(7, 30), recurrence = "FREQ=WEEKLY")
        val item = AssistantCalendar.agenda(snapshot(events = listOf(weekly)), today, days = 1).single().items.single()

        assertEquals(today.atTime(7, 30), item.start)
        assertEquals(today.atTime(8, 15), item.end)
    }

    @Test
    fun `hidden calendars are invisible to the agenda and to search`() {
        val snapshot = snapshot(events = listOf(event("h", "Secret dentist", today.atTime(10, 0), calendarId = "hidden")))

        assertTrue(AssistantCalendar.agenda(snapshot, today, days = 1).single().items.isEmpty())
        assertTrue(AssistantCalendar.search(snapshot, "dentist", today).isEmpty())
    }

    @Test
    fun `search finds events and tasks, tolerates a typo, and never returns journals`() {
        val snapshot = snapshot(
            events = listOf(event("e", "Design review", today.atTime(11, 0))),
            tasks = listOf(CalTask(id = "t", title = "Design brief", color = 0L, due = today, calendarId = "work")),
            journals = listOf(JournalEntry(id = "j", title = "Design thoughts", body = "private", date = today)),
        )

        val results = AssistantCalendar.search(snapshot, "desgn", today)
        assertEquals(setOf("Design review", "Design brief"), results.map { it.title }.toSet())
        assertTrue(results.none { it.title == "Design thoughts" })
    }

    @Test
    fun `an item id is Calino's own record link, so openItem resolves it like a notification tap`() {
        val item = AssistantCalendar.agenda(snapshot(events = listOf(event("e", "Standup", today.atTime(9, 0)))), today, 1)
            .single().items.single()

        val link = ReminderDeepLinks.parse(item.itemId)!!
        assertEquals(ReminderKind.Event, link.kind)
        assertEquals("e", link.recordId)
        assertEquals(today.toEpochDay(), link.occurrenceDay)
    }

    @Test
    fun `days are clamped to the documented range`() {
        assertEquals(AssistantCalendar.MaxAgendaDays, AssistantCalendar.agenda(snapshot(), today, 400).size)
        assertEquals(1, AssistantCalendar.agenda(snapshot(), today, 0).size)
        assertNull(AssistantCalendar.agenda(snapshot(), today, 1).single().items.firstOrNull())
    }

    @Test
    fun `the phone search subtitle says when and which calendar`() {
        val snapshot = snapshot(
            events = listOf(event("e", "Standup", today.atTime(17, 0))),
            tasks = listOf(CalTask(id = "t", title = "Standup notes", color = 0L, due = today, calendarId = "work")),
        )
        val results = AssistantCalendar.search(snapshot, "standup", today).associateBy { it.kind }

        assertEquals("Mon 14 Sep · 17:00 · Work", results.getValue("event").subtitle(clock24 = true, locale = Locale.US))
        assertEquals("Mon 14 Sep · 5:00 PM · Work", results.getValue("event").subtitle(clock24 = false, locale = Locale.US))
        assertEquals("Mon 14 Sep · Work", results.getValue("task").subtitle(clock24 = true, locale = Locale.US))
    }
}
