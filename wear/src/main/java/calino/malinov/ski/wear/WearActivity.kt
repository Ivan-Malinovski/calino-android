package calino.malinov.ski.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import calino.malinov.ski.wearcontract.TaskGroup
import calino.malinov.ski.wearcontract.WearAckResult
import calino.malinov.ski.wearcontract.WearCommand
import calino.malinov.ski.wearcontract.WearCommandOp
import calino.malinov.ski.wearcontract.WearEvent
import calino.malinov.ski.wearcontract.WearFormatting
import calino.malinov.ski.wearcontract.WearReducedState
import calino.malinov.ski.wearcontract.WearSelection
import calino.malinov.ski.wearcontract.WearSnapshot
import calino.malinov.ski.wearcontract.WearTask
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WearActivity : ComponentActivity() {
    private var requestedDetailId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedDetailId = intent.detailId()
        WearCommands.replay(this)
        setContent { MaterialTheme { AppScaffold { WearRoot() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDetailId = intent.detailId()
    }

    @Composable
    private fun WearRoot() {
        val revision by WearStateUpdates.revision.collectAsStateWithLifecycle()
        val state = remember(revision) { WearStore(this).state() }
        var tasksOnly by remember { mutableStateOf(false) }
        var selectedId by remember { mutableStateOf(requestedDetailId) }
        val navController = rememberSwipeDismissableNavController()
        LaunchedEffect(requestedDetailId) {
            requestedDetailId?.takeIf { state.snapshot?.record(it) != null }?.let {
                selectedId = it
                if (navController.currentDestination?.route != "detail") navController.navigate("detail")
            }
        }
        SwipeDismissableNavHost(navController, startDestination = "overview") {
            composable("overview") {
                OverviewScreen(state, tasksOnly, { tasksOnly = !tasksOnly }) {
                    selectedId = rowId(it)
                    navController.navigate("detail")
                }
            }
            composable("detail") {
                val selected = state.snapshot?.record(selectedId)
                if (selected != null) {
                    DetailScreen(selected, requireNotNull(state.snapshot))
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
        }
    }

    @Composable
    private fun OverviewScreen(
        state: WearReducedState,
        tasksOnly: Boolean,
        onToggleMode: () -> Unit,
        onOpen: (Any) -> Unit,
    ) {
        val listState = rememberTransformingLazyColumnState()
        val transformation = rememberTransformationSpec()
        val snapshot = state.snapshot
        val today = snapshot?.today() ?: LocalDate.now().toEpochDay()
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                item {
                    ListHeader(Modifier.fillMaxWidth().transformedHeight(this, transformation)) {
                        Text(if (tasksOnly) "Tasks" else "Agenda")
                    }
                }
                item {
                    Button(
                        onClick = onToggleMode,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformation),
                        transformation = SurfaceTransformation(transformation),
                        label = { Text(if (tasksOnly) "Show agenda" else "Show tasks") },
                    )
                }
                state.notices.firstOrNull()?.let { acknowledgement ->
                    item {
                        InfoCard(
                            title = acknowledgement.notice(),
                            subtitle = "Watch action",
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformation),
                            transformation = SurfaceTransformation(transformation),
                        )
                    }
                }
                if (snapshot == null) {
                    item {
                        InfoCard(
                            title = "Set up Calino on your phone",
                            subtitle = "The watch will sync automatically",
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformation),
                            transformation = SurfaceTransformation(transformation),
                        )
                    }
                } else {
                    staleHeader(snapshot)?.let { label ->
                        item {
                            InfoCard(
                                title = label,
                                subtitle = "Cached calendar",
                                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformation),
                                transformation = SurfaceTransformation(transformation),
                            )
                        }
                    }
                    if (tasksOnly) {
                        WearSelection.taskSections(snapshot, today).forEach { section ->
                            item {
                                ListHeader(Modifier.fillMaxWidth().transformedHeight(this, transformation)) {
                                    Text(section.group.label())
                                }
                            }
                            items(section.tasks.size, key = { section.tasks[it].occurrenceId }) { index ->
                                val task = section.tasks[index]
                                RecordCard(
                                    task,
                                    snapshot,
                                    task.writeState.name == "PENDING",
                                    Modifier.fillMaxWidth().transformedHeight(this, transformation),
                                    SurfaceTransformation(transformation),
                                ) { onOpen(task) }
                            }
                        }
                    } else {
                        WearSelection.agendaGroups(snapshot, today).forEach { group ->
                            item {
                                ListHeader(Modifier.fillMaxWidth().transformedHeight(this, transformation)) {
                                    Text(dayHeader(group.epochDay, today))
                                }
                            }
                            items(group.rows.size, key = { rowId(group.rows[it]) }) { index ->
                                val row = group.rows[index]
                                RecordCard(
                                    row,
                                    snapshot,
                                    row is WearTask && row.writeState.name == "PENDING",
                                    Modifier.fillMaxWidth().transformedHeight(this, transformation),
                                    SurfaceTransformation(transformation),
                                ) { onOpen(row) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun InfoCard(
        title: String,
        subtitle: String,
        modifier: Modifier,
        transformation: SurfaceTransformation,
    ) {
        TitleCard(
            onClick = {},
            modifier = modifier,
            transformation = transformation,
            title = { Text(title) },
            subtitle = { Text(subtitle) },
        )
    }

    @Composable
    private fun RecordCard(
        row: Any,
        snapshot: WearSnapshot,
        pending: Boolean,
        modifier: Modifier,
        transformation: SurfaceTransformation,
        onClick: () -> Unit,
    ) {
        val schedule = rowSchedule(row, snapshot)
        TitleCard(
            onClick = onClick,
            modifier = modifier.semantics {
                contentDescription = listOf(rowTitle(row), schedule, "Open details").joinToString(", ")
            },
            transformation = transformation,
            title = { Text(rowTitle(row)) },
            subtitle = { Text(if (pending) "Pending · $schedule" else schedule) },
        )
    }

    @Composable
    private fun DetailScreen(row: Any, snapshot: WearSnapshot) {
        val listState = rememberTransformingLazyColumnState()
        val transformation = rememberTransformationSpec()
        val haptics = LocalHapticFeedback.current
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    openPhone(row)
                }) { Text("Phone") }
            },
        ) { contentPadding ->
            TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                item {
                    ListHeader(Modifier.fillMaxWidth().transformedHeight(this, transformation)) {
                        Text(if (row is WearTask) "Task" else "Event")
                    }
                }
                item {
                    TitleCard(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformation),
                        transformation = SurfaceTransformation(transformation),
                        title = { Text(rowTitle(row)) },
                        subtitle = { Text(detailSchedule(row, snapshot)) },
                        content = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(rowMetadata(row))
                                if (row is WearTask) Text(WearFormatting.taskStatus(row))
                            }
                        },
                    )
                }
                if (row is WearTask) {
                    item {
                        ActionButton(
                            label = "Complete",
                            secondary = "Mark this task done",
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformation),
                            transformation = SurfaceTransformation(transformation),
                            colors = true,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            command(snapshot, row, WearCommandOp.SET_TASK_DONE, null)
                        }
                    }
                    item {
                        ActionButton(
                            label = "Tomorrow",
                            secondary = "Move the due date",
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformation),
                            transformation = SurfaceTransformation(transformation),
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            command(snapshot, row, WearCommandOp.RESCHEDULE_TASK, snapshot.tomorrow())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionButton(
        label: String,
        modifier: Modifier,
        transformation: SurfaceTransformation,
        secondary: String? = null,
        colors: Boolean = false,
        onClick: () -> Unit,
    ) {
        Button(
            onClick = onClick,
            modifier = modifier,
            transformation = transformation,
            colors = if (colors) ButtonDefaults.filledVariantButtonColors() else ButtonDefaults.buttonColors(),
            label = { Text(label) },
            secondaryLabel = secondary?.let { value -> { Text(value) } },
        )
    }

    private fun command(snapshot: WearSnapshot, task: WearTask, op: WearCommandOp, target: LocalDate?) {
        WearCommands.send(
            this,
            WearCommand(
                occurrenceId = task.occurrenceId,
                recordId = task.recordId,
                op = op,
                observedDone = task.done,
                observedDueEpochDay = task.dueEpochDay,
                sourceEpoch = snapshot.sourceEpoch,
                sourceSequence = snapshot.sequence,
                targetEpochDay = target?.toEpochDay(),
            ),
        )
    }

    private fun openPhone(row: Any) {
        val kind = if (row is WearTask) "task" else "event"
        val id = if (row is WearTask) row.recordId else (row as WearEvent).recordId
        val day = if (row is WearTask) row.dueEpochDay else (row as WearEvent).startEpochDay
        val encoded = URLEncoder.encode(id, StandardCharsets.UTF_8.name())
        RemoteActivityHelper(this).startRemoteActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("calino.malinov.ski://reminder/$kind?id=$encoded&day=$day")),
            null,
        )
    }
}

private fun Intent.detailId(): String? = data?.takeIf { it.scheme == "calino-wear" && it.host == "detail" }
    ?.pathSegments?.firstOrNull()
private fun WearSnapshot.record(id: String?): Any? = (events + tasks).firstOrNull { rowId(it) == id }
private fun WearSnapshot.today(): Long = LocalDate.now(ZoneId.of(phoneZone)).toEpochDay()
private fun WearSnapshot.tomorrow(): LocalDate = LocalDate.now(ZoneId.of(phoneZone)).plusDays(1)
private fun rowId(row: Any) = when (row) { is WearEvent -> row.occurrenceId; is WearTask -> row.occurrenceId; else -> row.hashCode().toString() }
private fun rowTitle(row: Any) = when (row) { is WearEvent -> row.title; is WearTask -> row.title; else -> "" }
private fun rowMetadata(row: Any) = when (row) {
    is WearEvent -> listOfNotNull(row.calendar, row.location).filter(String::isNotBlank).joinToString(" · ")
    is WearTask -> listOfNotNull(row.calendar, row.category).filter(String::isNotBlank).joinToString(" · ")
    else -> ""
}
private fun dayHeader(day: Long, today: Long) = when (day) {
    today -> "Today"
    today + 1 -> "Tomorrow"
    else -> WearFormatting.date(day)
}
private fun rowSchedule(row: Any, snapshot: WearSnapshot) = when (row) {
    is WearEvent -> WearFormatting.eventTime(row, snapshot.timeFormat)
    is WearTask -> WearFormatting.taskDue(row, snapshot.timeFormat)
    else -> ""
}
private fun detailSchedule(row: Any, snapshot: WearSnapshot) = when (row) {
    is WearEvent -> "${WearFormatting.eventDate(row)} · ${WearFormatting.eventTime(row, snapshot.timeFormat)}"
    is WearTask -> WearFormatting.taskDue(row, snapshot.timeFormat)
    else -> ""
}
private fun TaskGroup.label() = name.lowercase().replaceFirstChar(Char::uppercase)
private fun calino.malinov.ski.wearcontract.WearAck.notice() = when (result) {
    WearAckResult.APPLIED -> "Saved on phone"
    WearAckResult.QUEUED -> "Saved · waiting to sync"
    WearAckResult.NOOP -> "Already up to date"
    else -> message ?: result.name.lowercase()
}
private fun staleHeader(snapshot: WearSnapshot): String? {
    val age = Duration.between(Instant.ofEpochMilli(snapshot.generatedAtMillis), Instant.now())
    return if (snapshot.stale || age.toHours() > 1 || snapshot.truncated) {
        "Updated ${age.toHours().coerceAtLeast(0)}h ago${if (snapshot.truncated) " · shortened" else ""}"
    } else null
}
