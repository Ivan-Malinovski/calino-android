package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.R
import calino.malinov.ski.data.repository.TrashEntry
import calino.malinov.ski.data.repository.TrashKind
import calino.malinov.ski.data.repository.TrashStore
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.SectionLabel
import java.time.Duration
import java.time.Instant

/** How soon before permanent removal a row starts to say so in rose. */
private const val LastDays = 3

/**
 * Everything deleted in the last 30 days that Calino still holds, newest
 * first and grouped by how long ago. The copy is local to the phone and goes
 * with the account.
 */
@Composable
fun RecentlyDeletedSurface(
    items: List<TrashEntry>,
    onRestore: (TrashEntry) -> Unit,
    onDeleteForever: (TrashEntry) -> Unit,
    onEmpty: () -> Unit,
    now: Instant = Instant.now(),
) {
    var confirmEmpty by remember { mutableStateOf(false) }
    val groups = remember(items, now) {
        TrashAge.entries.mapNotNull { age ->
            val inGroup = items.filter { TrashAge.of(ageInDays(it, now)) == age }
            if (inGroup.isEmpty()) null else age to inGroup
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(CalinoColors.Canvas),
        contentPadding = PaddingValues(
            start = CalinoSpacing.Screen,
            end = CalinoSpacing.Screen,
            top = CalinoSpacing.Screen,
            bottom = CalinoSpacing.PillClearance,
        ),
    ) {
        item(key = "header") {
            Column {
                Text(t(R.string.set_recently_deleted), style = CalinoTypography.displayLarge)
                Text(
                    t(R.string.set_recently_deleted_note, TrashStore.Retention.toDays().toInt()),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") { EmptyTrash(Modifier.animateItem()) }
            return@LazyColumn
        }
        groups.forEach { (age, inGroup) ->
            item(key = "group-${age.name}") {
                SectionLabel(t(age.label), inGroup.size, Modifier.animateItem())
            }
            items(inGroup, key = { it.id }) { entry ->
                TrashCard(
                    entry = entry,
                    now = now,
                    onRestore = { onRestore(entry) },
                    onDeleteForever = { onDeleteForever(entry) },
                    modifier = Modifier.animateItem().padding(bottom = 8.dp),
                )
            }
        }
        item(key = "empty-all") {
            EmptyAll(
                confirming = confirmEmpty,
                onAsk = { confirmEmpty = true },
                onCancel = { confirmEmpty = false },
                onConfirm = {
                    confirmEmpty = false
                    onEmpty()
                },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private enum class TrashAge(val label: Int) {
    Today(R.string.set_trash_group_today),
    Week(R.string.set_trash_group_week),
    Earlier(R.string.set_trash_group_earlier),
    ;

    companion object {
        fun of(days: Int): TrashAge = when {
            days <= 0 -> Today
            days < 7 -> Week
            else -> Earlier
        }
    }
}

private fun ageInDays(entry: TrashEntry, now: Instant): Int =
    Duration.between(entry.deletedAt, now).toDays().toInt().coerceAtLeast(0)

private data class KindStyle(val label: Int, val icon: ImageVector, val color: Color)

@Composable
private fun kindStyle(kind: TrashKind): KindStyle = when (kind) {
    TrashKind.Event -> KindStyle(R.string.set_trash_kind_event, CalinoIcons.Calendar, CalinoColors.Accent)
    TrashKind.Task -> KindStyle(R.string.set_trash_kind_task, CalinoIcons.CheckSquare, CalinoColors.Green)
    TrashKind.Journal -> KindStyle(R.string.set_trash_kind_journal, CalinoIcons.BookOpen, CalinoColors.Plum)
    TrashKind.Contact -> KindStyle(R.string.set_trash_kind_contact, CalinoIcons.Users, CalinoColors.Blue)
}

/**
 * One deleted item: its kind in colour on the edge and the chip, what it was,
 * when it goes for good, and the two things you can do about it. Restore is
 * the primary action; deleting forever is quieter and sits beside it.
 */
@Composable
private fun TrashCard(
    entry: TrashEntry,
    now: Instant,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = kindStyle(entry.kind)
    val kind = t(style.label)
    val days = ageInDays(entry, now)
    val age = when (days) {
        0 -> t(R.string.set_deleted_today)
        1 -> t(R.string.set_deleted_yesterday)
        else -> t(R.string.set_deleted_days_ago, days)
    }
    val left = (TrashStore.Retention.toDays().toInt() - days).coerceAtLeast(1)
    val leftLabel = if (left == 1) t(R.string.set_trash_left_one) else t(R.string.set_trash_left_many, left)
    val title = entry.title.ifBlank { t(R.string.set_untitled) }
    val shape = RoundedCornerShape(CalinoShapes.Card)
    val summary = "$title, ${t(R.string.set_1_s_2_s, kind, age)}, $leftLabel"
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CalinoColors.Panel, shape)
            .border(1.dp, style.color.copy(alpha = .16f), shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = summary
                }
                .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(style.color.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                Text(
                    title,
                    style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                )
                Text(
                    t(R.string.set_1_s_2_s, kind, age),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Text(
                leftLabel.uppercase(),
                color = if (left <= LastDays) CalinoColors.Rose else CalinoColors.Ink3,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = .8.sp,
                textAlign = TextAlign.End,
                softWrap = false,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line2))
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            CardAction(
                label = t(R.string.set_restore),
                color = CalinoColors.Accent,
                description = t(R.string.set_restore_named, title),
                onClick = onRestore,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(1.dp).height(20.dp).background(CalinoColors.Line2))
            CardAction(
                label = t(R.string.set_delete_forever),
                color = CalinoColors.Rose.copy(alpha = .85f),
                description = t(R.string.set_delete_forever_named, title),
                onClick = onDeleteForever,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CardAction(
    label: String,
    color: Color,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Medium,
) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CalinoTypography.bodyMedium.copy(fontWeight = weight), color = color, maxLines = 1)
    }
}

@Composable
private fun EmptyTrash(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(CalinoColors.Line2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CalinoIcons.Trash, contentDescription = null, tint = CalinoColors.Ink3, modifier = Modifier.size(24.dp))
        }
        Text(
            t(R.string.set_nothing_deleted_recently),
            style = CalinoTypography.bodyLarge,
            color = CalinoColors.Ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * Emptying everything is one outlined button that turns, in place, into a
 * short question rather than opening a dialog.
 */
@Composable
private fun EmptyAll(
    confirming: Boolean,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CalinoShapes.Card)
    AnimatedContent(
        targetState = confirming,
        modifier = modifier.fillMaxWidth().animateContentSize(tween(CalinoMotion.ContentEnterMillis)),
        transitionSpec = {
            fadeIn(tween(CalinoMotion.ContentEnterMillis)) togetherWith fadeOut(tween(CalinoMotion.FadeThroughMillis))
        },
        label = "empty trash confirm",
    ) { asking ->
        if (asking) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(CalinoColors.Panel, shape)
                    .border(BorderStroke(1.dp, CalinoColors.Rose.copy(alpha = .3f)), shape),
            ) {
                Text(
                    t(R.string.set_delete_all_forever),
                    style = CalinoTypography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line2))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CardAction(
                        label = t(R.string.set_cancel),
                        color = CalinoColors.Ink2,
                        description = t(R.string.set_cancel),
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.width(1.dp).height(20.dp).background(CalinoColors.Line2))
                    CardAction(
                        label = t(R.string.set_delete_all),
                        color = CalinoColors.Rose,
                        description = t(R.string.set_delete_all),
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        weight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .border(BorderStroke(1.dp, CalinoColors.Rose.copy(alpha = .3f)), shape)
                    .clickable(role = Role.Button, onClick = onAsk),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(CalinoIcons.Trash, contentDescription = null, tint = CalinoColors.Rose, modifier = Modifier.size(15.dp))
                    Text(
                        t(R.string.set_empty_recently_deleted),
                        style = CalinoTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = CalinoColors.Rose,
                    )
                }
            }
        }
    }
}

@Composable
private fun t(id: Int, vararg args: Any): String = stringResource(id, *args)
