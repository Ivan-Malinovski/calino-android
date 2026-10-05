package calino.malinov.ski.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.state.SplitPaneWidthDp

/** Shared resting width; fold geometry may further transform it in Month. */
@Composable
fun animatedDayPaneWidth(collapsed: Boolean): Dp = animatedDayPaneWidthState(collapsed).value

@Composable
internal fun animatedDayPaneWidthState(collapsed: Boolean): State<Dp> =
    animateDpAsState(
        targetValue = if (collapsed) 0.dp else SplitPaneWidthDp.dp,
        animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
        label = "day pane width",
    )

/**
 * The rule between the two panes, doubling as the pane's collapse control.
 * The painted chevron is compact; the touch lane around it is not.
 */
@Composable
fun DayPaneDivider(collapsed: Boolean, onToggle: () -> Unit) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) 180f else 0f,
        animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
        label = "day pane chevron",
    )
    val label = if (collapsed) "Show day pane" else "Hide day pane"
    Box(
        Modifier.fillMaxHeight().width(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(CalinoColors.Line).align(Alignment.Center))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CalinoColors.Canvas)
                .calinoPressable(onClick = onToggle)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "\u203A",
                color = CalinoColors.Ink3,
                fontSize = 20.sp,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
    }
}
