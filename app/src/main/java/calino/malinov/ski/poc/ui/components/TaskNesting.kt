package calino.malinov.ski.poc.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.design.CalinoColors
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Indent applied per subtask level, and the width of one connector rail slot. */
const val TaskNestStep = 20

/**
 * Insets a subtask row by its [depth] and draws the connectors in the gutter it
 * vacates: a rail for every ancestor level that still continues below this row,
 * and an elbow from this row's own level into the card edge.
 *
 * [nestingLines] carries, per ancestor level, whether that level has a further
 * row underneath -- see `nestingLinesFor`. Pass `drawRails = false` for a row
 * that is being carried by a drag, so its rails do not travel with the card.
 *
 * [elbowInset] runs the elbow that far past the indent, for a row drawn without
 * a card behind it: there the elbow has to reach the checkbox to connect to
 * anything, where a card's own edge is already something to meet.
 *
 * [railOffset] places the rails across the row. It defaults to the middle of
 * each indent step, which is where a card row wants them -- in the gutter, off
 * the card edge. A row without a card passes its checkbox centre instead, so
 * the line drops out of the parent's checkbox rather than hanging left of it.
 *
 * [railOverhang] carries the rails that far above the row, across the gap to
 * the row above, so a parent and its children read as one line.
 */
@Composable
fun Modifier.taskNestIndent(
    depth: Int,
    nestingLines: List<Boolean> = emptyList(),
    drawRails: Boolean = true,
    elbowInset: Dp = 0.dp,
    railOffset: Dp = Dp.Unspecified,
    railOverhang: Dp = 0.dp,
): Modifier {
    if (depth <= 0) return this
    val color = CalinoColors.Ink.copy(alpha = .10f)
    val density = LocalDensity.current
    val stepPx = with(density) { TaskNestStep.dp.toPx() }
    val strokePx = with(density) { 1.dp.toPx() }
    val elbowEndPx = with(density) { (depth * TaskNestStep).dp.toPx() + elbowInset.toPx() }
    val railOffsetPx = with(density) { if (railOffset.isSpecified) railOffset.toPx() else stepPx / 2f }
    val overhangPx = with(density) { railOverhang.toPx() }
    // Drawn before the indent padding so the rails land in the gutter the card
    // has been pushed out of.
    return this
        .drawBehind {
            if (drawRails) {
                drawNestRails(depth, nestingLines, color, stepPx, strokePx, elbowEndPx, railOffsetPx, overhangPx)
            }
        }
        .padding(start = (depth * TaskNestStep).dp)
}

private fun DrawScope.drawNestRails(
    depth: Int,
    nestingLines: List<Boolean>,
    color: Color,
    stepPx: Float,
    strokePx: Float,
    elbowEndPx: Float,
    railOffsetPx: Float,
    overhangPx: Float,
) {
    if (depth <= 0) return
    val centerY = size.height / 2f
    for (level in 0 until depth) {
        val x = level * stepPx + railOffsetPx
        val continues = nestingLines.getOrElse(level) { false }
        val own = level == depth - 1
        val endY = if (own && !continues) centerY else size.height
        drawLine(color, Offset(x, -overhangPx), Offset(x, endY), strokePx, StrokeCap.Round)
        if (own) {
            drawLine(color, Offset(x, centerY), Offset(elbowEndPx, centerY), strokePx, StrokeCap.Round)
        }
    }
}

/** Where the day and the title sit apart from the rail they hang off. */
private val AbsentParentGutter = 7.dp

/** How the stand-in names a parent's day: "Mon 21". */
private val AbsentParentDay = DateTimeFormatter.ofPattern("EEE d", Locale.US)

/**
 * The stand-in for a parent that is not on this list -- see
 * [calino.malinov.ski.poc.state.TaskListRow.AbsentParent].
 *
 * Deliberately not a card and not a row: one line of quiet text, with the rail
 * dropping out of it into the subtasks below exactly as it drops out of a real
 * parent. Giving it a card would put the parent on a day it is not due, which
 * is the thing this exists to avoid.
 *
 * [railOffset] is the same column the child rows draw their rails in, so the
 * caller passes whatever it passes them: the middle of the indent step for a
 * list of cards, the checkbox centre for the compact list.
 */
@Composable
fun AbsentParentRow(
    parent: CalTask,
    modifier: Modifier = Modifier,
    railOffset: Dp = (TaskNestStep / 2).dp,
    onClick: (() -> Unit)? = null,
) {
    val color = CalinoColors.Ink.copy(alpha = .10f)
    val density = LocalDensity.current
    val railOffsetPx = with(density) { railOffset.toPx() }
    val strokePx = with(density) { 1.dp.toPx() }
    Row(
        modifier
            .fillMaxWidth()
            // Only the lower half: the line starts where the title is and
            // travels down to meet the first child's elbow.
            .drawBehind {
                drawLine(
                    color,
                    Offset(railOffsetPx, size.height / 2f),
                    Offset(railOffsetPx, size.height),
                    strokePx,
                    StrokeCap.Round,
                )
            }
            .then(if (onClick != null) Modifier.calinoPressable(onClick = onClick) else Modifier)
            .heightIn(min = 24.dp)
            .padding(start = railOffset + AbsentParentGutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            parent.title,
            Modifier.weight(1f),
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = CalinoColors.Ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        parent.due?.let { due ->
            Text(
                due.format(AbsentParentDay),
                Modifier.padding(start = AbsentParentGutter),
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = CalinoColors.Ink3,
                maxLines = 1,
            )
        }
    }
}
