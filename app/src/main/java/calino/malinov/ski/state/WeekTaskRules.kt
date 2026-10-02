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

/** An open week task that overlaps [first]..[last]; the Tasks list's "Sometime this week". */
fun CalTask.isSometimeThisWeek(first: LocalDate, last: LocalDate): Boolean =
    !done && isWeekTask() && !due!!.isBefore(first) && !startDate!!.isAfter(last)

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

enum class WeekShelfKind { Badge, Strip }

/**
 * How the week's tasks are presented in this window. The badge is always
 * there (empty, it is the way to add one); the strip stays out of sight until
 * there is something to list, but keeps its kind so a drag can still offer the
 * matching drop zone.
 */
data class WeekShelfLayout(
    val kind: WeekShelfKind,
    val hasTasks: Boolean,
    /** Strip height, 0 for the badge. */
    val stripHeightDp: Int = 0,
) {
    val visible: Boolean get() = kind == WeekShelfKind.Badge || hasTasks
}

const val WeekStripPhoneHeightDp = 32
const val WeekStripTabletHeightDp = 44
/** A phone held sideways is this short or shorter. */
const val CompactHeightMaxDp = 480

fun weekShelfLayoutFor(widthDp: Int, heightDp: Int, taskCount: Int): WeekShelfLayout {
    val hasTasks = taskCount > 0
    return if (calinoEndLaneActive(widthDp, heightDp)) WeekShelfLayout(
        WeekShelfKind.Strip, hasTasks,
        stripHeightDp = if (heightDp <= CompactHeightMaxDp) WeekStripPhoneHeightDp else WeekStripTabletHeightDp,
    ) else WeekShelfLayout(WeekShelfKind.Badge, hasTasks)
}
