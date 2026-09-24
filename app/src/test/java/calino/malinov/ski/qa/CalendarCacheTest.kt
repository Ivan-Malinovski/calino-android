package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CachedCalendar
import calino.malinov.ski.data.caldav.CachedAddressBook
import calino.malinov.ski.data.caldav.CardResource
import calino.malinov.ski.data.caldav.CalendarCacheJson
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.FileCalendarCache
import calino.malinov.ski.data.caldav.ICalMapper
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
    private val addressBookUrl = "https://carddav.example.com/dav.php/addressbooks/test-user/contacts/"

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

    private fun addressBookEntry(
        url: String = addressBookUrl,
        resources: List<CardResource> = listOf(
            CardResource(
                href = "$url/ada.vcf",
                etag = "card-etag-1",
                vcf = VCard,
            ),
            CardResource(
                href = "$url/chen.vcf",
                etag = null,
                vcf = VCard.replace("Ada Lovelace", "Chen Wei"),
            ),
        ),
    ) = CachedAddressBook(
        addressBookUrl = url,
        fetchedAt = Instant.parse("2026-09-09T07:30:00Z"),
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
    fun `a saved address book reads back raw vcards unchanged`() {
        val cache = FileCalendarCache(root)
        cache.saveAddressBook(addressBookEntry())

        val loaded = cache.loadAddressBook(addressBookUrl)
        assertEquals(addressBookEntry(), loaded)
        assertEquals(VCard, loaded!!.resources.first().vcf)
        assertNull("a missing ETag must stay null", loaded.resources[1].etag)
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
    fun `a cached resource can be read back by href`() {
        val cache = FileCalendarCache(root)
        cache.save(entry())

        assertEquals(
            entry().resources.first(),
            cache.loadResource(url, "$url/one.ics"),
        )
        assertNull(cache.loadResource(url, "$url/missing.ics"))
    }

    @Test
    fun `saving a resource replaces it in place and leaves siblings alone`() {
        val cache = FileCalendarCache(root)
        cache.save(entry())
        val replacement = CalendarResource("$url/one.ics", "etag-new", Ics.replace("lunch", "breakfast"))

        cache.saveResource(url, replacement)

        val resources = cache.load(url)!!.resources
        assertEquals(2, resources.size)
        assertEquals(replacement, resources.first { it.href == replacement.href })
        assertEquals("$url/two.ics", resources.single { it.href.endsWith("two.ics") }.href)
    }

    @Test
    fun `deleting a resource removes only that href`() {
        val cache = FileCalendarCache(root)
        cache.save(entry())

        cache.deleteResource(url, "$url/one.ics")

        val resources = cache.load(url)!!.resources
        assertEquals(1, resources.size)
        assertEquals("$url/two.ics", resources.single().href)
    }

    @Test
    fun `saving a resource into an uncached collection does nothing`() {
        val cache = FileCalendarCache(root)
        val otherUrl = "https://caldav.example.com/dav.php/calendars/test-user/uncached/"

        cache.saveResource(
            otherUrl,
            CalendarResource("$otherUrl/new.ics", "etag", Ics),
        )

        assertNull(cache.load(otherUrl))
        assertNull(cache.loadResource(otherUrl, "$otherUrl/new.ics"))
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

    @Test
    fun `address book eviction drops disabled books and keeps the rest`() {
        val cache = FileCalendarCache(root)
        val other = "https://carddav.example.com/dav.php/addressbooks/test-user/work/"
        cache.saveAddressBook(addressBookEntry())
        cache.saveAddressBook(addressBookEntry(url = other))

        cache.evictAddressBooksExcept(setOf(addressBookUrl))

        assertEquals(addressBookUrl, cache.loadAddressBook(addressBookUrl)!!.addressBookUrl)
        assertNull("a disabled address book's content must leave the disk", cache.loadAddressBook(other))
    }

    @Test
    fun `calendar eviction does not remove enabled address book caches`() {
        val cache = FileCalendarCache(root)
        cache.save(entry())
        cache.saveAddressBook(addressBookEntry())

        cache.evictExcept(emptySet())

        assertNull(cache.load(url))
        assertEquals(addressBookEntry(), cache.loadAddressBook(addressBookUrl))
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
    fun `a corrupt address book file loads as nothing rather than throwing`() {
        val cache = FileCalendarCache(root)
        cache.saveAddressBook(addressBookEntry())
        root.listFiles()!!.single().writeText("this is not gzipped json")

        assertNull(cache.loadAddressBook(addressBookUrl))
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
        val raw = CalendarCacheJson.encode(entry()).replace("\"version\":${CalendarCacheJson.Version}", "\"version\":99")
        assertNull(CalendarCacheJson.decode(raw))
    }

    @Test
    fun `malformed json decodes to nothing`() {
        assertNull(CalendarCacheJson.decode("{not json"))
        assertNull(CalendarCacheJson.decode(""))
        assertNull(CalendarCacheJson.decode(null))
    }

    @Test
    fun `a resource missing its text invalidates the whole snapshot`() {
        val raw = """{"version":${CalendarCacheJson.Version},"calendarUrl":"$url",
            "fetchedAt":"2026-09-09T07:30:00Z","windowStart":"2026-03-01","windowEnd":"2027-03-01",
            "resources":[{"href":"$url/one.ics","etag":"e","ics":"$IcsOneLine"},
                         {"href":"$url/two.ics"}]}"""

        assertNull(CalendarCacheJson.decode(raw))
    }

    // --- the size guards ------------------------------------------------------

    @Test
    fun `an oversized resource rejects the complete cache write`() {
        val cache = FileCalendarCache(root)
        val huge = CalendarResource("$url/huge.ics", "e", "X".repeat(1_000_001))
        assertFalse(cache.saveComplete(entry(resources = listOf(CalendarResource("$url/one.ics", "e", Ics), huge))))
        assertNull(cache.load(url))
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

        val VCard = """BEGIN:VCARD
VERSION:3.0
UID:ada
FN:Ada Lovelace
EMAIL;TYPE=work:ada@example.com
END:VCARD
"""
    }
}
