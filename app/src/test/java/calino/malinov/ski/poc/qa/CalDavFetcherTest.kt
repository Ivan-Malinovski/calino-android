package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.CalDavErrorCode
import calino.malinov.ski.poc.data.caldav.CalDavException
import calino.malinov.ski.poc.data.caldav.CalDavFetcher
import calino.malinov.ski.poc.data.caldav.DavCredentials
import calino.malinov.ski.poc.data.caldav.DavHttp
import calino.malinov.ski.poc.data.caldav.DiscoveredCalendar
import calino.malinov.ski.poc.data.caldav.ICalMapper
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Fetching, with the failure shapes that matter. */
class CalDavFetcherTest {

    private lateinit var server: MockWebServer
    private val credentials = DavCredentials("test-user", "test-pass")
    private val windowStart = LocalDate.of(2026, 9, 1)
    private val windowEnd = LocalDate.of(2026, 10, 1)

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun fetcher() = CalDavFetcher(DavHttp(), ICalMapper(ZoneId.of("Europe/Copenhagen")))

    private fun calendar(components: Set<String> = setOf("VEVENT", "VTODO", "VJOURNAL")) =
        DiscoveredCalendar(
            url = server.url("/cal/").toString(),
            displayName = "hellyeah",
            color = 0xFF11A602,
            readOnly = false,
            components = components,
        )

    private fun multiStatus(body: String) =
        MockResponse().setResponseCode(207).setBody(body)
            .setHeader("Content-Type", "application/xml; charset=utf-8")

    /**
     * Serves each component its own fixture, routed by the query body.
     *
     * The three component queries run concurrently, so a plain enqueued queue
     * would hand responses out in arrival order and match them to the wrong
     * component from run to run.
     */
    /** Request bodies seen by [serveAll], in arrival order. */
    private val seenBodies = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val seenAuth = java.util.Collections.synchronizedList(mutableListOf<String?>())

    private fun serveAll(
        events: MockResponse = multiStatus(CalDavFixtures.Events),
        todos: MockResponse = multiStatus(CalDavFixtures.Todos),
        journals: MockResponse = multiStatus(CalDavFixtures.Journals),
    ) {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                // The body is read here to route, which drains it; record it so
                // assertions can inspect the query that was actually sent.
                val body = request.body.readUtf8()
                seenBodies += body
                seenAuth += request.getHeader("Authorization")
                return when {
                    body.contains("VTODO") -> todos
                    body.contains("VJOURNAL") -> journals
                    else -> events
                }
            }
        }
    }

    private fun enqueueAll() = serveAll()

    @Test
    fun `a full fetch returns events tasks and journals`() = runBlocking {
        enqueueAll()
        val result = fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        assertTrue("expected events", result.events.isNotEmpty())
        assertTrue("expected tasks", result.tasks.isNotEmpty())
        assertTrue("expected journal entries", result.journals.isNotEmpty())
        assertFalse(result.hadComponentFailures)
        assertTrue(result.events.any { it.title == "Day off" })
        assertTrue(result.tasks.any { it.title == "Renew passport" })
        assertTrue(result.journals.any { it.title == "Notes: recurrence rework" })
    }

    @Test
    fun `fetched records carry the calendar's id and colour`() = runBlocking {
        enqueueAll()
        val result = fetcher().fetch(calendar(), credentials, windowStart, windowEnd)
        val calendarUrl = calendar().url
        assertTrue(result.events.all { it.calendarId == calendarUrl })
        assertTrue(result.events.all { it.color == 0xFF11A602 })
    }

    @Test
    fun `a calendar advertising only tasks is still asked for its events`() = runBlocking {
        // The Baikal regression. A calendar may advertise VTODO only in
        // supported-calendar-component-set and still hold events; a calendar
        // created by a task app commonly does. Gating the event query on that
        // property hid the whole calendar and, since nothing had failed,
        // reported the empty result as complete.
        enqueueAll()
        val result = fetcher().fetch(
            calendar(components = setOf("VTODO")),
            credentials, windowStart, windowEnd,
        )

        assertEquals("all three components must be requested", 3, server.requestCount)
        assertTrue("events must be read from a VTODO-only calendar", result.events.isNotEmpty())
        assertTrue(result.tasks.isNotEmpty())
    }

    @Test
    fun `a calendar advertising nothing is asked for all three components`() = runBlocking {
        enqueueAll()
        fetcher().fetch(calendar(components = emptySet()), credentials, windowStart, windowEnd)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `the event query is time-ranged and never asks the server to expand`() = runBlocking {
        enqueueAll()
        fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        val request = server.takeRequest()
        assertEquals("REPORT", request.method)
        assertEquals("1", request.getHeader("Depth"))

        val eventQuery = seenBodies.first { it.contains("""name="VEVENT"""") }
        // Server-side expansion is not asked for at all: sabre 500s on it for
        // any collection with a malformed calendar-timezone, and other servers
        // ignore it silently. ICalMapper expands instead.
        assertFalse("expansion is the client's job now", eventQuery.contains("expand"))
        assertTrue(eventQuery.contains("""start="20260901T000000Z""""))
        assertTrue(eventQuery.contains("""end="20261001T000000Z""""))
    }

    @Test
    fun `the task query is neither time-ranged nor expanded`() = runBlocking {
        enqueueAll()
        fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        val body = seenBodies.first { it.contains("""name="VTODO"""") }
        // A VTODO may carry no DTSTART or DUE at all, and a time-range filter
        // drops exactly those. This server also ignores expand for VTODO.
        assertFalse("tasks must not be time-ranged", body.contains("time-range"))
        assertFalse("tasks must not be expanded", body.contains("expand"))
    }

    @Test
    fun `one component failing does not lose the others`() = runBlocking {
        // The regression this guards: an erroring task query used to abort the
        // whole fetch and drop every event with it.
        serveAll(todos = MockResponse().setResponseCode(500))

        val result = fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        assertTrue("events must survive a task-query failure", result.events.isNotEmpty())
        assertTrue(result.journals.isNotEmpty())
        assertTrue(result.tasks.isEmpty())
        assertTrue("a partial result must say so", result.hadComponentFailures)
        // And it must say *what* is missing, not merely that something is.
        val described = result.failures.joinToString { it.describe() }
        assertTrue("the failure should name the tasks: $described", described.contains("Tasks"))
    }

    @Test
    fun `every component failing surfaces the error`() = runBlocking {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                MockResponse().setResponseCode(401)
        }
        val error = runCatching {
            fetcher().fetch(calendar(), credentials, windowStart, windowEnd)
        }.exceptionOrNull()
        assertTrue("expected a CalDAV error, got $error", error is CalDavException)
        assertEquals(CalDavErrorCode.Auth, (error as CalDavException).code)
    }

    @Test
    fun `a repeating master is expanded by the client across the window`() = runBlocking {
        // The server returns the master with its RRULE intact -- which is now
        // the only shape the app ever asks for. Before client-side expansion
        // this rendered a weekday series as a single event.
        val unexpanded = """<?xml version="1.0"?>
            <multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <response><href>/cal/series.ics</href><propstat><prop>
                <getetag>"abc"</getetag>
                <C:calendar-data>BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:series
DTSTART:20260907T210000Z
RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR
SUMMARY:Night check-in
END:VEVENT
END:VCALENDAR
</C:calendar-data>
              </prop><status>HTTP/1.1 200 OK</status></propstat></response>
            </multistatus>"""
        serveAll(events = multiStatus(unexpanded))
        val result = fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        assertTrue("the series must expand to one occurrence per weekday", result.events.size > 15)
        assertEquals("one series, so one uid", 1, result.events.mapNotNull { it.uid }.distinct().size)
        assertEquals(
            "and one occurrence per day",
            result.events.size,
            result.events.mapNotNull { it.start?.toLocalDate() }.distinct().size,
        )
    }

    @Test
    fun `a sabre server is never given the query that crashes it`() = runBlocking {
        // The Baikal regression: sabre parses the collection's
        // calendar-timezone property while expanding, and a calendar holding a
        // bare TZID there instead of a whole VCALENDAR makes the expanded query
        // 500. Every request is now checked to make sure that query is gone.
        serveAll()
        val result = fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        assertTrue(seenBodies.isNotEmpty())
        seenBodies.forEach { assertFalse("no request may ask for expansion: $it", it.contains("expand")) }
        assertTrue(result.events.any { it.title == "Day off" })
        assertTrue(result.tasks.isNotEmpty())
    }

    @Test
    fun `credentials are sent as basic auth on every request`() = runBlocking {
        enqueueAll()
        fetcher().fetch(calendar(), credentials, windowStart, windowEnd)
        assertEquals(3, seenAuth.size)
        assertTrue(seenAuth.all { it == "Basic dGVzdC11c2VyOnRlc3QtcGFzcw==" })
    }
}
