package calino.malinov.ski.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import calino.malinov.ski.R
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoTypography

/** The expanded face of the existing action pill, never a separate window. */
@Composable
internal fun UnsavedChangesPillContent(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
    canSave: Boolean,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val question = stringResource(R.string.cal_unsaved_changes_title)
    val discardDescription = stringResource(R.string.cal_discard_changes_description)
    val keepFocus = remember { FocusRequester() }
    LaunchedEffect(interactive) { if (interactive) keepFocus.requestFocus() }
    Column(
        modifier.width(312.dp).heightIn(max = 360.dp)
            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics { })
            .semantics { paneTitle = question }
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(question, style = CalinoTypography.headlineSmall, color = CalinoColors.OnFloat)
        Text(
            stringResource(R.string.cal_unsaved_changes_message),
            modifier = Modifier.padding(top = 8.dp),
            style = CalinoTypography.bodySmall,
            color = CalinoColors.OnFloat.copy(alpha = .8f),
        )
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onDiscard,
                enabled = interactive,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { contentDescription = discardDescription },
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, CalinoColors.OnFloat.copy(alpha = .5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CalinoColors.OnFloat,
                    disabledContentColor = CalinoColors.OnFloat,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) { Text(stringResource(R.string.cal_discard_changes), style = CalinoTypography.labelLarge) }
            TextButton(
                onClick = onSave,
                enabled = canSave && interactive,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = CalinoColors.SaveFill,
                    contentColor = CalinoColors.OnSave,
                    disabledContainerColor = if (canSave) CalinoColors.SaveFill else CalinoColors.SaveFill.copy(alpha = .3f),
                    disabledContentColor = CalinoColors.OnSave.copy(alpha = if (canSave) 1f else .45f),
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) { Text(stringResource(R.string.cal_save_changes), style = CalinoTypography.labelLarge) }
        }
        TextButton(
            onClick = onKeepEditing,
            enabled = interactive,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp).focusRequester(keepFocus),
            colors = ButtonDefaults.textButtonColors(
                contentColor = CalinoColors.OnFloat,
                disabledContentColor = CalinoColors.OnFloat,
            ),
        ) { Text(stringResource(R.string.cal_keep_editing), style = CalinoTypography.labelLarge) }
    }
}
