package calino.malinov.ski.ui.surfaces

import androidx.compose.ui.res.stringResource
import calino.malinov.ski.R

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
import androidx.compose.ui.platform.LocalContext
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
import calino.malinov.ski.util.LocalCalinoLocale
import calino.malinov.ski.util.localizedPendingFailure
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
                text = t(
                    if (queued.size == 1) R.string.set_1_more_waiting_to_sync
                    else R.string.set_1_d_more_waiting_to_sync,
                    queued.size,
                ),
                expanded = showQueued,
                onToggle = { showQueued = !showQueued },
            )
        } else {
            SectionLabel(t(R.string.set_waiting), count = queued.size)
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
        needsYou == 1 -> t(R.string.set_1_d_change_needs_you, needsYou)
        needsYou > 0 -> t(R.string.set_1_d_changes_need_you, needsYou)
        retrying == 1 -> t(R.string.set_1_d_change_retrying, retrying)
        retrying > 0 -> t(R.string.set_1_d_changes_retrying, retrying)
        queued == 1 -> t(R.string.set_1_d_change_waiting_to_sync, queued)
        else -> t(R.string.set_1_d_changes_waiting_to_sync, queued)
    }
    val subtitle = when {
        needsYou == 1 -> t(R.string.set_the_server_refused_it_everything_else_keeps_syncing)
        needsYou > 0 -> t(R.string.set_the_server_refused_them_everything_else_keeps_syncing)
        retrying > 0 -> t(R.string.set_calino_will_try_again_on_its_own_nothing_is_lost)
        else -> t(R.string.set_they_sync_on_their_own_once_the_server_is_reachable)
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
            text = t(R.string.set_retry_all_now),
            selected = true,
            description = t(R.string.set_try_every_failed_change_again),
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
    val context = LocalContext.current
    val locale = LocalCalinoLocale
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
    val rowStateDescription = if (expanded) t(R.string.set_expanded) else t(R.string.set_collapsed)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(CalinoShapes.Row))
                .calinoPressable(role = Role.Button) { expanded = !expanded }
                .semantics(mergeDescendants = true) {
                    stateDescription = rowStateDescription
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
                    t(R.string.set_needs_you)
                } else {
                    change.nextAttemptAt?.let { t(R.string.set_retry_1_s, formatStamp(it, timeFormat, locale)) } ?: t(R.string.set_retrying)
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
                        Text(localizedPendingFailure(context, failure.message), style = CalinoTypography.bodySmall, color = CalinoColors.Ink2)
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
                                text = if (dead) t(R.string.set_try_again) else t(R.string.set_retry_now),
                                selected = true,
                                description = t(R.string.set_send_1_s_to_the_server_again, title),
                                onClick = onRetry,
                            )
                            CalinoChip(
                                text = revertLabel(change),
                                selected = false,
                                description = t(R.string.set_undo_this_unsynced_change_to_1_s, title),
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
                                    description = t(R.string.set_confirm_this_cannot_be_undone),
                                    onClick = {
                                        confirming = false
                                        onDiscard()
                                    },
                                )
                                CalinoChip(
                                    text = t(R.string.set_keep_it),
                                    selected = true,
                                    description = t(R.string.set_keep_the_change_queued),
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
                if (waitingBehind) t(
                    R.string.set_1_s_2_s,
                    metaLine(change, collection),
                    t(R.string.set_after_the_change_above),
                ) else metaLine(change, collection),
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusTag(t(R.string.set_queued), CalinoColors.Ink3)
    }
}

@Composable
private fun DisclosureRow(text: String, expanded: Boolean, onToggle: () -> Unit) {
    val chevron by animateFloatAsState(
        if (expanded) 90f else 0f,
        tween(CalinoMotion.ContentEnterMillis),
        label = "queued chevron",
    )
    val rowStateDescription = if (expanded) t(R.string.set_expanded) else t(R.string.set_collapsed)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(CalinoShapes.Row))
            .calinoPressable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = text
                stateDescription = rowStateDescription
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

@Composable
private fun itemTitle(change: PendingChange): String =
    change.displayTitle() ?: t(R.string.set_untitled_1_s, kindLabel(change))

@Composable
private fun kindLabel(change: PendingChange): String = when (change.component.uppercase()) {
    "VEVENT" -> t(R.string.set_event)
    "VTODO" -> t(R.string.set_task)
    "VJOURNAL" -> t(R.string.set_journal_entry)
    "VCARD" -> t(R.string.set_contact)
    else -> t(R.string.set_item)
}

@Composable
private fun metaLine(change: PendingChange, collection: CollectionLabel?): String {
    val kind = kindLabel(change)
    val action = when (change.type) {
        PendingChangeType.CREATE -> t(R.string.set_new_1_s, kind)
        PendingChangeType.UPDATE -> t(R.string.set_edited_1_s, kind)
        PendingChangeType.DELETE -> t(R.string.set_deleted_1_s, kind)
        PendingChangeType.MOVE -> t(R.string.set_moved_1_s, kind)
        PendingChangeType.DELETE_HREF -> t(R.string.set_finishing_a_move)
    }
    return listOfNotNull(action, collection?.name).joinToString(" · ")
}

/** e.g. "HTTP 415 · ATTEMPT 3 OF 10 · LAST TRIED 17:52", set in the mono label face. */
@Composable
private fun failureDetail(change: PendingChange, timeFormat: CalinoTimeFormat): String? {
    val locale = LocalCalinoLocale
    val parts = buildList {
        change.lastFailure?.statusCode?.let { add(t(R.string.set_http_1_d, it)) }
        if (change.retryCount > 0 && change.state == PendingChangeState.RETRY) {
            add(t(R.string.set_attempt_1_d_of_2_d, change.retryCount, PENDING_CHANGE_MAX_RETRIES))
        }
        change.lastFailure?.let { add(t(R.string.set_last_tried_1_s, formatStamp(it.at, timeFormat, locale))) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.uppercase(locale)
}

private fun formatStamp(instant: Instant, timeFormat: CalinoTimeFormat, locale: java.util.Locale): String {
    val local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val time = timeFormat.format(local.toLocalTime(), locale)
    return if (local.toLocalDate() == LocalDate.now()) time else "${local.toLocalDate()} $time"
}

@Composable
private fun revertLabel(change: PendingChange): String = when (change.type) {
    PendingChangeType.CREATE -> t(R.string.set_delete_it)
    else -> t(R.string.set_revert)
}

@Composable
private fun discardWarning(change: PendingChange): String = when (change.type) {
    PendingChangeType.CREATE -> t(R.string.set_the_new_1_s_is_removed_from_this_device_it_was_never_saved_o, kindLabel(change))
    PendingChangeType.DELETE -> t(R.string.set_the_1_s_comes_back_as_it_is_on_the_server, kindLabel(change))
    PendingChangeType.DELETE_HREF ->
        t(R.string.set_the_copy_in_the_old_calendar_stays_on_the_server_so_the_item)
    PendingChangeType.MOVE -> t(R.string.set_the_1_s_stays_in_its_original_calendar, kindLabel(change))
    PendingChangeType.UPDATE -> t(R.string.set_your_unsynced_edits_are_lost_and_the_server_version_comes_ba)
}
@Composable
private fun t(id: Int, vararg args: Any): String = stringResource(id, *args)
