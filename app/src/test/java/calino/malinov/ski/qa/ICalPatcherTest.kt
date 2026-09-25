package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.caldav.ICalPatcher
import calino.malinov.ski.data.caldav.ICalWriter
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.model.RecurrenceEditScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The patcher's whole job is what it does *not* change.
 *
 * These cases are ported from the web app's `icalPatch.test.ts`, because the
 * property that matters -- an edit through Calino must be invisible to every
 * other client except in the field that changed -- is not something a round-trip
 * test can show.
 */
class ICalPatcherTest {

    private val zone = ZoneId.of("Europe/Copenhagen")
    private val patcher = ICalPatcher(ICalWriter(zone))
    private val mapper = ICalMapper(zone)
    private val now = Instant.parse("2026-03-05T08:00:00Z")

    private fun ics(vararg lines: String) = lines.joinToString("\r\n") + "\r\n"

    /** A resource as a foreign client would leave it: full of things we do not model. */
    private val foreignResource = ics(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Foreign Client//EN",
        "CALSCALE:GREGORIAN",
        "BEGIN:VTIMEZONE",
        "TZID:Europe/Copenhagen",
        "BEGIN:STANDARD",
        "DTSTART:19701025T030000",
        "TZOFFSETFROM:+0200",
        "TZOFFSETTO:+0100",
        "END:STANDARD",
        "END:VTIMEZONE",
        "BEGIN:VEVENT",
        "UID:ours",
        "DTSTAMP:20260101T000000Z",
        "DTSTART:20260305T090000Z",
        "DTEND:20260305T100000Z",
        "SUMMARY:Original",
        "ORGANIZER;CN=Boss:mailto:boss@example.com",
        "CLASS:CONFIDENTIAL",
        "X-CUSTOM-THING;X-PARAM=7:preserve this",
        "BEGIN:VALARM",
        "ACTION:DISPLAY",
        "TRIGGER:-PT15M",
        "DESCRIPTION:soon",
        "END:VALARM",
        "END:VEVENT",
        "END:VCALENDAR",
    )

    private fun eventFrom(resource: String, uid: String) =
        mapper.parse(resource, calendarId = "cal", color = 1L, href = "https://x/e.ics")
            .events.first { it.uid == uid }

    @Test
    fun `event title edit retains attendee state and timezone while normalizing duration`() {
        val resource = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0",
            "BEGIN:VTIMEZONE", "TZID:Europe/Copenhagen", "BEGIN:STANDARD",
            "DTSTART:19701025T030000", "TZOFFSETFROM:+0200", "TZOFFSETTO:+0100",
            "END:STANDARD", "END:VTIMEZONE",
            "BEGIN:VEVENT", "UID:foreign-event",
            "DTSTART;TZID=Europe/Copenhagen:20260305T090000", "DURATION:PT1H",
            "SUMMARY:Original",
            "ATTENDEE;CN=Ada;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=TRUE:mailto:ada@example.com",
            "END:VEVENT", "END:VCALENDAR",
        )
        val event = eventFrom(resource, "foreign-event")

        val patched = patcher.patchEvents(resource, listOf(event.copy(title = "Edited")), now)!!

        assertTrue(patched, patched.contains("SUMMARY:Edited"))
        assertTrue(patched, patched.contains("DTSTART;TZID=Europe/Copenhagen:20260305T090000"))
        assertTrue(patched, patched.contains("DTEND"))
        assertFalse(patched, patched.contains("DURATION:PT1H"))
        assertTrue(patched, patched.contains("ROLE=REQ-PARTICIPANT"))
        assertTrue(patched, patched.contains("PARTSTAT=ACCEPTED"))
        assertTrue(patched, patched.contains("RSVP=TRUE"))
    }

    @Test
    fun `all-scope task edit from an occurrence retains the master anchor`() {
        val resource = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO", "UID:repeat-task",
            "DTSTART;VALUE=DATE:20260303", "DUE;VALUE=DATE:20260303",
            "RRULE:FREQ=WEEKLY;BYDAY=TU", "SUMMARY:Original", "X-FOREIGN:keep",
            "STATUS:NEEDS-ACTION", "END:VTODO", "END:VCALENDAR",
        )
        val occurrence = mapper.parse(
            resource, "cal", 1L, "repeat.ics",
            windowStart = LocalDate.of(2026, 3, 9),
            windowEnd = LocalDate.of(2026, 3, 11),
        ).tasks.single()

        val patched = patcher.patchTask(
            resource,
            occurrence.copy(title = "Edited series", recurrenceScope = RecurrenceEditScope.All),
            now,
        )!!

        assertTrue(patched, patched.contains("SUMMARY:Edited series"))
        assertTrue(patched, patched.contains("DTSTART;VALUE=DATE:20260303"))
        assertTrue(patched, patched.contains("DUE;VALUE=DATE:20260303"))
        assertFalse(patched, patched.contains("DTSTART;VALUE=DATE:20260310"))
        assertTrue(patched, patched.contains("RRULE:FREQ=WEEKLY;BYDAY=TU"))
        assertTrue(patched, patched.contains("X-FOREIGN:keep"))
    }

    @Test
    fun `title-only task edit preserves distinct DTSTART and DUE`() {
        val resource = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO", "UID:window",
            "DTSTART:20260303T090000Z", "DUE:20260303T170000Z",
            "SUMMARY:Original", "STATUS:NEEDS-ACTION", "END:VTODO", "END:VCALENDAR",
        )
        val task = mapper.parse(resource, "cal", 1L, "window.ics").tasks.single()

        val patched = patcher.patchTask(resource, task.copy(title = "Edited"), now)!!

        assertTrue(patched, patched.contains("DTSTART:20260303T090000Z"))
        assertTrue(patched, patched.contains("DUE:20260303T170000Z"))
        assertTrue(patched, patched.contains("SUMMARY:Edited"))
    }

    @Test
    fun `title-only task edit preserves a Nextcloud repeating alarm`() {
        val resource = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO", "UID:repeated",
            "DUE:20260303T170000Z", "SUMMARY:Original",
            "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Reminder",
            "TRIGGER;RELATED=END:-PT1H", "REPEAT:1", "DURATION:PT10M",
            "END:VALARM", "END:VTODO", "END:VCALENDAR",
        )
        val task = mapper.parse(resource, "cal", 1L, "repeated.ics").tasks.single()
        val patched = patcher.patchTask(resource, task.copy(title = "Edited"), now)!!
        assertTrue(patched, patched.contains("REPEAT:1"))
        assertTrue(patched, patched.contains("DURATION:PT10M"))
        assertEquals(1, "BEGIN:VALARM".toRegex().findAll(patched).count())
    }

    @Test
    fun `task edit preserves sibling links while changing its parent`() {
        val resource = ics("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
            "UID:linked", "SUMMARY:Original", "RELATED-TO;RELTYPE=SIBLING:peer",
            "RELATED-TO;RELTYPE=PARENT:old-parent", "END:VTODO", "END:VCALENDAR")
        val task = mapper.parse(resource, "cal", 1L, "linked.ics").tasks.single()
        val patched = patcher.patchTask(resource, task.copy(parentTaskId = "new-parent"), now)!!
        assertTrue(patched, patched.contains("RELATED-TO;RELTYPE=SIBLING:peer"))
        assertTrue(patched, patched.contains("RELATED-TO:new-parent"))
        assertFalse(patched, patched.contains("old-parent"))
    }

    @Test
    fun `title edit does not reopen timestamp-only completed task`() {
        val resource = ics("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
            "UID:finished", "SUMMARY:Original", "COMPLETED:20260924T120000Z",
            "END:VTODO", "END:VCALENDAR")
        val task = mapper.parse(resource, "cal", 1L, "finished.ics").tasks.single()
        val patched = patcher.patchTask(resource, task.copy(title = "Edited"), now)!!
        assertTrue(patched, patched.contains("COMPLETED:20260924T120000Z"))
        assertTrue(patched, patched.contains("STATUS:COMPLETED"))
    }

    @Test
    fun `task edit preserves Nextcloud tags beyond the first category`() {
        val resource = ics("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
            "UID:tagged", "SUMMARY:Original", "CATEGORIES:Work,Important",
            "CATEGORIES:Follow-up", "END:VTODO", "END:VCALENDAR")
        val task = mapper.parse(resource, "cal", 1L, "tagged.ics").tasks.single()
        val titleEdit = patcher.patchTask(resource, task.copy(title = "Edited"), now)!!
        assertTrue(titleEdit, titleEdit.contains("CATEGORIES:Work,Important"))
        assertTrue(titleEdit, titleEdit.contains("CATEGORIES:Follow-up"))

        val categoryEdit = patcher.patchTask(resource, task.copy(category = "Personal"), now)!!
        assertTrue(categoryEdit, categoryEdit.contains("Personal"))
        assertTrue(categoryEdit, categoryEdit.contains("Important"))
        assertTrue(categoryEdit, categoryEdit.contains("Follow-up"))
    }

    @Test
    fun `moving task due date keeps Nextcloud exact-time alarm fixed`() {
        val resource = ics("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
            "UID:exact", "SUMMARY:Original", "DUE:20260925T150000Z",
            "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Reminder",
            "TRIGGER;VALUE=DATE-TIME:20260924T120000Z", "END:VALARM",
            "END:VTODO", "END:VCALENDAR")
        val task = mapper.parse(resource, "cal", 1L, "exact.ics").tasks.single()
        val patched = patcher.patchTask(resource, task.copy(due = task.due!!.plusDays(1)), now)!!
        assertTrue(patched, patched.contains("TRIGGER;VALUE=DATE-TIME:20260924T120000Z"))
    }

    @Test
    fun `moving a duration-based task writes DUE without DURATION`() {
        val resource = ics("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
            "UID:duration", "SUMMARY:Original", "DTSTART:20260924T090000Z",
            "DURATION:PT2H", "END:VTODO", "END:VCALENDAR")
        val task = mapper.parse(resource, "cal", 1L, "duration.ics").tasks.single()
        val patched = patcher.patchTask(resource, task.copy(due = task.due!!.plusDays(1)), now)!!
        assertTrue(patched, patched.contains("DUE:"))
        assertFalse(patched, patched.contains("DURATION:PT2H"))
    }

    @Test
    fun `task edit preserves additional Nextcloud alarms`() {
        val resource = ics("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO",
            "UID:two-alarms", "SUMMARY:Original", "DUE:20260924T150000Z",
            "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Early",
            "TRIGGER;RELATED=END:-PT1H", "END:VALARM",
            "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Late",
            "TRIGGER;RELATED=END:-PT10M", "END:VALARM",
            "END:VTODO", "END:VCALENDAR")
        val task = mapper.parse(resource, "cal", 1L, "two.ics").tasks.single()
        val titleEdit = patcher.patchTask(resource, task.copy(title = "Edited"), now)!!
        assertEquals(2, "BEGIN:VALARM".toRegex().findAll(titleEdit).count())
        assertTrue(titleEdit, titleEdit.contains("TRIGGER;RELATED=END:-PT10M"))

        val reminderEdit = patcher.patchTask(resource, task.copy(reminder = Reminder(30)), now)!!
        assertEquals(2, "BEGIN:VALARM".toRegex().findAll(reminderEdit).count())
        assertTrue(reminderEdit, reminderEdit.contains("TRIGGER;RELATED=END:-PT10M"))
    }

    @Test
    fun `future-scope task edit never rewrites the master anchor from the selected date`() {
        val resource = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO", "UID:repeat-task",
            "DTSTART;VALUE=DATE:20260303", "DUE;VALUE=DATE:20260303",
            "RRULE:FREQ=WEEKLY;BYDAY=TU", "SUMMARY:Original", "END:VTODO", "END:VCALENDAR",
        )
        val occurrence = mapper.parse(
            resource, "cal", 1L, "repeat.ics",
            windowStart = LocalDate.of(2026, 3, 9),
            windowEnd = LocalDate.of(2026, 3, 11),
        ).tasks.single()

        val patched = patcher.patchTask(
            resource,
            occurrence.copy(title = "Future title", recurrenceScope = RecurrenceEditScope.Future),
            now,
        )!!

        assertTrue(patched, patched.contains("DTSTART;VALUE=DATE:20260303"))
        assertTrue(patched, patched.contains("DUE;VALUE=DATE:20260303"))
        assertTrue(patched, patched.contains("RECURRENCE-ID;VALUE=DATE:20260310"))
    }

    @Test
    fun `completing a generated task appends a detached completion and leaves master open`() {
        val resource = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO", "UID:gym",
            "DTSTART;VALUE=DATE:20260303", "DUE;VALUE=DATE:20260303",
            "RRULE:FREQ=WEEKLY;BYDAY=TU", "SUMMARY:Exercise",
            "STATUS:NEEDS-ACTION", "PERCENT-COMPLETE:20",
            "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT1H", "DESCRIPTION:Exercise", "END:VALARM",
            "END:VTODO", "END:VCALENDAR",
        )
        val occurrence = mapper.parse(
            resource, "cal", 1L, "gym.ics",
            windowStart = LocalDate.of(2026, 3, 10), windowEnd = LocalDate.of(2026, 3, 10),
        ).tasks.single()
        assertEquals(RecurrenceEditScope.This, occurrence.recurrenceScope)

        val patched = patcher.patchTask(
            resource,
            occurrence.copy(done = true, percentComplete = 100, status = "COMPLETED", completedAt = now),
            now,
        )!!

        assertEquals(2, patched.split("BEGIN:VTODO").size - 1)
        val masterText = patched.substringAfter("BEGIN:VTODO").substringBefore("END:VTODO")
        val overrideText = patched.substringAfter("END:VTODO").substringAfter("BEGIN:VTODO").substringBefore("END:VTODO")
        assertTrue(masterText, masterText.contains("RRULE:FREQ=WEEKLY;BYDAY=TU"))
        assertTrue(masterText, masterText.contains("STATUS:NEEDS-ACTION"))
        assertTrue(masterText, masterText.contains("PERCENT-COMPLETE:20"))
        assertTrue(masterText, masterText.contains("BEGIN:VALARM"))
        assertTrue(overrideText, overrideText.contains("RECURRENCE-ID;VALUE=DATE:20260310"))
        assertTrue(overrideText, overrideText.contains("STATUS:COMPLETED"))
        assertTrue(overrideText, overrideText.contains("PERCENT-COMPLETE:100"))
        assertFalse(overrideText, overrideText.contains("BEGIN:VALARM"))
    }

    @Test
    fun `all-scope title edit from completed occurrence does not complete master`() {
        val resource = ics(
            "BEGIN:VCALENDAR", "VERSION:2.0",
            "BEGIN:VTODO", "UID:gym", "DTSTART;VALUE=DATE:20260303", "DUE;VALUE=DATE:20260303",
            "RRULE:FREQ=WEEKLY;BYDAY=TU", "SUMMARY:Exercise", "STATUS:NEEDS-ACTION", "END:VTODO",
            "BEGIN:VTODO", "UID:gym", "DTSTART;VALUE=DATE:20260310", "DUE;VALUE=DATE:20260310",
            "RECURRENCE-ID;VALUE=DATE:20260310", "SUMMARY:Exercise", "PERCENT-COMPLETE:100",
            "STATUS:COMPLETED", "COMPLETED:20260310T180400Z", "END:VTODO", "END:VCALENDAR",
        )
        val completed = mapper.parse(
            resource, "cal", 1L, "gym.ics",
            windowStart = LocalDate.of(2026, 3, 10), windowEnd = LocalDate.of(2026, 3, 10),
        ).tasks.single()

        val patched = patcher.patchTask(
            resource,
            completed.copy(title = "New series title", recurrenceScope = RecurrenceEditScope.All),
            now,
        )!!
        val masterText = patched.substringAfter("BEGIN:VTODO").substringBefore("END:VTODO")
        assertTrue(masterText, masterText.contains("SUMMARY:New series title"))
        assertTrue(masterText, masterText.contains("STATUS:NEEDS-ACTION"))
        assertFalse(masterText, masterText.contains("STATUS:COMPLETED"))
        assertFalse(masterText, masterText.contains("COMPLETED:"))
    }

    @Test
    fun `an edit preserves every property Calino does not model`() {
        val event = eventFrom(foreignResource, "ours")
        val patched = patcher.patchEvents(foreignResource, listOf(event.copy(title = "Edited")), now)!!

        assertTrue(patched, patched.contains("SUMMARY:Edited"))
        // Nothing else may move.
        assertTrue(patched, patched.contains("ORGANIZER;CN=Boss:mailto:boss@example.com"))
        assertTrue(patched, patched.contains("CLASS:CONFIDENTIAL"))
        assertTrue(patched, patched.contains("X-CUSTOM-THING;X-PARAM=7:preserve this"))
        assertTrue(patched, patched.contains("BEGIN:VALARM"))
        assertTrue(patched, patched.contains("TRIGGER:-PT15M"))
        assertTrue(patched, patched.contains("BEGIN:VTIMEZONE"))
        assertTrue(patched, patched.contains("TZID:Europe/Copenhagen"))
    }

    @Test
    fun `the origin server's PRODID is not replaced with ours`() {
        val event = eventFrom(foreignResource, "ours")
        val patched = patcher.patchEvents(foreignResource, listOf(event.copy(title = "Edited")), now)!!

        assertTrue(patched, patched.contains("PRODID:-//Foreign Client//EN"))
        assertFalse(patched, patched.contains("Calino Android"))
    }

    @Test
    fun `a component belonging to somebody else is left alone`() {
        val shared = foreignResource.replace(
            "END:VCALENDAR\r\n",
            ics(
                "BEGIN:VEVENT",
                "UID:not-ours",
                "DTSTAMP:20260101T000000Z",
                "DTSTART:20260401T090000Z",
                "SUMMARY:Somebody else",
                "END:VEVENT",
                "END:VCALENDAR",
            ),
        )
        val event = eventFrom(shared, "ours")
        val patched = patcher.patchEvents(shared, listOf(event.copy(title = "Edited")), now)!!

        assertTrue(patched, patched.contains("UID:not-ours"))
        assertTrue(patched, patched.contains("SUMMARY:Somebody else"))
        assertEquals(
            "both events survive",
            2,
            mapper.parse(patched, "cal", 1L, "https://x/e.ics").events.size,
        )
    }

    @Test
    fun `malformed input returns null so the caller can rebuild`() {
        val event = eventFrom(foreignResource, "ours")
        assertNull(patcher.patchEvents("not a calendar at all", listOf(event), now))
        assertNull(patcher.patchEvents("", listOf(event), now))
        assertNull(patcher.patchEvents("   ", listOf(event), now))
    }

    @Test
    fun `two concatenated calendars are refused rather than guessed at`() {
        // There is no way to tell which block the edit belongs in, and choosing
        // wrong would move the event between objects. A rebuild is safer.
        val doubled = foreignResource + foreignResource.replace("UID:ours", "UID:second")
        val event = eventFrom(foreignResource, "ours")
        assertNull(patcher.patchEvents(doubled, listOf(event), now))
    }

    @Test
    fun `an event with no UID cannot be patched`() {
        val event = eventFrom(foreignResource, "ours").copy(uid = null)
        assertNull(patcher.patchEvents(foreignResource, listOf(event), now))
    }

    @Test
    fun `removing the only component reports the resource as emptied`() {
        val removal = patcher.removeComponent(foreignResource, "ours")
        assertEquals(ICalPatcher.PatchRemoval.Emptied, removal)
    }

    @Test
    fun `removing one of two components rewrites the resource`() {
        val shared = foreignResource.replace(
            "END:VCALENDAR\r\n",
            ics(
                "BEGIN:VEVENT",
                "UID:not-ours",
                "DTSTAMP:20260101T000000Z",
                "DTSTART:20260401T090000Z",
                "SUMMARY:Somebody else",
                "END:VEVENT",
                "END:VCALENDAR",
            ),
        )
        val removal = patcher.removeComponent(shared, "ours") as ICalPatcher.PatchRemoval.Patched

        assertFalse(removal.ics, removal.ics.contains("UID:ours"))
        assertTrue(removal.ics, removal.ics.contains("UID:not-ours"))
        // The VTIMEZONE is not a Calino component and does not count as content,
        // but it also must not be dragged out by the removal.
        assertTrue(removal.ics, removal.ics.contains("BEGIN:VTIMEZONE"))
    }

    @Test
    fun `removing a component that is not there returns null`() {
        assertNull(patcher.removeComponent(foreignResource, "never-existed"))
    }

    @Test
    fun `removing a component is scoped by kind when UIDs are shared`() {
        val shared = foreignResource.replace(
            "END:VCALENDAR\r\n",
            ics(
                "BEGIN:VTODO",
                "UID:ours",
                "SUMMARY:Same UID task",
                "END:VTODO",
                "END:VCALENDAR",
            ),
        )

        val removal = patcher.removeComponent(shared, "ours", "VEVENT")
        assertTrue(removal is ICalPatcher.PatchRemoval.Patched)
        assertFalse((removal as ICalPatcher.PatchRemoval.Patched).ics.contains("SUMMARY:Original"))
        assertTrue(removal.ics.contains("SUMMARY:Same UID task"))
    }

    @Test
    fun `recurring VTODOs are rejected as unsafe standalone writes`() {
        val recurring = ics(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "BEGIN:VTODO",
            "UID:task-series",
            "DTSTART;VALUE=DATE:20260305",
            "DUE;VALUE=DATE:20260305",
            "RRULE:FREQ=DAILY;COUNT=2",
            "SUMMARY:Master task",
            "END:VTODO",
            "BEGIN:VTODO",
            "UID:task-series",
            "RECURRENCE-ID;VALUE=DATE:20260306",
            "DUE;VALUE=DATE:20260306",
            "SUMMARY:Task exception",
            "END:VTODO",
            "END:VCALENDAR",
        )
        val standalone = recurring.replace(
            "RRULE:FREQ=DAILY;COUNT=2\r\n",
            "",
        ).substringBefore("BEGIN:VTODO\r\nUID:task-series\r\nRECURRENCE-ID") +
            "END:VCALENDAR\r\n"

        assertTrue(patcher.taskRequiresGroupWrite(recurring, "task-series") == true)
        assertTrue(patcher.taskRequiresGroupWrite(standalone, "task-series") == false)
    }

    @Test
    fun `a new component is added to a resource that does not carry it yet`() {
        // The move and group-write paths write a component into a resource that
        // may not hold it, so an absent match must add rather than fail.
        val event = eventFrom(foreignResource, "ours").copy(uid = "fresh", id = "fresh", title = "New")
        val patched = patcher.patchEvents(foreignResource, listOf(event), now)!!

        assertTrue(patched, patched.contains("UID:fresh"))
        assertTrue(patched, patched.contains("UID:ours"))
    }

    @Test
    fun `a queued stale update rebases local fields without resurrecting old foreign fields`() {
        val baseEvent = eventFrom(foreignResource, "ours")
        val local = patcher.patchEvents(
            foreignResource,
            listOf(baseEvent.copy(title = "Local title", location = "Local room")),
            now,
        )!!
        val current = foreignResource
            .replace("SUMMARY:Original", "SUMMARY:Remote title")
            .replace("CN=Boss", "CN=New boss")
            .replace("preserve this", "remote-only update")

        val rebased = patcher.rebaseResource(
            currentIcs = current,
            localIcs = local,
            baseIcs = foreignResource,
            component = "VEVENT",
            uids = setOf("ours"),
        )!!

        assertTrue(rebased.contains("SUMMARY:Local title"))
        assertTrue(rebased.contains("LOCATION:Local room"))
        assertTrue(rebased.contains("ORGANIZER;CN=New boss:mailto:boss@example.com"))
        assertTrue(rebased.contains("X-CUSTOM-THING;X-PARAM=7:remote-only update"))
        assertTrue(rebased.contains("BEGIN:VALARM"))
    }

    @Test
    fun `a travel time edit rebases without overwriting another remote X property`() {
        val base = foreignResource.replace(
            "X-CUSTOM-THING;X-PARAM=7:preserve this",
            "X-APPLE-TRAVEL-DURATION:PT15M\r\nX-CUSTOM-THING;X-PARAM=7:preserve this",
        )
        val event = eventFrom(base, "ours")
        val local = patcher.patchEvents(base, listOf(event.copy(travelTimeMinutes = 30)), now)!!
        val current = base.replace("preserve this", "remote-only update")

        val rebased = patcher.rebaseResource(current, local, base, "VEVENT", setOf("ours"))!!

        assertTrue(rebased.contains("X-APPLE-TRAVEL-DURATION:PT30M"))
        assertTrue(rebased.contains("X-CUSTOM-THING;X-PARAM=7:remote-only update"))
    }

    @Test
    fun `an edit preserves a foreign CONFERENCE line with its required VALUE=URI`() {
        val conference = "CONFERENCE;VALUE=URI;FEATURE=AUDIO,VIDEO;LABEL=Room 4:https://meet.jit.si/calino-room"
        val base = foreignResource.replace(
            "X-CUSTOM-THING;X-PARAM=7:preserve this",
            "$conference\r\nX-CUSTOM-THING;X-PARAM=7:preserve this",
        )
        val event = eventFrom(base, "ours")
        assertEquals("https://meet.jit.si/calino-room", event.conferenceUrl)

        val patched = patcher.patchEvents(base, listOf(event.copy(title = "Renamed")), now)!!

        assertTrue(patched.contains("SUMMARY:Renamed"))
        // The patcher re-serializes, so parameter order may change; every
        // parameter, including RFC 7986's mandatory VALUE=URI, must survive.
        val line = patched.replace("\r\n ", "").lines().single { it.startsWith("CONFERENCE") }
        listOf("VALUE=URI", "FEATURE=AUDIO,VIDEO", "LABEL=Room 4").forEach { param ->
            assertTrue(line, line.contains(";$param"))
        }
        assertTrue(line, line.endsWith(":https://meet.jit.si/calino-room"))
    }

    @Test
    fun `an edit preserves URI and inline ATTACH lines with every parameter`() {
        val nextcloud = "ATTACH;FMTTYPE=application/pdf;FILENAME=/Calendar/Agenda.pdf;X-NC-FILE-ID=4711;X-NC-HAS-PREVIEW=false:https://cloud.example.org/f/4711"
        val plain = "ATTACH:https://example.org/brief.html"
        val inline = "ATTACH;FMTTYPE=text/plain;ENCODING=BASE64;VALUE=BINARY;X-FILENAME=note.txt:aGVsbG8gY2FsaW5v"
        val base = foreignResource.replace(
            "X-CUSTOM-THING;X-PARAM=7:preserve this",
            "$nextcloud\r\n$plain\r\n$inline\r\nX-CUSTOM-THING;X-PARAM=7:preserve this",
        )
        val event = eventFrom(base, "ours")

        val patched = patcher.patchEvents(base, listOf(event.copy(title = "Renamed")), now)!!

        assertTrue(patched.contains("SUMMARY:Renamed"))
        val lines = patched.replace("\r\n ", "").lines().filter { it.startsWith("ATTACH") }
        assertEquals(3, lines.size)
        listOf(nextcloud, plain, inline).forEach { original ->
            val value = original.substringAfter(":")
            val params = original.substringBefore(":").split(";").drop(1)
            val line = lines.single { it.endsWith(":$value") }
            params.forEach { param -> assertTrue(line, line.contains(";$param")) }
        }
    }

    private val attachResource = foreignResource.replace(
        "X-CUSTOM-THING;X-PARAM=7:preserve this",
        "ATTACH;FMTTYPE=application/pdf;FILENAME=/Talk/Agenda.pdf;X-NC-FILE-ID=4711:https://cloud.example.org/f/4711\r\n" +
            "ATTACH:https://example.org/docs/brief%20v2.html\r\n" +
            "ATTACH;FMTTYPE=text/plain;ENCODING=BASE64;VALUE=BINARY;X-FILENAME=note.txt:aGVsbG8gY2FsaW5v\r\n" +
            "X-CUSTOM-THING;X-PARAM=7:preserve this",
    )

    @Test
    fun `attachments map links and inline data with Nextcloud and X- file names`() {
        val event = eventFrom(attachResource, "ours")

        assertEquals(3, event.attachments.size)
        val (nextcloud, plain, inline) = event.attachments
        assertEquals("https://cloud.example.org/f/4711", nextcloud.uri)
        assertEquals("Agenda.pdf", nextcloud.fileName)
        assertEquals("application/pdf", nextcloud.mimeType)
        assertEquals("brief v2.html", plain.fileName)
        assertNull(inline.uri)
        assertEquals("note.txt", inline.fileName)
        assertEquals(12, inline.sizeBytes)
        assertEquals(
            "hello calino",
            String(calino.malinov.ski.data.caldav.readInlineAttachment(attachResource, "ours", inline)!!),
        )
    }

    @Test
    fun `an unedited attachment list leaves every ATTACH alone even when the model has none`() {
        val event = eventFrom(attachResource, "ours").copy(attachments = emptyList(), title = "Renamed")

        val patched = patcher.patchEvents(attachResource, listOf(event), now)!!

        assertEquals(3, patched.lines().count { it.startsWith("ATTACH") })
    }

    @Test
    fun `an attachment edit removes and adds links and keeps a listed inline file`() {
        val event = eventFrom(attachResource, "ours")
        val edited = event.copy(
            attachmentsEdited = true,
            attachments = event.attachments.drop(1) +
                calino.malinov.ski.data.model.EventAttachment(uri = "https://example.org/new.pdf"),
        )

        val patched = patcher.patchEvents(attachResource, listOf(edited), now)!!
        val lines = patched.replace("\r\n ", "").lines().filter { it.startsWith("ATTACH") }

        assertFalse(patched.contains("cloud.example.org/f/4711"))
        assertTrue(lines.any { it.endsWith(":https://example.org/docs/brief%20v2.html") })
        assertTrue(lines.any { it.endsWith(":https://example.org/new.pdf") })
        assertTrue(lines.any { it.endsWith(":aGVsbG8gY2FsaW5v") })
        assertEquals(3, lines.size)
    }

    @Test
    fun `removing an inline file drops only that ATTACH`() {
        val event = eventFrom(attachResource, "ours")
        val edited = event.copy(attachmentsEdited = true, attachments = event.attachments.filter { it.uri != null })

        val patched = patcher.patchEvents(attachResource, listOf(edited), now)!!
        val lines = patched.replace("\r\n ", "").lines().filter { it.startsWith("ATTACH") }

        assertFalse(patched.contains("aGVsbG8gY2FsaW5v"))
        assertTrue(lines.any { it.contains("X-NC-FILE-ID") })
        assertEquals(2, lines.size)
    }

    @Test
    fun `a picked file is embedded as base64 with its name and type`() {
        val event = eventFrom(attachResource, "ours")
        val picked = calino.malinov.ski.data.model.EventAttachment(
            fileName = "plan.txt",
            mimeType = "text/plain",
            sizeBytes = 4,
            data = "plan".toByteArray(),
        )
        val edited = event.copy(attachmentsEdited = true, attachments = event.attachments + picked)

        val patched = patcher.patchEvents(attachResource, listOf(edited), now)!!
        val reread = eventFrom(patched, "ours")

        assertEquals(4, reread.attachments.size)
        val added = reread.attachments.last()
        assertEquals("plan.txt", added.fileName)
        assertEquals("text/plain", added.mimeType)
        assertEquals(
            "plan",
            String(calino.malinov.ski.data.caldav.readInlineAttachment(patched, "ours", added)!!),
        )
        // The file that was already there is untouched.
        assertTrue(patched.replace("\r\n ", "").contains(":aGVsbG8gY2FsaW5v"))
    }

    @Test
    fun `a rebase keeps a link the server added while applying the local add and remove`() {
        val base = attachResource
        val event = eventFrom(base, "ours")
        val local = patcher.patchEvents(
            base,
            listOf(
                event.copy(
                    attachmentsEdited = true,
                    attachments = event.attachments.drop(1) +
                        calino.malinov.ski.data.model.EventAttachment(uri = "https://example.org/local.pdf"),
                ),
            ),
            now,
        )!!
        val current = base.replace(
            "X-CUSTOM-THING;X-PARAM=7:preserve this",
            "ATTACH:https://example.org/remote.pdf\r\nX-CUSTOM-THING;X-PARAM=7:preserve this",
        )

        val rebased = patcher.rebaseResource(current, local, base, "VEVENT", setOf("ours"))!!
        val uris = eventFrom(rebased, "ours").attachments.mapNotNull { it.uri }.toSet()

        assertEquals(
            setOf(
                "https://example.org/docs/brief%20v2.html",
                "https://example.org/remote.pdf",
                "https://example.org/local.pdf",
            ),
            uris,
        )
        assertTrue(rebased.contains("aGVsbG8gY2FsaW5v"))
    }

    // --- VALARM ---------------------------------------------------------------

    /**
     * The same resource, but with an alarm Calino genuinely cannot author.
     *
     * `foreignResource`'s own alarm is a plain `DISPLAY` / `-PT15M`, which is
     * exactly the shape Calino now owns -- so it no longer proves anything
     * about passthrough. An EMAIL alarm with a repeat does.
     */
    private val alienAlarmResource = ics(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Foreign Client//EN",
        "BEGIN:VEVENT",
        "UID:ours",
        "DTSTAMP:20260101T000000Z",
        "DTSTART:20260305T090000Z",
        "DTEND:20260305T100000Z",
        "SUMMARY:Original",
        "BEGIN:VALARM",
        "ACTION:EMAIL",
        "TRIGGER;RELATED=END:-PT45M",
        "DESCRIPTION:body",
        "SUMMARY:subject",
        "ATTENDEE:mailto:ada@example.com",
        "REPEAT:2",
        "DURATION:PT5M",
        "END:VALARM",
        "END:VEVENT",
        "END:VCALENDAR",
    )

    @Test
    fun `an edit leaves an alarm Calino did not author alone`() {
        val event = eventFrom(alienAlarmResource, "ours")
        assertTrue(event.reminders.isEmpty())

        val patched = patcher.patchEvents(alienAlarmResource, listOf(event.copy(title = "Edited")), now)!!

        assertTrue(patched, patched.contains("SUMMARY:Edited"))
        assertTrue(patched, patched.contains("ACTION:EMAIL"))
        assertTrue(patched, patched.contains("TRIGGER;RELATED=END:-PT45M"))
        assertTrue(patched, patched.contains("ATTENDEE:mailto:ada@example.com"))
        assertTrue(patched, patched.contains("REPEAT:2"))
    }

    @Test
    fun `a new reminder is added beside a foreign alarm, not instead of it`() {
        val event = eventFrom(alienAlarmResource, "ours")
        val patched = patcher.patchEvents(
            alienAlarmResource,
            listOf(event.copy(reminders = listOf(Reminder(10)))),
            now,
        )!!

        assertEquals(2, patched.split("BEGIN:VALARM").size - 1)
        assertTrue(patched, patched.contains("ACTION:EMAIL"))
        assertTrue(patched, patched.contains("TRIGGER:-PT10M"))
        assertEquals(listOf(Reminder(10)), eventFrom(patched, "ours").reminders)
    }

    @Test
    fun `clearing the reminders removes only Calino's alarm`() {
        val seeded = patcher.patchEvents(
            alienAlarmResource,
            listOf(eventFrom(alienAlarmResource, "ours").copy(reminders = listOf(Reminder(10)))),
            now,
        )!!

        val cleared = patcher.patchEvents(
            seeded,
            listOf(eventFrom(seeded, "ours").copy(reminders = emptyList())),
            now,
        )!!

        assertEquals(1, cleared.split("BEGIN:VALARM").size - 1)
        assertTrue(cleared, cleared.contains("ACTION:EMAIL"))
        assertFalse(cleared, cleared.contains("TRIGGER:-PT10M"))
        assertTrue(eventFrom(cleared, "ours").reminders.isEmpty())
    }

    @Test
    fun `a rebase keeps a reminder the local edit set`() {
        val baseEvent = eventFrom(foreignResource, "ours")
        val local = patcher.patchEvents(
            foreignResource,
            listOf(baseEvent.copy(reminders = listOf(Reminder(45)))),
            now,
        )!!
        // The server moved the summary in the meantime, but not the alarm.
        val current = foreignResource.replace("SUMMARY:Original", "SUMMARY:Remote title")

        val rebased = patcher.rebaseResource(
            currentIcs = current,
            localIcs = local,
            baseIcs = foreignResource,
            component = "VEVENT",
            uids = setOf("ours"),
        )!!

        assertEquals(listOf(Reminder(45)), eventFrom(rebased, "ours").reminders)
        assertFalse(rebased, rebased.contains("TRIGGER:-PT15M"))
    }

    @Test
    fun `a rebase keeps the server's alarm when the local edit left it alone`() {
        val baseEvent = eventFrom(foreignResource, "ours")
        val local = patcher.patchEvents(
            foreignResource,
            listOf(baseEvent.copy(title = "Local title")),
            now,
        )!!
        val current = foreignResource.replace("TRIGGER:-PT15M", "TRIGGER:-PT90M")

        val rebased = patcher.rebaseResource(
            currentIcs = current,
            localIcs = local,
            baseIcs = foreignResource,
            component = "VEVENT",
            uids = setOf("ours"),
        )!!

        assertTrue(rebased, rebased.contains("SUMMARY:Local title"))
        assertEquals(listOf(Reminder(90)), eventFrom(rebased, "ours").reminders)
    }

    @Test
    fun `an edit that does not touch the reminders leaves our own alarm untouched`() {
        // foreignResource's alarm is Calino-shaped, so Calino now owns it. That
        // does not license rewriting it: an unrelated edit must leave its
        // DESCRIPTION -- and anything else on it -- exactly where it was.
        val event = eventFrom(foreignResource, "ours")
        assertEquals(listOf(Reminder(15)), event.reminders)

        val patched = patcher.patchEvents(foreignResource, listOf(event.copy(title = "Edited")), now)!!

        assertTrue(patched, patched.contains("TRIGGER:-PT15M"))
        assertTrue(patched, patched.contains("DESCRIPTION:soon"))
        assertEquals(1, patched.split("BEGIN:VALARM").size - 1)
    }
}
