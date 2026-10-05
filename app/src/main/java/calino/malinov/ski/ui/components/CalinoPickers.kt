package calino.malinov.ski.ui.components

import calino.malinov.ski.util.isoWeekNumber
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.weekdayLetters
import java.time.YearMonth
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.R
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.LocalCalinoLocale
import calino.malinov.ski.util.localizedDateFormatter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

private val WheelRow = 46.dp
private const val WheelVisibleRows = 5

/** Enough laps that nobody scrolls off either end of a looping wheel. */
private const val WheelLaps = 2_000

/**
 * Opens Calino's time picker and returns the action that shows it. The
 * picker follows Calino's 12/24h setting; [title] names what is being set.
 */
@Composable
fun rememberTimePicker(
    initial: () -> LocalTime?,
    title: String? = null,
    onPicked: (LocalTime) -> Unit,
): () -> Unit {
    val pickerTitle = title ?: stringResource(R.string.cal_time)
    return rememberPickerDialog(
        initial = { initial() ?: LocalTime.of(LocalTime.now().hour, 0).plusHours(1) },
        onPicked = onPicked,
    ) { seed, cancel, done -> TimePickerCard(pickerTitle, seed, cancel, done) }
}

/**
 * Opens Calino's date picker -- a swipeable month in the same card as the
 * time wheel -- and returns the action that shows it.
 */
@Composable
fun rememberDatePicker(initial: () -> LocalDate, onPicked: (LocalDate) -> Unit): () -> Unit =
    rememberPickerDialog(initial, onPicked) { seed, cancel, done -> DatePickerCard(seed, cancel, done) }

/** Opens the date picker on its month/year wheels and returns the chosen month. */
@Composable
fun rememberMonthYearPicker(initial: () -> LocalDate, onPicked: (YearMonth) -> Unit): () -> Unit =
    rememberPickerDialog(initial, { onPicked(YearMonth.from(it)) }) { seed, cancel, done ->
        DatePickerCard(seed, cancel, done, startOnMonthYear = true)
    }

/**
 * The lifecycle both pickers share: a dialog window that stays mounted until
 * its card has finished leaving, seeded fresh each time it opens.
 */
@Composable
private fun <T : Any> rememberPickerDialog(
    initial: () -> T,
    onPicked: (T) -> Unit,
    card: @Composable (seed: T, cancel: () -> Unit, done: (T) -> Unit) -> Unit,
): () -> Unit {
    // Seeded when opened; null while closed.
    var seed by remember { mutableStateOf<T?>(null) }
    val visible = remember { MutableTransitionState(false) }
    val currentInitial by rememberUpdatedState(initial)
    val currentOnPicked by rememberUpdatedState(onPicked)

    val current = seed
    if (current != null && (visible.targetState || !visible.isIdle || visible.currentState)) {
        PickerDialog(visible, onDismiss = { visible.targetState = false }) {
            key(current) {
                card(
                    current,
                    { visible.targetState = false },
                    { picked -> currentOnPicked(picked); visible.targetState = false },
                )
            }
        }
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) seed = null
    }
    return {
        seed = currentInitial()
        visible.targetState = true
    }
}

@Composable
private fun PickerDialog(
    visible: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The window's own dim cannot fade with the card; Calino's scrim can.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) { window?.setDimAmount(0f) }

        Box(Modifier.fillMaxSize()) {
            CalinoScrim(visible = visible.targetState, modifier = Modifier.fillMaxSize(), onDismiss = onDismiss)
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(spring(dampingRatio = .86f, stiffness = 420f)) { it / 3 } +
                    fadeIn(tween(CalinoMotion.ContentEnterMillis)),
                exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 520f)) { it / 4 } +
                    fadeOut(tween(CalinoMotion.ContentExitMillis)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) { content() }
        }
    }
}

/** The card both pickers sit in, titled, with Cancel and Done beneath. */
@Composable
private fun PickerCard(
    title: String,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    titleAside: String? = null,
    onTitleClick: (() -> Unit)? = null,
    titleOpen: Boolean = false,
    headerEnd: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val choosingMonthLabel = stringResource(R.string.cal_choosing_month)
    val showingDaysLabel = stringResource(R.string.cal_showing_days)
    Column(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(CalinoShapes.Sheet), clip = false)
            .clip(RoundedCornerShape(CalinoShapes.Sheet))
            .background(CalinoColors.Panel)
            // A tap on the card's own padding must not reach the scrim behind it.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = title }
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp),
    ) {
        val density = LocalDensity.current
        val dismissDistance = with(density) { 72.dp.toPx() }
        var headerDrag by remember { mutableStateOf(Offset.Zero) }
        Row(
            modifier = Modifier.pointerInput(onCancel, dismissDistance) {
                detectDragGestures(
                    onDragStart = { headerDrag = Offset.Zero },
                    onDragEnd = {
                        if (headerDrag.y > dismissDistance && headerDrag.y > abs(headerDrag.x)) onCancel()
                        headerDrag = Offset.Zero
                    },
                    onDragCancel = { headerDrag = Offset.Zero },
                    onDrag = { change, amount ->
                        headerDrag += amount
                        if (headerDrag.y > 0f && headerDrag.y > abs(headerDrag.x)) change.consume()
                    },
                )
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(CalinoShapes.Row))
                    .then(if (onTitleClick != null) Modifier.calinoPressable(onClick = onTitleClick) else Modifier)
                    .semantics { if (onTitleClick != null) stateDescription = if (titleOpen) choosingMonthLabel else showingDaysLabel },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = CalinoTypography.titleLarge,
                    color = CalinoColors.Ink,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Quieter, beside the title, as the calendar's own month heading has it.
                if (titleAside != null) {
                    Text(
                        titleAside,
                        style = CalinoTypography.titleSmall,
                        color = CalinoColors.Ink3,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                    )
                }
                if (onTitleClick != null) {
                    val turn by animateFloatAsState(if (titleOpen) 180f else 0f, CalinoMotion.standardSpatial(), label = "title chevron")
                    androidx.compose.material3.Icon(
                        CalinoIcons.ChevronDown,
                        contentDescription = null,
                        tint = CalinoColors.Ink3,
                        modifier = Modifier.padding(start = 4.dp).size(18.dp).graphicsLayer { rotationZ = turn },
                    )
                }
            }
            headerEnd()
        }
        Spacer(Modifier.height(12.dp))
        content()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PromptButton(stringResource(R.string.cal_cancel), filled = false, enabled = true, onClick = onCancel, modifier = Modifier.weight(1f))
            PromptButton(stringResource(R.string.cal_done), filled = true, enabled = true, onClick = onDone, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TimePickerCard(title: String, seed: LocalTime, onCancel: () -> Unit, onDone: (LocalTime) -> Unit) {
    val twentyFour = LocalTimeFormat == CalinoTimeFormat.TwentyFourHour
    val hourLabel = stringResource(R.string.cal_hour)
    val minuteLabel = stringResource(R.string.cal_minute)
    val amPmLabel = stringResource(R.string.cal_am_pm)
    val amLabel = stringResource(R.string.cal_am)
    val pmLabel = stringResource(R.string.cal_pm)
    var hour by remember { mutableIntStateOf(seed.hour) }
    var minute by remember { mutableIntStateOf(seed.minute) }

    PickerCard(title, onCancel, onDone = { onDone(LocalTime.of(hour, minute)) }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The band the chosen row settles into, shared by every wheel.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WheelRow)
                    .clip(RoundedCornerShape(CalinoShapes.Row))
                    .background(CalinoColors.Ink.copy(alpha = .05f)),
            )
            // Each wheel owns its whole side of the card, so a swipe anywhere
            // on the left turns the hours and anywhere on the right the minutes.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (twentyFour) {
                    TimeWheel(
                        count = 24,
                        selected = hour,
                        label = { "%02d".format(it) },
                        description = hourLabel,
                        modifier = Modifier.weight(1f),
                        hug = Alignment.End,
                        onSelected = { hour = it },
                    )
                } else {
                    TimeWheel(
                        count = 12,
                        selected = (hour + 11) % 12,
                        label = { (it + 1).toString() },
                        description = hourLabel,
                        modifier = Modifier.weight(1f),
                        hug = Alignment.End,
                        onSelected = { hour = (it + 1) % 12 + if (hour >= 12) 12 else 0 },
                    )
                }
                Text(
                    ":",
                    style = CalinoTypography.headlineMedium,
                    color = CalinoColors.Ink,
                    modifier = Modifier.padding(horizontal = 2.dp).padding(bottom = 3.dp),
                )
                TimeWheel(
                    count = 60,
                    selected = minute,
                    label = { "%02d".format(it) },
                    description = minuteLabel,
                    modifier = Modifier.weight(if (twentyFour) 1f else .4f),
                    hug = Alignment.Start,
                    onSelected = { minute = it },
                )
                if (!twentyFour) {
                    TimeWheel(
                        count = 2,
                        selected = if (hour >= 12) 1 else 0,
                        label = { if (it == 0) amLabel else pmLabel },
                        description = amPmLabel,
                        modifier = Modifier.weight(.6f),
                        hug = Alignment.Start,
                        loops = false,
                        onSelected = { hour = hour % 12 + it * 12 },
                    )
                }
            }
        }
    }
}

/**
 * One snapping column of values. Rows fade, shrink and tilt away from the
 * centre band, all derived per frame from the scroll position so they move
 * with the finger rather than catching up to it.
 */
@Composable
private fun TimeWheel(
    count: Int,
    selected: Int,
    label: (Int) -> String,
    description: String,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
    loops: Boolean = true,
    /** Which edge of its lane the values hug: the one facing its neighbour. */
    hug: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val originX = when (hug) {
        Alignment.Start -> 0f
        Alignment.End -> 1f
        else -> .5f
    }
    val total = if (loops) count * WheelLaps else count
    val startIndex = if (loops) count * (WheelLaps / 2) + selected else selected
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val rowPx = with(LocalDensity.current) { WheelRow.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnSelected by rememberUpdatedState(onSelected)
    val currentSelected by rememberUpdatedState(selected)
    val nextActionLabel = stringResource(R.string.cal_next)
    val previousActionLabel = stringResource(R.string.cal_previous)

    val centred by remember { derivedStateOf { state.centredIndex(rowPx) } }
    LaunchedEffect(state) {
        snapshotFlow { centred }.distinctUntilChanged().collect {
            if (it % count != currentSelected) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                currentOnSelected(it % count)
            }
        }
    }
    // Another wheel can move this one (AM/PM following the hour, say).
    LaunchedEffect(selected) {
        if (centred % count != selected && !state.isScrollInProgress) {
            val target = centred - centred % count + selected
            state.animateScrollToItem(target.coerceIn(0, total - 1))
        }
    }

    val step: (Int) -> Unit = { delta ->
        scope.launch { state.animateScrollToItem((centred + delta).coerceIn(0, total - 1)) }
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        contentPadding = PaddingValues(vertical = WheelRow * (WheelVisibleRows / 2)),
        horizontalAlignment = hug,
        modifier = Modifier
            .then(modifier)
            .height(WheelRow * WheelVisibleRows)
            .semantics {
                contentDescription = description
                stateDescription = label(selected)
                customActions = listOf(
                    CustomAccessibilityAction(nextActionLabel) { step(1); true },
                    CustomAccessibilityAction(previousActionLabel) { step(-1); true },
                )
            },
    ) {
        items(total) { index ->
            val value = index % count
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WheelRow)
                    .graphicsLayer {
                        val distance = state.rowDistance(index, rowPx)
                        val t = (abs(distance) / (WheelVisibleRows / 2 + .5f)).coerceIn(0f, 1f)
                        alpha = lerp(1f, .12f, t)
                        scaleX = lerp(1f, .78f, t)
                        scaleY = scaleX
                        rotationX = (distance * -18f).coerceIn(-60f, 60f)
                        // Shrink toward the hugged edge, so values stay beside their neighbour.
                        transformOrigin = TransformOrigin(originX, .5f)
                        cameraDistance = 12f * density
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) { scope.launch { state.animateScrollToItem(index) } },
                contentAlignment = when (hug) {
                    Alignment.Start -> Alignment.CenterStart
                    Alignment.End -> Alignment.CenterEnd
                    else -> Alignment.Center
                },
            ) {
                Text(
                    label(value),
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = CalinoTypography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    color = CalinoColors.Ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The index nearest the centre band, from where the scroll sits right now. */
private fun LazyListState.centredIndex(rowPx: Float): Int =
    firstVisibleItemIndex + if (firstVisibleItemScrollOffset > rowPx / 2) 1 else 0

/** How many rows [index] sits from the centre band, fractional mid-scroll. */
private fun LazyListState.rowDistance(index: Int, rowPx: Float): Float =
    index - firstVisibleItemIndex - firstVisibleItemScrollOffset / rowPx

/** Months either side of the seed a person can swipe to. */
private const val DatePagerMonths = 1_200
private val DayCell = 44.dp
private val WeekGutter = 24.dp

@Composable
private fun DatePickerCard(seed: LocalDate, onCancel: () -> Unit, onDone: (LocalDate) -> Unit, startOnMonthYear: Boolean = false) {
    val weekStart = LocalCalinoPreferences.current.weekStart
    val weekNumbers = LocalCalinoPreferences.current.showWeekNumbers
    val locale = LocalCalinoLocale
    var picked by remember { mutableStateOf(seed) }
    val seedMonth = remember { YearMonth.from(seed) }
    val pager = rememberPagerState(initialPage = DatePagerMonths) { DatePagerMonths * 2 }
    val scope = rememberCoroutineScope()
    val monthAt = { page: Int -> seedMonth.plusMonths((page - DatePagerMonths).toLong()) }
    // The title follows the swipe: whichever month is more than half on screen.
    val shown = monthAt(pager.currentPage)
    var wheelMonth by remember { mutableStateOf(seedMonth) }
    val today = LocalDate.now()
    var choosingMonth by remember { mutableStateOf(startOnMonthYear) }
    val monthTitleFormat = localizedDateFormatter("MMMM")
    val previousMonth = stringResource(R.string.cal_previous_month)
    val nextMonth = stringResource(R.string.cal_next_month)

    PickerCard(
        title = (if (startOnMonthYear) wheelMonth else shown).format(monthTitleFormat),
        titleAside = (if (startOnMonthYear) wheelMonth else shown).year.toString(),
        onTitleClick = if (startOnMonthYear) null else { { choosingMonth = !choosingMonth } },
        titleOpen = choosingMonth,
        onCancel = onCancel,
        // With the wheels up no day has been chosen in the new month yet, so
        // Done first lands on that month's days.
        onDone = {
            if (startOnMonthYear) onDone(wheelMonth.atDay(seed.dayOfMonth.coerceAtMost(wheelMonth.lengthOfMonth())))
            else if (choosingMonth) choosingMonth = false else onDone(picked)
        },
        headerEnd = {
            if (!choosingMonth) MonthStep(CalinoIcons.ChevronLeft, previousMonth) {
                scope.launch { pager.animateScrollToPage(pager.currentPage - 1, animationSpec = CalinoMotion.standardSpatial()) }
            }
            if (!choosingMonth) MonthStep(CalinoIcons.ChevronRight, nextMonth) {
                scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = CalinoMotion.standardSpatial()) }
            }
        },
    ) {
        // Both faces keep the grid's height, so the card never jumps between them.
        AnimatedContent(
            targetState = choosingMonth,
            transitionSpec = {
                (fadeIn(tween(CalinoMotion.ContentEnterMillis, delayMillis = CalinoMotion.FadeThroughMillis / 2)) +
                    scaleIn(tween(CalinoMotion.ContentEnterMillis), initialScale = .96f)) togetherWith
                    fadeOut(tween(CalinoMotion.FadeThroughMillis))
            },
            label = "date picker face",
        ) { months ->
            if (months) {
                MonthYearWheels(
                    month = if (startOnMonthYear) wheelMonth else shown,
                    onMonth = { target ->
                        wheelMonth = target
                        val page = DatePagerMonths + (target.year - seedMonth.year) * 12 + (target.monthValue - seedMonth.monthValue)
                        scope.launch { pager.scrollToPage(page.coerceIn(0, pager.pageCount - 1)) }
                    },
                )
            } else {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        if (weekNumbers) Spacer(Modifier.width(WeekGutter))
                        weekdayLetters(weekStart, locale).forEach {
                            Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                                Text(it, style = CalinoTypography.labelMedium, color = CalinoColors.Ink3)
                            }
                        }
                    }
                    HorizontalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = 28.dp, verticalAlignment = Alignment.Top) { page ->
                        MonthGrid(monthAt(page), weekStart, weekNumbers, picked, today) { picked = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStep(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .calinoPressable(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = CalinoColors.Ink2, modifier = Modifier.size(20.dp))
    }
}

/** Six rows always, so the card keeps its height from month to month. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    weekStart: CalinoWeekStart,
    weekNumbers: Boolean,
    picked: LocalDate?,
    today: LocalDate,
    marked: Set<LocalDate> = emptySet(),
    pickable: (LocalDate) -> Boolean = { true },
    onPick: (LocalDate) -> Unit,
) {
    val cells = sidebarMonthCells(month, weekStart).let { it + List(42 - it.size) { null } }
    Column(Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (weekNumbers) {
                    val rowStart = week.withIndex().firstOrNull { it.value != null }
                        ?.let { (i, d) -> d!!.minusDays(i.toLong()) }
                    Box(Modifier.width(WeekGutter), contentAlignment = Alignment.Center) {
                        if (rowStart != null) {
                            Text(isoWeekNumber(rowStart).toString(), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
                        }
                    }
                }
                week.forEach { date ->
                    Box(Modifier.weight(1f).height(DayCell), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, selected = date == picked, today = date == today, marked = date in marked, enabled = pickable(date)) { onPick(date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, today: Boolean, marked: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val dateFormat = localizedDateFormatter("EEEE, MMMM d, yyyy")
    val fill by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = .7f, stiffness = 600f),
        label = "picked day fill",
    )
    val ink by animateColorAsState(
        when {
            selected -> CalinoColors.OnSelection
            today -> CalinoColors.Accent
            enabled -> CalinoColors.Ink
            else -> CalinoColors.Ink3
        },
        tween(CalinoMotion.FadeThroughMillis),
        label = "day ink",
    )
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.calinoPressable(onClick = onClick) else Modifier)
            .semantics {
                contentDescription = date.format(dateFormat)
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (today && !selected) {
            Box(Modifier.matchParentSize().padding(2.dp).border(1.dp, CalinoColors.Accent.copy(alpha = .5f), CircleShape))
        }
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { scaleX = fill; scaleY = fill; alpha = fill.coerceIn(0f, 1f) }
                .clip(CircleShape)
                .background(CalinoColors.SelectionFill)
                .border(1.dp, CalinoColors.SelectionBorder, CircleShape),
        )
        Text(
            date.dayOfMonth.toString(),
            style = CalinoTypography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = ink,
        )
        if (marked) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (selected) CalinoColors.OnSelection else CalinoColors.Accent),
            )
        }
    }
}

/**
 * The date picker's month face without its dialog: a swipeable month pager
 * whose title opens the month/year wheels. [month] is controlled, so the
 * host can move it (the journal does as its list scrolls); a settled swipe
 * reports back through [onMonth]. [marked] days carry a dot, and only
 * [pickable] days respond to a tap.
 */
@Composable
fun CalinoMonthCalendar(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    picked: LocalDate? = null,
    marked: Set<LocalDate> = emptySet(),
    pickable: (LocalDate) -> Boolean = { true },
) {
    val weekStart = LocalCalinoPreferences.current.weekStart
    val weekNumbers = LocalCalinoPreferences.current.showWeekNumbers
    val locale = LocalCalinoLocale
    val baseMonth = remember { month }
    val pager = rememberPagerState(initialPage = DatePagerMonths) { DatePagerMonths * 2 }
    val scope = rememberCoroutineScope()
    val monthAt = { page: Int -> baseMonth.plusMonths((page - DatePagerMonths).toLong()) }
    val pageOf = { target: YearMonth ->
        (DatePagerMonths + (target.year - baseMonth.year) * 12 + (target.monthValue - baseMonth.monthValue))
            .coerceIn(0, pager.pageCount - 1)
    }
    val currentOnMonth by rememberUpdatedState(onMonth)
    val currentMonth by rememberUpdatedState(month)
    val shown = monthAt(pager.currentPage)
    val today = LocalDate.now()
    var choosingMonth by remember { mutableStateOf(false) }
    val monthTitleFormat = localizedDateFormatter("MMMM")
    val choosingMonthLabel = stringResource(R.string.cal_choosing_month)
    val showingDaysLabel = stringResource(R.string.cal_showing_days)
    val previousMonth = stringResource(R.string.cal_previous_month)
    val nextMonth = stringResource(R.string.cal_next_month)

    // Host -> pager: follow the month the host commits, unless we are already there.
    LaunchedEffect(month) {
        val target = pageOf(month)
        if (pager.currentPage != target && !pager.isScrollInProgress) {
            pager.animateScrollToPage(target, animationSpec = CalinoMotion.standardSpatial())
        }
    }
    // Pager -> host: report a month only once a swipe has settled on it.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> monthAt(page).let { if (it != currentMonth) currentOnMonth(it) } }
    }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(CalinoShapes.Row))
                    .calinoPressable(onClick = { choosingMonth = !choosingMonth })
                    .semantics { stateDescription = if (choosingMonth) choosingMonthLabel else showingDaysLabel },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(shown.format(monthTitleFormat), style = CalinoTypography.titleMedium, color = CalinoColors.Ink, maxLines = 1)
                Text(shown.year.toString(), style = CalinoTypography.titleSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                val turn by animateFloatAsState(if (choosingMonth) 180f else 0f, CalinoMotion.standardSpatial(), label = "month title chevron")
                androidx.compose.material3.Icon(
                    CalinoIcons.ChevronDown,
                    contentDescription = null,
                    tint = CalinoColors.Ink3,
                    modifier = Modifier.padding(start = 4.dp).size(18.dp).graphicsLayer { rotationZ = turn },
                )
            }
            if (!choosingMonth) MonthStep(CalinoIcons.ChevronLeft, previousMonth) {
                scope.launch { pager.animateScrollToPage(pager.currentPage - 1, animationSpec = CalinoMotion.standardSpatial()) }
            }
            if (!choosingMonth) MonthStep(CalinoIcons.ChevronRight, nextMonth) {
                scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = CalinoMotion.standardSpatial()) }
            }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = choosingMonth,
            transitionSpec = {
                (fadeIn(tween(CalinoMotion.ContentEnterMillis, delayMillis = CalinoMotion.FadeThroughMillis / 2)) +
                    scaleIn(tween(CalinoMotion.ContentEnterMillis), initialScale = .96f)) togetherWith
                    fadeOut(tween(CalinoMotion.FadeThroughMillis))
            },
            label = "month calendar face",
        ) { months ->
            if (months) {
                MonthYearWheels(month = shown, onMonth = { target -> scope.launch { pager.scrollToPage(pageOf(target)) } })
            } else {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        if (weekNumbers) Spacer(Modifier.width(WeekGutter))
                        weekdayLetters(weekStart, locale).forEach {
                            Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                                Text(it, style = CalinoTypography.labelMedium, color = CalinoColors.Ink3)
                            }
                        }
                    }
                    HorizontalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = 28.dp, verticalAlignment = Alignment.Top) { page ->
                        MonthGrid(monthAt(page), weekStart, weekNumbers, picked, today, marked, pickable, onPick)
                    }
                }
            }
        }
    }
}

private const val FirstYear = 1900
private const val LastYear = 2200

/**
 * Month and year as wheels, standing in for the grid at the grid's height.
 * The pager follows every tick, so the heading reads the month as it spins.
 */
@Composable
private fun MonthYearWheels(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val locale = LocalCalinoLocale
    val monthNames = remember(locale) { List(12) { java.time.Month.of(it + 1).getDisplayName(java.time.format.TextStyle.FULL, locale) } }
    val monthLabel = stringResource(R.string.cal_month)
    val yearLabel = stringResource(R.string.cal_year)
    val currentOnMonth by rememberUpdatedState(onMonth)
    var chosen by remember { mutableStateOf(month) }
    Box(Modifier.fillMaxWidth().height(28.dp + DayCell * 6), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(WheelRow)
                .clip(RoundedCornerShape(CalinoShapes.Row))
                .background(CalinoColors.Ink.copy(alpha = .05f)),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TimeWheel(
                count = 12,
                selected = chosen.monthValue - 1,
                label = { monthNames[it] },
                description = monthLabel,
                modifier = Modifier.weight(1f),
                hug = Alignment.End,
                onSelected = { chosen = chosen.withMonth(it + 1); currentOnMonth(chosen) },
            )
            TimeWheel(
                count = LastYear - FirstYear + 1,
                selected = chosen.year - FirstYear,
                label = { (FirstYear + it).toString() },
                description = yearLabel,
                loops = false,
                modifier = Modifier.weight(1f),
                hug = Alignment.Start,
                onSelected = { chosen = chosen.withYear(FirstYear + it); currentOnMonth(chosen) },
            )
        }
    }
}
