package calino.malinov.ski.data.webcal

import calino.malinov.ski.data.caldav.DavHttp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class WebcalFetcherTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun fetcher() = WebcalFetcher(DavHttp())

    @Test fun `returns a VCALENDAR body`() = runBlocking {
        server.enqueue(MockResponse().setBody(Ics).setResponseCode(200))
        val body = fetcher().fetchIcs(server.url("/feed.ics").toString())
        assertTrue(body.contains("BEGIN:VCALENDAR"))
        assertTrue(body.contains("SUMMARY:Overlay"))
    }

    @Test fun `rejects HTML`() {
        server.enqueue(MockResponse().setBody("<html>nope</html>").setResponseCode(200))
        assertThrows(WebcalException::class.java) {
            runBlocking { fetcher().fetchIcs(server.url("/").toString()) }
        }
    }

    @Test fun `rejects a 404`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
        assertThrows(WebcalException::class.java) {
            runBlocking { fetcher().fetchIcs(server.url("/gone.ics").toString()) }
        }
    }

    private companion object {
        val Ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:overlay-1
            DTSTART:20260916T100000Z
            DTEND:20260916T110000Z
            SUMMARY:Overlay
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT10M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
    }
}
