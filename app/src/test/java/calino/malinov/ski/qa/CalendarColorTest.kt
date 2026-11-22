package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CalDavDiscovery
import calino.malinov.ski.data.caldav.CalDavWriter
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A calendar's colour has two spellings and every server and client picks one.
 *
 * Apple's `{http://apple.com/ns/ical/}calendar-color` is what servers persist
 * (Nextcloud maps it to its own colour column) and what clients read; RFC 7986's
 * `{urn:ietf:params:xml:ns:icalendar}color` is the standard. A calendar is only
 * reliably coloured if the write carries both and the read accepts either, so
 * these checks pin exactly that.
 */
class CalendarColorTest {

    private lateinit var server: MockWebServer
    private val credentials = DavCredentials("test-user", "test-pass")

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun home() = server.url("/calino/").toString()

    private fun multistatus(properties: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"
                       xmlns:ic="http://apple.com/ns/ical/" xmlns:i="urn:ietf:params:xml:ns:icalendar">
          <d:response>
            <d:href>/calino/team/</d:href>
            <d:propstat>
              <d:prop>
                <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                <d:displayname>Team</d:displayname>
                $properties
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `the settings collection is not a calendar a person sees`() = runBlocking {
        // Calino keeps its own preferences in a calendar collection, which is a
        // calendar by protocol and nothing a person should be offered, synced or
        // able to delete. Settings sync looks it up on its own.
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:response>
                    <d:href>/calino/team/</d:href>
                    <d:propstat><d:prop>
                      <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                      <d:displayname>Team</d:displayname>
                    </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/calino/calino-settings/</d:href>
                    <d:propstat><d:prop>
                      <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                      <d:displayname>Calino Settings</d:displayname>
                    </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                  </d:response>
                </d:multistatus>
                """.trimIndent(),
            ),
        )

        val calendars = CalDavDiscovery(DavHttp()).listCalendars(home(), credentials)

        assertEquals(listOf("Team"), calendars.map { it.displayName })
    }

    @Test
    fun `a colour is written in both spellings`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus("")))

        CalDavWriter(DavHttp()).setCalendarColor(home() + "team/", 0xFF2F6F4F.toInt(), credentials)

        val request = server.takeRequest()
        assertEquals("PROPPATCH", request.method)
        val body = request.body.readUtf8()
        // Both properties, both with the same value, each in its own namespace.
        assertTrue("apple spelling missing:\n$body", body.contains("<ic:calendar-color>#2f6f4f</ic:calendar-color>"))
        assertTrue("standard spelling missing:\n$body", body.contains("<i:color>#2f6f4f</i:color>"))
        assertTrue("apple namespace missing", body.contains("xmlns:ic=\"http://apple.com/ns/ical/\""))
        assertTrue(
            "standard namespace missing",
            body.contains("xmlns:i=\"urn:ietf:params:xml:ns:icalendar\""),
        )
    }

    @Test
    fun `a colour the server spells the standard way is read`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(207)
                .setBody(multistatus("<i:color>#2F6F4F</i:color>")),
        )

        val calendars = CalDavDiscovery(DavHttp()).listCalendars(home(), credentials)

        assertEquals(1, calendars.size)
        assertEquals(0xFF2F6F4F, calendars.single().color)
    }

    @Test
    fun `an apple colour is read`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(207)
                .setBody(multistatus("<ic:calendar-color>#2F6F4F</ic:calendar-color>")),
        )

        val calendars = CalDavDiscovery(DavHttp()).listCalendars(home(), credentials)

        assertEquals(0xFF2F6F4F, calendars.single().color)
    }

    @Test
    fun `apple wins when the two disagree, because that is the one servers store`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                multistatus(
                    "<ic:calendar-color>#2F6F4F</ic:calendar-color><i:color>#FF0000</i:color>",
                ),
            ),
        )

        val calendars = CalDavDiscovery(DavHttp()).listCalendars(home(), credentials)

        assertEquals(0xFF2F6F4F, calendars.single().color)
    }
}
