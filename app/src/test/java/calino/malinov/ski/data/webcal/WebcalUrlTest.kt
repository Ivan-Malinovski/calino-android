package calino.malinov.ski.data.webcal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebcalUrlTest {
    @Test fun `webcal becomes https`() {
        assertEquals(
            "https://calendar.google.com/calendar/ical/secret/basic.ics",
            WebcalUrl.normalize("webcal://calendar.google.com/calendar/ical/secret/basic.ics"),
        )
    }

    @Test fun `webcals becomes https`() {
        assertEquals(
            "https://example.com/feed.ics",
            WebcalUrl.normalize("webcals://example.com/feed.ics"),
        )
    }

    @Test fun `https is left alone`() {
        assertEquals("https://example.com/a.ics", WebcalUrl.normalize("https://example.com/a.ics"))
    }

    @Test fun `host only is safe to show`() {
        assertEquals(
            "calendar.google.com",
            WebcalUrl.hostOf("https://calendar.google.com/calendar/ical/secret/basic.ics"),
        )
    }

    @Test fun `rejects a non-http scheme`() {
        assertThrows(WebcalException::class.java) {
            WebcalUrl.requireHttpUrl("ftp://example.com/feed.ics")
        }
    }
}
