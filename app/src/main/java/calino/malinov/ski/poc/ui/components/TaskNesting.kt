package calino.malinov.ski.poc.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import calino.malinov.ski.poc.design.CalinoColors

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
 */
@Composable
fun Modifier.taskNestIndent(
    depth: Int,
    nestingLines: List<Boolean> = emptyList(),
    drawRails: Boolean = true,
    elbowInset: Dp = 0.dp,
): Modifier {
    if (depth <= 0) return this
    val color = CalinoColors.Ink.copy(alpha = .10f)
    val density = LocalDensity.current
    val stepPx = with(density) { TaskNestStep.dp.toPx() }
    val strokePx = with(density) { 1.dp.toPx() }
    val elbowEndPx = with(density) { (depth * TaskNestStep).dp.toPx() + elbowInset.toPx() }
    // Drawn before the indent padding so the rails land in the gutter the card
    // has been pushed out of.
    return this
        .drawBehind { if (drawRails) drawNestRails(depth, nestingLines, color, stepPx, strokePx, elbowEndPx) }
        .padding(start = (depth * TaskNestStep).dp)
}

private fun DrawScope.drawNestRails(
    depth: Int,
    nestingLines: List<Boolean>,
    color: Color,
    stepPx: Float,
    strokePx: Float,
    elbowEndPx: Float,
) {
    if (depth <= 0) return
    val centerY = size.height / 2f
    for (level in 0 until depth) {
        val x = level * stepPx + stepPx / 2f
        val continues = nestingLines.getOrElse(level) { false }
        val own = level == depth - 1
        val endY = if (own && !continues) centerY else size.height
        drawLine(color, Offset(x, 0f), Offset(x, endY), strokePx, StrokeCap.Round)
        if (own) {
            drawLine(color, Offset(x, centerY), Offset(elbowEndPx, centerY), strokePx, StrokeCap.Round)
        }
    }
}
