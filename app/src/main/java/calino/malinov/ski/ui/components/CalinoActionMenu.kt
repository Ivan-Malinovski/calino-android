package calino.malinov.ski.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.round
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Where the most recent finger went down, in host-window pixels. Written by a
 * non-consuming observer at the app root so a long-press menu can open under
 * the finger without every caller threading the position through.
 */
class CalinoPressPoint {
    var position: Offset? = null
}

val LocalCalinoPressPoint = staticCompositionLocalOf { CalinoPressPoint() }

/** Records every pointer-down for [LocalCalinoPressPoint] without consuming it. */
fun Modifier.recordCalinoPressPoint(point: CalinoPressPoint): Modifier = pointerInput(point) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        point.position = down.position
    }
}

private val MenuShape = RoundedCornerShape(14.dp)
private val MenuWidth = 248.dp

private val EdgeMargin = 12.dp
private val DismissThreshold = 56.dp
private val FadeDistance = 140.dp
private const val DismissVelocityDpPerSecond = 900f

/**
 * Calino's long-press action menu: a paper card anchored to the held row that
 * grows from the edge nearest the row and can be swiped down to dismiss.
 *
 * The popup spans the window so the card can follow a swipe unclipped, in the
 * same frame as the finger.
 */
@Composable
fun CalinoActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    var dragY by remember { mutableFloatStateOf(0f) }
    var swipedAway by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            dragY = 0f
            swipedAway = false
        }
    }
    if (!visibility.currentState && !visibility.targetState) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val thresholdPx = with(density) { DismissThreshold.toPx() }
    val fadePx = with(density) { FadeDistance.toPx() }
    val edgePx = with(density) { EdgeMargin.roundToPx() }
    val shadowPx = with(density) { 14.dp.toPx() }
    val shadowColor = CalinoColors.Ink
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .7f
    // The held row, in popup-window coordinates. The popup covers the whole
    // window and never moves, so the card can travel with a swipe without
    // being clipped and pointer positions stay steady.
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    var opensAbove by remember { mutableStateOf(false) }
    val provider = remember { FullWindowProvider { anchor = it } }
    // Anchor bounds arrive in the host window's coordinates; the popup window
    // can sit at a different screen origin (status bar, insets), so both are
    // taken to screen space before placing the card.
    val hostView = LocalView.current
    var popupOrigin by remember { mutableStateOf(IntOffset.Zero) }
    val pressPoint = LocalCalinoPressPoint.current
    // Captured once per opening and kept through the exit animation: later
    // touches (including the dismissing tap) must not move the menu.
    var press by remember { mutableStateOf<Offset?>(null) }
    if (expanded && !visibility.currentState) press = pressPoint.position
    var originX by remember { mutableFloatStateOf(.5f) }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }

    val transition = rememberTransition(visibility, label = "action menu")
    val appear by transition.animateFloat(
        transitionSpec = {
            tween(if (targetState) CalinoMotion.ContentEnterMillis - 40 else CalinoMotion.FadeThroughMillis)
        },
        label = "appear",
    ) { if (it) 1f else 0f }
    val scrollState = rememberScrollState()

    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        // Keep the pointer stream on the held row: a menu that appeared during
        // the hold must yield at once when that same finger starts a drag.
        properties = PopupProperties(focusable = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { popupOrigin = it.positionOnScreen().round() }
                // Touching anywhere off the card dismisses, as outside taps did.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!swipedAway && !cardBounds.contains(down.position)) currentOnDismiss()
                    }
                },
        ) {
            val host = IntArray(2).also(hostView::getLocationOnScreen)
            val shift = IntOffset(host[0] - popupOrigin.x, host[1] - popupOrigin.y)
            val row = anchor?.translate(shift) ?: return@Box
            val pressX = press?.let { it.x + shift.x }
            Column(
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val width = constraints.maxWidth
                        val height = constraints.maxHeight
                        // Centred under the finger when known, else the row's start edge.
                        val x = pressX?.let { (it - placeable.width / 2f).roundToInt() }
                            ?: if (layoutDirection == LayoutDirection.Ltr) row.left else row.right - placeable.width
                        val below = row.bottom
                        val above = row.top - placeable.height
                        opensAbove = below + placeable.height > height - edgePx && above >= edgePx
                        val y = if (opensAbove) above else below
                        val placedX = x.coerceIn(edgePx, (width - placeable.width - edgePx).coerceAtLeast(edgePx))
                        // Grow from the finger, wherever clamping put the card.
                        originX = pressX?.let { ((it - placedX) / placeable.width).coerceIn(0f, 1f) } ?: .5f
                        layout(width, height) {
                            placeable.place(
                                placedX,
                                y.coerceIn(edgePx, (height - placeable.height - edgePx).coerceAtLeast(edgePx)),
                            )
                        }
                    }
                    .width(MenuWidth)
                    .onGloballyPositioned { cardBounds = it.boundsInParent() }
                    .graphicsLayer {
                        translationY = dragY
                        val scale = .94f + .06f * appear
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(originX, if (opensAbove) 1f else 0f)
                    }
                    // Shadow and content are separate layers, and neither fades
                    // by layer alpha on the shadow's layer: an alpha layer is
                    // drawn offscreen and loses the shadow. The shadow fades by
                    // its colour instead.
                    .graphicsLayer {
                        val visible = appear * (1f - (dragY / fadePx).coerceIn(0f, 1f))
                        shadowElevation = shadowPx
                        shape = MenuShape
                        clip = true
                        ambientShadowColor = shadowColor.copy(alpha = visible)
                        spotShadowColor = shadowColor.copy(alpha = visible)
                    }
                    .graphicsLayer {
                        alpha = appear * (1f - (dragY / fadePx).coerceIn(0f, 1f))
                    }
                    .background(CalinoColors.Panel)
                    .border(1.dp, CalinoColors.Line, MenuShape)
                    .pointerInput(Unit) {
                        val axisSlop = 8.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val atTop = scrollState.value == 0
                            val tracker = VelocityTracker()
                            val startDrag = dragY
                            // Positions are card-local and the card moves with the
                            // drag, so add the translation back for steady values.
                            val startY = down.position.y + dragY
                            val startX = down.position.x
                            var dragging = false
                            var decided = false
                            var released = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    released = true
                                    break
                                }
                                val y = change.position.y + dragY
                                val totalX = change.position.x - startX
                                val totalY = y - startY
                                if (!decided && (abs(totalX) > axisSlop || abs(totalY) > axisSlop)) {
                                    decided = true
                                    dragging = atTop && totalY > abs(totalX)
                                }
                                if (dragging) {
                                    change.consume()
                                    dragY = (startDrag + totalY).coerceAtLeast(0f)
                                    tracker.addPosition(change.uptimeMillis, change.position.copy(y = y))
                                }
                            }
                            if (!dragging) return@awaitEachGesture
                            val velocity = if (released) tracker.calculateVelocity().y else 0f
                            val fling = velocity > DismissVelocityDpPerSecond * density.density
                            val from = dragY
                            if (released && (dragY >= thresholdPx || fling)) {
                                swipedAway = true
                                scope.launch {
                                    animate(from, from + fadePx, animationSpec = tween(CalinoMotion.FadeThroughMillis)) { v, _ -> dragY = v }
                                    currentOnDismiss()
                                }
                            } else {
                                scope.launch {
                                    animate(from, 0f, animationSpec = CalinoMotion.gestureReturn()) { v, _ -> dragY = v }
                                }
                            }
                        }
                    }
                    .heightIn(max = maxHeight)
                    .verticalScroll(scrollState)
                    .padding(vertical = 6.dp),
                content = content,
            )
        }
    }
}

/** Covers the whole window and reports where the held row is. */
private class FullWindowProvider(private val onAnchor: (IntRect) -> Unit) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        onAnchor(anchorBounds)
        return IntOffset.Zero
    }
}

/** The row of large, icon-over-label actions at the top of the menu. */
@Composable
fun ColumnScope.CalinoActionMenuTiles(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
    CalinoActionMenuDivider()
}

@Composable
fun RowScope.CalinoActionMenuTile(
    text: String,
    icon: ImageVector,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val tint = if (enabled) CalinoColors.Ink else CalinoColors.Ink3.copy(alpha = .6f)
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = 60.dp)
            .clip(shape)
            .then(if (emphasized) Modifier.background(CalinoColors.Side) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text }
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(5.dp))
        Text(text, color = tint, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CalinoActionMenuDivider() {
    Box(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(CalinoColors.Line),
    )
}

@Composable
fun CalinoActionMenuItem(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    description: String = text,
    onClick: () -> Unit,
) {
    val color = when {
        danger && enabled -> CalinoColors.Rose
        enabled -> CalinoColors.Ink
        else -> CalinoColors.Ink3.copy(alpha = .6f)
    }
    Row(
        Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger || !enabled) color else CalinoColors.Ink2,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(text, color = color, fontSize = 15.sp, lineHeight = 20.sp)
    }
}
