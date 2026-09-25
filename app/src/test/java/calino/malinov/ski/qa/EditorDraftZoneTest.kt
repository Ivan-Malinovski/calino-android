package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.editorDraftFor
import calino.malinov.ski.ui.surfaces.foreignZoneCaption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The editor edits wall times in the event's own zone and saves device-zone times. */
class EditorDraftZoneTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")

    private val standup = CalEvent(
        id = "e", title = "Standup", color = 0,
        // 15:00 in Berlin is 09:00 in New York on 2 March 2026.
        start = LocalDateTime.of(2026, 3, 2, 15, 0), durationMinutes = 30,
        calendarId = "cal", zoneId = "America/New_York",
    )

    @Test
    fun `a foreign-zone event opens in its own wall time and saves unchanged`() {
        val draft = editorDraftFor(standup, berlin)
        assertEquals(LocalTime.of(9, 0), draft.startTime)
        assertEquals(LocalDate.of(2026, 3, 2), draft.date)
        val saved = draft.toNewEvent(berlin)
        assertEquals(LocalTime.of(15, 0), saved.startTime)
        assertEquals("America/New_York", saved.zoneId)
    }

    @Test
    fun `changing the zone keeps the wall time and moves the instant`() {
        val draft = editorDraftFor(standup, berlin).withZone(ZoneId.of("America/Chicago"))
        assertEquals(LocalTime.of(9, 0), draft.startTime)
        // 09:00 Chicago is 16:00 Berlin.
        assertEquals(LocalTime.of(16, 0), draft.toNewEvent(berlin).startTime)
    }

    @Test
    fun `a separate end zone keeps the end wall time and derives the duration`() {
        val flight = editorDraftFor(standup.copy(zoneId = "Europe/Copenhagen", start = LocalDateTime.of(2026, 6, 1, 10, 0), durationMinutes = 120), berlin)
        // Ends 12:00 Copenhagen; re-read in New York the end is 06:00.
        val withEnd = flight.withEndZone(newYork)
        assertEquals(LocalTime.of(12, 0), withEnd.endTime)
        assertEquals(8 * 60, withEnd.durationMinutes)
        assertEquals("America/New_York", withEnd.toNewEvent(berlin).endZoneId)
        // Back to the start's zone clears it.
        assertNull(withEnd.withEndZone(ZoneId.of("Europe/Copenhagen")).endZoneId)
    }

    @Test
    fun `an event without a zone edits in the device zone`() {
        val draft = editorDraftFor(standup.copy(zoneId = null), berlin)
        assertEquals(LocalTime.of(15, 0), draft.startTime)
        assertNull(draft.toNewEvent(berlin).zoneId)
    }

    private fun caption(zone: String?, endZone: String? = null, minutes: Long = 30) = foreignZoneCaption(
        start = LocalDateTime.of(2026, 3, 2, 15, 0),
        end = LocalDateTime.of(2026, 3, 2, 15, 0).plusMinutes(minutes),
        zoneId = zone, endZoneId = endZone, device = berlin, format = { it.toString() },
    )

    @Test
    fun `detail caption names the event's own clock only when it reads differently`() {
        assertEquals("09:00 – 09:30 in New York", caption("America/New_York"))
        // Paris is another id with the same offset: nothing to say.
        assertNull(caption("Europe/Paris"))
        assertNull(caption(null))
        assertEquals("15:00 Paris → 10:00 New York", caption("Europe/Paris", "America/New_York", minutes = 60))
    }
}
