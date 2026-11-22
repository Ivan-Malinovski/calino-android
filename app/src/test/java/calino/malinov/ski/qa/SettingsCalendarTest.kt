package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.SettingsCalendarClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Finding the Calino Settings collection among a home's children.
 *
 * The marker property is the only reliable signal -- names are translatable and
 * URLs are server-chosen -- so the check has to look at a *successful* propstat.
 * Radicale answers a PROPFIND for an absent property with an empty element in a
 * 404 propstat, and treating that as a hit made the calendar home itself look
 * like the settings collection: the very first answer won every time.
 */
class SettingsCalendarTest {

    private lateinit var server: MockWebServer
    private val credentials = DavCredentials("calino", "calinopass")

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun home() = server.url("/calino/").toString()

    private fun response(body: String) =
        MockResponse().setResponseCode(207).setBody(body.trimIndent())

    @Test
    fun `the home echoing the marker in a not-found propstat is not the settings collection`() = runBlocking {
        server.enqueue(
            response(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:x="urn:calino:settings">
                  <d:response>
                    <d:href>/calino/</d:href>
                    <d:propstat>
                      <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                      <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                    <d:propstat>
                      <d:prop><x:X-CALINO-SETTINGS-CALENDAR/></d:prop>
                      <d:status>HTTP/1.1 404 Not Found</d:status>
                    </d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/calino/calino-settings/</d:href>
                    <d:propstat>
                      <d:prop>
                        <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                        <x:X-CALINO-SETTINGS-CALENDAR>1</x:X-CALINO-SETTINGS-CALENDAR>
                      </d:prop>
                      <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                  </d:response>
                </d:multistatus>
                """,
            ),
        )

        val collection = SettingsCalendarClient(DavHttp()).find(home(), credentials)

        assertEquals(server.url("/calino/calino-settings/").toString(), collection?.url)
        assertTrue(collection?.resourceUrl?.endsWith("/calino-settings.ics") == true)
    }

    @Test
    fun `a server that only carries the display name is still found`() = runBlocking {
        server.enqueue(
            response(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:response>
                    <d:href>/calino/calino-settings/</d:href>
                    <d:propstat>
                      <d:prop>
                        <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                        <d:displayname>Calino Settings</d:displayname>
                      </d:prop>
                      <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                  </d:response>
                </d:multistatus>
                """,
            ),
        )

        val collection = SettingsCalendarClient(DavHttp()).find(home(), credentials)

        assertEquals(server.url("/calino/calino-settings/").toString(), collection?.url)
    }

    @Test
    fun `no marker and no matching name means no collection`() = runBlocking {
        server.enqueue(
            response(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:x="urn:calino:settings">
                  <d:response>
                    <d:href>/calino/team/</d:href>
                    <d:propstat>
                      <d:prop>
                        <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                        <d:displayname>Team</d:displayname>
                      </d:prop>
                      <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                    <d:propstat>
                      <d:prop><x:X-CALINO-SETTINGS-CALENDAR/></d:prop>
                      <d:status>HTTP/1.1 404 Not Found</d:status>
                    </d:propstat>
                  </d:response>
                </d:multistatus>
                """,
            ),
        )

        assertNull(SettingsCalendarClient(DavHttp()).find(home(), credentials))
    }
}
