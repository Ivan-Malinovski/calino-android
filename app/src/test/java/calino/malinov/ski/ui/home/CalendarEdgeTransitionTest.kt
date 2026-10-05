package calino.malinov.ski.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEdgeTransitionTest {
    @Test fun crossingBothEndpointsIsReversibleInScreenDistance() {
        for (zoom in listOf(-.7f, 0f, .2f, 1.8f, 2f, 2.7f)) {
            val outward = if (zoom >= 1f) 50f else -50f
            assertEquals(zoom, calendarZoomAfterDrag(calendarZoomAfterDrag(zoom, outward), -outward), .0001f)
        }
        assertEquals(.5f, calendarZoomAfterDrag(0f, 140f), .0001f)
        assertEquals(-1f, calendarZoomAfterDrag(0f, -500f), .0001f)
        assertEquals(3f, calendarZoomAfterDrag(2f, 500f), .0001f)
    }

    @Test fun slowReleaseNeedsIntentionalTravel() {
        assertFalse(commitCalendarEdge(.44f, 0f))
        assertTrue(commitCalendarEdge(.45f, 0f))
    }

    @Test fun flingNeedsTravelAndReversingAlwaysReturns() {
        assertFalse(commitCalendarEdge(.02f, 900f))
        assertTrue(commitCalendarEdge(.10f, 900f))
        assertFalse(commitCalendarEdge(.80f, -900f))
    }
}
