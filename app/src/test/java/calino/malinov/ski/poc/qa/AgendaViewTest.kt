package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.model.occursOn
import calino.malinov.ski.poc.data.repository.FixtureRepository
import calino.malinov.ski.poc.state.PocReturnTarget
import calino.malinov.ski.poc.state.restoresAgenda
import calino.malinov.ski.poc.state.tasksDueOn
import calino.malinov.ski.poc.ui.home.MonthPagerPageCount
import calino.malinov.ski.poc.ui.home.monthEventIndex
import calino.malinov.ski.poc.ui.home.monthForPage
import calino.malinov.ski.poc.ui.home.monthPageFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The agenda lists every day of one month per page. It reuses the calendar's
 * month-page arithmetic and per-day indexes, so these guard the contract the
 * agenda depends on rather than its Compose layout.
 */
class AgendaViewTest {
    private val fixtureMonth = YearMonth.of(2026, 5)

    @Test
    fun monthPaging_roundTripsAndStaysInRange() {
        listOf(YearMonth.of(2025, 12), fixtureMonth, YearMonth.of(2026, 9), YearMonth.of(2030, 2))
            .forEach { month ->
                val page = monthPageFor(month)
                assertTrue(page in 0 until MonthPagerPageCount)
                assertEquals(month, monthForPage(page))
            }
        assertEquals(monthPageFor(fixtureMonth) + 1, monthPageFor(fixtureMonth.plusMonths(1)))
    }

    @Test
    fun agendaMonth_listsEveryDayAndPlacesRecurringEvents() {
        val repository = FixtureRepository()
        val days = (1..fixtureMonth.lengthOfMonth()).map(fixtureMonth::atDay)
        assertEquals(31, days.size)

        val index = monthEventIndex(repository.events(), fixtureMonth)
        // The shared index spans the whole month grid, so it must at least
        // cover every day the agenda actually renders.
        assertTrue(days.any { index.containsKey(it) })
        assertEquals(
            days.filter { day -> repository.events().any { it.occursOn(day) } },
            days.filter { index[it].orEmpty().isNotEmpty() },
        )
        // A weekly fixture event has to appear on more than its anchor day,
        // otherwise the agenda would silently drop occurrences.
        val recurring = repository.events().first { it.recurrence != null }
        val occurrences = days.count { day -> index[day].orEmpty().any { it.id == recurring.id } }
        assertTrue("expected repeats for ${recurring.id}, got $occurrences", occurrences > 1)
    }

    @Test
    fun agendaMonth_placesDueTasksOnTheirDueDate() {
        val repository = FixtureRepository()
        val task = repository.tasks().first { it.due != null }
        val due = task.due!!
        assertTrue(tasksDueOn(repository.tasks(), due).any { it.id == task.id })
        assertFalse(tasksDueOn(repository.tasks(), due.plusDays(1)).any { it.id == task.id })
    }

    @Test
    fun agendaOrigin_restoresTheAgenda() {
        assertTrue(PocReturnTarget.Agenda.restoresAgenda())
        assertFalse(PocReturnTarget.Calendar.restoresAgenda())
    }

    @Test
    fun everyDayOfTheMonthIsAddressable() {
        val month = YearMonth.of(2026, 2)
        val days = (1..month.lengthOfMonth()).map(month::atDay)
        assertEquals(28, days.size)
        assertEquals(LocalDate.of(2026, 2, 28), days.last())
    }
}
