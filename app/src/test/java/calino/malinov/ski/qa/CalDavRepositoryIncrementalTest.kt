package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CachedCalendar
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalDavSource
import calino.malinov.ski.data.repository.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalDavRepositoryIncrementalTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val credentials = DavCredentials("test-user", "test-pass")
    private val start = LocalDate.of(2024, 9, 1)
    private val end = LocalDate.of(2028, 9, 1)

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    @Test
    fun `repository applies a delta and carries its cursor into the next refresh`() {
        val collection = server.url("/cal/").toString()
        val href = "${collection}changed.ics"
        val cache = MemoryCalendarCache()
        cache.entries[collection] = CachedCalendar(
            calendarUrl = collection,
            fetchedAt = Instant.parse("2026-09-08T09:00:00Z"),
            windowStart = start,
            windowEnd = end,
            resources = listOf(CalendarResource(href, "old-etag", ics("old"))),
        )
        server.enqueue(syncResponse(href, "next-token", "report-etag"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"new-etag\"")
                .setBody(ics("new")),
        )

        val repository = CalDavRepository(
            fetcher = CalDavFetcher(DavHttp()),
            scope = scope,
            cache = cache,
            mapper = ICalMapper(ZoneId.of("Europe/Copenhagen")),
            today = { LocalDate.of(2026, 9, 8) },
        )
        var cursor: String? = null
        repository.setCalendarCursorListener { _, _, value -> cursor = value.syncToken }
        repository.setSources(
            listOf(
                CalDavSource(
                    calendar = DiscoveredCalendar(
                        url = collection,
                        displayName = "Personal",
                        color = 0xFF11A602,
                        readOnly = false,
                        components = setOf("VEVENT", "VTODO", "VJOURNAL"),
                        ctag = "old-ctag",
                        syncToken = "old-token",
                    ),
                    credentials = credentials,
                    accountId = "account",
                ),
            ),
        )

        awaitReady(repository)
        assertEquals(2, server.requestCount)
        assertEquals("new", repository.snapshot().events.single().title)
        assertEquals("next-token", cursor)
        assertEquals("new-etag", cache.entries.getValue(collection).resources.single().etag)

        server.enqueue(emptySyncResponse("final-token"))
        repository.refresh()
        awaitReady(repository)

        assertEquals(3, server.requestCount)
        val secondReport = server.takeRequest() // first REPORT
        server.takeRequest() // changed resource GET
        val refreshReport = server.takeRequest()
        assertEquals("REPORT", secondReport.method)
        assertTrue(secondReport.body.readUtf8().contains("old-token"))
        assertEquals("REPORT", refreshReport.method)
        val refreshBody = refreshReport.body.readUtf8()
        assertTrue(refreshBody.contains("next-token"))
    }

    @Test
    fun `repository does not commit cursor when complete cache write fails`() {
        val collection = server.url("/cal/").toString()
        val cache = MemoryCalendarCache().apply { refuseCompleteSave = true }
        val href = "${collection}changed.ics"
        cache.entries[collection] = CachedCalendar(
            collection, Instant.EPOCH, start, end,
            listOf(CalendarResource(href, "old", ics("old"))),
        )
        server.enqueue(syncResponse(href, "next-token", "new"))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"new\"").setBody(ics("new")))
        val repository = CalDavRepository(
            fetcher = CalDavFetcher(DavHttp()), scope = scope, cache = cache,
            mapper = ICalMapper(ZoneId.of("Europe/Copenhagen")),
            today = { LocalDate.of(2026, 9, 8) },
        )
        var committed = false
        repository.setCalendarCursorListener { _, _, _ -> committed = true }
        repository.setSources(listOf(CalDavSource(
            calendar = DiscoveredCalendar(collection, "Personal", 0xFF11A602, false,
                setOf("VEVENT"), ctag = "new-ctag", syncToken = "old-token"),
            credentials = credentials, accountId = "account",
        )))

        awaitReady(repository)
        assertEquals("new", repository.snapshot().events.single().title)
        assertTrue(!committed)
        assertEquals("old", cache.entries.getValue(collection).resources.single().etag)
    }

    private fun awaitReady(repository: CalDavRepository) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (repository.snapshot().sync is SyncState.Ready ||
                repository.snapshot().sync is SyncState.Failed
            ) return
            Thread.sleep(10)
        }
        error("repository did not settle: ${repository.snapshot().sync}")
    }

    private fun syncResponse(href: String, token: String, etag: String) = MockResponse()
        .setResponseCode(207)
        .setHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(
            """<d:multistatus xmlns:d="DAV:">
                <d:response><d:href>$href</d:href><d:propstat><d:prop>
                  <d:getetag>&quot;$etag&quot;</d:getetag>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                <d:sync-token>$token</d:sync-token>
            </d:multistatus>""".trimIndent(),
        )

    private fun emptySyncResponse(token: String) = MockResponse()
        .setResponseCode(207)
        .setHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody("""<d:multistatus xmlns:d="DAV:"><d:sync-token>$token</d:sync-token></d:multistatus>""")

    private fun ics(summary: String) = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:incremental-event
DTSTART:20260910T120000Z
DTEND:20260910T130000Z
SUMMARY:$summary
END:VEVENT
END:VCALENDAR
"""

    private class MemoryCalendarCache : CalendarCache {
        val entries = mutableMapOf<String, CachedCalendar>()
        var refuseCompleteSave = false

        override fun load(calendarUrl: String): CachedCalendar? = entries[calendarUrl]

        override fun save(entry: CachedCalendar) {
            entries[entry.calendarUrl] = entry
        }

        override fun saveComplete(entry: CachedCalendar): Boolean {
            if (refuseCompleteSave) return false
            save(entry)
            return true
        }

        override fun evictExcept(calendarUrls: Set<String>) {
            entries.keys.retainAll(calendarUrls)
        }
    }
}
