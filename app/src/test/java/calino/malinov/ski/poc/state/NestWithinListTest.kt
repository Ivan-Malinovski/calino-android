package calino.malinov.ski.poc.state

import calino.malinov.ski.poc.data.model.CalTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NestWithinListTest {
    private val day = LocalDate.of(2026, 9, 17)

    private fun task(id: String, parent: String? = null) =
        CalTask(id = id, title = id, color = 0L, due = day, parentTaskId = parent)

    private fun titles(rows: List<TaskListRow>) = rows.map { row ->
        when (row) {
            is TaskListRow.Item -> "${" ".repeat(row.depth)}${row.task.id}"
            is TaskListRow.AbsentParent -> "${" ".repeat(row.depth)}~${row.parent.id}"
        }
    }

    @Test
    fun `parent present on the list nests its children under it`() {
        val rows = nestWithinList(listOf(task("parent"), task("child", "parent"), task("other")))
        assertEquals(listOf("parent", " child", "other"), titles(rows))
    }

    @Test
    fun `absent parent becomes a stand-in row above its subtasks`() {
        val absent = task("renovation", null)
        val rows = nestWithinList(
            listOf(task("measure", "renovation"), task("dishwasher")),
        ) { id -> absent.takeIf { it.id == id } }
        assertEquals(listOf("~renovation", " measure", "dishwasher"), titles(rows))
    }

    @Test
    fun `subtasks of one absent parent gather under a single stand-in`() {
        val absent = task("renovation")
        val rows = nestWithinList(
            listOf(task("measure", "renovation"), task("dishwasher"), task("sink", "renovation")),
        ) { id -> absent.takeIf { it.id == id } }
        assertEquals(listOf("~renovation", " measure", " sink", "dishwasher"), titles(rows))
        // The rail has to survive the first child to reach the second.
        val firstChild = rows[1] as TaskListRow.Item
        assertTrue(firstChild.nestingLines[0])
    }

    @Test
    fun `an unresolvable parent still renders its subtask as a root`() {
        val rows = nestWithinList(listOf(task("measure", "renovation")))
        assertEquals(listOf("measure"), titles(rows))
    }

    @Test
    fun `a stand-in carries its own descendants that are on the list`() {
        val absent = task("renovation")
        val rows = nestWithinList(
            listOf(task("measure", "renovation"), task("recheck", "measure")),
        ) { id -> absent.takeIf { it.id == id } }
        assertEquals(listOf("~renovation", " measure", "  recheck"), titles(rows))
    }

    @Test
    fun `a cycle among present tasks keeps every row`() {
        val rows = nestWithinList(listOf(task("a", "b"), task("b", "a")))
        assertEquals(setOf("a", "b"), titles(rows).map { it.trim() }.toSet())
    }
}
