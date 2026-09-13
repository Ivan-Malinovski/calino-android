package calino.malinov.ski.poc.ui.range

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.state.LocalCalinoNow
import calino.malinov.ski.poc.state.LocalCalinoPreferences
import calino.malinov.ski.poc.state.LocalTimeFormat
import calino.malinov.ski.poc.state.tasksDueOn
import calino.malinov.ski.poc.ui.components.CalinoMonthHeading
import calino.malinov.ski.poc.ui.components.CompactSegmentedControl
import calino.malinov.ski.poc.ui.home.HourRailContent
import calino.malinov.ski.poc.ui.home.DirectTimelineDrop
import calino.malinov.ski.poc.ui.surfaces.EventMenuAction
import calino.malinov.ski.poc.ui.surfaces.TaskMenuAction
import calino.malinov.ski.poc.util.CalinoRangeMode
import calino.malinov.ski.poc.util.EventDateIndex
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

private val RangeDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)

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
    var anchorEpoch by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorEpoch)
    val eventIndex = remember(events) { EventDateIndex.build(events) }
    var timelineScale by rememberSaveable { mutableFloatStateOf(1f) }
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
    val subtitle = "${visibleDays.first().format(RangeDate)} – ${visibleDays.last().format(RangeDate)}"
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
            showTodayButton = false,
            trailingContent = {
                CompactSegmentedControl(
                    options = listOf("3", "7"),
                    selectedIndex = CalinoRangeMode.entries.indexOf(mode),
                    onSelected = { preferences.setRangeMode(CalinoRangeMode.entries[it]) },
                    semanticLabel = "Range size in days",
                    modifier = Modifier.width(112.dp),
                )
            },
        )
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(androidx.compose.animation.core.tween(CalinoMotion.ContentEnterMillis)) togetherWith fadeOut(androidx.compose.animation.core.tween(CalinoMotion.FadeThroughMillis)) },
            modifier = Modifier.fillMaxSize(),
            label = "range mode",
        ) { activeMode ->
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                key = { page -> "${activeMode.name}:$page" },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageAnchor = rangeAnchorForPage(base, page, activeMode)
                val days = rangeDays(pageAnchor, activeMode, weekStart)
                RangePage(
                    days = days,
                    eventIndex = eventIndex,
                    tasks = tasks,
                    timelineScale = timelineScale,
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
}

@Composable
private fun RangePage(
    days: List<LocalDate>,
    eventIndex: EventDateIndex,
    tasks: List<CalTask>,
    timelineScale: Float,
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
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val scroll = rememberScrollState(with(density) { (9 * 62).dp.roundToPx() })
    val hideDone = LocalCalinoPreferences.current.hideCompletedTasks
    Box(Modifier.fillMaxSize().semantics { contentDescription = "${days.size}-day calendar" }) {
        Row(
            Modifier.fillMaxSize()
                .rangePinch { zoom -> onTimelineScaleChanged((timelineScale * zoom).coerceIn(.65f, 1.8f)) }
                .verticalScroll(scroll).padding(bottom = CalinoSpacing.PillClearance),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
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
                        draggingCardKey = null,
                        onCardBounds = null,
                        onCardGone = null,
                        showHourLabels = false,
                        compactRangeCards = true,
                        onEventDragEnd = { event, offset ->
                            val start = event.start ?: return@HourRailContent null
                            val columnWidthPx = with(density) { ((screenWidthDp - 52f) / days.size).dp.toPx() }
                            val dayDelta = (offset.x / columnWidthPx.coerceAtLeast(1f)).roundToInt()
                            val minuteDelta = ((offset.y / with(density) { (62 * timelineScale).dp.toPx() }) * 4f).roundToInt() * 15
                            val targetDay = day.plusDays(dayDelta.toLong())
                            val target = LocalDateTime.of(targetDay, start.toLocalTime().plusMinutes(minuteDelta.toLong()))
                            if (target == start) {
                                null
                            } else {
                                onEventTimeDrop(event, target)
                                DirectTimelineDrop(
                                    offset = androidx.compose.ui.geometry.Offset(
                                        x = dayDelta * columnWidthPx,
                                        y = with(density) { (62 * timelineScale).dp.toPx() } * minuteDelta / 60f,
                                    ),
                                    targetStart = target,
                                )
                            }
                        },
                    )
                }
            }
        }
        // Match the compact week strip: the day labels own content and input,
        // but paint no backing, so the scrolling hour grid remains visible.
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 4.dp)) {
                days.forEach { day ->
                    val due = remember(tasks, day, hideDone) { tasksDueOn(tasks, day).filterNot { hideDone && it.done } }
                    val allDay = remember(eventIndex, day) { eventIndex.eventsOn(day).filter { it.allDay } }
                    Column(
                        Modifier.weight(1f).padding(horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(), fontSize = 10.sp, color = CalinoColors.Ink3)
                        Text(day.dayOfMonth.toString(), fontSize = if (days.size == 7) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = CalinoColors.Ink)
                        allDay.firstOrNull()?.let { event ->
                            Text(
                                event.title,
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                    .background(CalinoColors.AccentSoft)
                                    .combinedClickable(
                                        onClick = { onEventClick(day, event) },
                                        onLongClick = { onEventAction(EventMenuAction.Edit, event) },
                                    ).padding(2.dp),
                                fontSize = 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        } ?: due.firstOrNull()?.let { task ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                    .background(CalinoColors.AccentSoft)
                                    .combinedClickable(
                                        onClick = { onTaskClick(task) },
                                        onLongClick = { onTaskAction(TaskMenuAction.Edit, task) },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(task.title, Modifier.weight(1f).padding(start = 2.dp), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (task.done) "✓" else "○",
                                    Modifier.width(28.dp).height(44.dp).clickable { onTaskDone(task, !task.done) }
                                        .semantics { contentDescription = if (task.done) "Mark ${task.title} open" else "Complete ${task.title}" }
                                        .padding(top = 13.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        if (allDay.size + due.size > 1) Text("+${allDay.size + due.size - 1}", fontSize = 8.sp, color = CalinoColors.Ink3)
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line))
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
    Box(Modifier.width(52.dp).height((62 * timelineScale * 24).dp)) {
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
