package calino.malinov.ski.data.caldav

import biweekly.component.VTodo
import calino.malinov.ski.data.model.CalTask

/** Apple's manual task order, also written by Nextcloud Tasks and tasks.org. Lower comes first. */
internal const val AppleSortOrderProperty = "X-APPLE-SORT-ORDER"

/**
 * The largest magnitude the web app can hold exactly (`Number.MAX_SAFE_INTEGER`).
 * Values beyond it are treated as absent here too, so both apps agree on the
 * order of every task.
 */
private const val MaxSortOrder = 9_007_199_254_740_991L

/** Reads a whole number; anything else, including a value out of range, is absent. */
internal fun parseAppleSortOrder(raw: String?): Long? =
    raw?.trim()?.toLongOrNull()?.takeIf { it in -MaxSortOrder..MaxSortOrder }

internal fun VTodo.readAppleSortOrder(): Long? =
    parseAppleSortOrder(getExperimentalProperty(AppleSortOrderProperty)?.value)

/**
 * Writes the task's order. An unset value leaves the property alone: Calino
 * never clears an order, and a value the reader rejected still belongs to the
 * client that wrote it.
 *
 * An override only updates a value it already has. Without one it takes its
 * master's order when read, and copying that onto the override would pin the
 * occurrence to the old position once the master is reordered.
 */
internal fun VTodo.writeAppleSortOrder(task: CalTask) {
    val value = task.sortOrder ?: return
    val existing = getExperimentalProperty(AppleSortOrderProperty)
    val override = task.recurrenceId != null || task.recurrenceDate != null
    if (override && existing == null) return
    if (existing != null && parseAppleSortOrder(existing.value) == value) return
    removeExperimentalProperties(AppleSortOrderProperty)
    addExperimentalProperty(AppleSortOrderProperty, value.toString())
}
