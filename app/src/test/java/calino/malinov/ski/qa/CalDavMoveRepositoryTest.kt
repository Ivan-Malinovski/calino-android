package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CachedCalendar
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalDavSource
import calino.malinov.ski.data.repository.moveEventToDateTime
import calino.malinov.ski.data.repository.FilePendingChangeStore
import calino.malinov.ski.data.repository.PendingChangeRequest
import calino.malinov.ski.data.repository.PendingChangeType
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.data.repository.WriteResult
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.Collections
import java.util.Queue
import java.util.concurrent.CopyOnWriteArrayList
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

class CalDavMoveRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val credentials = DavCredentials("test-user", "test-pass")

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
    fun `move writes destination before conditional source cleanup`() = runBlocking {
        val calls = serveSourceAndTarget()
        val cache = MemoryCache()
        val source = calendar("/source/", "Source")
        val target = calendar("/target/", "Target")
        val repository = repository(cache)
        repository.setSources(listOf(CalDavSource(source, credentials, "account"), CalDavSource(target, credentials, "account")))
        repository.awaitSync()
        calls.clear()

        val event = repository.events().single()
        val result = repository.updateEvent(event.id, inputFor(event, target.url))

        val moved = assertApplied(result)
        assertEquals(target.url, moved.calendarId)
        assertTrue(calls.indexOfFirst { it.startsWith("PUT /target/") } >= 0)
        assertTrue(calls.indexOfFirst { it.startsWith("DELETE /source/") } >= 0)
        assertTrue(
            "destination PUT must happen before source DELETE",
            calls.indexOfFirst { it.startsWith("PUT /target/") } <
                calls.indexOfFirst { it.startsWith("DELETE /source/") },
        )
    }

    @Test
    fun `destination outage queues move and leaves source untouched`() = runBlocking {
        val calls = serveSourceAndTarget(destinationPutCode = 503)
        val cache = MemoryCache()
        val queueFile = File.createTempFile("calino-move-queue", ".json").also { it.delete() }
        val queue = FilePendingChangeStore(queueFile)
        val source = calendar("/source/", "Source")
        val target = calendar("/target/", "Target")
        val repository = repository(cache, queue)
        repository.setSources(listOf(CalDavSource(source, credentials, "account"), CalDavSource(target, credentials, "account")))
        repository.awaitSync()
        calls.clear()

        val event = repository.events().single()
        val result = repository.updateEvent(event.id, inputFor(event, target.url))

        assertTrue(result is WriteResult.Queued)
        assertEquals(PendingChangeType.MOVE, queue.snapshot().single().type)
        assertTrue(calls.none { it.startsWith("DELETE /source/") })
        queueFile.delete()
        Unit
    }

    @Test
    fun `destination collision rejects move without deleting source`() = runBlocking {
        val calls = serveSourceAndTarget(
            destinationPutCode = 412,
            destinationGetBody = { "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR" },
        )
        val source = calendar("/source/", "Source")
        val target = calendar("/target/", "Target")
        val repository = repository(MemoryCache())
        repository.setSources(listOf(CalDavSource(source, credentials, "account"), CalDavSource(target, credentials, "account")))
        repository.awaitSync()
        calls.clear()

        val event = repository.events().single()
        val result = repository.updateEvent(event.id, inputFor(event, target.url))

        assertTrue("expected a rejected collision: $result", result is WriteResult.Rejected)
        assertTrue(calls.any { it.startsWith("GET /target/") })
        assertTrue(calls.none { it.startsWith("DELETE /source/") })
    }

    @Test
    fun `UID conflict leaves the source in place`() = runBlocking {
        val calls = serveSourceAndTarget(destinationPutCode = 409)
        val source = calendar("/source/", "Source")
        val target = calendar("/target/", "Target")
        val repository = repository(MemoryCache())
        repository.setSources(listOf(CalDavSource(source, credentials, "account"), CalDavSource(target, credentials, "account")))
        repository.awaitSync()
        calls.clear()

        val event = repository.events().single()
        assertTrue(repository.updateEvent(event.id, inputFor(event, target.url)) is WriteResult.Rejected)
        assertTrue(calls.none { it.startsWith("DELETE /source/") })
    }

    @Test
    fun `replayed move accepts only its own existing destination copy`() = runBlocking {
        val queueFile = File.createTempFile("calino-move-lost-response", ".json").also { it.delete() }
        val queue = FilePendingChangeStore(queueFile)
        val calls = serveSourceAndTarget(
            destinationPutCodes = ArrayDeque(listOf(503, 412)),
            destinationGetBody = { queue.snapshot().firstOrNull()?.data },
        )
        val source = calendar("/source/", "Source")
        val target = calendar("/target/", "Target")
        val repository = repository(MemoryCache(), queue)
        repository.setSources(listOf(CalDavSource(source, credentials, "account"), CalDavSource(target, credentials, "account")))
        repository.awaitSync()
        calls.clear()

        val event = repository.events().single()
        assertTrue(repository.updateEvent(event.id, inputFor(event, target.url)) is WriteResult.Queued)
        repository.drainPendingWrites()
        awaitQueueCondition(queue) { it.isEmpty() }

        assertEquals(2, calls.count { it.startsWith("PUT /target/") })
        assertTrue(calls.any { it.startsWith("GET /target/") })
        assertTrue(calls.any { it.startsWith("DELETE /source/") })
        queueFile.delete()
        Unit
    }

    @Test
    fun `source cleanup outage queues delete href after destination succeeds`() = runBlocking {
        val calls = serveSourceAndTarget(sourceDeleteCode = 503)
        val cache = MemoryCache()
        val queueFile = File.createTempFile("calino-cleanup-queue", ".json").also { it.delete() }
        val queue = FilePendingChangeStore(queueFile)
        val source = calendar("/source/", "Source")
        val target = calendar("/target/", "Target")
        val repository = repository(cache, queue)
        repository.setSources(listOf(CalDavSource(source, credentials, "account"), CalDavSource(target, credentials, "account")))
        repository.awaitSync()
        calls.clear()

        val event = repository.events().single()
        val result = repository.updateEvent(event.id, inputFor(event, target.url))

        assertTrue(result is WriteResult.Queued)
        assertEquals(PendingChangeType.DELETE_HREF, queue.snapshot().single().type)
        assertEquals(source.url, queue.snapshot().single().calendarUrl)
        assertTrue(calls.any { it.startsWith("PUT /target/") })
        assertTrue(calls.any { it.startsWith("DELETE /source/") })
        queueFile.delete()
        Unit
    }

    @Test
    fun `replayed move inserts source cleanup before later dependent writes`() = runBlocking {
        val destinationResponses: Queue<Int> = ArrayDeque(listOf(503, 201))
        // The first 503 is the source-delete attempt inside MOVE; the second
        // is the queued DELETE_HREF replay. It must remain blocked so the
        // dependent write can prove the cleanup was inserted ahead of it.
        val sourceResponses: Queue<Int> = ArrayDeque(listOf(503, 503))
        val calls = serveSourceAndTarget(
            destinationPutCodes = destinationResponses,
            sourceDeleteCodes = sourceResponses,
        )
        val cache = MemoryCache()
        val queueFile = File.createTempFile("calino-replayed-move", ".json").also { it.delete() }
        val queue = FilePendingChangeStore(queueFile)
        val source = calendar("/source/", "Source")
        val target = calendar("/target/", "Target")
        val repository = repository(cache, queue)
        repository.setSources(listOf(CalDavSource(source, credentials, "account"), CalDavSource(target, credentials, "account")))
        repository.awaitSync()
        calls.clear()

        val event = repository.events().single()
        val queuedMove = repository.updateEvent(event.id, inputFor(event, target.url))
        assertTrue("expected the destination outage to queue a move: $queuedMove", queuedMove is WriteResult.Queued)
        val move = queue.snapshot().single()
        assertEquals(PendingChangeType.MOVE, move.type)
        assertTrue("a queued move must retain its source snapshot", move.sourceData!!.contains("SUMMARY:Move me"))

        val dependentHref = server.url("/target/dependent.ics").toString()
        queue.enqueue(
            PendingChangeRequest(
                type = PendingChangeType.UPDATE,
                eventId = "dependent",
                accountId = "account",
                calendarId = target.url,
                component = "VEVENT",
                calendarUrl = target.url,
                uid = "dependent",
                href = dependentHref,
                etag = "dependent-v1",
                data = "BEGIN:VCALENDAR\nEND:VCALENDAR",
                baseData = "BEGIN:VCALENDAR\nEND:VCALENDAR",
            ),
        )

        repository.drainPendingWrites()
        awaitQueueCondition(queue) { entries ->
            entries.size == 2 &&
                entries[0].type == PendingChangeType.DELETE_HREF &&
                entries[0].state == calino.malinov.ski.data.repository.PendingChangeState.RETRY
        }

        val entries = queue.snapshot()
        assertEquals(
            listOf(PendingChangeType.DELETE_HREF, PendingChangeType.UPDATE),
            entries.map { it.type },
        )
        assertEquals(move.sourceData, entries.first().data)
        assertTrue(calls.any { it.startsWith("PUT /target/") })
        assertTrue(calls.any { it.startsWith("DELETE /source/") })
        queueFile.delete()
        Unit
    }


    /**
     * The timeline drop: a timed event moved to a new hour in the calendar it
     * already lives in. The reported symptom was the card springing back while
     * the server had in fact taken the write, so this asserts on what the UI
     * observes, not only on what the call returned.
     */
    @Test
    fun `same-calendar time move reaches the observed snapshot`() = runBlocking {
        val cache = MemoryCache()
        val source = calendar("/source/", "Source")
        serveTimedEvent()
        val repository = repository(cache)
        repository.setSources(listOf(CalDavSource(source, credentials, "account")))
        repository.awaitSync()

        val event = repository.events().single()
        assertEquals(LocalDateTime.of(2026, 9, 8, 10, 0), event.start)

        val observed = CopyOnWriteArrayList<CalEvent?>()
        val subscription = repository.observe { snap ->
            observed += snap.events.firstOrNull { it.id == event.id }
        }

        val target = LocalDateTime.of(2026, 9, 8, 14, 30)
        val result = repository.moveEventToDateTime(event, target)
        assertTrue("expected the move to be accepted, got $result", result !is WriteResult.Rejected)

        assertEquals("repository state kept the old start", target, repository.events().single().start)
        assertEquals(
            "the last published snapshot kept the old start",
            target,
            observed.last()?.start,
        )
        subscription.close()
    }

    private fun serveTimedEvent(putCode: Int = 204): MutableList<String> {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val sourceXml = """<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
            <response><href>/source/event.ics</href><propstat><prop><getetag>"source-v1"</getetag><C:calendar-data>BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Calino Test//EN
BEGIN:VEVENT
UID:timed-event
DTSTART:20260908T100000Z
DTEND:20260908T110000Z
SUMMARY:Design review
END:VEVENT
END:VCALENDAR
</C:calendar-data></prop><status>HTTP/1.1 200 OK</status></propstat></response>
        </multistatus>""".trimIndent()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val method = request.method.orEmpty()
                calls += "$method $path"
                return when {
                    method == "PUT" ->
                        MockResponse().setResponseCode(putCode).setHeader("ETag", "\"source-v2\"")
                    method == "REPORT" && request.body.readUtf8().contains("VEVENT") -> multiStatus(sourceXml)
                    method == "REPORT" -> multiStatus("<multistatus xmlns=\"DAV:\"/>")
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
        return calls
    }

    private fun repository(cache: CalendarCache, queue: FilePendingChangeStore? = null) = CalDavRepository(
        fetcher = CalDavFetcher(DavHttp()),
        scope = scope,
        cache = cache,
        mapper = ICalMapper(ZoneId.of("UTC")),
        today = { LocalDate.of(2026, 9, 8) },
        pendingStore = queue,
    )

    private fun calendar(path: String, name: String) = DiscoveredCalendar(
        url = server.url(path).toString(),
        displayName = name,
        color = 0xFF11A602,
        readOnly = false,
        components = setOf("VEVENT", "VTODO", "VJOURNAL"),
    )

    private fun inputFor(event: CalEvent, destination: String) = NewEvent(
        title = event.title,
        date = event.date ?: event.start!!.toLocalDate(),
        startTime = event.start?.toLocalTime(),
        durationMinutes = event.durationMinutes,
        allDay = event.allDay,
        color = event.color,
        recurrence = event.recurrence,
        location = event.location,
        notes = event.notes,
        attendees = event.attendees,
        calendarId = destination,
        availability = event.availability,
        categories = event.categories,
        reminders = event.reminders,
        travelTimeMinutes = event.travelTimeMinutes,
        relatedTo = event.relatedTo,
        url = event.url,
        recurrenceId = event.recurrenceId,
        recurrenceDate = event.recurrenceDate,
        sequence = event.sequence,
    )

    private fun serveSourceAndTarget(
        destinationPutCode: Int = 201,
        sourceDeleteCode: Int = 204,
        destinationPutCodes: Queue<Int>? = null,
        sourceDeleteCodes: Queue<Int>? = null,
        destinationGetBody: () -> String? = { null },
    ): MutableList<String> {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val sourceXml = """<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
            <response><href>/source/event.ics</href><propstat><prop><getetag>"source-v1"</getetag><C:calendar-data>BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Calino Test//EN
BEGIN:VEVENT
UID:move-event
DTSTART;VALUE=DATE:20260908
DTEND;VALUE=DATE:20260909
SUMMARY:Move me
END:VEVENT
END:VCALENDAR
</C:calendar-data></prop><status>HTTP/1.1 200 OK</status></propstat></response>
        </multistatus>""".trimIndent()
        val emptyXml = "<multistatus xmlns=\"DAV:\"/>"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val method = request.method.orEmpty()
                calls += "$method $path"
                return when {
                    method == "PUT" && path.startsWith("/target/") ->
                        MockResponse().setResponseCode(destinationPutCodes?.poll() ?: destinationPutCode)
                            .setHeader("ETag", "\"target-v1\"")
                    method == "GET" && path.startsWith("/target/") ->
                        destinationGetBody()?.let { body ->
                            MockResponse().setResponseCode(200).setHeader("ETag", "\"target-v1\"").setBody(body)
                        } ?: MockResponse().setResponseCode(404)
                    method == "DELETE" && path.startsWith("/source/") ->
                        MockResponse().setResponseCode(sourceDeleteCodes?.poll() ?: sourceDeleteCode)
                    method == "REPORT" && path.startsWith("/source/") && request.body.readUtf8().contains("VEVENT") ->
                        multiStatus(sourceXml)
                    method == "REPORT" -> multiStatus(emptyXml)
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
        return calls
    }

    private fun multiStatus(body: String) = MockResponse()
        .setResponseCode(207)
        .setHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(body)

    private fun CalDavRepository.awaitSync(): SyncState {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val state = snapshot().sync
            if (state is SyncState.Ready || state is SyncState.Failed) return state
            Thread.sleep(10)
        }
        error("repository fetch did not settle: ${snapshot().sync}")
    }

    private fun awaitQueueCondition(
        queue: FilePendingChangeStore,
        condition: (List<calino.malinov.ski.data.repository.PendingChange>) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val entries = queue.snapshot()
            if (condition(entries)) return
            Thread.sleep(10)
        }
        error("queue did not reach the expected state: ${queue.snapshot()}")
    }

    private fun assertApplied(result: WriteResult<CalEvent>): CalEvent {
        assertTrue("expected applied result, got $result", result is WriteResult.Applied)
        return (result as WriteResult.Applied).record
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
