package calino.malinov.ski.poc.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.state.CalinoSurfaceKind
import calino.malinov.ski.poc.state.CalinoSurfaceMode

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
    content: @Composable (Modifier) -> Unit,
) {
    AdaptiveSurfaceHost(
        kind = surfaceKind,
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentDescription = "Dismiss detail card",
    ) { overlayModifier ->
        val mode = LocalCalinoSurfaceMode.current
        Box(overlayModifier.padding(horizontal = if (mode == CalinoSurfaceMode.BottomSheet) 10.dp else 0.dp)) {
            AdaptiveDetailCard(
                visible = visible,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
                dismissDistance = dismissDistance,
                resetKey = resetKey,
                canStartDismiss = canStartDismiss,
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
    content: @Composable (Modifier) -> Unit,
) {
    AdaptiveSurfaceHost(
        kind = surfaceKind,
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentDescription = "Dismiss detail card",
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
            ) { dragModifier ->
                DetailCardSurface(dragModifier, handleColor = handleColor, content = content)
            }
        }
    }
}
