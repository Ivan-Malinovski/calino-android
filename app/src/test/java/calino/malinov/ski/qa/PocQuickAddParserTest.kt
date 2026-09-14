package calino.malinov.ski.qa

import calino.malinov.ski.data.parser.PocQuickAddKind
import calino.malinov.ski.data.parser.parseQuickAdd
import calino.malinov.ski.data.parser.parseDateNavigation
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PocQuickAddParserTest {
    private val baseDate = LocalDate.of(2026, 5, 18)

    @Test
    fun event_parsesRelativeDateTimeDurationAndLocation() {
        val draft = parseQuickAdd(PocQuickAddKind.Event, "Coffee with Maya tomorrow at 15:00 for 1 h at Café Lumen", baseDate)

        assertEquals("Coffee with Maya", draft.title)
        assertEquals(baseDate.plusDays(1), draft.date)
        assertEquals(LocalTime.of(15, 0), draft.time)
        assertEquals(60, draft.durationMinutes)
        assertEquals("Café Lumen", draft.location)
    }

    @Test
    fun journal_keepsRawTextAsReviewableBody() {
        val draft = parseQuickAdd(PocQuickAddKind.Journal, "Walked by the lake today", baseDate)

        assertEquals("Walked by the lake", draft.title)
        assertEquals(baseDate, draft.date)
        assertEquals("Walked by the lake today", draft.body)
        assertNull(draft.time)
    }

    @Test
    fun task_withoutDateToken_usesSelectedDate() {
        val draft = parseQuickAdd(PocQuickAddKind.Task, "Send the invoice", baseDate)

        assertEquals("Send the invoice", draft.title)
        assertEquals(baseDate, draft.date)
        assertNull(draft.time)
    }

    @Test fun dateNavigation_supportsRelativeWeekdaysAndIsoDates() {
        assertEquals(LocalDate.of(2026, 5, 22), parseDateNavigation("next Friday", baseDate))
        assertEquals(LocalDate.of(2027, 2, 3), parseDateNavigation("2027-02-03", baseDate))
        assertNull(parseDateNavigation("Lunch next Friday", baseDate))
    }

    @Test fun event_parsesWeeklyRecurrence() {
        val draft = parseQuickAdd(PocQuickAddKind.Event, "Standup every weekday at 9", baseDate)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", draft.recurrence)
        assertEquals(LocalTime.of(9, 0), draft.time)
    }

    @Test fun dateDayIsNotMistakenForAClockTime() {
        val draft = parseQuickAdd(PocQuickAddKind.Event, "Dinner May 20 at 7pm", baseDate)
        assertEquals(LocalDate.of(2026, 5, 20), draft.date)
        assertEquals(LocalTime.of(19, 0), draft.time)
    }
}
