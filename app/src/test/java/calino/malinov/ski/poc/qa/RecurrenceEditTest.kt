package calino.malinov.ski.poc.qa

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.property.ExceptionDates
import biweekly.util.ICalDate
import calino.malinov.ski.poc.data.caldav.RecurrenceEdit
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceEditTest {

    @Test
    fun `THIS edit replaces a detached override and removes a legacy same-day EXDATE`() {
        val group = timedGroup()
        val target = requireNotNull(RecurrenceEdit.Target.from(group.overrides.first()))
        val replacement = event(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:edited
            DTSTART:20260302T110000Z
            DTEND:20260302T120000Z
            SUMMARY:Edited occurrence
            END:VEVENT
            END:VCALENDAR
            """,
        )
        val before = write(group)

        val result = RecurrenceEdit.edit(
            group = group,
            target = target,
            scope = RecurrenceEdit.Scope.THIS,
            replacement = replacement,
        )

        assertEquals(RecurrenceEdit.Scope.THIS, result.effectiveScope)
        assertEquals(1, result.groups.size)
        val edited = result.groups.single()
        assertEquals("series", edited.master.uid?.value)
        assertEquals(2, edited.overrides.size)
        val override = edited.overrides.single { it.summary?.value == "Edited occurrence" }
        assertEquals("series", override.uid?.value)
        assertEquals(Instant.parse("2026-03-02T09:00:00Z").toEpochMilli(), override.recurrenceId!!.value.time)
        assertNull(override.recurrenceRule)
        assertTrue(
            edited.master.getProperties(ExceptionDates::class.java)
                .flatMap { it.values }
                .none { it.time == Instant.parse("2026-03-02T00:00:00Z").toEpochMilli() },
        )
        assertEquals("the input group is not mutated", before, write(group))

        val ics = write(edited)
        assertTrue(ics.contains("RECURRENCE-ID:20260302T090000Z"))
        assertFalse(ics.contains("EXDATE:20260302T000000Z"))
    }

    @Test
    fun `THIS delete appends a date-only EXDATE and drops the detached override`() {
        val group = allDayGroup()
        val result = RecurrenceEdit.delete(
            group = group,
            target = RecurrenceEdit.Target.allDay(LocalDate.of(2026, 3, 3)),
            scope = RecurrenceEdit.Scope.THIS,
        )

        val edited = result.groups.single()
        assertTrue(edited.overrides.isEmpty())
        val dates = edited.master.getProperties(ExceptionDates::class.java)
            .flatMap { it.values }
            .map(::dateDigits)
        assertEquals(listOf("20260302", "20260303"), dates)
        assertTrue(write(edited).contains("EXDATE;VALUE=DATE:20260302,20260303"))
    }

    @Test
    fun `FUTURE timed edit truncates COUNT and migrates later overrides to the new UID`() {
        val group = timedGroup()
        val replacement = event(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:edited
            DTSTART:20260303T090000Z
            DTEND:20260303T100000Z
            RRULE:FREQ=DAILY;COUNT=5
            SUMMARY:Future title
            END:VEVENT
            END:VCALENDAR
            """,
        )

        val result = RecurrenceEdit.edit(
            group = group,
            target = RecurrenceEdit.Target.timed(Instant.parse("2026-03-03T09:00:00Z")),
            scope = RecurrenceEdit.Scope.FUTURE,
            replacement = replacement,
            newUid = "series-split",
        )

        assertEquals(2, result.groups.size)
        val old = result.groups[0]
        val future = result.groups[1]
        assertNull(old.master.recurrenceRule!!.value.count)
        assertEquals(
            Instant.parse("2026-03-03T08:59:59Z").toEpochMilli(),
            old.master.recurrenceRule!!.value.until!!.time,
        )
        assertEquals(1, old.overrides.size)
        assertEquals(
            Instant.parse("2026-03-02T09:00:00Z").toEpochMilli(),
            old.overrides.single().recurrenceId!!.value.time,
        )

        assertEquals("series-split", future.master.uid?.value)
        assertEquals(0, future.master.sequence?.value)
        assertEquals(3, future.master.recurrenceRule!!.value.count)
        assertEquals(
            Instant.parse("2026-03-03T09:00:00Z").toEpochMilli(),
            future.master.dateStart!!.value.time,
        )
        assertEquals(1, future.overrides.size)
        assertEquals("series-split", future.overrides.single().uid?.value)
        assertTrue(
            future.master.getProperties(ExceptionDates::class.java).isEmpty(),
        )

        assertTrue(write(old).contains("RRULE:FREQ=DAILY;UNTIL=20260303T085959Z"))
        assertTrue(write(future).contains("RRULE:FREQ=DAILY;COUNT=3"))
    }

    @Test
    fun `FUTURE timed edit migrates later override slots when the new start moves`() {
        val calendar = parsedCalendar(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:shifted-series
            DTSTART:20260301T090000Z
            DTEND:20260301T100000Z
            RRULE:FREQ=DAILY;COUNT=5
            SUMMARY:Master
            END:VEVENT
            BEGIN:VEVENT
            UID:shifted-series
            RECURRENCE-ID:20260304T090000Z
            DTSTART:20260304T100000Z
            DTEND:20260304T110000Z
            SUMMARY:Later exception
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )
        val group = RecurrenceEdit.Group.from(calendar, "shifted-series")
        val replacement = event(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:edited
            DTSTART:20260303T110000Z
            DTEND:20260303T120000Z
            RRULE:FREQ=DAILY;COUNT=5
            SUMMARY:Shifted future
            END:VEVENT
            END:VCALENDAR
            """,
        )

        val result = RecurrenceEdit.edit(
            group = group,
            target = RecurrenceEdit.Target.timed(Instant.parse("2026-03-03T09:00:00Z")),
            scope = RecurrenceEdit.Scope.FUTURE,
            replacement = replacement,
            newUid = "shifted-series-future",
        )

        val later = result.groups[1].overrides.single()
        assertEquals("shifted-series-future", later.uid?.value)
        assertEquals(
            Instant.parse("2026-03-04T11:00:00Z").toEpochMilli(),
            later.recurrenceId!!.value.time,
        )
    }

    @Test
    fun `FUTURE all-day edit uses the previous date and the remaining COUNT`() {
        val group = allDayGroup()
        val replacement = event(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:edited
            DTSTART;VALUE=DATE:20260303
            DTEND;VALUE=DATE:20260304
            RRULE:FREQ=DAILY;COUNT=4
            SUMMARY:Future day
            END:VEVENT
            END:VCALENDAR
            """,
        )

        val result = RecurrenceEdit.edit(
            group = group,
            target = RecurrenceEdit.Target.allDay(LocalDate.of(2026, 3, 3)),
            scope = RecurrenceEdit.Scope.FUTURE,
            replacement = replacement,
            newUid = "day-split",
        )

        val old = result.groups[0]
        val future = result.groups[1]
        assertEquals("20260302", dateDigits(old.master.recurrenceRule!!.value.until!!))
        assertEquals(3, future.master.recurrenceRule!!.value.count)
        assertEquals("day-split", future.master.uid?.value)
        assertFalse(write(old).contains("EXDATE;VALUE=DATE:20260303"))
        assertTrue(write(old).contains("RRULE:FREQ=DAILY;UNTIL=20260302"))
        assertTrue(write(future).contains("DTSTART;VALUE=DATE:20260303"))
    }

    @Test
    fun `FUTURE edit rejects a detached override with no slot in the new series`() {
        val calendar = parsedCalendar(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:short-series
            DTSTART;VALUE=DATE:20260301
            DTEND;VALUE=DATE:20260302
            RRULE:FREQ=DAILY;COUNT=4
            SUMMARY:Series
            END:VEVENT
            BEGIN:VEVENT
            UID:short-series
            RECURRENCE-ID;VALUE=DATE:20260303
            DTSTART;VALUE=DATE:20260303
            DTEND;VALUE=DATE:20260304
            SUMMARY:Detached later edit
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )
        val group = RecurrenceEdit.Group.from(calendar, "short-series")
        val replacement = event(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:replacement
            DTSTART;VALUE=DATE:20260302
            DTEND;VALUE=DATE:20260303
            RRULE:FREQ=DAILY;COUNT=1
            SUMMARY:Only one future day
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )

        val failure = runCatching {
            RecurrenceEdit.edit(
                group = group,
                target = RecurrenceEdit.Target.allDay(LocalDate.of(2026, 3, 2)),
                scope = RecurrenceEdit.Scope.FUTURE,
                replacement = replacement,
                newUid = "short-series-future",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("could not be mapped"))
    }

    @Test
    fun `FUTURE on the first occurrence is an ALL edit, and delete removes the series`() {
        val group = timedGroup()
        val replacement = group.master.copy().also { it.setSummary("All title") }
        val target = requireNotNull(RecurrenceEdit.Target.from(group.master))

        val edit = RecurrenceEdit.edit(
            group = group,
            target = target,
            scope = RecurrenceEdit.Scope.FUTURE,
            replacement = replacement,
            newUid = "should-not-be-used",
        )
        assertEquals(RecurrenceEdit.Scope.ALL, edit.effectiveScope)
        assertEquals(1, edit.groups.size)
        assertEquals("series", edit.groups.single().master.uid?.value)
        assertEquals("All title", edit.groups.single().master.summary?.value)
        assertEquals(2, edit.groups.single().overrides.size)

        val delete = RecurrenceEdit.delete(group, target, RecurrenceEdit.Scope.FUTURE)
        assertTrue(delete.deleted)
        assertTrue(delete.groups.isEmpty())
        assertEquals(RecurrenceEdit.Scope.ALL, delete.effectiveScope)
    }

    @Test
    fun `ALL edits only the master and keeps detached overrides`() {
        val group = timedGroup()
        val replacement = group.master.copy().also { it.setSummary("Whole series") }

        val result = RecurrenceEdit.edit(
            group = group,
            target = RecurrenceEdit.Target.timed(Instant.parse("2026-03-04T09:00:00Z")),
            scope = RecurrenceEdit.Scope.ALL,
            replacement = replacement,
        )

        assertEquals(1, result.groups.size)
        assertEquals("Whole series", result.groups.single().master.summary?.value)
        assertEquals(2, result.groups.single().overrides.size)
        assertEquals(
            group.overrides.map { it.summary?.value },
            result.groups.single().overrides.map { it.summary?.value },
        )
    }

    @Test
    fun `THIS edit preserves a parsed TZID on the override and edited times`() {
        val calendar = parsedCalendar(tzidGroupIcs)
        val group = RecurrenceEdit.Group.from(calendar, "tz-series")
        val targetEvent = group.overrides.single()
        val target = requireNotNull(RecurrenceEdit.Target.from(targetEvent))
        val replacement = targetEvent.copy().also { it.setSummary("Timezone edit") }

        val result = RecurrenceEdit.edit(
            group = group,
            target = target,
            scope = RecurrenceEdit.Scope.THIS,
            replacement = replacement,
        )
        val edited = result.groups.single().overrides.single()
        assertEquals("America/New_York", edited.dateStart!!.getParameter("TZID"))
        assertEquals("America/New_York", edited.dateEnd!!.getParameter("TZID"))
        assertEquals("America/New_York", edited.recurrenceId!!.getParameter("TZID"))
        assertEquals("20260308T100000", dateTimeDigits(edited.dateStart!!.value))
        assertEquals("20260308T110000", dateTimeDigits(edited.dateEnd!!.value))
        assertEquals("20260308T090000", dateTimeDigits(edited.recurrenceId!!.value))
        assertEquals(
            "America/New_York",
            group.calendar!!.timezoneInfo.getTimezone(group.master.dateStart!!)?.timeZone?.id,
        )
    }

    private fun timedGroup(): RecurrenceEdit.Group =
        RecurrenceEdit.Group.from(parsedCalendar(timedGroupIcs), "series")

    private fun allDayGroup(): RecurrenceEdit.Group =
        RecurrenceEdit.Group.from(parsedCalendar(allDayGroupIcs), "day-series")

    private fun event(ics: String): VEvent =
        parsedCalendar(ics).events.single()

    private fun parsedCalendar(ics: String): ICalendar =
        requireNotNull(Biweekly.parse(ics.trimIndent()).first())

    private fun dateDigits(value: ICalDate): String {
        val raw = requireNotNull(value.rawComponents)
        return "%04d%02d%02d".format(raw.year, raw.month, raw.date)
    }

    private fun dateTimeDigits(value: ICalDate): String {
        val raw = requireNotNull(value.rawComponents)
        return "%04d%02d%02dT%02d%02d%02d".format(
            raw.year,
            raw.month,
            raw.date,
            raw.hour,
            raw.minute,
            raw.second,
        )
    }

    /** Serializes one result group for assertions about ordinary value form. */
    private fun write(group: RecurrenceEdit.Group): String {
        val calendar = group.calendar?.let(::ICalendar) ?: ICalendar()
        val uid = requireNotNull(group.master.uid?.value)
        calendar.events
            .filter { it.uid?.value == uid }
            .toList()
            .forEach { calendar.removeComponent(it) }
        group.events.forEach { calendar.addEvent(it.copy()) }
        return Biweekly.write(calendar).go()
    }

    private companion object {
        const val timedGroupIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//EN
            BEGIN:VEVENT
            UID:series
            DTSTART:20260301T090000Z
            DTEND:20260301T100000Z
            RRULE:FREQ=DAILY;COUNT=5
            EXDATE:20260302T000000Z
            SUMMARY:Master
            END:VEVENT
            BEGIN:VEVENT
            UID:series
            RECURRENCE-ID:20260302T090000Z
            DTSTART:20260302T100000Z
            DTEND:20260302T110000Z
            SUMMARY:Existing override
            END:VEVENT
            BEGIN:VEVENT
            UID:series
            RECURRENCE-ID:20260304T090000Z
            DTSTART:20260304T120000Z
            DTEND:20260304T130000Z
            SUMMARY:Future override
            END:VEVENT
            END:VCALENDAR
        """

        const val allDayGroupIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//EN
            BEGIN:VEVENT
            UID:day-series
            DTSTART;VALUE=DATE:20260301
            DTEND;VALUE=DATE:20260302
            RRULE:FREQ=DAILY;COUNT=4
            EXDATE;VALUE=DATE:20260302
            SUMMARY:Days
            END:VEVENT
            BEGIN:VEVENT
            UID:day-series
            RECURRENCE-ID;VALUE=DATE:20260303
            DTSTART;VALUE=DATE:20260303
            DTEND;VALUE=DATE:20260304
            SUMMARY:Day override
            END:VEVENT
            END:VCALENDAR
        """

        const val tzidGroupIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//EN
            BEGIN:VTIMEZONE
            TZID:America/New_York
            BEGIN:DAYLIGHT
            TZOFFSETFROM:-0500
            TZOFFSETTO:-0400
            DTSTART:20260308T020000
            RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU
            TZNAME:EDT
            END:DAYLIGHT
            BEGIN:STANDARD
            TZOFFSETFROM:-0400
            TZOFFSETTO:-0500
            DTSTART:20261101T020000
            RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU
            TZNAME:EST
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:tz-series
            DTSTART;TZID=America/New_York:20260301T090000
            DTEND;TZID=America/New_York:20260301T100000
            RRULE:FREQ=WEEKLY;COUNT=3
            SUMMARY:TZ master
            END:VEVENT
            BEGIN:VEVENT
            UID:tz-series
            RECURRENCE-ID;TZID=America/New_York:20260308T090000
            DTSTART;TZID=America/New_York:20260308T100000
            DTEND;TZID=America/New_York:20260308T110000
            SUMMARY:TZ override
            END:VEVENT
            END:VCALENDAR
        """
    }
}
