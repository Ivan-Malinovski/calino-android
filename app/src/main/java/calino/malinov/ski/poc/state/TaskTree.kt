package calino.malinov.ski.poc.state

import androidx.compose.runtime.staticCompositionLocalOf
import calino.malinov.ski.poc.data.model.CalTask

/** A cycle-safe index for rendering and editing VTODO hierarchies. */
class TaskTree(tasks: List<CalTask>) {
    private val taskById = tasks.associateBy { it.id }
    private val children = tasks.groupBy { it.parentTaskId }.mapValues { (_, value) -> value.toList() }

    fun directChildren(parentId: String): List<CalTask> = children[parentId].orEmpty()

    fun descendants(parentId: String): List<CalTask> {
        val result = mutableListOf<CalTask>()
        val visited = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.add(parentId)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            for (child in children[current].orEmpty().asReversed()) {
                if (!visited.add(child.id)) continue
                result += child
                stack.add(child.id)
            }
        }
        return result
    }

    fun descendantIds(parentId: String): Set<String> = descendants(parentId).mapTo(mutableSetOf()) { it.id }

    fun depth(taskId: String): Int {
        var depth = 0
        var parent = taskById[taskId]?.parentTaskId
        val visited = mutableSetOf<String>()
        while (parent != null && visited.add(parent)) {
            if (taskById[parent] == null) break
            depth++
            parent = taskById[parent]?.parentTaskId
        }
        return depth
    }

    fun canAdopt(taskId: String, parentId: String?): Boolean {
        if (parentId == null) return true
        if (taskId == parentId) return false
        if (taskById[parentId] == null) return false
        return parentId !in descendantIds(taskId)
    }

    fun visibleForest(collapsed: Set<String> = emptySet()): List<Pair<CalTask, Int>> {
        val result = mutableListOf<Pair<CalTask, Int>>()
        val seen = mutableSetOf<String>()
        fun append(task: CalTask, depth: Int) {
            if (!seen.add(task.id)) return
            result += task to depth
            if (task.id in collapsed) return
            directChildren(task.id).forEach { append(it, depth + 1) }
        }
        val roots = tasks().filter { it.parentTaskId == null || it.parentTaskId !in taskById }
        roots.forEach { append(it, 0) }
        // Malformed cycles or duplicate references remain visible rather than
        // disappearing from the task surface.
        tasks().filter { it.id !in seen }.forEach { append(it, 0) }
        return result
    }

    private fun tasks(): List<CalTask> = taskById.values.toList()
}


/** One row of a nested task list. */
sealed interface TaskListRow {
    /** Indent level this row is drawn at. */
    val depth: Int

    /** Per ancestor level, whether a rail continues past this row. */
    val nestingLines: List<Boolean>

    /** A task that is actually on this list. */
    data class Item(
        val task: CalTask,
        override val depth: Int,
        override val nestingLines: List<Boolean>,
    ) : TaskListRow

    /**
     * A parent that is *not* on this list, standing in for itself above the
     * subtasks of it that are. It is a label, not a task row: it carries no
     * checkbox and cannot be completed, dragged or reordered from here.
     */
    data class AbsentParent(
        val parent: CalTask,
        override val depth: Int,
        override val nestingLines: List<Boolean>,
    ) : TaskListRow
}

/**
 * For a rendered sequence of depths, reports per row and per ancestor level
 * whether that level still has a row below it -- the rail a connector keeps
 * drawing past this row.
 *
 * A level continues only while another *child* of it is still coming: a later
 * row one level deeper, reached without passing anything at that level or
 * shallower, which would have closed the group. Asking instead for a later row
 * at the level itself let the rail run on to the next top-level task, so the
 * last child of every group trailed a line into the row beneath it.
 */
fun nestingLinesFor(depths: List<Int>): List<List<Boolean>> = depths.indices.map { index ->
    List(depths[index]) { level ->
        var continues = false
        for (next in index + 1 until depths.size) {
            val nextDepth = depths[next]
            if (nextDepth <= level) break
            if (nextDepth == level + 1) { continues = true; break }
        }
        continues
    }
}

/**
 * Orders one list of tasks -- a single day's, typically -- so every subtask
 * follows the parent it shares that list with, and reports the depth each row
 * should show.
 *
 * A subtask whose parent is absent from the list still nests, under a
 * [TaskListRow.AbsentParent] row naming that parent, as long as [absentParent]
 * can resolve it. The indent and its elbow were already saying "this belongs to
 * something"; without the parent's name on the day they did not say what, which
 * is the whole confusion this stands in for. Orphans sharing a parent are
 * gathered under one such row, at the first one's position, so the label heads
 * a run rather than repeating down the day.
 *
 * Where [absentParent] resolves nothing -- a parent in a calendar this surface
 * is not showing, or a dangling reference -- the subtask renders as a root, as
 * it always did: better flat than hanging off nothing.
 */
fun nestWithinList(
    tasks: List<CalTask>,
    absentParent: (String) -> CalTask? = { null },
): List<TaskListRow> {
    val present = tasks.associateBy { it.id }
    val childrenOf = tasks.filter { it.parentTaskId in present.keys }.groupBy { it.parentTaskId }
    val rows = mutableListOf<CalTask>()
    val depths = mutableListOf<Int>()
    val isLabel = mutableListOf<Boolean>()
    val seen = mutableSetOf<String>()
    fun append(task: CalTask, depth: Int) {
        if (!seen.add(task.id)) return
        rows += task
        depths += depth
        isLabel += false
        childrenOf[task.id].orEmpty().forEach { append(it, depth + 1) }
    }

    val roots = tasks.filter { it.parentTaskId == null || it.parentTaskId !in present.keys }
    // Resolved once per parent: the lookup is a caller's map read, and an
    // orphan's siblings would otherwise each repeat it.
    val standIns = mutableMapOf<String, CalTask>()
    val orphansOf = LinkedHashMap<String, MutableList<CalTask>>()
    for (root in roots) {
        val parentId = root.parentTaskId ?: continue
        val standIn = standIns.getOrElse(parentId) {
            absentParent(parentId)?.also { standIns[parentId] = it }
        } ?: continue
        if (standIn.id == root.id) continue
        orphansOf.getOrPut(parentId) { mutableListOf() } += root
    }
    val headed = mutableSetOf<String>()
    for (root in roots) {
        val parentId = root.parentTaskId
        val orphans = parentId?.let { orphansOf[it] }
        if (parentId == null || orphans == null) {
            append(root, 0)
            continue
        }
        if (!headed.add(parentId)) continue
        rows += standIns.getValue(parentId)
        depths += 0
        isLabel += true
        orphans.forEach { append(it, 1) }
    }
    // A cycle among present tasks would otherwise drop rows from the list.
    tasks.filter { it.id !in seen }.forEach { append(it, 0) }

    val lines = nestingLinesFor(depths)
    return rows.indices.map { index ->
        if (isLabel[index]) {
            TaskListRow.AbsentParent(rows[index], depths[index], lines[index])
        } else {
            TaskListRow.Item(rows[index], depths[index], lines[index])
        }
    }
}

/**
 * Resolves a task id against every task the app is showing, for surfaces that
 * hold only a slice of them -- one day's, one week's -- and need to name a task
 * outside their slice. See [nestWithinList]'s `absentParent`.
 *
 * The default resolves nothing, which is what a preview or a test gets: a
 * surface that cannot look a parent up renders its orphans flat, exactly as it
 * did before there was a lookup to miss.
 */
val LocalTaskLookup = staticCompositionLocalOf<(String) -> CalTask?> { { null } }
