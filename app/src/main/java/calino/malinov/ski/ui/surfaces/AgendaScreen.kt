package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.ui.components.AbsentParentRow
import calino.malinov.ski.ui.components.taskNestIndent
import calino.malinov.ski.state.LocalTaskLookup
import calino.malinov.ski.state.TaskListRow
import calino.malinov.ski.state.nestWithinList
import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.LocalFoldPosture
import calino.malinov.ski.state.calinoLayoutSpec
import calino.malinov.ski.state.tasksDueOn
import calino.malinov.ski.ui.components.AgendaRow
import calino.malinov.ski.ui.components.AgendaRowVariant
import calino.malinov.ski.ui.components.eventColor
import calino.malinov.ski.ui.components.AgendaTaskRow
import calino.malinov.ski.ui.components.CalinoMonthHeading
import calino.malinov.ski.ui.components.calinoPressable
import calino.malinov.ski.state.FixtureNow
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.ui.home.MonthPagerPageCount
import calino.malinov.ski.ui.home.dateForDayPage
import calino.malinov.ski.ui.home.monthEventIndex
import calino.malinov.ski.ui.home.monthForPage
import calino.malinov.ski.ui.home.monthPageFor
import calino.malinov.ski.util.EventDateIndex
import calino.malinov.ski.util.sortAgendaEvents
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val AgendaDayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * The month-paged agenda: one page per month, every day of that month listed
 * with its events and due tasks. It is a root destination of its own, distinct
 * from the zooming calendar surface, and shares the calendar's month heading
 * and month-page arithmetic so both stay on the same page for the same date.
 */
@Composable
fun AgendaScreen(
    events: List<CalEvent>,
    tasks: List<CalTask>,
    modifier: Modifier = Modifier,
    initialDate: LocalDate = FixtureNow.today,
    onOpenMenu: (() -> Unit)? = null,
    onDateChanged: (LocalDate) -> Unit = {},
    /** Carries the row's own day: an agenda row is not always the selected date. */
    onEventClick: ((LocalDate, CalEvent) -> Unit)? = null,
    onEventAction: ((EventMenuAction, CalEvent) -> Unit)? = null,
    onEventDrop: (CalEvent, LocalDate) -> Unit = { _, _ -> },
    onTaskClick: ((CalTask) -> Unit)? = null,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit = { _, _ -> },
    onTaskDrop: (CalTask, LocalDate) -> Unit = { _, _ -> },
    onTaskDone: (CalTask, Boolean) -> Unit = { _, _ -> },
    onAddOn: (LocalDate) -> Unit = {},
) {
    var selectedEpoch by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    LaunchedEffect(initialDate) { selectedEpoch = initialDate.toEpochDay() }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val today = LocalCalinoNow.current.today

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = monthPageFor(YearMonth.from(initialDate))) { MonthPagerPageCount }

    // Only a user drag may commit a month. Programmatic syncs must not feed
    // intermediate pages back into the selected date, the same discipline the
    // calendar pagers use.
    val currentSelectedEpoch = rememberUpdatedState(selectedEpoch)
    var dragOrigin by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) dragOrigin = currentSelectedEpoch.value
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .distinctUntilChanged()
            .collect { (inProgress, page) ->
                if (inProgress) return@collect
                if (dragOrigin != currentSelectedEpoch.value) return@collect
                dragOrigin = null
                val month = monthForPage(page)
                val current = LocalDate.ofEpochDay(currentSelectedEpoch.value)
                if (YearMonth.from(current) == month) return@collect
                // Keep the day-of-month where it can exist, so paging a month
                // forward from the 31st does not silently jump elsewhere.
                val day = current.dayOfMonth.coerceAtMost(month.lengthOfMonth())
                val next = month.atDay(day)
                selectedEpoch = next.toEpochDay()
                onDateChanged(next)
            }
    }
    LaunchedEffect(selectedEpoch) {
        val target = monthPageFor(YearMonth.from(LocalDate.ofEpochDay(selectedEpoch)))
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }

    fun goToPage(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, MonthPagerPageCount - 1)) }
        val month = monthForPage(page.coerceIn(0, MonthPagerPageCount - 1))
        val current = LocalDate.ofEpochDay(currentSelectedEpoch.value)
        val next = month.atDay(current.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
        selectedEpoch = next.toEpochDay()
        onDateChanged(next)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        val layoutSpec = calinoLayoutSpec(maxWidth.value.toInt(), maxHeight.value.toInt(), LocalFoldPosture.current)
        val split = layoutSpec.splitPanes
        val preferences = LocalCalinoPreferences.current
        val monthContent: @Composable (Modifier) -> Unit = { contentModifier ->
            Column(contentModifier) {
        CalinoMonthHeading(
            day = selected,
            onOpenMenu = onOpenMenu,
            onPreviousMonth = { goToPage(pagerState.currentPage - 1) },
            onNextMonth = { goToPage(pagerState.currentPage + 1) },
            onToday = {
                selectedEpoch = today.toEpochDay()
                onDateChanged(today)
            },
            showToday = selected != today,
            subtitle = null,
            monthPagerState = pagerState,
            monthForPage = ::monthForPage,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            AgendaMonthPage(
                month = monthForPage(page),
                selected = selected,
                reportsFocusedDate = page == pagerState.settledPage && !pagerState.isScrollInProgress,
                onFocusedDateChanged = { day ->
                    if (day.toEpochDay() != currentSelectedEpoch.value) {
                        selectedEpoch = day.toEpochDay()
                        onDateChanged(day)
                    }
                },
                events = events,
                tasks = tasks,
                onEventClick = onEventClick,
                onEventAction = onEventAction,
                onEventDrop = onEventDrop,
                onTaskClick = onTaskClick,
                onTaskAction = onTaskAction,
                onTaskDrop = onTaskDrop,
                onTaskDone = onTaskDone,
                onAddOn = onAddOn,
            )
        }
            }
        }
        if (split) {
            val selectedEvents = remember(events, selected, preferences.weekStart) { monthEventIndex(events, YearMonth.from(selected), preferences.weekStart)[selected].orEmpty() }
            val selectedTasks = remember(tasks, selected, preferences.hideCompletedTasks) { tasksDueOn(tasks.filterNot { preferences.hideCompletedTasks && it.done }, selected) }
            Row(Modifier.fillMaxSize()) {
                monthContent(Modifier.width(layoutSpec.startPane.widthDp.dp).fillMaxHeight())
                if (layoutSpec.hingeBandDp > 0f) Spacer(Modifier.width(layoutSpec.hingeBandDp.dp).fillMaxHeight().background(CalinoColors.Canvas))
                else Box(Modifier.width(1.dp).fillMaxHeight().background(CalinoColors.Line))
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp, vertical = 18.dp)) {
                    Text("Selected day", style = CalinoTypography.titleMedium, color = CalinoColors.Ink2)
                    Spacer(Modifier.height(8.dp))
                    AgendaDayBlock(
                        day = selected, events = selectedEvents, tasks = selectedTasks,
                        onEventClick = onEventClick, onEventAction = onEventAction, onEventDrop = onEventDrop,
                        onTaskClick = onTaskClick, onTaskAction = onTaskAction, onTaskDrop = onTaskDrop,
                        onTaskDone = onTaskDone, onAdd = { onAddOn(selected) },
                    )
                }
            }
        } else monthContent(Modifier.fillMaxSize())
    }
}

@Composable
private fun AgendaMonthPage(
    month: YearMonth,
    selected: LocalDate,
    reportsFocusedDate: Boolean,
    onFocusedDateChanged: (LocalDate) -> Unit,
    events: List<CalEvent>,
    tasks: List<CalTask>,
    onEventClick: ((LocalDate, CalEvent) -> Unit)?,
    onEventAction: ((EventMenuAction, CalEvent) -> Unit)?,
    onEventDrop: (CalEvent, LocalDate) -> Unit,
    onTaskClick: ((CalTask) -> Unit)?,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDrop: (CalTask, LocalDate) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
    onAddOn: (LocalDate) -> Unit,
) {
    val days = remember(month) { (1..month.lengthOfMonth()).map(month::atDay) }
    val weekStart = LocalCalinoPreferences.current.weekStart
    val eventsByDay = remember(events, month, weekStart) { monthEventIndex(events, month, weekStart) }
    val hideCompletedTasks = LocalCalinoPreferences.current.hideCompletedTasks
    val tasksByDay = remember(tasks, month, hideCompletedTasks) {
        val visible = tasks.filterNot { hideCompletedTasks && it.done }
        days.associateWith { day -> tasksDueOn(visible, day) }.filterValues { it.isNotEmpty() }
    }
    val listState = rememberLazyListState()
    var initialPositioningComplete by remember(month) { mutableStateOf(false) }
    val focusedDay by remember(listState, days) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val visible = layout.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf null

            val viewportStart = layout.viewportStartOffset
            val viewportEnd = layout.viewportEndOffset
            val focusLine = viewportStart + ((viewportEnd - viewportStart) * AgendaFocusLineFromTop).roundToInt()
            val focusedIndex = visible.firstOrNull { item ->
                focusLine >= item.offset && focusLine < item.offset + item.size
            }?.index ?: visible.minByOrNull { item ->
                when {
                    focusLine < item.offset -> item.offset - focusLine
                    focusLine >= item.offset + item.size -> focusLine - (item.offset + item.size)
                    else -> 0
                }
            }?.index
            focusedIndex?.let(days::getOrNull)
        }
    }

    val currentOnFocusedDateChanged = rememberUpdatedState(onFocusedDateChanged)
    LaunchedEffect(listState, reportsFocusedDate, initialPositioningComplete) {
        if (!reportsFocusedDate || !initialPositioningComplete) return@LaunchedEffect
        snapshotFlow { focusedDay }
            .distinctUntilChanged()
            .collect { day -> day?.let(currentOnFocusedDateChanged.value) }
    }

    // Entering the agenda from a chosen date should land on that date rather
    // than on the first of the month. Only the page that owns it scrolls.
    LaunchedEffect(month) {
        if (YearMonth.from(selected) == month && selected.dayOfMonth > 1) {
            listState.scrollToItem(selected.dayOfMonth - 1)
        }
        initialPositioningComplete = true
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(if (reportsFocusedDate) Modifier.testTag("agenda-month-list") else Modifier),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = CalinoSpacing.PillClearance),
    ) {
        items(days.size, key = { days[it].toEpochDay() }) { index ->
            val day = days[index]
            AgendaDayBlock(
                day = day,
                events = eventsByDay[day].orEmpty(),
                tasks = tasksByDay[day].orEmpty(),
                // A day block changes height whenever something is dropped
                // onto it or off it, and every later day moves with it. Let
                // the list carry that rather than snapping the month.
                modifier = Modifier.animateItem().padding(bottom = 10.dp),
                onEventClick = onEventClick,
                onEventAction = onEventAction,
                onEventDrop = onEventDrop,
                onTaskClick = onTaskClick,
                onTaskAction = onTaskAction,
                onTaskDrop = onTaskDrop,
                onTaskDone = onTaskDone,
                onAdd = { onAddOn(day) },
            )
        }
    }
}

/** The reading line sits 70% upward from the bottom of the list viewport. */
private const val AgendaFocusLineFromTop = .30f

/**
 * One day's agenda content: the day header, then its events and due tasks, or
 * the empty line. Shared by the month-paged agenda and by the landscape day
 * pane so the two cannot drift apart.
 */
@Composable
internal fun AgendaDayBlock(
    day: LocalDate,
    events: List<CalEvent>,
    tasks: List<CalTask>,
    modifier: Modifier = Modifier,
    onEventClick: ((LocalDate, CalEvent) -> Unit)?,
    onEventAction: ((EventMenuAction, CalEvent) -> Unit)?,
    onEventDrop: (CalEvent, LocalDate) -> Unit,
    onTaskClick: ((CalTask) -> Unit)?,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDrop: (CalTask, LocalDate) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
    onAdd: () -> Unit,
) {
    val timeFormat = LocalTimeFormat
    val dayEvents = remember(events) { sortAgendaEvents(events) }
    Column(
        modifier.animateContentSize(tween(CalinoMotion.ContentEnterMillis)),
    ) {
        AgendaDayHeader(day = day, onAdd = onAdd)
        if (dayEvents.isEmpty() && tasks.isEmpty()) {
            Text(
                "Nothing scheduled",
                color = CalinoColors.Ink3,
                fontSize = 13.sp,
                lineHeight = 19.5.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dayEvents.forEach { event ->
                    var menuOpen by remember(event.id) { mutableStateOf(false) }
                    Box {
                        AgendaRow(
                            title = event.title,
                            color = eventColor(event.color),
                            time = if (event.allDay) null else event.start?.let { timeFormat.format(it) },
                            subtitle = event.location ?: if (event.recurrence != null) "Repeats weekly" else null,
                            variant = AgendaRowVariant.Card,
                            onClick = onEventClick?.let { click -> { click(day, event) } },
                            onLongClick = onEventAction?.let { { menuOpen = true } },
                            onDragEnd = { offset ->
                                if (kotlin.math.abs(offset.y) > 36f) {
                                    onEventDrop(event, day.plusDays((offset.y / 76f).roundToInt().toLong()))
                                }
                            },
                        )
                        EventActionMenu(
                            event = event,
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            onAction = { action -> onEventAction?.invoke(action, event) },
                        )
                    }
                }
                val taskLookup = LocalTaskLookup.current
                nestWithinList(tasks, taskLookup).forEach { row ->
                    when (row) {
                        is TaskListRow.AbsentParent -> AbsentParentRow(
                            parent = row.parent,
                            onClick = onTaskClick?.let { click -> { click(row.parent) } },
                        )

                        is TaskListRow.Item -> {
                            val (task, depth, nestingLines) = row
                            var menuOpen by remember(task.id) { mutableStateOf(false) }
                            Box {
                                AgendaTaskRow(
                                    task = task,
                                    // The rail has to cross this column's 6dp
                                    // gap, or it reads as a dash beside each
                                    // card rather than one line down the run.
                                    modifier = Modifier.taskNestIndent(
                                        depth,
                                        nestingLines,
                                        railOverhang = 6.dp,
                                    ),
                                    // Only a task that carries a real due *time* gets a
                                    // clock face. Formatting the due date's midnight gave
                                    // every task an identical "12:00 AM" that said nothing.
                                    time = task.dueTime?.let { timeFormat.format(it) },
                                    onClick = onTaskClick?.let { click -> { click(task) } },
                                    onCheckedChange = { done -> onTaskDone(task, done) },
                                    onLongClick = { menuOpen = true },
                                    onDragEnd = { offset ->
                                        if (kotlin.math.abs(offset.y) > 36f) {
                                            onTaskDrop(task, day.plusDays((offset.y / 76f).roundToInt().toLong()))
                                        }
                                    },
                                )
                                TaskActionMenu(
                                    task = task,
                                    expanded = menuOpen,
                                    onDismiss = { menuOpen = false },
                                    onAction = { action -> onTaskAction(action, task) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The landscape companion pane: a day-paged agenda beside the month grid. The
 * pager is supplied by [HomeScreen], so its real-user settle collector remains
 * the one owner of the committed date. Each page scrolls vertically on its
 * own and reserves the add pill's clearance, since the pill sits over this pane
 * when it is showing.
 */
@Composable
fun DayPane(
    state: PagerState,
    eventDateIndex: EventDateIndex,
    tasksByDueDate: Map<LocalDate, List<CalTask>>,
    interactionEnabled: Boolean,
    modifier: Modifier = Modifier,
    onEventClick: ((LocalDate, CalEvent) -> Unit)? = null,
    onEventAction: ((EventMenuAction, CalEvent) -> Unit)? = null,
    onEventDrop: (CalEvent, LocalDate) -> Unit = { _, _ -> },
    onTaskClick: ((CalTask) -> Unit)? = null,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit = { _, _ -> },
    onTaskDrop: (CalTask, LocalDate) -> Unit = { _, _ -> },
    onTaskDone: (CalTask, Boolean) -> Unit = { _, _ -> },
    onAdd: (LocalDate) -> Unit = {},
) {
    HorizontalPager(
        state = state,
        modifier = modifier
            .fillMaxSize()
            .background(CalinoColors.Canvas)
            .testTag("day-pane-pager")
            .semantics { contentDescription = "Day sidebar" },
        userScrollEnabled = interactionEnabled,
        key = { page -> dateForDayPage(page).toEpochDay() },
    ) { page ->
        val pageDay = dateForDayPage(page)
        val pageEvents = remember(eventDateIndex, pageDay) { eventDateIndex.eventsOn(pageDay) }
        val pageTasks = tasksByDueDate[pageDay].orEmpty()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .padding(bottom = CalinoSpacing.PillClearance)
                .semantics {
                    contentDescription = "Day sidebar page ${pageDay.format(AgendaDayFormatter)}"
                },
        ) {
            AgendaDayBlock(
                day = pageDay,
                events = pageEvents,
                tasks = pageTasks,
                onEventClick = onEventClick,
                onEventAction = onEventAction,
                onEventDrop = onEventDrop,
                onTaskClick = onTaskClick,
                onTaskAction = onTaskAction,
                onTaskDrop = onTaskDrop,
                onTaskDone = onTaskDone,
                onAdd = { onAdd(pageDay) },
            )
        }
    }
}

/**
 * A day separator in the agenda: the weekday, the date, and the day's own add
 * affordance. Today's date is carried by the accent chip rather than by color
 * alone.
 */
@Composable
internal fun AgendaDayHeader(day: LocalDate, onAdd: () -> Unit) {
    val isToday = day == LocalCalinoNow.current.today
    val weekday = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.uppercase() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            weekday,
            color = CalinoColors.Accent,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (isToday) CalinoColors.AccentSoft else CalinoColors.Canvas)
                .padding(horizontal = if (isToday) 8.dp else 0.dp, vertical = if (isToday) 2.dp else 0.dp),
        ) {
            Text(
                day.format(AgendaDayFormatter),
                color = if (isToday) CalinoColors.Accent else CalinoColors.Ink3,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(CalinoShapes.Chip))
                .calinoPressable(onClick = onAdd)
                .semantics { contentDescription = "Add on ${day.format(AgendaDayFormatter)}" }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ Add", color = CalinoColors.Ink3, fontSize = 12.5.sp, lineHeight = 18.sp)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line2))
    Spacer(Modifier.height(8.dp))
}
