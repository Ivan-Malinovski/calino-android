package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.CalDavFetcher
import calino.malinov.ski.poc.data.caldav.DavCredentials
import calino.malinov.ski.poc.data.caldav.DavHttp
import calino.malinov.ski.poc.data.caldav.DiscoveredCalendar
import calino.malinov.ski.poc.data.caldav.ICalMapper
import calino.malinov.ski.poc.data.model.NewTask
import calino.malinov.ski.poc.data.repository.CalDavRepository
import calino.malinov.ski.poc.data.repository.CalDavSource
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import calino.malinov.ski.poc.data.repository.SyncState
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

    private fun repository() = CalDavRepository(
        fetcher = CalDavFetcher(DavHttp(), ICalMapper(ZoneId.of("Europe/Copenhagen"))),
        scope = scope,
        today = { LocalDate.of(2026, 9, 8) },
    )

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

    private fun enqueueAll() {
        server.enqueue(multiStatus(CalDavFixtures.Events))
        server.enqueue(multiStatus(CalDavFixtures.Todos))
        server.enqueue(multiStatus(CalDavFixtures.Journals))
    }

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
            states.contains(SyncState.Loading),
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

        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        repository.refresh()
        val sync = repository.awaitSync()
        assertTrue("expected a failure, got $sync", sync is SyncState.Failed)
        assertTrue((sync as SyncState.Failed).hadPreviousData)
        // Blanking the calendar because a refresh failed is worse than showing
        // stale data next to a clear error.
        assertEquals("previous data must survive a failed refresh", loaded, repository.snapshot().events.size)
    }

    @Test
    fun `an expand-ignoring server marks the result partial`() = runBlocking {
        val repository = repository()
        server.enqueue(multiStatus(
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
        repository.setSources(listOf(source(calendar().copy(components = setOf("VEVENT")))))
        repository.awaitSync()

        val sync = repository.snapshot().sync
        assertTrue(sync is SyncState.Ready)
        assertTrue("an unexpanded series is an incomplete answer", (sync as SyncState.Ready).partial)
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
    fun `a local edit shows immediately and reaches no server`() = runBlocking {
        val repository = repository()
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()
        val requestsBefore = server.requestCount

        val task = repository.addTask(
            NewTask(title = "Local only", color = 0L, due = LocalDate.of(2026, 9, 8)),
        )

        assertTrue(repository.snapshot().tasks.any { it.id == task.id })
        assertEquals("a local edit must not be written to the server", requestsBefore, server.requestCount)
    }

    @Test
    fun `completing a fetched task is undoable`() = runBlocking {
        val repository = repository()
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()

        val passport = repository.snapshot().tasks.first { it.title == "Renew passport" }
        assertFalse(passport.done)

        val change = repository.setTaskDone(passport.id, true)
        assertTrue(repository.snapshot().tasks.first { it.id == passport.id }.done)

        assertTrue(repository.undo(change))
        assertFalse(repository.snapshot().tasks.first { it.id == passport.id }.done)
    }

    @Test
    fun `a refetch discards local edits made against the previous answer`() = runBlocking {
        val repository = repository()
        enqueueAll()
        repository.setSources(listOf(source()))
        repository.awaitSync()

        val local = repository.addTask(
            NewTask(title = "Scratch", color = 0L, due = LocalDate.of(2026, 9, 8)),
        )
        assertTrue(repository.snapshot().tasks.any { it.id == local.id })

        enqueueAll()
        repository.refresh()
        repository.awaitSync()

        assertFalse(
            "the server's answer supersedes edits made against the previous one",
            repository.snapshot().tasks.any { it.id == local.id },
        )
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
}
