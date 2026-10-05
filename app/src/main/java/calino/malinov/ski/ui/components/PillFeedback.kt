package calino.malinov.ski.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One animation survives every root/modal handoff. Read its values only while drawing. */
@Stable
internal class PillSaveTrace(val color: State<Color>) {
    val phase = Animatable(0f)
    val completion = Animatable(0f)
    val alpha = Animatable(0f)
}

private val LocalPillSaveTrace = staticCompositionLocalOf<PillSaveTrace?> { null }

@Composable
fun CalinoPillFeedback(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPillSaveTrace provides animatePillSaveTrace()) {
        content()
    }
}

@Composable
internal fun rememberPillSaveTrace(): PillSaveTrace =
    LocalPillSaveTrace.current ?: animatePillSaveTrace()

@Composable
private fun animatePillSaveTrace(): PillSaveTrace {
    val lane = LocalCalinoPillLane.current
    val color = animateColorAsState(
        if (lane.writeKind == PillWriteKind.Remove) CalinoColors.Rose
        else androidx.compose.ui.graphics.lerp(CalinoColors.Green, CalinoColors.OnFloat, .42f),
        tween(CalinoMotion.ContentEnterMillis), label = "pill write tint",
    )
    val trace = remember { PillSaveTrace(color) }
    LaunchedEffect(lane.saveState) {
        when (lane.saveState) {
            PillSaveState.Saving -> coroutineScope {
                launch { trace.alpha.animateTo(1f, tween(CalinoMotion.FadeThroughMillis)) }
                launch { trace.completion.animateTo(0f, tween(CalinoMotion.ContentExitMillis)) }
                // Continue the previous phase, including a save that interrupts a result.
                while (true) {
                    trace.phase.animateTo(
                        trace.phase.value + 1f,
                        tween(CalinoMotion.PillTraceLapMillis, easing = LinearEasing),
                    )
                    trace.phase.snapTo(trace.phase.value % 1f)
                }
            }
            PillSaveState.Saved -> {
                // Extend the existing segment into a ring; never replace it at another position.
                trace.completion.animateTo(1f, tween(CalinoMotion.PillConfirmMillis, easing = FastOutSlowInEasing))
                delay(CalinoMotion.PillConfirmPauseMillis)
                trace.alpha.animateTo(0f, tween(CalinoMotion.PillConfirmMillis))
            }
            PillSaveState.Idle -> {
                // Rejected writes dissolve without claiming success.
                trace.alpha.animateTo(0f, tween(CalinoMotion.ContentExitMillis))
                trace.completion.snapTo(0f)
            }
        }
    }
    return trace
}
