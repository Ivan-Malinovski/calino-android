package calino.malinov.ski.poc.qa

import org.junit.Assert.assertEquals
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
