package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.DavNs
import calino.malinov.ski.poc.data.caldav.DavXml
import calino.malinov.ski.poc.data.caldav.ICalMapper
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.occursOn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

    // --- client-side recurrence expansion -------------------------------------

    @Test
    fun `an infinite weekday series expands across the window and skips weekends`() {
        val events = parseAll(CalDavFixtures.Recurring, WindowStart, WindowEnd).events
        assertTrue("the series should expand to many occurrences", events.size > 40)
        events.forEach { event ->
            val day = event.start!!.toLocalDate()
            assertFalse(
                "$day is a weekend and BYDAY=MO,TU,WE,TH,FR excludes it",
                day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY,
            )
            assertTrue("$day falls outside the requested window", !day.isBefore(WindowStart) && !day.isAfter(WindowEnd))
        }
        assertEquals("a daily series must not place two occurrences on one day", events.size, days(events).size)
    }

    @Test
    fun `an EXDATE stamped at the wrong time of day still cancels its day`() {
        // The fixture's DTSTART is 06:00Z but these two EXDATEs are 00:00Z, so
        // nothing matches them exactly. They are the owner's cancelled days and
        // must not reappear as work days.
        val days = days(parseAll(CalDavFixtures.Recurring, WindowStart, WindowEnd).events)
        assertFalse("EXDATE:20260525T000000Z", LocalDate.of(2026, 5, 25) in days)
        assertFalse("EXDATE:20260605T000000Z", LocalDate.of(2026, 6, 5) in days)
    }

    @Test
    fun `an EXDATE matching an occurrence exactly cancels it`() {
        val days = days(parseAll(CalDavFixtures.Recurring, WindowStart, WindowEnd).events)
        listOf(
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 21),
            LocalDate.of(2026, 7, 27),
            LocalDate.of(2026, 7, 28),
        ).forEach { assertFalse("EXDATE at 06:00Z should cancel $it", it in days) }
    }

    @Test
    fun `every occurrence shares the series uid and has its own id`() {
        val events = parseAll(CalDavFixtures.Recurring, WindowStart, WindowEnd).events
        assertEquals("the series is one record on the server", 1, events.mapNotNull { it.uid }.distinct().size)
        assertEquals("each occurrence must be addressable", events.size, events.map { it.id }.distinct().size)
        // Occurrence ids are derived from the instant, so a refetch reproduces
        // them rather than inventing new ones.
        assertEquals(events.map { it.id }, parseAll(CalDavFixtures.Recurring, WindowStart, WindowEnd).events.map { it.id })
    }

    @Test
    fun `each occurrence keeps the series title duration and categories`() {
        val events = parseAll(CalDavFixtures.Recurring, WindowStart, WindowEnd).events
        events.forEach { event ->
            assertEquals("Work", event.title)
            assertEquals("DTSTART 06:00Z to DTEND 14:00Z", 480, event.durationMinutes)
            assertEquals(listOf("Work"), event.categories)
        }
    }

    @Test
    fun `COUNT INTERVAL and UNTIL all bound the series`() {
        assertEquals(3, expand("RRULE:FREQ=DAILY;COUNT=3").size)
        val fortnightly = expand("RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=3").map { it.start!!.toLocalDate() }
        assertEquals(
            listOf(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)),
            fortnightly,
        )
        assertEquals(4, expand("RRULE:FREQ=DAILY;UNTIL=20260521T060000Z").size)
    }

    @Test
    fun `an RDATE adds an occurrence outside the rule`() {
        val days = days(expand("RRULE:FREQ=DAILY;COUNT=2", "RDATE:20260610T060000Z"))
        assertTrue("the RDATE occurrence should be present", LocalDate.of(2026, 6, 10) in days)
        assertEquals(3, days.size)
    }

    @Test
    fun `an override replaces the occurrence it names and moves its time`() {
        val events = expandWithOverride(
            "RECURRENCE-ID:20260519T060000Z",
            "DTSTART:20260519T100000Z",
            "DTEND:20260519T110000Z",
            "SUMMARY:Work (late start)",
        )
        val may19 = events.filter { it.start!!.toLocalDate() == LocalDate.of(2026, 5, 19) }
        assertEquals("the master occurrence must be replaced, not duplicated", 1, may19.size)
        assertEquals("Work (late start)", may19.single().title)
        assertEquals(LocalTime.of(12, 0), may19.single().start!!.toLocalTime())
    }

    @Test
    fun `an override wins over an EXDATE naming the same instant`() {
        // RFC 5545 3.8.5.1: moving an occurrence and cancelling it are
        // different acts, and the override is the later statement of intent.
        val events = expandWithOverride(
            "RECURRENCE-ID:20260519T060000Z",
            "DTSTART:20260519T100000Z",
            "DTEND:20260519T110000Z",
            "SUMMARY:Work (moved, not cancelled)",
            exdate = "EXDATE:20260519T060000Z",
        )
        val may19 = events.filter { it.start!!.toLocalDate() == LocalDate.of(2026, 5, 19) }
        assertEquals("Work (moved, not cancelled)", may19.single().title)
    }

    @Test
    fun `an all-day series keeps its span on every occurrence`() {
        val events = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:all-day-series
            DTSTART;VALUE=DATE:20260518
            DTEND;VALUE=DATE:20260521
            RRULE:FREQ=WEEKLY;COUNT=3
            SUMMARY:Conference
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            "cal", 0L, "href", null, WindowStart, WindowEnd,
        ).events
        assertEquals(3, events.size)
        events.forEach { event ->
            assertTrue(event.allDay)
            // DTEND is exclusive, so a 18th->21st span ends on the 20th.
            assertEquals(2L, ChronoUnit.DAYS.between(event.date!!, event.endDate!!))
        }
        assertEquals(
            listOf(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1)),
            events.map { it.date },
        )
    }

    @Test
    fun `a non-recurring event keeps its uid as its id`() {
        val event = expand(rule = null).single()
        assertEquals("nothing addresses an occurrence of a single event", event.uid, event.id)
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

    private fun days(events: List<CalEvent>): Set<LocalDate> =
        events.mapNotNull { it.start?.toLocalDate() ?: it.date }.toSet()

    /** One timed series anchored on 18 May 2026 06:00Z, plus any extra lines. */
    private fun expand(rule: String? = "RRULE:FREQ=DAILY;COUNT=3", vararg extra: String): List<CalEvent> =
        mapper.parse(
            listOfNotNull(
                "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT",
                "UID:series", "DTSTART:20260518T060000Z", "DTEND:20260518T070000Z",
                "SUMMARY:Work", rule, *extra, "END:VEVENT", "END:VCALENDAR",
            ).joinToString("\n"),
            "cal", 0L, "href", null, WindowStart, WindowEnd,
        ).events.sortedBy { it.start }

    /** The same series with a detached instance appended to the same resource. */
    private fun expandWithOverride(vararg overrideLines: String, exdate: String? = null): List<CalEvent> =
        mapper.parse(
            (
                listOfNotNull(
                    "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT",
                    "UID:series", "DTSTART:20260518T060000Z", "DTEND:20260518T070000Z",
                    "SUMMARY:Work", "RRULE:FREQ=DAILY;COUNT=3", exdate, "END:VEVENT",
                    "BEGIN:VEVENT", "UID:series",
                ) + overrideLines.toList() + listOf("END:VEVENT", "END:VCALENDAR")
                ).joinToString("\n"),
            "cal", 0L, "href", null, WindowStart, WindowEnd,
        ).events.sortedBy { it.start }

    /** Walks a captured multistatus and maps every calendar-data payload. */
    private fun parseAll(
        xml: String,
        windowStart: LocalDate = LocalDate.MIN,
        windowEnd: LocalDate = LocalDate.MAX,
    ): ICalMapper.Parsed {
        val root = requireNotNull(DavXml.parse(xml)) { "fixture did not parse as XML" }
        var combined = ICalMapper.Parsed()
        DavXml.elements(root, DavNs.Dav, "response").forEach { entry ->
            val href = DavXml.text(entry, DavNs.Dav, "href") ?: return@forEach
            val data = DavXml.text(entry, DavNs.CalDav, "calendar-data") ?: return@forEach
            val parsed = mapper.parse(
                data, CalDavFixtures.CalendarUrl, 0xFF11A602, href, null, windowStart, windowEnd,
            )
            combined = ICalMapper.Parsed(
                events = combined.events + parsed.events,
                tasks = combined.tasks + parsed.tasks,
                journals = combined.journals + parsed.journals,
            )
        }
        return combined.copy(events = combined.events.sortedBy { it.start ?: it.date?.atStartOfDay() })
    }

    private companion object {
        val WindowStart: LocalDate = LocalDate.of(2026, 5, 1)
        val WindowEnd: LocalDate = LocalDate.of(2026, 10, 31)
    }
}

private fun <T> assertNotNull(value: T?): T {
    org.junit.Assert.assertNotNull(value)
    return value!!
}
