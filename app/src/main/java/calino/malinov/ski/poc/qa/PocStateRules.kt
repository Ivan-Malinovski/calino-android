package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.model.CalTask
import java.time.LocalDate
import kotlin.math.abs

/** Small, pure support rules for deterministic POC QA. Not production navigation state. */
enum class ZoomRestState(val level: Int) {
    DAY(0),
    MONTH(1),
    DETAIL(2),
}

fun nearestZoomRest(zoom: Float): ZoomRestState {
    val clamped = zoom.coerceIn(0f, 2f)
    return ZoomRestState.entries.minWithOrNull(
        compareBy<ZoomRestState> { abs(it.level - clamped) }.thenByDescending { it.level },
    ) ?: ZoomRestState.DAY
}

/** Gesture ownership is locked once one axis is clearly dominant. */
enum class GestureAxis { HORIZONTAL, VERTICAL }

fun dominantAxis(dx: Float, dy: Float, dominanceRatio: Float = 1.15f): GestureAxis? = when {
    abs(dx) >= abs(dy) * dominanceRatio -> GestureAxis.HORIZONTAL
    abs(dy) >= abs(dx) * dominanceRatio -> GestureAxis.VERTICAL
    else -> null
}

/**
 * Apply a vertical screen-space drag. Positive y is a pull down and expands
 * the calendar; negative y collapses it. Keeping this rule pure makes the
 * direction contract explicit for gesture tests and callers.
 */
fun zoomAfterVerticalDrag(zoom: Float, dragDeltaDp: Float, stepDp: Float = 280f): Float =
    (zoom + dragDeltaDp / stepDp.coerceAtLeast(1f)).coerceIn(0f, 2f)

/**
 * Settle relative to the level where the drag began. A level changes after
 * 60% of a step, or after an intentional fling in that direction. Positive
 * velocity means a downward pull toward the more detailed level.
 */
fun zoomSettleLevel(
    zoom: Float,
    anchorLevel: Int,
    zoomVelocityDpPerSecond: Float = 0f,
    stepFraction: Float = 0.60f,
    flingThresholdDpPerSecond: Float = 650f,
): Int {
    val anchor = anchorLevel.coerceIn(0, 2)
    val delta = zoom.coerceIn(0f, 2f) - anchor
    val direction = when {
        abs(zoomVelocityDpPerSecond) >= flingThresholdDpPerSecond ->
            if (zoomVelocityDpPerSecond > 0f) 1 else -1
        abs(delta) >= stepFraction -> if (delta > 0f) 1 else -1
        else -> 0
    }
    return (anchor + direction).coerceIn(0, 2)
}

fun shouldPage(
    distanceDp: Float,
    velocityDpPerSecond: Float,
    distanceThresholdDp: Float = 64f,
    velocityThresholdDpPerSecond: Float = 900f,
): Boolean = abs(distanceDp) >= distanceThresholdDp ||
    (abs(distanceDp) >= 18f && abs(velocityDpPerSecond) >= velocityThresholdDpPerSecond)

/**
 * Pager offsets use screen travel coordinates: a left swipe is negative and
 * reveals the next page from the right. The settled target is therefore the
 * inverse of the logical date direction.
 */
fun pagerTargetOffset(pageDirection: Int): Float =
    -pageDirection.coerceIn(-1, 1).toFloat()

data class QaCalendarState(
    val zoom: Float,
    val selectedDate: LocalDate,
)

/** A zoom settle changes density only; the selected day remains the same. */
fun QaCalendarState.settleZoom(zoom: Float): QaCalendarState = copy(
    zoom = nearestZoomRest(zoom).level.toFloat(),
)

enum class TaskBucket {
    OVERDUE,
    TODAY,
    THIS_WEEK,
    LATER,
    NO_DATE,
    DONE,
}

/** Fixture task grouping, anchored to an explicit date rather than the device clock. */
fun taskBucket(task: CalTask, today: LocalDate): TaskBucket = when {
    task.done -> TaskBucket.DONE
    task.due == null -> TaskBucket.NO_DATE
    task.due.isBefore(today) -> TaskBucket.OVERDUE
    task.due == today -> TaskBucket.TODAY
    task.due.isBefore(today.plusDays(7)) -> TaskBucket.THIS_WEEK
    else -> TaskBucket.LATER
}
