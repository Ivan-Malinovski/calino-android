package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.DavNs
import calino.malinov.ski.data.caldav.DavXml
import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.model.occursOn
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
import org.junit.Assert.assertThrows
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

    @Test
    fun `feed parser keeps a valid UID when another series cannot expand`() {
        val feed = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:broken-series
            DTSTART:20260928T090000Z
            DTEND:20260928T100000Z
            RRULE:FREQ=WEEKLY
            SUMMARY:Broken
            END:VEVENT
            BEGIN:VEVENT
            UID:valid-event
            DTSTART:20260928T120000Z
            DTEND:20260928T130000Z
            SUMMARY:Valid
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        // The extreme upper bound makes recurrence expansion overflow, while
        // the independent event remains mappable.
        val events = mapper.parseFeedEvents(
            feed, "feed", 1L, LocalDate.of(2026, 9, 1), LocalDate.MAX,
        )
        assertEquals(listOf("valid-event"), events.map { it.uid })
        assertThrows(IllegalArgumentException::class.java) {
            mapper.parseFeedEvents(
                feed.substringBefore("BEGIN:VEVENT\nUID:valid-event") + "END:VCALENDAR",
                "feed", 1L, LocalDate.of(2026, 9, 1), LocalDate.MAX,
            )
        }
    }

    @Test
    fun `feed parser distinguishes an empty calendar from unusable events`() {
        val empty = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR"
        assertTrue(mapper.parseFeedEvents(
            empty, "feed", 1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
        ).isEmpty())

        val unusable = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:missing-start
            SUMMARY:Cannot map
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            mapper.parseFeedEvents(
                unusable, "feed", 1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
            )
        }
    }

    @Test
    fun `Exchange Windows VTIMEZONE expands recurring events and tasks`() {
        val result = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:Microsoft Exchange Server 2010
            BEGIN:VTIMEZONE
            TZID:South Africa Standard Time
            BEGIN:STANDARD
            DTSTART:16010101T000000
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0200
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:exchange-event
            DTSTART;TZID=South Africa Standard Time:20260928T090000
            DTEND;TZID=South Africa Standard Time:20260928T100000
            RRULE:FREQ=WEEKLY;COUNT=4
            SUMMARY:Event
            END:VEVENT
            BEGIN:VTODO
            UID:exchange-task
            DTSTART;TZID=South Africa Standard Time:20260928T090000
            DUE;TZID=South Africa Standard Time:20260928T100000
            RRULE:FREQ=WEEKLY;COUNT=4
            SUMMARY:Task
            END:VTODO
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "exchange.ics",
            windowStart = LocalDate.of(2026, 9, 1), windowEnd = LocalDate.of(2026, 11, 1),
        )

        assertEquals(4, result.events.size)
        assertEquals(4, result.tasks.size)
        assertEquals(LocalDateTime.of(2026, 9, 28, 9, 0), result.events.first().start)
    }

    @Test
    fun `Windows VTIMEZONE recurrence follows daylight transition`() {
        val events = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Pacific Standard Time
            BEGIN:DAYLIGHT
            DTSTART:20260308T020000
            TZOFFSETFROM:-0800
            TZOFFSETTO:-0700
            RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU
            END:DAYLIGHT
            BEGIN:STANDARD
            DTSTART:20261101T020000
            TZOFFSETFROM:-0700
            TZOFFSETTO:-0800
            RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:pacific
            DTSTART;TZID=Pacific Standard Time:20260301T090000
            DTEND;TZID=Pacific Standard Time:20260301T100000
            RRULE:FREQ=WEEKLY;COUNT=3
            SUMMARY:Meeting
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "pacific.ics",
            windowStart = LocalDate.of(2026, 3, 1), windowEnd = LocalDate.of(2026, 3, 20),
        ).events.sortedBy { it.start }

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 3, 1, 18, 0),
                LocalDateTime.of(2026, 3, 8, 17, 0),
                LocalDateTime.of(2026, 3, 15, 17, 0),
            ),
            events.map { it.start },
        )
    }

    @Test
    fun `timed task in a custom zone is found on its displayed day`() {
        val tasks = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Custom Work Zone
            BEGIN:STANDARD
            DTSTART:16010101T000000
            TZOFFSETFROM:+1400
            TZOFFSETTO:+1400
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VTODO
            UID:custom-task
            DTSTART;TZID=Custom Work Zone:20260928T003000
            DUE;TZID=Custom Work Zone:20260928T013000
            RRULE:FREQ=WEEKLY;COUNT=2
            SUMMARY:Deadline
            END:VTODO
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "custom.ics",
            windowStart = LocalDate.of(2026, 9, 27), windowEnd = LocalDate.of(2026, 9, 27),
        ).tasks

        assertEquals(1, tasks.size)
        assertEquals(LocalDate.of(2026, 9, 27), tasks.single().due)
        assertEquals(LocalDate.of(2026, 9, 27), tasks.single().startDate)
    }

    @Test
    fun `recurring VTODO expands and detached completion replaces its occurrence`() {
        val tasks = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:gym
            DTSTART;VALUE=DATE:20260303
            DUE;VALUE=DATE:20260303
            SUMMARY:Exercise
            RRULE:FREQ=WEEKLY;BYDAY=TU
            STATUS:NEEDS-ACTION
            PERCENT-COMPLETE:25
            PRIORITY:2
            END:VTODO
            BEGIN:VTODO
            UID:gym
            DTSTART;VALUE=DATE:20260310
            DUE;VALUE=DATE:20260310
            RECURRENCE-ID;VALUE=DATE:20260310
            SUMMARY:Exercise
            STATUS:COMPLETED
            PERCENT-COMPLETE:100
            COMPLETED:20260310T180400Z
            END:VTODO
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "gym.ics",
            windowStart = LocalDate.of(2026, 3, 1), windowEnd = LocalDate.of(2026, 3, 18),
        ).tasks.sortedBy { it.due }

        assertEquals(listOf(3, 10, 17), tasks.map { it.due!!.dayOfMonth })
        assertEquals(listOf(false, true, false), tasks.map { it.done })
        assertEquals(25, tasks.first().percentComplete)
        assertEquals(2, tasks.first().priority)
        assertEquals(LocalDate.of(2026, 3, 10), tasks[1].recurrenceDate)
        assertEquals("NEEDS-ACTION", tasks.first().status)
        assertEquals("COMPLETED", tasks[1].status)
        assertEquals(Instant.parse("2026-03-10T18:04:00Z"), tasks[1].completedAt)
        assertEquals(1, tasks.count { it.due == LocalDate.of(2026, 3, 10) })
    }

    @Test
    fun `detached VTODO occurrences have stable distinct ids`() {
        val tasks = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:gym
            DTSTART;VALUE=DATE:20260303
            DUE;VALUE=DATE:20260303
            RRULE:FREQ=WEEKLY
            SUMMARY:Exercise
            END:VTODO
            BEGIN:VTODO
            UID:gym
            RECURRENCE-ID;VALUE=DATE:20260310
            DTSTART;VALUE=DATE:20260310
            DUE;VALUE=DATE:20260310
            SUMMARY:Exercise
            STATUS:COMPLETED
            END:VTODO
            BEGIN:VTODO
            UID:gym
            RECURRENCE-ID;VALUE=DATE:20260317
            DTSTART;VALUE=DATE:20260317
            DUE;VALUE=DATE:20260317
            SUMMARY:Exercise
            STATUS:COMPLETED
            END:VTODO
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "gym.ics",
            windowStart = LocalDate.of(2026, 3, 1), windowEnd = LocalDate.of(2026, 3, 24),
        ).tasks

        assertEquals(tasks.size, tasks.map { it.id }.toSet().size)
        assertTrue(tasks.any { it.id == "gym@2026-03-10" })
        assertTrue(tasks.any { it.id == "gym@2026-03-17" })
    }

    @Test
    fun `timed recurring VTODO preserves DTSTART to DUE offset`() {
        val tasks = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:shifted
            DTSTART:20260303T090000Z
            DUE:20260303T170000Z
            RRULE:FREQ=WEEKLY
            SUMMARY:Shifted deadline
            END:VTODO
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "shifted.ics",
            windowStart = LocalDate.of(2026, 3, 9), windowEnd = LocalDate.of(2026, 3, 11),
        ).tasks.single()

        assertEquals(LocalDate.of(2026, 3, 10), tasks.startDate)
        assertEquals(LocalTime.of(10, 0), tasks.startTime)
        assertEquals(LocalDate.of(2026, 3, 10), tasks.due)
        assertEquals(LocalTime.of(18, 0), tasks.dueTime)
    }

    @Test
    fun `date-only DTSTART does not hide a timed DUE`() {
        val task = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:server-deadline
            DTSTART;VALUE=DATE:20260924
            DUE:20260924T150000Z
            SUMMARY:Server deadline
            END:VTODO
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "deadline.ics",
        ).tasks.single()

        assertEquals(LocalTime.of(17, 0), task.dueTime)
        assertEquals(LocalDate.of(2026, 9, 24), task.startDate)
        assertNull(task.startTime)
    }

    @Test
    fun `a recurring VTODO with only DUE still expands`() {
        // tasks.org and other task clients omit DTSTART entirely. Issue #3.
        val tasks = ICalMapper(ZoneId.of("America/Chicago")).parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:towels
            DTSTAMP:20260918T203424Z
            DUE;TZID=America/Chicago:20260922T153001
            RRULE:FREQ=WEEKLY;BYDAY=TU,FR
            PRIORITY:9
            STATUS:NEEDS-ACTION
            SUMMARY:Towels
            END:VTODO
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "towels.ics",
            windowStart = LocalDate.of(2026, 9, 20), windowEnd = LocalDate.of(2026, 9, 30),
        ).tasks

        assertEquals(
            listOf(22, 25, 29).map { LocalDate.of(2026, 9, it) },
            tasks.map { it.due },
        )
        assertEquals(LocalTime.of(15, 30, 1), tasks.first().dueTime)
        assertTrue(tasks.all { it.startDate == null })
    }

    @Test
    fun `a yearly DUE-only VTODO expands across the window`() {
        val tasks = ICalMapper(ZoneId.of("America/Chicago")).parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:solar
            DUE;TZID=America/Chicago:20260921T130001
            RRULE:FREQ=YEARLY;INTERVAL=1
            STATUS:NEEDS-ACTION
            SUMMARY:Reset Solar
            END:VTODO
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "solar.ics",
            windowStart = LocalDate.of(2026, 1, 1), windowEnd = LocalDate.of(2028, 12, 31),
        ).tasks

        assertEquals(
            listOf(LocalDate.of(2026, 9, 21), LocalDate.of(2027, 9, 21), LocalDate.of(2028, 9, 21)),
            tasks.map { it.due },
        )
    }

    @Test
    fun `undated VTODO remains visible`() {
        val task = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:inbox
            SUMMARY:Someday
            STATUS:NEEDS-ACTION
            END:VTODO
            END:VCALENDAR
            """.trimIndent(), "cal", 1L, "inbox.ics",
        ).tasks.single()

        assertNull(task.due)
        assertNull(task.dueTime)
    }

    @Test
    fun `Apple travel duration maps to whole travel minutes`() {
        val parsed = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:travel-1
            DTSTART:20260907T080000Z
            DTEND:20260907T090000Z
            SUMMARY:Appointment
            X-APPLE-TRAVEL-DURATION:PT1H15M
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            "cal",
            1L,
            "travel.ics",
        ).events.single()

        assertEquals(75, parsed.travelTimeMinutes)
    }

    @Test
    fun `invalid and non-positive Apple travel durations stay unset`() {
        listOf("not-a-duration", "PT0M", "-PT15M").forEach { value ->
            val event = mapper.parse(
                "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:$value\n" +
                    "DTSTART:20260907T080000Z\nX-APPLE-TRAVEL-DURATION:$value\n" +
                    "END:VEVENT\nEND:VCALENDAR",
                "cal",
                1L,
                "$value.ics",
            ).events.single()
            assertNull(value, event.travelTimeMinutes)
        }
    }

    @Test
    fun `a CONFERENCE URI becomes the event's conference link, ahead of URL`() {
        val event = mapper.parse(
            "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:conf-1\n" +
                "DTSTART:20260907T080000Z\nSUMMARY:Standup\n" +
                "URL:https://meet.google.com/aaa-bbbb-ccc\n" +
                "CONFERENCE;VALUE=URI;FEATURE=VIDEO:https://video.example.org/room/42\n" +
                "END:VEVENT\nEND:VCALENDAR",
            "cal", 1L, "conf-1.ics",
        ).events.single()

        assertEquals("https://video.example.org/room/42", event.conferenceUrl)
    }

    @Test
    fun `a URL counts as a conference link only for a known meeting service`() {
        fun parse(url: String) = mapper.parse(
            "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:u\n" +
                "DTSTART:20260907T080000Z\nURL:$url\nEND:VEVENT\nEND:VCALENDAR",
            "cal", 1L, "u.ics",
        ).events.single().conferenceUrl

        assertEquals("https://example.zoom.us/j/123456", parse("https://example.zoom.us/j/123456"))
        assertNull(parse("https://example.org/agenda"))
    }

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
        // This fixture is now correctly expanded as a weekly VTODO, so title
        // identifies the series rather than one occurrence. Select the anchor
        // occurrence by stable UID plus recurrence identity; `single` still
        // detects an accidental duplicate of that concrete occurrence.
        val anchor = LocalDate.of(2026, 8, 22)
        val plants = tasks().single {
            it.uid == "fixture-todo-plants" && it.recurrenceDate == anchor
        }
        assertEquals(anchor, plants.due)
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
    fun `all-day DURATION supplies the exclusive event end`() {
        val oneOff = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:all-day-duration
            DTSTART;VALUE=DATE:20260518
            DURATION:P3D
            SUMMARY:Conference
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            "cal", 0L, "href", null,
        ).events.single()
        assertEquals(LocalDate.of(2026, 5, 20), oneOff.endDate)

        val series = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:all-day-duration-series
            DTSTART;VALUE=DATE:20260518
            DURATION:P3D
            RRULE:FREQ=WEEKLY;COUNT=2
            SUMMARY:Conference
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            "cal", 0L, "href", null,
            LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 1),
        ).events
        assertEquals(2, series.size)
        assertTrue(series.all { ChronoUnit.DAYS.between(it.date, it.endDate) == 2L })
    }

    @Test
    fun `cancelled masters and detached occurrences are not displayed`() {
        val events = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:cancelled-master
            DTSTART:20260518T060000Z
            DTEND:20260518T070000Z
            STATUS:CANCELLED
            SUMMARY:Cancelled series
            RRULE:FREQ=DAILY;COUNT=2
            END:VEVENT
            BEGIN:VEVENT
            UID:cancelled-occurrence
            DTSTART:20260518T060000Z
            DTEND:20260518T070000Z
            SUMMARY:Active series
            RRULE:FREQ=DAILY;COUNT=3
            END:VEVENT
            BEGIN:VEVENT
            UID:cancelled-occurrence
            RECURRENCE-ID:20260519T060000Z
            DTSTART:20260519T060000Z
            DTEND:20260519T070000Z
            STATUS:CANCELLED
            SUMMARY:Cancelled occurrence
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            "cal", 0L, "href", null,
            LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 20),
        ).events

        assertEquals(listOf("2026-05-18", "2026-05-20"), events.map { it.start!!.toLocalDate().toString() }.sorted())
        assertTrue(events.all { it.title == "Active series" })
    }

    @Test
    fun `THISANDFUTURE moves later instances and keeps the shifted duration through DST`() {
        val events = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:range-dst
            DTSTART;TZID=Europe/Copenhagen:20260315T090000
            DTEND;TZID=Europe/Copenhagen:20260315T100000
            RRULE:FREQ=WEEKLY;COUNT=4
            SUMMARY:Weekly
            END:VEVENT
            BEGIN:VEVENT
            UID:range-dst
            RECURRENCE-ID;TZID=Europe/Copenhagen;RANGE=THISANDFUTURE:20260322T090000
            DTSTART;TZID=Europe/Copenhagen:20260322T110000
            DTEND;TZID=Europe/Copenhagen:20260322T123000
            SUMMARY:Weekly moved
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            "cal", 0L, "href", null,
            LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 5),
        ).events.sortedBy { it.start }

        assertEquals(4, events.size)
        assertEquals(listOf(9, 11, 11, 11), events.map { it.start!!.hour })
        assertEquals(listOf(60, 90, 90, 90), events.map { it.durationMinutes })
        assertEquals(
            listOf(15, 22, 29, 5),
            events.map { it.start!!.dayOfMonth },
        )
    }

    @Test
    fun `a cancelled THISANDFUTURE override suppresses its occurrence and later ones`() {
        val events = mapper.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:range-cancel
            DTSTART:20260518T060000Z
            DTEND:20260518T070000Z
            RRULE:FREQ=WEEKLY;COUNT=4
            SUMMARY:Weekly
            END:VEVENT
            BEGIN:VEVENT
            UID:range-cancel
            RECURRENCE-ID;RANGE=THISANDFUTURE:20260525T060000Z
            DTSTART:20260525T060000Z
            STATUS:CANCELLED
            SUMMARY:Cancelled from here
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            "cal", 0L, "href", null,
            LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8),
        ).events

        assertEquals(1, events.size)
        assertEquals(LocalDate.of(2026, 5, 18), events.single().start!!.toLocalDate())
    }

    @Test
    fun `recurring TZID instances are selected by dates in the display zone`() {
        val events = ICalMapper(ZoneId.of("Europe/Copenhagen")).parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:la-evening
            DTSTART;TZID=America/Los_Angeles:20260601T170000
            DTEND;TZID=America/Los_Angeles:20260601T180000
            RRULE:FREQ=DAILY;COUNT=3
            SUMMARY:Evening
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            "cal", 0L, "href", null,
            LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 2),
        ).events

        assertEquals(1, events.size)
        assertEquals(LocalDate.of(2026, 6, 2), events.single().start!!.toLocalDate())
        assertEquals(LocalTime.of(2, 0), events.single().start!!.toLocalTime())
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


    // --- VALARM ---------------------------------------------------------------

    /** One timed event carrying whatever alarm lines the case is about. */
    private fun withAlarm(vararg alarmLines: String): CalEvent =
        mapper.parse(
            (
                listOf(
                    "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT",
                    "UID:alarmed", "DTSTART:20260518T060000Z", "DTEND:20260518T070000Z",
                    "SUMMARY:Work",
                ) + alarmLines.toList() + listOf("END:VEVENT", "END:VCALENDAR")
                ).joinToString("\n"),
            "cal", 0L, "href", null,
        ).events.single()

    @Test
    fun `a relative display alarm becomes a reminder`() {
        val event = withAlarm("BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT15M", "DESCRIPTION:soon", "END:VALARM")
        assertEquals(listOf(Reminder(15)), event.reminders)
    }

    @Test
    fun `an hour-long trigger is read in minutes`() {
        val event = withAlarm("BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT1H", "DESCRIPTION:soon", "END:VALARM")
        assertEquals(listOf(Reminder(60)), event.reminders)
    }

    @Test
    fun `an audio alarm is Calino's too`() {
        val event = withAlarm("BEGIN:VALARM", "ACTION:AUDIO", "TRIGGER:-PT5M", "END:VALARM")
        assertEquals(listOf(Reminder(5)), event.reminders)
    }

    @Test
    fun `several alarms are read longest lead first`() {
        val event = withAlarm(
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT10M", "DESCRIPTION:a", "END:VALARM",
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT1H", "DESCRIPTION:b", "END:VALARM",
        )
        assertEquals(listOf(Reminder(60), Reminder(10)), event.reminders)
    }

    // The following unsupported alarms stay on the resource instead of being
    // presented as a different lead-time reminder in the editor.

    @Test
    fun `an absolute trigger is not a reminder`() {
        val event = withAlarm(
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER;VALUE=DATE-TIME:20260518T050000Z",
            "DESCRIPTION:soon", "END:VALARM",
        )
        assertTrue(event.reminders.isEmpty())
    }

    @Test
    fun `a trigger anchored to the end is not a reminder`() {
        val event = withAlarm(
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER;RELATED=END:-PT15M",
            "DESCRIPTION:soon", "END:VALARM",
        )
        assertTrue(event.reminders.isEmpty())
    }

    @Test
    fun `an email alarm is not a reminder`() {
        val event = withAlarm(
            "BEGIN:VALARM", "ACTION:EMAIL", "TRIGGER:-PT15M", "DESCRIPTION:body",
            "SUMMARY:subject", "ATTENDEE:mailto:ada@example.com", "END:VALARM",
        )
        assertTrue(event.reminders.isEmpty())
    }

    @Test
    fun `a whole-minute repeating alarm is a reminder`() {
        val event = withAlarm(
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT15M", "DESCRIPTION:soon",
            "REPEAT:3", "DURATION:PT5M", "END:VALARM",
        )
        assertEquals(listOf(Reminder(15, repeatCount = 3, repeatIntervalMinutes = 5)), event.reminders)
    }

    @Test
    fun `an alarm after the start is not a reminder`() {
        val event = withAlarm("BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:PT15M", "DESCRIPTION:late", "END:VALARM")
        assertTrue(event.reminders.isEmpty())
    }

    @Test
    fun `a foreign alarm does not hide a reminder beside it`() {
        val event = withAlarm(
            "BEGIN:VALARM", "ACTION:EMAIL", "TRIGGER:-PT30M", "DESCRIPTION:body",
            "SUMMARY:subject", "ATTENDEE:mailto:ada@example.com", "END:VALARM",
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT10M", "DESCRIPTION:a", "END:VALARM",
        )
        assertEquals(listOf(Reminder(10)), event.reminders)
    }

    @Test
    fun `a START task alarm without DTSTART stays foreign`() {
        val task = mapper.parse(
            listOf(
                "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
                "UID:task-alarmed", "DUE:20260518T060000Z", "SUMMARY:File taxes",
                "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT1H", "DESCRIPTION:soon", "END:VALARM",
                "END:VTODO", "END:VCALENDAR",
            ).joinToString("\n"),
            "cal", 0L, "href", null,
        ).tasks.single()

        assertNull(task.reminder)
    }

    @Test
    fun `a task START alarm keeps its distinct DTSTART anchor`() {
        val task = mapper.parse(
            listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
                "UID:start-alarm", "DTSTART:20260518T060000Z", "DUE:20260518T140000Z",
                "SUMMARY:Work", "BEGIN:VALARM", "ACTION:DISPLAY",
                "TRIGGER;RELATED=START:-PT1H", "DESCRIPTION:soon", "END:VALARM",
                "END:VTODO", "END:VCALENDAR").joinToString("\n"),
            "cal", 0L, "href", null,
        ).tasks.single()
        assertEquals(Reminder(60, relativeToStart = true), task.reminder)
    }

    @Test
    fun `a task alarm before DUE becomes the task reminder`() {
        val task = mapper.parse(
            listOf(
                "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
                "UID:due-alarm", "DTSTART:20260518T060000Z", "DUE:20260518T140000Z",
                "SUMMARY:File taxes", "BEGIN:VALARM", "ACTION:DISPLAY",
                "TRIGGER;RELATED=END:-PT1H", "DESCRIPTION:soon", "END:VALARM",
                "END:VTODO", "END:VCALENDAR",
            ).joinToString("\n"), "cal", 0L, "href", null,
        ).tasks.single()
        assertEquals(Reminder(60), task.reminder)
    }

    @Test
    fun `a Nextcloud repeating task alarm keeps its second firing`() {
        val task = mapper.parse(
            listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
                "UID:nextcloud-alarm", "DUE:20260518T140000Z", "SUMMARY:File taxes",
                "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER;RELATED=END:-PT1H",
                "DESCRIPTION:Reminder", "REPEAT:1", "DURATION:PT10M", "END:VALARM",
                "END:VTODO", "END:VCALENDAR").joinToString("\n"),
            "cal", 0L, "href", null,
        ).tasks.single()
        assertEquals(Reminder(60, repeatCount = 1, repeatIntervalMinutes = 10), task.reminder)
    }

    @Test
    fun `Nextcloud sibling link is not mistaken for a subtask parent`() {
        val task = mapper.parse(
            listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
                "UID:child", "SUMMARY:Child", "RELATED-TO;RELTYPE=SIBLING:other",
                "RELATED-TO;RELTYPE=PARENT:actual-parent", "END:VTODO", "END:VCALENDAR")
                .joinToString("\n"), "cal", 0L, "href", null,
        ).tasks.single()
        assertEquals("actual-parent", task.parentTaskId)
    }

    @Test
    fun `an escaped comma stays inside one task category`() {
        val task = mapper.parse(
            listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
                "UID:tags", "SUMMARY:Tagged", "CATEGORIES:Smith\\, Jr,Work",
                "END:VTODO", "END:VCALENDAR").joinToString("\n"),
            "cal", 0L, "href", null,
        ).tasks.single()
        assertEquals("Smith, Jr", task.category)
    }

    @Test
    fun `completion timestamp alone marks a Nextcloud task done`() {
        val task = mapper.parse(
            listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
                "UID:finished", "SUMMARY:Finished", "COMPLETED:20260924T120000Z",
                "END:VTODO", "END:VCALENDAR").joinToString("\n"),
            "cal", 0L, "href", null,
        ).tasks.single()
        assertTrue(task.done)
        assertEquals(Instant.parse("2026-09-24T12:00:00Z"), task.completedAt)
    }

    @Test
    fun `Nextcloud exact-time task alarm remains fixed`() {
        val task = mapper.parse(
            listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
                "UID:exact-alarm", "SUMMARY:Call Alex",
                "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Reminder",
                "TRIGGER;VALUE=DATE-TIME:20260925T120000Z", "END:VALARM",
                "END:VTODO", "END:VCALENDAR").joinToString("\n"),
            "cal", 0L, "href", null,
        ).tasks.single()
        assertEquals(Instant.parse("2026-09-25T12:00:00Z"), task.reminder?.absoluteAt)
        assertNull(task.due)
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
