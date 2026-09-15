package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.CachedCalendar
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalDavSource
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.data.repository.WriteResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The repository's contract, loading behaviour, and local-overlay posture.
 *
 * These run against a real MockWebServer socket on a real scope rather than a
 * virtual-time dispatcher: the fetch completes on OkHttp's own threads, which
 * a test scheduler does not control, so [awaitSync] waits for the state to
 * settle instead.
 */
class CalDavRepositoryTest {

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

    private fun repository(
        cache: CalendarCache = CalendarCache.None,
        windowMonths: Long = CalDavRepository.DefaultWindowMonths,
    ) = CalDavRepository(
        fetcher = CalDavFetcher(DavHttp()),
        scope = scope,
        cache = cache,
        mapper = ICalMapper(ZoneId.of("Europe/Copenhagen")),
        today = { LocalDate.of(2026, 9, 8) },
        windowMonths = windowMonths,
    )

    /** A cache held in memory, so the repository's use of it is observable. */
    private class FakeCache : CalendarCache {
        val entries = mutableMapOf<String, CachedCalendar>()
        var evictions = 0

        override fun load(calendarUrl: String): CachedCalendar? = entries[calendarUrl]
        override fun save(entry: CachedCalendar) { entries[entry.calendarUrl] = entry }
        override fun evictExcept(calendarUrls: Set<String>) {
            evictions++
            entries.keys.retainAll(calendarUrls)
        }

        override fun loadResource(calendarUrl: String, href: String): CalendarResource? =
            entries[calendarUrl]?.resources?.firstOrNull { it.href == href }

        override fun saveResource(calendarUrl: String, resource: CalendarResource) {
            val entry = entries[calendarUrl] ?: return
            entries[calendarUrl] = entry.copy(
                resources = entry.resources.filterNot { it.href == resource.href } + resource,
            )
        }

        override fun deleteResource(calendarUrl: String, href: String) {
            val entry = entries[calendarUrl] ?: return
            entries[calendarUrl] = entry.copy(resources = entry.resources.filterNot { it.href == href })
        }
    }

    /** Blocks until the repository stops loading, or fails the test. */
    private fun CalDavRepository.awaitSync(): SyncState {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val state = snapshot().sync
            if (state is SyncState.Ready || state is SyncState.Failed) return state
            Thread.sleep(10)
        }
        throw AssertionError("The fetch never settled; last state was ${snapshot().sync}")
    }

    private fun calendar(path: String = "/cal/", name: String = "hellyeah") = DiscoveredCalendar(
        url = server.url(path).toString(),
        displayName = name,
        color = 0xFF11A602,
        readOnly = false,
        components = setOf("VEVENT", "VTODO", "VJOURNAL"),
    )

    private fun source(calendar: DiscoveredCalendar = calendar()) =
        CalDavSource(calendar, credentials, "account")

    private fun multiStatus(body: String) =
        MockResponse().setResponseCode(207).setBody(body)
            .setHeader("Content-Type", "application/xml; charset=utf-8")

    /**
     * Serves each component its own fixture, routed by the query body, since
     * the three component queries run concurrently and an event query may
     * retry without expand.
     */
    private fun serveAll(
        events: MockResponse = multiStatus(CalDavFixtures.Events),
        todos: MockResponse = multiStatus(CalDavFixtures.Todos),
        journals: MockResponse = multiStatus(CalDavFixtures.Journals),
    ) {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                if (request.method == "PUT") {
                    return MockResponse().setResponseCode(201).setHeader("ETag", "\"written\"")
                }
                if (request.method == "DELETE") {
                    return MockResponse().setResponseCode(204)
                }
                val body = request.body.readUtf8()
                return when {
                    body.contains("VTODO") -> todos
                    body.contains("VJOURNAL") -> journals
                    else -> events
                }
            }
        }
    }

    private fun enqueueAll() = serveAll()

    // --- the observe contract -------------------------------------------------

    @Test
    fun `observe hands over the current snapshot synchronously`() = runBlocking {
        val repository = repository()
        var received: CalinoSnapshot? = null
        // Synchronous, not eventual: the Compose bridge seeds its state from
        // this call, and deferring it renders an empty first frame.
        repository.observe { received = it }
        assertTrue("observe must emit before it returns", received != null)
    }

    @Test
    fun `a closed subscription stops receiving snapshots`() = runBlocking {
        val repository = repository()
        var count = 0
        val subscription = repository.observe { count++ }
        val afterSubscribe = count
        subscription.close()
        repository.addTask(NewTask(title = "Local", color = 0L, due = LocalDate.of(2026, 9, 8)))
        assertEquals("a closed listener must not be called again", afterSubscribe, count)
    }

    // --- sync state -----------------------------------------------------------

    @Test
    fun `sync moves from idle through loading to ready`() = runBlocking {
        val repository = repository()
        val states = mutableListOf<SyncState>()
        repository.observe { states += it.sync }

        assertEquals(SyncState.Idle, states.first())

        enqueueAll()
        repository.setSources(listOf(source()))
        val settled = repository.awaitSync()

        assertTrue(
            "a loading state must be published before the answer arrives",
            states.any { it is SyncState.Loading },
        )
        assertTrue("expected a ready state, got $settled", settled is SyncState.Ready)
        assertFalse((settled as SyncState.Ready).partial)
    }

    @Test
    fun `a failed fetch reports the reason and keeps the previous data`() = runBlocking {
        val repository = repository()
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()
        val loaded = repository.snapshot().events.size
        assertTrue(loaded > 0)

        serveAll(
            events = MockResponse().setResponseCode(500),
            todos = MockResponse().setResponseCode(500),
            journals = MockResponse().setResponseCode(500),
        )
        repository.refresh()
        val sync = repository.awaitSync()
        assertTrue("expected a failure, got $sync", sync is SyncState.Failed)
        assertTrue((sync as SyncState.Failed).hadPreviousData)
        // Blanking the calendar because a refresh failed is worse than showing
        // stale data next to a clear error.
        assertEquals("previous data must survive a failed refresh", loaded, repository.snapshot().events.size)
    }

    @Test
    fun `a server that will not expand is no longer a partial result`() = runBlocking {
        val repository = repository()
        serveAll(events = multiStatus(
            """<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                 <response><href>/cal/s.ics</href><propstat><prop><getetag>"e"</getetag>
                 <C:calendar-data>BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:s
DTSTART:20260907T210000Z
RRULE:FREQ=WEEKLY
SUMMARY:Series
END:VEVENT
END:VCALENDAR
</C:calendar-data></prop><status>HTTP/1.1 200 OK</status></propstat></response>
               </multistatus>"""
        ))
        repository.setSources(listOf(source(calendar())))
        repository.awaitSync()

        val sync = repository.snapshot().sync
        assertTrue(sync is SyncState.Ready)
        // The app expands recurrence itself now, so a master with its RRULE
        // intact is the expected reply rather than a gap in the answer.
        assertFalse("nothing is missing; the client expands", (sync as SyncState.Ready).partial)
        assertTrue("no warning is warranted: ${sync.warnings}", sync.warnings.isEmpty())
        val events = repository.snapshot().events
        assertTrue("the weekly series must reach many dates", events.size > 20)
        assertEquals(1, events.mapNotNull { it.uid }.distinct().size)
    }

    // --- what the snapshot contains -------------------------------------------

    @Test
    fun `the snapshot carries real events tasks and journals`() = runBlocking {
        val repository = repository()
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()

        val snapshot = repository.snapshot()
        assertTrue(snapshot.events.any { it.title == "Day off" })
        assertTrue(snapshot.tasks.any { it.title == "Renew passport" })
        assertTrue(snapshot.journals.any { it.title == "Notes: recurrence rework" })
    }

    @Test
    fun `calendars come from the connected collections not the fixtures`() = runBlocking {
        val repository = repository()
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()

        val calendars = repository.snapshot().calendars
        assertEquals(1, calendars.size)
        assertEquals("hellyeah", calendars.single().name)
        assertFalse(
            "the fixture calendar list must not leak through",
            calendars.any { it.id == "personal" },
        )
    }

    @Test
    fun `dropping a source removes exactly its records`() = runBlocking {
        val repository = repository()
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()
        assertTrue(repository.snapshot().events.isNotEmpty())

        // This is what disabling a calendar's toggle does.
        repository.setSources(emptyList())
        assertTrue("a disabled calendar's events must leave the snapshot", repository.snapshot().events.isEmpty())
        assertEquals(SyncState.Idle, repository.snapshot().sync)
    }

    // --- the local overlay ----------------------------------------------------

    @Test
    fun `a direct write shows immediately and reaches the server`() = runBlocking {
        val repository = repository()
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()
        val requestsBefore = server.requestCount

        val task = (repository.addTask(
            NewTask(title = "Local only", color = 0L, due = LocalDate.of(2026, 9, 8)),
        ) as WriteResult.Applied).record

        assertTrue(repository.snapshot().tasks.any { it.id == task.id })
        assertTrue("a direct write must reach the server", server.requestCount > requestsBefore)
    }

    @Test
    fun `a new event with the legacy default calendar uses the first connected calendar`() = runBlocking {
        val repository = repository()
        enqueueAll()
        val connected = calendar()
        repository.setSources(listOf(source(connected)))
        repository.awaitSync()

        val result = repository.addEvent(
            NewEvent(
                title = "Default destination",
                date = LocalDate.of(2026, 9, 8),
                startTime = java.time.LocalTime.of(10, 0),
                durationMinutes = 30,
            ),
        )

        assertTrue("expected a real write, got $result", result is WriteResult.Applied)
        assertEquals(connected.url, (result as WriteResult.Applied).record.calendarId)
    }

    @Test
    fun `completing a fetched task is undoable`() = runBlocking {
        val repository = repository(FakeCache())
        enqueueAll()
        repository.setSources(listOf(source(calendar("/test-user/bed21d90-1639-2490-b6f5-721e0517aee6/"))))
        repository.awaitSync()

        val passport = repository.snapshot().tasks.first { it.title == "Renew passport" }
        assertFalse(passport.done)

        val changeResult = repository.setTaskDone(passport.id, true)
        assertTrue("write result: $changeResult", changeResult is WriteResult.Applied)
        val change = (changeResult as WriteResult.Applied).record
        assertTrue(repository.snapshot().tasks.first { it.id == passport.id }.done)

        assertTrue(repository.undo(change) is WriteResult.Applied)
        assertFalse(repository.snapshot().tasks.first { it.id == passport.id }.done)
    }

    @Test
    fun `a successful write survives a refetch until the server confirms it`() = runBlocking {
        val repository = repository()
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()

        val local = (repository.addTask(
            NewTask(title = "Scratch", color = 0L, due = LocalDate.of(2026, 9, 8)),
        ) as WriteResult.Applied).record
        assertTrue(repository.snapshot().tasks.any { it.id == local.id })

        enqueueAll()
        repository.refresh()
        repository.awaitSync()

        assertTrue(
            "a successful write must not be dropped by a concurrent/stale refetch",
            repository.snapshot().tasks.any { it.id == local.id },
        )
    }

    // --- the cache ------------------------------------------------------------

    @Test
    fun `a successful fetch is cached as the server's own text`() = runBlocking {
        val cache = FakeCache()
        val repository = repository(cache)
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()

        val entry = cache.entries[calendar().url]
        assertTrue("the fetch must be cached", entry != null)
        // Raw iCalendar, not mapped occurrences: that is what lets it be
        // re-expanded for a different window later.
        assertTrue(entry!!.resources.isNotEmpty())
        assertTrue(entry.resources.all { it.ics.contains("BEGIN:VCALENDAR") })
        assertEquals(LocalDate.of(2024, 9, 1), entry.windowStart)
        assertEquals(LocalDate.of(2028, 9, 1), entry.windowEnd)
    }

    @Test
    fun `navigation beyond the event report boundary extends the cached window`() = runBlocking {
        val cache = FakeCache()
        val repository = repository(cache, windowMonths = 6)
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()

        // CalDAV time-range ends are exclusive. March 1 is the first day
        // outside the initial [2026-03-01, 2027-03-01) event report.
        assertTrue(repository.extendWindowToInclude(LocalDate.of(2027, 3, 1)))
        repository.awaitSync()

        val extended = cache.entries.getValue(calendar().url)
        assertEquals(LocalDate.of(2026, 2, 1), extended.windowStart)
        assertEquals(LocalDate.of(2027, 4, 1), extended.windowEnd)
        assertFalse(
            "a date already covered by the widened report must not refetch",
            repository.extendWindowToInclude(LocalDate.of(2027, 3, 1)),
        )
    }

    @Test
    fun `a cached calendar renders before the server answers`() = runBlocking {
        val cache = FakeCache()
        cache.entries[calendar().url] = CachedCalendar(
            calendarUrl = calendar().url,
            fetchedAt = Instant.parse("2026-09-07T09:00:00Z"),
            windowStart = LocalDate.of(2026, 3, 1),
            windowEnd = LocalDate.of(2027, 3, 1),
            resources = listOf(CalendarResource("/cal/cached.ics", "e", CachedIcs)),
        )
        // Nothing is served: the server never answers, so anything on screen
        // came off the cache.
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                MockResponse().setResponseCode(500)
        }

        val repository = repository(cache)
        repository.setSources(listOf(source()))
        val deadline = System.currentTimeMillis() + 10_000
        while (repository.snapshot().events.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        assertTrue(
            "the cached copy must render without waiting on the server",
            repository.snapshot().events.any { it.title == "Cached lunch" },
        )
    }

    @Test
    fun `startup cache can be published before setSources returns`() = runBlocking {
        val cache = FakeCache()
        cache.entries[calendar().url] = CachedCalendar(
            calendarUrl = calendar().url,
            fetchedAt = Instant.parse("2026-09-07T09:00:00Z"),
            windowStart = LocalDate.of(2026, 3, 1),
            windowEnd = LocalDate.of(2027, 3, 1),
            resources = listOf(CalendarResource("/cal/cached.ics", "e", CachedIcs)),
        )
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                MockResponse().setResponseCode(500)
        }

        val repository = repository(cache)
        repository.setSources(listOf(source()), restoreCacheImmediately = true)

        assertTrue(
            "the first UI snapshot must already contain the disk cache",
            repository.snapshot().events.any { it.title == "Cached lunch" },
        )
    }

    @Test
    fun `a cached copy survives a launch with no network`() = runBlocking {
        val cache = FakeCache()
        val repository = repository(cache)
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()
        val loaded = repository.snapshot().events.size

        // A fresh repository over the same cache, with every request failing:
        // the offline cold start.
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                MockResponse().setResponseCode(503)
        }
        val relaunched = repository(cache)
        relaunched.setSources(listOf(source()))
        val sync = relaunched.awaitSync()

        assertTrue("expected a failure, got $sync", sync is SyncState.Failed)
        assertTrue("the cache must have filled the calendar", (sync as SyncState.Failed).hadPreviousData)
        assertEquals(loaded, relaunched.snapshot().events.size)
    }

    @Test
    fun `a refresh over a full calendar says what is on screen`() = runBlocking {
        // Otherwise a manual refresh reads as "reading calendars", which over a
        // calendar that is already full implies it might be empty until the
        // server answers.
        val repository = repository(FakeCache())
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()

        val states = mutableListOf<SyncState>()
        repository.observe { states += it.sync }
        repository.refresh()
        repository.awaitSync()

        val loading = states.filterIsInstance<SyncState.Loading>()
        assertTrue("expected a loading state", loading.isNotEmpty())
        assertTrue(
            "a refresh over existing data must report when that data was read",
            loading.all { it.cachedAt != null },
        )
    }

    @Test
    fun `an unchanged source set does not refetch`() = runBlocking {
        // A cold start calls setSources twice: once from the persisted account
        // list, once when rediscovery confirms it. The second must not discard
        // the loaded cache or fetch again.
        val repository = repository(FakeCache())
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()
        val requests = server.requestCount

        repository.setSources(listOf(source()))
        Thread.sleep(200)

        assertEquals("the same collections must not be refetched", requests, server.requestCount)
        assertTrue(repository.snapshot().sync is SyncState.Ready)
    }

    @Test
    fun `dropping a calendar evicts its cache`() = runBlocking {
        val cache = FakeCache()
        val repository = repository(cache)
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()
        assertTrue(cache.entries.isNotEmpty())

        repository.setSources(emptyList())
        assertTrue("a removed calendar must not leave its data on disk", cache.entries.isEmpty())
    }

    @Test
    fun `one unreachable calendar does not blank the others`() = runBlocking {
        val repository = repository()
        val good = calendar("/good/", "hellyeah")
        val bad = calendar("/bad/", "extra calendar")
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                when {
                    request.path?.startsWith("/bad/") == true -> MockResponse().setResponseCode(500)
                    request.body.readUtf8().contains("VEVENT") -> multiStatus(CalDavFixtures.Events)
                    else -> multiStatus(CalDavFixtures.Journals)
                }
        }

        repository.setSources(listOf(source(good), source(bad)))
        repository.awaitSync()

        assertTrue("the reachable calendar must still render", repository.snapshot().events.isNotEmpty())
        val sync = repository.snapshot().sync
        assertTrue(sync is SyncState.Ready)
        assertTrue("a partial answer must say so", (sync as SyncState.Ready).partial)
    }

    private companion object {
        val CachedIcs = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:cached-lunch
DTSTART:20260908T110000Z
DTEND:20260908T120000Z
SUMMARY:Cached lunch
END:VEVENT
END:VCALENDAR
"""
    }
}
