package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.RecurrenceFreq
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.model.recurrenceCountOf
import calino.malinov.ski.data.model.recurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A repeat can end after a number of occurrences as well as on a date. */
class RecurrenceCountRuleTest {

    @Test fun aCountIsWrittenAndReadBack() {
        val rule = recurrenceRule(RecurrenceFreq.Weekly, setOf(DayOfWeek.TUESDAY), null, 5)
        assertEquals("FREQ=WEEKLY;BYDAY=TU;COUNT=5", rule)
        assertEquals(5, recurrenceCountOf(rule))
    }

    @Test fun anUncappedRuleHasNoCount() {
        assertNull(recurrenceCountOf(recurrenceRule(RecurrenceFreq.Daily)))
        assertNull(recurrenceCountOf(null))
    }

    @Test fun aRuleNeverCarriesBothAnEndDateAndACount() {
        val rule = recurrenceRule(RecurrenceFreq.Daily, emptySet(), LocalDate.of(2026, 6, 1), 5)
        assertTrue(rule, rule.contains("UNTIL=") && !rule.contains("COUNT="))
    }

    @Test fun aCountBelowOneIsClampedToOne() {
        assertEquals("FREQ=DAILY;COUNT=1", recurrenceRule(RecurrenceFreq.Daily, emptySet(), null, 0))
    }

    @Test fun theSeriesStopsAfterItsCount() {
        val start = LocalDate.of(2026, 5, 18)
        val event = CalEvent(
            id = "e", title = "Course", color = 0xFF5B7FB5, start = start.atTime(9, 0), durationMinutes = 30,
            recurrence = recurrenceRule(RecurrenceFreq.Weekly, setOf(DayOfWeek.MONDAY), null, 3), calendarId = "personal",
        )
        assertTrue(event.occursOn(start.plusWeeks(2)))
        assertFalse(event.occursOn(start.plusWeeks(3)))
    }
}
