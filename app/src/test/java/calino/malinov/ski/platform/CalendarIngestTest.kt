package calino.malinov.ski.platform

import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping half of inbound ingest: a provider row turned back into a
 * [calino.malinov.ski.data.model.NewEvent].
 *
 * These are the assertions that matter for data loss. The provider has no
 * column for an attendee, a category or a recurrence rule, so the only thing
 * standing between a foreign edit and their destruction is that the mapping
 * carries them across from the record Calino already holds.
 */
class CalendarIngestTest {

    private val zone = ZoneId.of("Europe/Copenhagen")

    private fun change(
        eventId: String? = "event-1",
        title: String = "Renamed elsewhere",
        startMillis: Long = utc(2026, 5, 19, 12, 0),
        endMillis: Long = utc(2026, 5, 19, 13, 0),
        allDay: Boolean = false,
        free: Boolean = false,
        description: String? = null,
        location: String? = null,
        reminderMinutes: List<Int> = emptyList(),
        deleted: Boolean = false,
    ) = CalendarIngest.Change(
        rowId = 7,
        calendarId = "https://dav.invalid/cal/home/",
        eventId = eventId,
        deleted = deleted,
        title = title,
        description = description,
        location = location,
        startMillis = startMillis,
        endMillis = endMillis,
        allDay = allDay,
        free = free,
        reminderMinutes = reminderMinutes,
    )

    private fun existing(
        recurrenceId: Instant? = null,
        recurrence: String? = null,
    ) = CalEvent(
        id = "event-1",
        title = "Standup",
        color = 0xFF112233,
        start = LocalDateTime.of(2026, 5, 19, 9, 0),
        durationMinutes = 30,
        calendarId = "https://dav.invalid/cal/home/",
        recurrence = recurrence,
        notes = "Original notes",
        attendees = listOf(Attendee("ada@example.invalid", "Ada")),
        categories = listOf("work"),
        travelTimeMinutes = 15,
        relatedTo = listOf("task-4"),
        url = "https://example.invalid/agenda",
        uid = "uid-1",
        href = "https://dav.invalid/cal/home/uid-1.ics",
        etag = "\"abc\"",
        recurrenceId = recurrenceId,
        sequence = 3,
    )

    private fun utc(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `an edit keeps everything the provider cannot represent`() {
        val result = CalendarIngest.editOf(existing(recurrence = "FREQ=WEEKLY"), change(), zone)

        assertEquals("FREQ=WEEKLY", result.recurrence)
        assertEquals(listOf(Attendee("ada@example.invalid", "Ada")), result.attendees)
        assertEquals(listOf("work"), result.categories)
        assertEquals(15, result.travelTimeMinutes)
        assertEquals(listOf("task-4"), result.relatedTo)
        assertEquals("https://example.invalid/agenda", result.url)
        assertEquals("uid-1", result.uid)
        assertEquals("https://dav.invalid/cal/home/uid-1.ics", result.href)
        assertEquals("\"abc\"", result.etag)
        assertEquals(3, result.sequence)
        assertEquals(0xFF112233, result.color)
    }

    @Test
    fun `an edit takes the fields the provider does hold`() {
        val result = CalendarIngest.editOf(
            existing(),
            change(
                title = "Renamed elsewhere",
                description = "New notes",
                location = "Room 2",
                free = true,
                reminderMinutes = listOf(10, 30),
            ),
            zone,
        )

        assertEquals("Renamed elsewhere", result.title)
        assertEquals("New notes", result.notes)
        assertEquals("Room 2", result.location)
        assertEquals(Availability.Free, result.availability)
        assertEquals(listOf(Reminder(10), Reminder(30)), result.reminders)
    }

    @Test
    fun `a timed row comes back as wall clock in the device zone`() {
        // 12:00 UTC is 14:00 in Copenhagen in May.
        val result = CalendarIngest.editOf(existing(), change(), zone)

        assertEquals(LocalDate.of(2026, 5, 19), result.date)
        assertEquals(LocalTime.of(14, 0), result.startTime)
        assertEquals(60, result.durationMinutes)
        assertTrue(!result.allDay)
        assertNull(result.endDate)
    }

    @Test
    fun `an all-day row keeps its UTC midnight anchor and inclusive end`() {
        val result = CalendarIngest.editOf(
            existing(),
            change(
                allDay = true,
                startMillis = utc(2026, 5, 19, 0, 0),
                // Exclusive on the wire: three days, ending the 21st.
                endMillis = utc(2026, 5, 22, 0, 0),
            ),
            zone,
        )

        assertEquals(LocalDate.of(2026, 5, 19), result.date)
        assertEquals(LocalDate.of(2026, 5, 21), result.endDate)
        assertNull(result.startTime)
        assertTrue(result.allDay)
    }

    @Test
    fun `a single all-day row has no end date`() {
        val result = CalendarIngest.editOf(
            existing(),
            change(
                allDay = true,
                startMillis = utc(2026, 5, 19, 0, 0),
                endMillis = utc(2026, 5, 20, 0, 0),
            ),
            zone,
        )

        assertNull(result.endDate)
    }

    @Test
    fun `an occurrence edit reaches that occurrence only`() {
        val result = CalendarIngest.editOf(
            existing(recurrenceId = Instant.parse("2026-05-19T07:00:00Z")),
            change(),
            zone,
        )

        assertEquals(RecurrenceEditScope.This, result.recurrenceScope)
        assertEquals(Instant.parse("2026-05-19T07:00:00Z"), result.recurrenceId)
    }

    @Test
    fun `a one-off edit is not narrowed to an occurrence`() {
        assertEquals(RecurrenceEditScope.All, CalendarIngest.editOf(existing(), change(), zone).recurrenceScope)
    }

    @Test
    fun `an edit never claims the recurrence rule changed`() {
        // The provider was never given the rule, so nothing done there can
        // have changed it -- and claiming otherwise would rewrite the series.
        val result = CalendarIngest.editOf(existing(recurrence = "FREQ=DAILY"), change(), zone)

        assertTrue(!result.recurrenceChanged)
    }

    @Test
    fun `an emptied title falls back rather than blanking the record`() {
        assertEquals("Standup", CalendarIngest.editOf(existing(), change(title = ""), zone).title)
    }

    @Test
    fun `a foreign insert lands in the calendar it was created in`() {
        val result = CalendarIngest.insertOf(change(eventId = null, title = "Dentist"), zone)

        assertEquals("Dentist", result.title)
        assertEquals("https://dav.invalid/cal/home/", result.calendarId)
        assertNull(result.uid)
        assertNull(result.href)
        assertEquals(LocalTime.of(14, 0), result.startTime)
    }

    @Test
    fun `a foreign insert with no title still has one`() {
        assertEquals("(no title)", CalendarIngest.insertOf(change(eventId = null, title = ""), zone).title)
    }

    @Test
    fun `a duration stands in for a missing end`() {
        assertEquals(90 * 60_000L, CalendarIngest.durationMillis("PT1H30M"))
        assertEquals(7 * 86_400_000L, CalendarIngest.durationMillis("P1W"))
        assertEquals(86_400_000L + 3_600_000L, CalendarIngest.durationMillis("P1DT1H"))
        assertEquals(45_000L, CalendarIngest.durationMillis("PT45S"))
    }

    @Test
    fun `an unusable duration is not guessed at`() {
        assertNull(CalendarIngest.durationMillis(null))
        assertNull(CalendarIngest.durationMillis(""))
        assertNull(CalendarIngest.durationMillis("PT0S"))
        assertNull(CalendarIngest.durationMillis("tomorrow"))
    }
}
