package calino.malinov.ski.state

import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.NewTask

/**
 * Manual task order, stored as `X-APPLE-SORT-ORDER` on the VTODO -- the
 * property Apple Reminders, Nextcloud Tasks and tasks.org already use. Lower
 * comes first. It only breaks ties inside a day, a group or a set of siblings
 * and never moves a task to another day; the web app applies the same rule.
 */

/** Gap left between consecutive values Calino writes. */
const val SortOrderStep = 1000L

/**
 * Lower sort order first, tasks without one after every task that has one.
 * Two unordered tasks compare equal, so the comparators this is chained into
 * keep deciding their order exactly as before.
 */
val TaskManualOrder: Comparator<CalTask> = compareBy(nullsLast()) { it.sortOrder }

/**
 * The value a new subtask gets so it lands after its ordered siblings, or null
 * when none of them has one (the subtask then stays unordered too).
 */
fun nextSubtaskSortOrder(siblings: Iterable<CalTask>): Long? =
    siblings.mapNotNull { it.sortOrder }.maxOrNull()?.let { it + SortOrderStep }

/** A new subtask without an order of its own goes after its siblings. */
fun NewTask.withNewSubtaskOrder(tasks: List<CalTask>): NewTask {
    val parent = parentTaskId ?: return this
    if (sortOrder != null) return this
    // Occurrences of a repeating sibling share their master's value, so they
    // cannot raise the maximum beyond what the series itself holds.
    return copy(sortOrder = nextSubtaskSortOrder(tasks.filter { it.parentTaskId == parent }))
}
