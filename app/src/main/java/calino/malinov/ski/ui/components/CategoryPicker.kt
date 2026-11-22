package calino.malinov.ski.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calino.malinov.ski.R
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography

/**
 * The one category control: a row of chips that are either radio buttons (a
 * record belongs to one category) or checkboxes (it belongs to several). Events
 * and tasks pick one; journal entries, like the web's tags, pick several.
 *
 * The toggle itself is the caller's: an editor draft keeps its automatic
 * categories in step with the person's choice, and a record being edited keeps
 * only its own list.
 *
 * [onAddTag] turns the control into the web's tag editor as well: a line where a
 * tag that is not in the list yet can be typed. Without it the vocabulary stays
 * closed, which is what an event or task wants.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<String>,
    isSelected: (String) -> Boolean,
    single: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddTag: ((String) -> Unit)? = null,
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
        if (onAddTag != null) NewTagField(onAddTag)
    }
}

/** "Add a tag" — the web's journal tag input, sharing the chips' vocabulary. */
@Composable
private fun NewTagField(onAddTag: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val fieldDescription = stringResource(R.string.ed_journal_add_tag)
    val addDescription = stringResource(R.string.ed_journal_add_tag_action)
    fun commit() {
        val tag = draft.trim()
        if (tag.isNotEmpty()) {
            onAddTag(tag)
            draft = ""
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = CalinoTypography.bodyMedium.copy(color = CalinoColors.Ink),
            cursorBrush = SolidColor(CalinoColors.Accent),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(CalinoShapes.Chip))
                .background(CalinoColors.Ink.copy(alpha = .05f))
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .semantics { contentDescription = fieldDescription },
            decorationBox = { innerTextField ->
                if (draft.isEmpty()) {
                    Text(
                        stringResource(R.string.ed_journal_add_tag),
                        style = CalinoTypography.bodyMedium,
                        color = CalinoColors.Ink3,
                    )
                }
                innerTextField()
            },
        )
        Text(
            stringResource(R.string.ed_journal_add_tag_action),
            style = CalinoTypography.labelMedium,
            color = if (draft.isBlank()) CalinoColors.Ink3 else CalinoColors.Accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(CalinoShapes.Chip))
                .clickable(enabled = draft.isNotBlank(), role = Role.Button) { commit() }
                .semantics { contentDescription = addDescription }
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}
