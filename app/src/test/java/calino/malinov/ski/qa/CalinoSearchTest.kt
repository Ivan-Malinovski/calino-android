package calino.malinov.ski.qa

import calino.malinov.ski.data.search.CalinoSearchResult
import calino.malinov.ski.data.search.CalinoSearchDateMode
import calino.malinov.ski.data.search.CalinoSearchOptions
import calino.malinov.ski.data.search.CalinoSearchRecordType
import calino.malinov.ski.data.search.searchCalino
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.FixtureRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalinoSearchTest {
    private val base = LocalDate.of(2026, 5, 18)
    private val snapshot = FixtureRepository().snapshot()

    @Test fun `content search covers useful metadata and keeps creation available`() {
        val title = searchCalino(snapshot, "Design", base)
        assertTrue(title.actions.single() is CalinoSearchResult.AddEvent)
        assertTrue(title.events.any { it.event.title.contains("Design", ignoreCase = true) })

        val journalNeedle = snapshot.journals.first().body.split(Regex("\\W+")).first { it.length > 4 }
        assertFalse(searchCalino(snapshot, journalNeedle, base).journals.isEmpty())
    }

    @Test fun `pure relative date is navigation rather than creation`() {
        val result = searchCalino(snapshot, "next Friday", base).actions.single()
        assertTrue(result is CalinoSearchResult.NavigateDate)
        assertEquals(base.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)), (result as CalinoSearchResult.NavigateDate).date)
    }

    @Test fun `event language carries recurrence into the editor proposal`() {
        val result = searchCalino(snapshot, "Planning every other Monday at 9 for 30 min", base).actions.single()
            as CalinoSearchResult.AddEvent
        assertEquals("FREQ=WEEKLY;BYDAY=MO;INTERVAL=2", result.parsed.recurrence)
        assertEquals(30, result.parsed.durationMinutes)
    }

    @Test fun `fuzzy matching handles typos multiword queries punctuation and diacritics`() {
        val searchable = CalinoSnapshot(
            events = listOf(
                event("exact", "Design review", base, "work"),
                event("accent", "Café planning", base.plusDays(1), "personal"),
                event("noise", "Dentist appointment", base, "personal"),
            ), tasks = emptyList(), journals = emptyList(),
        )

        assertEquals("exact", searchCalino(searchable, "desgn review", base).events.single().event.id)
        assertEquals("accent", searchCalino(searchable, "cafe-planning", base).events.single().event.id)
        assertTrue(searchCalino(searchable, "dzqrx", base).events.isEmpty())
    }

    @Test fun `title quality outranks metadata and date proximity`() {
        val searchable = CalinoSnapshot(
            events = listOf(
                event("metadata", "Tomorrow", base, "work", notes = "Design"),
                event("title", "Design workshop", base.plusMonths(2), "work"),
            ), tasks = emptyList(), journals = emptyList(),
        )
        assertEquals(listOf("title", "metadata"), searchCalino(searchable, "design", base).events.map { it.event.id })
    }

    @Test fun `record calendar and inclusive custom date filters combine before limits`() {
        val searchable = CalinoSnapshot(
            events = listOf(event("inside", "Planning", base.plusDays(1), "work"), event("outside", "Planning", base.plusDays(3), "work")),
            tasks = listOf(CalTask("task", "Planning task", 0L, base.plusDays(1), calendarId = "personal")),
            journals = listOf(JournalEntry("journal", base.plusDays(1), "Planning note", "body")),
        )
        val options = CalinoSearchOptions(
            recordTypes = setOf(CalinoSearchRecordType.Events, CalinoSearchRecordType.Tasks),
            calendarIds = setOf("work"),
            dateMode = CalinoSearchDateMode.Custom,
            customStart = base.plusDays(1), customEnd = base.plusDays(1),
        )
        val result = searchCalino(searchable, "planning", base, options = options)
        assertEquals(listOf("inside"), result.events.map { it.event.id })
        assertTrue(result.tasks.isEmpty())
        assertTrue(result.journals.isEmpty())
        assertTrue(result.actions.single() is CalinoSearchResult.AddEvent)
    }

    @Test fun `past upcoming and active date filters handle boundaries and undated records`() {
        val searchable = CalinoSnapshot(
            events = listOf(event("past", "Item", base.minusDays(1), "work"), event("today", "Item", base, "work")),
            tasks = listOf(CalTask("undated", "Item", 0L, null)),
            journals = emptyList(), contacts = listOf(Contact("contact", "book", displayName = "Item Person")),
        )
        val past = searchCalino(searchable, "item", base, options = CalinoSearchOptions(dateMode = CalinoSearchDateMode.Past))
        assertEquals(listOf("past"), past.events.map { it.event.id })
        assertTrue(past.tasks.isEmpty() && past.contacts.isEmpty())
        val upcoming = searchCalino(searchable, "item", base, options = CalinoSearchOptions(dateMode = CalinoSearchDateMode.Upcoming))
        assertEquals(listOf("today"), upcoming.events.map { it.event.id })
    }

    @Test fun `group limit is applied after filtering and ordering is deterministic`() {
        val searchable = CalinoSnapshot(
            events = (1..10).map { event("e$it", "Meeting $it", base.plusDays(it.toLong()), if (it % 2 == 0) "work" else "personal") },
            tasks = emptyList(), journals = emptyList(),
        )
        val result = searchCalino(searchable, "meeting", base, limitPerGroup = 3, options = CalinoSearchOptions(calendarIds = setOf("work")))
        assertEquals(listOf("e2", "e4", "e6"), result.events.map { it.event.id })
    }

    private fun event(id: String, title: String, date: LocalDate, calendarId: String, notes: String? = null) = CalEvent(
        id = id, title = title, color = 0L, start = date.atTime(9, 0), durationMinutes = 60, calendarId = calendarId, notes = notes,
    )
}
