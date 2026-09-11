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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.state.CalinoSurfaceKind
import calino.malinov.ski.poc.state.LocalHingeOpenness
import calino.malinov.ski.poc.state.foldSplitProgress
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
    pill: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mounted = true }

    // Claimed here, at the top of the host, and not down where the pill is
    // actually composed: everything below is inside a BoxWithConstraints,
    // which subcomposes its content during the layout pass. A claim from in
    // there lands after the root pill has already composed for that frame,
    // and the lane looks empty for exactly one frame in each direction.
    val lane = LocalCalinoPillLane.current
    if (pill != null) {
        remember(lane) { lane.claim() }
        DisposableEffect(lane) { onDispose { lane.release() } }
    }

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
        // Bending the device halves the room a transient surface may take, in
        // step with the hinge, so a sheet or a panel does not end up lying
        // across the crease while the calendar behind it has already parted.
        val hingeOpenness = LocalHingeOpenness.current
        val splitProgress by remember(hingeOpenness) {
            derivedStateOf {
                val openness = hingeOpenness?.value ?: return@derivedStateOf 0f
                (foldSplitProgress(openness) * 100f).roundToInt() / 100f
            }
        }
        val foldWidthCap = lerpDp(
            maxWidth,
            ((maxWidth - 44.dp) / 2f).coerceAtLeast(1.dp),
            splitProgress,
        )
        val floatingWidth = minOf((maxWidth - 32.dp).coerceAtLeast(1.dp), surfaceWidthCap, foldWidthCap)
        val floatingHeight = minOf((maxHeight - 32.dp).coerceAtLeast(1.dp), surfaceHeightCap)
        val settledSideWidth by animateDpAsState(
            targetValue = minOf((maxWidth * .46f).coerceAtLeast(1.dp), surfaceWidthCap),
            animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
            label = "adaptive side panel width",
        )
        // The animation is for a window-size change. The fold cap is applied
        // after it, so the panel tracks the hinge instead of chasing it.
        val sideWidth = minOf(settledSideWidth, foldWidthCap)
        val sideHeight = (maxHeight - 24.dp).coerceAtLeast(1.dp)
        val bottomHeight = maxHeight * if (maxHeight < 520.dp) .96f else .86f

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
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .width(sideWidth)
                    .height(sideHeight)
        }

        // Everything the pill floats over is recorded here so the pill can
        // blur its own patch of it. The pill is drawn as a sibling of this
        // box, never inside it, so the layer cannot recurse.
        val backdropLayer = rememberGraphicsLayer()
        var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { backdropOrigin = it.positionInRoot() }
                .drawWithContent {
                    backdropLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backdropLayer)
                },
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(CalinoColors.scrim(scrimProgress))
                .clickable(enabled = visible, onClick = onDismiss)
                .semantics { this.contentDescription = contentDescription },
        )

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

        if (pill != null) {
            DisposableEffect(lane, backdropLayer, backdropOrigin) {
                lane.setBackdrop(backdropLayer, backdropOrigin)
                onDispose { lane.setBackdrop(null, Offset.Zero) }
            }
        }

        // The action pill is deliberately outside the visibility transition
        // and outside the card's drag: it belongs to the pill lane, not to
        // the card, and it stays there while the card arrives and leaves.
        // Anything else would slide the pill off screen and then bring the
        // root add pill back, which is two objects where there is one.
        if (pill != null) {
            val anchor = lane.addPillBounds
            val laneContent: @Composable () -> Unit = {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalCalinoSurfaceMode provides mode,
                    content = pill,
                )
            }
            if (anchor != null) {
                RootAnchoredPill(anchor, laneContent)
            } else {
                // No root pill has been on screen this session (a modal
                // restored straight into view); fall back to the card's own
                // bottom edge.
                Box(
                    when (mode) {
                        CalinoSurfaceMode.BottomSheet ->
                            Modifier.align(Alignment.BottomCenter).padding(bottom = PillLaneInset)
                        else -> panelModifier.padding(bottom = PillLaneInset)
                    },
                    contentAlignment = Alignment.BottomCenter,
                ) { laneContent() }
            }
        }
    }
}

/**
 * Places [content] with its bottom centre on the root pill's bottom centre --
 * centred in portrait, in the right-hand lane on a wide screen -- so a modal's
 * pill continues from that exact spot without this host having to know which
 * of those it is.
 *
 * The conversion out of root coordinates is done during placement, from this
 * layout's own coordinates, rather than from a position recorded by an
 * `onGloballyPositioned` in a previous frame. A recorded origin is not yet
 * known the first time the pill is placed, and the pill spent that frame an
 * inset away from the root pill it is supposed to be continuing from -- which
 * is the second pill that flashed as a modal opened.
 */
@Composable
private fun RootAnchoredPill(anchor: Rect, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        // Unbounded: the pill is placed against a point, so the lane it sits
        // in must never be what decides how wide it may grow.
        val pill = measurables.first().measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            val origin = coordinates?.positionInRoot() ?: Offset.Zero
            pill.place(
                (anchor.center.x - origin.x - pill.width / 2f).roundToInt(),
                (anchor.bottom - origin.y - pill.height).roundToInt(),
            )
        }
    }
}

/** The pill's distance from the bottom of its lane, shared with the root pill. */
private val PillLaneInset = 20.dp

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
    allowDownwardDismiss: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragDownDistance by remember { mutableFloatStateOf(0f) }
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

    fun animateOffsetTo(
        targetX: Float,
        targetY: Float,
        onFinished: (() -> Unit)? = null,
    ) {
        animationJob?.cancel()
        val startX = dragDistance
        val startY = dragDownDistance
        animationJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = spring(dampingRatio = .86f, stiffness = 420f),
            ) { value, _ ->
                dragDistance = startX + (targetX - startX) * value
                dragDownDistance = startY + (targetY - startY) * value
            }
            dragDistance = targetX
            dragDownDistance = targetY
            animationJob = null
            onFinished?.invoke()
        }
    }

    LaunchedEffect(visible, resetKey) {
        if (visible) {
            if (dragDistance > .5f || dragDownDistance > .5f) {
                animateOffsetTo(0f, 0f) { dismissing = false }
            } else {
                dismissing = false
            }
        } else {
            animationJob?.cancel()
        }
    }

    val progress = (maxOf(dragDistance, dragDownDistance) / dismissDistancePx).coerceIn(0f, 1f)
    val gestureModifier = Modifier.pointerInput(visible, dismissing, layoutDirection, allowDownwardDismiss) {
        if (visible && !dismissing) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                var lastPosition = down.position
                var totalX = 0f
                var totalY = 0f
                var horizontal = false
                var vertical = false
                var axisDecided = false
                var completed = false
                val allowedAtDown = currentCanStartDismiss(down.position)
                val downwardAllowedAtDown = allowDownwardDismiss
                val startDistance = dragDistance
                val startDownDistance = dragDownDistance
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
                        vertical = !horizontal
                        axisDecided = true
                    }
                    val outwardTravel = totalX * outwardSign
                    if (axisDecided && horizontal && allowedAtDown && outwardTravel > 0f) {
                        change.consume()
                        dragDistance = (startDistance + outwardTravel).coerceAtMost(dismissDistancePx)
                        dragDownDistance = 0f
                    } else if (axisDecided && vertical && downwardAllowedAtDown && totalY > 0f) {
                        change.consume()
                        dragDistance = 0f
                        dragDownDistance = (startDownDistance + totalY).coerceAtMost(dismissDistancePx)
                    }
                }

                if (completed && (
                        allowedAtDown && dragDistance >= dismissThresholdPx ||
                            downwardAllowedAtDown && dragDownDistance >= dismissThresholdPx
                    )
                ) {
                    dismissing = true
                    currentOnDismiss()
                } else {
                    animateOffsetTo(0f, 0f)
                }
            }
        }
    }

    Box(modifier.then(gestureModifier)) {
        content(
            modifier
                .offset {
                    IntOffset(
                        (dragDistance * outwardSign).roundToInt(),
                        dragDownDistance.roundToInt(),
                    )
                }
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
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(28.dp)
    }
    Column(
        modifier
            .shadow(18.dp * if (mode == CalinoSurfaceMode.EndPanel) CalinoColors.elevationAlpha else 0f, shape, clip = false)
            .clip(shape)
            .background(CalinoColors.Canvas),
    ) {
        if (mode == CalinoSurfaceMode.EndPanel) {
            Box(
                Modifier.fillMaxWidth().height(16.dp).background(handleColor),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(36.dp).height(4.dp)
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
