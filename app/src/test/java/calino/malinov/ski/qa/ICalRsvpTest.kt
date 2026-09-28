package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.ICalRsvp
import calino.malinov.ski.data.caldav.ICalPatcher
import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.caldav.ICalWriter
import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.EventOrganizer
import calino.malinov.ski.data.model.RecurrenceEditScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ICalRsvpTest {
    private val now = Instant.parse("2026-05-18T08:00:00Z")
    private val mapper = ICalMapper(ZoneId.of("UTC"))
    private val base = listOf(
        "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Other//EN",
        "BEGIN:VEVENT", "UID:invite", "DTSTAMP:20260501T000000Z",
        "DTSTART:20260518T100000Z", "DTEND:20260518T110000Z", "SUMMARY:Meeting",
        "ORGANIZER;CN=Alice:mailto:alice@example.com",
        "ATTENDEE;CN=Bob;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PARTSTAT=NEEDS-ACTION;X-OTHER=keep:mailto:bob@example.com",
        "ATTENDEE;CN=Carol;PARTSTAT=TENTATIVE:mailto:carol@example.com",
        "X-FOREIGN:untouched", "END:VEVENT", "END:VCALENDAR", "",
    ).joinToString("\r\n")

    private fun event(ics: String = base) = mapper.parse(
        ics, "calendar", 1, "https://example.test/invite.ics", "\"one\"",
    ).events.single()

    @Test fun `new invitation writes organizer and an actionable attendee`() {
        val component = ICalWriter(ZoneId.of("UTC")).writeEvent(
            CalEvent(
                id = "new", title = "Meeting", color = 1,
                start = java.time.LocalDateTime.of(2026, 5, 18, 10, 0), durationMinutes = 60,
                calendarId = "calendar", organizer = EventOrganizer("Alice", "alice@example.com"),
                attendees = listOf(Attendee("Bob", "bob@example.com")),
            ), now = now,
        )
        assertEquals("alice@example.com", component.organizer.email)
        assertEquals("NEEDS-ACTION", component.attendees.single().participationStatus.value)
        assertEquals(true, component.attendees.single().rsvp)
    }

    @Test fun `response changes only own status and preserves foreign parameters`() {
        val patched = ICalRsvp.patch(base, event(), "BOB@example.com", "ACCEPTED", "NEEDS-ACTION", RecurrenceEditScope.All, now)
        assertNotNull(patched)
        assertTrue(patched!!.contains("PARTSTAT=ACCEPTED"))
        assertTrue(patched.contains("X-OTHER=keep"))
        assertEquals("TENTATIVE", event(patched).attendees.first { it.email == "carol@example.com" }.participationStatus)
        assertTrue(patched.contains("X-FOREIGN:untouched"))
        assertTrue(patched.contains("ORGANIZER;CN=Alice:mailto:alice@example.com"))
        assertEquals("ACCEPTED", event(patched).attendees.first { it.email == "bob@example.com" }.participationStatus)
    }

    @Test fun `stale response refuses changed own status`() {
        val remote = base.replace("PARTSTAT=NEEDS-ACTION", "PARTSTAT=DECLINED")
        assertNull(ICalRsvp.patch(remote, event(), "bob@example.com", "ACCEPTED", "NEEDS-ACTION", RecurrenceEditScope.All, now))
    }

    @Test fun `queued rebase keeps another attendee response`() {
        val local = ICalRsvp.patch(base, event(), "bob@example.com", "ACCEPTED", "NEEDS-ACTION", RecurrenceEditScope.All, now)!!
        val remote = base.replace("PARTSTAT=TENTATIVE", "PARTSTAT=ACCEPTED")
        val rebased = ICalPatcher().rebaseResource(remote, local, base, "VEVENT", setOf("invite"))!!
        val attendees = event(rebased).attendees.associateBy { it.email }
        assertEquals("ACCEPTED", attendees["bob@example.com"]?.participationStatus)
        assertEquals("ACCEPTED", attendees["carol@example.com"]?.participationStatus)
    }

    @Test fun `queued rebase refuses conflicting response to same attendee`() {
        val local = ICalRsvp.patch(base, event(), "bob@example.com", "ACCEPTED", "NEEDS-ACTION", RecurrenceEditScope.All, now)!!
        val remote = base.replace("PARTSTAT=NEEDS-ACTION", "PARTSTAT=DECLINED")
        assertNull(ICalPatcher().rebaseResource(remote, local, base, "VEVENT", setOf("invite")))
    }

    @Test fun `one occurrence response creates a detached event`() {
        val series = base.replace("SUMMARY:Meeting", "RRULE:FREQ=DAILY;COUNT=3\r\nSUMMARY:Meeting")
        val selected = mapper.parse(series, "calendar", 1, "https://example.test/invite.ics", "\"one\"",
            LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 20)).events.first { it.recurrenceId == Instant.parse("2026-05-19T10:00:00Z") }
        val patched = ICalRsvp.patch(series, selected, "bob@example.com", "ACCEPTED", "NEEDS-ACTION", RecurrenceEditScope.This, now)
        assertNotNull(patched)
        assertTrue(patched!!.contains("RECURRENCE-ID:20260519T100000Z"))
        assertEquals(2, "BEGIN:VEVENT".toRegex().findAll(patched).count())
        val mapped = mapper.parse(patched, "calendar", 1, "https://example.test/invite.ics", "\"two\"",
            LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 20)).events
        assertEquals("NEEDS-ACTION", mapped.first { it.recurrenceId == Instant.parse("2026-05-18T10:00:00Z") }.attendees.first { it.email == "bob@example.com" }.participationStatus)
        assertEquals("ACCEPTED", mapped.first { it.recurrenceId == Instant.parse("2026-05-19T10:00:00Z") }.attendees.first { it.email == "bob@example.com" }.participationStatus)
    }
}
