package calino.malinov.ski.poc.state

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

