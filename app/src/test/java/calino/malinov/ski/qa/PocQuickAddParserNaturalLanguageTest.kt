package calino.malinov.ski.qa

import calino.malinov.ski.data.parser.PocQuickAddKind
import calino.malinov.ski.data.parser.parseQuickAdd
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The natural-language line behind quick add, at the vocabulary the web app
 * accepts: spoken kinds, casual times of day, ranges, durations that survive a
 * recurrence, and a title that keeps only what the line actually said.
 */
class PocQuickAddParserNaturalLanguageTest {
    private val baseDate = LocalDate.of(2026, 5, 18) // a Monday

    private fun event(input: String) = parseQuickAdd(PocQuickAddKind.Event, input, baseDate)

    // -- Spoken kinds -----------------------------------------------------

    @Test fun aReminderPrefixTurnsTheLineIntoATask() {
        val draft = event("remind me to call the plumber tomorrow at 9")

        assertEquals(PocQuickAddKind.Task, draft.kind)
        assertEquals("call the plumber", draft.title)
        assertEquals(baseDate.plusDays(1), draft.date)
        assertEquals(LocalTime.of(9, 0), draft.time)
    }

    @Test fun aTasksOwnPrefixesAreDroppedFromItsTitle() {
        val todo = parseQuickAdd(PocQuickAddKind.Task, "todo buy milk", baseDate)
        assertEquals("buy milk", todo.title)
        assertEquals(PocQuickAddKind.Task, todo.kind)
        assertEquals("draft the letter", parseQuickAdd(PocQuickAddKind.Task, "task draft the letter", baseDate).title)
        assertEquals("pay the rent", event("reminder: pay the rent").title)
    }

    @Test fun ordinaryCalendarTextIsNotMistakenForAnInstruction() {
        // "task" and "todo" are words in a calendar before they are prefixes,
        // so only an already-a-task line reads them as one.
        val meeting = event("task force meeting at 3pm")
        assertEquals(PocQuickAddKind.Event, meeting.kind)
        assertEquals("task force meeting", meeting.title)
        assertEquals(LocalTime.of(15, 0), meeting.time)

        assertEquals("force meeting", parseQuickAdd(PocQuickAddKind.Task, "task force meeting", baseDate).title)
    }

    @Test fun aBareTaskWordIsStillATitle() {
        val draft = event("task")

        assertEquals(PocQuickAddKind.Event, draft.kind)
        assertEquals("task", draft.title)
    }

    @Test fun aJournalEntryKeepsItsOwnKindAndBody() {
        val draft = parseQuickAdd(PocQuickAddKind.Journal, "remind me to walk by the lake today", baseDate)

        assertEquals(PocQuickAddKind.Journal, draft.kind)
        assertEquals("remind me to walk by the lake", draft.title)
        // A journal keeps the whole line as its body: the words are the entry.
        assertEquals("remind me to walk by the lake today", draft.body)
    }

    // -- Times of day -----------------------------------------------------

    @Test fun casualTimesOfDayGetTheirEverydayHour() {
        assertEquals(LocalTime.of(12, 0), event("lunch at noon").time)
        assertEquals(LocalTime.of(20, 0), event("dinner tonight").time)
        assertEquals(LocalTime.of(9, 0), event("review notes in the morning").time)
        assertEquals(LocalTime.of(21, 0), event("drinks at night").time)
        assertEquals(LocalTime.of(0, 0), event("wrap up at midnight").time)
    }

    @Test fun anExplicitClockTimeBeatsAWord() {
        val draft = event("drinks tonight at 8pm")

        assertEquals(LocalTime.of(20, 0), draft.time)
        assertEquals("drinks", draft.title)
    }

    @Test fun aBareNumberIsSomebodysCountNotATime() {
        val draft = event("meeting with 3 people")

        assertNull(draft.time)
        assertEquals("meeting with 3 people", draft.title)
    }

    // -- Ranges -----------------------------------------------------------

    @Test fun anExplicitRangeSetsStartAndDuration() {
        val draft = event("meeting from 2pm to 3pm")

        assertEquals(LocalTime.of(14, 0), draft.time)
        assertEquals(60, draft.durationMinutes)
        assertEquals("meeting", draft.title)
    }

    @Test fun aShorthandRangeWorksWithoutPrepositions() {
        val draft = event("workout 10-11")

        assertEquals(LocalTime.of(10, 0), draft.time)
        assertEquals(60, draft.durationMinutes)
        assertEquals("workout", draft.title)
    }

    @Test fun aRangeOverNoonEndsOnTheNextDayOfHoursRatherThanWrappingBackwards() {
        val draft = event("shift from 9pm to 1am")

        assertEquals(LocalTime.of(21, 0), draft.time)
        assertEquals(240, draft.durationMinutes)
    }

    @Test fun aNumberRangeWithoutATimeCueStaysInTheTitle() {
        val draft = event("party 18-20")

        assertNull(draft.time)
        assertNull(draft.durationMinutes)
        assertEquals("party 18-20", draft.title)
    }

    // -- Durations and recurrence -----------------------------------------

    @Test fun aDurationSurvivesARepeatedRange() {
        val draft = event("standup daily at 9am to 9:30am")

        assertEquals("FREQ=DAILY", draft.recurrence)
        assertEquals(LocalTime.of(9, 0), draft.time)
        assertEquals(30, draft.durationMinutes)
        assertEquals("standup", draft.title)
    }

    @Test fun aSpokenDurationIsMinutesAndSurvivesAWeeklySeries() {
        val draft = event("yoga every week for 90 minutes")

        assertEquals("FREQ=WEEKLY", draft.recurrence)
        assertEquals(90, draft.durationMinutes)
        assertEquals("yoga", draft.title)
    }

    @Test fun aFractionalDurationIsRoundedToWholeMinutes() {
        assertEquals(90, event("call the bank for 1,5 hours").durationMinutes)
        assertEquals(90, event("call the bank for 1.5 h").durationMinutes)
    }

    @Test fun aSeriesOnAWeekdayStartsThatWeekdayAndKeepsItsNameOffTheTitle() {
        val draft = event("gym every monday")

        assertEquals("FREQ=WEEKLY;BYDAY=MO", draft.recurrence)
        assertEquals(LocalDate.of(2026, 5, 25), draft.date)
        assertEquals("gym", draft.title)
    }

    @Test fun everyWeekdayIsMondayThroughFriday() {
        val draft = event("standup every weekday at 10am")

        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", draft.recurrence)
        assertEquals(LocalTime.of(10, 0), draft.time)
    }

    // -- Dates ------------------------------------------------------------

    @Test fun datesWrittenEitherWayRoundAreRead() {
        assertEquals(LocalDate.of(2026, 5, 20), event("dinner May 20").date)
        assertEquals(LocalDate.of(2026, 5, 20), event("dinner 20 May").date)
        assertEquals(LocalDate.of(2026, 5, 20), event("dinner on the 20th of May").date)
        assertEquals(LocalDate.of(2027, 2, 3), event("trip 2027-02-03").date)
    }

    @Test fun relativePhrasesAndMonthEnds() {
        assertEquals(LocalDate.of(2026, 5, 21), event("standup in 3 days").date)
        assertEquals(LocalDate.of(2026, 6, 18), event("review next month").date)
        assertEquals(LocalDate.of(2026, 5, 31), event("invoices end of month").date)
    }

    @Test fun aWeekdayKeepsItsDirection() {
        assertEquals(LocalDate.of(2026, 5, 22), event("demo next friday").date)
        assertEquals(LocalDate.of(2026, 5, 15), event("demo last friday").date)
        assertEquals(LocalDate.of(2026, 5, 18), event("demo this monday").date)
    }

    // -- Titles and locations ---------------------------------------------

    @Test fun aTrailingPlaceIsALocationButATimeIsNot() {
        val draft = event("lunch at 2pm at Café Lumen")

        assertEquals(LocalTime.of(14, 0), draft.time)
        assertEquals("Café Lumen", draft.location)
        assertEquals("lunch", draft.title)
    }

    @Test fun oneLineStillYieldsEveryFieldTheWebDoes() {
        val draft = event("Coffee with Maya next friday at 15:00 for 1 h at Café Lumen")

        assertEquals("Coffee with Maya", draft.title)
        assertEquals(baseDate.plusDays(4), draft.date)
        assertEquals(LocalTime.of(15, 0), draft.time)
        assertEquals(60, draft.durationMinutes)
        assertEquals("Café Lumen", draft.location)
    }

    @Test fun anEmptyLineFallsBackToTheKindName() {
        assertEquals("New event", event("").title)
        assertEquals("New task", parseQuickAdd(PocQuickAddKind.Task, "   ", baseDate).title)
    }

    @Test fun aLineThatSaysNothingIsNotAParsedField() {
        val draft = event("call the plumber")

        assertEquals("call the plumber", draft.title)
        assertEquals(baseDate, draft.date)
        assertNull(draft.time)
        assertNull(draft.durationMinutes)
        assertNull(draft.location)
        assertNull(draft.recurrence)
        assertTrue(draft.body.isNotBlank())
    }
}
