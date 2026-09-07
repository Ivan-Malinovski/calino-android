package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.model.CalTask
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PocStateRulesTest {
    private val fixtureDate = LocalDate.of(2026, 5, 18)

    @Test
    fun zoom_settlesToNearestRestState_andPreservesSelectedDate() {
        assertEquals(ZoomRestState.DAY, nearestZoomRest(0.49f))
        assertEquals(ZoomRestState.MONTH, nearestZoomRest(0.5f))
        assertEquals(ZoomRestState.DETAIL, nearestZoomRest(1.6f))

        val state = QaCalendarState(zoom = 0.8f, selectedDate = fixtureDate)
        assertEquals(QaCalendarState(1f, fixtureDate), state.settleZoom(0.8f))
    }

    @Test
    fun zoom_restState_clampsOverscroll() {
        assertEquals(ZoomRestState.DAY, nearestZoomRest(-2f))
        assertEquals(ZoomRestState.DETAIL, nearestZoomRest(9f))
    }

    @Test
    fun zoom_settle_requiresSixtyPercent_unlessVelocityIsIntentional() {
        assertEquals(0, zoomSettleLevel(0.59f, anchorLevel = 0))
        assertEquals(1, zoomSettleLevel(0.60f, anchorLevel = 0))
        assertEquals(1, zoomSettleLevel(1.41f, anchorLevel = 1))
        assertEquals(0, zoomSettleLevel(0.40f, anchorLevel = 1))
        assertEquals(2, zoomSettleLevel(1.12f, anchorLevel = 1, zoomVelocityDpPerSecond = 700f))
        assertEquals(0, zoomSettleLevel(0.88f, anchorLevel = 1, zoomVelocityDpPerSecond = -700f))
        assertEquals(2, zoomSettleLevel(2f, anchorLevel = 2, zoomVelocityDpPerSecond = 900f))
    }

    @Test
    fun paging_usesDpDistance_andVelocityThresholds() {
        assertEquals(false, shouldPage(distanceDp = 63.9f, velocityDpPerSecond = 0f))
        assertEquals(true, shouldPage(distanceDp = 64f, velocityDpPerSecond = 0f))
        assertEquals(false, shouldPage(distanceDp = 17f, velocityDpPerSecond = 1_500f))
        assertEquals(true, shouldPage(distanceDp = 20f, velocityDpPerSecond = 950f))
        assertEquals(GestureAxis.HORIZONTAL, dominantAxis(30f, 10f))
        assertEquals(GestureAxis.VERTICAL, dominantAxis(10f, 30f))
        assertEquals(null, dominantAxis(20f, 20f))
    }

    @Test
    fun taskBuckets_useExplicitFixtureDate_andDoneWins() {
        val overdue = task("overdue", fixtureDate.minusDays(1))
        val thisWeek = task("today", fixtureDate)
        val noDate = task("inbox", null)
        val doneOverdue = task("done", fixtureDate.minusDays(30), done = true)

        assertEquals(TaskBucket.OVERDUE, taskBucket(overdue, fixtureDate))
        assertEquals(TaskBucket.TODAY, taskBucket(thisWeek, fixtureDate))
        assertEquals(TaskBucket.THIS_WEEK, taskBucket(task("week", fixtureDate.plusDays(3)), fixtureDate))
        assertEquals(TaskBucket.LATER, taskBucket(task("later", fixtureDate.plusDays(8)), fixtureDate))
        assertEquals(TaskBucket.NO_DATE, taskBucket(noDate, fixtureDate))
        assertEquals(TaskBucket.DONE, taskBucket(doneOverdue, fixtureDate))
    }

    private fun task(id: String, due: LocalDate?, done: Boolean = false) = CalTask(
        id = id,
        title = id,
        color = 0xFF5D9A78,
        due = due,
        done = done,
        category = "QA",
    )
}
