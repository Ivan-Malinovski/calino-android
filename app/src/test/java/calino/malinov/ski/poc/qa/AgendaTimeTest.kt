package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.ui.components.splitMeridiem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The agenda gutter measures the numerals and the meridiem as separate slots
 * so the colons line up down the list. Everything hangs off this split, so it
 * has to leave a 24-hour face and the all-day label alone.
 */
class AgendaTimeTest {

    @Test
    fun `a twelve hour face splits off its meridiem`() {
        assertEquals("8:00" to "AM", splitMeridiem("8:00 AM"))
        assertEquals("12:30" to "PM", splitMeridiem("12:30 PM"))
    }

    @Test
    fun `a twenty four hour face has no meridiem`() {
        assertEquals("14:00" to null, splitMeridiem("14:00"))
        assertEquals("08:00" to null, splitMeridiem("08:00"))
    }

    @Test
    fun `the all-day label is not mistaken for a clock face`() {
        assertEquals("ALL-DAY" to null, splitMeridiem("ALL-DAY"))
    }

    @Test
    fun `a trailing word that is not a meridiem stays put`() {
        assertEquals("Due soon" to null, splitMeridiem("Due soon"))
        assertEquals("8:00 XM" to null, splitMeridiem("8:00 XM"))
        assertEquals("" to null, splitMeridiem(""))
        assertEquals(" AM" to null, splitMeridiem(" AM"))
    }
}
