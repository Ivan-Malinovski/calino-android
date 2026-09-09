package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.CachedCalendar
import calino.malinov.ski.poc.data.caldav.CalendarCacheJson
import calino.malinov.ski.poc.data.caldav.CalendarResource
import calino.malinov.ski.poc.data.caldav.FileCalendarCache
import calino.malinov.ski.poc.data.caldav.ICalMapper
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The on-disk read cache.
 *
 * Every failure here is a launch-time failure, so the bias throughout is that
 * an unreadable cache yields nothing and the calendar refetches -- never an
 * exception on the way into the app.
 */
class CalendarCacheTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("calino-cache-test").toFile()
    }

    private val url = "https://caldav.example.com/dav.php/calendars/test-user/default/"

    private fun entry(
        calendarUrl: String = url,
        resources: List<CalendarResource> = listOf(
            CalendarResource("$calendarUrl/one.ics", "etag-1", Ics),
            CalendarResource("$calendarUrl/two.ics", null, Ics.replace("lunch", "dinner")),
        ),
    ) = CachedCalendar(
        calendarUrl = calendarUrl,
        fetchedAt = Instant.parse("2026-09-09T07:30:00Z"),
        windowStart = LocalDate.of(2026, 3, 1),
        windowEnd = LocalDate.of(2027, 3, 1),
        resources = resources,
    )

    // --- the round trip -------------------------------------------------------

    @Test
    fun `a saved calendar reads back unchanged`() {
        val cache = FileCalendarCache(root)
        cache.save(entry())

        val loaded = cache.load(url)
        assertEquals(entry(), loaded)
        // The ETag rides along: it is what an incremental sync would need.
        assertEquals("etag-1", loaded!!.resources.first().etag)
        assertNull("a missing ETag must not become an empty string", loaded.resources[1].etag)
    }

    @Test
    fun `a calendar that was never cached loads as nothing`() {
        assertNull(FileCalendarCache(root).load(url))
    }

    @Test
    fun `saving twice replaces rather than appends`() {
        val cache = FileCalendarCache(root)
        cache.save(entry())
        cache.save(entry(resources = listOf(CalendarResource("$url/only.ics", "e", Ics))))

        assertEquals(1, cache.load(url)!!.resources.size)
        assertEquals(1, root.listFiles()!!.size)
    }

    @Test
    fun `two calendars do not collide`() {
        val cache = FileCalendarCache(root)
        val other = "https://caldav.example.com/dav.php/calendars/test-user/work/"
        cache.save(entry())
        cache.save(entry(calendarUrl = other))

        assertEquals(url, cache.load(url)!!.calendarUrl)
        assertEquals(other, cache.load(other)!!.calendarUrl)
    }

    @Test
    fun `no temporary file is left behind`() {
        FileCalendarCache(root).save(entry())
        assertTrue(root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    // --- what comes back is still mappable ------------------------------------

    @Test
    fun `a cached series expands into a window it was not fetched for`() {
        // The reason raw iCalendar is cached rather than mapped occurrences: a
        // month later the window has moved, and the master still expands into
        // it without going back to the server.
        val cache = FileCalendarCache(root)
        cache.save(entry(resources = listOf(CalendarResource("$url/series.ics", "e", SeriesIcs))))

        val mapper = ICalMapper(ZoneId.of("Europe/Copenhagen"))
        val later = mapper.mapAll(
            resources = cache.load(url)!!.resources,
            calendarId = url,
            color = 0L,
            windowStart = LocalDate.of(2027, 1, 1),
            windowEnd = LocalDate.of(2027, 2, 1),
        )

        assertTrue("the series must reach the later window", later.events.size > 20)
        assertTrue(
            "and only that window",
            later.events.all { it.start!!.toLocalDate() >= LocalDate.of(2027, 1, 1) },
        )
    }

    // --- eviction -------------------------------------------------------------

    @Test
    fun `eviction drops the calendars no longer wanted and keeps the rest`() {
        val cache = FileCalendarCache(root)
        val other = "https://caldav.example.com/dav.php/calendars/test-user/work/"
        cache.save(entry())
        cache.save(entry(calendarUrl = other))

        cache.evictExcept(setOf(url))

        assertEquals(url, cache.load(url)!!.calendarUrl)
        assertNull("a disabled calendar's content must leave the disk", cache.load(other))
    }

    @Test
    fun `evicting everything empties the directory`() {
        val cache = FileCalendarCache(root)
        cache.save(entry())
        cache.evictExcept(emptySet())
        assertEquals(0, root.listFiles()!!.size)
    }

    // --- damage ---------------------------------------------------------------

    @Test
    fun `a corrupt file loads as nothing rather than throwing`() {
        val cache = FileCalendarCache(root)
        cache.save(entry())
        root.listFiles()!!.first().writeText("this is not gzipped json")

        assertNull(cache.load(url))
    }

    @Test
    fun `a truncated file loads as nothing`() {
        val cache = FileCalendarCache(root)
        cache.save(entry())
        val file = root.listFiles()!!.first()
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size / 2))

        assertNull(cache.load(url))
    }

    @Test
    fun `an unknown version is discarded rather than misread`() {
        val raw = CalendarCacheJson.encode(entry()).replace("\"version\":1", "\"version\":99")
        assertNull(CalendarCacheJson.decode(raw))
    }

    @Test
    fun `malformed json decodes to nothing`() {
        assertNull(CalendarCacheJson.decode("{not json"))
        assertNull(CalendarCacheJson.decode(""))
        assertNull(CalendarCacheJson.decode(null))
    }

    @Test
    fun `a resource missing its text is skipped, not fatal`() {
        // One usable resource and one with no `ics` field. Losing the damaged
        // record is right; losing the calendar with it is not.
        val raw = """{"version":1,"calendarUrl":"$url",
            "fetchedAt":"2026-09-09T07:30:00Z","windowStart":"2026-03-01","windowEnd":"2027-03-01",
            "resources":[{"href":"$url/one.ics","etag":"e","ics":"$IcsOneLine"},
                         {"href":"$url/two.ics"}]}"""

        val decoded = CalendarCacheJson.decode(raw)
        assertEquals(1, decoded!!.resources.size)
        assertEquals("$url/one.ics", decoded.resources.single().href)
    }

    // --- the size guards ------------------------------------------------------

    @Test
    fun `an oversized resource is dropped rather than cached`() {
        val cache = FileCalendarCache(root)
        val huge = CalendarResource("$url/huge.ics", "e", "X".repeat(1_000_001))
        cache.save(entry(resources = listOf(CalendarResource("$url/one.ics", "e", Ics), huge)))

        val loaded = cache.load(url)!!
        assertEquals(1, loaded.resources.size)
        assertFalse(loaded.resources.any { it.href.endsWith("huge.ics") })
    }

    @Test
    fun `an oversized calendar is not written at all`() {
        val cache = FileCalendarCache(root)
        val many = (1..20).map { CalendarResource("$url/$it.ics", "e", "X".repeat(500_000)) }
        cache.save(entry(resources = many))

        assertNull("the cache must not grow without bound", cache.load(url))
    }

    private companion object {
        val Ics = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:lunch
DTSTART:20260908T110000Z
DTEND:20260908T120000Z
SUMMARY:lunch
END:VEVENT
END:VCALENDAR
"""

        /** The same, on one line, for a hand-written JSON payload. */
        val IcsOneLine = Ics.replace("\n", "\\n")

        val SeriesIcs = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:standup
DTSTART:20260907T070000Z
DTEND:20260907T071500Z
RRULE:FREQ=DAILY
SUMMARY:Standup
END:VEVENT
END:VCALENDAR
"""
    }
}
