package calino.malinov.ski.poc.ui.home

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.occursOn
import calino.malinov.ski.poc.data.repository.CalinoRepository
import calino.malinov.ski.poc.data.repository.FixtureRepository
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoShapes
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.design.eventTint
import calino.malinov.ski.poc.qa.zoomAfterVerticalDrag
import calino.malinov.ski.poc.qa.zoomSettleLevel
import calino.malinov.ski.poc.state.FixtureNow
import calino.malinov.ski.poc.state.LocalCalinoNow
import calino.malinov.ski.poc.state.LocalCalinoPreferences
import calino.malinov.ski.poc.state.LocalTimeFormat
import calino.malinov.ski.poc.state.BookPostureSplitMinWidthDp
import calino.malinov.ski.poc.state.LocalFoldPosture
import calino.malinov.ski.poc.state.LocalHingeOpenness
import calino.malinov.ski.poc.state.foldSplitProgress
import calino.malinov.ski.poc.state.SplitPaneWidthDp
import calino.malinov.ski.poc.state.calinoLayoutSpec
import calino.malinov.ski.poc.state.openTasksDueOn
import calino.malinov.ski.poc.state.tasksDueOn
import calino.malinov.ski.poc.ui.components.CalinoIcons
import calino.malinov.ski.poc.ui.components.CalinoMonthHeading
import calino.malinov.ski.poc.ui.components.MenuButton
import calino.malinov.ski.poc.ui.components.TaskRow
import calino.malinov.ski.poc.ui.components.calinoPressable
import calino.malinov.ski.poc.ui.surfaces.DayPane
import calino.malinov.ski.poc.util.CalinoTimeFormat
import calino.malinov.ski.poc.util.CalinoEventDensity
import calino.malinov.ski.poc.util.CalinoWeekStart
import calino.malinov.ski.poc.util.DayRailSlot
import calino.malinov.ski.poc.util.formatCalinoDuration
import calino.malinov.ski.poc.util.WashKind
import calino.malinov.ski.poc.util.gridStart
import calino.malinov.ski.poc.util.monthWashPlan
import calino.malinov.ski.poc.util.layoutDayRail
import calino.malinov.ski.poc.util.leadingCells
import calino.malinov.ski.poc.util.startOfWeek
import calino.malinov.ski.poc.util.weekdayColumn
import calino.malinov.ski.poc.util.weekdayLetters
import calino.malinov.ski.poc.util.weekendColumns
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The fixed origin for pager page arithmetic.
 *
 * Deliberately *not* "today". Page indices are offsets from this date, so it
 * has to be a constant: were it to advance at midnight, every mounted pager
 * would silently renumber its pages under the user. Today lives in
 * [LocalCalinoNow].
 */
private val PagerEpoch = LocalDate.of(2026, 5, 18)
private const val DaytimeScrollHour = 9
private const val ZoomStepDp = 280f

/**
 * Smallest relative width change that counts as a fold rather than as insets
 * moving. Four percent is well under a hinge and well over a status bar.
 */
private const val MinFoldMorphRatio = .04f
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

private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
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

/**
 * The space the zoom handle takes between the calendar and the day surface.
 * Its touch lane stays at [ZoomHandleTouchHeight] and overflows this band, so
 * halving the painted bar did not halve what a finger has to hit.
 */
private val ZoomHandleHeight = 22.dp

private val ZoomHandleTouchHeight = 44.dp

/**
 * How far the day rail reaches up behind the compact strip and the zoom
 * handle: the whole of both, so the glass starts at the very top of the strip
 * rather than partway down the dates. It changes only with the pull-bar
 * setting, never during a zoom drag, so the overlap stays out of that measure
 * pass.
 */
private fun compactLaneOverlap(handleHeight: Dp) = CompactWeekMetrics.Height + handleHeight

/** The height over which the lane's scrim dissolves into the rail below it. */
private val CompactLaneSoftEdge = 10.dp

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
    (DayPagerCenter.toLong() + date.toEpochDay() - PagerEpoch.toEpochDay())
        .coerceIn(0L, (DayPagerPageCount - 1).toLong())
        .toInt()

private fun dateForDayPage(page: Int): LocalDate =
    PagerEpoch.plusDays((page - DayPagerCenter).toLong())

internal fun weekPageFor(date: LocalDate, weekStart: CalinoWeekStart): Int {
    val epochWeek = PagerEpoch.startOfWeek(weekStart)
    val week = date.startOfWeek(weekStart)
    return (WeekPagerCenter + ((week.toEpochDay() - epochWeek.toEpochDay()) / 7L))
        .coerceIn(0L, (WeekPagerPageCount - 1).toLong())
        .toInt()
}

internal fun weekStartForPage(page: Int, weekStart: CalinoWeekStart): LocalDate =
    PagerEpoch.startOfWeek(weekStart).plusWeeks((page - WeekPagerCenter).toLong())

internal fun monthPageFor(month: YearMonth): Int {
    val fixtureMonth = YearMonth.from(PagerEpoch)
    return (MonthPagerCenter + (month.year - fixtureMonth.year) * 12 + month.monthValue - fixtureMonth.monthValue)
        .coerceIn(0, MonthPagerPageCount - 1)
}

internal fun monthForPage(page: Int): YearMonth =
    YearMonth.from(PagerEpoch).plusMonths((page - MonthPagerCenter).toLong())

/**
 * The native, single-surface calendar. The host only needs to call
 * [HomeScreen]. The optional [onOpenDay] callback is an explicit open affordance
 * for the split view; all existing callers remain source-compatible.
 */
@Composable
fun HomeScreen(
    // Required, with no fixture default. A default here silently constructed a
    // second, unconnected repository at any call site that omitted it, which
    // now means quietly showing sample data instead of the user's calendar.
    repository: CalinoRepository,
    journals: List<JournalEntry> = emptyList(),
    tasks: List<CalTask> = repository.tasks(),
    modifier: Modifier = Modifier,
    initialDate: LocalDate = FixtureNow.today,
    onOpenMenu: (() -> Unit)? = null,
    onDateChanged: (LocalDate) -> Unit = {},
    onDayClick: ((LocalDate) -> Unit)? = null,
    onEventClick: ((CalEvent) -> Unit)? = null,
    onTaskDone: (CalTask, Boolean) -> Unit = { _, _ -> },
    onTaskRescheduleTo: (CalTask, LocalDate?) -> Unit = { _, _ -> },
    onTaskClick: ((CalTask) -> Unit)? = null,
    onOpenDay: ((LocalDate) -> Unit)? = null,
    interactionEnabled: Boolean = true,
    /** Reports whether the large split month layout is active. */
    onSplitPaneChanged: (Boolean) -> Unit = {},
) {
    // Hoisted: the compact lane's draw scope cannot read the composition local.
    val canvas = CalinoColors.Canvas
    var selectedEpoch by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    // The default view seeds the zoom once, on the first composition of a
    // session. Reading it continuously would pin the calendar to that level and
    // leave the user unable to zoom away from their own default.
    val initialZoom = LocalCalinoPreferences.current.defaultView.zoomLevel
    val zoomState = rememberSaveable { mutableFloatStateOf(initialZoom) }
    var settledZoom by rememberSaveable { mutableFloatStateOf(initialZoom) }
    LaunchedEffect(initialDate) { selectedEpoch = initialDate.toEpochDay() }

    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val today = LocalCalinoNow.current.today
    val events = repository.events()
    val hideCompletedTasks = LocalCalinoPreferences.current.hideCompletedTasks
    val tasksByDueDate = remember(tasks, hideCompletedTasks) {
        tasks.filter { it.due != null && !(hideCompletedTasks && it.done) }
            .groupBy { it.due!! }
            .mapValues { (date, dueTasks) -> tasksDueOn(dueTasks, date) }
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val preferences = LocalCalinoPreferences.current
    val weekStart = preferences.weekStart
    val showWeekNumber = preferences.showWeekNumbers
    val railScroll = rememberScrollState(initial = with(density) { (DaytimeScrollHour * 62).dp.roundToPx() })
    // A week page is an offset from an epoch week, and that offset does not
    // survive a change of week start: for a Sunday it shifts by one, for every
    // other day it does not. A pager left on the old index reads back a date a
    // week off and the settled-page collector commits it, so the pagers are
    // rebuilt when the setting changes -- seeded from the live selected date
    // rather than `initialDate`, which would throw the calendar back to
    // wherever the session began.
    val pagerAnchor = LocalDate.ofEpochDay(selectedEpoch)
    val dayPagerState = key(weekStart) {
        rememberPagerState(initialPage = dayPageFor(pagerAnchor)) { DayPagerPageCount }
    }
    val weekPagerState = key(weekStart) {
        rememberPagerState(initialPage = weekPageFor(pagerAnchor, weekStart)) { WeekPagerPageCount }
    }
    val monthPagerState = key(weekStart) {
        rememberPagerState(initialPage = monthPageFor(YearMonth.from(pagerAnchor))) { MonthPagerPageCount }
    }
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
    val selectedWeekdayIndex = selected.weekdayColumn(weekStart)
    // A day swipe across a week boundary pages the strip to the neighboring
    // week while the drag is still live. Aim the pill at the previewed day
    // from the moment that starts, so it travels with the incoming week
    // instead of resting on the old column and jumping once the date commits.
    val selectorWeekdayIndex = (compactBoundaryDay ?: selected).weekdayColumn(weekStart)
    val compactSelectorPosition = remember {
        Animatable(selectedWeekdayIndex.toFloat())
    }

    // Changing the week start moves the selected day to a different column.
    // Springing it across the strip would read as a week change that is not
    // happening, and any preview token still in flight describes pages that no
    // longer mean the same thing.
    LaunchedEffect(weekStart) {
        suppressedWeekPreview = null
        compactBoundaryDay = null
        blockedBoundaryDay = null
        weekPreviewGeneration += 1
        compactSelectorPosition.snapTo(selected.weekdayColumn(weekStart).toFloat())
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
                val targetWeekStart = weekStartForPage(it, weekStart)
                val currentDate = LocalDate.ofEpochDay(currentSelectedEpoch.value)
                val targetDate = targetWeekStart.plusDays(currentDate.weekdayColumn(weekStart).toLong())
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
            val targetWeekPage = weekPageFor(selected, weekStart)
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
            if (previewDate.startOfWeek(weekStart) != selected.startOfWeek(weekStart)) {
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
            val targetIsBoundary = target.startOfWeek(weekStart) != committed.startOfWeek(weekStart)

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
            val committedWeekPage = weekPageFor(selected, weekStart)
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

        val targetWeekPage = weekPageFor(boundary, weekStart)
        weekRollbackJob?.cancel()
        weekRollbackJob = null
        if (!weekPagerState.isScrollInProgress && weekPagerState.currentPage != targetWeekPage) {
            val committedWeekPage = weekPageFor(selected, weekStart)
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
    // The zoom the compact layout was left at, so folding back does not dump
    // the calendar at the split layout's pinned endpoint.
    var zoomBeforeSplit by rememberSaveable { mutableFloatStateOf(initialZoom) }
    BoxWithConstraints(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
    val layoutSpec = calinoLayoutSpec(
        widthDp = maxWidth.value.toInt(),
        heightDp = maxHeight.value.toInt(),
        posture = LocalFoldPosture.current,
    )
    val hingeOpenness = LocalHingeOpenness.current
    /**
     * How far the fold has divided the layout, 0 flat and 1 properly bent.
     *
     * Quantized before it reaches the layout: the sensor delivers a fine
     * stream and the month grid is expensive to remeasure, so the panes move in
     * one-percent steps. That is well under what an eye can see moving and well
     * over what a pager wants to be remeasured at.
     */
    val splitProgress by remember(hingeOpenness) {
        derivedStateOf {
            val openness = hingeOpenness?.value ?: return@derivedStateOf 0f
            (foldSplitProgress(openness) * 100f).roundToInt() / 100f
        }
    }
    // Bending the device divides the layout even where width alone would not:
    // the crease is doing the dividing, so the rule only has to be wide enough
    // for two readable columns.
    val foldSplitting = splitProgress > 0f && maxWidth.value >= BookPostureSplitMinWidthDp
    val splitLayout = layoutSpec.splitPanes || foldSplitting

    /**
     * How far the arrangement has settled after a fold, 0 at the change and 1
     * once it is done.
     *
     * Android hands the app a new window size; the swap between the physical
     * panels belongs to the system and cannot be animated from here. What can
     * be animated is everything after: rather than appearing already at its new
     * size, the calendar starts at the geometry it had and settles into the one
     * it now has, under a brief blur that covers the frame in which it
     * re-lays-out.
     *
     * Keyed on the width the calendar actually gets, not on the split
     * decision. A book-style foldable unfolds into a portrait window that is
     * wider but still one pane -- keying on the arrangement meant the most
     * common unfold on the device this was built for changed nothing at all.
     */
    val morph = remember { Animatable(1f) }
    // Scale the incoming layout starts at. Captured when the change fires,
    // since it depends on the width the calendar is coming *from*.
    var morphStartScale by remember { mutableFloatStateOf(1f) }
    var lastSplit by remember { mutableStateOf<Boolean?>(null) }
    var lastContentWidth by remember { mutableFloatStateOf(0f) }
    // Width the grid itself gets: the window, less the pane when there is one.
    val contentWidth = (maxWidth.value - if (splitLayout) SplitPaneWidthDp.toFloat() else 0f)
        .coerceAtLeast(1f)
    LaunchedEffect(splitLayout, contentWidth) {
        val previousSplit = lastSplit
        val previousWidth = lastContentWidth
        lastSplit = splitLayout
        lastContentWidth = contentWidth
        if (previousSplit == null) {
            // First measure. There is no previous geometry to travel from.
            morph.snapTo(1f)
            return@LaunchedEffect
        }
        val ratio = previousWidth / contentWidth
        // A few dp of inset shuffling is not a fold. Only a real change moves.
        if (previousSplit == splitLayout && abs(1f - ratio) < MinFoldMorphRatio) {
            morph.snapTo(1f)
            return@LaunchedEffect
        }
        if (previousSplit != splitLayout) {
            if (splitLayout) {
                // The split grid is pinned to the detailed endpoint. Entering,
                // the compact surface is already gone, so there is nothing to
                // animate; remember where it was instead.
                zoomBeforeSplit = settledZoom
                cancelMotion()
                zoomState.floatValue = 2f
                settledZoom = 2f
            } else {
                animateZoomTo(zoomBeforeSplit)
            }
        }
        if (foldSplitting) {
            // The hinge is already driving this one. Running a timed animation
            // over the top would be the app moving on its own while the user
            // is still moving the device.
            morph.snapTo(1f)
            return@LaunchedEffect
        }
        // Clamped hard: this should read as the grid settling into its new
        // size, not as a zoom.
        morphStartScale = ratio.coerceIn(.88f, 1.14f)
        morph.snapTo(0f)
        morph.animateTo(1f, tween(CalinoMotion.FoldMorphMillis, easing = FastOutSlowInEasing))
    }
    val morphFraction = morph.value
    val foldMorph = if (morphFraction >= 1f) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            // Read here rather than in composition: the layer repaints without
            // recomposing the calendar.
            val scale = morphStartScale + (1f - morphStartScale) * morph.value
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin.Center
        }
    }
    // This callback describes the layout, rather than the pane's visibility:
    // the shell uses it to keep the add pill in the same right-side lane while
    // the day pane is collapsed and expanded.
    LaunchedEffect(splitLayout) { onSplitPaneChanged(splitLayout) }
    DisposableEffect(Unit) { onDispose { onSplitPaneChanged(false) } }
    if (splitLayout) {
        SplitHomeLayout(
            modifier = foldMorph,
            morphFraction = morphFraction,
            splitProgress = splitProgress,
            windowWidth = maxWidth,
            hingeStartDp = layoutSpec.hingeStartDp,
            hingeBandDp = layoutSpec.hingeBandDp,
            selected = selected,
            weekStart = weekStart,
            showWeekNumber = showWeekNumber,
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
                selectedEpoch = today.toEpochDay()
                onDateChanged(today)
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
    Column(foldMorph.fillMaxSize()) {
        MonthHeading(
            day = selected,
            showWeekNumber = showWeekNumber,
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
                selectedEpoch = today.toEpochDay()
                onDateChanged(today)
            },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val showZoomHandle = LocalCalinoPreferences.current.showZoomHandle
            val handleHeight = if (showZoomHandle) ZoomHandleHeight else 0.dp

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

            /**
             * The zoom drag, hosted on the container rather than on the
             * layers it moves.
             *
             * The week strip leaves the composition at [MonthEndpointBlendEnd],
             * so a drag that started on it died a fifth of the way through and
             * the rest of the travel was lost -- the zoom stopped following
             * the finger and snapped, which is what made the gesture feel like
             * an either/or rather than the continuous morph it is. The
             * container outlives every layer, so one drag stays gradual from
             * the first pixel to release.
             *
             * It watches the initial pointer pass and claims the gesture only
             * once it is decisively vertical, leaving taps and the pagers'
             * horizontal swipes to the children. The host container covers the
             * day surface too, so the calendar can be collapsed or expanded
             * from the space below the visible month/week surface.
             */
            val calendarZoomGesture = Modifier.pointerInput(handleHeight) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var travel = Offset.Zero
                    var owned = false
                    var anchorLevel = 0
                    var velocityTracker = VelocityTracker()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (owned) {
                                val velocity = velocityTracker.calculateVelocity()
                                animateZoomTo(
                                    zoomSettleLevel(
                                        zoom = zoomState.floatValue,
                                        anchorLevel = anchorLevel,
                                        // Positive y velocity means a downward pull.
                                        zoomVelocityDpPerSecond = velocity.y.toDp().value,
                                    ).toFloat(),
                                )
                            }
                            break
                        }
                        // Ignore-consumed: once this gesture consumes a change,
                        // `positionChange` reports zero for the rest of the
                        // drag. Reading that, the zoom stopped following the
                        // finger after the first frame and only the settling
                        // fling moved it -- which is what made a continuous
                        // morph behave like a switch.
                        travel += change.positionChangeIgnoreConsumed()
                        var claimed = 0f
                        if (!owned) {
                            if (abs(travel.y) > viewConfiguration.touchSlop * .5f &&
                                abs(travel.y) > abs(travel.x)
                            ) {
                                owned = true
                                cancelMotion()
                                anchorLevel = zoomState.floatValue.roundToInt().coerceIn(0, 2)
                                velocityTracker = VelocityTracker()
                                // The travel spent deciding belongs to the same
                                // drag; dropping it made the calendar jump in
                                // rather than start under the finger.
                                claimed = travel.y
                            } else if (abs(travel.x) > viewConfiguration.touchSlop) {
                                break
                            }
                        }
                        if (owned) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            val delta = if (claimed != 0f) claimed else change.positionChangeIgnoreConsumed().y
                            zoomState.floatValue = zoomAfterVerticalDrag(
                                zoomState.floatValue,
                                delta.toDp().value,
                                ZoomStepDp,
                            )
                        }
                    }
                }
            }
            val laneOverlap = compactLaneOverlap(handleHeight)
            Box(
                Modifier.fillMaxSize()
                    .clipToBounds()
                    .then(if (interactionEnabled) calendarZoomGesture else Modifier),
            ) {
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
                        weekStart = weekStart,
                        events = events,
                        tasksByDueDate = tasksByDueDate,
                        pagerOffset = dayPagerTravel,
                        selectorIndex = compactSelectorIndex,
                        gestureModifier = Modifier,
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
                                    weekPageFor(weekStripDay, weekStart) != weekPagerState.settledPage
                                if (previewVisible) {
                                    drawRect(canvas)
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
                        // The grid stays measured at its full height so the
                        // zoom drag never remeasures it, but the lane it
                        // *occupies* has to end where it is visible: the rail
                        // now sits under this layer, and a full-height touch
                        // box would hand every scroll on the day list to the
                        // calendar's zoom gesture instead.
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val visible = calendarHeight.value.roundToPx().coerceIn(0, placeable.height)
                            layout(placeable.width, visible) { placeable.place(0, 0) }
                        }
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
                        weekStart = weekStart,
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
                        gestureModifier = Modifier,
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
                // travels with the month-to-week morph as one gesture
                // affordance. Turned off, it gives its band back to the day
                // surface and the vertical drag on the grid remains the way to
                // change zoom.
                if (showZoomHandle) {
                    Box(
                        Modifier.fillMaxWidth()
                            .requiredHeight(handleHeight)
                            .offset {
                                IntOffset(0, with(density) { calendarHeight.value.roundToPx() })
                            },
                        // The bar occupies a half-height band but keeps a full
                        // touch lane, which overflows this box evenly.
                        contentAlignment = Alignment.Center,
                    ) {
                        ZoomHandle(
                            zoomLevel = zoomLevel,
                            zoomBand = zoomBand,
                            gestureModifier = Modifier,
                            onTap = {
                                val level = zoomState.floatValue.roundToInt().coerceIn(0, 2)
                                animateZoomTo(if (level < 2) level + 1f else 1f)
                            },
                        )
                    }
                }

                // The rail runs up behind the handle and the compact strip
                // instead of starting below them, so hours slide under a
                // translucent lane rather than stopping at a hard edge. The
                // overlap only changes with the pull-bar setting, so this
                // never remeasures during a zoom drag; the lane's own scrim is
                // what hides the rail again once the month grid owns that
                // space.
                Box(
                    Modifier.fillMaxWidth()
                        .zIndex(-1f)
                        .requiredHeight(daySurfaceHeight + laneOverlap)
                        .offset {
                            IntOffset(
                                0,
                                with(density) {
                                    (calendarHeight.value + handleHeight - laneOverlap).roundToPx()
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
                        laneOverlap = laneOverlap,
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
    modifier: Modifier = Modifier,
    morphFraction: Float,
    splitProgress: Float,
    windowWidth: Dp,
    hingeStartDp: Float?,
    hingeBandDp: Float,
    selected: LocalDate,
    weekStart: CalinoWeekStart,
    showWeekNumber: Boolean,
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
    val settledPaneWidth by animateDpAsState(
        targetValue = if (dayPaneCollapsed) 0.dp else SplitPaneWidthDp.dp,
        animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
        label = "day pane width",
    )
    // The pane grows with the grid settling rather than arriving already open,
    // so an unfold reads as one motion.
    val restingWidth = settledPaneWidth * morphFraction
    // Folding drives the two panes towards equal shares. The rule between them
    // is 44dp wide, so an even split is half of what is left after it.
    val evenWidth = ((windowWidth - 44.dp) / 2f).coerceAtLeast(0.dp)
    val paneWidth = lerpDp(restingWidth, evenWidth, splitProgress.coerceIn(0f, 1f))
    // Half open, the crease is a real edge: put the rule in the band so
    // neither pane straddles it.
    val hingeSplit = hingeStartDp != null && hingeStartDp > 0f && !dayPaneCollapsed
    val dayEvents = remember(events, selected) {
        events.filter { it.occursOn(selected) }
    }
    val dayTasks = tasksByDueDate[selected].orEmpty()

    Row(modifier.fillMaxSize()) {
        Column(
            if (hingeSplit) {
                Modifier.width(hingeStartDp!!.dp).fillMaxHeight()
            } else {
                Modifier.weight(1f).fillMaxHeight()
            },
        ) {
            MonthHeading(
                day = selected,
                showWeekNumber = showWeekNumber,
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
                    weekStart = weekStart,
                    events = events,
                    journals = journals,
                    tasksByDueDate = tasksByDueDate,
                    zoomState = pinnedZoom,
                    compactGridHeight = gridHeight,
                    detailedGridHeight = gridHeight,
                    compactDay = selected,
                    compactSelectorIndex = selected.weekdayColumn(weekStart).toFloat(),
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
        if (hingeSplit) {
            Spacer(Modifier.width((hingeBandDp.dp - 44.dp).coerceAtLeast(0.dp)).fillMaxHeight())
        }
        DayPaneDivider(collapsed = dayPaneCollapsed, onToggle = onToggleDayPane)
        if (paneWidth > 0.dp) {
            DayPane(
                day = selected,
                events = dayEvents,
                tasks = dayTasks,
                modifier = (if (hingeSplit) Modifier.weight(1f) else Modifier.width(paneWidth))
                    .fillMaxHeight()
                    .clipToBounds(),
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
    showWeekNumber: Boolean,
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
    showToday = day != LocalCalinoNow.current.today,
    // The grid's own selection pill already says which day is selected, so the
    // subtitle carries only what the grid cannot show.
    subtitle = if (showWeekNumber) "Week ${day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" else null,
)

@Composable
private fun WeekStrip(
    state: PagerState,
    day: LocalDate,
    displayedWeekDay: LocalDate,
    weekStart: CalinoWeekStart,
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
        val committedWeekStart = day.startOfWeek(weekStart)
        val settledIndex = day.weekdayColumn(weekStart)
        // A day pager offset is screen travel: negative reveals tomorrow and
        // positive reveals yesterday. Keep the week row fixed, but move its
        // indicator in lockstep while the agenda is being dragged/settled.
        // [selectorIndex] already tracks the previewed day, including a
        // boundary day in the neighboring week, so the week the strip is
        // displaying always follows it.
        val displayedWeekStart = displayedWeekDay.startOfWeek(weekStart)
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
            val pageWeekStart = weekStartForPage(page, weekStart)
            val pageDay = if (pageWeekStart == displayedWeekDay.startOfWeek(weekStart)) {
                displayedWeekDay
            } else {
                pageWeekStart.plusDays(day.weekdayColumn(weekStart).toLong())
            }
            // The displayed week owns the moving indicator. Other pages keep
            // the committed weekday so a week that is only sliding past does
            // not animate an indicator of its own.
            WeekStripPage(
                firstDay = pageWeekStart,
                weekStart = weekStart,
                selected = pageDay,
                indicatorIndex = if (pageWeekStart == displayedWeekStart) indicatorTargetIndex else settledIndex.toFloat(),
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
    firstDay: LocalDate,
    weekStart: CalinoWeekStart,
    selected: LocalDate,
    indicatorIndex: Float,
    events: List<CalEvent>,
    tasksByDueDate: Map<LocalDate, List<CalTask>>,
    interactionEnabled: Boolean,
    onDay: (LocalDate) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellWidth = maxWidth / 7
        val selectedIndex = selected.weekdayColumn(weekStart)
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
                    val date = firstDay.plusDays(dayDelta.toLong())
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
        if (drawSelectionBackground) {
            CalinoColors.SelectionFill.copy(alpha = CalinoColors.SelectionFill.alpha * selectedWeight)
        } else {
            Color.Transparent
        },
        label = "week selection",
    )
    // Fades in with the fill rather than being switched on, so the edge and
    // the block it outlines arrive together instead of the border snapping.
    val selectionBorder by animateColorAsState(
        if (drawSelectionBackground) {
            CalinoColors.SelectionBorder.copy(alpha = CalinoColors.SelectionBorder.alpha * selectedWeight)
        } else {
            Color.Transparent
        },
        label = "week selection edge",
    )
    val eventDensity = LocalCalinoPreferences.current.eventDensity
    val weekdayColor = lerpColor(CalinoColors.Ink3, CalinoColors.OnSelection.copy(.65f), selectedWeight)
    val dateColor = lerpColor(CalinoColors.Ink2, CalinoColors.OnSelection, selectedWeight)
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
            .border(1.dp, selectionBorder, RoundedCornerShape(14.dp))
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
            eventsFor(events, date).take(monthCellMarkerCap(eventDensity, 4)).forEach { event ->
                Box(Modifier.size(
                    width = if (event.allDay) 18.dp else 5.dp,
                    height = if (event.allDay) 3.dp else 5.dp,
                ).clip(RoundedCornerShape(2.dp)).background(CalinoColors.forEvent(Color(event.color))))
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
    val baseDate = task.due ?: LocalCalinoNow.current.today

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
    weekStart: CalinoWeekStart,
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
                        weekStart = weekStart,
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
    weekStart: CalinoWeekStart,
    events: List<CalEvent>,
    journals: List<JournalEntry>,
    zoomState: androidx.compose.runtime.State<Float>,
    compactGridHeight: Dp,
    detailedGridHeight: Dp,
    interactionEnabled: Boolean,
    targetHeight: Dp,
    onDay: (LocalDate) -> Unit,
) {
    // Hoisted once: draw scopes cannot read the palette's composition local.
    val colors = CalinoColors
    // Read here rather than inside the draw scope, which is not composable.
    val today = LocalCalinoNow.current.today
    val eventDensity = LocalCalinoPreferences.current.eventDensity
    val geometry = remember(month, weekStart) { monthGridGeometry(month, weekStart) }
    val start = geometry.start
    val rows = geometry.rows
    val monthEvents = remember(events, geometry) { monthEventIndex(events, month, weekStart) }
    val monthJournalDates = remember(journals, geometry) { monthJournalDates(journals, month, weekStart) }
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
        val dateLayouts = remember(geometry, density) {
            List(rows * 7) { index ->
                textMeasurer.measure(
                    start.plusDays(index.toLong()).dayOfMonth.toString(),
                    dateStyle,
                )
            }
        }
        val weekdayLayouts = remember(density, weekStart) {
            weekdayLetters(weekStart).map { textMeasurer.measure(it, weekdayStyle) }
        }
        val eventLayouts = remember(geometry, events, eventTextMaxWidth, density) {
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

                weekdayLetters(weekStart).forEachIndexed { column, _ ->
                    val layout = weekdayLayouts[column]
                    drawText(
                        layout,
                        topLeft = Offset(
                            horizontalPaddingPx + cellWidthPx * column + (cellWidthPx - layout.size.width) / 2f,
                            (headerHeightPx - layout.size.height) / 2f,
                        ),
                        color = faded(colors.Ink3),
                    )
                }

                repeat(rows * 7) { index ->
                    val row = index / 7
                    val column = index % 7
                    val date = start.plusDays(index.toLong())
                    val cellLeft = horizontalPaddingPx + cellWidthPx * column
                    val cellTop = headerHeightPx + rowHeightPx * row
                    val isSelected = date == selected
                    val isToday = date == today
                    val cellFill = when {
                        isSelected -> colors.AccentSoft.copy(alpha = .72f)
                        isToday -> colors.AccentSoft.copy(alpha = .45f)
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
                                if (isSelected) colors.Accent else colors.Accent.copy(alpha = .78f),
                            ),
                            radius = dateSizePx / 2f,
                            center = Offset(cellLeft + cellWidthPx / 2f, dateTop + dateSizePx / 2f),
                        )
                    }
                    val dateLayout = dateLayouts[index]
                    val dateColor = if (isSelected) colors.OnAccent else if (YearMonth.from(date) == month) {
                        colors.Ink2
                    } else {
                        colors.Ink3.copy(.5f)
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
                    val markerEvents = dayEvents.take(monthCellMarkerCap(eventDensity, 4))
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
                    // Only ever two chips here, however dense the setting: the
                    // morph measures these on the hot zoom path. Quiet lowers
                    // it; the settled grid below honours the full cap.
                    val morphChipCount = monthCellMarkerCap(eventDensity, 2)
                    markerEvents.forEachIndexed { eventIndex, event ->
                        val markerWidth = rawWidths[eventIndex] * markerScale
                        val markerHeight = with(density) { if (event.allDay) 3.dp.toPx() else 5.dp.toPx() }
                        val markerTop = eventAreaTop + (with(density) { 7.dp.toPx() } - markerHeight) / 2f
                        if (eventIndex < morphChipCount) {
                            val chipWidth = (cellWidthPx - chipHorizontalPaddingPx * 2f).coerceAtLeast(1f)
                            val chipTop = eventAreaTop + chipHeightPx * eventIndex
                            val x = markerLeft + (cellLeft + chipHorizontalPaddingPx - markerLeft) * eventMorph
                            val y = markerTop + (chipTop - markerTop) * eventMorph
                            val width = markerWidth + (chipWidth - markerWidth) * eventMorph
                            val height = markerHeight + (chipHeightPx - markerHeight) * eventMorph
                            val eventColor = colors.forEvent(Color(event.color))
                            val chipColor = colors.tint(Color(event.color), if (event.allDay) .18f else .10f)
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
                                        color = faded(colors.Ink, textMorph),
                                    )
                                }
                            }
                        } else {
                            drawRoundRect(
                                color = faded(colors.forEvent(Color(event.color)), 1f - eventMorph),
                                topLeft = Offset(markerLeft, markerTop),
                                size = Size(markerWidth, markerHeight),
                                cornerRadius = CornerRadius(with(density) { 2.dp.toPx() }),
                            )
                        }
                        markerLeft += markerWidth + eventMarkerGapPx
                    }
                    // Every "+n" in the app is the count that did not fit,
                    // never a literal: the shown count and the overflow have to
                    // come from the same cap or they disagree.
                    val overflow = dayEvents.size - monthCellShownCount(dayEvents.size, morphChipCount)
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
                            color = faded(colors.Ink3, overflowMorph),
                        )
                    }
                    if (date in monthJournalDates) {
                        drawCircle(
                            color = faded(colors.Plum),
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
                weekStart = weekStart,
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
    weekStart: CalinoWeekStart,
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
    // Read here rather than inside the draw scope, which is not composable.
    val today = LocalCalinoNow.current.today
    val eventDensity = LocalCalinoPreferences.current.eventDensity
    val geometry = remember(month, weekStart) { monthGridGeometry(month, weekStart) }
    val start = geometry.start
    val rows = geometry.rows

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
        val compactWeekStart = compactDay.startOfWeek(weekStart)
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
    weekStart: CalinoWeekStart,
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
    // Hoisted once: draw scopes cannot read the palette's composition local.
    val colors = CalinoColors
    // Read here rather than inside the draw scope, which is not composable.
    val today = LocalCalinoNow.current.today
    val eventDensity = LocalCalinoPreferences.current.eventDensity
    val geometry = remember(month, weekStart) { monthGridGeometry(month, weekStart) }
    val start = geometry.start
    val rows = geometry.rows
    val monthEvents = remember(events, geometry) { monthEventIndex(events, month, weekStart) }
    val monthJournalDates = remember(journals, geometry) { monthJournalDates(journals, month, weekStart) }
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
        val chipGapPx = with(density) { 2.dp.toPx() }
        val chipHorizontalPaddingPx = with(density) { 2.dp.toPx() }
        // No colour rail any more, so the title starts at the card's own
        // inset. It is symmetric with the trailing inset so a truncated title
        // does not read as pushed against the border.
        val chipTextStartPx = with(density) { 6.dp.toPx() }
        val chipTextEndPx = with(density) { 6.dp.toPx() }
        val chipCornerPx = with(density) { 8.dp.toPx() }
        val chipBorderPx = with(density) { 1.dp.toPx() }
        val overflowHeightPx = with(density) { 14.dp.toPx() }
        val dateLayouts = remember(geometry, density) {
            List(rows * 7) { index ->
                textMeasurer.measure(
                    text = start.plusDays(index.toLong()).dayOfMonth.toString(),
                    style = dateStyle,
                )
            }
        }
        val weekdayLayouts = remember(density, weekStart) {
            weekdayLetters(weekStart).map { textMeasurer.measure(it, weekdayStyle) }
        }
        val eventTextMaxWidth = (cellWidthPx - chipHorizontalPaddingPx * 2f - chipTextStartPx - chipTextEndPx)
            .roundToInt()
            .coerceAtLeast(1)
        val eventLayouts = remember(geometry, events, eventTextMaxWidth, density) {
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
        val cellDates = remember(geometry) {
            List(rows * 7) { index -> start.plusDays(index.toLong()) }
        }
        val cellEvents = remember(geometry, events) {
            cellDates.map { date -> monthEvents[date].orEmpty() }
        }
        val inMonthFlags = remember(geometry) {
            cellDates.map { date -> YearMonth.from(date) == month }
        }
        val compactMarkerWidths = remember(geometry, events, density, eventDensity) {
            cellEvents.map { dayEvents ->
                FloatArray(dayEvents.size.coerceAtMost(monthCellMarkerCap(eventDensity, 4))) { index ->
                    with(density) { if (dayEvents[index].allDay) 18.dp.toPx() else 5.dp.toPx() }
                }
            }
        }
        val overflowStyle = remember { ComposeTextStyle(fontSize = 10.sp) }
        // How many cards a fully expanded cell can actually hold. A day only
        // rolls up into "+n" once the stack would run past the bottom of its
        // row -- a fixed two-card limit was hiding events under a "+1" with
        // half the cell still empty.
        val chipPitchPx = chipHeightPx + chipGapPx
        val chipAreaHeightPx = (
            detailedRowHeightPx - dateTopPaddingPx - detailedDateSizePx - dateGapPx -
                with(density) { 2.dp.toPx() }
            ).coerceAtLeast(0f)
        val chipCapacity = monthCellChipCapacity(chipAreaHeightPx, chipHeightPx, chipGapPx, eventDensity.maxItems)
        val shownCounts = remember(geometry, events, chipCapacity) {
            cellEvents.map { dayEvents -> monthCellShownCount(dayEvents.size, chipCapacity) }
        }
        val overflowLayouts = remember(geometry, events, chipCapacity, density) {
            cellEvents.mapIndexed { index, dayEvents ->
                val overflow = dayEvents.size - shownCounts[index]
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
                val compactWeekStart = compactDay.startOfWeek(weekStart)
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
                    weekdayLetters(weekStart).forEachIndexed { column, _ ->
                        val layout = weekdayLayouts[column]
                        drawText(
                            layout,
                            topLeft = Offset(
                                horizontalPaddingPx + cellWidthPx * column + (cellWidthPx - layout.size.width) / 2f,
                                (headerHeightPx - layout.size.height) / 2f * (1f - compactProgress) +
                                    (compactStartHeightPx - compactWeekContentHeightPx) / 2f * compactProgress,
                            ),
                            color = faded(
                                lerpColor(colors.Ink3, colors.OnSelection,
                                    (1f - abs(compactSelectorIndex - column)).coerceIn(0f, 1f) * compactProgress),
                            ),
                        )
                    }
                }
                // The month's structural wash, under everything else. It is
                // painted as whole regions rather than per cell: square cell
                // corners read as a spreadsheet against the rounded blocks the
                // rest of the calendar is built from, and a band drawn whole
                // also keeps the weekend columns continuous instead of
                // breaking into a row of beads.
                //
                // The regions abut rather than overlap -- two translucent
                // washes stacked on one cell compound into a patch far darker
                // than either, which drew the eye to the corner of the grid
                // that deserves the least of it. So a corner is rounded only
                // where the region meets paper; where a borrowed run runs into
                // the weekend band the touching corners stay square on both
                // sides, and the two read as one continuous shape rather than
                // as two pills with a pinch between them.
                //
                // The whole thing retreats as the grid collapses into the week
                // strip, which has its own selected-day pill to carry.
                if (compactProgress < .999f) {
                    val washAlpha = 1f - compactProgress
                    // A single-column band under a Sunday start is only one
                    // cell wide, so the block radius has to fit inside it.
                    val washRadius = min(
                        with(density) { CalinoShapes.DayBlock.toPx() },
                        cellWidthPx / 2f,
                    )
                    val gridTop = rowTopFor(0)
                    val gridBottom = rowTopFor(rows - 1) + rowHeightFor(rows - 1)
                    monthWashPlan(
                        weekStart = weekStart,
                        leadingCells = geometry.leadingCells,
                        trailingIndex = geometry.trailingIndex,
                        rows = rows,
                    ).forEach { region ->
                        val top = region.row?.let { rowTopFor(it) } ?: gridTop
                        val bottom = region.row?.let { it -> rowTopFor(it) + rowHeightFor(it) } ?: gridBottom
                        val rect = Rect(
                            horizontalPaddingPx + cellWidthPx * region.columns.first,
                            top,
                            horizontalPaddingPx + cellWidthPx * (region.columns.last + 1),
                            bottom,
                        )
                        fun radius(round: Boolean) = CornerRadius(if (round) washRadius else 0f)
                        drawPath(
                            Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect,
                                        topLeft = radius(region.roundTopLeft),
                                        topRight = radius(region.roundTopRight),
                                        bottomRight = radius(region.roundBottomRight),
                                        bottomLeft = radius(region.roundBottomLeft),
                                    ),
                                )
                            },
                            color = faded(
                                when (region.kind) {
                                    WashKind.Weekend -> colors.WeekendWash
                                    WashKind.OutsideMonth -> colors.OutsideMonthWash
                                },
                                washAlpha,
                            ),
                        )
                    }
                }
                if (zoom < 1f && compactProgress > .001f) {
                    val pillHeight = min(
                        with(density) { CompactWeekMetrics.PillHeight.toPx() },
                        naturalWeekHeight.coerceAtLeast(1f),
                    )
                    val pillTopLeft = Offset(
                        compactWeekInsetPx + compactWeekCellWidthPx * compactSelectorIndex.coerceIn(0f, 6f),
                        compactWeekCenter - pillHeight / 2f,
                    )
                    val pillSize = Size(compactWeekCellWidthPx, pillHeight)
                    val pillCorner = CornerRadius(with(density) { CompactWeekMetrics.PillRadius.toPx() })
                    drawRoundRect(
                        color = faded(colors.SelectionFill.copy(alpha = colors.SelectionFill.alpha * .95f), compactProgress),
                        topLeft = pillTopLeft,
                        size = pillSize,
                        cornerRadius = pillCorner,
                    )
                    if (colors.SelectionBorder.alpha > 0f) {
                        // Inset by half the stroke so the edge lands inside the
                        // pill rather than straddling its bounds and reading a
                        // pixel wider than the fill.
                        val strokePx = with(density) { 1.dp.toPx() }
                        drawRoundRect(
                            color = faded(colors.SelectionBorder, compactProgress),
                            topLeft = pillTopLeft + Offset(strokePx / 2f, strokePx / 2f),
                            size = Size(pillSize.width - strokePx, pillSize.height - strokePx),
                            cornerRadius = pillCorner,
                            style = Stroke(width = strokePx),
                        )
                    }
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
                    val isToday = date == today
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
                        isSelected -> colors.AccentSoft.copy(alpha = .72f * (1f - compactProgress))
                        isToday -> colors.AccentSoft.copy(alpha = .45f * (1f - compactProgress))
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
                        isSelected -> colors.AccentSoft.copy(alpha = .72f * detailProgress)
                        isToday -> colors.AccentSoft.copy(alpha = .45f * detailProgress)
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
                                    if (isSelected) colors.Accent else colors.Accent.copy(alpha = .78f),
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
                                if (inMonthFlags[index]) colors.Ink2 else colors.Ink3.copy(.5f),
                                colors.OnSelection,
                                compactWeekSelectionWeight,
                            )
                        } else if (isSelected) {
                            colors.OnAccent
                        } else if (inMonthFlags[index]) {
                            colors.Ink2
                        } else {
                            colors.Ink3.copy(.5f)
                        }),
                    )

                    val dayEvents = cellEvents[index]
                    val shownCount = shownCounts[index]
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
                    // The collapsed grid only ever carries four markers, so a
                    // card past the fourth has nothing to grow out of: it
                    // simply fades in where it belongs.
                    repeat(max(rawWidths.size, shownCount)) { eventIndex ->
                        val event = dayEvents[eventIndex]
                        val hasMarker = eventIndex < rawWidths.size
                        val markerWidth = if (hasMarker) rawWidths[eventIndex] * markerScale else 0f
                        val markerHeight = with(density) { if (event.allDay) 3.dp.toPx() else 5.dp.toPx() }
                        val markerTop = eventAreaTop + (eventAreaHeightPx - markerHeight) / 2f
                        if (eventIndex < shownCount) {
                            val morph = if (hasMarker) detailProgress else 1f
                            val chipFade = if (hasMarker) 1f else detailProgress
                            val chipWidth = (cellWidthPx - chipHorizontalPaddingPx * 2f).coerceAtLeast(1f)
                            val chipLeft = cellLeft + chipHorizontalPaddingPx
                            val chipTop = eventAreaTop + chipPitchPx * eventIndex
                            val fromX = if (hasMarker) markerLeft else chipLeft
                            val fromY = if (hasMarker) markerTop else chipTop
                            val fromWidth = if (hasMarker) markerWidth else chipWidth
                            val fromHeight = if (hasMarker) markerHeight else chipHeightPx
                            val x = fromX + (chipLeft - fromX) * morph
                            val y = fromY + (chipTop - fromY) * morph
                            val width = fromWidth + (chipWidth - fromWidth) * morph
                            val height = fromHeight + (chipHeightPx - fromHeight) * morph
                            val eventColor = colors.forEvent(Color(event.color))
                            // The same fill and hairline edge the full-size
                            // event card wears, so a day's cards and its
                            // agenda entries read as one family.
                            val chipColor = colors.tint(eventColor, .12f, colors.Panel)
                            val corner = CornerRadius(
                                with(density) { 2.dp.toPx() } + (chipCornerPx - with(density) { 2.dp.toPx() }) * morph,
                            )
                            drawRoundRect(
                                color = faded(lerpColor(eventColor, chipColor, morph), chipFade),
                                topLeft = Offset(x, y),
                                size = Size(width, height),
                                cornerRadius = corner,
                            )
                            if (morph > .01f) {
                                drawRoundRect(
                                    color = faded(eventColor.copy(alpha = .16f), morph * chipFade),
                                    topLeft = Offset(x + chipBorderPx / 2f, y + chipBorderPx / 2f),
                                    size = Size(
                                        (width - chipBorderPx).coerceAtLeast(0f),
                                        (height - chipBorderPx).coerceAtLeast(0f),
                                    ),
                                    cornerRadius = corner,
                                    style = Stroke(width = chipBorderPx),
                                )
                                val textProgress = smoothStep(((morph - .5f) / .5f).coerceIn(0f, 1f))
                                if (textProgress > .01f) eventLayouts[event.id]?.let { layout ->
                                    drawText(
                                        layout,
                                        topLeft = Offset(
                                            x + chipTextStartPx,
                                            y + (height - layout.size.height) / 2f,
                                        ),
                                        color = faded(colors.Ink, textProgress * chipFade),
                                    )
                                }
                            }
                        } else if (hasMarker) {
                            drawRoundRect(
                                color = faded(colors.forEvent(Color(event.color)), 1f - detailProgress),
                                topLeft = Offset(markerLeft, markerTop),
                                size = Size(markerWidth, markerHeight),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }
                        if (hasMarker) markerLeft += markerWidth + eventMarkerGapPx
                    }
                    val overflowProgress = smoothStep(((detailProgress - .5f) / .5f).coerceIn(0f, 1f))
                    if (overflowProgress > .01f) {
                        overflowLayouts[index]?.let { layout ->
                            drawText(
                                layout,
                                topLeft = Offset(
                                    cellLeft + (cellWidthPx - layout.size.width) / 2f,
                                    eventAreaTop + chipPitchPx * shownCount +
                                        (overflowHeightPx - layout.size.height) / 2f,
                                ),
                                color = faded(colors.Ink3, overflowProgress),
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
                weekStart = weekStart,
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
    weekStart: CalinoWeekStart,
    events: List<CalEvent>,
    journals: List<JournalEntry>,
    detailProgress: Float,
    compactProgress: Float,
    compactDay: LocalDate,
    compactPagerOffset: Float,
    interactionEnabled: Boolean,
    onDay: (LocalDate) -> Unit,
) {
    // Read here rather than inside the draw scope, which is not composable.
    val today = LocalCalinoNow.current.today
    val eventDensity = LocalCalinoPreferences.current.eventDensity
    val geometry = remember(month, weekStart) { monthGridGeometry(month, weekStart) }
    val start = geometry.start
    val rows = geometry.rows
    // Index the month once per data/month change. The zoom animation only
    // changes layout progress; it must not make every cell rescan and
    // reparse the complete event list on every frame.
    val monthEvents = remember(events, geometry) { monthEventIndex(events, month, weekStart) }
    val monthJournalDates = remember(journals, geometry) { monthJournalDates(journals, month, weekStart) }
    val compactWeekStart = compactDay.startOfWeek(weekStart)
    val compactWeekRow = ((compactWeekStart.toEpochDay() - start.toEpochDay()) / 7L).toInt()
    val compactWeekIsInGrid = compactWeekRow in 0 until rows
    val indicatorIndex = (compactDay.weekdayColumn(weekStart) - compactPagerOffset).coerceIn(0f, 6f)
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
                weekdayLetters(weekStart).forEach {
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
                                weekStart = weekStart,
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
    weekStart: CalinoWeekStart,
    events: Map<LocalDate, List<CalEvent>>,
    journalDates: Set<LocalDate>,
    compactProgress: Float,
    interactive: Boolean,
    modifier: Modifier,
    onDay: (LocalDate) -> Unit,
) {
    val calinoToday = LocalCalinoNow.current.today
    Row(modifier) {
        repeat(7) { column ->
            val date = start.plusDays(column.toLong())
            val inMonth = YearMonth.from(date) == month
            val today = date == calinoToday
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
            val eventDensity = LocalCalinoPreferences.current.eventDensity
            // The Today fill fades out before the compact row settles. Keep
            // the label dark rather than leaving white text on the canvas.
            val regularDateColor = if (inMonth) CalinoColors.Ink2 else CalinoColors.Ink3.copy(.5f)
            val dateColor = if (today) {
                lerpColor(CalinoColors.OnAccent, CalinoColors.Ink2, compactProgress)
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
                    dayEvents.take(monthCellMarkerCap(eventDensity, 3)).forEach { event ->
                        Box(
                            Modifier
                                .width(if (event.allDay) 16.dp else 4.dp)
                                .height(if (event.allDay) 3.dp else 4.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(CalinoColors.forEvent(Color(event.color))),
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

/**
 * How many event cards fit in one expanded month cell: cards of [chipHeightPx]
 * stacked [chipGapPx] apart inside [chipAreaHeightPx]. The last card needs no
 * trailing gap, so the gap is added back before dividing.
 */
/**
 * The same capacity, lowered by the user's density setting.
 *
 * A cap over the measured geometry, never a replacement for it: Dense means
 * "everything that fits", not cards drawn past the bottom of the cell.
 */
internal fun monthCellChipCapacity(
    chipAreaHeightPx: Float,
    chipHeightPx: Float,
    chipGapPx: Float,
    densityCap: Int,
): Int = min(
    monthCellChipCapacity(chipAreaHeightPx, chipHeightPx, chipGapPx),
    densityCap.coerceAtLeast(0),
)

/**
 * How many markers a compact cell draws: the renderer's own structural limit,
 * then the user's. A renderer that only has room for three never draws four
 * because the setting says Dense.
 */
internal fun monthCellMarkerCap(density: CalinoEventDensity, rendererMax: Int): Int =
    min(rendererMax, density.maxItems)

internal fun monthCellChipCapacity(chipAreaHeightPx: Float, chipHeightPx: Float, chipGapPx: Float): Int {
    val pitch = chipHeightPx + chipGapPx
    if (pitch <= 0f) return 0
    return ((chipAreaHeightPx + chipGapPx) / pitch).toInt().coerceAtLeast(0)
}

/**
 * How many of a day's [eventCount] events get their own card. A day rolls up
 * only once it genuinely overruns its cell, and when it does the last slot
 * goes to the "+n" line rather than to a card, so the count itself is never
 * the thing pushed out of view.
 */
internal fun monthCellShownCount(eventCount: Int, capacity: Int): Int =
    if (eventCount <= capacity) eventCount else (capacity - 1).coerceAtLeast(0)

internal fun monthEventIndex(
    events: List<CalEvent>,
    month: YearMonth,
    weekStart: CalinoWeekStart,
): Map<LocalDate, List<CalEvent>> {
    val start = month.gridStart(weekStart)
    val cellCount = monthGridRows(month, weekStart) * 7
    return buildMap {
        repeat(cellCount) { index ->
            val date = start.plusDays(index.toLong())
            val dayEvents = eventsFor(events, date)
            if (dayEvents.isNotEmpty()) put(date, dayEvents)
        }
    }
}

private fun monthJournalDates(
    journals: List<JournalEntry>,
    month: YearMonth,
    weekStart: CalinoWeekStart,
): Set<LocalDate> {
    val start = month.gridStart(weekStart)
    val endExclusive = start.plusDays((monthGridRows(month, weekStart) * 7).toLong())
    return journals.asSequence()
        .map { it.date }
        .filter { it >= start && it < endExclusive }
        .toSet()
}

private fun monthGridRows(month: YearMonth, weekStart: CalinoWeekStart): Int {
    val start = month.gridStart(weekStart)
    return ((month.atEndOfMonth().toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
}

/**
 * Everything about a month's grid that depends on where the week begins.
 *
 * `rows` is 5 or 6 for the *same* month depending on the week start, so every
 * cache sized `rows * 7` has to be rebuilt when it changes. Keying those caches
 * on this value rather than on the month alone makes that automatic: a cache
 * that forgets the week start is the one bug in this area that stays invisible
 * until a user toggles the setting.
 */
@Immutable
internal data class MonthGridGeometry(
    val month: YearMonth,
    val weekStart: CalinoWeekStart,
    val start: LocalDate,
    val rows: Int,
) {
    val cellCount: Int get() = rows * 7

    /** How many borrowed cells precede the 1st. */
    val leadingCells: Int get() = month.leadingCells(weekStart)

    /** The flat cell index just past the last day of the month. */
    val trailingIndex: Int get() = leadingCells + month.lengthOfMonth()

    fun dateAt(index: Int): LocalDate = start.plusDays(index.toLong())
}

internal fun monthGridGeometry(month: YearMonth, weekStart: CalinoWeekStart): MonthGridGeometry =
    MonthGridGeometry(
        month = month,
        weekStart = weekStart,
        start = month.gridStart(weekStart),
        rows = monthGridRows(month, weekStart),
    )

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
    val today = date == LocalCalinoNow.current.today
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
                color = lerpColor(CalinoColors.Ink3, CalinoColors.OnSelection, compactSelectedWeight),
            )
        }
        val dateSize = lerpDp(lerpDp(22.dp, 25.dp, detailProgress), 18.dp, compactProgress)
        val monthDateColor = when {
            inMonth -> CalinoColors.Ink2
            else -> CalinoColors.Ink3.copy(.5f)
        }
        val compactDateColor = if (compactSelectedWeight > .5f) CalinoColors.OnSelection else CalinoColors.Ink2
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
                        lerpColor(monthDateColor, if (selected || today) CalinoColors.OnAccent else monthDateColor, selectedWeight),
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
    // Hoisted once: draw scopes cannot read the palette's composition local.
    val colors = CalinoColors
    // Only the first two events ever become readable chips. The remaining
    // events stay compact dots/lines and fade out as detail expands, so
    // measuring them as full composable subtrees only adds work to the hot
    // zoom path.
    val eventDensity = LocalCalinoPreferences.current.eventDensity
    val chipCount = monthCellMarkerCap(eventDensity, 2)
    val shownCount = events.size.coerceAtMost(chipCount)
    val compactExtraCount = (events.size - chipCount).coerceIn(0, 2)
    val overflow = events.size - monthCellShownCount(events.size, chipCount)
    val visibleMarkerCount = (shownCount + compactExtraCount)
        .coerceAtMost(monthCellMarkerCap(eventDensity, 4))
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
                    color = colors.Ink3,
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
                val color = colors.forEvent(Color(event.color))
                val chipColor = colors.tint(Color(event.color), if (event.allDay) .18f else .10f)
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
    val color = CalinoColors.forEvent(Color(event.color))
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

private fun eventDescription(event: CalEvent, timeFormat: CalinoTimeFormat): String = buildString {
    append(event.title)
    event.start?.let { append(", ").append(timeFormat.format(it)) }
    event.location?.let { append(", ").append(it) }
}

@Composable
private fun EventChip(event: CalEvent, minHeight: Dp = 34.dp, onClick: (() -> Unit)? = null, agendaStyle: Boolean = false) {
    val timeFormat = LocalTimeFormat
    val preferences = LocalCalinoPreferences.current
    val metadata = buildString {
        if (event.allDay) {
            append("All day")
        } else {
            event.start?.let { append(timeFormat.format(it)) }
            event.durationMinutes?.takeIf { preferences.showEndTimes }?.let { duration ->
                if (isNotEmpty()) append(" · ")
                append(formatCalinoDuration(duration))
            }
        }
        event.location?.takeIf { preferences.showLocations }?.let { location ->
            if (isNotEmpty()) append(" · ")
            append(location)
        }
    }
    val shape = RoundedCornerShape(if (agendaStyle) 10.dp else 6.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = maxOf(44.dp, minHeight)).clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = eventDescription(event, timeFormat) }
            .background(eventTint(Color(event.color), if (agendaStyle) .12f else .10f, CalinoColors.Panel))
            .border(1.dp, CalinoColors.forEvent(Color(event.color)).copy(alpha = if (agendaStyle) .16f else .12f), shape)
            .padding(horizontal = if (agendaStyle) 10.dp else 4.dp, vertical = if (agendaStyle) 7.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(if (agendaStyle) 4.dp else 2.dp)
                .height(if (agendaStyle) 30.dp else 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CalinoColors.forEvent(Color(event.color))),
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
    laneOverlap: Dp,
    dayRailOwnsInput: Boolean,
    agendaOwnsInput: Boolean,
    onEvent: ((CalEvent) -> Unit)?,
    onTaskDone: (CalTask, Boolean) -> Unit,
    onTaskRescheduleTo: (CalTask, LocalDate?) -> Unit,
    onTaskClick: ((CalTask) -> Unit)?,
    onOpenDay: ((LocalDate) -> Unit)?,
) {
    // Hoisted once: draw scopes cannot read the palette's composition local.
    val colors = CalinoColors
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
                    laneOverlap = laneOverlap,
                    laneBlend = { (1f - zoomState.value / MonthEndpointBlendEnd).coerceIn(0f, 1f) },
                    active = dayRailOwnsInput,
                    scrollEnabled = dayRailOwnsInput,
                    onEvent = if (dayRailOwnsInput) onEvent else null,
                    onTaskDone = if (dayRailOwnsInput) onTaskDone else null,
                    onTaskRescheduleTo = if (dayRailOwnsInput) onTaskRescheduleTo else null,
                    onTaskClick = if (dayRailOwnsInput) onTaskClick else null,
                )
            }
            Box(
                Modifier.fillMaxSize().padding(top = laneOverlap).drawWithContent {
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
                            drawRect(colors.Canvas)
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
    laneOverlap: Dp,
    laneBlend: () -> Float,
    onEvent: ((CalEvent) -> Unit)?,
    onTaskDone: ((CalTask, Boolean) -> Unit)?,
    onTaskRescheduleTo: ((CalTask, LocalDate?) -> Unit)?,
    onTaskClick: ((CalTask) -> Unit)?,
) {
    val interactionModifier = if (active) Modifier else Modifier.clearAndSetSemantics { }
    val density = LocalDensity.current
    // The all-day strip still never scrolls, but it is now an overlay rather
    // than a row above the rail: the rail has to start at the very top of the
    // lane for hours to pass under it and under the compact strip.
    val allDayEvents = dayEvents.filter { it.allDay }
    val hasHeader = dayTasks.isNotEmpty() || allDayEvents.isNotEmpty()
    var measuredHeader by remember { mutableStateOf(0.dp) }
    // A day with nothing above the hours gives the space straight back.
    val headerHeight = if (hasHeader) measuredHeader else 0.dp
    val railLayer = rememberGraphicsLayer()

    Box(interactionModifier.fillMaxSize()) {
        val laneHeight = laneOverlap + headerHeight
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    // Recorded so the lane above can draw a blurred copy of
                    // exactly what is passing under it.
                    railLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(railLayer)
                    // The lane owns its own band: the rail stops drawing there
                    // so what shows through the glass is only the blurred
                    // copy. Drawn twice -- once crisp, once blurred -- it read
                    // as smudged text instead of as something underneath.
                    val fadeIn = CompactLaneSoftEdge.toPx()
                    val laneBottom = laneHeight.toPx().coerceIn(0f, size.height)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                (laneBottom / size.height).coerceIn(0f, 1f) to Color.Transparent,
                                ((laneBottom + fadeIn) / size.height).coerceIn(0f, 1f) to Color.Black,
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .verticalScroll(scrollState, enabled = scrollEnabled),
        ) {
            // At rest the hours sit where they always did; this is the space
            // the lane and the all-day strip occupy above them.
            Spacer(Modifier.height(laneOverlap + headerHeight))
            HourRailContent(day, dayEvents, onEvent)
            // The add pill floats over this rail; keep the last hours
            // scrollable clear of it.
            Spacer(Modifier.height(CalinoSpacing.PillClearance))
        }

        CompactLaneScrim(
            source = railLayer,
            blend = laneBlend,
            modifier = Modifier
                .fillMaxWidth()
                .height(laneHeight + CompactLaneSoftEdge)
                .align(Alignment.TopCenter),
        )

        if (hasHeader) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .offset(y = laneOverlap)
                    .onSizeChanged { size ->
                        measuredHeader = with(density) { size.height.toDp() }
                    }
                    .padding(start = 52.dp, end = 20.dp, top = 5.dp, bottom = 5.dp),
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
                allDayEvents.take(2).forEach { event ->
                    EventChip(event, minHeight = 40.dp, onClick = onEvent?.let { callback -> { callback(event) } }, agendaStyle = true)
                }
            }
        }
    }
}

/**
 * The frosted lane the compact strip, the zoom handle, and the all-day strip
 * sit on. It is deliberately not a bar: it is mildly transparent over a blurred
 * copy of the rail, and its lower edge dissolves rather than ending on a line,
 * so hours read as passing underneath.
 *
 * [blend] is 1 at the compact endpoint and 0 once the month grid owns the
 * space, where the lane turns fully opaque again so the rail behind it cannot
 * bleed through the grid.
 */
@Composable
private fun CompactLaneScrim(
    source: GraphicsLayer,
    blend: () -> Float,
    modifier: Modifier = Modifier,
) {
    // Hoisted once: draw scopes cannot read the palette's composition local.
    val colors = CalinoColors
    val blurred = rememberGraphicsLayer()
    // RenderEffect is Android 12+. Below it, the tint and the dissolving edge
    // carry the effect on their own.
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val laneBlend = blend().coerceIn(0f, 1f)
                if (canBlur && laneBlend > 0f) {
                    // The rail has erased itself here, so this copy is the
                    // only thing showing through the glass.
                    blurred.renderEffect = BlurEffect(26f, 26f, TileMode.Clamp)
                    blurred.record { drawLayer(source) }
                    drawLayer(blurred)
                    // The tint mutes it to a suggestion of what is underneath
                    // and keeps the strip on top legible. It goes fully opaque
                    // as the month grid takes the lane.
                    drawRect(colors.Canvas.copy(alpha = lerp(1f, .74f, laneBlend)))
                } else {
                    drawRect(colors.Canvas)
                }
                val softEdge = CompactLaneSoftEdge.toPx().coerceAtMost(size.height)
                val fadeStart = ((size.height - softEdge) / size.height).coerceIn(0f, 1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        fadeStart to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

@Composable
private fun HourRailContent(day: LocalDate, dayEvents: List<CalEvent>, onEvent: ((CalEvent) -> Unit)?) {
    // Hoisted once: draw scopes cannot read the palette's composition local.
    val colors = CalinoColors
    val timeFormat = LocalTimeFormat
    val slots = remember(dayEvents) { layoutDayRail(dayEvents) }
    Box(Modifier.fillMaxWidth().height(1488.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(24) { hour ->
                val y = hour * 62.dp.toPx()
                drawLine(colors.Ink.copy(.08f), androidx.compose.ui.geometry.Offset(52.dp.toPx(), y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
            }
        }
        (0..23).forEach { hour ->
            Text(
                timeFormat.formatHour(hour),
                Modifier.offset(x = 8.dp, y = (hour * 62 - 7).dp),
                fontSize = 10.sp,
                color = colors.Ink3,
            )
        }
        // Overlapping events share the rail's width instead of being stacked
        // on top of each other, where the later one hid the earlier one.
        val railPreferences = LocalCalinoPreferences.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val railStart = 52.dp
            val railWidth = (maxWidth - railStart - 20.dp).coerceAtLeast(0.dp)
            val laneGap = 3.dp
            slots.forEach { slot ->
                val event = slot.event
                val laneWidth = ((railWidth - laneGap * (slot.columns - 1)) / slot.columns)
                    .coerceAtLeast(0.dp)
                val top = (slot.startMinute / 60f * 62).dp
                val height = ((slot.endMinute - slot.startMinute) / 60f * 62 - 4)
                    .coerceAtLeast(24f).dp
                // Only a block with room for a second line gets one; a
                // half-width 30-minute event would otherwise clip its title.
                val showMetadata = height >= 42.dp && laneWidth >= 110.dp
                Box(
                    Modifier.offset(x = railStart + (laneWidth + laneGap) * slot.column, y = top)
                        .width(laneWidth)
                        .height(height)
                        .clip(RoundedCornerShape(11.dp))
                        .then(if (onEvent != null) Modifier.clickable { onEvent(event) } else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = eventDescription(event, timeFormat) }
                        .background(eventTint(Color(event.color), .13f, colors.Panel))
                        .border(1.dp, Color(event.color).copy(alpha = .16f), RoundedCornerShape(11.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.width(4.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(CalinoColors.forEvent(Color(event.color))),
                        )
                        Column(Modifier.padding(start = 8.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                event.title,
                                fontSize = 13.5.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.Ink,
                            )
                            if (showMetadata) {
                                val metadata = buildString {
                                    append(timeFormat.format(event.start!!))
                                    event.durationMinutes
                                        ?.takeIf { railPreferences.showEndTimes }
                                        ?.let { append(" · ").append(formatCalinoDuration(it)) }
                                    event.location
                                        ?.takeIf { railPreferences.showLocations }
                                        ?.let { append(" · ").append(it) }
                                }
                                Text(metadata, fontSize = 11.sp, lineHeight = 14.sp, color = colors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        // The current-time marker, on today's rail only. It used to be drawn at
        // a hard-coded 11.33 hours on every page, so every day claimed to be
        // 11:20 and the line never moved. LocalCalinoNow re-reads on the minute.
        val now = LocalCalinoNow.current
        if (day == now.today) {
            Canvas(
                Modifier.fillMaxWidth()
                    .offset(y = (now.hourOfDay * 62).dp)
                    .height(8.dp)
                    .semantics { contentDescription = "Current time, ${timeFormat.format(now.time)}" },
            ) {
                drawLine(colors.Rose, androidx.compose.ui.geometry.Offset(44.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width, 4.dp.toPx()), 1.5f)
                drawCircle(colors.Rose, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(44.dp.toPx(), 4.dp.toPx()))
            }
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
        Modifier.fillMaxWidth().requiredHeight(ZoomHandleTouchHeight).then(gestureModifier)
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
