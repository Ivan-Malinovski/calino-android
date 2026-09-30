package calino.malinov.ski.ui.surfaces

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import calino.malinov.ski.design.CalinoMotion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.ui.components.CalinoChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Shared by the quick-add editor and existing task detail; dates remain ordinary VTODO fields. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskRangeFields(
    start: LocalDate?, due: LocalDate?, recurring: Boolean,
    onStart: () -> Unit, onClearStart: () -> Unit, onWeek: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().animateContentSize(CalinoMotion.standardSpatial()).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalinoChip(
                text = start?.let { "From ${it.format(DateTimeFormatter.ofPattern("MMM d"))}" } ?: "Add start",
                modifier = Modifier.heightIn(min = 44.dp), selected = start != null, description = "Choose task start date", onClick = onStart,
            )
            if (!recurring) CalinoChip(text = "This week", modifier = Modifier.heightIn(min = 44.dp), selected = start != null && due != null && due.toEpochDay() - start.toEpochDay() >= 2,
                description = "Set task to this week", onClick = onWeek)
            if (start != null) CalinoChip(text = "Clear", modifier = Modifier.heightIn(min = 44.dp), selected = false, description = "Remove task start date", onClick = onClearStart)
        }
        val hint = when {
            recurring -> "Repeating tasks keep their start date; week planning is for one-off tasks."
            start != null && due != null && !start.isBefore(due) -> "Start must be before due."
            start != null && due != null && due.toEpochDay() - start.toEpochDay() >= 2 -> "Sometime this week · choose a day when you’re ready."
            start != null && due != null -> "Short range · shown on the due day."
            else -> null
        }
        hint?.let { Text(it, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3) }
    }
}
