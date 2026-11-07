package calino.malinov.ski.ui.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calino.malinov.ski.R
import calino.malinov.ski.data.repository.TrashEntry
import calino.malinov.ski.data.repository.TrashKind
import calino.malinov.ski.data.repository.TrashStore
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoTypography
import java.time.Duration
import java.time.Instant

/**
 * Everything deleted in the last 30 days that Calino still holds, newest
 * first. The copy is local to the phone and goes with the account.
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
    Column(
        Modifier
            .fillMaxSize()
            .background(CalinoColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(t(R.string.set_recently_deleted), style = CalinoTypography.displayLarge)
        Text(
            t(R.string.set_recently_deleted_note, TrashStore.Retention.toDays().toInt()),
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        if (items.isEmpty()) {
            Text(
                t(R.string.set_nothing_deleted_recently),
                style = CalinoTypography.bodyLarge,
                color = CalinoColors.Ink3,
                modifier = Modifier.padding(top = 18.dp),
            )
            return@Column
        }
        items.forEach { entry ->
            TrashRow(entry, now, onRestore = { onRestore(entry) }, onDeleteForever = { onDeleteForever(entry) })
        }
        Spacer(Modifier.padding(top = 14.dp))
        if (confirmEmpty) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t(R.string.set_delete_all_forever),
                    style = CalinoTypography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextAction(t(R.string.set_cancel), CalinoColors.Ink2) { confirmEmpty = false }
                TextAction(t(R.string.set_delete_all), CalinoColors.Rose) {
                    confirmEmpty = false
                    onEmpty()
                }
            }
        } else {
            TextAction(t(R.string.set_empty_recently_deleted), CalinoColors.Rose) { confirmEmpty = true }
        }
        Spacer(Modifier.padding(top = 24.dp))
    }
}

@Composable
private fun TrashRow(entry: TrashEntry, now: Instant, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    val kind = t(
        when (entry.kind) {
            TrashKind.Event -> R.string.set_trash_kind_event
            TrashKind.Task -> R.string.set_trash_kind_task
            TrashKind.Journal -> R.string.set_trash_kind_journal
            TrashKind.Contact -> R.string.set_trash_kind_contact
        },
    )
    val days = Duration.between(entry.deletedAt, now).toDays().toInt().coerceAtLeast(0)
    val age = when (days) {
        0 -> t(R.string.set_deleted_today)
        1 -> t(R.string.set_deleted_yesterday)
        else -> t(R.string.set_deleted_days_ago, days)
    }
    val title = entry.title.ifBlank { t(R.string.set_untitled) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 2)
            Text(t(R.string.set_1_s_2_s, kind, age), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
        }
        val restoreLabel = t(R.string.set_restore_named, title)
        val deleteLabel = t(R.string.set_delete_forever_named, title)
        TextAction(t(R.string.set_restore), CalinoColors.Accent, restoreLabel, onRestore)
        TextAction(t(R.string.set_delete_forever), CalinoColors.Rose, deleteLabel, onDeleteForever)
    }
}

@Composable
private fun TextAction(label: String, color: androidx.compose.ui.graphics.Color, description: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (description != null) contentDescription = description }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CalinoTypography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = color)
    }
}

@Composable
private fun t(id: Int, vararg args: Any): String = stringResource(id, *args)
