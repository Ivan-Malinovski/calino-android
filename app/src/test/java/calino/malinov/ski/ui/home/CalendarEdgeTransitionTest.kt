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

    @Test fun oneDragNeverCrossesMoreThanOneView() {
        // From the month a long pull stops at the day surface or the compact week,
        // instead of running on into the Year or Agenda edge.
        assertEquals(2f, calendarZoomWithinOneStep(calendarZoomAfterDrag(1f, 900f), anchorLevel = 1), .0001f)
        assertEquals(0f, calendarZoomWithinOneStep(calendarZoomAfterDrag(1f, -900f), anchorLevel = 1), .0001f)
        // From the compact week a long pull down stops at the month.
        assertEquals(1f, calendarZoomWithinOneStep(calendarZoomAfterDrag(0f, 900f), anchorLevel = 0), .0001f)
        assertEquals(1f, calendarZoomWithinOneStep(calendarZoomAfterDrag(2f, -900f), anchorLevel = 2), .0001f)
        // The outward edge past each endpoint stays reachable from that endpoint.
        assertEquals(-1f, calendarZoomWithinOneStep(calendarZoomAfterDrag(0f, -500f), anchorLevel = 0), .0001f)
        assertEquals(3f, calendarZoomWithinOneStep(calendarZoomAfterDrag(2f, 500f), anchorLevel = 2), .0001f)
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
