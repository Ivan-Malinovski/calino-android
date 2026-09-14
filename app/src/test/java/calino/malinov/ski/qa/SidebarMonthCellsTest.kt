package calino.malinov.ski.qa

import calino.malinov.ski.ui.components.sidebarMonthCells
import calino.malinov.ski.util.CalinoWeekStart
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarMonthCellsTest {
    @Test
    fun incompleteFinalWeekKeepsDatesInWeekdayColumns() {
        val cells = sidebarMonthCells(YearMonth.of(2026, 3), CalinoWeekStart.Monday)

        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 3, 1), cells[6])
        assertEquals(LocalDate.of(2026, 3, 30), cells[35])
        assertEquals(LocalDate.of(2026, 3, 31), cells[36])
        assertEquals(List(5) { null }, cells.takeLast(5))
    }

    @Test
    fun configuredSundayStartMovesSundayToFirstColumn() {
        val cells = sidebarMonthCells(YearMonth.of(2026, 3), CalinoWeekStart.Sunday)

        assertEquals(LocalDate.of(2026, 3, 1), cells.first())
        assertEquals(LocalDate.of(2026, 3, 31), cells[30])
        assertEquals(35, cells.size)
    }
}
