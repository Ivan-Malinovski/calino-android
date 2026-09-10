package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.util.CalinoWeekStart
import calino.malinov.ski.poc.util.dayOfWeekForColumn
import calino.malinov.ski.poc.util.startOfWeek
import calino.malinov.ski.poc.util.weekdayColumn
import calino.malinov.ski.poc.util.weekdayLetters
import calino.malinov.ski.poc.util.weekendColumns
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The week grid used to hardcode Monday. These cover the three ways the
 * generalisation goes quietly wrong: an ISO-week adjuster that moves the wrong
 * direction, a negative modulo, and a letter row that looks plausible whether
 * or not it is aligned.
 */
class WeekStartTest {

    // 2026-05-18 is a Monday, so the week it belongs to is unambiguous.
    private val monday = LocalDate.of(2026, 5, 18)
    private val sunday = LocalDate.of(2026, 5, 24)

    @Test
    fun `a week starts on or before the date it contains`() {
        assertEquals(monday, monday.startOfWeek(CalinoWeekStart.Monday))
        assertEquals(monday, sunday.startOfWeek(CalinoWeekStart.Monday))
        assertEquals(LocalDate.of(2026, 5, 17), monday.startOfWeek(CalinoWeekStart.Sunday))
        assertEquals(sunday, sunday.startOfWeek(CalinoWeekStart.Sunday))
    }

    @Test
    fun `the ISO adjuster is not previous-or-same`() {
        // The trap this helper exists to avoid: `with(DayOfWeek.SUNDAY)` moves
        // within the Monday..Sunday ISO week, so for a Monday it jumps six days
        // FORWARD. If anyone "simplifies" startOfWeek back to it, this fails.
        assertEquals(LocalDate.of(2026, 5, 24), monday.with(DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 5, 17), monday.startOfWeek(CalinoWeekStart.Sunday))
    }

    @Test
    fun `every day of a week resolves to the same week start`() {
        CalinoWeekStart.entries.forEach { weekStart ->
            val start = monday.startOfWeek(weekStart)
            repeat(7) { offset ->
                assertEquals(start, start.plusDays(offset.toLong()).startOfWeek(weekStart))
            }
        }
    }

    @Test
    fun `columns and days round-trip`() {
        CalinoWeekStart.entries.forEach { weekStart ->
            val start = monday.startOfWeek(weekStart)
            repeat(7) { column ->
                val date = start.plusDays(column.toLong())
                assertEquals(column, date.weekdayColumn(weekStart))
                assertEquals(date.dayOfWeek, dayOfWeekForColumn(column, weekStart))
            }
        }
    }

    @Test
    fun `a column is never negative`() {
        // A plain `%` returns -6..0 for most days under a Sunday start, which
        // indexes off the front of every column-keyed array.
        CalinoWeekStart.entries.forEach { weekStart ->
            repeat(14) { offset ->
                val column = monday.plusDays(offset.toLong()).weekdayColumn(weekStart)
                assertTrue("column $column out of range", column in 0..6)
            }
        }
    }

    @Test
    fun `the letter row follows the week start`() {
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), weekdayLetters(CalinoWeekStart.Monday))
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), weekdayLetters(CalinoWeekStart.Sunday))
    }

    @Test
    fun `the weekend splits across both edges on a Sunday start`() {
        assertEquals(setOf(5, 6), weekendColumns(CalinoWeekStart.Monday))
        assertEquals(setOf(0, 6), weekendColumns(CalinoWeekStart.Sunday))
    }
}
