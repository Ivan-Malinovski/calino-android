package calino.malinov.ski.qa

import calino.malinov.ski.state.CalinoNow
import calino.malinov.ski.state.FixtureNow
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's present moment.
 *
 * The regression these guard: the day rail's current-time marker was a
 * hard-coded 11.33 hours, and "today" was frozen at the fixture date in four
 * separate places, so a connected account opened on the real date while every
 * Today control still pointed at May 2026.
 */
class CalinoClockTest {

    @Test
    fun `the fixture moment is the frozen sample date`() {
        assertEquals(LocalDate.of(2026, 5, 18), FixtureNow.today)
        assertEquals(LocalTime.of(11, 20), FixtureNow.time)
    }

    @Test
    fun `hourOfDay places the marker fractionally through the hour`() {
        assertEquals(0f, CalinoNow(FixtureNow.today, LocalTime.MIDNIGHT).hourOfDay, 0.0001f)
        assertEquals(9.5f, CalinoNow(FixtureNow.today, LocalTime.of(9, 30)).hourOfDay, 0.0001f)
        assertEquals(23.75f, CalinoNow(FixtureNow.today, LocalTime.of(23, 45)).hourOfDay, 0.0001f)
    }

    @Test
    fun `the marker never escapes the twenty-four hour rail`() {
        (0..23).forEach { hour ->
            listOf(0, 30, 59).forEach { minute ->
                val fraction = CalinoNow(FixtureNow.today, LocalTime.of(hour, minute)).hourOfDay
                assertTrue("$hour:$minute placed at $fraction", fraction >= 0f && fraction < 24f)
            }
        }
    }

    @Test
    fun `dateTime combines the tracked day and time`() {
        val now = CalinoNow(LocalDate.of(2026, 9, 9), LocalTime.of(16, 4))
        assertEquals(LocalDate.of(2026, 9, 9).atTime(16, 4), now.dateTime)
    }
}
