package calino.malinov.ski.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.util.CalinoTimeFormat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.abs

private val WheelRow = 46.dp
private const val WheelVisibleRows = 5

/** Enough laps that nobody scrolls off either end of a looping wheel. */
private const val WheelLaps = 2_000

/**
 * Opens Calino's time picker and returns the action that shows it. The
 * picker follows Calino's 12/24h setting; [title] names what is being set.
 */
@Composable
fun rememberTimePicker(
    initial: () -> LocalTime?,
    title: String = "Time",
    onPicked: (LocalTime) -> Unit,
): () -> Unit {
    // Seeded when opened; null while closed.
    var seed by remember { mutableStateOf<LocalTime?>(null) }
    val visible = remember { MutableTransitionState(false) }
    val currentInitial by rememberUpdatedState(initial)
    val currentOnPicked by rememberUpdatedState(onPicked)

    // The dialog window stays until the card has finished leaving.
    val mounted = seed != null && (visible.targetState || !visible.isIdle || visible.currentState)
    if (mounted) {
        TimePickerDialog(
            title = title,
            seed = seed!!,
            visible = visible,
            onClose = { picked ->
                picked?.let(currentOnPicked)
                visible.targetState = false
            },
        )
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) seed = null
    }
    return {
        seed = currentInitial() ?: LocalTime.of(LocalTime.now().hour, 0).plusHours(1)
        visible.targetState = true
    }
}

@Composable
private fun TimePickerDialog(
    title: String,
    seed: LocalTime,
    visible: MutableTransitionState<Boolean>,
    onClose: (LocalTime?) -> Unit,
) {
    Dialog(
        onDismissRequest = { onClose(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The window's own dim cannot fade with the card; Calino's scrim can.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) { window?.setDimAmount(0f) }

        Box(Modifier.fillMaxSize()) {
            CalinoScrim(visible = visible.targetState, modifier = Modifier.fillMaxSize(), onDismiss = { onClose(null) })
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(spring(dampingRatio = .86f, stiffness = 420f)) { it / 3 } +
                    fadeIn(tween(CalinoMotion.ContentEnterMillis)),
                exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 520f)) { it / 4 } +
                    fadeOut(tween(CalinoMotion.ContentExitMillis)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                TimePickerCard(title, seed, onCancel = { onClose(null) }, onDone = { onClose(it) })
            }
        }
    }
}

@Composable
private fun TimePickerCard(title: String, seed: LocalTime, onCancel: () -> Unit, onDone: (LocalTime) -> Unit) {
    val twentyFour = LocalTimeFormat == CalinoTimeFormat.TwentyFourHour
    var hour by remember { mutableIntStateOf(seed.hour) }
    var minute by remember { mutableIntStateOf(seed.minute) }

    Column(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(CalinoShapes.Sheet), clip = false)
            .clip(RoundedCornerShape(CalinoShapes.Sheet))
            .background(CalinoColors.Panel)
            .semantics { paneTitle = title }
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp),
    ) {
        Text(title, style = CalinoTypography.titleLarge, color = CalinoColors.Ink)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The band the chosen row settles into, shared by every wheel.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WheelRow)
                    .clip(RoundedCornerShape(CalinoShapes.Row))
                    .background(CalinoColors.Ink.copy(alpha = .05f)),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (twentyFour) {
                    TimeWheel(
                        count = 24,
                        selected = hour,
                        label = { "%02d".format(it) },
                        description = "Hour",
                        onSelected = { hour = it },
                    )
                } else {
                    TimeWheel(
                        count = 12,
                        selected = (hour + 11) % 12,
                        label = { (it + 1).toString() },
                        description = "Hour",
                        onSelected = { hour = (it + 1) % 12 + if (hour >= 12) 12 else 0 },
                    )
                }
                Text(
                    ":",
                    style = CalinoTypography.headlineMedium,
                    color = CalinoColors.Ink,
                    modifier = Modifier.padding(horizontal = 2.dp).padding(bottom = 3.dp),
                )
                TimeWheel(
                    count = 60,
                    selected = minute,
                    label = { "%02d".format(it) },
                    description = "Minute",
                    onSelected = { minute = it },
                )
                if (!twentyFour) {
                    Spacer(Modifier.width(8.dp))
                    TimeWheel(
                        count = 2,
                        selected = if (hour >= 12) 1 else 0,
                        label = { if (it == 0) "AM" else "PM" },
                        description = "AM or PM",
                        loops = false,
                        onSelected = { hour = hour % 12 + it * 12 },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PromptButton("Cancel", filled = false, enabled = true, onClick = onCancel, modifier = Modifier.weight(1f))
            PromptButton("Done", filled = true, enabled = true, onClick = { onDone(LocalTime.of(hour, minute)) }, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * One snapping column of values. Rows fade, shrink and tilt away from the
 * centre band, all derived per frame from the scroll position so they move
 * with the finger rather than catching up to it.
 */
@Composable
private fun TimeWheel(
    count: Int,
    selected: Int,
    label: (Int) -> String,
    description: String,
    onSelected: (Int) -> Unit,
    loops: Boolean = true,
) {
    val total = if (loops) count * WheelLaps else count
    val startIndex = if (loops) count * (WheelLaps / 2) + selected else selected
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val rowPx = with(LocalDensity.current) { WheelRow.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnSelected by rememberUpdatedState(onSelected)

    val centred by remember { derivedStateOf { state.centredIndex(rowPx) } }
    LaunchedEffect(state) {
        snapshotFlow { centred }.distinctUntilChanged().drop(1).collect {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            currentOnSelected(it % count)
        }
    }
    // Another wheel can move this one (AM/PM following the hour, say).
    LaunchedEffect(selected) {
        if (centred % count != selected && !state.isScrollInProgress) {
            val target = centred - centred % count + selected
            state.animateScrollToItem(target.coerceIn(0, total - 1))
        }
    }

    val step: (Int) -> Unit = { delta ->
        scope.launch { state.animateScrollToItem((centred + delta).coerceIn(0, total - 1)) }
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        contentPadding = PaddingValues(vertical = WheelRow * (WheelVisibleRows / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(if (count == 2) 64.dp else 76.dp)
            .height(WheelRow * WheelVisibleRows)
            .semantics {
                contentDescription = description
                stateDescription = label(selected)
                customActions = listOf(
                    CustomAccessibilityAction("Next") { step(1); true },
                    CustomAccessibilityAction("Previous") { step(-1); true },
                )
            },
    ) {
        items(total) { index ->
            val value = index % count
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WheelRow)
                    .graphicsLayer {
                        val distance = state.rowDistance(index, rowPx)
                        val t = (abs(distance) / (WheelVisibleRows / 2 + .5f)).coerceIn(0f, 1f)
                        alpha = lerp(1f, .12f, t)
                        scaleX = lerp(1f, .78f, t)
                        scaleY = scaleX
                        rotationX = (distance * -18f).coerceIn(-60f, 60f)
                        cameraDistance = 12f * density
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) { scope.launch { state.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(value),
                    style = CalinoTypography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    color = CalinoColors.Ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The index nearest the centre band, from where the scroll sits right now. */
private fun LazyListState.centredIndex(rowPx: Float): Int =
    firstVisibleItemIndex + if (firstVisibleItemScrollOffset > rowPx / 2) 1 else 0

/** How many rows [index] sits from the centre band, fractional mid-scroll. */
private fun LazyListState.rowDistance(index: Int, rowPx: Float): Float =
    index - firstVisibleItemIndex - firstVisibleItemScrollOffset / rowPx
