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
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalDavSource
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.FilePendingChangeStore
import calino.malinov.ski.data.repository.PendingChangeStore
import calino.malinov.ski.data.repository.PendingChangeType
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.data.repository.WriteResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
        pendingStore: PendingChangeStore? = null,
    ) = CalDavRepository(
        fetcher = CalDavFetcher(DavHttp()),
        scope = scope,
        cache = cache,
        mapper = ICalMapper(ZoneId.of("Europe/Copenhagen")),
        today = { LocalDate.of(2026, 9, 8) },
        windowMonths = windowMonths,
        pendingStore = pendingStore,
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

    @Test
    fun `a saved event shows a later server change to the same resource without a restart`() = runBlocking {
        val repository = repository(FakeCache())
        enqueueAll()
        repository.setSources(listOf(source(calendar("/test-user/bed21d90-1639-2490-b6f5-721e0517aee6/"))))
        repository.awaitSync()

        val dayOff = repository.snapshot().events.first { it.title == "Day off" }
        val saved = repository.updateEvent(
            dayOff.id,
            NewEvent(
                title = "Day off (edited here)",
                date = dayOff.date!!,
                allDay = true,
                calendarId = dayOff.calendarId,
                sequence = dayOff.sequence,
            ),
        )
        assertTrue("write result: $saved", saved is WriteResult.Applied)
        assertEquals("written", (saved as WriteResult.Applied).record.etag)

        // Someone else (an attendee's RSVP, another client) now changes the
        // same resource, so the server serves it under a new ETag.
        serveAll(
            events = multiStatus(
                CalDavFixtures.Events
                    .replace("SUMMARY:Day off", "SUMMARY:Day off (changed elsewhere)")
                    .replace("3a32a3c8d9ed05d14625e4bd031336b8795bc68e5969ef305c7dd7d137bcce5d", "changed-elsewhere"),
            ),
        )
        repository.refresh()
        repository.awaitSync()

        assertEquals(
            "an acknowledged edit must not mask what the server now holds",
            "Day off (changed elsewhere)",
            repository.snapshot().events.first { it.id == dayOff.id }.title,
        )
    }

    @Test
    fun `editing a whole series leaves the series visible after the refetch`() = runBlocking {
        val repository = repository(FakeCache())
        serveAll(events = multiStatus(CalDavFixtures.Recurring))
        repository.setSources(listOf(source(calendar("/dav.php/calendars/test-user/mail_events/"))))
        repository.awaitSync()

        val work = repository.snapshot().events.filter { it.title == "Work" }
        assertTrue("fixture should expand to many rows", work.size > 5)
        val target = work.first()
        val saved = repository.updateEvent(
            target.id,
            NewEvent(
                title = "Work edited",
                date = target.start!!.toLocalDate(),
                startTime = target.start!!.toLocalTime(),
                durationMinutes = target.durationMinutes,
                allDay = false,
                calendarId = target.calendarId,
                recurrence = target.recurrence,
                recurrenceScope = RecurrenceEditScope.All,
                sequence = target.sequence,
            ),
        )
        assertTrue("write result: $saved", saved is WriteResult.Applied)

        serveAll(
            events = multiStatus(
                CalDavFixtures.Recurring
                    .replace("SUMMARY:Work", "SUMMARY:Work edited")
                    .replace("\"work-1\"", "\"work-2\""),
            ),
        )
        // synchronize() returns when the refetch is done. refresh() launches it
        // on the scope, so awaitSync() could see the old Ready state and return
        // before the refetch had started.
        repository.synchronize()

        val after = repository.snapshot().events.count { it.title == "Work edited" }
        assertEquals("the rewritten series must come back under its unchanged ids", work.size, after)
    }

    /** A PUT as the server saw it: path, precondition header value, body. */
    private data class RecordedPut(val path: String, val precondition: String, val body: String)

    /** Serves the recurring fixture and records every PUT. */
    private fun serveRecurringRecordingPuts(putStatus: Int = 201): MutableList<RecordedPut> {
        val puts = java.util.concurrent.CopyOnWriteArrayList<RecordedPut>()
        val etags = AtomicInteger()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                if (request.method == "PUT") {
                    puts += RecordedPut(
                        request.path.orEmpty(),
                        request.getHeader("If-None-Match") ?: request.getHeader("If-Match").orEmpty(),
                        request.body.readUtf8(),
                    )
                    if (putStatus != 201) return MockResponse().setResponseCode(putStatus)
                    return MockResponse().setResponseCode(201).setHeader("ETag", "\"written-${etags.incrementAndGet()}\"")
                }
                val body = request.body.readUtf8()
                return when {
                    body.contains("VTODO") -> multiStatus(CalDavFixtures.Todos)
                    body.contains("VJOURNAL") -> multiStatus(CalDavFixtures.Journals)
                    else -> multiStatus(CalDavFixtures.Recurring)
                }
            }
        }
        return puts
    }

    /** Edits the fourth "Work" occurrence, i.e. not the first, in [scope]. */
    private suspend fun CalDavRepository.editFourthWorkOccurrence(scope: RecurrenceEditScope): WriteResult<*> {
        val target = snapshot().events.filter { it.title == "Work" }.sortedBy { it.start }[3]
        return updateEvent(
            target.id,
            NewEvent(
                title = "Work edited",
                date = target.start!!.toLocalDate(),
                startTime = target.start!!.toLocalTime(),
                durationMinutes = target.durationMinutes,
                allDay = false,
                calendarId = target.calendarId,
                recurrence = target.recurrence,
                recurrenceScope = scope,
                sequence = target.sequence,
            ),
        )
    }

    @Test
    fun `editing this and following events writes each series to its own resource`() = runBlocking {
        val repository = repository(FakeCache())
        val puts = serveRecurringRecordingPuts()
        repository.setSources(listOf(source(calendar("/dav.php/calendars/test-user/mail_events/"))))
        repository.awaitSync()

        val saved = repository.editFourthWorkOccurrence(RecurrenceEditScope.Future)
        assertTrue("write result: $saved", saved is WriteResult.Applied)

        // RFC 4791 4.1: a resource holds exactly one UID. Radicale answers 400
        // to anything else, so the two halves must be two writes.
        assertEquals("one PUT per series: ${puts.map { it.path }}", 2, puts.size)
        assertEquals("two different resources", 2, puts.map { it.path }.distinct().size)
        val uidsOf = { body: String -> Regex("(?m)^UID:(.*?)\\r?$").findAll(body).map { it.groupValues[1] }.toSet() }
        puts.forEach { put ->
            assertEquals("${put.path} must carry exactly one UID but had ${uidsOf(put.body)}", 1, uidsOf(put.body).size)
        }
        assertEquals("the series have different UIDs", 2, puts.flatMap { uidsOf(it.body) }.distinct().size)
        assertTrue("the new series is created, not overwritten", puts.any { it.precondition == "*" })
        assertTrue("the old series is updated conditionally", puts.any { it.precondition.isNotEmpty() && it.precondition != "*" })
        val old = puts.single { it.precondition != "*" }
        assertTrue("the old series is cut short", old.body.contains("UNTIL="))
        assertEquals("the new series is written first", "*", puts.first().precondition)
    }

    @Test
    fun `a split made offline queues the new series before the old one`() = runBlocking {
        val queueFile = java.io.File.createTempFile("calino-split-queue", ".json").also { it.deleteOnExit() }
        val store = FilePendingChangeStore(queueFile)
        val repository = repository(FakeCache(), pendingStore = store)
        // Reads work, every write fails with a retryable status.
        serveRecurringRecordingPuts(putStatus = 503)
        repository.setSources(listOf(source(calendar("/dav.php/calendars/test-user/mail_events/"))))
        repository.awaitSync()

        val saved = repository.editFourthWorkOccurrence(RecurrenceEditScope.Future)
        assertTrue("write result: $saved", saved is WriteResult.Queued)

        val queued = store.snapshot()
        assertEquals("a create and an update: ${queued.map { it.type }}", 2, queued.size)
        // Same order as the online path: if only one ever replays, it is the
        // harmless one (an extra series), never a truncated original without
        // its continuation.
        assertEquals(listOf(PendingChangeType.CREATE, PendingChangeType.UPDATE), queued.map { it.type })
        assertEquals("separate resources", 2, queued.map { it.href }.distinct().size)
        assertEquals("separate events", 2, queued.map { it.eventId }.distinct().size)
        val uidsOf = { body: String -> Regex("(?m)^UID:(.*?)\\r?$").findAll(body).map { it.groupValues[1] }.toSet() }
        queued.forEach { change ->
            assertEquals("${change.href} must carry one UID", 1, uidsOf(change.data.orEmpty()).size)
        }
    }

    @Test
    fun `editing one occurrence leaves the series master alone`() = runBlocking {
        // RecurrenceEdit needs the master as the server has it. Handing the
        // live master to the writer as the edit's "original" moved its DTSTART
        // to the edited occurrence, so a later split looked like the first.
        val repository = repository(FakeCache())
        val puts = serveRecurringRecordingPuts()
        repository.setSources(listOf(source(calendar("/dav.php/calendars/test-user/mail_events/"))))
        repository.awaitSync()

        val saved = repository.editFourthWorkOccurrence(RecurrenceEditScope.This)
        assertTrue("write result: $saved", saved is WriteResult.Applied)

        val body = puts.single().body
        assertTrue(body, body.contains("DTSTART:20260518T060000Z"))
        assertTrue(body, body.contains("RECURRENCE-ID:20260521T060000Z"))
        assertEquals("master plus one override", 2, Regex("(?m)^BEGIN:VEVENT").findAll(body).count())
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
    fun `simultaneous awaitable syncs serialize their repository reads`() = runBlocking {
        val firstBatchStarted = CountDownLatch(3)
        val releaseFirstBatch = CountDownLatch(1)
        val totalReports = AtomicInteger()
        val laterBatchStarted = CountDownLatch(1)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                val number = totalReports.incrementAndGet()
                if (number <= 3) {
                    firstBatchStarted.countDown()
                    check(releaseFirstBatch.await(5, TimeUnit.SECONDS))
                } else {
                    laterBatchStarted.countDown()
                }
                return when {
                    body.contains("VTODO") -> multiStatus(CalDavFixtures.Todos)
                    body.contains("VJOURNAL") -> multiStatus(CalDavFixtures.Journals)
                    else -> multiStatus(CalDavFixtures.Events)
                }
            }
        }
        val repository = repository()
        repository.setSources(listOf(source()), refreshAfterSourceChange = false)

        val first = async(Dispatchers.IO) { repository.synchronize() }
        assertTrue("the first sync did not start its component reads", firstBatchStarted.await(5, TimeUnit.SECONDS))
        val second = async(Dispatchers.IO) { repository.synchronize() }
        Thread.sleep(100)
        assertEquals("the second sync must wait behind the first", 3, server.requestCount)

        releaseFirstBatch.countDown()
        assertTrue(first.await() is calino.malinov.ski.data.repository.RepositorySyncResult.Success)
        assertTrue(second.await() is calino.malinov.ski.data.repository.RepositorySyncResult.Success)
        assertTrue(laterBatchStarted.await(5, TimeUnit.SECONDS))
        assertEquals(6, totalReports.get())
    }

    @Test
    fun `account removal aborts an in-flight awaitable read before it publishes`() = runBlocking {
        val requestsStarted = CountDownLatch(3)
        val releaseRequests = CountDownLatch(1)
        val isCurrent = AtomicBoolean(true)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                requestsStarted.countDown()
                check(releaseRequests.await(5, TimeUnit.SECONDS))
                return when {
                    body.contains("VTODO") -> multiStatus(CalDavFixtures.Todos)
                    body.contains("VJOURNAL") -> multiStatus(CalDavFixtures.Journals)
                    else -> multiStatus(CalDavFixtures.Events)
                }
            }
        }
        val cache = FakeCache()
        val repository = repository(cache)
        repository.setSources(listOf(source()), refreshAfterSourceChange = false)

        val sync = async(Dispatchers.IO) { repository.synchronize { isCurrent.get() } }
        assertTrue("the read did not reach the server", requestsStarted.await(5, TimeUnit.SECONDS))
        isCurrent.set(false)
        releaseRequests.countDown()

        assertEquals(
            calino.malinov.ski.data.repository.RepositorySyncResult.AccountsRemoved,
            sync.await(),
        )
        assertTrue("removed-account data must not publish", repository.snapshot().events.isEmpty())
        assertTrue("removed-account bytes must not repopulate cache", cache.entries.isEmpty())
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
