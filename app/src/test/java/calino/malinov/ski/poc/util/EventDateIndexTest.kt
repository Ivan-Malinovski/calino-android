package calino.malinov.ski.poc.util

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.occursOn
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class EventDateIndexTest {
    private val monday = LocalDate.of(2026, 5, 18)

    @Test
    fun `matches occursOn for timed all-day spans midnight and recurrence`() {
        val events = listOf(
            event("timed", start = monday.atTime(10, 0), minutes = 60),
            event("midnight", start = monday.atTime(23, 0), minutes = 60),
            event("crossing", start = monday.atTime(23, 0), minutes = 61),
            event("all-day", date = monday, allDay = true),
            event("span", date = monday.minusDays(1), endDate = monday.plusDays(1), allDay = true),
            event("weekly", start = monday.minusWeeks(2).atTime(9, 0), recurrence = "FREQ=WEEKLY;BYDAY=MO"),
            // A recurring *multi-day* all-day span: days 2..n of a later
            // occurrence (here, the week before `monday`) fall on dates the
            // occurrence start alone does not predict -- the bug this guards.
            event(
                "weekly-span", date = monday.minusWeeks(1), endDate = monday.minusWeeks(1).plusDays(2),
                allDay = true, recurrence = "FREQ=WEEKLY;BYDAY=MO",
            ),
            event("detached", start = monday.atTime(12, 0), recurrence = null),
        )
        val index = EventDateIndex.build(events)

        for (offset in -9L..3L) {
            val day = monday.plusDays(offset)
            assertEquals(events.filter { it.occursOn(day) }, index.eventsOn(day))
        }
    }

    @Test
    fun `overlaps retain repository order`() {
        val events = listOf(
            event("weekly", start = monday.minusWeeks(1).atTime(9, 0), recurrence = "FREQ=WEEKLY;BYDAY=MO"),
            event("direct", start = monday.atTime(8, 0)),
            event("daily", start = monday.minusDays(1).atTime(7, 0), recurrence = "FREQ=DAILY"),
        )
        assertEquals(events, EventDateIndex.build(events).eventsOn(monday))
    }

    @Test
    fun `empty and visibility-filtered inputs stay isolated`() {
        assertEquals(emptyList<CalEvent>(), EventDateIndex.build(emptyList()).eventsOn(monday))
        val visible = listOf(event("visible", start = monday.atStartOfDay(), calendarId = "shown"))
        assertEquals(listOf("visible"), EventDateIndex.build(visible).eventsOn(monday).map { it.id })
    }

    @Test
    fun `large direct dataset lookup considers only requested date content`() {
        val events = List(20_000) { index ->
            event("event-$index", start = monday.plusDays(index.toLong()).atStartOfDay())
        }
        val index = EventDateIndex.build(events)
        assertEquals(1, index.candidateCount(monday.plusDays(12_345)))
        assertEquals("event-12345", index.eventsOn(monday.plusDays(12_345)).single().id)
    }

    private fun event(
        id: String,
        start: LocalDateTime? = null,
        minutes: Int? = null,
        date: LocalDate? = null,
        endDate: LocalDate? = null,
        allDay: Boolean = false,
        recurrence: String? = null,
        calendarId: String = "personal",
    ) = CalEvent(
        id = id,
        title = id,
        color = 0L,
        start = start,
        durationMinutes = minutes,
        allDay = allDay,
        recurrence = recurrence,
        calendarId = calendarId,
        date = date,
        endDate = endDate,
    )
}
