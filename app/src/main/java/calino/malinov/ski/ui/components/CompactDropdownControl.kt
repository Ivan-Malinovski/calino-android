package calino.malinov.ski.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoTypography

/** A compact painted selector inside a full touch lane, with an anchored menu. */
@Composable
fun CompactDropdownControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    semanticLabel: String,
    modifier: Modifier = Modifier,
    optionDescriptions: List<String> = options,
) {
    if (options.isEmpty()) return
    val selectedOption = selectedIndex.coerceIn(options.indices)
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        CalinoMotion.standardSpatial(),
        label = "dropdown chevron",
    )
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Box(
            Modifier.heightIn(min = 48.dp)
                .calinoPressable(role = Role.Button, onClick = { expanded = !expanded })
                .semantics(mergeDescendants = true) {
                    contentDescription = semanticLabel
                    stateDescription = options[selectedOption]
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.height(34.dp).clip(shape)
                    .background(CalinoColors.Panel)
                    .border(1.dp, CalinoColors.Line, shape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedContent(
                    targetState = options[selectedOption],
                    transitionSpec = {
                        fadeIn(tween(CalinoMotion.ContentEnterMillis)) togetherWith
                            fadeOut(tween(CalinoMotion.FadeThroughMillis))
                    },
                    label = "dropdown selection",
                ) { option ->
                    Text(option, style = CalinoTypography.labelMedium, color = CalinoColors.Ink, maxLines = 1)
                }
                Icon(CalinoIcons.ChevronDown, contentDescription = null, tint = CalinoColors.Ink2,
                    modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = chevronRotation })
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = shape,
            containerColor = CalinoColors.Panel,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp * CalinoColors.elevationAlpha,
            border = BorderStroke(1.dp, CalinoColors.Line),
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, color = CalinoColors.Ink, style = CalinoTypography.bodyMedium) },
                    trailingIcon = {
                        if (index == selectedOption) Icon(CalinoIcons.Check, contentDescription = null,
                            tint = CalinoColors.Accent, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = optionDescriptions.getOrElse(index) { option }
                        selected = index == selectedOption
                    },
                    onClick = {
                        expanded = false
                        if (index != selectedOption) onSelected(index)
                    },
                )
            }
        }
    }
}
