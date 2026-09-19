package calino.malinov.ski.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.state.CalinoSurfaceMode

/** The child owns finger tracking; this host owns entry and final removal motion. */
@Composable
fun BottomDetailCard(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissDistance: androidx.compose.ui.unit.Dp = 980.dp,
    resetKey: Any? = null,
    canStartDismiss: () -> Boolean = { true },
    surfaceKind: CalinoSurfaceKind = CalinoSurfaceKind.Detail,
    handleColor: Color = CalinoColors.Canvas,
    // The card's action pill, hosted in the pill lane rather than in the card,
    // so it can change shape in place instead of leaving with the card.
    pill: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    AdaptiveSurfaceHost(
        kind = surfaceKind,
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentDescription = "Dismiss detail card",
        pill = pill,
    ) { overlayModifier ->
        val mode = LocalCalinoSurfaceMode.current
        // The end-panel host reserves a shadow bleed on every side; paying it
        // back here keeps the card where it was and leaves the bleed free for
        // the card's own shadow.
        Box(
            overlayModifier.calinoSurfaceShadowBleed(
                horizontal = if (mode == CalinoSurfaceMode.BottomSheet) 10.dp else 0.dp,
            ),
        ) {
            AdaptiveDetailCard(
                visible = visible,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
                dismissDistance = dismissDistance,
                resetKey = resetKey,
                canStartDismiss = canStartDismiss,
                handleColor = handleColor,
                content = content,
            )
        }
    }
}

/** Shared backdrop and adaptive motion; event paging places a complete card on each page. */
@Composable
fun BottomDetailOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceKind: CalinoSurfaceKind = CalinoSurfaceKind.Detail,
    preferredSurfaceHeight: androidx.compose.ui.unit.Dp? = null,
    pill: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    AdaptiveSurfaceHost(
        kind = surfaceKind,
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentDescription = "Dismiss detail card",
        preferredSurfaceHeight = preferredSurfaceHeight,
        pill = pill,
        content = content,
    )
}

/** Applies the mode-appropriate dismissal primitive and the shared card shell. */
@Composable
fun AdaptiveDetailCard(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissDistance: androidx.compose.ui.unit.Dp = 980.dp,
    resetKey: Any? = null,
    canStartDismiss: () -> Boolean = { true },
    allowDownwardDismissInEndPanel: Boolean = false,
    handleColor: Color = CalinoColors.Canvas,
    content: @Composable (Modifier) -> Unit,
) {
    when (LocalCalinoSurfaceMode.current) {
        CalinoSurfaceMode.BottomSheet -> SwipeDownDismiss(
            visible = visible,
            onDismiss = onDismiss,
            modifier = modifier,
            dismissDistance = dismissDistance,
            resetKey = resetKey,
            canStartDismiss = canStartDismiss,
        ) { dragModifier ->
            DetailCardSurface(dragModifier, handleColor = handleColor, content = content)
        }

        CalinoSurfaceMode.FloatingWindow -> SwipeDownDismiss(
            visible = visible,
            onDismiss = onDismiss,
            modifier = modifier,
            dismissDistance = dismissDistance,
            resetKey = resetKey,
            canStartDismiss = canStartDismiss,
        ) { dragModifier ->
            DetailCardSurface(dragModifier, handleColor = handleColor, content = content)
        }

        CalinoSurfaceMode.EndPanel -> {
            val headerLanePx = with(LocalDensity.current) { 72.dp.toPx() }
            SwipeEndDismiss(
                visible = visible,
                onDismiss = onDismiss,
                modifier = modifier,
                dismissDistance = dismissDistance,
                resetKey = resetKey,
                canStartDismiss = { position -> position.y <= headerLanePx },
                allowDownwardDismiss = allowDownwardDismissInEndPanel,
            ) { dragModifier ->
                DetailCardSurface(dragModifier, handleColor = handleColor, content = content)
            }
        }
    }
}
