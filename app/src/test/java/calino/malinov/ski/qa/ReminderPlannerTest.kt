package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.notify.ReminderKind
import calino.malinov.ski.notify.ReminderPlanOptions
import calino.malinov.ski.notify.ReminderPlanner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What fires, and when.
 *
 * Everything here is fixed in time on purpose: a planner that depends on the
 * wall clock is a test that fails once a year, in March, for reasons nobody
 * remembers. The zone is Copenhagen because that is where the DST case has to
 * be, and using it throughout keeps the arithmetic in one frame of reference.
 */
class ReminderPlannerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Copenhagen")

    /** Monday 14 September 2026, 08:00 local. */
    private val now: Instant = LocalDateTime.of(2026, 9, 14, 8, 0).atZone(zone).toInstant()

    private val visible = setOf("work", "personal")

    private fun instantAt(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    private fun plan(
        events: List<CalEvent> = emptyList(),
        tasks: List<CalTask> = emptyList(),
        options: ReminderPlanOptions = ReminderPlanOptions(),
        calendars: Set<String> = visible,
        at: Instant = now,
    ) = ReminderPlanner.plan(events, tasks, calendars, at, zone, options)

    @Test
    fun `a timed reminder fires its lead time before the start`() {
        val start = LocalDateTime.of(2026, 9, 14, 10, 0)
        val firings = plan(events = listOf(event(start = start, reminders = listOf(Reminder(10)))))

        assertEquals(1, firings.size)
        val firing = firings.single()
        assertEquals(ReminderKind.Event, firing.kind)
        assertEquals("evt-1", firing.recordId)
        assertEquals(instantAt(start.toLocalDate(), LocalTime.of(9, 50)), firing.at)
        assertEquals(instantAt(start.toLocalDate(), LocalTime.of(10, 0)), firing.anchor)
        assertEquals(start.toLocalDate().toEpochDay(), firing.occurrenceDay)
        assertEquals(10, firing.minutesBefore)
    }

    @Test
    fun `two reminders on one event become two firings, earliest first`() {
        val start = LocalDateTime.of(2026, 9, 14, 14, 0)
        val firings = plan(events = listOf(event(start = start, reminders = listOf(Reminder(10), Reminder(60)))))

        assertEquals(2, firings.size)
        assertEquals(2, firings.map { it.key }.toSet().size)
        assertEquals(listOf(60, 10), firings.map { it.minutesBefore })
        assertTrue(firings[0].at.isBefore(firings[1].at))
    }

    @Test
    fun `an all-day event anchors at the all-day hour, not midnight`() {
        val day = LocalDate.of(2026, 9, 15)
        val firings = plan(
            events = listOf(
                event(start = null, allDay = true, date = day, reminders = listOf(Reminder(0))),
            ),
        )

        assertEquals(instantAt(day, LocalTime.of(9, 0)), firings.single().at)
        assertEquals(instantAt(day, LocalTime.of(9, 0)), firings.single().anchor)
    }

    @Test
    fun `already expanded occurrences are used as they are, not re-expanded`() {
        val first = LocalDateTime.of(2026, 9, 14, 9, 30)
        val second = LocalDateTime.of(2026, 9, 16, 9, 30)
        val firings = plan(
            events = listOf(
                event(id = "uid-1@2026-09-14T07:30:00Z", start = first, reminders = listOf(Reminder(15))),
                event(id = "uid-1@2026-09-16T07:30:00Z", start = second, reminders = listOf(Reminder(15))),
            ),
        )

        assertEquals(2, firings.size)
        assertEquals(
            listOf(
                instantAt(first.toLocalDate(), LocalTime.of(9, 15)),
                instantAt(second.toLocalDate(), LocalTime.of(9, 15)),
            ),
            firings.map { it.at },
        )
    }

    @Test
    fun `an unexpanded weekly master produces one firing per matching day in the horizon`() {
        // Mondays inside a week of Monday 14 September: the 14th itself is
        // already past at 08:00 for an 07:00 event, so anchor the series later
        // in the day and expect the 14th and the 21st to be out of range.
        val start = LocalDateTime.of(2026, 9, 7, 17, 0)
        val firings = plan(
            events = listOf(
                event(start = start, recurrence = "FREQ=WEEKLY;BYDAY=MO", reminders = listOf(Reminder(30))),
            ),
        )

        assertEquals(1, firings.size)
        assertEquals(instantAt(LocalDate.of(2026, 9, 14), LocalTime.of(16, 30)), firings.single().at)
    }

    @Test
    fun `a task anchors at its due time, or at the all-day hour without one`() {
        val due = LocalDate.of(2026, 9, 15)
        val timed = plan(tasks = listOf(task(due = due, dueTime = LocalTime.of(17, 0), reminder = Reminder(30))))
        assertEquals(instantAt(due, LocalTime.of(16, 30)), timed.single().at)

        val dateOnly = plan(tasks = listOf(task(due = due, reminder = Reminder(0))))
        assertEquals(instantAt(due, LocalTime.of(9, 0)), dateOnly.single().at)
        assertEquals(ReminderKind.Task, dateOnly.single().kind)
    }

    @Test
    fun `a task with no date, and a completed task, produce nothing`() {
        assertTrue(plan(tasks = listOf(task(due = null, reminder = Reminder(10)))).isEmpty())
        assertTrue(
            plan(
                tasks = listOf(task(due = LocalDate.of(2026, 9, 15), reminder = Reminder(10), done = true)),
            ).isEmpty(),
        )
    }

    @Test
    fun `a firing in the past, or exactly now, is excluded`() {
        val past = LocalDateTime.of(2026, 9, 14, 7, 0)
        assertTrue(plan(events = listOf(event(start = past, reminders = listOf(Reminder(10))))).isEmpty())

        // Lead time chosen so the firing lands exactly on `now`.
        val boundary = LocalDateTime.of(2026, 9, 14, 8, 30)
        assertTrue(plan(events = listOf(event(start = boundary, reminders = listOf(Reminder(30))))).isEmpty())
    }

    @Test
    fun `a record on a hidden calendar is excluded`() {
        val start = LocalDateTime.of(2026, 9, 14, 10, 0)
        val events = listOf(event(start = start, calendarId = "hidden", reminders = listOf(Reminder(10))))
        assertTrue(plan(events = events).isEmpty())
        assertEquals(1, plan(events = events, calendars = visible + "hidden").size)
    }

    @Test
    fun `the horizon excludes a reminder beyond it`() {
        val start = LocalDateTime.of(2026, 9, 20, 10, 0)
        assertEquals(1, plan(events = listOf(event(start = start, reminders = listOf(Reminder(10))))).size)
        assertTrue(
            plan(
                events = listOf(event(start = start, reminders = listOf(Reminder(10)))),
                options = ReminderPlanOptions(horizon = Duration.ofDays(2)),
            ).isEmpty(),
        )
    }

    @Test
    fun `the cap keeps the earliest firings`() {
        val events = (1..5).map { day ->
            event(
                id = "evt-$day",
                start = LocalDateTime.of(2026, 9, 14 + day - 1, 12, 0),
                reminders = listOf(Reminder(10)),
            )
        }
        val firings = plan(events = events, options = ReminderPlanOptions(maxFirings = 2))

        assertEquals(2, firings.size)
        assertEquals(listOf("evt-1", "evt-2"), firings.map { it.recordId })
    }

    @Test
    fun `the same input plans the same list twice`() {
        val events = listOf(
            event(id = "evt-b", start = LocalDateTime.of(2026, 9, 15, 9, 0), reminders = listOf(Reminder(10))),
            event(id = "evt-a", start = LocalDateTime.of(2026, 9, 15, 9, 0), reminders = listOf(Reminder(10))),
        )
        assertEquals(plan(events = events), plan(events = events))
    }

    @Test
    fun `a reminder across the spring-forward boundary keeps its wall clock`() {
        // Copenhagen moves 02:00 to 03:00 on 29 March 2026. An event at 10:00
        // that morning is 08:00 UTC, not 09:00.
        val springForward = LocalDate.of(2026, 3, 29)
        val at = LocalDateTime.of(springForward, LocalTime.of(10, 0))
        val before = LocalDateTime.of(2026, 3, 28, 8, 0).atZone(zone).toInstant()
        val firings = plan(
            events = listOf(event(start = at, reminders = listOf(Reminder(10)))),
            at = before,
        )

        assertEquals(Instant.parse("2026-03-29T07:50:00Z"), firings.single().at)
        assertEquals(Instant.parse("2026-03-29T08:00:00Z"), firings.single().anchor)
    }

    @Test
    fun `each switch suppresses only its own kind`() {
        val events = listOf(event(start = LocalDateTime.of(2026, 9, 14, 10, 0), reminders = listOf(Reminder(10))))
        val tasks = listOf(task(due = LocalDate.of(2026, 9, 15), reminder = Reminder(10)))

        val withoutEvents = plan(events, tasks, ReminderPlanOptions(eventRemindersEnabled = false))
        assertEquals(listOf(ReminderKind.Task), withoutEvents.map { it.kind })

        val withoutTasks = plan(events, tasks, ReminderPlanOptions(taskRemindersEnabled = false))
        assertEquals(listOf(ReminderKind.Event), withoutTasks.map { it.kind })
    }

    private fun event(
        id: String = "evt-1",
        start: LocalDateTime? = null,
        allDay: Boolean = false,
        date: LocalDate? = null,
        recurrence: String? = null,
        calendarId: String = "work",
        reminders: List<Reminder> = emptyList(),
    ) = CalEvent(
        id = id,
        title = "Design review",
        color = 0xFF5B7FB5,
        start = start,
        durationMinutes = if (allDay) null else 60,
        allDay = allDay,
        date = date,
        recurrence = recurrence,
        location = "Studio",
        calendarId = calendarId,
        reminders = reminders,
        uid = id.substringBefore('@'),
    )

    private fun task(
        id: String = "task-1",
        due: LocalDate?,
        dueTime: LocalTime? = null,
        reminder: Reminder? = null,
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
        reminder = reminder,
        uid = id,
        calendarId = calendarId,
    )
}
