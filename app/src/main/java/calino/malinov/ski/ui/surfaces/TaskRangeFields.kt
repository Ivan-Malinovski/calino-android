package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.EditorLabel
import calino.malinov.ski.ui.components.EditorReveal
import androidx.compose.foundation.border
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RangeDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)

/**
 * The task's dates, laid out the way the web editor does: the dates first, the
 * presets under them, one line of explanation last.
 *
 * Without a start date the task has one date, so [dueRow] -- the caller's own
 * due-date row, with whatever quick picks it carries -- is shown as it always
 * was. With one, the task is a range, and the two dates sit side by side as
 * *Start* and *Due* instead. [dueRow] gives way then: a "Today" quick pick
 * would set a due date ahead of the start and break the range it sits in.
 * Dates remain ordinary VTODO fields either way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskRangeFields(
    start: LocalDate?, due: LocalDate?, recurring: Boolean,
    onStart: () -> Unit, onDue: () -> Unit, onClearStart: () -> Unit, onWeek: () -> Unit,
    picks: List<Pair<LocalDate, String>> = emptyList(), onPick: (LocalDate) -> Unit = {},
    dueRow: @Composable (startAction: @Composable () -> Unit) -> Unit,
    /**
     * Fold every action behind one chevron at the end of the date line, so the
     * block is a single line until it is opened. [dueRow] then draws only the
     * date, and [onClearDue] backs the "Clear due" action the row no longer shows.
     */
    folded: Boolean = false,
    onClearDue: (() -> Unit)? = null,
) {
    if (folded) {
        FoldedTaskRange(start, due, recurring, onStart, onDue, onClearDue, onClearStart, onWeek, dueRow)
        return
    }
    val ranged = start != null && due != null
    val spansWeek = ranged && due!!.toEpochDay() - start!!.toEpochDay() >= 2
    Column(Modifier.fillMaxWidth().animateContentSize(CalinoMotion.standardSpatial())) {
        EditorReveal(!ranged) {
            dueRow { TaskTextAction("Add start", "Choose task start date", onStart) }
        }
        EditorReveal(ranged) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RangeDateField("Start", start?.format(RangeDateFormat).orEmpty(), "Change task start date", onStart, Modifier.weight(1f))
                RangeDateField("Due", due?.format(RangeDateFormat).orEmpty(), "Change task due date", onDue, Modifier.weight(1f))
            }
        }
        // One row of presets. A single date's quick picks and "This week" are
        // the same kind of answer, so they share a line under the date; a range
        // has only its own two actions.
        FlowRow(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (!ranged) picks.forEach { (date, text) ->
                CalinoChip(
                    text = text, selected = due == date, description = "Set due date",
                    semanticsRole = Role.RadioButton, onClick = { onPick(date) },
                )
            }
            if (!recurring) CalinoChip(
                text = "This week", selected = spansWeek,
                description = "Set task to this week", onClick = onWeek,
            )
            if (ranged) CalinoChip(
                text = "Clear start", selected = false,
                description = "Remove task start date", onClick = onClearStart,
            )
        }
        val hint = rangeHint(start, due, recurring)
        EditorReveal(hint != null) {
            Text(hint.orEmpty(), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun rangeHint(start: LocalDate?, due: LocalDate?, recurring: Boolean): String? {
    val ranged = start != null && due != null
    val spansWeek = ranged && due!!.toEpochDay() - start!!.toEpochDay() >= 2
    return when {
        recurring -> "Repeating tasks keep their start date; week planning is for one-off tasks."
        start != null && due != null && !start.isBefore(due) -> "Start must be before the due date."
        spansWeek -> "Spans 3+ days, so it shows under “Sometime this week” in the week view."
        ranged -> "Under 3 days, so it shows on its due day."
        else -> null
    }
}

/** The date line with every action behind a chevron; see [TaskRangeFields]'s `folded`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoldedTaskRange(
    start: LocalDate?, due: LocalDate?, recurring: Boolean,
    onStart: () -> Unit, onDue: () -> Unit, onClearDue: (() -> Unit)?, onClearStart: () -> Unit, onWeek: () -> Unit,
    dueRow: @Composable (startAction: @Composable () -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (open) 180f else 0f, CalinoMotion.expressiveSpatial(), label = "date actions chevron")
    val ranged = start != null && due != null
    val spansWeek = ranged && due!!.toEpochDay() - start!!.toEpochDay() >= 2
    Column(Modifier.fillMaxWidth().animateContentSize(CalinoMotion.standardSpatial())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (ranged) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RangeDateField("Start", start?.format(RangeDateFormat).orEmpty(), "Change task start date", onStart, Modifier.weight(1f))
                        RangeDateField("Due", due?.format(RangeDateFormat).orEmpty(), "Change task due date", onDue, Modifier.weight(1f))
                    }
                } else {
                    dueRow {}
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CalinoShapes.Row))
                    .clickable(role = Role.Button, onClickLabel = if (open) "Hide date options" else "Show date options") { open = !open }
                    .semantics { contentDescription = if (open) "Hide date options" else "Show date options" },
                contentAlignment = Alignment.Center,
            ) {
                CalinoIcon(CalinoIcon.Down, tint = CalinoColors.Ink3, modifier = Modifier.size(18.dp).rotate(chevron), contentDescription = null)
            }
        }
        EditorReveal(open) {
            FlowRow(Modifier.padding(start = 40.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (!ranged && due != null) {
                    if (onClearDue != null) CalinoChip(
                        text = "Clear due", selected = false, description = "Remove task due date", onClick = onClearDue,
                    )
                    CalinoChip(text = "Add start", selected = false, description = "Choose task start date", onClick = onStart)
                }
                if (!recurring) CalinoChip(
                    text = "This week", selected = spansWeek, description = "Set task to this week", onClick = onWeek,
                )
                if (ranged) CalinoChip(
                    text = "Clear start", selected = false, description = "Remove task start date", onClick = onClearStart,
                )
            }
        }
        val hint = rangeHint(start, due, recurring)
        EditorReveal(hint != null) {
            Text(hint.orEmpty(), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3, modifier = Modifier.padding(start = 40.dp, bottom = 8.dp))
        }
    }
}

/** A quiet text action at the end of a row, beside its "Clear". */
@Composable
internal fun TaskTextAction(text: String, description: String, onClick: () -> Unit) {
    Text(
        text,
        style = CalinoTypography.bodyMedium,
        color = CalinoColors.Ink2,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 12.dp),
    )
}

/** A labelled date that opens its picker; the web editor's date input, as a tappable read-out. */
@Composable
private fun RangeDateField(label: String, value: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CalinoShapes.Row)
    Column(
        modifier
            .heightIn(min = 54.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, CalinoColors.Ink.copy(alpha = .14f)), shape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EditorLabel(label)
        Text(value, style = CalinoTypography.bodyLarge, color = CalinoColors.Ink)
    }
}
