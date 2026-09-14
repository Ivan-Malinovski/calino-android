package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.widget.WidgetAgendaBuilder
import calino.malinov.ski.widget.WidgetAgendaOptions
import calino.malinov.ski.widget.WidgetContent
import calino.malinov.ski.widget.WidgetRowKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the home screen widget shows.
 *
 * Fixed in time for the same reason the reminder planner's tests are: a widget
 * that depends on the wall clock is a test that fails once a year. The rules
 * being pinned here are mostly rules the calendar surfaces already have, and
 * that is the point -- the widget disagreeing with the grid behind it is the
 * failure this file exists to catch.
 */
class WidgetAgendaTest {

    /** Monday 14 September 2026. */
    private val today: LocalDate = LocalDate.of(2026, 9, 14)

    private val calendars = listOf(
        CalinoCalendar(id = "work", name = "Work", color = 0xFF5B7FB5),
        CalinoCalendar(id = "personal", name = "Personal", color = 0xFFC2697F),
    )

    @Test
    fun `events come before tasks on the same day, all-day before timed`() {
        val agenda = build(
            events = listOf(
                event(id = "timed", start = today.atTime(14, 0)),
                event(id = "allday", allDay = true, date = today),
            ),
            tasks = listOf(task(id = "chore", due = today)),
        )

        val rows = agenda.days.single { it.date == today }.rows
        assertEquals(listOf("allday", "timed", "chore"), rows.map { it.recordId })
        assertEquals(
            listOf(WidgetRowKind.Event, WidgetRowKind.Event, WidgetRowKind.Task),
            rows.map { it.kind },
        )
    }

    @Test
    fun `an invisible calendar contributes neither events nor tasks`() {
        val agenda = build(
            calendars = listOf(
                CalinoCalendar(id = "work", name = "Work", color = 0L, visible = false),
                CalinoCalendar(id = "personal", name = "Personal", color = 0L),
            ),
            events = listOf(event(id = "hidden", start = today.atTime(9, 0), calendarId = "work")),
            tasks = listOf(task(id = "hidden-task", due = today, calendarId = "work")),
        )

        assertTrue(agenda.empty)
        assertTrue(agenda.days.single().rows.isEmpty())
    }

    @Test
    fun `showTasksInViews off drops the tasks but keeps the events`() {
        val agenda = build(
            calendars = listOf(
                CalinoCalendar(id = "work", name = "Work", color = 0L, showTasksInViews = false),
            ),
            events = listOf(event(id = "meeting", start = today.atTime(9, 0), calendarId = "work")),
            tasks = listOf(task(id = "chore", due = today, calendarId = "work")),
        )

        assertEquals(listOf("meeting"), agenda.days.single().rows.map { it.recordId })
    }

    @Test
    fun `a weekly recurrence lands on its own weekday across the window`() {
        val agenda = build(
            events = listOf(
                event(
                    id = "standup",
                    start = today.atTime(9, 30),
                    recurrence = "FREQ=WEEKLY;BYDAY=MO",
                ),
            ),
            options = options(dayCount = 8),
        )

        val withRows = agenda.days.filter { it.rows.isNotEmpty() }.map { it.date }
        assertEquals(listOf(today, today.plusDays(7)), withRows)
    }

    @Test
    fun `a multi-day event appears on every day it covers, timed only on the first`() {
        val agenda = build(
            events = listOf(
                event(id = "offsite", allDay = true, date = today, endDate = today.plusDays(2)),
            ),
            options = options(dayCount = 4),
        )

        val days = agenda.days.filter { it.rows.isNotEmpty() }.map { it.date }
        assertEquals((0L..2L).map(today::plusDays), days)
        assertTrue(agenda.days.flatMap { it.rows }.all { it.allDay })
    }

    @Test
    fun `a timed event spanning midnight reads as all-day on its second day`() {
        val agenda = build(
            events = listOf(
                event(id = "night", start = today.atTime(22, 0), durationMinutes = 300),
            ),
            options = options(dayCount = 2),
        )

        val first = agenda.days.first().rows.single()
        val second = agenda.days.last().rows.single()
        assertFalse(first.allDay)
        assertEquals("10:00 PM", first.timeLabel)
        assertTrue(second.allDay)
        assertNull(second.timeLabel)
    }

    @Test
    fun `completed tasks drop out only when the preference says so`() {
        val tasks = listOf(task(id = "done", due = today, done = true))

        assertEquals(listOf("done"), build(tasks = tasks).days.single().rows.map { it.recordId })
        assertTrue(
            build(tasks = tasks, options = options(hideCompletedTasks = true))
                .days.single().rows.isEmpty(),
        )
    }

    @Test
    fun `the row budget truncates across days rather than overflowing`() {
        val agenda = build(
            events = (1..5).map { event(id = "e$it", start = today.atTime(8 + it, 0)) },
            tasks = listOf(task(id = "tomorrow", due = today.plusDays(1))),
            options = options(dayCount = 3, maxRows = 3),
        )

        assertEquals(3, agenda.days.sumOf { it.rows.size })
        // The window stops where the budget runs out; a day nobody has room for
        // is not carried as an empty heading.
        assertEquals(listOf(today), agenda.days.map { it.date })
        assertFalse(agenda.empty)
    }

    @Test
    fun `an empty window is flagged once, with the days still present`() {
        val agenda = build(options = options(dayCount = 3))

        assertTrue(agenda.empty)
        assertEquals(3, agenda.days.size)
        assertTrue(agenda.days.all { it.rows.isEmpty() })
    }

    @Test
    fun `the time label follows the clock preference`() {
        val events = listOf(event(id = "e", start = today.atTime(14, 30)))

        assertEquals("2:30 PM", build(events = events).days.single().rows.single().timeLabel)
        assertEquals(
            "14:30",
            build(
                events = events,
                options = options(timeFormat = CalinoTimeFormat.TwentyFourHour),
            ).days.single().rows.single().timeLabel,
        )
    }

    @Test
    fun `locations are carried only when the preference allows them`() {
        val events = listOf(event(id = "e", start = today.atTime(9, 0)))

        assertEquals("Studio", build(events = events).days.single().rows.single().location)
        assertNull(
            build(events = events, options = options(showLocations = false))
                .days.single().rows.single().location,
        )
    }

    @Test
    fun `the next mark falls on the earliest event today that has not begun`() {
        val agenda = build(
            events = listOf(
                event(id = "standup", start = today.atTime(9, 30)),
                event(id = "review", start = today.atTime(11, 0)),
                event(id = "dentist", start = today.atTime(15, 0)),
            ),
            now = LocalTime.of(10, 42),
        )

        assertEquals(listOf("review"), agenda.days.single().rows.filter { it.isNext }.map { it.recordId })
    }

    @Test
    fun `an event already under way is not next, and neither is the one before it`() {
        val agenda = build(
            events = listOf(
                event(id = "running", start = today.atTime(10, 0), durationMinutes = 90),
                event(id = "later", start = today.atTime(15, 0)),
            ),
            now = LocalTime.of(10, 42),
        )

        // "Next" is the next thing to start, so the meeting in progress is
        // skipped rather than marked -- and so is nothing at all above it.
        assertEquals(listOf("later"), agenda.days.single().rows.filter { it.isNext }.map { it.recordId })
    }

    @Test
    fun `a task due later today is never the next thing`() {
        val agenda = build(
            tasks = listOf(task(id = "chore", due = today, dueTime = LocalTime.of(17, 0))),
            now = LocalTime.of(10, 42),
        )

        assertTrue(agenda.days.single().rows.none { it.isNext })
    }

    @Test
    fun `nothing is marked once the day's last event has started`() {
        val agenda = build(
            events = listOf(event(id = "standup", start = today.atTime(9, 30))),
            now = LocalTime.of(10, 42),
        )

        assertTrue(agenda.days.single().rows.none { it.isNext })
    }

    @Test
    fun `tomorrow's first event is not marked next`() {
        val agenda = build(
            events = listOf(event(id = "tomorrow", start = today.plusDays(1).atTime(9, 0))),
            options = options(dayCount = 2),
            now = LocalTime.of(10, 42),
        )

        assertTrue(agenda.days.flatMap { it.rows }.none { it.isNext })
    }

    @Test
    fun `no clock means no mark`() {
        val agenda = build(events = listOf(event(id = "review", start = today.atTime(11, 0))))

        assertTrue(agenda.days.single().rows.none { it.isNext })
    }

    @Test
    fun `a task widget drops the events and keeps the tasks`() {
        val agenda = build(
            events = listOf(event(id = "meeting", start = today.atTime(9, 0))),
            tasks = listOf(task(id = "chore", due = today)),
            options = options(content = WidgetContent.Tasks),
        )

        assertEquals(listOf("chore"), agenda.days.single().rows.map { it.recordId })
    }

    @Test
    fun `overdue tasks are gathered oldest first, and only for a task widget`() {
        val tasks = listOf(
            task(id = "old", due = today.minusDays(9)),
            task(id = "recent", due = today.minusDays(1)),
            task(id = "today", due = today),
        )

        val list = build(tasks = tasks, options = options(content = WidgetContent.Tasks))
        assertEquals(listOf("old", "recent"), list.overdue.map { it.recordId })
        assertEquals(listOf("today"), list.days.single().rows.map { it.recordId })

        // The agenda is a view of a day and never looks backwards.
        assertTrue(build(tasks = tasks).overdue.isEmpty())
    }

    @Test
    fun `a completed task is never overdue`() {
        val agenda = build(
            tasks = listOf(task(id = "done", due = today.minusDays(3), done = true)),
            options = options(content = WidgetContent.Tasks),
        )

        assertTrue(agenda.overdue.isEmpty())
        assertTrue(agenda.empty)
    }

    @Test
    fun `overdue stops at the lookback bound`() {
        val agenda = build(
            tasks = listOf(
                task(id = "ancient", due = today.minusDays(400)),
                task(id = "late", due = today.minusDays(2)),
            ),
            options = options(content = WidgetContent.Tasks),
        )

        assertEquals(listOf("late"), agenda.overdue.map { it.recordId })
    }

    @Test
    fun `an overdue row is labelled with its date rather than a time`() {
        val agenda = build(
            tasks = listOf(
                task(id = "late", due = LocalDate.of(2026, 8, 30), dueTime = LocalTime.of(17, 0)),
            ),
            options = options(content = WidgetContent.Tasks),
        )

        val row = agenda.overdue.single()
        assertEquals("Aug 30", row.timeLabel)
        assertNull(row.startTime)
    }

    @Test
    fun `overdue rows are spent from the same budget as the days`() {
        val agenda = build(
            tasks = listOf(
                task(id = "late-a", due = today.minusDays(2)),
                task(id = "late-b", due = today.minusDays(1)),
                task(id = "today", due = today),
            ),
            options = options(content = WidgetContent.Tasks, maxRows = 2),
        )

        assertEquals(listOf("late-a", "late-b"), agenda.overdue.map { it.recordId })
        assertTrue(agenda.days.all { it.rows.isEmpty() })
    }

    private fun build(
        calendars: List<CalinoCalendar> = this.calendars,
        events: List<CalEvent> = emptyList(),
        tasks: List<CalTask> = emptyList(),
        options: WidgetAgendaOptions = options(),
        now: LocalTime? = null,
    ) = WidgetAgendaBuilder.build(
        snapshot = CalinoSnapshot(
            events = events,
            tasks = tasks,
            journals = emptyList(),
            calendars = calendars,
        ),
        today = today,
        options = options,
        now = now,
    )

    private fun options(
        dayCount: Int = 1,
        maxRows: Int = 12,
        hideCompletedTasks: Boolean = false,
        showLocations: Boolean = true,
        timeFormat: CalinoTimeFormat = CalinoTimeFormat.TwelveHour,
        content: WidgetContent = WidgetContent.Agenda,
    ) = WidgetAgendaOptions(
        dayCount = dayCount,
        maxRows = maxRows,
        hideCompletedTasks = hideCompletedTasks,
        showLocations = showLocations,
        timeFormat = timeFormat,
        content = content,
    )

    private fun event(
        id: String,
        start: LocalDateTime? = null,
        durationMinutes: Int? = 60,
        allDay: Boolean = false,
        date: LocalDate? = null,
        endDate: LocalDate? = null,
        recurrence: String? = null,
        calendarId: String = "work",
    ) = CalEvent(
        id = id,
        title = "Design review",
        color = 0xFF5B7FB5,
        start = start,
        durationMinutes = if (allDay) null else durationMinutes,
        allDay = allDay,
        date = date,
        endDate = endDate,
        recurrence = recurrence,
        location = "Studio",
        calendarId = calendarId,
        uid = id,
    )

    private fun task(
        id: String,
        due: LocalDate?,
        dueTime: LocalTime? = null,
        done: Boolean = false,
        calendarId: String = "personal",
    ) = CalTask(
        id = id,
        title = "Buy flowers",
        color = 0xFFC2697F,
        due = due,
        done = done,
        category = "Personal",
        dueTime = dueTime,
        uid = id,
        calendarId = calendarId,
    )
}
