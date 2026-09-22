package calino.malinov.ski.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.remote.interactions.RemoteActivityHelper
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
import calino.malinov.ski.wearcontract.WearTimeFormat
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class WearActivity : ComponentActivity() {
    private var requestedDetailId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedDetailId = intent.detailId()
        WearCommands.replay(this)
        setContent { MaterialTheme { WearRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDetailId = intent.detailId()
    }

    @Composable private fun WearRoot() {
        val revision by WearStateUpdates.revision.collectAsStateWithLifecycle()
        val state = remember(revision) { WearStore(this).state() }
        var tasksOnly by remember { mutableStateOf(false) }
        var selectedId by remember { mutableStateOf(requestedDetailId) }
        LaunchedEffect(requestedDetailId) { selectedId = requestedDetailId }
        val selected = state.snapshot?.let { snapshot ->
            (snapshot.events + snapshot.tasks).firstOrNull {
                when (it) {
                    is WearEvent -> it.occurrenceId == selectedId
                    is WearTask -> it.occurrenceId == selectedId
                    else -> false
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(if (tasksOnly) "Tasks" else "Agenda")
                TextButton(onClick = { tasksOnly = !tasksOnly }) {
                    Text(if (tasksOnly) "Agenda" else "Tasks")
                }
            }
            state.notices.firstOrNull()?.let { acknowledgement ->
                item {
                    Text(
                        when (acknowledgement.result) {
                            WearAckResult.APPLIED -> "Saved on phone"
                            WearAckResult.QUEUED -> "Saved · waiting to sync"
                            WearAckResult.NOOP -> "Already up to date"
                            else -> acknowledgement.message ?: acknowledgement.result.name.lowercase()
                        },
                    )
                }
            }
            val snapshot = state.snapshot
            if (snapshot == null) {
                item { Text("Set up Calino on your phone") }
            } else {
                staleHeader(snapshot)?.let { label -> item { Text(label) } }
                if (tasksOnly) {
                    WearSelection.taskSections(snapshot, WearFormatting.today(snapshot)).forEach { section ->
                        item { Text(section.group.label()) }
                        items(section.tasks, key = { it.occurrenceId }) { task ->
                            TaskRow(task, snapshot, state, onOpen = { selectedId = task.occurrenceId })
                        }
                    }
                } else {
                    WearSelection.agendaGroups(snapshot, WearFormatting.today(snapshot)).forEach { group ->
                        item { Text(WearFormatting.date(group.epochDay)) }
                        items(group.rows, key = ::rowId) { row ->
                            AgendaRow(row, snapshot, state, onOpen = { selectedId = rowId(row) })
                        }
                    }
                }
            }
            selected?.let { row ->
                item { Detail(row, state.snapshot, onClose = { selectedId = null }) }
            }
        }
    }

    @Composable private fun AgendaRow(
        row: Any,
        snapshot: WearSnapshot,
        state: WearReducedState,
        onOpen: () -> Unit,
    ) {
        val pending = state.pendingCommandIds.isNotEmpty() && row is WearTask && row.writeState.name == "PENDING"
        val label = when (row) {
            is WearEvent -> WearFormatting.eventRow(row, snapshot.timeFormat)
            is WearTask -> WearFormatting.taskRow(row, snapshot.timeFormat)
            else -> rowTitle(row)
        }
        val spoken = (if (pending) "Pending. " else "") + label
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClick = onOpen)
                .semantics { contentDescription = "$spoken. Open details" }
                .padding(6.dp),
        ) {
            Text((if (pending) "Pending · " else "") + label)
        }
    }

    @Composable private fun TaskRow(
        task: WearTask,
        snapshot: WearSnapshot,
        state: WearReducedState,
        onOpen: () -> Unit,
    ) = AgendaRow(task, snapshot, state, onOpen)

    @Composable private fun Detail(row: Any, snapshot: WearSnapshot?, onClose: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rowTitle(row))
            when (row) {
                is WearEvent -> {
                    Text(WearFormatting.eventDate(row))
                    Text(WearFormatting.eventTime(row, snapshot?.timeFormat ?: WearTimeFormat.H24))
                    Text("Calendar · ${row.calendar}")
                    row.location?.takeIf(String::isNotBlank)?.let { Text("Location · $it") }
                }
                is WearTask -> {
                    Text(WearFormatting.taskDue(row, snapshot?.timeFormat ?: WearTimeFormat.H24))
                    Text("Status · ${WearFormatting.taskStatus(row)}")
                    Text("Calendar · ${row.calendar}")
                    row.category?.takeIf(String::isNotBlank)?.let { Text("Category · $it") }
                }
            }
            TextButton(onClick = { openPhone(row) }) { Text("Open on phone") }
            if (row is WearTask && snapshot != null) {
                Row {
                    TextButton(onClick = { command(snapshot, row, WearCommandOp.SET_TASK_DONE, null) }) {
                        Text("Complete")
                    }
                    TextButton(onClick = {
                        command(
                            snapshot,
                            row,
                            WearCommandOp.RESCHEDULE_TASK,
                            LocalDate.ofEpochDay(WearFormatting.today(snapshot)).plusDays(1),
                        )
                    }) { Text("Tomorrow") }
                }
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
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

private fun rowId(row: Any) = when (row) {
    is WearEvent -> row.occurrenceId
    is WearTask -> row.occurrenceId
    else -> row.hashCode().toString()
}

private fun rowTitle(row: Any) = when (row) {
    is WearEvent -> row.title
    is WearTask -> row.title
    else -> ""
}

private fun TaskGroup.label() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun staleHeader(snapshot: WearSnapshot): String? {
    val age = Duration.between(Instant.ofEpochMilli(snapshot.generatedAtMillis), Instant.now())
    return if (snapshot.stale || age.toHours() > 1 || snapshot.truncated) {
        "Updated ${age.toHours().coerceAtLeast(0)}h ago${if (snapshot.truncated) " · shortened" else ""}"
    } else null
}
