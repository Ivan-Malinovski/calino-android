package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.util.CalinoTimeFormat
import calino.malinov.ski.poc.util.formatCalinoDuration
import calino.malinov.ski.poc.util.layoutDayRail
import org.junit.Assert.assertEquals
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
        val slots = layoutDayRail(listOf(event("a", 9), event("b", 9, 30)))
        assertEquals(2, slots.size)
        assertTrue(slots.all { it.columns == 2 })
        assertEquals(setOf(0, 1), slots.map { it.column }.toSet())
    }

    @Test
    fun sequentialEvents_eachKeepTheFullWidth() {
        val slots = layoutDayRail(listOf(event("a", 9), event("b", 10), event("c", 14)))
        assertTrue(slots.all { it.columns == 1 && it.column == 0 })
    }

    @Test
    fun touchingEvents_doNotCountAsAnOverlap() {
        // 9:00-10:00 and 10:00-11:00 share only a boundary.
        val slots = layoutDayRail(listOf(event("a", 9), event("b", 10)))
        assertTrue(slots.all { it.columns == 1 })
    }

    @Test
    fun clustersAreSizedIndependently() {
        val slots = layoutDayRail(
            listOf(event("a", 9), event("b", 9, 30), event("c", 15)),
        ).associateBy { it.event.id }
        assertEquals(2, slots.getValue("a").columns)
        assertEquals(2, slots.getValue("b").columns)
        // The lone afternoon event must not inherit the morning's split.
        assertEquals(1, slots.getValue("c").columns)
    }

    @Test
    fun threeWayOverlap_splitsIntoThree() {
        val slots = layoutDayRail(listOf(event("a", 9, 0, 180), event("b", 9, 30), event("c", 10)))
        assertTrue(slots.all { it.columns == 3 })
        assertEquals(listOf(0, 1, 2), slots.map { it.column }.sorted())
    }

    @Test
    fun allDayEvents_leaveTheRail() {
        val allDay = event("a", 0, minutes = null).copy(allDay = true)
        assertTrue(layoutDayRail(listOf(allDay)).isEmpty())
    }

    @Test
    fun shortEventsGetAReadableBlock() {
        val slot = layoutDayRail(listOf(event("a", 9, 0, 5))).single()
        assertTrue(slot.endMinute - slot.startMinute >= 20)
    }

    @Test
    fun durations_readAsHoursAndMinutes() {
        assertEquals("1 h 30 min", formatCalinoDuration(90))
        assertEquals("45 min", formatCalinoDuration(45))
        assertEquals("2 h", formatCalinoDuration(120))
        assertEquals("4 h 5 min", formatCalinoDuration(245))
        assertEquals("0 min", formatCalinoDuration(0))
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
