package calino.malinov.ski.poc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.occursOn
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.NewEvent
import calino.malinov.ski.poc.data.model.NewJournal
import calino.malinov.ski.poc.data.model.NewTask
import calino.malinov.ski.poc.data.model.EditorDraft
import calino.malinov.ski.poc.data.model.blankEditorDraft
import calino.malinov.ski.poc.data.model.editorDraftFor
import calino.malinov.ski.poc.data.parser.PocQuickAddKind
import calino.malinov.ski.poc.data.repository.CalinoRepository
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import calino.malinov.ski.poc.data.repository.FixtureRepository
import calino.malinov.ski.poc.data.repository.UndoableChange
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.design.CalinoTheme
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.state.SplitPaneWidthDp
import calino.malinov.ski.poc.state.PocReturnTarget
import calino.malinov.ski.poc.ui.components.AddPill
import calino.malinov.ski.poc.ui.components.NavSidebar
import calino.malinov.ski.poc.ui.home.HomeScreen
import calino.malinov.ski.poc.ui.components.SwipeDownDismiss
import calino.malinov.ski.poc.ui.surfaces.DayModalSurface
import calino.malinov.ski.poc.ui.surfaces.EventDetail
import calino.malinov.ski.poc.ui.surfaces.TaskDetail
import calino.malinov.ski.poc.ui.surfaces.NotificationPreview
import calino.malinov.ski.poc.ui.surfaces.AgendaScreen
import calino.malinov.ski.poc.ui.surfaces.PockRoute
import calino.malinov.ski.poc.ui.surfaces.QuickAddKind
import calino.malinov.ski.poc.ui.surfaces.QuickAddSheet
import calino.malinov.ski.poc.ui.surfaces.QuickAddSheetState
import calino.malinov.ski.poc.ui.surfaces.toParserKind
import calino.malinov.ski.poc.ui.surfaces.JournalSurface
import calino.malinov.ski.poc.ui.surfaces.SettingsSurface
import calino.malinov.ski.poc.ui.surfaces.Tasks
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val FixtureDate = LocalDate.of(2026, 5, 18)
private val DateLabel = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)

private fun fallbackRescheduleDate(taskDate: LocalDate?, selectedDate: LocalDate): LocalDate = maxOf(
    FixtureDate.plusDays(1),
    taskDate?.plusDays(1) ?: selectedDate.plusDays(1),
)

private val LocalDateSaver = Saver<LocalDate, Long>(
    save = { it.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) },
)

private val RouteSaver = Saver<PockRoute, String>(
    save = { it.saveableKey() },
    restore = { key ->
        when (key) {
            "agenda" -> PockRoute.Agenda
            "detail" -> PockRoute.Detail
            "task-detail" -> PockRoute.TaskDetail
            "tasks" -> PockRoute.Tasks
            "journal" -> PockRoute.Journal
            "settings" -> PockRoute.Settings
            "quick-add" -> PockRoute.QuickAdd
            "notifications" -> PockRoute.Notifications
            else -> PockRoute.Day
        }
    },
)

private val ReturnTargetSaver = Saver<PocReturnTarget, String>(
    save = { it.name },
    restore = { value -> runCatching { PocReturnTarget.valueOf(value) }.getOrDefault(PocReturnTarget.Calendar) },
)

private val QuickAddKindSaver = Saver<QuickAddKind, String>(
    save = { it.name },
    restore = { value -> runCatching { QuickAddKind.valueOf(value) }.getOrDefault(QuickAddKind.Event) },
)

private fun PockRoute.saveableKey(): String = when (this) {
    PockRoute.Day -> "calendar"
    PockRoute.Agenda -> "agenda"
    PockRoute.Detail -> "detail"
    PockRoute.TaskDetail -> "task-detail"
    PockRoute.Tasks -> "tasks"
    PockRoute.Journal -> "journal"
    PockRoute.Settings -> "settings"
    PockRoute.QuickAdd -> "quick-add"
    PockRoute.Notifications -> "notifications"
}

private fun PockRoute.rootOrder(): Int = when (this) {
    PockRoute.Day -> 0
    PockRoute.Agenda -> 1
    PockRoute.Tasks -> 2
    PockRoute.Journal -> 3
    PockRoute.Settings -> 4
    // Detail and notification previews are pushed destinations. Keeping them
    // after the root destinations makes opening them enter from the right and
    // returning from them reverse the same motion, instead of treating them
    // as another instance of the calendar route.
    PockRoute.Detail -> 5
    PockRoute.TaskDetail -> 5
    PockRoute.Notifications -> 5
    PockRoute.QuickAdd -> 5
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalinoApp() }
    }
}

/** Retains the in-memory POC repository across configuration changes only. */
class PocRepositoryViewModel : ViewModel() {
    val repository = FixtureRepository()
}

/** The launch shell for the fixture-only native POC. No WebView or Capacitor is involved. */
@Composable
fun CalinoApp() {
    CalinoTheme {
        val repository = viewModel<PocRepositoryViewModel>().repository
        val snapshot = rememberRepositorySnapshot(repository)
        val saveableStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
        var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf<PockRoute>(PockRoute.Day) }
        var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(FixtureDate) }
        var showDayModal by rememberSaveable { mutableStateOf(false) }
        var selectedEventId by rememberSaveable { mutableStateOf<String?>(null) }
        // Keep the calendar occurrence separate from the event's series
        // anchor. Detail can then show the occurrence the user actually
        // tapped while retaining the original event identity for editing.
        var selectedEventOccurrenceDay by rememberSaveable { mutableStateOf<Long?>(null) }
        var editEventId by rememberSaveable { mutableStateOf<String?>(null) }
        var detailOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
        var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
        var taskDetailOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
        var quickAddOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
        var quickAddKind by rememberSaveable(stateSaver = QuickAddKindSaver) { mutableStateOf(QuickAddKind.Event) }
        var notificationOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
        var journalReviewVisible by rememberSaveable { mutableStateOf(false) }
        var journalEditorVisible by rememberSaveable { mutableStateOf(false) }
        var journalOpenEntryId by rememberSaveable { mutableStateOf<String?>(null) }
        var sidebarVisible by rememberSaveable { mutableStateOf(false) }
        // The landscape month root puts the day beside the grid. The pill
        // belongs over that pane, not centred on the rule between the two.
        var splitDayPaneVisible by remember { mutableStateOf(false) }
        var journalEntryRequest by rememberSaveable { mutableIntStateOf(0) }
        var pendingUndo by remember { mutableStateOf<UndoableChange?>(null) }
        var displayedUndo by remember { mutableStateOf<UndoableChange?>(null) }
        var undoNonce by remember { mutableIntStateOf(0) }

        LaunchedEffect(pendingUndo) {
            pendingUndo?.let { displayedUndo = it }
        }

        val selectedEvent = snapshot.events.firstOrNull { it.id == selectedEventId }
        val selectedTask = snapshot.tasks.firstOrNull { it.id == selectedTaskId }
        val calendarDayModalVisible = showDayModal && (
            route == PockRoute.Day ||
                (route == PockRoute.QuickAdd && quickAddOrigin == PocReturnTarget.DayModal)
            )

        fun openQuickAdd(kind: QuickAddKind, origin: PocReturnTarget) {
            editEventId = null
            quickAddKind = kind
            quickAddOrigin = origin
            route = PockRoute.QuickAdd
        }

        /** The same editor, seeded from a record that already exists. */
        fun openEditor(event: CalEvent, origin: PocReturnTarget) {
            editEventId = event.id
            quickAddKind = QuickAddKind.Event
            quickAddOrigin = origin
            route = PockRoute.QuickAdd
        }

        fun restoreDetailOrigin() {
            when (detailOrigin) {
                PocReturnTarget.DayModal -> {
                    route = PockRoute.Day
                    showDayModal = true
                }
                PocReturnTarget.Agenda -> route = PockRoute.Agenda
                PocReturnTarget.Tasks -> route = PockRoute.Tasks
                PocReturnTarget.Journal -> route = PockRoute.Journal
                else -> {
                    route = PockRoute.Day
                    showDayModal = false
                }
            }
        }

        fun restoreTaskDetailOrigin() {
            route = when (taskDetailOrigin) {
                PocReturnTarget.Tasks -> PockRoute.Tasks
                PocReturnTarget.Agenda -> PockRoute.Agenda
                else -> PockRoute.Day
            }
        }

        fun dismissQuickAdd() {
            editEventId = null
            when (quickAddOrigin) {
                PocReturnTarget.DayModal -> {
                    route = PockRoute.Day
                    showDayModal = true
                }
                PocReturnTarget.Tasks -> {
                    route = PockRoute.Tasks
                    showDayModal = false
                }
                PocReturnTarget.Journal -> {
                    route = PockRoute.Journal
                    showDayModal = false
                }
                PocReturnTarget.Settings -> {
                    route = PockRoute.Settings
                    showDayModal = false
                }
                PocReturnTarget.Detail -> route = PockRoute.Detail
                PocReturnTarget.TaskDetail -> route = PockRoute.TaskDetail
                PocReturnTarget.Agenda -> {
                    route = PockRoute.Agenda
                    showDayModal = false
                }
                PocReturnTarget.Calendar -> {
                    route = PockRoute.Day
                    showDayModal = false
                }
            }
        }

        fun navigateRoot(next: PockRoute) {
            showDayModal = false
            journalReviewVisible = false
            editEventId = null
            selectedEventId = null
            selectedEventOccurrenceDay = null
            route = next
        }

        fun showUndo(change: UndoableChange) {
            pendingUndo = change
            undoNonce += 1
        }

        BackHandler(enabled = sidebarVisible || (route != PockRoute.Detail && route != PockRoute.TaskDetail &&
            !journalEditorVisible && (journalReviewVisible || route != PockRoute.Day || showDayModal))) {
            when {
                sidebarVisible -> sidebarVisible = false
                journalReviewVisible -> journalReviewVisible = false
                route == PockRoute.QuickAdd -> dismissQuickAdd()
                route == PockRoute.Detail -> {
                    selectedEventId = null
                    selectedEventOccurrenceDay = null
                    restoreDetailOrigin()
                }
                route == PockRoute.TaskDetail -> {
                    selectedTaskId = null
                    restoreTaskDetailOrigin()
                }
                route == PockRoute.Notifications -> route = if (notificationOrigin == PocReturnTarget.Settings) PockRoute.Settings else PockRoute.Day
                showDayModal -> showDayModal = false
                else -> route = PockRoute.Day
            }
        }

        Column(
            Modifier.fillMaxSize()
                .background(CalinoColors.Canvas)
                .padding(WindowInsets.safeDrawing.asPaddingValues()),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            val rootRoute = when (route) {
                PockRoute.Day -> PockRoute.Day
                PockRoute.Agenda -> PockRoute.Agenda
                PockRoute.Tasks -> PockRoute.Tasks
                PockRoute.Journal -> PockRoute.Journal
                PockRoute.Settings -> PockRoute.Settings
                PockRoute.Detail -> when (detailOrigin) {
                    PocReturnTarget.Agenda -> PockRoute.Agenda
                    PocReturnTarget.Tasks -> PockRoute.Tasks
                    PocReturnTarget.Journal -> PockRoute.Journal
                    else -> PockRoute.Day
                }
                PockRoute.TaskDetail -> when (taskDetailOrigin) {
                    PocReturnTarget.Agenda -> PockRoute.Agenda
                    PocReturnTarget.Tasks -> PockRoute.Tasks
                    else -> PockRoute.Day
                }
                PockRoute.QuickAdd -> when (quickAddOrigin) {
                    PocReturnTarget.Agenda -> PockRoute.Agenda
                    PocReturnTarget.Tasks -> PockRoute.Tasks
                    PocReturnTarget.Journal -> PockRoute.Journal
                    PocReturnTarget.Settings -> PockRoute.Settings
                    PocReturnTarget.Detail -> PockRoute.Day
                    else -> PockRoute.Day
                }
                PockRoute.Notifications -> PockRoute.Notifications
            }
            AnimatedContent(
                targetState = rootRoute,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = if (targetState.rootOrder() >= initialState.rootOrder()) 1 else -1
                    (slideInHorizontally(tween(260)) { direction * it / 4 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(210)) { -direction * it / 4 } + fadeOut(tween(140)))
                },
                label = "root destination transition",
            ) { currentRoute ->
                saveableStateHolder.SaveableStateProvider("root:${currentRoute.saveableKey()}") {
                    when (currentRoute) {
                        PockRoute.Day -> HomeScreen(
                            repository = repository,
                            journals = snapshot.journals,
                            tasks = snapshot.tasks,
                            modifier = Modifier.fillMaxSize(),
                            interactionEnabled = route == PockRoute.Day && !showDayModal && !journalReviewVisible,
                            initialDate = selectedDate,
                            onOpenMenu = { sidebarVisible = true },
                            onDateChanged = { selectedDate = it },
                            onDayClick = { date -> selectedDate = date; showDayModal = true; route = PockRoute.Day },
                            onEventClick = { event ->
                                selectedEventId = event.id
                                selectedEventOccurrenceDay = selectedDate.toEpochDay()
                                detailOrigin = PocReturnTarget.Calendar
                                showDayModal = false
                                route = PockRoute.Detail
                            },
                            onTaskDone = { task, done ->
                                showUndo(repository.setTaskDone(task.id, done))
                            },
                            onTaskRescheduleTo = { task, date ->
                                showUndo(repository.rescheduleTask(task.id, date))
                            },
                            onTaskClick = { task ->
                                selectedTaskId = task.id
                                taskDetailOrigin = PocReturnTarget.Calendar
                                route = PockRoute.TaskDetail
                            },
                            onSplitPaneChanged = { splitDayPaneVisible = it },
                        )
                        PockRoute.Agenda -> AgendaScreen(
                            repository = repository,
                            tasks = snapshot.tasks,
                            modifier = Modifier.fillMaxSize(),
                            initialDate = selectedDate,
                            onOpenMenu = { sidebarVisible = true },
                            onDateChanged = { selectedDate = it },
                            onEventClick = { day, event ->
                                selectedEventId = event.id
                                selectedEventOccurrenceDay = day.toEpochDay()
                                detailOrigin = PocReturnTarget.Agenda
                                route = PockRoute.Detail
                            },
                            onTaskClick = { task ->
                                selectedTaskId = task.id
                                taskDetailOrigin = PocReturnTarget.Agenda
                                route = PockRoute.TaskDetail
                            },
                            onTaskDone = { task, done -> showUndo(repository.setTaskDone(task.id, done)) },
                            onAddOn = { date ->
                                selectedDate = date
                                openQuickAdd(QuickAddKind.Event, PocReturnTarget.Agenda)
                            },
                        )
                        PockRoute.Tasks -> Tasks(
                            tasks = snapshot.tasks,
                            onComplete = { task -> repository.setTaskDone(task.id, true) },
                            onReschedule = { task -> showUndo(repository.rescheduleTask(task.id, fallbackRescheduleDate(task.due, selectedDate))) },
                            onRescheduleTo = { task, date -> showUndo(repository.rescheduleTask(task.id, date)) },
                            onTaskClick = { task ->
                                selectedTaskId = task.id
                                taskDetailOrigin = PocReturnTarget.Tasks
                                route = PockRoute.TaskDetail
                            },
                            onUndoComplete = { task -> repository.setTaskDone(task.id, false) },
                            onOpenMenu = { sidebarVisible = true },
                        )
                        PockRoute.Journal -> JournalSurface(
                            entries = snapshot.journals,
                            newEntryDate = selectedDate,
                            onCreate = { entry ->
                                repository.addJournal(NewJournal(entry.date, entry.title, entry.body))
                            },
                            onUpdate = { entry ->
                                repository.updateJournal(entry.id, NewJournal(entry.date, entry.title, entry.body))
                            },
                            onDelete = { entry -> repository.deleteJournal(entry.id) },
                            onEditingChanged = { journalEditorVisible = it },
                            openEntryId = journalOpenEntryId,
                            onOpenEntryConsumed = { journalOpenEntryId = null },
                            onOpenMenu = { sidebarVisible = true },
                            startEntryRequest = journalEntryRequest,
                        )
                        PockRoute.Settings -> SettingsSurface(
                            onOpenNotifications = {
                                notificationOrigin = PocReturnTarget.Settings
                                route = PockRoute.Notifications
                            },
                            onOpenMenu = { sidebarVisible = true },
                        )
                        PockRoute.Detail, PockRoute.TaskDetail -> Unit
                        PockRoute.Notifications -> NotificationPreview(
                            data = calino.malinov.ski.poc.ui.surfaces.NotificationPreviewData(
                                "Design review", "10:00 AM · Studio · with 2 others",
                            ),
                            onAction = { notificationOrigin = PocReturnTarget.Settings; route = PockRoute.Settings },
                        )
                        PockRoute.QuickAdd -> Unit
                    }
                }
            }

            when (route) {
                PockRoute.Detail -> selectedEvent?.let { event ->
                    EventDetail(
                        event = event,
                        events = remember(snapshot.events, selectedEventOccurrenceDay) {
                            snapshot.events.filter {
                                it.occursOn(selectedEventOccurrenceDay?.let(LocalDate::ofEpochDay) ?: selectedDate)
                            }.sortedWith(compareBy<CalEvent> { !it.allDay }
                                .thenBy { it.start?.toLocalTime() }.thenBy { it.id })
                                .ifEmpty { listOf(event) }
                        },
                        onEventSelected = { selectedEventId = it.id },
                        onEditEvent = { openEditor(it, PocReturnTarget.Detail) },
                        occurrenceDate = selectedEventOccurrenceDay?.let(LocalDate::ofEpochDay),
                        onBack = {
                            selectedEventId = null
                            selectedEventOccurrenceDay = null
                            restoreDetailOrigin()
                        },
                        // The editor is an overlay route, so the selected
                        // event stays set and a save lands back on this
                        // detail surface.
                        onPrimaryAction = {
                            selectedEventId = event.id
                            openEditor(event, PocReturnTarget.Detail)
                        },
                    )
                }
                PockRoute.TaskDetail -> selectedTask?.let { task ->
                    TaskDetail(
                        task = task,
                        onBack = {
                            selectedTaskId = null
                            restoreTaskDetailOrigin()
                        },
                        onSave = { input, done ->
                            repository.updateTask(task.id, input, done)
                            selectedTaskId = null
                            restoreTaskDetailOrigin()
                        },
                    )
                }
                else -> Unit
            }

            if (calendarDayModalVisible) {
                DayModalSurface(
                    date = selectedDate,
                    events = snapshot.events,
                    journals = snapshot.journals,
                    onDateChanged = { selectedDate = it },
                    onDismiss = { showDayModal = false; route = PockRoute.Day },
                    onAdd = {
                        // Unmount the day sheet while Quick Add owns the
                        // overlay. The return target restores a fresh sheet,
                        // avoiding a hidden modal left behind the editor.
                        showDayModal = false
                        openQuickAdd(QuickAddKind.Event, PocReturnTarget.DayModal)
                    },
                    onEvent = { event ->
                        selectedEventId = event.id
                        selectedEventOccurrenceDay = selectedDate.toEpochDay()
                        detailOrigin = PocReturnTarget.DayModal
                        showDayModal = false
                        route = PockRoute.Detail
                    },
                    onJournal = { journal ->
                        journalOpenEntryId = journal.id
                        showDayModal = false
                        route = PockRoute.Journal
                    },
                )
            }

            when (route) {
                PockRoute.QuickAdd -> {
                    val editing = editEventId?.let { id -> snapshot.events.firstOrNull { it.id == id } }
                    QuickAddSheet(
                        state = QuickAddSheetState(
                            visible = true,
                            kind = quickAddKind,
                            date = selectedDate,
                            draft = editing
                                ?.let(::editorDraftFor)
                                ?: blankEditorDraft(quickAddKind.toParserKind(), selectedDate),
                        ),
                        calendars = snapshot.calendars,
                        categories = snapshot.categories,
                        relatedCandidates = remember(snapshot.tasks) {
                            snapshot.tasks.filterNot { it.done }.map { it.id to it.title }
                        },
                        onDismiss = ::dismissQuickAdd,
                        // The editor owns every field now, so the host only
                        // decides between creating and updating a record.
                        onSave = { draft ->
                            saveEditorDraft(repository, draft)
                            if (draft.kind == PocQuickAddKind.Journal) {
                                journalReviewVisible = false
                                quickAddOrigin = PocReturnTarget.Journal
                            }
                            selectedDate = draft.date
                            dismissQuickAdd()
                        },
                    )
                }
                else -> Unit
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = pendingUndo != null,
                enter = slideInVertically(tween(200), initialOffsetY = { it / 2 }) + fadeIn(tween(170)),
                exit = slideOutVertically(tween(170), targetOffsetY = { it / 2 }) + fadeOut(tween(130)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = CalinoSpacing.PillClearance),
            ) {
                displayedUndo?.let { change ->
                    PocUndoBanner(
                        change = change,
                        nonce = undoNonce,
                        onUndo = {
                            repository.undo(change)
                            pendingUndo = null
                        },
                        onExpired = { if (pendingUndo == change) pendingUndo = null },
                        modifier = Modifier,
                    )
                }
            }

            // The add affordance floats over the surfaces instead of taking
            // layout space; every scrollable root reserves PillClearance for it.
            val pillVisible = when (rootRoute) {
                PockRoute.Day -> route == PockRoute.Day && !showDayModal && !journalReviewVisible && editEventId == null
                PockRoute.Agenda -> route == PockRoute.Agenda
                PockRoute.Tasks -> route == PockRoute.Tasks
                PockRoute.Journal -> route == PockRoute.Journal && !journalEditorVisible
                else -> false
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = pillVisible && !sidebarVisible,
                enter = slideInVertically(tween(240), initialOffsetY = { it }) + fadeIn(tween(180)),
                exit = slideOutVertically(tween(200), targetOffsetY = { it }) + fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 20.dp),
                label = "add pill visibility",
            ) {
                // In the landscape split the pill rides over the day pane, so
                // the lane it centres in is the pane rather than the window.
                val pillLaneWidth by animateDpAsState(
                    targetValue = if (splitDayPaneVisible) (SplitPaneWidthDp + 44).dp else 0.dp,
                    animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
                    label = "add pill lane",
                )
                // The pill also carries the three main views: a horizontal
                // drag steps through them in the same order the sidebar lists.
                val pillRoutes = listOf(PockRoute.Day, PockRoute.Agenda, PockRoute.Tasks, PockRoute.Journal)
                val pillIndex = pillRoutes.indexOf(rootRoute)
                Box(
                    if (pillLaneWidth > 0.dp) Modifier.width(pillLaneWidth) else Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                AddPill(
                    canSwipe = { direction -> pillIndex >= 0 && (pillIndex + direction) in pillRoutes.indices },
                    onSwipe = { direction ->
                        pillRoutes.getOrNull(pillIndex + direction)?.let(::navigateRoot)
                    },
                    label = when (rootRoute) {
                        PockRoute.Tasks -> "New task"
                        PockRoute.Journal -> "New entry"
                        else -> "Add on ${selectedDate.format(DateLabel)}"
                    },
                    onClick = {
                        when (rootRoute) {
                            PockRoute.Tasks -> openQuickAdd(QuickAddKind.Task, PocReturnTarget.Tasks)
                            PockRoute.Journal -> journalEntryRequest += 1
                            else -> openQuickAdd(QuickAddKind.Event, PocReturnTarget.Calendar)
                        }
                    },
                )
                }
            }

            NavSidebar(
                visible = sidebarVisible,
                selectedRoute = rootRoute,
                onRoute = { next -> navigateRoot(next) },
                onDismiss = { sidebarVisible = false },
            )
        }

        if (journalReviewVisible) {
            JournalReviewDialog(
                journals = snapshot.journals,
                onDismiss = { journalReviewVisible = false },
            )
        }
    }
}

}

@Composable
private fun rememberRepositorySnapshot(repository: CalinoRepository): CalinoSnapshot {
    var snapshot by remember(repository) { mutableStateOf(repository.snapshot()) }
    DisposableEffect(repository) {
        val subscription = repository.observe { snapshot = it }
        onDispose { subscription.close() }
    }
    return snapshot
}

@Composable
private fun PocUndoBanner(
    change: UndoableChange,
    nonce: Int,
    onUndo: () -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(nonce) {
        delay(5_000)
        onExpired()
    }
    Surface(
        modifier = modifier.fillMaxWidth(.92f),
        shape = RoundedCornerShape(16.dp),
        color = CalinoColors.Ink,
        contentColor = Color.White,
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(change.description, Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = onUndo) { Text("Undo", color = CalinoColors.AccentSoft) }
        }
    }
}

@Composable
private fun JournalReviewDialog(journals: List<JournalEntry>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = CalinoColors.Panel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Journal", style = CalinoTypography.titleLarge)
                        Text("Local entries", color = CalinoColors.Ink3, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Text("×", fontSize = 22.sp, color = CalinoColors.Ink2) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(journals.asReversed(), key = { it.id }) { journal ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(CalinoColors.Canvas, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(journal.title.ifBlank { "Untitled note" }, style = CalinoTypography.bodyLarge)
                            Text(journal.date.format(DateLabel), color = CalinoColors.Ink3, fontSize = 11.sp)
                            Text(journal.body, color = CalinoColors.Ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(CalinoColors.Ink),
                ) { Text("Done") }
            }
        }
    }
}

/** Routes a finished draft to the add or update call for its kind. */
private fun saveEditorDraft(repository: CalinoRepository, draft: EditorDraft) {
    val id = draft.editingId
    when (draft.kind) {
        PocQuickAddKind.Event ->
            if (id == null) repository.addEvent(draft.toNewEvent())
            else repository.updateEvent(id, draft.toNewEvent())
        PocQuickAddKind.Task -> {
            val done = id?.let { taskId -> repository.tasks().firstOrNull { it.id == taskId }?.done } ?: false
            if (id == null) repository.addTask(draft.toNewTask())
            else repository.updateTask(id, draft.toNewTask(), done)
        }
        PocQuickAddKind.Journal ->
            if (id == null) repository.addJournal(draft.toNewJournal())
            else repository.updateJournal(id, draft.toNewJournal())
    }
}
