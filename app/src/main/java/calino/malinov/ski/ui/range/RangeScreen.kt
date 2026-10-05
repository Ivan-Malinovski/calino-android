package calino.malinov.ski.ui.range

import calino.malinov.ski.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import calino.malinov.ski.util.localizedDateFormatter
import calino.malinov.ski.util.LocalCalinoLocale

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalConfiguration
import calino.malinov.ski.ui.components.LocalCalinoPillLane
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import calino.malinov.ski.state.*
import calino.malinov.ski.ui.components.rememberDatePicker
import calino.malinov.ski.ui.components.rememberTimePicker
import calino.malinov.ski.ui.components.AgendaRow
import calino.malinov.ski.ui.surfaces.TaskActionMenu
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.ui.components.AllDayBand
import calino.malinov.ski.ui.components.AllDayBandDensity
import calino.malinov.ski.ui.components.CalinoMonthHeading
import calino.malinov.ski.ui.components.rememberMonthYearPicker
import calino.malinov.ski.ui.components.CompactDropdownControl
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.home.CompactLaneScrim
import calino.malinov.ski.ui.home.HourRailContent
import calino.malinov.ski.ui.home.TimelineCardBounds
import calino.malinov.ski.ui.home.TimelineEventCard
import calino.malinov.ski.ui.surfaces.EventMenuAction
import calino.malinov.ski.ui.surfaces.TaskMenuAction
import calino.malinov.ski.util.CalinoRangeMode
import calino.malinov.ski.util.EventDateIndex
import calino.malinov.ski.util.eventOnDayRail
import calino.malinov.ski.util.isTimedSpan
import calino.malinov.ski.util.layoutAllDayBand
import calino.malinov.ski.util.startOfWeek
import calino.malinov.ski.util.resolveAllDaySpans
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged


/** The range pager, addressed by tag the way the calendar pagers are. */
const val RangePagerTag = "range-pager"

// Keep the animated date line available for a future header preference.
private const val ShowRangeSubtitle = false

@Composable
fun RangeScreen(
    events: List<CalEvent>,
    tasks: List<CalTask>,
    initialDate: LocalDate,
    modifier: Modifier = Modifier,
    onOpenMenu: () -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    // The first day currently on screen, which is not the same as the selected
    // date once a multi-day range is showing: the add pill creates there, and
    // only this screen knows where the pager has come to rest.
    onFirstVisibleDayChanged: (LocalDate) -> Unit,
    onEventClick: (LocalDate, CalEvent) -> Unit,
    onEventAction: (EventMenuAction, CalEvent) -> Unit,
    onEventDrop: (CalEvent, LocalDate) -> Unit,
    onEventTimeDrop: (CalEvent, LocalDateTime) -> Unit,
    onEventResize: (CalEvent, Int) -> Unit,
    onCreateEventAt: (LocalDateTime) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
    onAddWeekTask: suspend (String, LocalDate, LocalDate) -> Boolean,
    onWeekTaskDetails: (String, LocalDate, LocalDate) -> Unit,
    onTaskSchedule: (CalTask, LocalDate, java.time.LocalTime?) -> Unit,
    onTaskWeek: (CalTask, LocalDate, LocalDate) -> Unit,
    taskIsWritable: (CalTask) -> Boolean,
    onWeekPopoverVisibilityChange: (Boolean) -> Unit,
) {
    val preferences = LocalCalinoPreferences.current
    val mode = preferences.rangeMode
    var popoverOpen by remember(mode) { mutableStateOf(false) }
    fun changePopoverVisibility(visible: Boolean) {
        popoverOpen = visible
        // Start the shell's status-bar fade in the same snapshot as the
        // heading/grid fades, instead of notifying it after composition.
        onWeekPopoverVisibilityChange(visible)
    }
    DisposableEffect(mode) {
        onWeekPopoverVisibilityChange(false)
        onDispose { onWeekPopoverVisibilityChange(false) }
    }
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
    // A date-bar swipe steps the window by single days, so a seven-day window
    // stops snapping to the week start until Today, the month picker or a mode
    // change puts it back.
    var weekAligned by rememberSaveable(mode, weekStart) { mutableStateOf(true) }
    // While the date bar holds the pager between pages, its settle position is
    // not a range; the bar rebases the pager itself when it lets go.
    var headerSliding by remember { mutableStateOf(false) }

    LaunchedEffect(initialDate) { anchorEpoch = initialDate.toEpochDay() }
    LaunchedEffect(pager, mode) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }
            .distinctUntilChanged()
            .collect { (scrolling, page) ->
                if (!scrolling && !headerSliding) {
                    // Read the base fresh: a date-bar step rebases this same pager.
                    val next = rangeAnchorForPage(LocalDate.ofEpochDay(pagerBaseEpoch), page, mode)
                    if (next.toEpochDay() != anchorEpoch) {
                        anchorEpoch = next.toEpochDay()
                        onDateChanged(next)
                    }
                }
            }
    }

    val visibleDays = rangeDays(anchor, mode, weekStart, weekAligned)
    val firstVisibleDay = visibleDays.first()
    LaunchedEffect(firstVisibleDay) { onFirstVisibleDayChanged(firstVisibleDay) }
    // The heading follows the pager, not the settled anchor: it names the range
    // the finger is bringing in as soon as that page is the nearer one. A
    // date-bar swipe sits between pages, so it also shifts by whole days.
    fun pageFirstDay(page: Int) =
        rangeStart(rangeAnchorForPage(base, page, mode), mode, weekStart, weekAligned)
    val liveFirstDay by remember(pager, base, mode, weekStart, weekAligned) {
        derivedStateOf {
            // The page being headed for, so the month title and range line
            // change together when a fling is released rather than late in
            // its settle, when currentPage finally crosses over.
            if (headerSliding) {
                pageFirstDay(pager.currentPage)
                    .plusDays((pager.currentPageOffsetFraction * mode.dayCount).roundToInt().toLong())
            } else {
                pageFirstDay(pager.targetPage)
            }
        }
    }
    val openMonthYearPicker = rememberMonthYearPicker(initial = { LocalDate.ofEpochDay(anchorEpoch) }) { month ->
        val current = LocalDate.ofEpochDay(anchorEpoch)
        val next = month.atDay(current.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
        pagerBaseEpoch = next.toEpochDay()
        anchorEpoch = next.toEpochDay()
        weekAligned = true
        onDateChanged(next)
        pagerGeneration += 1
    }
    val rangeDateFormat = localizedDateFormatter("MMM d")
    val rangeDescription = stringResource(R.string.cal_range_dates_accessibility, rangeLabel(liveFirstDay, mode.dayCount, rangeDateFormat))
    Column(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        Box(Modifier.semantics {
            // Retain the complete visible dates for accessibility without a painted subtitle.
            if (!ShowRangeSubtitle) contentDescription = rangeDescription
        }) {
            // The shared heading emits its control row and divider separately.
            // Stack them before placing the week-task scrim over both.
            Column {
                CalinoMonthHeading(
                    day = liveFirstDay,
                    onMonthYearClick = openMonthYearPicker,
                    onOpenMenu = onOpenMenu,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {
                        pagerBaseEpoch = today.toEpochDay()
                        anchorEpoch = today.toEpochDay()
                        weekAligned = true
                        onDateChanged(today)
                        pagerGeneration += 1
                    },
                    showToday = today !in visibleDays,
                    subtitleContent = if (ShowRangeSubtitle) {
                        {
                            RangeSubtitle(
                                pager = pager,
                                dayCount = mode.dayCount,
                                sliding = headerSliding,
                                firstDayNow = { liveFirstDay },
                                firstDayOf = ::pageFirstDay,
                            )
                        }
                    } else null,
                    showNavigationArrows = false,
                    showTodayButton = true,
                    todayIcon = CalinoIcons.CalendarToday,
                    trailingContent = {
                        CompactDropdownControl(
                            options = CalinoRangeMode.entries.map { pluralStringResource(R.plurals.cal_day_span_count, it.dayCount, it.dayCount) },
                            optionDescriptions = CalinoRangeMode.entries.map { stringResource(R.string.cal_segmented_option, stringResource(R.string.cal_range_size_days), it.dayCount.toString()) },
                            selectedIndex = CalinoRangeMode.entries.indexOf(mode),
                            onSelected = { preferences.setRangeMode(CalinoRangeMode.entries[it]) },
                            semanticLabel = stringResource(R.string.cal_range_size_days),
                        )
                    },
                )
            }
            WeekTaskScrim(
                visible = popoverOpen,
                onDismiss = { changePopoverVisibility(false) },
                modifier = Modifier.matchParentSize(),
            )
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(androidx.compose.animation.core.tween(CalinoMotion.ContentEnterMillis)) togetherWith fadeOut(androidx.compose.animation.core.tween(CalinoMotion.FadeThroughMillis)) },
            modifier = Modifier.fillMaxSize(),
            label = "range mode",
        ) { activeMode ->
            RangePagerSurface(
                pager = pager,
                activeMode = activeMode,
                popoverOpen = popoverOpen,
                onPopoverOpenChange = ::changePopoverVisibility,
                base = base,
                weekStart = weekStart,
                weekAligned = weekAligned,
                onHeaderSlidingChange = { headerSliding = it },
                onRebase = { firstDay, page ->
                    pagerBaseEpoch = firstDay.minusDays((page - RangePagerCenter).toLong() * activeMode.dayCount).toEpochDay()
                    anchorEpoch = firstDay.toEpochDay()
                    weekAligned = false
                    onDateChanged(firstDay)
                },
                eventIndex = eventIndex,
                tasks = tasks,
                timelineScale = timelineScale,
                timelineScroll = timelineScroll,
                onTimelineScaleChanged = { timelineScale = it },
                onEventClick = onEventClick,
                onEventAction = onEventAction,
                onEventDrop = onEventDrop,
                onEventTimeDrop = onEventTimeDrop,
                onEventResize = onEventResize,
                onCreateEventAt = onCreateEventAt,
                onTaskClick = onTaskClick,
                onTaskAction = onTaskAction,
                onTaskDone = onTaskDone,
                onAddWeekTask = onAddWeekTask,
                onWeekTaskDetails = onWeekTaskDetails,
                onTaskSchedule = onTaskSchedule,
                onTaskWeek = onTaskWeek,
                taskIsWritable = taskIsWritable,
            )
        }
    }
}

private fun rangeLabel(first: LocalDate, dayCount: Int, dateFormat: java.time.format.DateTimeFormatter): String =
    if (dayCount == 1) first.format(dateFormat)
    else "${first.format(dateFormat)} – ${first.plusDays(dayCount - 1L).format(dateFormat)}"

/**
 * The date range under the month title. It names the page the pager is headed
 * for (`targetPage`), which is known as soon as a drag passes the halfway point
 * or a fling is released, and swaps with a short timed transition, part by part: the month text moves only when the month does. Timing it,
 * instead of tying it to the finger, keeps a fast swipe from crossing the whole
 * hand-over in a couple of frames. A date-bar swipe lands between pages, so it
 * shows the exact first day and changes with it.
 */
@Composable
private fun RangeSubtitle(
    pager: androidx.compose.foundation.pager.PagerState,
    dayCount: Int,
    sliding: Boolean,
    firstDayNow: () -> LocalDate,
    firstDayOf: (Int) -> LocalDate,
) {
    val slide = with(LocalDensity.current) { 8.dp.roundToPx() }
    val style = CalinoTypography.labelSmall
    val rangeDateFormatter = localizedDateFormatter("MMM d")
    val rangeMonthFormatter = localizedDateFormatter("MMM")
    val describedFirst = if (sliding) firstDayNow() else firstDayOf(pager.targetPage)
    val rangeDescription = stringResource(R.string.cal_range_dates_accessibility, rangeLabel(describedFirst, dayCount, rangeDateFormatter))
    Box(Modifier.padding(top = 1.dp).clearAndSetSemantics {
        contentDescription = rangeDescription
    }) {
        if (sliding) {
            Text(rangeLabel(firstDayNow(), dayCount, rangeDateFormatter), style = style, color = CalinoColors.Ink3, maxLines = 1)
        } else {
            val target by remember(pager) { derivedStateOf { pager.targetPage } }
            val first = firstDayOf(target)
            val last = first.plusDays(dayCount - 1L)
            // Each part animates on its own, so a week that stays in the same
            // month changes only its day numbers.
            Row {
                RangeLabelPart(target, first.format(rangeMonthFormatter), slide, style)
                Text(" ", style = style)
                RangeLabelPart(target, first.dayOfMonth.toString(), slide, style)
                if (dayCount > 1) {
                    Text(" – ", style = style, color = CalinoColors.Ink3, maxLines = 1)
                    RangeLabelPart(target, last.format(rangeMonthFormatter), slide, style)
                    Text(" ", style = style)
                    RangeLabelPart(target, last.dayOfMonth.toString(), slide, style)
                }
            }
        }
    }
}

/** A label part that equals another by text alone, so only a real change animates. */
private class RangeLabelText(val page: Int, val text: String) {
    override fun equals(other: Any?) = other is RangeLabelText && other.text == text
    override fun hashCode() = text.hashCode()
}

@Composable
private fun RangeLabelPart(page: Int, text: String, slide: Int, style: androidx.compose.ui.text.TextStyle) {
    AnimatedContent(
        targetState = RangeLabelText(page, text),
        transitionSpec = {
            val direction = targetState.page.compareTo(initialState.page).coerceIn(-1, 1)
            ((fadeIn(tween(170)) + slideInHorizontally(tween(170)) { direction * slide }) togetherWith
                (fadeOut(tween(110)) + slideOutHorizontally(tween(110)) { -direction * slide / 2 })) using
                // Animate the width too: a new month name is not as wide as the
                // old one, and a snap would jump every part after it.
                SizeTransform(clip = false) { _, _ -> tween(170) }
        },
        label = "range label part",
    ) { part -> Text(part.text, style = style, color = CalinoColors.Ink3, maxLines = 1) }
}

@Composable
private fun RangePagerSurface(
    pager: androidx.compose.foundation.pager.PagerState,
    activeMode: CalinoRangeMode,
    popoverOpen: Boolean,
    onPopoverOpenChange: (Boolean) -> Unit,
    base: LocalDate,
    weekStart: calino.malinov.ski.util.CalinoWeekStart,
    weekAligned: Boolean,
    onHeaderSlidingChange: (Boolean) -> Unit,
    onRebase: (firstDay: LocalDate, page: Int) -> Unit,
    eventIndex: EventDateIndex,
    tasks: List<CalTask>,
    timelineScale: Float,
    timelineScroll: ScrollState,
    onTimelineScaleChanged: (Float) -> Unit,
    onEventClick: (LocalDate, CalEvent) -> Unit,
    onEventAction: (EventMenuAction, CalEvent) -> Unit,
    onEventDrop: (CalEvent, LocalDate) -> Unit,
    onEventTimeDrop: (CalEvent, LocalDateTime) -> Unit,
    onEventResize: (CalEvent, Int) -> Unit,
    onCreateEventAt: (LocalDateTime) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
    onAddWeekTask: suspend (String, LocalDate, LocalDate) -> Boolean,
    onWeekTaskDetails: (String, LocalDate, LocalDate) -> Unit,
    onTaskSchedule: (CalTask, LocalDate, java.time.LocalTime?) -> Unit,
    onTaskWeek: (CalTask, LocalDate, LocalDate) -> Unit,
    taskIsWritable: (CalTask) -> Boolean,
) {
    val density = LocalDensity.current
    val locale = LocalCalinoLocale
    val rangeDateFormatter = localizedDateFormatter("MMM d")
    val haptics = LocalHapticFeedback.current
    val timeFormat = LocalTimeFormat
    val taskToday = LocalCalinoNow.current.today
    val preferences = LocalCalinoPreferences.current
    val cardBounds = remember { mutableStateMapOf<String, TimelineCardBounds>() }
    var drag by remember { mutableStateOf<RangeDragSession?>(null) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var hostWidth by remember { mutableIntStateOf(0) }
    var hostHeight by remember { mutableIntStateOf(0) }
    var edgeDirection by remember { mutableIntStateOf(0) }
    var autoScrollDirection by remember { mutableIntStateOf(0) }
    var menuDismissalGeneration by remember { mutableIntStateOf(0) }
    var lastHapticMinute by remember { mutableStateOf<java.time.LocalTime?>(null) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    // Each mounted page reports its own date bar; the hour column clears the
    // one that is showing, not whichever neighbour measured last.
    val stripHeights = remember { mutableStateMapOf<Int, androidx.compose.ui.unit.Dp>() }
    // Derived per frame from the pager's own position, so the hour column
    // tracks the date bar the finger is revealing instead of snapping when
    // the settled page changes.
    val stripHeight = run {
        val current = stripHeights[pager.currentPage] ?: 0.dp
        val fraction = pager.currentPageOffsetFraction
        val neighbour = stripHeights[pager.currentPage + if (fraction >= 0f) 1 else -1] ?: current
        current + (neighbour - current) * kotlin.math.abs(fraction).coerceIn(0f, 1f)
    }
    val gutterLayer = rememberGraphicsLayer()
    val hourHeightPx = with(density) { (62 * timelineScale).dp.toPx() }

    val visibleDragDays = rangeDays(
        rangeAnchorForPage(base, pager.currentPage, activeMode),
        activeMode,
        weekStart,
        weekAligned,
    )
    val taskBounds = remember { mutableStateMapOf<String, Pair<CalTask, Rect>>() }
    var taskDrag by remember { mutableStateOf<Pair<CalTask, Offset>?>(null) }
    var taskDragging by remember { mutableStateOf(false) }
    // Measured bounds of the docked week-task strip.
    var shelfRect by remember { mutableStateOf(Rect.Zero) }
    var popoverRect by remember { mutableStateOf(Rect.Zero) }
    val configuration = LocalConfiguration.current
    // The week a new week task, a "this week" drop and the menu's "Move to this
    // week" target. Seven days show their own window (stepped ones included);
    // one or three days sit inside the calendar week of their first day.
    val weekFirst = if (activeMode.dayCount == 7) visibleDragDays.first() else visibleDragDays.first().startOfWeek(weekStart)
    val weekLast = if (activeMode.dayCount == 7) visibleDragDays.last() else weekFirst.plusDays(6)
    // What the badge and strip list: week tasks overlapping the days on screen.
    val weekTasks = remember(tasks, visibleDragDays) { weekTasksInRange(tasks, visibleDragDays.first(), visibleDragDays.last()) }
    val shelfLayout = weekShelfLayoutFor(configuration.screenWidthDp, configuration.screenHeightDp, weekTasks.size)
    val weekComposer = rememberWeekTaskComposer(weekFirst) { title -> onAddWeekTask(title, weekFirst, weekLast) }
    val navInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val stripShown = shelfLayout.kind == WeekShelfKind.Strip && shelfLayout.hasTasks
    val badgeShown = shelfLayout.kind == WeekShelfKind.Badge
    val shelfHeight = with(density) { shelfRect.height.toDp() }
    // How far the grid and hour column stay clear of the bottom: the pill, plus
    // the docked strip. The strip lifts the pill, so the two stack.
    val bottomReserve = when {
        stripShown -> shelfHeight + CalinoSpacing.PillClearance - 4.dp
        else -> CalinoSpacing.PillClearance
    }
    // The band a dragged task is released in for "this week". Geometric rather
    // than measured, so it is right on the first frame of a drag.
    val weekDropBand = WeekDropZoneHeight + navInset
    // Only the week-task surfaces are live while an overlay is open.
    val overlayOpen = popoverOpen && badgeShown
    LaunchedEffect(badgeShown) { if (!badgeShown) onPopoverOpenChange(false) }
    LaunchedEffect(popoverOpen) { if (!popoverOpen) weekComposer.adding = false }
    var taskMenu by remember { mutableStateOf<CalTask?>(null) }
    var scheduling by remember { mutableStateOf<CalTask?>(null) }
    var schedulingDay by remember { mutableStateOf(visibleDragDays.first()) }
    var scheduleChoice by remember { mutableStateOf(false) }
    val pickScheduleDay = rememberDatePicker({ schedulingDay }) { schedulingDay = it; scheduleChoice = true }
    val pickScheduleTime = rememberTimePicker({ scheduling?.dueTime }, title = stringResource(R.string.cal_due)) { time ->
        scheduling?.let { onTaskSchedule(it, schedulingDay, time) }; scheduling = null
    }
    fun taskAction(action: TaskMenuAction, task: CalTask) {
        when (action) {
            TaskMenuAction.Schedule -> { scheduling = task; schedulingDay = task.due?.takeIf { it in visibleDragDays } ?: visibleDragDays.first(); pickScheduleDay() }
            TaskMenuAction.ThisWeek -> onTaskWeek(task, weekFirst, weekLast)
            TaskMenuAction.Today, TaskMenuAction.Tomorrow, TaskMenuAction.NextWeek -> {
                if (task.isWeekTask()) {
                    val day = taskToday
                    onTaskSchedule(task, day.plusDays(when (action) { TaskMenuAction.Tomorrow -> 1L; TaskMenuAction.NextWeek -> 7L; else -> 0L }), null)
                } else onTaskAction(action, task)
            }
            else -> onTaskAction(action, task)
        }
    }
    val reopenTaskLabel = stringResource(R.string.cal_reopen_task)
    val completeTaskLabel = stringResource(R.string.cal_complete_task)
    val scheduleTaskLabel = stringResource(R.string.cal_schedule_task)
    val moveTaskThisWeekLabel = stringResource(R.string.cal_move_to_week)
    val taskModifier: @Composable (CalTask, String) -> Modifier = { task, source ->
        val key = "$source:${task.id}"
        DisposableEffect(key) { onDispose { taskBounds.remove(key) } }
        Modifier.onGloballyPositioned { taskBounds[key] = task to it.boundsInRoot() }
            .semantics { customActions = listOf(
                CustomAccessibilityAction(if (task.done) reopenTaskLabel else completeTaskLabel) { onTaskDone(task, !task.done); true },
                CustomAccessibilityAction(scheduleTaskLabel) { taskAction(TaskMenuAction.Schedule, task); true },
                CustomAccessibilityAction(moveTaskThisWeekLabel) { if (!task.isRecurringTask() && taskIsWritable(task)) { onTaskWeek(task, weekFirst, weekLast); true } else false },
            ) }
    }
    fun taskDestination(pointer: Offset): TaskDropDestination? {
        if (pointer.y < 0 || pointer.y >= hostHeight || pointer.x < 0 || pointer.x >= hostWidth) return null
        val band = with(density) { weekDropBand.toPx() }
        if (pointer.y >= hostHeight - band) return TaskDropDestination.Week
        val gutter = with(density) { CalinoSpacing.RailGutter.toPx() }
        if (pointer.x < gutter || pointer.y >= hostHeight - band) return null
        val day = rangeDropDay(pointer.x, hostWidth, gutter, visibleDragDays) ?: return null
        val time = if (pointer.y < with(density) { stripHeight.toPx() }) null
            else java.time.LocalTime.MIDNIGHT.plusMinutes(taskDropMinute(pointer.y, timelineScroll.value - with(density) { stripHeight.toPx() }.toInt(), hourHeightPx).toLong())
        return TaskDropDestination.Day(day, time)
    }
    val taskDestination = taskDrag?.let { taskDestination(it.second) }
    val taskOverShelf = taskDestination == TaskDropDestination.Week
    val taskDropDay = (taskDestination as? TaskDropDestination.Day)?.day
    val taskMinute = (taskDestination as? TaskDropDestination.Day)?.time?.let { it.hour * 60 + it.minute }
    val taskOverHeader = (taskDestination as? TaskDropDestination.Day)?.time == null && taskDropDay != null
    LaunchedEffect(taskMinute, taskDragging, taskOverHeader, taskOverShelf) {
        if (taskDragging && !taskOverHeader && !taskOverShelf) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val dragTarget = drag?.takeUnless { it.resize }?.let { session ->
        val day = rangeDropDay(
            session.pointer.x,
            hostWidth,
            with(density) { CalinoSpacing.RailGutter.toPx() },
            visibleDragDays,
        )
        val start = eventOnDayRail(session.card.event, session.card.day)?.start
        if (day != null && start != null) {
            rangeDropTarget(
                start = start,
                day = day,
                dragY = session.offset.y,
                scrollDelta = timelineScroll.value - session.scrollAtLift,
                hourHeight = hourHeightPx,
            )
        } else null
    }

    // The length a held end edge would give the event, on the same quarter-hour
    // grid a move snaps to. Shared by the preview and the write on release.
    val resizeTarget = drag?.takeIf { it.resize }?.let { session ->
        rangeResizeDuration(
            start = session.card.event.start!!,
            durationMinutes = session.card.event.durationMinutes!!,
            dragY = session.offset.y,
            scrollDelta = timelineScroll.value - session.scrollAtLift,
            hourHeight = hourHeightPx,
        )
    }
    var lastHapticDuration by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(resizeTarget) {
        val minutes = resizeTarget ?: return@LaunchedEffect
        val previous = lastHapticDuration
        if (previous != null && minutes != previous) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHapticDuration = minutes
    }

    LaunchedEffect(dragTarget?.toLocalTime()) {
        val minute = dragTarget?.toLocalTime() ?: return@LaunchedEffect
        val previous = lastHapticMinute
        if (previous != null && minute != previous) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHapticMinute = minute
    }

    // Key on whether a drag exists, not the per-frame session value. Keying on
    // `drag` restarts this delay for every pointer update, making an edge turn
    // require an unrealistically motionless hold.
    LaunchedEffect(edgeDirection, drag != null) {
        if (edgeDirection == 0 || drag == null) return@LaunchedEffect
        while (true) {
            delay(RangeEdgeTurnDelayMillis)
            val target = pager.currentPage + edgeDirection
            if (target !in 0 until pager.pageCount) break
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            // Once a page turn has visibly begun, finish it. Leaving the edge
            // cancels this effect; allowing that cancellation to interrupt the
            // pager animation strands it at a fractional page until the next
            // manual swipe.
            withContext(NonCancellable) {
                pager.animateScrollToPage(target)
            }
        }
    }

    LaunchedEffect(autoScrollDirection, drag != null || taskDragging) {
        if (autoScrollDirection == 0 || (drag == null && !taskDragging)) return@LaunchedEffect
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
                    taskBounds.entries.firstOrNull { (key, value) ->
                    val (task, rect) = value
                    // An open popover owns the screen: only its rows can be lifted.
                    (!overlayOpen || key.startsWith("shelf:")) &&
                    taskIsWritable(task) && !task.isRecurringTask() && rect.width > 0 && rect.contains(point + hostOrigin) &&
                    // Completion remains a separate touch lane at the leading edge.
                    when {
                        key.startsWith("shelf:") -> point.x + hostOrigin.x > rect.left + with(density) { 44.dp.toPx() }
                        key.startsWith("chip:") -> point.x + hostOrigin.x > rect.left + with(density) { WeekChipCompletionLane.toPx() }
                        else -> true
                    }
                }?.value?.first },
                onLift = { task, pointer -> taskDrag = task to pointer },
                onDragStart = { taskDragging = true; taskMenu = null; onPopoverOpenChange(false) },
                onDrag = { _, pointer ->
                    taskDrag = taskDrag?.let { it.first to pointer }
                    autoScrollDirection = if (pointer.y >= hostHeight - with(density) { weekDropBand.toPx() } || pointer.y < with(density) { stripHeight.toPx() }) 0
                        else edgeScrollDirection(pointer.y, hostHeight - with(density) { weekDropBand.toPx() }.toInt(), with(density) { RangeAutoScrollEdge.toPx() })
                },
                onRelease = {
                    taskDrag?.let { (task, pointer) ->
                        if (taskDragging) when (val destination = taskDestination(pointer)) {
                            TaskDropDestination.Week -> onTaskWeek(task, weekFirst, weekLast)
                            is TaskDropDestination.Day -> onTaskSchedule(task, destination.day, destination.time)
                            null -> Unit
                        } else taskMenu = task
                    }
                    taskDrag = null; taskDragging = false; autoScrollDirection = 0
                },
                onCancel = { taskDrag = null; taskDragging = false; autoScrollDirection = 0 },
            )
            .rangeTimelineLiftDrag(
                hitTest = { point ->
                    val root = point + hostOrigin
                    val visible = rangeDays(rangeAnchorForPage(base, pager.currentPage, activeMode), activeMode, weekStart, weekAligned)
                    if (overlayOpen || stripShown && shelfRect.contains(root) ||
                        taskBounds.values.any { it.second.contains(root) }) null
                    else cardBounds.values.firstOrNull { it.day in visible && it.rootRect.contains(root) }
                },
                onLift = { card, pointer ->
                    val resize = rangeCanResize(card.event, card.day) && rangeInResizeEdge(
                        pointerY = pointer.y + hostOrigin.y,
                        cardTop = card.rootRect.top,
                        cardBottom = card.rootRect.bottom,
                        edge = with(density) { RangeResizeEdge.toPx() },
                    )
                    drag = RangeDragSession(card, pointer = pointer, scrollAtLift = timelineScroll.value, resize = resize)
                    lastHapticMinute = card.event.start?.toLocalTime()
                    lastHapticDuration = card.event.durationMinutes.takeIf { resize }
                },
                onDragStart = { menuDismissalGeneration++ },
                onDrag = { delta, pointer ->
                    drag = drag?.let { it.copy(offset = it.offset + delta, pointer = pointer) }
                    // An end edge stays in its own day, so it never turns pages.
                    edgeDirection = if (drag?.resize == true) 0 else {
                        rangeEdgeDirection(pointer.x, hostWidth, with(density) { RangeEdgeTurnZone.toPx() })
                    }
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
                    lastHapticMinute = null
                    lastHapticDuration = null
                    if (session != null && session.resize) {
                        val minutes = resizeTarget
                        if (minutes != null && minutes != session.card.event.durationMinutes) {
                            onEventResize(session.card.event, minutes)
                        }
                    } else if (session != null) {
                        val start = session.card.event.start
                        val target = dragTarget?.let { rangeEventStartAfterDrop(session.card.event, session.card.day, it) }
                        if (start != null && target != null) {
                            if (target != start) onEventTimeDrop(session.card.event, target)
                        }
                    }
                },
                onCancel = {
                    edgeDirection = 0
                    autoScrollDirection = 0
                    drag = null
                    lastHapticMinute = null
                    lastHapticDuration = null
                },
            ),
    ) {
        // One hour column for the whole surface, outside the pager, so a swipe
        // moves only days. It shares the pages' scroll state and keeps the date
        // bar's height clear at its top.
        Row(Modifier.fillMaxSize()) {
            // Dressed exactly like a page's leading edge: the hours scroll
            // under the date bar's frosted scrim and its divider line.
            Box(Modifier.width(CalinoSpacing.RailGutter).fillMaxHeight()) {
                RangeHourGutter(
                    timelineScale,
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            gutterLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(gutterLayer)
                        }
                        .verticalScroll(timelineScroll)
                        .padding(top = stripHeight, bottom = bottomReserve),
                )
                CompactLaneScrim(
                    source = gutterLayer,
                    blend = { 1f },
                    modifier = Modifier.fillMaxWidth().height(stripHeight),
                    dissolveEdge = false,
                )
                Spacer(
                    Modifier.fillMaxWidth()
                        .padding(top = (stripHeight - 1.dp).coerceAtLeast(0.dp))
                        .height(1.dp)
                        .background(CalinoColors.Line),
                )
                // Separates the hours from the days below the date bar.
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .padding(top = stripHeight)
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(CalinoColors.Line2),
                )
            }
            HorizontalPager(
                    state = pager,
                    userScrollEnabled = drag == null && !taskDragging,
                    beyondViewportPageCount = 1,
                    key = { page -> "${activeMode.name}:$page" },
                    // Tagged like month-pager/week-pager/day-pager, so a device
                    // test can scope a day query to this surface. Several grids are
                    // mounted at once during a route change and day descriptions
                    // are not unique across them.
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag(RangePagerTag),
                ) { page ->
                    val pageAnchor = rangeAnchorForPage(base, page, activeMode)
                    val days = rangeDays(pageAnchor, activeMode, weekStart, weekAligned)
                    RangePage(
                        days = days,
                        eventIndex = eventIndex,
                        tasks = tasks.filterNot { it.isWeekTask() },
                        taskModifier = { task -> taskModifier(task, "page:$page") },
                        bottomReserve = bottomReserve,
                        timelineScale = timelineScale,
                        timelineScroll = timelineScroll,
                        onTimelineScaleChanged = onTimelineScaleChanged,
                        onRangeModePinch = { scale ->
                            val next = rangeModeAfterHorizontalPinch(activeMode, scale)
                            if (next != activeMode) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                preferences.setRangeMode(next)
                            }
                        },
                        onEventClick = onEventClick,
                        onEventAction = onEventAction,
                        onEventDrop = onEventDrop,
                        onEventTimeDrop = onEventTimeDrop,
                        onCreateEventAt = onCreateEventAt,
                        onTaskClick = onTaskClick,
                        onTaskAction = { action, task -> taskAction(action, task) },
                        onTaskDone = onTaskDone,
                        draggingCardKey = drag?.card?.key,
                        menuDismissalGeneration = menuDismissalGeneration,
                        onCardBounds = { cardBounds[it.key] = it },
                        onCardGone = { cardBounds.remove(it) },
                        onHeaderHeight = { headerHeight = it },
                        onStripHeight = { stripHeights[page] = it },
                    )
                }
      }
        if (stripShown) {
            WeekTaskStrip(
                tasks = weekTasks, hovering = taskOverShelf && taskDragging, height = shelfLayout.stripHeightDp.dp,
                composer = weekComposer,
                onOpen = onTaskClick, onDone = onTaskDone, onLongClick = { taskMenu = it },
                taskModifier = { task -> taskModifier(task, "chip") },
                onDetails = { title -> onWeekTaskDetails(title, weekFirst, weekLast) },
                onBounds = { shelfRect = it },
                modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
            )
            DisposableEffect(Unit) { onDispose { shelfRect = Rect.Zero } }
        }
        // A docked strip pushes the add pill up with its own measured height,
        // frame for frame with the surface. The pill only rests there while the
        // lift is the whole story: dragging a task and an open task modal
        // (whose pill appears at the lane's normal place) each settle it back
        // down first, on a short tween, and it glides up again after.
        val pillLane = LocalCalinoPillLane.current
        val settleLift = taskDragging || pillLane.claimedByModal
        val liftHold by animateFloatAsState(
            if (settleLift) 0f else 1f,
            animationSpec = tween(CalinoMotion.ContentEnterMillis),
            label = "week shelf pill lift",
        )
        val rawLift = if (stripShown) (shelfHeight - navInset + 16.dp - 20.dp).coerceAtLeast(0.dp) else 0.dp
        val lift = rawLift * liftHold
        DisposableEffect(lift) {
            pillLane.bottomLift = lift
            onDispose { if (pillLane.bottomLift == lift) pillLane.bottomLift = 0.dp }
        }
        taskMenu?.let { task ->
            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = bottomReserve)) {
                TaskActionMenu(task, expanded = true, onDismiss = { taskMenu = null }, onAction = { taskAction(it, task) })
            }
        }
        if (scheduleChoice) AlertDialog(
            onDismissRequest = { scheduleChoice = false; scheduling = null },
            title = { Text(stringResource(R.string.cal_schedule_task)) }, text = { Text(schedulingDay.format(rangeDateFormatter)) },
            confirmButton = { TextButton(onClick = { scheduleChoice = false; scheduling?.let { onTaskSchedule(it, schedulingDay, null) }; scheduling = null }) { Text(stringResource(R.string.cal_all_day)) } },
            dismissButton = { TextButton(onClick = { scheduleChoice = false; pickScheduleTime() }) { Text(stringResource(R.string.cal_choose_time)) } },
        )
        WeekTaskDropZone(
            visible = taskDragging, hovering = taskOverShelf, height = weekDropBand,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        val visibleTaskDrag = taskDrag
        if (taskDragging && visibleTaskDrag != null) {
            val destination = taskDestination
            val label = when (destination) {
                TaskDropDestination.Week -> stringResource(R.string.cal_sometime_this_week)
                is TaskDropDestination.Day -> if (destination.time != null) stringResource(R.string.cal_day_with_time, destination.day.format(rangeDateFormatter), timeFormat.format(destination.time, locale)) else stringResource(R.string.cal_day_all_day, destination.day.format(rangeDateFormatter))
                null -> stringResource(R.string.cal_drag_to_day_or_week)
            }
            if (destination is TaskDropDestination.Day && destination.time != null) {
                val gutter = with(density) { CalinoSpacing.RailGutter.toPx() }
                val columnWidth = (hostWidth - gutter) / visibleDragDays.size
                val column = visibleDragDays.indexOf(destination.day)
                val minute = destination.time.hour * 60 + destination.time.minute
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((gutter + columnWidth * column).toInt(),
                    (minute / 60f * hourHeightPx - timelineScroll.value + with(density) { stripHeight.toPx() }).toInt()) }
                    .width(with(density) { columnWidth.toDp() }).height(2.dp).background(CalinoColors.Accent))
            }
            val dropPreviewDescription = stringResource(R.string.cal_task_drop_preview, label)
            Column(Modifier.align(Alignment.TopStart).offset {
                val x = (visibleTaskDrag.second.x - with(density) { 110.dp.toPx() }).toInt()
                    .coerceIn(0, (hostWidth - with(density) { 220.dp.toPx() }).toInt().coerceAtLeast(0))
                androidx.compose.ui.unit.IntOffset(x, (visibleTaskDrag.second.y - with(density) { 72.dp.toPx() }).toInt().coerceAtLeast(0))
            }.width(220.dp).background(CalinoColors.Panel.copy(alpha = .96f), RoundedCornerShape(12.dp))
                .border(1.dp, CalinoColors.Accent.copy(alpha = .6f), RoundedCornerShape(12.dp)).padding(12.dp)
                .semantics { contentDescription = dropPreviewDescription }) {
                Text(visibleTaskDrag.first.title, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
            }
        }
        if (activeMode.dayCount > 1 && drag == null && !taskDragging) {
            val gutterPx = with(density) { CalinoSpacing.RailGutter.toPx() }
            val gapPx = with(density) { CalinoSpacing.RailColumnGap.toPx() }
            val columnStep = (hostWidth - gutterPx - gapPx * activeMode.dayCount) / activeMode.dayCount + gapPx
            RangeDateBarSwipe(
                pager = pager,
                dayCount = activeMode.dayCount,
                columnStep = columnStep,
                firstDayOf = { page ->
                    rangeStart(rangeAnchorForPage(base, page, activeMode), activeMode, weekStart, weekAligned)
                },
                onSlidingChange = onHeaderSlidingChange,
                onRebase = onRebase,
                modifier = Modifier.fillMaxWidth().height(headerHeight),
            )
        }
        if (badgeShown) {
            WeekTaskPopover(
                visible = popoverOpen,
                onDismiss = { onPopoverOpenChange(false); weekComposer.adding = false },
                first = weekFirst, last = weekLast, tasks = weekTasks, composer = weekComposer,
                anchorTop = ((headerHeight + 48.dp) / 2 + 2.dp).coerceAtLeast(0.dp),
                onOpen = { onPopoverOpenChange(false); onTaskClick(it) }, onDone = onTaskDone, onLongClick = { taskMenu = it },
                taskModifier = { task -> taskModifier(task, "shelf") },
                onDetails = { title -> onPopoverOpenChange(false); onWeekTaskDetails(title, weekFirst, weekLast) },
                onBounds = { popoverRect = it },
            )
            // Last, so the badge stays lit above the popover's scrim.
            WeekTaskBadge(
                openTasks = weekTasks.count { !it.done }, expanded = popoverOpen,
                onClick = { onPopoverOpenChange(!popoverOpen); if (popoverOpen) weekComposer.adding = false },
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(start = 2.dp, top = ((headerHeight - 48.dp) / 2).coerceAtLeast(0.dp)),
            )
        }
        if (drag != null && dragTarget != null) {
            val session = drag!!
            val dayIndex = visibleDragDays.indexOf(dragTarget.toLocalDate())
            val gutterPx = with(density) { CalinoSpacing.RailGutter.toPx() }
            val gapPx = with(density) { CalinoSpacing.RailColumnGap.toPx() }
            val columnWidth = ((hostWidth - gutterPx - gapPx * visibleDragDays.size) /
                visibleDragDays.size.coerceAtLeast(1)).coerceAtLeast(1f)
            val previewLeft = gutterPx + gapPx + dayIndex.coerceAtLeast(0) * (columnWidth + gapPx)
            val previewTop = (dragTarget.hour * 60 + dragTarget.minute) / 60f * hourHeightPx - timelineScroll.value +
                with(density) { stripHeight.toPx() }
            val dropStartDescription = stringResource(R.string.cal_drop_start_time, timeFormat.format(dragTarget.toLocalTime(), locale))
            val dropPreviewDescription = stringResource(R.string.cal_drop_preview, dragTarget.toLocalDate().format(rangeDateFormatter), timeFormat.format(dragTarget.toLocalTime(), locale))
            Text(
                text = timeFormat.format(dragTarget.toLocalTime(), locale),
                modifier = Modifier
                    .width(CalinoSpacing.RailGutter - 6.dp)
                    .graphicsLayer {
                        translationX = with(density) { 3.dp.toPx() }
                        translationY = previewTop - with(density) { 8.dp.toPx() }
                    }
                    .clip(RoundedCornerShape(7.dp))
                    .background(CalinoColors.Canvas.copy(alpha = .94f))
                    .border(1.dp, CalinoColors.Accent.copy(alpha = .7f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .semantics {
                        contentDescription = dropStartDescription
                    },
                color = CalinoColors.Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            TimelineEventCard(
                modifier = Modifier
                    .width(with(density) { session.card.rootRect.width.toDp() })
                    .height(with(density) { session.card.rootRect.height.toDp() })
                    .graphicsLayer {
                        translationX = previewLeft
                        translationY = previewTop
                        alpha = .34f
                    }
                    .semantics {
                        contentDescription = dropPreviewDescription
                    },
                event = eventOnDayRail(session.card.event, session.card.day)!!,
                showMetadata = session.card.showMetadata,
                timeFormat = timeFormat,
                preferences = preferences,
                colors = CalinoColors,
                hideAccentRail = true,
            )
        }
        val resizing = drag?.takeIf { it.resize }
        if (resizing != null && resizeTarget != null) {
            RangeResizePreview(
                session = resizing,
                durationMinutes = resizeTarget,
                hostOrigin = hostOrigin,
                scrollDelta = timelineScroll.value - resizing.scrollAtLift,
                hourHeightPx = hourHeightPx,
            )
        }
        drag?.takeUnless { it.resize }?.let { session ->
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
                event = eventOnDayRail(session.card.event, session.card.day)!!,
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
    taskModifier: @Composable (CalTask) -> Modifier,
    bottomReserve: androidx.compose.ui.unit.Dp,
    timelineScale: Float,
    timelineScroll: ScrollState,
    onTimelineScaleChanged: (Float) -> Unit,
    onRangeModePinch: (Float) -> Unit,
    onEventClick: (LocalDate, CalEvent) -> Unit,
    onEventAction: (EventMenuAction, CalEvent) -> Unit,
    onEventDrop: (CalEvent, LocalDate) -> Unit,
    onEventTimeDrop: (CalEvent, LocalDateTime) -> Unit,
    onCreateEventAt: (LocalDateTime) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
    draggingCardKey: String?,
    menuDismissalGeneration: Int,
    onCardBounds: (TimelineCardBounds) -> Unit,
    onCardGone: (String) -> Unit,
    onHeaderHeight: (androidx.compose.ui.unit.Dp) -> Unit,
    onStripHeight: (androidx.compose.ui.unit.Dp) -> Unit,
) {
    val density = LocalDensity.current
    val hideDone = LocalCalinoPreferences.current.hideCompletedTasks
    val multiDayEventsInHeader = LocalCalinoPreferences.current.rangeMultiDayEventsInHeader
    val locale = LocalCalinoLocale
    val dayCalendarDescription = stringResource(R.string.cal_day_calendar, days.size)
    val railLayer = rememberGraphicsLayer()
    // The date bar's height is already animated by the all-day band, so the
    // measured value is per-frame; a second spring here would lag it and let the
    // gutter's header line drift from the page's.
    var stripHeight by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize().semantics { contentDescription = dayCalendarDescription }) {
        Row(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    railLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(railLayer)
                }
                .rangePinch(
                    onVerticalZoom = { zoom ->
                        onTimelineScaleChanged((timelineScale * zoom).coerceIn(.65f, 1.8f))
                    },
                    onHorizontalPinch = onRangeModePinch,
                )
                .verticalScroll(timelineScroll).padding(top = stripHeight, bottom = bottomReserve),
            horizontalArrangement = Arrangement.spacedBy(CalinoSpacing.RailColumnGap),
        ) {
            // The hours live outside the pager (see RangePagerSurface); this
            // keeps the leading gap so columns sit where they always have.
            Spacer(Modifier.width(0.dp))
            days.forEach { day ->
                val timed = remember(eventIndex, day, multiDayEventsInHeader) {
                    eventIndex.eventsOn(day).filterNot { it.allDay || (multiDayEventsInHeader && it.isTimedSpan()) }
                }
                Box(
                    Modifier.weight(1f).border(0.5.dp, CalinoColors.Line2)
                        .rangeEmptyLongPress(
                            isOccupied = { point ->
                                val minute = point.y /
                                    with(density) { (62 * timelineScale).dp.toPx() } * 60f
                                tasks.any { it.due == day && it.dueTime != null && minute >= it.dueTime.hour * 60 + it.dueTime.minute && minute < it.dueTime.hour * 60 + it.dueTime.minute + 44f * 60 / (62 * timelineScale) } || timed.any { event ->
                                    val slice = eventOnDayRail(event, day) ?: return@any false
                                    val start = slice.start!!
                                    val startMinute = start.hour * 60 + start.minute
                                    val duration = slice.durationMinutes ?: 60
                                    minute >= startMinute && minute < startMinute + duration
                                }
                            },
                            onLongPress = { point ->
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
                        menuDismissalGeneration = menuDismissalGeneration,
                        onCardBounds = onCardBounds,
                        onCardGone = onCardGone,
                        showHourLabels = false,
                        compactRangeCards = true,
                        growFromEvents = days.size > 1,
                        onEventDragEnd = null,
                    )
                    tasks.filter { it.due == day && it.dueTime != null && (!hideDone || !it.done) }.forEach { task ->
                        val minute = task.dueTime!!.hour * 60 + task.dueTime.minute
                        RangeTaskDeadline(task, onTaskClick, onTaskDone, onTaskAction,
                            Modifier.offset(y = (minute / 60f * 62 * timelineScale).dp).then(taskModifier(task)))
                    }
                }
            }
        }
        CompactLaneScrim(
            source = railLayer,
            blend = { 1f },
            modifier = Modifier.fillMaxWidth().height(stripHeight),
            dissolveEdge = false,
        )
        Column(
            Modifier.fillMaxWidth().onSizeChanged { size ->
                stripHeight = with(density) { size.height.toDp() }
                onStripHeight(stripHeight)
            },
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .onSizeChanged { onHeaderHeight(with(density) { it.height.toDp() }) }
                    .padding(start = CalinoSpacing.RailColumnGap),
                horizontalArrangement = Arrangement.spacedBy(CalinoSpacing.RailColumnGap),
            ) {
                days.forEach { day ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val weekdayStyle = when (days.size) {
                            1 -> TextStyle.FULL
                            3 -> TextStyle.SHORT
                            else -> TextStyle.NARROW
                        }
                        Text(
                            day.dayOfWeek.getDisplayName(weekdayStyle, locale).uppercase(locale),
                            fontSize = 10.sp,
                            color = CalinoColors.Ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(day.dayOfMonth.toString(), fontSize = if (days.size == 7) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = CalinoColors.Ink)
                    }
                }
            }
            // Filtered once, ahead of the packer, rather than per day inside
            // it -- otherwise toggling "hide completed" would not relayout.
            val visibleTasks = remember(tasks, hideDone) { tasks.filter { it.dueTime == null && (!hideDone || !it.done) } }
            val spans = remember(eventIndex, days, multiDayEventsInHeader) {
                resolveAllDaySpans(days, includeTimedSpans = multiDayEventsInHeader, eventsOn = eventIndex::eventsOn)
            }
            var bandExpanded by rememberSaveable(days.first(), days.size) { mutableStateOf(false) }
            val bandLayout = remember(spans, visibleTasks, days, bandExpanded) {
                layoutAllDayBand(days, spans, visibleTasks, if (bandExpanded) Int.MAX_VALUE else RangeBandLaneLimit)
            }
            AllDayBand(
                days = days,
                layout = bandLayout,
                density = AllDayBandDensity.Narrow,
                gutterWidth = CalinoSpacing.RailColumnGap,
                columnGap = CalinoSpacing.RailColumnGap,
                edgeWidth = 0.dp,
                expanded = bandExpanded,
                onExpandedChange = { bandExpanded = it },
                onEventClick = onEventClick,
                onEventAction = onEventAction,
                onTaskClick = onTaskClick,
                onTaskAction = onTaskAction,
                onTaskDone = onTaskDone,
                taskModifier = taskModifier,
            )
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line))
        }
    }
}

/**
 * The weekday/date bar as a finer page swipe: the pager follows the finger
 * one-to-one, and on release it settles on the nearest whole day instead of
 * the nearest page. The pager is then rebased so that day-aligned position is
 * an ordinary resting page again.
 *
 * It sits over the pager rather than inside a page, so the finger's
 * coordinates stay put while the content it drags moves underneath.
 */
@Composable
private fun RangeDateBarSwipe(
    pager: androidx.compose.foundation.pager.PagerState,
    dayCount: Int,
    columnStep: Float,
    firstDayOf: (Int) -> LocalDate,
    onSlidingChange: (Boolean) -> Unit,
    onRebase: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentFirstDayOf by rememberUpdatedState(firstDayOf)
    val currentStep by rememberUpdatedState(columnStep)
    val currentOnSlidingChange by rememberUpdatedState(onSlidingChange)
    val currentOnRebase by rememberUpdatedState(onRebase)
    var settling by remember { mutableStateOf(false) }

    suspend fun settle(startPage: Int, scrolled: Float, steps: Int) {
        settling = true
        try {
            withContext(NonCancellable) {
                pager.animateScrollBy(steps * currentStep - scrolled, CalinoMotion.gestureReturn())
                if (steps != 0) currentOnRebase(currentFirstDayOf(startPage).plusDays(steps.toLong()), startPage)
                pager.scrollToPage(startPage)
            }
        } finally {
            currentOnSlidingChange(false)
            settling = false
        }
    }

    fun stepBy(steps: Int) {
        if (settling || pager.isScrollInProgress) return
        val startPage = pager.currentPage
        currentOnSlidingChange(true)
        scope.launch { settle(startPage, 0f, steps) }
    }

    val previousDayActionLabel = stringResource(R.string.cal_previous_day)
    val nextDayActionLabel = stringResource(R.string.cal_next_day)
    Box(
        modifier
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(previousDayActionLabel) { stepBy(-1); true },
                    CustomAccessibilityAction(nextDayActionLabel) { stepBy(1); true },
                )
            }
            .pointerInput(dayCount) {
                var startPage = 0
                var scrolled = 0f
                val velocity = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        startPage = pager.currentPage
                        scrolled = 0f
                        velocity.resetTracking()
                        currentOnSlidingChange(true)
                    },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        velocity.addPosition(change.uptimeMillis, change.position)
                        // Never more than one window either way, matching the
                        // cap on where a release can land.
                        val limit = currentStep * dayCount
                        val wanted = (scrolled - dx).coerceIn(-limit, limit) - scrolled
                        scrolled += pager.dispatchRawDelta(wanted)
                    },
                    onDragEnd = {
                        val steps = rangeHeaderDaySteps(scrolled, -velocity.calculateVelocity().x, currentStep, dayCount)
                        val from = scrolled
                        scope.launch { settle(startPage, from, steps) }
                    },
                    onDragCancel = {
                        val steps = rangeHeaderDaySteps(scrolled, 0f, currentStep, dayCount)
                        val from = scrolled
                        scope.launch { settle(startPage, from, steps) }
                    },
                )
            },
    )
}

private data class RangeDragSession(
    val card: TimelineCardBounds,
    val offset: Offset = Offset.Zero,
    val pointer: Offset,
    val scrollAtLift: Int,
    /** Lifted by its end edge: the drag changes the length, not the start. */
    val resize: Boolean = false,
)

/**
 * A card being stretched by its end edge.
 *
 * The card itself follows the finger exactly -- its start stays pinned to the
 * rail, scrolling with it, and its end is wherever the finger is. Behind it a
 * faint copy shows the quarter-hour length a release would commit, and the
 * gutter names that end time, the way a move names its start.
 */
@Composable
private fun RangeResizePreview(
    session: RangeDragSession,
    durationMinutes: Int,
    hostOrigin: Offset,
    scrollDelta: Int,
    hourHeightPx: Float,
) {
    val density = LocalDensity.current
    val timeFormat = LocalTimeFormat
    val locale = LocalCalinoLocale
    val preferences = LocalCalinoPreferences.current
    val rect = session.card.rootRect
    val event = session.card.event
    val start = event.start!!
    val startMinute = start.hour * 60 + start.minute
    val inset = with(density) { RangeCardBottomInset.toPx() }
    val minHeight = with(density) { RangeCardMinHeight.toPx() }
    fun cardHeight(minutes: Int) = (minutes / 60f * hourHeightPx - inset).coerceAtLeast(minHeight)
    val top = rect.top - hostOrigin.y - scrollDelta
    val left = rect.left - hostOrigin.x
    val liveHeight = (rect.height + session.offset.y + scrollDelta)
        .coerceIn(cardHeight(RangeMinResizeMinutes), cardHeight(24 * 60 - startMinute))
    val snappedHeight = cardHeight(durationMinutes)
    val end = start.plusMinutes(durationMinutes.toLong())
    val endLabel = timeFormat.format(end.toLocalTime(), locale)
    val resizePreviewDescription = stringResource(R.string.cal_resize_preview, endLabel)
    val resizeEndDescription = stringResource(R.string.cal_resize_end_time, endLabel)
    val width = with(density) { rect.width.toDp() }
    TimelineEventCard(
        modifier = Modifier
            .width(width)
            .height(with(density) { snappedHeight.toDp() })
            .graphicsLayer {
                translationX = left
                translationY = top
                alpha = .34f
            }
            .semantics { contentDescription = resizePreviewDescription },
        event = event,
        showMetadata = session.card.showMetadata,
        timeFormat = timeFormat,
        preferences = preferences,
        colors = CalinoColors,
        hideAccentRail = true,
    )
    Text(
        text = endLabel,
        modifier = Modifier
            .width(CalinoSpacing.RailGutter - 6.dp)
            .graphicsLayer {
                translationX = with(density) { 3.dp.toPx() }
                translationY = top + snappedHeight + inset - with(density) { 8.dp.toPx() }
            }
            .clip(RoundedCornerShape(7.dp))
            .background(CalinoColors.Canvas.copy(alpha = .94f))
            .border(1.dp, CalinoColors.Accent.copy(alpha = .7f), RoundedCornerShape(7.dp))
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .semantics { contentDescription = resizeEndDescription },
        color = CalinoColors.Accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
    TimelineEventCard(
        modifier = Modifier
            .width(width)
            .height(with(density) { liveHeight.toDp() })
            .graphicsLayer {
                translationX = left
                translationY = top
            },
        event = event,
        showMetadata = session.card.showMetadata,
        timeFormat = timeFormat,
        preferences = preferences,
        colors = CalinoColors,
        lifted = true,
        liftFromTop = true,
        hideAccentRail = true,
    ) {
        // The edge being held, so the card says which end it is carrying.
        Box(Modifier.fillMaxSize().padding(bottom = 3.dp), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.width(18.dp).height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CalinoColors.Accent.copy(alpha = .8f)),
            )
        }
    }
}

/** How far up from a card's end a lift takes the edge instead of the card. */
private val RangeResizeEdge = 14.dp

/** Mirrors the rail's card geometry: a 4dp gap below each card, 24dp minimum. */
private val RangeCardBottomInset = 4.dp
private val RangeCardMinHeight = 24.dp

/** Lanes shown at rest before the all-day band's overflow row takes over. */
private const val RangeBandLaneLimit = 2

private val RangeEdgeTurnZone = 52.dp
private const val RangeEdgeTurnDelayMillis = 420L
private val RangeAutoScrollEdge = 64.dp
private val RangeAutoScrollStep = 10.dp
private const val RangeAutoScrollFrameMillis = 16L

private fun <T> Modifier.rangeTimelineLiftDrag(
    hitTest: (Offset) -> T?,
    onLift: (T, Offset) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset, Offset) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
): Modifier = composed {
    val currentHitTest by rememberUpdatedState(hitTest)
    val currentOnLift by rememberUpdatedState(onLift)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val haptics = LocalHapticFeedback.current
    var gestureGeneration by remember { mutableIntStateOf(0) }
    pointerInput(gestureGeneration) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pointerId = down.id
            val startedAt = android.os.SystemClock.uptimeMillis()
            val liftDelay = minOf(viewConfiguration.longPressTimeoutMillis.toLong(), 220L)
            var lifted = false
            var dragging = false
            var finished = false
            try {
                while (!finished) {
                    val remaining = liftDelay - (android.os.SystemClock.uptimeMillis() - startedAt)
                    // An event can win the timeout race at the hold boundary.
                    // Lift before awaiting another event once the delay elapsed.
                    if (!lifted && remaining <= 0) {
                        val card = currentHitTest(down.position) ?: break
                        lifted = true
                        currentOnLift(card, down.position)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
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
                    if (event.changes.count { it.pressed } > 1) {
                        if (lifted) currentOnCancel()
                        finished = true
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    // Input may be delivered before an overdue timer gets CPU time.
                    // A first move after a completed hold still belongs to this drag.
                    if (!lifted && change.pressed && android.os.SystemClock.uptimeMillis() - startedAt >= liftDelay) {
                        val card = currentHitTest(down.position) ?: break
                        lifted = true
                        currentOnLift(card, down.position)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (!change.pressed) {
                        if (lifted) {
                            // Compose marks the synthetic up from ACTION_CANCEL consumed.
                            if (change.isConsumed) {
                                currentOnCancel()
                                // Android's synthetic cancel does not replace currentEvent.
                                // Restart the observer so awaitAllPointersUp cannot swallow
                                // the following stream while waiting on the old pressed event.
                                gestureGeneration++
                            } else currentOnRelease()
                        }
                        finished = true
                    } else if (!lifted && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        break
                    } else if (lifted && !dragging &&
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    ) {
                        dragging = true
                        currentOnDragStart()
                        change.consume()
                        currentOnDrag(change.positionChangeIgnoreConsumed(), change.position)
                    } else if (dragging) {
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
 * Observes a press on a day column without claiming the pointer stream from
 * event cards, scrolling, or the horizontal pager. A stationary hold on
 * genuinely empty timeline space creates an event there; anything that moves,
 * lifts early, or lands on a card is left to whoever else wants it.
 */
private fun Modifier.rangeEmptyLongPress(
    isOccupied: (androidx.compose.ui.geometry.Offset) -> Boolean,
    onLongPress: (androidx.compose.ui.geometry.Offset) -> Unit,
): Modifier = composed {
    val currentIsOccupied by rememberUpdatedState(isOccupied)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val haptics = LocalHapticFeedback.current
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (currentIsOccupied(down.position)) return@awaitEachGesture
            val pointerId = down.id
            // The hold is only a hold while the finger neither travels nor
            // lifts, so the window is spent waiting for either -- a timeout
            // rather than a sleep, or a scroll that started here would still
            // spawn an editor once it stopped.
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
            }
            if (ended == null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongPress(down.position)
            }
        }
    }
}

private fun Modifier.rangePinch(
    onVerticalZoom: (Float) -> Unit,
    onHorizontalPinch: (Float) -> Unit,
): Modifier = composed {
    val currentOnVerticalZoom by rememberUpdatedState(onVerticalZoom)
    val currentOnHorizontalPinch by rememberUpdatedState(onHorizontalPinch)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var initialVector: Offset? = null
            var previousVerticalSpan: Float? = null
            var finalHorizontalScale = 1f
            var gestureAxis = 0 // 0 undecided, 1 horizontal, 2 vertical
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    val vector = pressed[0].position - pressed[1].position
                    val initial = initialVector
                    if (initial == null) {
                        initialVector = vector
                        previousVerticalSpan = kotlin.math.abs(vector.y).coerceAtLeast(1f)
                    } else {
                        val horizontalChange = kotlin.math.abs(kotlin.math.abs(vector.x) - kotlin.math.abs(initial.x))
                        val verticalChange = kotlin.math.abs(kotlin.math.abs(vector.y) - kotlin.math.abs(initial.y))
                        if (gestureAxis == 0 && maxOf(horizontalChange, verticalChange) > viewConfiguration.touchSlop) {
                            gestureAxis = if (horizontalChange > verticalChange) 1 else 2
                        }
                        if (gestureAxis == 1) {
                            finalHorizontalScale = kotlin.math.abs(vector.x) /
                                kotlin.math.abs(initial.x).coerceAtLeast(1f)
                        } else if (gestureAxis == 2) {
                            val span = kotlin.math.abs(vector.y).coerceAtLeast(1f)
                            previousVerticalSpan?.takeIf { it > 0f }?.let { currentOnVerticalZoom(span / it) }
                            previousVerticalSpan = span
                        }
                    }
                    pressed.forEach { it.consume() }
                }
            }
            if (gestureAxis == 1) currentOnHorizontalPinch(finalHorizontalScale)
        }
    }
}

@Composable
private fun RangeHourGutter(timelineScale: Float, modifier: Modifier = Modifier) {
    val timeFormat = LocalTimeFormat
    val locale = LocalCalinoLocale
    val secondary = calino.malinov.ski.state.LocalCalinoPreferences.current.secondaryZoneId
        ?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() }
        ?.takeIf { 62 * timelineScale >= 30f }
    val device = androidx.compose.runtime.remember { java.time.ZoneId.systemDefault() }
    // One label per hour serves the whole range; the offset between two zones
    // only moves on a DST night, and today's is the one most likely in view.
    val today = androidx.compose.runtime.remember { java.time.LocalDate.now() }
    Box(modifier.width(CalinoSpacing.RailGutter).height((62 * timelineScale * 24).dp)) {
        (0..23).forEach { hour ->
            Text(
                timeFormat.formatHour(hour, locale),
                Modifier.padding(start = 8.dp, top = (hour * 62 * timelineScale).dp),
                fontSize = 10.sp,
                color = CalinoColors.Ink3,
                maxLines = 1,
            )
            if (secondary != null) {
                Text(
                    calino.malinov.ski.util.CalinoZones.secondaryHour(today, hour, device, secondary, timeFormat, locale),
                    Modifier.padding(start = 8.dp, top = (hour * 62 * timelineScale + 12).dp),
                    fontSize = 9.sp,
                    color = CalinoColors.Ink3.copy(alpha = .6f),
                    maxLines = 1,
                )
            }
        }
    }
}

private sealed interface TaskDropDestination {
    data object Week : TaskDropDestination
    data class Day(val day: LocalDate, val time: java.time.LocalTime?) : TaskDropDestination
}
