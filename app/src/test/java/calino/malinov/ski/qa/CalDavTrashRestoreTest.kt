package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CachedCalendar
import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalDavSource
import calino.malinov.ski.data.repository.FilePendingChangeStore
import calino.malinov.ski.data.repository.PendingChangeType
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.data.repository.UndoableChange
import calino.malinov.ski.data.repository.WriteResult
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalDavTrashRestoreTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val credentials = DavCredentials("test-user", "test-pass")

    /** href -> body, the "server". */
    private val store = ConcurrentHashMap<String, String>()
    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    @Volatile private var deleteCode = 204

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path.orEmpty()
                return when (request.method) {
                    "PUT" -> {
                        val exists = store.containsKey(path)
                        if (request.getHeader("If-None-Match") == "*" && exists) {
                            MockResponse().setResponseCode(412)
                        } else {
                            store[path] = request.body.readUtf8()
                            MockResponse().setResponseCode(201).setHeader("ETag", "\"v${store.size}\"")
                        }
                    }
                    "DELETE" -> if (deleteCode == 204) {
                        store.remove(path)
                        MockResponse().setResponseCode(204)
                    } else {
                        MockResponse().setResponseCode(deleteCode)
                    }
                    "GET" -> store[path]?.let { MockResponse().setResponseCode(200).setHeader("ETag", "\"v1\"").setBody(it) }
                        ?: MockResponse().setResponseCode(404)
                    "REPORT" -> {
                        val body = request.body.readUtf8()
                        val component = listOf("VEVENT", "VTODO", "VJOURNAL").firstOrNull { body.contains(it) }
                        val entries = store.entries.filter { component != null && it.value.contains("BEGIN:$component") }
                        MockResponse().setResponseCode(207)
                            .setHeader("Content-Type", "application/xml; charset=utf-8")
                            .setBody(
                                "<multistatus xmlns=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">" +
                                    entries.joinToString("") {
                                        "<response><href>${it.key}</href><propstat><prop><getetag>\"v1\"</getetag>" +
                                            "<C:calendar-data>${it.value}</C:calendar-data></prop>" +
                                            "<status>HTTP/1.1 200 OK</status></propstat></response>"
                                    } + "</multistatus>",
                            )
                    }
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun ics(component: String, uid: String, summary: String, extra: String = "") =
        "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//Calino Test//EN\nBEGIN:$component\nUID:$uid\n" +
            (if (component == "VEVENT") "DTSTART;VALUE=DATE:20260908\nDTEND;VALUE=DATE:20260909\n" else "") +
            (if (component == "VJOURNAL") "DTSTART;VALUE=DATE:20260908\n" else "") +
            "SUMMARY:$summary\n$extra" + "END:$component\nEND:VCALENDAR\n"

    private fun calendar() = DiscoveredCalendar(
        url = server.url("/cal/").toString(),
        displayName = "Cal",
        color = 0xFF11A602,
        readOnly = false,
        components = setOf("VEVENT", "VTODO", "VJOURNAL"),
    )

    private suspend fun connected(queue: FilePendingChangeStore? = null): CalDavRepository {
        val repository = CalDavRepository(
            fetcher = CalDavFetcher(DavHttp()),
            scope = scope,
            cache = MemoryCache(),
            mapper = ICalMapper(ZoneId.of("UTC")),
            today = { LocalDate.of(2026, 9, 8) },
            pendingStore = queue,
        )
        repository.setSources(listOf(CalDavSource(calendar(), credentials, "account")))
        repository.awaitSync()
        return repository
    }

    private fun CalDavRepository.awaitSync() {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val state = snapshot().sync
            if (state is SyncState.Ready || state is SyncState.Failed) return
            Thread.sleep(10)
        }
        error("sync did not settle")
    }

    private fun puts() = requests.filter { it.method == "PUT" }

    private fun change(result: WriteResult<UndoableChange?>): UndoableChange {
        val value = when (result) {
            is WriteResult.Applied -> result.record
            is WriteResult.Queued -> result.record
            else -> null
        }
        return assertNotNull("delete should offer an undo: $result", value).let { value!! }
    }

    @Test
    fun `deleted event is recreated at its href with its raw bytes`() = runBlocking {
        val body = ics("VEVENT", "ev-1", "Dentist", "X-FOREIGN;X-PARAM=1:keep me\n")
        store["/cal/ev-1.ics"] = body
        val repository = connected()
        val event = repository.events().single()

        val offered = change(repository.deleteEvent(event.id))
        assertTrue(store.isEmpty())
        assertEquals(1, repository.trashedItems().size)
        requests.clear()

        assertTrue(repository.undo(offered) is WriteResult.Applied)

        val put = puts().single()
        assertEquals("/cal/ev-1.ics", put.path)
        assertEquals("*", put.getHeader("If-None-Match"))
        assertTrue(store.getValue("/cal/ev-1.ics").contains("X-FOREIGN;X-PARAM=1:keep me"))
        assertTrue(repository.trashedItems().isEmpty())
        awaitEvents(repository) { it.events().size == 1 }
        assertEquals("Dentist", repository.events().single().title)
    }

    @Test
    fun `deleted task and journal are restored`() = runBlocking {
        store["/cal/t-1.ics"] = ics("VTODO", "t-1", "Buy milk", "X-KEEP:1\n")
        store["/cal/j-1.ics"] = ics("VJOURNAL", "j-1", "Diary", "X-KEEP:2\n")
        val repository = connected()

        val task = change(repository.deleteTask(repository.tasks().single().id))
        val journal = change(repository.deleteJournal(repository.journals().single().id))
        assertTrue(store.isEmpty())

        assertTrue(repository.undo(journal) is WriteResult.Applied)
        assertTrue(repository.undo(task) is WriteResult.Applied)

        assertTrue(store.getValue("/cal/t-1.ics").contains("X-KEEP:1"))
        assertTrue(store.getValue("/cal/j-1.ics").contains("X-KEEP:2"))
    }

    @Test
    fun `restore while offline queues a create instead of failing`() = runBlocking {
        store["/cal/ev-2.ics"] = ics("VEVENT", "ev-2", "Offline")
        val queueFile = File.createTempFile("calino-trash-queue", ".json").also { it.delete() }
        val queue = FilePendingChangeStore(queueFile)
        val repository = connected(queue)
        val offered = change(repository.deleteEvent(repository.events().single().id))

        server.shutdown()
        val result = repository.undo(offered)

        assertTrue("expected queued restore: $result", result is WriteResult.Queued)
        assertTrue(queue.snapshot().any { it.type == PendingChangeType.CREATE && it.href?.endsWith("ev-2.ics") == true })
        assertTrue(repository.trashedItems().isEmpty())
        queueFile.delete()
        Unit
    }

    @Test
    fun `undo of a delete still waiting in the queue replays the create after it`() = runBlocking {
        store["/cal/ev-3.ics"] = ics("VEVENT", "ev-3", "Queued delete", "X-KEEP:3\n")
        deleteCode = 503
        val queueFile = File.createTempFile("calino-trash-queue", ".json").also { it.delete() }
        val queue = FilePendingChangeStore(queueFile)
        val repository = connected(queue)

        val offered = change(repository.deleteEvent(repository.events().single().id))
        assertTrue(queue.snapshot().any { it.type == PendingChangeType.DELETE })

        val result = repository.undo(offered)

        assertTrue("expected queued restore: $result", result is WriteResult.Queued)
        assertEquals(
            listOf(PendingChangeType.DELETE, PendingChangeType.CREATE),
            queue.snapshot().map { it.type },
        )
        deleteCode = 204
        repository.drainPendingWrites()
        val deadline = System.currentTimeMillis() + 10_000
        while (queue.snapshot().isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(queue.snapshot().isEmpty())
        assertTrue(store.getValue("/cal/ev-3.ics").contains("X-KEEP:3"))
        queueFile.delete()
        Unit
    }

    @Test
    fun `restore of an unknown or purged entry is rejected`() = runBlocking {
        store["/cal/ev-4.ics"] = ics("VEVENT", "ev-4", "Gone")
        val repository = connected()
        val offered = change(repository.deleteEvent(repository.events().single().id))
        repository.emptyTrash()

        assertTrue(repository.undo(offered) is WriteResult.Rejected)
        assertTrue(store.isEmpty())
    }

    private fun awaitEvents(repository: CalDavRepository, condition: (CalDavRepository) -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (condition(repository)) return
            Thread.sleep(10)
        }
        error("repository did not reach the expected state")
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
