package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.search.CalinoSearchResult
import calino.malinov.ski.poc.data.search.searchCalino
import calino.malinov.ski.poc.data.repository.FixtureRepository
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
}
