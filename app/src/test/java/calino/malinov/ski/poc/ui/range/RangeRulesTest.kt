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
}
