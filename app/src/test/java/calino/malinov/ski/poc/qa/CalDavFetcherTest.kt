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

    private fun enqueueAll() {
        server.enqueue(multiStatus(CalDavFixtures.Events))
        server.enqueue(multiStatus(CalDavFixtures.Todos))
        server.enqueue(multiStatus(CalDavFixtures.Journals))
    }

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
    fun `only advertised components are requested`() = runBlocking {
        server.enqueue(multiStatus(CalDavFixtures.Events))
        fetcher().fetch(calendar(components = setOf("VEVENT")), credentials, windowStart, windowEnd)
        assertEquals("a VEVENT-only calendar should be asked once", 1, server.requestCount)
    }

    @Test
    fun `a calendar advertising nothing is asked for all three components`() = runBlocking {
        enqueueAll()
        // Absent metadata is not a statement that the calendar is empty.
        fetcher().fetch(calendar(components = emptySet()), credentials, windowStart, windowEnd)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `the event query asks the server to expand recurrence`() = runBlocking {
        server.enqueue(multiStatus(CalDavFixtures.Events))
        fetcher().fetch(calendar(components = setOf("VEVENT")), credentials, windowStart, windowEnd)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("REPORT", request.method)
        assertEquals("1", request.getHeader("Depth"))
        assertTrue("the query must ask for expansion", body.contains("<c:expand"))
        assertTrue(body.contains("""start="20260901T000000Z""""))
        assertTrue(body.contains("""end="20261001T000000Z""""))
    }

    @Test
    fun `the task query is neither time-ranged nor expanded`() = runBlocking {
        server.enqueue(multiStatus(CalDavFixtures.Todos))
        fetcher().fetch(calendar(components = setOf("VTODO")), credentials, windowStart, windowEnd)

        val body = server.takeRequest().body.readUtf8()
        // A VTODO may carry no DTSTART or DUE at all, and a time-range filter
        // drops exactly those. This server also ignores expand for VTODO.
        assertFalse("tasks must not be time-ranged", body.contains("time-range"))
        assertFalse("tasks must not be expanded", body.contains("expand"))
        assertTrue(body.contains("""name="VTODO""""))
    }

    @Test
    fun `one component failing does not lose the others`() = runBlocking {
        // The regression this guards: an erroring task query used to abort the
        // whole fetch and drop every event with it.
        server.enqueue(multiStatus(CalDavFixtures.Events))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(multiStatus(CalDavFixtures.Journals))

        val result = fetcher().fetch(calendar(), credentials, windowStart, windowEnd)

        assertTrue("events must survive a task-query failure", result.events.isNotEmpty())
        assertTrue(result.journals.isNotEmpty())
        assertTrue(result.tasks.isEmpty())
        assertTrue("a partial result must say so", result.hadComponentFailures)
    }

    @Test
    fun `every component failing surfaces the error`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(401)) }
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
        server.enqueue(multiStatus(unexpanded))

        val result = fetcher().fetch(calendar(setOf("VEVENT")), credentials, windowStart, windowEnd)

        assertTrue(
            "a surviving RRULE means the whole series is showing as one event",
            result.expandUnsupported,
        )
        assertEquals(1, result.events.size)
    }

    @Test
    fun `a recurring task is reported as expanded even though events are not`() = runBlocking {
        // This server ignores expand for VTODO, so 'Water the plants' arrives
        // with its RRULE intact. That must not be mistaken for a server that
        // cannot expand events.
        server.enqueue(multiStatus(CalDavFixtures.Todos))
        val result = fetcher().fetch(calendar(setOf("VTODO")), credentials, windowStart, windowEnd)

        assertTrue(result.tasks.any { it.title == "Water the plants" })
        assertFalse(
            "the expand check is about events; an unexpanded task must not trip it",
            result.expandUnsupported,
        )
    }

    @Test
    fun `credentials are sent as basic auth on every request`() = runBlocking {
        server.enqueue(multiStatus(CalDavFixtures.Events))
        fetcher().fetch(calendar(setOf("VEVENT")), credentials, windowStart, windowEnd)
        val header = server.takeRequest().getHeader("Authorization")
        assertEquals("Basic dGVzdC11c2VyOnRlc3QtcGFzcw==", header)
    }
}
