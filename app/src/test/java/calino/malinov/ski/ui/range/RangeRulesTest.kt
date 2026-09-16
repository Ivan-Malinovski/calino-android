package calino.malinov.ski.ui.range

import calino.malinov.ski.util.CalinoRangeMode
import calino.malinov.ski.util.CalinoWeekStart
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RangeRulesTest {
    private val wednesday = LocalDate.of(2026, 9, 16)

    @Test fun `one day range is the selected date`() {
        assertEquals(
            listOf(wednesday),
            rangeDays(wednesday, CalinoRangeMode.OneDay, CalinoWeekStart.Monday),
        )
    }

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
        assertEquals(wednesday.plusDays(1), rangeAnchorForPage(wednesday, RangePagerCenter + 1, CalinoRangeMode.OneDay))
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

    @Test fun `live range drop target snaps movement to quarter hours`() {
        val start = LocalDateTime.of(2026, 9, 16, 9, 0)
        assertEquals(
            LocalDateTime.of(2026, 9, 17, 9, 30),
            rangeDropTarget(start, wednesday.plusDays(1), dragY = 31f, scrollDelta = 0, hourHeight = 62f),
        )
        assertEquals(
            LocalDateTime.of(2026, 9, 16, 8, 45),
            rangeDropTarget(start, wednesday, dragY = -8f, scrollDelta = 0, hourHeight = 62f),
        )
    }

    @Test fun `live range drop target stays inside its destination day`() {
        val start = LocalDateTime.of(2026, 9, 16, 23, 30)
        assertEquals(
            LocalDateTime.of(2026, 9, 17, 23, 45),
            rangeDropTarget(start, wednesday.plusDays(1), dragY = 500f, scrollDelta = 0, hourHeight = 62f),
        )
    }
}
