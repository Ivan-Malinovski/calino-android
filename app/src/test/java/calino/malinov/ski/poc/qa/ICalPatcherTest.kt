package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.ICalMapper
import calino.malinov.ski.poc.data.caldav.ICalPatcher
import calino.malinov.ski.poc.data.caldav.ICalWriter
import java.time.Instant
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
}
