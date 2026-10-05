package calino.malinov.ski.ui.components

import calino.malinov.ski.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import kotlinx.coroutines.delay

/** What the prompt is about: a parent just completed, and its subtasks still open. */
data class SubtaskCompletionRequest(val parent: CalTask, val open: List<CalTask>)

private const val VisibleSubtasks = 4
private const val CheckStaggerMillis = 70L
private const val CheckSettleMillis = 260L

/**
 * Asks whether to finish a completed parent's open subtasks. It rises from
 * where the add pill sits, lists the subtasks with their own rings, and on
 * "Mark done" ticks them off one after another before it leaves, so the
 * answer is seen landing rather than only reported.
 *
 * Drawn inside the root Box, over everything; dismissing it by the scrim or
 * "Leave open" leaves the subtasks alone.
 */
@Composable
fun BoxScope.SubtaskCompletionPrompt(
    request: SubtaskCompletionRequest?,
    onLeaveOpen: () -> Unit,
    onMarkDone: (List<CalTask>) -> Unit,
) {
    // Kept through the exit so the card does not empty as it slides away.
    var shown by remember { mutableStateOf(request) }
    if (request != null) shown = request
    var ticking by remember(request) { mutableStateOf(false) }

    CalinoScrim(visible = request != null, modifier = Modifier.matchParentSize(), onDismiss = {
        if (!ticking) onLeaveOpen()
    })
    AnimatedVisibility(
        visible = request != null,
        enter = slideInVertically(spring(dampingRatio = .86f, stiffness = 420f)) { it / 3 } +
            fadeIn(tween(CalinoMotion.ContentEnterMillis)),
        exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 520f)) { it / 4 } +
            fadeOut(tween(CalinoMotion.ContentExitMillis)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            // Sits above the add pill rather than over it: the pill keeps
            // narrating the parent's completion underneath.
            .padding(start = 12.dp, end = 12.dp, bottom = CalinoSpacing.PillClearance - 8.dp),
    ) {
        val current = shown ?: return@AnimatedVisibility
        LaunchedEffect(ticking) {
            if (!ticking) return@LaunchedEffect
            delay(CheckStaggerMillis * current.open.size.coerceAtMost(VisibleSubtasks) + CheckSettleMillis)
            onMarkDone(current.open)
        }
        PromptCard(current, ticking, onLeaveOpen = onLeaveOpen, onMarkDone = { ticking = true })
    }
}

@Composable
private fun PromptCard(
    request: SubtaskCompletionRequest,
    ticking: Boolean,
    onLeaveOpen: () -> Unit,
    onMarkDone: () -> Unit,
) {
    val count = request.open.size
    val promptTitle = stringResource(R.string.cal_mark_subtasks_done_question)
    val openSummary = if (count == 1) stringResource(R.string.cal_one_subtask_open)
        else pluralStringResource(R.plurals.cal_subtasks_open, count, count)
    val leaveOpenLabel = stringResource(R.string.cal_leave_open)
    val markDoneLabel = stringResource(R.string.cal_mark_done)
    Column(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(CalinoShapes.Sheet), clip = false)
            .clip(RoundedCornerShape(CalinoShapes.Sheet))
            .background(CalinoColors.Panel)
            .semantics { paneTitle = promptTitle }
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp),
    ) {
        // The parent, already finished: the reason for the question.
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskRing(color = eventColor(request.parent.color), checked = true, size = 16.dp)
            Spacer(Modifier.size(8.dp))
            Text(
                request.parent.title,
                style = CalinoTypography.bodyMedium,
                color = CalinoColors.Ink3,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(promptTitle, style = CalinoTypography.titleLarge, color = CalinoColors.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            openSummary,
            style = CalinoTypography.bodyMedium,
            color = CalinoColors.Ink2,
        )
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CalinoShapes.Row))
                .background(CalinoColors.Ink.copy(alpha = .035f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            request.open.take(VisibleSubtasks).forEachIndexed { index, task ->
                SubtaskLine(task, ticking, delayMillis = CheckStaggerMillis * index)
            }
            if (count > VisibleSubtasks) {
                Text(
                    pluralStringResource(R.plurals.cal_more_count, count - VisibleSubtasks, count - VisibleSubtasks),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                    modifier = Modifier.padding(start = 30.dp, top = 2.dp, bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PromptButton(leaveOpenLabel, filled = false, enabled = !ticking, onClick = onLeaveOpen, modifier = Modifier.weight(1f))
            PromptButton(markDoneLabel, filled = true, enabled = !ticking, onClick = onMarkDone, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SubtaskLine(task: CalTask, ticking: Boolean, delayMillis: Long) {
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(ticking) {
        if (!ticking) return@LaunchedEffect
        delay(delayMillis)
        checked = true
    }
    val ink by animateColorAsState(
        if (checked) CalinoColors.Ink3 else CalinoColors.Ink,
        tween(CalinoMotion.SurfaceFadeMillis),
        label = "subtask line ink",
    )
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskRing(color = eventColor(task.color), checked = checked, size = 18.dp)
        Spacer(Modifier.size(12.dp))
        Text(
            task.title,
            style = CalinoTypography.bodyLarge,
            color = ink,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The task list's completion ring, small: hollow while open, filled with a check once done. */
@Composable
private fun TaskRing(color: Color, checked: Boolean, size: Dp) {
    val fill by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = .6f, stiffness = 700f),
        label = "task ring fill",
    )
    Box(
        Modifier
            .size(size)
            .border(1.5.dp, color, CircleShape)
            .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { scaleX = fill; scaleY = fill; alpha = fill.coerceIn(0f, 1f) }
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CalinoIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * .62f))
        }
    }
}

private fun Modifier.matchParentSize(): Modifier = this.then(Modifier.fillMaxWidth().height(androidx.compose.ui.unit.Dp.Unspecified))

@Composable
internal fun PromptButton(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(CalinoShapes.Pill))
            .background(if (filled) CalinoColors.FloatFill else CalinoColors.Ink.copy(alpha = .06f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = CalinoTypography.labelLarge,
            color = if (filled) CalinoColors.OnFloat else CalinoColors.Ink,
        )
    }
}
