package calino.malinov.ski.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.spanLengthDays
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.eventTint
import calino.malinov.ski.design.priorityLabel
import calino.malinov.ski.design.priorityStripeColor
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.ui.surfaces.EventMenuAction
import calino.malinov.ski.ui.surfaces.TaskMenuAction
import calino.malinov.ski.util.AllDayBandLayout
import calino.malinov.ski.util.AllDayItem
import calino.malinov.ski.util.AllDayPlacement
import calino.malinov.ski.util.CalinoTimeFormat
import java.time.LocalDate

/** Narrow: the range surfaces, 3-7 columns share the width. Wide: the single-day rail. */
enum class AllDayBandDensity { Narrow, Wide }

// Range view only: keep the all-day/task shelf subordinate to the timed grid.
// 20dp is ~30% shorter than the old 28dp lanes; the wide day rail retains its
// roomier sizing below.
private val NarrowLaneHeight = 20.dp
private val WideLaneHeight = 22.dp
private val NarrowOverflowHeight = 16.dp
private val WideOverflowHeight = 44.dp
private val WideChipMinWidth = 150.dp
private val WideChipGap = 6.dp
private val NarrowChipFontSize = 9.sp
private val WideChipFontSize = 13.sp

/**
 * The strip above the hour rail: multi-day events as one chip spanning their
 * columns, tasks due as a visually distinct outlined chip, both past two
 * lanes rolling into a tappable overflow row that expands the band in place.
 *
 * The caller owns [layout] (so it can `remember` it on its own keys) and
 * [expanded] (so it survives paging); this composable only draws. A custom
 * [Layout] places every child on the same day-column grid [gutterWidth] and
 * [columnGap] describe -- a weighted `Row` cannot host a chip spanning
 * several sibling columns, which is what the band replaces.
 */
@Composable
fun AllDayBand(
    days: List<LocalDate>,
    layout: AllDayBandLayout,
    density: AllDayBandDensity,
    gutterWidth: Dp,
    columnGap: Dp,
    edgeWidth: Dp = CalinoSpacing.LaneEdge,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEventClick: (LocalDate, CalEvent) -> Unit,
    onEventAction: (EventMenuAction, CalEvent) -> Unit,
    onTaskClick: (CalTask) -> Unit,
    onTaskAction: (TaskMenuAction, CalTask) -> Unit,
    onTaskDone: (CalTask, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (layout.placements.isEmpty() && layout.overflow.isEmpty()) return
    val timeFormat = LocalTimeFormat
    val laneHeight = if (density == AllDayBandDensity.Narrow) NarrowLaneHeight else WideLaneHeight
    val rowGap = CalinoSpacing.LaneRowGap
    val laneCount = layout.laneCount.coerceAtLeast(0)

    Column(modifier.fillMaxWidth().animateContentSize(CalinoMotion.standardSpatial())) {
        if (laneCount > 0) {
            Layout(
                content = {
                    layout.placements.forEach { placement ->
                        when (val item = placement.item) {
                            is AllDayItem.Event -> AllDayEventChip(
                                placement = placement,
                                event = item.event,
                                density = density,
                                timeFormat = timeFormat,
                                onClick = { onEventClick(item.occurrenceStart, item.event) },
                                onLongClick = { onEventAction(EventMenuAction.Edit, item.event) },
                            )

                            is AllDayItem.Task -> AllDayTaskChip(
                                task = item.task,
                                density = density,
                                onClick = { onTaskClick(item.task) },
                                onLongClick = { onTaskAction(TaskMenuAction.Edit, item.task) },
                                onDone = { done -> onTaskDone(item.task, done) },
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { measurables, constraints ->
                val gutterPx = gutterWidth.roundToPx()
                val edgePx = edgeWidth.roundToPx()
                val gapPx = columnGap.roundToPx()
                val columnCount = days.size.coerceAtLeast(1)
                val available = (constraints.maxWidth - gutterPx - edgePx - gapPx * (columnCount - 1)).coerceAtLeast(0)
                val columnWidth = available / columnCount
                val laneHeightPx = laneHeight.roundToPx()
                val rowGapPx = rowGap.roundToPx()

                if (density == AllDayBandDensity.Wide) {
                    // A single day is one column, but a whole phone's width --
                    // once chips are this short, stacking every lane full-width
                    // wastes most of it. Pack lanes two to a row instead
                    // whenever there's room for a second chip beside the
                    // first; a lone item left over on the last row still
                    // spans the full width rather than sitting stranded at
                    // half width.
                    // The caller's columnGap is 0 here (there's only one day
                    // column), so side-by-side chips need their own gap
                    // rather than inheriting that.
                    val wideGapPx = maxOf(gapPx, WideChipGap.roundToPx())
                    val itemCount = layout.placements.size
                    val itemsPerRow = (available / WideChipMinWidth.roundToPx().coerceAtLeast(1)).coerceIn(1, 2)
                    val rowCount = if (itemsPerRow > 0) (itemCount + itemsPerRow - 1) / itemsPerRow else 0
                    val rowCellWidth = IntArray(rowCount) { row ->
                        val isLast = row == rowCount - 1
                        val count = if (isLast) itemCount - row * itemsPerRow else itemsPerRow
                        if (count <= 0) available else ((available - wideGapPx * (count - 1)) / count).coerceAtLeast(0)
                    }
                    val totalHeight = laneHeightPx * rowCount + rowGapPx * (rowCount - 1).coerceAtLeast(0)

                    val placed = measurables.mapIndexed { index, measurable ->
                        val placement = layout.placements[index]
                        val row = placement.lane / itemsPerRow
                        val cellWidth = rowCellWidth.getOrElse(row) { available }
                        measurable.measure(Constraints.fixed(cellWidth, laneHeightPx)) to (placement to row)
                    }

                    layout(constraints.maxWidth, totalHeight) {
                        placed.forEach { (placeable, pair) ->
                            val (placement, row) = pair
                            val col = placement.lane % itemsPerRow
                            val cellWidth = rowCellWidth.getOrElse(row) { available }
                            val x = gutterPx + col * (cellWidth + wideGapPx)
                            val y = row * (laneHeightPx + rowGapPx)
                            placeable.placeRelative(x, y)
                        }
                    }
                } else {
                    val totalHeight = laneHeightPx * laneCount + rowGapPx * (laneCount - 1).coerceAtLeast(0)

                    val placed = measurables.mapIndexed { index, measurable ->
                        val placement = layout.placements[index]
                        val cols = (placement.endColumn - placement.startColumn + 1).coerceAtLeast(1)
                        val width = (columnWidth * cols + gapPx * (cols - 1)).coerceAtLeast(0)
                        measurable.measure(Constraints.fixed(width, laneHeightPx)) to placement
                    }

                    layout(constraints.maxWidth, totalHeight) {
                        placed.forEach { (placeable, placement) ->
                            val x = gutterPx + placement.startColumn * (columnWidth + gapPx)
                            val y = placement.lane * (laneHeightPx + rowGapPx)
                            placeable.placeRelative(x, y)
                        }
                    }
                }
            }
        }
        if (expanded || layout.overflow.isNotEmpty()) {
            AllDayOverflowRow(
                expanded = expanded,
                overflowEventCount = layout.overflowEventCount,
                overflowTaskCount = layout.overflowTaskCount,
                gutterWidth = gutterWidth,
                density = density,
                onToggle = { onExpandedChange(!expanded) },
            )
        }
    }
}

private enum class ContinuationEdge { Start, End }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllDayEventChip(
    placement: AllDayPlacement,
    event: CalEvent,
    density: AllDayBandDensity,
    timeFormat: CalinoTimeFormat,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val rawColor = Color(event.color)
    val edgeColor = CalinoColors.forEvent(rawColor)
    val chipRadius = CalinoShapes.Chip
    val shape = RoundedCornerShape(
        topStart = if (placement.continuesBefore) 0.dp else chipRadius,
        bottomStart = if (placement.continuesBefore) 0.dp else chipRadius,
        topEnd = if (placement.continuesAfter) 0.dp else chipRadius,
        bottomEnd = if (placement.continuesAfter) 0.dp else chipRadius,
    )
    val fontSize = if (density == AllDayBandDensity.Narrow) NarrowChipFontSize else WideChipFontSize
    val description = buildString {
        append(eventDescription(event, timeFormat))
        val totalDays = event.spanLengthDays() + 1
        if (totalDays > 1) append(", ").append(totalDays).append(" days")
        if (placement.continuesBefore) append(", continues before")
        if (placement.continuesAfter) append(", continues after")
    }
    Row(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(eventTint(rawColor, .12f, CalinoColors.Panel), shape)
            .border(1.dp, edgeColor.copy(alpha = .16f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (placement.continuesBefore) {
            ContinuationMarker(ContinuationEdge.Start, edgeColor, Modifier.width(6.dp).fillMaxHeight())
        } else {
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .width(2.dp)
                    .fillMaxHeight(.6f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(edgeColor),
            )
        }
        Text(
            event.title,
            Modifier.weight(1f).padding(horizontal = 4.dp),
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = CalinoColors.Ink,
        )
        if (placement.continuesAfter) {
            ContinuationMarker(ContinuationEdge.End, edgeColor, Modifier.width(6.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun ContinuationMarker(edge: ContinuationEdge, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            when (edge) {
                ContinuationEdge.Start -> {
                    moveTo(w, 0f)
                    lineTo(0f, h / 2f)
                    lineTo(w, h)
                }

                ContinuationEdge.End -> {
                    moveTo(0f, 0f)
                    lineTo(w, h / 2f)
                    lineTo(0f, h)
                }
            }
            close()
        }
        drawPath(path, color.copy(alpha = .6f))
    }
}

/**
 * No outer `mergeDescendants` here on purpose: the checkbox glyph below keeps
 * its own explicit description and click target, exactly the pair of strings
 * -- `"Complete \${task.title}"` / `"Mark \${task.title} open"` -- the range
 * surface's original code and `TaskInteractionTest` depend on. Merging the
 * whole chip into one semantics node would fold that target into the chip's
 * own click action instead of leaving it independently tappable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllDayTaskChip(
    task: CalTask,
    density: AllDayBandDensity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDone: (Boolean) -> Unit,
) {
    val color = eventColor(task.color)
    // Hoisted once: draw scopes cannot read the palette's composition local.
    val checkColor = CalinoColors.OnAccent
    val stripeColor = priorityStripeColor(task.priority)
    val shape = RoundedCornerShape(CalinoShapes.Chip)
    val narrow = density == AllDayBandDensity.Narrow
    val fontSize = if (narrow) NarrowChipFontSize else WideChipFontSize
    // Narrow lanes are already below the app's 44dp hit-target floor, so the
    // checkbox itself keeps its size -- shrinking the one real tap target
    // further to buy text width would trade accessibility for legibility.
    // The width instead comes out of the chrome around it: tighter row
    // padding and a tighter priority-stripe gap. The checkbox alone used to
    // eat most of a ~45dp 7-day column and the title never got more than
    // three or four characters before its ellipsis.
    val glyphSize = if (narrow) 18.dp else 28.dp
    val rowPadding = if (narrow) 2.dp else 4.dp
    val stripeGap = if (narrow) 1.dp else 3.dp
    // The tap target already carries its own centering padding around the
    // dot (glyphSize is bigger than the dot itself); a second padding here on
    // top of it is what pushed the title so far right.
    val textStartPadding = if (narrow) 0.dp else 1.dp
    // The dot is drawn small in the middle of a larger tap target, which
    // leaves visible dead space on its trailing edge. Pull the title back
    // into that space rather than starting it at the tap target's own edge.
    val textPullLeft = if (narrow) 3.5.dp else 6.dp
    // Not merged with descendants: the checkbox below keeps its own,
    // independently queryable description and click target rather than being
    // folded into this row's.
    val description = buildString {
        append(task.title)
        task.due?.let { append(", due ").append(it) }
        priorityLabel(task.priority)?.let { append(", ").append(it) }
    }
    Row(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .border(1.dp, color.copy(alpha = .30f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description }
            .padding(horizontal = rowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stripeColor != null) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight(.7f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(stripeColor),
            )
            Spacer(Modifier.width(stripeGap))
        }
        // Drawn rather than set as glyph text: the "○"/"✓" characters carry
        // uneven side bearings in the app's fonts, so centering them with
        // `textAlign` left the dot looking off-center in its own tap target.
        // A `Canvas` circle centers exactly on the box regardless of font.
        Box(
            Modifier
                .size(glyphSize)
                .clickable { onDone(!task.done) }
                .semantics { contentDescription = if (task.done) "Mark ${task.title} open" else "Complete ${task.title}" },
            contentAlignment = Alignment.Center,
        ) {
            val dotSize = if (narrow) 7.dp else 12.dp
            Canvas(Modifier.size(dotSize)) {
                if (task.done) {
                    drawCircle(color = color)
                    val check = Path().apply {
                        moveTo(size.width * .26f, size.height * .55f)
                        lineTo(size.width * .44f, size.height * .74f)
                        lineTo(size.width * .76f, size.height * .32f)
                    }
                    drawPath(
                        check,
                        checkColor,
                        style = Stroke(width = size.minDimension * .14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                } else {
                    val strokeWidth = size.minDimension * .12f
                    drawCircle(color = color, radius = (size.minDimension - strokeWidth) / 2f, style = Stroke(strokeWidth))
                }
            }
        }
        Text(
            task.title,
            Modifier
                .weight(1f)
                .offset(x = -textPullLeft, y = if (narrow) (-0.5).dp else 0.dp)
                .padding(start = textStartPadding),
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (task.done) CalinoColors.Ink3 else CalinoColors.Ink,
            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
        )
    }
}

@Composable
private fun AllDayOverflowRow(
    expanded: Boolean,
    overflowEventCount: Int,
    overflowTaskCount: Int,
    gutterWidth: Dp,
    density: AllDayBandDensity,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = tween(180),
        label = "all-day band overflow chevron",
    )
    val overflowTotal = overflowEventCount + overflowTaskCount
    val summary = buildList {
        if (overflowEventCount > 0) add("$overflowEventCount ${if (overflowEventCount == 1) "event" else "events"}")
        if (overflowTaskCount > 0) add("$overflowTaskCount ${if (overflowTaskCount == 1) "task" else "tasks"}")
    }.joinToString(", ")
    val label = if (expanded) "Show less" else "$overflowTotal more · $summary"
    val description = if (expanded) "Show fewer all-day items" else "Show $overflowTotal more all-day items"
    val rowHeight = if (density == AllDayBandDensity.Narrow) NarrowOverflowHeight else WideOverflowHeight

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = gutterWidth, end = CalinoSpacing.LaneEdge)
            .heightIn(min = rowHeight, max = rowHeight)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 11.sp, color = CalinoColors.Ink3)
        Icon(
            CalinoIcons.ChevronRight,
            contentDescription = null,
            tint = CalinoColors.Ink3,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevronRotation },
        )
    }
}
