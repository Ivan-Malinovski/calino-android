package calino.malinov.ski.state

import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.editorDraftFor
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

const val WeekTaskMinDays = 3L

fun CalTask.isRecurringTask(): Boolean = recurrence != null || recurrenceId != null || recurrenceDate != null

/** Mapper dates are already in the display frame; DATE values stay in their date space. */
fun CalTask.isWeekTask(): Boolean = !isRecurringTask() &&
    startDate != null && due != null && due.toEpochDay() - startDate.toEpochDay() + 1 >= WeekTaskMinDays

fun weekTasksInRange(tasks: List<CalTask>, first: LocalDate, last: LocalDate): List<CalTask> = tasks
    .filter { it.isWeekTask() && !it.due!!.isBefore(first) && !it.startDate!!.isAfter(last) }
    .sortedWith(compareBy<CalTask> { it.startDate }.thenBy { it.due }
        .thenBy { it.title.lowercase(Locale.ROOT) }.thenBy { it.id })

/** Preserve every modeled field and identity through the ordinary conditional update path. */
fun CalTask.scheduledTask(day: LocalDate, time: LocalTime? = null): NewTask =
    editorDraftFor(this, day).toNewTask().copy(due = day, dueTime = time, startDate = null, startTime = null)

fun CalTask.weekTask(first: LocalDate, last: LocalDate): NewTask {
    require(!isRecurringTask()) { "Repeating tasks cannot move to a week range" }
    require(last.toEpochDay() - first.toEpochDay() + 1 >= WeekTaskMinDays)
    return editorDraftFor(this, first).toNewTask().copy(due = last, dueTime = null, startDate = first, startTime = null)
}

/** A deadline follows the pointer rather than an event's relative start offset. */
fun taskDropMinute(pointerY: Float, scrollY: Int, hourHeight: Float): Int =
    ((pointerY + scrollY).coerceAtLeast(0f) / hourHeight * 60 / 15).toInt().coerceIn(0, 95) * 15
