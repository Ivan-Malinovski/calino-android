package calino.malinov.ski.ui.range

import calino.malinov.ski.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import calino.malinov.ski.util.localizedDateFormatter
import calino.malinov.ski.util.LocalCalinoLocale

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.semantics.disabled
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
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.EditorReveal
import calino.malinov.ski.ui.components.TaskCheckbox
import calino.malinov.ski.ui.components.calinoPressable
import calino.malinov.ski.ui.components.eventColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** Height of the bottom zone a dragged task is released on; tall enough to clear the add pill with the label above it. */
internal val WeekDropZoneHeight = 124.dp
/** Corner badge diameter, and the touch square around it. */
private val BadgeSize = 30.dp
private val BadgeTouch = 48.dp
/**
 * The popover's left rail: its title, checkbox ring and divider start here.
 * [AgendaRow] keeps 8dp of its own padding and centres a 21dp ring in a 44dp
 * lane, so the ring's edge lands at 19.5dp.
 */
private val PopoverRail = 20.dp

@Composable
private fun weekRangeLabel(first: LocalDate, last: LocalDate, count: Int): String {
    val monthDay = localizedDateFormatter("MMM d")
    return pluralStringResource(R.plurals.cal_week_range_count, count, first.format(monthDay), last.format(monthDay), count)
        .uppercase(LocalCalinoLocale)
}

/** Accent, darkened far enough to read on [CalinoColors.AccentSoft] (and lightened in dark themes). */
private val AccentInk: Color @Composable get() = lerp(CalinoColors.Accent, CalinoColors.Ink, .32f)

/**
 * The shared "add a week task" state, so the popover and the strip
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
    val badgeDescription = if (empty) stringResource(R.string.cal_sometime_this_week_add_task)
        else pluralStringResource(R.plurals.cal_sometime_this_week_open_count, openTasks, openTasks)
    val expandedLabel = stringResource(R.string.cal_expanded)
    val collapsedLabel = stringResource(R.string.cal_collapsed)
    Box(
        modifier.size(BadgeTouch)
            .calinoPressable(onClick = onClick)
            .semantics {
                contentDescription = badgeDescription
                stateDescription = if (expanded) expandedLabel else collapsedLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        val accent = CalinoColors.Accent
        // While its card is open the disc fills with the accent, so the two read as one object.
        val disc by animateColorAsState(
            when {
                expanded -> accent
                empty -> accent.copy(alpha = 0f)
                else -> CalinoColors.AccentSoft
            },
            tween(CalinoMotion.ContentEnterMillis), label = "week badge disc",
        )
        val ink by animateColorAsState(
            if (expanded) CalinoColors.OnAccent else AccentInk, tween(CalinoMotion.ContentEnterMillis), label = "week badge ink",
        )
        val ring by animateColorAsState(
            if (expanded) CalinoColors.OnAccent else accent, tween(CalinoMotion.ContentEnterMillis), label = "week badge ring",
        )
        val dashes by animateFloatAsState(
            if (empty && !expanded) 1f else 0f, tween(CalinoMotion.ContentEnterMillis), label = "week badge dashes",
        )
        Box(
            Modifier.size(BadgeSize)
                .background(disc, CircleShape)
                .drawBehind {
                    if (dashes > 0f) drawCircle(
                        accent.copy(alpha = accent.alpha * dashes),
                        radius = size.minDimension / 2 - 0.75.dp.toPx(),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // The leaving count keeps its last value rather than flashing "0".
            var lastCount by remember { mutableIntStateOf(openTasks) }
            if (openTasks > 0) lastCount = openTasks
            // The plus and the count trade places by fading and scaling through
            // the disc, which is itself changing fill and dashed ring.
            AnimatedContent(
                targetState = empty,
                transitionSpec = {
                    (fadeIn(tween(CalinoMotion.ContentEnterMillis, delayMillis = CalinoMotion.FadeThroughMillis / 2)) +
                        scaleIn(tween(CalinoMotion.ContentEnterMillis, delayMillis = CalinoMotion.FadeThroughMillis / 2), initialScale = .7f))
                        .togetherWith(fadeOut(tween(CalinoMotion.FadeThroughMillis)) + scaleOut(tween(CalinoMotion.FadeThroughMillis), targetScale = .7f))
                        .using(SizeTransform(clip = false))
                },
                label = "week badge content",
            ) { isEmpty ->
                if (isEmpty) {
                    CalinoIcon(CalinoIcon.Plus, tint = ink, modifier = Modifier.size(15.dp), contentDescription = null)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(9.dp).border(1.5.dp, ring, CircleShape))
                        Text(lastCount.toString(), color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
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
    /** A hairline between rows, inset to [PopoverRail]. */
    dividers: Boolean = false,
) {
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
            Column(Modifier.animateItem()) {
                if (dividers && index > 0) HorizontalDivider(Modifier.padding(start = PopoverRail, end = 14.dp), color = CalinoColors.Line)
                AgendaRow(
                    task = task, modifier = taskModifier(task).heightIn(min = 44.dp), compact = true, checkboxTouchSize = 44.dp,
                    onLongClick = { onLongClick(task) }, onClick = { onOpen(task) }, onCheckedChange = { onDone(task, it) },
                )
            }
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
    val titleDescription = stringResource(R.string.cal_week_task_title)
    val placeholder = stringResource(R.string.cal_week_task_placeholder)
    val savingLabel = stringResource(R.string.cal_saving_ellipsis)
    val addLabel = stringResource(R.string.cal_add)
    val moreDetailsLabel = stringResource(R.string.cal_more_details)
    Column(modifier.padding(start = 18.dp, end = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                composer.title, { composer.title = it }, enabled = !composer.saving, singleLine = true,
                textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).focusRequester(focusRequester)
                    .semantics { contentDescription = titleDescription },
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
                            placeholder, color = CalinoColors.Ink3,
                            style = CalinoTypography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        field()
                    }
                },
            )
            TextButton(onClick = { composer.save() }, enabled = composer.title.isNotBlank() && !composer.saving) {
                Text(if (composer.saving) savingLabel else addLabel)
            }
        }
        TextButton(onClick = { onDetails(composer.title) }, enabled = !composer.saving) { Text(moreDetailsLabel) }
    }
}

/**
 * The popover's composer: a filled pill with the Add button inside it, and
 * "More details" as a quiet link underneath.
 */
@Composable
private fun WeekTaskPillInput(composer: WeekTaskComposer, onDetails: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    BackHandler(enabled = composer.adding) { composer.adding = false }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val canAdd = composer.title.isNotBlank() && !composer.saving
    val titleDescription = stringResource(R.string.cal_week_task_title)
    val placeholder = stringResource(R.string.cal_week_task_placeholder)
    val savingTaskLabel = stringResource(R.string.cal_saving_task)
    val addTaskLabel = stringResource(R.string.cal_add_task)
    val moreDetailsLabel = stringResource(R.string.cal_more_details)
    val shape = RoundedCornerShape(CalinoShapes.Pill)
    val fill by animateColorAsState(if (canAdd) CalinoColors.Accent else CalinoColors.AccentSoft, tween(CalinoMotion.ContentEnterMillis), label = "add fill")
    val tint by animateColorAsState(if (canAdd) CalinoColors.OnAccent else CalinoColors.Ink3, tween(CalinoMotion.ContentEnterMillis), label = "add tint")
    Column(modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape)
                .background(CalinoColors.Side, shape).border(1.dp, CalinoColors.Line2, shape)
                .padding(start = 19.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                composer.title, { composer.title = it }, enabled = !composer.saving, singleLine = true,
                textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink),
                cursorBrush = SolidColor(CalinoColors.Accent),
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
                    .semantics { contentDescription = titleDescription },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { composer.save() }),
                decorationBox = { field ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (composer.title.isEmpty()) Text(
                            placeholder, color = CalinoColors.Ink3,
                            style = CalinoTypography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        field()
                    }
                },
            )
            Box(
                Modifier.size(44.dp)
                    .calinoPressable(enabled = canAdd) { composer.save() }
                    .semantics {
                        contentDescription = if (composer.saving) savingTaskLabel else addTaskLabel
                        if (!canAdd) disabled()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(34.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(CalinoIcons.ArrowUp, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(end = 2.dp).heightIn(min = 44.dp)
                .calinoPressable(enabled = !composer.saving) { onDetails(composer.title) }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End,
        ) {
            Text(moreDetailsLabel, color = CalinoColors.Ink2, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Icon(CalinoIcons.ChevronRight, contentDescription = null, tint = CalinoColors.Ink2, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun WeekTaskHeader(
    first: LocalDate, last: LocalDate, count: Int, composer: WeekTaskComposer, showAdd: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(start = PopoverRail, end = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(top = 14.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.cal_sometime_this_week), style = CalinoTypography.titleSmall.copy(fontSize = 19.sp), color = CalinoColors.Ink)
            Text(weekRangeLabel(first, last, count), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
        }
        if (showAdd) {
            // The plus turns to a cross while the composer is open, so it says what a second tap does.
            val turn by animateFloatAsState(if (composer.adding) 45f else 0f, tween(CalinoMotion.ContentEnterMillis), label = "add turn")
            val addWeekTaskLabel = stringResource(R.string.cal_add_week_task)
            val expandedLabel = stringResource(R.string.cal_expanded)
            val collapsedLabel = stringResource(R.string.cal_collapsed)
            Box(
                Modifier.size(44.dp).calinoPressable { composer.adding = !composer.adding }
                    .semantics { contentDescription = addWeekTaskLabel; stateDescription = if (composer.adding) expandedLabel else collapsedLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(34.dp).background(CalinoColors.AccentSoft, CircleShape), contentAlignment = Alignment.Center) {
                    CalinoIcon(CalinoIcon.Plus, tint = AccentInk, modifier = Modifier.size(18.dp).rotate(turn), contentDescription = null)
                }
            }
        }
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
    val closeDescription = stringResource(R.string.cal_close_week_tasks)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cardMaxHeight = maxHeight * .6f
        AnimatedVisibility(
            visible, enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)), exit = fadeOut(tween(CalinoMotion.ContentExitMillis)),
        ) {
            Box(
                Modifier.fillMaxSize().background(CalinoColors.scrim(.22f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                    .semantics { contentDescription = closeDescription; role = Role.Button; onClick { onDismiss(); true } },
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
            val shape = RoundedCornerShape(CalinoShapes.Card)
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
                    // With nothing listed the composer is always shown, so the header has no plus to offer.
                    WeekTaskHeader(first, last, tasks.size, composer, showAdd = tasks.isNotEmpty())
                    if (tasks.isNotEmpty()) {
                        WeekTaskRows(
                            tasks, onOpen, onDone, onLongClick, taskModifier, Modifier.heightIn(max = cardMaxHeight),
                            horizontalPadding = 0.dp, dividers = true,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    EditorReveal(inputShown) { WeekTaskPillInput(composer, onDetails) }
                }
            }
        }
    }
}

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
    val stripLabel = stringResource(if (hovering) R.string.cal_release_week_caps else R.string.cal_sometime_week_caps)
    val addWeekTaskLabel = stringResource(R.string.cal_add_week_task)
    val expandedLabel = stringResource(R.string.cal_expanded)
    val collapsedLabel = stringResource(R.string.cal_collapsed)
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
                stripLabel,
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
                    .semantics { contentDescription = addWeekTaskLabel; stateDescription = if (composer.adding) expandedLabel else collapsedLabel },
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
    val completionState = stringResource(if (task.done) R.string.cal_completed else R.string.cal_open)
    val checkboxDescription = stringResource(R.string.cal_checkbox_for_task, task.title)
    val checkedState = stringResource(if (task.done) R.string.cal_checked else R.string.cal_not_checked)
    Row(
        Modifier.height(height).clip(shape).border(1.dp, CalinoColors.Ink.copy(alpha = .16f), shape)
            .combinedClickable(interactionSource = null, indication = null, onClick = { onOpen(task) }, onLongClick = { onLongClick(task) })
            .semantics(mergeDescendants = true) {
                contentDescription = task.title
                stateDescription = completionState
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Completion keeps its own lane, like the list rows.
        Box(
            Modifier.fillMaxHeight().width(WeekChipCompletionLane).calinoPressable(role = Role.Checkbox) { onDone(task, !task.done) }
                .semantics { contentDescription = checkboxDescription; stateDescription = checkedState },
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
    val titleDescription = stringResource(R.string.cal_week_task_title)
    val placeholder = stringResource(R.string.cal_week_task_placeholder)
    val detailsLabel = stringResource(R.string.cal_details)
    val savingLabel = stringResource(R.string.cal_saving_ellipsis)
    val addLabel = stringResource(R.string.cal_add)
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
                .semantics { contentDescription = titleDescription },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { composer.save() }),
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth().drawBehind { drawLine(accent, Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 1.5.dp.toPx()) }.padding(vertical = 4.dp)) {
                    if (composer.title.isEmpty()) Text(placeholder, color = CalinoColors.Ink3, style = CalinoTypography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            },
        )
        if (composer.title.isNotBlank()) TextButton(onClick = { onDetails(composer.title) }, enabled = !composer.saving) { Text(detailsLabel) }
        TextButton(onClick = { composer.save() }, enabled = composer.title.isNotBlank() && !composer.saving) { Text(if (composer.saving) savingLabel else addLabel) }
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
        val releaseDescription = stringResource(R.string.cal_release_plan_week)
        val releaseLabel = stringResource(R.string.cal_release_plan_week_caps)
        val accent = CalinoColors.Accent
        Box(
            Modifier.fillMaxWidth().height(height).clip(shape)
                .background(if (hovering) CalinoColors.AccentSoft else CalinoColors.AccentSoft.copy(alpha = .86f))
                .drawBehind { drawLine(accent, Offset(0f, 0.75.dp.toPx()), Offset(size.width, 0.75.dp.toPx()), 1.5.dp.toPx()) }
                .semantics { contentDescription = releaseDescription },
        ) {
            Box(Modifier.fillMaxSize().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
                Text(releaseLabel, style = CalinoTypography.labelSmall, color = AccentInk, maxLines = 1)
            }
        }
    }
}
