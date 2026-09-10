package calino.malinov.ski.poc.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.state.CalinoSurfaceKind
import calino.malinov.ski.poc.state.CalinoSurfaceMode
import calino.malinov.ski.poc.state.calinoSurfaceModeFor
import calino.malinov.ski.poc.state.calinoWindowClassFor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The mode currently used by the nearest adaptive surface host. */
val LocalCalinoSurfaceMode = androidx.compose.runtime.staticCompositionLocalOf {
    CalinoSurfaceMode.BottomSheet
}

/**
 * Presents a transient surface according to the available window width.
 *
 * The host owns the scrim and enter/exit motion. Its child owns any content
 * scrolling and, when appropriate, the finger-follow dismissal translation.
 * Keeping those responsibilities separate prevents a settle animation from
 * fighting a child drag during the handoff.
 */
@Composable
fun AdaptiveSurfaceHost(
    kind: CalinoSurfaceKind,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = .28f,
    contentDescription: String = "Dismiss surface",
    content: @Composable (Modifier) -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mounted = true }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val windowClass = calinoWindowClassFor(maxWidth.value.roundToInt())
        val mode = calinoSurfaceModeFor(windowClass, kind)
        val scrimProgress by animateFloatAsState(
            targetValue = if (mounted && visible) scrimAlpha else 0f,
            animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
            label = "adaptive surface scrim",
        )
        val surfaceWidthCap = kind.widthCapDp.dp
        val surfaceHeightCap = kind.heightCapDp.dp
        val floatingWidth = minOf((maxWidth - 32.dp).coerceAtLeast(1.dp), surfaceWidthCap)
        val floatingHeight = minOf((maxHeight - 32.dp).coerceAtLeast(1.dp), surfaceHeightCap)
        val sideWidth by animateDpAsState(
            targetValue = minOf((maxWidth * .62f).coerceAtLeast(1.dp), surfaceWidthCap),
            animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
            label = "adaptive side panel width",
        )
        val sideHeight = (maxHeight - 24.dp).coerceAtLeast(1.dp)
        val bottomHeight = maxHeight * if (maxHeight < 520.dp) .96f else .86f

        Box(
            Modifier
                .fillMaxSize()
                .background(CalinoColors.scrim(scrimProgress))
                .clickable(enabled = visible, onClick = onDismiss)
                .semantics { this.contentDescription = contentDescription },
        )

        val enter = when (mode) {
            CalinoSurfaceMode.BottomSheet ->
                slideInVertically(tween(240)) { it } + fadeIn(tween(180))
            CalinoSurfaceMode.FloatingWindow ->
                scaleIn(tween(220), initialScale = .94f) + fadeIn(tween(180))
            CalinoSurfaceMode.EndPanel ->
                slideInHorizontally(tween(240)) { it } + fadeIn(tween(180))
        }
        val exit = when (mode) {
            CalinoSurfaceMode.BottomSheet ->
                slideOutVertically(tween(240)) { it } + fadeOut(tween(160))
            CalinoSurfaceMode.FloatingWindow ->
                scaleOut(tween(180), targetScale = .94f) + fadeOut(tween(150))
            CalinoSurfaceMode.EndPanel ->
                slideOutHorizontally(tween(220)) { it } + fadeOut(tween(150))
        }
        val panelModifier: Modifier = when (mode) {
            CalinoSurfaceMode.BottomSheet ->
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
                    .height(bottomHeight)
            CalinoSurfaceMode.FloatingWindow ->
                Modifier
                    .align(Alignment.Center)
                    .width(floatingWidth)
                    .height(floatingHeight)
            CalinoSurfaceMode.EndPanel ->
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 12.dp, horizontal = 12.dp)
                    .width(sideWidth)
                    .height(sideHeight)
        }

        AnimatedVisibility(
            visible = mounted && visible,
            enter = enter,
            exit = exit,
            modifier = Modifier.fillMaxSize(),
            label = "adaptive surface visibility",
        ) {
            // AnimatedVisibility is not a BoxScope, so alignment modifiers on
            // its direct child are only metadata. Give the panel a real Box
            // parent or CenterEnd/Center would be ignored and the child could
            // be measured at the full window width.
            Box(Modifier.fillMaxSize()) {
                Box(panelModifier) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalCalinoSurfaceMode provides mode,
                    ) {
                        content(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/**
 * A horizontal counterpart to [SwipeDownDismiss] for end-anchored panels.
 * [canStartDismiss] is evaluated at pointer-down so a caller can reserve a
 * header/handle lane when its body already owns a horizontal pager.
 */
@Composable
fun SwipeEndDismiss(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissThreshold: Dp = 112.dp,
    dismissDistance: Dp = 980.dp,
    resetKey: Any? = null,
    canStartDismiss: (Offset) -> Boolean = { true },
    content: @Composable (Modifier) -> Unit,
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentCanStartDismiss by rememberUpdatedState(canStartDismiss)
    var dismissing by remember { mutableStateOf(false) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val outwardSign = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
    val dismissThresholdPx = with(density) { dismissThreshold.toPx() }
    val dismissDistancePx = with(density) { dismissDistance.toPx() }
    val axisThresholdPx = with(density) { 8.dp.toPx() }

    fun animateOffsetTo(target: Float, onFinished: (() -> Unit)? = null) {
        animationJob?.cancel()
        animationJob = scope.launch {
            animate(
                initialValue = dragDistance,
                targetValue = target,
                animationSpec = spring(dampingRatio = .86f, stiffness = 420f),
            ) { value, _ -> dragDistance = value }
            dragDistance = target
            animationJob = null
            onFinished?.invoke()
        }
    }

    LaunchedEffect(visible, resetKey) {
        if (visible) {
            if (dragDistance > .5f) {
                animateOffsetTo(0f) { dismissing = false }
            } else {
                dismissing = false
            }
        } else {
            animationJob?.cancel()
        }
    }

    val progress = (dragDistance / dismissDistancePx).coerceIn(0f, 1f)
    val gestureModifier = Modifier.pointerInput(visible, dismissing, layoutDirection) {
        if (visible && !dismissing) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                var lastPosition = down.position
                var totalX = 0f
                var totalY = 0f
                var horizontal = false
                var axisDecided = false
                var completed = false
                val allowedAtDown = currentCanStartDismiss(down.position)
                val startDistance = dragDistance
                animationJob?.cancel()

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                        completed = true
                        break
                    }

                    val amount = change.position - lastPosition
                    lastPosition = change.position
                    totalX += amount.x
                    totalY += amount.y
                    if (!axisDecided && (abs(totalX) > axisThresholdPx || abs(totalY) > axisThresholdPx)) {
                        horizontal = abs(totalX) > abs(totalY)
                        axisDecided = true
                    }
                    val outwardTravel = totalX * outwardSign
                    if (axisDecided && horizontal && allowedAtDown && outwardTravel > 0f) {
                        change.consume()
                        dragDistance = (startDistance + outwardTravel).coerceAtMost(dismissDistancePx)
                    }
                }

                if (completed && allowedAtDown && dragDistance >= dismissThresholdPx) {
                    dismissing = true
                    currentOnDismiss()
                } else {
                    animateOffsetTo(0f)
                }
            }
        }
    }

    Box(modifier.then(gestureModifier)) {
        content(
            modifier
                .offset { IntOffset((dragDistance * outwardSign).roundToInt(), 0) }
                .graphicsLayer {
                    alpha = 1f - progress * .14f
                    scaleX = 1f - progress * .018f
                    scaleY = 1f - progress * .018f
                },
        )
    }
}

/** Shape and handle treatment shared by detail/editor cards in every mode. */
@Composable
fun DetailCardSurface(
    modifier: Modifier = Modifier,
    handleColor: androidx.compose.ui.graphics.Color = CalinoColors.Canvas,
    content: @Composable (Modifier) -> Unit,
) {
    val mode = LocalCalinoSurfaceMode.current
    val shape = if (mode == CalinoSurfaceMode.EndPanel) {
        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(28.dp)
    }
    Column(modifier.clip(shape).background(CalinoColors.Canvas)) {
        if (mode == CalinoSurfaceMode.EndPanel) {
            Box(
                Modifier.fillMaxWidth().height(24.dp).background(handleColor),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier.padding(start = 12.dp).width(4.dp).height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CalinoColors.Ink.copy(alpha = .18f)),
                )
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(24.dp).background(handleColor),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(36.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CalinoColors.Ink.copy(alpha = .18f)),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize()) }
    }
}
