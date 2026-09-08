package calino.malinov.ski.poc.ui.surfaces

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.data.model.Attendee
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.occursOn
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.NewTask
import calino.malinov.ski.poc.data.model.EditorDraft
import calino.malinov.ski.poc.data.model.blankEditorDraft
import calino.malinov.ski.poc.data.repository.CalinoCalendar
import calino.malinov.ski.poc.data.parser.PocQuickAddKind
import calino.malinov.ski.poc.data.parser.parseQuickAdd
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.design.eventTint
import calino.malinov.ski.poc.qa.TaskBucket
import calino.malinov.ski.poc.qa.taskBucket
import calino.malinov.ski.poc.ui.components.BottomDetailOverlay
import calino.malinov.ski.poc.ui.components.DetailCardSurface
import calino.malinov.ski.poc.ui.components.BottomDetailCard
import calino.malinov.ski.poc.ui.components.SwipeDownDismiss
import calino.malinov.ski.poc.ui.components.CalinoIcon
import calino.malinov.ski.poc.ui.components.MenuButton
import calino.malinov.ski.poc.ui.components.CompactSegmentedControl
import calino.malinov.ski.poc.util.formatRecurrenceSummary
import calino.malinov.ski.poc.util.nextOccurrences
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Small, stable routes to make these surfaces easy to wire into a pager later. */
sealed interface PockRoute {
    data object Day : PockRoute
    data object Agenda : PockRoute
    data object Detail : PockRoute
    data object TaskDetail : PockRoute
    data object Tasks : PockRoute
    data object Journal : PockRoute
    data object Settings : PockRoute
    data object QuickAdd : PockRoute
    data object Notifications : PockRoute
}
enum class QuickAddKind { Event, Task, Journal }
enum class TaskFilter { All, Active, Completed }

private data class CompletionUndo(val task: CalTask)

private const val CompletionVisualSettleMillis = 400L
private const val CompletionUndoWindowMillis = 5_000L

private val May18 = LocalDate.of(2026, 5, 18)
private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

private fun eventColor(event: CalEvent) = Color(event.color)
private fun taskColor(task: CalTask) = Color(task.color)

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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp), colors = CardDefaults.cardColors(CalinoColors.Panel), border = androidx.compose.foundation.BorderStroke(1.dp, CalinoColors.Ink.copy(alpha = .07f))) {
        Row(Modifier.padding(vertical = 10.dp, horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(3.dp)).background(color)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.start?.format(timeFormat) ?: "ALL-DAY", style = CalinoTypography.labelSmall, color = CalinoColors.Ink2)
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
    val scrim by animateFloatAsState(if (shown) .62f else 0f, animationSpec = tween(200), label = "calendar scrim")

    fun animateDragTo(targetX: Float, targetY: Float, onFinished: (() -> Unit)? = null) {
        dragAnimationJob?.cancel()
        val startX = dragX
        val startY = dragY
        dragAnimationJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = spring(dampingRatio = .86f, stiffness = 420f),
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

    BackHandler(enabled = shown, onBack = dismiss)
    Box(Modifier.fillMaxSize()) {
        // This is deliberately only a scrim. HomeScreen remains the real,
        // visible backdrop instead of being replaced by a second fake calendar.
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = scrim * (1f - (dragY / dismissDistancePx).coerceIn(0f, .72f))))
                .clickable(onClick = dismiss)
                .semantics { contentDescription = "Dismiss day details" },
        )
        AnimatedVisibility(
            shown,
            enter = slideInVertically(animationSpec = tween(240), initialOffsetY = { it }) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(animationSpec = tween(240), targetOffsetY = { it }) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(.92f)
                    .pointerInput(Unit) {
                        // Initial-pass observation lets a downward dismissal
                        // start over any list row, while upward list scrolling
                        // remains available until a downward axis is claimed.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val pointerId = down.id
                                var lastPosition = down.position
                                var totalX = 0f
                                var totalY = 0f
                                var horizontal = false
                                var axisDecided = false
                                var completed = false
                                val startDragX = dragX
                                val startDragY = dragY
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
                                        change.consume()
                                        dragX = (startDragX + totalX).coerceIn(-horizontalDragLimitPx, horizontalDragLimitPx)
                                        dragY = startDragY
                                    } else if (axisDecided && totalY > 0f) {
                                        // A downward dismissal can begin
                                        // anywhere on the sheet, including
                                        // inside the event list.
                                        change.consume()
                                        dragX = startDragX
                                        dragY = (startDragY + totalY).coerceAtLeast(0f)
                                    }
                                }

                                if (completed) {
                                    val horizontalPage = abs(dragX) > horizontalThresholdPx && abs(dragX) > dragY
                                    val swipeDown = dragY > dismissThresholdPx && dragY > abs(dragX)
                                    when {
                                        swipeDown -> {
                                            // Hand the dismissal to the host at the
                                            // finger's final position. The sheet's
                                            // exit animation owns the remaining
                                            // travel; a second spring to a fixed
                                            // distance makes the handoff visibly
                                            // lag and compounds the translation.
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
                Surface(
                    shape = RoundedCornerShape(26.dp, 26.dp, 0.dp, 0.dp),
                    color = CalinoColors.Panel,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) },
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
                                    label(if (pageDate == May18) "Today · ${dayEvents.size} events" else "${dayEvents.size} events")
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
    BottomDetailOverlay(
        visible = shown,
        onDismiss = { closeAfterAnimation(onBack) },
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
                SwipeDownDismiss(
                    visible = shown,
                    onDismiss = { closeAfterAnimation(onBack) },
                    modifier = Modifier.fillMaxSize(),
                    dismissDistance = 980.dp,
                ) { dragModifier ->
                    DetailCardSurface(
                        modifier = dragModifier,
                        handleColor = eventTint(eventColor(pageEvent), .13f, CalinoColors.Panel),
                    ) {
                        EventDetailContent(pageEvent, occurrenceDate,
                            onBack = { closeAfterAnimation(onBack) },
                            onPrimary = { if (!pager.isScrollInProgress) onEditEvent(pageEvent) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDetailContent(
    event: CalEvent,
    occurrenceDate: LocalDate?,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
) {
    val tint = eventTint(eventColor(event), .13f, CalinoColors.Panel)
    var moreOpen by remember(event.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(tint).padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButtonGlyph("‹", "Back", { onBack() })
                Box {
                    IconButtonGlyph("⋮", "More actions", { moreOpen = true })
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (event.location?.startsWith("http") == true) "Edit event" else "Edit") },
                            onClick = { moreOpen = false; onPrimary() },
                        )
                    }
                }
            }
            label(event.calendarId, Modifier.padding(top = 12.dp))
            Text(event.title, style = CalinoTypography.headlineLarge, modifier = Modifier.padding(top = 6.dp))
            Text(
                eventHeaderText(event, occurrenceDate),
                style = CalinoTypography.bodyLarge,
                color = CalinoColors.Ink2,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            event.location?.let { location -> item(key = "location") { DetailRow("⌖", "Location", location) } }
            event.notes?.let { notes -> item(key = "notes") { DetailRow("≡", "Notes", notes) } }
            if (event.attendees.isNotEmpty()) item(key = "attendees") { Attendees(event.attendees) }
            if (event.recurrence != null) item(key = "occurrences") {
                Column(Modifier.padding(top = 20.dp, bottom = 16.dp)) {
                    label("Next occurrences")
                    Text(recurrenceSummary(event), style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 5.dp))
                    nextOccurrences(event, occurrenceDate ?: May18).forEach { occurrence ->
                        Text(
                            occurrence.format(dateFormat) + " · " + occurrence.format(timeFormat),
                            style = CalinoTypography.bodyLarge,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onPrimary,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(CalinoColors.Ink),
            ) { Text(if (event.location?.startsWith("http") == true) "Join call" else "Edit") }
            OutlinedButton(
                onClick = { moreOpen = true },
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CalinoColors.Ink.copy(.12f)),
            ) { Text("···", color = CalinoColors.Ink) }
        }
    }
}

/**
 * Fixture-backed task detail/editor. The task body is the primary tap target
 * in both the calendar and task ledger; completion and rescheduling remain
 * separate row actions so opening a task never mutates it accidentally.
 */
@Composable
fun TaskDetailSurface(
    task: CalTask,
    onBack: () -> Unit = {},
    onSave: (NewTask, Boolean) -> Unit = { _, _ -> },
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var category by remember(task.id) { mutableStateOf(task.category.orEmpty()) }
    var due by remember(task.id) { mutableStateOf(task.due) }
    var done by remember(task.id) { mutableStateOf(task.done) }
    var shown by remember(task.id) { mutableStateOf(true) }
    var pendingSave by remember(task.id) { mutableStateOf(false) }

    LaunchedEffect(shown) {
        if (!shown) {
            delay(220)
            if (pendingSave) {
                onSave(
                    NewTask(
                        title = title.trim(),
                        due = due,
                        color = task.color,
                        category = category.trim().ifEmpty { null },
                    ),
                    done,
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
    ) { detailModifier ->
        Column(
            detailModifier
                .fillMaxSize()
                .background(CalinoColors.Canvas),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButtonGlyph("‹", "Back", { dismiss(false) })
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text("Task details", style = CalinoTypography.titleLarge)
                    Text("Local fixture task", color = CalinoColors.Ink3, fontSize = 11.sp)
                }
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(taskColor(task))
                        .semantics { contentDescription = "Task category color" },
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                label("Task")
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = false,
                    minLines = 2,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CalinoColors.Panel,
                        unfocusedContainerColor = CalinoColors.Panel,
                        focusedIndicatorColor = CalinoColors.Accent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                TextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Category") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CalinoColors.Panel,
                        unfocusedContainerColor = CalinoColors.Panel,
                        focusedIndicatorColor = CalinoColors.Accent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    label("Due date")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val choices = listOf(
                            May18 to "Today",
                            May18.plusDays(1) to "Tomorrow",
                            May18.plusDays(7) to "Next week",
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
                    due?.let { selectedDue ->
                        Text(
                            selectedDue.format(dateFormat),
                            color = CalinoColors.Ink3,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
                OutlinedButton(
                    onClick = { done = !done },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (done) CalinoColors.Green else CalinoColors.Line),
                ) {
                    Text(if (done) "Completed · mark open" else "Open · mark completed", color = if (done) CalinoColors.Green else CalinoColors.Ink2)
                }
            }
            Button(
                enabled = title.trim().isNotEmpty(),
                onClick = { dismiss(true) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(CalinoColors.Ink),
            ) { Text("Save changes") }
        }
    }
}

private fun formatDuration(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60} h"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}

/**
 * Recurring events keep their series start as the stable event identity, but
 * the detail surface is opened for a concrete occurrence. Show that tapped
 * date while retaining the series time and duration.
 */
private fun eventHeaderText(event: CalEvent, occurrenceDate: LocalDate?): String {
    val date = occurrenceDate ?: event.start?.toLocalDate() ?: event.date
    if (event.allDay || event.start == null) {
        return date?.format(dateFormat)?.let { "$it · All day" } ?: "All day"
    }

    val displayedDate = date?.format(dateFormat) ?: event.start.format(dateFormat)
    return buildString {
        append(displayedDate)
        append(" · ")
        append(event.start.format(timeFormat))
        event.durationMinutes?.let { minutes -> append(" · "); append(formatDuration(minutes)) }
    }
}

@Composable
private fun DetailRow(icon: String, name: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(icon, fontSize = 20.sp, color = CalinoColors.Ink3, modifier = Modifier.width(38.dp).padding(top = 1.dp))
            Column(Modifier.weight(1f)) { label(name); Text(value, style = CalinoTypography.bodyLarge) }
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
                    Text(attendee.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
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
) {
    var filter by remember { mutableStateOf(TaskFilter.All) }
    var pendingCompletionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var completionUndo by remember { mutableStateOf<List<CompletionUndo>>(emptyList()) }
    var reschedulingTaskId by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val taskScope = rememberCoroutineScope()
    var completionJobs by remember { mutableStateOf<Map<String, Job>>(emptyMap()) }

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
        taskBucket(if (isPending(task)) task.copy(done = false) else task, May18)
    }
    val renderTask: (CalTask) -> CalTask = { task ->
        if (isPending(task)) task.copy(done = true) else task
    }

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
                        activeVisible.filter { isOpenForBucket(it) && displayBucket(it) == TaskBucket.OVERDUE },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                    )
                    TaskBucket(
                        "Today",
                        activeVisible.filter { isOpenForBucket(it) && displayBucket(it) == TaskBucket.TODAY },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                    )
                    TaskBucket(
                        "This week",
                        activeVisible.filter { isOpenForBucket(it) && displayBucket(it) == TaskBucket.THIS_WEEK },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                    )
                    TaskBucket(
                        "Later",
                        activeVisible.filter { isOpenForBucket(it) && displayBucket(it) == TaskBucket.LATER },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                    )
                    TaskBucket(
                        "No date",
                        activeVisible.filter { isOpenForBucket(it) && displayBucket(it) == TaskBucket.NO_DATE },
                        ::complete,
                        { reschedulingTaskId = it.id },
                        { task, newDate -> reschedulingTaskId = null; onRescheduleTo(task, newDate) },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
                    )
                    TaskBucket(
                        "Completed",
                        activeVisible.filter { displayBucket(it) == TaskBucket.DONE },
                        {},
                        {},
                        { _, _ -> },
                        reschedulingTaskId,
                        renderTask,
                        onTaskClick,
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
                                color = Color.White,
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
) {
    if (tasks.isNotEmpty()) {
        item(key = "bucket:$name") {
            label(
                "$name · ${tasks.size}",
                Modifier
                    .animateItem()
                    .padding(top = 10.dp, bottom = 3.dp),
            )
        }
        tasks.forEach { originalTask ->
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
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    task: CalTask,
    onComplete: (CalTask) -> Unit,
    onReschedule: (CalTask) -> Unit,
    showReschedule: Boolean = false,
    onRescheduleTo: (CalTask, LocalDate) -> Unit = { _, _ -> },
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var drag by remember(task.id) { mutableStateOf(0f) }
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
    val canAct = !task.done
    val description = buildString {
        append(task.title)
        task.due?.let { append(", due "); append(it.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))) }
        task.category?.let { append(", "); append(it) }
        if (task.done) append(", completed")
    }
    val actionProgress = (abs(offset) / actionThresholdPx).coerceIn(0f, 1f)
    val titleColor = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clip(rowShape)) {
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
                        tint = Color.White.copy(alpha = actionProgress.coerceAtLeast(.72f)),
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel, color = Color.White.copy(alpha = actionProgress.coerceAtLeast(.72f)), style = CalinoTypography.bodyMedium)
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .offset { IntOffset(offset.roundToInt(), 0) }
                    .clip(rowShape)
                    .background(CalinoColors.Panel)
                    .border(BorderStroke(1.dp, CalinoColors.Ink.copy(.045f)), rowShape)
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
                                tint = Color.White,
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
                        .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
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
                                color = if (due.isBefore(May18) && !task.done) CalinoColors.Rose else CalinoColors.Ink3,
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
                if (canAct) {
                    // Completion has one clear 44dp target at the leading edge.
                    // Keep reschedule as the separate trailing action; the
                    // horizontal swipe remains available on the whole row.
                    IconButton(
                        onClick = { onReschedule(task) },
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { contentDescription = "Reschedule ${task.title}" },
                    ) {
                        CalinoIcon(
                            CalinoIcon.Repeat,
                            tint = CalinoColors.Ink2,
                            modifier = Modifier.size(18.dp),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(visible = showReschedule, enter = expandVertically(tween(180)) + fadeIn(tween(160)), exit = shrinkVertically(tween(160)) + fadeOut(tween(120))) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 44.dp, top = 5.dp, bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(May18 to "Today", May18.plusDays(1) to "Tomorrow", May18.plusDays(7) to "Next week").forEach { (date, labelText) ->
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

/** Notification handoff preview, including the three channel choices and two-action ceiling. */
@Composable
fun NotificationPreviewSurface(data: NotificationPreviewData = NotificationPreviewData("Design review", "10:00 AM · Studio · with 2 others"), onAction: (String) -> Unit = {}) { val cards = listOf(data.title to data.text, "Buy flowers" to "Due today · Personal"); Column(Modifier.fillMaxSize().background(CalinoColors.Canvas).padding(20.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Notifications", modifier = Modifier.weight(1f), style = CalinoTypography.displayLarge); Text("Calino · 2 more", style = CalinoTypography.bodySmall, color = CalinoColors.Ink3) }; Text("PREVIEW", style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 6.dp)); cards.forEachIndexed { index, card -> NotificationCard(card.first, card.second, index == 0, onAction) }; Spacer(Modifier.height(20.dp)); label("Channels"); listOf("Events reminders" to "Default · no sound", "Tasks due" to "Default", "Daily brief" to "Low importance").forEach { (name, setting) -> Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(CalinoColors.Accent)); Column(Modifier.padding(start = 12.dp)) { Text(name, style = CalinoTypography.bodyLarge); Text(setting, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3) } } } } }
@Composable private fun NotificationCard(title: String, body: String, event: Boolean, onAction: (String) -> Unit) { Card(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(CalinoColors.Panel), border = androidx.compose.foundation.BorderStroke(1.dp, CalinoColors.Ink.copy(.07f))) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) { Box(Modifier.size(5.dp, 58.dp).clip(RoundedCornerShape(4.dp)).background(if (event) CalinoColors.Blue else CalinoColors.Rose)); Column(Modifier.padding(start = 13.dp).weight(1f)) { label("Calino · now"); Text(title, style = CalinoTypography.titleMedium, modifier = Modifier.padding(top = 3.dp)); Text(body, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 2.dp)); Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { TextButton(onClick = { onAction(if (event) "snooze" else "done") }) { Text(if (event) "Snooze 5 min" else "Mark done") }; TextButton(onClick = { onAction(if (event) "directions" else "tomorrow") }) { Text(if (event) "Directions" else "Tomorrow") } } } } }
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
)
data class NotificationPreviewData(val title: String, val text: String, val kind: NotificationKind = NotificationKind.Event)
enum class NotificationKind { Event, Task }

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
) = EventDetailSurface(event, onBack, onPrimaryAction, occurrenceDate, events, onEventSelected, onEditEvent)

@Composable
fun TaskDetail(
    task: CalTask,
    onBack: () -> Unit = {},
    onSave: (NewTask, Boolean) -> Unit = { _, _ -> },
) = TaskDetailSurface(task, onBack, onSave)

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
) = TasksSurface(tasks, onComplete, onReschedule, onRescheduleTo, onTaskClick, onUndoComplete, onOpenMenu)

/** Shared animated Event/Task/Journal editor sheet. */
@Composable
fun QuickAddSheet(
    state: QuickAddSheetState = QuickAddSheetState(visible = true),
    calendars: List<CalinoCalendar> = emptyList(),
    categories: List<String> = emptyList(),
    relatedCandidates: List<Pair<String, String>> = emptyList(),
    onDismiss: () -> Unit = {},
    onSave: (EditorDraft) -> Unit = {},
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
            onDismiss = { mounted = false; onDismiss() },
            onSave = onSave,
            visible = state.visible,
        )
    }
}

/** In-app representation of the v2 notification cards (two actions maximum). */
@Composable
fun NotificationPreview(data: NotificationPreviewData = NotificationPreviewData("Design review", "10:00 AM · Studio · with 2 others"), onAction: (String) -> Unit = {}) = NotificationPreviewSurface(data, onAction)
