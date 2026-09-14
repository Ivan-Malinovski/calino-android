package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CachedCalendar
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalDavSource
import calino.malinov.ski.data.repository.FilePendingChangeStore
import calino.malinov.ski.data.repository.PendingChangeRequest
import calino.malinov.ski.data.repository.PendingChangeType
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.data.repository.WriteResult
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalDavQueuedRebaseTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var queueFile: File
    private val credentials = DavCredentials("test-user", "test-pass")

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        queueFile = File.createTempFile("calino-queued-rebase", ".json").also { it.delete() }
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
        queueFile.delete()
    }

    @Test
    fun `a stale queued event is rebased onto the current server resource`() = runBlocking {
        val collection = server.url("/cal/").toString()
        val href = server.url("/cal/queued.ics").toString()
        val base = calendarResource(
            href = href,
            summary = "Original",
            organizer = "Base boss",
            foreign = "base-only",
        )
        val remote = calendarResource(
            href = href,
            summary = "Remote title",
            organizer = "Remote boss",
            foreign = "remote-only",
        )
        val putBodies = Collections.synchronizedList(mutableListOf<String>())
        var putCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    request.method == "REPORT" && body.contains("VEVENT") ->
                        multiStatus(href, "base-etag", base)
                    request.method == "REPORT" -> multiStatusEmpty()
                    request.method == "PUT" -> {
                        putBodies += body
                        putCount += 1
                        when (putCount) {
                            1 -> MockResponse().setResponseCode(503)
                            2 -> MockResponse().setResponseCode(412).setBody("changed elsewhere")
                            else -> MockResponse().setResponseCode(204).setHeader("ETag", "\"rebased-etag\"")
                        }
                    }
                    request.method == "GET" && request.path == "/cal/queued.ics" ->
                        MockResponse().setResponseCode(200)
                            .setHeader("ETag", "\"remote-etag\"")
                            .setBody(remote)
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val cache = MemoryCache()
        val queue = FilePendingChangeStore(queueFile)
        val repository = CalDavRepository(
            fetcher = CalDavFetcher(DavHttp()),
            scope = scope,
            cache = cache,
            mapper = ICalMapper(ZoneId.of("UTC")),
            today = { LocalDate.of(2026, 9, 8) },
            pendingStore = queue,
        )
        val calendar = DiscoveredCalendar(
            url = collection,
            displayName = "Test calendar",
            color = 0xFF11A602,
            readOnly = false,
            components = setOf("VEVENT", "VTODO", "VJOURNAL"),
        )
        repository.setSources(listOf(CalDavSource(calendar, credentials, "account")))
        repository.awaitSync()

        val event = repository.events().single()
        val queued = repository.updateEvent(
            event.id,
            NewEvent(
                title = "Local title",
                date = LocalDate.of(2026, 9, 8),
                allDay = true,
                calendarId = collection,
            ),
        )
        assertTrue("expected the outage to queue the update: $queued", queued is WriteResult.Queued)
        assertEquals("base-etag", queue.snapshot().single().etag)
        assertEquals(base, queue.snapshot().single().baseData)

        repository.drainPendingWrites()
        awaitQueue(queue)

        assertTrue("the queued write was not acknowledged: ${queue.snapshot()}", queue.snapshot().isEmpty())
        assertEquals(3, putBodies.size)
        val rebasedBody = putBodies.last()
        assertTrue(rebasedBody.contains("SUMMARY:Local title"))
        assertTrue(rebasedBody.contains("ORGANIZER;CN=Remote boss"))
        assertTrue(rebasedBody.contains("X-FOREIGN:remote-only"))
    }

    @Test
    fun `an edit to an offline-created event replaces its queued create`() = runBlocking {
        val collection = server.url("/cal/").toString()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.method) {
                    "REPORT" -> multiStatusEmpty()
                    "PUT" -> MockResponse().setResponseCode(503)
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
        val queue = FilePendingChangeStore(queueFile)
        val repository = CalDavRepository(
            fetcher = CalDavFetcher(DavHttp()),
            scope = scope,
            cache = MemoryCache(),
            mapper = ICalMapper(ZoneId.of("UTC")),
            today = { LocalDate.of(2026, 9, 8) },
            pendingStore = queue,
        )
        val calendar = DiscoveredCalendar(
            url = collection,
            displayName = "Test calendar",
            color = 0xFF11A602,
            readOnly = false,
            components = setOf("VEVENT", "VTODO", "VJOURNAL"),
        )
        repository.setSources(listOf(CalDavSource(calendar, credentials, "account")))
        repository.awaitSync()

        val created = repository.addEvent(
            NewEvent(
                title = "Original title",
                date = LocalDate.of(2026, 9, 8),
                allDay = true,
                calendarId = collection,
            ),
        )
        assertTrue(created is WriteResult.Queued)
        val local = (created as WriteResult.Queued).record

        val updated = repository.updateEvent(
            local.id,
            NewEvent(
                title = "Final title",
                date = LocalDate.of(2026, 9, 8),
                allDay = true,
                calendarId = collection,
            ),
        )

        assertTrue(updated is WriteResult.Queued)
        assertEquals(1, queue.snapshot().size)
        assertEquals(calino.malinov.ski.data.repository.PendingChangeType.CREATE, queue.snapshot().single().type)
        assertTrue(queue.snapshot().single().data!!.contains("SUMMARY:Final title"))
        assertTrue(!queue.snapshot().single().data!!.contains("SUMMARY:Original title"))
    }

    @Test
    fun `a queued create with an identical server resource is acknowledged after a 412`() = runBlocking {
        val collection = server.url("/cal/").toString()
        val href = server.url("/cal/queued-create.ics").toString()
        val payload = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Calino Queue Test//EN
BEGIN:VEVENT
UID:queued-create
DTSTART;VALUE=DATE:20260908
DTEND;VALUE=DATE:20260909
SUMMARY:Already applied
END:VEVENT
END:VCALENDAR"""
        var putCount = 0
        var getCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                request.body.readUtf8()
                return when {
                    request.method == "REPORT" -> multiStatusEmpty()
                    request.method == "PUT" -> {
                        putCount += 1
                        MockResponse().setResponseCode(412)
                    }
                    request.method == "GET" && request.path == "/cal/queued-create.ics" -> {
                        getCount += 1
                        MockResponse().setResponseCode(200)
                            .setHeader("ETag", "\"server-v1\"")
                            .setBody(payload)
                    }
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val queue = FilePendingChangeStore(queueFile)
        val queued = queue.enqueue(
            PendingChangeRequest(
                type = PendingChangeType.CREATE,
                eventId = "queued-create",
                accountId = "account",
                calendarId = collection,
                component = "VEVENT",
                calendarUrl = collection,
                uid = "queued-create",
                href = href,
                data = payload,
            ),
        )
        assertTrue(queued is calino.malinov.ski.data.repository.PendingChangeEnqueueResult.Enqueued)

        val repository = CalDavRepository(
            fetcher = CalDavFetcher(DavHttp()),
            scope = scope,
            cache = MemoryCache(),
            mapper = ICalMapper(ZoneId.of("UTC")),
            today = { LocalDate.of(2026, 9, 8) },
            pendingStore = queue,
        )
        val calendar = DiscoveredCalendar(
            url = collection,
            displayName = "Test calendar",
            color = 0xFF11A602,
            readOnly = false,
            components = setOf("VEVENT", "VTODO", "VJOURNAL"),
        )
        repository.setSources(listOf(CalDavSource(calendar, credentials, "account")))
        repository.awaitSync()
        awaitQueue(queue)

        assertTrue("the already-applied create should be acknowledged", queue.snapshot().isEmpty())
        assertEquals(1, putCount)
        assertEquals(1, getCount)
        assertEquals(href, repository.events().single().href)
        assertEquals("server-v1", repository.events().single().etag)
    }

    private fun calendarResource(
        href: String,
        summary: String,
        organizer: String,
        foreign: String,
    ): String = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Calino Queue Test//EN
BEGIN:VEVENT
UID:queued
DTSTART;VALUE=DATE:20260908
DTEND;VALUE=DATE:20260909
SUMMARY:$summary
ORGANIZER;CN=$organizer:mailto:boss@example.com
X-FOREIGN:$foreign
END:VEVENT
END:VCALENDAR
""".trim()

    private fun multiStatus(href: String, etag: String, body: String) =
        MockResponse().setResponseCode(207)
            .setHeader("Content-Type", "application/xml; charset=utf-8")
            .setBody(
                """<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
<response><href>$href</href><propstat><prop><getetag>&quot;$etag&quot;</getetag><C:calendar-data>$body</C:calendar-data></prop><status>HTTP/1.1 200 OK</status></propstat></response>
</multistatus>""",
            )

    private fun multiStatusEmpty() = MockResponse().setResponseCode(207)
        .setHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody("<multistatus xmlns=\"DAV:\"/>")

    private fun CalDavRepository.awaitSync() {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            when (val state = snapshot().sync) {
                is SyncState.Ready, is SyncState.Failed -> return
                else -> Thread.sleep(10)
            }
        }
        error("repository fetch did not settle: ${snapshot().sync}")
    }

    private fun awaitQueue(queue: FilePendingChangeStore) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (queue.snapshot().isEmpty()) return
            Thread.sleep(10)
        }
    }

    private class MemoryCache : CalendarCache {
        private val entries = mutableMapOf<String, CachedCalendar>()

        override fun load(calendarUrl: String): CachedCalendar? = entries[calendarUrl]
        override fun save(entry: CachedCalendar) { entries[entry.calendarUrl] = entry }
        override fun evictExcept(calendarUrls: Set<String>) { entries.keys.retainAll(calendarUrls) }

        override fun loadResource(calendarUrl: String, href: String): CalendarResource? =
            entries[calendarUrl]?.resources?.firstOrNull { it.href == href }

        override fun saveResource(calendarUrl: String, resource: CalendarResource) {
            val entry = entries[calendarUrl] ?: return
            entries[calendarUrl] = entry.copy(resources = entry.resources.filterNot { it.href == resource.href } + resource)
        }

        override fun deleteResource(calendarUrl: String, href: String) {
            entries[calendarUrl]?.let { entry ->
                entries[calendarUrl] = entry.copy(resources = entry.resources.filterNot { it.href == href })
            }
        }
    }
}
