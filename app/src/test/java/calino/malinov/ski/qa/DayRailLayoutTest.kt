package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.formatCalinoDuration
import calino.malinov.ski.util.layoutDayRail
import calino.malinov.ski.util.eventOnDayRail
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The day rail's column assignment, the clock preference, and the duration
 * copy. All three used to be wrong in a way a reader sees immediately: two
 * overlapping events drawn on top of each other, one hard-coded 12-hour clock,
 * and "90 min" where "1 h 30 min" belongs.
 */
class DayRailLayoutTest {

    private val monday = LocalDate.of(2026, 5, 18)
    private fun layout(events: List<CalEvent>) = layoutDayRail(events, monday)

    private fun event(id: String, hour: Int, minute: Int = 0, minutes: Int? = 60) = CalEvent(
        id = id,
        title = id,
        color = 0xFF5B7FB5,
        start = LocalDateTime.of(2026, 5, 18, hour, minute),
        durationMinutes = minutes,
        calendarId = "personal",
    )

    @Test
    fun overlappingEvents_takeSeparateColumns() {
        val slots = layout(listOf(event("a", 9), event("b", 9, 30)))
        assertEquals(2, slots.size)
        assertTrue(slots.all { it.columns == 2 })
        assertEquals(setOf(0, 1), slots.map { it.column }.toSet())
    }

    @Test
    fun sequentialEvents_eachKeepTheFullWidth() {
        val slots = layout(listOf(event("a", 9), event("b", 10), event("c", 14)))
        assertTrue(slots.all { it.columns == 1 && it.column == 0 })
    }

    @Test
    fun touchingEvents_doNotCountAsAnOverlap() {
        // 9:00-10:00 and 10:00-11:00 share only a boundary.
        val slots = layout(listOf(event("a", 9), event("b", 10)))
        assertTrue(slots.all { it.columns == 1 })
    }

    @Test
    fun clustersAreSizedIndependently() {
        val slots = layout(
            listOf(event("a", 9), event("b", 9, 30), event("c", 15)),
        ).associateBy { it.event.id }
        assertEquals(2, slots.getValue("a").columns)
        assertEquals(2, slots.getValue("b").columns)
        // The lone afternoon event must not inherit the morning's split.
        assertEquals(1, slots.getValue("c").columns)
    }

    @Test
    fun threeWayOverlap_splitsIntoThree() {
        val slots = layout(listOf(event("a", 9, 0, 180), event("b", 9, 30), event("c", 10)))
        assertTrue(slots.all { it.columns == 3 })
        assertEquals(listOf(0, 1, 2), slots.map { it.column }.sorted())
    }

    @Test
    fun simultaneousEvents_putTheLongestInTheLeftmostColumn() {
        val slots = layout(listOf(event("short", 9, minutes = 30), event("long", 9, minutes = 180)))
            .associateBy { it.event.id }

        assertEquals(0, slots.getValue("long").column)
        assertEquals(1, slots.getValue("short").column)
    }

    @Test
    fun allDayEvents_leaveTheRail() {
        val allDay = event("a", 0, minutes = null).copy(allDay = true)
        assertTrue(layout(listOf(allDay)).isEmpty())
    }

    @Test
    fun shortEventsGetAReadableBlock() {
        val slot = layout(listOf(event("a", 9, 0, 5))).single()
        assertTrue(slot.endMinute - slot.startMinute >= 20)
    }

    @Test fun timedSpanUsesItsActualBoundsOnEachDay() {
        val spanning = event("span", 16, 15, 49 * 60)
        val expected = listOf(975 to 1440, 0 to 1440, 0 to 1035)
        expected.forEachIndexed { offset, (start, end) ->
            val day = monday.plusDays(offset.toLong())
            val slot = layoutDayRail(listOf(spanning), day).single()
            assertEquals(start, slot.startMinute)
            assertEquals(end, slot.endMinute)
            assertSame("Callbacks retain the whole event", spanning, slot.event)
            val display = eventOnDayRail(spanning, day)!!
            assertEquals(day.atStartOfDay().plusMinutes(start.toLong()), display.start)
            assertEquals(end - start, display.durationMinutes)
        }
        assertTrue(layoutDayRail(listOf(spanning), monday.minusDays(1)).isEmpty())
        assertTrue(layoutDayRail(listOf(spanning), monday.plusDays(3)).isEmpty())
    }

    @Test fun midnightEndDoesNotOccupyTheNextDay() {
        val spanning = event("overnight", 22, minutes = 26 * 60)
        val last = layoutDayRail(listOf(spanning), monday.plusDays(1)).single()
        assertEquals(0, last.startMinute)
        assertEquals(1440, last.endMinute)
        assertNull(eventOnDayRail(spanning, monday.plusDays(2)))
        assertTrue(layoutDayRail(listOf(spanning), monday.plusDays(2)).isEmpty())
    }

    @Test fun continuationOverlapsMorningButReleasesTheRailAfterItsEnd() {
        val overnight = event("overnight", 23, minutes = 10 * 60)
        val nextDay = monday.plusDays(1)
        val morning = event("morning", 8).copy(start = nextDay.atTime(8, 0))
        val later = event("later", 10).copy(start = nextDay.atTime(10, 0))
        val slots = layoutDayRail(listOf(overnight, morning, later), nextDay).associateBy { it.event.id }
        assertEquals(2, slots.getValue("overnight").columns)
        assertEquals(2, slots.getValue("morning").columns)
        assertEquals(1, slots.getValue("later").columns)
    }

    @Test fun recurringSpanClipsRelativeToTheOccurrenceRatherThanTheMaster() {
        val series = event("weekly", 16, 15, 49 * 60).copy(recurrence = "FREQ=WEEKLY;BYDAY=MO")
        val followingWeek = monday.plusWeeks(1)
        listOf(975 to 1440, 0 to 1440, 0 to 1035).forEachIndexed { offset, bounds ->
            val slot = layoutDayRail(listOf(series), followingWeek.plusDays(offset.toLong())).single()
            assertEquals(bounds.first, slot.startMinute)
            assertEquals(bounds.second, slot.endMinute)
        }
    }

    @Test
    fun durations_readAsHoursAndMinutes() {
        assertEquals("1 h 30 min", formatCalinoDuration(90))
        assertEquals("45 min", formatCalinoDuration(45))
        assertEquals("2 h", formatCalinoDuration(120))
        assertEquals("4 h 5 min", formatCalinoDuration(245))
        assertEquals("0 min", formatCalinoDuration(0))
        // A span past midnight is named in days rather than counted in hours.
        assertEquals("1 d", formatCalinoDuration(24 * 60))
        assertEquals("1 d 2 h", formatCalinoDuration(26 * 60))
        assertEquals("1 d 30 min", formatCalinoDuration(24 * 60 + 30))
        assertEquals("2 d", formatCalinoDuration(48 * 60))
        assertEquals("23 h", formatCalinoDuration(23 * 60))
    }

    @Test
    fun bothClocksFormatTheSameMoment() {
        val time = LocalTime.of(14, 5)
        assertEquals("2:05 PM", CalinoTimeFormat.TwelveHour.format(time))
        assertEquals("14:05", CalinoTimeFormat.TwentyFourHour.format(time))
        assertEquals("2 PM", CalinoTimeFormat.TwelveHour.formatHour(14))
        assertEquals("14:00", CalinoTimeFormat.TwentyFourHour.formatHour(14))
        assertEquals(CalinoTimeFormat.TwelveHour, CalinoTimeFormat.fromName("TwelveHour"))
        assertEquals(CalinoTimeFormat.Default, CalinoTimeFormat.fromName("nonsense"))
    }
}
