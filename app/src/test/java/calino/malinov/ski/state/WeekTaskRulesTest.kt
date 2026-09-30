package calino.malinov.ski.state

import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.caldav.ICalWriter
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.model.editorDraftFor
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.startOfWeek
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WeekTaskRulesTest {
    private val monday = LocalDate.of(2026, 5, 18)
    private val task = CalTask("week", "Call plumber", 1L, monday.plusDays(6), startDate = monday)

    @Test fun qualificationUsesInclusiveDaysAndExcludesEveryRecurrenceIdentity() {
        assertTrue(task.isWeekTask())
        assertFalse(task.copy(due = monday.plusDays(1)).isWeekTask())
        assertTrue(task.copy(due = monday.plusDays(2)).isWeekTask())
        listOf(task.copy(startDate = null), task.copy(due = null), task.copy(due = monday.minusDays(1)),
            task.copy(recurrence = "FREQ=WEEKLY"), task.copy(recurrenceId = Instant.EPOCH),
            task.copy(recurrenceDate = monday)).forEach { assertFalse(it.isWeekTask()) }
    }

    @Test fun overlapIncludesBothWeeksAndKeepsCompletedTasksInStableOrder() {
        val crossing = task.copy(id = "cross", title = "Flights", startDate = monday.plusDays(3), due = monday.plusDays(9))
        val completed = task.copy(id = "done", title = "Birthday gift", done = true)
        assertEquals(listOf("done", "week", "cross"), weekTasksInRange(listOf(crossing, task, completed), monday, monday.plusDays(6)).map { it.id })
        assertEquals(listOf("cross"), weekTasksInRange(listOf(task, crossing), monday.plusDays(7), monday.plusDays(13)).map { it.id })
        assertEquals(monday, monday.startOfWeek(CalinoWeekStart.Monday))
        assertEquals(monday.minusDays(1), monday.startOfWeek(CalinoWeekStart.Sunday))
    }

    @Test fun tasksListShowsOnlyOpenWeekTasksOverlappingTheWeekAsSometimeThisWeek() {
        val last = monday.plusDays(6)
        assertTrue(task.isSometimeThisWeek(monday, last))
        assertFalse(task.copy(done = true).isSometimeThisWeek(monday, last))
        assertFalse(task.copy(due = monday.plusDays(1)).isSometimeThisWeek(monday, last))
        assertTrue(task.copy(startDate = monday.plusDays(5), due = monday.plusDays(9)).isSometimeThisWeek(monday, last))
        assertFalse(task.copy(startDate = monday.plusDays(7), due = monday.plusDays(10)).isSometimeThisWeek(monday, last))
        assertFalse(task.copy(startDate = monday.minusDays(5), due = monday.minusDays(1)).isSometimeThisWeek(monday, last))
    }

    @Test fun schedulingCollapsesRangeAndPreservesTaskContentsAndIdentity() {
        val original = task.copy(notes = "Keep this", priority = 1, percentComplete = 50, status = "IN-PROCESS",
            reminder = Reminder(minutesBefore = 10), uid = "uid", href = "https://example.test/t", etag = "etag", parentTaskId = "parent")
        val scheduled = original.scheduledTask(monday.plusDays(1), LocalTime.of(10, 30))
        assertEquals(editorDraftFor(original, monday).toNewTask().copy(due = monday.plusDays(1), dueTime = LocalTime.of(10, 30), startDate = null, startTime = null), scheduled)
        val range = original.weekTask(monday.plusDays(7), monday.plusDays(13))
        assertEquals(monday.plusDays(7), range.startDate)
        assertNull(range.startTime)
        assertNull(range.dueTime)
        assertEquals(original.notes, range.notes)
    }

    @Test(expected = IllegalArgumentException::class) fun recurringTaskCannotMoveToShelf() {
        task.copy(recurrence = "FREQ=WEEKLY").weekTask(monday, monday.plusDays(6))
    }

    @Test fun pointerSnapFloorsQuarterHoursAndUsesScrolledPosition() {
        assertEquals(630, taskDropMinute(10.6f * 60, 0, 60f))
        assertEquals(645, taskDropMinute(10.999f * 60, 0, 60f))
        assertEquals(630, taskDropMinute(.6f * 60, 600, 60f))
        assertEquals(0, taskDropMinute(-100f, 0, 60f))
        assertEquals(1425, taskDropMinute(99999f, 0, 60f))
    }

    @Test fun dateOnlyRangeRoundTripsWithoutVendorPropertiesOrEndOfDayTime() {
        val zone = ZoneId.of("Europe/Copenhagen")
        val writer = ICalWriter(zone)
        val text = writer.buildCalendar(listOf(writer.writeTask(task, now = Instant.EPOCH)))
        assertTrue(text.contains("DTSTART;VALUE=DATE:20260518"))
        assertTrue(text.contains("DUE;VALUE=DATE:20260524"))
        val back = ICalMapper(zone).parse(text, calendarId = "personal", color = 1, href = "https://example.test/t").tasks.single()
        assertTrue(back.isWeekTask())
        assertEquals(task.startDate, back.startDate)
        assertEquals(task.due, back.due)
    }

    @Test fun timedRangeQualifiesAfterConversionToDisplayTimezone() {
        val text = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VTODO
UID:shift
SUMMARY:Shifted
DTSTART:20260517T233000Z
DUE:20260520T003000Z
END:VTODO
END:VCALENDAR"""
        val back = ICalMapper(ZoneId.of("Europe/Copenhagen")).parse(text, calendarId = "personal", color = 1, href = "https://example.test/t").tasks.single()
        assertEquals(monday, back.startDate)
        assertEquals(monday.plusDays(2), back.due)
        assertTrue(back.isWeekTask())
    }
}
