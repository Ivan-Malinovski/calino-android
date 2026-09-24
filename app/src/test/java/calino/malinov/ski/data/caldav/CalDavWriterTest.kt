package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalDavWriterTest {
    private lateinit var server: MockWebServer
    private lateinit var cache: MemoryCache
    private val credentials = DavCredentials("test-user", "test-pass")

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        cache = MemoryCache()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun calendar() = DiscoveredCalendar(
        url = server.url("/cal/").toString(),
        displayName = "Calendar",
        color = 0xFF11A602,
        readOnly = false,
        components = setOf("VEVENT", "VTODO", "VJOURNAL"),
    )

    private fun event(
        calendar: DiscoveredCalendar,
        href: String? = null,
        etag: String? = null,
        uid: String = "event:1",
        title: String = "Calino event",
    ) = CalEvent(
        id = uid,
        uid = uid,
        title = title,
        color = 0L,
        start = LocalDateTime.of(2030, 1, 2, 10, 0),
        durationMinutes = 45,
        calendarId = calendar.url,
        href = href,
        etag = etag,
    )

    private fun emptyCache(calendar: DiscoveredCalendar) {
        cache.save(
            CachedCalendar(
                calendarUrl = calendar.url,
                fetchedAt = Instant.EPOCH,
                windowStart = LocalDate.of(2029, 1, 1),
                windowEnd = LocalDate.of(2031, 1, 1),
                resources = emptyList(),
            ),
        )
    }

    @Test
    fun `create uses a safe href and if-none-match and caches the accepted resource`() = runBlocking {
        val calendar = calendar()
        emptyCache(calendar)
        server.enqueue(MockResponse().setResponseCode(201).setHeader("ETag", "\"created\""))

        val result = CalDavWriter(
            cache = cache,
            now = { Instant.parse("2026-09-10T12:00:00Z") },
        ).putEvent(calendar, credentials, event(calendar))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/cal/event~3A1.ics", request.requestUrl!!.encodedPath)
        assertEquals("*", request.getHeader("If-None-Match"))
        assertNull(request.getHeader("If-Match"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("text/calendar"))
        assertTrue(request.body.readUtf8().contains("UID:event:1"))
        assertEquals("created", result.etag)
        assertEquals(result.ics, cache.load(calendar.url)!!.resources.single().ics)
    }

    @Test
    fun `matching cached etag patches foreign properties and sends if-match`() = runBlocking {
        val calendar = calendar()
        val href = server.url("/cal/event.ics").toString()
        val raw = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Remote//EN
BEGIN:VEVENT
UID:event:1
DTSTART:20300102T100000Z
DTEND:20300102T104500Z
SUMMARY:Old title
ORGANIZER;CN=Remote:mailto:remote@example.com
X-SERVER-ONLY:keep-me
END:VEVENT
END:VCALENDAR
"""
        cache.save(
            CachedCalendar(
                calendarUrl = calendar.url,
                fetchedAt = Instant.EPOCH,
                windowStart = LocalDate.of(2029, 1, 1),
                windowEnd = LocalDate.of(2031, 1, 1),
                resources = listOf(CalendarResource(href, "old", raw)),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "W/\"new\""))

        val result = CalDavWriter(
            cache = cache,
            now = { Instant.parse("2026-09-10T12:00:00Z") },
        ).putEvent(
            calendar,
            credentials,
            event(calendar, href = href, etag = "\"old\"", title = "Updated title"),
        )

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("PUT", request.method)
        assertEquals("\"old\"", request.getHeader("If-Match"))
        assertNull(request.getHeader("If-None-Match"))
        assertTrue(body.contains("SUMMARY:Updated title"))
        assertTrue(body.contains("ORGANIZER;CN=Remote:mailto:remote@example.com"))
        assertTrue(body.contains("X-SERVER-ONLY:keep-me"))
        assertFalse(body.contains("SUMMARY:Old title"))
        assertEquals("new", result.etag)
        assertEquals(result.ics, cache.loadResource(calendar.url, href)!!.ics)
    }

    @Test
    fun `an etag mismatch rebuilds instead of copying stale server-only properties`() = runBlocking {
        val calendar = calendar()
        val href = server.url("/cal/event.ics").toString()
        cache.save(
            CachedCalendar(
                calendarUrl = calendar.url,
                fetchedAt = Instant.EPOCH,
                windowStart = LocalDate.of(2029, 1, 1),
                windowEnd = LocalDate.of(2031, 1, 1),
                resources = listOf(
                    CalendarResource(
                        href,
                        "server-version",
                        """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:event:1
DTSTART:20300102T100000Z
SUMMARY:Server title
X-SERVER-ONLY:must-not-be-resurrected
END:VEVENT
END:VCALENDAR
""",
                    ),
                ),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "client-version"))

        val result = CalDavWriter(cache = cache).putEvent(
            calendar,
            credentials,
            event(calendar, href = href, etag = "client-version", title = "Client title"),
        )

        val request = server.takeRequest()
        assertEquals("\"client-version\"", request.getHeader("If-Match"))
        assertTrue(request.body.readUtf8().contains("SUMMARY:Client title"))
        assertFalse(result.ics.contains("X-SERVER-ONLY:must-not-be-resurrected"))
    }

    @Test
    fun `missing put etag reads back server representation and validator`() = runBlocking {
        val calendar = calendar()
        emptyCache(calendar)
        server.enqueue(MockResponse().setResponseCode(201))
        val stored = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nX-SERVER:normalized\r\nEND:VCALENDAR\r\n"
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"stored\"").setBody(stored))

        val result = CalDavWriter(cache = cache).putEvent(calendar, credentials, event(calendar))
        val put = server.takeRequest()
        val get = server.takeRequest()

        assertNull(put.getHeader("ETag"))
        assertEquals("GET", get.method)
        assertEquals("stored", result.etag)
        assertEquals(stored, result.ics)
        assertEquals(stored, cache.loadResource(calendar.url, result.href)!!.ics)
    }

    @Test
    fun `missing new validator is not silently paired with the old etag`() = runBlocking {
        val calendar = calendar()
        val href = server.url("/cal/event.ics").toString()
        val raw = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:event:1
DTSTART:20300102T100000Z
SUMMARY:Old title
END:VEVENT
END:VCALENDAR
"""
        cache.save(
            CachedCalendar(
                calendarUrl = calendar.url,
                fetchedAt = Instant.EPOCH,
                windowStart = LocalDate.of(2029, 1, 1),
                windowEnd = LocalDate.of(2031, 1, 1),
                resources = listOf(CalendarResource(href, "old", raw)),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = CalDavWriter(cache = cache).putEvent(
            calendar,
            credentials,
            event(calendar, href = href, etag = "old", title = "Updated title"),
        )

        assertNull(result.etag)
        assertNull(cache.loadResource(calendar.url, href)!!.etag)
    }

    @Test
    fun `deleting one component patches a shared resource and preserves its sibling`() = runBlocking {
        val calendar = calendar()
        val href = server.url("/cal/shared.ics").toString()
        val raw = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:event:1
DTSTART:20300102T100000Z
SUMMARY:Remove me
END:VEVENT
BEGIN:VEVENT
UID:event:2
DTSTART:20300103T100000Z
SUMMARY:Keep me
END:VEVENT
END:VCALENDAR
"""
        cache.save(
            CachedCalendar(
                calendarUrl = calendar.url,
                fetchedAt = Instant.EPOCH,
                windowStart = LocalDate.of(2029, 1, 1),
                windowEnd = LocalDate.of(2031, 1, 1),
                resources = listOf(CalendarResource(href, "shared", raw)),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "after-delete"))

        CalDavWriter(cache = cache).delete(
            calendar = calendar,
            credentials = credentials,
            href = href,
            uid = "event:1",
            etag = "shared",
            component = "VEVENT",
        )

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("PUT", request.method)
        assertEquals("\"shared\"", request.getHeader("If-Match"))
        assertFalse(body.contains("UID:event:1"))
        assertTrue(body.contains("UID:event:2"))
        assertEquals("after-delete", cache.loadResource(calendar.url, href)!!.etag)
    }

    @Test
    fun `recurring task preparation patches the cached group conditionally`() = runBlocking {
        val calendar = calendar()
        val href = server.url("/cal/task.ics").toString()
        cache.save(
            CachedCalendar(
                calendarUrl = calendar.url,
                fetchedAt = Instant.EPOCH,
                windowStart = LocalDate.of(2029, 1, 1),
                windowEnd = LocalDate.of(2031, 1, 1),
                resources = listOf(
                    CalendarResource(
                        href,
                        "task-version",
                        """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VTODO
UID:task:1
DTSTART;VALUE=DATE:20300102
RRULE:FREQ=DAILY
SUMMARY:Repeat
END:VTODO
END:VCALENDAR
""",
                    ),
                ),
            ),
        )

        val prepared = CalDavWriter(cache = cache).prepareTask(
            calendar,
            CalTask(
                id = "task:1",
                uid = "task:1",
                title = "Updated",
                color = 0L,
                due = LocalDate.of(2030, 1, 2),
                href = href,
                etag = "task-version",
            ),
        )

        assertEquals(DavPrecondition.Match("task-version"), prepared.precondition)
        assertTrue(prepared.body.contains("SUMMARY:Updated"))
        assertTrue(prepared.body.contains("RRULE:FREQ=DAILY"))
        assertTrue(prepared.body.contains("DTSTART;VALUE=DATE:20300102"))
        assertEquals(0, server.requestCount)
    }

    private class MemoryCache : CalendarCache {
        private val entries = linkedMapOf<String, CachedCalendar>()

        override fun load(calendarUrl: String): CachedCalendar? = entries[calendarUrl]
        override fun save(entry: CachedCalendar) {
            entries[entry.calendarUrl] = entry
        }
        override fun evictExcept(calendarUrls: Set<String>) {
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
            entries[calendarUrl]?.let { entry ->
                entries[calendarUrl] = entry.copy(resources = entry.resources.filterNot { it.href == href })
            }
        }
    }
}
