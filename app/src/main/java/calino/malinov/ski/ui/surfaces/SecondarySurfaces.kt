package calino.malinov.ski.ui.surfaces

import androidx.compose.ui.graphics.vector.ImageVector
import calino.malinov.ski.ui.components.CalinoActionMenu
import calino.malinov.ski.ui.components.CalinoActionMenuDivider
import calino.malinov.ski.ui.components.CalinoActionMenuItem
import calino.malinov.ski.ui.components.CalinoActionMenuTile
import calino.malinov.ski.ui.components.CalinoActionMenuTiles
import calino.malinov.ski.util.meetingLink
import calino.malinov.ski.util.MeetingLink
import android.net.Uri
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.caldav.normalizedCalendarAddress
import calino.malinov.ski.ui.components.EditorReveal
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.R
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.EventAttachment
import calino.malinov.ski.data.model.upcomingOccurrences
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.model.occurrenceStartCovering
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.EditorDraft
import calino.malinov.ski.data.model.blankEditorDraft
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.RecurrenceFreq
import calino.malinov.ski.data.model.recurrenceDaysOf
import calino.malinov.ski.data.model.recurrenceFreqOf
import calino.malinov.ski.data.model.recurrenceRule
import calino.malinov.ski.util.formatRecurrenceRule
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.asUpdate
import calino.malinov.ski.data.model.WebcalSubscription
import calino.malinov.ski.platform.AndroidCalendarId
import calino.malinov.ski.data.parser.PocQuickAddKind
import calino.malinov.ski.data.parser.parseQuickAdd
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.state.FixtureNow
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.state.TaskTree
import calino.malinov.ski.state.orderTasksByDue
import calino.malinov.ski.ui.components.taskNestIndent
import calino.malinov.ski.ui.components.rememberDatePicker
import calino.malinov.ski.ui.components.rememberTimePicker
import calino.malinov.ski.ui.components.WhenHero
import calino.malinov.ski.ui.components.HeroMasthead
import calino.malinov.ski.ui.components.TaskNestStep
import calino.malinov.ski.state.nestingLinesFor
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.formatCalinoDuration
import calino.malinov.ski.util.LocalCalinoLocale
import calino.malinov.ski.util.localizedDateFormatter
import calino.malinov.ski.util.formatRecurrenceSummary
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.ui.components.calinoPressable
import calino.malinov.ski.design.eventTint
import calino.malinov.ski.qa.TaskBucket
import calino.malinov.ski.qa.taskBucket
import calino.malinov.ski.ui.components.BottomDetailOverlay
import calino.malinov.ski.ui.components.LocalCalinoSurfaceOrigin
import calino.malinov.ski.ui.components.calinoSurfaceEdgeFade
import calino.malinov.ski.ui.components.calinoSurfaceShadowBleed
import calino.malinov.ski.ui.components.AdaptiveDetailCard
import calino.malinov.ski.ui.components.AdaptiveSurfaceHost
import calino.malinov.ski.ui.components.BottomDetailCard
import calino.malinov.ski.ui.components.DetailCardSurface
import calino.malinov.ski.ui.components.LocalCalinoPillLane
import calino.malinov.ski.ui.components.LocalCalinoSurfaceMode
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.CalinoProgressSlider
import calino.malinov.ski.ui.components.CalinoScrim
import calino.malinov.ski.ui.components.PromptButton
import androidx.compose.ui.semantics.paneTitle
import calino.malinov.ski.ui.components.CalinoSheet
import calino.malinov.ski.ui.components.CalinoMarkdown
import calino.malinov.ski.ui.components.CalinoMarkdownEditor
import calino.malinov.ski.ui.components.toggleCalinoMarkdownTask
import calino.malinov.ski.ui.components.ModalActionPill
import calino.malinov.ski.ui.components.ModalPillActionTone
import calino.malinov.ski.ui.components.MenuButton
import calino.malinov.ski.ui.components.CompactSegmentedControl
import calino.malinov.ski.ui.components.EventLocationButton
import calino.malinov.ski.ui.components.calinoLongPressDrag
import calino.malinov.ski.util.formatRecurrenceSummary
import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.state.CalinoSurfaceMode
import calino.malinov.ski.util.startOfWeek
import calino.malinov.ski.state.isRecurringTask
import calino.malinov.ski.state.isSometimeThisWeek
import calino.malinov.ski.state.isWeekTask
import calino.malinov.ski.state.shouldSplit
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.compositeOver
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class TaskDropBounds(val top: Float, val bottom: Float)

/** Small, stable routes to make these surfaces easy to wire into a pager later. */
sealed interface PockRoute {
    data object Day : PockRoute
    data object Year : PockRoute
    data object Range : PockRoute
    data object Agenda : PockRoute
    data object Detail : PockRoute
    data object TaskDetail : PockRoute
    data object Tasks : PockRoute
    data object Journal : PockRoute
    data object Contacts : PockRoute
    data object Settings : PockRoute
    data object Accounts : PockRoute
    data object QuickAdd : PockRoute
    data object Notifications : PockRoute
}
enum class QuickAddKind { Event, Task, Journal }
enum class TaskFilter { All, Active, Completed }

enum class TaskMenuAction {
    Schedule,
    ThisWeek,
    Edit,
    AddSubtask,
    Promote,
    Today,
    Tomorrow,
    NextWeek,
    ToggleDone,
    Duplicate,
    ConvertToEvent,
    Delete,
}

enum class EventMenuAction {
    Edit,
    Share,
    Duplicate,
    ConvertToTask,
    Delete,
}

/** One action list shared by ledger rows and calendar task rows. */
@Composable
fun TaskActionMenu(
    task: CalTask,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (TaskMenuAction) -> Unit,
    hasSubtasks: Boolean = false,
) {
    CalinoActionMenu(expanded = expanded, onDismiss = onDismiss) {
        fun run(action: TaskMenuAction) {
            onDismiss()
            onAction(action)
        }

        @Composable
        fun action(action: TaskMenuAction, text: String, icon: ImageVector? = null, enabled: Boolean = true) {
            CalinoActionMenuItem(text = text, icon = icon, enabled = enabled, danger = action == TaskMenuAction.Delete) { run(action) }
        }
        CalinoActionMenuTiles {
            CalinoActionMenuTile(stringResource(R.string.ed_menu_edit), CalinoIcons.Edit, emphasized = true) { run(TaskMenuAction.Edit) }
            CalinoActionMenuTile(if (task.done) stringResource(R.string.ed_menu_reopen) else stringResource(R.string.ed_menu_done), CalinoIcons.Check) { run(TaskMenuAction.ToggleDone) }
            CalinoActionMenuTile(stringResource(R.string.ed_menu_duplicate), CalinoIcons.Copy) { run(TaskMenuAction.Duplicate) }
        }
        action(TaskMenuAction.Schedule, stringResource(R.string.ed_menu_schedule), CalinoIcons.Clock)
        action(TaskMenuAction.ThisWeek, stringResource(R.string.ed_menu_move_this_week), CalinoIcons.CalendarRange,
            task.recurrence == null && task.recurrenceId == null && task.recurrenceDate == null)
        action(TaskMenuAction.Today, stringResource(R.string.ed_menu_move_today), CalinoIcons.Calendar, !task.done)
        action(TaskMenuAction.Tomorrow, stringResource(R.string.ed_menu_move_tomorrow), CalinoIcons.ChevronRight, !task.done)
        action(TaskMenuAction.NextWeek, stringResource(R.string.ed_menu_move_next_week), CalinoIcons.CalendarRange, !task.done)
        if (!hasSubtasks) action(TaskMenuAction.AddSubtask, stringResource(R.string.ed_task_add_subtask), CalinoIcons.Plus)
        if (task.parentTaskId != null) action(TaskMenuAction.Promote, stringResource(R.string.ed_menu_move_top_level), CalinoIcons.AgendaList)
        action(TaskMenuAction.ConvertToEvent, stringResource(R.string.ed_menu_convert_event), CalinoIcons.Clock, task.due != null)
        CalinoActionMenuDivider()
        CalinoActionMenuItem(stringResource(R.string.ed_menu_delete), icon = CalinoIcons.Trash, danger = true, description = stringResource(R.string.ed_task_delete)) { run(TaskMenuAction.Delete) }
    }
}

@Composable
fun EventActionMenu(
    event: CalEvent,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (EventMenuAction) -> Unit,
) {
    CalinoActionMenu(expanded = expanded, onDismiss = onDismiss) {
        fun run(action: EventMenuAction) {
            onDismiss()
            onAction(action)
        }
        CalinoActionMenuTiles {
            CalinoActionMenuTile(stringResource(R.string.ed_menu_edit), CalinoIcons.Edit, emphasized = true) { run(EventMenuAction.Edit) }
            CalinoActionMenuTile(stringResource(R.string.ed_menu_duplicate), CalinoIcons.Copy) { run(EventMenuAction.Duplicate) }
            CalinoActionMenuTile(stringResource(R.string.ed_menu_share), CalinoIcons.Share) { run(EventMenuAction.Share) }
        }
        CalinoActionMenuItem(
            stringResource(R.string.ed_menu_convert_task),
            icon = CalinoIcons.CheckSquare,
            enabled = event.recurrence == null && event.recurrenceId == null && event.recurrenceDate == null,
        ) { run(EventMenuAction.ConvertToTask) }
        CalinoActionMenuItem(stringResource(R.string.ed_menu_delete), icon = CalinoIcons.Trash, danger = true, description = stringResource(R.string.ed_event_delete)) { run(EventMenuAction.Delete) }
    }
}

private const val CompletionVisualSettleMillis = 400L

/**
 * The fixture anchor, for sample records and preview defaults only.
 *
 * Anything that means "today" reads [LocalCalinoNow] instead -- this used to be
 * a fourth frozen copy of the date and it made every Today/Tomorrow control
 * point at May 2026 even with a real account connected.
 */
private val May18 = FixtureNow.today

@Composable @ReadOnlyComposable
private fun eventColor(event: CalEvent) = CalinoColors.forEvent(Color(event.color))

@Composable @ReadOnlyComposable
private fun taskColor(task: CalTask) = CalinoColors.forEvent(Color(task.color))

private fun recurrenceSummary(context: android.content.Context, event: CalEvent): String = formatRecurrenceSummary(context, event)

fun QuickAddKind.toParserKind(): PocQuickAddKind = when (this) {
    QuickAddKind.Event -> PocQuickAddKind.Event
    QuickAddKind.Task -> PocQuickAddKind.Task
    QuickAddKind.Journal -> PocQuickAddKind.Journal
}

private fun dayEventsFor(events: List<CalEvent>, date: LocalDate): List<CalEvent> =
    events.filter { event ->
        event.occursOn(date)
    }

/** Materialised instances keep recurrence readable; the UI never exposes RRULE text. */
@Composable
private fun label(text: String, modifier: Modifier = Modifier) = Text(text.uppercase(LocalCalinoLocale), modifier, style = CalinoTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, color = CalinoColors.Ink3), fontWeight = FontWeight.Bold)
@Composable
private fun AgendaCard(event: CalEvent, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    val color = eventColor(event)
    val timeFormat = LocalTimeFormat
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp), colors = CardDefaults.cardColors(CalinoColors.Panel), border = androidx.compose.foundation.BorderStroke(1.dp, CalinoColors.Ink.copy(alpha = .07f))) {
        Row(Modifier.padding(vertical = 10.dp, horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(3.dp)).background(color)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.start?.let { timeFormat.format(it, LocalCalinoLocale) } ?: context.getString(R.string.ed_all_day), style = CalinoTypography.labelSmall, color = CalinoColors.Ink2)
                Text(event.title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                event.location?.let { Text(it, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3) }
            }
            Text("›", fontSize = 25.sp, color = CalinoColors.Ink3)
        }
    }
}

@Composable
private fun JournalAgendaCard(entry: JournalEntry, onClick: () -> Unit = {}) {
    val title = entry.title.ifBlank { stringResource(R.string.ed_journal_untitled_note) }
    val openDescription = stringResource(R.string.ed_journal_open_entry, title)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(CalinoColors.AccentSoft.copy(.58f))
            .border(1.dp, CalinoColors.Accent.copy(.16f), RoundedCornerShape(11.dp))
            .semantics { contentDescription = openDescription }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("✦", color = CalinoColors.Accent, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ed_journal_title).uppercase(LocalCalinoLocale), style = CalinoTypography.labelSmall, color = CalinoColors.Accent)
            Text(title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            Text(entry.body, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * Measured heights of the day card's parts, in dp, used to ask the host for a
 * card that fits the day instead of one that fills the pane. They are the
 * rendered heights of [AgendaCard], [JournalAgendaCard] and the card's own
 * handle/header/padding, so a change to any of those belongs here too.
 */
private const val DayRowSpacing = 9
private const val DayCardChrome = 112
private const val EmptyDayRowHeight = 62

/** A transparent modal layer over the calendar supplied by the host screen. */
@Composable
fun DayModalSurface(
    date: LocalDate = May18,
    events: List<CalEvent> = fixtureEvents(),
    journals: List<JournalEntry> = emptyList(),
    onDismiss: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEvent: (CalEvent) -> Unit = {},
    onJournal: (JournalEntry) -> Unit = {},
    onDateChanged: (LocalDate) -> Unit = {},
    visible: Boolean = true,
) {
    val dateFormat = localizedDateFormatter("EEE, d MMM")
    val dayDismiss = stringResource(R.string.ed_day_dismiss)
    val todayLabel = stringResource(R.string.ed_day_today)
    val nothingScheduled = stringResource(R.string.ed_day_nothing_scheduled)
    val today = LocalCalinoNow.current.today
    var displayedDate by remember(date) { mutableStateOf(date) }
    var shown by remember { mutableStateOf(true) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var dragAnimationJob by remember { mutableStateOf<Job?>(null) }
    var pendingCloseAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val horizontalThresholdPx = with(density) { 72.dp.toPx() }
    val dismissThresholdPx = with(density) { 112.dp.toPx() }
    val dismissDistancePx = with(density) { 720.dp.toPx() }
    val axisThresholdPx = with(density) { 8.dp.toPx() }
    val horizontalDragLimitPx = with(density) { 180.dp.toPx() }

    fun animateDragTo(targetX: Float, targetY: Float, onFinished: (() -> Unit)? = null) {
        dragAnimationJob?.cancel()
        val startX = dragX
        val startY = dragY
        dragAnimationJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = CalinoMotion.gestureReturn(),
            ) { value, _ ->
                dragX = startX + (targetX - startX) * value
                dragY = startY + (targetY - startY) * value
            }
            dragX = targetX
            dragY = targetY
            dragAnimationJob = null
            onFinished?.invoke()
        }
    }

    // Keep the host overlay until the sheet's short exit transition has
    // finished. This also gives add and event actions the same clean handoff.
    LaunchedEffect(shown) {
        if (!shown) {
            delay(240)
            val action = pendingCloseAction
            pendingCloseAction = null
            action?.invoke()
        }
    }
    val closeAfterAnimation: (() -> Unit) -> Unit = { action ->
        if (shown) {
            pendingCloseAction = action
            shown = false
        }
    }
    LaunchedEffect(visible) {
        if (!visible) closeAfterAnimation(onDismiss)
    }
    val dismiss: () -> Unit = { closeAfterAnimation(onDismiss) }

    // A quiet day should not open a card the height of a busy one. The rows
    // here are fixed-height by construction, so the card can ask for exactly
    // what the displayed day needs and let the host cap it at .86 of the pane
    // when the day overflows. Sizing on displayedDate rather than the settled
    // page lets the card reach the incoming day's height during the swipe.
    val preferredDayHeight = run {
        val pageEvents = dayEventsFor(events, displayedDate)
        val pageJournals = journals.filter { it.date == displayedDate }
        val rows = pageEvents.sumOf { if (it.location.isNullOrBlank()) 60 else 63 } +
            pageJournals.sumOf { entry ->
                // The body is capped at two lines; one more line of it is the
                // only thing that changes a journal row's height.
                if (entry.body.length > 42) 84 else 65
            }
        val rowCount = pageEvents.size + pageJournals.size
        val body = if (rowCount == 0) EmptyDayRowHeight else rows + (rowCount - 1) * DayRowSpacing
        (DayCardChrome + body + CalinoSpacing.PillClearance.value.toInt()).dp
    }

    AdaptiveSurfaceHost(
        kind = CalinoSurfaceKind.Day,
        visible = shown,
        onDismiss = dismiss,
        preferredSurfaceHeight = preferredDayHeight,
        contentDescription = dayDismiss,
        // The day's actions belong in the same lane as every other modal's,
        // so the root add pill morphs into them instead of the card growing a
        // button row of its own.
        pill = {
            ModalActionPill(
                addLabel = stringResource(R.string.ed_day_add_on, displayedDate.format(dateFormat)),
                morphFromAddPill = true,
                inPillLane = true,
                expanded = shown,
                cancelLabel = stringResource(R.string.ed_day_close),
                onCancel = dismiss,
                cancelDescription = stringResource(R.string.ed_day_close),
                primaryLabel = stringResource(R.string.ed_day_new_event),
                onPrimary = { closeAfterAnimation(onAdd) },
                primaryDescription = stringResource(R.string.ed_day_new_event_on, displayedDate.format(dateFormat)),
            )
        },
    ) { panelModifier ->
        val mode = LocalCalinoSurfaceMode.current
        val layoutDirection = LocalLayoutDirection.current
        val outwardSign = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
        val sideDismissLanePx = with(density) { 72.dp.toPx() }
        Box(
            panelModifier.pointerInput(mode, layoutDirection) {
                // Bottom sheets and centered floating windows keep the same
                // two-axis contract. An end panel reserves its header lane
                // for outward dismissal so body swipes can still page the
                // selected day.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val pointerId = down.id
                    var lastPosition = down.position
                    var totalX = 0f
                    var totalY = 0f
                    var horizontal = false
                    var axisDecided = false
                    var completed = false
                    var sideDismissStarted = false
                    val startDragX = dragX
                    val startDragY = dragY
                    val sideDismissAllowed = mode == CalinoSurfaceMode.EndPanel && down.position.y <= sideDismissLanePx
                    dragAnimationJob?.cancel()

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
                        if (axisDecided && horizontal) {
                            val outwardTravel = totalX * outwardSign
                            if (sideDismissAllowed && outwardTravel > 0f) {
                                sideDismissStarted = true
                                change.consume()
                                dragX = (startDragX + outwardTravel * outwardSign)
                                    .coerceIn(-horizontalDragLimitPx, horizontalDragLimitPx)
                                dragY = 0f
                            } else if (!sideDismissStarted) {
                                change.consume()
                                dragX = (startDragX + totalX).coerceIn(-horizontalDragLimitPx, horizontalDragLimitPx)
                                dragY = startDragY
                            }
                        } else if (
                            (mode == CalinoSurfaceMode.BottomSheet || mode == CalinoSurfaceMode.FloatingWindow) &&
                            axisDecided && totalY > 0f
                        ) {
                            // A downward dismissal can begin anywhere on the
                            // sheet, including inside the event list.
                            change.consume()
                            dragX = startDragX
                            dragY = (startDragY + totalY).coerceAtLeast(0f)
                        }
                    }

                    if (completed) {
                        val outwardDrag = dragX * outwardSign
                        val sideDismiss = sideDismissStarted && outwardDrag > dismissThresholdPx
                        val horizontalPage = !sideDismissStarted && abs(dragX) > horizontalThresholdPx && abs(dragX) > dragY
                        val swipeDown = (mode == CalinoSurfaceMode.BottomSheet || mode == CalinoSurfaceMode.FloatingWindow) &&
                            dragY > dismissThresholdPx && dragY > abs(dragX)
                        when {
                            swipeDown || sideDismiss -> {
                                // The host owns the remaining exit travel;
                                // do not spring the child to a second fixed
                                // distance before handing off.
                                dragAnimationJob?.cancel()
                                dismiss()
                            }
                            horizontalPage -> {
                                displayedDate = displayedDate.plusDays(if (dragX < 0f) 1 else -1)
                                onDateChanged(displayedDate)
                                animateDragTo(0f, 0f)
                            }
                            else -> animateDragTo(0f, 0f)
                        }
                    } else {
                        animateDragTo(0f, 0f)
                    }
                }
            },
        ) {
            // The same shell the detail and editor cards use: rounded on all
            // four corners, lifted off the window edges, with the shared
            // handle. The day was the one modal still glued to the bottom.
            DetailCardSurface(
                Modifier
                    .fillMaxSize()
                    .calinoSurfaceShadowBleed(
                        horizontal = if (mode == CalinoSurfaceMode.BottomSheet) 10.dp else 0.dp,
                    )
                    .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) },
            ) { _ ->
                AnimatedContent(
                    targetState = displayedDate,
                    transitionSpec = {
                        val direction = if (targetState.isAfter(initialState)) 1 else -1
                        slideInHorizontally(tween(220)) { direction * it } togetherWith
                            slideOutHorizontally(tween(180)) { -direction * it }
                    },
                    label = "day modal pager",
                ) { pageDate ->
                    val dayEvents = dayEventsFor(events, pageDate)
                    val dayJournals = journals.filter { it.date == pageDate }
                    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
                        // Closing is the pill's left lane, like every other
                        // modal; a second × in the header was the day card
                        // answering for itself.
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(pageDate.format(dateFormat), style = CalinoTypography.titleLarge)
                            val count = pluralStringResource(R.plurals.ed_day_event_count, dayEvents.size, dayEvents.size)
                            label(if (pageDate == today) stringResource(R.string.ed_day_today_count, count) else count)
                        }
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(DayRowSpacing.dp),
                            // The pill floats over the tail of the list now,
                            // so the last chip needs the lane's clearance.
                            contentPadding = PaddingValues(bottom = CalinoSpacing.PillClearance),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (dayEvents.isEmpty()) {
                                item(key = "empty:$pageDate") {
                                    AnimatedVisibility(visible = true, enter = fadeIn(tween(160)) + expandVertically(tween(180))) {
                                        Text(nothingScheduled, style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3, modifier = Modifier.padding(20.dp))
                                    }
                                }
                            }
                            items(dayEvents, key = { it.id }) { event ->
                                AnimatedVisibility(visible = true, enter = fadeIn(tween(160)) + expandVertically(tween(180))) {
                                    AgendaCard(event) { closeAfterAnimation { onEvent(event) } }
                                }
                            }
                            items(dayJournals, key = { "journal:${it.id}" }) { journal ->
                                AnimatedVisibility(visible = true, enter = fadeIn(tween(160)) + expandVertically(tween(180))) {
                                    JournalAgendaCard(journal) { closeAfterAnimation { onJournal(journal) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Editorial event detail with only populated metadata rows and materialised recurrence. */
@Composable
fun EventDetailSurface(
    event: CalEvent = fixtureEvents().first { it.id == "evt-design" },
    readOnly: Boolean = false,
    onBack: () -> Unit = {},
    onPrimary: () -> Unit = {},
    occurrenceDate: LocalDate? = null,
    events: List<CalEvent> = listOf(event),
    onEventSelected: (CalEvent) -> Unit = {},
    onEditEvent: (CalEvent) -> Unit = { onPrimary() },
    onDeleteEvent: (CalEvent, RecurrenceEditScope) -> Unit = { _, _ -> },
    onEventAction: (EventMenuAction, CalEvent) -> Unit = { _, _ -> },
    onInlineSave: suspend (CalEvent, NewEvent, RecurrenceEditScope) -> Boolean = { _, _, _ -> false },
    selfAddresses: Set<String> = emptySet(),
    canRespond: Boolean = false,
    onRespond: suspend (CalEvent, String, RecurrenceEditScope) -> Boolean = { _, _, _ -> false },
    readOnlyForEvent: (CalEvent) -> Boolean = { readOnly },
    selfAddressesForEvent: (CalEvent) -> Set<String> = { selfAddresses },
    canRespondToEvent: (CalEvent) -> Boolean = { canRespond },
    localReminders: (CalEvent) -> List<Reminder>? = { null },
    onLocalReminders: ((CalEvent, List<Reminder>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val eventDateFormat = localizedDateFormatter("EEE, d MMM")
    var shown by remember { mutableStateOf(true) }
    var pendingCloseAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(shown) {
        if (!shown) {
            delay(220)
            pendingCloseAction?.invoke()
        }
    }
    val closeAfterAnimation: (() -> Unit) -> Unit = { action ->
        if (shown) {
            pendingCloseAction = action
            shown = false
        }
    }

    val initialPage = remember { events.indexOfFirst { it.id == event.id }.coerceAtLeast(0) }
    val pager = rememberPagerState(
        initialPage = initialPage,
        pageCount = { events.size },
    )
    // Taken once, as the surface first composes: the card grows out of the
    // event that was tapped. It only returns there while that event is still
    // the one showing -- after a swipe to a neighbour the tapped card is no
    // longer what this surface is about.
    val originRecorder = LocalCalinoSurfaceOrigin.current
    val growFromEvent = LocalCalinoPreferences.current.growDetailFromEvent
    val origin = remember { originRecorder.take(event.id)?.takeIf { growFromEvent } }
    val currentSelectionCallback by rememberUpdatedState(onEventSelected)
    LaunchedEffect(pager, events) {
        snapshotFlow { pager.settledPage }.collect { page ->
            events.getOrNull(page)?.let(currentSelectionCallback)
        }
    }
    // Size for the page the gesture is heading toward, not the page that last
    // happened to occupy the snap position. PagerState exposes targetPage as
    // soon as it resolves the drag/fling destination, which gives the shared
    // card shell the whole swipe to reach the incoming event's height. Using
    // currentPage here made the shell begin resizing only at the page handoff,
    // so a short incoming card visibly shrank after it had arrived.
    val sizingEvent = events.getOrNull(pager.targetPage) ?: event
    val descriptionLines = sizingEvent.notes.orEmpty().lineSequence().sumOf { line ->
        ((line.length.coerceAtLeast(1) + 37) / 38)
    }.coerceAtLeast(1)
    val attendeeLines = if (sizingEvent.attendees.isEmpty()) 0 else {
        val length = sizingEvent.attendees.sumOf { it.name.ifBlank { it.email }.length + 2 }
        ((length + 37) / 38).coerceAtLeast(1)
    }
    val hasMeeting = meetingLink(sizingEvent) != null
    // Base includes the handle, the masthead with its when-hero, the
    // location and description rows, the divider, and the real pill
    // clearance. Add only what this event renders on top of that.
    val preferredPreviewHeight = (
        // An all-day hero is one row where a timed one carries a day line
        // under each clock face, so the card does not reserve room for a
        // second line that is never drawn.
        // Tracks the masthead's own height: the tint block was tightened, so
        // the card asks for less before its rows are counted.
        (if (sizingEvent.allDay) 368 else 398) +
            (descriptionLines - 1) * 22 +
            attendeeLines * 24 +
            (if (hasMeeting) 52 else 0) +
            sizingEvent.attachments.size * 54 +
            (if (sizingEvent.recurrence != null) 54 else 0) +
            (if (sizingEvent.reminders.isNotEmpty() ||
                (onLocalReminders != null && eventDetailReadOnly(readOnlyForEvent(sizingEvent), sizingEvent))) 54 else 0) +
            (if (sizingEvent.travelTimeMinutes != null) 54 else 0)
        // Matches CalinoSurfaceKind.EventPreviewCompact's own ceiling, which
        // clamps this anyway; asking for more here only hides that.
        ).coerceAtMost(560).dp
    var pillState by remember { mutableStateOf(EventPreviewPillState(false, false, {}, {}, {}, {}, {})) }
    // Derived, so the fade is switched on and off once per swipe instead of
    // recomposing the card on every frame of one.
    val paging by remember(pager) {
        derivedStateOf { pager.isScrollInProgress || pager.currentPageOffsetFraction != 0f }
    }
    val currentPageEvent = events.getOrNull(pager.currentPage) ?: event
    val currentPageReadOnly = readOnlyForEvent(currentPageEvent)
    BottomDetailOverlay(
        visible = shown,
        onDismiss = { closeAfterAnimation(onBack) },
        surfaceKind = CalinoSurfaceKind.EventPreviewCompact,
        preferredSurfaceHeight = preferredPreviewHeight,
        origin = origin.takeIf { pager.currentPage == initialPage },
        pill = {
            val state = pillState
            ModalActionPill(
                    addLabel = context.getString(R.string.ed_add_event),
                    morphFromAddPill = true,
                    inPillLane = true,
                    expanded = shown,
                    cancelLabel = context.getString(R.string.ed_cancel),
                    onCancel = { closeAfterAnimation(onBack) },
                    cancelDescription = context.getString(R.string.ed_close_event_preview),
                    // Null rather than disabled: a greyed-out trash on
                    // somebody else's calendar invites the question every
                    // time it is seen.
                    deleteLabel = context.getString(R.string.ed_delete).takeUnless { currentPageReadOnly },
                    onDelete = state.onDelete.takeUnless { currentPageReadOnly },
                    deleteDescription = context.getString(R.string.ed_delete_event),
                    deleteConfirmationActive = state.confirmingDelete,
                    onDeleteConfirmationChange = state.onDeletePromptChanged,
                    deleteHoldToConfirm = true,
                    onDeleteHold = state.onDeleteOccurrence,
                    secondaryLabel = context.getString(R.string.ed_open).takeUnless { currentPageReadOnly },
                    onSecondary = state.onOpen.takeUnless { currentPageReadOnly },
                    secondaryDescription = context.getString(R.string.ed_open_event),
                    primaryLabel = context.getString(R.string.ed_save),
                    primaryTone = ModalPillActionTone.Save,
                    onPrimary = state.onSave,
                    // Delete keeps its own lane whatever happens; Save is the
                    // one that appears, and only once the preview is dirty.
                    primaryVisible = state.dirty && !currentPageReadOnly,
                    primaryDescription = context.getString(R.string.ed_save_event_changes),
            )
        },
    ) { overlayModifier ->
        HorizontalPager(
            state = pager,
            key = { events[it].id },
            beyondViewportPageCount = 1,
            userScrollEnabled = shown,
            // The page that leaves is clipped by the pager at the panel's
            // leading edge; fade it out on that line instead of cutting it.
            modifier = overlayModifier.calinoSurfaceEdgeFade(active = paging),
        ) { page ->
            val pageEvent = events[page]
            val detailListState = rememberLazyListState()
            // A pager clips its pages along the scroll axis, so the card's
            // shadow has to fit inside this padding or it ends at a hard
            // vertical line. The host reserves the same bleed in the panel it
            // hands down, so widening the padding does not narrow the card.
            Box(Modifier.fillMaxSize().calinoSurfaceShadowBleed(horizontal = 10.dp)) {
                AdaptiveDetailCard(
                    visible = shown,
                    onDismiss = { closeAfterAnimation(onBack) },
                    modifier = Modifier.fillMaxSize(),
                    dismissDistance = 980.dp,
                    canStartDismiss = { !detailListState.canScrollBackward },
                    handleColor = eventTint(eventColor(pageEvent), .13f, CalinoColors.Panel),
                    allowDownwardDismissInEndPanel = true,
                ) { cardModifier ->
                    EventDetailContent(
                        pageEvent,
                        // Read per page, not per card: the pager can reach a
                        // neighbour in a different calendar from the one the
                        // card opened on.
                        // Imported CalendarContract events use the calendar's
                        // capability passed by the host; their id alone no
                        // longer makes them read-only. Subscriptions remain
                        // intrinsically read-only.
                        readOnly = eventDetailReadOnly(readOnlyForEvent(pageEvent), pageEvent),
                        // Only the page the card opened on is the occurrence
                        // that was tapped; a paged-to neighbour states its own
                        // date.
                        occurrenceDate = occurrenceDate.takeIf { pageEvent.id == event.id },
                        onBack = { closeAfterAnimation(onBack) },
                        onPrimary = { onEditEvent(pageEvent) },
                        onDeleteEvent = { target, scope ->
                            closeAfterAnimation { onDeleteEvent(target, scope) }
                        },
                        onInlineSave = onInlineSave,
                        selfAddresses = selfAddressesForEvent(pageEvent),
                        canRespond = canRespondToEvent(pageEvent),
                        onRespond = onRespond,
                        localReminders = localReminders(pageEvent),
                        onLocalReminders = onLocalReminders,
                        active = page == pager.currentPage,
                        listState = detailListState,
                        onPillState = { pillState = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventDetailContent(
    event: CalEvent,
    readOnly: Boolean,
    occurrenceDate: LocalDate?,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    onDeleteEvent: (CalEvent, RecurrenceEditScope) -> Unit,
    onInlineSave: suspend (CalEvent, NewEvent, RecurrenceEditScope) -> Boolean,
    selfAddresses: Set<String>,
    canRespond: Boolean,
    onRespond: suspend (CalEvent, String, RecurrenceEditScope) -> Boolean,
    localReminders: List<Reminder>?,
    onLocalReminders: ((CalEvent, List<Reminder>) -> Unit)?,
    active: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPillState: (EventPreviewPillState) -> Unit,
) {
    val context = LocalContext.current
    val eventDateFormat = localizedDateFormatter("EEE, d MMM")
    val eventTitleRequired = stringResource(R.string.ed_event_title_required)
    val eventEndDateInvalid = stringResource(R.string.ed_event_end_date_invalid)
    val eventEndTimeInvalid = stringResource(R.string.ed_event_end_time_invalid)
    val responseSaveError = stringResource(R.string.ed_response_save_error)
    val tint = eventTint(eventColor(event), .13f, CalinoColors.Panel)
    // An expansion of a series carries the master's DTSTART. Resolve the
    // occurrence shown by the tapped day; on day 2..n of a spanning event,
    // that is still the span's start rather than the day under the finger.
    val original = remember(event, occurrenceDate) {
        eventPreviewDraft(event).let { draft ->
            occurrenceDate?.let(event::occurrenceStartCovering)?.let(draft::withStartDate) ?: draft
        }
    }
    var draft by remember(event.id, event.etag, occurrenceDate) { mutableStateOf(original) }
    var savedDraft by remember(event.id, event.etag, occurrenceDate) { mutableStateOf(original) }
    var error by remember(event.id) { mutableStateOf<String?>(null) }
    // The card is height-capped by its surface kind, so the open list scrolls
    // inside it rather than growing it; the state stays local because nothing
    // outside this page can act on it.
    var occurrencesExpanded by remember(event.id) { mutableStateOf(false) }
    var saving by remember(event.id) { mutableStateOf(false) }
    var responding by remember(event.id) { mutableStateOf(false) }
    var responseScope by remember(event.id) { mutableStateOf(RecurrenceEditScope.All) }
    var responseError by remember(event.id) { mutableStateOf<String?>(null) }
    val ownAttendee = event.attendees.singleOrNull {
        normalizedCalendarAddress(it.email) in selfAddresses.map(::normalizedCalendarAddress)
    }
    var saveScope by remember(event.id) {
        mutableStateOf(if (event.providerRecurring || event.recurrenceId != null || event.recurrenceDate != null) RecurrenceEditScope.This else RecurrenceEditScope.All)
    }
    var pendingOpen by remember(event.id) { mutableStateOf(false) }
    var scopePrompt by remember(event.id) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // Removing a repeating event is two questions, and they have to be asked
    // one at a time: which occurrences, and then whether to really do it. Both
    // used to hang off a single flag, so the scope chips and "Are you sure?"
    // appeared together and the pill was asking the person to confirm a choice
    // they had not made yet.
    var deleteStage by remember(event.id) { mutableStateOf(EventDeleteStage.None) }
    val confirmDelete = deleteStage != EventDeleteStage.None
    var deleteScope by remember(event.id, event.recurrenceId, event.recurrenceDate) {
        mutableStateOf(defaultEventDeleteScope(event))
    }
    val dirty = draft != savedDraft && !readOnly

    /**
     * The only way the draft changes.
     *
     * One gate rather than six call sites each remembering to check. Letting
     * a person type into a field whose value can never be saved is a worse
     * answer than not taking the keystroke: the edit looks accepted right up
     * until it silently is not.
     */
    fun edit(change: (EventPreviewDraft) -> EventPreviewDraft) {
        if (readOnly) return
        draft = change(draft)
        error = null
    }

    fun save(openAfter: Boolean) {
        // The when-block is edited through pickers now, so there is no text to
        // reject: whatever the draft holds is already a real date and time.
        val validation = draft.validationError(
            eventTitleRequired,
            eventEndDateInvalid,
            eventEndTimeInvalid,
        )
        if (validation != null) { error = validation; return }
        if (isRecurringEvent(event) && !scopePrompt) {
            pendingOpen = openAfter
            scopePrompt = true
            return
        }
        saving = true
        error = null
        coroutineScope.launch {
            val saved = onInlineSave(event, draft.toNewEvent(event, saveScope), saveScope)
            saving = false
            if (saved) {
                savedDraft = draft
                scopePrompt = false
                if (openAfter || pendingOpen) onPrimary()
            }
        }
    }
    val openAction = { if (!saving) { if (dirty) save(true) else onPrimary() } }
    val saveAction = { if (!saving) save(false) }
    val deleteAction = { onDeleteEvent(event, deleteScope) }
    // Holding the trash skips both questions, so it answers the scope one the
    // safest way it can: a repeating event loses the occurrence in front of
    // the person, never the series. Before this it fired the card's current
    // scope, which defaults to the whole series for a master event -- the
    // largest possible blast radius from the gesture that asks the fewest
    // questions.
    val deleteOccurrenceAction = {
        onDeleteEvent(event, if (isRecurringEvent(event)) RecurrenceEditScope.This else deleteScope)
    }
    val pickDate = rememberDatePicker({ draft.date }) { picked -> edit { it.withStartDate(picked) } }
    val pickEndDate = rememberDatePicker({ draft.finishDate }) { picked -> edit { it.withEndDate(picked) } }
    val pickStartTime = rememberTimePicker({ draft.startTime }, title = stringResource(R.string.ed_event_starts)) { picked ->
        // Keep the span the person already agreed to rather than snapping the
        // end back to an hour: moving a meeting is not re-planning its length.
        edit { it.copy(startTime = picked, durationMinutes = it.durationMinutes ?: DefaultEventMinutes) }
    }
    val pickEndTime = rememberTimePicker({ draft.finish?.toLocalTime() }, title = stringResource(R.string.ed_event_ends)) { picked ->
        edit { it.withEndTime(picked) }
    }
    val toggleAllDay = {
        edit {
            if (it.startTime == null) {
                it.copy(startTime = LocalTime.of(9, 0), durationMinutes = DefaultEventMinutes)
            } else {
                it.copy(startTime = null, durationMinutes = null)
            }
        }
    }
    val deletePromptChanged: (Boolean) -> Unit = { asked ->
        deleteStage = when {
            !asked -> EventDeleteStage.None
            // A one-off event has no scope to choose, so it goes straight to
            // the only question there is.
            isRecurringEvent(event) -> EventDeleteStage.Scope
            else -> EventDeleteStage.Confirm
        }
    }
    // The pill only says "Are you sure?" once the scope question is answered.
    val pillConfirming = deleteStage == EventDeleteStage.Confirm
    LaunchedEffect(dirty, saving, active, deleteStage, deleteScope) {
        if (active) {
            onPillState(
                EventPreviewPillState(
                    dirty,
                    pillConfirming,
                    openAction,
                    saveAction,
                    deleteAction,
                    deleteOccurrenceAction,
                    deletePromptChanged,
                ),
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
    EventDeleteDialog(
        event = event.takeIf { deleteStage == EventDeleteStage.Scope },
        onDismiss = { deleteStage = EventDeleteStage.None },
        onDelete = { target, scope ->
            deleteStage = EventDeleteStage.None
            onDeleteEvent(target, scope)
        },
    )
    Column(Modifier.fillMaxSize()) {
        HeroMasthead(tint) {
            val kicker = eventKicker(event, context.getString(R.string.ed_repeating), LocalCalinoLocale)
            Column(
                // An event with nothing to say above its title would otherwise
                // start one line higher than every other card, with the title
                // pressed against the handle. The kicker's line is held open
                // whether or not there is a kicker in it.
                Modifier.fillMaxWidth().padding(top = if (kicker == null) 14.dp else 0.dp),
            ) {
                kicker?.let {
                    Text(it, style = CalinoTypography.labelSmall, color = eventColor(event))
                }
                BasicTextField(
                    value = draft.title,
                    onValueChange = { value -> edit { it.copy(title = value) } },
                    textStyle = CalinoTypography.headlineMedium.copy(color = CalinoColors.Ink),
                    singleLine = true,
                    // The kicker is a 10sp label and the title a display
                    // numeral's worth of type; at 2dp apart they read as one
                    // clump rather than a label and the thing it labels.
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        .semantics { contentDescription = context.getString(R.string.ed_event_title_label) },
                )
            }
            // The draft holds a start and a span, so the finishing day is
            // derived: an event that runs past midnight ends tomorrow, and the
            // hero has to say so rather than show a smaller number.
            val finish = draft.finish
            WhenHero(
                startDate = draft.date,
                startTime = draft.startTime,
                endDate = if (draft.startTime == null) draft.allDayEndDate else finish?.toLocalDate(),
                endTime = finish?.toLocalTime(),
                accent = eventColor(event),
                // The preview draft has no separate flag: no start time is
                // exactly what all-day means for a record that already exists.
                allDay = draft.startTime == null,
                modifier = Modifier.padding(top = 8.dp),
                onStartDate = pickDate,
                onStartTime = pickStartTime,
                onEndDate = pickEndDate,
                onEndTime = pickEndTime,
                onAllDay = toggleAllDay,
                // The same slide and typing the editor offers, straight on the card.
                onStartTimeTyped = { picked ->
                    edit { it.copy(startTime = picked, durationMinutes = it.durationMinutes ?: DefaultEventMinutes) }
                },
                onStartDateTyped = { picked -> edit { it.withStartDate(picked) } },
                onEndDateTyped = { picked -> edit { it.withEndDate(picked) } },
                onEndTimeTyped = { picked -> edit { it.withEndTime(picked) } },
                onSlide = { startBy, endBy ->
                    edit { current ->
                        val start = current.startTime ?: return@edit current
                        val moved = current.date.atTime(start).plusMinutes(startBy.toLong())
                        val length = (current.durationMinutes ?: DefaultEventMinutes) + endBy - startBy
                        if (length <= 0) current
                        else current.copy(date = moved.toLocalDate(), startTime = moved.toLocalTime(), durationMinutes = length)
                    }
                },
            )
            val timeFormat = LocalTimeFormat
            val locale = LocalCalinoLocale
            val zoneInLabel = stringResource(R.string.ed_time_in)
            val zoneCaption = remember(draft.date, draft.startTime, finish, event.zoneId, event.endZoneId, timeFormat, locale, zoneInLabel) {
                foreignZoneCaption(
                    start = draft.startTime?.let(draft.date::atTime),
                    end = finish,
                    zoneId = event.zoneId,
                    endZoneId = event.endZoneId,
                    device = ZoneId.systemDefault(),
                    format = { time -> timeFormat.format(time, locale) },
                    inLabel = zoneInLabel,
                )
            }
            // The event was written for another place's clock; say what that
            // clock reads, quietly, under the device-time numerals.
            AnimatedVisibility(
                visible = zoneCaption != null,
                enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) + expandVertically(tween(CalinoMotion.ContentEnterMillis)),
                exit = fadeOut(tween(CalinoMotion.FadeThroughMillis)) + shrinkVertically(tween(CalinoMotion.ContentExitMillis)),
            ) {
                // Held (not state) so the caption keeps its words while it fades out.
                val shown = remember { arrayOf("") }
                zoneCaption?.let { shown[0] = it }
                Text(
                    shown[0],
                    style = CalinoTypography.labelSmall,
                    color = CalinoColors.Ink3,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }
        }
        HorizontalDivider(color = eventColor(event).copy(alpha = .30f))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 18.dp).testTag("event-detail-list"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            // The pill floats over the tail of the list, so its lane is
            // scroll content: the rows run to the card's edge under it.
            contentPadding = PaddingValues(bottom = EventPreviewPillClearance),
        ) {
            item(key = "meeting") {
                val meeting = meetingLink(event.conferenceUrl, draft.location, draft.description)
                AnimatedVisibility(
                    visible = meeting != null,
                    enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) +
                        expandVertically(tween(CalinoMotion.ContentEnterMillis)),
                    exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) +
                        shrinkVertically(tween(CalinoMotion.ContentExitMillis)),
                ) {
                    // Keep the last link while the row animates out.
                    var shown by remember { mutableStateOf(meeting) }
                    if (meeting != null) shown = meeting
                    shown?.let { PreviewMeetingRow(it) }
                }
            }
            item(key = "attachments") {
                AnimatedVisibility(
                    visible = event.attachments.isNotEmpty(),
                    enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) +
                        expandVertically(tween(CalinoMotion.ContentEnterMillis)),
                    exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) +
                        shrinkVertically(tween(CalinoMotion.ContentExitMillis)),
                ) {
                    // Keep the last list while the block animates out.
                    var shown by remember { mutableStateOf(event.attachments) }
                    if (event.attachments.isNotEmpty()) shown = event.attachments
                    Column { shown.forEach { PreviewAttachmentRow(event, it) } }
                }
            }
            item {
                PreviewEditRow(
                    CalinoIcon.Pin,
                    context.getString(R.string.ed_location),
                    draft.location.orEmpty(),
                    onValue = { value -> edit { it.copy(location = value) } },
                    trailing = {
                        // The location button is a 44dp target centring a 20dp
                        // glyph, so its box has to stop 12dp short of the
                        // chevron's for the two glyphs to share an edge.
                        Box(Modifier.padding(end = PreviewTrailingInset - 12.dp)) {
                            EventLocationButton(draft.location)
                        }
                    },
                )
            }
            if (event.recurrence != null) item {
                PreviewRecurrenceRow(
                    summary = recurrenceSummary(context, event),
                    occurrences = remember(event.id, draft.date) {
                        event.upcomingOccurrences(draft.date, PreviewOccurrenceCount)
                    },
                    expanded = occurrencesExpanded,
                    onExpandedChange = { occurrencesExpanded = it },
                )
            }
            if (readOnly && onLocalReminders != null) item {
                // The event cannot take a VALARM, but a reminder never needs
                // to change the event: it is kept on this device instead.
                val shownReminders = localReminders ?: event.reminders
                var open by remember(event.id) { mutableStateOf(false) }
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .clickable(onClickLabel = context.getString(R.string.ed_change_reminder)) { open = !open },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CalinoIcon(CalinoIcon.Bell, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            label(context.getString(R.string.ed_reminder))
                            val summary = eventReminderSummary(context, shownReminders, LocalTimeFormat, LocalCalinoLocale)
                            Text(
                                if (localReminders != null) context.getString(R.string.ed_reminder_on_this_device, summary) else summary,
                                style = CalinoTypography.bodyLarge,
                            )
                        }
                    }
                    EditorReveal(open) {
                        Column(Modifier.fillMaxWidth().padding(start = 38.dp, bottom = 10.dp)) {
                            EventReminderChips(shownReminders) { onLocalReminders(event, it) }
                        }
                    }
                }
            } else if (event.reminders.isNotEmpty()) item { PreviewStaticRow(CalinoIcon.Bell, context.getString(R.string.ed_reminder), event.reminders.joinToString { context.resources.getQuantityString(R.plurals.ed_minutes_before_count, it.minutesBefore, it.minutesBefore) }) }
            event.travelTimeMinutes?.takeIf { it > 0 }?.let { minutes ->
                item { PreviewStaticRow(CalinoIcon.Clock, context.getString(R.string.ed_travel_time), formatCalinoDuration(context, minutes)) }
            }
            if (canRespond && ownAttendee != null) {
                item { Text(context.getString(R.string.ed_your_response), style = CalinoTypography.bodySmall, color = CalinoColors.Ink2,
                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, top = 8.dp, bottom = 4.dp)) }
                if (event.recurrence != null || event.recurrenceId != null || event.recurrenceDate != null) item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(RecurrenceEditScope.All to context.getString(R.string.ed_whole_series), RecurrenceEditScope.This to context.getString(R.string.ed_this_occurrence)).forEach { (scope, label) ->
                            CalinoChip(label, responseScope == scope, context.getString(R.string.ed_respond_to, label), onClick = { responseScope = scope }, modifier = Modifier.heightIn(min = 44.dp))
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("ACCEPTED" to context.getString(R.string.ed_accept), "TENTATIVE" to context.getString(R.string.ed_maybe), "DECLINED" to context.getString(R.string.ed_decline)).forEach { (status, label) ->
                            CalinoChip(label, ownAttendee.participationStatus == status, context.getString(R.string.ed_invitation, label), modifier = Modifier.heightIn(min = 44.dp), onClick = {
                                if (!responding) {
                                    responding = true
                                    responseError = null
                                    coroutineScope.launch {
                                        if (!onRespond(event, status, responseScope)) responseError = context.getString(R.string.ed_response_save_error)
                                        responding = false
                                    }
                                }
                            })
                        }
                    }
                }
                responseError?.let { message -> item { Text(message, color = CalinoColors.Rose, style = CalinoTypography.bodySmall,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) } }
            }
            event.organizer?.let { organizer ->
                item { PreviewStaticRow(CalinoIcon.Users, context.getString(R.string.ed_organizer), organizer.name) }
            }
            if (event.attendees.isNotEmpty()) item {
                val counts = event.attendees.groupingBy { it.participationStatus.uppercase() }.eachCount()
                val summary = listOf(
                    "ACCEPTED" to context.getString(R.string.ed_accepted),
                    "TENTATIVE" to context.getString(R.string.ed_maybe),
                    "DECLINED" to context.getString(R.string.ed_declined),
                    "NEEDS-ACTION" to context.getString(R.string.ed_pending),
                ).mapNotNull { (key, label) ->
                    counts[key]?.takeIf { it > 0 }?.let { context.getString(R.string.ed_attendee_response_count, it, label) }
                }
                    .joinToString(" · ")
                PreviewStaticRow(CalinoIcon.Users, context.getString(R.string.ed_responses), summary)
                Column(Modifier.fillMaxWidth().padding(start = 38.dp, end = 20.dp, bottom = 8.dp)) {
                    event.attendees.forEach { attendee ->
                        Text(
                            "${attendee.name.ifBlank { attendee.email }} · ${localizedParticipationStatus(attendee.participationStatus, context)}",
                            style = CalinoTypography.bodySmall,
                            color = CalinoColors.Ink2,
                        )
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = CalinoColors.Ink.copy(.1f)) }
            item {
                if (draft.description.isBlank()) {
                    PreviewStaticRow(CalinoIcon.Note, context.getString(R.string.ed_description), context.getString(R.string.ed_add_description), prompt = true)
                } else {
                    DetailRow(CalinoIcon.Note, context.getString(R.string.ed_description), draft.description, markdown = true) { taskIndex, checked ->
                        if (!readOnly && !saving) {
                            val before = draft
                            val changed = toggleCalinoMarkdownTask(before.description, taskIndex, checked)
                            if (changed != before.description) {
                                draft = before.copy(description = changed)
                                saving = true
                                coroutineScope.launch {
                                    val checkboxScope = if (isRecurringEvent(event)) RecurrenceEditScope.This else RecurrenceEditScope.All
                                    val saved = onInlineSave(event, draft.toNewEvent(event, checkboxScope), checkboxScope)
                                    saving = false
                                    if (saved) savedDraft = draft else draft = before
                                }
                            }
                        }
                    }
                }
            }
            error?.let { message -> item { Text(message, color = CalinoColors.Rose, style = CalinoTypography.bodySmall, modifier = Modifier.padding(12.dp)) } }
        }
    }
    // DetailCardSurface draws its drag handle above this content slot. Cover
    // that lane too, otherwise the handle stays bright through the prompt's
    // shade while the rest of the card dims.
    val handleHeight = if (LocalCalinoSurfaceMode.current == CalinoSurfaceMode.EndPanel) 16.dp else 24.dp
    AnimatedVisibility(
        visible = scopePrompt,
        enter = fadeIn(tween(CalinoMotion.SurfaceFadeMillis)),
        exit = fadeOut(tween(CalinoMotion.ContentExitMillis)),
        modifier = Modifier.align(Alignment.TopCenter).offset(y = -handleHeight)
            .fillMaxWidth().height(handleHeight),
    ) {
        Box(Modifier.fillMaxSize().background(CalinoColors.Scrim))
    }
    CalinoScrim(visible = scopePrompt, modifier = Modifier.matchParentSize(), onDismiss = { if (!saving) scopePrompt = false })
    AnimatedVisibility(
        visible = scopePrompt,
        enter = slideInVertically(CalinoMotion.expressiveSpatial()) { it / 3 } + fadeIn(tween(CalinoMotion.ContentEnterMillis)),
        exit = slideOutVertically(tween(CalinoMotion.ContentExitMillis)) { it / 4 } + fadeOut(tween(CalinoMotion.ContentExitMillis)),
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = EventPreviewPillClearance),
    ) {
        Column(
            Modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(CalinoShapes.Sheet), clip = false)
                .clip(RoundedCornerShape(CalinoShapes.Sheet)).background(CalinoColors.Panel)
                .semantics { paneTitle = context.getString(R.string.ed_apply_changes_to) }
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp),
        ) {
            Text(context.getString(R.string.ed_apply_changes_to), style = CalinoTypography.titleLarge, color = CalinoColors.Ink)
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(CalinoShapes.Row))
                    .background(CalinoColors.Ink.copy(alpha = .035f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                (if (event.providerRecurring) listOf(RecurrenceEditScope.This, RecurrenceEditScope.All) else RecurrenceEditScope.entries).forEach { option ->
                    val label = when (option) {
                        RecurrenceEditScope.This -> context.getString(R.string.ed_this_event)
                        RecurrenceEditScope.Future -> context.getString(R.string.ed_this_and_future)
                        RecurrenceEditScope.All -> context.getString(R.string.ed_entire_series)
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(CalinoShapes.Row))
                            .clickable(role = Role.RadioButton, onClickLabel = context.getString(R.string.ed_apply_changes_to_option, label)) { saveScope = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (saveScope == option) "●" else "○", color = CalinoColors.Accent, style = CalinoTypography.bodyLarge)
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = CalinoColors.Ink, style = CalinoTypography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PromptButton(context.getString(R.string.ed_cancel), filled = false, enabled = !saving, onClick = { scopePrompt = false }, modifier = Modifier.weight(1f))
                PromptButton(context.getString(R.string.ed_continue), filled = true, enabled = !saving, onClick = { save(pendingOpen) }, modifier = Modifier.weight(1f))
            }
        }
    }
    }
}

/**
 * How far through removing an event the person has got.
 *
 * [Scope] is only ever reached by a repeating event: it is the card asking
 * which occurrences are meant, with the pill still showing a plain trash
 * glyph. [Confirm] is the pill's own "Are you sure?", and nothing else.
 */
private enum class EventDeleteStage { None, Scope, Confirm }

private data class EventPreviewPillState(
    val dirty: Boolean,
    val confirmingDelete: Boolean,
    val onOpen: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    // The hold shortcut's own removal: this occurrence and nothing else.
    val onDeleteOccurrence: () -> Unit,
    val onDeletePromptChanged: (Boolean) -> Unit,
)

// This compact card's pill is 56dp high and sits close to the card edge; the
// global 96dp editor clearance needlessly hid the final row.
private val EventPreviewPillClearance = 76.dp

/** The default span an event gets when it is handed a time it did not have. */
private const val DefaultEventMinutes = 60

/**
 * The line above the title: what this event is, in the words the record
 * already carries. Null when the event says nothing worth a kicker -- an
 * empty strip above the title is worse than no strip.
 */
private fun eventKicker(event: CalEvent, repeatingLabel: String, locale: Locale): String? {
    val parts = buildList {
        event.categories.firstOrNull()?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (isRecurringEvent(event)) add(repeatingLabel)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.uppercase(locale)
}

@Composable
private fun PreviewEditRow(
    icon: CalinoIcon,
    labelText: String,
    value: String,
    placeholder: String = "",
    trailing: (@Composable () -> Unit)? = null,
    onValue: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        CalinoIcon(icon, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
        TextField(value, onValue, placeholder = { if (placeholder.isNotBlank()) Text(placeholder) }, label = { Text(labelText) },
            singleLine = icon != CalinoIcon.Note, modifier = Modifier.weight(1f).semantics { contentDescription = labelText },
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        trailing?.invoke()
    }
}

/**
 * How far a row's trailing control sits in from the card's edge, beyond the
 * list's own inset.
 *
 * The card reads as one text measure -- the masthead, every row's text and
 * every trailing control share it -- with the leading icons in a gutter
 * outside it. 18dp of list inset plus this is the 56dp the masthead uses.
 */
private val PreviewTrailingInset = 38.dp

/** How many future occurrences the recurrence row is willing to name. */
private const val PreviewOccurrenceCount = 5

/**
 * The recurrence row, with the series it describes one tap away.
 *
 * "Every Friday until 30 Jun" is a rule, not an answer: the question people
 * actually bring to a repeating event is which days it lands on next. The
 * chevron keeps that available without spending five rows on an event nobody
 * asked the question about.
 */
@Composable
private fun PreviewRecurrenceRow(
    summary: String,
    occurrences: List<LocalDate>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val occurrenceDateFormat = localizedDateFormatter("EEE, d MMM")
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(CalinoMotion.ContentEnterMillis),
        label = "recurrence-chevron",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button) { onExpandedChange(!expanded) }
                .semantics(mergeDescendants = true) {
                    contentDescription = if (expanded) {
                        context.getString(R.string.ed_repeats_hide_dates, summary)
                    } else {
                        context.getString(R.string.ed_repeats_show_dates, summary)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalinoIcon(CalinoIcon.Repeat, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                label(context.getString(R.string.ed_repeats))
                Text(summary, style = CalinoTypography.bodyLarge)
            }
            Icon(
                CalinoIcons.ChevronDown,
                contentDescription = null,
                tint = CalinoColors.Ink3,
                modifier = Modifier
                    .padding(end = PreviewTrailingInset)
                    .size(20.dp)
                    .graphicsLayer { rotationZ = turn },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(CalinoMotion.ContentEnterMillis)) + fadeIn(tween(CalinoMotion.FadeThroughMillis)),
            exit = shrinkVertically(tween(CalinoMotion.ContentExitMillis)) + fadeOut(tween(CalinoMotion.FadeThroughMillis)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 38.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (occurrences.isEmpty()) {
                    Text(
                        context.getString(R.string.ed_no_more_occurrences),
                        style = CalinoTypography.bodyMedium,
                        color = CalinoColors.Ink3,
                    )
                } else {
                    occurrences.forEach { date ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(4.dp).background(CalinoColors.Ink.copy(alpha = .22f), CircleShape))
                            Text(
                                date.format(occurrenceDateFormat),
                                style = CalinoTypography.bodyMedium,
                                color = CalinoColors.Ink2,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The event's meeting link, opened with a plain VIEW so the meeting app or
 * the browser handles it. Calino only reads the link; it never writes one.
 */
@Composable
private fun PreviewMeetingRow(meeting: MeetingLink) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        CalinoIcon(CalinoIcon.Users, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            label(context.getString(R.string.ed_meeting))
            Text(meeting.service, style = CalinoTypography.bodyLarge)
        }
        // A slim pill inside the usual 44dp touch lane: at chip height it
        // outweighs the row it belongs to.
        val join = meeting.service
        Box(
            Modifier
                .padding(end = PreviewTrailingInset - 18.dp)
                .heightIn(min = 44.dp)
                .calinoPressable(role = Role.Button) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(meeting.url)))
                    }
                }
                .semantics { contentDescription = context.getString(R.string.ed_join_meeting, join) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(CalinoShapes.Pill))
                    .background(CalinoColors.Accent.copy(.12f))
                    .border(1.dp, CalinoColors.Accent.copy(.2f), RoundedCornerShape(CalinoShapes.Pill))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(context.getString(R.string.ed_join), style = CalinoTypography.bodySmall, color = CalinoColors.Ink)
            }
        }
    }
}

/**
 * One `ATTACH`. The whole row is the target: a link opens with VIEW, inline
 * data is written to a cached file and handed over through the FileProvider.
 */
@Composable
private fun PreviewAttachmentRow(event: CalEvent, attachment: EventAttachment) {
    val context = LocalContext.current
    val open = calino.malinov.ski.state.LocalAttachmentOpener.current
    val fallback = stringResource(R.string.ed_attachment_fallback)
    val name = attachmentLabel(attachment, fallback)
    val detail = attachmentDetail(attachment, fallback, stringResource(R.string.ed_attachment_link))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClickLabel = context.getString(R.string.ed_open)) { open(event, attachment) }
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalinoIcon(CalinoIcon.Paperclip, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
        // Label over value, like every other row on the card; the type, size
        // or host is a quiet note at the row's end rather than a third line.
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            label(fallback)
            Text(name, style = CalinoTypography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        detail?.let {
            Text(
                it,
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp, end = PreviewTrailingInset),
            )
        }
    }
}

@Composable
private fun PreviewStaticRow(icon: CalinoIcon, labelText: String, value: String, prompt: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        CalinoIcon(icon, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
        Column(Modifier.padding(start = 16.dp)) {
            label(labelText)
            // A prompt to add something is a suggestion, not a value: it sits
            // a little further from its label and fainter than real content.
            Text(
                value,
                style = CalinoTypography.bodyLarge,
                modifier = if (prompt) Modifier.padding(top = 3.dp).alpha(.55f) else Modifier,
            )
        }
    }
}

private fun localizedParticipationStatus(status: String, context: android.content.Context): String = when (status.uppercase()) {
    "ACCEPTED" -> context.getString(R.string.ed_accepted)
    "TENTATIVE" -> context.getString(R.string.ed_maybe)
    "DECLINED" -> context.getString(R.string.ed_declined)
    "NEEDS-ACTION" -> context.getString(R.string.ed_pending)
    else -> status.lowercase(context.resources.configuration.locales[0]).replace('-', ' ')
}

/** True when the event is a series master, a detached override, or an expansion. */
fun isRecurringEvent(event: CalEvent): Boolean =
    event.providerRecurring || event.recurrence != null || event.recurrenceId != null || event.recurrenceDate != null

/**
 * The scope a delete confirmation should open on. A single occurrence defaults
 * to [RecurrenceEditScope.This] so confirming without touching the chips never
 * removes more of the series than the user pointed at.
 */
fun defaultEventDeleteScope(event: CalEvent): RecurrenceEditScope =
    if (event.providerRecurring || event.recurrenceId != null || event.recurrenceDate != null) {
        RecurrenceEditScope.This
    } else {
        RecurrenceEditScope.All
    }

/** Imported provider series never support the ambiguous This-and-future scope. */
fun eventDeleteScopes(event: CalEvent): List<RecurrenceEditScope> =
    if (event.providerRecurring || AndroidCalendarId.isImported(event.calendarId)) {
        listOf(RecurrenceEditScope.This, RecurrenceEditScope.All)
    } else {
        RecurrenceEditScope.entries
    }

/** Capability supplied by the calendar owns imported-event editability. */
fun eventDetailReadOnly(hostReadOnly: Boolean, event: CalEvent): Boolean =
    hostReadOnly || WebcalSubscription.isWebcalCalendarId(event.calendarId)

/** One way of removing a repeating record: a full-width action in the delete popup. */
@Composable
private fun DeleteScopeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CalinoColors.Rose.copy(alpha = .16f),
            contentColor = CalinoColors.Ink,
        ),
        elevation = null,
    ) {
        Text(label, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
    }
}

/** True when [rule] has parts the repeat editor cannot show (interval, month day...). */
private fun hasCustomRepeatParts(rule: String): Boolean {
    val known = setOf("FREQ", "BYDAY", "UNTIL", "COUNT")
    return rule.removePrefix("RRULE:").split(';').any { part ->
        val key = part.substringBefore('=').uppercase()
        key.isNotEmpty() && key !in known && !(key == "INTERVAL" && part.substringAfter('=') == "1")
    }
}

/**
 * The one question asked before removing part of a repeating series, for
 * tasks and events alike: a floating popup over the whole window, never a
 * card that belongs to either surface.
 *
 * Each scope is its own full-width action, so choosing is the confirmation:
 * there is no pre-selected default to confirm by accident, and the narrowest
 * reading is listed first. [item] is null while nothing is pending, so the
 * popup keeps rendering the last item through its exit.
 */
@Composable
private fun <T : Any> RepeatDeleteDialog(
    item: T?,
    title: (T) -> String,
    heading: String,
    scopes: List<Pair<RecurrenceEditScope, String>>,
    tagPrefix: String,
    onDismiss: () -> Unit,
    onDelete: (T, RecurrenceEditScope) -> Unit,
) {
    var shown by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(item) { if (item != null) shown = item }
    // The window outlives `item` just long enough to play the exit.
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = item != null
    val target = item ?: shown
    if (target == null || !(transition.currentState || transition.targetState)) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The window's own dim cannot animate; the scrim below does.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CalinoScrim(visible = transition.targetState, onDismiss = onDismiss)
            AnimatedVisibility(
                visibleState = transition,
                enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) + scaleIn(tween(CalinoMotion.ContentEnterMillis), initialScale = .94f),
                exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) + scaleOut(tween(CalinoMotion.ContentExitMillis), targetScale = .96f),
            ) {
                Surface(
                    modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 380.dp).fillMaxWidth().shadow(16.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = CalinoColors.Panel,
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(title(target), style = CalinoTypography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(heading, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Text(stringResource(R.string.ed_delete_series_question), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
                        }
                        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            scopes.forEach { (scope, label) ->
                                DeleteScopeButton(
                                    label = label,
                                    onClick = { onDelete(target, scope) },
                                    modifier = Modifier.testTag("$tagPrefix-delete-${scope.name.lowercase()}"),
                                )
                            }
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, CalinoColors.Line),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CalinoColors.Ink3),
                            ) {
                                Text(stringResource(R.string.ed_cancel), style = CalinoTypography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Delete popup for a repeating task. */
@Composable
fun TaskDeleteSheet(
    task: CalTask?,
    onDismiss: () -> Unit,
    onDelete: (CalTask, RecurrenceEditScope) -> Unit,
) {
    RepeatDeleteDialog(
        item = task,
        title = { it.title },
        heading = stringResource(R.string.ed_remove_recurring_task),
        scopes = listOf(
            RecurrenceEditScope.This to stringResource(R.string.ed_repeat_this_task),
            RecurrenceEditScope.Future to stringResource(R.string.ed_this_and_future),
            RecurrenceEditScope.All to stringResource(R.string.ed_entire_series),
        ),
        tagPrefix = "task",
        onDismiss = onDismiss,
        onDelete = onDelete,
    )
}

/** Delete popup for a repeating event; imported series omit "this and future". */
@Composable
fun EventDeleteDialog(
    event: CalEvent?,
    onDismiss: () -> Unit,
    onDelete: (CalEvent, RecurrenceEditScope) -> Unit,
) {
    val labels = mapOf(
        RecurrenceEditScope.This to stringResource(R.string.ed_this_event),
        RecurrenceEditScope.Future to stringResource(R.string.ed_this_and_future),
        RecurrenceEditScope.All to stringResource(R.string.ed_entire_series),
    )
    var scopes by remember { mutableStateOf(emptyList<Pair<RecurrenceEditScope, String>>()) }
    if (event != null) scopes = eventDeleteScopes(event).map { it to labels.getValue(it) }
    RepeatDeleteDialog(
        item = event,
        title = { it.title },
        heading = stringResource(R.string.ed_remove_recurring_event),
        scopes = scopes,
        tagPrefix = "event",
        onDismiss = onDismiss,
        onDelete = onDelete,
    )
}

/**
 * Event detail's lane contents. Overflow is one segment of the same pill,
 * rather than a second button competing with the preview header.
 */
@Composable
private fun EventDetailPill(
    event: CalEvent,
    occurrenceDate: LocalDate?,
    expanded: Boolean,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    onMoreOpen: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = localizedDateFormatter("EEE, d MMM")
    ModalActionPill(
        addLabel = (occurrenceDate ?: event.start?.toLocalDate() ?: event.date)?.let { context.getString(R.string.ed_day_add_on, it.format(dateFormat)) } ?: context.getString(R.string.ed_add_event),
        morphFromAddPill = true,
        inPillLane = true,
        expanded = expanded,
        cancelLabel = context.getString(R.string.ed_cancel),
        onCancel = onBack,
        cancelDescription = context.getString(R.string.ed_close_event_details),
        secondaryLabel = context.getString(R.string.ed_edit),
        onSecondary = onPrimary,
        secondaryDescription = context.getString(R.string.ed_edit_event),
        primaryLabel = "···",
        onPrimary = onMoreOpen,
        primaryDescription = context.getString(R.string.ed_more_event_actions),
    )
}

/**
 * Fixture-backed task detail/editor. The task body is the primary tap target
 * in both the calendar and task ledger; completion and rescheduling remain
 * separate row actions so opening a task never mutates it accidentally.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetailSurface(
    task: CalTask,
    tasks: List<CalTask> = listOf(task),
    onBack: () -> Unit = {},
    onSave: suspend (NewTask, Boolean) -> Boolean = { _, _ -> true },
    onInlineNotesSave: suspend (NewTask, Boolean) -> Boolean = { _, _ -> false },
    onDelete: suspend (RecurrenceEditScope) -> Boolean = { true },
    onAddSubtask: () -> Unit = {},
    categories: List<String> = emptyList(),
    onOpenSubtask: (CalTask) -> Unit = {},
    onToggleSubtask: (CalTask) -> Unit = {},
    planningDate: LocalDate? = null,
) {
    val context = LocalContext.current
    val dateFormat = localizedDateFormatter("EEE, d MMM")
    val today = LocalCalinoNow.current.today
    var title by remember(task.id) { mutableStateOf(task.title) }
    var category by remember(task.id) { mutableStateOf(task.category.orEmpty()) }
    var notes by remember(task.id) { mutableStateOf(task.notes.orEmpty()) }
    var savedNotes by remember(task.id) { mutableStateOf(task.notes.orEmpty()) }
    var due by remember(task.id) { mutableStateOf(task.due) }
    var taskStart by remember(task.id) { mutableStateOf(task.startDate) }
    var taskStartTime by remember(task.id) { mutableStateOf(task.startTime) }
    var dueTime by remember(task.id) { mutableStateOf(task.dueTime) }
    var reminder by remember(task.id) { mutableStateOf(task.reminder) }
    var reminderOpen by remember(task.id) { mutableStateOf(false) }
    // A repeating task edits its rule for the whole series; a one-off has none.
    var recurrence by remember(task.id) { mutableStateOf(task.recurrence) }
    var repeatOpen by remember(task.id) { mutableStateOf(false) }
    var done by remember(task.id) { mutableStateOf(task.done) }
    var priority by remember(task.id) { mutableIntStateOf(task.priority) }
    var percentComplete by remember(task.id) { mutableIntStateOf(task.percentComplete) }
    var shown by remember(task.id) { mutableStateOf(true) }
    var pendingSave by remember(task.id) { mutableStateOf(false) }
    var pendingDelete by remember(task.id) { mutableStateOf(false) }
    var confirmingDelete by remember(task.id) { mutableStateOf(false) }
    // A repeating task asks which part of the series to remove before it leaves.
    var scopeSheetOpen by remember(task.id) { mutableStateOf(false) }
    var deleteScope by remember(task.id) { mutableStateOf(RecurrenceEditScope.All) }
    var requestedDone by remember(task.id) { mutableStateOf<Boolean?>(null) }
    var savingNotes by remember(task.id) { mutableStateOf(false) }
    var editingNotes by remember(task.id) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val detailScrollState = rememberScrollState()
    // Opening a subtask swaps the task under this card; start it at the top.
    LaunchedEffect(task.id) { detailScrollState.scrollTo(0) }
    val headerTint = eventTint(taskColor(task), .13f, CalinoColors.Panel)
    val pickDueDate = rememberDatePicker({ due ?: today }) { due = it }
    val pickTaskStart = rememberDatePicker({ taskStart ?: (due ?: today).minusDays(1) }) { taskStart = it; taskStartTime = null }
    val weekStart = LocalCalinoPreferences.current.weekStart
    val pickDueTime = rememberTimePicker({ dueTime }, title = stringResource(R.string.ed_editor_due)) { picked ->
        if (due == null) due = today
        dueTime = picked
    }
    val repeatAnchor = task.recurrenceDate ?: due ?: today
    val pickRepeatEnd = rememberDatePicker({ repeatAnchor.plusMonths(3) }) { picked ->
        recurrence = recurrenceRule(
            recurrenceFreqOf(recurrence) ?: RecurrenceFreq.Weekly,
            recurrenceDaysOf(recurrence),
            picked,
        )
    }

    LaunchedEffect(shown) {
        if (!shown) {
            delay(220)
            if (pendingDelete) {
                if (!onDelete(deleteScope)) {
                    // The host's scrim remains composed while this surface is
                    // selected. Reopen it after a rejected write instead of
                    // leaving an invisible, full-window surface over the app.
                    pendingDelete = false
                    shown = true
                }
            } else if (pendingSave) {
                // Completion is latched separately from presentation state.
                // The sheet exits before saving, and the delayed coroutine can
                // otherwise observe the pre-click `done` value from its exit
                // composition even though the checkmark already changed.
                val savedDone = requestedDone ?: done
                val saved = onSave(
                    NewTask(
                        title = title.trim(),
                        due = due,
                        color = task.color,
                        category = category.trim().ifEmpty { null },
                        dueTime = dueTime,
                        startDate = taskStart,
                        startTime = taskStartTime,
                        notes = notes.trim().ifEmpty { null },
                        reminder = reminder,
                        priority = priority,
                        percentComplete = if (savedDone) 100 else percentComplete.coerceAtMost(99),
                        status = if (savedDone) "COMPLETED" else if (percentComplete > 0) "IN-PROCESS" else "NEEDS-ACTION",
                        completedAt = task.completedAt,
                        recurrence = recurrence,
                        uid = task.uid,
                        href = task.href,
                        etag = task.etag,
                        calendarId = task.calendarId,
                        parentTaskId = task.parentTaskId,
                        recurrenceId = task.recurrenceId,
                        recurrenceDate = task.recurrenceDate,
                        sequence = task.sequence,
                        recurrenceChanged = recurrence != task.recurrence,
                    ),
                    savedDone,
                )
                if (!saved) {
                    pendingSave = false
                    shown = true
                }
            } else {
                onBack()
            }
        }
    }
    fun dismiss(save: Boolean) {
        if (!shown) return
        pendingSave = save
        shown = false
    }

    Box(Modifier.fillMaxSize()) {
        BottomDetailCard(
            visible = shown,
            onDismiss = { dismiss(false) },
            modifier = Modifier.fillMaxSize(),
            dismissDistance = 980.dp,
            canStartDismiss = { !detailScrollState.canScrollBackward },
            // The event card's own gestures: a swipe down closes it in the side
            // panel too, not only a swipe toward the edge.
            allowDownwardDismissInEndPanel = true,
            surfaceKind = CalinoSurfaceKind.TaskPreview,
            handleColor = headerTint,
            pill = {
                val canSave = title.trim().isNotEmpty()
                // Completion has its own action, so it does not count as an edit
                // waiting to be saved.
                val dirty = title != task.title ||
                    category != task.category.orEmpty() ||
                    notes != savedNotes ||
                    due != task.due ||
                    dueTime != task.dueTime ||
                    taskStart != task.startDate || taskStartTime != task.startTime ||
                    reminder != task.reminder ||
                    recurrence != task.recurrence ||
                    priority != task.priority ||
                    percentComplete != task.percentComplete
                ModalActionPill(
                    addLabel = context.getString(R.string.ed_new_task),
                    morphFromAddPill = true,
                    inPillLane = true,
                    expanded = shown,
                    cancelLabel = context.getString(R.string.ed_cancel),
                    onCancel = { dismiss(false) },
                    cancelDescription = context.getString(R.string.ed_cancel_task_editing),
                    deleteLabel = context.getString(R.string.ed_delete),
                    onDelete = {
                        if (task.isRecurringTask()) {
                            scopeSheetOpen = true
                        } else {
                            pendingDelete = true
                            confirmingDelete = false
                            shown = false
                        }
                    },
                    deleteDescription = context.getString(R.string.ed_delete_task),
                    deleteConfirmationActive = confirmingDelete,
                    onDeleteConfirmationChange = { confirmingDelete = it },
                    // The scope sheet is the confirmation for a series.
                    deleteHoldToConfirm = !task.isRecurringTask(),
                    primaryLabel = context.getString(R.string.ed_save),
                    primaryTone = ModalPillActionTone.Save,
                    onPrimary = { dismiss(true) },
                    primaryEnabled = canSave,
                    primaryDescription = context.getString(R.string.ed_save_task),
                    secondaryLabel = if (done) context.getString(R.string.ed_mark_open) else context.getString(R.string.ed_mark_done),
                    onSecondary = {
                        val nextDone = !done
                        requestedDone = nextDone
                        done = nextDone
                        // Keep the richer progress model consistent with the
                        // primary completion action before the delayed save takes
                        // its snapshot. This also makes completion robust if the
                        // boolean callback and NewTask payload are observed on
                        // adjacent recomposition frames.
                        percentComplete = if (nextDone) 100 else 0
                        dismiss(true)
                    },
                    secondaryEnabled = canSave,
                    secondaryDescription = if (done) context.getString(R.string.ed_mark_task_open) else context.getString(R.string.ed_mark_task_done),
                    primaryVisible = dirty,
                )
            },
        ) { detailModifier ->
            Column(
                detailModifier
                    .fillMaxSize()
                    .background(CalinoColors.Canvas),
            ) {
                HeroMasthead(headerTint) {
                    // Same shell as an event's: the category is the kicker above
                    // the title, and an empty kicker still holds its line open so
                    // every card's title starts at the same height.
                    val kicker = listOfNotNull(
                        category.trim().takeIf { it.isNotEmpty() },
                        context.getString(R.string.ed_repeating).takeIf { task.recurrence != null },
                    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")?.uppercase(LocalCalinoLocale)
                    Column(Modifier.fillMaxWidth().padding(top = if (kicker == null) 14.dp else 0.dp)) {
                        kicker?.let { Text(it, style = CalinoTypography.labelSmall, color = taskColor(task)) }
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 6.dp)
                                .semantics { contentDescription = context.getString(R.string.ed_task_title_label) },
                            textStyle = CalinoTypography.headlineMedium.copy(color = CalinoColors.Ink),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (title.isBlank()) {
                                        Text(context.getString(R.string.ed_add_task_title), style = CalinoTypography.headlineMedium.copy(color = CalinoColors.Ink3))
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }
                }
                HorizontalDivider(color = taskColor(task).copy(alpha = .30f))
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(detailScrollState)
                        .padding(horizontal = 18.dp),
                ) {
                    TaskRangeFields(
                        start = taskStart, due = due,
                        recurring = task.recurrence != null || task.recurrenceId != null || task.recurrenceDate != null,
                        onStart = pickTaskStart,
                        onDue = pickDueDate,
                        onClearStart = { taskStart = null; taskStartTime = null },
                        onWeek = {
                            val first = (planningDate ?: today).startOfWeek(weekStart)
                            taskStart = first; taskStartTime = null; due = first.plusDays(6); dueTime = null
                        },
                        picks = listOf(today to context.getString(R.string.ed_today), today.plusDays(1) to context.getString(R.string.ed_tomorrow), today.plusDays(7) to context.getString(R.string.ed_next_week)),
                        onPick = { due = it },
                        dueRow = { startAction ->
                            TaskRow(
                                icon = CalinoIcon.Calendar,
                                text = due?.format(dateFormat) ?: context.getString(R.string.ed_add_due_date),
                                set = due != null,
                                description = context.getString(R.string.ed_choose_custom_due_date),
                                onClick = pickDueDate,
                                onClear = if (due != null) ({ due = null; dueTime = null; taskStart = null; taskStartTime = null }) else null,
                                clearDescription = context.getString(R.string.ed_remove_due_date),
                                trailing = { if (due != null) startAction() },
                            )
                        },
                    )
                    HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                    TaskRow(
                        icon = CalinoIcon.Clock,
                        text = dueTime?.let { LocalTimeFormat.format(it, LocalCalinoLocale) } ?: context.getString(R.string.ed_add_due_time),
                        set = dueTime != null,
                        description = context.getString(R.string.ed_change_due_time),
                        onClick = pickDueTime,
                        onClear = if (dueTime != null) ({ dueTime = null }) else null,
                        clearDescription = context.getString(R.string.ed_remove_due_time),
                    )
                    HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                    Column {
                        val chevron by animateFloatAsState(
                            if (reminderOpen) 180f else 0f,
                            CalinoMotion.expressiveSpatial(),
                            label = "reminder chevron",
                        )
                        // The whole row is the control: value and chevron say what
                        // it holds and that it opens, with no button inside it.
                        TaskRow(
                            icon = CalinoIcon.Bell,
                            text = if (reminder != null) taskReminderSummary(context, reminder, LocalTimeFormat, LocalCalinoLocale) else context.getString(R.string.ed_add_reminder),
                            set = reminder != null,
                            description = context.getString(R.string.ed_change_task_reminder),
                            onClick = { reminderOpen = !reminderOpen },
                        ) {
                            CalinoIcon(
                                CalinoIcon.Down,
                                tint = CalinoColors.Ink3,
                                modifier = Modifier.size(18.dp).rotate(chevron),
                                contentDescription = null,
                            )
                        }
                        EditorReveal(reminderOpen) {
                            Box(Modifier.padding(start = 38.dp, bottom = 12.dp)) {
                                TaskReminderChips(reminder) { reminder = it }
                            }
                        }
                    }
                    HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                    // Any top-level task can start repeating here; subtasks and tasks
                    // with subtasks cannot (the repository rejects both).
                    if (task.recurrence != null || (task.parentTaskId == null && tasks.none { it.parentTaskId == task.id })) {
                        Column {
                            val repeatChevron by animateFloatAsState(
                                if (repeatOpen) 180f else 0f,
                                CalinoMotion.expressiveSpatial(),
                                label = "repeat chevron",
                            )
                            TaskRow(
                                icon = CalinoIcon.Repeat,
                                text = recurrence?.let { formatRecurrenceRule(context, it, repeatAnchor) } ?: context.getString(R.string.ed_editor_dont_repeat),
                                set = recurrence != null,
                                description = context.getString(R.string.ed_change_task_repeat),
                                onClick = { repeatOpen = !repeatOpen },
                            ) {
                                CalinoIcon(
                                    CalinoIcon.Down,
                                    tint = CalinoColors.Ink3,
                                    modifier = Modifier.size(18.dp).rotate(repeatChevron),
                                    contentDescription = null,
                                )
                            }
                            EditorReveal(repeatOpen) {
                                Column(
                                    Modifier.padding(start = 38.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    RecurrenceRuleEditor(
                                        rule = recurrence,
                                        anchor = repeatAnchor,
                                        onRule = { picked ->
                                            // A repeat needs a date to start from.
                                            if (picked != null && due == null) due = today
                                            recurrence = picked
                                        },
                                        pickUntil = pickRepeatEnd,
                                        // An existing series ends with an end date or a
                                        // delete; only a one-off can go back to "Never".
                                        allowNever = task.recurrence == null,
                                        showLabel = false,
                                    )
                                    if (task.recurrence?.let(::hasCustomRepeatParts) == true) {
                                        Text(context.getString(R.string.ed_repeat_custom_note), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
                                    }
                                    if (task.recurrence != null) {
                                        Text(context.getString(R.string.ed_repeat_series_note), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                    }
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        label(context.getString(R.string.ed_priority))
                        val priorities = listOf(0 to context.getString(R.string.ed_none), 1 to context.getString(R.string.ed_high), 5 to context.getString(R.string.ed_medium), 9 to context.getString(R.string.ed_low))
                        CompactSegmentedControl(
                            options = priorities.map { it.second },
                            selectedIndex = priorities.indexOfFirst { it.first == priority }.coerceAtLeast(0),
                            onSelected = { priority = priorities[it].first },
                            modifier = Modifier.fillMaxWidth(),
                            semanticLabel = context.getString(R.string.ed_priority),
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            label(context.getString(R.string.ed_progress), Modifier.weight(1f))
                            Text("$percentComplete%", style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
                        }
                        CalinoProgressSlider(
                            percent = percentComplete,
                            onPercentChange = { percentComplete = it; done = it == 100 },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                    val subtasks = tasks.filter { it.parentTaskId == task.id }
                    if (subtasks.isEmpty()) {
                        // Same shape as the empty notes row: nothing to list, so
                        // the whole row is the way to add one.
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .clickable(role = Role.Button, onClickLabel = context.getString(R.string.ed_add_subtask), onClick = onAddSubtask),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(38.dp)) {
                                CalinoIcon(CalinoIcon.Check, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
                            }
                            Text(context.getString(R.string.ed_add_subtask), style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3)
                        }
                    } else {
                        Column(Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(38.dp)) {
                                    CalinoIcon(CalinoIcon.Check, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
                                }
                                label(context.getString(R.string.ed_subtasks), Modifier.weight(1f))
                                Text(
                                    context.resources.getQuantityString(R.plurals.ed_task_subtask_count, subtasks.size, subtasks.count { it.done }, subtasks.size),
                                    style = CalinoTypography.labelSmall,
                                    color = CalinoColors.Ink3,
                                )
                                IconButton(onClick = onAddSubtask, modifier = Modifier.size(44.dp)) {
                                    CalinoIcon(CalinoIcon.Plus, tint = CalinoColors.Accent, modifier = Modifier.size(20.dp), contentDescription = context.getString(R.string.ed_add_subtask))
                                }
                            }
                            subtasks.forEach { child ->
                                SubtaskRow(child, onOpen = { onOpenSubtask(child) }, onToggle = { onToggleSubtask(child) })
                            }
                        }
                    }
                    HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                    if (editingNotes) {
                        CalinoMarkdownEditor(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            label = context.getString(R.string.ed_task_notes),
                            placeholder = context.getString(R.string.ed_add_task_notes),
                        )
                    } else if (notes.isBlank()) {
                        // An empty field is one row, not a four-line editor and its
                        // Write/Preview toolbar; the editor opens on tap.
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .clickable(role = Role.Button, onClickLabel = context.getString(R.string.ed_add_task_notes)) { editingNotes = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(38.dp)) {
                                CalinoIcon(CalinoIcon.Note, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
                            }
                            Text(context.getString(R.string.ed_add_notes), style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3)
                        }
                    } else {
                        // Tapping the text edits it; the checkboxes inside keep
                        // their own taps, so a list can still be ticked off.
                        Box(
                            Modifier.clickable(role = Role.Button, onClickLabel = context.getString(R.string.ed_edit_notes)) { editingNotes = true },
                        ) {
                            DetailRow(CalinoIcon.Note, context.getString(R.string.ed_notes), notes, markdown = true) { taskIndex, checked ->
                                if (!savingNotes) {
                                    val before = notes
                                    val changed = toggleCalinoMarkdownTask(before, taskIndex, checked)
                                    if (changed != before) {
                                        notes = changed
                                        savingNotes = true
                                        coroutineScope.launch {
                                            val input = task.asUpdate().copy(notes = changed.trim().ifEmpty { null })
                                            val saved = onInlineNotesSave(input, done)
                                            savingNotes = false
                                            if (saved) savedNotes = changed else notes = before
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // The editor's own single-choice pills, offered from the same
                    // list; a category the task already carries stays selectable
                    // even if it has since left that list.
                    val choices = remember(categories, task.category) {
                        (categories + listOfNotNull(task.category?.takeIf { it.isNotBlank() })).distinct()
                    }
                    if (choices.isNotEmpty()) {
                        HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Box(Modifier.width(38.dp).padding(top = 7.dp)) {
                                CalinoIcon(CalinoIcon.Filter, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
                            }
                            FlowRow(
                                Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                choices.forEach { choice ->
                                    val on = choice == category
                                    CalinoChip(
                                        text = choice,
                                        selected = on,
                                        description = context.getString(R.string.ed_choose_category),
                                        semanticsRole = Role.RadioButton,
                                        onClick = { category = if (on) "" else choice },
                                    )
                                }
                            }
                        }
                    }
                    // The floating pill overlaps the card, so reserve its lane as
                    // scrollable content rather than as a fixed blank footer.
                    Spacer(Modifier.height(CalinoSpacing.PillClearance))
                }
            }
        }
        TaskDeleteSheet(
            task = task.takeIf { scopeSheetOpen },
            onDismiss = { scopeSheetOpen = false },
            onDelete = { _, scope ->
                scopeSheetOpen = false
                deleteScope = scope
                pendingDelete = true
                confirmingDelete = false
                shown = false
            },
        )
    }
}

/**
 * One editable value on the task card: icon and text form the tap target, and an
 * optional clear action sits beside it as its own target rather than nested in
 * it. Every value row shares this size, weight and spacing, so the card reads as
 * one list; only the small mono labels above a control are a different voice.
 */
@Composable
private fun TaskRow(
    icon: CalinoIcon,
    text: String,
    set: Boolean,
    description: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    clearDescription: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val clearLabel = clearDescription ?: context.getString(R.string.ed_clear)
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 54.dp)
                .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(38.dp)) {
                CalinoIcon(
                    icon,
                    tint = if (set) CalinoColors.Accent else CalinoColors.Ink2,
                    modifier = Modifier.size(22.dp),
                    contentDescription = null,
                )
            }
            Text(
                text,
                style = CalinoTypography.bodyLarge,
                color = if (set) CalinoColors.Ink else CalinoColors.Ink3,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        if (onClear != null) {
            Text(
                clearLabel,
                style = CalinoTypography.bodyMedium,
                color = CalinoColors.Ink2,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClickLabel = clearDescription, onClick = onClear)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 12.dp),
            )
        }
    }
}

/**
 * One subtask under its parent. The circle is its own 44dp target and completes
 * it; the title opens it, so neither tap can be mistaken for the other.
 */
@Composable
private fun SubtaskRow(task: CalTask, onOpen: () -> Unit, onToggle: () -> Unit) {
    val context = LocalContext.current
    val fade = tween<Color>(CalinoMotion.ContentEnterMillis)
    val fill by animateColorAsState(if (task.done) CalinoColors.Accent else Color.Transparent, fade, label = "subtask fill")
    val edge by animateColorAsState(if (task.done) CalinoColors.Accent else CalinoColors.Ink3, fade, label = "subtask edge")
    val tick by animateFloatAsState(if (task.done) 1f else 0f, tween(CalinoMotion.ContentEnterMillis), label = "subtask tick")
    Row(Modifier.fillMaxWidth().padding(start = 25.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Checkbox,
                    onClickLabel = if (task.done) context.getString(R.string.ed_mark_subtask_open) else context.getString(R.string.ed_mark_subtask_done),
                    onClick = onToggle,
                )
                .semantics { contentDescription = context.getString(R.string.ed_task_subtask, task.title); stateDescription = if (task.done) context.getString(R.string.ed_done) else context.getString(R.string.ed_open) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(fill).border(1.5.dp, edge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CalinoIcon(
                    CalinoIcon.Check,
                    tint = CalinoColors.Panel,
                    modifier = Modifier.size(11.dp).alpha(tick),
                    contentDescription = null,
                )
            }
        }
        Text(
            task.title,
            style = CalinoTypography.bodyLarge,
            color = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink,
            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = context.getString(R.string.ed_open_subtask), onClick = onOpen)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(start = 2.dp),
        )
    }
}

/**
 * Recurring events keep their series start as the stable event identity, but
 * the detail surface is opened for a concrete occurrence. Show that tapped
 * date while retaining the series time and duration.
 */
private fun eventHeaderText(
    event: CalEvent,
    occurrenceDate: LocalDate?,
    timeFormat: CalinoTimeFormat,
    showEndTimes: Boolean,
    dateFormat: DateTimeFormatter,
    locale: Locale,
    allDayLabel: String,
    durationFormatter: (Int) -> String,
): String {
    val date = occurrenceDate ?: event.start?.toLocalDate() ?: event.date
    if (event.allDay || event.start == null) {
        return date?.format(dateFormat)?.let { "$it · $allDayLabel" } ?: allDayLabel
    }

    val displayedDate = date?.format(dateFormat) ?: event.start.format(dateFormat)
    return buildString {
        append(displayedDate)
        append(" · ")
        append(timeFormat.format(event.start, locale))
        event.durationMinutes?.takeIf { showEndTimes }?.let { minutes -> append(" · "); append(durationFormatter(minutes)) }
    }
}

@Composable
private fun DetailRow(
    icon: CalinoIcon,
    name: String,
    value: String,
    markdown: Boolean = false,
    onTaskCheckedChange: ((Int, Boolean) -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(38.dp).padding(top = 1.dp)) {
                CalinoIcon(icon, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
            }
            Column(Modifier.weight(1f)) {
                label(name)
                if (markdown) {
                    CalinoMarkdown(
                        value,
                        modifier = Modifier.padding(top = 6.dp),
                        onTaskCheckedChange = onTaskCheckedChange,
                    )
                } else {
                    Text(value, style = CalinoTypography.bodyLarge)
                }
            }
        }
    }
    HorizontalDivider(color = CalinoColors.Ink.copy(.06f))
}

@Composable
private fun Attendees(attendees: List<Attendee>) {
    val context = LocalContext.current
    Column(Modifier.padding(vertical = 15.dp)) {
        label(context.getString(R.string.ed_with))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
            attendees.take(3).forEach { attendee ->
                Box(Modifier.size(32.dp).clip(CircleShape).background(CalinoColors.Accent).semantics { contentDescription = attendee.name }) {
                    Text(attendee.name.take(1), color = CalinoColors.OnAccent, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                }
            }
            if (attendees.size > 3) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(CalinoColors.AccentSoft)) {
                    Text("+${attendees.size - 3}", color = CalinoColors.Ink2, fontSize = 11.sp, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
    HorizontalDivider(color = CalinoColors.Ink.copy(.06f))
}

/** Task ledger. A real callback is emitted at the threshold; the row stays in place
 * for the short undo window so a host that does not accept the change naturally
 * settles back instead of displaying a permanent fake success. */
@Composable
fun TasksSurface(
    tasks: List<CalTask> = fixtureTasks(),
    onComplete: (CalTask) -> Unit = {},
    onReschedule: (CalTask) -> Unit = {},
    onRescheduleTo: (CalTask, LocalDate) -> Unit = { task, _ -> onReschedule(task) },
    onTaskClick: (CalTask) -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit = { _, _ -> },
    onTaskDrop: (CalTask, CalTask?) -> Unit = { _, _ -> },
    onReopen: (CalTask) -> Unit = {},
) {
    val context = LocalContext.current
    val shortTaskDateFormat = localizedDateFormatter("MMM d")
    val taskDateFormat = localizedDateFormatter("EEE, MMM d")
    val taskWeekdayFormat = localizedDateFormatter("EEE")
    val today = LocalCalinoNow.current.today
    var filter by remember { mutableStateOf(TaskFilter.All) }
    var pendingCompletionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reschedulingTaskId by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val taskScope = rememberCoroutineScope()
    var previousDoneById by remember { mutableStateOf(tasks.associate { it.id to it.done }) }
    var collapsedTaskIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    // Parents whose subtasks from other sections are shown beneath them. Those
    // rows are hidden until asked for, since they already live in their own section.
    var peekedTaskIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val taskTree = remember(tasks) { TaskTree(tasks) }
    var draggingTaskId by remember { mutableStateOf<String?>(null) }
    var dragDistanceY by remember { mutableFloatStateOf(0f) }
    var dragDistanceX by remember { mutableFloatStateOf(0f) }
    // Positions are only read while a drag is recomposing. Keeping this map
    // non-snapshot avoids turning every ordinary LazyColumn scroll frame into
    // a whole TasksSurface recomposition.
    val taskDropBounds = remember { mutableMapOf<String, TaskDropBounds>() }

    // Completion from the detail sheet bypasses complete(), so without this
    // transition bridge the row jumps straight from its visible date bucket
    // to the uncomposed Completed bucket at the bottom of the LazyColumn. Keep
    // the same short checked settle used by inline completion; after it, the
    // row may move normally. This is presentation state only—the repository
    // has already committed the completion.
    LaunchedEffect(tasks) {
        val newlyCompleted = tasks.filter { task ->
            task.done && previousDoneById[task.id] == false && task.id !in pendingCompletionIds
        }.map { it.id }.toSet()
        previousDoneById = tasks.associate { it.id to it.done }
        if (newlyCompleted.isNotEmpty()) {
            pendingCompletionIds = pendingCompletionIds + newlyCompleted
            delay(CompletionVisualSettleMillis)
            pendingCompletionIds = pendingCompletionIds - newlyCompleted
        }
    }

    val recordTaskPosition: (CalTask, LayoutCoordinates) -> Unit = { task, coordinates ->
        val bounds = coordinates.boundsInRoot()
        val next = TaskDropBounds(bounds.top, bounds.bottom)
        if (taskDropBounds[task.id] != next) {
            taskDropBounds[task.id] = next
        }
    }

    val isPending = { task: CalTask -> task.id in pendingCompletionIds }
    val isOpenForBucket = { task: CalTask -> !task.done || isPending(task) }
    val weekFirst = today.startOfWeek(LocalCalinoPreferences.current.weekStart)
    val weekLast = weekFirst.plusDays(6)
    // The repository callback can update a task to `done` immediately. Keep a
    // completing row in its original date bucket until its short visual settle
    // finishes, so All and Active never briefly lose it or move it underneath
    // the undo affordance. An open week task that overlaps this week is listed
    // under "This week" whatever its due date, matching the Range shelf.
    val displayBucket = { task: CalTask ->
        val shown = if (isPending(task)) task.copy(done = false) else task
        if (shown.isSometimeThisWeek(weekFirst, weekLast)) TaskBucket.THIS_WEEK else taskBucket(shown, today)
    }

    // A collapsed parent hides the subtasks listed beneath it. A subtask that
    // sits in another section has no parent above it (it carries a parent line
    // instead), so collapsing must not make it vanish from its own section.
    fun isHiddenByCollapsedAncestor(task: CalTask): Boolean {
        val bucket = displayBucket(task)
        var parent = task.parentTaskId
        val visited = mutableSetOf<String>()
        while (parent != null && visited.add(parent)) {
            val ancestor = taskTree.task(parent) ?: return false
            if (parent in collapsedTaskIds && displayBucket(ancestor) == bucket) return true
            parent = ancestor.parentTaskId
        }
        return false
    }

    // Direct subtasks that are listed in a different section than their parent.
    fun outsideChildren(task: CalTask): List<CalTask> {
        val bucket = displayBucket(task)
        return taskTree.directChildren(task.id).filter { displayBucket(it) != bucket }
    }

    // With subtasks under it in this section the chevron follows the fold; a
    // parent whose subtasks are all elsewhere is open only while peeked.
    fun subtasksCollapsed(task: CalTask): Boolean {
        val hasInside = taskTree.directChildren(task.id).size > outsideChildren(task).size
        return if (hasInside) task.id in collapsedTaskIds else task.id !in peekedTaskIds
    }

    fun toggleSubtasks(task: CalTask) {
        if (subtasksCollapsed(task)) {
            collapsedTaskIds = collapsedTaskIds - task.id
            peekedTaskIds = peekedTaskIds + task.id
        } else {
            collapsedTaskIds = collapsedTaskIds + task.id
            peekedTaskIds = peekedTaskIds - task.id
        }
    }

    fun complete(task: CalTask) {
        if (task.id in pendingCompletionIds) return
        if (task.done) {
            // A done row's checkbox is a toggle: tapping it reopens the task.
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onReopen(task)
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Commit synchronously at release. The visual settle below is
        // independent of the composition, so navigating away cannot cancel
        // the actual repository mutation.
        onComplete(task)
        pendingCompletionIds = pendingCompletionIds + task.id

        taskScope.launch {
            delay(CompletionVisualSettleMillis)
            pendingCompletionIds = pendingCompletionIds - task.id
        }
    }

    val openTasks = tasks.filter { task -> !task.done || task.id in pendingCompletionIds }
    val renderTask: (CalTask) -> CalTask = { task ->
        if (isPending(task)) task.copy(done = true) else task
    }

    // The rows are rendered bucket-by-bucket, not in the repository's raw
    // order. Keep the drag target calculation in that same order so the row
    // highlighted under a dragged task is also the row that receives the
    // reparent operation.
    val dragOrder = remember(tasks, filter, pendingCompletionIds, collapsedTaskIds) {
        val candidates = when (filter) {
            TaskFilter.All -> tasks
            TaskFilter.Active -> openTasks
            TaskFilter.Completed -> tasks.filter { it.done && it.id !in pendingCompletionIds }
        }
        listOf(
            TaskBucket.OVERDUE,
            TaskBucket.TODAY,
            TaskBucket.THIS_WEEK,
            TaskBucket.LATER,
            TaskBucket.NO_DATE,
            TaskBucket.DONE,
        ).flatMap { bucket ->
            orderTasksByDue(candidates.filter { task ->
                !isHiddenByCollapsedAncestor(task) &&
                    displayBucket(task) == bucket &&
                    (bucket == TaskBucket.DONE || isOpenForBucket(task))
            })
        }
    }
    val dropTarget = remember(dragOrder, draggingTaskId, dragDistanceY) {
        val fromIndex = dragOrder.indexOfFirst { it.id == draggingTaskId }
        val dragged = dragOrder.getOrNull(fromIndex)
        val sourceBounds = dragged?.let { taskDropBounds[it.id] }
        if (fromIndex < 0 || dragged == null || sourceBounds == null) {
            null
        } else {
            val draggedCenter = (sourceBounds.top + sourceBounds.bottom) / 2f + dragDistanceY
            dragOrder.firstOrNull { target ->
                val targetBounds = taskDropBounds[target.id]
                targetBounds != null && draggedCenter >= targetBounds.top &&
                    draggedCenter <= targetBounds.bottom
            }?.takeIf { target ->
                target.id != dragged.id &&
                    target.parentTaskId != dragged.id &&
                    taskTree.canAdopt(dragged.id, target.id)
            }
        }
    }
    // Dragging a subtask clear of its indent is the inverse of dropping one row
    // onto another: it lets go of the parent without going through the menu.
    val unnestThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val unnestingTaskId = draggingTaskId?.takeIf { id ->
        dropTarget == null &&
            dragDistanceX <= -unnestThresholdPx &&
            tasks.firstOrNull { it.id == id }?.parentTaskId != null
    }
    val beginTaskDrag: (CalTask) -> Unit = { task -> draggingTaskId = task.id; dragDistanceY = 0f; dragDistanceX = 0f }
    val moveTaskDrag: (Offset) -> Unit = { amount -> dragDistanceY += amount.y; dragDistanceX += amount.x }
    val finishTaskDrag: () -> Unit = {
        val dragged = tasks.firstOrNull { it.id == draggingTaskId }
        // A null target is reserved for an explicit top-level action in the
        // menu. A drag that misses a valid task (including a cycle-forming
        // descendant) should simply spring back instead of unexpectedly
        // promoting the source task.
        if (dragged != null && dropTarget != null) {
            onTaskDrop(dragged, dropTarget)
        } else if (dragged != null && unnestingTaskId == dragged.id) {
            onTaskDrop(dragged, null)
        }
        draggingTaskId = null
        dragDistanceY = 0f
        dragDistanceX = 0f
    }
    val cancelTaskDrag: () -> Unit = { draggingTaskId = null; dragDistanceY = 0f; dragDistanceX = 0f }

    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        val windowWidth = maxWidth
        val splitPanes = shouldSplit(maxWidth.value.toInt(), maxHeight.value.toInt())
        // Tablet portrait keeps one readable column and puts the filter beside
        // the title; a phone stacks them.
        val wideColumn = !splitPanes && maxWidth >= TaskColumnWideMin
        val selectedTask = if (splitPanes) {
            tasks.firstOrNull { it.id == selectedTaskId }
                ?: tasks.firstOrNull { !it.done }
                ?: tasks.firstOrNull()
        } else null
        val openTask: (CalTask) -> Unit = if (splitPanes) { task -> selectedTaskId = task.id } else onTaskClick

        val listPane: @Composable (Modifier) -> Unit = { paneModifier ->
            Column(paneModifier) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    onOpenMenu?.let {
                        MenuButton(onClick = it, modifier = Modifier.padding(end = 4.dp))
                    }
                    Text(context.getString(R.string.ed_tasks_title), modifier = Modifier.weight(1f), style = CalinoTypography.displayLarge)
                    if (wideColumn) {
                        SegmentedFilter(filter, Modifier.width(330.dp)) { filter = it }
                    }
                }
                if (!wideColumn) SegmentedFilter(filter, Modifier.fillMaxWidth()) { filter = it }
                Spacer(Modifier.height(if (wideColumn) 6.dp else 4.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = filter,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            (fadeIn(tween(170)) + slideInHorizontally(tween(190)) { it / 5 }) togetherWith
                                (fadeOut(tween(130)) + slideOutHorizontally(tween(150)) { -it / 5 })
                        },
                        label = "task filter transition",
                    ) { activeFilter ->
                        val activeVisible = when (activeFilter) {
                            // Keep pending completions in All. renderTask
                            // supplies the checked presentation while the
                            // source item remains mounted for the undo/settle
                            // animation.
                            TaskFilter.All -> tasks
                            TaskFilter.Active -> openTasks
                            TaskFilter.Completed -> tasks.filter { it.done && it.id !in pendingCompletionIds }
                        }
                        fun LazyListScope.bucket(name: String, kind: TaskBucket) {
                            val done = kind == TaskBucket.DONE
                            // The long history is a preview in All; the
                            // Completed filter is the way to see all of it.
                            val limit = if (done && activeFilter == TaskFilter.All && !completedExpanded) CompletedPreviewCount else null
                        TaskBucket(
                            name,
                            context,
                                activeVisible.filter {
                                    !isHiddenByCollapsedAncestor(it) && displayBucket(it) == kind &&
                                        (done || isOpenForBucket(it))
                                },
                                ::complete,
                                if (done) ({ _ -> }) else ({ reschedulingTaskId = it.id }),
                                if (done) ({ _, _ -> }) else ({ task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) }),
                                reschedulingTaskId,
                                renderTask,
                                openTask,
                                taskTree,
                                ::subtasksCollapsed,
                                ::toggleSubtasks,
                                onTaskAction,
                                dropTarget,
                                beginTaskDrag,
                                moveTaskDrag,
                                finishTaskDrag,
                                cancelTaskDrag,
                                recordTaskPosition,
                                unnestingTaskId,
                                selectedTaskId = selectedTask?.id,
                                grouped = done,
                                previewLimit = limit,
                                onTogglePreview = if (done && activeFilter == TaskFilter.All) ({ completedExpanded = !completedExpanded }) else null,
                                previewExpanded = completedExpanded,
                                peekedTaskIds = peekedTaskIds,
                                outsideChildren = ::outsideChildren,
                                onCompleteAny = ::complete,
                            )
                        }
                        LazyColumn(
                            // Each row carries its own gap so the Completed rows
                            // can sit flush inside one grouped surface.
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            // The floating add pill is drawn by the shell over this
                            // list, so the reservation belongs in the scroll content.
                            contentPadding = PaddingValues(bottom = CalinoSpacing.PillClearance),
                            // The Completed bucket sits below the fold and is not
                            // composed until scrolled to, so a test that follows a row
                            // there needs a handle on the list itself.
                            modifier = Modifier.fillMaxSize().testTag("task-list"),
                        ) {
                            bucket(context.getString(R.string.ed_overdue), TaskBucket.OVERDUE)
                            bucket(context.getString(R.string.ed_today), TaskBucket.TODAY)
                            bucket(context.getString(R.string.ed_this_week), TaskBucket.THIS_WEEK)
                            bucket(context.getString(R.string.ed_later), TaskBucket.LATER)
                            bucket(context.getString(R.string.ed_no_date), TaskBucket.NO_DATE)
                            bucket(context.getString(R.string.ed_completed), TaskBucket.DONE)
                            if (activeVisible.isEmpty()) {
                                item(key = "tasks-empty:${activeFilter.name}") {
                                    TaskEmptyState(activeFilter)
                                }
                            }
                        }
                    }
                }
            }
        }

        when {
            splitPanes -> Row(Modifier.fillMaxSize()) {
                listPane(Modifier.width(minOf(TaskListPaneWidth, windowWidth * .42f)).fillMaxHeight().padding(horizontal = 16.dp))
                TaskDetailPane(
                    task = selectedTask,
                    taskTree = taskTree,
                    onComplete = ::complete,
                    onOpenTask = { selectedTaskId = it.id },
                    onEdit = onTaskClick,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            wideColumn -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                listPane(Modifier.widthIn(max = TaskColumnMaxWidth).fillMaxSize().padding(horizontal = 16.dp))
            }
            else -> listPane(Modifier.fillMaxSize().padding(horizontal = 16.dp))
        }
    }
}

private val TaskColumnWideMin = 600.dp
private val TaskColumnMaxWidth = 672.dp
private val TaskListPaneWidth = 470.dp
private const val CompletedPreviewCount = 5

@Composable
private fun TaskEmptyState(filter: TaskFilter) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(CalinoColors.AccentSoft), contentAlignment = Alignment.Center) { Text("✓", color = CalinoColors.Accent, fontSize = 20.sp) }
        Text(if (filter == TaskFilter.Completed) context.getString(R.string.ed_nothing_completed) else context.getString(R.string.ed_clear_slate), style = CalinoTypography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(if (filter == TaskFilter.Completed) context.getString(R.string.ed_finished_tasks_empty) else context.getString(R.string.ed_new_work_empty), style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun SegmentedFilter(selected: TaskFilter, modifier: Modifier, onSelected: (TaskFilter) -> Unit) {
    val context = LocalContext.current
    CompactSegmentedControl(
        options = TaskFilter.entries.map { filter ->
            when (filter) {
                TaskFilter.All -> context.getString(R.string.ed_task_filter_all)
                TaskFilter.Active -> context.getString(R.string.ed_task_filter_active)
                TaskFilter.Completed -> context.getString(R.string.ed_task_filter_completed)
            }
        },
        selectedIndex = TaskFilter.entries.indexOf(selected),
        onSelected = { onSelected(TaskFilter.entries[it]) },
        modifier = modifier,
        semanticLabel = context.getString(R.string.ed_task_filter),
        maxControlWidth = androidx.compose.ui.unit.Dp.Infinity,
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.TaskBucket(
    name: String,
    context: android.content.Context,
    tasks: List<CalTask>,
    onComplete: (CalTask) -> Unit,
    onRequestReschedule: (CalTask) -> Unit,
    onRescheduleTo: (CalTask, LocalDate) -> Unit,
    reschedulingTaskId: String?,
    renderTask: (CalTask) -> CalTask,
    onTaskClick: (CalTask) -> Unit,
    taskTree: TaskTree,
    isCollapsed: (CalTask) -> Boolean,
    onToggleSubtasks: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    dropTarget: CalTask?,
    onDragStart: (CalTask) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTaskPositioned: (CalTask, LayoutCoordinates) -> Unit,
    unnestingTaskId: String?,
    selectedTaskId: String? = null,
    /** Rows sit flush in one inset surface (the Completed history). */
    grouped: Boolean = false,
    /** Show only this many rows with a footer to reveal the rest. */
    previewLimit: Int? = null,
    onTogglePreview: (() -> Unit)? = null,
    previewExpanded: Boolean = false,
    /** Parents whose subtasks from other sections are shown beneath them. */
    peekedTaskIds: Set<String> = emptySet(),
    outsideChildren: (CalTask) -> List<CalTask> = { emptyList() },
    /** Completes an open subtask shown here under a parent of another section. */
    onCompleteAny: (CalTask) -> Unit = onComplete,
) {
    if (tasks.isNotEmpty()) {
        val ordered = orderTasksByDue(tasks)
        val orderedTasks = if (previewLimit != null) ordered.take(previewLimit) else ordered
        val hiddenCount = ordered.size - orderedTasks.size
        val hasFooter = grouped && onTogglePreview != null && (hiddenCount > 0 || previewExpanded)
        item(key = "bucket:$name") {
            label(
                context.getString(R.string.ed_task_bucket_count, name, tasks.size),
                Modifier
                    .animateItem()
                    .padding(top = 20.dp, bottom = 10.dp, start = 4.dp)
                    // These divide the list into sections a screen reader can
                    // jump between. Marked here rather than inside `label`,
                    // which is also used for field captions that are not
                    // headings.
                    .semantics { heading() },
            )
        }
        // A subtask whose parent is in another section has nothing above it to
        // hang from, so it is drawn at the top level with a parent line; only
        // a parent listed in this same section is a real indent and rail.
        val listed = orderedTasks.mapTo(mutableSetOf()) { it.id }
        // A peeked parent brings its subtasks from other sections along, as
        // read-only copies right under it; they stay in their own sections too.
        val entries = buildList {
            orderedTasks.forEach { task ->
                add(task to false)
                if (task.id in peekedTaskIds) orderTasksByDue(outsideChildren(task)).forEach { add(it to true) }
            }
        }
        val depthById = mutableMapOf<String, Int>()
        val depths = entries.map { (task, peek) ->
            val parentInList = task.parentTaskId?.takeIf { it in listed }
            val depth = when {
                peek -> (depthById[task.parentTaskId] ?: 0) + 1
                parentInList != null -> (depthById[parentInList] ?: 0) + 1
                else -> 0
            }
            if (!peek) depthById[task.id] = depth
            depth
        }
        val lineages = nestingLinesFor(depths)
        entries.forEachIndexed { index, (originalTask, peek) ->
            val task = renderTask(originalTask)
            val parent = if (peek) null else task.parentTaskId?.takeIf { it !in listed }?.let(taskTree::task)
            val children = if (peek) emptyList() else taskTree.directChildren(task.id)
            // A completion can move a row from its date bucket to Completed.
            // Give each bucket its own identity so LazyColumn fades the old
            // item out and the new item in instead of animating it through all
            // intervening rows and headers.
            item(key = if (peek) "peek:$name:${task.parentTaskId}:${task.id}" else "task:$name:${task.id}") {
                TaskRow(
                    task = task,
                    onComplete = if (peek) onCompleteAny else onComplete,
                    onReschedule = onRequestReschedule,
                    showReschedule = reschedulingTaskId == task.id,
                    onRescheduleTo = onRescheduleTo,
                    onClick = { onTaskClick(task) },
                    depth = depths[index],
                    nestingLines = lineages[index],
                    hasSubtasks = children.isNotEmpty(),
                    subtaskDone = children.count { it.done },
                    subtaskTotal = children.size,
                    parent = parent,
                    onOpenParent = { parent?.let(onTaskClick) },
                    selected = task.id == selectedTaskId,
                    groupPosition = if (!grouped) null else when {
                        entries.size == 1 && !hasFooter -> TaskGroupPosition.Only
                        index == 0 -> TaskGroupPosition.First
                        index == entries.lastIndex && !hasFooter -> TaskGroupPosition.Last
                        else -> TaskGroupPosition.Middle
                    },
                    subtasksCollapsed = isCollapsed(task),
                    onToggleSubtasks = { onToggleSubtasks(task) },
                    onTaskAction = onTaskAction,
                    isDropTarget = dropTarget?.id == task.id,
                    isUnnesting = unnestingTaskId == task.id,
                    // A peeked copy is not a drop target or a drag source: its
                    // real row, in its own section, owns those.
                    onDragStart = if (peek) null else ({ onDragStart(task) }),
                    onDrag = if (peek) null else onDrag,
                    onDragEnd = if (peek) null else onDragEnd,
                    onDragCancel = if (peek) null else onDragCancel,
                    onPositioned = if (peek) null else ({ coordinates -> onTaskPositioned(task, coordinates) }),
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (hasFooter) {
            item(key = "bucket-footer:$name") {
                TaskGroupFooter(
                    text = if (previewExpanded) context.getString(R.string.ed_show_fewer) else context.resources.getQuantityString(R.plurals.ed_more_completed_count, hiddenCount, hiddenCount),
                    onClick = { onTogglePreview?.invoke() },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

enum class TaskGroupPosition { First, Middle, Last, Only }

private fun TaskGroupPosition.shape(): RoundedCornerShape = when (this) {
    TaskGroupPosition.First -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    TaskGroupPosition.Middle -> RoundedCornerShape(0.dp)
    TaskGroupPosition.Last -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    TaskGroupPosition.Only -> RoundedCornerShape(20.dp)
}

/** The inset surface the Completed history sits in, a step away from the cards. */
private val TaskInsetFill: Color
    @Composable get() = CalinoColors.Ink.copy(alpha = .04f).compositeOver(CalinoColors.Canvas)

@Composable
private fun TaskGroupFooter(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(TaskGroupPosition.Last.shape())
            .background(TaskInsetFill)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = CalinoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = CalinoColors.Accent)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: CalTask,
    onComplete: (CalTask) -> Unit,
    onReschedule: (CalTask) -> Unit,
    showReschedule: Boolean = false,
    onRescheduleTo: (CalTask, LocalDate) -> Unit = { _, _ -> },
    onClick: (() -> Unit)? = null,
    depth: Int = 0,
    /** Per ancestor level, whether that level still has a row below this one. */
    nestingLines: List<Boolean> = emptyList(),
    hasSubtasks: Boolean = false,
    /** Direct subtasks done / total, shown on the parent. Zero total hides it. */
    subtaskDone: Int = 0,
    subtaskTotal: Int = 0,
    /** Set when the parent is listed in another section, so it is named here. */
    parent: CalTask? = null,
    onOpenParent: () -> Unit = {},
    /** The row whose detail the wide layout's pane is showing. */
    selected: Boolean = false,
    groupPosition: TaskGroupPosition? = null,
    subtasksCollapsed: Boolean = false,
    onToggleSubtasks: () -> Unit = {},
    onTaskAction: (TaskMenuAction, CalTask) -> Unit = { _, _ -> },
    isDropTarget: Boolean = false,
    isUnnesting: Boolean = false,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    onPositioned: ((LayoutCoordinates) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalCalinoLocale
    val taskDateFormat = localizedDateFormatter("EEE, MMM d")
    val shortTaskDateFormat = localizedDateFormatter("MMM d")
    val today = LocalCalinoNow.current.today
    var drag by remember(task.id) { mutableStateOf(0f) }
    var verticalDrag by remember(task.id) { mutableFloatStateOf(0f) }
    var liftedDrag by remember(task.id) { mutableStateOf(Offset.Zero) }
    var isLifted by remember(task.id) { mutableStateOf(false) }
    var isDragging by remember(task.id) { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) drag else 0f,
        animationSpec = spring(dampingRatio = .86f, stiffness = 520f),
        label = "swipe settle",
    )
    // The raw offset follows the finger. Animation is only used for the
    // release-to-rest leg; animating the live drag makes rows visibly lag.
    val offset = if (isDragging) drag else animatedOffset
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { 108.dp.toPx() }
    val maxDragPx = with(density) { 140.dp.toPx() }
    val color = taskColor(task)
    val grouped = groupPosition != null
    val rowShape = groupPosition?.shape() ?: RoundedCornerShape(20.dp)
    val insetFill = TaskInsetFill
    val selectedFill = CalinoColors.Ink.copy(alpha = .05f).compositeOver(CalinoColors.Panel)
    val restingFill = when {
        grouped -> insetFill
        selected -> selectedFill
        else -> CalinoColors.Panel
    }
    val rowFill by animateColorAsState(
        targetValue = if (isDropTarget) CalinoColors.AccentSoft.copy(alpha = .82f) else restingFill,
        animationSpec = tween(120),
        label = "task drop fill",
    )
    val rowBorder by animateColorAsState(
        targetValue = when {
            isDropTarget -> CalinoColors.Accent
            selected -> CalinoColors.Accent.copy(alpha = .55f)
            grouped -> Color.Transparent
            else -> CalinoColors.Ink.copy(.06f)
        },
        animationSpec = tween(120),
        label = "task drop border",
    )
    val hairline = CalinoColors.Ink.copy(.06f)
    val drawsDivider = groupPosition == TaskGroupPosition.First || groupPosition == TaskGroupPosition.Middle
    val canAct = !task.done
    val chevronRotation by animateFloatAsState(
        targetValue = if (subtasksCollapsed) 0f else 90f,
        animationSpec = tween(180),
        label = "subtask chevron",
    )
    val description = buildString {
        append(task.title)
        task.due?.let { append(context.getString(R.string.ed_task_due_accessibility, it.format(shortTaskDateFormat))) }
        task.dueTime?.let { append(context.getString(R.string.ed_task_at_accessibility, LocalTimeFormat.format(it, locale))) }
        task.category?.let { append(", "); append(it) }
        if (task.priority > 0) append(context.getString(R.string.ed_task_priority_accessibility, task.priority))
        if (!task.done && task.percentComplete > 0) append(context.getString(R.string.ed_task_percent_accessibility, task.percentComplete))
        if (task.recurrence != null) append(context.getString(R.string.ed_task_recurring_accessibility))
        if (task.done) append(context.getString(R.string.ed_task_completed_accessibility))
    }
    val actionProgress = (abs(offset) / actionThresholdPx).coerceIn(0f, 1f)
    val titleColor = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink
    var menuOpen by remember(task.id) { mutableStateOf(false) }
    val rowInteraction = if (onDrag != null) {
        Modifier.calinoLongPressDrag(
            onClick = onClick,
            onLongPress = { menuOpen = true },
            onDragStart = { menuOpen = false; verticalDrag = 0f; liftedDrag = Offset.Zero; isLifted = true; onDragStart?.invoke() },
            onDrag = { amount -> verticalDrag += amount.y; liftedDrag += amount; onDrag(amount) },
            onDragEnd = { _ -> onDragEnd?.invoke(); verticalDrag = 0f; liftedDrag = Offset.Zero; isLifted = false },
            onDragCancel = { onDragCancel?.invoke(); verticalDrag = 0f; liftedDrag = Offset.Zero; isLifted = false },
        )
    } else {
        Modifier.combinedClickable(
            enabled = onClick != null,
            onClick = { onClick?.invoke() },
            onLongClick = { menuOpen = true },
        )
    }

    Column(
        modifier
            .onGloballyPositioned { coordinates -> onPositioned?.invoke(coordinates) }
            .fillMaxWidth()
            // The gap below a card; a grouped row sits flush on the next one.
            .padding(bottom = if (grouped) 0.dp else 10.dp)
            .zIndex(if (abs(verticalDrag) > .5f) 1f else 0f),
    ) {
        // Move the whole card so the source stays visible while it is held.
        // Translating only the inner row would make the card disappear as the
        // parent clip follows its original bounds.
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(liftedDrag.x.roundToInt(), verticalDrag.roundToInt()) }
                // A carried row drops its own rails -- they would otherwise
                // travel with the card and hide that it has left the parent.
                // A grouped card is one flush surface: indent inside its fill
                // rather than carving a notch out of it with a rail. The
                // overhang spans the 10dp gap under the card above, so the
                // rail reads as one line from the parent down its children.
                .then(
                    if (grouped) Modifier else Modifier.taskNestIndent(
                        depth,
                        nestingLines,
                        drawRails = !isLifted,
                        railOverhang = 10.dp,
                    ),
                )
                .clip(rowShape),
        ) {
            if (canAct) {
                val actionLabel = if (offset < 0f) context.getString(R.string.ed_reschedule) else context.getString(R.string.ed_complete)
                val actionColor = if (offset < 0f) CalinoColors.Accent else CalinoColors.Green
                Row(
                    Modifier
                        .matchParentSize()
                        .background(actionColor.copy(alpha = actionProgress * .92f))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (offset < 0f) Arrangement.End else Arrangement.Start,
                ) {
                    CalinoIcon(
                        if (offset < 0f) CalinoIcon.Repeat else CalinoIcon.Check,
                        tint = CalinoColors.OnAccent.copy(alpha = actionProgress.coerceAtLeast(.72f)),
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel, color = CalinoColors.OnAccent.copy(alpha = actionProgress.coerceAtLeast(.72f)), style = CalinoTypography.bodyMedium)
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .offset { IntOffset(offset.roundToInt(), 0) }
                    .clip(rowShape)
                    .shadow(if (isDropTarget) 8.dp else 0.dp, rowShape, clip = false)
                    .background(rowFill)
                    .then(
                        if (drawsDivider) {
                            Modifier.drawBehind {
                                drawLine(hairline, Offset(0f, size.height - .5f), Offset(size.width, size.height - .5f), 1.dp.toPx())
                            }
                        } else Modifier,
                    )
                    .border(BorderStroke(if (isDropTarget) 2.dp else 1.dp, rowBorder), rowShape)
                    .semantics { contentDescription = description }
                    .pointerInput(task.id, canAct) {
                        if (canAct) detectHorizontalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                when {
                                    drag > actionThresholdPx -> onComplete(task)
                                    drag < -actionThresholdPx -> onReschedule(task)
                                }
                                isDragging = false
                                drag = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                drag = 0f
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                drag = (drag + amount).coerceIn(-maxDragPx, maxDragPx)
                            },
                        )
                    }
                    .then(rowInteraction)
                    .padding(start = if (grouped) (depth * TaskNestStep).dp else 0.dp)
                    // A grouped row is flush with its card, so the trailing
                    // date and count need their own inset from the edge.
                    .padding(end = if (grouped) 14.dp else 0.dp)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = { onComplete(task) })
                        .semantics {
                            contentDescription = if (task.done) context.getString(R.string.ed_task_completed_reopen, task.title)
                            else context.getString(R.string.ed_task_complete, task.title)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (task.done) 22.dp else 24.dp)
                            .clip(CircleShape)
                            // Done is a quiet green wash, not a solid disc: a
                            // long history should recede behind the open work.
                            .background(if (task.done) CalinoColors.Green.copy(alpha = .16f) else Color.Transparent)
                            .then(if (task.done) Modifier else Modifier.border(BorderStroke(1.6.dp, color), CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (task.done) {
                            CalinoIcon(
                                CalinoIcon.Check,
                                tint = CalinoColors.Green,
                                modifier = Modifier.size(13.dp),
                                contentDescription = null,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .semantics {
                            contentDescription = context.getString(R.string.ed_task_open_task, task.title)
                        }
                        .padding(top = if (parent != null) 0.dp else 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (parent != null) {
                        TaskParentLine(parent, onOpen = onOpenParent)
                    }
                    Text(
                        task.title,
                        style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = if (grouped && task.done) CalinoColors.Ink2 else titleColor,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = taskMetaLine(task, today, grouped)
                    if (meta != null || task.category != null) {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            meta?.let { line ->
                                Text(
                                    line.primary,
                                    color = line.primaryColor,
                                    style = CalinoTypography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                                line.secondary?.let {
                                    Text(it, color = CalinoColors.Ink3, style = CalinoTypography.bodySmall)
                                }
                            }
                            if (!grouped) task.category?.let { category ->
                                Text(
                                    category,
                                    style = CalinoTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = color.copy(alpha = .88f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(eventTint(color, .10f))
                                        .padding(horizontal = 7.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = isUnnesting,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(80)),
                ) {
                    Text(
                        context.getString(R.string.ed_move_top_level),
                        color = CalinoColors.Accent,
                        style = CalinoTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                AnimatedVisibility(
                    visible = isDropTarget,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(80)),
                ) {
                    Text(
                        context.getString(R.string.ed_make_subtask),
                        color = CalinoColors.Accent,
                        style = CalinoTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                if (hasSubtasks) {
                    // The count is the parent's only trace of subtasks that live
                    // in other sections, so it stays even when they are folded.
                    Row(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onToggleSubtasks)
                            .semantics {
                                contentDescription = context.getString(
                                    R.string.ed_task_subtasks_of_done,
                                    if (subtasksCollapsed) context.getString(R.string.ed_expand_subtasks) else context.getString(R.string.ed_collapse_subtasks),
                                    task.title,
                                    subtaskDone,
                                    subtaskTotal,
                                )
                            }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(CalinoColors.Ink.copy(alpha = .07f))
                                .padding(start = 9.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(context.getString(R.string.ed_subtask_progress, subtaskDone, subtaskTotal), style = CalinoTypography.labelSmall, color = CalinoColors.Ink2)
                            CalinoIcon(
                                CalinoIcon.Forward,
                                tint = CalinoColors.Ink2,
                                modifier = Modifier
                                    .padding(start = 3.dp)
                                    .size(13.dp)
                                    .graphicsLayer { rotationZ = chevronRotation },
                                contentDescription = null,
                            )
                        }
                    }
                }
                if (grouped) {
                    task.due?.let { due ->
                        Text(
                            due.format(shortTaskDateFormat).uppercase(locale),
                            style = CalinoTypography.labelSmall,
                            color = CalinoColors.Ink3,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            TaskActionMenu(
                task = task,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onAction = { onTaskAction(it, task) },
                hasSubtasks = hasSubtasks && !subtasksCollapsed,
            )
        }
        AnimatedVisibility(visible = showReschedule, enter = expandVertically(tween(180)) + fadeIn(tween(160)), exit = shrinkVertically(tween(160)) + fadeOut(tween(120))) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = (depth * TaskNestStep + 44).dp, top = 5.dp, bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(today to context.getString(R.string.ed_today), today.plusDays(1) to context.getString(R.string.ed_tomorrow), today.plusDays(7) to context.getString(R.string.ed_next_week)).forEach { (date, labelText) ->
                    TextButton(
                        onClick = { onRescheduleTo(task, date) },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(CalinoColors.Accent.copy(.09f))
                    .semantics { contentDescription = context.getString(R.string.ed_reschedule_task_to, task.title, labelText) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) { Text(labelText, color = CalinoColors.Ink2, fontSize = 12.sp) }
                }
            }
        }
    }
}

/**
 * The wide layout's detail pane: a read-only summary of the selected task with
 * its subtasks. Editing stays in the existing editor surface, which the pencil
 * opens, so the pane never becomes a second editor with its own write path.
 */
@Composable
private fun TaskDetailPane(
    task: CalTask?,
    taskTree: TaskTree,
    onComplete: (CalTask) -> Unit,
    onOpenTask: (CalTask) -> Unit,
    onEdit: (CalTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val today = LocalCalinoNow.current.today
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.padding(top = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(TaskInsetFill)
                .border(BorderStroke(1.dp, CalinoColors.Ink.copy(.06f)), shape),
        ) {
            androidx.compose.animation.Crossfade(
                targetState = task,
                animationSpec = tween(CalinoMotion.ContentEnterMillis),
                label = "task detail pane",
            ) { shown ->
                if (shown == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(context.getString(R.string.ed_select_task), style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3)
                    }
                } else {
                    TaskDetailPaneContent(shown, taskTree, today, onComplete, onOpenTask, onEdit)
                }
            }
        }
    }
}

@Composable
private fun TaskDetailPaneContent(
    task: CalTask,
    taskTree: TaskTree,
    today: LocalDate,
    onComplete: (CalTask) -> Unit,
    onOpenTask: (CalTask) -> Unit,
    onEdit: (CalTask) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalCalinoLocale
    val shortTaskDateFormat = localizedDateFormatter("EEE, MMM d")
    val color = taskColor(task)
    val subtasks = orderTasksByDue(taskTree.directChildren(task.id))
    val parent = task.parentTaskId?.let(taskTree::task)
    val meta = taskMetaLine(task, today, grouped = false)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 44.dp, end = 44.dp, top = 28.dp, bottom = CalinoSpacing.PillClearance + 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.ed_task_label), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CalinoColors.Ink.copy(.06f))
                    .clickable(role = Role.Button, onClickLabel = context.getString(R.string.ed_edit_task), onClick = { onEdit(task) })
                    .semantics { contentDescription = context.getString(R.string.ed_task_edit_named, task.title) },
                contentAlignment = Alignment.Center,
            ) {
                CalinoIcon(CalinoIcon.Edit, tint = CalinoColors.Ink, modifier = Modifier.size(20.dp), contentDescription = null)
            }
        }
        if (parent != null) {
            TaskParentLine(parent, Modifier.heightIn(min = 44.dp)) { onOpenTask(parent) }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !task.done, onClick = { onComplete(task) })
                    .semantics {
                        contentDescription = if (task.done) context.getString(R.string.ed_task_completed_status, task.title)
                        else context.getString(R.string.ed_task_complete, task.title)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (task.done) CalinoColors.Green.copy(alpha = .16f) else Color.Transparent)
                        .then(if (task.done) Modifier else Modifier.border(BorderStroke(1.8.dp, color), CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (task.done) CalinoIcon(CalinoIcon.Check, tint = CalinoColors.Green, modifier = Modifier.size(16.dp), contentDescription = null)
                }
            }
            Text(
                task.title,
                style = CalinoTypography.displayMedium,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                color = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
        }
        if (meta != null) {
            Row(Modifier.padding(start = 54.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(meta.primary, color = meta.primaryColor, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                meta.secondary?.let { Text(it, color = CalinoColors.Ink3, style = CalinoTypography.bodyLarge) }
            }
        }
        if (subtasks.isNotEmpty()) {
            label(
                context.getString(R.string.ed_task_subtasks_count, subtasks.count { it.done }, subtasks.size),
                Modifier.padding(top = 32.dp, bottom = 6.dp).semantics { heading() },
            )
            subtasks.forEach { sub ->
                HorizontalDivider(color = CalinoColors.Ink.copy(.06f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClickLabel = context.getString(R.string.ed_show_subtask), onClick = { onOpenTask(sub) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !sub.done, onClick = { onComplete(sub) })
                        .semantics {
                            contentDescription = if (sub.done) context.getString(R.string.ed_task_completed_status, sub.title)
                            else context.getString(R.string.ed_task_complete, sub.title)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (sub.done) CalinoColors.Green.copy(alpha = .16f) else Color.Transparent)
                                .then(if (sub.done) Modifier else Modifier.border(BorderStroke(1.6.dp, color), CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sub.done) CalinoIcon(CalinoIcon.Check, tint = CalinoColors.Green, modifier = Modifier.size(13.dp), contentDescription = null)
                        }
                    }
                    Text(
                        sub.title,
                        style = CalinoTypography.bodyLarge,
                        color = if (sub.done) CalinoColors.Ink2 else CalinoColors.Ink,
                        textDecoration = if (sub.done) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    sub.due?.let { due ->
                        Text(
                            due.format(shortTaskDateFormat).uppercase(locale),
                            style = CalinoTypography.labelSmall,
                            color = if (sub.done) CalinoColors.Ink3 else CalinoColors.Accent,
                        )
                    }
                }
            }
            HorizontalDivider(color = CalinoColors.Ink.copy(.06f))
        }
        task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            label(context.getString(R.string.ed_notes), Modifier.padding(top = 28.dp, bottom = 8.dp).semantics { heading() })
            CalinoMarkdown(notes, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** What a row says about its dates: an accent lead and a quieter relative note. */
private data class TaskMeta(val primary: String, val primaryColor: Color, val secondary: String? = null)

@Composable
private fun taskMetaLine(task: CalTask, today: LocalDate, grouped: Boolean): TaskMeta? {
    val context = LocalContext.current
    val locale = LocalCalinoLocale
    val timeFormat = LocalTimeFormat
    val taskDateFormat = localizedDateFormatter("EEE, MMM d")
    val shortTaskDateFormat = localizedDateFormatter("MMM d")
    val taskWeekdayFormat = localizedDateFormatter("EEE")
    // A completed row carries its date on the right, in the group's own voice.
    if (grouped) return null
    val due = task.due ?: return null
    if (!task.done && task.isWeekTask()) {
        val start = task.startDate!!
        val days = due.toEpochDay() - start.toEpochDay() + 1
        val text = if (days <= 7) {
            "${start.format(taskWeekdayFormat)} – ${due.format(taskWeekdayFormat)}"
        } else {
            "${start.format(shortTaskDateFormat)} – ${due.format(shortTaskDateFormat)}"
        }
        return TaskMeta(text, CalinoColors.Accent)
    }
    val date = due.format(taskDateFormat) +
        (task.dueTime?.let { " · ${timeFormat.format(it, locale)}" } ?: "")
    val late = due.isBefore(today) && !task.done
    return TaskMeta(date, if (late) CalinoColors.Rose else CalinoColors.Accent, relativeDayLabel(due, today, context))
}

private fun relativeDayLabel(date: LocalDate, today: LocalDate, context: android.content.Context): String {
    val days = date.toEpochDay() - today.toEpochDay()
    return when {
        days == 0L -> context.getString(R.string.ed_relative_today)
        days == 1L -> context.getString(R.string.ed_relative_tomorrow)
        days == -1L -> context.getString(R.string.ed_relative_yesterday)
        days > 1 -> context.resources.getQuantityString(R.plurals.ed_relative_days_future, days.toInt(), days.toInt())
        else -> context.resources.getQuantityString(R.plurals.ed_relative_days_past, (-days).toInt(), (-days).toInt())
    }
}

/**
 * Names the parent of a subtask that is listed apart from it. The parent's own
 * state rides on the line -- struck and ticked when done, a small open ring when
 * not -- so the row answers "is the parent finished?" without opening it.
 *
 * The line is visually compact; its tap target relies on Compose's 48dp
 * minimum-touch-target expansion rather than reserving layout space.
 */
@Composable
private fun TaskParentLine(parent: CalTask, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClickLabel = context.getString(R.string.ed_open_parent_task), onClick = onOpen)
            .padding(top = 1.dp, bottom = 3.dp)
            .semantics {
                contentDescription = context.getString(
                    R.string.ed_task_subtask_of_parent,
                    parent.title,
                    if (parent.done) context.getString(R.string.ed_task_parent_done) else context.getString(R.string.ed_task_parent_open),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CalinoIcons.CornerDownRight, null, tint = CalinoColors.Ink3, modifier = Modifier.size(13.dp))
        Text(
            parent.title,
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
            textDecoration = if (parent.done) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 5.dp, end = 5.dp).weight(1f, fill = false),
        )
        if (parent.done) {
            CalinoIcon(CalinoIcon.Check, tint = CalinoColors.Green, modifier = Modifier.size(11.dp), contentDescription = null)
        } else {
            Box(Modifier.size(7.dp).border(BorderStroke(1.4.dp, CalinoColors.Blue), CircleShape))
        }
    }
}

private fun fixtureEvents() = listOf(CalEvent("evt-design", "Design review", 0xFF5B7FB5, May18.atTime(10, 0), 60, recurrence = "FREQ=WEEKLY;BYDAY=MO;UNTIL=20260630T235959Z", location = "Studio", attendees = listOf(Attendee("Maya", "maya@example.com"), Attendee("Ivo", "ivo@example.com")), calendarId = "work"), CalEvent("evt-lunch", "Lunch with Maya", 0xFFC2697F, May18.atTime(12, 30), 90, location = "Café Lumen", calendarId = "personal"), CalEvent("evt-flight", "Flight to Berlin", 0xFFBF944E, null, null, allDay = true, calendarId = "travel"))
private fun fixtureTasks() = listOf(CalTask("task-inbox", "Review calendar notes", 0xFF5D9A78, May18, category = "Work"), CalTask("task-overdue", "Send itinerary", 0xFFBF944E, May18.minusDays(2), category = "Travel"), CalTask("task-buy", "Buy flowers", 0xFFC2697F, null, category = "Personal"), CalTask("task-done", "Book accommodation", 0xFF5B7FB5, May18.minusDays(1), true, "Travel"))

/** Stable state contract for an embedding screen. The backdrop is supplied by that screen. */
data class DayModalState(val date: LocalDate = May18, val visible: Boolean = true)
/**
 * The editor's mount contract. [draft] carries a seeded record when the host
 * opened the editor to change something that already exists.
 */
data class QuickAddSheetState(
    val visible: Boolean = false,
    val kind: QuickAddKind = QuickAddKind.Event,
    val date: LocalDate = May18,
    val draft: EditorDraft = blankEditorDraft(kind.toParserKind(), date),
    val morphFromAddPill: Boolean = false,
)

/** Day agenda sheet over a dimmed calendar fixture. */
@Composable
fun DayModal(state: DayModalState = DayModalState(), onDismiss: () -> Unit = {}, onAdd: () -> Unit = {}, onEvent: (CalEvent) -> Unit = {}) {
    var mounted by remember { mutableStateOf(state.visible) }
    LaunchedEffect(state.visible) {
        if (state.visible) mounted = true
    }
    if (mounted) {
        DayModalSurface(
            date = state.date,
            onDismiss = { mounted = false; onDismiss() },
            onAdd = onAdd,
            onEvent = onEvent,
            visible = state.visible,
        )
    }
}

/** Detail surface with contextual Edit/Join call action and three next instances. */
@Composable
fun EventDetail(
    event: CalEvent = fixtureEvents().first(),
    /**
     * True when the event's calendar cannot be written to -- an imported
     * device calendar, or a CalDAV collection the server grants no write
     * privilege on.
     *
     * The repository refuses such a write anyway, but a refusal is a poor
     * substitute for not offering the action: a person who taps Delete and
     * reads an apology has already decided to delete something.
     */
    readOnly: Boolean = false,
    onBack: () -> Unit = {},
    onPrimaryAction: () -> Unit = {},
    occurrenceDate: LocalDate? = null,
    events: List<CalEvent> = listOf(event),
    onEventSelected: (CalEvent) -> Unit = {},
    onEditEvent: (CalEvent) -> Unit = { onPrimaryAction() },
    onDeleteEvent: (CalEvent, RecurrenceEditScope) -> Unit = { _, _ -> },
    onEventAction: (EventMenuAction, CalEvent) -> Unit = { _, _ -> },
    onInlineSave: suspend (CalEvent, NewEvent, RecurrenceEditScope) -> Boolean = { _, _, _ -> false },
    selfAddresses: Set<String> = emptySet(),
    canRespond: Boolean = false,
    onRespond: suspend (CalEvent, String, RecurrenceEditScope) -> Boolean = { _, _, _ -> false },
    readOnlyForEvent: (CalEvent) -> Boolean = { readOnly },
    selfAddressesForEvent: (CalEvent) -> Set<String> = { selfAddresses },
    canRespondToEvent: (CalEvent) -> Boolean = { canRespond },
    localReminders: (CalEvent) -> List<Reminder>? = { null },
    onLocalReminders: ((CalEvent, List<Reminder>) -> Unit)? = null,
) = EventDetailSurface(
    event = event,
    readOnly = readOnly,
    onBack = onBack,
    onPrimary = onPrimaryAction,
    occurrenceDate = occurrenceDate,
    events = events,
    onEventSelected = onEventSelected,
    onEditEvent = onEditEvent,
    onDeleteEvent = onDeleteEvent,
    onEventAction = onEventAction,
    onInlineSave = onInlineSave,
    selfAddresses = selfAddresses,
    canRespond = canRespond,
    onRespond = onRespond,
    readOnlyForEvent = readOnlyForEvent,
    selfAddressesForEvent = selfAddressesForEvent,
    canRespondToEvent = canRespondToEvent,
    localReminders = localReminders,
    onLocalReminders = onLocalReminders,
)

@Composable
fun TaskDetail(
    task: CalTask,
    tasks: List<CalTask> = listOf(task),
    onBack: () -> Unit = {},
    onSave: suspend (NewTask, Boolean) -> Boolean = { _, _ -> true },
    onInlineNotesSave: suspend (NewTask, Boolean) -> Boolean = { _, _ -> false },
    onDelete: suspend (RecurrenceEditScope) -> Boolean = { true },
    onAddSubtask: () -> Unit = {},
    categories: List<String> = emptyList(),
    onOpenSubtask: (CalTask) -> Unit = {},
    onToggleSubtask: (CalTask) -> Unit = {},
    planningDate: LocalDate? = null,
) = TaskDetailSurface(
    task, tasks, onBack, onSave, onInlineNotesSave, onDelete, onAddSubtask, categories, onOpenSubtask, onToggleSubtask, planningDate,
)

/** Task ledger; horizontal drag reveals completion/rescheduling affordances. */
@Composable
fun Tasks(
    tasks: List<CalTask> = fixtureTasks(),
    onComplete: (CalTask) -> Unit = {},
    onReschedule: (CalTask) -> Unit = {},
    onRescheduleTo: (CalTask, LocalDate) -> Unit = { task, _ -> onReschedule(task) },
    onTaskClick: (CalTask) -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit = { _, _ -> },
    onTaskDrop: (CalTask, CalTask?) -> Unit = { _, _ -> },
    onReopen: (CalTask) -> Unit = {},
) = TasksSurface(tasks, onComplete, onReschedule, onRescheduleTo, onTaskClick, onOpenMenu, onTaskAction, onTaskDrop, onReopen)

/** Shared animated Event/Task/Journal editor sheet. */
@Composable
fun QuickAddSheet(
    state: QuickAddSheetState = QuickAddSheetState(visible = true),
    calendars: List<CalinoCalendar> = emptyList(),
    categories: List<String> = emptyList(),
    relatedCandidates: List<Pair<String, String>> = emptyList(),
    onPhoto: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    onSave: (EditorDraft) -> Unit = {},
    onSaveStarted: (EditorDraft) -> Unit = {},
) {
    var mounted by remember { mutableStateOf(state.visible) }
    LaunchedEffect(state.visible) {
        if (state.visible) mounted = true
    }
    if (mounted) {
        EditorSurface(
            initial = state.draft,
            baseDate = state.date,
            calendars = calendars,
            categories = categories,
            relatedCandidates = relatedCandidates,
            onPhoto = onPhoto,
            onDismiss = { mounted = false; onDismiss() },
            onSave = onSave,
            onSaveStarted = onSaveStarted,
            visible = state.visible,
            morphFromAddPill = state.morphFromAddPill,
        )
    }
}
