package calino.malinov.ski.data.ical

import calino.malinov.ski.data.model.CalEvent
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsInteropTest {
    private val zone = ZoneId.of("Europe/Copenhagen")

    @Test fun `imports events and reports unsupported components`() {
        val parsed = IcsInterop.parseEvents(IcsWithTask, zone)
        assertEquals(1, parsed.events.size)
        assertEquals("Planning", parsed.events.single().title)
        assertEquals(1, parsed.unsupportedComponents)
    }

    @Test fun `duplicate UID is skipped`() {
        val parsed = IcsInterop.parseEvents(IcsWithTask, zone)
        val existing = parsed.events.single().copy(id = "existing")
        assertEquals(setOf("event-1"), IcsInterop.withDuplicates(parsed, listOf(existing)).duplicateUids)
    }

    @Test fun `export retains recurrence and alarm`() {
        val event = IcsInterop.parseEvents(IcsWithTask, zone).events.single().copy(
            recurrence = "FREQ=WEEKLY;COUNT=3",
            reminders = listOf(calino.malinov.ski.data.model.Reminder(15)),
        )
        val text = IcsInterop.export(listOf(event), zone)
        assertTrue(text.contains("RRULE:FREQ=WEEKLY;COUNT=3"))
        assertTrue(text.contains("BEGIN:VALARM"))
    }

    @Test fun `imports event from exporter that omits UID`() {
        val parsed = IcsInterop.parseEvents(IcsWithoutUid, zone)

        assertEquals(1, parsed.events.size)
        assertEquals("Trial lesson", parsed.events.single().title)
        assertTrue(parsed.events.single().uid?.isNotBlank() == true)
    }

    @Test fun `rejects files without events`() {
        assertThrows(IllegalArgumentException::class.java) {
            IcsInterop.parseEvents("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n", zone)
        }
    }

    private companion object {
        val IcsWithoutUid = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//My italki calendar////
X-WR-CALDESC:Italki Lessons
X-WR-CALNAME:Italki Lessons
BEGIN:VEVENT
SUMMARY:Trial lesson
DTSTART;VALUE=DATE-TIME:20260921T170000Z
DTEND;VALUE=DATE-TIME:20260921T173000Z
LOCATION:italki.com
END:VEVENT
END:VCALENDAR
""".trimIndent().replace("\n", "\r\n")

        val IcsWithTask = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//test//EN
BEGIN:VEVENT
UID:event-1
DTSTART;VALUE=DATE:20260520
DTEND;VALUE=DATE:20260521
SUMMARY:Planning
END:VEVENT
BEGIN:VTODO
UID:task-1
SUMMARY:Ignored
END:VTODO
END:VCALENDAR
""".trimIndent().replace("\n", "\r\n")
    }
}
