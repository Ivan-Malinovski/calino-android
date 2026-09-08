package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.DavNs
import calino.malinov.ski.poc.data.caldav.DavXml
import calino.malinov.ski.poc.data.caldav.ICalMapper
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.occursOn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapping from real server output to the app's models.
 *
 * The zone is pinned to Europe/Copenhagen so the assertions describe what the
 * user in that zone sees; the expanded instances arrive in UTC.
 */
class ICalMapperTest {

    private val zone = ZoneId.of("Europe/Copenhagen")
    private val mapper = ICalMapper(zone)

    // --- the day-bucketing regression ----------------------------------------

    @Test
    fun `a late evening UTC instant keeps its local calendar day`() {
        // 21:00 UTC is 23:00 in Copenhagen on the *same* day. Reading the date
        // off the UTC value would push it to the next day.
        val local = mapper.toLocalDateTime(Instant.parse("2026-09-07T21:00:00Z"))
        assertEquals(LocalDate.of(2026, 9, 7), local.toLocalDate())
        assertEquals(LocalTime.of(23, 0), local.toLocalTime())
    }

    @Test
    fun `the weekday series stays on weekdays at 23 00 local`() {
        val events = events().filter { it.title == "Night check-in" }
        assertTrue("expected the expanded series", events.size >= 20)
        events.forEach { event ->
            val start = assertNotNull(event.start).let { event.start!! }
            assertEquals("Night check-in should be 23:00 local", LocalTime.of(23, 0), start.toLocalTime())
            val day = start.dayOfWeek
            assertTrue(
                "Night check-in landed on $day (${start.toLocalDate()}); the series is Mon-Fri",
                day.value <= 5,
            )
        }
    }

    @Test
    fun `each expanded occurrence gets its own id and lands on its own day`() {
        val series = events().filter { it.title == "Night check-in" }
        assertEquals("ids must be unique per occurrence", series.size, series.map { it.id }.toSet().size)
        assertEquals("all occurrences share one UID", 1, series.mapNotNull { it.uid }.toSet().size)
        // The first working week of September 2026: Mon 7th to Fri 11th.
        val week = (7..11).map { LocalDate.of(2026, 9, it) }
        week.forEach { day ->
            assertTrue("no occurrence on $day", series.any { it.occursOn(day) })
        }
        listOf(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13)).forEach { weekendDay ->
            assertFalse("occurrence on the weekend $weekendDay", series.any { it.occursOn(weekendDay) })
        }
    }

    // --- all-day handling -----------------------------------------------------

    @Test
    fun `a single all-day event has a date and no start time`() {
        val dayOff = events().single { it.title == "Day off" }
        assertTrue(dayOff.allDay)
        assertEquals(LocalDate.of(2026, 9, 7), dayOff.date)
        assertNull(dayOff.start)
        assertNull("a single day has no span end", dayOff.endDate)
    }

    @Test
    fun `an exclusive DTEND becomes an inclusive last day`() {
        // DTSTART 20260911, DTEND 20260914 -> the 11th, 12th and 13th.
        val berlin = events().single { it.title == "Berlin trip" }
        assertEquals(LocalDate.of(2026, 9, 11), berlin.date)
        assertEquals(LocalDate.of(2026, 9, 13), berlin.endDate)
        listOf(11, 12, 13).forEach { day ->
            assertTrue("Berlin trip should cover Sep $day", berlin.occursOn(LocalDate.of(2026, 9, day)))
        }
        assertFalse(
            "DTEND is exclusive; Sep 14 is not part of the trip",
            berlin.occursOn(LocalDate.of(2026, 9, 14)),
        )
        assertFalse(berlin.occursOn(LocalDate.of(2026, 9, 10)))
    }

    // --- tasks ----------------------------------------------------------------

    @Test
    fun `a task with a timed due date keeps its local time`() {
        val passport = tasks().single { it.title == "Renew passport" }
        // DUE;TZID=Europe/Copenhagen:20260828T170000
        assertEquals(LocalDate.of(2026, 8, 28), passport.due)
        assertEquals(LocalTime.of(17, 0), passport.dueTime)
        assertFalse(passport.done)
    }

    @Test
    fun `an all-day task has a due date and no due time`() {
        val plants = tasks().single { it.title == "Water the plants" }
        assertEquals(LocalDate.of(2026, 8, 22), plants.due)
        assertNull("an all-day task must not gain a time", plants.dueTime)
    }

    @Test
    fun `every task carries its server identity`() {
        tasks().forEach { task ->
            assertNotNull("task ${task.title} has no UID", task.uid)
            assertNotNull("task ${task.title} has no href", task.href)
        }
    }

    // --- journals -------------------------------------------------------------

    @Test
    fun `a journal entry maps to its date with summary and body`() {
        val entry = journals().single()
        assertEquals(LocalDate.of(2026, 8, 19), entry.date)
        assertEquals("Notes: recurrence rework", entry.title)
        assertTrue("body should carry the description", entry.body.startsWith("Journal entry."))
        // The ICS escapes the comma as \, ; it must arrive unescaped.
        assertTrue("escaped comma should be decoded", entry.body.contains("UTC instant, so BYDAY"))
    }

    // --- parser robustness ----------------------------------------------------

    @Test
    fun `a BOM and LF-only line endings parse the same as clean input`() {
        val clean = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:bom-check
            DTSTART;VALUE=DATE:20260907
            SUMMARY:Day off
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val fromLf = mapper.parse(clean, "cal", 0L, "href").events
        val fromCrlf = mapper.parse(clean.replace("\n", "\r\n"), "cal", 0L, "href").events
        val fromBom = mapper.parse("\uFEFF$clean", "cal", 0L, "href").events

        assertEquals(1, fromLf.size)
        assertEquals("CRLF input should parse identically", fromLf, fromCrlf)
        assertEquals("a leading BOM must not silently empty the calendar", fromLf, fromBom)
    }

    @Test
    fun `expanded events report no leftover recurrence rule`() {
        val parsed = parseAll(CalDavFixtures.Events)
        assertTrue("this fixture was fetched with expand", parsed.events.isNotEmpty())
        assertFalse(
            "the server expanded the series, so no RRULE should survive",
            parsed.sawUnexpandedRecurrence,
        )
    }

    @Test
    fun `an unexpanded series is reported rather than shown as one event`() {
        val master = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:not-expanded
            DTSTART:20260907T210000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR
            SUMMARY:Night check-in
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val parsed = mapper.parse(master, "cal", 0L, "href")
        assertTrue(
            "a surviving RRULE means the server ignored expand and must be surfaced",
            parsed.sawUnexpandedRecurrence,
        )
    }

    @Test
    fun `every mapped event carries its server identity and none carries a secret`() {
        events().forEach { event ->
            assertNotNull("event ${event.title} has no UID", event.uid)
            assertNotNull("event ${event.title} has no href", event.href)
        }
        val rendered = events().toString()
        assertFalse("no password may ever reach a model", rendered.contains("s3cret-not-a-real-password"))
    }

    // --- helpers --------------------------------------------------------------

    private fun events(): List<CalEvent> = parseAll(CalDavFixtures.Events).events
    private fun tasks(): List<CalTask> = parseAll(CalDavFixtures.Todos).tasks
    private fun journals(): List<JournalEntry> = parseAll(CalDavFixtures.Journals).journals

    /** Walks a captured multistatus and maps every calendar-data payload. */
    private fun parseAll(xml: String): ICalMapper.Parsed {
        val root = requireNotNull(DavXml.parse(xml)) { "fixture did not parse as XML" }
        var combined = ICalMapper.Parsed()
        DavXml.elements(root, DavNs.Dav, "response").forEach { entry ->
            val href = DavXml.text(entry, DavNs.Dav, "href") ?: return@forEach
            val data = DavXml.text(entry, DavNs.CalDav, "calendar-data") ?: return@forEach
            val parsed = mapper.parse(data, CalDavFixtures.CalendarUrl, 0xFF11A602, href)
            combined = ICalMapper.Parsed(
                events = combined.events + parsed.events,
                tasks = combined.tasks + parsed.tasks,
                journals = combined.journals + parsed.journals,
                sawUnexpandedRecurrence =
                    combined.sawUnexpandedRecurrence || parsed.sawUnexpandedRecurrence,
            )
        }
        return combined
    }
}

private fun <T> assertNotNull(value: T?): T {
    org.junit.Assert.assertNotNull(value)
    return value!!
}
