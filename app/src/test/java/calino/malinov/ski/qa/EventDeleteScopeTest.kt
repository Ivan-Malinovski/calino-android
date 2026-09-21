package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.ui.surfaces.defaultEventDeleteScope
import calino.malinov.ski.ui.surfaces.eventDeleteScopes
import calino.malinov.ski.ui.surfaces.eventDetailReadOnly
import calino.malinov.ski.ui.surfaces.isRecurringEvent
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
        calendarId: String = "work",
        providerRecurring: Boolean = false,
    ) = CalEvent(
        id = "evt",
        title = "Design review",
        color = 0xFF5B7FB5,
        start = start,
        durationMinutes = 60,
        recurrence = recurrence,
        recurrenceId = recurrenceId,
        recurrenceDate = recurrenceDate,
        calendarId = calendarId,
        providerRecurring = providerRecurring,
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
    fun `imported recurrence omits this and future in every shared delete body`() {
        val event = event(calendarId = "android:7", providerRecurring = true)

        assertEquals(
            listOf(RecurrenceEditScope.This, RecurrenceEditScope.All),
            eventDeleteScopes(event),
        )
    }

    @Test
    fun `writable imported detail follows calendar capability rather than its id`() {
        val imported = event(calendarId = "android:7")

        assertFalse(eventDetailReadOnly(hostReadOnly = false, imported))
        assertTrue(eventDetailReadOnly(hostReadOnly = true, imported))
    }

    @Test
    fun `a detached override with no rule of its own is still recurring`() {
        val event = event(recurrenceDate = LocalDate.of(2026, 5, 25))
        assertTrue(isRecurringEvent(event))
        assertEquals(RecurrenceEditScope.This, defaultEventDeleteScope(event))
    }
}
