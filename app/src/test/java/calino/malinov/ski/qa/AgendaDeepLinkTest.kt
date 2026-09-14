package calino.malinov.ski.qa

import calino.malinov.ski.notify.AgendaDeepLinks
import calino.malinov.ski.notify.ReminderDeepLinks
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The widget's "open the calendar on this date" link.
 *
 * It shares a scheme with the reminder links, so the cases that matter are the
 * ones where the two could be confused for each other: `MainActivity` offers
 * every incoming VIEW intent to both parsers, and a link answered by both would
 * fight over the route.
 */
class AgendaDeepLinkTest {

    private val day = LocalDate.of(2026, 9, 14)

    @Test
    fun `a date round trips through the link`() {
        assertEquals(day, AgendaDeepLinks.parse(AgendaDeepLinks.uri(day)))
    }

    @Test
    fun `the link uses the app scheme and the agenda host`() {
        assertEquals(
            "${ReminderDeepLinks.Scheme}://${AgendaDeepLinks.Host}?day=${day.toEpochDay()}",
            AgendaDeepLinks.uri(day),
        )
    }

    @Test
    fun `a reminder link is not an agenda link, and the reverse`() {
        val reminder = "${ReminderDeepLinks.Scheme}://reminder/event?id=evt-1&day=${day.toEpochDay()}"
        assertNull(AgendaDeepLinks.parse(reminder))
        assertNull(ReminderDeepLinks.parse(AgendaDeepLinks.uri(day)))
    }

    @Test
    fun `rubbish never throws and never resolves`() {
        listOf(
            null,
            "",
            "   ",
            "not a uri at all ::::",
            "https://example.com/agenda?day=1",
            "${ReminderDeepLinks.Scheme}://agenda",
            "${ReminderDeepLinks.Scheme}://agenda?day=",
            "${ReminderDeepLinks.Scheme}://agenda?day=tomorrow",
            "${ReminderDeepLinks.Scheme}://agenda?day=999999999999999",
        ).forEach { assertNull(it, AgendaDeepLinks.parse(it)) }
    }

    @Test
    fun `the host is matched without regard to case`() {
        assertEquals(day, AgendaDeepLinks.parse("${ReminderDeepLinks.Scheme}://AGENDA?day=${day.toEpochDay()}"))
    }
}
