package calino.malinov.ski.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoPalette
import calino.malinov.ski.design.CalinoThemes
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * The editor's shared controls. They exist so the long editor form reuses one
 * set of geometries rather than restating the field, chip and card treatments
 * that were previously copy-pasted per surface.
 */

private val FieldColors: @Composable () -> androidx.compose.material3.TextFieldColors = {
    TextFieldDefaults.colors(
        focusedContainerColor = CalinoColors.Canvas,
        unfocusedContainerColor = CalinoColors.Canvas,
        focusedIndicatorColor = CalinoColors.Accent,
        unfocusedIndicatorColor = CalinoColors.Ink.copy(.09f),
        // Material's default error container is off-palette lavender. Keep the
        // field on the Calino canvas and let the indicator carry the error.
        errorContainerColor = CalinoColors.Canvas,
        errorIndicatorColor = CalinoColors.Rose,
        errorLabelColor = CalinoColors.Rose,
        errorSupportingTextColor = CalinoColors.Rose,
        errorCursorColor = CalinoColors.Rose,
    )
}

@Composable
fun CalinoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    description: String = label,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 5,
    textStyle: androidx.compose.ui.text.TextStyle = CalinoTypography.bodyLarge,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = errorText?.let { "$description, $it" } ?: description
        },
        textStyle = textStyle,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = CalinoColors.Ink3) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it, style = CalinoTypography.bodySmall) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = FieldColors(),
    )
}

/**
 * The pill used for parsed values, categories, attendees and related records.
 * [selected] carries the accent treatment the parsed chips introduced.
 */
@Composable
fun CalinoChip(
    text: String,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    semanticsRole: Role = Role.Button,
) {
    // Selection can change without a tap -- keyword rules select a category
    // as the title is typed -- so it fades rather than snapping.
    val fade = tween<Color>(CalinoMotion.ContentEnterMillis)
    val fill by animateColorAsState(
        if (selected) CalinoColors.Accent.copy(.12f) else CalinoColors.Ink.copy(.05f), fade, label = "chip fill",
    )
    val edge by animateColorAsState(
        if (selected) CalinoColors.Accent.copy(.2f) else CalinoColors.Accent.copy(0f), fade, label = "chip edge",
    )
    val ink by animateColorAsState(
        if (selected) CalinoColors.Ink else CalinoColors.Ink2, fade, label = "chip ink",
    )
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(CalinoShapes.Pill))
            .background(fill)
            .border(1.dp, edge, RoundedCornerShape(CalinoShapes.Pill))
            .calinoPressable(role = semanticsRole, onClick = onClick)
            .semantics {
                contentDescription = "$text, $description"
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, color = ink)
    }
}

/** The grouped card every editor section sits in. */
@Composable
fun EditorSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalinoShapes.Card),
        colors = CardDefaults.cardColors(CalinoColors.Panel),
        border = BorderStroke(1.dp, CalinoColors.Line),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            title?.let { EditorLabel(it) }
            content()
        }
    }
}

/** The uppercase mono eyebrow used inside sections. */
@Composable
fun EditorLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.getDefault()),
        modifier,
        style = CalinoTypography.labelSmall,
        color = CalinoColors.Ink3,
        fontWeight = FontWeight.Bold,
    )
}

/** A tappable read-out of a value the platform pickers own. */
@Composable
fun EditorValueField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val pressable = if (enabled) Modifier.calinoPressable(onClick = onClick) else Modifier
    Column(
        modifier
            .clip(RoundedCornerShape(CalinoShapes.Row))
            .background(CalinoColors.Canvas)
            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Row))
            .then(pressable)
            .semantics { contentDescription = "$label, $value${if (enabled) ", tap to change" else ", unavailable"}" }
            .heightIn(min = 52.dp)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EditorLabel(label)
        Text(
            value,
            style = CalinoTypography.bodyLarge,
            color = if (enabled) CalinoColors.Ink else CalinoColors.Ink3,
        )
    }
}

@Composable
fun CalinoToggleRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = if (checked) "On" else "Off"
                role = Role.Switch
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = CalinoTypography.bodyLarge, color = if (enabled) CalinoColors.Ink else CalinoColors.Ink3)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = CalinoColors.Accent,
                checkedThumbColor = CalinoColors.Panel,
                uncheckedTrackColor = CalinoColors.Ink3.copy(.26f),
                uncheckedBorderColor = Color.Transparent,
                uncheckedThumbColor = CalinoColors.Panel,
            ),
        )
    }
}

/**
 * The colors this row writes into a draft.
 *
 * Deliberately the light palette's hues rather than the current theme's: an
 * event color is *data*, stored and sent to a server, so picking Rose at night
 * must not persist a value that reads as washed-out by day. The swatch is
 * *painted* through [CalinoPalette.forEvent], so it still looks right in dark.
 */
private val SwatchColors = with(CalinoThemes.PaperLight) {
    listOf("Rose" to Rose, "Blue" to Blue, "Green" to Green, "Amber" to Amber, "Plum" to Plum)
}

@Composable
fun CalinoColorSwatchRow(selected: Color, modifier: Modifier = Modifier, onSelect: (Color) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        EditorLabel("Color")
        Spacer(Modifier.weight(1f))
        SwatchColors.forEach { (name, color) ->
            Box(
                Modifier
                    .size(48.dp)
                    .calinoPressable(role = Role.RadioButton) { onSelect(color) }
                    .semantics {
                        contentDescription = "Select $name color"
                        stateDescription = if (selected == color) "Selected" else "Not selected"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CalinoColors.forEvent(color))
                        .border(if (selected == color) 3.dp else 0.dp, CalinoColors.Canvas, RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}

/** The standard reveal for an editor's optional detail. */
@Composable
fun EditorReveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(CalinoMotion.ContentEnterMillis)) + fadeIn(tween(CalinoMotion.FadeThroughMillis)),
        exit = shrinkVertically(tween(CalinoMotion.ContentExitMillis)) + fadeOut(tween(CalinoMotion.FadeThroughMillis)),
    ) { content() }
}

/**
 * The platform date picker, opened by the returned lambda. Keeping it here means
 * the editor opens four of them without restating the dialog lifecycle, and the
 * DisposableEffect still dismisses a dialog that outlives its surface.
 */
@Composable
fun rememberDatePicker(initial: () -> LocalDate, onPicked: (LocalDate) -> Unit): () -> Unit {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentInitial by rememberUpdatedState(initial)
    val currentOnPicked by rememberUpdatedState(onPicked)
    DisposableEffect(context, open) {
        if (!open) return@DisposableEffect onDispose { }
        val seed = currentInitial()
        val dialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                currentOnPicked(LocalDate.of(year, month + 1, day))
                open = false
            },
            seed.year,
            seed.monthValue - 1,
            seed.dayOfMonth,
        )
        dialog.setOnDismissListener { open = false }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            if (dialog.isShowing) dialog.dismiss()
        }
    }
    return { open = true }
}

@Composable
fun rememberTimePicker(initial: () -> LocalTime?, onPicked: (LocalTime) -> Unit): () -> Unit {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentInitial by rememberUpdatedState(initial)
    val currentOnPicked by rememberUpdatedState(onPicked)
    DisposableEffect(context, open) {
        if (!open) return@DisposableEffect onDispose { }
        val seed = currentInitial()
        val dialog = TimePickerDialog(
            context,
            { _, hour, minute ->
                currentOnPicked(LocalTime.of(hour, minute))
                open = false
            },
            seed?.hour ?: 12,
            seed?.minute ?: 0,
            false,
        )
        dialog.setOnDismissListener { open = false }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            if (dialog.isShowing) dialog.dismiss()
        }
    }
    return { open = true }
}
