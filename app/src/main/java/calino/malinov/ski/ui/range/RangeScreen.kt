package calino.malinov.ski.ui.range

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.qa.edgeScrollDirection
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.ui.components.AllDayBand
import calino.malinov.ski.ui.components.AllDayBandDensity
import calino.malinov.ski.ui.components.CalinoMonthHeading
import calino.malinov.ski.ui.components.CompactSegmentedControl
import calino.malinov.ski.ui.home.CompactLaneScrim
import calino.malinov.ski.ui.home.HourRailContent
import calino.malinov.ski.ui.home.TimelineCardBounds
import calino.malinov.ski.ui.home.TimelineEventCard
import calino.malinov.ski.ui.surfaces.EventMenuAction
import calino.malinov.ski.ui.surfaces.TaskMenuAction
import calino.malinov.ski.util.CalinoRangeMode
import calino.malinov.ski.util.EventDateIndex
import calino.malinov.ski.util.layoutAllDayBand
import calino.malinov.ski.util.resolveAllDaySpans
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.distinctUntilChanged

private val RangeDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** The range pager, addressed by tag the way the calendar pagers are. */
const val RangePagerTag = "range-pager"

@Composable
fun RangeScreen(
    events: List<CalEvent>,
    tasks: List<CalTask>,
    initialDate: LocalDate,
    modifier: Modifier = Modifier,
    onOpenMenu: () -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    onEventClick: (LocalDate, CalEvent) -> Unit,
    onEventAction: (EventMenuAction, CalEvent) -> Unit,
    onEventDrop: (CalEvent, LocalDate) -> Unit,
    onEventTimeDrop: (CalEvent, LocalDateTime) -> Unit,
    onCreateEventAt: (LocalDateTime) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
) {
    val preferences = LocalCalinoPreferences.current
    val mode = preferences.rangeMode
    val weekStart = preferences.weekStart
    val today = LocalCalinoNow.current.today
    val density = LocalDensity.current
    var anchorEpoch by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorEpoch)
    val eventIndex = remember(events) { EventDateIndex.build(events) }
    var timelineScale by rememberSaveable { mutableFloatStateOf(1f) }
    val timelineScroll = rememberScrollState(with(density) { (9 * 62).dp.roundToPx() })
    var pagerGeneration by rememberSaveable(mode, weekStart) { mutableIntStateOf(0) }
    var pagerBaseEpoch by rememberSaveable(mode, weekStart) { mutableLongStateOf(anchorEpoch) }
    val pager = key(mode, weekStart, pagerGeneration) {
        rememberPagerState(initialPage = RangePagerCenter) { RangePagerPageCount }
    }
    val base = LocalDate.ofEpochDay(pagerBaseEpoch)

    LaunchedEffect(initialDate) { anchorEpoch = initialDate.toEpochDay() }
    LaunchedEffect(pager, mode) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }
            .distinctUntilChanged()
            .collect { (scrolling, page) ->
                if (!scrolling) {
                    val next = rangeAnchorForPage(base, page, mode)
                    if (next.toEpochDay() != anchorEpoch) {
                        anchorEpoch = next.toEpochDay()
                        onDateChanged(next)
                    }
                }
            }
    }

    val visibleDays = rangeDays(anchor, mode, weekStart)
    val subtitle = if (visibleDays.size == 1) {
        visibleDays.single().format(RangeDate)
    } else {
        "${visibleDays.first().format(RangeDate)} – ${visibleDays.last().format(RangeDate)}"
    }
    Column(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        CalinoMonthHeading(
            day = visibleDays.first(),
            onOpenMenu = onOpenMenu,
            onPreviousMonth = {},
            onNextMonth = {},
            onToday = {
                pagerBaseEpoch = today.toEpochDay()
                anchorEpoch = today.toEpochDay()
                onDateChanged(today)
                pagerGeneration += 1
            },
            showToday = today !in visibleDays,
            subtitle = subtitle,
            showNavigationArrows = false,
            showTodayButton = true,
            trailingContent = {
                CompactSegmentedControl(
                    options = listOf("1", "3", "7"),
                    selectedIndex = CalinoRangeMode.entries.indexOf(mode),
                    onSelected = { preferences.setRangeMode(CalinoRangeMode.entries[it]) },
                    semanticLabel = "Range size in days",
                    modifier = Modifier.width(144.dp),
                )
            },
        )
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(androidx.compose.animation.core.tween(CalinoMotion.ContentEnterMillis)) togetherWith fadeOut(androidx.compose.animation.core.tween(CalinoMotion.FadeThroughMillis)) },
            modifier = Modifier.fillMaxSize(),
            label = "range mode",
        ) { activeMode ->
            RangePagerSurface(
                pager = pager,
                activeMode = activeMode,
                base = base,
                weekStart = weekStart,
                eventIndex = eventIndex,
                tasks = tasks,
                timelineScale = timelineScale,
                timelineScroll = timelineScroll,
                onTimelineScaleChanged = { timelineScale = it },
                onEventClick = onEventClick,
                onEventAction = onEventAction,
                onEventDrop = onEventDrop,
                onEventTimeDrop = onEventTimeDrop,
                onCreateEventAt = onCreateEventAt,
                onTaskClick = onTaskClick,
                onTaskAction = onTaskAction,
                onTaskDone = onTaskDone,
            )
        }
    }
}

@Composable
private fun RangePagerSurface(
    pager: androidx.compose.foundation.pager.PagerState,
    activeMode: CalinoRangeMode,
    base: LocalDate,
    weekStart: calino.malinov.ski.util.CalinoWeekStart,
    eventIndex: EventDateIndex,
    tasks: List<CalTask>,
    timelineScale: Float,
    timelineScroll: ScrollState,
    onTimelineScaleChanged: (Float) -> Unit,
    onEventClick: (LocalDate, CalEvent) -> Unit,
    onEventAction: (EventMenuAction, CalEvent) -> Unit,
    onEventDrop: (CalEvent, LocalDate) -> Unit,
    onEventTimeDrop: (CalEvent, LocalDateTime) -> Unit,
    onCreateEventAt: (LocalDateTime) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val timeFormat = LocalTimeFormat
    val preferences = LocalCalinoPreferences.current
    val cardBounds = remember { mutableStateMapOf<String, TimelineCardBounds>() }
    var drag by remember { mutableStateOf<RangeDragSession?>(null) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var hostWidth by remember { mutableIntStateOf(0) }
    var hostHeight by remember { mutableIntStateOf(0) }
    var edgeDirection by remember { mutableIntStateOf(0) }
    var autoScrollDirection by remember { mutableIntStateOf(0) }
    val hourHeightPx = with(density) { (62 * timelineScale).dp.toPx() }

    LaunchedEffect(edgeDirection, drag) {
        if (edgeDirection == 0 || drag == null) return@LaunchedEffect
        while (true) {
            delay(RangeEdgeTurnDelayMillis)
            val target = pager.currentPage + edgeDirection
            if (target !in 0 until pager.pageCount) break
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pager.animateScrollToPage(target)
        }
    }

    LaunchedEffect(autoScrollDirection, drag) {
        if (autoScrollDirection == 0 || drag == null) return@LaunchedEffect
        val step = with(density) { RangeAutoScrollStep.toPx() }
        while (true) {
            val consumed = timelineScroll.scrollBy(autoScrollDirection * step)
            if (consumed == 0f) break
            delay(RangeAutoScrollFrameMillis)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned {
                hostOrigin = it.positionInRoot()
                hostWidth = it.size.width
                hostHeight = it.size.height
            }
            .rangeTimelineLiftDrag(
                hitTest = { point ->
                    val root = point + hostOrigin
                    val visible = rangeDays(rangeAnchorForPage(base, pager.currentPage, activeMode), activeMode, weekStart)
                    cardBounds.values.firstOrNull { it.day in visible && it.rootRect.contains(root) }
                },
                onLift = { card, pointer ->
                    drag = RangeDragSession(card, pointer = pointer, scrollAtLift = timelineScroll.value)
                },
                onDrag = { delta, pointer ->
                    drag = drag?.let { it.copy(offset = it.offset + delta, pointer = pointer) }
                    edgeDirection = rangeEdgeDirection(pointer.x, hostWidth, with(density) { RangeEdgeTurnZone.toPx() })
                    autoScrollDirection = edgeScrollDirection(
                        pointer.y,
                        hostHeight,
                        with(density) { RangeAutoScrollEdge.toPx() },
                    )
                },
                onRelease = {
                    val session = drag
                    edgeDirection = 0
                    autoScrollDirection = 0
                    drag = null
                    if (session != null) {
                        val visible = rangeDays(rangeAnchorForPage(base, pager.currentPage, activeMode), activeMode, weekStart)
                        val day = rangeDropDay(
                            session.pointer.x,
                            hostWidth,
                            with(density) { CalinoSpacing.RailGutter.toPx() },
                            visible,
                        )
                        val start = session.card.event.start
                        if (day != null && start != null && hourHeightPx > 0f) {
                            val scrollDelta = timelineScroll.value - session.scrollAtLift
                            val minuteDelta = (((session.offset.y + scrollDelta) / hourHeightPx) * 4f).roundToInt() * 15
                            val target = LocalDateTime.of(day, start.toLocalTime().plusMinutes(minuteDelta.toLong()))
                            if (target != start) onEventTimeDrop(session.card.event, target)
                        }
                    }
                },
                onCancel = { edgeDirection = 0; autoScrollDirection = 0; drag = null },
            ),
    ) {
        HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                key = { page -> "${activeMode.name}:$page" },
                // Tagged like month-pager/week-pager/day-pager, so a device
                // test can scope a day query to this surface. Several grids are
                // mounted at once during a route change and day descriptions
                // are not unique across them.
                modifier = Modifier.fillMaxSize().testTag(RangePagerTag),
            ) { page ->
                val pageAnchor = rangeAnchorForPage(base, page, activeMode)
                val days = rangeDays(pageAnchor, activeMode, weekStart)
                RangePage(
                    days = days,
                    eventIndex = eventIndex,
                    tasks = tasks,
                    timelineScale = timelineScale,
                    timelineScroll = timelineScroll,
                    onTimelineScaleChanged = onTimelineScaleChanged,
                    onEventClick = onEventClick,
                    onEventAction = onEventAction,
                    onEventDrop = onEventDrop,
                    onEventTimeDrop = onEventTimeDrop,
                    onCreateEventAt = onCreateEventAt,
                    onTaskClick = onTaskClick,
                    onTaskAction = onTaskAction,
                    onTaskDone = onTaskDone,
                    draggingCardKey = drag?.card?.key,
                    onCardBounds = { cardBounds[it.key] = it },
                    onCardGone = { cardBounds.remove(it) },
                )
            }
        drag?.let { session ->
            val rect = session.card.rootRect
            TimelineEventCard(
                modifier = Modifier
                    .padding(0.dp)
                    .width(with(density) { rect.width.toDp() })
                    .height(with(density) { rect.height.toDp() })
                    .graphicsLayer {
                        translationX = rect.left - hostOrigin.x + session.offset.x
                        translationY = rect.top - hostOrigin.y + session.offset.y
                    },
                event = session.card.event,
                showMetadata = session.card.showMetadata,
                timeFormat = timeFormat,
                preferences = preferences,
                colors = CalinoColors,
                lifted = true,
                hideAccentRail = true,
            )
        }
    }
}

@Composable
private fun RangePage(
    days: List<LocalDate>,
    eventIndex: EventDateIndex,
    tasks: List<CalTask>,
    timelineScale: Float,
    timelineScroll: ScrollState,
    onTimelineScaleChanged: (Float) -> Unit,
    onEventClick: (LocalDate, CalEvent) -> Unit,
    onEventAction: (EventMenuAction, CalEvent) -> Unit,
    onEventDrop: (CalEvent, LocalDate) -> Unit,
    onEventTimeDrop: (CalEvent, LocalDateTime) -> Unit,
    onCreateEventAt: (LocalDateTime) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
    draggingCardKey: String?,
    onCardBounds: (TimelineCardBounds) -> Unit,
    onCardGone: (String) -> Unit,
) {
    val density = LocalDensity.current
    val hideDone = LocalCalinoPreferences.current.hideCompletedTasks
    val railLayer = rememberGraphicsLayer()
    var stripHeight by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize().semantics { contentDescription = "${days.size}-day calendar" }) {
        Row(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    railLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(railLayer)
                }
                .rangePinch { zoom -> onTimelineScaleChanged((timelineScale * zoom).coerceIn(.65f, 1.8f)) }
                .verticalScroll(timelineScroll).padding(bottom = CalinoSpacing.PillClearance),
            horizontalArrangement = Arrangement.spacedBy(CalinoSpacing.RailColumnGap),
        ) {
            RangeHourGutter(timelineScale)
            days.forEach { day ->
                val timed = remember(eventIndex, day) { eventIndex.eventsOn(day).filterNot { it.allDay } }
                Box(
                    Modifier.weight(1f).border(0.5.dp, CalinoColors.Line2)
                        .rangeEmptyDoubleTap(
                            isOccupied = { point ->
                                val minute = point.y /
                                    with(density) { (62 * timelineScale).dp.toPx() } * 60f
                                timed.any { event ->
                                    val start = event.start ?: return@any false
                                    val startMinute = start.hour * 60 + start.minute
                                    val duration = event.durationMinutes ?: 30
                                    minute >= startMinute && minute <= startMinute + duration
                                }
                            },
                            onDoubleTap = { point ->
                                val raw = (point.y / with(density) { (62 * timelineScale).dp.toPx() } * 60f)
                                val minute = ((raw / 15f).roundToInt() * 15).coerceIn(0, 23 * 60 + 45)
                                onCreateEventAt(LocalDateTime.of(day, java.time.LocalTime.MIDNIGHT.plusMinutes(minute.toLong())))
                            },
                        ),
                ) {
                    HourRailContent(
                        day = day,
                        dayEvents = timed,
                        onEvent = { onEventClick(day, it) },
                        onEventAction = onEventAction,
                        onEventDrop = onEventDrop,
                        timelineScale = timelineScale,
                        draggingCardKey = draggingCardKey,
                        onCardBounds = onCardBounds,
                        onCardGone = onCardGone,
                        showHourLabels = false,
                        compactRangeCards = true,
                        onEventDragEnd = null,
                    )
                }
            }
        }
        // Animated rather than following `stripHeight` directly: that height
        // is one frame behind its own content (measured via onSizeChanged),
        // so an expanding band would otherwise make the scrim step instead of
        // grow smoothly under it.
        val scrimHeight by animateDpAsState(stripHeight + 10.dp, CalinoMotion.standardSpatial(), label = "range strip scrim height")
        CompactLaneScrim(
            source = railLayer,
            blend = { 1f },
            modifier = Modifier.fillMaxWidth().height(scrimHeight),
        )
        // Match the compact week strip: content and input sit over the same
        // translucent, blurred copy of the hour rail instead of an opaque bar.
        Column(
            Modifier.fillMaxWidth().onSizeChanged { size ->
                stripHeight = with(density) { size.height.toDp() }
            },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = CalinoSpacing.RailGutter + CalinoSpacing.RailColumnGap),
                horizontalArrangement = Arrangement.spacedBy(CalinoSpacing.RailColumnGap),
            ) {
                days.forEach { day ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(), fontSize = 10.sp, color = CalinoColors.Ink3)
                        Text(day.dayOfMonth.toString(), fontSize = if (days.size == 7) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = CalinoColors.Ink)
                    }
                }
            }
            // Filtered once, ahead of the packer, rather than per day inside
            // it -- otherwise toggling "hide completed" would not relayout.
            val visibleTasks = remember(tasks, hideDone) { if (hideDone) tasks.filterNot { it.done } else tasks }
            val spans = remember(eventIndex, days) { resolveAllDaySpans(days, eventIndex::eventsOn) }
            var bandExpanded by rememberSaveable(days.first(), days.size) { mutableStateOf(false) }
            val bandLayout = remember(spans, visibleTasks, days, bandExpanded) {
                layoutAllDayBand(days, spans, visibleTasks, if (bandExpanded) Int.MAX_VALUE else RangeBandLaneLimit)
            }
            AllDayBand(
                days = days,
                layout = bandLayout,
                density = AllDayBandDensity.Narrow,
                gutterWidth = CalinoSpacing.RailGutter + CalinoSpacing.RailColumnGap,
                columnGap = CalinoSpacing.RailColumnGap,
                edgeWidth = 0.dp,
                expanded = bandExpanded,
                onExpandedChange = { bandExpanded = it },
                onEventClick = onEventClick,
                onEventAction = onEventAction,
                onTaskClick = onTaskClick,
                onTaskAction = onTaskAction,
                onTaskDone = onTaskDone,
            )
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line))
        }
    }
}

private data class RangeDragSession(
    val card: TimelineCardBounds,
    val offset: Offset = Offset.Zero,
    val pointer: Offset,
    val scrollAtLift: Int,
)

/** Lanes shown at rest before the all-day band's overflow row takes over. */
private const val RangeBandLaneLimit = 2

private val RangeEdgeTurnZone = 52.dp
private const val RangeEdgeTurnDelayMillis = 420L
private val RangeAutoScrollEdge = 64.dp
private val RangeAutoScrollStep = 10.dp
private const val RangeAutoScrollFrameMillis = 16L

private fun Modifier.rangeTimelineLiftDrag(
    hitTest: (Offset) -> TimelineCardBounds?,
    onLift: (TimelineCardBounds, Offset) -> Unit,
    onDrag: (Offset, Offset) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
): Modifier = composed {
    val currentHitTest by rememberUpdatedState(hitTest)
    val currentOnLift by rememberUpdatedState(onLift)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val haptics = LocalHapticFeedback.current
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pointerId = down.id
            val startedAt = android.os.SystemClock.uptimeMillis()
            val liftDelay = minOf(viewConfiguration.longPressTimeoutMillis.toLong(), 220L)
            var lifted = false
            var finished = false
            try {
                while (!finished) {
                    val remaining = liftDelay - (android.os.SystemClock.uptimeMillis() - startedAt)
                    val event = if (!lifted && remaining > 0) {
                        withTimeoutOrNull(remaining) {
                            awaitPointerEvent(PointerEventPass.Initial)
                        }
                    } else {
                        awaitPointerEvent(PointerEventPass.Initial)
                    }
                    if (event == null) {
                        val card = currentHitTest(down.position) ?: break
                        lifted = true
                        currentOnLift(card, down.position)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        continue
                    }
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                        if (lifted) currentOnRelease()
                        finished = true
                    } else if (!lifted && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        break
                    } else if (lifted) {
                        change.consume()
                        currentOnDrag(change.positionChangeIgnoreConsumed(), change.position)
                    }
                }
            } finally {
                if (lifted && !finished) currentOnCancel()
            }
        }
    }
}

/**
 * Observes taps on a day column without claiming the pointer stream from event
 * cards, scrolling, or the horizontal pager. Only a second stationary tap on
 * genuinely empty timeline space creates an event.
 */
private fun Modifier.rangeEmptyDoubleTap(
    isOccupied: (androidx.compose.ui.geometry.Offset) -> Boolean,
    onDoubleTap: (androidx.compose.ui.geometry.Offset) -> Unit,
): Modifier = composed {
    val currentIsOccupied by rememberUpdatedState(isOccupied)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    pointerInput(Unit) {
        var previousTapTime = 0L
        var previousTapPosition = androidx.compose.ui.geometry.Offset.Unspecified
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val pointerId = down.id
            var moved = false
            var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
            while (up == null) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (!change.pressed) up = change
            }
            val release = up
            if (!moved && release != null && !currentIsOccupied(release.position)) {
                val elapsed = release.uptimeMillis - previousTapTime
                val closeEnough = previousTapPosition.isSpecified &&
                    (release.position - previousTapPosition).getDistance() <= viewConfiguration.touchSlop * 2f
                if (elapsed in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis && closeEnough) {
                    currentOnDoubleTap(release.position)
                    previousTapTime = 0L
                    previousTapPosition = androidx.compose.ui.geometry.Offset.Unspecified
                } else {
                    previousTapTime = release.uptimeMillis
                    previousTapPosition = release.position
                }
            } else {
                previousTapTime = 0L
                previousTapPosition = androidx.compose.ui.geometry.Offset.Unspecified
            }
        }
    }
}

private fun Modifier.rangePinch(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var previousSpan: Float? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val span = (pressed[0].position - pressed[1].position).getDistance()
                previousSpan?.takeIf { it > 0f }?.let { onZoom(span / it) }
                previousSpan = span
                pressed.forEach { it.consume() }
            } else {
                previousSpan = null
            }
        }
    }
}

@Composable
private fun RangeHourGutter(timelineScale: Float) {
    val timeFormat = LocalTimeFormat
    Box(Modifier.width(CalinoSpacing.RailGutter).height((62 * timelineScale * 24).dp)) {
        (0..23).forEach { hour ->
            Text(
                timeFormat.formatHour(hour),
                Modifier.padding(start = 8.dp, top = (hour * 62 * timelineScale).dp),
                fontSize = 10.sp,
                color = CalinoColors.Ink3,
                maxLines = 1,
            )
        }
    }
}
