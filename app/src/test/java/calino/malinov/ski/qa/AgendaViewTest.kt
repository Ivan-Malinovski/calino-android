package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.repository.FixtureRepository
import calino.malinov.ski.state.PocReturnTarget
import calino.malinov.ski.state.restoresAgenda
import calino.malinov.ski.state.tasksDueOn
import calino.malinov.ski.ui.home.MonthPagerPageCount
import calino.malinov.ski.ui.home.monthEventIndex
import calino.malinov.ski.ui.home.expandedMonthSpanSegment
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.sortAgendaEvents
import calino.malinov.ski.ui.home.monthForPage
import calino.malinov.ski.ui.home.monthPageFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

        val index = monthEventIndex(repository.events(), fixtureMonth, CalinoWeekStart.Monday)
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
    fun dayAgenda_sortsTimedEventsByStartTime() {
        val day = LocalDate.of(2026, 5, 18)
        fun event(id: String, hour: Int, minute: Int = 0) = CalEvent(
            id = id,
            title = id,
            color = 0L,
            start = LocalDateTime.of(day, LocalTime.of(hour, minute)),
            durationMinutes = 30,
            calendarId = "test",
        )

        assertEquals(
            listOf("12:30", "14:00", "15:00"),
            sortAgendaEvents(
                listOf(event("15:00", 15), event("12:30", 12, 30), event("14:00", 14)),
            ).map { it.id },
        )
    }

    @Test
    fun expandedMonth_ordersSpansThenRecurringThenOneOffEvents_stably() {
        val day = LocalDate.of(2026, 5, 12)
        fun event(
            id: String,
            recurrence: String? = null,
            endDate: LocalDate? = null,
        ) = CalEvent(
            id = id,
            title = id,
            color = 0L,
            start = null,
            durationMinutes = null,
            allDay = true,
            recurrence = recurrence,
            calendarId = "test",
            date = day,
            endDate = endDate,
        )
        val events = listOf(
            event("one-off-a"),
            event("recurring-a", recurrence = "FREQ=WEEKLY"),
            event("span-a", endDate = day.plusDays(2)),
            event("one-off-b"),
            event("span-b", recurrence = "FREQ=DAILY", endDate = day.plusDays(1)),
            event("recurring-b", recurrence = "FREQ=MONTHLY"),
        )

        assertEquals(
            listOf("span-a", "span-b", "recurring-a", "recurring-b", "one-off-a", "one-off-b"),
            monthEventIndex(events, YearMonth.from(day), CalinoWeekStart.Monday)
                .getValue(day)
                .map { it.id },
        )
    }

    @Test
    fun expandedMonth_placesTimedSpansOnEveryDateTheyOccupy() {
        val start = LocalDateTime.of(2026, 5, 12, 18, 0)
        val event = CalEvent(
            id = "timed-span",
            title = "Timed span",
            color = 0L,
            start = start,
            durationMinutes = 31 * 60,
            calendarId = "test",
        )
        val index = monthEventIndex(listOf(event), YearMonth.from(start), CalinoWeekStart.Monday)

        assertTrue(index.getValue(LocalDate.of(2026, 5, 12)).contains(event))
        assertTrue(index.getValue(LocalDate.of(2026, 5, 13)).contains(event))
        assertTrue(index.getValue(LocalDate.of(2026, 5, 14)).contains(event))
        assertFalse(index.containsKey(LocalDate.of(2026, 5, 15)))
    }

    @Test
    fun timedSpanEndingAtMidnight_doesNotOccupyTheFollowingDate() {
        val day = LocalDate.of(2026, 5, 12)
        val event = CalEvent(
            id = "until-midnight",
            title = "Until midnight",
            color = 0L,
            start = LocalDateTime.of(day, LocalTime.of(18, 0)),
            durationMinutes = 6 * 60,
            calendarId = "test",
        )
        val index = monthEventIndex(listOf(event), YearMonth.from(day), CalinoWeekStart.Monday)

        assertTrue(index.getValue(day).contains(event))
        assertFalse(index.containsKey(day.plusDays(1)))
    }

    @Test
    fun expandedMonthSpan_connectsWithinAWeekAndBreaksAtWeekEdges() {
        val monday = LocalDate.of(2026, 5, 11)
        val event = CalEvent(
            id = "week-span",
            title = "Week span",
            color = 0L,
            start = null,
            durationMinutes = null,
            allDay = true,
            calendarId = "test",
            date = monday,
            endDate = monday.plusDays(8),
        )

        assertEquals(listOf(false, true, false, false), expandedMonthSpanSegment(event, monday, 0).edges())
        assertEquals(listOf(true, true, false, false), expandedMonthSpanSegment(event, monday.plusDays(3), 3).edges())
        assertEquals(listOf(true, false, false, true), expandedMonthSpanSegment(event, monday.plusDays(6), 6).edges())
        assertEquals(listOf(false, true, true, false), expandedMonthSpanSegment(event, monday.plusDays(7), 0).edges())
        assertEquals(listOf(true, false, false, false), expandedMonthSpanSegment(event, monday.plusDays(8), 1).edges())
    }

    private fun calino.malinov.ski.ui.home.ExpandedMonthSpanSegment.edges() = listOf(
        continuesFromPrevious,
        continuesToNext,
        continuesFromPreviousWeek,
        continuesToNextWeek,
    )

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
