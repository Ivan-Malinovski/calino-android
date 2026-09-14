package calino.malinov.ski.poc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoShapes
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.repository.CalinoCalendar
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import calino.malinov.ski.poc.state.LocalCalinoPreferences
import calino.malinov.ski.poc.ui.surfaces.PockRoute
import calino.malinov.ski.poc.ui.surfaces.TaskActionMenu
import calino.malinov.ski.poc.ui.surfaces.TaskMenuAction
import calino.malinov.ski.poc.util.CalinoWeekStart
import calino.malinov.ski.poc.util.leadingCells
import calino.malinov.ski.poc.util.weekdayLetters
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The root navigator: a floating, card-like sidebar opened from the hamburger
 * in each screen header. It replaces the former bottom dock, so the root
 * surfaces keep their full height and the add pill can float above them.
 */
@Composable
fun NavSidebar(
    visible: Boolean,
    selectedRoute: PockRoute,
    onRoute: (PockRoute) -> Unit,
    onDismiss: () -> Unit,
    snapshot: CalinoSnapshot,
    accounts: List<CalDavAccount> = emptyList(),
    selectedDate: LocalDate,
    onDateChanged: (LocalDate) -> Unit,
    onToggleCalendar: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onToggleCalendarTasks: (String, String, Boolean) -> Unit = { _, _, _ -> },
    fixtureHiddenCalendarIds: Set<String> = emptySet(),
    fixtureHiddenTaskCalendarIds: Set<String> = emptySet(),
    onToggleFixtureCalendar: (String, Boolean) -> Unit = { _, _ -> },
    onToggleFixtureCalendarTasks: (String, Boolean) -> Unit = { _, _ -> },
    onRenameCalendar: (String, String, String) -> Unit = { _, _, _ -> },
    onColorCalendar: (String, String, Long) -> Unit = { _, _, _ -> },
    onSyncAll: () -> Unit = {},
    onSyncCalendar: (String, String) -> Unit = { _, _ -> },
    onTaskClick: (CalTask) -> Unit = {},
    onTaskComplete: (CalTask, Boolean) -> Unit = { _, _ -> },
    onTaskAction: (TaskMenuAction, CalTask) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val preferences = LocalCalinoPreferences.current
    // The calendar views come first as a group; the rule separates them from
    // the other surfaces.
    val calendarItems = listOf(
        NavItem(pockRouteLabel(PockRoute.Day), PockRoute.Day, CalinoIcons.Calendar),
        NavItem(pockRouteLabel(PockRoute.Range), PockRoute.Range, CalinoIcons.Calendar),
        NavItem(pockRouteLabel(PockRoute.Agenda), PockRoute.Agenda, CalinoIcons.AgendaList),
    )
    val items = listOfNotNull(
        NavItem(pockRouteLabel(PockRoute.Tasks), PockRoute.Tasks, CalinoIcons.ListChecks),
        NavItem(pockRouteLabel(PockRoute.Journal), PockRoute.Journal, CalinoIcons.BookOpen)
            .takeIf { preferences.journalEnabled },
        NavItem(pockRouteLabel(PockRoute.Contacts), PockRoute.Contacts, CalinoIcons.Users)
            .takeIf { preferences.contactsEnabled },
        NavItem(pockRouteLabel(PockRoute.Settings), PockRoute.Settings, CalinoIcons.Settings),
    )
    val settingsItem = items.first { it.route == PockRoute.Settings }
    val mainItems = items.filterNot { it.route == PockRoute.Settings }

    var dragX by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val axisThresholdPx = with(density) { 8.dp.toPx() }
    val travelPx = with(density) { 360.dp.toPx() }
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val scrimDragAlpha = 1f - (abs(dragX) / travelPx).coerceIn(0f, 1f)

    LaunchedEffect(visible) {
        if (visible) {
            animationJob?.cancel()
            dragX = 0f
            dismissing = false
        } else {
            animationJob?.cancel()
        }
    }

    Box(modifier.fillMaxSize()) {
        // This sidebar host is inset below the status bar. Paint the matching
        // slice just outside its bounds so the transparent system bar follows
        // the same veil instead of remaining a bright strip above it.
        AnimatedVisibility(
            visible = visible && !dismissing,
            enter = fadeIn(tween(CalinoMotion.SurfaceFadeMillis)),
            exit = fadeOut(tween(CalinoMotion.ContentExitMillis)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = -statusBarHeight)
                .fillMaxWidth()
                .height(statusBarHeight)
                .graphicsLayer { alpha = scrimDragAlpha },
        ) {
            Box(Modifier.fillMaxSize().background(CalinoColors.Scrim))
        }
        CalinoScrim(
            visible = visible && !dismissing,
            onDismiss = onDismiss,
            modifier = Modifier.graphicsLayer { alpha = scrimDragAlpha },
        )
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(240)) { -it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(200)) { -it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            BoxWithConstraints {
                val cardWidth = if (maxWidth * .8f < 300.dp) maxWidth * .8f else 300.dp
                // Keep the pointer-input node stationary while the card moves,
                // so local pointer coordinates cannot chase the finger.
                Box(
                    Modifier.pointerInput(visible, dismissing) {
                        if (!visible || dismissing) return@pointerInput
                        awaitEachGestureCompat(
                            axisThresholdPx = axisThresholdPx,
                            onDrag = { total ->
                                animationJob?.cancel()
                                dragX = total.coerceIn(-travelPx, 0f)
                            },
                            onRelease = {
                                if (-dragX >= dismissThresholdPx) {
                                    // Hand off at release; the exit animation
                                    // above owns the remaining travel.
                                    dismissing = true
                                    currentOnDismiss()
                                } else {
                                    animationJob?.cancel()
                                    animationJob = scope.launch {
                                        animate(dragX, 0f, animationSpec = CalinoMotion.gestureReturn()) { value, _ -> dragX = value }
                                        animationJob = null
                                    }
                                }
                            },
                        )
                    },
                ) {
                    Column(
                        Modifier
                            .offset { IntOffset(dragX.roundToInt(), 0) }
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                            .width(cardWidth)
                            .fillMaxHeight()
                            // The flat-dark rule: a drop shadow is a light-mode
                            // device. In dark the Panel step and the hairline
                            // below carry the elevation instead.
                            .shadow(18.dp * CalinoColors.elevationAlpha, RoundedCornerShape(CalinoShapes.Card), clip = false)
                            .clip(RoundedCornerShape(CalinoShapes.Card))
                            .background(if (CalinoColors.isDark) CalinoColors.Panel else CalinoColors.Canvas)
                            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card))
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Match the web sidebar brand: an 11dp accent diamond
                            // with a theme-aware accent focus ring.
                            Box(
                                Modifier
                                    .size(19.dp)
                                    .graphicsLayer { rotationZ = 45f }
                                    .background(
                                        CalinoColors.Accent.copy(alpha = if (CalinoColors.isDark) .20f else .14f),
                                        RoundedCornerShape(5.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(11.dp)
                                        .background(CalinoColors.Accent, RoundedCornerShape(3.dp)),
                                )
                            }
                            Text(
                                "Calino",
                                style = CalinoTypography.titleLarge.copy(fontSize = 22.sp),
                            )
                        }
                        SidebarMiniCalendar(
                            selectedDate = selectedDate,
                            onDateChanged = onDateChanged,
                        )
                        SidebarSectionLabel("VIEWS")
                        SidebarNavGroup {
                            calendarItems.forEach { item ->
                                NavRow(item, selected = selectedRoute == item.route) {
                                    onRoute(item.route)
                                    onDismiss()
                                }
                            }
                        }
                        SidebarSectionDivider()
                        SidebarSectionLabel("ORGANIZE")
                        SidebarNavGroup {
                            mainItems.forEach { item ->
                                NavRow(item, selected = selectedRoute == item.route) {
                                    onRoute(item.route)
                                    onDismiss()
                                }
                            }
                        }
                        SidebarExtras(
                            snapshot = snapshot,
                            accounts = accounts,
                            onToggleCalendar = onToggleCalendar,
                            onToggleCalendarTasks = onToggleCalendarTasks,
                            fixtureHiddenCalendarIds = fixtureHiddenCalendarIds,
                            fixtureHiddenTaskCalendarIds = fixtureHiddenTaskCalendarIds,
                            onToggleFixtureCalendar = onToggleFixtureCalendar,
                            onToggleFixtureCalendarTasks = onToggleFixtureCalendarTasks,
                            onRenameCalendar = onRenameCalendar,
                            onColorCalendar = onColorCalendar,
                            onSyncAll = onSyncAll,
                            onSyncCalendar = onSyncCalendar,
                            onTaskClick = onTaskClick,
                            onTaskComplete = onTaskComplete,
                            onTaskAction = onTaskAction,
                        )
                        Spacer(Modifier.height(8.dp))
                        SidebarNavGroup {
                            NavRow(settingsItem, selected = selectedRoute == settingsItem.route) {
                                onRoute(settingsItem.route)
                                onDismiss()
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

private data class SidebarCalendarRow(
    val accountId: String?,
    val calendar: CalinoCalendar,
    val enabled: Boolean = true,
)

internal fun sidebarMonthCells(
    month: YearMonth,
    weekStart: CalinoWeekStart,
): List<LocalDate?> {
    val leading = month.leadingCells(weekStart)
    val cells = List<LocalDate?>(leading) { null } +
        (1..month.lengthOfMonth()).map(month::atDay)
    return cells + List((7 - cells.size % 7) % 7) { null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarMiniCalendar(
    selectedDate: LocalDate,
    onDateChanged: (LocalDate) -> Unit,
) {
    val preferences = LocalCalinoPreferences.current
    val weekStart = preferences.weekStart
    val expanded = preferences.sidebarCalendarExpanded
    var miniMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val cardShape = RoundedCornerShape(12.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(CalinoMotion.ContentEnterMillis),
        label = "sidebar calendar chevron",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp * CalinoColors.elevationAlpha, cardShape)
            .clip(cardShape)
            .background(if (CalinoColors.isDark) CalinoColors.Side else CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line, cardShape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !expanded,
                enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)),
                exit = fadeOut(tween(CalinoMotion.ContentExitMillis)),
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { preferences.setSidebarCalendarExpanded(true) }
                        .semantics {
                            contentDescription = "Calendar in sidebar"
                            stateDescription = "Collapsed"
                            role = Role.Button
                        }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "CALENDAR",
                        style = CalinoTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.1.sp, color = CalinoColors.Ink3),
                        modifier = Modifier.weight(1f),
                    )
                    CalinoIcon(
                        CalinoIcon.Forward,
                        tint = CalinoColors.Ink3,
                        modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevronRotation },
                        contentDescription = null,
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)),
                exit = fadeOut(tween(CalinoMotion.ContentExitMillis)),
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { miniMonth = miniMonth.minusMonths(1) },
                        modifier = Modifier.size(44.dp).clearAndSetSemantics {
                            contentDescription = "Previous month in sidebar"
                            role = Role.Button
                        },
                    ) { Text("‹", fontSize = 22.sp, color = CalinoColors.Ink2) }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onDateChanged(LocalDate.now()); miniMonth = YearMonth.now() }
                            .semantics {
                                contentDescription = "Go to today in sidebar"
                                role = Role.Button
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "CALENDAR",
                                style = CalinoTypography.labelSmall.copy(fontSize = 8.sp, letterSpacing = .9.sp, color = CalinoColors.Ink3),
                            )
                            Text(
                                miniMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                                style = CalinoTypography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                            )
                        }
                    }
                    IconButton(
                        onClick = { miniMonth = miniMonth.plusMonths(1) },
                        modifier = Modifier.size(44.dp).clearAndSetSemantics {
                            contentDescription = "Next month in sidebar"
                            role = Role.Button
                        },
                    ) { Text("›", fontSize = 22.sp, color = CalinoColors.Ink2) }
                    IconButton(
                        onClick = { preferences.setSidebarCalendarExpanded(false) },
                        modifier = Modifier.size(44.dp).clearAndSetSemantics {
                            contentDescription = "Collapse calendar in sidebar"
                            role = Role.Button
                        },
                    ) {
                        CalinoIcon(
                            CalinoIcon.Forward,
                            tint = CalinoColors.Ink3,
                            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevronRotation },
                            contentDescription = null,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(CalinoMotion.ContentEnterMillis)) + fadeIn(tween(CalinoMotion.ContentEnterMillis)),
            exit = shrinkVertically(tween(CalinoMotion.ContentExitMillis)) + fadeOut(tween(CalinoMotion.ContentExitMillis)),
        ) {
            Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            weekdayLetters(weekStart).forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(it, color = CalinoColors.Ink3, fontSize = 10.sp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            sidebarMonthCells(miniMonth, weekStart).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .then(
                                    if (date != null) {
                                        Modifier
                                            .clip(CircleShape)
                                            .calinoPressable { onDateChanged(date) }
                                            .semantics {
                                                contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))
                                                this.selected = date == selectedDate
                                            }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                val selected = date == selectedDate
                                val today = date == LocalDate.now()
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) CalinoColors.Accent else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        color = if (selected) CalinoColors.OnAccent else if (today) CalinoColors.Accent else CalinoColors.Ink2,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarExtras(
    snapshot: CalinoSnapshot,
    accounts: List<CalDavAccount>,
    onToggleCalendar: (String, String, Boolean) -> Unit,
    onToggleCalendarTasks: (String, String, Boolean) -> Unit,
    fixtureHiddenCalendarIds: Set<String>,
    fixtureHiddenTaskCalendarIds: Set<String>,
    onToggleFixtureCalendar: (String, Boolean) -> Unit,
    onToggleFixtureCalendarTasks: (String, Boolean) -> Unit,
    onRenameCalendar: (String, String, String) -> Unit,
    onColorCalendar: (String, String, Long) -> Unit,
    onSyncAll: () -> Unit,
    onSyncCalendar: (String, String) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskComplete: (CalTask, Boolean) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
) {
    var editingCalendarKey by remember { mutableStateOf<String?>(null) }
    var editedCalendarName by remember { mutableStateOf("") }
    var fixtureCalendarNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var fixtureCalendarColors by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var upcomingTasksExpanded by remember { mutableStateOf(false) }
    val upcomingChevronRotation by animateFloatAsState(
        targetValue = if (upcomingTasksExpanded) 90f else 0f,
        animationSpec = tween(180),
        label = "upcoming tasks chevron",
    )
    val rows = if (accounts.isEmpty()) {
        snapshot.calendars.map { calendar ->
            SidebarCalendarRow(
                accountId = null,
                calendar = calendar.copy(
                    name = fixtureCalendarNames[calendar.id] ?: calendar.name,
                    color = fixtureCalendarColors[calendar.id] ?: calendar.color,
                ),
            )
        }
    } else {
        accounts.flatMap { account ->
            account.calendars.map { calendar ->
                SidebarCalendarRow(
                    accountId = account.id,
                    calendar = CalinoCalendar(
                        id = calendar.id,
                        name = calendar.name,
                        color = calendar.color,
                        readOnly = calendar.readOnly,
                        visible = calendar.visible,
                        showTasksInViews = calendar.showTasksInViews,
                    ),
                    enabled = calendar.enabled,
                )
            }
        }
    }

    val taskCardShape = RoundedCornerShape(12.dp)
    val upcoming = snapshot.tasks
        .filter { !it.done }
        .filter { it.parentTaskId == null }
        .sortedBy { it.due ?: LocalDate.MAX }
        .take(10)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(taskCardShape)
            .background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line, taskCardShape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(7.dp))
                .clickable { upcomingTasksExpanded = !upcomingTasksExpanded }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "UPCOMING TASKS",
                style = CalinoTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.1.sp, color = CalinoColors.Ink3),
                modifier = Modifier.weight(1f),
            )
            Text("${upcoming.size}", color = CalinoColors.Ink3, fontSize = 12.sp)
            CalinoIcon(
                CalinoIcon.Forward,
                tint = CalinoColors.Ink3,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp)
                    .graphicsLayer { rotationZ = upcomingChevronRotation },
                contentDescription = null,
            )
        }
        AnimatedVisibility(
            visible = upcomingTasksExpanded,
            enter = expandVertically(tween(180)) + fadeIn(tween(140)),
            exit = shrinkVertically(tween(140)) + fadeOut(tween(100)),
        ) {
            Column {
                upcoming.forEach { task ->
                    var menuOpen by remember(task.id) { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth()) {
                        TaskRow(
                            task = task,
                            compact = true,
                            onCheckedChange = { onTaskComplete(task, it) },
                            onClick = { onTaskClick(task) },
                            onLongClick = { menuOpen = true },
                        )
                        TaskActionMenu(
                            task = task,
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            onAction = { action -> onTaskAction(action, task) },
                        )
                    }
                }
                if (upcoming.isEmpty()) {
                    Text(
                        "No upcoming tasks",
                        color = CalinoColors.Ink3,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "CALENDARS",
            style = CalinoTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.1.sp, color = CalinoColors.Ink3),
            modifier = Modifier.weight(1f),
        )
        val visibleCount = rows.count { row ->
            if (row.accountId == null) row.calendar.id !in fixtureHiddenCalendarIds else row.calendar.visible
        }
        Text("$visibleCount of ${rows.size} visible", color = CalinoColors.Ink3, fontSize = 12.sp)
        IconButton(
            onClick = onSyncAll,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "Sync all calendars" },
        ) {
            Icon(
                CalinoIcons.Refresh,
                contentDescription = null,
                tint = CalinoColors.Accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CalinoColors.Side)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
    rows.forEach { row ->
        val key = "${row.accountId.orEmpty()}:${row.calendar.id}"
        val visible = if (row.accountId == null) row.calendar.id !in fixtureHiddenCalendarIds else row.calendar.visible
        val tasksVisible = if (row.accountId == null) row.calendar.id !in fixtureHiddenTaskCalendarIds else row.calendar.showTasksInViews
        var menuOpen by remember(key) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (visible) CalinoColors.Panel.copy(alpha = .72f) else Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .calinoPressable {
                            if (row.accountId == null) onToggleFixtureCalendar(row.calendar.id, !visible)
                            else onToggleCalendar(row.accountId, row.calendar.id, !visible)
                        }
                        .semantics {
                            contentDescription = "Show ${row.calendar.name}"
                            stateDescription = if (visible) "Visible" else "Hidden"
                            role = Role.Checkbox
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (visible) Color(row.calendar.color) else Color.Transparent)
                            .border(1.dp, Color(row.calendar.color), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (visible) {
                            Icon(CalinoIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                if (editingCalendarKey == key) {
                    TextField(
                        value = editedCalendarName,
                        onValueChange = { editedCalendarName = it },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        singleLine = true,
                    )
                    TextButton(
                        onClick = {
                            val name = editedCalendarName.trim()
                            if (name.isNotEmpty()) {
                                if (row.accountId == null) {
                                    fixtureCalendarNames = fixtureCalendarNames + (row.calendar.id to name)
                                } else {
                                    onRenameCalendar(row.accountId, row.calendar.id, name)
                                }
                                editingCalendarKey = null
                            }
                        },
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) { Text("Save", color = CalinoColors.Accent) }
                } else {
                    Text(
                        row.calendar.name,
                        color = if (visible) CalinoColors.Ink else CalinoColors.Ink3,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp)
                            .combinedClickable(
                                onClick = {},
                                onDoubleClick = {
                                    editingCalendarKey = key
                                    editedCalendarName = row.calendar.name
                                },
                            ),
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(CalinoIcons.More, contentDescription = "More options for ${row.calendar.name}", tint = CalinoColors.Ink3, modifier = Modifier.size(18.dp))
                }
                if (row.accountId != null) {
                    IconButton(onClick = { onSyncCalendar(row.accountId, row.calendar.id) }, modifier = Modifier.size(44.dp)) { Text("↻", fontSize = 17.sp, color = CalinoColors.Ink2) }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename calendar") },
                    onClick = {
                        menuOpen = false
                        editingCalendarKey = key
                        editedCalendarName = row.calendar.name
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (tasksVisible) "Hide tasks in calendar" else "Show tasks in calendar") },
                    onClick = {
                        menuOpen = false
                        if (row.accountId == null) onToggleFixtureCalendarTasks(row.calendar.id, !tasksVisible)
                        else onToggleCalendarTasks(row.accountId, row.calendar.id, !tasksVisible)
                    },
                )
                listOf(
                    0xFFC2697F to "Rose",
                    0xFF5B7FB5 to "Blue",
                    0xFF5D9A78 to "Green",
                    0xFFBF944E to "Gold",
                ).forEach { (color, name) ->
                    DropdownMenuItem(
                        text = { Text("Use $name color") },
                        onClick = {
                            menuOpen = false
                            if (row.accountId == null) {
                                fixtureCalendarColors = fixtureCalendarColors + (row.calendar.id to color)
                            } else {
                                onColorCalendar(row.accountId, row.calendar.id, color)
                            }
                        },
                    )
                }
                DropdownMenuItem(text = { Text("Export ICS") }, onClick = { menuOpen = false })
            }
        }
    }
    }
}

/**
 * The name a route goes by in the UI. The sidebar and the add pill's swipe
 * preview both point at the same views, so they read from one list rather than
 * drifting apart.
 */
fun pockRouteLabel(route: PockRoute): String = when (route) {
    PockRoute.Day -> "Month"
    PockRoute.Range -> "Range"
    PockRoute.Agenda -> "Agenda"
    PockRoute.Accounts -> "Calendars"
    PockRoute.Tasks -> "Tasks"
    PockRoute.Journal -> "Journal"
    PockRoute.Contacts -> "Contacts"
    PockRoute.Settings -> "Settings"
    PockRoute.Detail -> "Event"
    PockRoute.TaskDetail -> "Task"
    PockRoute.QuickAdd -> "Quick add"
    PockRoute.Notifications -> "Notifications"
}

private data class NavItem(
    val label: String,
    val route: PockRoute,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun SidebarSectionLabel(label: String) {
    Text(
        label,
        style = CalinoTypography.labelSmall.copy(
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = CalinoColors.Ink3,
        ),
        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SidebarNavGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CalinoColors.Side)
            .border(1.dp, CalinoColors.Line2, RoundedCornerShape(12.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
private fun SidebarSectionDivider() {
    Box(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(CalinoColors.Line),
    )
}

@Composable
private fun NavRow(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val foreground by animateColorAsState(if (selected) CalinoColors.Accent else CalinoColors.Ink2, tween(CalinoMotion.ContentEnterMillis), label = "nav row tint")
    val background by animateColorAsState(
        if (selected) CalinoColors.AccentSoft else Color.Transparent,
        tween(CalinoMotion.ContentEnterMillis),
        label = "nav row background",
    )
    val railProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(CalinoMotion.ContentEnterMillis),
        label = "nav row rail",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .calinoPressable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.label}${if (selected) ", selected" else ""}"
                stateDescription = if (selected) "Selected" else "Not selected"
                role = Role.Tab
                this.selected = selected
            }
            .heightIn(min = 44.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(CircleShape)
                .graphicsLayer {
                    alpha = railProgress
                    scaleY = railProgress
                }
                .background(CalinoColors.Accent),
        )
        Icon(item.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
        Text(item.label, style = CalinoTypography.bodyLarge, color = foreground)
    }
}

/**
 * Watches for a leftward drag in the Initial pass so the gesture can begin
 * anywhere on the card, including over its rows, while taps keep working.
 */
private suspend fun PointerInputScope.awaitEachGestureCompat(
    axisThresholdPx: Float,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val pointerId = down.id
        var lastPosition = down.position
        var totalX = 0f
        var totalY = 0f
        var horizontal = false
        var axisDecided = false
        var completed = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                completed = true
                break
            }
            val amount = change.position - lastPosition
            lastPosition = change.position
            totalX += amount.x
            totalY += amount.y
            if (!axisDecided && (abs(totalX) > axisThresholdPx || abs(totalY) > axisThresholdPx)) {
                horizontal = abs(totalX) > abs(totalY)
                axisDecided = true
            }
            if (axisDecided && horizontal && totalX < 0f) {
                change.consume()
                onDrag(totalX)
            }
        }

        if (completed && axisDecided && horizontal) onRelease()
    }
}
