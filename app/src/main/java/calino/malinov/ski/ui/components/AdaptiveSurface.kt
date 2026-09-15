package calino.malinov.ski.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.state.LocalHingeOpenness
import calino.malinov.ski.state.foldSplitProgress
import calino.malinov.ski.state.CalinoSurfaceMode
import calino.malinov.ski.state.EndLaneWidthDp
import calino.malinov.ski.state.LocalFoldPosture
import calino.malinov.ski.state.calinoLayoutSpec
import calino.malinov.ski.state.calinoEndLaneActive
import calino.malinov.ski.state.calinoFloatsInEndLane
import calino.malinov.ski.state.calinoSurfaceModeFor
import calino.malinov.ski.state.calinoWindowClassFor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The live translation of a surface being dragged toward dismissal.
 *
 * The drag is measured by the gesture primitive wrapped around the card, but
 * it has to be *applied* by the host: the card itself sits inside whatever
 * container its surface uses -- for the previews, a pager, which clips its
 * pages -- so a card that moved itself would be cut off at that container's
 * edge and could never reach the bottom of the window. The host's panel box
 * is the outermost thing that belongs to this one surface, and nothing clips
 * it.
 *
 * The values are plain mutable state read from a `graphicsLayer` lambda, so a
 * drag frame re-layers without recomposing anything.
 */
@androidx.compose.runtime.Stable
class CalinoSurfaceDismissDrag {
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    /** 0..1 toward the full dismissal distance; drives the fade and shrink. */
    var progress by mutableFloatStateOf(0f)

    /**
     * The translation the host's layer is actually carrying, recorded as the
     * layer block runs.
     *
     * Moving the panel moves the gesture node inside it, so the pointer
     * positions the primitive reads are measured against an origin that is
     * itself chasing the finger -- which is the pixel vibration that
     * translating the card in place was written to avoid. The primitive adds
     * these back toevery position to work in a space that does not move. It has
     * to be what the layer is carrying rather than what was last written:
     * hit testing uses the transform from the last layer update, and a value
     * written during this very event is not in effect yet.
     */
    var appliedX = 0f
        private set
    var appliedY = 0f
        private set

    /** Called from the host's layer block with the values it is applying. */
    fun recordApplied(x: Float, y: Float) {
        appliedX = x
        appliedY = y
    }

    fun clear() {
        offsetX = 0f
        offsetY = 0f
        progress = 0f
    }
}

/** Set by [AdaptiveSurfaceHost] for the gesture primitive inside it. */
val LocalCalinoSurfaceDismissDrag =
    androidx.compose.runtime.compositionLocalOf<CalinoSurfaceDismissDrag?> { null }

/** The mode currently used by the nearest adaptive surface host. */
val LocalCalinoSurfaceMode = androidx.compose.runtime.staticCompositionLocalOf {
    CalinoSurfaceMode.BottomSheet
}

/**
 * Extends a scrim from an inset content host into the transparent status bar.
 *
 * Root content is intentionally laid out below [WindowInsets.statusBars], but
 * edge-to-edge makes the pixels behind that inset part of the same window. Any
 * transient veil hosted by the inset content therefore needs this matching
 * slice or the notification bar remains visually detached from the dimmed
 * background. Keeping the geometry here makes drawers and modal hosts follow
 * the same system-bar rule.
 */
@Composable
internal fun StatusBarScrimExtension(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(
        modifier
            .offset(y = -statusBarHeight)
            .fillMaxWidth()
            .height(statusBarHeight)
            .background(color),
    )
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
    preferredSurfaceHeight: Dp? = null,
    pill: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = visible) { events ->
        try {
            events.collect { event ->
                predictiveBackProgress = event.progress.coerceIn(0f, 1f)
            }
            // Keep the final gesture frame in place while visibility is
            // committed. AnimatedVisibility can then dispose an already
            // departed surface instead of restarting its exit from rest.
            predictiveBackProgress = 1f
            onDismiss()
        } catch (cancelled: CancellationException) {
            val start = predictiveBackProgress
            animate(
                initialValue = start,
                targetValue = 0f,
                animationSpec = CalinoMotion.gestureReturn(),
            ) { value, _ -> predictiveBackProgress = value }
            throw cancelled
        }
    }
    LaunchedEffect(visible) {
        if (visible) predictiveBackProgress = 0f
    }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mounted = true }

    // Claimed here, at the top of the host, and not down where the pill is
    // actually composed: everything below is inside a BoxWithConstraints,
    // which subcomposes its content during the layout pass. A claim from in
    // there lands after the root pill has already composed for that frame,
    // and the lane looks empty for exactly one frame in each direction.
    val lane = LocalCalinoPillLane.current
    if (pill != null) {
        // Keep a non-Unit remembered value: the claim must still happen during
        // composition (before the root pill is composed), but Compose lint
        // correctly rejects using remember purely for a Unit side effect.
        @Suppress("UNUSED_VARIABLE")
        val laneClaimed = remember(lane) {
            lane.claim()
            true
        }
        DisposableEffect(lane) { onDispose { lane.release() } }
    }

    // Owned by the host rather than by the card, so the card's own container
    // cannot clip the travel. See [CalinoSurfaceDismissDrag].
    val dismissDrag = remember { CalinoSurfaceDismissDrag() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val layoutSpec = calinoLayoutSpec(
            maxWidth.value.roundToInt(),
            maxHeight.value.roundToInt(),
            LocalFoldPosture.current,
        )
        val windowClass = layoutSpec.windowClass
        val endLane = layoutSpec.splitPanes
        val mode = calinoSurfaceModeFor(windowClass, kind, endLane)
        // A floating card that keeps its own size but sits under the pill
        // instead of in the middle of the window.
        val inLane = endLane && calinoFloatsInEndLane(kind)
        val targetPane = layoutSpec.preferredTransientPane
        // WindowManager only publishes the separating bounds once the device
        // reaches its stable pose. Follow the hinge sensor in the gap so a
        // transient card never waits until the last frame to leave the crease.
        val hingeOpenness = LocalHingeOpenness.current
        val splitProgress by remember(hingeOpenness) {
            derivedStateOf { foldSplitProgress(hingeOpenness?.value ?: 1f) }
        }
        // Some ordinary phones expose a hinge-angle sensor (or report a
        // stale zero reading) without WindowManager reporting a folding
        // feature. The sensor alone is not layout authority: otherwise every
        // portrait sheet is moved into a phantom right pane. It only smooths
        // the transition on a window WindowManager has identified as foldable.
        val intermediateFold =
            layoutSpec.mode == calino.malinov.ski.state.CalinoLayoutMode.Single &&
                LocalFoldPosture.current.hasFoldingFeature &&
                splitProgress > 0f
        val paneWidth = if (intermediateFold) {
            lerpDp(maxWidth, ((maxWidth - 44.dp) / 2f).coerceAtLeast(1.dp), splitProgress)
        } else targetPane.widthDp.dp.coerceAtLeast(1.dp)
        val paneHeight = targetPane.heightDp.dp.coerceAtLeast(1.dp)
        val paneLeft = if (intermediateFold) maxWidth - paneWidth else targetPane.leftDp.dp
        val paneTop = targetPane.topDp.dp
        val laneWidth = minOf(EndLaneWidthDp.dp, paneWidth)
        val scrimProgress by animateFloatAsState(
            targetValue = if (mounted && visible) scrimAlpha else 0f,
            animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
            label = "adaptive surface scrim",
        )
        val surfaceWidthCap = kind.widthCapDp.dp
        val surfaceHeightCap = minOf(kind.heightCapDp.dp, preferredSurfaceHeight ?: kind.heightCapDp.dp)
        val laneInnerWidth = (laneWidth - 32.dp).coerceAtLeast(1.dp)
        val laneWidthCap = if (inLane) laneInnerWidth else paneWidth
        val floatingWidthTarget =
            minOf((paneWidth - 32.dp).coerceAtLeast(1.dp), surfaceWidthCap, laneWidthCap)
        val floatingHeightTarget = minOf((paneHeight - 32.dp).coerceAtLeast(1.dp), surfaceHeightCap)
        val floatingWidth by animateDpAsState(
            targetValue = floatingWidthTarget,
            animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
            label = "adaptive floating width",
        )
        val floatingHeight by animateDpAsState(
            targetValue = floatingHeightTarget,
            animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
            label = "adaptive floating height",
        )
        // Capped to the lane as well, so the panel and the pill riding above it
        // share one centre line instead of being a gutter's width apart.
        val settledSideWidth by animateDpAsState(
            targetValue = minOf((paneWidth * .92f).coerceAtLeast(1.dp), surfaceWidthCap, laneInnerWidth),
            animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
            label = "adaptive side panel width",
        )
        val sideWidth = settledSideWidth
        val sideHeight = (paneHeight - 24.dp).coerceAtLeast(1.dp)
        val bottomHeightTarget = when {
            paneHeight < 520.dp -> paneHeight * .96f
            kind == CalinoSurfaceKind.CompactPreview -> minOf(paneHeight * .5f, surfaceHeightCap)
            // The foldable cover display is shorter in dp than the emulator;
            // half-height there clips the final editable row. This remains a
            // compact card but reaches its content cap when the screen allows.
            kind == CalinoSurfaceKind.EventPreviewCompact -> minOf(paneHeight * .86f, surfaceHeightCap)
            kind == CalinoSurfaceKind.Preview -> minOf(paneHeight * .68f, surfaceHeightCap)
            else -> paneHeight * .86f
        }
        val bottomHeight by animateDpAsState(
            targetValue = bottomHeightTarget,
            animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
            label = "adaptive bottom sheet height",
        )
        val bottomSheetSpareHeight = (paneHeight - bottomHeight).coerceAtLeast(0.dp)

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
            // A centred card has nowhere to slide off to, so its exit is the
            // only one without travel. Scale alone at this size is close to
            // invisible and the card reads as cut rather than dismissed, so
            // the fade is stretched over the whole exit and given a short
            // downward drift to carry it.
            CalinoSurfaceMode.FloatingWindow ->
                scaleOut(
                    tween(FloatingExitMillis, easing = FastOutLinearInEasing),
                    targetScale = .92f,
                ) + slideOutVertically(
                    tween(FloatingExitMillis, easing = FastOutLinearInEasing),
                ) { (it * .09f).roundToInt() } + fadeOut(
                    tween(FloatingExitMillis, easing = LinearEasing),
                )
            CalinoSurfaceMode.EndPanel ->
                slideOutHorizontally(tween(220)) { it } + fadeOut(tween(150))
        }
        val panelModifier: Modifier = when (mode) {
            CalinoSurfaceMode.BottomSheet ->
                Modifier
                    .align(Alignment.TopStart)
                    // Keep the sheet's existing size and give its tail a
                    // small, fixed clearance from Android's gesture pill.
                    // A fraction of the spare height moved short detail cards
                    // much farther than tall editor cards on physical phones.
                    .absoluteOffset(
                        x = paneLeft,
                        y = paneTop + bottomSheetSpareHeight - minOf(bottomSheetSpareHeight, BottomSheetLift),
                    )
                    .padding(vertical = 8.dp)
                    .width(paneWidth)
                    .height(bottomHeight)
            CalinoSurfaceMode.FloatingWindow -> if (inLane) {
                Modifier
                    .align(Alignment.TopStart)
                    .absoluteOffset(
                        x = paneLeft + (paneWidth - floatingWidth) / 2f,
                        y = paneTop + (paneHeight - floatingHeight) / 2f,
                    )
                    .width(floatingWidth)
                    .height(floatingHeight)
            } else {
                Modifier
                    .align(Alignment.TopStart)
                    .absoluteOffset(
                        x = paneLeft + (paneWidth - floatingWidth) / 2f,
                        y = paneTop + (paneHeight - floatingHeight) / 2f,
                    )
                    .width(floatingWidth)
                    .height(floatingHeight)
            }
            CalinoSurfaceMode.EndPanel ->
                Modifier
                    .align(Alignment.TopStart)
                    .absoluteOffset(
                        x = paneLeft + (paneWidth - sideWidth - 16.dp).coerceAtLeast(0.dp),
                        y = paneTop + 12.dp,
                    )
                    .width(sideWidth)
                    .height(sideHeight)
        }

        // The scrim is already visible behind the pill. Keep it outside the
        // recorded layer: drawing a recording that also contained the scrim
        // over the real scrim applied the dimming twice until the arriving
        // card reached the pill (and again while it left).
        val backdropLayer = rememberGraphicsLayer()
        var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
        StatusBarScrimExtension(
            color = CalinoColors.scrim(scrimProgress * (1f - predictiveBackProgress)),
            modifier = Modifier.align(Alignment.TopStart),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(CalinoColors.scrim(scrimProgress * (1f - predictiveBackProgress)))
                .clickable(enabled = visible, onClick = onDismiss)
                .semantics { this.contentDescription = contentDescription },
        )

        // Only the card is recorded for the pill's blur. Transparent space in
        // this layer reveals the one real scrim already drawn above.
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { backdropOrigin = it.positionInRoot() }
                .drawWithContent {
                    backdropLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backdropLayer)
                },
        ) {
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
                Box(
                    panelModifier.graphicsLayer {
                        val predictiveX = when (mode) {
                            CalinoSurfaceMode.EndPanel -> size.width * predictiveBackProgress
                            else -> 0f
                        }
                        val predictiveY = when (mode) {
                            CalinoSurfaceMode.BottomSheet -> size.height * predictiveBackProgress
                            CalinoSurfaceMode.FloatingWindow -> size.height * .09f * predictiveBackProgress
                            else -> 0f
                        }
                        translationX = dismissDrag.offsetX + predictiveX
                        translationY = dismissDrag.offsetY + predictiveY
                        dismissDrag.recordApplied(dismissDrag.offsetX, dismissDrag.offsetY)
                        val settling = maxOf(dismissDrag.progress, predictiveBackProgress)
                        alpha = 1f - settling * .14f
                        scaleX = 1f - settling * .018f
                        scaleY = 1f - settling * .018f
                    },
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalCalinoSurfaceMode provides mode,
                        LocalCalinoSurfaceDismissDrag provides dismissDrag,
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
                // Retain the last valid modal frame until the root pill's
                // SideEffect replaces it. Clearing here creates an unblurred
                // frame at the collapsed end of the return morph.
                onDispose { }
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

/**
 * Just inside the host's own unmount delay, so the card finishes leaving
 * before its caller takes it out of the composition.
 */
private const val FloatingExitMillis = 200

/** A height-independent lift keeps compact and tall bottom cards aligned. */
private val BottomSheetLift = 36.dp

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
    val hostDrag = LocalCalinoSurfaceDismissDrag.current
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

    // As in [SwipeDownDismiss]: the host applies the translation, because the
    // panel this card sits in is the only box nothing else clips.
    fun publish(x: Float, y: Float) {
        dragDistance = x
        dragDownDistance = y
        hostDrag?.offsetX = x * outwardSign
        hostDrag?.offsetY = y
        hostDrag?.progress = (maxOf(x, y) / dismissDistancePx).coerceIn(0f, 1f)
    }

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
                animationSpec = CalinoMotion.gestureReturn(),
            ) { value, _ ->
                publish(
                    startX + (targetX - startX) * value,
                    startY + (targetY - startY) * value,
                )
            }
            publish(targetX, targetY)
            animationJob = null
            onFinished?.invoke()
        }
    }

    DisposableEffect(hostDrag) { onDispose { hostDrag?.clear() } }

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
                // See [SwipeDownDismiss]: measure against the translation the
                // host is carrying, not against a node moving with the finger.
                fun steady(position: Offset) = if (hostDrag == null) {
                    position
                } else {
                    position + Offset(hostDrag.appliedX, hostDrag.appliedY)
                }

                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                var lastPosition = steady(down.position)
                var totalX = 0f
                var totalY = 0f
                var horizontal = false
                var vertical = false
                var axisDecided = false
                var completed = false
                val allowedAtDown = currentCanStartDismiss(steady(down.position))
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

                    val position = steady(change.position)
                    val amount = position - lastPosition
                    lastPosition = position
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
                        publish((startDistance + outwardTravel).coerceAtMost(dismissDistancePx), 0f)
                    } else if (axisDecided && vertical && downwardAllowedAtDown && totalY > 0f) {
                        change.consume()
                        publish(0f, (startDownDistance + totalY).coerceAtMost(dismissDistancePx))
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

    Box(modifier.testTag(SwipeEndDismissTag).then(gestureModifier)) {
        content(
            if (hostDrag != null) {
                modifier
            } else {
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
                    }
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
