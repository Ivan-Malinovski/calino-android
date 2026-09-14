package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.EditorDraft
import calino.malinov.ski.data.model.blankEditorDraft
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.util.formatRecurrenceRule
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.parser.PocQuickAddKind
import calino.malinov.ski.data.parser.parseQuickAdd
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.state.FixtureNow
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.state.TaskTree
import calino.malinov.ski.ui.components.taskNestIndent
import calino.malinov.ski.ui.components.rememberDatePicker
import calino.malinov.ski.ui.components.TaskNestStep
import calino.malinov.ski.state.nestingLinesFor
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.formatCalinoDuration
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.design.eventTint
import calino.malinov.ski.qa.TaskBucket
import calino.malinov.ski.qa.taskBucket
import calino.malinov.ski.ui.components.BottomDetailOverlay
import calino.malinov.ski.ui.components.AdaptiveDetailCard
import calino.malinov.ski.ui.components.AdaptiveSurfaceHost
import calino.malinov.ski.ui.components.BottomDetailCard
import calino.malinov.ski.ui.components.LocalCalinoPillLane
import calino.malinov.ski.ui.components.LocalCalinoSurfaceMode
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.CalinoScrim
import calino.malinov.ski.ui.components.CalinoSheet
import calino.malinov.ski.ui.components.CalinoMarkdown
import calino.malinov.ski.ui.components.CalinoMarkdownEditor
import calino.malinov.ski.ui.components.ModalActionPill
import calino.malinov.ski.ui.components.MenuButton
import calino.malinov.ski.ui.components.CompactSegmentedControl
import calino.malinov.ski.ui.components.EventLocationButton
import calino.malinov.ski.ui.components.calinoLongPressDrag
import calino.malinov.ski.util.formatRecurrenceSummary
import calino.malinov.ski.util.nextOccurrences
import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.state.CalinoSurfaceMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

/** Compact action menu matching the web app's plain, paper-like context menu. */
@Composable
private fun CalinoActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 184.dp, max = 264.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line, shape)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun CalinoActionMenuItem(
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val itemShape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clip(itemShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = text }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            color = when {
                danger && enabled -> CalinoColors.Rose
                enabled -> CalinoColors.Ink
                else -> CalinoColors.Ink3
            },
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
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
        @Composable
        fun action(action: TaskMenuAction, text: String, enabled: Boolean = true) {
            CalinoActionMenuItem(text = text, enabled = enabled, danger = action == TaskMenuAction.Delete) {
                onDismiss()
                onAction(action)
            }
        }
        action(TaskMenuAction.Edit, "Edit task")
        if (!hasSubtasks) action(TaskMenuAction.AddSubtask, "Add subtask")
        if (task.parentTaskId != null) action(TaskMenuAction.Promote, "Move to top level")
        action(TaskMenuAction.Today, "Move to today", !task.done)
        action(TaskMenuAction.Tomorrow, "Move to tomorrow", !task.done)
        action(TaskMenuAction.NextWeek, "Move to next week", !task.done)
        action(TaskMenuAction.ToggleDone, if (task.done) "Mark as open" else "Mark as done")
        action(TaskMenuAction.Duplicate, "Duplicate")
        action(TaskMenuAction.ConvertToEvent, "Convert to event", task.due != null)
        action(TaskMenuAction.Delete, "Delete task")
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
        CalinoActionMenuItem("Edit event") { onDismiss(); onAction(EventMenuAction.Edit) }
        CalinoActionMenuItem("Share event") { onDismiss(); onAction(EventMenuAction.Share) }
        CalinoActionMenuItem("Duplicate") { onDismiss(); onAction(EventMenuAction.Duplicate) }
        CalinoActionMenuItem(
            "Convert to task",
            enabled = event.recurrence == null && event.recurrenceId == null && event.recurrenceDate == null,
        ) { onDismiss(); onAction(EventMenuAction.ConvertToTask) }
        CalinoActionMenuItem("Delete event", danger = true) { onDismiss(); onAction(EventMenuAction.Delete) }
    }
}

private data class CompletionUndo(val task: CalTask)

private const val CompletionVisualSettleMillis = 400L
private const val CompletionUndoWindowMillis = 5_000L

/**
 * The fixture anchor, for sample records and preview defaults only.
 *
 * Anything that means "today" reads [LocalCalinoNow] instead -- this used to be
 * a fourth frozen copy of the date and it made every Today/Tomorrow control
 * point at May 2026 even with a real account connected.
 */
private val May18 = FixtureNow.today
private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)

@Composable @ReadOnlyComposable
private fun eventColor(event: CalEvent) = CalinoColors.forEvent(Color(event.color))

@Composable @ReadOnlyComposable
private fun taskColor(task: CalTask) = CalinoColors.forEvent(Color(task.color))

private fun recurrenceSummary(event: CalEvent): String = formatRecurrenceSummary(event)

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
private fun label(text: String, modifier: Modifier = Modifier) = Text(text.uppercase(), modifier, style = CalinoTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, color = CalinoColors.Ink3), fontWeight = FontWeight.Bold)
@Composable
private fun IconButtonGlyph(glyph: String, description: String, onClick: () -> Unit) = IconButton(
    onClick = onClick,
    modifier = Modifier.size(44.dp).semantics { contentDescription = description },
) {
    Text(glyph, fontSize = 22.sp, color = CalinoColors.Ink, modifier = Modifier.alpha(.85f))
}

@Composable
private fun AgendaCard(event: CalEvent, onClick: () -> Unit = {}) {
    val color = eventColor(event)
    val timeFormat = LocalTimeFormat
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp), colors = CardDefaults.cardColors(CalinoColors.Panel), border = androidx.compose.foundation.BorderStroke(1.dp, CalinoColors.Ink.copy(alpha = .07f))) {
        Row(Modifier.padding(vertical = 10.dp, horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(3.dp)).background(color)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.start?.let { timeFormat.format(it) } ?: "ALL-DAY", style = CalinoTypography.labelSmall, color = CalinoColors.Ink2)
                Text(event.title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                event.location?.let { Text(it, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3) }
            }
            Text("›", fontSize = 25.sp, color = CalinoColors.Ink3)
        }
    }
}

@Composable
private fun JournalAgendaCard(entry: JournalEntry, onClick: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(CalinoColors.AccentSoft.copy(.58f))
            .border(1.dp, CalinoColors.Accent.copy(.16f), RoundedCornerShape(11.dp))
            .semantics { contentDescription = "Open journal entry ${entry.title.ifBlank { "Untitled note" }}" }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("✦", color = CalinoColors.Accent, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text("JOURNAL", style = CalinoTypography.labelSmall, color = CalinoColors.Accent)
            Text(entry.title.ifBlank { "Untitled note" }, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            Text(entry.body, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

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

    AdaptiveSurfaceHost(
        kind = CalinoSurfaceKind.Day,
        visible = shown,
        onDismiss = dismiss,
        scrimAlpha = .62f,
        contentDescription = "Dismiss day details",
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
            val shape = when (mode) {
                CalinoSurfaceMode.BottomSheet -> RoundedCornerShape(26.dp, 26.dp, 0.dp, 0.dp)
                CalinoSurfaceMode.FloatingWindow -> RoundedCornerShape(26.dp)
                CalinoSurfaceMode.EndPanel -> RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 0.dp, bottomEnd = 0.dp)
            }
            Surface(
                shape = shape,
                color = CalinoColors.Panel,
                modifier = Modifier.fillMaxSize().offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) },
            ) {
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
                        Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 4.dp).clip(CircleShape).background(CalinoColors.Ink.copy(.16f)))
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(pageDate.format(dateFormat), style = CalinoTypography.titleLarge)
                                label(if (pageDate == today) "Today · ${dayEvents.size} events" else "${dayEvents.size} events")
                            }
                            IconButtonGlyph("×", "Close day", dismiss)
                        }
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
                            if (dayEvents.isEmpty()) {
                                item(key = "empty:$pageDate") {
                                    AnimatedVisibility(visible = true, enter = fadeIn(tween(160)) + expandVertically(tween(180))) {
                                        Text("Nothing scheduled", style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3, modifier = Modifier.padding(20.dp))
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
                        HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Add on ${pageDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.US))}", style = CalinoTypography.bodyLarge, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { closeAfterAnimation(onAdd) },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(CalinoColors.Ink),
                                modifier = Modifier.size(46.dp).semantics { contentDescription = "Add on ${pageDate.format(dateFormat)}" },
                            ) { Text("+", fontSize = 24.sp) }
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
    onBack: () -> Unit = {},
    onPrimary: () -> Unit = {},
    occurrenceDate: LocalDate? = null,
    events: List<CalEvent> = listOf(event),
    onEventSelected: (CalEvent) -> Unit = {},
    onEditEvent: (CalEvent) -> Unit = { onPrimary() },
    onDeleteEvent: (CalEvent, RecurrenceEditScope) -> Unit = { _, _ -> },
    onEventAction: (EventMenuAction, CalEvent) -> Unit = { _, _ -> },
    onInlineSave: suspend (CalEvent, NewEvent, RecurrenceEditScope) -> Boolean = { _, _, _ -> false },
) {
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
    BackHandler(enabled = shown) { closeAfterAnimation(onBack) }

    val pager = rememberPagerState(
        initialPage = events.indexOfFirst { it.id == event.id }.coerceAtLeast(0),
        pageCount = { events.size },
    )
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
    // Base includes handle/header, date/time/location/description rows, the
    // divider, and the real pill clearance. Add only what this event renders.
    val preferredPreviewHeight = (
        420 +
            (descriptionLines - 1) * 22 +
            attendeeLines * 24 +
            (if (sizingEvent.recurrence != null) 54 else 0) +
            (if (sizingEvent.reminders.isNotEmpty()) 54 else 0) +
            (if (sizingEvent.travelTimeMinutes != null) 54 else 0)
        ).coerceAtMost(560).dp
    var pillState by remember { mutableStateOf(EventPreviewPillState(false, {}, {}, {})) }
    BottomDetailOverlay(
        visible = shown,
        onDismiss = { closeAfterAnimation(onBack) },
        surfaceKind = CalinoSurfaceKind.EventPreviewCompact,
        preferredSurfaceHeight = preferredPreviewHeight,
        pill = {
            val state = pillState
            ModalActionPill(
                    minExpandedWidth = 300.dp,
                    addLabel = "Add event",
                    morphFromAddPill = true,
                    inPillLane = true,
                    expanded = shown,
                    cancelLabel = "Cancel",
                    onCancel = { closeAfterAnimation(onBack) },
                    cancelDescription = "Close event preview",
                    secondaryLabel = "Open",
                    onSecondary = state.onOpen,
                    secondaryDescription = "Open event",
                    primaryLabel = if (state.dirty) "Save" else "Delete",
                    onPrimary = if (state.dirty) state.onSave else state.onDelete,
                    primaryDescription = if (state.dirty) "Save event changes" else "Delete event",
            )
        },
    ) { overlayModifier ->
        HorizontalPager(
            state = pager,
            key = { events[it].id },
            beyondViewportPageCount = 1,
            userScrollEnabled = shown,
            modifier = overlayModifier,
        ) { page ->
            val pageEvent = events[page]
            Box(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                AdaptiveDetailCard(
                    visible = shown,
                    onDismiss = { closeAfterAnimation(onBack) },
                    modifier = Modifier.fillMaxSize(),
                    dismissDistance = 980.dp,
                    handleColor = eventTint(eventColor(pageEvent), .13f, CalinoColors.Panel),
                    allowDownwardDismissInEndPanel = true,
                ) { cardModifier ->
                    EventDetailContent(pageEvent,
                        onBack = { closeAfterAnimation(onBack) },
                        onPrimary = { onEditEvent(pageEvent) },
                        onDeleteEvent = { target, scope ->
                            closeAfterAnimation { onDeleteEvent(target, scope) }
                        },
                        onInlineSave = onInlineSave,
                        active = page == pager.currentPage,
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
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    onDeleteEvent: (CalEvent, RecurrenceEditScope) -> Unit,
    onInlineSave: suspend (CalEvent, NewEvent, RecurrenceEditScope) -> Boolean,
    active: Boolean,
    onPillState: (EventPreviewPillState) -> Unit,
) {
    val tint = eventTint(eventColor(event), .13f, CalinoColors.Panel)
    val original = remember(event) { eventPreviewDraft(event) }
    var draft by remember(event.id, event.etag) { mutableStateOf(original) }
    var dateText by remember(event.id, event.etag) { mutableStateOf(original.date.toString()) }
    val originalTimeText = remember(original) {
        if (original.startTime == null) "All day" else original.startTime.toString() + " – " +
            original.startTime.plusMinutes(original.durationMinutes?.toLong() ?: 0).toString()
    }
    var timeText by remember(event.id, event.etag) { mutableStateOf(originalTimeText) }
    var error by remember(event.id) { mutableStateOf<String?>(null) }
    var saving by remember(event.id) { mutableStateOf(false) }
    var saveScope by remember(event.id) {
        mutableStateOf(if (event.recurrenceId != null || event.recurrenceDate != null) RecurrenceEditScope.This else RecurrenceEditScope.All)
    }
    var pendingOpen by remember(event.id) { mutableStateOf(false) }
    var scopePrompt by remember(event.id) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var confirmDelete by remember(event.id) { mutableStateOf(false) }
    var deleteScope by remember(event.id, event.recurrenceId, event.recurrenceDate) {
        mutableStateOf(defaultEventDeleteScope(event))
    }
    val dirty = draft != original || dateText != original.date.toString() || timeText != originalTimeText
    fun save(openAfter: Boolean) {
        val validDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
        val timeParts = timeText.split('–', '-', limit = 2).map(String::trim)
        val allDayText = timeText.equals("all day", true)
        val validStart = timeParts.getOrNull(0)?.takeUnless { allDayText }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val validEnd = timeParts.getOrNull(1)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val validation = when {
            validDate == null -> "Use a date in YYYY-MM-DD format."
            !allDayText && (validStart == null || validEnd == null) -> "Use times in HH:MM format."
            else -> draft.validationError()
        }
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
                scopePrompt = false
                if (openAfter || pendingOpen) onPrimary()
            }
        }
    }
    val openAction = { if (!saving) { if (dirty) save(true) else onPrimary() } }
    val saveAction = { if (!saving) save(false) }
    val deleteAction = { confirmDelete = true }
    LaunchedEffect(dirty, saving, active) {
        if (active) onPillState(EventPreviewPillState(dirty, openAction, saveAction, deleteAction))
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(52.dp).background(tint)) {
            EventPreviewArtwork(eventPreviewDecoration(draft.title), Modifier.matchParentSize().alpha(.22f))
            Row(
                Modifier.fillMaxSize().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(eventColor(event), CircleShape))
                BasicTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it); error = null },
                    textStyle = CalinoTypography.headlineSmall.copy(color = CalinoColors.Ink),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp).semantics { contentDescription = "Event title" },
                )
                IconButtonGlyph("×", "Close event preview", onBack)
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { PreviewEditRow(CalinoIcon.Calendar, "Date", dateText) {
                dateText = it; runCatching { LocalDate.parse(it) }.getOrNull()?.let { value -> draft = draft.copy(date = value) }
            } }
            item {
                PreviewEditRow(CalinoIcon.Clock, "Time", timeText) { value ->
                    timeText = value
                    if (value.equals("all day", true)) { draft = draft.copy(startTime = null, durationMinutes = null) }
                    else {
                        val parts = value.split('–', '-', limit = 2).map(String::trim)
                        if (parts.size == 2) {
                            val start = runCatching { LocalTime.parse(parts[0]) }.getOrNull()
                            val end = runCatching { LocalTime.parse(parts[1]) }.getOrNull()
                            if (start != null && end != null) draft = draft.copy(startTime = start,
                                durationMinutes = java.time.Duration.between(start, end).toMinutes().toInt())
                        }
                    }
                }
            }
            item {
                PreviewEditRow(
                    CalinoIcon.Pin,
                    "Location",
                    draft.location.orEmpty(),
                    onValue = { draft = draft.copy(location = it) },
                    trailing = { EventLocationButton(draft.location) },
                )
            }
            if (event.recurrence != null) item { PreviewStaticRow(CalinoIcon.Repeat, "Repeats", recurrenceSummary(event)) }
            if (event.reminders.isNotEmpty()) item { PreviewStaticRow(CalinoIcon.Bell, "Reminder", event.reminders.joinToString { "${it.minutesBefore} minutes before" }) }
            event.travelTimeMinutes?.takeIf { it > 0 }?.let { minutes ->
                item { PreviewStaticRow(CalinoIcon.Clock, "Travel time", formatCalinoDuration(minutes)) }
            }
            if (event.attendees.isNotEmpty()) item { PreviewStaticRow(CalinoIcon.Users, "Attendees", event.attendees.joinToString { it.name.ifBlank { it.email } }) }
            item { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = CalinoColors.Ink.copy(.1f)) }
            item { PreviewEditRow(CalinoIcon.Note, "Description", draft.description, if (draft.description.isBlank()) "+ Add description" else "") { draft = draft.copy(description = it) } }
            error?.let { message -> item { Text(message, color = CalinoColors.Rose, style = CalinoTypography.bodySmall, modifier = Modifier.padding(12.dp)) } }
        }
        AnimatedVisibility(scopePrompt) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Apply changes to", style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecurrenceEditScope.entries.forEach { option ->
                        val text = when(option) { RecurrenceEditScope.This -> "This"; RecurrenceEditScope.Future -> "This and future"; RecurrenceEditScope.All -> "Entire series" }
                        CalinoChip(text, saveScope == option, "Save $text", onClick = { saveScope = option }, semanticsRole = Role.RadioButton)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ scopePrompt = false }) { Text("Cancel") }
                    TextButton({ save(pendingOpen) }) { Text("Continue") }
                }
            }
        }
        AnimatedVisibility(
            visible = confirmDelete,
            enter = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(110)),
        ) {
            EventDeleteConfirmBody(
                event = event,
                scope = deleteScope,
                onScope = { deleteScope = it },
                onCancel = { confirmDelete = false },
                onConfirm = { onDeleteEvent(event, deleteScope) },
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }
        // This compact card's pill is 56dp high and sits close to the card
        // edge; the global 96dp editor clearance needlessly hid the final row.
        Spacer(Modifier.height(76.dp))
    }
}

private data class EventPreviewPillState(
    val dirty: Boolean,
    val onOpen: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
)

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

@Composable
private fun PreviewStaticRow(icon: CalinoIcon, labelText: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        CalinoIcon(icon, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
        Column(Modifier.padding(start = 16.dp)) { label(labelText); Text(value, style = CalinoTypography.bodyLarge) }
    }
}

@Composable
private fun EventPreviewArtwork(decoration: EventPreviewDecoration?, modifier: Modifier = Modifier) {
    if (decoration == null) return
    val ink = CalinoColors.Ink.copy(alpha = .55f)
    Canvas(modifier) {
        when (decoration) {
            EventPreviewDecoration.Mountain -> {
                drawLine(ink, Offset(size.width * .55f, size.height * .86f), Offset(size.width * .72f, size.height * .3f), 4f, StrokeCap.Round)
                drawLine(ink, Offset(size.width * .72f, size.height * .3f), Offset(size.width * .94f, size.height * .86f), 4f, StrokeCap.Round)
                drawLine(ink, Offset(size.width * .73f, size.height * .45f), Offset(size.width * .79f, size.height * .58f), 3f, StrokeCap.Round)
            }
            else -> {
                drawCircle(ink, radius = size.minDimension * .18f, center = Offset(size.width * .78f, size.height * .52f), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                drawLine(ink, Offset(size.width * .65f, size.height * .75f), Offset(size.width * .91f, size.height * .3f), 3f, StrokeCap.Round)
            }
        }
    }
}

/** True when the event is a series master, a detached override, or an expansion. */
fun isRecurringEvent(event: CalEvent): Boolean =
    event.recurrence != null || event.recurrenceId != null || event.recurrenceDate != null

/**
 * The scope a delete confirmation should open on. A single occurrence defaults
 * to [RecurrenceEditScope.This] so confirming without touching the chips never
 * removes more of the series than the user pointed at.
 */
fun defaultEventDeleteScope(event: CalEvent): RecurrenceEditScope =
    if (event.recurrenceId != null || event.recurrenceDate != null) {
        RecurrenceEditScope.This
    } else {
        RecurrenceEditScope.All
    }

/**
 * The shared delete confirmation. The detail card expands it inline; the
 * context menu shows it in [EventDeleteSheet]. Both must offer the same scope
 * choice, so the body lives here rather than in either host.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EventDeleteConfirmBody(
    event: CalEvent,
    scope: RecurrenceEditScope,
    onScope: (RecurrenceEditScope) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recurring = isRecurringEvent(event)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (recurring) "Remove this recurring event?" else "Remove this event?",
            style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        )
        if (recurring) {
            Text("Choose which part of the series to remove.", style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                RecurrenceEditScope.entries.forEach { option ->
                    val label = when (option) {
                        RecurrenceEditScope.This -> "This event"
                        RecurrenceEditScope.Future -> "This and future"
                        RecurrenceEditScope.All -> "Entire series"
                    }
                    CalinoChip(
                        text = label,
                        selected = scope == option,
                        description = "Delete scope $label",
                        semanticsRole = Role.RadioButton,
                        onClick = { onScope(option) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(CalinoColors.Rose),
            ) { Text("Delete") }
        }
    }
}

/**
 * Delete confirmation for the event overflow menu, which is opened from the
 * agenda and calendar surfaces where there is no detail card to expand into.
 * [event] is null while nothing is pending, so the sheet keeps rendering the
 * last event through its exit animation.
 */
@Composable
fun EventDeleteSheet(
    event: CalEvent?,
    onDismiss: () -> Unit,
    onDelete: (CalEvent, RecurrenceEditScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf<CalEvent?>(null) }
    LaunchedEffect(event) { if (event != null) shown = event }
    val target = event ?: shown ?: return
    var scope by remember(target.id) { mutableStateOf(defaultEventDeleteScope(target)) }
    BackHandler(enabled = event != null, onBack = onDismiss)
    Box(modifier.fillMaxSize()) {
        CalinoScrim(visible = event != null, onDismiss = onDismiss)
        CalinoSheet(
            visible = event != null,
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    target.title,
                    style = CalinoTypography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                EventDeleteConfirmBody(
                    event = target,
                    scope = scope,
                    onScope = { scope = it },
                    onCancel = onDismiss,
                    onConfirm = { onDelete(target, scope) },
                )
            }
        }
    }
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
    ModalActionPill(
        addLabel = (occurrenceDate ?: event.start?.toLocalDate() ?: event.date)?.let { "Add on ${it.format(dateFormat)}" } ?: "Add event",
        morphFromAddPill = true,
        inPillLane = true,
        expanded = expanded,
        cancelLabel = "Cancel",
        onCancel = onBack,
        cancelDescription = "Close event details",
        secondaryLabel = "Edit",
        onSecondary = onPrimary,
        secondaryDescription = "Edit event",
        primaryLabel = "···",
        onPrimary = onMoreOpen,
        primaryDescription = "More event actions",
    )
}

/**
 * Fixture-backed task detail/editor. The task body is the primary tap target
 * in both the calendar and task ledger; completion and rescheduling remain
 * separate row actions so opening a task never mutates it accidentally.
 */
@Composable
fun TaskDetailSurface(
    task: CalTask,
    tasks: List<CalTask> = listOf(task),
    onBack: () -> Unit = {},
    onSave: (NewTask, Boolean) -> Unit = { _, _ -> },
    onAddSubtask: () -> Unit = {},
) {
    val today = LocalCalinoNow.current.today
    var title by remember(task.id) { mutableStateOf(task.title) }
    var category by remember(task.id) { mutableStateOf(task.category.orEmpty()) }
    var notes by remember(task.id) { mutableStateOf(task.notes.orEmpty()) }
    var due by remember(task.id) { mutableStateOf(task.due) }
    var done by remember(task.id) { mutableStateOf(task.done) }
    var priority by remember(task.id) { mutableIntStateOf(task.priority) }
    var percentComplete by remember(task.id) { mutableIntStateOf(task.percentComplete) }
    var shown by remember(task.id) { mutableStateOf(true) }
    var pendingSave by remember(task.id) { mutableStateOf(false) }
    var requestedDone by remember(task.id) { mutableStateOf<Boolean?>(null) }
    val headerTint = eventTint(taskColor(task), .13f, CalinoColors.Panel)
    val pickDueDate = rememberDatePicker({ due ?: today }) { due = it }

    LaunchedEffect(shown) {
        if (!shown) {
            delay(220)
            if (pendingSave) {
                // Completion is latched separately from presentation state.
                // The sheet exits before saving, and the delayed coroutine can
                // otherwise observe the pre-click `done` value from its exit
                // composition even though the checkmark already changed.
                val savedDone = requestedDone ?: done
                onSave(
                    NewTask(
                        title = title.trim(),
                        due = due,
                        color = task.color,
                        category = category.trim().ifEmpty { null },
                        dueTime = task.dueTime,
                        notes = notes.trim().ifEmpty { null },
                        reminder = task.reminder,
                        priority = priority,
                        percentComplete = if (savedDone) 100 else percentComplete.coerceAtMost(99),
                        status = if (savedDone) "COMPLETED" else if (percentComplete > 0) "IN-PROCESS" else "NEEDS-ACTION",
                        completedAt = task.completedAt,
                        recurrence = task.recurrence,
                        uid = task.uid,
                        href = task.href,
                        etag = task.etag,
                        calendarId = task.calendarId,
                        parentTaskId = task.parentTaskId,
                        recurrenceId = task.recurrenceId,
                        recurrenceDate = task.recurrenceDate,
                        sequence = task.sequence,
                    ),
                    savedDone,
                )
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
    BackHandler(enabled = shown) { dismiss(false) }

    BottomDetailCard(
        visible = shown,
        onDismiss = { dismiss(false) },
        modifier = Modifier.fillMaxSize(),
        dismissDistance = 980.dp,
        surfaceKind = CalinoSurfaceKind.Preview,
        handleColor = headerTint,
        pill = {
            val canSave = title.trim().isNotEmpty()
            ModalActionPill(
                addLabel = "New task",
                morphFromAddPill = true,
                inPillLane = true,
                expanded = shown,
                cancelLabel = "Cancel",
                onCancel = { dismiss(false) },
                cancelDescription = "Cancel task editing",
                primaryLabel = "Save",
                onPrimary = { dismiss(true) },
                primaryEnabled = canSave,
                primaryDescription = "Save task",
                secondaryLabel = if (done) "Mark as open" else "Mark as done",
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
                secondaryDescription = if (done) "Mark task as open" else "Mark task as done",
            )
        },
    ) { detailModifier ->
        Column(
            detailModifier
                .fillMaxSize()
                .background(CalinoColors.Canvas),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 70.dp).background(headerTint)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CalinoIcon(
                        CalinoIcon.Check,
                        tint = taskColor(task),
                        modifier = Modifier.size(23.dp),
                        contentDescription = null,
                    )
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 70.dp)
                            .padding(horizontal = 14.dp, vertical = 18.dp)
                            .semantics { contentDescription = "Task title" },
                        textStyle = CalinoTypography.headlineSmall.copy(color = CalinoColors.Ink),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (title.isBlank()) {
                                    Text("Add task title", style = CalinoTypography.headlineSmall.copy(color = CalinoColors.Ink3))
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                PreviewEditRow(CalinoIcon.Filter, "Category", category, "Add category") { category = it }
                HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                PreviewEditRow(CalinoIcon.Note, "Notes", notes, "Add task notes") { notes = it }
                HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                val subtasks = tasks.filter { it.parentTaskId == task.id }
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CalinoIcon(CalinoIcon.Check, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
                        label("Subtasks", Modifier.weight(1f).padding(start = 16.dp))
                        TextButton(onClick = onAddSubtask, modifier = Modifier.heightIn(min = 44.dp)) {
                            Text("Add", color = CalinoColors.Accent)
                        }
                    }
                    if (subtasks.isEmpty()) {
                        Text("No subtasks yet", color = CalinoColors.Ink3, fontSize = 12.sp)
                    } else {
                        subtasks.forEach { child ->
                            Text(
                                "• ${child.title}",
                                color = if (child.done) CalinoColors.Ink3 else CalinoColors.Ink2,
                                textDecoration = if (child.done) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.padding(start = 5.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CalinoIcon(CalinoIcon.Calendar, tint = CalinoColors.Ink2, modifier = Modifier.size(22.dp), contentDescription = null)
                        label("Due date", Modifier.padding(start = 16.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val choices = listOf(
                            today to "Today",
                            today.plusDays(1) to "Tomorrow",
                            today.plusDays(7) to "Next week",
                            null to "No date",
                        )
                        choices.forEach { (date, text) ->
                            val selected = due == date
                            TextButton(
                                onClick = { due = date },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selected) CalinoColors.AccentSoft else CalinoColors.Panel)
                                    .semantics {
                                        contentDescription = if (selected) "$text, selected" else "Set due date to $text"
                                    },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                Text(text, fontSize = 10.sp, color = if (selected) CalinoColors.Accent else CalinoColors.Ink2, maxLines = 1)
                            }
                        }
                    }
                    TextButton(
                        onClick = pickDueDate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(CalinoColors.Panel)
                            .semantics { contentDescription = "Choose a custom due date" },
                    ) {
                        Text("Choose date…", color = CalinoColors.Accent)
                    }
                    due?.let { selectedDue ->
                        Text(
                            selectedDue.format(dateFormat),
                            color = CalinoColors.Ink3,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
                HorizontalDivider(color = CalinoColors.Ink.copy(.08f))
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    label("Priority")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "None", 1 to "High", 5 to "Medium", 9 to "Low").forEach { (value, text) ->
                            val selected = priority == value
                            TextButton(
                                onClick = { priority = value },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                    .semantics { contentDescription = "$text priority${if (selected) ", selected" else ""}" },
                            ) { Text(text, color = if (selected) CalinoColors.Accent else CalinoColors.Ink2, fontSize = 11.sp) }
                        }
                    }
                    label("Progress · $percentComplete%")
                    androidx.compose.material3.Slider(
                        value = percentComplete.toFloat(),
                        onValueChange = { percentComplete = it.toInt(); done = percentComplete == 100 },
                        valueRange = 0f..100f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .semantics { contentDescription = "Task progress, $percentComplete percent" },
                    )
                    task.recurrence?.let { Text(formatRecurrenceRule(it, task.due ?: today), color = CalinoColors.Ink3, fontSize = 12.sp) }
                }
            }
            // The pill stands in the pill lane rather than in this card, so
            // it can change shape in place; this holds its room open.
            Spacer(Modifier.height(CalinoSpacing.PillClearance))
        }
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
): String {
    val date = occurrenceDate ?: event.start?.toLocalDate() ?: event.date
    if (event.allDay || event.start == null) {
        return date?.format(dateFormat)?.let { "$it · All day" } ?: "All day"
    }

    val displayedDate = date?.format(dateFormat) ?: event.start.format(dateFormat)
    return buildString {
        append(displayedDate)
        append(" · ")
        append(timeFormat.format(event.start))
        event.durationMinutes?.takeIf { showEndTimes }?.let { minutes -> append(" · "); append(formatCalinoDuration(minutes)) }
    }
}

@Composable
private fun DetailRow(icon: String, name: String, value: String, markdown: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(icon, fontSize = 20.sp, color = CalinoColors.Ink3, modifier = Modifier.width(38.dp).padding(top = 1.dp))
            Column(Modifier.weight(1f)) {
                label(name)
                if (markdown) {
                    CalinoMarkdown(value, modifier = Modifier.padding(top = 6.dp))
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
    Column(Modifier.padding(vertical = 15.dp)) {
        label("With")
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
    onUndoComplete: (CalTask) -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit = { _, _ -> },
    onTaskDrop: (CalTask, CalTask?) -> Unit = { _, _ -> },
) {
    val today = LocalCalinoNow.current.today
    var filter by remember { mutableStateOf(TaskFilter.All) }
    var pendingCompletionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var completionUndo by remember { mutableStateOf<List<CompletionUndo>>(emptyList()) }
    var reschedulingTaskId by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val taskScope = rememberCoroutineScope()
    var completionJobs by remember { mutableStateOf<Map<String, Job>>(emptyMap()) }
    var previousDoneById by remember { mutableStateOf(tasks.associate { it.id to it.done }) }
    var collapsedTaskIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
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

    fun isHiddenByCollapsedAncestor(task: CalTask): Boolean {
        var parent = task.parentTaskId
        val visited = mutableSetOf<String>()
        while (parent != null && visited.add(parent)) {
            if (parent in collapsedTaskIds) return true
            parent = tasks.firstOrNull { it.id == parent }?.parentTaskId
        }
        return false
    }

    fun complete(task: CalTask) {
        if (task.done || task.id in pendingCompletionIds || completionUndo.any { it.task.id == task.id }) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Commit synchronously at release. The visual settle below is
        // independent of the composition, so navigating away cannot cancel
        // the actual repository mutation.
        onComplete(task)
        completionUndo = completionUndo + CompletionUndo(task)
        pendingCompletionIds = pendingCompletionIds + task.id

        taskScope.launch {
            delay(CompletionVisualSettleMillis)
            pendingCompletionIds = pendingCompletionIds - task.id
        }
        val expiryJob = taskScope.launch {
            delay(CompletionUndoWindowMillis)
            completionUndo = completionUndo.filterNot { it.task.id == task.id }
            completionJobs = completionJobs - task.id
        }
        completionJobs = completionJobs + (task.id to expiryJob)
    }

    val openTasks = tasks.filter { task -> !task.done || task.id in pendingCompletionIds }
    val isPending = { task: CalTask -> task.id in pendingCompletionIds }
    val isOpenForBucket = { task: CalTask -> !task.done || isPending(task) }
    // The repository callback can update a task to `done` immediately. Keep a
    // completing row in its original date bucket until its short visual settle
    // finishes, so All and Active never briefly lose it or move it underneath
    // the undo affordance.
    val displayBucket = { task: CalTask ->
        taskBucket(if (isPending(task)) task.copy(done = false) else task, today)
    }
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
            candidates.filter { task ->
                !isHiddenByCollapsedAncestor(task) &&
                    displayBucket(task) == bucket &&
                    (bucket == TaskBucket.DONE || isOpenForBucket(task))
            }
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

    Column(Modifier.fillMaxSize().background(CalinoColors.Canvas).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            onOpenMenu?.let {
                MenuButton(onClick = it, modifier = Modifier.padding(end = 4.dp))
            }
            Text("Tasks", modifier = Modifier.weight(1f), style = CalinoTypography.displayLarge)
        }
        SegmentedFilter(filter) { filter = it }
        TaskProgress(tasks)
        Spacer(Modifier.height(14.dp))
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
                    // Keep pending completions in All. renderTask supplies the
                    // checked presentation while the source item remains
                    // mounted for the undo/settle animation.
                    TaskFilter.All -> tasks
                    TaskFilter.Active -> openTasks
                    TaskFilter.Completed -> tasks.filter { it.done && it.id !in pendingCompletionIds }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // The floating add pill is drawn by the shell over this
                    // list, so the reservation belongs in the scroll content.
                    contentPadding = PaddingValues(bottom = CalinoSpacing.PillClearance),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TaskBucket(
                        "Overdue",
                        activeVisible.filter { !isHiddenByCollapsedAncestor(it) && isOpenForBucket(it) && displayBucket(it) == TaskBucket.OVERDUE },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                        taskTree,
                        collapsedTaskIds,
                        { task -> collapsedTaskIds = if (task.id in collapsedTaskIds) collapsedTaskIds - task.id else collapsedTaskIds + task.id },
                        onTaskAction,
                        dropTarget,
                        beginTaskDrag,
                        moveTaskDrag,
                        finishTaskDrag,
                        cancelTaskDrag,
                        recordTaskPosition,
                        unnestingTaskId,
                    )
                    TaskBucket(
                        "Today",
                        activeVisible.filter { !isHiddenByCollapsedAncestor(it) && isOpenForBucket(it) && displayBucket(it) == TaskBucket.TODAY },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                        taskTree,
                        collapsedTaskIds,
                        { task -> collapsedTaskIds = if (task.id in collapsedTaskIds) collapsedTaskIds - task.id else collapsedTaskIds + task.id },
                        onTaskAction,
                        dropTarget,
                        beginTaskDrag,
                        moveTaskDrag,
                        finishTaskDrag,
                        cancelTaskDrag,
                        recordTaskPosition,
                        unnestingTaskId,
                    )
                    TaskBucket(
                        "This week",
                        activeVisible.filter { !isHiddenByCollapsedAncestor(it) && isOpenForBucket(it) && displayBucket(it) == TaskBucket.THIS_WEEK },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                        taskTree,
                        collapsedTaskIds,
                        { task -> collapsedTaskIds = if (task.id in collapsedTaskIds) collapsedTaskIds - task.id else collapsedTaskIds + task.id },
                        onTaskAction,
                        dropTarget,
                        beginTaskDrag,
                        moveTaskDrag,
                        finishTaskDrag,
                        cancelTaskDrag,
                        recordTaskPosition,
                        unnestingTaskId,
                    )
                    TaskBucket(
                        "Later",
                        activeVisible.filter { !isHiddenByCollapsedAncestor(it) && isOpenForBucket(it) && displayBucket(it) == TaskBucket.LATER },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                        taskTree,
                        collapsedTaskIds,
                        { task -> collapsedTaskIds = if (task.id in collapsedTaskIds) collapsedTaskIds - task.id else collapsedTaskIds + task.id },
                        onTaskAction,
                        dropTarget,
                        beginTaskDrag,
                        moveTaskDrag,
                        finishTaskDrag,
                        cancelTaskDrag,
                        recordTaskPosition,
                        unnestingTaskId,
                    )
                    TaskBucket(
                        "No date",
                        activeVisible.filter { !isHiddenByCollapsedAncestor(it) && isOpenForBucket(it) && displayBucket(it) == TaskBucket.NO_DATE },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                        taskTree,
                        collapsedTaskIds,
                        { task -> collapsedTaskIds = if (task.id in collapsedTaskIds) collapsedTaskIds - task.id else collapsedTaskIds + task.id },
                        onTaskAction,
                        dropTarget,
                        beginTaskDrag,
                        moveTaskDrag,
                        finishTaskDrag,
                        cancelTaskDrag,
                        recordTaskPosition,
                        unnestingTaskId,
                    )
                    TaskBucket(
                        "Completed",
                        activeVisible.filter { !isHiddenByCollapsedAncestor(it) && displayBucket(it) == TaskBucket.DONE },
                        {},
                        {},
                        { _, _ -> },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                        taskTree,
                        collapsedTaskIds,
                        { task -> collapsedTaskIds = if (task.id in collapsedTaskIds) collapsedTaskIds - task.id else collapsedTaskIds + task.id },
                        onTaskAction,
                        dropTarget,
                        beginTaskDrag,
                        moveTaskDrag,
                        finishTaskDrag,
                        cancelTaskDrag,
                        recordTaskPosition,
                        unnestingTaskId,
                    )
                    if (activeVisible.isEmpty()) {
                        item(key = "tasks-empty:${activeFilter.name}") {
                            TaskEmptyState(activeFilter)
                        }
                    }
                }
            }

            // The undo banner is the only bottom-aligned action left; keep it
            // clear of the floating add pill the shell draws over this list.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = CalinoSpacing.PillClearance),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = completionUndo.isNotEmpty(),
                    enter = slideInVertically(tween(220), initialOffsetY = { it / 2 }) +
                        expandVertically(tween(220), expandFrom = Alignment.Bottom) +
                        fadeIn(tween(180)),
                    exit = slideOutVertically(tween(180), targetOffsetY = { it / 2 }) +
                        shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom) +
                        fadeOut(tween(140)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val completed = completionUndo
                    if (completed.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CalinoColors.Ink).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (completed.size == 1) "Completed" else "${completed.size} tasks completed",
                                color = CalinoColors.OnInk,
                                style = CalinoTypography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    completed.forEach { item -> onUndoComplete(item.task) }
                                    val completedIds = completed.map { it.task.id }.toSet()
                                    pendingCompletionIds = pendingCompletionIds - completedIds
                                    completedIds.forEach { completionJobs[it]?.cancel() }
                                    completionJobs = completionJobs - completedIds
                                    completionUndo = emptyList()
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = if (completed.size == 1) {
                                        "Undo completing ${completed.first().task.title}"
                                    } else {
                                        "Undo completing ${completed.size} tasks"
                                    }
                                },
                            ) { Text(if (completed.size == 1) "Undo" else "Undo all", color = CalinoColors.AccentSoft) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskProgress(tasks: List<CalTask>) {
    val completed = tasks.count { it.done }
    val progress by animateFloatAsState(
        targetValue = if (tasks.isEmpty()) 0f else completed.toFloat() / tasks.size,
        animationSpec = tween(240),
        label = "task completion progress",
    )
    Column(Modifier.fillMaxWidth().padding(top = 7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TASK PROGRESS", style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
            Spacer(Modifier.weight(1f))
            Text(
                "$completed of ${tasks.size} complete",
                style = CalinoTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = CalinoColors.Ink2,
            )
        }
        Box(Modifier.fillMaxWidth().padding(top = 7.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(CalinoColors.Ink.copy(.07f))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(CalinoColors.Green))
        }
    }
}

@Composable
private fun TaskEmptyState(filter: TaskFilter) {
    Column(Modifier.fillMaxWidth().padding(vertical = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(CalinoColors.AccentSoft), contentAlignment = Alignment.Center) { Text("✓", color = CalinoColors.Accent, fontSize = 20.sp) }
        Text(if (filter == TaskFilter.Completed) "Nothing completed yet" else "A clear slate", style = CalinoTypography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(if (filter == TaskFilter.Completed) "Finished tasks will settle here." else "New work can land here when you are ready.", style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun SegmentedFilter(selected: TaskFilter, onSelected: (TaskFilter) -> Unit) {
    CompactSegmentedControl(
        options = TaskFilter.entries.map { it.name },
        selectedIndex = TaskFilter.entries.indexOf(selected),
        onSelected = { onSelected(TaskFilter.entries[it]) },
        modifier = Modifier.fillMaxWidth(),
        semanticLabel = "Task filter",
        maxControlWidth = 360.dp,
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.TaskBucket(
    name: String,
    tasks: List<CalTask>,
    onComplete: (CalTask) -> Unit,
    onRequestReschedule: (CalTask) -> Unit,
    onRescheduleTo: (CalTask, LocalDate) -> Unit,
    reschedulingTaskId: String?,
    renderTask: (CalTask) -> CalTask,
    onTaskClick: (CalTask) -> Unit,
    taskTree: TaskTree,
    collapsedTaskIds: Set<String>,
    onToggleSubtasks: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    dropTarget: CalTask?,
    onDragStart: (CalTask) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTaskPositioned: (CalTask, LayoutCoordinates) -> Unit,
    unnestingTaskId: String?,
) {
    if (tasks.isNotEmpty()) {
        item(key = "bucket:$name") {
            label(
                "$name · ${tasks.size}",
                Modifier
                    .animateItem()
                    .padding(top = 10.dp, bottom = 3.dp)
                    // These divide the list into sections a screen reader can
                    // jump between. Marked here rather than inside `label`,
                    // which is also used for field captions that are not
                    // headings.
                    .semantics { heading() },
            )
        }
        // Rails are drawn from the rendered order: a level keeps its rail when a
        // later row still sits at that depth before the list climbs above it.
        val depths = tasks.map { taskTree.depth(it.id) }
        val lineages = nestingLinesFor(depths)
        tasks.forEachIndexed { index, originalTask ->
            val task = renderTask(originalTask)
            // A completion can move a row from its date bucket to Completed.
            // Give each bucket its own identity so LazyColumn fades the old
            // item out and the new item in instead of animating it through all
            // intervening rows and headers.
            item(key = "task:$name:${task.id}") {
                TaskRow(
                    task = task,
                    onComplete = onComplete,
                    onReschedule = onRequestReschedule,
                    showReschedule = reschedulingTaskId == task.id,
                    onRescheduleTo = onRescheduleTo,
                    onClick = { onTaskClick(task) },
                    depth = depths[index],
                    nestingLines = lineages[index],
                    hasSubtasks = taskTree.directChildren(task.id).isNotEmpty(),
                    subtasksCollapsed = task.id in collapsedTaskIds,
                    onToggleSubtasks = { onToggleSubtasks(task) },
                    onTaskAction = onTaskAction,
                    isDropTarget = dropTarget?.id == task.id,
                    isUnnesting = unnestingTaskId == task.id,
                    onDragStart = { onDragStart(task) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onPositioned = { coordinates -> onTaskPositioned(task, coordinates) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
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
    val rowShape = RoundedCornerShape(16.dp)
    val rowFill by animateColorAsState(
        targetValue = if (isDropTarget) CalinoColors.AccentSoft.copy(alpha = .82f) else CalinoColors.Panel,
        animationSpec = tween(120),
        label = "task drop fill",
    )
    val rowBorder by animateColorAsState(
        targetValue = if (isDropTarget) CalinoColors.Accent else CalinoColors.Ink.copy(.045f),
        animationSpec = tween(120),
        label = "task drop border",
    )
    val canAct = !task.done
    val chevronRotation by animateFloatAsState(
        targetValue = if (subtasksCollapsed) 0f else 90f,
        animationSpec = tween(180),
        label = "subtask chevron",
    )
    val description = buildString {
        append(task.title)
        task.due?.let { append(", due "); append(it.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))) }
        task.category?.let { append(", "); append(it) }
        if (task.priority > 0) append(", priority ${task.priority}")
        if (!task.done && task.percentComplete > 0) append(", ${task.percentComplete} percent complete")
        if (task.recurrence != null) append(", recurring")
        if (task.done) append(", completed")
    }
    val actionProgress = (abs(offset) / actionThresholdPx).coerceIn(0f, 1f)
    val titleColor = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink
    var menuOpen by remember(task.id) { mutableStateOf(false) }
    val rowInteraction = if (onDrag != null) {
        Modifier.calinoLongPressDrag(
            onClick = onClick,
            onLongPress = { menuOpen = true },
            onDragStart = { verticalDrag = 0f; liftedDrag = Offset.Zero; isLifted = true; onDragStart?.invoke() },
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
                .taskNestIndent(depth, nestingLines, drawRails = !isLifted)
                .clip(rowShape),
        ) {
            if (canAct) {
                val actionLabel = if (offset < 0f) "Reschedule" else "Complete"
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
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canAct, onClick = { onComplete(task) })
                        .semantics { contentDescription = if (task.done) "${task.title}, completed" else "Complete ${task.title}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (task.done) color else Color.Transparent)
                            .border(BorderStroke(1.5.dp, color), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (task.done) {
                            CalinoIcon(
                                CalinoIcon.Check,
                                tint = CalinoColors.OnAccent,
                                modifier = Modifier.size(14.dp),
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
                            contentDescription = "Open task: ${task.title}"
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        task.title,
                        style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = titleColor,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        task.due?.let { due ->
                            Text(
                                due.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)),
                                color = if (due.isBefore(today) && !task.done) CalinoColors.Rose else CalinoColors.Ink3,
                                style = CalinoTypography.bodySmall,
                            )
                        }
                        task.category?.let { category ->
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
                AnimatedVisibility(
                    visible = isUnnesting,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(80)),
                ) {
                    Text(
                        "Move to top level",
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
                        "Make subtask",
                        color = CalinoColors.Accent,
                        style = CalinoTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                if (hasSubtasks) {
                    IconButton(
                        onClick = onToggleSubtasks,
                        modifier = Modifier
                            .size(44.dp)
                            .semantics {
                                contentDescription = if (subtasksCollapsed) {
                                    "Expand subtasks of ${task.title}"
                                } else {
                                    "Collapse subtasks of ${task.title}"
                                }
                            },
                    ) {
                        CalinoIcon(
                            CalinoIcon.Forward,
                            tint = CalinoColors.Ink3,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                            contentDescription = null,
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
                listOf(today to "Today", today.plusDays(1) to "Tomorrow", today.plusDays(7) to "Next week").forEach { (date, labelText) ->
                    TextButton(
                        onClick = { onRescheduleTo(task, date) },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(CalinoColors.Accent.copy(.09f))
                            .semantics { contentDescription = "Reschedule ${task.title} to $labelText" },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) { Text(labelText, color = CalinoColors.Ink2, fontSize = 12.sp) }
                }
            }
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
    onBack: () -> Unit = {},
    onPrimaryAction: () -> Unit = {},
    occurrenceDate: LocalDate? = null,
    events: List<CalEvent> = listOf(event),
    onEventSelected: (CalEvent) -> Unit = {},
    onEditEvent: (CalEvent) -> Unit = { onPrimaryAction() },
    onDeleteEvent: (CalEvent, RecurrenceEditScope) -> Unit = { _, _ -> },
    onEventAction: (EventMenuAction, CalEvent) -> Unit = { _, _ -> },
    onInlineSave: suspend (CalEvent, NewEvent, RecurrenceEditScope) -> Boolean = { _, _, _ -> false },
) = EventDetailSurface(
    event = event,
    onBack = onBack,
    onPrimary = onPrimaryAction,
    occurrenceDate = occurrenceDate,
    events = events,
    onEventSelected = onEventSelected,
    onEditEvent = onEditEvent,
    onDeleteEvent = onDeleteEvent,
    onEventAction = onEventAction,
    onInlineSave = onInlineSave,
)

@Composable
fun TaskDetail(
    task: CalTask,
    tasks: List<CalTask> = listOf(task),
    onBack: () -> Unit = {},
    onSave: (NewTask, Boolean) -> Unit = { _, _ -> },
    onAddSubtask: () -> Unit = {},
) = TaskDetailSurface(task, tasks, onBack, onSave, onAddSubtask)

/** Task ledger; horizontal drag reveals completion/rescheduling affordances. */
@Composable
fun Tasks(
    tasks: List<CalTask> = fixtureTasks(),
    onComplete: (CalTask) -> Unit = {},
    onReschedule: (CalTask) -> Unit = {},
    onRescheduleTo: (CalTask, LocalDate) -> Unit = { task, _ -> onReschedule(task) },
    onTaskClick: (CalTask) -> Unit = {},
    onUndoComplete: (CalTask) -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit = { _, _ -> },
    onTaskDrop: (CalTask, CalTask?) -> Unit = { _, _ -> },
) = TasksSurface(tasks, onComplete, onReschedule, onRescheduleTo, onTaskClick, onUndoComplete, onOpenMenu, onTaskAction, onTaskDrop)

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
