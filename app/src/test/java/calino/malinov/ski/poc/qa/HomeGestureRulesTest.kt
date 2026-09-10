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
