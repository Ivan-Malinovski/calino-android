package calino.malinov.ski.ui.tour

import android.view.ViewConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.R
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.CalinoTour
import calino.malinov.ski.state.LocalCalinoTour
import calino.malinov.ski.state.TourStep
import calino.malinov.ski.ui.components.CalinoIcon

/**
 * The tour drawn over the live screen: a small card, and a ghost finger that
 * loops the gesture the card is asking for. Neither takes touches outside the
 * card, so the person performs the real gesture on the real surface; the
 * handler that owns it reports it to [tour].
 *
 * The caller decides what Next and Skip mean for the rest of the app, e.g.
 * closing an editor the tour opened, without saving.
 */
@Composable
fun CalinoTourHost(
    tour: CalinoTour,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = tour.step
    var lastStep by remember { mutableStateOf(step) }
    if (step != null && lastStep != step) lastStep = step
    // The card stays off the surface its step works on: the zoom swipe starts on
    // the week strip at the top, so that card sits low; the hold step uses
    // empty space lower down, so its card sits high.
    val atBottom = (step ?: lastStep) == TourStep.Zoom
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible = step != null && !tour.performed, enter = fadeIn(), exit = fadeOut(tween(CalinoMotion.FadeThroughMillis))) {
            if (step != null) GhostFinger(step)
        }
        AnimatedVisibility(
            visible = step != null,
            modifier = if (atBottom) {
                // Clear of the pill lane and the notice above it.
                Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 188.dp)
            } else {
                Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp)
            },
            enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) + slideInVertically(tween(CalinoMotion.ContentEnterMillis)) { if (atBottom) it / 3 else -it / 3 },
            exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) + slideOutVertically(tween(CalinoMotion.ContentExitMillis)) { if (atBottom) it / 3 else -it / 3 },
        ) {
            // The card keeps the last step while it leaves.
            val current = step ?: lastStep
            if (current != null) {
                TourCard(
                    step = current,
                    steps = tour.steps,
                    performed = tour.performed,
                    onNext = onNext,
                    onSkip = onSkip,
                )
            }
        }
    }
}

private data class TourCopy(val title: Int, val line: Int, val done: Int)

private fun TourStep.copy(): TourCopy = when (this) {
    TourStep.Zoom -> TourCopy(R.string.tour_zoom_title, R.string.tour_zoom_line, R.string.tour_zoom_done)
    TourStep.Hold -> TourCopy(R.string.tour_hold_title, R.string.tour_hold_line, R.string.tour_hold_done)
    TourStep.Clock -> TourCopy(R.string.tour_clock_title, R.string.tour_clock_line, R.string.tour_clock_done)
    TourStep.Journal -> TourCopy(R.string.tour_journal_title, R.string.tour_journal_line, R.string.tour_journal_line)
    TourStep.Contacts -> TourCopy(R.string.tour_contacts_title, R.string.tour_contacts_line, R.string.tour_contacts_line)
    TourStep.PillSwipe -> TourCopy(R.string.tour_pill_swipe_title, R.string.tour_pill_swipe_line, R.string.tour_pill_swipe_done)
    TourStep.PillHold -> TourCopy(R.string.tour_pill_hold_title, R.string.tour_pill_hold_line, R.string.tour_pill_hold_done)
}

@Composable
private fun TourCard(step: TourStep, steps: List<TourStep>, performed: Boolean, onNext: () -> Unit, onSkip: () -> Unit) {
    val copy = step.copy()
    val total = steps.size
    val index = steps.indexOf(step) + 1
    val last = step == steps.last()
    // A card that only informs has nothing to perform: it reads as ready, without the sage wash.
    val ready = performed || step.informational
    val title = stringResource(copy.title)
    val line = stringResource(if (performed) copy.done else copy.line)
    val shape = RoundedCornerShape(18.dp)
    // The same sage the Save wash uses: the card stays quiet while asking, and
    // takes the colour once the real gesture has landed.
    val tone by animateColorAsState(
        if (performed) CalinoColors.Green else CalinoColors.Accent,
        tween(CalinoMotion.SurfaceFadeMillis),
        label = "tour tone",
    )
    val wash by animateColorAsState(
        if (performed) CalinoColors.Green.copy(alpha = .14f) else Color.Transparent,
        tween(CalinoMotion.SurfaceFadeMillis),
        label = "tour wash",
    )
    val description = stringResource(R.string.tour_card_description, index, total, title)
    Surface(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .shadow(10.dp * CalinoColors.elevationAlpha, shape, clip = false)
            .border(1.dp, tone.copy(alpha = .38f), shape)
            .semantics { contentDescription = description; liveRegion = LiveRegionMode.Polite },
        shape = shape,
        color = CalinoColors.Panel,
        contentColor = CalinoColors.Ink,
    ) {
        Column(Modifier.drawBehind { drawRect(wash) }.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).drawBehind { drawRect(tone.copy(alpha = .16f)) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (performed) {
                        CalinoIcon(CalinoIcon.Check, tint = tone, modifier = Modifier.size(14.dp), contentDescription = null)
                    } else {
                        Text("$index", color = tone, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    title,
                    Modifier.weight(1f),
                    style = CalinoTypography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.tour_step_count, index, total),
                    color = CalinoColors.Ink3,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            AnimatedContent(
                targetState = line,
                transitionSpec = { fadeIn(tween(CalinoMotion.ContentEnterMillis, delayMillis = 60)) togetherWith fadeOut(tween(CalinoMotion.FadeThroughMillis)) },
                label = "tour line",
            ) { text ->
                Text(
                    text,
                    Modifier.padding(top = 6.dp, end = 8.dp),
                    style = CalinoTypography.bodyMedium,
                    color = CalinoColors.Ink2,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!last || !ready) {
                    TextButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 44.dp)) {
                        Text(stringResource(R.string.tour_skip), color = CalinoColors.Ink2)
                    }
                }
                TextButton(onClick = onNext, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(
                        stringResource(if (last) R.string.tour_finish else R.string.tour_next),
                        color = tone,
                        fontWeight = if (ready) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * The finger that shows the gesture. It draws only: no pointer modifier, so
 * every touch still reaches the app underneath. One loop per step: a vertical
 * drag for [TourStep.Zoom]; a press with a filling ring for [TourStep.Hold],
 * the ring running for exactly the system long-press timeout.
 */
@Composable
private fun GhostFinger(step: TourStep) {
    val longPressMillis = ViewConfiguration.getLongPressTimeout()
    val transition = rememberInfiniteTransition(label = "tour ghost")
    val accent = CalinoColors.Accent
    val ink = CalinoColors.Ink
    when (step) {
        TourStep.Zoom -> {
            val travel by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 2400
                        0f at 0 using LinearEasing
                        0f at 350
                        1f at 1150
                        1f at 1500
                        0f at 2300
                    },
                    RepeatMode.Restart,
                ),
                label = "zoom travel",
            )
            val press by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 2400
                        0f at 0
                        1f at 200
                        1f at 1300
                        0f at 1500
                        0f at 2400
                    },
                    RepeatMode.Restart,
                ),
                label = "zoom press",
            )
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2f
                // Starts on the zoom handle row under the week strip and pulls down.
                val top = 198.dp.toPx()
                val span = 230.dp.toPx()
                val center = Offset(x, top + span * travel)
                drawFinger(center, press, accent, ink)
                // The path it follows, as a faint line, so the direction reads at a glance.
                drawLine(
                    accent.copy(alpha = .22f * press),
                    Offset(x, top),
                    Offset(x, top + span),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        TourStep.Hold -> {
            val cycle = longPressMillis + 1500
            val ring by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = cycle
                        0f at 0
                        0f at 350
                        1f at 350 + longPressMillis using LinearEasing
                        1f at 350 + longPressMillis + 450
                        0f at 350 + longPressMillis + 450 + 1
                        0f at cycle
                    },
                    RepeatMode.Restart,
                ),
                label = "hold ring",
            )
            val press by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = cycle
                        0f at 0
                        1f at 300
                        1f at 350 + longPressMillis + 450
                        0f at 350 + longPressMillis + 650
                        0f at cycle
                    },
                    RepeatMode.Restart,
                ),
                label = "hold press",
            )
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * .72f)
                val radius = 30.dp.toPx()
                if (ring > 0f) {
                    drawCircle(accent.copy(alpha = .18f), radius, center, style = Stroke(3.dp.toPx()))
                    drawArc(
                        accent,
                        startAngle = -90f,
                        sweepAngle = 360f * ring,
                        useCenter = false,
                        topLeft = center - Offset(radius, radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                drawFinger(center, press, accent, ink)
            }
        }
        TourStep.Clock -> {
            val clock = LocalCalinoTour.current.startClockCenter
            val travel by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 2400
                        0f at 0 using LinearEasing
                        0f at 350
                        1f at 1100
                        1f at 1500
                        0f at 1500
                        0f at 2400
                    },
                    RepeatMode.Restart,
                ),
                label = "clock travel",
            )
            val press by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 2400
                        0f at 0
                        1f at 250
                        1f at 1250
                        0f at 1500
                        0f at 2400
                    },
                    RepeatMode.Restart,
                ),
                label = "clock press",
            )
            // Until the editor has laid its clock out there is nothing to point at.
            if (clock != null) Canvas(Modifier.fillMaxSize()) {
                val span = 90.dp.toPx()
                val y = clock.y
                val from = clock.x - span / 2f
                drawFinger(Offset(from + span * travel, y), press, accent, ink)
                drawLine(
                    accent.copy(alpha = .22f * press),
                    Offset(from, y),
                    Offset(from + span, y),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        // Informational cards have no gesture to show.
        TourStep.Journal, TourStep.Contacts -> Unit
        TourStep.PillSwipe -> {
            val travel by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 2400
                        0f at 0 using LinearEasing
                        0f at 350
                        1f at 1100
                        1f at 1500
                        0f at 1500
                        0f at 2400
                    },
                    RepeatMode.Restart,
                ),
                label = "pill swipe travel",
            )
            val press by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 2400
                        0f at 0
                        1f at 250
                        1f at 1250
                        0f at 1500
                        0f at 2400
                    },
                    RepeatMode.Restart,
                ),
                label = "pill swipe press",
            )
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2f
                val start = size.height - PillCenterFromBottom.toPx()
                val span = 150.dp.toPx()
                drawFinger(Offset(x, start - span * travel), press, accent, ink)
                drawLine(
                    accent.copy(alpha = .22f * press),
                    Offset(x, start),
                    Offset(x, start - span),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        TourStep.PillHold -> {
            val pillButton = LocalCalinoTour.current.pillButtonCenter
            val cycle = longPressMillis + 1500
            val ring by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = cycle
                        0f at 0
                        0f at 350
                        1f at 350 + longPressMillis using LinearEasing
                        1f at 350 + longPressMillis + 450
                        0f at 350 + longPressMillis + 450 + 1
                        0f at cycle
                    },
                    RepeatMode.Restart,
                ),
                label = "pill hold ring",
            )
            val press by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = cycle
                        0f at 0
                        1f at 300
                        1f at 350 + longPressMillis + 450
                        0f at 350 + longPressMillis + 650
                        0f at cycle
                    },
                    RepeatMode.Restart,
                ),
                label = "pill hold press",
            )
            Canvas(Modifier.fillMaxSize()) {
                val center = pillButton ?: Offset(size.width / 2f - 66.dp.toPx(), size.height - PillCenterFromBottom.toPx())
                val radius = 30.dp.toPx()
                if (ring > 0f) {
                    drawCircle(accent.copy(alpha = .18f), radius, center, style = Stroke(3.dp.toPx()))
                    drawArc(
                        accent,
                        startAngle = -90f,
                        sweepAngle = 360f * ring,
                        useCenter = false,
                        topLeft = center - Offset(radius, radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                drawFinger(center, press, accent, ink)
            }
        }
    }
}

/** Where the resting pill's centre sits above the bottom of the window. */
private val PillCenterFromBottom = 72.dp

private fun DrawScope.drawFinger(center: Offset, press: Float, accent: Color, ink: Color) {
    if (press <= 0f) return
    val radius = 15.dp.toPx() * (1f - .12f * press)
    drawCircle(ink.copy(alpha = .16f * press), radius * 1.55f, center)
    drawCircle(lerp(Color.Transparent, accent, .85f).copy(alpha = .55f * press), radius, center)
    drawCircle(accent.copy(alpha = press), radius, center, style = Stroke(2.dp.toPx()))
}
