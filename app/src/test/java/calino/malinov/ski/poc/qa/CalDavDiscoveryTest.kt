package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.CalDavDiscovery
import calino.malinov.ski.poc.data.caldav.CalDavErrorCode
import calino.malinov.ski.poc.data.caldav.CalDavException
import calino.malinov.ski.poc.data.caldav.DavCredentials
import calino.malinov.ski.poc.data.caldav.DavHttp
import calino.malinov.ski.poc.data.caldav.caldavSubdomain
import calino.malinov.ski.poc.data.caldav.normalizeColor
import calino.malinov.ski.poc.data.caldav.resolveHref
import calino.malinov.ski.poc.data.model.CalDavForm
import calino.malinov.ski.poc.state.CalDavConnectResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Discovery against replayed real-server responses. */
class CalDavDiscoveryTest {

    private lateinit var server: MockWebServer
    private val credentials = DavCredentials("test-user", "test-pass")

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun multiStatus(body: String) =
        MockResponse().setResponseCode(207).setBody(body)
            .setHeader("Content-Type", "application/xml; charset=utf-8")

    private fun discovery() = CalDavDiscovery(DavHttp())
    private fun url(path: String = "/") = server.url(path).toString()

    // --- the collection listing ----------------------------------------------

    @Test
    fun `only calendar collections survive the listing`() = runBlocking {
        server.enqueue(multiStatus(CalDavFixtures.Calendars))
        val calendars = discovery().listCalendars(url(), credentials)

        // The home also lists the principal itself and two address books.
        assertEquals(2, calendars.size)
        assertEquals(
            listOf("hellyeah", "extra calendar"),
            calendars.map { it.displayName },
        )
        assertTrue(
            "address books must never appear as calendars",
            calendars.none { it.displayName.contains("Contacts", ignoreCase = true) },
        )
    }

    @Test
    fun `calendar colours drop the alpha byte`() = runBlocking {
        server.enqueue(multiStatus(CalDavFixtures.Calendars))
        val calendars = discovery().listCalendars(url(), credentials)
        assertEquals(0xFF11A602, calendars.first { it.displayName == "hellyeah" }.color)
        assertEquals(0xFFF6DC6B, calendars.first { it.displayName == "extra calendar" }.color)
    }

    @Test
    fun `supported components are read off each collection`() = runBlocking {
        server.enqueue(multiStatus(CalDavFixtures.Calendars))
        val hellyeah = discovery().listCalendars(url(), credentials).first { it.displayName == "hellyeah" }
        assertEquals(setOf("VEVENT", "VTODO", "VJOURNAL"), hellyeah.components)
    }

    @Test
    fun `a collection granting write is not read-only`() = runBlocking {
        server.enqueue(multiStatus(CalDavFixtures.Calendars))
        assertTrue(discovery().listCalendars(url(), credentials).none { it.readOnly })
    }

    @Test
    fun `a collection with no privilege information is treated as writable`() = runBlocking {
        server.enqueue(multiStatus(
            """<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                 <response><href>/cal/terse/</href><propstat><prop>
                   <resourcetype><C:calendar/><collection/></resourcetype>
                   <displayname>Terse</displayname>
                 </prop><status>HTTP/1.1 200 OK</status></propstat></response>
               </multistatus>"""
        ))
        val calendar = discovery().listCalendars(url(), credentials).single()
        assertFalse("absent metadata is not a denial", calendar.readOnly)
    }

    @Test
    fun `a subscribed collection is read-only`() = runBlocking {
        server.enqueue(multiStatus(
            """<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav"
                            xmlns:CS="http://calendarserver.org/ns/">
                 <response><href>/cal/feed/</href><propstat><prop>
                   <resourcetype><C:calendar/><collection/></resourcetype>
                   <displayname>Holidays</displayname>
                   <CS:subscribed>true</CS:subscribed>
                 </prop><status>HTTP/1.1 200 OK</status></propstat></response>
               </multistatus>"""
        ))
        assertTrue(discovery().listCalendars(url(), credentials).single().readOnly)
    }

    @Test
    fun `an unfamiliar namespace prefix still parses`() = runBlocking {
        // Same document, different prefixes. Strict namespace matching alone
        // broke on real servers; the local-name fallback covers this.
        server.enqueue(multiStatus(
            """<D:multistatus xmlns:D="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
                 <D:response><D:href>/cal/x/</D:href><D:propstat><D:prop>
                   <D:resourcetype><cal:calendar/><D:collection/></D:resourcetype>
                   <D:displayname>Prefixed</D:displayname>
                 </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
               </D:multistatus>"""
        ))
        assertEquals("Prefixed", discovery().listCalendars(url(), credentials).single().displayName)
    }

    // --- the full walk --------------------------------------------------------

    @Test
    fun `discovery walks well-known then principal then home then calendars`() = runBlocking {
        server.enqueue(multiStatus(CalDavFixtures.Principal))   // well-known probe
        server.enqueue(multiStatus(CalDavFixtures.Principal))   // current-user-principal
        server.enqueue(multiStatus(CalDavFixtures.HomeSet))     // calendar-home-set
        server.enqueue(multiStatus(CalDavFixtures.Calendars))   // the listing

        val account = discovery().discoverAccount(url().trimEnd('/'), credentials)

        assertTrue(account.principalUrl.endsWith("/test-user/"))
        assertTrue(account.homeSetUrl.endsWith("/test-user/"))
        assertEquals(2, account.calendars.size)

        val methods = (1..4).map { server.takeRequest().method }
        assertTrue("every discovery step uses PROPFIND", methods.all { it == "PROPFIND" })
    }

    @Test
    fun `a bad password surfaces as an auth failure not a generic one`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        val result = discovery().discover(
            CalDavForm(serverUrl = url(), username = "test-user", password = "wrong"),
        )
        val failure = result as CalDavConnectResult.Failed
        assertTrue(
            "expected a credentials message, got: ${failure.message}",
            failure.message.contains("username or password", ignoreCase = true),
        )
    }

    @Test
    fun `a server that answers with HTML is rejected as not CalDAV`() = runBlocking {
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body>Hi</body></html>"))
        }
        val error = runCatching { discovery().discoverAccount(url().trimEnd('/'), credentials) }
            .exceptionOrNull()
        assertTrue("expected a CalDAV error, got $error", error is CalDavException)
        assertEquals(CalDavErrorCode.NotCalDav, (error as CalDavException).code)
    }

    @Test
    fun `an unreadable address fails before any request is made`() = runBlocking {
        val result = discovery().discover(CalDavForm(serverUrl = "not a url", username = "a", password = "b"))
        assertTrue(result is CalDavConnectResult.Failed)
        assertEquals(0, server.requestCount)
    }

    // --- pure helpers ---------------------------------------------------------

    @Test
    fun `a DAV status is a 207 or a 401`() {
        // A 401 counts: an auth challenge proves the endpoint understood
        // PROPFIND, which is what stops a redirect chain into a web UI from
        // being mistaken for the calendar root.
        assertTrue(CalDavDiscovery.isDavStatus(207))
        assertTrue(CalDavDiscovery.isDavStatus(401))
        assertFalse(CalDavDiscovery.isDavStatus(200))
        assertFalse(CalDavDiscovery.isDavStatus(302))
    }

    @Test
    fun `colours parse in every accepted form`() {
        assertEquals(0xFF11A602, normalizeColor("#11a602ff"))  // RRGGBBAA
        assertEquals(0xFF11A602, normalizeColor("#11A602"))    // RRGGBB
        assertEquals(0xFFAABBCC, normalizeColor("#abc"))       // RGB shorthand
        assertEquals(0xFF11A602, normalizeColor("11a602"))     // no leading hash
        // A fully transparent alpha must not make the calendar invisible.
        assertEquals(0xFF11A602, normalizeColor("#11a60200"))
        assertEquals(0xFF5B7FB5, normalizeColor(null))
        assertEquals(0xFF5B7FB5, normalizeColor("not a colour"))
    }

    @Test
    fun `an href is resolved without being decoded first`() {
        // Decoding before resolving turns ev%231.ics into ev#1.ics, where
        // everything after the hash becomes a fragment and the path truncates.
        val resolved = resolveHref("https://example.com/cal/", "/cal/ev%231.ics")
        assertEquals("https://example.com/cal/ev%231.ics", resolved)
        assertFalse(resolved.contains("#1.ics"))
    }

    @Test
    fun `the caldav subdomain fallback is derived from the bare domain`() {
        assertEquals("https://caldav.example.com", caldavSubdomain("https://dav.example.com"))
        assertEquals("https://caldav.example.com", caldavSubdomain("https://example.com"))
        // Already there, so there is nothing to fall back to.
        org.junit.Assert.assertNull(caldavSubdomain("https://caldav.example.com"))
    }
}
