package calino.malinov.ski.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp

/** Draw-only geometry leaves the gesture's full-size hit path intact as the month shrinks. */
internal fun ContentDrawScope.drawCalendarIntoYearTile(
    tile: Rect,
    page: Rect,
    progress: Float,
    cornerPx: Float,
) {
    val width = lerp(page.width, tile.width, progress).coerceAtLeast(1f)
    val height = lerp(page.height, tile.height, progress).coerceAtLeast(1f)
    val scale = width / page.width.coerceAtLeast(1f)
    val clip = Path().apply {
        addRoundRect(RoundRect(
            0f, 0f, size.width, (height / scale).coerceAtMost(size.height),
            CornerRadius(cornerPx * progress / scale),
        ))
    }
    withTransform({
        translate((tile.left - page.left) * progress, (tile.top - page.top) * progress)
        scale(scale, scale, Offset.Zero)
    }) {
        clipPath(clip) { this@drawCalendarIntoYearTile.drawContent() }
    }
}

/** Requires an isolated layer so the alpha mask cannot erase the Agenda underneath. */
internal fun ContentDrawScope.drawCalendarWithSoftAgendaEdge(header: Float, progress: Float) {
    if (progress <= 0f) {
        drawContent()
        return
    }
    val remaining = 1f - progress
    val edge = header + (size.height - header) * remaining * remaining * remaining
    // Feather both sides of the travelling edge, tapering at either endpoint
    // to preserve the complete resting surface and the anchored heading.
    val feather = 56.dp.toPx() * (progress / .1f).coerceIn(0f, 1f) * (remaining / .15f).coerceIn(0f, 1f)
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startY = (edge - feather / 2f).coerceAtLeast(header),
            endY = (edge + feather / 2f).coerceAtMost(size.height).coerceAtLeast(header + .01f),
        ),
        blendMode = BlendMode.DstIn,
    )
}
