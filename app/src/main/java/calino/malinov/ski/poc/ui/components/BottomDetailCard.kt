package calino.malinov.ski.poc.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion

/** The child owns finger tracking; this host owns entry and final removal motion. */
@Composable
fun BottomDetailCard(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissDistance: androidx.compose.ui.unit.Dp = 980.dp,
    resetKey: Any? = null,
    content: @Composable (Modifier) -> Unit,
) {
    BottomDetailOverlay(visible, onDismiss, modifier) { overlayModifier ->
        Box(overlayModifier.padding(horizontal = 10.dp)) {
            SwipeDownDismiss(visible, onDismiss, Modifier.fillMaxSize(),
                dismissDistance = dismissDistance, resetKey = resetKey) { dragModifier ->
                DetailCardSurface(dragModifier, content = content)
            }
        }
    }
}

/** Shared backdrop and vertical motion; event paging places a complete card on each page. */
@Composable
fun BottomDetailOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    BackHandler { if (visible) onDismiss() }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        if (entered && visible) 1f else 0f,
        tween(CalinoMotion.SurfaceFadeMillis), label = "bottom card slide",
    )
    // No imePadding here: the route host already pads by safeDrawing, which
    // includes the IME. Adding it again subtracted the keyboard twice and
    // crushed the card to its header and action row.
    BoxWithConstraints(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(CalinoColors.scrim(.28f * progress))
            .semantics { contentDescription = "Dismiss detail card" }
            .clickable(onClick = onDismiss))
        // A short landscape window has no room to spare for the peek of the
        // surface behind the card, so the card takes almost all of it.
        val cardHeight = maxHeight * if (maxHeight < 520.dp) .96f else .86f
        Box(Modifier.align(Alignment.BottomCenter).padding(vertical = 8.dp)
            .widthIn(max = 660.dp).fillMaxWidth().height(cardHeight)
            .graphicsLayer { translationY = (1f - progress) * (size.height + 32.dp.toPx()) }) {
            content(Modifier.fillMaxSize())
        }
    }
}

@Composable
fun DetailCardSurface(
    modifier: Modifier = Modifier,
    handleColor: Color = CalinoColors.Canvas,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(CalinoColors.Canvas)) {
        Box(Modifier.fillMaxWidth().height(24.dp).background(handleColor),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp))
                .background(CalinoColors.Ink.copy(alpha = .18f)))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize()) }
    }
}
