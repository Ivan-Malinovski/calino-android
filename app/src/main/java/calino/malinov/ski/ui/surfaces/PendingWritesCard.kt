package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.repository.PENDING_CHANGE_MAX_RETRIES
import calino.malinov.ski.data.repository.PendingChange
import calino.malinov.ski.data.repository.PendingChangeState
import calino.malinov.ski.data.repository.PendingChangeType
import calino.malinov.ski.data.repository.displayTitle
import calino.malinov.ski.data.repository.resourceKeys
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.EditorSection
import calino.malinov.ski.ui.components.SectionLabel
import calino.malinov.ski.ui.components.calinoPressable
import calino.malinov.ski.util.CalinoTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A calendar or address book as the queued-changes card names it. */
internal data class CollectionLabel(val name: String, val color: Color?)

/**
 * Saved changes that have not reached the server yet.
 *
 * The card leads with what needs the person: each failed change by name,
 * with its calendar, why it failed and what to do about it, expandable in
 * place. Changes that are only waiting fold into a quiet list underneath, so
 * an offline afternoon of edits does not read like an emergency.
 */
@Composable
internal fun PendingWritesCard(
    changes: List<PendingChange>,
    collections: Map<String, CollectionLabel>,
    onRetry: (String) -> Unit,
    onRetryAll: () -> Unit,
    onDiscard: (String) -> Unit,
    modifier: Modifier = Modifier,
) = EditorSection(null, modifier.animateContentSize(tween(CalinoMotion.ContentEnterMillis))) {
    val problems = changes.filter { it.state != PendingChangeState.PENDING }
        .sortedBy { if (it.state == PendingChangeState.DEAD_LETTER) 0 else 1 }
    val queued = changes.filter { it.state == PendingChangeState.PENDING }
    val needsYou = problems.count { it.state == PendingChangeState.DEAD_LETTER }
    val timeFormat = LocalTimeFormat

    // A queued change behind a failed one for the same item waits for it.
    val waitingBehind = remember(changes) {
        val blocked = HashSet<String>()
        buildSet {
            changes.forEach { change ->
                val keys = change.resourceKeys()
                val behind = keys.any(blocked::contains)
                if (behind) add(change.id)
                if (behind || change.state != PendingChangeState.PENDING) blocked += keys
            }
        }
    }

    PendingHeader(
        needsYou = needsYou,
        retrying = problems.size - needsYou,
        queued = queued.size,
        onRetryAll = onRetryAll.takeIf { problems.size > 1 },
    )

    problems.forEachIndexed { index, change ->
        key(change.id) {
            HorizontalDivider(color = CalinoColors.Line)
            ProblemRow(
                change = change,
                collection = collection(collections, change),
                timeFormat = timeFormat,
                // The first thing that needs the person opens already.
                initiallyExpanded = index == 0 && change.state == PendingChangeState.DEAD_LETTER,
                onRetry = { onRetry(change.id) },
                onDiscard = { onDiscard(change.id) },
            )
        }
    }

    if (queued.isNotEmpty()) {
        // With problems above, the waiting list folds away; on its own it is
        // the whole story and stays open.
        var showQueued by rememberSaveable { mutableStateOf(false) }
        val open = problems.isEmpty() || showQueued
        if (problems.isNotEmpty()) {
            HorizontalDivider(color = CalinoColors.Line)
            DisclosureRow(
                text = "${queued.size} more waiting to sync",
                expanded = showQueued,
                onToggle = { showQueued = !showQueued },
            )
        } else {
            SectionLabel("Waiting", count = queued.size)
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) +
                expandVertically(tween(CalinoMotion.ContentEnterMillis)),
            exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) +
                shrinkVertically(tween(CalinoMotion.ContentExitMillis)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                queued.forEach { change ->
                    key(change.id) {
                        QueuedRow(change, collection(collections, change), change.id in waitingBehind)
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingHeader(needsYou: Int, retrying: Int, queued: Int, onRetryAll: (() -> Unit)?) {
    val tone = when {
        needsYou > 0 -> CalinoColors.Rose
        retrying > 0 -> CalinoColors.Amber
        else -> CalinoColors.Accent
    }
    val icon: ImageVector = when {
        needsYou > 0 -> CalinoIcons.Bell
        retrying > 0 -> CalinoIcons.Refresh
        else -> CalinoIcons.Clock
    }
    val title = when {
        needsYou > 0 -> "$needsYou change${plural(needsYou)} need${if (needsYou == 1) "s" else ""} you"
        retrying > 0 -> "$retrying change${plural(retrying)} retrying"
        else -> "$queued change${plural(queued)} waiting to sync"
    }
    val subtitle = when {
        needsYou > 0 -> "The server refused ${if (needsYou == 1) "it" else "them"}. Everything else keeps syncing."
        retrying > 0 -> "Calino will try again on its own. Nothing is lost."
        else -> "They sync on their own once the server is reachable."
    }
    val tint by animateColorAsState(tone, tween(CalinoMotion.SurfaceFadeMillis), label = "pending tone")
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    fadeIn(tween(CalinoMotion.ContentEnterMillis)) togetherWith
                        fadeOut(tween(CalinoMotion.FadeThroughMillis))
                },
                label = "pending icon",
            ) { glyph ->
                Icon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
        }
    }
    onRetryAll?.let {
        CalinoChip(
            text = "Retry all now",
            selected = true,
            description = "try every failed change again",
            onClick = it,
        )
    }
}

@Composable
private fun ProblemRow(
    change: PendingChange,
    collection: CollectionLabel?,
    timeFormat: CalinoTimeFormat,
    initiallyExpanded: Boolean,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    var expanded by rememberSaveable(change.id) { mutableStateOf(initiallyExpanded) }
    var confirming by rememberSaveable(change.id) { mutableStateOf(false) }
    val dead = change.state == PendingChangeState.DEAD_LETTER
    val tone = if (dead) CalinoColors.Rose else CalinoColors.Amber
    val title = itemTitle(change)
    val chevron by animateFloatAsState(
        if (expanded) 90f else 0f,
        tween(CalinoMotion.ContentEnterMillis),
        label = "problem chevron",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(CalinoShapes.Row))
                .calinoPressable(role = Role.Button) { expanded = !expanded }
                .semantics(mergeDescendants = true) {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CollectionDot(collection)
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(
                    title,
                    style = CalinoTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    metaLine(change, collection),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusTag(
                text = if (dead) {
                    "Needs you"
                } else {
                    change.nextAttemptAt?.let { "Retry ${formatStamp(it, timeFormat)}" } ?: "Retrying"
                },
                tone = tone,
            )
            Icon(
                CalinoIcons.ChevronRight,
                contentDescription = null,
                tint = CalinoColors.Ink3,
                modifier = Modifier.padding(start = 4.dp).size(18.dp).rotate(chevron),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) +
                expandVertically(tween(CalinoMotion.ContentEnterMillis)),
            exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) +
                shrinkVertically(tween(CalinoMotion.ContentExitMillis)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                change.lastFailure?.let { failure ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(tone.copy(alpha = .08f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(failure.message, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2)
                        failureDetail(change, timeFormat)?.let {
                            Text(it, style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
                        }
                    }
                }
                AnimatedContent(
                    targetState = confirming,
                    transitionSpec = {
                        fadeIn(tween(CalinoMotion.ContentEnterMillis)) togetherWith
                            fadeOut(tween(CalinoMotion.FadeThroughMillis))
                    },
                    label = "problem actions",
                ) { asking ->
                    if (!asking) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CalinoChip(
                                text = if (dead) "Try again" else "Retry now",
                                selected = true,
                                description = "send $title to the server again",
                                onClick = onRetry,
                            )
                            CalinoChip(
                                text = revertLabel(change),
                                selected = false,
                                description = "undo this unsynced change to $title",
                                onClick = { confirming = true },
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                discardWarning(change),
                                style = CalinoTypography.bodySmall,
                                color = CalinoColors.Rose,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CalinoChip(
                                    text = revertLabel(change),
                                    selected = false,
                                    description = "confirm, this cannot be undone",
                                    onClick = {
                                        confirming = false
                                        onDiscard()
                                    },
                                )
                                CalinoChip(
                                    text = "Keep it",
                                    selected = true,
                                    description = "keep the change queued",
                                    onClick = { confirming = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuedRow(change: PendingChange, collection: CollectionLabel?, waitingBehind: Boolean) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionDot(collection)
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(itemTitle(change), style = CalinoTypography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (waitingBehind) "${metaLine(change, collection)} · after the change above" else metaLine(change, collection),
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusTag("Queued", CalinoColors.Ink3)
    }
}

@Composable
private fun DisclosureRow(text: String, expanded: Boolean, onToggle: () -> Unit) {
    val chevron by animateFloatAsState(
        if (expanded) 90f else 0f,
        tween(CalinoMotion.ContentEnterMillis),
        label = "queued chevron",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(CalinoShapes.Row))
            .calinoPressable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = text
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.weight(1f))
        Icon(
            CalinoIcons.ChevronRight,
            contentDescription = null,
            tint = CalinoColors.Ink3,
            modifier = Modifier.size(18.dp).rotate(chevron),
        )
    }
}

@Composable
private fun CollectionDot(collection: CollectionLabel?) {
    Box(
        Modifier.size(10.dp).clip(CircleShape).background(collection?.color ?: CalinoColors.Ink3.copy(alpha = .5f)),
    )
}

/** A non-interactive state tag, sized to sit at the end of a row. */
@Composable
private fun StatusTag(text: String, tone: Color) {
    val tint by animateColorAsState(tone, tween(CalinoMotion.SurfaceFadeMillis), label = "status tag")
    Box(
        Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(CalinoShapes.Pill))
            .background(tint.copy(alpha = .12f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = tint, maxLines = 1)
    }
}

private fun collection(collections: Map<String, CollectionLabel>, change: PendingChange): CollectionLabel? =
    change.calendarUrl?.let(collections::get) ?: collections[change.calendarId]

private fun itemTitle(change: PendingChange): String =
    change.displayTitle() ?: "Untitled ${kindLabel(change)}"

private fun plural(count: Int) = if (count == 1) "" else "s"

private fun kindLabel(change: PendingChange): String = when (change.component.uppercase()) {
    "VEVENT" -> "event"
    "VTODO" -> "task"
    "VJOURNAL" -> "journal entry"
    "VCARD" -> "contact"
    else -> "item"
}

private fun metaLine(change: PendingChange, collection: CollectionLabel?): String {
    val kind = kindLabel(change)
    val action = when (change.type) {
        PendingChangeType.CREATE -> "New $kind"
        PendingChangeType.UPDATE -> "Edited $kind"
        PendingChangeType.DELETE -> "Deleted $kind"
        PendingChangeType.MOVE -> "Moved $kind"
        PendingChangeType.DELETE_HREF -> "Finishing a move"
    }
    return listOfNotNull(action, collection?.name).joinToString(" · ")
}

/** e.g. "HTTP 415 · ATTEMPT 3 OF 10 · LAST TRIED 17:52", set in the mono label face. */
private fun failureDetail(change: PendingChange, timeFormat: CalinoTimeFormat): String? {
    val parts = buildList {
        change.lastFailure?.statusCode?.let { add("HTTP $it") }
        if (change.retryCount > 0 && change.state == PendingChangeState.RETRY) {
            add("attempt ${change.retryCount} of $PENDING_CHANGE_MAX_RETRIES")
        }
        change.lastFailure?.let { add("last tried ${formatStamp(it.at, timeFormat)}") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.uppercase()
}

private fun formatStamp(instant: Instant, timeFormat: CalinoTimeFormat): String {
    val local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val time = timeFormat.format(local.toLocalTime())
    return if (local.toLocalDate() == LocalDate.now()) time else "${local.toLocalDate()} $time"
}

private fun revertLabel(change: PendingChange): String = when (change.type) {
    PendingChangeType.CREATE -> "Delete it"
    else -> "Revert"
}

private fun discardWarning(change: PendingChange): String = when (change.type) {
    PendingChangeType.CREATE -> "The new ${kindLabel(change)} is removed from this device. It was never saved on the server."
    PendingChangeType.DELETE -> "The ${kindLabel(change)} comes back as it is on the server."
    PendingChangeType.DELETE_HREF ->
        "The copy in the old calendar stays on the server, so the item may appear twice until you delete one."
    PendingChangeType.MOVE -> "The ${kindLabel(change)} stays in its original calendar."
    PendingChangeType.UPDATE -> "Your unsynced edits are lost and the server version comes back."
}
