package calino.malinov.ski.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography

/** The compact search control shared by Contacts and Settings. */
@Composable
fun CalinoSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    placeholder: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
) {
    val focused = remember { mutableStateOf(false) }
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(CalinoShapes.Pill))
            .background(CalinoColors.Panel)
            .border(
                1.dp,
                if (focused.value) CalinoColors.Accent.copy(.35f) else CalinoColors.Line,
                RoundedCornerShape(CalinoShapes.Pill),
            )
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CalinoIcons.Search, contentDescription = null, tint = CalinoColors.Ink3, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(start = 9.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink3)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = inputModifier.fillMaxWidth()
                    .onFocusChanged { focused.value = it.isFocused }
                    .semantics { this.contentDescription = contentDescription },
                singleLine = true,
                textStyle = CalinoTypography.bodyMedium.copy(color = CalinoColors.Ink),
                cursorBrush = SolidColor(CalinoColors.Accent),
            )
        }
    }
}
