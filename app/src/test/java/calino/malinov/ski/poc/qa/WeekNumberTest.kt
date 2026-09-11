package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.util.CalinoWeekStart
import calino.malinov.ski.poc.util.isoWeekNumber
import calino.malinov.ski.poc.util.startOfWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The month grid's week rail prints one ISO number per row, whichever day the
 * week is set to begin on. These pin the cases where "the week the row starts
 * in" and "the ISO week the row covers" are not the same week: a Sunday-start
 * row begins in the *previous* ISO week, and a row spanning New Year belongs
 * to whichever year owns its Thursday, not to its first cell.
 */
class WeekNumberTest {

    private fun rowNumber(date: LocalDate, weekStart: CalinoWeekStart): Int =
        isoWeekNumber(date.startOfWeek(weekStart))

    @Test
    fun `a row is numbered by the iso week it covers`() {
        // Monday..Saturday land in a row covering the same ISO week under
        // either setting, so the rail must print the same number for them. A
        // rail numbered from the row's first cell would disagree for every
        // Sunday-start row in the year.
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            if (date.dayOfWeek != DayOfWeek.SUNDAY) {
                assertEquals(
                    "week number for $date",
                    date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                    rowNumber(date, CalinoWeekStart.Sunday),
                )
                assertEquals(
                    "week number for $date",
                    date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                    rowNumber(date, CalinoWeekStart.Monday),
                )
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `a sunday belongs to the row it opens, not to its own iso week`() {
        // Sunday 2026-03-01 closes ISO week 9, but under a Sunday start it
        // *opens* the row running to Saturday 2026-03-07 -- ISO week 10. The
        // rail numbers the row, so the cell holding it reads 10.
        val sunday = LocalDate.of(2026, 3, 1)
        assertEquals(9, sunday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        assertEquals(10, rowNumber(sunday, CalinoWeekStart.Sunday))
        // Under a Monday start the same Sunday closes a row, and reads 9.
        assertEquals(9, rowNumber(sunday, CalinoWeekStart.Monday))
    }

    @Test
    fun `a row spanning new year takes the year that owns it`() {
        // 2026-01-01 is a Thursday, so its row is week 1 of 2026 even though
        // under a Monday start the row opens back in 2025-12-29.
        val newYear = LocalDate.of(2026, 1, 1)
        assertEquals(1, rowNumber(newYear, CalinoWeekStart.Monday))
        assertEquals(1, rowNumber(newYear, CalinoWeekStart.Sunday))
        // 2027-01-01 is a Friday: its row is the *last* week of 2026.
        assertEquals(53, rowNumber(LocalDate.of(2027, 1, 1), CalinoWeekStart.Monday))
    }

    @Test
    fun `a long year runs to week 53`() {
        // 2026 is a 53-week year; 2025 is not, and its last days have already
        // rolled into week 1 of 2026.
        assertEquals(53, rowNumber(LocalDate.of(2026, 12, 31), CalinoWeekStart.Monday))
        assertEquals(1, rowNumber(LocalDate.of(2025, 12, 31), CalinoWeekStart.Monday))
        assertEquals(52, rowNumber(LocalDate.of(2025, 12, 22), CalinoWeekStart.Monday))
    }
}
