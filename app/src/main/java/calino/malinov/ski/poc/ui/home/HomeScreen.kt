package calino.malinov.ski.poc.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.zIndex
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.occursOn
import calino.malinov.ski.poc.data.repository.CalinoRepository
import calino.malinov.ski.poc.data.repository.FixtureRepository
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.design.eventTint
import calino.malinov.ski.poc.ui.components.CalinoMonthHeading
import calino.malinov.ski.poc.ui.components.MenuButton
import calino.malinov.ski.poc.ui.components.CalinoIcons
import calino.malinov.ski.poc.ui.components.TaskRow
import calino.malinov.ski.poc.ui.components.calinoPressable
import calino.malinov.ski.poc.ui.surfaces.DayPane
import calino.malinov.ski.poc.qa.zoomAfterVerticalDrag
import calino.malinov.ski.poc.qa.zoomSettleLevel
import calino.malinov.ski.poc.state.SplitPaneWidthDp
import calino.malinov.ski.poc.state.openTasksDueOn
import calino.malinov.ski.poc.state.shouldSplit
import calino.malinov.ski.poc.state.tasksDueOn
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

internal val FixtureDate = LocalDate.of(2026, 5, 18)
private const val DaytimeScrollHour = 9
private const val ZoomStepDp = 280f
private const val DayPagerCenter = 100_000
private const val DayPagerPageCount = DayPagerCenter * 2 + 1
private const val WeekPagerCenter = 10_000
private const val WeekPagerPageCount = WeekPagerCenter * 2 + 1
internal const val MonthPagerCenter = 10_000
internal const val MonthPagerPageCount = MonthPagerCenter * 2 + 1
// Retained by the deprecated compatibility month renderer below. The active
// calendar path uses StaticMonthGrid and does not use these crossfade bounds.
private const val CompactMonthRowPlaceholderThreshold = .62f
private const val CollapsedMonthRowThreshold = .92f
private const val CompactMonthRowCrossfadeHalfWidth = .06f
private const val CollapsedMonthRowCrossfadeHalfWidth = .06f
private const val DaySurfaceOwnershipHysteresis = .08f
private const val MonthEndpointBlendStart = .10f
private const val MonthEndpointBlendEnd = .18f
private const val DaySurfaceBlendStart = .38f
private const val DaySurfaceBlendEnd = .62f
private val WeekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
private val TimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val ShortDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val AgendaDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

/**
 * Geometry shared by the compact month endpoint and its interactive pager.
 * Keeping these values together prevents the ownership handoff from moving
 * the selected week by a few pixels when the pager becomes visible.
 */
private object CompactWeekMetrics {
    val Height = 80.dp
    val HorizontalPadding = 14.dp
    val VerticalPadding = 7.dp
    val PillHeight = 58.dp
    val PillHorizontalPadding = 3.dp
    val PillRadius = 14.dp
}

private data class BoundaryPagerSnapshot(
    val compactBoundaryDay: LocalDate?,
    val blockedBoundaryDay: LocalDate?,
    val committedEpochDay: Long,
    val dayInProgress: Boolean,
    val dayTargetPage: Int,
    val daySettledPage: Int,
    val monthInProgress: Boolean,
    val monthSettledPage: Int,
)

private data class WeekPreviewSuppression(
    val targetPage: Int,
    val committedPage: Int,
    val boundaryDay: LocalDate,
    val generation: Long,
    val cancelled: Boolean = false,
)

private fun dayPageFor(date: LocalDate): Int =
    (DayPagerCenter.toLong() + date.toEpochDay() - FixtureDate.toEpochDay())
        .coerceIn(0L, (DayPagerPageCount - 1).toLong())
        .toInt()

private fun dateForDayPage(page: Int): LocalDate =
    FixtureDate.plusDays((page - DayPagerCenter).toLong())

private fun weekPageFor(date: LocalDate): Int {
    val fixtureMonday = FixtureDate.with(DayOfWeek.MONDAY)
    val monday = date.with(DayOfWeek.MONDAY)
    return (WeekPagerCenter + ((monday.toEpochDay() - fixtureMonday.toEpochDay()) / 7L))
        .coerceIn(0L, (WeekPagerPageCount - 1).toLong())
        .toInt()
}

private fun mondayForWeekPage(page: Int): LocalDate =
    FixtureDate.with(DayOfWeek.MONDAY).plusWeeks((page - WeekPagerCenter).toLong())

internal fun monthPageFor(month: YearMonth): Int {
    val fixtureMonth = YearMonth.from(FixtureDate)
    return (MonthPagerCenter + (month.year - fixtureMonth.year) * 12 + month.monthValue - fixtureMonth.monthValue)
        .coerceIn(0, MonthPagerPageCount - 1)
}

internal fun monthForPage(page: Int): YearMonth =
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
    tasks: List<CalTask> = repository.tasks(),
    modifier: Modifier = Modifier,
    initialDate: LocalDate = FixtureDate,
    onOpenMenu: (() -> Unit)? = null,
    onDateChanged: (LocalDate) -> Unit = {},
    onDayClick: ((LocalDate) -> Unit)? = null,
    onEventClick: ((CalEvent) -> Unit)? = null,
    onTaskDone: (CalTask, Boolean) -> Unit = { _, _ -> },
    onTaskRescheduleTo: (CalTask, LocalDate?) -> Unit = { _, _ -> },
    onTaskClick: ((CalTask) -> Unit)? = null,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    interactionEnabled: Boolean = true,
    /** Reports whether the landscape day pane is currently showing. */
    onSplitPaneChanged: (Boolean) -> Unit = {},
) {
    var selectedEpoch by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    val zoomState = rememberSaveable { mutableFloatStateOf(0f) }
    var settledZoom by rememberSaveable { mutableFloatStateOf(0f) }
    LaunchedEffect(initialDate) { selectedEpoch = initialDate.toEpochDay() }

    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val events = repository.events()
    val tasksByDueDate = remember(tasks) {
        tasks.filter { it.due != null }
            .groupBy { it.due!! }
            .mapValues { (date, dueTasks) -> tasksDueOn(dueTasks, date) }
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val railScroll = rememberScrollState(initial = with(density) { (DaytimeScrollHour * 62).dp.roundToPx() })
    val dayPagerState = rememberPagerState(initialPage = dayPageFor(initialDate)) { DayPagerPageCount }
    val weekPagerState = rememberPagerState(initialPage = weekPageFor(initialDate)) { WeekPagerPageCount }
    val monthPagerState = rememberPagerState(initialPage = monthPageFor(YearMonth.from(initialDate))) { MonthPagerPageCount }
    // Boundary previews move the week pager ahead of the committed date so
    // the compact row can stay continuous. Do not let that visual sync
    // become a second, possibly wrong date commit when it settles.
    var suppressedWeekPreview by remember { mutableStateOf<WeekPreviewSuppression?>(null) }
    var weekPreviewGeneration by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var compactBoundaryDay by remember { mutableStateOf<LocalDate?>(null) }
    var blockedBoundaryDay by remember { mutableStateOf<LocalDate?>(null) }
    var weekUserGestureActive by remember { mutableStateOf(false) }
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    var weekRollbackJob by remember { mutableStateOf<Job?>(null) }
    val currentZoom = zoomState
    val currentSelectedEpoch = rememberUpdatedState(selectedEpoch)
    // Only a user drag may commit a pager destination. Synchronizing a pager
    // to a clicked date must never feed intermediate pages back into selection.
    val pagerDragOrigins = remember { androidx.compose.runtime.mutableStateMapOf<PagerState, Long>() }
    listOf(dayPagerState, weekPagerState, monthPagerState).forEach { pager ->
        LaunchedEffect(pager) {
            pager.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) {
                    pagerDragOrigins[pager] = currentSelectedEpoch.value
                }
            }
        }
    }

    fun consumeUserSettle(pager: PagerState): Boolean =
        pagerDragOrigins.remove(pager) == currentSelectedEpoch.value
    val selectedWeekdayIndex = (selected.dayOfWeek.value - 1).coerceIn(0, 6)
    // A day swipe across a week boundary pages the strip to the neighboring
    // week while the drag is still live. Aim the pill at the previewed day
    // from the moment that starts, so it travels with the incoming week
    // instead of resting on the old column and jumping once the date commits.
    val selectorWeekdayIndex = ((compactBoundaryDay ?: selected).dayOfWeek.value - 1).coerceIn(0, 6)
    val compactSelectorPosition = remember {
        Animatable(selectedWeekdayIndex.toFloat())
    }

    LaunchedEffect(selectorWeekdayIndex) {
        compactSelectorPosition.animateTo(
            selectorWeekdayIndex.toFloat(),
            animationSpec = spring(dampingRatio = .82f, stiffness = 520f),
        )
    }

    fun cancelMotion() {
        zoomJob?.cancel()
        zoomJob = null
    }

    fun animateZoomTo(targetValue: Float) {
        cancelMotion()
        val target = targetValue.coerceIn(0f, 2f)
        val start = zoomState.floatValue
        zoomJob = scope.launch {
            animate(
                initialValue = start,
                targetValue = target,
                animationSpec = spring(dampingRatio = .85f, stiffness = 380f),
            ) { value, _ -> zoomState.floatValue = value.coerceIn(0f, 2f) }
            zoomState.floatValue = target
            settledZoom = target
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
        snapshotFlow { dayPagerState.isScrollInProgress to dayPagerState.settledPage }
            .distinctUntilChanged()
            .collect { (scrolling, it) ->
                if (scrolling || !consumeUserSettle(dayPagerState)) return@collect
                val date = dateForDayPage(it)
                if (date.toEpochDay() != currentSelectedEpoch.value) {
                    selectedEpoch = date.toEpochDay()
                    onDateChanged(date)
                }
            }
    }

    // The week strip is an independent seven-day pager. A swipe advances a
    // whole week while preserving the selected weekday; the committed date is
    // still written only after the pager settles.
    LaunchedEffect(weekPagerState) {
        snapshotFlow { weekPagerState.isScrollInProgress to weekPagerState.settledPage }
            .distinctUntilChanged()
            .collect { (scrolling, it) ->
                if (scrolling || !consumeUserSettle(weekPagerState)) return@collect
                val suppression = suppressedWeekPreview
                if (suppression?.targetPage == it) {
                    val activeGeneration = suppression.generation == weekPreviewGeneration
                    val activeBoundary = compactBoundaryDay == suppression.boundaryDay
                    // A live boundary preview is synthetic and must not write
                    // the selected date. A cancelled preview is also ignored
                    // until its rollback reaches the committed page. The
                    // latter closes the race where a stale synthetic target
                    // settles after the day gesture has been cancelled.
                    if (suppression.cancelled || (activeGeneration && activeBoundary)) {
                        if (suppression.committedPage == it) {
                            suppressedWeekPreview = null
                        }
                        return@collect
                    }
                    // The token no longer owns this settle (for example a
                    // user gesture invalidated it), so let the real pager
                    // destination commit normally.
                    suppressedWeekPreview = null
                } else if (suppression?.committedPage == it) {
                    // Clear a token when rollback reaches its owner even if
                    // the target page was cancelled before settling.
                    suppressedWeekPreview = null
                }
                val targetMonday = mondayForWeekPage(it)
                val currentDate = LocalDate.ofEpochDay(currentSelectedEpoch.value)
                val targetDate = targetMonday.plusDays((currentDate.dayOfWeek.value - 1).toLong())
                if (targetDate.toEpochDay() != currentSelectedEpoch.value) {
                    selectedEpoch = targetDate.toEpochDay()
                    onDateChanged(targetDate)
                }
            }
    }

    // A cancelled tap/drag can return to the current week without changing
    // settledPage. Clear the user-ownership guard when the pager's settle
    // animation is actually finished so a later boundary transition is not
    // mistaken for the original gesture.
    LaunchedEffect(weekPagerState) {
        snapshotFlow { weekPagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) weekUserGestureActive = false
            }
    }

    // A month swipe preserves the selected day number where possible. The
    // date-keyed pages keep the grid's hit regions aligned with what is drawn.
    LaunchedEffect(monthPagerState) {
        snapshotFlow { monthPagerState.isScrollInProgress to monthPagerState.settledPage }
            .distinctUntilChanged()
            .collect { (scrolling, it) ->
                if (scrolling || !consumeUserSettle(monthPagerState)) return@collect
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
            val targetWeekPage = weekPageFor(selected)
            if (weekPagerState.currentPage != targetWeekPage || abs(weekPagerState.currentPageOffsetFraction) > .001f) {
                weekPagerState.animateScrollToPage(targetWeekPage)
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
            if (pagerDragOrigins[dayPagerState] != selected.toEpochDay()) return@derivedStateOf 0f
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
    // Null while no same-week day preview is live. The pill follows this
    // directly during the drag; the spring below owns it the rest of the time.
    val compactSelectorPreview = remember(dayPagerTravel, selectedWeekdayIndex) {
        derivedStateOf {
            val liveOffset = dayPagerTravel.coerceIn(-1f, 1f)
            if (abs(liveOffset) <= .001f) return@derivedStateOf null
            val previewDate = if (liveOffset < 0f) selected.plusDays(1) else selected.minusDays(1)
            if (previewDate.with(DayOfWeek.MONDAY) != selected.with(DayOfWeek.MONDAY)) {
                return@derivedStateOf null
            }
            (selectedWeekdayIndex - liveOffset).coerceIn(0f, 6f)
        }
    }
    // Keep the spring seeded with the live preview so the handoff at release
    // continues from where the finger left the pill. Without this the preview
    // drops out the instant the settle consumes the gesture, the pill falls
    // back to the spring's stale previous weekday for a frame, and only then
    // animates to the committed day.
    LaunchedEffect(compactSelectorPreview) {
        snapshotFlow { compactSelectorPreview.value }
            .collect { preview -> preview?.let { compactSelectorPosition.snapTo(it) } }
    }
    val compactSelectorIndex by remember(compactSelectorPreview) {
        derivedStateOf { compactSelectorPreview.value ?: compactSelectorPosition.value }
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
                anchor = zoomState.floatValue.roundToInt().coerceIn(0, 2)
                velocityTracker = VelocityTracker()
            },
            onVerticalDrag = { change, delta ->
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
                // The detector only starts after vertical touch-slop wins the
                // gesture, so HorizontalPager retains horizontal swipes.
                // Down is positive in screen coordinates and expands.
                zoomState.floatValue = zoomAfterVerticalDrag(
                    zoomState.floatValue,
                    with(density) { delta.toDp().value },
                    ZoomStepDp,
                )
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity()
                animateZoomTo(
                    zoomSettleLevel(
                        zoom = zoomState.floatValue,
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
    // A boundary week should begin its own transition as soon as the pager
    // has selected that destination, rather than waiting for settledPage to
    // update the app's committed date after the animation completes. Keep
    // that preview alive through the last settling frame as well: the pager
    // can report that scrolling has stopped one frame before its settled-page
    // collector commits the new date, which otherwise exposes a blank/old
    // month row at the handoff.
    LaunchedEffect(dayPagerState, monthPagerState) {
        snapshotFlow {
            BoundaryPagerSnapshot(
                compactBoundaryDay = compactBoundaryDay,
                blockedBoundaryDay = blockedBoundaryDay,
                committedEpochDay = currentSelectedEpoch.value,
                dayInProgress = dayPagerState.isScrollInProgress &&
                    pagerDragOrigins[dayPagerState] == currentSelectedEpoch.value,
                dayTargetPage = dayPagerState.targetPage,
                daySettledPage = dayPagerState.settledPage,
                monthInProgress = monthPagerState.isScrollInProgress,
                monthSettledPage = monthPagerState.settledPage,
            )
        }.collect { snapshot ->
            val committed = LocalDate.ofEpochDay(snapshot.committedEpochDay)
            val target = dateForDayPage(snapshot.dayTargetPage)
            val targetIsBoundary = target.with(DayOfWeek.MONDAY) != committed.with(DayOfWeek.MONDAY)

            if (snapshot.dayInProgress && targetIsBoundary && target != snapshot.blockedBoundaryDay) {
                // Start the preview as soon as the day pager chooses a
                // boundary destination. The selected date itself remains
                // committed by the settled-page collector above.
                blockedBoundaryDay = null
                compactBoundaryDay = target
            } else {
                val boundary = snapshot.compactBoundaryDay
                if (boundary != null) {
                    val daySettledElsewhere =
                        !snapshot.dayInProgress &&
                            snapshot.daySettledPage != dayPageFor(boundary)
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
                    if (daySettledElsewhere ||
                        (dayTransitionComplete && monthTransitionComplete && committed == boundary)
                    ) {
                        if (daySettledElsewhere) {
                            // Mark the synthetic week target as cancelled
                            // before clearing the boundary state. This write
                            // must happen in the same snapshot as the clear;
                            // otherwise the week settled collector could see
                            // a late target between the two effects and commit
                            // the cancelled preview as a real date change.
                            suppressedWeekPreview?.let { preview ->
                                if (preview.boundaryDay == boundary && !preview.cancelled) {
                                    suppressedWeekPreview = preview.copy(cancelled = true)
                                }
                            }
                        }
                        compactBoundaryDay = null
                    }
                }
                if (!snapshot.dayInProgress && blockedBoundaryDay != null) {
                    blockedBoundaryDay = null
                }
            }
        }
    }
    LaunchedEffect(compactBoundaryDay, selectedEpoch) {
        val boundary = compactBoundaryDay
        if (boundary == null) {
            // A real week swipe owns the pager while it is settling. Do not
            // let clearing a concurrent day-boundary preview start a rollback
            // that fights the user's horizontal gesture.
            if (weekUserGestureActive) return@LaunchedEffect
            weekRollbackJob?.cancel()
            weekRollbackJob = null
            // A boundary drag can cancel after the week preview animation has
            // already reached its destination but before its settled-page
            // collector runs. Return that visual preview to the committed
            // week instead of allowing a stale suppression token to leave the
            // week pager ahead of the selected date.
            val committedWeekPage = weekPageFor(selected)
            val preview = suppressedWeekPreview
            if (preview != null) {
                if (preview.targetPage == committedWeekPage) {
                    // The boundary target is already the committed week; no
                    // suppression is needed if the collector has not consumed
                    // it yet.
                    suppressedWeekPreview = null
                } else if (!preview.cancelled) {
                    // Retain a tombstone until rollback reaches the owner.
                    // Clearing this immediately would let a late synthetic
                    // target settle into selected-date state.
                    suppressedWeekPreview = preview.copy(cancelled = true)
                }
            }
            if (weekPagerState.currentPage != committedWeekPage ||
                weekPagerState.targetPage != committedWeekPage
            ) {
                // Supersede an in-flight preview immediately. Waiting for
                // isScrollInProgress to clear leaves a race in which the
                // stale target can settle before the rollback starts. A user
                // gesture can invalidate this rollback through the observer
                // above; it cancels this job and invalidates its generation.
                weekRollbackJob?.cancel()
                val rollbackGeneration = weekPreviewGeneration
                weekRollbackJob = scope.launch {
                    val runningJob = currentCoroutineContext()[Job]
                    try {
                        if (weekUserGestureActive || weekPreviewGeneration != rollbackGeneration) {
                            return@launch
                        }
                        weekPagerState.animateScrollToPage(committedWeekPage)
                    } finally {
                        if (weekRollbackJob === runningJob) {
                            weekRollbackJob = null
                        }
                    }
                }
            }
            return@LaunchedEffect
        }

        val targetWeekPage = weekPageFor(boundary)
        weekRollbackJob?.cancel()
        weekRollbackJob = null
        if (!weekPagerState.isScrollInProgress && weekPagerState.currentPage != targetWeekPage) {
            val committedWeekPage = weekPageFor(selected)
            weekPreviewGeneration += 1
            suppressedWeekPreview = WeekPreviewSuppression(
                targetPage = targetWeekPage,
                committedPage = committedWeekPage,
                boundaryDay = boundary,
                generation = weekPreviewGeneration,
            )
            var completed = false
            try {
                weekPagerState.animateScrollToPage(targetWeekPage)
                completed = true
            } finally {
                // If the boundary drag reverses or the route disables
                // interaction before the target settles, do not leave a
                // one-shot suppression token behind to swallow a later
                // genuine week swipe. A completed animation keeps its token
                // until the settled collector consumes it or the cancellation
                // path above animates back to the committed week.
                if (!completed && suppressedWeekPreview?.targetPage == targetWeekPage) {
                    suppressedWeekPreview = null
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
        snapshotFlow { zoomState.floatValue }.collect { currentZoom ->
            val railVisible = currentZoom < DaySurfaceBlendEnd
            val agendaVisible = currentZoom > DaySurfaceBlendStart && currentZoom < 1.99f
            val blend = daySurfaceBlend(currentZoom)
            val nextOwner = when {
                !interactionEnabled || (!railVisible && !agendaVisible) -> false
                !railVisible -> true
                !agendaVisible -> false
                agendaOwnsInput -> blend >= .5f - DaySurfaceOwnershipHysteresis
                else -> blend > .5f + DaySurfaceOwnershipHysteresis
            }
            if (nextOwner != agendaOwnsInput) agendaOwnsInput = nextOwner
        }
    }
    val dayRailOwnsInput = interactionEnabled && !agendaOwnsInput
    val agendaOwnsInputNow = interactionEnabled && agendaOwnsInput

    val zoomLevel by remember(zoomState) {
        derivedStateOf { zoomState.floatValue.roundToInt().coerceIn(0, 2) }
    }
    val zoomBand by remember(zoomState) {
        derivedStateOf {
            when {
                zoomState.floatValue < .5f -> 0
                zoomState.floatValue < 1.5f -> 1
                else -> 2
            }
        }
    }

    BackHandler(enabled = interactionEnabled && settledZoom > .01f) {
        animateZoomTo(if (settledZoom.roundToInt() >= 2) 1f else 0f)
    }

    // Landscape on a wide window is a different layout, not a wider version
    // of the zoom continuum: the month grid is pinned open beside a day pane,
    // so the week strip, the day rail and the zoom gesture are not composed.
    var dayPaneCollapsed by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
    val splitLayout = shouldSplit(maxWidth.value.toInt(), maxHeight.value.toInt())
    val dayPaneShowing = splitLayout && !dayPaneCollapsed
    LaunchedEffect(dayPaneShowing) { onSplitPaneChanged(dayPaneShowing) }
    DisposableEffect(Unit) { onDispose { onSplitPaneChanged(false) } }
    if (splitLayout) {
        SplitHomeLayout(
            selected = selected,
            events = events,
            journals = journals,
            tasksByDueDate = tasksByDueDate,
            monthPagerState = monthPagerState,
            interactionEnabled = interactionEnabled,
            dayPaneCollapsed = dayPaneCollapsed,
            onToggleDayPane = { dayPaneCollapsed = !dayPaneCollapsed },
            onOpenMenu = onOpenMenu,
            onPreviousMonth = {
                scope.launch {
                    pagerDragOrigins[monthPagerState] = selectedEpoch
                    monthPagerState.animateScrollToPage((monthPagerState.currentPage - 1).coerceAtLeast(0))
                }
            },
            onNextMonth = {
                scope.launch {
                    pagerDragOrigins[monthPagerState] = selectedEpoch
                    monthPagerState.animateScrollToPage((monthPagerState.currentPage + 1).coerceAtMost(MonthPagerPageCount - 1))
                }
            },
            onToday = {
                selectedEpoch = FixtureDate.toEpochDay()
                onDateChanged(FixtureDate)
            },
            onDay = { date ->
                // A first tap selects the day into the pane. Only a tap on the
                // day already showing there drills into the day modal.
                val reselected = date == selected
                selectedEpoch = date.toEpochDay()
                onDateChanged(date)
                if (reselected) onDayClick?.invoke(date)
            },
            onEventClick = onEventClick,
            onTaskClick = onTaskClick,
            onTaskDone = onTaskDone,
            onAddOn = { date -> onOpenDay?.invoke(date) },
        )
        return@BoxWithConstraints
    }
    Column(Modifier.fillMaxSize()) {
        MonthHeading(
            day = selected,
            onOpenMenu = onOpenMenu,
            onPreviousMonth = {
                scope.launch {
                    pagerDragOrigins[monthPagerState] = selectedEpoch
                    monthPagerState.animateScrollToPage((monthPagerState.currentPage - 1).coerceAtLeast(0))
                }
            },
            onNextMonth = {
                scope.launch {
                    pagerDragOrigins[monthPagerState] = selectedEpoch
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
            val daySurfaceHeight = (maxHeight - handleHeight - 80.dp).coerceAtLeast(0.dp)
            // Keep the expensive month and day surfaces at stable measured
            // sizes. The zoom frame only clips the month endpoint and places
            // the handle/day surface, which avoids remeasuring the whole
            // pager and its long agenda on every drag sample.
            val calendarHeight = remember(maxHeight, splitGridHeight, detailedGridHeight) {
                derivedStateOf {
                    val currentZoom = currentZoom.value
                    if (currentZoom <= 1f) {
                        lerpDp(80.dp, splitGridHeight, currentZoom)
                    } else {
                        lerpDp(splitGridHeight, detailedGridHeight, (currentZoom - 1f).coerceIn(0f, 1f))
                    }
                }
            }
            Box(Modifier.fillMaxSize().clipToBounds()) {
                // The week pager is the compact endpoint of the same
                // selected-week geometry drawn by StaticMonthGrid. Keeping it
                // underneath the morphing canvas lets the canvas take over
                // at the exact shared row instead of painting a separate
                // week surface on top of a fading month.
                if (zoomState.value < MonthEndpointBlendEnd) {
                        WeekStrip(
                        state = weekPagerState,
                        day = selected,
                        displayedWeekDay = weekStripDay,
                        events = events,
                        tasksByDueDate = tasksByDueDate,
                        pagerOffset = dayPagerTravel,
                        selectorIndex = compactSelectorIndex,
                        gestureModifier = if (interactionEnabled) calendarGesture else Modifier,
                        interactionEnabled = interactionEnabled,
                            onUserSwipeStart = {
                                weekRollbackJob?.cancel()
                                weekRollbackJob = null
                                weekUserGestureActive = true
                                compactBoundaryDay?.let { boundary ->
                                    // Prevent the day-boundary observer from
                                    // recreating this preview while the real
                                    // week pager gesture owns the strip.
                                    blockedBoundaryDay = boundary
                                    compactBoundaryDay = null
                                }
                                weekPreviewGeneration += 1
                                // A human horizontal gesture takes ownership
                                // from any boundary preview. Without this
                                // invalidation, a quick swipe to the same page as
                                // a cancelled synthetic preview could be mistaken
                                // for that preview's settle.
                                suppressedWeekPreview = null
                            },
                        modifier = Modifier
                            .zIndex(1f)
                            .fillMaxWidth()
                            .requiredHeight(CompactWeekMetrics.Height)
                            .drawWithContent {
                                // The month Canvas owns the idle endpoint.
                                // During a horizontal preview this layer
                                // becomes the sole owner of the whole compact
                                // lane. The opaque backing is important: a
                                // transparent pager would leave the committed
                                // month row visible underneath and create the
                                // reported double-strip effect.
                                val previewVisible = weekPagerState.isScrollInProgress ||
                                    abs(weekPagerState.currentPageOffsetFraction) > .001f ||
                                    weekPageFor(weekStripDay) != weekPagerState.settledPage
                                if (previewVisible) {
                                    drawRect(CalinoColors.Canvas)
                                    drawContent()
                                }
                            },
                        onDay = { date ->
                            if (interactionEnabled) {
                                selectedEpoch = date.toEpochDay()
                                onDateChanged(date)
                            }
                        },
                    )
                }
                Box(
                    Modifier.fillMaxWidth()
                        .requiredHeight(detailedGridHeight)
                        .drawWithContent {
                            val visibleHeight = calendarHeight.value.toPx().coerceIn(0f, size.height)
                            clipRect(bottom = visibleHeight) {
                                this@drawWithContent.drawContent()
                            }
                        },
                ) {
                    MonthPager(
                        state = monthPagerState,
                        selected = selected,
                        events = events,
                        journals = journals,
                        tasksByDueDate = tasksByDueDate,
                        zoomState = currentZoom,
                        compactGridHeight = splitGridHeight,
                        detailedGridHeight = detailedGridHeight,
                        compactDay = weekStripDay,
                        compactSelectorIndex = compactSelectorIndex,
                        compactBoundaryTransition = isDayPagerBoundaryTransition,
                        modifier = Modifier.fillMaxSize(),
                        gestureModifier = if (interactionEnabled) calendarGesture else Modifier,
                        // At the compact endpoint the week pager is the sole
                        // horizontal owner. Disable the month pager as soon
                        // as the visual morph reaches its handoff threshold,
                        // including while a reverse zoom settle is still in
                        // flight.
                        userScrollEnabled = interactionEnabled &&
                            zoomState.value >= MonthEndpointBlendEnd,
                        onDay = { date ->
                            if (interactionEnabled) {
                                // The split level is a selection surface. Only
                                // the detailed settled level opens the day modal.
                                val isDetailed = zoomState.floatValue >= 1.5f
                                selectedEpoch = date.toEpochDay()
                                onDateChanged(date)
                                if (isDetailed) onDayClick?.invoke(date)
                            }
                        },
                    )
                }
                // The handle stays attached to the shared surface, so it
                // travels with the month-to-week morph as one gesture affordance.
                Box(
                    Modifier.fillMaxWidth()
                        .requiredHeight(handleHeight)
                        .offset {
                            IntOffset(0, with(density) { calendarHeight.value.roundToPx() })
                        },
                ) {
                    ZoomHandle(
                        zoomLevel = zoomLevel,
                        zoomBand = zoomBand,
                        gestureModifier = if (interactionEnabled) calendarGesture else Modifier,
                        onTap = {
                            val level = zoomState.floatValue.roundToInt().coerceIn(0, 2)
                            animateZoomTo(if (level < 2) level + 1f else 1f)
                        },
                    )
                }

                Box(
                    Modifier.fillMaxWidth()
                        .requiredHeight(daySurfaceHeight)
                        .offset {
                            IntOffset(
                                0,
                                with(density) {
                                    (calendarHeight.value + handleHeight).roundToPx()
                                },
                            )
                        }
                        .clipToBounds(),
                ) {
                    DayPagerSurface(
                        state = dayPagerState,
                        events = events,
                        tasksByDueDate = tasksByDueDate,
                        scrollState = railScroll,
                        modifier = Modifier.fillMaxSize(),
                        interactionEnabled = interactionEnabled,
                        zoomState = currentZoom,
                        dayRailOwnsInput = dayRailOwnsInput,
                        agendaOwnsInput = agendaOwnsInputNow,
                        onEvent = onEventClick,
                        onTaskDone = onTaskDone,
                        onTaskRescheduleTo = onTaskRescheduleTo,
                        onTaskClick = onTaskClick,
                        onOpenDay = splitOpenDay,
                    )
                }
            }
        }
    }
    }
}

/**
 * The landscape month root: the grid pinned open on the left, the selected
 * day's agenda on the right, and a rule between them carrying the pane's own
 * collapse control. The zoom continuum is deliberately absent here — with the
 * day already beside the grid there is nothing for the month-to-day morph to
 * reveal.
 */
@Composable
private fun SplitHomeLayout(
    selected: LocalDate,
    events: List<CalEvent>,
    journals: List<JournalEntry>,
    tasksByDueDate: Map<LocalDate, List<CalTask>>,
    monthPagerState: PagerState,
    interactionEnabled: Boolean,
    dayPaneCollapsed: Boolean,
    onToggleDayPane: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onEventClick: ((CalEvent) -> Unit)?,
    onTaskClick: ((CalTask) -> Unit)?,
    onTaskDone: (CalTask, Boolean) -> Unit,
    onAddOn: (LocalDate) -> Unit,
) {
    // The grid is drawn at its detailed endpoint and stays there. MonthPager
    // reads this as a plain State, so a constant is all the zoom it needs.
    val pinnedZoom = remember { mutableFloatStateOf(2f) }
    val paneWidth by animateDpAsState(
        targetValue = if (dayPaneCollapsed) 0.dp else SplitPaneWidthDp.dp,
        animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
        label = "day pane width",
    )
    val dayEvents = remember(events, selected) {
        events.filter { it.occursOn(selected) }
    }
    val dayTasks = tasksByDueDate[selected].orEmpty()

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            MonthHeading(
                day = selected,
                onOpenMenu = onOpenMenu,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onToday = onToday,
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val gridHeight = maxHeight
                MonthPager(
                    state = monthPagerState,
                    selected = selected,
                    events = events,
                    journals = journals,
                    tasksByDueDate = tasksByDueDate,
                    zoomState = pinnedZoom,
                    compactGridHeight = gridHeight,
                    detailedGridHeight = gridHeight,
                    compactDay = selected,
                    compactSelectorIndex = (selected.dayOfWeek.value - 1).toFloat(),
                    compactBoundaryTransition = false,
                    modifier = Modifier.fillMaxSize(),
                    // No vertical zoom drag in this layout, so the pager is
                    // the sole owner of the pointer stream over the grid.
                    gestureModifier = Modifier,
                    userScrollEnabled = interactionEnabled,
                    onDay = { date -> if (interactionEnabled) onDay(date) },
                )
            }
        }
        DayPaneDivider(collapsed = dayPaneCollapsed, onToggle = onToggleDayPane)
        if (paneWidth > 0.dp) {
            DayPane(
                day = selected,
                events = dayEvents,
                tasks = dayTasks,
                modifier = Modifier.width(paneWidth).fillMaxHeight().clipToBounds(),
                onEventClick = { _, event -> onEventClick?.invoke(event) },
                onTaskClick = onTaskClick,
                onTaskDone = onTaskDone,
                onAdd = { onAddOn(selected) },
            )
        }
    }
}

/**
 * The rule between the two panes, doubling as the pane's collapse control.
 * The painted chevron is compact; the touch lane around it is not.
 */
@Composable
private fun DayPaneDivider(collapsed: Boolean, onToggle: () -> Unit) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) 180f else 0f,
        animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
        label = "day pane chevron",
    )
    val label = if (collapsed) "Show day pane" else "Hide day pane"
    Box(
        Modifier.fillMaxHeight().width(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(CalinoColors.Line).align(Alignment.Center))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CalinoColors.Canvas)
                .calinoPressable(onClick = onToggle)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "\u203A",
                color = CalinoColors.Ink3,
                fontSize = 20.sp,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
    }
}

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun daySurfaceBlend(zoom: Float): Float = smoothStep(
    ((zoom - DaySurfaceBlendStart) / (DaySurfaceBlendEnd - DaySurfaceBlendStart)).coerceIn(0f, 1f),
)

@Composable
private fun MonthHeading(
    day: LocalDate,
    onOpenMenu: (() -> Unit)?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
) = CalinoMonthHeading(
    day = day,
    onOpenMenu = onOpenMenu,
    onPreviousMonth = onPreviousMonth,
    onNextMonth = onNextMonth,
    onToday = onToday,
    showToday = day != FixtureDate,
    subtitle = "Week ${day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${day.format(ShortDateFormatter)}",
)

@Composable
private fun WeekStrip(
    state: PagerState,
    day: LocalDate,
    displayedWeekDay: LocalDate,
    events: List<CalEvent>,
    tasksByDueDate: Map<LocalDate, List<CalTask>>,
    pagerOffset: Float,
    selectorIndex: Float,
    gestureModifier: Modifier,
    interactionEnabled: Boolean,
    onUserSwipeStart: () -> Unit,
    modifier: Modifier = Modifier,
    onDay: (LocalDate) -> Unit,
) {
    val semanticsModifier = if (interactionEnabled) Modifier else Modifier.clearAndSetSemantics { }
    val currentOnUserSwipeStart = rememberUpdatedState(onUserSwipeStart)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val observeHorizontalSwipe = Modifier.pointerInput(interactionEnabled, touchSlop) {
        if (interactionEnabled) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                var lastPosition = down.position
                var totalX = 0f
                var totalY = 0f
                var axisDecided = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val delta = change.position - lastPosition
                    lastPosition = change.position
                    totalX += delta.x
                    totalY += delta.y
                    if (!axisDecided &&
                        (abs(totalX) > touchSlop || abs(totalY) > touchSlop)
                    ) {
                        axisDecided = true
                        if (abs(totalX) > abs(totalY)) {
                            currentOnUserSwipeStart.value()
                        }
                    }
                }
            }
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(CompactWeekMetrics.Height)
            .then(observeHorizontalSwipe)
            .then(gestureModifier)
            .then(semanticsModifier)
            .clipToBounds(),
    ) {
        val committedMonday = day.with(DayOfWeek.MONDAY)
        val settledIndex = (day.dayOfWeek.value - 1).coerceIn(0, 6)
        // A day pager offset is screen travel: negative reveals tomorrow and
        // positive reveals yesterday. Keep the week row fixed, but move its
        // indicator in lockstep while the agenda is being dragged/settled.
        // [selectorIndex] already tracks the previewed day, including a
        // boundary day in the neighboring week, so the week the strip is
        // displaying always follows it.
        val displayedMonday = displayedWeekDay.with(DayOfWeek.MONDAY)
        val indicatorTargetIndex = selectorIndex.coerceIn(0f, 6f)
        HorizontalPager(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = CompactWeekMetrics.HorizontalPadding,
                    vertical = CompactWeekMetrics.VerticalPadding,
                ),
            beyondViewportPageCount = 0,
            userScrollEnabled = interactionEnabled,
            key = { page -> page },
        ) { page ->
            val pageMonday = mondayForWeekPage(page)
            val pageDay = if (pageMonday == displayedWeekDay.with(DayOfWeek.MONDAY)) {
                displayedWeekDay
            } else {
                pageMonday.plusDays((day.dayOfWeek.value - 1).toLong())
            }
            // The displayed week owns the moving indicator. Other pages keep
            // the committed weekday so a week that is only sliding past does
            // not animate an indicator of its own.
            WeekStripPage(
                monday = pageMonday,
                selected = pageDay,
                indicatorIndex = if (pageMonday == displayedMonday) indicatorTargetIndex else settledIndex.toFloat(),
                events = events,
                tasksByDueDate = tasksByDueDate,
                interactionEnabled = interactionEnabled,
                onDay = onDay,
            )
        }
    }
}

@Composable
private fun WeekStripPage(
    monday: LocalDate,
    selected: LocalDate,
    indicatorIndex: Float,
    events: List<CalEvent>,
    tasksByDueDate: Map<LocalDate, List<CalTask>>,
    interactionEnabled: Boolean,
    onDay: (LocalDate) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellWidth = maxWidth / 7
        val selectedIndex = (selected.dayOfWeek.value - 1).coerceIn(0, 6)
        val indicatorIndex = indicatorIndex.coerceIn(0f, 6f)

        Box(Modifier.fillMaxSize()) {
            // This is one indicator shared by the whole strip. Its animated
            // position makes Monday -> Tuesday a physical move, not two
            // independent cells fading their backgrounds.
            Box(
                Modifier.offset(x = cellWidth * indicatorIndex)
                    .width(cellWidth)
                    .height(CompactWeekMetrics.PillHeight)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(CompactWeekMetrics.PillRadius))
                    .background(CalinoColors.Ink.copy(alpha = .95f)),
            )
            Row(Modifier.fillMaxSize()) {
                (0..6).forEach { dayDelta ->
                    val date = monday.plusDays(dayDelta.toLong())
                    val distance = abs(indicatorIndex - dayDelta.toFloat()).coerceIn(0f, 1f)
                    WeekDay(
                        date = date,
                        events = events,
                        tasksDueCount = openTasksDueOn(tasksByDueDate[date].orEmpty(), date).size,
                        currentSelectionWeight = 1f - distance,
                        targetSelectionWeight = 0f,
                        committedSelected = date == selected,
                        drawSelectionBackground = false,
                        modifier = Modifier.weight(1f),
                        interactionEnabled = interactionEnabled,
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
    tasksDueCount: Int = 0,
    currentSelectionWeight: Float,
    targetSelectionWeight: Float,
    committedSelected: Boolean,
    drawSelectionBackground: Boolean = true,
    modifier: Modifier,
    interactionEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val selectedWeight = max(currentSelectionWeight, targetSelectionWeight).coerceIn(0f, 1f)
    val background by animateColorAsState(
        if (drawSelectionBackground) CalinoColors.Ink.copy(alpha = selectedWeight) else Color.Transparent,
        label = "week selection",
    )
    val weekdayColor = lerpColor(CalinoColors.Ink3, Color.White.copy(.65f), selectedWeight)
    val dateColor = lerpColor(CalinoColors.Ink2, Color.White, selectedWeight)
    val interactionModifier = if (interactionEnabled) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    val semanticsModifier = if (interactionEnabled) {
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append(date.format(FullDateFormatter))
                if (committedSelected) append(", selected")
                if (tasksDueCount > 0) append(", $tasksDueCount open tasks due")
            }
        }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .then(interactionModifier)
            .then(semanticsModifier)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = ComposeTextStyle(fontSize = 10.sp),
            color = weekdayColor,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                style = ComposeTextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                color = dateColor,
            )
        }
        Spacer(Modifier.height(1.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(7.dp),
        ) {
            eventsFor(events, date).take(4).forEach { event ->
                Box(Modifier.size(
                    width = if (event.allDay) 18.dp else 5.dp,
                    height = if (event.allDay) 3.dp else 5.dp,
                ).clip(RoundedCornerShape(2.dp)).background(Color(event.color)))
            }
        }
    }
}

@Composable
private fun CalendarTaskRow(
    task: CalTask,
    onTaskDone: ((Boolean) -> Unit)?,
    onTaskRescheduleTo: ((CalTask, LocalDate?) -> Unit)?,
    onTaskClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var rescheduleOpen by remember(task.id) { mutableStateOf(false) }
    val baseDate = task.due ?: FixtureDate

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskRow(
                task = task,
                modifier = Modifier.weight(1f),
                onCheckedChange = onTaskDone,
                onClick = onTaskClick,
                compact = true,
            )
            if (!task.done && onTaskRescheduleTo != null) {
                IconButton(
                    onClick = { rescheduleOpen = !rescheduleOpen },
                    modifier = Modifier
                        .size(36.dp)
                        .semantics {
                            contentDescription = if (rescheduleOpen) {
                                "Hide reschedule options for ${task.title}"
                            } else {
                                "Show reschedule options for ${task.title}"
                            }
                        },
                ) {
                    Icon(
                        CalinoIcons.Repeat,
                        contentDescription = null,
                        tint = CalinoColors.Ink2,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = rescheduleOpen && !task.done && onTaskRescheduleTo != null,
            enter = expandVertically(tween(180)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 8.dp, bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    baseDate.plusDays(1) to "Tomorrow",
                    baseDate.plusDays(7) to "Next week",
                ).forEach { (date, label) ->
                    TextButton(
                        onClick = {
                            rescheduleOpen = false
                            onTaskRescheduleTo?.invoke(task, date)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .semantics {
                                contentDescription = "Reschedule ${task.title} to $label"
                            },
                    ) {
                        Text(label, color = CalinoColors.Ink2, fontSize = 11.sp, maxLines = 1)
                    }
                }
                TextButton(
                    onClick = {
                        rescheduleOpen = false
                        onTaskRescheduleTo?.invoke(task, null)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .semantics {
                            contentDescription = "Remove due date from ${task.title}"
                        },
                ) {
                    Text("No date", color = CalinoColors.Ink2, fontSize = 11.sp, maxLines = 1)
                }
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
    tasksByDueDate: Map<LocalDate, List<CalTask>>,
    zoomState: androidx.compose.runtime.State<Float>,
    compactGridHeight: Dp,
    detailedGridHeight: Dp,
    compactDay: LocalDate,
    compactSelectorIndex: Float,
    compactBoundaryTransition: Boolean,
    modifier: Modifier,
    gestureModifier: Modifier,
    userScrollEnabled: Boolean,
    onDay: (LocalDate) -> Unit,
) {
    val compactPreviewVisible by remember(zoomState) {
        derivedStateOf { zoomState.value < .999f }
    }
    val monthInteractive by remember(zoomState) {
        derivedStateOf { zoomState.value >= .2f }
    }
    // StaticMonthGrid owns the compact endpoint as well as the expanded
    // month. The week pager remains an interaction/preview layer, so the
    // selected week never fades out while a second idle row fades in.
    val monthVisualAlpha = remember { mutableFloatStateOf(1f) }
    Box(modifier.clipToBounds().then(gestureModifier)) {
        fun effectiveMonth(page: Int): YearMonth {
            val pagerMonth = monthForPage(page)
            val compactTargetPage = compactBoundaryTransition && page == state.currentPage && compactPreviewVisible
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
            // Page identity is stable while boundary preview content changes.
            // Re-keying from transient preview state would recreate the page
            // mid-gesture and can cause a jump or blank frame.
            key = { page -> page },
        ) { page ->
            val pagerMonth = monthForPage(page)
            // While compacting, the visible row belongs to the targeted day,
            // not necessarily to the month page that is still settling. This
            // keeps a boundary swipe from briefly exposing the first row of
            // the old/next month.
            val compactTargetPage = compactBoundaryTransition && page == state.currentPage && compactPreviewVisible
            val pageMonth = effectiveMonth(page)
            val pageSelected = pageMonth.atDay(
                (if (compactTargetPage) compactDay.dayOfMonth else selected.dayOfMonth)
                    .coerceAtMost(pageMonth.lengthOfMonth()),
            )
            val monthLayer = Modifier
                .fillMaxWidth()
                .requiredHeight(detailedGridHeight)
            Box(Modifier.fillMaxSize()) {
                Box(monthLayer) {
                    StaticMonthGrid(
                        selected = pageSelected,
                        month = pageMonth,
                        events = events,
                        journals = journals,
                        tasksByDueDate = tasksByDueDate,
                        visualAlpha = monthVisualAlpha,
                        zoomState = zoomState,
                        compactGridHeight = compactGridHeight,
                        detailedGridHeight = detailedGridHeight,
                        compactDay = compactDay,
                        compactSelectorIndex = compactSelectorIndex,
                        interactionEnabled = monthInteractive,
                        onDay = onDay,
                    )
                }
            }
        }
    }
}

/**
 * One canvas owns the compact-to-detailed month morph. Keeping the row
 * geometry in one draw pass avoids crossfading two full month grids with
 * different row heights, which both looked like a ghosted double calendar
 * and forced the renderer to maintain two large layers.
 */
@Composable
private fun MorphingMonthGrid(
    selected: LocalDate,
    month: YearMonth,
    events: List<CalEvent>,
    journals: List<JournalEntry>,
    zoomState: androidx.compose.runtime.State<Float>,
    compactGridHeight: Dp,
    detailedGridHeight: Dp,
    interactionEnabled: Boolean,
    targetHeight: Dp,
    onDay: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val rows = monthGridRows(month)
    val monthEvents = remember(events, month) { monthEventIndex(events, month) }
    val monthJournalDates = remember(journals, month) { monthJournalDates(journals, month) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val dateStyle = remember {
        ComposeTextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    val weekdayStyle = remember { ComposeTextStyle(fontSize = 10.sp) }
    val eventStyle = remember { ComposeTextStyle(fontSize = 10.5.sp, lineHeight = 12.sp) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding = CompactWeekMetrics.HorizontalPadding
        val headerHeight = 22.dp
        val cellWidth = ((maxWidth - horizontalPadding * 2) / 7).coerceAtLeast(0.dp)
        val eventTextMaxWidth = with(density) {
            (cellWidth - 2.dp - 8.dp - 3.dp).toPx().roundToInt().coerceAtLeast(1)
        }
        val dateLayouts = remember(month, density) {
            List(rows * 7) { index ->
                textMeasurer.measure(
                    start.plusDays(index.toLong()).dayOfMonth.toString(),
                    dateStyle,
                )
            }
        }
        val weekdayLayouts = remember(density) {
            WeekdayLetters.map { textMeasurer.measure(it, weekdayStyle) }
        }
        val eventLayouts = remember(month, events, eventTextMaxWidth, density) {
            buildMap {
                monthEvents.values.flatten().forEach { event ->
                    put(
                        event.id,
                        textMeasurer.measure(
                            event.title,
                            eventStyle,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            constraints = Constraints(maxWidth = eventTextMaxWidth),
                        ),
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val zoom = zoomState.value.coerceIn(0f, 2f)
                val detailProgress = smoothStep((zoom - 1f).coerceIn(0f, 1f))
                val compactReveal = if (zoom < 1f) {
                    smoothStep(
                        ((zoom - MonthEndpointBlendStart) /
                            (1f - MonthEndpointBlendStart)).coerceIn(0f, 1f),
                    )
                } else {
                    1f
                }
                val revealAlpha = compactReveal
                val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
                val headerHeightPx = with(density) { headerHeight.toPx() }
                val compactHeightPx = with(density) { compactGridHeight.toPx() }
                val detailedHeightPx = with(density) { detailedGridHeight.toPx() }
                val gridHeightPx = compactHeightPx + (detailedHeightPx - compactHeightPx) * detailProgress
                val cellWidthPx = ((size.width - horizontalPaddingPx * 2f) / 7f).coerceAtLeast(0f)
                val rowHeightPx = ((gridHeightPx - headerHeightPx) / rows).coerceAtLeast(0f)
                val dateSizePx = with(density) {
                    (22.dp.toPx() + (25.dp.toPx() - 22.dp.toPx()) * detailProgress)
                }
                val dateTopPaddingPx = with(density) { 2.dp.toPx() }
                val dateGapPx = with(density) { 1.dp.toPx() }
                val eventMarkerGapPx = with(density) { 3.dp.toPx() }
                val journalRadiusPx = with(density) { 2.dp.toPx() }
                val chipHeightPx = with(density) { 20.dp.toPx() }
                val chipHorizontalPaddingPx = with(density) { 2.dp.toPx() }
                val chipTextStartPx = with(density) { 8.dp.toPx() }

                fun faded(color: Color, factor: Float = 1f): Color =
                    color.copy(alpha = color.alpha * revealAlpha * factor.coerceIn(0f, 1f))

                WeekdayLetters.forEachIndexed { column, _ ->
                    val layout = weekdayLayouts[column]
                    drawText(
                        layout,
                        topLeft = Offset(
                            horizontalPaddingPx + cellWidthPx * column + (cellWidthPx - layout.size.width) / 2f,
                            (headerHeightPx - layout.size.height) / 2f,
                        ),
                        color = faded(CalinoColors.Ink3),
                    )
                }

                repeat(rows * 7) { index ->
                    val row = index / 7
                    val column = index % 7
                    val date = start.plusDays(index.toLong())
                    val cellLeft = horizontalPaddingPx + cellWidthPx * column
                    val cellTop = headerHeightPx + rowHeightPx * row
                    val isSelected = date == selected
                    val isToday = date == FixtureDate
                    val cellFill = when {
                        isSelected -> CalinoColors.AccentSoft.copy(alpha = .72f)
                        isToday -> CalinoColors.AccentSoft.copy(alpha = .45f)
                        else -> Color.Transparent
                    }
                    if (cellFill != Color.Transparent) {
                        drawRoundRect(
                            color = faded(cellFill),
                            topLeft = Offset(cellLeft, cellTop),
                            size = Size(cellWidthPx, rowHeightPx),
                            cornerRadius = CornerRadius(with(density) { 4.dp.toPx() }),
                        )
                    }

                    val dateTop = cellTop + dateTopPaddingPx
                    if (isSelected || isToday) {
                        drawCircle(
                            color = faded(
                                if (isSelected) CalinoColors.Accent else CalinoColors.Accent.copy(alpha = .78f),
                            ),
                            radius = dateSizePx / 2f,
                            center = Offset(cellLeft + cellWidthPx / 2f, dateTop + dateSizePx / 2f),
                        )
                    }
                    val dateLayout = dateLayouts[index]
                    val dateColor = if (isSelected) Color.White else if (YearMonth.from(date) == month) {
                        CalinoColors.Ink2
                    } else {
                        CalinoColors.Ink3.copy(.5f)
                    }
                    drawText(
                        dateLayout,
                        topLeft = Offset(
                            cellLeft + (cellWidthPx - dateLayout.size.width) / 2f,
                            dateTop + (dateSizePx - dateLayout.size.height) / 2f,
                        ),
                        color = faded(dateColor),
                    )

                    val dayEvents = monthEvents[date].orEmpty()
                    val eventAreaTop = dateTop + dateSizePx + dateGapPx
                    val markerEvents = dayEvents.take(4)
                    val rawWidths = markerEvents.map { event ->
                        with(density) { if (event.allDay) 18.dp.toPx() else 5.dp.toPx() }
                    }
                    val totalGap = eventMarkerGapPx * (markerEvents.size - 1).coerceAtLeast(0)
                    val rawTotal = rawWidths.sum() + totalGap
                    val markerScale = if (rawTotal > 0f) {
                        min(1f, (cellWidthPx - totalGap).coerceAtLeast(1f) / rawTotal)
                    } else {
                        1f
                    }
                    val markerTotal = rawTotal * markerScale
                    var markerLeft = cellLeft + (cellWidthPx - markerTotal) / 2f
                    val eventMorph = smoothStep((detailProgress - .18f).coerceIn(0f, 1f) / .82f)
                    markerEvents.forEachIndexed { eventIndex, event ->
                        val markerWidth = rawWidths[eventIndex] * markerScale
                        val markerHeight = with(density) { if (event.allDay) 3.dp.toPx() else 5.dp.toPx() }
                        val markerTop = eventAreaTop + (with(density) { 7.dp.toPx() } - markerHeight) / 2f
                        if (eventIndex < 2) {
                            val chipWidth = (cellWidthPx - chipHorizontalPaddingPx * 2f).coerceAtLeast(1f)
                            val chipTop = eventAreaTop + chipHeightPx * eventIndex
                            val x = markerLeft + (cellLeft + chipHorizontalPaddingPx - markerLeft) * eventMorph
                            val y = markerTop + (chipTop - markerTop) * eventMorph
                            val width = markerWidth + (chipWidth - markerWidth) * eventMorph
                            val height = markerHeight + (chipHeightPx - markerHeight) * eventMorph
                            val eventColor = Color(event.color)
                            val chipColor = eventTint(eventColor, if (event.allDay) .18f else .10f)
                            val textMorph = smoothStep(((eventMorph - .55f) / .45f).coerceIn(0f, 1f))
                            drawRoundRect(
                                color = faded(lerpColor(eventColor, chipColor, eventMorph)),
                                topLeft = Offset(x, y),
                                size = Size(width, height),
                                cornerRadius = CornerRadius(with(density) { 2.dp.toPx() + 4.dp.toPx() * eventMorph }),
                            )
                            if (eventMorph > .01f) {
                                drawRoundRect(
                                    color = faded(eventColor, eventMorph),
                                    topLeft = Offset(x, y + 3.dp.toPx()),
                                    size = Size(with(density) { 2.5.dp.toPx() }, height - 6.dp.toPx()),
                                    cornerRadius = CornerRadius(with(density) { 1.dp.toPx() }),
                                )
                                if (textMorph > .01f) eventLayouts[event.id]?.let { layout ->
                                    drawText(
                                        layout,
                                        topLeft = Offset(
                                            x + chipTextStartPx,
                                            y + (height - layout.size.height) / 2f,
                                        ),
                                        color = faded(CalinoColors.Ink, textMorph),
                                    )
                                }
                            }
                        } else {
                            drawRoundRect(
                                color = faded(Color(event.color), 1f - eventMorph),
                                topLeft = Offset(markerLeft, markerTop),
                                size = Size(markerWidth, markerHeight),
                                cornerRadius = CornerRadius(with(density) { 2.dp.toPx() }),
                            )
                        }
                        markerLeft += markerWidth + eventMarkerGapPx
                    }
                    val overflow = (dayEvents.size - 2).coerceAtLeast(0)
                    val overflowMorph = smoothStep(((eventMorph - .55f) / .45f).coerceIn(0f, 1f))
                    if (overflow > 0 && overflowMorph > .01f) {
                        val layout = textMeasurer.measure(
                            "+$overflow",
                            ComposeTextStyle(fontSize = 10.sp),
                        )
                        drawText(
                            layout,
                            topLeft = Offset(
                                cellLeft + (cellWidthPx - layout.size.width) / 2f,
                                eventAreaTop + 2 * chipHeightPx,
                            ),
                            color = faded(CalinoColors.Ink3, overflowMorph),
                        )
                    }
                    if (date in monthJournalDates) {
                        drawCircle(
                            color = faded(CalinoColors.Plum),
                            radius = journalRadiusPx,
                            center = Offset(
                                cellLeft + cellWidthPx / 2f,
                                cellTop + rowHeightPx - journalRadiusPx - 1.dp.toPx(),
                            ),
                        )
                    }
                }
            }
            MonthGridHitTargets(
                selected = selected,
                month = month,
                events = monthEvents,
                journalDates = monthJournalDates,
                targetHeight = targetHeight,
                interactionEnabled = interactionEnabled,
                onDay = onDay,
            )
        }
    }
}

@Composable
private fun MonthGridHitTargets(
    selected: LocalDate,
    month: YearMonth,
    events: Map<LocalDate, List<CalEvent>>,
    journalDates: Set<LocalDate>,
    tasks: Map<LocalDate, List<CalTask>> = emptyMap(),
    targetHeight: Dp,
    zoomState: androidx.compose.runtime.State<Float>? = null,
    compactGridHeight: Dp = targetHeight,
    detailedGridHeight: Dp = targetHeight,
    compactDay: LocalDate = selected,
    interactionEnabled: Boolean,
    onDay: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val rows = monthGridRows(month)

    Layout(
        content = {
            repeat(rows) { row ->
                repeat(7) { column ->
                    val date = start.plusDays((row * 7 + column).toLong())
                    val dayEvents = events[date].orEmpty()
                    val dueTasks = tasks[date].orEmpty()
                    val dateDescription = remember(date, selected, dayEvents, date in journalDates, dueTasks) {
                            buildString {
                                append(date.format(FullDateFormatter))
                                if (date == selected) append(", selected")
                                if (dayEvents.isNotEmpty()) append(", events: ").append(dayEvents.joinToString { it.title })
                                if (date in journalDates) append(", journal entry")
                                val openTaskCount = dueTasks.count { !it.done }
                                if (openTaskCount > 0) append(", $openTaskCount open tasks due")
                            }
                        }
                    val cellModifier = if (interactionEnabled) {
                        Modifier
                            .clickable { onDay(date) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = dateDescription
                            }
                    } else {
                        Modifier.clearAndSetSemantics { }
                    }
                    Box(cellModifier)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val horizontalPaddingPx = CompactWeekMetrics.HorizontalPadding.roundToPx()
        val headerHeightPx = 22.dp.roundToPx().toFloat()
        val compactHeightPx = compactGridHeight.roundToPx().toFloat()
        val detailedHeightPx = detailedGridHeight.roundToPx().toFloat()
        // Read the animated zoom in measurement, not composition. The 42
        // semantic hit nodes keep a stable composition tree while only their
        // bounds are remeasured as the canvas morphs.
        val zoom = zoomState?.value?.coerceIn(0f, 2f) ?: 2f
        val compactProgress = smoothStep(1f - zoom.coerceIn(0f, 1f))
        val detailProgress = smoothStep((zoom - 1f).coerceIn(0f, 1f))
        val compactWeekStart = compactDay.with(DayOfWeek.MONDAY)
        val compactWeekRow = ((compactWeekStart.toEpochDay() - start.toEpochDay()) / 7L)
            .toInt()
            .coerceIn(0, rows - 1)
        val compactStartHeightPx = CompactWeekMetrics.Height.roundToPx().toFloat()
        val visibleHeightPx = if (zoom <= 1f) {
            compactStartHeightPx + (compactHeightPx - compactStartHeightPx) * zoom
        } else {
            compactHeightPx + (detailedHeightPx - compactHeightPx) * (zoom - 1f)
        }
        val contentHeaderHeightPx = headerHeightPx * (1f - compactProgress)
        val compactAvailableHeightPx = (compactStartHeightPx +
            (compactHeightPx - compactStartHeightPx) * zoom.coerceIn(0f, 1f) - contentHeaderHeightPx)
            .coerceAtLeast(0f)
        val compactOtherRowWeight = (1f - compactProgress).coerceAtLeast(.001f)
        val compactTotalRowWeight = 1f + compactOtherRowWeight * (rows - 1)
        val compactEqualRowHeightPx = ((compactHeightPx - headerHeightPx) / rows).coerceAtLeast(0f)
        val detailedRowHeightPx = ((detailedHeightPx - headerHeightPx) / rows).coerceAtLeast(0f)
        fun rowHeight(row: Int): Float = if (zoom <= 1f) {
            val weight = if (row == compactWeekRow) 1f else compactOtherRowWeight
            compactAvailableHeightPx * weight / compactTotalRowWeight
        } else {
            compactEqualRowHeightPx + (detailedRowHeightPx - compactEqualRowHeightPx) * detailProgress
        }
        val cellWidth = ((constraints.maxWidth - horizontalPaddingPx * 2) / 7).coerceAtLeast(1)
        val placeables = measurables.mapIndexed { index, measurable ->
            val row = index / 7
            measurable.measure(
                Constraints.fixed(cellWidth, rowHeight(row).roundToInt().coerceAtLeast(1)),
            )
        }
        val layoutHeight = if (zoomState == null) {
            targetHeight.roundToPx()
        } else {
            visibleHeightPx.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        }
        layout(constraints.maxWidth, layoutHeight) {
            var y = contentHeaderHeightPx
            repeat(rows) { row ->
                val rowHeightPx = rowHeight(row)
                // Keep a row interactive while it is still visibly large
                // enough to contain its painted dates. Only unplace rows once
                // the same interpolated geometry has collapsed them below a
                // meaningful visual lane; this avoids showing dates that no
                // longer respond during the reverse morph.
                val rowHidden = zoomState != null && zoom <= 1f &&
                    row != compactWeekRow && rowHeightPx < 20.dp.roundToPx()
                if (!rowHidden) {
                    repeat(7) { column ->
                        val index = row * 7 + column
                        placeables[index].placeRelative(
                            x = horizontalPaddingPx + column * cellWidth,
                            y = y.roundToInt(),
                        )
                    }
                }
                y += rowHeightPx
            }
        }
    }
}

/**
 * One fixed-size month surface owns the compact-to-detailed morph. Text and
 * event labels are laid out once, while the draw pass interpolates row bounds
 * and marker/chip geometry without crossfading two month grids.
 */
@Composable
private fun StaticMonthGrid(
    selected: LocalDate,
    month: YearMonth,
    events: List<CalEvent>,
    journals: List<JournalEntry>,
    tasksByDueDate: Map<LocalDate, List<CalTask>>,
    visualAlpha: androidx.compose.runtime.State<Float>,
    zoomState: androidx.compose.runtime.State<Float>,
    compactGridHeight: Dp,
    detailedGridHeight: Dp,
    compactDay: LocalDate,
    compactSelectorIndex: Float,
    interactionEnabled: Boolean,
    onDay: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val rows = monthGridRows(month)
    val monthEvents = remember(events, month) { monthEventIndex(events, month) }
    val monthJournalDates = remember(journals, month) { monthJournalDates(journals, month) }
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dateStyle = remember {
        ComposeTextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
    }
    val weekdayStyle = remember {
        ComposeTextStyle(fontSize = 10.sp)
    }
    val eventStyle = remember {
        ComposeTextStyle(fontSize = 10.5.sp, lineHeight = 12.sp)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding = CompactWeekMetrics.HorizontalPadding
        val headerHeight = 22.dp
        val gridWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp)
        val cellWidth = gridWidth / 7
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val headerHeightPx = with(density) { headerHeight.toPx() }
        val compactGridHeightPx = with(density) { compactGridHeight.toPx() }
        val detailedGridHeightPx = with(density) { detailedGridHeight.toPx() }
        val detailedRowHeightPx = ((detailedGridHeightPx - headerHeightPx) / rows).coerceAtLeast(0f)
        val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
        val dateGapPx = with(density) { 1.dp.toPx() }
        val dateTopPaddingPx = with(density) { 2.dp.toPx() }
        val compactDateSizePx = with(density) { 22.dp.toPx() }
        val detailedDateSizePx = with(density) { 25.dp.toPx() }
        val eventMarkerGapPx = with(density) { 3.dp.toPx() }
        val eventAreaHeightPx = with(density) { 7.dp.toPx() }
        val chipHeightPx = with(density) { 20.dp.toPx() }
        val chipHorizontalPaddingPx = with(density) { 2.dp.toPx() }
        val chipTextStartPx = with(density) { 8.dp.toPx() }
        val chipTextEndPx = with(density) { 3.dp.toPx() }
        val dateLayouts = remember(month, density) {
            List(rows * 7) { index ->
                textMeasurer.measure(
                    text = start.plusDays(index.toLong()).dayOfMonth.toString(),
                    style = dateStyle,
                )
            }
        }
        val weekdayLayouts = remember(density) {
            WeekdayLetters.map { textMeasurer.measure(it, weekdayStyle) }
        }
        val eventTextMaxWidth = (cellWidthPx - chipHorizontalPaddingPx * 2f - chipTextStartPx - chipTextEndPx)
            .roundToInt()
            .coerceAtLeast(1)
        val eventLayouts = remember(month, events, eventTextMaxWidth, density) {
            buildMap {
                monthEvents.values.flatten().forEach { event ->
                    put(
                        event.id,
                        textMeasurer.measure(
                            text = event.title,
                            style = eventStyle,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            constraints = Constraints(maxWidth = eventTextMaxWidth),
                        ),
                    )
                }
            }
        }
        val cellDates = remember(month) {
            List(rows * 7) { index -> start.plusDays(index.toLong()) }
        }
        val cellEvents = remember(month, events) {
            cellDates.map { date -> monthEvents[date].orEmpty() }
        }
        val inMonthFlags = remember(month) {
            cellDates.map { date -> YearMonth.from(date) == month }
        }
        val compactMarkerWidths = remember(month, events, density) {
            cellEvents.map { dayEvents ->
                FloatArray(dayEvents.size.coerceAtMost(4)) { index ->
                    with(density) { if (dayEvents[index].allDay) 18.dp.toPx() else 5.dp.toPx() }
                }
            }
        }
        val overflowStyle = remember { ComposeTextStyle(fontSize = 10.sp) }
        val overflowLayouts = remember(month, events, density) {
            cellEvents.map { dayEvents ->
                val overflow = (dayEvents.size - 2).coerceAtLeast(0)
                if (overflow > 0) {
                    textMeasurer.measure("+$overflow", overflowStyle)
                } else {
                    null
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val drawAlpha = visualAlpha.value.coerceIn(0f, 1f)
                val zoom = zoomState.value.coerceIn(0f, 2f)
                val compactProgress = smoothStep(1f - zoom.coerceIn(0f, 1f))
                val detailProgress = smoothStep((zoom - 1f).coerceIn(0f, 1f))
                val compactWeekStart = compactDay.with(DayOfWeek.MONDAY)
                val compactWeekRow = ((compactWeekStart.toEpochDay() - start.toEpochDay()) / 7L)
                    .toInt()
                    .coerceIn(0, rows - 1)
                val contentHeaderHeightPx = headerHeightPx * (1f - compactProgress)
                val compactStartHeightPx = with(density) { CompactWeekMetrics.Height.toPx() }
                val compactVisibleHeightPx = compactStartHeightPx +
                    (compactGridHeightPx - compactStartHeightPx) * zoom.coerceIn(0f, 1f)
                val compactAvailableHeightPx = (compactVisibleHeightPx - contentHeaderHeightPx).coerceAtLeast(0f)
                val compactOtherRowWeight = compactProgress.let { (1f - it).coerceAtLeast(.001f) }
                val compactTotalRowWeight = 1f + compactOtherRowWeight * (rows - 1)
                val compactEqualRowHeightPx =
                    ((compactGridHeightPx - headerHeightPx) / rows).coerceAtLeast(0f)
                fun compactRowHeightFor(row: Int): Float {
                    val weight = if (row == compactWeekRow) 1f else compactOtherRowWeight
                    return compactAvailableHeightPx * weight / compactTotalRowWeight
                }
                fun rowHeightFor(row: Int): Float {
                    return if (zoom <= 1f) {
                        compactRowHeightFor(row)
                    } else {
                        compactEqualRowHeightPx +
                            (detailedRowHeightPx - compactEqualRowHeightPx) * detailProgress
                    }
                }
                fun rowTopFor(row: Int): Float {
                    var top = contentHeaderHeightPx
                    repeat(row) { previous -> top += rowHeightFor(previous) }
                    return top
                }
                // The compact selected row starts as the 80dp week lane. Its
                // natural month-grid center is introduced gradually so the
                // first frames of the morph do not nudge the strip downward.
                val stripGeometryProgress = smoothStep((zoom / .22f).coerceIn(0f, 1f))
                val naturalWeekTop = rowTopFor(compactWeekRow)
                val naturalWeekHeight = rowHeightFor(compactWeekRow)
                val compactWeekCenter = if (compactWeekRow == 0) {
                    // The first week is already at the top. Do not track the
                    // weighted row's expanding center and then spring back.
                    val monthCenter = headerHeightPx + compactEqualRowHeightPx / 2f
                    monthCenter + (compactStartHeightPx / 2f - monthCenter) * compactProgress
                } else compactStartHeightPx / 2f +
                    (naturalWeekTop + naturalWeekHeight / 2f - compactStartHeightPx / 2f) *
                    stripGeometryProgress
                val compactWeekInsetPx = with(density) { CompactWeekMetrics.HorizontalPadding.toPx() }
                val compactWeekCellWidthPx =
                    ((size.width - compactWeekInsetPx * 2f) / 7f).coerceAtLeast(0f)
                val compactWeekContentHeightPx = weekdayLayouts.maxOf { it.size.height } +
                    with(density) { 4.dp.toPx() } + compactDateSizePx + dateGapPx + eventAreaHeightPx
                var contentFade = 1f
                fun faded(color: Color, factor: Float = 1f): Color = color.copy(
                    alpha = color.alpha * drawAlpha * factor * contentFade,
                )

                // Reuse the month headings at the week endpoint, without a
                // second set fading into the selected row.
                val compactWeekContentTop = compactWeekCenter - compactWeekContentHeightPx / 2f
                fun drawWeekdayHeadings() {
                    WeekdayLetters.forEachIndexed { column, _ ->
                        val layout = weekdayLayouts[column]
                        drawText(
                            layout,
                            topLeft = Offset(
                                horizontalPaddingPx + cellWidthPx * column + (cellWidthPx - layout.size.width) / 2f,
                                (headerHeightPx - layout.size.height) / 2f * (1f - compactProgress) +
                                    (compactStartHeightPx - compactWeekContentHeightPx) / 2f * compactProgress,
                            ),
                            color = faded(
                                lerpColor(CalinoColors.Ink3, Color.White,
                                    (1f - abs(compactSelectorIndex - column)).coerceIn(0f, 1f) * compactProgress),
                            ),
                        )
                    }
                }
                if (zoom < 1f && compactProgress > .001f) {
                    val pillHeight = min(
                        with(density) { CompactWeekMetrics.PillHeight.toPx() },
                        naturalWeekHeight.coerceAtLeast(1f),
                    )
                    drawRoundRect(
                        color = faded(CalinoColors.Ink.copy(alpha = .95f), compactProgress),
                        topLeft = Offset(
                            compactWeekInsetPx + compactWeekCellWidthPx * compactSelectorIndex.coerceIn(0f, 6f),
                            compactWeekCenter - pillHeight / 2f,
                        ),
                        size = Size(compactWeekCellWidthPx, pillHeight),
                        cornerRadius = CornerRadius(with(density) { CompactWeekMetrics.PillRadius.toPx() }),
                    )
                }
                repeat(rows * 7) { index ->
                    val row = index / 7
                    val column = index % 7
                    val date = cellDates[index]
                    val cellTop = rowTopFor(row)
                    val cellRowHeight = rowHeightFor(row)
                    val compactWeekStyle = zoom < 1f && row == compactWeekRow
                    val cellLeft = horizontalPaddingPx + cellWidthPx * column
                    contentFade = if (zoom <= 1f && row != compactWeekRow) 1f - compactProgress else 1f
                    clipRect(
                        left = cellLeft,
                        top = cellTop,
                        right = cellLeft + cellWidthPx,
                        bottom = cellTop + cellRowHeight,
                    ) {
                    val isSelected = date == selected
                    val isToday = date == FixtureDate
                    val dateSizePx = compactDateSizePx +
                        (detailedDateSizePx - compactDateSizePx) * detailProgress
                    val compactWeekSelectionWeight = if (compactWeekStyle) {
                        (1f - abs(compactSelectorIndex.coerceIn(0f, 6f) - column.toFloat()))
                            .coerceIn(0f, 1f) * compactProgress
                    } else {
                        0f
                    }
                    val dateTop = if (compactWeekStyle) {
                        val monthDateTop = if (compactWeekRow == 0) {
                            headerHeightPx + dateTopPaddingPx
                        } else cellTop + dateTopPaddingPx
                        val weekContentTop = if (compactWeekRow == 0) {
                            (compactStartHeightPx - compactWeekContentHeightPx) / 2f
                        } else compactWeekContentTop
                        val weekDateTop = weekContentTop + weekdayLayouts[column].size.height + with(density) { 4.dp.toPx() }
                        monthDateTop + (weekDateTop - monthDateTop) * compactProgress
                    } else {
                        cellTop + dateTopPaddingPx
                    }
                    val compactFill = when {
                        isSelected -> CalinoColors.AccentSoft.copy(alpha = .72f * (1f - compactProgress))
                        isToday -> CalinoColors.AccentSoft.copy(alpha = .45f * (1f - compactProgress))
                        else -> Color.Transparent
                    }
                    if (compactFill != Color.Transparent) {
                        drawRoundRect(
                            color = faded(compactFill),
                            topLeft = Offset(cellLeft, cellTop),
                            size = Size(cellWidthPx, cellRowHeight),
                            cornerRadius = CornerRadius(with(density) { 4.dp.toPx() }),
                        )
                    }
                    val detailFill = when {
                        isSelected -> CalinoColors.AccentSoft.copy(alpha = .72f * detailProgress)
                        isToday -> CalinoColors.AccentSoft.copy(alpha = .45f * detailProgress)
                        else -> Color.Transparent
                    }
                    if (detailFill != Color.Transparent) {
                        drawRoundRect(
                            color = faded(detailFill),
                            topLeft = Offset(cellLeft, cellTop),
                            size = Size(cellWidthPx, cellRowHeight),
                            cornerRadius = CornerRadius(with(density) { 4.dp.toPx() }),
                        )
                    }
                    if (isSelected || isToday) {
                        if (!isSelected || compactProgress < .999f) {
                            drawCircle(
                                color = faded(
                                    if (isSelected) CalinoColors.Accent else CalinoColors.Accent.copy(alpha = .78f),
                                    if (isSelected && zoom <= 1f) 1f - compactProgress else 1f,
                                ),
                                radius = dateSizePx / 2f,
                                center = Offset(cellLeft + cellWidthPx / 2f, dateTop + dateSizePx / 2f),
                            )
                        }
                    }
                    val dateLayout = dateLayouts[index]
                    drawText(
                        dateLayout,
                        topLeft = Offset(
                            cellLeft + (cellWidthPx - dateLayout.size.width) / 2f,
                            dateTop + (dateSizePx - dateLayout.size.height) / 2f,
                        ),
                        color = faded(if (compactWeekStyle && compactWeekSelectionWeight > .001f) {
                            lerpColor(
                                if (inMonthFlags[index]) CalinoColors.Ink2 else CalinoColors.Ink3.copy(.5f),
                                Color.White,
                                compactWeekSelectionWeight,
                            )
                        } else if (isSelected) {
                            Color.White
                        } else if (inMonthFlags[index]) {
                            CalinoColors.Ink2
                        } else {
                            CalinoColors.Ink3.copy(.5f)
                        }),
                    )

                    val dayEvents = cellEvents[index]
                    val eventAreaTop = dateTop + dateSizePx + dateGapPx
                    val rawWidths = compactMarkerWidths[index]
                    val totalGap = eventMarkerGapPx * (rawWidths.size - 1).coerceAtLeast(0)
                    var rawTotal = totalGap
                    rawWidths.forEach { width -> rawTotal += width }
                    val markerScale = if (rawTotal > 0f) {
                        min(1f, (cellWidthPx - totalGap).coerceAtLeast(1f) / rawTotal)
                    } else {
                        1f
                    }
                    var markerLeft = cellLeft + (cellWidthPx - rawTotal * markerScale) / 2f
                    rawWidths.forEachIndexed { eventIndex, _ ->
                        val event = dayEvents[eventIndex]
                        val markerWidth = rawWidths[eventIndex] * markerScale
                        val markerHeight = with(density) { if (event.allDay) 3.dp.toPx() else 5.dp.toPx() }
                        val markerTop = eventAreaTop + (eventAreaHeightPx - markerHeight) / 2f
                        if (eventIndex < 2) {
                            val chipWidth = (cellWidthPx - chipHorizontalPaddingPx * 2f).coerceAtLeast(1f)
                            val chipTop = eventAreaTop + chipHeightPx * eventIndex
                            val x = markerLeft +
                                (cellLeft + chipHorizontalPaddingPx - markerLeft) * detailProgress
                            val y = markerTop + (chipTop - markerTop) * detailProgress
                            val width = markerWidth + (chipWidth - markerWidth) * detailProgress
                            val height = markerHeight + (chipHeightPx - markerHeight) * detailProgress
                            val eventColor = Color(event.color)
                            val chipColor = eventTint(eventColor, if (event.allDay) .18f else .10f)
                            drawRoundRect(
                                color = faded(lerpColor(eventColor, chipColor, detailProgress)),
                                topLeft = Offset(x, y),
                                size = Size(width, height),
                                cornerRadius = CornerRadius(with(density) { 2.dp.toPx() + 4.dp.toPx() * detailProgress }),
                            )
                            if (detailProgress > .01f) {
                                drawRoundRect(
                                    color = faded(eventColor, detailProgress),
                                    topLeft = Offset(x, y + 3.dp.toPx()),
                                    size = Size(
                                        2.5.dp.toPx(),
                                        (height - 6.dp.toPx()).coerceAtLeast(1f),
                                    ),
                                    cornerRadius = CornerRadius(1.dp.toPx()),
                                )
                                val textProgress = smoothStep(((detailProgress - .5f) / .5f).coerceIn(0f, 1f))
                                if (textProgress > .01f) eventLayouts[event.id]?.let { layout ->
                                    drawText(
                                        layout,
                                        topLeft = Offset(
                                            x + chipTextStartPx,
                                            y + (height - layout.size.height) / 2f,
                                        ),
                                        color = faded(CalinoColors.Ink, textProgress),
                                    )
                                }
                            }
                        } else {
                            drawRoundRect(
                                color = faded(Color(event.color), 1f - detailProgress),
                                topLeft = Offset(markerLeft, markerTop),
                                size = Size(markerWidth, markerHeight),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }
                        markerLeft += markerWidth + eventMarkerGapPx
                    }
                    val overflow = (dayEvents.size - 2).coerceAtLeast(0)
                    val overflowProgress = smoothStep(((detailProgress - .5f) / .5f).coerceIn(0f, 1f))
                    if (overflow > 0 && overflowProgress > .01f) {
                        overflowLayouts[index]?.let { layout ->
                            drawText(
                                layout,
                                topLeft = Offset(
                                    cellLeft + (cellWidthPx - layout.size.width) / 2f,
                                    eventAreaTop + 2 * chipHeightPx,
                                ),
                                color = faded(CalinoColors.Ink3, overflowProgress),
                            )
                        }
                    }
                }
            }
                contentFade = 1f
                drawWeekdayHeadings()
            }
            MonthGridHitTargets(
                selected = selected,
                month = month,
                events = monthEvents,
                journalDates = monthJournalDates,
                tasks = tasksByDueDate,
                targetHeight = detailedGridHeight,
                zoomState = zoomState,
                compactGridHeight = compactGridHeight,
                detailedGridHeight = detailedGridHeight,
                compactDay = compactDay,
                interactionEnabled = interactionEnabled,
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
    interactionEnabled: Boolean,
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
                    Text(
                        it,
                        Modifier.weight(1f),
                        fontSize = 10.sp,
                        color = CalinoColors.Ink3,
                        textAlign = TextAlign.Center,
                    )
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
                                .height(58.dp)
                                .align(Alignment.CenterStart)
                                .padding(horizontal = 3.dp)
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
                                    interactive = interactionEnabled && (isCompactWeek || compactProgress < .86f),
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
                                        interactive = interactionEnabled && compactProgress < .86f,
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
                                interactive = interactionEnabled && compactProgress < .86f,
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
            val regularDateColor = if (inMonth) CalinoColors.Ink2 else CalinoColors.Ink3.copy(.5f)
            val dateColor = if (today) {
                lerpColor(Color.White, CalinoColors.Ink2, compactProgress)
            } else {
                regularDateColor
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
                    horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dayEvents.take(3).forEach { event ->
                        Box(
                            Modifier
                                .width(if (event.allDay) 16.dp else 4.dp)
                                .height(if (event.allDay) 3.dp else 4.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(event.color)),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (date in journalDates) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(CalinoColors.Plum))
                    }
                }
            }
        }
    }
}

internal fun monthEventIndex(events: List<CalEvent>, month: YearMonth): Map<LocalDate, List<CalEvent>> {
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
    val compactFade = (1f - compactProgress).coerceIn(0f, 1f)
    val selectedWeight = max(compactFade.takeIf { selected } ?: 0f, compactSelectedWeight)
        .coerceIn(0f, 1f)
    val monthFill = when {
        selected -> CalinoColors.AccentSoft.copy(alpha = .72f * compactFade)
        today -> CalinoColors.AccentSoft.copy(alpha = .45f * compactFade)
        else -> Color.Transparent
    }
    val dateDescription = remember(date, selected, events, hasJournal) {
        buildString {
            append(date.format(FullDateFormatter))
            if (selected) append(", selected")
            if (events.isNotEmpty()) append(", events: ").append(events.joinToString { it.title })
            if (hasJournal) append(", journal entry")
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
            .background(monthFill)
            .then(interactionModifier)
            .padding(
                horizontal = lerpDp(2.dp, 5.dp, compactProgress),
                vertical = lerpDp(2.dp, 11.dp, compactProgress),
            ),
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
            inMonth -> CalinoColors.Ink2
            else -> CalinoColors.Ink3.copy(.5f)
        }
        val compactDateColor = if (compactSelectedWeight > .5f) Color.White else CalinoColors.Ink2
        Row(
            Modifier.fillMaxWidth().height(dateSize),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val dateFontScale = (
                (12f + 1.5f * detailProgress) * (1f - compactProgress) +
                    16f * compactProgress
                ) / 16f
            Box(
                Modifier.width(lerpDp(dateSize, 30.dp, compactProgress)).height(dateSize).clip(CircleShape)
                    .background(
                        when {
                            selected -> CalinoColors.Accent.copy(alpha = compactFade)
                            today -> CalinoColors.Accent.copy(alpha = .78f * compactFade)
                            else -> Color.Transparent
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    Modifier.graphicsLayer {
                        scaleX = dateFontScale
                        scaleY = dateFontScale
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = lerpColor(
                        lerpColor(monthDateColor, if (selected || today) Color.White else monthDateColor, selectedWeight),
                        compactDateColor,
                        compactProgress,
                    ),
                )
            }
        }
        EventDensityContent(events, detailProgress, Modifier.fillMaxWidth())
        // Journal dates are fixture/state data during this transition. Avoid
        // one AnimatedVisibility state machine in every cell; if journal data
        // changes later, the enclosing grid can animate that state change.
        Row(
            Modifier.fillMaxWidth().height(6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasJournal) {
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
    val visibleMarkerCount = (shownCount + compactExtraCount).coerceAtMost(4)
    val density = LocalDensity.current
    val compactMarkerWidthsPx = remember(events, density) {
        IntArray(visibleMarkerCount) { index ->
            with(density) { if (events[index].allDay) 18.dp.roundToPx() else 5.dp.roundToPx() }
        }
    }
    val compactMarkerTotalWidthPx = remember(events, density) { compactMarkerWidthsPx.sum() }
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
            val gap = 3.dp.toPx()
            val totalGap = gap * (visibleMarkerCount - 1).coerceAtLeast(0)
            val compactScale = if (compactMarkerTotalWidthPx > 0) {
                min(1f, ((size.width - totalGap).coerceAtLeast(1f) / compactMarkerTotalWidthPx))
            } else {
                1f
            }
            val totalWidth = compactMarkerTotalWidthPx * compactScale + totalGap
            val compactStart = ((size.width - totalWidth) / 2f).coerceAtLeast(0f)
            var precedingWidth = 0f
            repeat(shownCount) { index -> precedingWidth += compactMarkerWidthsPx[index] * compactScale }
            repeat(compactExtraCount) { extraIndex ->
                val event = events[extraIndex + 2]
                val index = extraIndex + 2
                val compactWidth = compactMarkerWidthsPx[index] * compactScale
                val compactX = compactStart + precedingWidth + gap * index
                val childWidth = lerpInt(compactWidth.roundToInt(), 5.dp.toPx().roundToInt(), progress).toFloat()
                val compactHeight = if (event.allDay) 3.dp.toPx() else 5.dp.toPx()
                val childHeight = lerpInt(compactHeight.roundToInt(), 5.dp.toPx().roundToInt(), progress).toFloat()
                val x = lerpInt(compactX.roundToInt(), 0, progress).toFloat()
                val y = lerpInt(
                    ((size.height - compactHeight) / 2f).roundToInt(),
                    0,
                    progress,
                ).toFloat()
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
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
                precedingWidth += compactWidth
            }
        },
    ) { measurables, constraints ->
        val progress = detailProgress.coerceIn(0f, 1f)
        val width = constraints.maxWidth
        val gap = 3.dp.roundToPx()
        val totalGap = gap * (visibleMarkerCount - 1).coerceAtLeast(0)
        val compactScale = if (compactMarkerTotalWidthPx > 0) {
            min(1f, ((width - totalGap).coerceAtLeast(1) / compactMarkerTotalWidthPx.toFloat()))
        } else {
            1f
        }
        val totalWidth = (compactMarkerTotalWidthPx * compactScale + totalGap).roundToInt()
        val compactStart = ((width - totalWidth) / 2).coerceAtLeast(0)
        val chipHeight = 20.dp.roundToPx()
        val overflowHeight = 14.dp.roundToPx()
        val coarseHeight = 7.dp.roundToPx()
        val detailHeight = shownCount * chipHeight + if (overflow > 0) overflowHeight else 0
        val height = lerpInt(coarseHeight, detailHeight.coerceAtLeast(coarseHeight), progress)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val places = measurables.mapIndexed { index, measurable ->
            val isOverflow = overflow > 0 && index == shownCount
            if (isOverflow) {
                measurable.measure(Constraints.fixed(maxOf(1, width), maxOf(1, overflowHeight)))
            } else {
                val event = events[index]
                val compactWidth = (compactMarkerWidthsPx[index] * compactScale).roundToInt().coerceAtLeast(1)
                val targetWidth = width
                val childHeight = if (index < 2) {
                    lerpInt(if (event.allDay) 3.dp.roundToPx() else 5.dp.roundToPx(), chipHeight, progress)
                } else {
                    if (event.allDay) 3.dp.roundToPx() else 5.dp.roundToPx()
                }
                val childWidth = lerpInt(compactWidth, if (index < 2) targetWidth else 5.dp.roundToPx(), progress)
                    .coerceIn(1, maxOf(1, width))
                measurable.measure(Constraints.fixed(childWidth, maxOf(1, childHeight)))
            }
        }
        layout(width, height) {
            var precedingWidth = 0
            places.forEachIndexed { index, placeable ->
                if (overflow > 0 && index == shownCount) {
                    val y = lerpInt(coarseHeight + 2.dp.roundToPx(), shownCount * chipHeight, progress)
                    placeable.placeRelative(0, y.coerceIn(0, max(0, height - placeable.height)))
                } else {
                    val event = events[index]
                    val compactX = compactStart + precedingWidth + gap * index
                    val compactHeight = if (event.allDay) 3.dp.roundToPx() else 5.dp.roundToPx()
                    val coarseX = compactX
                    val coarseY = ((coarseHeight - compactHeight) / 2).coerceAtLeast(0)
                    val targetY = if (index < 2) index * chipHeight else 0
                    val x = lerpInt(coarseX, 0, progress)
                    val y = lerpInt(coarseY, targetY, progress)
                    placeable.placeRelative(
                        x.coerceIn(0, max(0, width - placeable.width)),
                        y.coerceIn(0, max(0, height - placeable.height)),
                    )
                    precedingWidth += (compactMarkerWidthsPx[index] * compactScale).roundToInt().coerceAtLeast(1)
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
    val metadata = buildString {
        if (event.allDay) {
            append("All day")
        } else {
            event.start?.let { append(it.format(TimeFormatter)) }
            event.durationMinutes?.let { duration ->
                if (isNotEmpty()) append(" · ")
                append(duration).append(" min")
            }
        }
        event.location?.let { location ->
            if (isNotEmpty()) append(" · ")
            append(location)
        }
    }
    val shape = RoundedCornerShape(if (agendaStyle) 10.dp else 6.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = maxOf(44.dp, minHeight)).clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = eventDescription(event) }
            .background(eventTint(Color(event.color), if (agendaStyle) .12f else .10f, CalinoColors.Panel))
            .border(1.dp, Color(event.color).copy(alpha = if (agendaStyle) .16f else .12f), shape)
            .padding(horizontal = if (agendaStyle) 10.dp else 4.dp, vertical = if (agendaStyle) 7.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(if (agendaStyle) 4.dp else 2.dp)
                .height(if (agendaStyle) 30.dp else 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(event.color)),
        )
        if (agendaStyle) {
            Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    event.title,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = CalinoColors.Ink,
                )
                Text(metadata, fontSize = 11.sp, lineHeight = 14.sp, color = CalinoColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(
                event.title,
                Modifier.padding(start = 7.dp),
                fontSize = 9.sp,
                lineHeight = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = CalinoColors.Ink,
            )
        }
    }
}

@Composable
private fun DayPagerSurface(
    state: PagerState,
    events: List<CalEvent>,
    tasksByDueDate: Map<LocalDate, List<CalTask>>,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier,
    interactionEnabled: Boolean,
    zoomState: androidx.compose.runtime.State<Float>,
    dayRailOwnsInput: Boolean,
    agendaOwnsInput: Boolean,
    onEvent: ((CalEvent) -> Unit)?,
    onTaskDone: (CalTask, Boolean) -> Unit,
    onTaskRescheduleTo: (CalTask, LocalDate?) -> Unit,
    onTaskClick: ((CalTask) -> Unit)?,
    onOpenDay: ((LocalDate) -> Unit)?,
) {
    val dayRailVisibility = Modifier.drawWithContent {
        if (zoomState.value < DaySurfaceBlendEnd) drawContent()
    }
    HorizontalPager(
        state = state,
        modifier = modifier.clipToBounds(),
            // The pager itself keeps the adjacent page that is entering the
            // viewport. Extra eagerly composed month grids are expensive while
            // zoom continuously changes every cell's measured height.
            beyondViewportPageCount = 0,
        userScrollEnabled = interactionEnabled,
        key = { page -> dateForDayPage(page).toEpochDay() },
    ) { page ->
        val pageDay = dateForDayPage(page)
        val dayEvents = remember(events, pageDay) { eventsFor(events, pageDay) }
        val dayTasks = tasksByDueDate[pageDay].orEmpty()
        Box(Modifier.fillMaxSize()) {
            // Keep the rail mounted until the agenda has covered it. The
            // incoming surface uses an opaque clipped reveal instead of a
            // full-screen alpha crossfade: both endpoint trees remain stable,
            // but their text and event cards never render as a ghosted double.
            Box(Modifier.fillMaxSize().then(dayRailVisibility)) {
                DayRailPage(
                    day = pageDay,
                    dayEvents = dayEvents,
                    scrollState = scrollState,
                    dayTasks = dayTasks,
                    active = dayRailOwnsInput,
                    scrollEnabled = dayRailOwnsInput,
                    onEvent = if (dayRailOwnsInput) onEvent else null,
                    onTaskDone = if (dayRailOwnsInput) onTaskDone else null,
                    onTaskRescheduleTo = if (dayRailOwnsInput) onTaskRescheduleTo else null,
                    onTaskClick = if (dayRailOwnsInput) onTaskClick else null,
                )
            }
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    val zoom = zoomState.value
                    if (zoom > DaySurfaceBlendStart && zoom < 1.99f) {
                        val reveal = if (zoom < DaySurfaceBlendEnd) {
                            daySurfaceBlend(zoom)
                        } else {
                            1f
                        }
                        clipRect(bottom = size.height * reveal) {
                            // Cover the rail before drawing the incoming
                            // agenda so the transition has one readable
                            // surface at every frame.
                            drawRect(CalinoColors.Canvas)
                            this@drawWithContent.drawContent()
                        }
                    }
                },
            ) {
                SelectedDayAgendaPage(
                    day = pageDay,
                    dayEvents = dayEvents,
                    dayTasks = dayTasks,
                    active = agendaOwnsInput,
                    onEvent = if (agendaOwnsInput) onEvent else null,
                    onTaskDone = if (agendaOwnsInput) onTaskDone else null,
                    onTaskRescheduleTo = if (agendaOwnsInput) onTaskRescheduleTo else null,
                    onTaskClick = if (agendaOwnsInput) onTaskClick else null,
                    onOpenDay = if (agendaOwnsInput) onOpenDay else null,
                )
            }
        }
    }
}

@Composable
private fun SelectedDayAgendaPage(
    day: LocalDate,
    dayEvents: List<CalEvent>,
    dayTasks: List<CalTask>,
    modifier: Modifier = Modifier.fillMaxSize(),
    active: Boolean,
    onEvent: ((CalEvent) -> Unit)?,
    onTaskDone: ((CalTask, Boolean) -> Unit)?,
    onTaskRescheduleTo: ((CalTask, LocalDate?) -> Unit)?,
    onTaskClick: ((CalTask) -> Unit)?,
    onOpenDay: ((LocalDate) -> Unit)?,
) {
    var tasksExpanded by remember(day) { mutableStateOf(true) }
    val interactionModifier = if (active) {
        modifier.semantics {
            contentDescription = "Agenda for ${day.format(FullDateFormatter)}"
        }
    } else {
        modifier.clearAndSetSemantics { }
    }
    Column(
        interactionModifier
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                day.format(AgendaDateFormatter),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = CalinoColors.Ink2,
                modifier = Modifier.weight(1f),
            )
            Text(
                "OPEN DAY",
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = CalinoColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .heightIn(min = 36.dp)
                    .clickable(enabled = onOpenDay != null) { onOpenDay?.invoke(day) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (dayTasks.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        enabled = active,
                        onClickLabel = if (tasksExpanded) {
                            "Collapse tasks due for ${day.format(FullDateFormatter)}"
                        } else {
                            "Expand tasks due for ${day.format(FullDateFormatter)}"
                        },
                    ) { tasksExpanded = !tasksExpanded }
                    .padding(top = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "TASKS DUE · ${dayTasks.count { !it.done }} OPEN",
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = CalinoColors.Green,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = CalinoIcons.ChevronRight,
                    contentDescription = null,
                    tint = CalinoColors.Green,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (tasksExpanded) -90f else 90f),
                )
            }
            AnimatedVisibility(
                visible = tasksExpanded,
                enter = expandVertically(tween(180)) + fadeIn(tween(140)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    dayTasks.forEach { task ->
                        CalendarTaskRow(
                            task = task,
                            onTaskDone = onTaskDone?.let { callback -> { done -> callback(task, done) } },
                            onTaskRescheduleTo = onTaskRescheduleTo,
                            onTaskClick = onTaskClick?.let { callback -> { callback(task) } },
                        )
                    }
                }
            }
        }
        if (dayEvents.isEmpty()) {
            Text("Nothing scheduled", fontSize = 13.sp, color = CalinoColors.Ink3, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            Column(
                Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                dayEvents.forEach { event ->
                    EventChip(event, minHeight = 44.dp, onClick = onEvent?.let { callback -> { callback(event) } }, agendaStyle = true)
                }
            }
        }
    }
}

@Composable
private fun DayRailPage(
    day: LocalDate,
    dayEvents: List<CalEvent>,
    dayTasks: List<CalTask>,
    scrollState: androidx.compose.foundation.ScrollState,
    active: Boolean,
    scrollEnabled: Boolean,
    onEvent: ((CalEvent) -> Unit)?,
    onTaskDone: ((CalTask, Boolean) -> Unit)?,
    onTaskRescheduleTo: ((CalTask, LocalDate?) -> Unit)?,
    onTaskClick: ((CalTask) -> Unit)?,
) {
    val interactionModifier = if (active) Modifier else Modifier.clearAndSetSemantics { }
    Column(interactionModifier.fillMaxSize()) {
        // This strip deliberately sits outside the scrolling rail so changing
        // hours never makes the all-day context disappear.
        Column(
            Modifier.fillMaxWidth().padding(start = 52.dp, end = 20.dp, top = 5.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (dayTasks.isNotEmpty()) {
                Text("TASKS DUE", fontSize = 10.sp, letterSpacing = 1.sp, color = CalinoColors.Green)
                dayTasks.forEach { task ->
                    CalendarTaskRow(
                        task = task,
                        onTaskDone = onTaskDone?.let { callback -> { done -> callback(task, done) } },
                        onTaskRescheduleTo = onTaskRescheduleTo,
                        onTaskClick = onTaskClick?.let { callback -> { callback(task) } },
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
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
                // The add pill floats over this rail; keep the last hours
                // scrollable clear of it.
                Spacer(Modifier.height(CalinoSpacing.PillClearance))
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
                val height = maxOf(44, ((event.durationMinutes ?: 60) / 60f * 62 - 6).toInt()).dp
                Box(
                    Modifier.offset(y = top)
                        .fillMaxWidth()
                        .padding(start = 52.dp, end = 20.dp)
                        .height(height)
                        .clip(RoundedCornerShape(11.dp))
                        .then(if (onEvent != null) Modifier.clickable { onEvent(event) } else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = eventDescription(event) }
                        .background(eventTint(Color(event.color), .13f, CalinoColors.Panel))
                        .border(1.dp, Color(event.color).copy(alpha = .16f), RoundedCornerShape(11.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.width(4.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(event.color)),
                        )
                        Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                event.title,
                                fontSize = 13.5.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = CalinoColors.Ink,
                            )
                            val metadata = buildString {
                                append(start.format(TimeFormatter))
                                event.durationMinutes?.let { append(" · ").append(it).append(" min") }
                                event.location?.let { append(" · ").append(it) }
                            }
                            Text(metadata, fontSize = 11.sp, lineHeight = 14.sp, color = CalinoColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
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
private fun ZoomHandle(
    zoomLevel: Int,
    zoomBand: Int,
    gestureModifier: Modifier,
    onTap: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).then(gestureModifier)
            .clickable(onClick = onTap)
            .semantics(mergeDescendants = true) { contentDescription = "Change calendar zoom, level ${zoomLevel + 1} of 3" }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(26.dp).height(3.dp).background(CalinoColors.Ink.copy(.25f)))
        Text(
            when (zoomBand) {
                0 -> "PULL FOR MONTH"
                1 -> "PULL AGAIN FOR DETAIL"
                else -> "RELEASE TO COLLAPSE"
            },
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = CalinoColors.Ink3,
        )
        repeat(3) { index ->
            Box(
                Modifier.width(if (index == zoomLevel) 14.dp else 6.dp)
                    .height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (index == zoomLevel) CalinoColors.Accent else CalinoColors.Ink3.copy(.3f)),
            )
        }
        Box(Modifier.width(26.dp).height(3.dp).background(CalinoColors.Ink.copy(.25f)))
    }
}

