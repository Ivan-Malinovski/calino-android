package calino.malinov.ski.ui.year

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.SplitPaneWidthDp
import calino.malinov.ski.state.tasksDueOn
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.MenuButton
import calino.malinov.ski.ui.components.calinoPressable
import calino.malinov.ski.ui.components.rememberMonthYearPicker
import calino.malinov.ski.ui.surfaces.AgendaDayBlock
import calino.malinov.ski.ui.surfaces.EventMenuAction
import calino.malinov.ski.ui.surfaces.TaskMenuAction
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.gridStart
import calino.malinov.ski.util.isoWeekNumber
import calino.malinov.ski.util.EventDateIndex
import calino.malinov.ski.util.leadingCells
import calino.malinov.ski.util.weekendColumns
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The year pager, addressed by tag the way the calendar and range pagers are. */
const val YearPagerTag = "year-pager"

/** A tile that has to be shorter than this scrolls the page instead of squeezing the grid. */
private val MinTileHeight = 112.dp
private val TileGap = 10.dp
private val ScreenGutter = 16.dp

/**
 * One year as twelve mini-months, shaded by how busy each day is.
 *
 * A phone tap on a tile opens that month through [onOpenMonth]. Where the
 * window is wide enough to split, the tiles stay put and a tap on a day
 * selects it instead, with its agenda in a pane beside the grid; the month
 * title then carries the "open this month" action.
 */
@Composable
fun YearScreen(
    events: List<CalEvent>,
    tasks: List<CalTask>,
    initialDate: LocalDate,
    modifier: Modifier = Modifier,
    onOpenMenu: () -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    onOpenMonth: (YearMonth, Rect) -> Unit,
    onEventClick: (LocalDate, CalEvent) -> Unit,
    onEventAction: (EventMenuAction, CalEvent) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
    onAddOn: (LocalDate) -> Unit,
    onMonthBounds: (YearMonth, Rect) -> Unit = { _, _ -> },
) {
    val today = LocalCalinoNow.current.today
    val preferences = LocalCalinoPreferences.current
    val weekStart = preferences.weekStart
    var selectedEpoch by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    // The pager is addressed relative to the year the page opened on; the base
    // survives recreation so a restored pager lands on the same page.
    val baseYear = rememberSaveable { initialDate.year }
    val pager = rememberPagerState(initialPage = pageForYear(baseYear, initialDate.year)) { YearPagerPageCount }
    val currentSelected by rememberUpdatedState(selected)
    val eventIndex = remember(events) { EventDateIndex.build(events) }

    // The date follows the pager only once it has come to rest, and a date
    // that arrives from outside (the picker, a return from the editor) moves
    // the pager only when it is showing a different year.
    LaunchedEffect(initialDate) {
        selectedEpoch = initialDate.toEpochDay()
        val page = pageForYear(baseYear, initialDate.year)
        if (page != pager.settledPage && !pager.isScrollInProgress) pager.scrollToPage(page)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }
            .distinctUntilChanged()
            .collect { (scrolling, page) ->
                if (scrolling) return@collect
                val year = yearForPage(baseYear, page)
                if (year != currentSelected.year) {
                    val next = clampDayInYear(currentSelected, year)
                    selectedEpoch = next.toEpochDay()
                    onDateChanged(next)
                }
            }
    }
    // The title names the year being headed for, as soon as a drag passes the
    // halfway point or a fling is released, not when the pager finally rests.
    val liveYear by remember(pager) { derivedStateOf { yearForPage(baseYear, pager.targetPage) } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun goToYear(year: Int) {
        scope.launch { pager.animateScrollToPage(pageForYear(baseYear, year).coerceIn(0, YearPagerPageCount - 1)) }
    }
    val openYearPicker = rememberMonthYearPicker(initial = { currentSelected }) { month ->
        val next = month.atDay(currentSelected.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
        selectedEpoch = next.toEpochDay()
        onDateChanged(next)
        scope.launch { pager.scrollToPage(pageForYear(baseYear, next.year).coerceIn(0, YearPagerPageCount - 1)) }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        val shape = yearGridShape(maxWidth.value.toInt(), maxHeight.value.toInt())
        val landscape = maxWidth > maxHeight
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                YearHeader(
                    year = liveYear,
                    legendInHeader = landscape && !shape.showPane,
                    onOpenMenu = onOpenMenu,
                    onPrevious = { goToYear(liveYear - 1) },
                    onNext = { goToYear(liveYear + 1) },
                    onTitleClick = openYearPicker,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = ScreenGutter).background(CalinoColors.Line))
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag(YearPagerTag),
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    YearPage(
                        year = yearForPage(baseYear, page),
                        eventIndex = eventIndex,
                        shape = shape,
                        weekStart = weekStart,
                        today = today,
                        selected = selected.takeIf { shape.showPane },
                        showLegend = !(landscape && !shape.showPane),
                        onOpenMonth = onOpenMonth,
                        onMonthBounds = onMonthBounds,
                        onSelectDay = { day ->
                            selectedEpoch = day.toEpochDay()
                            onDateChanged(day)
                        },
                    )
                }
            }
            if (shape.showPane) {
                YearDayPane(
                    day = selected,
                    eventIndex = eventIndex,
                    tasks = tasks,
                    onEventClick = onEventClick,
                    onEventAction = onEventAction,
                    onTaskClick = onTaskClick,
                    onTaskAction = onTaskAction,
                    onTaskDone = onTaskDone,
                    onAdd = { onAddOn(selected) },
                )
            }
        }
    }
}

@Composable
private fun YearHeader(
    year: Int,
    legendInHeader: Boolean,
    onOpenMenu: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit,
) {
    val slide = with(LocalDensity.current) { 10.dp.roundToPx() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuButton(onClick = onOpenMenu)
        YearStepButton(CalinoIcons.ChevronLeft, "Previous year", onPrevious)
        AnimatedContent(
            targetState = year,
            transitionSpec = {
                val direction = targetState.compareTo(initialState).coerceIn(-1, 1)
                ((fadeIn(tween(170)) + slideInHorizontally(tween(170)) { direction * slide }) togetherWith
                    (fadeOut(tween(110)) + slideOutHorizontally(tween(110)) { -direction * slide / 2 })) using
                    SizeTransform(clip = false) { _, _ -> tween(170) }
            },
            modifier = Modifier
                .clickable(interactionSource = null, indication = null, onClick = onTitleClick)
                .semantics { contentDescription = "Choose year"; role = Role.Button }
                .padding(horizontal = 6.dp),
            label = "year title",
        ) { shown ->
            Text(
                shown.toString(),
                style = CalinoTypography.displayMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("year-title"),
            )
        }
        YearStepButton(CalinoIcons.ChevronRight, "Next year", onNext)
        Spacer(Modifier.weight(1f))
        if (legendInHeader) YearLegend()
    }
}

@Composable
private fun YearStepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            .calinoPressable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = CalinoColors.Ink2) }
}

/** "Fewer", the five heat swatches, "More events". */
@Composable
private fun YearLegend(modifier: Modifier = Modifier) {
    val isDark = CalinoColors.isDark
    val accent = CalinoColors.Accent
    val hairline = CalinoColors.Line
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Fewer to more events, five shades" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Fewer", style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
        repeat(YearHeatLevels) { level ->
            Box(
                Modifier.size(14.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (level == 0) hairline else accent.copy(alpha = yearHeatAlpha(level, isDark))),
            )
        }
        Text("More events", style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
    }
}

@Composable
private fun YearPage(
    year: Int,
    eventIndex: EventDateIndex,
    shape: YearGridShape,
    weekStart: CalinoWeekStart,
    today: LocalDate,
    selected: LocalDate?,
    showLegend: Boolean,
    onOpenMonth: (YearMonth, Rect) -> Unit,
    onMonthBounds: (YearMonth, Rect) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
) {
    // Off the main thread: a year is 365 index lookups, and the pager composes
    // the neighbouring years ahead of a swipe. The tiles draw quiet until the
    // counts arrive, then the shading fades in.
    val counts by produceState<IntArray?>(null, year, eventIndex) {
        value = withContext(Dispatchers.Default) { yearDayCounts(eventIndex, year) }
    }
    val reveal by animateFloatAsState(
        targetValue = if (counts != null) 1f else 0f,
        animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
        label = "year heat reveal",
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val legendHeight = if (showLegend) 40.dp else 0.dp
        val bottomClearance = if (landscape) 72.dp else CalinoSpacing.PillClearance
        val tileWidth = (maxWidth - ScreenGutter * 2 - TileGap * (shape.columns - 1)) / shape.columns
        val fitHeight = (maxHeight - legendHeight - bottomClearance - TileGap * (shape.rows + 1)) / shape.rows
        val tileHeight = fitHeight.coerceAtLeast(MinTileHeight)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenGutter)
                .padding(top = TileGap, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            repeat(shape.rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    repeat(shape.columns) { column ->
                        val month = YearMonth.of(year, row * shape.columns + column + 1)
                        YearMonthTile(
                            month = month,
                            counts = { counts },
                            reveal = { reveal },
                            weekStart = weekStart,
                            today = today,
                            selected = selected,
                            splitDays = shape.showPane,
                            onOpen = { rect -> onOpenMonth(month, rect) },
                            onSelectDay = onSelectDay,
                            onBounds = { rect -> onMonthBounds(month, rect) },
                            modifier = Modifier.width(tileWidth).height(tileHeight),
                        )
                    }
                }
            }
            if (showLegend) YearLegend(Modifier.padding(top = 2.dp).align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun YearMonthTile(
    month: YearMonth,
    counts: () -> IntArray?,
    reveal: () -> Float,
    weekStart: CalinoWeekStart,
    today: LocalDate,
    selected: LocalDate?,
    splitDays: Boolean,
    onOpen: (Rect) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = CalinoColors.isDark
    val accent = CalinoColors.Accent
    val onAccent = CalinoColors.OnAccent
    val ink = CalinoColors.Ink
    val ink2 = CalinoColors.Ink2
    val ink3 = CalinoColors.Ink3
    val isCurrentMonth = YearMonth.from(today) == month
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val label = yearTileLabel(month, yearMonthTotal(counts() ?: IntArray(0), month))
    val monthName = month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())
    val titleColor = if (isCurrentMonth) CalinoColors.Accent else CalinoColors.Ink
    val tileModifier = modifier
        .clip(RoundedCornerShape(YearTileCornerRadius))
        .background(if (isCurrentMonth) CalinoColors.AccentSoft else Color.Transparent)
        .onGloballyPositioned {
            bounds = it.boundsInRoot()
            onBounds(bounds)
        }
        .then(
            if (splitDays) {
                Modifier
            } else {
                Modifier.calinoPressable(onClick = { onOpen(bounds) })
                    .semantics(mergeDescendants = true) { contentDescription = label }
            },
        )
        .testTag("year-tile-${month.monthValue}")
    val measurer = rememberTextMeasurer()
    val weekNumbers = LocalCalinoPreferences.current.showWeekNumbers
    val gutterUnits = if (weekNumbers) WeekGutterUnits else 0f
    val leading = month.leadingCells(weekStart)
    val rows = (leading + month.lengthOfMonth() + 6) / 7
    val weekend = remember(weekStart) { weekendColumns(weekStart) }
    val length = month.lengthOfMonth()
    val firstDayIndex = month.atDay(1).dayOfYear - 1
    Column(tileModifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        // A six-column landscape tile is ~110dp wide; shrink the title with it
        // so the longest names ("September") stay whole.
        val tileWidthDp = with(LocalDensity.current) { bounds.width.toDp().value }
        val titleSize = if (tileWidthDp > 0f) (tileWidthDp * .17f).coerceIn(13f, 19f) else 19f
        Text(
            monthName,
            style = CalinoTypography.titleSmall.copy(fontSize = titleSize.sp),
            softWrap = false,
            color = titleColor,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                .then(
                    if (splitDays) {
                        Modifier.clickable(interactionSource = null, indication = null) { onOpen(bounds) }
                            .semantics { contentDescription = label; role = Role.Button }
                    } else {
                        Modifier
                    },
                ),
        )
        Box(
            Modifier.fillMaxSize()
                .then(
                    if (splitDays) {
                        Modifier.pointerInput(month, leading, gutterUnits) {
                            detectTapGestures { position ->
                                val cellW = size.width / (7f + gutterUnits)
                                val cellH = size.height / 6f
                                val column = ((position.x - gutterUnits * cellW) / cellW).toInt()
                                if (position.x < gutterUnits * cellW || column > 6) return@detectTapGestures
                                val index = (position.y / cellH).toInt().coerceIn(0, 5) * 7 + column - leading
                                if (index in 0 until length) onSelectDay(month.atDay(index + 1))
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .drawWithCache {
                    val cellW = size.width / (7f + gutterUnits)
                    val gutter = gutterUnits * cellW
                    val cellH = size.height / 6f
                    val inset = 1.dp.toPx()
                    val corner = minOf(cellW, cellH) * .3f
                    val fontSize = (minOf(cellW, cellH * 1.1f) / density * .48f).coerceIn(6f, 13f).sp
                    val regular = TextStyle(fontSize = fontSize)
                    val heavy = TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold)
                    val digits = digitLayouts(measurer, regular, length)
                    val todayDigits = digitLayouts(measurer, heavy, length)
                    val weekStyle = TextStyle(fontSize = (fontSize.value * .8f).sp)
                    val gridStart = month.gridStart(weekStart)
                    val weekLayouts = if (weekNumbers) {
                        List(rows) { measurer.measure(isoWeekNumber(gridStart.plusDays(it * 7L)).toString(), weekStyle) }
                    } else {
                        emptyList()
                    }
                    onDrawBehind {
                        val shown = counts()
                        val fade = reveal()
                        weekLayouts.forEachIndexed { row, layout ->
                            drawText(
                                layout,
                                color = ink3,
                                topLeft = Offset(
                                    (gutter - layout.size.width) / 2f,
                                    row * cellH + (cellH - layout.size.height) / 2f,
                                ),
                            )
                        }
                        for (day in 1..length) {
                            val cell = leading + day - 1
                            val column = cell % 7
                            val row = cell / 7
                            val topLeft = Offset(gutter + column * cellW + inset, row * cellH + inset)
                            val cellSize = Size(cellW - inset * 2, cellH - inset * 2)
                            val date = month.atDay(day)
                            val isToday = date == today
                            val count = shown?.getOrNull(firstDayIndex + day - 1) ?: 0
                            val alpha = yearHeatAlpha(yearHeatLevel(count), isDark) * fade
                            if (alpha > 0f) {
                                drawRoundRect(accent.copy(alpha = alpha), topLeft, cellSize, CornerRadius(corner))
                            }
                            if (isToday) {
                                drawRoundRect(accent, topLeft, cellSize, CornerRadius(corner))
                            }
                            if (date == selected) {
                                val stroke = 1.5.dp.toPx()
                                drawRoundRect(
                                    ink,
                                    topLeft + Offset(stroke / 2, stroke / 2),
                                    Size(cellSize.width - stroke, cellSize.height - stroke),
                                    CornerRadius(corner),
                                    style = Stroke(stroke),
                                )
                            }
                            val layout = (if (isToday) todayDigits else digits)[day - 1]
                            drawText(
                                layout,
                                color = if (isToday) onAccent else if (column in weekend) ink2 else ink,
                                topLeft = Offset(
                                    topLeft.x + (cellSize.width - layout.size.width) / 2f,
                                    topLeft.y + (cellSize.height - layout.size.height) / 2f,
                                ),
                            )
                        }
                    }
                },
        )
    }
}

/** The week-number rail beside a tile's grid, in day-cell widths. */
private const val WeekGutterUnits = .85f

private fun digitLayouts(measurer: TextMeasurer, style: TextStyle, length: Int): List<TextLayoutResult> =
    List(length) { measurer.measure((it + 1).toString(), style) }

/** The right-hand pane of the split layout: one fixed day's agenda. */
@Composable
private fun YearDayPane(
    day: LocalDate,
    eventIndex: EventDateIndex,
    tasks: List<CalTask>,
    onEventClick: (LocalDate, CalEvent) -> Unit,
    onEventAction: (EventMenuAction, CalEvent) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
    onAdd: () -> Unit,
) {
    val hideCompleted = LocalCalinoPreferences.current.hideCompletedTasks
    Row(Modifier.width(SplitPaneWidthDp.dp).fillMaxHeight().testTag("year-day-pane")) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(CalinoColors.Line))
        AnimatedContent(
            targetState = day,
            transitionSpec = {
                fadeIn(tween(CalinoMotion.SurfaceFadeMillis)) togetherWith fadeOut(tween(CalinoMotion.FadeThroughMillis))
            },
            modifier = Modifier.weight(1f).fillMaxHeight().background(CalinoColors.Side),
            label = "year day pane",
        ) { shownDay ->
            val dayEvents = remember(eventIndex, shownDay) { eventIndex.eventsOn(shownDay) }
            val dueTasks = remember(tasks, shownDay, hideCompleted) {
                tasksDueOn(tasks.filter { it.due == shownDay && !(hideCompleted && it.done) }, shownDay)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = CalinoSpacing.PillClearance)) {
                AgendaDayBlock(
                    day = shownDay,
                    events = dayEvents,
                    tasks = dueTasks,
                    onEventClick = onEventClick,
                    onEventAction = onEventAction,
                    onEventDrop = { _, _ -> },
                    onTaskClick = onTaskClick,
                    onTaskAction = onTaskAction,
                    onTaskDrop = { _, _ -> },
                    onTaskDone = onTaskDone,
                    onAdd = onAdd,
                )
            }
        }
    }
}

/** A tile's corner; the month grows out of a rectangle rounded like this. */
internal val YearTileCornerRadius = 14.dp

/** The year stays fully visible this long while its month grows, then fades over [YearGrowFadeMillis]. */
internal const val YearGrowHoldMillis = 140
internal const val YearGrowFadeMillis = 180
