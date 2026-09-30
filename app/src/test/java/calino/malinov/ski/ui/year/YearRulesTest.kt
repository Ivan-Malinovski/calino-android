package calino.malinov.ski.ui.year

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.EventDateIndex
import calino.malinov.ski.util.leadingCells
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YearRulesTest {
    private fun event(id: String, start: LocalDateTime, minutes: Int = 60, recurrence: String? = null) = CalEvent(
        id = id,
        title = id,
        color = 0xFF000000,
        start = start,
        durationMinutes = minutes,
        recurrence = recurrence,
        calendarId = "cal",
    )

    @Test fun `heat level steps at the documented counts`() {
        assertEquals(listOf(0, 1, 2, 2, 3, 3, 4, 4), (0..7).map(::yearHeatLevel))
        assertEquals(4, yearHeatLevel(40))
    }

    @Test fun `heat alpha tables match the design and clamp`() {
        assertEquals(listOf(0f, .10f, .22f, .38f, .55f), (0..4).map { yearHeatAlpha(it, isDark = false) })
        assertEquals(listOf(0f, .14f, .26f, .40f, .55f), (0..4).map { yearHeatAlpha(it, isDark = true) })
        assertEquals(.55f, yearHeatAlpha(9, isDark = false))
        assertEquals(0f, yearHeatAlpha(-1, isDark = true))
    }

    @Test fun `grid shape follows the window`() {
        assertEquals(YearGridShape(3, 4, false), yearGridShape(360, 800))
        assertEquals(YearGridShape(6, 2, false), yearGridShape(800 - 100, 360))
        assertEquals(YearGridShape(3, 4, false), yearGridShape(800, 1280))
        assertEquals(YearGridShape(4, 3, true), yearGridShape(1280, 800))
    }

    @Test fun `split boundary is 720 wide and the fourth column needs 560 beside the pane`() {
        assertEquals(YearGridShape(6, 2, false), yearGridShape(719, 400))
        assertEquals(YearGridShape(3, 4, true), yearGridShape(720, 400))
        assertEquals(YearGridShape(3, 4, true), yearGridShape(919, 400))
        assertEquals(YearGridShape(4, 3, true), yearGridShape(920, 400))
    }

    @Test fun `pages map to years and back`() {
        assertEquals(2026, yearForPage(2026, YearPagerCenter))
        assertEquals(2027, yearForPage(2026, YearPagerCenter + 1))
        assertEquals(2025, yearForPage(2026, YearPagerCenter - 1))
        assertEquals(YearPagerCenter + 3, pageForYear(2026, 2029))
    }

    @Test fun `day counts cover a multi day event, a recurring master and its exception`() {
        val index = EventDateIndex.build(
            listOf(
                event("span", LocalDateTime.of(2026, 5, 18, 22, 0), minutes = 60 * 30),
                event("weekly", LocalDateTime.of(2026, 5, 4, 9, 0), recurrence = "FREQ=WEEKLY;BYDAY=MO"),
                event("single", LocalDateTime.of(2026, 1, 1, 9, 0)),
            ),
        )
        val counts = yearDayCounts(index, 2026)
        assertEquals(365, counts.size)
        assertEquals(1, counts[LocalDate.of(2026, 1, 1).dayOfYear - 1])
        // Mon 18 May: the span begins and the weekly master lands.
        assertEquals(2, counts[LocalDate.of(2026, 5, 18).dayOfYear - 1])
        // Tue 19 May: the span's second day only.
        assertEquals(1, counts[LocalDate.of(2026, 5, 19).dayOfYear - 1])
        assertEquals(0, counts[LocalDate.of(2026, 5, 21).dayOfYear - 1])
        assertEquals(1, counts[LocalDate.of(2026, 12, 28).dayOfYear - 1])
    }

    @Test fun `a leap year has 366 counts and months sum from them`() {
        val index = EventDateIndex.build(
            listOf(event("daily", LocalDateTime.of(2028, 2, 1, 9, 0), recurrence = "FREQ=DAILY;COUNT=40")),
        )
        val counts = yearDayCounts(index, 2028)
        assertEquals(366, counts.size)
        // Feb 2028 has 29 days, all covered; March takes the remaining 11.
        assertEquals(29, yearMonthTotal(counts, YearMonth.of(2028, 2)))
        assertEquals(11, yearMonthTotal(counts, YearMonth.of(2028, 3)))
        assertEquals(0, yearMonthTotal(counts, YearMonth.of(2028, 12)))
    }

    @Test fun `feb 29 clamps into a common year`() {
        assertEquals(LocalDate.of(2027, 2, 28), clampDayInYear(LocalDate.of(2028, 2, 29), 2027))
        assertEquals(LocalDate.of(2032, 2, 29), clampDayInYear(LocalDate.of(2028, 2, 29), 2032))
        assertEquals(LocalDate.of(2027, 5, 18), clampDayInYear(LocalDate.of(2026, 5, 18), 2027))
    }

    @Test fun `tile label is singular for one event`() {
        val may = YearMonth.of(2026, 5)
        assertEquals("May 2026, 1 event", yearTileLabel(may, 1, Locale.US))
        assertEquals("May 2026, 0 events", yearTileLabel(may, 0, Locale.US))
        assertEquals("May 2026, 12 events", yearTileLabel(may, 12, Locale.US))
    }

    @Test fun `leading cells depend on the week start`() {
        val may = YearMonth.of(2026, 5) // 1 May 2026 is a Friday
        assertEquals(4, may.leadingCells(CalinoWeekStart.Monday))
        assertEquals(5, may.leadingCells(CalinoWeekStart.Sunday))
        assertTrue(may.leadingCells(CalinoWeekStart.Monday) + may.lengthOfMonth() <= 42)
        assertFalse(YearMonth.of(2026, 2).leadingCells(CalinoWeekStart.Monday) > 6)
    }
}
