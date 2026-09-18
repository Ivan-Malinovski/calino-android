package calino.malinov.ski.platform

import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.CalinoSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of the calendar projection. These assertions are the ones a
 * device test cannot make cheaply: a wrong hash is invisible until it churns
 * every alarm on the phone, and a wrong all-day anchor is invisible until an
 * event lands a day out in someone else's calendar app.
 */
class ProviderIdentityTest {

    private val zone = ZoneId.of("Europe/Copenhagen")
    private val now = Instant.parse("2026-05-18T09:00:00Z")
    private val calendarId = "https://dav.example/cal/home/"

    // ------------------------------------------------------------------ hash

    @Test
    fun `hash is stable for the same content`() {
        assertEquals(timed().hash, timed().hash)
    }

    @Test
    fun `hash follows a field that is written`() {
        assertNotEquals(timed().hash, timed(title = "Elsewhere").hash)
    }

    @Test
    fun `hash ignores the etag`() {
        // An ETag moves on every server write. Rewriting the row for that
        // would re-create its reminders and hand the provider a fresh alarm.
        val before = projectOne(event(etag = "\"one\""))
        val after = projectOne(event(etag = "\"two\""))
        assertEquals(before.hash, after.hash)
        assertNotEquals(before.etag, after.etag)
    }

    @Test
    fun `hash does not confuse adjacent fields`() {
        // Naive concatenation would hash ("ab", "c") and ("a", "bc") alike,
        // and two events would then share a row's worth of content.
        assertNotEquals(
            ProviderIdentity.hash("ab", "c"),
            ProviderIdentity.hash("a", "bc"),
        )
    }

    @Test
    fun `reminder order does not change the hash`() {
        val ascending = projectOne(event(reminders = listOf(10, 30)))
        val descending = projectOne(event(reminders = listOf(30, 10, 30)))
        assertEquals(ascending.hash, descending.hash)
        assertEquals(listOf(10, 30), descending.reminderMinutes)
    }

    // ------------------------------------------------------------- placement

    @Test
    fun `an all-day event is anchored at midnight UTC`() {
        val projected = projectOne(allDay(LocalDate.of(2026, 5, 18)))
        assertEquals("UTC", projected.timeZone)
        assertEquals(Instant.parse("2026-05-18T00:00:00Z").toEpochMilli(), projected.startMillis)
        // Calino stores the last covered day inclusively; DTEND is exclusive.
        assertEquals(Instant.parse("2026-05-19T00:00:00Z").toEpochMilli(), projected.endMillis)
    }

    @Test
    fun `a multi-day all-day span ends the day after its last day`() {
        val projected = projectOne(
            allDay(LocalDate.of(2026, 5, 18), endDate = LocalDate.of(2026, 5, 20)),
        )
        assertEquals(Instant.parse("2026-05-21T00:00:00Z").toEpochMilli(), projected.endMillis)
    }

    @Test
    fun `a timed event keeps its wall clock in the device zone`() {
        val projected = projectOne(event())
        assertEquals(zone.id, projected.timeZone)
        assertEquals(
            LocalDateTime.of(2026, 5, 18, 14, 0).atZone(zone).toInstant().toEpochMilli(),
            projected.startMillis,
        )
        assertEquals(projected.startMillis + 45 * 60_000L, projected.endMillis)
    }

    @Test
    fun `an event with no placement is not projected`() {
        val placeless = event().copy(start = null, allDay = false)
        assertNull(ProviderIdentity.projectEvent(placeless, zone))
    }

    // -------------------------------------------------------------- identity

    @Test
    fun `an occurrence carries its own id and recurrence id`() {
        val instant = Instant.parse("2026-05-25T12:00:00Z")
        val projected = projectOne(
            event(id = "uid-1@$instant").copy(uid = "uid-1", recurrenceId = instant),
        )
        // ICalMapper.occurrenceId is the identity; nothing here invents a
        // second one, which is what lets ingest resolve to one occurrence.
        assertEquals("uid-1@$instant", projected.id)
        assertEquals(instant.toString(), projected.recurrenceId)
    }

    // --------------------------------------------------------------- scoping

    @Test
    fun `only opted-in calendars are projected`() {
        val projection = ProviderIdentity.project(
            snapshot = snapshot(listOf(event())),
            accounts = listOf(account()),
            optedIn = emptySet(),
            zone = zone,
            now = now,
        )
        assertTrue(projection.calendars.isEmpty())
        assertTrue(projection.events.isEmpty())
    }

    @Test
    fun `a calendar with no connected account is dropped rather than orphaned`() {
        val projection = ProviderIdentity.project(
            snapshot = snapshot(listOf(event())),
            accounts = emptyList(),
            optedIn = setOf(calendarId),
            zone = zone,
            now = now,
        )
        assertTrue(projection.calendars.isEmpty())
        assertTrue(projection.events.isEmpty())
    }

    @Test
    fun `a hidden calendar is not projected`() {
        val projection = ProviderIdentity.project(
            snapshot = snapshot(listOf(event()), visible = false),
            accounts = listOf(account()),
            optedIn = setOf(calendarId),
            zone = zone,
            now = now,
        )
        assertTrue(projection.calendars.isEmpty())
    }

    @Test
    fun `a read-only collection is projected as read-only`() {
        val projection = ProviderIdentity.project(
            snapshot = snapshot(listOf(event()), readOnly = true),
            accounts = listOf(account()),
            optedIn = setOf(calendarId),
            zone = zone,
            now = now,
        )
        assertTrue(projection.calendars.single().readOnly)
    }

    @Test
    fun `events outside the window are not projected`() {
        val ancient = event(start = LocalDateTime.of(2000, 1, 1, 9, 0))
        val distant = event(id = "far", start = LocalDateTime.of(2040, 1, 1, 9, 0))
        val projection = ProviderIdentity.project(
            snapshot = snapshot(listOf(event(), ancient, distant)),
            accounts = listOf(account()),
            optedIn = setOf(calendarId),
            zone = zone,
            now = now,
        )
        assertEquals(listOf("e1"), projection.events.map { it.id })
    }

    // --------------------------------------------------------------- helpers

    private fun timed(title: String = "Standup") = projectOne(event(title = title))

    private fun projectOne(event: CalEvent): ProviderIdentity.ProjectedEvent =
        ProviderIdentity.project(
            snapshot = snapshot(listOf(event)),
            accounts = listOf(account()),
            optedIn = setOf(calendarId),
            zone = zone,
            now = now,
        ).events.single()

    private fun event(
        id: String = "e1",
        title: String = "Standup",
        start: LocalDateTime = LocalDateTime.of(2026, 5, 18, 14, 0),
        etag: String? = "\"one\"",
        reminders: List<Int> = emptyList(),
    ) = CalEvent(
        id = id,
        title = title,
        color = 0xFFC2697F,
        start = start,
        durationMinutes = 45,
        calendarId = calendarId,
        availability = Availability.Busy,
        reminders = reminders.map { Reminder(it) },
        uid = id,
        href = "https://dav.example/cal/home/$id.ics",
        etag = etag,
    )

    private fun allDay(date: LocalDate, endDate: LocalDate? = null) = CalEvent(
        id = "a1",
        title = "Holiday",
        color = 0xFFC2697F,
        start = null,
        durationMinutes = null,
        allDay = true,
        date = date,
        endDate = endDate,
        calendarId = calendarId,
    )

    private fun snapshot(
        events: List<CalEvent>,
        visible: Boolean = true,
        readOnly: Boolean = false,
    ) = CalinoSnapshot(
        events = events,
        tasks = emptyList(),
        journals = emptyList(),
        calendars = listOf(
            CalinoCalendar(
                id = calendarId,
                name = "Home",
                color = 0xFFC2697F,
                readOnly = readOnly,
                visible = visible,
            ),
        ),
    )

    private fun account() = CalDavAccount(
        id = "account-1",
        displayName = "Home",
        serverUrl = "https://dav.example/",
        username = "ivan",
        calendars = listOf(CalDavCalendar(id = calendarId, name = "Home", color = 0xFFC2697F)),
    )
}
