package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.RecurrenceEditScope
import calino.malinov.ski.poc.ui.surfaces.defaultEventDeleteScope
import calino.malinov.ski.poc.ui.surfaces.isRecurringEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overflow menu and the detail card share one confirmation, so they must
 * also share the scope it opens on. A single occurrence must never default to
 * removing the whole series.
 */
class EventDeleteScopeTest {

    private val start = LocalDateTime.of(2026, 5, 18, 10, 0)

    private fun event(
        recurrence: String? = null,
        recurrenceId: Instant? = null,
        recurrenceDate: LocalDate? = null,
    ) = CalEvent(
        id = "evt",
        title = "Design review",
        color = 0xFF5B7FB5,
        start = start,
        durationMinutes = 60,
        recurrence = recurrence,
        recurrenceId = recurrenceId,
        recurrenceDate = recurrenceDate,
        calendarId = "work",
    )

    @Test
    fun `a one-off event deletes itself`() {
        val event = event()
        assertFalse(isRecurringEvent(event))
        assertEquals(RecurrenceEditScope.All, defaultEventDeleteScope(event))
    }

    @Test
    fun `a series master defaults to the entire series`() {
        val event = event(recurrence = "FREQ=WEEKLY;BYDAY=MO")
        assertTrue(isRecurringEvent(event))
        assertEquals(RecurrenceEditScope.All, defaultEventDeleteScope(event))
    }

    @Test
    fun `a timed occurrence defaults to this event only`() {
        val event = event(
            recurrence = "FREQ=WEEKLY;BYDAY=MO",
            recurrenceId = start.plusDays(7).toInstant(java.time.ZoneOffset.UTC),
        )
        assertTrue(isRecurringEvent(event))
        assertEquals(RecurrenceEditScope.This, defaultEventDeleteScope(event))
    }

    @Test
    fun `an all-day occurrence defaults to this event only`() {
        val event = event(
            recurrence = "FREQ=WEEKLY;BYDAY=MO",
            recurrenceDate = LocalDate.of(2026, 5, 25),
        )
        assertTrue(isRecurringEvent(event))
        assertEquals(RecurrenceEditScope.This, defaultEventDeleteScope(event))
    }

    @Test
    fun `a detached override with no rule of its own is still recurring`() {
        val event = event(recurrenceDate = LocalDate.of(2026, 5, 25))
        assertTrue(isRecurringEvent(event))
        assertEquals(RecurrenceEditScope.This, defaultEventDeleteScope(event))
    }
}
