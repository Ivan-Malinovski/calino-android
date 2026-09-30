package calino.malinov.ski.ui.range

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.design.*
import calino.malinov.ski.ui.surfaces.TaskActionMenu
import calino.malinov.ski.ui.surfaces.TaskMenuAction
import calino.malinov.ski.ui.components.eventColor
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.state.LocalTimeFormat

/** A deadline marker anchored at its due time; its hit lane has no duration semantics. */
@Composable
internal fun RangeTaskDeadline(
    task: CalTask, onOpen: (CalTask) -> Unit, onDone: (CalTask, Boolean) -> Unit,
    onAction: (TaskMenuAction, CalTask) -> Unit, modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val color = eventColor(task.color)
    val shape = RoundedCornerShape(8.dp)
    val dueLabel = LocalTimeFormat.format(task.dueTime!!)
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp)
        .combinedClickable(onClick = { onOpen(task) }, onLongClick = { menu = true })
        .semantics { contentDescription = "${task.title}, due $dueLabel" }) {
        val wide = maxWidth >= 120.dp
        // Paint a small badge on the deadline, leaving the rest of its touch
        // lane transparent so it cannot read as a forty-minute task block.
        Row(Modifier.fillMaxWidth().height(24.dp)
            .background(CalinoColors.Panel, shape).border(1.dp, color.copy(alpha = .5f), shape)
            .padding(start = 3.dp, end = if (wide) 44.dp else 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CalinoIcon(if (task.done) CalinoIcon.Check else CalinoIcon.Clock, tint = color, modifier = Modifier.size(12.dp))
            Text(task.title, modifier = Modifier.padding(start = 3.dp),
                style = if (wide) CalinoTypography.bodySmall else CalinoTypography.bodySmall.copy(fontSize = 10.sp),
                color = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.done) TextDecoration.LineThrough else null)
        }
        if (wide) Box(Modifier.align(Alignment.TopEnd).size(44.dp).clickable { onDone(task, !task.done) }
            .semantics { contentDescription = if (task.done) "Mark ${task.title} open" else "Complete ${task.title}" }, contentAlignment = Alignment.Center) {
            CalinoIcon(if (task.done) CalinoIcon.Check else CalinoIcon.Clock, tint = color, modifier = Modifier.size(16.dp))
        }
        TaskActionMenu(task, expanded = menu, onDismiss = { menu = false }, onAction = { onAction(it, task) })
    }
}
