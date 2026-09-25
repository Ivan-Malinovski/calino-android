package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.caldav.ICalPatcher
import calino.malinov.ski.data.caldav.ICalTimezones
import calino.malinov.ski.data.caldav.ICalWriter
import calino.malinov.ski.data.caldav.RecurrenceEdit
import calino.malinov.ski.data.caldav.VTimezoneBuilder
import calino.malinov.ski.data.model.CalEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Event timezones against RFC 5545: every TZID has a VTIMEZONE (3.2.19,
 * 3.6.5), floating and UTC values keep their form (3.3.5), DTEND may use its
 * own zone (3.8.2.2), and a series expands in its DTSTART zone (3.8.5.3).
 */
class TimezoneTest {

    /** The device zone is deliberately not the events' zone. */
    private val device = ZoneId.of("Europe/Berlin")
    private val writer = ICalWriter(device)
    private val mapper = ICalMapper(device)
    private val patcher = ICalPatcher(writer)
    private val now = Instant.parse("2026-03-01T08:00:00Z")

    private fun ics(vararg lines: String) = lines.joinToString("\r\n") + "\r\n"

    /** Thunderbird's shape: its own VTIMEZONE, a foreign property, TZID times. */
    private val thunderbird = ics(
        "BEGIN:VCALENDAR",
        "PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN",
        "VERSION:2.0",
        "BEGIN:VTIMEZONE",
        "TZID:America/New_York",
        "X-LIC-LOCATION:America/New_York",
        "BEGIN:DAYLIGHT",
        "TZOFFSETFROM:-0500",
        "TZOFFSETTO:-0400",
        "TZNAME:EDT",
        "DTSTART:19700308T020000",
        "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU",
        "END:DAYLIGHT",
        "BEGIN:STANDARD",
        "TZOFFSETFROM:-0400",
        "TZOFFSETTO:-0500",
        "TZNAME:EST",
        "DTSTART:19701101T020000",
        "RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU",
        "END:STANDARD",
        "END:VTIMEZONE",
        "BEGIN:VEVENT",
        "CREATED:20260201T100000Z",
        "LAST-MODIFIED:20260201T100000Z",
        "DTSTAMP:20260201T100000Z",
        "UID:tb-standup",
        "SUMMARY:Standup",
        "RRULE:FREQ=WEEKLY;BYDAY=MO",
        "DTSTART;TZID=America/New_York:20260302T090000",
        "DTEND;TZID=America/New_York:20260302T093000",
        "X-MOZ-GENERATION:1",
        "END:VEVENT",
        "END:VCALENDAR",
    )

    private fun unfold(text: String) = text.replace("\r\n ", "").lines()

    /** Structural RFC 5545 checks that do not depend on biweekly's reading. */
    private fun assertValidZones(text: String) {
        val lines = unfold(text)
        val defined = lines.filter { it.startsWith("TZID:") }.map { it.removePrefix("TZID:") }.toSet()
        val used = Regex(";TZID=([^;:]+)[;:]").findAll(lines.joinToString("\n")).map { it.groupValues[1] }.toSet()
        assertTrue("undefined TZIDs ${used - defined} in\n$text", defined.containsAll(used))
        lines.filter { it.contains(";TZID=") }.forEach { line ->
            assertFalse("TZID with a UTC value: $line", line.endsWith("Z"))
        }
        var inObservance = false
        val seen = mutableSetOf<String>()
        lines.forEach { line ->
            when {
                line == "BEGIN:STANDARD" || line == "BEGIN:DAYLIGHT" -> { inObservance = true; seen.clear() }
                line == "END:STANDARD" || line == "END:DAYLIGHT" -> {
                    assertTrue("observance missing fields: $seen", seen.containsAll(listOf("DTSTART", "TZOFFSETFROM", "TZOFFSETTO")))
                    inObservance = false
                }
                inObservance -> seen += line.substringBefore(':').substringBefore(';')
            }
        }
    }

    private fun line(text: String, prefix: String) = unfold(text).first { it.startsWith(prefix) }

    private fun event(start: LocalDateTime, zoneId: String?, endZoneId: String? = null, minutes: Int = 60) = CalEvent(
        id = "new",
        uid = "new",
        title = "Flight",
        color = 0,
        start = start,
        durationMinutes = minutes,
        calendarId = "cal",
        zoneId = zoneId,
        endZoneId = endZoneId,
    )

    // --- VTIMEZONE builder --------------------------------------------------

    /**
     * Independent reading of a VTIMEZONE (RFC 5545 3.6.5): each onset is the
     * observance's local DTSTART/RRULE/RDATE time minus TZOFFSETFROM, and the
     * offset in force is the latest onset's TZOFFSETTO. Not biweekly's
     * ICalTimeZone, which is what is under suspicion.
     */
    private fun onsets(vtimezone: biweekly.component.VTimezone, until: Instant): List<Pair<Instant, Int>> {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        return (vtimezone.standardTimes + vtimezone.daylightSavingsTime).flatMap { observance ->
            val from = observance.timezoneOffsetFrom.value.millis
            val to = observance.timezoneOffsetTo.value.millis.toInt()
            // Observance times are local; reading their digits as UTC gives
            // the local time line that the offsets are then applied to.
            fun digits(value: biweekly.util.ICalDate): Instant = value.rawComponents.let {
                LocalDateTime.of(it.year, it.month, it.date, it.hour, it.minute, it.second).toInstant(java.time.ZoneOffset.UTC)
            }
            val start = digits(observance.dateStart.value)
            val locals = mutableListOf(start)
            observance.recurrenceDates.flatMap { it.dates }.mapTo(locals, ::digits)
            observance.recurrenceRule?.let { rule ->
                val iterator = rule.getDateIterator(java.util.Date.from(start), utc)
                while (iterator.hasNext()) {
                    val local = iterator.next().toInstant()
                    if (local > until) break
                    locals += local
                }
            }
            locals.map { it.minusMillis(from) to to }
        }.sortedBy { it.first }
    }

    @Test
    fun `built VTIMEZONE offsets match tzdb`() {
        val from = Instant.parse("2020-01-01T00:00:00Z")
        listOf(
            "Europe/Berlin", "America/New_York", "Asia/Kolkata",
            "Australia/Lord_Howe", "Africa/Casablanca", "America/Sao_Paulo", "Pacific/Chatham",
        ).forEach { id ->
            val zone = ZoneId.of(id)
            val vtimezone = VTimezoneBuilder.build(zone, from)
            val onsets = onsets(vtimezone, until = from.plus(Duration.ofDays(365L * 31)))
            val end = from.plus(Duration.ofDays(365L * 30))
            // Every transition edge, where an onset an hour off would show,
            // plus an hourly sweep of the first years.
            val samples = mutableListOf<Instant>()
            var transition = zone.rules.nextTransition(from)
            while (transition != null && transition.instant < end) {
                listOf(-1L, 0L, 1L, 3599L, -3599L).forEach { samples += transition!!.instant.plusSeconds(it) }
                transition = zone.rules.nextTransition(transition.instant)
            }
            var sweep = from
            while (sweep < from.plus(Duration.ofDays(365L * 3))) { samples += sweep; sweep = sweep.plus(Duration.ofHours(1)) }
            samples.forEach { sample ->
                assertEquals(
                    "$id at $sample",
                    zone.rules.getOffset(sample).totalSeconds * 1000,
                    onsets.last { it.first <= sample }.second,
                )
            }
        }
    }

    @Test
    fun `zone without DST gets one observance and no RRULE`() {
        val vtimezone = VTimezoneBuilder.build(ZoneId.of("Asia/Kolkata"), Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(1, vtimezone.standardTimes.size + vtimezone.daylightSavingsTime.size)
    }

    // --- Writing ------------------------------------------------------------

    @Test
    fun `new timed event is written in its zone with a matching VTIMEZONE`() {
        val start = LocalDateTime.of(2026, 3, 2, 15, 0) // 09:00 in New York
        val text = writer.buildCalendar(listOf(writer.writeEvent(event(start, "America/New_York"), now = now)))
        assertEquals("DTSTART;TZID=America/New_York:20260302T090000", line(text, "DTSTART;"))
        assertEquals("DTEND;TZID=America/New_York:20260302T100000", line(text, "DTEND"))
        assertValidZones(text)

        val back = mapper.parse(text, "cal", 0, "x.ics").events.single()
        assertEquals(start, back.start)
        assertEquals("America/New_York", back.zoneId)
        assertNull(back.endZoneId)
    }

    @Test
    fun `DTEND keeps its own zone`() {
        // 10:00 Copenhagen -> 12:00 New York (an eight-hour flight).
        val start = LocalDateTime.of(2026, 6, 1, 10, 0)
        val text = writer.buildCalendar(
            listOf(writer.writeEvent(event(start, "Europe/Copenhagen", "America/New_York", minutes = 8 * 60), now = now)),
        )
        assertEquals("DTSTART;TZID=Europe/Copenhagen:20260601T100000", line(text, "DTSTART;"))
        assertEquals("DTEND;TZID=America/New_York:20260601T120000", line(text, "DTEND"))
        assertValidZones(text)
        val back = mapper.parse(text, "cal", 0, "x.ics").events.single()
        assertEquals("Europe/Copenhagen", back.zoneId)
        assertEquals("America/New_York", back.endZoneId)
        assertEquals(8 * 60, back.durationMinutes)
    }

    @Test
    fun `an event without a zone is written in UTC`() {
        val text = writer.buildCalendar(listOf(writer.writeEvent(event(LocalDateTime.of(2026, 3, 2, 10, 0), null), now = now)))
        assertEquals("DTSTART:20260302T090000Z", line(text, "DTSTART"))
        assertFalse(text.contains("VTIMEZONE"))
    }

    @Test
    fun `a floating event stays floating when moved`() {
        val floating = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:x",
            "BEGIN:VEVENT", "UID:f", "DTSTAMP:20260101T000000Z",
            "DTSTART:20260302T090000", "DTEND:20260302T100000", "SUMMARY:Floating",
            "END:VEVENT", "END:VCALENDAR",
        )
        val mapped = mapper.parse(floating, "cal", 0, "f.ics").events.single()
        assertNull(mapped.zoneId)
        val moved = mapped.copy(start = mapped.start!!.plusHours(2))
        val text = requireNotNull(patcher.patchEvents(floating, listOf(moved), now))
        assertEquals("DTSTART:20260302T110000", line(text, "DTSTART"))
        assertEquals("DTEND:20260302T120000", line(text, "DTEND"))
    }

    @Test
    fun `a UTC event stays UTC when moved`() {
        val utc = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:x",
            "BEGIN:VEVENT", "UID:u", "DTSTAMP:20260101T000000Z",
            "DTSTART:20260302T090000Z", "DTEND:20260302T100000Z", "SUMMARY:UTC",
            "END:VEVENT", "END:VCALENDAR",
        )
        val mapped = mapper.parse(utc, "cal", 0, "u.ics").events.single()
        val text = requireNotNull(patcher.patchEvents(utc, listOf(mapped.copy(start = mapped.start!!.plusHours(1))), now))
        assertEquals("DTSTART:20260302T100000Z", line(text, "DTSTART"))
    }

    // --- Thunderbird round trip ---------------------------------------------

    private fun tbMaster(): CalEvent =
        mapper.parse(
            thunderbird, "cal", 0, "tb.ics",
            windowStart = LocalDate.of(2026, 3, 1), windowEnd = LocalDate.of(2026, 3, 7),
        ).events.single().let { occurrence ->
            // The editor saves the series master for scope ALL.
            occurrence.copy(id = "tb-standup", recurrenceId = null, recurrence = "FREQ=WEEKLY;BYDAY=MO")
        }

    @Test
    fun `a foreign zone survives a title edit byte for byte`() {
        val master = tbMaster()
        assertEquals("America/New_York", master.zoneId)
        val text = requireNotNull(patcher.patchEvents(thunderbird, listOf(master.copy(title = "Daily sync")), now))
        assertEquals("DTSTART;TZID=America/New_York:20260302T090000", line(text, "DTSTART;"))
        assertEquals(1, Regex("BEGIN:VTIMEZONE").findAll(text).count())
        assertTrue("server VTIMEZONE kept", text.contains("X-LIC-LOCATION:America/New_York"))
        assertValidZones(text)
    }

    @Test
    fun `moving a foreign-zone event keeps its TZID and the server VTIMEZONE`() {
        val master = tbMaster()
        val text = requireNotNull(patcher.patchEvents(thunderbird, listOf(master.copy(start = master.start!!.plusHours(1))), now))
        assertEquals("DTSTART;TZID=America/New_York:20260302T100000", line(text, "DTSTART;"))
        assertEquals(1, Regex("BEGIN:VTIMEZONE").findAll(text).count())
        assertTrue(text.contains("X-LIC-LOCATION:America/New_York"))
        assertValidZones(text)
    }

    @Test
    fun `changing the zone keeps wall time semantics of the new zone and adds its VTIMEZONE`() {
        val master = tbMaster()
        // The editor keeps the wall time: 09:00 in Chicago, shown in Berlin.
        val chicagoNine = LocalDate.of(2026, 3, 2).atTime(LocalTime.of(9, 0))
            .atZone(ZoneId.of("America/Chicago")).withZoneSameInstant(device).toLocalDateTime()
        val text = requireNotNull(
            patcher.patchEvents(thunderbird, listOf(master.copy(start = chicagoNine, zoneId = "America/Chicago")), now),
        )
        assertEquals("DTSTART;TZID=America/Chicago:20260302T090000", line(text, "DTSTART;"))
        assertEquals("DTEND;TZID=America/Chicago:20260302T093000", line(text, "DTEND"))
        assertTrue(unfold(text).contains("TZID:America/Chicago"))
        assertValidZones(text)
    }

    @Test
    fun `a New York series keeps 0900 local across DST in a Berlin device`() {
        val occurrences = mapper.parse(
            thunderbird, "cal", 0, "tb.ics",
            windowStart = LocalDate.of(2026, 3, 1), windowEnd = LocalDate.of(2026, 11, 30),
        ).events
        assertTrue(occurrences.size > 30)
        occurrences.forEach { occurrence ->
            val local = occurrence.start!!.atZone(device).withZoneSameInstant(ZoneId.of("America/New_York"))
            assertEquals("$occurrence", LocalTime.of(9, 0), local.toLocalTime())
            assertEquals("America/New_York", occurrence.zoneId)
        }
    }

    // --- Recurrence edits ----------------------------------------------------

    @Test
    fun `a THIS edit on a TZID series writes a defined zone, never TZID with Z`() {
        val calendar = ICalTimezones.parse(thunderbird).single()
        val group = RecurrenceEdit.Group.from(calendar, "tb-standup")
        val occurrence = Instant.parse("2026-03-09T13:00:00Z")
        val replacement = writer.writeEvent(
            tbMaster().copy(start = LocalDateTime.of(2026, 3, 9, 15, 0)),
            original = group.master.copy(),
            now = now,
        )
        val result = RecurrenceEdit.edit(group, RecurrenceEdit.Target.timed(occurrence), RecurrenceEdit.Scope.THIS, replacement)
        val text = requireNotNull(patcher.patchRecurrence(thunderbird, "tb-standup", result))
        assertValidZones(text)
        assertTrue(text, unfold(text).contains("RECURRENCE-ID;TZID=America/New_York:20260309T090000"))
        assertTrue(text, unfold(text).contains("DTSTART;TZID=America/New_York:20260309T100000"))
    }

    @Test
    fun `an untouched override keeps its TZID when another occurrence is edited`() {
        val withOverride = thunderbird.replace(
            "END:VEVENT\r\nEND:VCALENDAR",
            "END:VEVENT\r\n" + ics(
                "BEGIN:VEVENT", "UID:tb-standup", "DTSTAMP:20260201T100000Z",
                "RECURRENCE-ID;TZID=America/New_York:20260316T090000",
                "DTSTART;TZID=America/New_York:20260316T110000",
                "DTEND;TZID=America/New_York:20260316T113000",
                "SUMMARY:Moved standup", "END:VEVENT",
            ) + "END:VCALENDAR",
        )
        val calendar = ICalTimezones.parse(withOverride).single()
        val group = RecurrenceEdit.Group.from(calendar, "tb-standup")
        val replacement = writer.writeEvent(
            tbMaster().copy(title = "Only this one"),
            original = group.master.copy(),
            now = now,
        )
        val result = RecurrenceEdit.edit(
            group, RecurrenceEdit.Target.timed(Instant.parse("2026-03-09T13:00:00Z")), RecurrenceEdit.Scope.THIS, replacement,
        )
        val text = requireNotNull(patcher.patchRecurrence(withOverride, "tb-standup", result))
        assertValidZones(text)
        assertTrue(text, unfold(text).contains("DTSTART;TZID=America/New_York:20260316T110000"))
        assertTrue(text, unfold(text).contains("RECURRENCE-ID;TZID=America/New_York:20260316T090000"))
    }

    // --- 412 rebase ----------------------------------------------------------

    @Test
    fun `a rebase keeps the foreign zone and the server's other change`() {
        val local = requireNotNull(patcher.patchEvents(thunderbird, listOf(tbMaster().copy(title = "Daily sync")), now))
        val current = thunderbird.replace("X-MOZ-GENERATION:1", "X-MOZ-GENERATION:2\r\nLOCATION:Room 4")
        val text = requireNotNull(patcher.rebaseResource(current, local, thunderbird, "VEVENT", setOf("tb-standup")))
        assertValidZones(text)
        assertEquals("DTSTART;TZID=America/New_York:20260302T090000", line(text, "DTSTART;"))
        assertTrue(text.contains("SUMMARY:Daily sync"))
        assertTrue(text.contains("LOCATION:Room 4"))
        assertTrue(text.contains("X-LIC-LOCATION:America/New_York"))
    }

    @Test
    fun `a rebase carries a local zone change`() {
        val chicagoNine = LocalDateTime.of(2026, 3, 2, 9, 0)
            .atZone(ZoneId.of("America/Chicago")).withZoneSameInstant(device).toLocalDateTime()
        val local = requireNotNull(
            patcher.patchEvents(thunderbird, listOf(tbMaster().copy(start = chicagoNine, zoneId = "America/Chicago")), now),
        )
        val current = thunderbird.replace("X-MOZ-GENERATION:1", "X-MOZ-GENERATION:2")
        val text = requireNotNull(patcher.rebaseResource(current, local, thunderbird, "VEVENT", setOf("tb-standup")))
        assertValidZones(text)
        assertEquals("DTSTART;TZID=America/Chicago:20260302T090000", line(text, "DTSTART;"))
    }

    // --- TZID resolution ----------------------------------------------------

    @Test
    fun `TZIDs resolve from IANA, vendor prefixes and Windows names`() {
        assertEquals(ZoneId.of("America/New_York"), ICalTimezones.resolve("America/New_York"))
        assertEquals(ZoneId.of("America/New_York"), ICalTimezones.resolve("/mozilla.org/20050126_1/America/New_York"))
        assertEquals(ZoneId.of("America/Argentina/Buenos_Aires"), ICalTimezones.resolve("/citadel.org/20190914_1/America/Argentina/Buenos_Aires"))
        assertEquals(ZoneId.of("Europe/Berlin"), ICalTimezones.resolve("W. Europe Standard Time"))
        assertNull(ICalTimezones.resolve("Nowhere Standard Time"))
    }

    @Test
    fun `an undefined Windows TZID is read in its zone and written with a definition`() {
        val outlook = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:Microsoft Exchange Server 2010",
            "BEGIN:VEVENT", "UID:ms", "DTSTAMP:20260101T000000Z",
            "DTSTART;TZID=Eastern Standard Time:20260302T090000",
            "DTEND;TZID=Eastern Standard Time:20260302T100000",
            "SUMMARY:Outlook", "END:VEVENT", "END:VCALENDAR",
        )
        val mapped = mapper.parse(outlook, "cal", 0, "ms.ics").events.single()
        assertEquals(LocalDateTime.of(2026, 3, 2, 15, 0), mapped.start)
        assertEquals("America/New_York", mapped.zoneId)
        val text = requireNotNull(patcher.patchEvents(outlook, listOf(mapped.copy(title = "Renamed")), now))
        assertValidZones(text)
        assertEquals(mapped.start, mapper.parse(text, "cal", 0, "ms.ics").events.single().start)
    }

    @Test
    fun `a Java zone id without a VTIMEZONE gets a definition on write`() {
        val bare = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:x",
            "BEGIN:VEVENT", "UID:b", "DTSTAMP:20260101T000000Z",
            "DTSTART;TZID=Europe/Paris:20260302T090000", "SUMMARY:Bare",
            "END:VEVENT", "END:VCALENDAR",
        )
        val mapped = mapper.parse(bare, "cal", 0, "b.ics").events.single()
        val text = requireNotNull(patcher.patchEvents(bare, listOf(mapped.copy(title = "Still bare?")), now))
        assertValidZones(text)
        assertTrue(text, unfold(text).contains("TZID:Europe/Paris"))
    }
}
