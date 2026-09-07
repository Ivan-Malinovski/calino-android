package calino.malinov.ski.poc.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.occursOn
import calino.malinov.ski.poc.data.repository.CalinoRepository
import calino.malinov.ski.poc.data.repository.FixtureRepository
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.design.eventTint
import calino.malinov.ski.poc.ui.components.CalinoIcons
import calino.malinov.ski.poc.qa.zoomAfterVerticalDrag
import calino.malinov.ski.poc.qa.zoomSettleLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

private val FixtureDate = LocalDate.of(2026, 5, 18)
private const val DaytimeScrollHour = 9
private const val ZoomStepDp = 280f
private const val DayPagerCenter = 100_000
private const val DayPagerPageCount = DayPagerCenter * 2 + 1
private const val MonthPagerCenter = 10_000
private const val MonthPagerPageCount = MonthPagerCenter * 2 + 1
private const val CompactMonthRowPlaceholderThreshold = .62f
private const val CollapsedMonthRowThreshold = .92f
private const val CompactMonthRowCrossfadeHalfWidth = .06f
private const val CollapsedMonthRowCrossfadeHalfWidth = .06f
private const val DaySurfaceOwnershipHysteresis = .08f
private val WeekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
private val TimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val ShortDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val AgendaDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val AddDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

private data class BoundaryPagerSnapshot(
    val compactBoundaryDay: LocalDate?,
    val committedEpochDay: Long,
    val dayInProgress: Boolean,
    val dayTargetPage: Int,
    val daySettledPage: Int,
    val monthInProgress: Boolean,
    val monthSettledPage: Int,
)

private data class EventDensitySummary(
    val allDayCount: Int,
    val precedingAllDayCounts: IntArray,
)

private fun dayPageFor(date: LocalDate): Int =
    (DayPagerCenter.toLong() + date.toEpochDay() - FixtureDate.toEpochDay())
        .coerceIn(0L, (DayPagerPageCount - 1).toLong())
        .toInt()

private fun dateForDayPage(page: Int): LocalDate =
    FixtureDate.plusDays((page - DayPagerCenter).toLong())

private fun monthPageFor(month: YearMonth): Int {
    val fixtureMonth = YearMonth.from(FixtureDate)
    return (MonthPagerCenter + (month.year - fixtureMonth.year) * 12 + month.monthValue - fixtureMonth.monthValue)
        .coerceIn(0, MonthPagerPageCount - 1)
}

private fun monthForPage(page: Int): YearMonth =
    YearMonth.from(FixtureDate).plusMonths((page - MonthPagerCenter).toLong())

/**
 * The native, single-surface calendar. The host only needs to call
 * [HomeScreen]. The optional [onOpenDay] callback is an explicit open affordance
 * for the split view; all existing callers remain source-compatible.
 */
@Composable
fun HomeScreen(
    repository: CalinoRepository = remember { FixtureRepository() },
    journals: List<JournalEntry> = emptyList(),
    modifier: Modifier = Modifier,
    initialDate: LocalDate = FixtureDate,
    onAdd: (LocalDate) -> Unit = {},
    onDateChanged: (LocalDate) -> Unit = {},
    onDayClick: ((LocalDate) -> Unit)? = null,
    onEventClick: ((CalEvent) -> Unit)? = null,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    interactionEnabled: Boolean = true,
) {
    var selectedEpoch by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    var zoom by rememberSaveable { mutableFloatStateOf(0f) }
    LaunchedEffect(initialDate) { selectedEpoch = initialDate.toEpochDay() }

    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val events = repository.events()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val railScroll = rememberScrollState(initial = with(density) { (DaytimeScrollHour * 62).dp.roundToPx() })
    val dayPagerState = rememberPagerState(initialPage = dayPageFor(initialDate)) { DayPagerPageCount }
    val monthPagerState = rememberPagerState(initialPage = monthPageFor(YearMonth.from(initialDate))) { MonthPagerPageCount }
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    val currentZoom = rememberUpdatedState(zoom)
    val currentSelectedEpoch = rememberUpdatedState(selectedEpoch)

    fun cancelMotion() {
        zoomJob?.cancel()
        zoomJob = null
    }

    fun animateZoomTo(targetValue: Float) {
        cancelMotion()
        val target = targetValue.coerceIn(0f, 2f)
        zoomJob = scope.launch {
            animate(
                initialValue = zoom,
                targetValue = target,
                animationSpec = spring(dampingRatio = .85f, stiffness = 380f),
            ) { value, _ -> zoom = value.coerceIn(0f, 2f) }
            zoom = target
        }
    }

    LaunchedEffect(interactionEnabled) {
        if (!interactionEnabled) {
            cancelMotion()
        }
    }

    // Commit a day only after the official pager has settled. During the drag,
    // the selected date remains stable while the visible page is allowed to
    // preview its neighbor.
    LaunchedEffect(dayPagerState) {
        snapshotFlow { dayPagerState.settledPage }
            .distinctUntilChanged()
            .collect {
                val date = dateForDayPage(it)
                if (date.toEpochDay() != currentSelectedEpoch.value) {
                    selectedEpoch = date.toEpochDay()
                    onDateChanged(date)
                }
            }
    }

    // A month swipe preserves the selected day number where possible. The
    // date-keyed pages keep the grid's hit regions aligned with what is drawn.
    LaunchedEffect(monthPagerState) {
        snapshotFlow { monthPagerState.settledPage }
            .distinctUntilChanged()
            .collect {
                val targetMonth = monthForPage(it)
                val currentDate = LocalDate.ofEpochDay(currentSelectedEpoch.value)
                if (YearMonth.from(currentDate) != targetMonth) {
                    val date = targetMonth.atDay(currentDate.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth()))
                    selectedEpoch = date.toEpochDay()
                    onDateChanged(date)
                }
            }
    }

    // Keep both pagers in sync when a date is selected from another surface.
    // animateScrollToPage gives clicks and external date changes the same
    // physically continuous motion as a finger swipe.
    LaunchedEffect(selectedEpoch) {
        val targetDayPage = dayPageFor(selected)
        val targetMonthPage = monthPageFor(YearMonth.from(selected))
        // These are independent surfaces. Starting both children before
        // awaiting either one keeps a boundary day change from making the
        // month/week morph wait for the day agenda to finish. The selected
        // date is still committed only by the settled-page collectors above;
        // this effect only brings the visual pagers to an already committed
        // selection.
        launch {
            if (dayPagerState.currentPage != targetDayPage || abs(dayPagerState.currentPageOffsetFraction) > .001f) {
                dayPagerState.animateScrollToPage(targetDayPage)
            }
        }
        launch {
            if (monthPagerState.currentPage != targetMonthPage || abs(monthPagerState.currentPageOffsetFraction) > .001f) {
                monthPagerState.animateScrollToPage(targetMonthPage)
            }
        }
    }

    val selectedDayPage = dayPageFor(selected)
    val dayPagerTravel by remember(dayPagerState, selectedDayPage) {
        derivedStateOf {
            // This API accounts for currentPageOffsetFraction flipping when
            // currentPage crosses the halfway point. Building this from
            // currentPage + fraction manually makes the indicator jump to
            // the opposite day at exactly that boundary.
            val distance = dayPagerState.getOffsetDistanceInPages(selectedDayPage)
            // A larger distance comes from a calendar-cell click. Let the
            // selected-date animation handle that rather than treating it as
            // a one-day preview.
            if (abs(distance) <= 1.05f) distance.coerceIn(-1f, 1f) else 0f
        }
    }

    /**
     * One locked recognizer owns vertical zoom on the strip, month pager, and
     * handle. Horizontal travel is deliberately left to HorizontalPager's
     * touch/slop/fling machinery. Pulling down increases zoom; pulling up
     * decreases it.
     */
    val calendarGesture = Modifier.pointerInput(Unit) {
        var anchor = 0
        var velocityTracker = VelocityTracker()

        detectVerticalDragGestures(
            onDragStart = {
                cancelMotion()
                anchor = currentZoom.value.roundToInt().coerceIn(0, 2)
                velocityTracker = VelocityTracker()
            },
            onVerticalDrag = { change, delta ->
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
                // The detector only starts after vertical touch-slop wins the
                // gesture, so HorizontalPager retains horizontal swipes.
                // Down is positive in screen coordinates and expands.
                zoom = zoomAfterVerticalDrag(zoom, with(density) { delta.toDp().value }, ZoomStepDp)
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity()
                animateZoomTo(
                    zoomSettleLevel(
                        zoom = currentZoom.value,
                        anchorLevel = anchor,
                        // Positive y velocity means the user pulled down.
                        zoomVelocityDpPerSecond = with(density) { velocity.y.toDp().value },
                    ).toFloat(),
                )
            },
            onDragCancel = {
                animateZoomTo(anchor.toFloat())
            },
        )
    }

    val splitOpenDay = if (interactionEnabled) onOpenDay ?: onDayClick else null
    val dayRailAlpha = (1f - zoom).coerceIn(0f, 1f)
    val agendaAlpha = zoom.coerceIn(0f, 1f) * (2f - zoom).coerceIn(0f, 1f)
    // A boundary week should begin its own transition as soon as the pager
    // has selected that destination, rather than waiting for settledPage to
    // update the app's committed date after the animation completes. Keep
    // that preview alive through the last settling frame as well: the pager
    // can report that scrolling has stopped one frame before its settled-page
    // collector commits the new date, which otherwise exposes a blank/old
    // month row at the handoff.
    var compactBoundaryDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(dayPagerState, monthPagerState) {
        snapshotFlow {
            BoundaryPagerSnapshot(
                compactBoundaryDay = compactBoundaryDay,
                committedEpochDay = currentSelectedEpoch.value,
                dayInProgress = dayPagerState.isScrollInProgress,
                dayTargetPage = dayPagerState.targetPage,
                daySettledPage = dayPagerState.settledPage,
                monthInProgress = monthPagerState.isScrollInProgress,
                monthSettledPage = monthPagerState.settledPage,
            )
        }.collect { snapshot ->
            val committed = LocalDate.ofEpochDay(snapshot.committedEpochDay)
            val target = dateForDayPage(snapshot.dayTargetPage)
            val targetIsBoundary = target.with(DayOfWeek.MONDAY) != committed.with(DayOfWeek.MONDAY)

            if (snapshot.dayInProgress && targetIsBoundary) {
                // Start the preview as soon as the day pager chooses a
                // boundary destination. The selected date itself remains
                // committed by the settled-page collector above.
                compactBoundaryDay = target
            } else {
                val boundary = snapshot.compactBoundaryDay
                if (boundary != null) {
                    val dayTransitionComplete =
                        !snapshot.dayInProgress &&
                            snapshot.daySettledPage == dayPageFor(boundary)
                    val monthTransitionComplete =
                        !snapshot.monthInProgress &&
                            snapshot.monthSettledPage == monthPageFor(YearMonth.from(boundary))

                    // The compact target is shared by the month grid and the
                    // week strip. Keep it alive until both independent
                    // pagers have finished their part of a cross-month
                    // boundary transition. In particular, the day pager can
                    // settle one frame before the selected-date collector
                    // starts the month pager animation; clearing here would
                    // briefly restore the old month's grid in that gap.
                    if (dayTransitionComplete && monthTransitionComplete) {
                        compactBoundaryDay = null
                    }
                }
            }
        }
    }
    val weekStripDay = compactBoundaryDay ?: selected
    val isDayPagerBoundaryTransition = compactBoundaryDay != null

    // The day rail and selected-day agenda crossfade through an overlap. Keep
    // one stable owner for taps, scrolling, and accessibility while the
    // visual layers overlap. The hysteresis prevents ownership from changing
    // back and forth when the two alpha curves are nearly equal.
    var agendaOwnsInput by remember { mutableStateOf(false) }
    LaunchedEffect(interactionEnabled) {
        snapshotFlow { zoom }.collect { currentZoom ->
            val railAlpha = (1f - currentZoom).coerceIn(0f, 1f)
            val agendaAlpha = currentZoom.coerceIn(0f, 1f) * (2f - currentZoom).coerceIn(0f, 1f)
            val nextOwner = when {
                !interactionEnabled -> false
                agendaOwnsInput -> railAlpha > agendaAlpha + DaySurfaceOwnershipHysteresis
                else -> agendaAlpha > railAlpha + DaySurfaceOwnershipHysteresis
            }
            if (nextOwner != agendaOwnsInput) agendaOwnsInput = nextOwner
        }
    }
    val dayRailOwnsInput = interactionEnabled && !agendaOwnsInput && dayRailAlpha > .01f
    val agendaOwnsInputNow = interactionEnabled && agendaOwnsInput && agendaAlpha > .01f

    BackHandler(enabled = interactionEnabled && zoom > .01f) {
        animateZoomTo(if (zoom.roundToInt() >= 2) 1f else 0f)
    }

    Column(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        MonthHeading(
            day = selected,
            onPreviousMonth = {
                scope.launch {
                    monthPagerState.animateScrollToPage((monthPagerState.currentPage - 1).coerceAtLeast(0))
                }
            },
            onNextMonth = {
                scope.launch {
                    monthPagerState.animateScrollToPage((monthPagerState.currentPage + 1).coerceAtMost(MonthPagerPageCount - 1))
                }
            },
            onToday = {
                selectedEpoch = FixtureDate.toEpochDay()
                onDateChanged(FixtureDate)
            },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val handleHeight = 44.dp
            val splitGridHeight = 292.dp
            val detailedGridHeight = (maxHeight - handleHeight).coerceAtLeast(splitGridHeight)
            // Level 1 and level 2 now share one physical calendar surface.
            // The surface grows from the week strip's height into the month
            // grid's height, so the week is revealed as the month surface
            // morphs rather than appearing in a separate slot above it.
            val weekProgress = (1f - zoom).coerceIn(0f, 1f)
            val compactProgress = smoothStep(weekProgress)
            val calendarHeight = if (zoom <= 1f) {
                lerpDp(80.dp, splitGridHeight, zoom)
            } else {
                lerpDp(splitGridHeight, detailedGridHeight, (zoom - 1f).coerceIn(0f, 1f))
            }
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(calendarHeight)
                        .clipToBounds(),
                ) {
                    MonthPager(
                        state = monthPagerState,
                        selected = selected,
                        events = events,
                        journals = journals,
                        detailProgress = (zoom - 1f).coerceIn(0f, 1f),
                        compactProgress = compactProgress,
                        compactDay = weekStripDay,
                        compactPagerOffset = dayPagerTravel,
                        compactBoundaryTransition = isDayPagerBoundaryTransition,
                        modifier = Modifier.fillMaxSize(),
                        gestureModifier = if (interactionEnabled) calendarGesture else Modifier,
                        userScrollEnabled = interactionEnabled && zoom > .01f,
                        onDay = { date ->
                            if (interactionEnabled) {
                                // The split level is a selection surface. Only
                                // the detailed settled level opens the day modal.
                                val isDetailed = zoom >= 1.5f
                                selectedEpoch = date.toEpochDay()
                                onDateChanged(date)
                                if (isDetailed) onDayClick?.invoke(date)
                            }
                        },
                    )

                }

                // The handle stays attached to the shared surface, so it
                // travels with the month-to-week morph as one gesture affordance.
                ZoomHandle(
                    zoom = zoom,
                    gestureModifier = if (interactionEnabled) calendarGesture else Modifier,
                    onTap = {
                        val level = zoom.roundToInt().coerceIn(0, 2)
                        animateZoomTo(if (level < 2) level + 1f else 1f)
                    },
                )

                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    DayPagerSurface(
                        state = dayPagerState,
                        events = events,
                        scrollState = railScroll,
                        modifier = Modifier.fillMaxSize(),
                        interactionEnabled = interactionEnabled,
                        dayRailAlpha = dayRailAlpha,
                        agendaAlpha = agendaAlpha,
                        dayRailOwnsInput = dayRailOwnsInput,
                        agendaOwnsInput = agendaOwnsInputNow,
                        onEvent = onEventClick,
                        onOpenDay = splitOpenDay,
                    )
                }
            }
        }
        AddBar(selected, if (interactionEnabled) onAdd else null)
    }
}

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
private fun MonthHeading(
    day: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPreviousMonth,
            modifier = Modifier.semantics { contentDescription = "Previous month" },
        ) { Icon(CalinoIcons.ChevronLeft, contentDescription = null, tint = CalinoColors.Ink2) }
        Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
            AnimatedContent(
                targetState = YearMonth.from(day),
                transitionSpec = {
                    val direction = if (targetState.isAfter(initialState)) 1 else -1
                    slideInHorizontally(tween(190)) { direction * it / 4 } + fadeIn(tween(150)) togetherWith
                        slideOutHorizontally(tween(150)) { -direction * it / 4 } + fadeOut(tween(110))
                },
                label = "month heading",
            ) { month ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() },
                        style = CalinoTypography.titleLarge.copy(fontSize = 27.sp, lineHeight = 30.sp),
                    )
                    Text(month.year.toString(), style = CalinoTypography.bodyMedium, color = CalinoColors.Ink3, modifier = Modifier.padding(start = 7.dp, bottom = 2.dp))
                }
            }
            AnimatedContent(
                targetState = day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                label = "week heading",
            ) { week ->
                Text("Week $week · ${day.format(ShortDateFormatter)}", style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (day != FixtureDate) {
            TextButton(onClick = onToday, modifier = Modifier.semantics { contentDescription = "Go to today" }) { Text("Today", color = CalinoColors.Accent, fontSize = 12.sp) }
        }
        IconButton(
            onClick = onNextMonth,
            modifier = Modifier.semantics { contentDescription = "Next month" },
        ) { Icon(CalinoIcons.ChevronRight, contentDescription = null, tint = CalinoColors.Ink2) }
    }
    Box(
        Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 16.dp)
            .background(CalinoColors.Accent.copy(alpha = .22f)),
    )
}

@Composable
private fun WeekStrip(
    day: LocalDate,
    displayedWeekDay: LocalDate,
    events: List<CalEvent>,
    pagerOffset: Float,
    gestureModifier: Modifier,
    modifier: Modifier = Modifier,
    onDay: (LocalDate) -> Unit,
) {
    Box(
        modifier.fillMaxWidth().height(80.dp).then(gestureModifier).clipToBounds(),
    ) {
        val committedMonday = day.with(DayOfWeek.MONDAY)
        val monday = displayedWeekDay.with(DayOfWeek.MONDAY)
        val settledIndex = (day.dayOfWeek.value - 1).coerceIn(0, 6)
        val liveOffset = pagerOffset.coerceIn(-1f, 1f)
        val previewDate = when {
            liveOffset < 0f -> day.plusDays(1)
            liveOffset > 0f -> day.minusDays(1)
            else -> day
        }
        val previewStaysInWeek = previewDate.with(DayOfWeek.MONDAY) == committedMonday
        // A day pager offset is screen travel: negative reveals tomorrow and
        // positive reveals yesterday. Keep the week row fixed, but move its
        // indicator in lockstep while the agenda is being dragged/settled.
        val indicatorTargetIndex = if (monday == committedMonday && previewStaysInWeek) {
            (settledIndex - liveOffset).coerceIn(0f, 6f)
        } else {
            settledIndex.toFloat()
        }
        // Day paging belongs to the agenda below. Keep this strip anchored to
        // the selected date's week so an in-week swipe only moves the
        // indicator. A week boundary gets its own directional strip
        // transition as soon as the pager has a boundary target.
        AnimatedContent(
            targetState = monday,
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 7.dp),
            transitionSpec = {
                val direction = if (targetState.isAfter(initialState)) 1 else -1
                (slideInHorizontally(tween(240)) { direction * it } + fadeIn(tween(150))) togetherWith
                    (slideOutHorizontally(tween(240)) { -direction * it } + fadeOut(tween(150)))
            },
            label = "week strip transition",
        ) { pageMonday ->
            // Preserve the outgoing boundary day's indicator while the old
            // week exits, and render the target day in the incoming week so
            // the new week is visually correct before the date commit.
            val pageDay = if (pageMonday == committedMonday) {
                day
            } else if (monday.isAfter(pageMonday)) {
                pageMonday.plusDays(6)
            } else {
                displayedWeekDay
            }
            WeekStripPage(
                monday = pageMonday,
                selected = pageDay,
                indicatorTargetIndex = if (pageMonday == committedMonday) indicatorTargetIndex else null,
                followPager = pageMonday == committedMonday && abs(liveOffset) > .001f && previewStaysInWeek,
                events = events,
                onDay = onDay,
            )
        }
    }
}

@Composable
private fun WeekStripPage(
    monday: LocalDate,
    selected: LocalDate,
    indicatorTargetIndex: Float?,
    followPager: Boolean,
    events: List<CalEvent>,
    onDay: (LocalDate) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellWidth = maxWidth / 7
        val selectedIndex = (selected.dayOfWeek.value - 1).coerceIn(0, 6)
        val indicatorPosition = remember(monday) { Animatable(selectedIndex.toFloat()) }
        LaunchedEffect(indicatorTargetIndex, followPager) {
            val target = indicatorTargetIndex ?: selectedIndex.toFloat()
            if (followPager) {
                // During the gesture the pill is physically attached to the
                // pager offset, so it cannot lag behind or spring ahead.
                indicatorPosition.snapTo(target)
            } else {
                // This covers both a committed page and a cancelled swipe.
                // Animating from the current position avoids the old target
                // flashing back at release.
                indicatorPosition.animateTo(
                    target,
                    animationSpec = spring(dampingRatio = .82f, stiffness = 520f),
                )
            }
        }
        val indicatorIndex = indicatorPosition.value

        Box(Modifier.fillMaxSize()) {
            // This is one indicator shared by the whole strip. Its animated
            // position makes Monday -> Tuesday a physical move, not two
            // independent cells fading their backgrounds.
            Box(
                Modifier.offset(x = cellWidth * indicatorIndex)
                    .width(cellWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CalinoColors.Ink.copy(alpha = .95f)),
            )
            Row(Modifier.fillMaxSize()) {
                (0..6).forEach { dayDelta ->
                    val date = monday.plusDays(dayDelta.toLong())
                    val distance = abs(indicatorIndex - dayDelta.toFloat()).coerceIn(0f, 1f)
                    WeekDay(
                        date = date,
                        events = events,
                        currentSelectionWeight = 1f - distance,
                        targetSelectionWeight = 0f,
                        committedSelected = date == selected,
                        drawSelectionBackground = false,
                        modifier = Modifier.weight(1f),
                        onClick = { onDay(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDay(
    date: LocalDate,
    events: List<CalEvent>,
    currentSelectionWeight: Float,
    targetSelectionWeight: Float,
    committedSelected: Boolean,
    drawSelectionBackground: Boolean = true,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val selectedWeight = max(currentSelectionWeight, targetSelectionWeight).coerceIn(0f, 1f)
    val background by animateColorAsState(
        if (drawSelectionBackground) CalinoColors.Ink.copy(alpha = selectedWeight) else Color.Transparent,
        label = "week selection",
    )
    val weekdayColor = lerpColor(CalinoColors.Ink3, Color.White.copy(.65f), selectedWeight)
    val dateColor = lerpColor(CalinoColors.Ink2, Color.White, selectedWeight)
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${date.format(FullDateFormatter)}${if (committedSelected) ", selected" else ""}"
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            fontSize = 10.sp,
            color = weekdayColor,
        )
        Text(
            date.dayOfMonth.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = dateColor,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(7.dp)) {
            eventsFor(events, date).take(3).forEach { event ->
                Box(Modifier.size(4.dp).clip(CircleShape).background(Color(event.color)))
            }
        }
    }
}

@Composable
private fun MonthPager(
    state: PagerState,
    selected: LocalDate,
    events: List<CalEvent>,
    journals: List<JournalEntry>,
    detailProgress: Float,
    compactProgress: Float,
    compactDay: LocalDate,
    compactPagerOffset: Float,
    compactBoundaryTransition: Boolean,
    modifier: Modifier,
    gestureModifier: Modifier,
    userScrollEnabled: Boolean,
    onDay: (LocalDate) -> Unit,
) {
    Box(modifier.clipToBounds().then(gestureModifier)) {
        fun effectiveMonth(page: Int): YearMonth {
            val pagerMonth = monthForPage(page)
            val compactTargetPage = compactBoundaryTransition && page == state.currentPage && compactProgress > .001f
            return if (compactTargetPage) YearMonth.from(compactDay) else pagerMonth
        }

        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize(),
            // The in-viewport neighbour is still composed during a swipe.
            // Keeping two extra full day surfaces mounted made every zoom
            // frame measure five rail/agenda trees unnecessarily.
            beyondViewportPageCount = 0,
            userScrollEnabled = userScrollEnabled,
            // Include the effective rendered month in the key so a boundary
            // preview cannot retain the identity of the old month's content.
            // The page ordinal keeps keys unique when the preview month is
            // also the adjacent pager page during the same swipe.
            key = { page -> "$page:${effectiveMonth(page)}" },
        ) { page ->
            val pagerMonth = monthForPage(page)
            // While compacting, the visible row belongs to the targeted day,
            // not necessarily to the month page that is still settling. This
            // keeps a boundary swipe from briefly exposing the first row of
            // the old/next month.
            val compactTargetPage = compactBoundaryTransition && page == state.currentPage && compactProgress > .001f
            val pageMonth = effectiveMonth(page)
            val pageSelected = pageMonth.atDay(
                (if (compactTargetPage) compactDay.dayOfMonth else selected.dayOfMonth)
                    .coerceAtMost(pageMonth.lengthOfMonth()),
            )
            MonthGrid(
                selected = pageSelected,
                month = pageMonth,
                events = events,
                journals = journals,
                detailProgress = detailProgress,
                compactProgress = compactProgress,
                compactDay = compactDay,
                compactPagerOffset = compactPagerOffset,
                onDay = onDay,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    selected: LocalDate,
    month: YearMonth,
    events: List<CalEvent>,
    journals: List<JournalEntry>,
    detailProgress: Float,
    compactProgress: Float,
    compactDay: LocalDate,
    compactPagerOffset: Float,
    onDay: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val rows = monthGridRows(month)
    // Index the month once per data/month change. The zoom animation only
    // changes layout progress; it must not make every cell rescan and
    // reparse the complete event list on every frame.
    val monthEvents = remember(events, month) { monthEventIndex(events, month) }
    val monthJournalDates = remember(journals, month) { monthJournalDates(journals, month) }
    val compactWeekStart = compactDay.with(DayOfWeek.MONDAY)
    val compactWeekRow = ((compactWeekStart.toEpochDay() - start.toEpochDay()) / 7L).toInt()
    val compactWeekIsInGrid = compactWeekRow in 0 until rows
    val indicatorIndex = (compactDay.dayOfWeek.value - 1 - compactPagerOffset).coerceIn(0f, 6f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // One constraint subcomposition serves the entire grid. A
        // BoxWithConstraints per row caused six independent remeasurements on
        // every zoom frame just to calculate the indicator width.
        val gridHorizontalPadding = lerpDp(16.dp, 14.dp, compactProgress)
        val gridWidth = (maxWidth - gridHorizontalPadding - gridHorizontalPadding).coerceAtLeast(0.dp)
        val cellWidth = gridWidth / 7
        Column(
            Modifier.fillMaxSize().padding(horizontal = gridHorizontalPadding),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .height(lerpDp(22.dp, 0.dp, compactProgress))
                    .graphicsLayer { alpha = 1f - compactProgress },
            ) {
                WeekdayLetters.forEach {
                    Text(it, Modifier.weight(1f), fontSize = 10.sp, color = CalinoColors.Ink3)
                }
            }
            (0 until rows).forEach { row ->
                val isCompactWeek = compactWeekIsInGrid && row == compactWeekRow
                val rowWeight = when {
                    compactProgress <= .001f || !compactWeekIsInGrid -> 1f
                    isCompactWeek -> 1f
                    else -> (1f - compactProgress).coerceAtLeast(.001f)
                }
                Box(
                    Modifier.fillMaxWidth()
                        .weight(rowWeight)
                        .clipToBounds()
                        .graphicsLayer { alpha = if (isCompactWeek) 1f else 1f - compactProgress },
                ) {
                    if (isCompactWeek && compactProgress > .001f) {
                        Box(
                            Modifier.offset(x = cellWidth * indicatorIndex)
                                .width(cellWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(14.dp))
                            .background(CalinoColors.Ink.copy(alpha = .95f * compactProgress)),
                        )
                    }
                    if (isCompactWeek) {
                        Row(Modifier.fillMaxSize()) {
                            (0 until 7).forEach { col ->
                                val date = start.plusDays((row * 7 + col).toLong())
                                DayCell(
                                    date = date,
                                    selected = date == selected,
                                    compactSelectedWeight = if (isCompactWeek) {
                                        (1f - abs(indicatorIndex - col.toFloat())).coerceIn(0f, 1f) * compactProgress
                                    } else {
                                        0f
                                    },
                                    inMonth = YearMonth.from(date) == month,
                                    events = monthEvents[date].orEmpty(),
                                    hasJournal = date in monthJournalDates,
                                    detailProgress = detailProgress,
                                    compactProgress = compactProgress,
                                    interactive = isCompactWeek || compactProgress < .86f,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onDay = { onDay(date) },
                                )
                            }
                        }
                    } else {
                        // Swap the expensive month cells for the lightweight
                        // row only through an explicit, short crossfade. This
                        // removes the old hard pop at the optimization
                        // threshold while keeping the expensive trees mounted
                        // for only a small overlap window.
                        val placeholderStart = CompactMonthRowPlaceholderThreshold - CompactMonthRowCrossfadeHalfWidth
                        val placeholderEnd = CompactMonthRowPlaceholderThreshold + CompactMonthRowCrossfadeHalfWidth
                        val placeholderBlend = ((compactProgress - placeholderStart) /
                            (placeholderEnd - placeholderStart)).coerceIn(0f, 1f)
                        val collapseStart = CollapsedMonthRowThreshold - CollapsedMonthRowCrossfadeHalfWidth
                        val collapseEnd = CollapsedMonthRowThreshold + CollapsedMonthRowCrossfadeHalfWidth
                        val collapseBlend = ((compactProgress - collapseStart) /
                            (collapseEnd - collapseStart)).coerceIn(0f, 1f)
                        val fullCellAlpha = 1f - placeholderBlend
                        val compactRowAlpha = placeholderBlend * (1f - collapseBlend)

                        if (fullCellAlpha > .001f) {
                            Row(
                                Modifier.fillMaxSize().graphicsLayer { alpha = fullCellAlpha },
                            ) {
                                (0 until 7).forEach { col ->
                                    val date = start.plusDays((row * 7 + col).toLong())
                                    DayCell(
                                        date = date,
                                        selected = date == selected,
                                        compactSelectedWeight = 0f,
                                        inMonth = YearMonth.from(date) == month,
                                        events = monthEvents[date].orEmpty(),
                                        hasJournal = date in monthJournalDates,
                                        detailProgress = detailProgress,
                                        compactProgress = compactProgress,
                                        interactive = compactProgress < .86f,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        onDay = { onDay(date) },
                                    )
                                }
                            }
                        }
                        if (compactRowAlpha > .001f) {
                            // Before the row becomes fully collapsed, retain
                            // its date labels and marker affordances with the
                            // cheap representation. It preserves the visible
                            // fade and geometry without measuring a full
                            // DayCell/EventDensity tree for every hidden row.
                            CompactMonthRow(
                                start = start.plusDays((row * 7).toLong()),
                                month = month,
                                events = monthEvents,
                                journalDates = monthJournalDates,
                                compactProgress = compactProgress,
                                interactive = compactProgress < .86f,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = compactRowAlpha },
                                onDay = onDay,
                            )
                        }
                        if (fullCellAlpha <= .001f && compactRowAlpha <= .001f) {
                            // At this point the row is almost fully transparent.
                            // Retain its weighted box so the shared month-to-
                            // week geometry remains continuous, but compose no
                            // date/event content for the final frames.
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMonthRow(
    start: LocalDate,
    month: YearMonth,
    events: Map<LocalDate, List<CalEvent>>,
    journalDates: Set<LocalDate>,
    compactProgress: Float,
    interactive: Boolean,
    modifier: Modifier,
    onDay: (LocalDate) -> Unit,
) {
    Row(modifier) {
        repeat(7) { column ->
            val date = start.plusDays(column.toLong())
            val inMonth = YearMonth.from(date) == month
            val today = date == FixtureDate
            val dayEvents = events[date].orEmpty()
            val dateDescription = remember(date, dayEvents) {
                buildString {
                    append(date.format(FullDateFormatter))
                    if (dayEvents.isNotEmpty()) append(", events: ").append(dayEvents.joinToString { it.title })
                }
            }
            val cellModifier = if (interactive) {
                Modifier
                    .clickable { onDay(date) }
                    .semantics(mergeDescendants = true) { contentDescription = dateDescription }
            } else {
                Modifier.clearAndSetSemantics { }
            }
            // The Today fill fades out before the compact row settles. Keep
            // the label dark rather than leaving white text on the canvas.
            val dateColor = when {
                today && compactProgress >= .8f -> CalinoColors.Ink2
                today -> Color.White
                inMonth -> CalinoColors.Ink2
                else -> CalinoColors.Ink3.copy(.5f)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(lerpDp(4.dp, 14.dp, compactProgress)))
                    .background(if (today) CalinoColors.Accent.copy(.05f * (1f - compactProgress)) else Color.Transparent)
                    .then(cellModifier)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(15.dp * compactProgress),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        fontSize = 10.sp,
                        color = CalinoColors.Ink3,
                    )
                }
                Box(
                    Modifier.width(18.dp).height(18.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = dateColor,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().height(7.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    dayEvents.take(3).forEach { event ->
                        Box(Modifier.size(4.dp).clip(CircleShape).background(Color(event.color)))
                    }
                }
                if (date in journalDates) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(CalinoColors.Plum))
                }
            }
        }
    }
}

private fun monthEventIndex(events: List<CalEvent>, month: YearMonth): Map<LocalDate, List<CalEvent>> {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val cellCount = monthGridRows(month) * 7
    return buildMap {
        repeat(cellCount) { index ->
            val date = start.plusDays(index.toLong())
            val dayEvents = eventsFor(events, date)
            if (dayEvents.isNotEmpty()) put(date, dayEvents)
        }
    }
}

private fun monthJournalDates(journals: List<JournalEntry>, month: YearMonth): Set<LocalDate> {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val endExclusive = start.plusDays((monthGridRows(month) * 7).toLong())
    return journals.asSequence()
        .map { it.date }
        .filter { it >= start && it < endExclusive }
        .toSet()
}

private fun monthGridRows(month: YearMonth): Int {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    return ((month.atEndOfMonth().toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    compactSelectedWeight: Float,
    inMonth: Boolean,
    events: List<CalEvent>,
    hasJournal: Boolean,
    detailProgress: Float,
    compactProgress: Float,
    interactive: Boolean,
    modifier: Modifier,
    onDay: () -> Unit,
) {
    val today = date == FixtureDate
    val monthFill = when {
        selected -> CalinoColors.Accent.copy(.09f)
        today -> CalinoColors.Accent.copy(.05f)
        else -> Color.Transparent
    }
    val fill = lerpColor(monthFill, Color.Transparent, compactProgress)
    val monthSelectionWeight = if (selected) 1f - compactProgress else 0f
    val selectedWeight = max(monthSelectionWeight, compactSelectedWeight).coerceIn(0f, 1f)
    val selectedBorder = if (selected && compactProgress < .8f) {
        Modifier.border(1.5.dp, CalinoColors.Accent.copy(alpha = .45f * (1f - compactProgress)))
    } else {
        Modifier
    }
    val dateDescription = remember(date, selected, events) {
        buildString {
            append(date.format(FullDateFormatter))
            if (selected) append(", selected")
            if (events.isNotEmpty()) append(", events: ").append(events.joinToString { it.title })
        }
    }
    val interactionModifier = if (interactive) {
        Modifier
            .clickable(onClick = onDay)
            .semantics(mergeDescendants = true) {
                contentDescription = dateDescription
            }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(lerpDp(4.dp, 14.dp, compactProgress)))
            .background(fill)
            .then(selectedBorder)
            .then(interactionModifier)
            .padding(horizontal = lerpDp(2.dp, 5.dp, compactProgress), vertical = lerpDp(2.dp, 5.dp, compactProgress)),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        val weekdayHeight = lerpDp(0.dp, 15.dp, compactProgress)
        Box(
            Modifier.fillMaxWidth()
                .height(weekdayHeight)
                .graphicsLayer { alpha = compactProgress },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                fontSize = 10.sp,
                color = lerpColor(CalinoColors.Ink3, Color.White, compactSelectedWeight),
            )
        }
        val dateSize = lerpDp(lerpDp(22.dp, 25.dp, detailProgress), 18.dp, compactProgress)
        val monthDateColor = when {
            today && compactProgress >= .8f && compactSelectedWeight <= .5f -> CalinoColors.Ink2
            today -> Color.White
            inMonth -> CalinoColors.Ink2
            else -> CalinoColors.Ink3.copy(.5f)
        }
        val compactDateColor = if (compactSelectedWeight > .5f) Color.White else CalinoColors.Ink2
        Row(Modifier.fillMaxWidth().height(dateSize), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(lerpDp(dateSize, 30.dp, compactProgress)).height(dateSize).clip(CircleShape)
                    .background(if (today && compactProgress < .8f) CalinoColors.Accent else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = ((12f + 1.5f * detailProgress) * (1f - compactProgress) + 16f * compactProgress).sp,
                    fontWeight = FontWeight.Medium,
                    color = lerpColor(monthDateColor, compactDateColor, compactProgress),
                )
            }
        }
        EventDensityContent(events, detailProgress, Modifier.fillMaxWidth())
        // Journal dates are fixture/state data during this transition. Avoid
        // one AnimatedVisibility state machine in every cell; if journal data
        // changes later, the enclosing grid can animate that state change.
        if (hasJournal) {
            Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.Center) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(CalinoColors.Plum))
            }
        }
    }
}

/**
 * A single measured child per event continuously grows from its coarse dot or
 * all-day line into a readable chip. The layout interpolates positions and
 * widths rather than replacing the cell tree at a threshold.
 */
@Composable
private fun EventDensityContent(events: List<CalEvent>, detailProgress: Float, modifier: Modifier) {
    // Only the first two events ever become readable chips. The remaining
    // events stay compact dots/lines and fade out as detail expands, so
    // measuring them as full composable subtrees only adds work to the hot
    // zoom path.
    val shownCount = events.size.coerceAtMost(2)
    val compactExtraCount = (events.size - 2).coerceIn(0, 2)
    val overflow = (events.size - 2).coerceAtLeast(0)
    val densitySummary = remember(events) {
        var allDayCount = 0
        val precedingAllDayCounts = IntArray(events.size) { index ->
            val precedingCount = allDayCount
            if (events[index].allDay) allDayCount++
            precedingCount
        }
        EventDensitySummary(allDayCount, precedingAllDayCounts)
    }
    val allDayCount = densitySummary.allDayCount
    Layout(
        content = {
            repeat(shownCount) { index -> EventDensityItem(events[index], detailProgress) }
            if (overflow > 0) {
                Text(
                    "+$overflow",
                    fontSize = 10.sp,
                    color = CalinoColors.Ink3,
                    modifier = Modifier.graphicsLayer { alpha = detailProgress },
                )
            }
        },
        modifier = modifier.drawBehind {
            val progress = detailProgress.coerceIn(0f, 1f)
            val dot = 5.dp.toPx()
            val gap = 3.dp.toPx()
            val radius = 2.dp.toPx()
            val allDayGap = if (allDayCount > 1) min(gap, size.width / (allDayCount * 2f)) else 0f
            val allDayWidth = if (allDayCount > 0) {
                ((size.width - allDayGap * (allDayCount - 1)) / allDayCount).coerceAtLeast(1f)
            } else {
                dot
            }
            repeat(compactExtraCount) { extraIndex ->
                val event = events[extraIndex + 2]
                val index = extraIndex + 2
                val allDayIndex = densitySummary.precedingAllDayCounts[index]
                val compactWidth = if (event.allDay) allDayWidth else dot
                val childWidth = lerpInt(compactWidth.roundToInt(), dot.roundToInt(), progress).toFloat()
                val coarseX = if (event.allDay) {
                    allDayIndex * (allDayWidth + allDayGap)
                } else {
                    index * (dot + gap)
                }
                val coarseY = 0f
                val x = lerpInt(coarseX.roundToInt(), 0, progress).toFloat()
                val y = lerpInt(coarseY.roundToInt(), 0, progress).toFloat()
                val childHeight = dot
                val color = Color(event.color)
                val chipColor = eventTint(color, if (event.allDay) .18f else .10f)
                drawRoundRect(
                    color = lerpColor(color, chipColor, progress).copy(alpha = 1f - progress),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x.coerceIn(0f, max(0f, size.width - childWidth)),
                        y.coerceIn(0f, max(0f, size.height - childHeight)),
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        childWidth.coerceAtLeast(1f),
                        childHeight.coerceAtLeast(1f),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                )
            }
        },
    ) { measurables, constraints ->
        val progress = detailProgress.coerceIn(0f, 1f)
        val width = constraints.maxWidth
        val dot = 5.dp.roundToPx()
        val gap = 3.dp.roundToPx()
        val allDayGap = if (allDayCount > 1) min(gap, width / (allDayCount * 2)) else 0
        val allDayWidth = if (allDayCount > 0) {
            ((width - allDayGap * (allDayCount - 1)) / allDayCount).coerceAtLeast(1)
        } else {
            dot
        }
        val chipHeight = 20.dp.roundToPx()
        val overflowHeight = 14.dp.roundToPx()
        val coarseHeight = dot
        val detailHeight = shownCount * chipHeight + if (overflow > 0) overflowHeight else 0
        val height = lerpInt(coarseHeight, detailHeight.coerceAtLeast(coarseHeight), progress)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val places = measurables.mapIndexed { index, measurable ->
            val isOverflow = overflow > 0 && index == shownCount
            if (isOverflow) {
                measurable.measure(Constraints.fixed(maxOf(1, width), maxOf(1, overflowHeight)))
            } else {
                val event = events[index]
                val compactWidth = if (event.allDay) allDayWidth else dot
                val targetWidth = if (index < 2) width else dot
                val childHeight = if (index < 2) {
                    lerpInt(if (event.allDay) 3.dp.roundToPx() else dot, chipHeight, progress)
                } else {
                    dot
                }
                val childWidth = lerpInt(compactWidth, targetWidth, progress)
                    .coerceIn(1, maxOf(1, width))
                measurable.measure(Constraints.fixed(childWidth, maxOf(1, childHeight)))
            }
        }
        layout(width, height) {
            places.forEachIndexed { index, placeable ->
                if (overflow > 0 && index == shownCount) {
                    val y = lerpInt(coarseHeight + 2.dp.roundToPx(), shownCount * chipHeight, progress)
                    placeable.placeRelative(0, y.coerceIn(0, max(0, height - placeable.height)))
                } else {
                    val event = events[index]
                    val allDayIndex = densitySummary.precedingAllDayCounts[index]
                    val coarseX = if (event.allDay) {
                        allDayIndex * (allDayWidth + allDayGap)
                    } else {
                        index * (dot + gap)
                    }
                    val coarseY = 0
                    val targetY = if (index < 2) index * chipHeight else 0
                    val x = lerpInt(coarseX, 0, progress)
                    val y = lerpInt(coarseY, targetY, progress)
                    placeable.placeRelative(
                        x.coerceIn(0, max(0, width - placeable.width)),
                        y.coerceIn(0, max(0, height - placeable.height)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EventDensityItem(event: CalEvent, detailProgress: Float) {
    val progress = detailProgress.coerceIn(0f, 1f)
    val color = Color(event.color)
    val chipColor = eventTint(color, if (event.allDay) .18f else .10f)
    Box(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(lerpDp(2.dp, 6.dp, progress)))
            .background(lerpColor(color, chipColor, progress)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(vertical = 3.dp)
                .width(lerpDp(0.dp, 2.5.dp, progress))
                .fillMaxHeight()
                .background(color)
                .alpha(progress),
        ) { }
        Text(
            event.title,
            Modifier.padding(start = lerpDp(0.dp, 8.dp, progress), end = 3.dp)
                .alpha(progress),
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = CalinoColors.Ink,
        )
    }
}

private fun lerpInt(start: Int, stop: Int, fraction: Float): Int =
    (start + (stop - start) * fraction.coerceIn(0f, 1f)).roundToInt()

private fun eventsFor(events: List<CalEvent>, date: LocalDate): List<CalEvent> =
    events.filter { it.occursOn(date) }

private fun eventDescription(event: CalEvent): String = buildString {
    append(event.title)
    event.start?.let { append(", ").append(it.format(TimeFormatter)) }
    event.location?.let { append(", ").append(it) }
}

@Composable
private fun EventChip(event: CalEvent, minHeight: Dp = 34.dp, onClick: (() -> Unit)? = null, agendaStyle: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = eventDescription(event) }
            .background(eventTint(Color(event.color), .10f)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(if (agendaStyle) 3.dp else 2.dp).height(22.dp).background(Color(event.color)))
        Text(
            if (agendaStyle) {
                event.start?.format(TimeFormatter)?.let { "$it  ${event.title}" } ?: event.title
            } else event.title,
            Modifier.padding(start = 7.dp),
            fontSize = if (agendaStyle) 13.5.sp else 9.sp,
            lineHeight = if (agendaStyle) 18.sp else 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = CalinoColors.Ink,
        )
    }
}

@Composable
private fun DayPagerSurface(
    state: PagerState,
    events: List<CalEvent>,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier,
    interactionEnabled: Boolean,
    dayRailAlpha: Float,
    agendaAlpha: Float,
    dayRailOwnsInput: Boolean,
    agendaOwnsInput: Boolean,
    onEvent: ((CalEvent) -> Unit)?,
    onOpenDay: ((LocalDate) -> Unit)?,
) {
    HorizontalPager(
        state = state,
        modifier = modifier.clipToBounds(),
            // The pager itself keeps the adjacent page that is entering the
            // viewport. Extra eagerly composed month grids are expensive while
            // zoom continuously changes every cell's measured height.
            beyondViewportPageCount = 0,
        userScrollEnabled = interactionEnabled && max(dayRailAlpha, agendaAlpha) > .01f,
        key = { page -> dateForDayPage(page).toEpochDay() },
    ) { page ->
        val pageDay = dateForDayPage(page)
        val dayEvents = remember(events, pageDay) { eventsFor(events, pageDay) }
        Box(Modifier.fillMaxSize()) {
            // Do not merely hide a fully transparent surface: clearAndSetSemantics
            // removes accessibility nodes but the hidden tree still costs a full
            // composition/measure pass on every zoom frame. The two trees are
            // mounted only while they contribute visible pixels; the threshold
            // is crossed once per transition, so the rest remains continuous.
            if (dayRailAlpha > .01f) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = dayRailAlpha },
                ) {
                    DayRailPage(
                        day = pageDay,
                        dayEvents = dayEvents,
                        scrollState = scrollState,
                        active = dayRailOwnsInput,
                        scrollEnabled = dayRailOwnsInput,
                        onEvent = if (dayRailOwnsInput) onEvent else null,
                    )
                }
            }
            if (agendaAlpha > .01f) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = agendaAlpha },
                ) {
                    SelectedDayAgendaPage(
                        day = pageDay,
                        dayEvents = dayEvents,
                        active = agendaOwnsInput,
                        onEvent = if (agendaOwnsInput) onEvent else null,
                        onOpenDay = if (agendaOwnsInput) onOpenDay else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayAgendaPage(
    day: LocalDate,
    dayEvents: List<CalEvent>,
    modifier: Modifier = Modifier.fillMaxSize(),
    active: Boolean,
    onEvent: ((CalEvent) -> Unit)?,
    onOpenDay: ((LocalDate) -> Unit)?,
) {
    val interactionModifier = if (active) {
        modifier.semantics {
            contentDescription = "Selected-day agenda for ${day.format(FullDateFormatter)}"
        }
    } else {
        modifier.clearAndSetSemantics { }
    }
    Column(
        interactionModifier
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SELECTED DAY", fontSize = 10.sp, letterSpacing = 1.sp, color = CalinoColors.Ink3, modifier = Modifier.weight(1f))
            Text(
                "OPEN DAY",
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = CalinoColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .heightIn(min = 44.dp)
                    .clickable(enabled = onOpenDay != null) { onOpenDay?.invoke(day) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        Text(day.format(AgendaDateFormatter), fontSize = 11.sp, color = CalinoColors.Ink2)
        if (dayEvents.isEmpty()) {
            Text("Nothing scheduled", fontSize = 13.sp, color = CalinoColors.Ink3, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            dayEvents.forEach { event ->
                EventChip(event, minHeight = 44.dp, onClick = onEvent?.let { callback -> { callback(event) } }, agendaStyle = true)
            }
        }
    }
}

@Composable
private fun DayRailPage(
    day: LocalDate,
    dayEvents: List<CalEvent>,
    scrollState: androidx.compose.foundation.ScrollState,
    active: Boolean,
    scrollEnabled: Boolean,
    onEvent: ((CalEvent) -> Unit)?,
) {
    val interactionModifier = if (active) Modifier else Modifier.clearAndSetSemantics { }
    Column(interactionModifier.fillMaxSize()) {
        // This strip deliberately sits outside the scrolling rail so changing
        // hours never makes the all-day context disappear.
        Column(
            Modifier.fillMaxWidth().padding(start = 52.dp, end = 20.dp, top = 5.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (dayEvents.any { it.allDay }) {
                dayEvents.filter { it.allDay }.take(2).forEach { event ->
                    EventChip(event, minHeight = 40.dp, onClick = onEvent?.let { callback -> { callback(event) } }, agendaStyle = true)
                }
            } else {
                Text("NO ALL-DAY EVENTS", fontSize = 10.sp, letterSpacing = 1.sp, color = CalinoColors.Ink3)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            Column(Modifier.fillMaxWidth().verticalScroll(scrollState, enabled = scrollEnabled)) {
                HourRailContent(dayEvents, onEvent)
            }
        }
    }
}

@Composable
private fun HourRailContent(dayEvents: List<CalEvent>, onEvent: ((CalEvent) -> Unit)?) {
    Box(Modifier.fillMaxWidth().height(1488.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(24) { hour ->
                val y = hour * 62.dp.toPx()
                drawLine(CalinoColors.Ink.copy(.08f), androidx.compose.ui.geometry.Offset(52.dp.toPx(), y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
            }
        }
        (0..23).forEach { hour ->
            Text(
                String.format(Locale.US, "%02d:00", hour),
                Modifier.offset(x = 8.dp, y = (hour * 62 - 7).dp),
                fontSize = 10.sp,
                color = CalinoColors.Ink3,
            )
        }
        dayEvents.filterNot { it.allDay }.forEach { event ->
            event.start?.let { start ->
                val top = ((start.hour + start.minute / 60f) * 62).dp
                val height = maxOf(38, ((event.durationMinutes ?: 60) / 60f * 62 - 6).toInt()).dp
                Box(
                    Modifier.offset(y = top)
                        .fillMaxWidth()
                        .padding(start = 52.dp, end = 20.dp)
                        .height(height)
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (onEvent != null) Modifier.clickable { onEvent(event) } else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = eventDescription(event) }
                        .background(eventTint(Color(event.color), .12f, CalinoColors.Panel))
                        .padding(8.dp),
                ) {
                    Text(
                        event.title + "\n" + start.format(TimeFormatter) + (event.location?.let { " · $it" } ?: ""),
                        fontSize = 13.sp,
                        color = CalinoColors.Ink,
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().offset(y = (11.33f * 62).dp).height(8.dp)) {
            drawLine(CalinoColors.Rose, androidx.compose.ui.geometry.Offset(44.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width, 4.dp.toPx()), 1.5f)
            drawCircle(CalinoColors.Rose, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(44.dp.toPx(), 4.dp.toPx()))
        }
    }
}

@Composable
private fun ZoomHandle(zoom: Float, gestureModifier: Modifier, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).then(gestureModifier)
            .clickable(onClick = onTap)
            .semantics(mergeDescendants = true) { contentDescription = "Change calendar zoom, level ${zoom.roundToInt() + 1} of 3" }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(26.dp).height(3.dp).background(CalinoColors.Ink.copy(.25f)))
        Text(
            when {
                zoom < .5f -> "PULL FOR MONTH"
                zoom < 1.5f -> "PULL AGAIN FOR DETAIL"
                else -> "RELEASE TO COLLAPSE"
            },
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = CalinoColors.Ink3,
        )
        repeat(3) { index ->
            Box(
                Modifier.width(if (index == zoom.roundToInt()) 14.dp else 6.dp)
                    .height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (index == zoom.roundToInt()) CalinoColors.Accent else CalinoColors.Ink3.copy(.3f)),
            )
        }
        Box(Modifier.width(26.dp).height(3.dp).background(CalinoColors.Ink.copy(.25f)))
    }
}

@Composable
private fun AddBar(day: LocalDate, onAdd: ((LocalDate) -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line).clickable(enabled = onAdd != null) { onAdd?.invoke(day) }
            .semantics(mergeDescendants = true) { contentDescription = "Add event on ${day.format(AddDateFormatter)}" }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = day,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val direction = if (targetState.isAfter(initialState)) 1 else -1
                (slideInHorizontally(tween(180)) { direction * it / 3 } + fadeIn(tween(140))) togetherWith
                    (slideOutHorizontally(tween(150)) { -direction * it / 3 } + fadeOut(tween(100)))
            },
            label = "add bar date",
        ) { targetDay ->
            Text("Add on ${targetDay.format(AgendaDateFormatter)}", style = CalinoTypography.bodyLarge, color = CalinoColors.Ink2)
        }
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(CalinoColors.Ink), contentAlignment = Alignment.Center) {
            Text("+", fontSize = 27.sp, color = Color.White, fontWeight = FontWeight.Light)
        }
    }
}
