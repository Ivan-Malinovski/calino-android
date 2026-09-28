package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import calino.malinov.ski.data.repository.PENDING_CHANGE_MAX_RETRIES
import calino.malinov.ski.data.repository.PendingChange
import calino.malinov.ski.data.repository.PendingChangeState
import calino.malinov.ski.data.repository.PendingChangeType
import calino.malinov.ski.data.repository.displayTitle
import calino.malinov.ski.data.repository.resourceKeys
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.ui.components.EditorSection
import calino.malinov.ski.util.CalinoTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Durable writes that are waiting, retrying, or need a decision. Each row
 * names the item, its calendar and why it has not synced; failed rows can be
 * retried now or discarded, which reverts the item to its server version.
 */
@Composable
internal fun PendingWritesCard(
    changes: List<PendingChange>,
    collectionNames: Map<String, String>,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
) = EditorSection("Pending writes", Modifier.animateContentSize(tween(CalinoMotion.ContentEnterMillis))) {
    val dead = changes.count { it.state == PendingChangeState.DEAD_LETTER }
    val retrying = changes.count { it.state == PendingChangeState.RETRY }
    val queued = changes.size - dead - retrying
    Text(
        listOfNotNull(
            dead.takeIf { it > 0 }?.let { "$it need${if (it == 1) "s" else ""} attention" },
            retrying.takeIf { it > 0 }?.let { "$it will retry" },
            queued.takeIf { it > 0 }?.let { "$it waiting to sync" },
        ).joinToString(" · ").replaceFirstChar { it.uppercase() } + ".",
        style = CalinoTypography.bodySmall,
        color = if (dead == 0) CalinoColors.Ink3 else CalinoColors.Rose,
    )

    // A queued change behind a failed one for the same item waits for it.
    val waitingBehind = remember(changes) {
        val blocked = HashSet<String>()
        val result = HashSet<String>()
        changes.forEach { change ->
            val keys = change.resourceKeys()
            if (keys.any(blocked::contains)) result += change.id
            if (change.state != PendingChangeState.PENDING || change.id in result) blocked += keys
        }
        result
    }
    // Problems first; queue order otherwise.
    val ordered = remember(changes) {
        changes.sortedBy {
            when (it.state) {
                PendingChangeState.DEAD_LETTER -> 0
                PendingChangeState.RETRY -> 1
                PendingChangeState.PENDING -> 2
            }
        }
    }
    val timeFormat = LocalTimeFormat
    ordered.forEachIndexed { index, change ->
        key(change.id) {
            if (index > 0) HorizontalDivider(color = CalinoColors.Line)
            PendingChangeRow(
                change = change,
                collectionName = collectionNames[change.calendarUrl] ?: collectionNames[change.calendarId],
                waitingBehind = change.id in waitingBehind,
                timeFormat = timeFormat,
                onRetry = { onRetry(change.id) },
                onDiscard = { onDiscard(change.id) },
            )
        }
    }
}

@Composable
private fun PendingChangeRow(
    change: PendingChange,
    collectionName: String?,
    waitingBehind: Boolean,
    timeFormat: CalinoTimeFormat,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val failed = change.state != PendingChangeState.PENDING
    var confirmingDiscard by rememberSaveable(change.id) { mutableStateOf(false) }
    val title = remember(change.id, change.updatedAt) { change.displayTitle() } ?: "Untitled ${kindLabel(change)}"
    Column(
        Modifier.fillMaxWidth().animateContentSize(tween(CalinoMotion.ContentEnterMillis)),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = CalinoTypography.bodyMedium, maxLines = 2)
                Text(
                    listOfNotNull(actionLabel(change), collectionName).joinToString(" · "),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                )
                Text(
                    statusLabel(change, waitingBehind),
                    style = CalinoTypography.bodySmall,
                    color = when (change.state) {
                        PendingChangeState.DEAD_LETTER -> CalinoColors.Rose
                        PendingChangeState.RETRY -> CalinoColors.Ink2
                        PendingChangeState.PENDING -> CalinoColors.Ink3
                    },
                )
            }
            AnimatedVisibility(
                visible = failed && !confirmingDiscard,
                enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)),
                exit = fadeOut(tween(CalinoMotion.ContentExitMillis)),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 44.dp)
                            .semantics { contentDescription = "Retry $title now" },
                    ) {
                        Text(
                            if (change.state == PendingChangeState.RETRY) "Retry now" else "Retry",
                            color = CalinoColors.Accent,
                        )
                    }
                    TextButton(
                        onClick = { confirmingDiscard = true },
                        modifier = Modifier.heightIn(min = 44.dp)
                            .semantics { contentDescription = "Discard the change to $title" },
                    ) { Text("Discard", color = CalinoColors.Rose) }
                }
            }
        }
        change.lastFailure?.let { failure ->
            Text(failure.message, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2)
            failureDetail(change, timeFormat)?.let {
                Text(it, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
            }
        }
        AnimatedVisibility(
            visible = failed && confirmingDiscard,
            enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) +
                expandVertically(tween(CalinoMotion.ContentEnterMillis)),
            exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) +
                shrinkVertically(tween(CalinoMotion.ContentExitMillis)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    discardWarning(change),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = {
                            confirmingDiscard = false
                            onDiscard()
                        },
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) { Text(discardConfirmLabel(change), color = CalinoColors.Rose) }
                    TextButton(
                        onClick = { confirmingDiscard = false },
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) { Text("Keep", color = CalinoColors.Accent) }
                }
            }
        }
    }
}

private fun kindLabel(change: PendingChange): String = when (change.component.uppercase()) {
    "VEVENT" -> "event"
    "VTODO" -> "task"
    "VJOURNAL" -> "journal entry"
    "VCARD" -> "contact"
    else -> "item"
}

private fun actionLabel(change: PendingChange): String {
    val kind = kindLabel(change)
    return when (change.type) {
        PendingChangeType.CREATE -> "New $kind"
        PendingChangeType.UPDATE -> "Edited $kind"
        PendingChangeType.DELETE -> "Deleted $kind"
        PendingChangeType.MOVE -> "Moved $kind"
        PendingChangeType.DELETE_HREF -> "Finishing a move"
    }
}

private fun statusLabel(change: PendingChange, waitingBehind: Boolean): String = when (change.state) {
    PendingChangeState.DEAD_LETTER -> "Needs attention"
    PendingChangeState.RETRY -> "Will retry automatically"
    PendingChangeState.PENDING ->
        if (waitingBehind) "Waiting for an earlier change to this item" else "Queued"
}

/** HTTP status, attempts and timing, e.g. "HTTP 415 · attempt 3 of 10 · next try 18:05". */
private fun failureDetail(change: PendingChange, timeFormat: CalinoTimeFormat): String? {
    val parts = buildList {
        change.lastFailure?.statusCode?.let { add("HTTP $it") }
        if (change.state == PendingChangeState.RETRY && change.retryCount > 0) {
            add("attempt ${change.retryCount} of $PENDING_CHANGE_MAX_RETRIES")
        }
        when {
            change.state == PendingChangeState.RETRY && change.nextAttemptAt != null ->
                add("next try ${formatStamp(change.nextAttemptAt, timeFormat)}")
            change.lastFailure != null ->
                add("last tried ${formatStamp(change.lastFailure.at, timeFormat)}")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.replaceFirstChar { it.uppercase() }
}

private fun formatStamp(instant: Instant, timeFormat: CalinoTimeFormat): String {
    val local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val time = timeFormat.format(local.toLocalTime())
    return if (local.toLocalDate() == LocalDate.now()) time else "${local.toLocalDate()} $time"
}

private fun discardWarning(change: PendingChange): String = when (change.type) {
    PendingChangeType.CREATE -> "This removes the new ${kindLabel(change)} from this device. It was never saved on the server."
    PendingChangeType.DELETE -> "The ${kindLabel(change)} comes back as it is on the server."
    PendingChangeType.DELETE_HREF ->
        "The copy in the old calendar stays on the server, so the item may appear twice until you delete one."
    PendingChangeType.MOVE -> "The ${kindLabel(change)} stays in its original calendar."
    PendingChangeType.UPDATE -> "Unsynced edits to this ${kindLabel(change)} are lost and the server version is shown again."
}

private fun discardConfirmLabel(change: PendingChange): String = when (change.type) {
    PendingChangeType.CREATE -> "Delete it"
    else -> "Revert"
}
