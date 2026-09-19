package calino.malinov.ski.data.webcal

import calino.malinov.ski.data.model.WebcalSubscription
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebcalJsonTest {
    @Test fun `round trips a subscription and keeps mute as the default`() {
        val original = WebcalSubscription(
            id = "abc",
            calendarId = "webcal:abc",
            name = "Katrina",
            color = 0xFF5B7FB5,
            url = "https://calendar.google.com/calendar/ical/secret/basic.ics",
            refreshIntervalMinutes = 60,
            lastFetchedAt = Instant.parse("2026-09-16T12:00:00Z"),
        )
        val restored = WebcalJson.decode(WebcalJson.encode(listOf(original))).single()
        assertEquals(original.id, restored.id)
        assertEquals(original.calendarId, restored.calendarId)
        assertEquals(original.name, restored.name)
        assertEquals(original.url, restored.url)
        assertEquals(original.lastFetchedAt, restored.lastFetchedAt)
        assertFalse(restored.notifyReminders)
        assertEquals(true, restored.visible)
    }

    @Test fun `empty or junk JSON is no subscriptions`() {
        assertEquals(emptyList<WebcalSubscription>(), WebcalJson.decode(null))
        assertEquals(emptyList<WebcalSubscription>(), WebcalJson.decode(""))
    }

    @Test fun `a renamed subscription round-trips the new name`() {
        val original = WebcalSubscription(
            id = "abc",
            calendarId = "webcal:abc",
            name = "calendar.google.com",
            color = 0xFF5B7FB5,
            url = "https://calendar.google.com/calendar/ical/secret/basic.ics",
        )
        val renamed = original.copy(name = "Katrina")
        val restored = WebcalJson.decode(WebcalJson.encode(listOf(renamed))).single()
        assertEquals("Katrina", restored.name)
        assertEquals("webcal:abc", restored.calendarId)
    }
}
