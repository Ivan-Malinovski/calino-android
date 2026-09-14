package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.occursOn
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins `CalEvent.occursOn` to the whole RRULE grammar rather than the
 * `FREQ`/`BYDAY`/`UNTIL` subset it once parsed. TODO item 5: the calendar and
 * the CalDAV expander must agree, so both now evaluate rules with the same
 * library engine.
 */
class RecurrenceRuleTest {

    private val anchor = LocalDate.of(2026, 5, 18) // a Monday

    private fun event(rule: String, start: LocalDate = anchor) = CalEvent(
        id = "evt",
        title = "Repeat",
        color = 0xFF5B7FB5,
        start = start.atTime(9, 0),
        durationMinutes = 30,
        recurrence = rule,
        calendarId = "personal",
    )

    @Test
    fun intervalSkipsTheWeeksTheRuleExcludes() {
        val fortnightly = event("FREQ=WEEKLY;INTERVAL=2")

        assertTrue(fortnightly.occursOn(anchor))
        assertFalse(fortnightly.occursOn(anchor.plusWeeks(1)))
        assertTrue(fortnightly.occursOn(anchor.plusWeeks(2)))
        assertFalse(fortnightly.occursOn(anchor.plusWeeks(3)))
    }

    @Test
    fun intervalAppliesToEveryFrequency() {
        assertFalse(event("FREQ=DAILY;INTERVAL=3").occursOn(anchor.plusDays(1)))
        assertTrue(event("FREQ=DAILY;INTERVAL=3").occursOn(anchor.plusDays(3)))
        assertFalse(event("FREQ=MONTHLY;INTERVAL=2").occursOn(LocalDate.of(2026, 6, 18)))
        assertTrue(event("FREQ=MONTHLY;INTERVAL=2").occursOn(LocalDate.of(2026, 7, 18)))
        assertFalse(event("FREQ=YEARLY;INTERVAL=2").occursOn(LocalDate.of(2027, 5, 18)))
        assertTrue(event("FREQ=YEARLY;INTERVAL=2").occursOn(LocalDate.of(2028, 5, 18)))
    }

    @Test
    fun countBoundsTheSeries() {
        val threeDays = event("FREQ=DAILY;COUNT=3")

        assertTrue(threeDays.occursOn(anchor.plusDays(2)))
        assertFalse(threeDays.occursOn(anchor.plusDays(3)))
    }

    @Test
    fun byMonthDayAndBySetPosArePlacedByTheRuleNotTheAnchor() {
        // First Friday of the month, from a Monday anchor.
        val firstFriday = event("FREQ=MONTHLY;BYDAY=FR;BYSETPOS=1")
        assertTrue(firstFriday.occursOn(LocalDate.of(2026, 6, 5)))
        assertFalse(firstFriday.occursOn(LocalDate.of(2026, 6, 12)))

        val fifteenth = event("FREQ=MONTHLY;BYMONTHDAY=15")
        assertTrue(fifteenth.occursOn(LocalDate.of(2026, 6, 15)))
        assertFalse(fifteenth.occursOn(LocalDate.of(2026, 6, 18)))
    }

    @Test
    fun monthlyAnchorPastAShortMonthSkipsThatMonth() {
        // RFC 5545 3.3.10: an invalid date is skipped, not clamped.
        val thirtyFirst = event("FREQ=MONTHLY", start = LocalDate.of(2026, 1, 31))

        assertTrue(thirtyFirst.occursOn(LocalDate.of(2026, 3, 31)))
        assertFalse(thirtyFirst.occursOn(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun exdateCancelsAnOccurrence() {
        val weekdays = event("FREQ=DAILY;EXDATE=20260520T090000")

        assertTrue(weekdays.occursOn(anchor.plusDays(1)))
        assertFalse(weekdays.occursOn(LocalDate.of(2026, 5, 20)))
    }

    @Test
    fun untilAndByDayStillHold() {
        val gym = event("FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260603T235959Z")

        assertTrue(gym.occursOn(LocalDate.of(2026, 5, 20)))
        assertFalse(gym.occursOn(LocalDate.of(2026, 5, 21)))
        assertTrue(gym.occursOn(LocalDate.of(2026, 6, 3)))
        assertFalse(gym.occursOn(LocalDate.of(2026, 6, 8)))
    }

    @Test
    fun anUnparseableRuleNeverRepeats() {
        val broken = event("FREQ=NONSENSE;INTERVAL=x")

        assertTrue(broken.occursOn(anchor))
        assertFalse(broken.occursOn(anchor.plusDays(1)))
    }

    @Test
    fun allDaySpansStillCoverTheirMiddleDays() {
        val trip = CalEvent(
            id = "evt-span",
            title = "Trip",
            color = 0xFF5B7FB5,
            start = null,
            durationMinutes = null,
            allDay = true,
            date = anchor,
            endDate = anchor.plusDays(2),
            calendarId = "personal",
        )

        assertEquals(
            listOf(true, true, true, false),
            (0..3).map { trip.occursOn(anchor.plusDays(it.toLong())) },
        )
    }

    @Test
    fun recurringMultiDaySpanCoversTheMiddleDaysOfALaterOccurrenceNotJustTheMaster() {
        // A weekly Mon-Wed all-day trip. The master (`anchor`) is Monday; its
        // own Tue/Wed fall inside `lastCoveredDate`'s window, but a *later*
        // occurrence's Tue/Wed are only reachable through the recurrence rule,
        // and `RecurrenceRules.occursOn` only answers "is this an occurrence
        // start" -- so the fix must re-derive the span length per occurrence.
        val trip = CalEvent(
            id = "evt-recurring-span",
            title = "Weekly trip",
            color = 0xFF5B7FB5,
            start = null,
            durationMinutes = null,
            allDay = true,
            date = anchor,
            endDate = anchor.plusDays(2),
            recurrence = "FREQ=WEEKLY;BYDAY=MO",
            calendarId = "personal",
        )
        val laterMonday = anchor.plusWeeks(3)

        assertTrue(trip.occursOn(laterMonday))
        assertTrue(trip.occursOn(laterMonday.plusDays(1)))
        assertTrue(trip.occursOn(laterMonday.plusDays(2)))
        assertFalse(trip.occursOn(laterMonday.plusDays(3)))
        assertFalse(trip.occursOn(laterMonday.minusDays(1)))
    }
}
