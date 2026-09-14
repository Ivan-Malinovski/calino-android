package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalTask
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * The one rendering contract for the compact-week, split-month and day-surface
 * transition. Every value is derived from the live zoom sample, so reversing a
 * drag produces the exact reverse frame instead of waiting for another state
 * holder to catch up.
 */
data class CalendarTransitionFrame(
    val zoom: Float,
    val compactProgress: Float,
    val detailProgress: Float,
    val unfoldProgress: Float,
    val railVisible: Boolean,
    val agendaVisible: Boolean,
)

private const val CalendarUnfoldStart = .20f
private const val CalendarUnfoldEnd = .84f

fun calendarTransitionFrame(zoom: Float): CalendarTransitionFrame {
    val clamped = zoom.coerceIn(0f, 2f)
    val compactZoom = clamped.coerceIn(0f, 1f)
    val unfold = monthUnfoldPhase(compactZoom, CalendarUnfoldStart, CalendarUnfoldEnd)
    return CalendarTransitionFrame(
        zoom = clamped,
        compactProgress = smoothStep01(1f - compactZoom),
        detailProgress = smoothStep01((clamped - 1f).coerceIn(0f, 1f)),
        unfoldProgress = unfold,
        railVisible = unfold < 1f,
        agendaVisible = unfold > 0f && clamped < 1.99f,
    )
}

/** Input ownership follows the shared visual frame with hysteresis. */
fun agendaOwnsCalendarInput(
    frame: CalendarTransitionFrame,
    currentlyOwns: Boolean,
    hysteresis: Float = .08f,
): Boolean = when {
    !frame.railVisible && !frame.agendaVisible -> false
    !frame.railVisible -> true
    !frame.agendaVisible -> false
    currentlyOwns -> frame.unfoldProgress >= .5f - hysteresis
    else -> frame.unfoldProgress > .5f + hysteresis
}

/**
 * A horizontally moving week page may replace the canvas only while that week
 * pager is actually mounted at the compact endpoint.
 */
fun monthCanvasVisibleDuringWeekPreview(
    zoom: Float,
    weekPreviewActive: Boolean,
    endpointHandoff: Float = .18f,
): Boolean = !weekPreviewActive || zoom >= endpointHandoff

private fun smoothStep01(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A reversible, finger-driven phase within the compact-week to month morph. */
fun monthUnfoldPhase(zoom: Float, start: Float, end: Float): Float {
    if (end <= start) return if (zoom >= end) 1f else 0f
    val t = ((zoom.coerceIn(0f, 1f) - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Rows nearest the selected-week hinge become legible first. */
fun monthRowReveal(zoom: Float, row: Int, hingeRow: Int): Float {
    val distance = abs(row - hingeRow)
    if (distance == 0) return 1f
    val stagger = .055f * (distance - 1)
    return monthUnfoldPhase(zoom, start = .20f + stagger, end = .78f + stagger)
}

/** Direction and remaining amount of the small translation toward the hinge. */
fun monthRowHingeOffset(zoom: Float, row: Int, hingeRow: Int): Float = when {
    row < hingeRow -> 1f - monthRowReveal(zoom, row, hingeRow)
    row > hingeRow -> -(1f - monthRowReveal(zoom, row, hingeRow))
    else -> 0f
}

fun monthSelectorMorphProgress(zoom: Float): Float = monthUnfoldPhase(zoom, .06f, .46f)

/**
 * A downward pull from the day rail expands the calendar only at the rail's
 * top boundary. Every other vertical pull belongs to the rail's scroll
 * container, so a time-line drag cannot accidentally move the calendar.
 */
fun shouldExpandFromDayRail(dragDeltaY: Float, railScrollValue: Int): Boolean =
    dragDeltaY > 0f && railScrollValue <= 0

/** Apply a pinch multiplier to the timeline's bounded vertical scale. */
fun timelineScaleAfterPinch(
    scale: Float,
    pinchFactor: Float,
    minScale: Float = .65f,
    maxScale: Float = 1.8f,
): Float = (scale * pinchFactor).coerceIn(minScale, maxScale)

/** Snap a touched timeline position to a valid event start interval. */
fun timelineCreateMinute(rawMinute: Float, intervalMinutes: Int): Int? {
    if (rawMinute < 0f || rawMinute >= 24f * 60f) return null
    val interval = intervalMinutes.coerceAtLeast(1)
    val latestStart = ((24 * 60 - 1) / interval) * interval
    return ((rawMinute / interval).roundToInt() * interval).coerceIn(0, latestStart)
}

/** Direction for drag auto-scroll when a pointer enters an edge lane. */
fun edgeScrollDirection(pointer: Float, extent: Int, edge: Float): Int = when {
    extent <= 0 || edge <= 0f -> 0
    pointer < edge -> -1
    pointer > extent - edge -> 1
    else -> 0
}

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
