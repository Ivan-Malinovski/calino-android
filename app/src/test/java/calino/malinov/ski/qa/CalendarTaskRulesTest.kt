package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.state.openTasksDueOn
import calino.malinov.ski.state.taskDueCountsForWeek
import calino.malinov.ski.state.tasksDueOn
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarTaskRulesTest {
    private val monday = LocalDate.of(2026, 5, 18)

    @Test
    fun tasksDueOn_sortsOpenBeforeCompleted_andIgnoresUndatedTasks() {
        val tasks = listOf(
            task("completed", "A completed task", monday, done = true),
            task("open-later", "Later open task", monday),
            task("open-first", "First open task", monday),
            task("inbox", "Inbox", null),
        )

        assertEquals(
            listOf("open-first", "open-later", "completed"),
            tasksDueOn(tasks, monday).map(CalTask::id),
        )
        assertEquals(listOf("open-first", "open-later"), openTasksDueOn(tasks, monday).map(CalTask::id))
    }

    @Test
    fun taskDueCountsForWeek_countsOnlyOpenTasksByDate() {
        val tasks = listOf(
            task("monday", "Monday", monday),
            task("monday-done", "Done", monday, done = true),
            task("wednesday", "Wednesday", monday.plusDays(2)),
            task("next-week", "Next week", monday.plusDays(7)),
        )

        assertEquals(listOf(1, 0, 1, 0, 0, 0, 0), taskDueCountsForWeek(tasks, monday))
    }

    private fun task(id: String, title: String, due: LocalDate?, done: Boolean = false) = CalTask(
        id = id,
        title = title,
        color = 0xFF5D9A78,
        due = due,
        done = done,
    )
}
