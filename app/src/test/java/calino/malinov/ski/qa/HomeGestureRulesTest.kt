package calino.malinov.ski.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGestureRulesTest {
    @Test
    fun verticalScreenDrag_pullDownExpands_andPullUpCollapses() {
        assertEquals(0.5f, zoomAfterVerticalDrag(0f, 140f), 0.001f)
        assertEquals(0.5f, zoomAfterVerticalDrag(1f, -140f), 0.001f)
        assertEquals(0f, zoomAfterVerticalDrag(0f, -500f), 0.001f)
        assertEquals(2f, zoomAfterVerticalDrag(2f, 500f), 0.001f)
    }

    @Test
    fun zoomFlingUsesScreenVelocityDirection() {
        assertEquals(1, zoomSettleLevel(0.12f, anchorLevel = 0, zoomVelocityDpPerSecond = 700f))
        assertEquals(0, zoomSettleLevel(0.88f, anchorLevel = 1, zoomVelocityDpPerSecond = -700f))
    }

    @Test
    fun monthUnfoldPhases_areBoundedMonotonicAndReachTheirEndpoints() {
        assertEquals(0f, monthUnfoldPhase(.08f, .08f, .28f), 0.001f)
        val middle = monthUnfoldPhase(.18f, .08f, .28f)
        assertTrue(middle in 0f..1f)
        assertTrue(middle > monthUnfoldPhase(.12f, .08f, .28f))
        assertEquals(1f, monthUnfoldPhase(.28f, .08f, .28f), 0.001f)
    }

    @Test
    fun monthRows_unfoldNearestFirstAndSymmetricallyAroundTheHinge() {
        val near = monthRowReveal(.5f, row = 1, hingeRow = 2)
        val far = monthRowReveal(.5f, row = 0, hingeRow = 2)
        assertTrue(near > far)
        assertEquals(near, monthRowReveal(.5f, row = 3, hingeRow = 2), 0.001f)
        assertEquals(1f, monthRowReveal(0f, row = 2, hingeRow = 2), 0.001f)
    }

    @Test
    fun monthRows_beginTowardTheHingeAndSelectorResolvesToMonthGeometry() {
        assertTrue(monthRowHingeOffset(.2f, row = 1, hingeRow = 2) > 0f)
        assertTrue(monthRowHingeOffset(.2f, row = 3, hingeRow = 2) < 0f)
        assertEquals(0f, monthRowHingeOffset(1f, row = 1, hingeRow = 2), 0.001f)
        assertEquals(0f, monthSelectorMorphProgress(.06f), 0.001f)
        assertEquals(1f, monthSelectorMorphProgress(.46f), 0.001f)
    }

    @Test
    fun calendarTransition_isDeterministicAtEveryColdEntryLevel() {
        val week = calendarTransitionFrame(0f)
        assertEquals(0f, week.unfoldProgress, 0.001f)
        assertEquals(false, week.railVisible)
        assertTrue(week.agendaVisible)
        assertTrue(agendaOwnsCalendarInput(week, currentlyOwns = false))

        val split = calendarTransitionFrame(1f)
        assertEquals(1f, split.unfoldProgress, 0.001f)
        assertEquals(false, split.railVisible)
        assertTrue(split.agendaVisible)
        assertTrue(agendaOwnsCalendarInput(split, currentlyOwns = false))

        val detail = calendarTransitionFrame(2f)
        assertEquals(1f, detail.unfoldProgress, 0.001f)
        assertEquals(false, detail.railVisible)
        assertEquals(false, detail.agendaVisible)
        assertEquals(false, agendaOwnsCalendarInput(detail, currentlyOwns = true))
    }

    @Test
    fun calendarAndDaySurface_shareOneReversibleProgress() {
        val forward = listOf(.2f, .35f, .5f, .7f, .84f).map(::calendarTransitionFrame)
        assertTrue(forward.zipWithNext().all { (left, right) ->
            left.unfoldProgress <= right.unfoldProgress
        })
        forward.reversed().zipWithNext().forEach { (left, right) ->
            assertTrue(left.unfoldProgress >= right.unfoldProgress)
        }
        forward.forEach { frame ->
            assertEquals(
                monthUnfoldPhase(frame.zoom, .20f, .84f),
                frame.unfoldProgress,
                0.001f,
            )
            assertEquals(false, frame.railVisible)
            assertTrue(frame.agendaVisible)
        }
    }

    @Test
    fun agendaOwnsInputThroughoutWeekToPartialMonthTransition() {
        val middle = calendarTransitionFrame(.52f)
        assertTrue(agendaOwnsCalendarInput(middle, currentlyOwns = false))
        assertTrue(agendaOwnsCalendarInput(middle, currentlyOwns = true))
        assertTrue(agendaOwnsCalendarInput(calendarTransitionFrame(.2f), currentlyOwns = true))
        assertTrue(agendaOwnsCalendarInput(calendarTransitionFrame(.84f), currentlyOwns = false))
    }

    @Test
    fun backgroundWeekPreview_neverBlanksTheSplitMonthSettle() {
        assertEquals(false, monthCanvasVisibleDuringWeekPreview(.1f, weekPreviewActive = true))
        assertTrue(monthCanvasVisibleDuringWeekPreview(.18f, weekPreviewActive = true))
        assertTrue(monthCanvasVisibleDuringWeekPreview(.5f, weekPreviewActive = true))
        assertTrue(monthCanvasVisibleDuringWeekPreview(1f, weekPreviewActive = true))
        assertTrue(monthCanvasVisibleDuringWeekPreview(.1f, weekPreviewActive = false))
    }

    @Test
    fun dayRailExpansionOnlyStartsOnDownwardPullAtTop() {
        assertEquals(true, shouldExpandFromDayRail(dragDeltaY = 24f, railScrollValue = 0))
        assertEquals(false, shouldExpandFromDayRail(dragDeltaY = 24f, railScrollValue = 1))
        assertEquals(false, shouldExpandFromDayRail(dragDeltaY = -24f, railScrollValue = 0))
        assertEquals(false, shouldExpandFromDayRail(dragDeltaY = 0f, railScrollValue = 0))
    }

    @Test
    fun timelinePinchScale_isBounded() {
        assertEquals(.65f, timelineScaleAfterPinch(1f, .2f), 0.001f)
        assertEquals(1.5f, timelineScaleAfterPinch(1f, 1.5f), 0.001f)
        assertEquals(1.8f, timelineScaleAfterPinch(1.5f, 2f), 0.001f)
    }

    @Test
    fun emptyTimelineTouches_snapToValidHalfHours() {
        assertEquals(0, timelineCreateMinute(0f, 30))
        assertEquals(30, timelineCreateMinute(16f, 30))
        assertEquals(13 * 60 + 30, timelineCreateMinute(13 * 60 + 44f, 30))
        assertEquals(23 * 60 + 30, timelineCreateMinute(23 * 60 + 59f, 30))
        assertEquals(null, timelineCreateMinute(-1f, 30))
        assertEquals(null, timelineCreateMinute(24 * 60f, 30))
    }

    @Test
    fun heldEmptyTimelineSelection_usesQuarterHours() {
        assertEquals(13 * 60, timelineCreateMinute(13 * 60 + 7f, 15))
        assertEquals(13 * 60 + 15, timelineCreateMinute(13 * 60 + 8f, 15))
        assertEquals(13 * 60 + 45, timelineCreateMinute(13 * 60 + 52f, 15))
        assertEquals(23 * 60 + 45, timelineCreateMinute(23 * 60 + 59f, 15))
    }

    @Test
    fun liftedEventAutoScrollsOnlyInsideVerticalEdgeLanes() {
        assertEquals(-1, edgeScrollDirection(20f, extent = 800, edge = 64f))
        assertEquals(0, edgeScrollDirection(400f, extent = 800, edge = 64f))
        assertEquals(1, edgeScrollDirection(780f, extent = 800, edge = 64f))
    }

    @Test
    fun pagerTarget_invertsLogicalPageDirectionForScreenTravel() {
        assertEquals(-1f, pagerTargetOffset(pageDirection = 1), 0.001f)
        assertEquals(1f, pagerTargetOffset(pageDirection = -1), 0.001f)
        assertEquals(0f, pagerTargetOffset(pageDirection = 0), 0.001f)
    }

    @Test
    fun directionLock_waitsUntilOneAxisIsClearlyDominant() {
        assertEquals(null, dominantAxis(20f, 20f))
        assertEquals(GestureAxis.VERTICAL, dominantAxis(12f, 30f))
        assertEquals(GestureAxis.HORIZONTAL, dominantAxis(-30f, 12f))
    }
}
