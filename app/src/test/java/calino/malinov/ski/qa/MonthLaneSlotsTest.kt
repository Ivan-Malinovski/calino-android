package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.ui.home.monthEventIndex
import calino.malinov.ski.ui.home.monthLaneSlots
import calino.malinov.ski.util.CalinoWeekStart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * A multi-day span must sit in the same lane on every day of its week row.
 * Sorting each day on its own let a second span take the first lane mid-run,
 * so one bar broke into a titled stub and an untitled block a row lower.
 */
class MonthLaneSlotsTest {

    private val month = YearMonth.of(2026, 3)

    private fun allDay(id: String, first: LocalDate, last: LocalDate = first) = CalEvent(
        id = id,
        title = id,
        color = 0xFF000000,
        start = null,
        durationMinutes = null,
        allDay = true,
        calendarId = "c",
        date = first,
        endDate = last.takeIf { it != first },
    )

    private fun slots(vararg events: CalEvent): Map<LocalDate, List<String?>> {
        val index = monthEventIndex(events.toList(), month, CalinoWeekStart.Monday)
        val start = LocalDate.of(2026, 2, 23)
        return monthLaneSlots(index, start, 6).mapValues { (_, v) -> v.map { it?.id } }
    }

    private fun day(n: Int) = LocalDate.of(2026, 3, n)

    @Test
    fun `an overlapping span keeps its lane across the week`() {
        val result = slots(
            allDay("residency", day(17), day(18)),
            allDay("sprint", day(18), day(20)),
            allDay("lunch", day(17)),
        )
        // The longer sprint owns lane 0; lunch fills it on the 17th.
        assertEquals(listOf("lunch", "residency"), result[day(17)])
        assertEquals(listOf("sprint", "residency"), result[day(18)])
        assertEquals(listOf("sprint"), result[day(19)])
    }

    @Test
    fun `a lane with nothing to fill it stays empty`() {
        val result = slots(
            allDay("long", day(10), day(12)),
            allDay("short", day(12), day(13)),
        )
        assertEquals(listOf("long", "short"), result[day(12)])
        assertEquals(listOf(null, "short"), result[day(13)])
    }

    @Test
    fun `single-day events keep their order without spans`() {
        val result = slots(allDay("a", day(3)), allDay("b", day(3)))
        assertEquals(listOf("a", "b"), result[day(3)])
    }
}
