package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
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
import calino.malinov.ski.R
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.EditorLabel
import calino.malinov.ski.ui.components.EditorReveal
import calino.malinov.ski.util.localizedDateFormatter
import androidx.compose.foundation.border
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val rangeDateFormat = localizedDateFormatter("EEE, d MMM")
    val addStart = stringResource(R.string.ed_task_add_start)
    val chooseStart = stringResource(R.string.ed_task_choose_start)
    val startLabel = stringResource(R.string.ed_task_start)
    val dueLabel = stringResource(R.string.ed_task_due)
    val changeStart = stringResource(R.string.ed_task_change_start)
    val changeDue = stringResource(R.string.ed_task_change_due)
    val setDue = stringResource(R.string.ed_task_set_due)
    val thisWeek = stringResource(R.string.ed_task_this_week)
    val setThisWeek = stringResource(R.string.ed_task_set_this_week)
    val clearStart = stringResource(R.string.ed_task_clear_start)
    val removeStart = stringResource(R.string.ed_task_remove_start)
    val repeatingHint = stringResource(R.string.ed_task_repeat_range_hint)
    val invalidHint = stringResource(R.string.ed_task_start_before_due)
    val weekHint = stringResource(R.string.ed_task_week_range_hint)
    val shortHint = stringResource(R.string.ed_task_short_range_hint)
    val hint = rangeHint(start, due, recurring, repeatingHint, invalidHint, weekHint, shortHint)
    if (folded) {
        FoldedTaskRange(
            start, due, recurring, onStart, onDue, onClearDue, onClearStart, onWeek, dueRow,
            rangeDateFormat, addStart, chooseStart, startLabel, dueLabel, changeStart, changeDue,
            thisWeek, setThisWeek, clearStart, removeStart,
            stringResource(R.string.ed_task_hide_date_options), stringResource(R.string.ed_task_show_date_options),
            stringResource(R.string.ed_task_clear_due), stringResource(R.string.ed_task_remove_due), hint,
        )
        return
    }
    val ranged = start != null && due != null
    val spansWeek = ranged && due!!.toEpochDay() - start!!.toEpochDay() >= 2
    Column(Modifier.fillMaxWidth().animateContentSize(CalinoMotion.standardSpatial())) {
        EditorReveal(!ranged) {
            dueRow { TaskTextAction(addStart, chooseStart, onStart) }
        }
        EditorReveal(ranged) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RangeDateField(startLabel, start?.format(rangeDateFormat).orEmpty(), changeStart, onStart, Modifier.weight(1f))
                RangeDateField(dueLabel, due?.format(rangeDateFormat).orEmpty(), changeDue, onDue, Modifier.weight(1f))
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
                    text = text, selected = due == date, description = setDue,
                    semanticsRole = Role.RadioButton, onClick = { onPick(date) },
                )
            }
            if (!recurring) CalinoChip(
                text = thisWeek, selected = spansWeek,
                description = setThisWeek, onClick = onWeek,
            )
            if (ranged) CalinoChip(
                text = clearStart, selected = false,
                description = removeStart, onClick = onClearStart,
            )
        }
        EditorReveal(hint != null) {
            Text(hint.orEmpty(), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun rangeHint(start: LocalDate?, due: LocalDate?, recurring: Boolean, repeatingHint: String, invalidHint: String, weekHint: String, shortHint: String): String? {
    val ranged = start != null && due != null
    val spansWeek = ranged && due!!.toEpochDay() - start!!.toEpochDay() >= 2
    return when {
        recurring -> repeatingHint
        start != null && due != null && !start.isBefore(due) -> invalidHint
        spansWeek -> weekHint
        ranged -> shortHint
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
    rangeDateFormat: DateTimeFormatter,
    addStart: String, chooseStart: String, startLabel: String, dueLabel: String, changeStart: String, changeDue: String,
    thisWeek: String, setThisWeek: String, clearStart: String, removeStart: String,
    hideOptions: String, showOptions: String, clearDue: String, removeDue: String, hint: String?,
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
                        RangeDateField(startLabel, start?.format(rangeDateFormat).orEmpty(), changeStart, onStart, Modifier.weight(1f))
                        RangeDateField(dueLabel, due?.format(rangeDateFormat).orEmpty(), changeDue, onDue, Modifier.weight(1f))
                    }
                } else {
                    dueRow {}
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CalinoShapes.Row))
                    .clickable(role = Role.Button, onClickLabel = if (open) hideOptions else showOptions) { open = !open }
                    .semantics { contentDescription = if (open) hideOptions else showOptions },
                contentAlignment = Alignment.Center,
            ) {
                CalinoIcon(CalinoIcon.Down, tint = CalinoColors.Ink3, modifier = Modifier.size(18.dp).rotate(chevron), contentDescription = null)
            }
        }
        EditorReveal(open) {
            FlowRow(Modifier.padding(start = 40.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (!ranged && due != null) {
                    if (onClearDue != null) CalinoChip(
                        text = clearDue, selected = false, description = removeDue, onClick = onClearDue,
                    )
                    CalinoChip(text = addStart, selected = false, description = chooseStart, onClick = onStart)
                }
                if (!recurring) CalinoChip(
                    text = thisWeek, selected = spansWeek, description = setThisWeek, onClick = onWeek,
                )
                if (ranged) CalinoChip(
                    text = clearStart, selected = false, description = removeStart, onClick = onClearStart,
                )
            }
        }
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
