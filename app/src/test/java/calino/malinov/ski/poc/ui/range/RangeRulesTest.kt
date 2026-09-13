package calino.malinov.ski.poc.ui.range

import calino.malinov.ski.poc.util.CalinoRangeMode
import calino.malinov.ski.poc.util.CalinoWeekStart
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RangeRulesTest {
    private val wednesday = LocalDate.of(2026, 9, 16)

    @Test fun `three day range rolls from selected date`() {
        assertEquals(
            listOf(wednesday, wednesday.plusDays(1), wednesday.plusDays(2)),
            rangeDays(wednesday, CalinoRangeMode.ThreeDay, CalinoWeekStart.Monday),
        )
    }

    @Test fun `seven day range follows configured monday week`() {
        val days = rangeDays(wednesday, CalinoRangeMode.SevenDay, CalinoWeekStart.Monday)
        assertEquals(LocalDate.of(2026, 9, 14), days.first())
        assertEquals(LocalDate.of(2026, 9, 20), days.last())
    }

    @Test fun `seven day range follows configured sunday week`() {
        val days = rangeDays(wednesday, CalinoRangeMode.SevenDay, CalinoWeekStart.Sunday)
        assertEquals(LocalDate.of(2026, 9, 13), days.first())
        assertEquals(LocalDate.of(2026, 9, 19), days.last())
    }

    @Test fun `paging advances by active range width`() {
        assertEquals(wednesday.plusDays(3), rangeAnchorForPage(wednesday, RangePagerCenter + 1, CalinoRangeMode.ThreeDay))
        assertEquals(wednesday.minusDays(7), rangeAnchorForPage(wednesday, RangePagerCenter - 1, CalinoRangeMode.SevenDay))
    }

    @Test fun `held drag enters edge zones only`() {
        assertEquals(-1, rangeEdgeDirection(20f, 360, 52f))
        assertEquals(0, rangeEdgeDirection(180f, 360, 52f))
        assertEquals(1, rangeEdgeDirection(340f, 360, 52f))
    }

    @Test fun `drop resolves against newly visible range columns`() {
        val days = List(3) { wednesday.plusDays(it.toLong()) }
        assertEquals(days.first(), rangeDropDay(52f, 352, 52f, days))
        assertEquals(days[1], rangeDropDay(202f, 352, 52f, days))
        assertEquals(days.last(), rangeDropDay(351f, 352, 52f, days))
    }
}
