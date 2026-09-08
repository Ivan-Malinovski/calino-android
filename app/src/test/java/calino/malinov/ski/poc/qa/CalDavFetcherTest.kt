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
    fun `the event query asks the server to expand recurrence`() = runBlocking {
        enqueueAll()
        fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        val request = server.takeRequest()
        assertEquals("REPORT", request.method)
        assertEquals("1", request.getHeader("Depth"))

        val eventQuery = seenBodies.first { it.contains("""name="VEVENT"""") }
        assertTrue("the query must ask for expansion", eventQuery.contains("<c:expand"))
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
    fun `a server that ignores expand is reported rather than under-rendering`() = runBlocking {
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

        assertTrue(
            "a surviving RRULE means the whole series is showing as one event",
            result.expandUnsupported,
        )
        assertEquals(1, result.events.size)
    }

    @Test
    fun `a server that rejects expand still returns its events`() = runBlocking {
        // Falling back to an unexpanded query keeps recurring events showing
        // only on their first date -- reported via expandUnsupported -- rather
        // than showing no events at all.
        var firstEventQuery = true
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    body.contains("VTODO") -> multiStatus(CalDavFixtures.Todos)
                    body.contains("VJOURNAL") -> multiStatus(CalDavFixtures.Journals)
                    body.contains("expand") && firstEventQuery -> {
                        firstEventQuery = false
                        MockResponse().setResponseCode(400)
                    }
                    else -> multiStatus(CalDavFixtures.Events)
                }
            }
        }

        val result = fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        assertTrue("the fallback must recover the events", result.events.isNotEmpty())
        assertTrue("and must report that recurrence is not expanded", result.expandUnsupported)
        assertFalse("the fallback succeeded, so this is not a failure", result.hadComponentFailures)
    }

    @Test
    fun `a recurring task is reported as expanded even though events are not`() = runBlocking {
        // This server ignores expand for VTODO, so 'Water the plants' arrives
        // with its RRULE intact. That must not be mistaken for a server that
        // cannot expand events.
        serveAll()
        val result = fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        assertTrue(result.tasks.any { it.title == "Water the plants" })
        assertFalse(
            "the expand check is about events; an unexpanded task must not trip it",
            result.expandUnsupported,
        )
    }

    @Test
    fun `a sabre server that crashes on expand still returns its events`() = runBlocking {
        // The Baikal regression, in the server's own words. sabre parses the
        // collection's calendar-timezone property while expanding; a calendar
        // holding a bare TZID there instead of a whole VCALENDAR makes the
        // expanded query 500, which used to take every event with it and leave
        // a calendar showing only its tasks.
        val sabreError = MockResponse().setResponseCode(500).setBody(
            """<?xml version="1.0" encoding="utf-8"?>
<d:error xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns">
  <s:sabredav-version>4.7.0</s:sabredav-version>
  <s:exception>Sabre\VObject\ParseException</s:exception>
  <s:message>This parser only supports VCARD and VCALENDAR files</s:message>
</d:error>""",
        )
        var firstEventQuery = true
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    body.contains("VTODO") -> multiStatus(CalDavFixtures.Todos)
                    body.contains("VJOURNAL") -> multiStatus(CalDavFixtures.Journals)
                    body.contains("expand") && firstEventQuery -> {
                        firstEventQuery = false
                        sabreError
                    }
                    else -> multiStatus(CalDavFixtures.Events)
                }
            }
        }

        val result = fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        assertTrue("the events must survive the server's crash", result.events.isNotEmpty())
        assertTrue(result.events.any { it.title == "Day off" })
        assertTrue(result.tasks.isNotEmpty())
        assertTrue("and the loss of expansion must be reported", result.expandUnsupported)
    }

    @Test
    fun `credentials are sent as basic auth on every request`() = runBlocking {
        enqueueAll()
        fetcher().fetch(calendar(), credentials, windowStart, windowEnd)
        assertEquals(3, seenAuth.size)
        assertTrue(seenAuth.all { it == "Basic dGVzdC11c2VyOnRlc3QtcGFzcw==" })
    }
}
