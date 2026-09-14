package calino.malinov.ski.state

import calino.malinov.ski.data.model.CalTask
import java.time.LocalDate
import java.util.Locale

/**
 * Calendar-facing task projections. A task has a date-only due value, so it
 * belongs in the day's context rather than in an hourly slot.
 */
fun tasksDueOn(tasks: List<CalTask>, date: LocalDate): List<CalTask> =
    tasks.asSequence()
        .filter { it.due == date }
        .sortedWith(
            compareBy<CalTask> { it.done }
                .thenBy { it.title.lowercase(Locale.US) }
                .thenBy { it.id },
        )
        .toList()

fun openTasksDueOn(tasks: List<CalTask>, date: LocalDate): List<CalTask> =
    tasksDueOn(tasks, date).filterNot(CalTask::done)

fun taskDueCountsForWeek(tasks: List<CalTask>, monday: LocalDate): List<Int> =
    (0L..6L).map { offset -> openTasksDueOn(tasks, monday.plusDays(offset)).size }
