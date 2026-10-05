package calino.malinov.ski.wear

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.produceState
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import androidx.wear.compose.material3.FailureConfirmationDialog
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WearActivity : ComponentActivity() {
    private var requestedDetailId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedDetailId = intent.detailId()
        lifecycleScope.launch(Dispatchers.IO) { WearCommands.replay(this@WearActivity) }
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
        // Decoding the stored snapshot/outbox is file IO; keep it off the UI thread. The previous
        // state stays on screen while a newer revision loads.
        val loaded by produceState<WearReducedState?>(null, revision) {
            value = withContext(Dispatchers.IO) { WearStore(this@WearActivity).state() }
        }
        val state = loaded ?: return
        val nowMillis = rememberMinuteClock()
        var selectedId by remember { mutableStateOf(requestedDetailId) }
        val navController = rememberSwipeDismissableNavController()
        LaunchedEffect(requestedDetailId, state.snapshot != null) {
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
                item { PageHeader(getString(R.string.wear_agenda)) }
                statusItems(state, nowMillis)
                if (snapshot != null && today != null) {
                    groups.forEach { group ->
                        item(key = "day-${group.epochDay}") {
                            SectionHeader(dayHeader(this@WearActivity, group.epochDay, today))
                        }
                        if (group.rows.isEmpty()) {
                            item(key = "empty-${group.epochDay}") { EmptyRow(getString(R.string.wear_nothing_planned)) }
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
                item { PageHeader(getString(R.string.wear_tasks)) }
                statusItems(state, nowMillis)
                if (snapshot != null) {
                    if (sections.isEmpty()) item { EmptyRow(getString(R.string.wear_no_open_tasks)) }
                    sections.forEach { section ->
                        item(key = "group-${section.group}") {
                            SectionHeader(section.group.label(this@WearActivity))
                        }
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
        listOfNotNull(
            state.recentNotice(nowMillis)?.notice(this),
            state.snapshot?.let { staleLabel(this, it, nowMillis) },
        ).size +
            if (state.snapshot == null) 1 else 0

    private fun ScalingLazyListScope.statusItems(state: WearReducedState, nowMillis: Long) {
        state.recentNotice(nowMillis)?.let { acknowledgement ->
            item(key = "notice") { InfoBlock(acknowledgement.notice(this@WearActivity), getString(R.string.wear_watch_action)) }
        }
        val snapshot = state.snapshot
        if (snapshot == null) {
            item(key = "setup") {
                InfoBlock(
                    getString(R.string.wear_set_up_on_phone),
                    getString(R.string.wear_sync_automatically),
                )
            }
        } else {
            staleLabel(this@WearActivity, snapshot, nowMillis)?.let { label ->
                item(key = "stale") { InfoBlock(label, getString(R.string.wear_cached_calendar)) }
            }
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
        val color = Color(rowColor(row))
        // A task's marker is an open ring, like an unticked checkbox, so it
        // reads differently from an event's filled dot in the mixed agenda.
        if (row is WearTask) {
            Box(Modifier.size(10.dp).border(1.5.dp, color, CircleShape))
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        }
    }

    @Composable
    private fun RecordCard(row: Any, snapshot: WearSnapshot, ended: Boolean, onClick: () -> Unit) {
        val schedule = rowSchedule(this@WearActivity, row, snapshot.timeFormat)
        val pending = row is WearTask && row.writeState == WearWriteState.PENDING
        val endedLabel = getString(R.string.wear_ended)
        val openDetailsLabel = getString(R.string.wear_open_details)
        val pendingSchedule = getString(R.string.wear_pending, schedule)
        TitleCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (ended) ENDED_ALPHA else 1f)
                .semantics {
                    contentDescription = listOfNotNull(rowTitle(row), schedule, endedLabel.takeIf { ended }, openDetailsLabel)
                        .joinToString(", ")
                },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorDot(row)
                    Text(rowTitle(row), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            subtitle = { Text(if (pending) pendingSchedule else schedule) },
        )
    }

    @Composable
    private fun DetailScreen(row: Any, snapshot: WearSnapshot, onFinished: () -> Unit) {
        val listState = rememberScalingLazyListState()
        val haptics = LocalHapticFeedback.current
        var confirmation by remember { mutableStateOf<String?>(null) }
        var phoneFailed by remember { mutableStateOf(false) }
        val tomorrow = WearFormatting.today(snapshot) + 1
        val phoneLabel = getString(R.string.wear_phone)
        val taskLabel = getString(R.string.wear_task)
        val eventLabel = getString(R.string.wear_event)
        val completeLabel = getString(R.string.wear_complete)
        val markTaskDoneLabel = getString(R.string.wear_mark_task_done)
        val completedLabel = getString(R.string.wear_completed)
        val tomorrowLabel = getString(R.string.wear_tomorrow)
        val moveDueDateLabel = getString(R.string.wear_move_due_date)
        val movedLabel = getString(R.string.wear_moved)
        val phoneUnreachableLabel = getString(R.string.wear_phone_unreachable)
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    openPhone(row) { phoneFailed = true }
                }) { Text(phoneLabel) }
            },
        ) { contentPadding ->
            ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
                item { PageHeader(if (row is WearTask) taskLabel else eventLabel) }
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
                        Text(detailSchedule(this@WearActivity, row, snapshot), style = MaterialTheme.typography.bodyMedium)
                        rowMetadata(row).takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (row is WearTask) {
                            Text(wearTaskStatus(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (row is WearTask && !row.done) {
                    item {
                        ActionButton(completeLabel, markTaskDoneLabel, filled = true) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            command(snapshot, row, WearCommandOp.SET_TASK_DONE, null)
                            confirmation = completedLabel
                        }
                    }
                    if (row.dueEpochDay != tomorrow) {
                        item {
                            ActionButton(tomorrowLabel, moveDueDateLabel) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                command(snapshot, row, WearCommandOp.RESCHEDULE_TASK, tomorrow)
                                confirmation = movedLabel
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
        FailureConfirmationDialog(
            visible = phoneFailed,
            onDismissRequest = { phoneFailed = false },
            curvedText = { confirmationDialogCurvedText(phoneUnreachableLabel, curvedStyle) },
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
        lifecycleScope.launch(Dispatchers.IO) { sendCommand(snapshot, task, op, targetEpochDay) }
    }

    private fun sendCommand(snapshot: WearSnapshot, task: WearTask, op: WearCommandOp, targetEpochDay: Long?) {
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

    /** [onFailure] runs on the main thread when no phone accepted the request. */
    private fun openPhone(row: Any, onFailure: () -> Unit) {
        val kind = if (row is WearTask) "task" else "event"
        val id = if (row is WearTask) row.recordId else (row as WearEvent).recordId
        val day = if (row is WearTask) row.dueEpochDay else (row as WearEvent).startEpochDay
        val encoded = URLEncoder.encode(id, StandardCharsets.UTF_8.name())
        val result = RemoteActivityHelper(this).startRemoteActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("calino.malinov.ski://reminder/$kind?id=$encoded&day=$day")),
            null,
        )
        result.addListener(
            { if (runCatching { result.get() }.isFailure && !isFinishing) onFailure() },
            ContextCompat.getMainExecutor(this),
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
private fun dayHeader(context: Context, day: Long, today: Long) = when (day) {
    today -> context.getString(R.string.wear_today)
    today + 1 -> context.getString(R.string.wear_tomorrow)
    else -> context.wearDate(day)
}
private fun rowSchedule(context: Context, row: Any, timeFormat: WearTimeFormat) = when (row) {
    is WearEvent -> context.wearEventTime(row, timeFormat)
    is WearTask -> context.wearTaskDue(row, timeFormat)
    else -> ""
}
private fun detailSchedule(context: Context, row: Any, snapshot: WearSnapshot) = when (row) {
    is WearEvent -> "${context.wearEventDate(row)} · ${context.wearEventTime(row, snapshot.timeFormat)}"
    is WearTask -> context.wearTaskDue(row, snapshot.timeFormat)
    else -> ""
}
private fun TaskGroup.label(context: Context) = context.getString(
    when (this) {
        TaskGroup.OVERDUE -> R.string.wear_group_overdue
        TaskGroup.TODAY -> R.string.wear_group_today
        TaskGroup.UPCOMING -> R.string.wear_group_upcoming
        TaskGroup.UNDATED -> R.string.wear_group_undated
    },
)

/** Failures stay until replaced; confirmations fade out of the list after a few minutes. */
private fun WearReducedState.recentNotice(nowMillis: Long): WearAck? = notices.firstOrNull()?.takeIf {
    it.result !in setOf(WearAckResult.APPLIED, WearAckResult.QUEUED, WearAckResult.NOOP) ||
        nowMillis - it.atMillis < NOTICE_MILLIS
}
private fun WearAck.notice(context: Context) = when (result) {
    WearAckResult.APPLIED -> context.getString(R.string.wear_saved_on_phone)
    WearAckResult.QUEUED -> context.getString(R.string.wear_saved_waiting_to_sync)
    WearAckResult.NOOP -> context.getString(R.string.wear_already_up_to_date)
    else -> message?.let { context.localizedProcessorMessage(it) } ?: context.getString(
        when (result) {
            WearAckResult.CONFLICT -> R.string.wear_conflict
            WearAckResult.NOT_FOUND -> R.string.wear_not_found
            WearAckResult.REJECTED -> R.string.wear_rejected
            WearAckResult.EXPIRED -> R.string.wear_expired
            WearAckResult.NO_ACCOUNT -> R.string.wear_no_account
            WearAckResult.UNSUPPORTED -> R.string.wear_unsupported
            WearAckResult.APPLIED -> R.string.wear_saved_on_phone
            WearAckResult.QUEUED -> R.string.wear_saved_waiting_to_sync
            WearAckResult.NOOP -> R.string.wear_already_up_to_date
        },
    )
}

/** Translate only stable messages authored by WearCommandProcessor; keep unknown diagnostics intact. */
private fun Context.localizedProcessorMessage(message: String): String {
    val resourceId = when (message) {
        "Command expired" -> R.string.wear_ack_command_expired
        "Connect Calino on phone" -> R.string.wear_ack_connect_calino_on_phone
        "Watch data is no longer current" -> R.string.wear_ack_watch_data_no_longer_current
        "Unsupported command" -> R.string.wear_ack_unsupported_command
        "Missing target day" -> R.string.wear_ack_missing_target_day
        "Task changed or was removed" -> R.string.wear_ack_task_changed_or_removed
        "Task changed on phone" -> R.string.wear_ack_task_changed_on_phone
        else -> return message
    }
    return getString(resourceId)
}
private fun staleLabel(context: Context, snapshot: WearSnapshot, nowMillis: Long): String? {
    val age = Duration.between(Instant.ofEpochMilli(snapshot.generatedAtMillis), Instant.ofEpochMilli(nowMillis))
    val old = snapshot.stale || age.toHours() >= 1
    val parts = listOfNotNull(
        context.getString(R.string.wear_updated_ago, context.wearAgo(age)).takeIf { old },
        context.getString(R.string.wear_some_items_hidden).takeIf { snapshot.truncated },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun Context.wearDate(epochDay: Long): String {
    val locale = resources.configuration.locales[0] ?: Locale.getDefault()
    val pattern = if (locale.language in setOf("da", "de")) {
        DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
    } else {
        "EEE, d MMM"
    }
    return LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(pattern, locale))
}

internal fun Context.wearTime(minuteOfDay: Int, format: WearTimeFormat, compact: Boolean = false): String {
    val locale = resources.configuration.locales[0] ?: Locale.getDefault()
    val normalized = Math.floorMod(minuteOfDay, 24 * 60)
    val pattern = when {
        format == WearTimeFormat.H24 -> "HH:mm"
        compact -> "h:mma"
        else -> "h:mm a"
    }
    val formatted = LocalTime.of(normalized / 60, normalized % 60)
        .format(DateTimeFormatter.ofPattern(pattern, locale))
    return if (format == WearTimeFormat.H12 && compact) {
        formatted.lowercase(locale).replace(":00", "")
    } else {
        formatted
    }
}

internal fun Context.wearEventDate(event: WearEvent): String = if (event.endEpochDay > event.startEpochDay) {
    "${wearDate(event.startEpochDay)} – ${wearDate(event.endEpochDay)}"
} else {
    wearDate(event.startEpochDay)
}

internal fun Context.wearEventTime(event: WearEvent, format: WearTimeFormat): String {
    if (event.allDay) return getString(R.string.wear_all_day)
    val start = event.startMinute ?: return getString(R.string.wear_time_unavailable)
    val end = event.durationMinutes?.let { start + it }
    return if (end != null) "${wearTime(start, format)}–${wearTime(end, format)}" else wearTime(start, format)
}

internal fun Context.wearTaskDue(task: WearTask, format: WearTimeFormat): String {
    val day = task.dueEpochDay ?: return getString(R.string.wear_no_due_date)
    val time = task.dueMinute?.let { " · ${wearTime(it, format)}" }.orEmpty()
    return getString(R.string.wear_due_1_s, "${wearDate(day)}$time")
}

private fun Context.wearTaskStatus(task: WearTask): String = when {
    task.done -> getString(R.string.wear_completed)
    task.progress > 0 -> getString(R.string.wear_progress_percent, task.progress)
    else -> getString(R.string.wear_open)
}

private fun Context.wearAgo(age: Duration): String = when {
    age.toMinutes() < 1 -> getString(R.string.wear_just_now)
    age.toHours() < 1 -> resources.getQuantityString(
        R.plurals.wear_minutes_ago, age.toMinutes().toInt(), age.toMinutes().toInt(),
    )
    age.toDays() < 2 -> resources.getQuantityString(
        R.plurals.wear_hours_ago, age.toHours().toInt(), age.toHours().toInt(),
    )
    else -> resources.getQuantityString(
        R.plurals.wear_days_ago, age.toDays().toInt(), age.toDays().toInt(),
    )
}
