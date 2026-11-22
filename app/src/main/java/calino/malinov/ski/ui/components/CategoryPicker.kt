package calino.malinov.ski.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import calino.malinov.ski.R

/**
 * The one category control: a row of chips that are either radio buttons (a
 * record belongs to one category) or checkboxes (it belongs to several). Events
 * and tasks pick one; journal entries, like the web's tags, pick several.
 *
 * The toggle itself is the caller's: an editor draft keeps its automatic
 * categories in step with the person's choice, and a record being edited keeps
 * only its own list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<String>,
    isSelected: (String) -> Boolean,
    single: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (single) {
        stringResource(R.string.ed_editor_category)
    } else {
        stringResource(R.string.ed_editor_categories)
    }
    Column(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditorLabel(label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            categories.forEach { category ->
                CalinoChip(
                    text = category,
                    selected = isSelected(category),
                    // Named: a row of chips that all read "Toggle category" gives
                    // a screen reader no way to tell them apart.
                    description = if (single) {
                        stringResource(R.string.ed_editor_choose_category_named, category)
                    } else {
                        stringResource(R.string.ed_editor_toggle_category_named, category)
                    },
                    semanticsRole = if (single) Role.RadioButton else Role.Checkbox,
                    onClick = { onToggle(category) },
                )
            }
        }
    }
}
