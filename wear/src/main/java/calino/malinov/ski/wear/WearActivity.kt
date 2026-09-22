package calino.malinov.ski.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.remote.interactions.RemoteActivityHelper
import calino.malinov.ski.wearcontract.AgendaGroup
import calino.malinov.ski.wearcontract.TaskGroup
import calino.malinov.ski.wearcontract.WearAck
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
import calino.malinov.ski.wearcontract.WearWriteState
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

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
        val nowMillis = rememberMinuteClock()
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
                OverviewPager(state, nowMillis) {
                    selectedId = rowId(it)
                    navController.navigate("detail")
                }
            }
            composable("detail") {
                val selected = state.snapshot?.record(selectedId)
                if (selected != null) {
                    DetailScreen(selected, requireNotNull(state.snapshot)) { navController.popBackStack() }
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
        }
    }

    /** Agenda and Tasks as sibling pages: the list starts with content, not a mode switch. */
    @Composable
    private fun OverviewPager(state: WearReducedState, nowMillis: Long, onOpen: (Any) -> Unit) {
        val pagerState = rememberPagerState(pageCount = { 2 })
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(state = pagerState) { page ->
                AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    if (page == 0) AgendaPage(state, nowMillis, onOpen) else TasksPage(state, nowMillis, onOpen)
                }
            }
        }
    }

    @Composable
    private fun AgendaPage(state: WearReducedState, nowMillis: Long, onOpen: (Any) -> Unit) {
        val snapshot = state.snapshot
        val today = snapshot?.let { WearFormatting.today(it, nowMillis) }
        val minute = snapshot?.let { WearFormatting.minuteNow(it, nowMillis) } ?: 0
        val groups = remember(snapshot, today) {
            if (snapshot == null || today == null) emptyList() else {
                val groups = WearSelection.agendaGroups(snapshot, today)
                if (groups.any { it.epochDay == today }) groups else listOf(AgendaGroup(today, emptyList())) + groups
            }
        }
        val status = statusCount(state, nowMillis)
        // Land on what is happening now rather than on the morning's finished meetings.
        val focusIndex = remember(snapshot == null) {
            if (today == null) 1 else {
                var index = 1 + status
                for (group in groups) {
                    index += 1
                    val live = group.rows.indexOfFirst { !WearSelection.ended(it, today, minute) }
                    if (live >= 0) return@remember if (group.epochDay == today && live == 0) index - 1 else index + live
                    index += group.rows.size.coerceAtLeast(1)
                }
                1
            }
        }
        val listState = rememberScalingLazyListState(initialCenterItemIndex = focusIndex)
        // Also after recreation, where restored scroll state would otherwise win over "now".
        LaunchedEffect(snapshot == null) { if (snapshot != null) listState.scrollToItem(focusIndex) }
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
                item { PageHeader("Agenda") }
                statusItems(state, nowMillis)
                if (snapshot != null && today != null) {
                    groups.forEach { group ->
                        item(key = "day-${group.epochDay}") { SectionHeader(dayHeader(group.epochDay, today)) }
                        if (group.rows.isEmpty()) {
                            item(key = "empty-${group.epochDay}") { EmptyRow("Nothing planned") }
                        }
                        items(group.rows.size, key = { rowId(group.rows[it]) }) { index ->
                            val row = group.rows[index]
                            RecordCard(row, snapshot, ended = WearSelection.ended(row, today, minute)) { onOpen(row) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TasksPage(state: WearReducedState, nowMillis: Long, onOpen: (Any) -> Unit) {
        val snapshot = state.snapshot
        val today = snapshot?.let { WearFormatting.today(it, nowMillis) }
        val sections = remember(snapshot, today) {
            if (snapshot == null || today == null) emptyList() else WearSelection.taskSections(snapshot, today)
        }
        val listState = rememberScalingLazyListState()
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
                item { PageHeader("Tasks") }
                statusItems(state, nowMillis)
                if (snapshot != null) {
                    if (sections.isEmpty()) item { EmptyRow("No open tasks") }
                    sections.forEach { section ->
                        item(key = "group-${section.group}") { SectionHeader(section.group.label()) }
                        items(section.tasks.size, key = { section.tasks[it].occurrenceId }) { index ->
                            val task = section.tasks[index]
                            RecordCard(task, snapshot, ended = false) { onOpen(task) }
                        }
                    }
                }
            }
        }
    }

    private fun statusCount(state: WearReducedState, nowMillis: Long) =
        listOfNotNull(state.recentNotice(nowMillis), state.snapshot?.let { staleLabel(it, nowMillis) }).size +
            if (state.snapshot == null) 1 else 0

    private fun ScalingLazyListScope.statusItems(state: WearReducedState, nowMillis: Long) {
        state.recentNotice(nowMillis)?.let { acknowledgement ->
            item(key = "notice") { InfoBlock(acknowledgement.notice(), "Watch action") }
        }
        val snapshot = state.snapshot
        if (snapshot == null) {
            item(key = "setup") { InfoBlock("Set up Calino on your phone", "The watch will sync automatically") }
        } else {
            staleLabel(snapshot, nowMillis)?.let { label -> item(key = "stale") { InfoBlock(label, "Cached calendar") } }
        }
    }

    @Composable
    private fun PageHeader(title: String) {
        ListHeader(Modifier.fillMaxWidth()) { Text(title) }
    }

    @Composable
    private fun SectionHeader(title: String) {
        ListHeader(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun EmptyRow(text: String) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    /** Status text on a quiet surface; it is information, so it is not a tappable card. */
    @Composable
    private fun InfoBlock(title: String, subtitle: String) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun ColorDot(row: Any) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(rowColor(row))))
    }

    @Composable
    private fun RecordCard(row: Any, snapshot: WearSnapshot, ended: Boolean, onClick: () -> Unit) {
        val schedule = remember(row, snapshot.timeFormat) { rowSchedule(row, snapshot.timeFormat) }
        val pending = row is WearTask && row.writeState == WearWriteState.PENDING
        TitleCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (ended) ENDED_ALPHA else 1f)
                .semantics {
                    contentDescription = listOfNotNull(rowTitle(row), schedule, "ended".takeIf { ended }, "Open details")
                        .joinToString(", ")
                },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorDot(row)
                    Text(rowTitle(row), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            subtitle = { Text(if (pending) "Pending · $schedule" else schedule) },
        )
    }

    @Composable
    private fun DetailScreen(row: Any, snapshot: WearSnapshot, onFinished: () -> Unit) {
        val listState = rememberScalingLazyListState()
        val haptics = LocalHapticFeedback.current
        var confirmation by remember { mutableStateOf<String?>(null) }
        val tomorrow = WearFormatting.today(snapshot) + 1
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    openPhone(row)
                }) { Text("Phone") }
            },
        ) { contentPadding ->
            ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
                item { PageHeader(if (row is WearTask) "Task" else "Event") }
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorDot(row)
                            Text(rowTitle(row), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(detailSchedule(row, snapshot), style = MaterialTheme.typography.bodyMedium)
                        rowMetadata(row).takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (row is WearTask) {
                            Text(WearFormatting.taskStatus(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (row is WearTask && !row.done) {
                    item {
                        ActionButton("Complete", "Mark this task done", filled = true) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            command(snapshot, row, WearCommandOp.SET_TASK_DONE, null)
                            confirmation = "Completed"
                        }
                    }
                    if (row.dueEpochDay != tomorrow) {
                        item {
                            ActionButton("Tomorrow", "Move the due date") {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                command(snapshot, row, WearCommandOp.RESCHEDULE_TASK, tomorrow)
                                confirmation = "Moved"
                            }
                        }
                    }
                }
            }
        }
        val curvedStyle = ConfirmationDialogDefaults.curvedTextStyle
        SuccessConfirmationDialog(
            visible = confirmation != null,
            onDismissRequest = {
                confirmation = null
                onFinished()
            },
            curvedText = { confirmationDialogCurvedText(confirmation.orEmpty(), curvedStyle) },
        )
    }

    @Composable
    private fun ActionButton(label: String, secondary: String, filled: Boolean = false, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = if (filled) ButtonDefaults.filledVariantButtonColors() else ButtonDefaults.buttonColors(),
            label = { Text(label) },
            secondaryLabel = { Text(secondary) },
        )
    }

    private fun command(snapshot: WearSnapshot, task: WearTask, op: WearCommandOp, targetEpochDay: Long?) {
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
                targetEpochDay = targetEpochDay,
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

private const val ENDED_ALPHA = 0.5f
private const val NOTICE_MILLIS = 10 * 60 * 1000L

/** Wall clock that ticks on the minute, so ended events dim and "today" rolls over while open. */
@Composable
private fun rememberMinuteClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - now % 60_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

internal const val EXTRA_OCCURRENCE_ID = "occurrenceId"

/** Complications deep-link with a URI; tiles can only pass extras. */
private fun Intent.detailId(): String? = getStringExtra(EXTRA_OCCURRENCE_ID)
    ?: data?.takeIf { it.scheme == "calino-wear" && it.host == "detail" }?.pathSegments?.firstOrNull()
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
private fun rowSchedule(row: Any, timeFormat: WearTimeFormat) = when (row) {
    is WearEvent -> WearFormatting.eventTime(row, timeFormat)
    is WearTask -> WearFormatting.taskDue(row, timeFormat)
    else -> ""
}
private fun detailSchedule(row: Any, snapshot: WearSnapshot) = when (row) {
    is WearEvent -> "${WearFormatting.eventDate(row)} · ${WearFormatting.eventTime(row, snapshot.timeFormat)}"
    is WearTask -> WearFormatting.taskDue(row, snapshot.timeFormat)
    else -> ""
}
private fun TaskGroup.label() = name.lowercase().replaceFirstChar(Char::uppercase)

/** Failures stay until replaced; confirmations fade out of the list after a few minutes. */
private fun WearReducedState.recentNotice(nowMillis: Long): WearAck? = notices.firstOrNull()?.takeIf {
    it.result !in setOf(WearAckResult.APPLIED, WearAckResult.QUEUED, WearAckResult.NOOP) ||
        nowMillis - it.atMillis < NOTICE_MILLIS
}
private fun WearAck.notice() = when (result) {
    WearAckResult.APPLIED -> "Saved on phone"
    WearAckResult.QUEUED -> "Saved · waiting to sync"
    WearAckResult.NOOP -> "Already up to date"
    else -> message ?: result.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
}
private fun staleLabel(snapshot: WearSnapshot, nowMillis: Long): String? {
    val age = Duration.between(Instant.ofEpochMilli(snapshot.generatedAtMillis), Instant.ofEpochMilli(nowMillis))
    val old = snapshot.stale || age.toHours() >= 1
    val parts = listOfNotNull(
        "Updated ${ago(age)}".takeIf { old },
        "Some items hidden".takeIf { snapshot.truncated },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
private fun ago(age: Duration) = when {
    age.toMinutes() < 1 -> "just now"
    age.toHours() < 1 -> "${age.toMinutes()}m ago"
    age.toDays() < 2 -> "${age.toHours()}h ago"
    else -> "${age.toDays()}d ago"
}
