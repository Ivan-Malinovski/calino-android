package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.util.AllDayItem
import calino.malinov.ski.util.AllDaySpan
import calino.malinov.ski.util.layoutAllDayBand
import calino.malinov.ski.util.resolveAllDaySpans
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The all-day band's lane packer. Its predecessor rendered `allDay.firstOrNull()
 * ?: due.firstOrNull()` -- a day with both an all-day event and a due task lost
 * the task outright, silently. That data loss, the reshuffle-on-expand
 * regression it is easy to reintroduce, and the recurring-span trap in
 * `occurrenceStartCovering` are what these tests pin down.
 */
class AllDayBandLayoutTest {
    private val monday = LocalDate.of(2026, 5, 18)
    private val week = (0L..6L).map { monday.plusDays(it) }

    private fun event(id: String, date: LocalDate, endDate: LocalDate? = null, recurrence: String? = null) = CalEvent(
        id = id,
        title = id,
        color = 0xFF5B7FB5,
        start = null,
        durationMinutes = null,
        allDay = true,
        date = date,
        endDate = endDate,
        recurrence = recurrence,
        calendarId = "personal",
    )

    private fun task(id: String, due: LocalDate) = CalTask(
        id = id,
        title = id,
        color = 0xFF5B7FB5,
        due = due,
    )

    private fun span(event: CalEvent, start: LocalDate, endInclusive: LocalDate = start) = AllDaySpan(
        event = event,
        occurrenceStart = start,
        start = start,
        endInclusive = endInclusive,
        continuesBefore = false,
        continuesAfter = false,
    )

    // -- Packing --------------------------------------------------------

    @Test
    fun nonOverlappingSpansShareALane() {
        val a = event("a", monday)
        val b = event("b", monday.plusDays(2))
        val layout = layoutAllDayBand(week, listOf(span(a, monday), span(b, monday.plusDays(2))), emptyList(), laneLimit = 2)

        assertEquals(setOf(0), layout.placements.map { it.lane }.toSet())
    }

    @Test
    fun overlappingSpansTakeSeparateLanes() {
        val a = event("a", monday, monday.plusDays(2))
        val b = event("b", monday.plusDays(1), monday.plusDays(3))
        val layout = layoutAllDayBand(
            week,
            listOf(span(a, monday, monday.plusDays(2)), span(b, monday.plusDays(1), monday.plusDays(3))),
            emptyList(),
            laneLimit = 2,
        )

        assertEquals(setOf(0, 1), layout.placements.map { it.lane }.toSet())
    }

    @Test
    fun theLongerSpanWinsTheLowerLaneOnATie() {
        val short = event("short", monday, monday.plusDays(1))
        val long = event("long", monday, monday.plusDays(4))
        val layout = layoutAllDayBand(
            week,
            listOf(span(short, monday, monday.plusDays(1)), span(long, monday, monday.plusDays(4))),
            emptyList(),
            laneLimit = 2,
        ).placements.associateBy { (it.item as AllDayItem.Event).event.id }

        assertEquals(0, layout.getValue("long").lane)
        assertEquals(1, layout.getValue("short").lane)
    }

    @Test
    fun eventsPrecedeTasksInLaneOrder() {
        // A task on day 0 and an event spanning days 2-3: they never overlap
        // in columns, so the task must land in lane 0 alongside the event,
        // not be pushed to lane 1 just because events are placed first.
        val trip = event("trip", monday.plusDays(2), monday.plusDays(3))
        val t = task("t", monday)
        val layout = layoutAllDayBand(week, listOf(span(trip, monday.plusDays(2), monday.plusDays(3))), listOf(t), laneLimit = 2)
            .placements.associateBy { it.item.key }

        assertEquals(0, layout.getValue("event:trip@${monday.plusDays(2)}").lane)
        assertEquals(0, layout.getValue("task:t@$monday").lane)
    }

    @Test
    fun aClashingTaskIsPushedBehindTheEventThatClaimedTheLane() {
        val allDay = event("allday", monday)
        val t = task("t", monday)
        val layout = layoutAllDayBand(week, listOf(span(allDay, monday)), listOf(t), laneLimit = 2)
            .placements.associateBy { it.item.key }

        assertEquals(0, layout.getValue("event:allday@$monday").lane)
        assertEquals(1, layout.getValue("task:t@$monday").lane)
    }

    @Test
    fun aDayWithBothAnAllDayEventAndATaskYieldsAllOfThem() {
        // The original bug: `allDay.firstOrNull() ?: due.firstOrNull()` meant
        // a day with an all-day event never showed its tasks at all.
        val allDay = event("allday", monday)
        val t1 = task("t1", monday)
        val t2 = task("t2", monday)
        // All three clash on the same column, so three lanes are needed for
        // all of them to be simultaneously visible -- the point being tested.
        val layout = layoutAllDayBand(week, listOf(span(allDay, monday)), listOf(t1, t2), laneLimit = 3)

        val eventIds = layout.placements.mapNotNull { (it.item as? AllDayItem.Event)?.event?.id }
        val taskIds = layout.placements.mapNotNull { (it.item as? AllDayItem.Task)?.task?.id }
        assertEquals(listOf("allday"), eventIds)
        assertEquals(setOf("t1", "t2"), taskIds.toSet())
    }

    @Test
    fun overflowPastTheLaneLimitReportsCountsPerKind() {
        val a = event("a", monday)
        val b = event("b", monday)
        val t = task("t", monday)
        val layout = layoutAllDayBand(week, listOf(span(a, monday), span(b, monday)), listOf(t), laneLimit = 1)

        assertEquals(1, layout.placements.size)
        assertEquals(2, layout.overflow.size)
        assertEquals(1, layout.overflowEventCount)
        assertEquals(1, layout.overflowTaskCount)
    }

    @Test
    fun expandingDoesNotReshuffleTheVisibleLanes() {
        val events = (0..3).map { event("e$it", monday.plusDays(it.toLong())) }
        val spans = events.mapIndexed { i, e -> span(e, monday.plusDays(i.toLong())) }
        val collapsed = layoutAllDayBand(week, spans, emptyList(), laneLimit = 2)
        val expanded = layoutAllDayBand(week, spans, emptyList(), laneLimit = Int.MAX_VALUE)

        val collapsedFirstTwoLanes = collapsed.placements.associate { it.item.key to it.lane }
        val expandedFirstTwoLanes = expanded.placements
            .filter { it.lane < 2 }
            .associate { it.item.key to it.lane }
        assertEquals(collapsedFirstTwoLanes, expandedFirstTwoLanes)
    }

    // -- Span resolution --------------------------------------------------

    @Test
    fun aNonRecurringMultiDaySpanResolvesOnceAcrossItsDays() {
        val trip = event("trip", monday, monday.plusDays(3))
        val spans = resolveAllDaySpans(week) { day -> listOf(trip).filter { it.occursOn(day) } }

        assertEquals(1, spans.size)
        assertEquals(monday, spans.single().start)
        assertEquals(monday.plusDays(3), spans.single().endInclusive)
    }

    @Test
    fun aSingleDayEventHasEqualStartAndEnd() {
        val single = event("single", monday)
        val spans = resolveAllDaySpans(week) { day -> listOf(single).filter { it.occursOn(day) } }

        assertEquals(monday, spans.single().start)
        assertEquals(monday, spans.single().endInclusive)
    }

    @Test
    fun aRecurringOccurrenceUsesItsOwnStartNotTheMaster() {
        // Weekly Mon-Wed trip; the master anchors three weeks before `week`.
        val trip = event("trip", monday.minusWeeks(3), monday.minusWeeks(3).plusDays(2), recurrence = "FREQ=WEEKLY;BYDAY=MO")
        val spans = resolveAllDaySpans(week) { day -> listOf(trip).filter { it.occursOn(day) } }

        assertEquals(1, spans.size)
        // This week's occurrence starts Monday, not the master's date.
        assertEquals(monday, spans.single().occurrenceStart)
        assertEquals(monday.plusDays(2), spans.single().endInclusive)
    }

    @Test
    fun spansOutsideTheWindowAreDropped() {
        val distant = event("distant", monday.minusMonths(1))
        val spans = resolveAllDaySpans(week) { day -> listOf(distant).filter { it.occursOn(day) } }

        assertTrue(spans.isEmpty())
    }

    @Test
    fun clippingSetsContinuesBeforeAndAfter() {
        val long = event("long", monday.minusDays(2), monday.plusDays(8))
        val spans = resolveAllDaySpans(week) { day -> listOf(long).filter { it.occursOn(day) } }

        val only = spans.single()
        assertEquals(monday, only.start)
        assertEquals(week.last(), only.endInclusive)
        assertTrue(only.continuesBefore)
        assertTrue(only.continuesAfter)
    }

    @Test
    fun clippedOnlyAtTheStart() {
        val leading = event("leading", monday.minusDays(2), monday.plusDays(1))
        val spans = resolveAllDaySpans(week) { day -> listOf(leading).filter { it.occursOn(day) } }

        val only = spans.single()
        assertTrue(only.continuesBefore)
        assertFalse(only.continuesAfter)
    }
}
