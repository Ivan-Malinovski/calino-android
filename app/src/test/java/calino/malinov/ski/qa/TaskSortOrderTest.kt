package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.caldav.ICalPatcher
import calino.malinov.ski.data.caldav.ICalWriter
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.state.SortOrderStep
import calino.malinov.ski.state.TaskTree
import calino.malinov.ski.state.nextSubtaskSortOrder
import calino.malinov.ski.state.orderTasksByDue
import calino.malinov.ski.state.tasksDueOn
import calino.malinov.ski.state.weekTasksInRange
import calino.malinov.ski.state.withNewSubtaskOrder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `X-APPLE-SORT-ORDER`: read, kept through every write, and used only to break
 * ties. The cases mirror the web app's `taskSortOrder.test.ts`, since both apps
 * must show the same order for the same server data.
 */
class TaskSortOrderTest {

    private val zone = ZoneId.of("Europe/Copenhagen")
    private val writer = ICalWriter(zone)
    private val patcher = ICalPatcher(writer)
    private val mapper = ICalMapper(zone)
    private val now = Instant.parse("2026-03-05T08:00:00Z")
    private val day = LocalDate.of(2026, 3, 3)

    private fun ics(vararg lines: String) = lines.joinToString("\r\n") + "\r\n"

    private fun calendar(vararg components: List<String>) =
        ics("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Other Client//EN", *components.flatMap { it }.toTypedArray(), "END:VCALENDAR")

    private fun todo(vararg lines: String) = listOf(
        "BEGIN:VTODO", "UID:t1", "DTSTAMP:20260101T000000Z", "SUMMARY:Pack bag", "DUE;VALUE=DATE:20260303",
        *lines, "END:VTODO",
    )

    private fun parse(text: String): List<CalTask> =
        mapper.parse(text, "cal", 1L, "https://dav.example/t.ics").tasks

    private fun sortOrderLines(text: String) =
        text.lineSequence().map { it.trimEnd('\r') }.filter { it.startsWith("X-APPLE-SORT-ORDER") }.toList()

    private fun task(id: String, sortOrder: Long? = null, due: LocalDate? = day, time: LocalTime? = null, parent: String? = null) =
        CalTask(id = id, title = id, color = 0L, due = due, dueTime = time, sortOrder = sortOrder, parentTaskId = parent)

    // Parsing

    @Test
    fun `reads the value, including tasks-org sized ones`() {
        assertEquals(813528420L, parse(calendar(todo("X-APPLE-SORT-ORDER:813528420"))).single().sortOrder)
        assertEquals(-7L, parse(calendar(todo("X-APPLE-SORT-ORDER: -7 "))).single().sortOrder)
    }

    @Test
    fun `leaves missing, invalid and out of range values unset`() {
        assertNull(parse(calendar(todo())).single().sortOrder)
        assertNull(parse(calendar(todo("X-APPLE-SORT-ORDER:soon"))).single().sortOrder)
        assertNull(parse(calendar(todo("X-APPLE-SORT-ORDER:1.5"))).single().sortOrder)
        // Beyond what the web app can hold exactly, so both apps ignore it.
        assertNull(parse(calendar(todo("X-APPLE-SORT-ORDER:9007199254740993"))).single().sortOrder)
        assertEquals(9_007_199_254_740_991L, parse(calendar(todo("X-APPLE-SORT-ORDER:9007199254740991"))).single().sortOrder)
    }

    // Writing

    @Test
    fun `writes the value on a new task and nothing without one`() {
        val written = writer.buildCalendar(listOf(writer.writeTask(task("new", sortOrder = 2000), null, now)))
        assertEquals(listOf("X-APPLE-SORT-ORDER:2000"), sortOrderLines(written))
        val plain = writer.buildCalendar(listOf(writer.writeTask(task("new"), null, now)))
        assertEquals(emptyList<String>(), sortOrderLines(plain))
    }

    @Test
    fun `survives an edit and completing the task`() {
        val original = calendar(todo("X-APPLE-SORT-ORDER:813528420"))
        val parsed = parse(original).single()

        val edited = patcher.patchTask(original, parsed.copy(title = "Pack the bag"), now)!!
        assertEquals(listOf("X-APPLE-SORT-ORDER:813528420"), sortOrderLines(edited))
        assertEquals(813528420L, parse(edited).single().sortOrder)

        val done = patcher.patchTask(
            original,
            parsed.copy(done = true, status = "COMPLETED", percentComplete = 100, completedAt = now),
            now,
        )!!
        assertEquals(listOf("X-APPLE-SORT-ORDER:813528420"), sortOrderLines(done))
    }

    @Test
    fun `leaves a value it could not read untouched`() {
        val original = calendar(todo("X-APPLE-SORT-ORDER:first"))
        val parsed = parse(original).single()
        val edited = patcher.patchTask(original, parsed.copy(title = "x"), now)!!
        assertEquals(listOf("X-APPLE-SORT-ORDER:first"), sortOrderLines(edited))
    }

    @Test
    fun `an edit through the editor draft keeps the value`() {
        val parsed = parse(calendar(todo("X-APPLE-SORT-ORDER:5000"))).single()
        val draft = calino.malinov.ski.data.model.editorDraftFor(parsed, day)
        assertEquals(5000L, draft.copy(title = "Renamed").toNewTask().sortOrder)
    }

    // Repeating tasks

    private val master = listOf(
        "BEGIN:VTODO", "UID:gym", "DTSTAMP:20260101T000000Z", "SUMMARY:Exercise",
        "DTSTART;VALUE=DATE:20260303", "DUE;VALUE=DATE:20260303", "RRULE:FREQ=DAILY;COUNT=5",
        "X-APPLE-SORT-ORDER:5000", "END:VTODO",
    )

    private fun override(vararg extra: String) = listOf(
        "BEGIN:VTODO", "UID:gym", "DTSTAMP:20260101T000000Z", "SUMMARY:Exercise",
        "RECURRENCE-ID;VALUE=DATE:20260304", "DTSTART;VALUE=DATE:20260304", "DUE;VALUE=DATE:20260304",
        "STATUS:COMPLETED", *extra, "END:VTODO",
    )

    @Test
    fun `occurrences and an override without its own value take the master's`() {
        val tasks = parse(calendar(master, override()))
        assertEquals(5, tasks.size)
        tasks.forEach { assertEquals(it.id, 5000L, it.sortOrder) }
    }

    @Test
    fun `an override keeps its own value`() {
        val tasks = parse(calendar(master, override("X-APPLE-SORT-ORDER:10")))
        assertEquals(10L, tasks.single { it.due == LocalDate.of(2026, 3, 4) }.sortOrder)
    }

    @Test
    fun `completing an occurrence keeps the master's value and does not copy it`() {
        val original = calendar(master)
        val occurrence = parse(original).single { it.due == LocalDate.of(2026, 3, 5) }
        assertEquals(5000L, occurrence.sortOrder)

        val out = patcher.patchTask(
            original,
            occurrence.copy(done = true, status = "COMPLETED", percentComplete = 100, completedAt = now),
            now,
        )!!

        assertEquals(listOf("X-APPLE-SORT-ORDER:5000"), sortOrderLines(out))
        val completed = parse(out).single { it.due == LocalDate.of(2026, 3, 5) }
        assertEquals(true, completed.done)
        assertEquals(5000L, completed.sortOrder)
    }

    // Ordering

    @Test
    fun `a day lists ordered tasks first, then unordered ones by title, and times still win`() {
        val tasks = listOf(
            task("b-none"),
            task("timed", sortOrder = 1, time = LocalTime.of(8, 30)),
            task("z-first", sortOrder = 1000),
            task("a-none"),
            task("y-second", sortOrder = 813528420),
        )
        assertEquals(
            listOf("z-first", "y-second", "a-none", "b-none", "timed"),
            tasksDueOn(tasks, day).map { it.id },
        )
    }

    @Test
    fun `never moves a task to another day`() {
        val tasks = listOf(
            task("tomorrow", sortOrder = 1, due = day.plusDays(1)),
            task("today"),
        )
        assertEquals(listOf("today", "tomorrow"), orderTasksByDue(tasks).map { it.id })
    }

    @Test
    fun `siblings follow their order under the parent`() {
        val tasks = listOf(
            task("parent"),
            task("c-none", parent = "parent"),
            task("b", sortOrder = 2000, parent = "parent"),
            task("a", sortOrder = 1000, parent = "parent"),
        )
        assertEquals(listOf("parent", "a", "b", "c-none"), orderTasksByDue(tasks).map { it.id })
        assertEquals(listOf("a", "b", "c-none"), TaskTree(tasks).directChildren("parent").map { it.id })
    }

    @Test
    fun `week tasks order within their range only`() {
        fun week(id: String, start: LocalDate, sortOrder: Long? = null) =
            task(id, sortOrder = sortOrder, due = start.plusDays(6)).copy(startDate = start)
        val monday = LocalDate.of(2026, 3, 2)
        val tasks = listOf(
            week("z-next", monday.plusDays(7), sortOrder = 1),
            week("y", monday),
            week("x", monday, sortOrder = 50),
        )
        assertEquals(listOf("x", "y", "z-next"), weekTasksInRange(tasks, monday, monday.plusDays(13)).map { it.id })
    }

    // New subtasks

    @Test
    fun `a new subtask lands after its ordered siblings`() {
        val siblings = listOf(task("a", sortOrder = 813528420, parent = "p"), task("b", sortOrder = 3, parent = "p"))
        assertEquals(813528420L + SortOrderStep, nextSubtaskSortOrder(siblings))
        assertEquals(
            813528420L + SortOrderStep,
            NewTask(title = "new", parentTaskId = "p").withNewSubtaskOrder(siblings + task("other", sortOrder = 9_999_999_999)).sortOrder,
        )
    }

    @Test
    fun `a new subtask stays unordered when no sibling is ordered, and top-level tasks are untouched`() {
        assertNull(nextSubtaskSortOrder(emptyList()))
        assertNull(NewTask(title = "new", parentTaskId = "p").withNewSubtaskOrder(listOf(task("a", parent = "p"))).sortOrder)
        assertNull(NewTask(title = "top").withNewSubtaskOrder(listOf(task("a", sortOrder = 5))).sortOrder)
    }
}
