package calino.malinov.ski.poc.data.caldav

import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.model.CalDavCalendar
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalDavPhase4IntegrationTest {

    private lateinit var server: MockWebServer
    private val credentials = DavCredentials("test-user", "test-pass")
    private val start = LocalDate.of(2026, 9, 1)
    private val end = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `calendar discovery retains ctag and sync token`() = runBlocking {
        val calendarUrl = server.url("/cal/").toString()
        server.enqueue(
            multiStatus(
                """<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/">
                    <d:response><d:href>/cal/</d:href><d:propstat><d:prop>
                      <d:resourcetype><c:calendar/></d:resourcetype>
                      <d:displayname>Personal</d:displayname>
                      <cs:getctag>&quot;ctag-1&quot;</cs:getctag>
                      <d:sync-token>token-1</d:sync-token>
                    </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                </d:multistatus>""".trimIndent(),
            ),
        )

        val calendar = CalDavDiscovery(DavHttp()).listCalendars(calendarUrl, credentials).single()

        assertEquals("\"ctag-1\"", calendar.ctag)
        assertEquals("token-1", calendar.syncToken)
        assertEquals("\"ctag-1\"", calendar.toCalDavCalendar().ctag)
        assertEquals("token-1", calendar.toCalDavCalendar().syncToken)
    }

    @Test
    fun `calendar cursors survive account json and old payloads remain cursorless`() {
        val account = CalDavAccount(
            id = "account",
            displayName = "Home",
            serverUrl = "https://dav.example.test",
            username = "test-user",
            calendars = listOf(
                CalDavCalendar(
                    id = "https://dav.example.test/cal/",
                    name = "Personal",
                    color = 0xFF11A602,
                    ctag = "ctag-1",
                    syncToken = "token-1",
                ),
            ),
        )

        val encoded = CalDavAccountJson.encode(listOf(account))
        val restored = CalDavAccountJson.decode(encoded).single()

        assertTrue(encoded.contains("\"ctag\""))
        assertTrue(encoded.contains("\"syncToken\""))
        assertEquals(account, restored)

        val legacy = """[{"id":"account","displayName":"Home","serverUrl":"https://dav.example.test","username":"test-user","calendars":[{"id":"https://dav.example.test/cal/","name":"Personal","color":4278911490,"enabled":true,"readOnly":false}]}]"""
        val restoredLegacy = CalDavAccountJson.decode(legacy).single().calendars.single()
        assertNull(restoredLegacy.ctag)
        assertNull(restoredLegacy.syncToken)
    }

    @Test
    fun `connection merge persists both cursors while retaining visibility`() {
        val account = CalDavAccount(
            id = "account",
            displayName = "Home",
            serverUrl = "https://dav.example.test",
            username = "test-user",
            calendars = listOf(
                CalDavCalendar(
                    id = "https://dav.example.test/old/",
                    name = "Old name",
                    color = 0xFF000000,
                    enabled = false,
                    ctag = "old-ctag",
                    syncToken = "old-token",
                ),
            ),
        )
        val found = listOf(
            DiscoveredCalendar(
                url = "https://dav.example.test/old/",
                displayName = "Renamed",
                color = 0xFF11A602,
                readOnly = true,
                components = setOf("VEVENT"),
                ctag = "fresh-ctag",
                syncToken = "fresh-token",
            ),
            DiscoveredCalendar(
                url = "https://dav.example.test/new/",
                displayName = "New",
                color = 0xFFF6DC6B,
                readOnly = false,
                components = setOf("VTODO"),
                ctag = "new-ctag",
                syncToken = "new-token",
            ),
        )

        val merged = mergeDiscoveredCalendars(account, found)

        assertEquals(listOf(false, true), merged.map { it.enabled })
        assertEquals(listOf("fresh-ctag", "new-ctag"), merged.map { it.ctag })
        assertEquals(listOf("fresh-token", "new-token"), merged.map { it.syncToken })
        assertEquals("Renamed", merged.first().name)
    }

    @Test
    fun `missing sync token uses the unchanged full fetch path`() = runBlocking {
        serveFullFetches()
        val result = fetcher().fetchIncremental(
            calendar = calendar(syncToken = null),
            credentials = credentials,
            cachedResources = emptyList(),
            windowStart = start,
            windowEnd = end,
        )

        assertEquals(CalendarFetchMode.Full, result.mode)
        assertFalse(result.usedIncremental)
        assertFalse(result.fellBackToFull)
        assertEquals(3, server.requestCount)
        assertEquals("ctag-current", result.cursor.ctag)
        assertNull(result.cursor.syncToken)
        assertTrue(result.fetchResult.resources.isNotEmpty())
    }

    @Test
    fun `a valid report merges changed and removed resources and advances both cursors`() = runBlocking {
        val collection = server.url("/cal/").toString()
        val changedHref = "${collection}changed.ics"
        val removedHref = "${collection}removed.ics"
        server.enqueue(multiStatus(syncReport(changedHref, removedHref, "next-token")))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"server-new\"")
                .setBody(calendarIcs("changed")),
        )

        val result = fetcher().fetchIncremental(
            calendar = calendar(url = collection, ctag = "ctag-fresh", syncToken = "old-token"),
            credentials = credentials,
            cachedResources = listOf(
                CalendarResource("${collection}unchanged.ics", "unchanged-etag", calendarIcs("unchanged")),
                CalendarResource(removedHref, "removed-etag", calendarIcs("removed")),
            ),
            windowStart = start,
            windowEnd = end,
            storedCursor = CollectionCursor(ctag = "ctag-stored", syncToken = "old-token"),
        )

        assertEquals(CalendarFetchMode.Incremental, result.mode)
        assertTrue(result.usedIncremental)
        assertFalse(result.fellBackToFull)
        assertEquals(2, server.requestCount)
        assertEquals("ctag-fresh", result.cursor.ctag)
        assertEquals("next-token", result.cursor.syncToken)
        assertEquals(
            listOf("unchanged.ics", "changed.ics"),
            result.resources.map { it.href.substringAfterLast('/') },
        )
        assertEquals("server-new", result.resources.single { it.href == changedHref }.etag)

        val report = server.takeRequest()
        val get = server.takeRequest()
        assertEquals("REPORT", report.method)
        assertTrue(report.body.readUtf8().contains("<d:sync-token>old-token</d:sync-token>"))
        assertEquals("GET", get.method)
        assertEquals("text/calendar", get.getHeader("Accept"))
        assertEquals("Basic dGVzdC11c2VyOnRlc3QtcGFzcw==", get.getHeader("Authorization"))
    }

    @Test
    fun `an unchanged ctag skips the report when a committed token and cache exist`() = runBlocking {
        val collection = server.url("/cal/").toString()
        val cached = listOf(CalendarResource("${collection}unchanged.ics", "etag", calendarIcs("unchanged")))

        val result = fetcher().fetchIncremental(
            calendar = calendar(url = collection, ctag = "same", syncToken = "fresh-token"),
            credentials = credentials,
            cachedResources = cached,
            windowStart = start,
            windowEnd = end,
            storedCursor = CollectionCursor(ctag = "same", syncToken = "committed-token"),
        )

        assertEquals(CalendarFetchMode.Skipped, result.mode)
        assertEquals(cached, result.resources)
        assertEquals(CollectionCursor("same", "committed-token"), result.cursor)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing cache falls back to a full fetch without risking a delta`() = runBlocking {
        serveFullFetches()
        val result = fetcher().fetchIncremental(
            calendar = calendar(syncToken = "still-valid"),
            credentials = credentials,
            cachedResources = null,
            windowStart = start,
            windowEnd = end,
        )

        assertEquals(CalendarFetchMode.FullFallback, result.mode)
        assertTrue(result.fellBackToFull)
        assertEquals(3, server.requestCount)
        assertEquals("still-valid", result.cursor.syncToken)
        assertTrue(result.fetchResult.resources.isNotEmpty())
    }

    @Test
    fun `a rejected sync token falls back to full fetch and clears the old token`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("<d:error xmlns:d=\"DAV:\"><d:valid-sync-token/></d:error>"),
        )
        repeat(3) { server.enqueue(fullResponse()) }

        val result = fetcher().fetchIncremental(
            calendar = calendar(syncToken = "stale-token"),
            credentials = credentials,
            cachedResources = emptyList(),
            windowStart = start,
            windowEnd = end,
        )

        assertEquals(CalendarFetchMode.FullFallback, result.mode)
        assertEquals(SyncCollectionFallbackReason.ReportRejected, result.fallbackReason)
        assertNull(result.cursor.syncToken)
        assertEquals("ctag-current", result.cursor.ctag)
        assertEquals(4, server.requestCount)
        assertEquals("REPORT", server.takeRequest().method)
    }

    @Test
    fun `a tokenless report also falls back before applying any changes`() = runBlocking {
        server.enqueue(multiStatus("<d:multistatus xmlns:d=\"DAV:\"><d:response/></d:multistatus>"))
        repeat(3) { server.enqueue(fullResponse()) }

        val result = fetcher().fetchIncremental(
            calendar = calendar(syncToken = "old-token"),
            credentials = credentials,
            cachedResources = emptyList(),
            windowStart = start,
            windowEnd = end,
        )

        assertEquals(CalendarFetchMode.FullFallback, result.mode)
        assertEquals(SyncCollectionFallbackReason.MissingSyncToken, result.fallbackReason)
        assertNull(result.cursor.syncToken)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `a changed resource that disappears between report and get is removed safely`() = runBlocking {
        val collection = server.url("/cal/").toString()
        val disappearingHref = "${collection}gone.ics"
        server.enqueue(multiStatus(syncReport(disappearingHref, null, "next-token")))
        server.enqueue(MockResponse().setResponseCode(404))

        val result = fetcher().fetchIncremental(
            calendar = calendar(url = collection, syncToken = "old-token"),
            credentials = credentials,
            cachedResources = listOf(CalendarResource(disappearingHref, "old", calendarIcs("gone"))),
            windowStart = start,
            windowEnd = end,
        )

        assertEquals(CalendarFetchMode.Incremental, result.mode)
        assertTrue(result.resources.isEmpty())
        assertEquals("next-token", result.cursor.syncToken)
    }

    @Test
    fun `an external changed href is rejected and the full fetch is used`() = runBlocking {
        server.enqueue(multiStatus(syncReport("https://other.example.test/secret.ics", null, "next-token")))
        repeat(3) { server.enqueue(fullResponse()) }

        val result = fetcher().fetchIncremental(
            calendar = calendar(syncToken = "old-token"),
            credentials = credentials,
            cachedResources = emptyList(),
            windowStart = start,
            windowEnd = end,
        )

        assertEquals(CalendarFetchMode.FullFallback, result.mode)
        assertEquals(4, server.requestCount)
        // The report's next token was not committed because its resource href
        // was unsafe; retaining the last committed token is safe and causes
        // the malformed delta to be retried rather than skipped.
        assertEquals("old-token", result.cursor.syncToken)
    }

    private fun fetcher() = CalDavFetcher(DavHttp())

    private fun calendar(
        url: String = server.url("/cal/").toString(),
        ctag: String? = "ctag-current",
        syncToken: String? = "token-current",
    ) = DiscoveredCalendar(
        url = url,
        displayName = "Personal",
        color = 0xFF11A602,
        readOnly = false,
        components = setOf("VEVENT", "VTODO", "VJOURNAL"),
        ctag = ctag,
        syncToken = syncToken,
    )

    private fun serveFullFetches() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = fullResponse()
        }
    }

    private fun fullResponse() = multiStatus(
        """<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
            <d:response><d:href>/cal/full.ics</d:href><d:propstat><d:prop>
              <d:getetag>&quot;full-etag&quot;</d:getetag>
              <c:calendar-data>${calendarIcs("full")}</c:calendar-data>
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        </d:multistatus>""".trimIndent(),
    )

    private fun syncReport(changedHref: String, removedHref: String?, token: String): String {
        val changed = """<d:response><d:href>$changedHref</d:href><d:propstat><d:prop>
            <d:getetag>&quot;report-etag&quot;</d:getetag>
          </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"""
        val removed = removedHref?.let {
            """<d:response><d:href>$it</d:href><d:status>HTTP/1.1 404 Not Found</d:status></d:response>"""
        }.orEmpty()
        return """<d:multistatus xmlns:d="DAV:">$changed$removed<d:sync-token>$token</d:sync-token></d:multistatus>"""
    }

    private fun multiStatus(body: String) = MockResponse()
        .setResponseCode(207)
        .setHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(body)

    private fun calendarIcs(summary: String) = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:$summary
DTSTART:20260910T120000Z
SUMMARY:$summary
END:VEVENT
END:VCALENDAR
""".trimIndent()
}
