package calino.malinov.ski.ui.home

import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.startOfWeek
import calino.malinov.ski.util.weekdayColumn
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The week strip's pill is bound to the day each page is showing.
 *
 * The strip pages a whole week's width under the pill while a day drag is still
 * live, so an indicator derived from that drag's offset is expressed in a week
 * that is itself moving. It used to land on the far side of the incoming week
 * for the whole gesture, and holding the drag let the settle handoff and the
 * selector spring walk it through days that were never the destination. Reading
 * the column off the page's own day makes all of that unrepresentable.
 */
class WeekStripSelectorTest {

    private val monday = CalinoWeekStart.Monday
    private val committedMonday = LocalDate.of(2026, 5, 18)
    private val previousSunday = LocalDate.of(2026, 5, 17)

    @Test fun thePreviewedWeekShowsThePreviewedDay() {
        // Dragging Monday 18 back toward Sunday 17: the incoming week's page
        // shows Sunday, so its pill is column 6 -- the right-hand edge, from
        // the first frame that week is drawn.
        assertEquals(6, columnOn(LocalDate.of(2026, 5, 11), previewing = previousSunday))
    }

    @Test fun theWeekBeingLeftKeepsTheCommittedDay() {
        assertEquals(0, columnOn(LocalDate.of(2026, 5, 18), previewing = previousSunday))
    }

    @Test fun theMirrorCrossingShowsTheIncomingMonday() {
        // Sunday 24 forward into Monday 25, the first column of the next week.
        val committedSunday = LocalDate.of(2026, 5, 24)
        val nextMonday = LocalDate.of(2026, 5, 25)
        assertEquals(
            0,
            weekStripPageDay(
                pageWeekStart = LocalDate.of(2026, 5, 25),
                displayedWeekDay = nextMonday,
                committedColumn = committedSunday.weekdayColumn(monday),
                weekStart = monday,
            ).weekdayColumn(monday),
        )
    }

    @Test fun aWeekMerelySlidingPastKeepsTheCommittedWeekday() {
        // A plain week-strip swipe previews no boundary day, so every page
        // shows the same weekday and the pill does not move between them.
        val committedWednesday = LocalDate.of(2026, 5, 20)
        listOf(
            LocalDate.of(2026, 5, 11),
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 5, 25),
        ).forEach { pageWeekStart ->
            assertEquals(
                2,
                weekStripPageDay(
                    pageWeekStart = pageWeekStart,
                    displayedWeekDay = committedWednesday,
                    committedColumn = committedWednesday.weekdayColumn(monday),
                    weekStart = monday,
                ).weekdayColumn(monday),
            )
        }
    }

    @Test fun thePillAlwaysNamesTheDayDrawnUnderIt() {
        // The property the whole change rests on: for every page and every
        // previewed day, the pill's column is that page's own day's column, so
        // the indicator can never point at a date the row is not showing.
        val pages = (-2L..2L).map { LocalDate.of(2026, 5, 18).plusWeeks(it) }
        val previews = (-9L..9L).map { committedMonday.plusDays(it) }
        previews.forEach { preview ->
            pages.forEach { pageWeekStart ->
                val pageDay = weekStripPageDay(
                    pageWeekStart = pageWeekStart,
                    displayedWeekDay = preview,
                    committedColumn = committedMonday.weekdayColumn(monday),
                    weekStart = monday,
                )
                assertEquals(pageWeekStart, pageDay.startOfWeek(monday))
                assertEquals(
                    pageDay,
                    pageWeekStart.plusDays(pageDay.weekdayColumn(monday).toLong()),
                )
            }
        }
    }

    @Test fun weekStartIsHonoured() {
        // With a Sunday week start, Sunday 17 is column 0 of the week that
        // Monday 18 also belongs to -- no crossing, and no jump.
        val sunday = CalinoWeekStart.Sunday
        assertEquals(
            0,
            weekStripPageDay(
                pageWeekStart = LocalDate.of(2026, 5, 17),
                displayedWeekDay = previousSunday,
                committedColumn = committedMonday.weekdayColumn(sunday),
                weekStart = sunday,
            ).weekdayColumn(sunday),
        )
    }

    @Test fun theColumnIsMeasuredInTheWeekTheRowIsShowing() {
        // Committed Saturday 23, row still on its own week: the pill sits on
        // column 6 and slides off the right edge as the drag advances.
        val committedSaturday = LocalDate.of(2026, 5, 23)
        val ownWeek = LocalDate.of(2026, 5, 17)
        assertEquals(6f, column(committedSaturday, ownWeek, 0f), .001f)
        assertEquals(6.5f, column(committedSaturday, ownWeek, -.5f), .001f)
    }

    @Test fun theSameTravelReadsAsTheIncomingEdgeOnceTheRowHasPaged() {
        // Identical pager travel, row now showing the next week: the very same
        // gesture reads as sliding in from the left edge onto Sunday 24. This
        // is the frame change the whole fix rests on -- measured in the week
        // being left, this travel put the pill in the middle of the new week.
        val committedSaturday = LocalDate.of(2026, 5, 23)
        val nextWeek = LocalDate.of(2026, 5, 24)
        assertEquals(-0.5f, column(committedSaturday, nextWeek, -.5f), .001f)
        assertEquals(0f, column(committedSaturday, nextWeek, -1f), .001f)
    }

    @Test fun theColumnIsMonotonicInTravel() {
        // No reversal anywhere along a crossing: the pill can drift off an edge
        // but never doubles back, which is what "walking through days that were
        // never the destination" looked like.
        val committedSaturday = LocalDate.of(2026, 5, 23)
        val nextWeek = LocalDate.of(2026, 5, 24)
        var previous = Float.NEGATIVE_INFINITY
        (0..40).forEach { step ->
            val value = column(committedSaturday, nextWeek, -step / 20f)
            assert(value >= previous) { "column went backwards at step $step: $value < $previous" }
            previous = value
        }
    }

    @Test fun atRestTheColumnIsTheCommittedDaysOwn() {
        (0L..6L).forEach { offset ->
            val day = LocalDate.of(2026, 5, 24).plusDays(offset)
            assertEquals(
                offset.toFloat(),
                column(day, LocalDate.of(2026, 5, 24), 0f),
                .001f,
            )
        }
    }

    private fun column(committed: LocalDate, rowWeekStart: LocalDate, distance: Float): Float =
        selectorColumnInRowWeek(
            committedDay = committed,
            rowWeekStart = rowWeekStart,
            distanceInPages = distance,
        )

    private fun columnOn(pageWeekStart: LocalDate, previewing: LocalDate): Int =
        weekStripPageDay(
            pageWeekStart = pageWeekStart,
            displayedWeekDay = previewing,
            committedColumn = committedMonday.weekdayColumn(monday),
            weekStart = monday,
        ).weekdayColumn(monday)
}
