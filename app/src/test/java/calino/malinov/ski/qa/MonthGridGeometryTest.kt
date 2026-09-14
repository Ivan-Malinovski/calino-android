package calino.malinov.ski.qa

import calino.malinov.ski.ui.home.monthGridGeometry
import calino.malinov.ski.ui.home.weekPageFor
import calino.malinov.ski.ui.home.weekStartForPage
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.startOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The month grid's shape and the week pager's page numbering both move with the
 * week start. These pin the two consequences that are invisible until someone
 * changes the setting: a cache sized for the wrong number of rows, and a pager
 * left pointing at the wrong week.
 */
class MonthGridGeometryTest {

    @Test
    fun `a grid begins on or before the first of the month`() {
        CalinoWeekStart.entries.forEach { weekStart ->
            (1..12).forEach { monthValue ->
                val month = YearMonth.of(2026, monthValue)
                val geometry = monthGridGeometry(month, weekStart)
                assertTrue(
                    "$weekStart $month starts after the 1st",
                    !geometry.start.isAfter(month.atDay(1)),
                )
                assertEquals(weekStart.dayOfWeek, geometry.start.dayOfWeek)
            }
        }
    }

    @Test
    fun `the same month is a different number of rows under each week start`() {
        // The fact that invalidates every cache sized `rows * 7`, and it goes
        // both ways. March 2026 begins on a Sunday: six rows Monday-start,
        // five Sunday-start. May 2026 begins on a Friday and does the reverse.
        val march = YearMonth.of(2026, 3)
        assertEquals(6, monthGridGeometry(march, CalinoWeekStart.Monday).rows)
        assertEquals(5, monthGridGeometry(march, CalinoWeekStart.Sunday).rows)

        val may = YearMonth.of(2026, 5)
        assertEquals(5, monthGridGeometry(may, CalinoWeekStart.Monday).rows)
        assertEquals(6, monthGridGeometry(may, CalinoWeekStart.Sunday).rows)
    }

    @Test
    fun `the grid covers every day of its month`() {
        CalinoWeekStart.entries.forEach { weekStart ->
            (1..12).forEach { monthValue ->
                val month = YearMonth.of(2026, monthValue)
                val geometry = monthGridGeometry(month, weekStart)
                val last = geometry.dateAt(geometry.cellCount - 1)
                assertTrue(
                    "$weekStart $month ends before its last day",
                    !last.isBefore(month.atEndOfMonth()),
                )
                assertEquals(month.atDay(1), geometry.dateAt(geometry.leadingCells))
                assertEquals(
                    month.atEndOfMonth(),
                    geometry.dateAt(geometry.trailingIndex - 1),
                )
            }
        }
    }

    @Test
    fun `a week page and its first day round-trip`() {
        CalinoWeekStart.entries.forEach { weekStart ->
            var date = LocalDate.of(2026, 1, 1)
            repeat(370) {
                val page = weekPageFor(date, weekStart)
                assertEquals(date.startOfWeek(weekStart), weekStartForPage(page, weekStart))
                date = date.plusDays(1)
            }
        }
    }

    @Test
    fun `only a Sunday changes page when the week start does`() {
        // The reason the pagers are rebuilt when this setting changes. Leave
        // them alone and a restored pager reads back a date a week off -- and
        // the settled-page collector commits it.
        val sunday = LocalDate.of(2026, 5, 24)
        val wednesday = LocalDate.of(2026, 5, 20)
        assertNotEquals(
            weekPageFor(sunday, CalinoWeekStart.Monday),
            weekPageFor(sunday, CalinoWeekStart.Sunday),
        )
        assertEquals(
            weekPageFor(wednesday, CalinoWeekStart.Monday),
            weekPageFor(wednesday, CalinoWeekStart.Sunday),
        )
    }
}
