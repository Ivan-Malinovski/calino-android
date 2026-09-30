package calino.malinov.ski.ui.range

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.design.*
import calino.malinov.ski.ui.components.AgendaRow
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.EditorReveal
import calino.malinov.ski.ui.components.TaskCheckbox
import calino.malinov.ski.ui.components.calinoPressable
import calino.malinov.ski.ui.components.eventColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Height of the bottom zone a dragged task is released on; tall enough to clear the add pill with the label above it. */
internal val WeekDropZoneHeight = 124.dp
/** Corner badge diameter, and the touch square around it. */
private val BadgeSize = 30.dp
private val BadgeTouch = 48.dp
private const val SheetPeekRowDp = 34
private const val SheetHandleDp = 7

private val MonthDay = DateTimeFormatter.ofPattern("MMM d")

private fun weekRangeLabel(first: LocalDate, last: LocalDate, count: Int): String =
    "${first.format(MonthDay)} – ${last.format(MonthDay)} · $count ${if (count == 1) "task" else "tasks"}".uppercase(Locale.getDefault())

private fun openCount(tasks: List<CalTask>) = tasks.count { !it.done }

/** Accent, darkened far enough to read on [CalinoColors.AccentSoft] (and lightened in dark themes). */
private val AccentInk: Color @Composable get() = lerp(CalinoColors.Accent, CalinoColors.Ink, .32f)

/**
 * The shared "add a week task" state, so the popover, the sheet and the strip
 * keep one draft between them and a rejected write keeps what was typed.
 */
@Stable
internal class WeekTaskComposer(
    private val addingState: MutableState<Boolean>,
    private val titleState: MutableState<String>,
    private val scope: CoroutineScope,
    private val submit: State<suspend (String) -> Boolean>,
) {
    var adding by addingState
    var title by titleState
    var saving by mutableStateOf(false)
        private set

    fun save() {
        val text = title.trim()
        if (text.isEmpty() || saving) return
        saving = true
        scope.launch {
            try {
                if (submit.value(text)) { title = ""; adding = false }
            } finally { saving = false }
        }
    }
}

@Composable
internal fun rememberWeekTaskComposer(first: LocalDate, onAdd: suspend (String) -> Boolean): WeekTaskComposer {
    val adding = rememberSaveable(first) { mutableStateOf(false) }
    val title = rememberSaveable(first) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val submit = rememberUpdatedState(onAdd)
    return remember(first, adding, title, scope) { WeekTaskComposer(adding, title, scope, submit) }
}

// ─── Corner badge ────────────────────────────────────────────────────────────

/**
 * The compact-portrait entry point: a small disc in the day header's empty
 * gutter cell. It counts open tasks; with none it is a dashed "+".
 */
@Composable
internal fun WeekTaskBadge(openTasks: Int, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val empty = openTasks == 0
    Box(
        modifier.size(BadgeTouch)
            .calinoPressable(onClick = onClick)
            .semantics {
                contentDescription = if (empty) "Sometime this week, add a task"
                    else "Sometime this week, $openTasks open ${if (openTasks == 1) "task" else "tasks"}"
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        contentAlignment = Alignment.Center,
    ) {
        val accent = CalinoColors.Accent
        val ink = AccentInk
        Box(
            Modifier.size(BadgeSize)
                .then(
                    if (empty) Modifier.drawBehind {
                        drawCircle(
                            accent,
                            radius = size.minDimension / 2 - 0.75.dp.toPx(),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    } else Modifier.background(CalinoColors.AccentSoft, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (empty) {
                CalinoIcon(CalinoIcon.Plus, tint = ink, modifier = Modifier.size(15.dp), contentDescription = null)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(9.dp).border(1.5.dp, accent, CircleShape))
                    Text(openTasks.toString(), color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

// ─── Shared rows and input ───────────────────────────────────────────────────

@Composable
private fun WeekTaskRows(
    tasks: List<CalTask>,
    onOpen: (CalTask) -> Unit,
    onDone: (CalTask, Boolean) -> Unit,
    onLongClick: (CalTask) -> Unit,
    taskModifier: @Composable (CalTask) -> Modifier,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 8.dp,
) {
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        items(tasks, key = { it.id }) { task ->
            AgendaRow(
                task = task, modifier = taskModifier(task).heightIn(min = 44.dp).animateItem(), compact = true, checkboxTouchSize = 44.dp,
                onLongClick = { onLongClick(task) }, onClick = { onOpen(task) }, onCheckedChange = { onDone(task, it) },
            )
        }
    }
}

/** The one-line title field with an accent underline, its Add button and "More details". */
@Composable
private fun WeekTaskInput(composer: WeekTaskComposer, onDetails: (String) -> Unit, modifier: Modifier = Modifier, autofocus: Boolean = true) {
    val focusRequester = remember { FocusRequester() }
    BackHandler(enabled = composer.adding) { composer.adding = false }
    if (autofocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val accent = CalinoColors.Accent
    Column(modifier.padding(start = 18.dp, end = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                composer.title, { composer.title = it }, enabled = !composer.saving, singleLine = true,
                textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).focusRequester(focusRequester)
                    .semantics { contentDescription = "Week task title" },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { composer.save() }),
                decorationBox = { field ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .drawBehind { drawLine(accent, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (composer.title.isEmpty()) Text(
                            "What would you like to get done?", color = CalinoColors.Ink3,
                            style = CalinoTypography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        field()
                    }
                },
            )
            TextButton(onClick = { composer.save() }, enabled = composer.title.isNotBlank() && !composer.saving) {
                Text(if (composer.saving) "Saving…" else "Add")
            }
        }
        TextButton(onClick = { onDetails(composer.title) }, enabled = !composer.saving) { Text("More details") }
    }
}

@Composable
private fun WeekTaskHeader(
    first: LocalDate, last: LocalDate, count: Int, composer: WeekTaskComposer, showingInput: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text("Sometime this week", style = CalinoTypography.titleSmall.copy(fontSize = 17.sp), color = CalinoColors.Ink)
            Text(weekRangeLabel(first, last, count), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
        }
        Box(
            Modifier.size(40.dp).calinoPressable { composer.adding = !composer.adding }
                .semantics { contentDescription = "Add week task"; stateDescription = if (showingInput) "Expanded" else "Collapsed" },
            contentAlignment = Alignment.Center,
        ) { CalinoIcon(CalinoIcon.Plus, tint = CalinoColors.Ink2, contentDescription = null) }
    }
}

// ─── Popover (corner badge) ──────────────────────────────────────────────────

/**
 * The badge's card: a scrim, a caret under the badge and the week's tasks. With
 * nothing listed it is only the title block and a focused input.
 */
@Composable
internal fun WeekTaskPopover(
    visible: Boolean,
    onDismiss: () -> Unit,
    first: LocalDate, last: LocalDate, tasks: List<CalTask>,
    composer: WeekTaskComposer,
    /** Top edge of the card, in the host's coordinates. */
    anchorTop: Dp,
    onOpen: (CalTask) -> Unit, onDone: (CalTask, Boolean) -> Unit, onLongClick: (CalTask) -> Unit,
    taskModifier: @Composable (CalTask) -> Modifier,
    onDetails: (String) -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible) { onDismiss() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cardMaxHeight = maxHeight * .6f
        AnimatedVisibility(
            visible, enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)), exit = fadeOut(tween(CalinoMotion.ContentExitMillis)),
        ) {
            Box(
                Modifier.fillMaxSize().background(CalinoColors.scrim(.22f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                    .semantics { contentDescription = "Close week tasks"; role = Role.Button; onClick { onDismiss(); true } },
            )
        }
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) +
                scaleIn(tween(CalinoMotion.ContentEnterMillis), initialScale = .92f, transformOrigin = TransformOrigin(.1f, 0f)),
            exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) +
                scaleOut(tween(CalinoMotion.ContentExitMillis), targetScale = .94f, transformOrigin = TransformOrigin(.1f, 0f)),
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = anchorTop).widthIn(max = 320.dp).fillMaxWidth(),
        ) {
            val shape = RoundedCornerShape(18.dp)
            val panel = CalinoColors.Panel
            val edge = CalinoColors.SurfaceBorder
            Column(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                        .shadow(14.dp * CalinoColors.elevationAlpha, shape, clip = false)
                        .clip(shape).background(panel).border(1.dp, edge, shape)
                        .animateContentSize(CalinoMotion.standardSpatial()),
                ) {
                    val inputShown = composer.adding || tasks.isEmpty()
                    WeekTaskHeader(first, last, tasks.size, composer, inputShown)
                    if (tasks.isNotEmpty()) WeekTaskRows(tasks, onOpen, onDone, onLongClick, taskModifier, Modifier.heightIn(max = cardMaxHeight))
                    EditorReveal(inputShown) { WeekTaskInput(composer, onDetails) }
                }
            }
        }
    }
}

// ─── Peek sheet ──────────────────────────────────────────────────────────────

/**
 * A 34dp row that swipes up into the list. The list's height follows the
 * finger and the pill above is lifted with the measured height, so the two
 * move as one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WeekTaskSheet(
    first: LocalDate, last: LocalDate, tasks: List<CalTask>,
    composer: WeekTaskComposer,
    expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    maxWidth: Dp,
    onOpen: (CalTask) -> Unit, onDone: (CalTask, Boolean) -> Unit, onLongClick: (CalTask) -> Unit,
    taskModifier: @Composable (CalTask) -> Modifier,
    onDetails: (String) -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val windowHeight = LocalConfiguration.current.screenHeightDp.dp
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var naturalListPx by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(topStart = CalinoShapes.Card, topEnd = CalinoShapes.Card)
    ImeResizeWhile(composer.adding)

    fun settle(target: Boolean, velocity: Float = 0f) {
        onExpandedChange(target)
        scope.launch { progress.animateTo(if (target) 1f else 0f, CalinoMotion.standardSpatial(), velocity) }
    }
    // Back, the grid's tap-away and a drag that lifts a task all collapse
    // through the same state the gesture writes.
    LaunchedEffect(expanded) {
        if (progress.targetValue != (if (expanded) 1f else 0f) && !progress.isRunning) {
            progress.animateTo(if (expanded) 1f else 0f, CalinoMotion.standardSpatial())
        }
    }
    BackHandler(enabled = expanded) { settle(false) }

    val dragState = rememberDraggableState { delta ->
        val full = naturalListPx.coerceAtLeast(1)
        scope.launch { progress.snapTo((progress.value - delta / full).coerceIn(0f, 1f)) }
    }
    Column(
        modifier.widthIn(max = maxWidth).fillMaxWidth()
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .shadow(14.dp * CalinoColors.elevationAlpha, shape, clip = false)
            .clip(shape).background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.SurfaceBorder, shape),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .draggable(dragState, Orientation.Vertical, onDragStopped = { velocity ->
                    val fling = -velocity / naturalListPx.coerceAtLeast(1)
                    settle(progress.value + fling * .18f > .5f, fling)
                })
                .calinoPressable(role = Role.Button) { settle(!expanded) }
                .semantics { contentDescription = "Sometime this week, sheet"; stateDescription = if (expanded) "Expanded" else "Collapsed" },
        ) {
            Box(Modifier.fillMaxWidth().height(SheetHandleDp.dp), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.size(width = 32.dp, height = 4.dp).background(CalinoColors.Ink.copy(alpha = .18f), CircleShape))
            }
            Row(Modifier.fillMaxWidth().height(SheetPeekRowDp.dp).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sometime this week", style = CalinoTypography.titleSmall.copy(fontSize = 17.sp), color = CalinoColors.Ink, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                Text("${openCount(tasks)} OPEN", color = CalinoColors.Ink3, maxLines = 1,
                    style = CalinoTypography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.1.sp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(width = 44.dp, height = SheetPeekRowDp.dp).calinoPressable { composer.adding = !composer.adding; if (composer.adding) settle(true) }
                        .semantics { contentDescription = "Add week task"; stateDescription = if (composer.adding) "Expanded" else "Collapsed" },
                    contentAlignment = Alignment.Center,
                ) { CalinoIcon(CalinoIcon.Plus, tint = CalinoColors.Ink2, contentDescription = null) }
            }
        }
        // Measured at its full capped height, revealed by the finger's progress.
        Box(
            Modifier.fillMaxWidth().clipToBoundsCompat()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    naturalListPx = placeable.height
                    // The settle spring can overshoot past either end; a layout size can't go negative.
                    val height = (placeable.height * progress.value).roundToInt().coerceIn(0, placeable.height)
                    layout(placeable.width, height) { placeable.place(0, 0) }
                },
        ) {
            WeekTaskRows(tasks, onOpen, onDone, onLongClick, taskModifier, Modifier.heightIn(max = windowHeight * .3f), horizontalPadding = 10.dp)
        }
        EditorReveal(composer.adding) { WeekTaskInput(composer, onDetails) }
        Spacer(Modifier.windowInsetsBottom())
    }
}

private fun Modifier.clipToBoundsCompat(): Modifier = this.clip(androidx.compose.ui.graphics.RectangleShape)

/**
 * Hands the keyboard inset to `imePadding` alone while [active].
 *
 * The activity leaves `windowSoftInputMode` unspecified, so when the keyboard
 * starts to appear over a field that sits at the very bottom of the screen --
 * the docked shelf's -- the system also pans the whole window to reveal it.
 * The pan is computed before `imePadding` has lifted the shelf and is not
 * taken back afterwards, so the shelf was lifted twice and ended up at the top
 * of the screen until the window was laid out again. Every other screen keeps
 * the default, which is why this is scoped to the shelf being typed into.
 */
@Composable
private fun ImeResizeWhile(active: Boolean) {
    var context = LocalContext.current
    while (context is ContextWrapper && context !is Activity) context = context.baseContext
    val window = (context as? Activity)?.window
    DisposableEffect(active, window) {
        if (!active || window == null) return@DisposableEffect onDispose {}
        // Window.setSoftInputMode ignores an unspecified mode, so neither the
        // switch nor the way back goes through it.
        fun apply(mode: Int) {
            val attrs = window.attributes
            attrs.softInputMode = mode
            window.attributes = attrs
        }
        val previous = window.attributes.softInputMode
        apply((previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { apply(previous) }
    }
}

@Composable
private fun Modifier.windowInsetsBottom(): Modifier = this.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)).height(0.dp)

// ─── Strip (landscape / wide) ────────────────────────────────────────────────

/**
 * The web-style strip: a label, a row of chips and a "+", docked under the
 * grid. The whole row is the touch target, so a 24dp chip stays easy to hit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WeekTaskStrip(
    tasks: List<CalTask>, hovering: Boolean, height: Dp,
    composer: WeekTaskComposer,
    onOpen: (CalTask) -> Unit, onDone: (CalTask, Boolean) -> Unit, onLongClick: (CalTask) -> Unit,
    taskModifier: @Composable (CalTask) -> Modifier,
    onDetails: (String) -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tablet = height >= 44.dp
    val chipHeight = if (tablet) 30.dp else 24.dp
    val panel = if (hovering) CalinoColors.AccentSoft else CalinoColors.Panel
    ImeResizeWhile(composer.adding)
    Column(
        modifier.fillMaxWidth().onGloballyPositioned { onBounds(it.boundsInRoot()) }.background(panel)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    ) {
        HorizontalDivider(color = if (hovering) CalinoColors.Accent else CalinoColors.Line)
        Row(
            Modifier.fillMaxWidth().height(height).padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (hovering) "RELEASE TO PLAN FOR THIS WEEK" else "SOMETIME THIS WEEK",
                style = CalinoTypography.labelSmall, color = if (hovering) AccentInk else CalinoColors.Ink3, maxLines = 1, softWrap = false,
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (composer.adding) {
                    StripInput(composer, onDetails)
                } else {
                    LazyRow(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        items(tasks, key = { it.id }) { task ->
                            Box(Modifier.fillMaxHeight().then(taskModifier(task)), contentAlignment = Alignment.Center) {
                                WeekTaskChip(task, chipHeight, onOpen, onDone, onLongClick)
                            }
                        }
                    }
                    Box(
                        Modifier.align(Alignment.CenterEnd).width(32.dp).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, panel))),
                    )
                }
            }
            val ring = if (tablet) 28.dp else 24.dp
            Box(
                Modifier.size(height).calinoPressable { composer.adding = !composer.adding }
                    .semantics { contentDescription = "Add week task"; stateDescription = if (composer.adding) "Expanded" else "Collapsed" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(ring).border(1.dp, CalinoColors.Ink.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                    CalinoIcon(CalinoIcon.Plus, tint = CalinoColors.Ink2, modifier = Modifier.size(14.dp), contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekTaskChip(
    task: CalTask, height: Dp,
    onOpen: (CalTask) -> Unit, onDone: (CalTask, Boolean) -> Unit, onLongClick: (CalTask) -> Unit,
) {
    val color = eventColor(task.color)
    val shape = RoundedCornerShape(CalinoShapes.Pill)
    Row(
        Modifier.height(height).clip(shape).border(1.dp, CalinoColors.Ink.copy(alpha = .16f), shape)
            .combinedClickable(interactionSource = null, indication = null, onClick = { onOpen(task) }, onLongClick = { onLongClick(task) })
            .semantics(mergeDescendants = true) {
                contentDescription = task.title
                stateDescription = if (task.done) "Completed" else "Open"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Completion keeps its own lane, like the list rows.
        Box(
            Modifier.fillMaxHeight().width(WeekChipCompletionLane).calinoPressable(role = Role.Checkbox) { onDone(task, !task.done) }
                .semantics { contentDescription = "${task.title}, checkbox"; stateDescription = if (task.done) "Checked" else "Not checked" },
            contentAlignment = Alignment.CenterEnd,
        ) { TaskCheckbox(task.done, color, Modifier.size(14.dp), circular = true) }
        Text(
            task.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, end = 12.dp).widthIn(max = 220.dp),
            color = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            fontSize = 12.5.sp,
        )
    }
}

/** The chip's leading completion lane; the host's lift gesture leaves it to the tap. */
internal val WeekChipCompletionLane = 30.dp

@Composable
private fun StripInput(composer: WeekTaskComposer, onDetails: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    BackHandler { composer.adding = false }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val accent = CalinoColors.Accent
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            composer.title, { composer.title = it }, enabled = !composer.saving, singleLine = true,
            textStyle = CalinoTypography.bodyMedium.copy(color = CalinoColors.Ink), cursorBrush = SolidColor(accent),
            modifier = Modifier.weight(1f).focusRequester(focusRequester)
                .onFocusChanged {
                    // Losing focus with nothing typed puts the field away.
                    if (focused && !it.isFocused && composer.title.isBlank()) composer.adding = false
                    focused = it.isFocused
                }
                .semantics { contentDescription = "Week task title" },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { composer.save() }),
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth().drawBehind { drawLine(accent, Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 1.5.dp.toPx()) }.padding(vertical = 4.dp)) {
                    if (composer.title.isEmpty()) Text("What would you like to get done?", color = CalinoColors.Ink3, style = CalinoTypography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            },
        )
        if (composer.title.isNotBlank()) TextButton(onClick = { onDetails(composer.title) }, enabled = !composer.saving) { Text("Details") }
        TextButton(onClick = { composer.save() }, enabled = composer.title.isNotBlank() && !composer.saving) { Text(if (composer.saving) "Saving…" else "Add") }
    }
}

// ─── Drop zone ───────────────────────────────────────────────────────────────

/** Offered while a task is being dragged and no shelf can take the drop. */
@Composable
internal fun WeekTaskDropZone(visible: Boolean, hovering: Boolean, height: Dp, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) + slideInVertically(tween(CalinoMotion.ContentEnterMillis)) { it / 2 },
        exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) + slideOutVertically(tween(CalinoMotion.ContentExitMillis)) { it / 2 },
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        val accent = CalinoColors.Accent
        Box(
            Modifier.fillMaxWidth().height(height).clip(shape)
                .background(if (hovering) CalinoColors.AccentSoft else CalinoColors.AccentSoft.copy(alpha = .86f))
                .drawBehind { drawLine(accent, Offset(0f, 0.75.dp.toPx()), Offset(size.width, 0.75.dp.toPx()), 1.5.dp.toPx()) }
                .semantics { contentDescription = "Release to plan for this week" },
        ) {
            Box(Modifier.fillMaxSize().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
                Text("RELEASE TO PLAN FOR THIS WEEK", style = CalinoTypography.labelSmall, color = AccentInk, maxLines = 1)
            }
        }
    }
}
