package calino.malinov.ski.data.repository

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingChangeDetailsTest {
    @Test fun `title is read from the queued payload, unfolded and unescaped`() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VTODO\r\nUID:1\r\nSUMMARY;ALTREP=\"https://x/y\":Buy milk\\, eggs\r\n  and bread\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"
        assertEquals("Buy milk, eggs and bread", change(data = ics).displayTitle())
    }

    @Test fun `a delete falls back to the server copy it was based on`() {
        val base = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Dentist\nEND:VEVENT\nEND:VCALENDAR\n"
        assertEquals("Dentist", change(baseData = base).displayTitle())
        assertNull(change().displayTitle())
    }

    @Test fun `normalizing uses CRLF, drops blank lines and keeps content`() {
        assertEquals(
            "BEGIN:VCALENDAR\r\nSUMMARY:x\r\n y\r\nEND:VCALENDAR\r\n",
            normalizeICalendarText("BEGIN:VCALENDAR\n\nSUMMARY:x\r\n y\rEND:VCALENDAR"),
        )
    }

    private fun change(data: String? = null, baseData: String? = null) = PendingChange(
        id = "1",
        type = PendingChangeType.UPDATE,
        eventId = "e",
        accountId = "a",
        calendarId = "c",
        component = "VTODO",
        data = data,
        baseData = baseData,
        timestamp = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
