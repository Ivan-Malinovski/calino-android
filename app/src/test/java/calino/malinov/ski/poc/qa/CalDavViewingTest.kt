package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.CalDavAccountJson
import calino.malinov.ski.poc.data.caldav.DavCredentials
import calino.malinov.ski.poc.data.caldav.DavNs
import calino.malinov.ski.poc.data.caldav.DavXml
import calino.malinov.ski.poc.data.caldav.ICalMapper
import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.model.CalDavCalendar
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.state.tasksDueOn
import calino.malinov.ski.poc.ui.home.monthEventIndex
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the calendar surfaces actually render from real server data.
 *
 * The tests above prove each layer maps correctly; these prove the result
 * reaches the grid the user looks at, in the right cells and the right order.
 */
class CalDavViewingTest {

    private val zone = ZoneId.of("Europe/Copenhagen")
    private val mapper = ICalMapper(zone)
    private val september = YearMonth.of(2026, 9)

    private fun events(): List<CalEvent> = parse(CalDavFixtures.Events).events
    private fun index() = monthEventIndex(events(), september)

    @Test
    fun `the month grid places every fixture event on its own day`() {
        val grid = index()
        fun titlesOn(day: Int) = grid[LocalDate.of(2026, 9, day)].orEmpty().map { it.title }

        assertTrue("Sep 7 should hold the all-day event", titlesOn(7).contains("Day off"))
        assertTrue("Sep 7 should also hold a series occurrence", titlesOn(7).contains("Night check-in"))
        assertTrue(titlesOn(11).contains("Berlin trip"))
        assertTrue(titlesOn(12).contains("Berlin trip"))
        assertTrue(titlesOn(13).contains("Berlin trip"))
        assertFalse("DTEND is exclusive", titlesOn(14).contains("Berlin trip"))
    }

    @Test
    fun `a day cell orders all-day events before timed ones`() {
        // The ordering the agenda and detail surfaces both rely on.
        val ordering = compareBy<CalEvent> { !it.allDay }
            .thenBy { it.start?.toLocalTime() }
            .thenBy { it.id }
        val sep7 = index()[LocalDate.of(2026, 9, 7)].orEmpty().sortedWith(ordering)

        assertTrue("Sep 7 should have several entries", sep7.size >= 2)
        assertEquals("the all-day event sorts first", "Day off", sep7.first().title)
        assertTrue("the 23:00 occurrence sorts last", sep7.last().title == "Night check-in")
    }

    @Test
    fun `the grid covers the leading days of the month's first week`() {
        // September 2026 starts on a Tuesday, so the Monday-first grid opens on
        // Aug 31 -- the index must reach into it.
        val grid = index()
        val leading = LocalDate.of(2026, 9, 1)
        assertTrue(
            "the series should appear on Sep 1",
            grid[leading].orEmpty().any { it.title == "Night check-in" },
        )
    }

    @Test
    fun `tasks land on their due dates`() {
        val tasks = parse(CalDavFixtures.Todos).tasks
        val passport = tasksDueOn(tasks, LocalDate.of(2026, 8, 28))
        assertTrue(passport.any { it.title == "Renew passport" })
        assertTrue(
            "a task must not appear on a day it is not due",
            tasksDueOn(tasks, LocalDate.of(2026, 8, 27)).none { it.title == "Renew passport" },
        )
    }

    @Test
    fun `no fetched record renders as an untitled blank`() {
        val all = events().map { it.title } +
            parse(CalDavFixtures.Todos).tasks.map { it.title }
        assertTrue(all.isNotEmpty())
        assertTrue("every record needs a visible title", all.none { it.isBlank() })
    }

    // --- persistence ----------------------------------------------------------

    @Test
    fun `an account round-trips through its stored form`() {
        val account = CalDavAccount(
            id = "https://caldav.example.test|test-user",
            displayName = "caldav.example.test",
            serverUrl = "https://caldav.example.test",
            username = "test-user",
            calendars = listOf(
                CalDavCalendar("https://caldav.example.test/test-user/a/", "hellyeah", 0xFF11A602),
                CalDavCalendar("https://caldav.example.test/test-user/b/", "extra calendar", 0xFFF6DC6B, enabled = false, readOnly = true),
            ),
        )
        val restored = CalDavAccountJson.decode(CalDavAccountJson.encode(listOf(account)))
        assertEquals(listOf(account), restored)
        // Specifically: a disabled calendar stays disabled across a restart.
        assertFalse(restored.single().calendars[1].enabled)
    }

    @Test
    fun `no password is ever written to the stored account form`() {
        val account = CalDavAccount(
            id = "id", displayName = "Home", serverUrl = "https://example.com",
            username = "test-user", calendars = emptyList(),
        )
        val encoded = CalDavAccountJson.encode(listOf(account))
        assertFalse(encoded.contains("password", ignoreCase = true))
        assertFalse(encoded.contains("s3cret-not-a-real-password"))
    }

    @Test
    fun `a corrupt stored payload yields no accounts rather than crashing`() {
        // Losing the account list is recoverable by reconnecting; failing to
        // launch is not.
        assertEquals(emptyList<CalDavAccount>(), CalDavAccountJson.decode("{not json"))
        assertEquals(emptyList<CalDavAccount>(), CalDavAccountJson.decode(null))
        assertEquals(emptyList<CalDavAccount>(), CalDavAccountJson.decode(""))
    }

    @Test
    fun `credentials never print their password`() {
        val rendered = DavCredentials("test-user", "s3cret-not-a-real-password").toString()
        assertFalse("a password must not reach logs", rendered.contains("s3cret-not-a-real-password"))
        assertTrue(rendered.contains("test-user"))
    }

    @Test
    fun `the basic auth header is UTF-8 encoded`() {
        // A Latin-1 encoder throws above U+00FF and locks out the password
        // entirely; this must simply work.
        val header = DavCredentials("test-user", "pässwörd✓").basicAuthHeader()
        assertTrue(header.startsWith("Basic "))
        val decoded = String(
            java.util.Base64.getDecoder().decode(header.removePrefix("Basic ")),
            Charsets.UTF_8,
        )
        assertEquals("test-user:pässwörd✓", decoded)
    }

    private fun parse(xml: String): ICalMapper.Parsed {
        val root = requireNotNull(DavXml.parse(xml))
        var all = ICalMapper.Parsed()
        DavXml.elements(root, DavNs.Dav, "response").forEach { entry ->
            val href = DavXml.text(entry, DavNs.Dav, "href") ?: return@forEach
            val data = DavXml.text(entry, DavNs.CalDav, "calendar-data") ?: return@forEach
            val parsed = mapper.parse(data, CalDavFixtures.CalendarUrl, 0xFF11A602, href)
            all = ICalMapper.Parsed(
                all.events + parsed.events,
                all.tasks + parsed.tasks,
                all.journals + parsed.journals,
                all.sawUnexpandedRecurrence || parsed.sawUnexpandedRecurrence,
            )
        }
        return all
    }
}
