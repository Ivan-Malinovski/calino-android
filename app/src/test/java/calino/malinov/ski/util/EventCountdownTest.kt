package calino.malinov.ski.util

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventCountdownTest {
    private val now = LocalDateTime.of(2026, 10, 15, 9, 0)

    private fun until(hours: Long, minutes: Long = 0) = eventCountdown(now.plusHours(hours).plusMinutes(minutes), now)

    @Test
    fun givesDaysAndHoursUpToThreeDaysOut() {
        assertEquals(EventCountdown(days = 1, hours = 1), until(25))
        assertEquals(EventCountdown(days = 2, hours = 23), until(71, 59))
        assertEquals(EventCountdown(days = 1, hours = 0), until(24, 30))
    }

    @Test
    fun dropsTheHoursFromThreeDaysOn() {
        assertEquals(EventCountdown(days = 3, hours = 0), until(72))
        assertEquals(EventCountdown(days = 3, hours = 0), until(76))
        assertEquals(EventCountdown(days = 12, hours = 0), until(288, 59))
    }

    @Test
    fun givesJustHoursInsideTheLastDayUntilTheLastTwoHours() {
        assertEquals(EventCountdown(days = 0, hours = 3), until(3, 40))
        assertEquals(EventCountdown(days = 0, hours = 23), until(23, 59))
        assertEquals(EventCountdown(days = 0, hours = 2), until(2))
        assertEquals(EventCountdown(days = 0, hours = 2), until(2, 59))
        assertTrue(until(3)!!.soon)
        assertFalse(until(24)!!.soon)
    }

    @Test
    fun addsMinutesOnceUnderTwoHours() {
        assertEquals(EventCountdown(days = 0, hours = 1, minutes = 59), until(1, 59))
        assertEquals(EventCountdown(days = 0, hours = 1, minutes = 25), until(1, 25))
        assertEquals(EventCountdown(days = 0, hours = 1, minutes = 0), until(1))
        assertEquals(EventCountdown(days = 0, hours = 0, minutes = 45), until(0, 45))
        assertEquals(EventCountdown(days = 0, hours = 0, minutes = 1), until(0, 1))
    }

    @Test
    fun aStartUnderAMinuteAwayStillCounts() {
        assertEquals(EventCountdown(days = 0, hours = 0, minutes = 1), eventCountdown(now.plusSeconds(20), now))
    }

    @Test
    fun hasNoCountdownOnceStartedOrWithoutAStart() {
        assertNull(eventCountdown(now, now))
        assertNull(eventCountdown(now.minusMinutes(5), now))
        assertNull(eventCountdown(null, now))
    }
}
