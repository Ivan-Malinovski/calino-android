package calino.malinov.ski.poc.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoShapes
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.design.eventTint
import calino.malinov.ski.poc.state.LocalCalinoPreferences
import calino.malinov.ski.poc.state.LocalTimeFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Mono = androidx.compose.ui.text.font.FontFamily.Monospace
private val ShortDateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/**
 * A stored calendar color, made fit for the current theme.
 *
 * The single seam for data-driven color. Event colors arrive as raw ARGB longs
 * from the fixtures and from CalDAV servers, chosen by whoever made the
 * calendar and usually against a white one, so they are adapted on the way out
 * rather than stored differently.
 */
@Composable
@ReadOnlyComposable
fun eventColor(value: Long): Color = CalinoColors.forEvent(Color(value))

/**
 * Adds the native downward-dismiss gesture to a surface.
 *
 * A downward drag is allowed to start anywhere on the surface. The surface
 * follows the finger, failed drags spring back instead of snapping, and an
 * accepted drag hands off at release so the host's exit animation can finish
 * the dismissal without a second, competing off-screen animation.
 *
 * [resetKey] lets a host that rejected a dismissal (for example, to show a
 * discard confirmation) ask the gesture surface to smoothly return to rest.
 * This is deliberately separate from [visible]: a host can keep the surface
 * visible underneath its confirmation UI without replaying its exit animation.
 */
@Composable
fun SwipeDownDismiss(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissThreshold: androidx.compose.ui.unit.Dp = 112.dp,
    dismissDistance: androidx.compose.ui.unit.Dp = 720.dp,
    resetKey: Any? = null,
    canStartDismiss: () -> Boolean = { true },
    content: @Composable (Modifier) -> Unit,
) {
    var dragY by remember { mutableFloatStateOf(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentCanStartDismiss by rememberUpdatedState(canStartDismiss)
    var dismissing by remember { mutableStateOf(false) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { dismissThreshold.toPx() }
    val dismissDistancePx = with(density) { dismissDistance.toPx() }
    val axisThresholdPx = with(density) { 8.dp.toPx() }

    fun animateOffsetTo(target: Float, onFinished: (() -> Unit)? = null) {
        animationJob?.cancel()
        animationJob = scope.launch {
            animate(
                initialValue = dragY,
                targetValue = target,
                animationSpec = spring(dampingRatio = .86f, stiffness = 420f),
            ) { value, _ -> dragY = value }
            dragY = target
            animationJob = null
            onFinished?.invoke()
        }
    }

    LaunchedEffect(visible, resetKey) {
        if (visible) {
            // A dirty editor can reject the gesture by opening a discard
            // prompt while remaining visible. Reset the translation as soon
            // as that state changes so the prompt never sits on a dragged
            // surface and the gesture can be tried again after cancellation.
            if (abs(dragY) > .5f) {
                animateOffsetTo(0f) { dismissing = false }
            } else {
                dismissing = false
            }
        } else {
            // A thresholded release is handed to the host immediately. Stop
            // any previous spring so it cannot continue writing dragY while
            // the host is running its exit transition.
            animationJob?.cancel()
        }
    }

    val progress = (dragY / dismissDistancePx).coerceIn(0f, 1f)
    val gestureModifier = Modifier.pointerInput(visible, dismissing) {
        if (visible && !dismissing) {
            // Observe in Initial so a downward dismissal can begin over a
            // LazyColumn/TextField, while taps and upward child scrolling keep
            // their normal behavior. We only consume after the axis is clear.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                var lastPosition = down.position
                var totalX = 0f
                var totalY = 0f
                var vertical = false
                var axisDecided = false
                var completed = false
                val startDragY = dragY
                // Once the child has the stream, keep it. This lets a
                // scrollable editor consume a downward drag that started away
                // from its top instead of handing it to the sheet later.
                val dismissAllowedAtDown = currentCanStartDismiss()
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
                        vertical = abs(totalY) > abs(totalX)
                        axisDecided = true
                    }
                    if (axisDecided && vertical && totalY > 0f && dismissAllowedAtDown) {
                        change.consume()
                        dragY = (startDragY + totalY).coerceAtMost(dismissDistancePx)
                    }
                }

                if (completed && vertical && dismissAllowedAtDown && dragY >= dismissThresholdPx) {
                    // The parent surfaces already animate their exit after
                    // onDismiss. Calling it only after a second spring to
                    // dismissDistance made the gesture feel delayed and
                    // caused two translations to compound. Keep the
                    // finger's final position and let that host animation
                    // own the remaining travel.
                    dismissing = true
                    currentOnDismiss()
                } else {
                    animateOffsetTo(0f)
                }
            }
        }
    }

    // Keep the pointer-input node stationary while the child moves. If the
    // node receiving coordinates also moves, its local pointer coordinates
    // move against the finger and produce the characteristic pixel vibration.
    Box(modifier.then(gestureModifier)) {
        content(
            modifier
                .offset { IntOffset(0, dragY.roundToInt()) }
                .graphicsLayer {
                    alpha = 1f - progress * .14f
                    scaleX = 1f - progress * .018f
                    scaleY = 1f - progress * .018f
                },
        )
    }
}

/** The three visual forms used by calendar chips. */
enum class EventChipVariant { Rail, Tint, Task }
enum class AgendaRowVariant { Card, Flat }

enum class CalinoIcon { Back, Forward, Plus, Search, Calendar, Repeat, Check, Pin, Note, Users, Edit, Trash, Bell, Filter, Clock, More }

/**
 * Pressed surfaces use a very small scale and a 6% ink wash. This keeps the
 * handoff's quiet paper language while making touch feedback visible without a
 * ripple covering dense calendar content.
 */
@Composable
internal fun Modifier.calinoPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    pressedScale: Float = .97f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Hoisted: a draw scope cannot read the palette's composition local. The
    // wash also has to invert -- darkening an already dark row does nothing.
    val pressWash = CalinoColors.PressWash
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(CalinoMotion.PressMillis),
        label = "pressed scale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            if (pressed) drawRect(pressWash)
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

@Composable
fun EventChip(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    variant: EventChipVariant = EventChipVariant.Rail,
    checked: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    EventChipContent(
        title = title,
        color = color,
        modifier = modifier,
        variant = variant,
        checked = checked,
        enabled = enabled,
        onClick = onClick,
        accessibilityDescription = if (variant == EventChipVariant.Task) "$title, task" else title,
    )
}

@Composable
private fun EventChipContent(
    title: String,
    color: Color,
    modifier: Modifier,
    variant: EventChipVariant,
    checked: Boolean,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    accessibilityDescription: String,
) {
    val shape = RoundedCornerShape(CalinoShapes.Chip)
    val fill by animateColorAsState(
        targetValue = if (variant == EventChipVariant.Task) Color.Transparent else eventTint(color, .10f),
        animationSpec = tween(CalinoMotion.FadeThroughMillis),
        label = "event chip tint",
    )
    val pressModifier = if (onClick != null) {
        Modifier.calinoPressable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .heightIn(min = if (onClick != null) 44.dp else 20.dp)
            .then(pressModifier)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
                if (!enabled) stateDescription = "Disabled"
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp)
                .clip(shape)
                .background(fill, shape)
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (variant == EventChipVariant.Task) {
                TaskCheckbox(checked, color, Modifier.size(13.dp), circular = false)
                Spacer(Modifier.width(5.dp))
            } else {
                Box(
                    Modifier
                        .padding(start = 3.dp, end = 6.dp)
                        .width(2.5.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (checked) CalinoColors.Ink3 else CalinoColors.Ink,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            )
        }
    }
}

@Composable
fun EventChip(event: CalEvent, modifier: Modifier = Modifier, variant: EventChipVariant = EventChipVariant.Rail, onClick: (() -> Unit)? = null) {
    val timeFormat = LocalTimeFormat
    EventChipContent(
        title = event.title,
        color = eventColor(event.color),
        modifier = modifier,
        variant = variant,
        checked = false,
        enabled = true,
        onClick = onClick,
        accessibilityDescription = buildString {
            append(event.title)
            append(", ")
            append(event.start?.let { timeFormat.format(it) } ?: "all-day")
            event.location?.let { append(", ").append(it) }
            append(", ").append(event.calendarId)
        },
    )
}

@Composable
fun EventDots(colors: List<Color>, modifier: Modifier = Modifier, taskIndexes: Set<Int> = emptySet(), max: Int = 4) {
    EventDotsContent(colors, modifier, taskIndexes, max)
}

@Composable
private fun EventDotsContent(colors: List<Color>, modifier: Modifier, taskIndexes: Set<Int>, max: Int) {
    Row(
        modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.take(max).forEachIndexed { index, color ->
            val task = index in taskIndexes
            Box(
                Modifier
                    .size(if (task) 7.dp else 5.dp)
                    .border(if (task) 1.5.dp else 0.dp, color, CircleShape)
                    .clip(CircleShape)
                    .background(if (task) Color.Transparent else color),
            )
        }
    }
}

@Composable
fun EventDots(events: List<CalEvent>, modifier: Modifier = Modifier, max: Int = 4) {
    EventDotsContent(events.map { eventColor(it.color) }, modifier, emptySet(), max)
}

@Composable
fun EventLines(colors: List<Color>, modifier: Modifier = Modifier, max: Int = 2) {
    Column(
        modifier.clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        colors.take(max).forEach { color ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
fun EventLinesForEvents(events: List<CalEvent>, modifier: Modifier = Modifier, max: Int = 2) =
    EventLines(events.map { eventColor(it.color) }, modifier, max)

@Composable
private fun TaskCheckbox(checked: Boolean, color: Color, modifier: Modifier, circular: Boolean) {
    val shape = if (circular) CircleShape else RoundedCornerShape(4.dp)
    val fill by animateColorAsState(
        targetValue = if (checked) color else Color.Transparent,
        animationSpec = tween(CalinoMotion.ContentEnterMillis),
        label = "task checkbox fill",
    )
    val stroke by animateColorAsState(
        targetValue = if (checked) color else lerp(CalinoColors.Ink3, color, .6f),
        animationSpec = tween(CalinoMotion.ContentEnterMillis),
        label = "task checkbox stroke",
    )
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.5.dp, stroke, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) CalinoIcon(CalinoIcon.Check, tint = CalinoColors.OnAccent, modifier = Modifier.fillMaxSize().padding(2.dp), contentDescription = null)
    }
}

@Composable
fun AgendaRow(
    title: String,
    color: Color,
    time: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    variant: AgendaRowVariant = AgendaRowVariant.Card,
    onClick: (() -> Unit)? = null,
    trailingDescription: String? = null,
    struck: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(CalinoShapes.Row)
    val isCard = variant == AgendaRowVariant.Card
    val surface = if (isCard) {
        Modifier
            .clip(shape)
            .background(CalinoColors.Panel, shape)
            // The edge carries the event's colour rather than the neutral
            // hairline, so a card is identifiable before its rail is read.
            .border(1.dp, color.copy(alpha = .16f), shape)
    } else {
        Modifier
    }
    val pressModifier = if (onClick != null) Modifier.calinoPressable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isCard) Modifier.heightIn(min = 44.dp) else Modifier)
            .then(surface)
            .then(pressModifier)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(title)
                    time?.let { append(", ").append(it) }
                    subtitle?.let { append(", ").append(it) }
                    trailingDescription?.let { append(", ").append(it) }
                }
            }
            .padding(
                start = if (isCard) 10.dp else 0.dp,
                top = if (isCard) 9.dp else 6.dp,
                bottom = if (isCard) 9.dp else 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same rail the day surface's event cards carry: a rounded pill
        // sitting inside the card's own inset, rather than a square-ended tab
        // pressed flush into the rounded left edge.
        Box(
            Modifier
                .width(4.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        AgendaRowTime(time)
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                color = if (struck) CalinoColors.Ink3 else CalinoColors.Ink,
                fontSize = 14.5.sp,
                lineHeight = 21.75.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (struck) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(
                subtitle,
                color = CalinoColors.Ink3,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.let {
            it()
            Spacer(Modifier.width(4.dp))
        }
    }
}

/**
 * The agenda gutter's clock face. The numerals and the meridiem are measured
 * as two slots rather than one string: the numerals right-align so every
 * colon in the list falls in the same column, and AM/PM sits in a fixed slot
 * after them at a smaller size and lighter ink, so it labels the time without
 * competing with it.
 *
 * The single fixed-width string this replaces was 46dp wide against a
 * monospace face that needs ~50dp for "12:30 PM", so any two-digit hour on a
 * 12-hour clock was silently clipped down to its numerals.
 */
@Composable
private fun AgendaRowTime(time: String?) {
    val (numerals, meridiem) = remember(time) { splitMeridiem(time ?: AllDayLabel) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            numerals,
            modifier = Modifier.width(AgendaTimeNumeralWidth),
            color = CalinoColors.Ink2,
            fontFamily = Mono,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text(
            meridiem.orEmpty(),
            modifier = Modifier.width(AgendaTimeMeridiemWidth).padding(start = 3.dp),
            color = CalinoColors.Ink3,
            fontFamily = Mono,
            fontSize = 8.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private const val AllDayLabel = "ALL-DAY"
private val AgendaTimeNumeralWidth = 34.dp
private val AgendaTimeMeridiemWidth = 17.dp

/**
 * Splits a formatted clock face into its numerals and its trailing meridiem.
 * A 24-hour face, or the all-day label, has no meridiem and keeps the whole
 * string in the numeral slot.
 */
internal fun splitMeridiem(time: String): Pair<String, String?> {
    val cut = time.lastIndexOf(' ')
    if (cut <= 0) return time to null
    val tail = time.substring(cut + 1)
    val isMeridiem = tail.length == 2 &&
        (tail[0] == 'A' || tail[0] == 'a' || tail[0] == 'P' || tail[0] == 'p') &&
        (tail[1] == 'M' || tail[1] == 'm')
    return if (isMeridiem) time.substring(0, cut) to tail else time to null
}

@Composable
fun AgendaRow(event: CalEvent, modifier: Modifier = Modifier, variant: AgendaRowVariant = AgendaRowVariant.Card, onClick: (() -> Unit)? = null) {
    val timeFormat = LocalTimeFormat
    val showLocations = LocalCalinoPreferences.current.showLocations
    AgendaRow(
        title = event.title,
        color = eventColor(event.color),
        time = event.start?.let { timeFormat.format(it) },
        subtitle = event.location?.takeIf { showLocations }
            ?: if (event.recurrence != null) "Repeats weekly" else null,
        modifier = modifier,
        variant = variant,
        onClick = onClick,
    )
}

/**
 * The agenda-list shape of a due task: the same rail/time/card geometry as an
 * event row, with the completion circle as a trailing control. The
 * checkbox-first [AgendaRow] overload below stays the list shape used by the
 * Tasks surface.
 */
@Composable
fun AgendaTaskRow(
    task: CalTask,
    modifier: Modifier = Modifier,
    time: String? = null,
    onClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val color = eventColor(task.color)
    AgendaRow(
        title = task.title,
        color = color,
        time = time,
        subtitle = task.category,
        modifier = modifier,
        variant = AgendaRowVariant.Card,
        onClick = onClick,
        trailingDescription = if (task.done) "completed" else "open",
        struck = task.done,
        trailing = {
            val checkboxPressModifier = if (onCheckedChange != null) {
                Modifier.calinoPressable(role = Role.Checkbox) { onCheckedChange(!task.done) }
            } else {
                Modifier
            }
            // Wide for the thumb, but no taller than the rail: a square 44dp
            // touch target was the tallest thing in the row and pushed every
            // task card a third taller than the event cards beside it. The
            // whole card is tappable anyway, so the checkbox only needs to be
            // comfortably hittable, not the row's height driver.
            Box(
                Modifier
                    .size(width = 44.dp, height = 30.dp)
                    .then(checkboxPressModifier)
                    .semantics {
                        contentDescription = "${task.title}, checkbox"
                        stateDescription = if (task.done) "Checked" else "Not checked"
                    },
                contentAlignment = Alignment.Center,
            ) {
                TaskCheckbox(task.done, color, Modifier.size(19.dp), circular = true)
            }
        },
    )
}

@Composable
fun AgendaRow(
    task: CalTask,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    compact: Boolean = false,
) {
    val color = eventColor(task.color)
    val description = buildString {
        append(task.title)
        if (!compact) task.due?.let { append(", due ").append(it.format(ShortDateFormat)) }
        task.category?.let { append(", ").append(it) }
    }
    val rowPressModifier = if (onClick != null) Modifier.calinoPressable(onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 36.dp else 44.dp)
            .then(rowPressModifier)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = if (task.done) "Completed" else "Open"
            }
            .padding(horizontal = 8.dp, vertical = if (compact) 0.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val checkboxPressModifier = if (onCheckedChange != null) {
            Modifier.calinoPressable(role = Role.Checkbox) { onCheckedChange(!task.done) }
        } else {
            Modifier
        }
        Box(
            Modifier
                .size(if (compact) 36.dp else 44.dp)
                .then(checkboxPressModifier)
                .semantics {
                    contentDescription = "${task.title}, checkbox"
                    stateDescription = if (task.done) "Checked" else "Not checked"
                },
            contentAlignment = Alignment.Center,
        ) {
            TaskCheckbox(task.done, color, Modifier.size(21.dp), circular = true)
        }
        if (compact) {
            Row(
                Modifier.weight(1f).padding(start = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    task.title,
                    Modifier.weight(1f),
                    fontSize = 15.sp,
                    lineHeight = 22.5.sp,
                    color = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                task.category?.let {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(eventTint(color, .14f))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text(
                            it,
                            color = lerp(CalinoColors.Ink, color, .7f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f).padding(start = 5.dp)) {
                Text(
                    task.title,
                    fontSize = 15.sp,
                    lineHeight = 22.5.sp,
                    color = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.due != null || task.category != null) {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        task.due?.let { Text(it.format(ShortDateFormat), color = CalinoColors.Ink3, fontSize = 12.sp, lineHeight = 18.sp) }
                        task.category?.let {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(eventTint(color, .14f))
                                    .padding(horizontal = 7.dp, vertical = 1.dp),
                            ) {
                                Text(it, color = lerp(CalinoColors.Ink, color, .7f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The shared calendar header: menu, month title with year, an optional
 * secondary line, previous/next month, and a Today shortcut. Used by both the
 * zooming calendar and the agenda so their headers stay identical.
 */
@Composable
fun CalinoMonthHeading(
    day: LocalDate,
    onOpenMenu: (() -> Unit)?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    showToday: Boolean,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onOpenMenu?.let { MenuButton(onClick = it) }
        IconButton(
            onClick = onPreviousMonth,
            modifier = Modifier.semantics { contentDescription = "Previous month" },
        ) { Icon(CalinoIcons.ChevronLeft, contentDescription = null, tint = CalinoColors.Ink2) }
        Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
            AnimatedContent(
                targetState = YearMonth.from(day),
                transitionSpec = {
                    val direction = if (targetState.isAfter(initialState)) 1 else -1
                    slideInHorizontally(tween(190)) { direction * it / 4 } + fadeIn(tween(150)) togetherWith
                        slideOutHorizontally(tween(150)) { -direction * it / 4 } + fadeOut(tween(110))
                },
                label = "month heading",
            ) { month ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() },
                        style = CalinoTypography.titleLarge.copy(fontSize = 27.sp, lineHeight = 30.sp),
                    )
                    Text(month.year.toString(), style = CalinoTypography.bodyMedium, color = CalinoColors.Ink3, modifier = Modifier.padding(start = 7.dp, bottom = 2.dp))
                }
            }
            if (subtitle != null) {
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                    label = "week heading",
                ) { line ->
                    Text(line, style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 1.dp))
                }
            }
        }
        if (showToday) {
            TextButton(
                onClick = onToday,
                modifier = Modifier.semantics { contentDescription = "Go to today" },
            ) { Text("Today", color = CalinoColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        }
        IconButton(
            onClick = onNextMonth,
            modifier = Modifier.semantics { contentDescription = "Next month" },
        ) { Icon(CalinoIcons.ChevronRight, contentDescription = null, tint = CalinoColors.Ink2) }
    }
    Box(
        Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 16.dp)
            .background(CalinoColors.Accent.copy(alpha = .22f)),
    )
}

@Composable
fun DayGroupHeader(day: LocalDate, isToday: Boolean = false, eventCount: Int? = null, modifier: Modifier = Modifier, empty: Boolean = false) {
    // Hoisted: a draw scope cannot read the palette's composition local.
    val hairline = CalinoColors.Line2
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(hairline, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
            }
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)))
                    if (isToday) append(", today")
                    eventCount?.let { append(", $it events") }
                    if (empty) append(", nothing scheduled")
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(58.dp)) {
            Text(
                if (isToday) "TODAY" else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()),
                color = if (isToday) CalinoColors.Accent else CalinoColors.Ink3,
                fontFamily = Mono,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(day.dayOfMonth.toString(), fontFamily = CalinoTypography.titleMedium.fontFamily, color = CalinoColors.Ink, fontSize = 27.sp, lineHeight = 27.sp)
        }
        if (empty) Text("Nothing scheduled", color = CalinoColors.Ink3, fontSize = 13.sp, lineHeight = 19.5.sp, fontStyle = FontStyle.Italic)
        else eventCount?.let { Text(it.toString(), color = CalinoColors.Ink3, fontFamily = Mono, fontSize = 11.sp, lineHeight = 13.sp) }
    }
}

@Composable
fun TaskRow(
    task: CalTask,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
) = AgendaRow(task, modifier, onClick, onCheckedChange, compact)

/**
 * Shared compact choice control for the mobile surfaces.
 *
 * The outer 44dp lane is a compact touch target; the painted track is 30dp tall.
 * Width is bounded by [maxControlWidth] and the parent's available width, so equal
 * option lanes never force a sibling label into a one-character column.
 */
private object CompactSegmentedMetrics {
    val TouchLaneHeight = 44.dp
    val TrackHeight = 30.dp
}

@Composable
fun CompactSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    semanticLabel: String? = null,
    maxControlWidth: androidx.compose.ui.unit.Dp = 360.dp,
) {
    if (options.isEmpty()) return

    val safeSelected = selectedIndex.coerceIn(0, options.lastIndex)
    val trackShape = RoundedCornerShape(10.dp)
    val segmentShape = RoundedCornerShape(8.dp)
    val touchLaneHeight = CompactSegmentedMetrics.TouchLaneHeight
    val trackHeight = CompactSegmentedMetrics.TrackHeight
    val horizontalPadding = 2.dp
    val gap = 2.dp

    BoxWithConstraints(
        modifier = Modifier
            .widthIn(max = maxControlWidth)
            .then(modifier)
            .height(touchLaneHeight)
            .selectableGroup(),
    ) {
        val availableWidth = (maxWidth - horizontalPadding * 2 - gap * (options.size - 1))
            .coerceAtLeast(0.dp)
        val segmentWidth = availableWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = (segmentWidth + gap) * safeSelected,
            animationSpec = tween(CalinoMotion.ContentEnterMillis),
            label = "segmented selection position",
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(trackShape)
                .background(CalinoColors.Ink.copy(alpha = .05f)),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = horizontalPadding)
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .height(trackHeight)
                .clip(segmentShape)
                .background(CalinoColors.Panel)
                .border(1.dp, CalinoColors.Line, segmentShape),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val active = index == safeSelected
                val labelColor by animateColorAsState(
                    targetValue = if (active) CalinoColors.Ink else CalinoColors.Ink2,
                    animationSpec = tween(CalinoMotion.FadeThroughMillis),
                    label = "segmented label color $index",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(segmentShape)
                        .selectable(
                            selected = active,
                            role = Role.RadioButton,
                            onClick = { onSelected(index) },
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = semanticLabel?.let { "$it: $option" } ?: option
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        color = labelColor,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Backwards-compatible name retained for existing previews and callers. */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    CompactSegmentedControl(
        options = options,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun SectionLabel(text: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 28.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(text.uppercase(Locale.getDefault()), color = CalinoColors.Ink3, fontFamily = Mono, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.weight(1f).height(1.dp).background(CalinoColors.Line2))
        count?.let { Text(it.toString(), color = CalinoColors.Ink3, fontFamily = Mono, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/**
 * The floating add affordance. A horizontal drag on the pill moves between the
 * main views, so the destination can be changed without opening the sidebar;
 * [canSwipe] lets the host refuse a direction at the ends of the row, where the
 * pill springs back instead of committing.
 *
 * The drag used to be a blind commit: nothing said where releasing would land
 * until it already had. [destinationLabel] names the view a direction leads to
 * and a chip carrying that name is revealed from under the pill as it travels,
 * filling in once the drag is past the commit threshold.
 */
@Composable
fun AddPill(
    label: String,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null,
    backdropOrigin: () -> Offset = { Offset.Zero },
    canSwipe: (Int) -> Boolean = { false },
    destinationLabel: (Int) -> String? = { null },
    onSwipe: (Int) -> Unit = {},
    onSearch: () -> Unit = {},
    onPhoto: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentCanSwipe by rememberUpdatedState(canSwipe)
    val currentDestinationLabel by rememberUpdatedState(destinationLabel)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Frosted glass over the surface behind it: a blurred patch of the
    // recorded backdrop, then the ink at just under full opacity.
    val blurred = rememberGraphicsLayer()
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var pillOrigin by remember { mutableStateOf(Offset.Zero) }
    val commitPx = with(density) { 56.dp.toPx() }
    // A refused direction still moves, but only enough to read as a limit.
    val maxTravelPx = with(density) { 88.dp.toPx() }
    val edgeTravelPx = with(density) { 22.dp.toPx() }

    fun settle() {
        scope.launch {
            animate(dragX, 0f, animationSpec = spring(dampingRatio = .78f, stiffness = 520f)) { value, _ -> dragX = value }
        }
        scope.launch {
            animate(dragY, 0f, animationSpec = spring(dampingRatio = .78f, stiffness = 520f)) { value, _ -> dragY = value }
        }
    }

    // A drag left moves forward through the views, matching the direction the
    // root content slides in.
    val dragDirection = if (dragX < 0f) 1 else -1
    val destination = if (dragX != 0f && currentCanSwipe(dragDirection)) currentDestinationLabel(dragDirection) else null
    val progress = (abs(dragX) / commitPx).coerceIn(0f, 1f)

    Box(modifier, contentAlignment = Alignment.Center) {
        if (dragY < 0f) {
            SwipeDestinationChip(
                label = "Search",
                direction = 1,
                progress = (abs(dragY) / commitPx).coerceIn(0f, 1f),
                modifier = Modifier.align(Alignment.TopCenter).wrapContentSize(unbounded = true)
                    .offset { IntOffset(0, (-dragY * .30f).roundToInt()) },
            )
        }
        if (destination != null) {
            // The chip sits on the edge the pill is vacating and trails it, so
            // it reads as something uncovered rather than a second floating
            // control. Unbounded so revealing it never resizes the lane.
            SwipeDestinationChip(
                label = destination,
                direction = dragDirection,
                progress = progress,
                modifier = Modifier
                    .align(if (dragDirection == 1) Alignment.CenterEnd else Alignment.CenterStart)
                    .wrapContentSize(unbounded = true)
                    // It slides out as the pill retreats, so the reveal reads
                    // as twice the travel and the name is legible early.
                    .offset { IntOffset((-dragX * .3f).roundToInt(), 0) },
            )
        }
        // Hoisted: a draw scope cannot read the palette's composition local.
        val pillFill = CalinoColors.FloatFill
        Row(
            Modifier
                .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                .shadow(14.dp * CalinoColors.elevationAlpha, RoundedCornerShape(CalinoShapes.Pill), clip = false)
                .clip(RoundedCornerShape(CalinoShapes.Pill))
                // Carries the pill's shape where the fill is too close to the
                // canvas to do it alone. Transparent in light, which needs no
                // edge and never drew one.
                .border(1.dp, CalinoColors.FloatBorder, RoundedCornerShape(CalinoShapes.Pill))
                .onGloballyPositioned { pillOrigin = it.positionInRoot() }
                .drawBehind {
                    if (backdrop != null && canBlur) {
                        blurred.renderEffect = BlurEffect(24f, 24f, TileMode.Clamp)
                        val offset = pillOrigin - backdropOrigin()
                        blurred.record {
                            translate(-offset.x, -offset.y) { drawLayer(backdrop) }
                        }
                        drawLayer(blurred)
                        drawRect(pillFill.copy(alpha = .86f))
                    } else {
                        drawRect(pillFill)
                    }
                }
                .calinoPressable(pressedScale = .97f, onClick = onClick)
                .pointerInput(Unit) {
                    var horizontal = false
                    var vertical = false
                    detectDragGestures(
                        onDragStart = { horizontal = false; vertical = false },
                        onDragEnd = {
                            if (vertical && dragY <= -commitPx) currentOnSearch()
                            if (horizontal) {
                                val direction = if (dragX <= -commitPx) 1 else if (dragX >= commitPx) -1 else 0
                                if (direction != 0 && currentCanSwipe(direction)) currentOnSwipe(direction)
                            }
                            settle()
                        },
                        onDragCancel = { settle() },
                    ) { change, amount ->
                        if (!horizontal && !vertical) {
                            horizontal = abs(amount.x) >= abs(amount.y)
                            vertical = !horizontal
                        }
                        change.consume()
                        if (horizontal) {
                            val next = dragX + amount.x
                            val direction = if (next < 0f) 1 else -1
                            val limit = if (currentCanSwipe(direction)) maxTravelPx else edgeTravelPx
                            dragX = next.coerceIn(-limit, limit)
                        } else {
                            dragY = (dragY + amount.y).coerceIn(-maxTravelPx, edgeTravelPx)
                        }
                    }
                }
                .semantics(mergeDescendants = true) { contentDescription = "$label. Swipe up to search" }
                .padding(start = 16.dp, end = 20.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            onPhoto?.let { photo ->
                Text(
                    "Photo",
                    color = CalinoColors.OnFloat,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CalinoShapes.Pill))
                        .clickable(onClick = photo)
                        .semantics { contentDescription = "Import event or task from photo" }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            CalinoIcon(CalinoIcon.Plus, tint = CalinoColors.OnFloat, modifier = Modifier.size(19.dp), contentDescription = null)
            AnimatedContent(
                targetState = label,
                transitionSpec = { fadeIn(tween(CalinoMotion.FadeThroughMillis)) togetherWith fadeOut(tween(CalinoMotion.FadeThroughMillis)) },
                label = "add pill label",
            ) { text ->
                Text(text, color = CalinoColors.OnFloat, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The destination preview revealed by an [AddPill] drag. Below the commit
 * threshold it stays outlined and muted; at the threshold it fills in, which is
 * the only cue that says "release now and you land here".
 */
@Composable
private fun SwipeDestinationChip(
    label: String,
    direction: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val committed = progress >= 1f
    // Reaching the threshold used to flip the chip to solid ink, which read as
    // a different control appearing rather than the same one arming. It warms
    // to the accent instead, and animates rather than snapping.
    val background by animateColorAsState(
        if (committed) CalinoColors.Accent.copy(alpha = .16f) else CalinoColors.Panel,
        tween(CalinoMotion.FadeThroughMillis),
        label = "swipe destination fill",
    )
    val foreground by animateColorAsState(
        if (committed) CalinoColors.Accent else CalinoColors.Ink2,
        tween(CalinoMotion.FadeThroughMillis),
        label = "swipe destination tint",
    )
    val outline by animateColorAsState(
        CalinoColors.Accent.copy(alpha = if (committed) .38f else 0f),
        tween(CalinoMotion.FadeThroughMillis),
        label = "swipe destination outline",
    )
    val scale = .94f + .06f * progress
    Row(
        modifier
            .graphicsLayer {
                alpha = progress
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(CalinoShapes.Pill))
            .background(background)
            .border(1.dp, if (committed) outline else CalinoColors.Ink.copy(alpha = .12f), RoundedCornerShape(CalinoShapes.Pill))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (direction == -1) {
            CalinoIcon(CalinoIcon.Back, tint = foreground, modifier = Modifier.size(15.dp), contentDescription = null)
        }
        Text(label, color = foreground, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        if (direction == 1) {
            CalinoIcon(CalinoIcon.Forward, tint = foreground, modifier = Modifier.size(15.dp), contentDescription = null)
        }
    }
}

/** The hamburger that opens the root navigation sidebar from a screen header. */
@Composable
fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(CalinoShapes.Button))
            .calinoPressable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Open navigation" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(CalinoIcons.Menu, contentDescription = null, tint = CalinoColors.Ink2, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun ZoomHandle(level: Int, caption: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val safeLevel = level.coerceIn(0, 2)
    val pressModifier = if (onClick != null) Modifier.calinoPressable(onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(pressModifier)
            .semantics(mergeDescendants = true) {
                contentDescription = "Change calendar zoom"
                stateDescription = "Level ${safeLevel + 1} of 3"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(CalinoColors.Ink.copy(alpha = .14f)))
        Spacer(Modifier.width(10.dp))
        Text(caption.uppercase(Locale.getDefault()), color = CalinoColors.Ink3, fontFamily = Mono, fontSize = 9.5.sp, lineHeight = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(26.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(CalinoColors.Ink.copy(alpha = .14f)))
        Spacer(Modifier.width(10.dp))
        repeat(3) { index ->
            val active = index == safeLevel
            val width by animateDpAsState(if (active) 14.dp else 5.dp, tween(CalinoMotion.ContentEnterMillis), label = "zoom step width")
            Box(Modifier.width(width).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (active) CalinoColors.Accent else CalinoColors.Ink3.copy(alpha = .35f)))
            if (index < 2) Spacer(Modifier.width(3.dp))
        }
    }
}

@Composable
fun DetailRow(icon: CalinoIcon, label: String, value: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    val pressModifier = if (onClick != null) Modifier.calinoPressable(onClick = onClick) else Modifier
    // Hoisted: a draw scope cannot read the palette's composition local.
    val hairline = CalinoColors.Line2
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(pressModifier)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(hairline, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
            }
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(26.dp).padding(top = 1.dp), contentAlignment = Alignment.TopStart) {
            CalinoIcon(icon, tint = CalinoColors.Ink3, modifier = Modifier.size(18.dp), contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(Locale.getDefault()), color = CalinoColors.Ink3, fontFamily = Mono, fontSize = 9.5.sp, lineHeight = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Text(value, color = CalinoColors.Ink, fontSize = 14.5.sp, lineHeight = 21.75.sp)
        }
    }
}

@Composable
fun DetailRows(rows: List<Triple<CalinoIcon, String, String>>, modifier: Modifier = Modifier) {
    Column(modifier) {
        rows.filter { it.third.isNotBlank() }.forEach { (icon, label, value) ->
            DetailRow(icon, label, value, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun CalinoScrim(visible: Boolean, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(CalinoMotion.SurfaceFadeMillis)),
        exit = fadeOut(tween(CalinoMotion.ContentExitMillis)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(CalinoColors.Scrim)
                .then(if (onDismiss != null) Modifier.clickable(role = Role.Button, onClick = onDismiss) else Modifier)
                .semantics { contentDescription = "Dismiss" },
        )
    }
}

@Composable
fun CalinoSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = spring(dampingRatio = .85f, stiffness = 380f), initialOffsetY = { it }) + fadeIn(tween(CalinoMotion.ContentEnterMillis)),
        exit = slideOutVertically(animationSpec = spring(dampingRatio = .85f, stiffness = 380f), targetOffsetY = { it }) + fadeOut(tween(CalinoMotion.ContentExitMillis)),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = CalinoShapes.Sheet, topEnd = CalinoShapes.Sheet),
            color = CalinoColors.Panel,
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 44.dp)
                        .fillMaxWidth()
                        .semantics { contentDescription = "Dismiss sheet" }
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(38.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(CalinoColors.Ink.copy(alpha = .16f)))
                }
                content()
            }
        }
    }
}

@Composable
fun CalinoIcon(icon: CalinoIcon, tint: Color = LocalContentColor.current, modifier: Modifier = Modifier, contentDescription: String? = icon.name.lowercase()) {
    Icon(
        imageVector = when (icon) {
            CalinoIcon.Back -> CalinoIcons.ChevronLeft
            CalinoIcon.Forward -> CalinoIcons.ChevronRight
            CalinoIcon.Plus -> CalinoIcons.Plus
            CalinoIcon.Search -> CalinoIcons.Search
            CalinoIcon.Calendar -> CalinoIcons.Calendar
            CalinoIcon.Repeat -> CalinoIcons.Repeat
            CalinoIcon.Check -> CalinoIcons.Check
            CalinoIcon.Pin -> CalinoIcons.Pin
            CalinoIcon.Note -> CalinoIcons.Note
            CalinoIcon.Users -> CalinoIcons.Users
            CalinoIcon.Edit -> CalinoIcons.Edit
            CalinoIcon.Trash -> CalinoIcons.Trash
            CalinoIcon.Bell -> CalinoIcons.Bell
            CalinoIcon.Filter -> CalinoIcons.Filter
            CalinoIcon.Clock -> CalinoIcons.Clock
            CalinoIcon.More -> CalinoIcons.More
        },
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun PreviewComponents() {
    var selected by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(CalinoColors.Canvas).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EventChip("Design review", CalinoColors.Blue, variant = EventChipVariant.Tint)
        EventDots(listOf(CalinoColors.Rose, CalinoColors.Blue, CalinoColors.Green))
        AgendaRow("Design review", CalinoColors.Blue, "10:00 AM", "Studio", variant = AgendaRowVariant.Card)
        SegmentedControl(listOf("Open", "Scheduled", "Done"), selected, { selected = it })
        AddPill(
            label = "Add on Tuesday, May 19",
            canSwipe = { true },
            destinationLabel = { direction -> if (direction == 1) "Agenda" else "Month" },
        ) {}
    }
}
