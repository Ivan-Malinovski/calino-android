package calino.malinov.ski.ui.components

import calino.malinov.ski.R
import androidx.compose.ui.res.stringResource
import calino.malinov.ski.util.localizedDateFormatter
import calino.malinov.ski.util.LocalCalinoLocale

import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.model.WebcalSubscription
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.ui.surfaces.PockRoute
import calino.malinov.ski.ui.surfaces.TaskActionMenu
import calino.malinov.ski.ui.surfaces.TaskMenuAction
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.leadingCells
import calino.malinov.ski.util.weekdayLetters
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
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
    updateAvailable: Boolean = false,
    onUpdateClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val preferences = LocalCalinoPreferences.current
    val routeLabel: @Composable (PockRoute) -> String = { pockRouteLabel(it) }
    val updateAvailableLabel = stringResource(R.string.cal_update_available)
    // The calendar views come first as a group; the rule separates them from
    // the other surfaces.
    val calendarItems = listOf(
        NavItem(routeLabel(PockRoute.Day), PockRoute.Day, pockRouteIcon(PockRoute.Day)),
        NavItem(routeLabel(PockRoute.Year), PockRoute.Year, pockRouteIcon(PockRoute.Year)),
        NavItem(routeLabel(PockRoute.Range), PockRoute.Range, pockRouteIcon(PockRoute.Range)),
        NavItem(routeLabel(PockRoute.Agenda), PockRoute.Agenda, pockRouteIcon(PockRoute.Agenda)),
    )
    val items = listOfNotNull(
        NavItem(routeLabel(PockRoute.Tasks), PockRoute.Tasks, pockRouteIcon(PockRoute.Tasks)),
        NavItem(routeLabel(PockRoute.Journal), PockRoute.Journal, pockRouteIcon(PockRoute.Journal))
            .takeIf { preferences.journalEnabled },
        NavItem(routeLabel(PockRoute.Contacts), PockRoute.Contacts, pockRouteIcon(PockRoute.Contacts))
            .takeIf { preferences.contactsEnabled },
        NavItem(routeLabel(PockRoute.Settings), PockRoute.Settings, pockRouteIcon(PockRoute.Settings)),
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
    val scrimDragAlpha = 1f - (abs(dragX) / travelPx).coerceIn(0f, 1f)
    val statusBarScrimProgress by animateFloatAsState(
        targetValue = if (visible && !dismissing) 1f else 0f,
        animationSpec = if (visible && !dismissing) {
            CalinoMotion.expressiveSpatial()
        } else {
            tween(200)
        },
        label = "sidebar status bar scrim",
    )

    PredictiveBackHandler(enabled = visible && !dismissing) { events ->
        try {
            events.collect { event -> dragX = -travelPx * event.progress.coerceIn(0f, 1f) }
            dragX = -travelPx
            dismissing = true
            currentOnDismiss()
        } catch (cancelled: CancellationException) {
            animate(dragX, 0f, animationSpec = CalinoMotion.gestureReturn()) { value, _ -> dragX = value }
            throw cancelled
        }
    }

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
        // Keep the negatively offset status-bar slice mounted while its color
        // animates. An AnimatedVisibility layer can clip drawing outside its
        // own bounds mid-transition, making this slice appear in one step.
        StatusBarScrimExtension(
            color = CalinoColors.scrim(
                .38f * statusBarScrimProgress.coerceIn(0f, 1f) * scrimDragAlpha,
            ),
            modifier = Modifier
                .align(Alignment.TopStart),
        )
        CalinoScrim(
            visible = visible && !dismissing,
            onDismiss = onDismiss,
            modifier = Modifier.graphicsLayer { alpha = scrimDragAlpha },
        )
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(CalinoMotion.expressiveSpatial()) { -it } +
                fadeIn(tween(CalinoMotion.ContentEnterMillis)),
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
                            .padding(start = 12.dp, top = 12.dp, bottom = 18.dp)
                            .width(cardWidth)
                            .fillMaxHeight()
                            // The flat-dark rule: a drop shadow is a light-mode
                            // device. In dark the Panel step and the hairline
                            // below carry the elevation instead.
                            .shadow(12.dp * CalinoColors.elevationAlpha, RoundedCornerShape(CalinoShapes.Card), clip = false)
                            .clip(RoundedCornerShape(CalinoShapes.Card))
                            .background(if (CalinoColors.isDark) CalinoColors.Panel else CalinoColors.Canvas)
                            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card))
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
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
                                modifier = Modifier.offset(y = 1.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            AnimatedVisibility(
                                visible = updateAvailable,
                                enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)),
                                exit = fadeOut(tween(CalinoMotion.ContentExitMillis)),
                            ) {
                                TextButton(
                                    onClick = onUpdateClick,
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .semantics { contentDescription = updateAvailableLabel },
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) {
                                    Text(
                                        updateAvailableLabel,
                                        color = CalinoColors.Accent,
                                        style = CalinoTypography.labelSmall,
                                    )
                                }
                            }
                        }
                        SidebarMiniCalendar(
                            selectedDate = selectedDate,
                            onDateChanged = onDateChanged,
                        )
                        SidebarUpcomingTasks(
                            tasks = snapshot.tasks,
                            onTaskClick = onTaskClick,
                            onTaskComplete = onTaskComplete,
                            onTaskAction = onTaskAction,
                        )
                        SidebarSectionLabel(stringResource(R.string.cal_views_group))
                        SidebarNavGroup {
                            calendarItems.forEach { item ->
                                NavRow(item, selected = selectedRoute == item.route) {
                                    onRoute(item.route)
                                    onDismiss()
                                }
                            }
                        }
                        SidebarSectionDivider()
                        SidebarSectionLabel(stringResource(R.string.cal_organize_group))
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

internal data class SidebarCalendarRow(
    val accountId: String?,
    val calendar: CalinoCalendar,
    val enabled: Boolean = true,
)

/**
 * CalDAV rows come from the account store; webcal rows always come from the
 * snapshot, even when there is no CalDAV account. The empty-account branch is
 * fixture-only. Mixing webcal into it kept rename/colour in `remember` state
 * and threw them away when the sidebar left composition.
 */
internal fun sidebarCalendarRows(
    snapshot: CalinoSnapshot,
    accounts: List<CalDavAccount>,
    fixtureCalendarNames: Map<String, String> = emptyMap(),
    fixtureCalendarColors: Map<String, Long> = emptyMap(),
): List<SidebarCalendarRow> {
    val accountRows = accounts.flatMap { account ->
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
    val knownIds = accountRows.map { it.calendar.id }.toSet()
    val webcalRows = snapshot.calendars
        .filter { WebcalSubscription.isWebcalCalendarId(it.id) }
        .filter { it.id !in knownIds }
        .map { calendar ->
            SidebarCalendarRow(
                accountId = WebcalSubscription.AccountId,
                calendar = calendar,
            )
        }
    if (accountRows.isNotEmpty() || webcalRows.isNotEmpty()) {
        return accountRows + webcalRows
    }
    return snapshot.calendars.map { calendar ->
        SidebarCalendarRow(
            accountId = null,
            calendar = calendar.copy(
                name = fixtureCalendarNames[calendar.id] ?: calendar.name,
                color = fixtureCalendarColors[calendar.id] ?: calendar.color,
            ),
        )
    }
}

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
    val today = LocalCalinoNow.current.today
    val weekStart = preferences.weekStart
    val locale = LocalCalinoLocale
    val expanded = preferences.sidebarCalendarExpanded
    var miniMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val cardShape = RoundedCornerShape(12.dp)
    val monthYearFormatter = localizedDateFormatter("MMMM yyyy")
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(CalinoMotion.ContentEnterMillis),
        label = "sidebar calendar chevron",
    )
    val calendarSidebarLabel = stringResource(R.string.cal_sidebar_calendar)
    val collapsedLabel = stringResource(R.string.cal_collapsed)
    val previousMonthLabel = stringResource(R.string.cal_previous_month_in_sidebar)
    val todaySidebarLabel = stringResource(R.string.cal_today_in_sidebar)
    val nextMonthLabel = stringResource(R.string.cal_next_month_in_sidebar)
    val collapseCalendarLabel = stringResource(R.string.cal_collapse_calendar)
    val fullDateFormatter = localizedDateFormatter("EEEE, MMMM d, yyyy")
    Column(
        Modifier
            .fillMaxWidth()
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
                            contentDescription = calendarSidebarLabel
                            stateDescription = collapsedLabel
                            role = Role.Button
                        }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.cal_calendar_caps),
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
                            contentDescription = previousMonthLabel
                            role = Role.Button
                        },
                    ) { Text("‹", fontSize = 22.sp, color = CalinoColors.Ink2) }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onDateChanged(today); miniMonth = YearMonth.from(today) }
                            .semantics {
                                contentDescription = todaySidebarLabel
                                role = Role.Button
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.cal_calendar_caps),
                                style = CalinoTypography.labelSmall.copy(fontSize = 8.sp, letterSpacing = .9.sp, color = CalinoColors.Ink3),
                            )
                            Text(
                                miniMonth.format(monthYearFormatter),
                                style = CalinoTypography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                            )
                        }
                    }
                    IconButton(
                        onClick = { miniMonth = miniMonth.plusMonths(1) },
                        modifier = Modifier.size(44.dp).clearAndSetSemantics {
                            contentDescription = nextMonthLabel
                            role = Role.Button
                        },
                    ) { Text("›", fontSize = 22.sp, color = CalinoColors.Ink2) }
                    IconButton(
                        onClick = { preferences.setSidebarCalendarExpanded(false) },
                        modifier = Modifier.size(44.dp).clearAndSetSemantics {
                            contentDescription = collapseCalendarLabel
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
            weekdayLetters(weekStart, locale).forEach {
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
                                                contentDescription = date.format(fullDateFormatter)
                                                this.selected = date == selectedDate
                                            }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                val selected = date == selectedDate
                                val isToday = date == today
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) CalinoColors.Accent else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        color = if (selected) CalinoColors.OnAccent else if (isToday) CalinoColors.Accent else CalinoColors.Ink2,
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
private fun SidebarUpcomingTasks(
    tasks: List<CalTask>,
    onTaskClick: (CalTask) -> Unit,
    onTaskComplete: (CalTask, Boolean) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(CalinoMotion.ContentEnterMillis),
        label = "upcoming tasks chevron",
    )
    val upcoming = tasks
        .filter { !it.done }
        .filter { it.parentTaskId == null }
        .sortedBy { it.due ?: LocalDate.MAX }
        .take(10)
    val cardShape = RoundedCornerShape(12.dp)
    val upcomingTasksLabel = stringResource(R.string.cal_upcoming_tasks_sidebar)
    val expandedLabel = stringResource(R.string.cal_expanded)
    val collapsedLabel = stringResource(R.string.cal_collapsed)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (CalinoColors.isDark) CalinoColors.Side else CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line, cardShape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(7.dp))
                .clickable { expanded = !expanded }
                .semantics {
                    contentDescription = upcomingTasksLabel
                    stateDescription = if (expanded) expandedLabel else collapsedLabel
                    role = Role.Button
                }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.cal_upcoming_tasks_caps),
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
                    .graphicsLayer { rotationZ = chevronRotation },
                contentDescription = null,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(CalinoMotion.ContentEnterMillis)) + fadeIn(tween(CalinoMotion.ContentEnterMillis)),
            exit = shrinkVertically(tween(CalinoMotion.ContentExitMillis)) + fadeOut(tween(CalinoMotion.ContentExitMillis)),
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
                        stringResource(R.string.cal_no_upcoming_tasks),
                        color = CalinoColors.Ink3,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    )
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
) {
    var editingCalendarKey by remember { mutableStateOf<String?>(null) }
    var editedCalendarName by remember { mutableStateOf("") }
    var fixtureCalendarNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var fixtureCalendarColors by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val rows = sidebarCalendarRows(
        snapshot = snapshot,
        accounts = accounts,
        fixtureCalendarNames = fixtureCalendarNames,
        fixtureCalendarColors = fixtureCalendarColors,
    )

    val calendarsLabel = stringResource(R.string.cal_calendars_caps)
    val visibleCount = rows.count { row ->
        if (row.accountId == null) row.calendar.id !in fixtureHiddenCalendarIds else row.calendar.visible
    }
    val visibleCountLabel = stringResource(R.string.cal_visible_count, visibleCount, rows.size)
    val syncAllLabel = stringResource(R.string.cal_sync_all_calendars)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            calendarsLabel,
            style = CalinoTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.1.sp, color = CalinoColors.Ink3),
            modifier = Modifier.weight(1f),
        )
        Text(visibleCountLabel, color = CalinoColors.Ink3, fontSize = 12.sp)
        // One sync control for the section, and it says what it does. A bare
        // accent glyph floating at the end of the header read as a stray mark
        // rather than a button; the same chrome the rest of the sidebar uses --
        // a quiet rounded field, an icon at label weight -- makes it one.
        Row(
            Modifier
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CalinoColors.Side)
                .clickable(role = Role.Button, onClick = onSyncAll)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = syncAllLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                CalinoIcons.Refresh,
                contentDescription = null,
                tint = CalinoColors.Ink2,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(R.string.cal_sync),
                style = CalinoTypography.labelSmall.copy(fontSize = 11.sp, letterSpacing = .4.sp, color = CalinoColors.Ink2),
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
        val showCalendarLabel = stringResource(R.string.cal_show_calendar, row.calendar.name)
        val visibilityState = stringResource(if (visible) R.string.cal_visible else R.string.cal_hidden)
        val saveLabel = stringResource(R.string.cal_save)
        val moreOptionsLabel = stringResource(R.string.cal_more_options_for, row.calendar.name)
        val syncNowLabel = stringResource(R.string.cal_sync_now)
        val renameCalendarLabel = stringResource(R.string.cal_rename_calendar)
        val hideTasksLabel = stringResource(R.string.cal_hide_calendar_tasks)
        val showTasksLabel = stringResource(R.string.cal_show_calendar_tasks)
        val exportIcsLabel = stringResource(R.string.cal_export_ics)
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
                            contentDescription = showCalendarLabel
                            stateDescription = visibilityState
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
                    ) { Text(saveLabel, color = CalinoColors.Accent) }
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
                    Icon(CalinoIcons.More, contentDescription = moreOptionsLabel, tint = CalinoColors.Ink3, modifier = Modifier.size(18.dp))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // Syncing one calendar used to be a glyph on every row, which
                // gave the list a column of identical buttons nobody was
                // looking for. It is a per-calendar action like the others, so
                // it lives where the other per-calendar actions live.
                if (row.accountId != null) {
                    DropdownMenuItem(
                        text = { Text(syncNowLabel) },
                        onClick = {
                            menuOpen = false
                            onSyncCalendar(row.accountId, row.calendar.id)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(renameCalendarLabel) },
                    onClick = {
                        menuOpen = false
                        editingCalendarKey = key
                        editedCalendarName = row.calendar.name
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (tasksVisible) hideTasksLabel else showTasksLabel) },
                    onClick = {
                        menuOpen = false
                        if (row.accountId == null) onToggleFixtureCalendarTasks(row.calendar.id, !tasksVisible)
                        else onToggleCalendarTasks(row.accountId, row.calendar.id, !tasksVisible)
                    },
                )
                listOf(
                    0xFFC2697F to R.string.cal_color_rose,
                    0xFF5B7FB5 to R.string.cal_color_blue,
                    0xFF5D9A78 to R.string.cal_color_green,
                    0xFFBF944E to R.string.cal_color_gold,
                ).forEach { (color, nameResource) ->
                    val name = stringResource(nameResource)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cal_use_color, name)) },
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
                DropdownMenuItem(text = { Text(exportIcsLabel) }, onClick = { menuOpen = false })
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
@Composable
fun pockRouteLabel(route: PockRoute): String = stringResource(when (route) {
    PockRoute.Day -> R.string.cal_route_month
    PockRoute.Year -> R.string.cal_route_year
    PockRoute.Range -> R.string.cal_route_range
    PockRoute.Agenda -> R.string.cal_route_agenda
    PockRoute.Accounts -> R.string.cal_route_calendars
    PockRoute.Tasks -> R.string.cal_route_tasks
    PockRoute.Journal -> R.string.cal_route_journal
    PockRoute.Contacts -> R.string.cal_route_contacts
    PockRoute.Settings -> R.string.cal_route_settings
    PockRoute.Detail -> R.string.cal_route_event
    PockRoute.TaskDetail -> R.string.cal_route_task
    PockRoute.QuickAdd -> R.string.cal_route_quick_add
    PockRoute.Notifications -> R.string.cal_route_notifications
})

/**
 * The stable name a route is stored under (a data object prints as its own
 * name), for preferences that list routes.
 */
fun pockRouteKey(route: PockRoute): String = route.toString()

/**
 * The glyph a route goes by. The sidebar and the root pill's view button,
 * menu and dock all draw from here, for the same reason as [pockRouteLabel].
 */
fun pockRouteIcon(route: PockRoute): ImageVector = when (route) {
    PockRoute.Year -> CalinoIcons.CalendarYear
    PockRoute.Range -> CalinoIcons.CalendarRange
    PockRoute.Agenda -> CalinoIcons.AgendaList
    PockRoute.Tasks -> CalinoIcons.ListChecks
    PockRoute.Journal -> CalinoIcons.BookOpen
    PockRoute.Contacts -> CalinoIcons.Users
    PockRoute.Settings -> CalinoIcons.Settings
    else -> CalinoIcons.Calendar
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
    val selectedLabel = stringResource(R.string.cal_selected)
    val notSelectedLabel = stringResource(R.string.cal_not_selected_state)
    val rowDescription = if (selected) stringResource(R.string.cal_selected_value, item.label, selectedLabel) else item.label
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .calinoPressable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                stateDescription = if (selected) selectedLabel else notSelectedLabel
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
