package calino.malinov.ski.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import calino.malinov.ski.R
import calino.malinov.ski.data.model.ColorMath
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoThemes
import calino.malinov.ski.design.CalinoTypography

/** The hue strip's stops: a colour every 30 degrees, wrapping back to red. */
internal val HueStops: List<Color> = (0..12).map { step -> Color(ColorMath.argbFrom(step * 30f, 1f, 1f)) }

/** The hues the editor offers as one-tap choices, mirrored here so a custom pick can be undone. */
internal val ColorPickerPresets: List<Color> = with(CalinoThemes.PaperLight) {
    listOf(Rose, Blue, Green, Amber, Plum)
}

/**
 * Picking any colour, not the five the row offers.
 *
 * The five presets are useful but they are not a palette anyone chose; the web
 * app has always used a full colour input, so this is the phone catching up. The
 * values are the same opaque `#rrggbb` the rest of the app stores and sends, so a
 * custom colour is no less portable than a preset one.
 *
 * Three ways in, because each suits a different intent: drag the square for a
 * colour you are choosing by eye, drag the hue strip for the same, or type a hex
 * value when the colour has to match something exact. The hex field is also the
 * accessible path -- a two-dimensional drag is not usable with a screen reader.
 */
@Composable
fun CalinoColorPickerSheet(
    initial: Color,
    onDismiss: () -> Unit,
    onPick: (Color) -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = true

    val starting = ColorMath.hsvFrom(initial.toArgb())
    var hue by remember { mutableFloatStateOf(starting[0]) }
    var saturation by remember { mutableFloatStateOf(starting[1]) }
    var brightness by remember { mutableFloatStateOf(starting[2]) }
    // The text is its own state: it has to be able to hold "#12" while someone
    // types, which no valid colour can represent.
    var hexText by remember { mutableStateOf(ColorMath.toHex(initial.toArgb())) }
    var hexError by remember { mutableStateOf(false) }

    val current = ColorMath.argbFrom(hue, saturation, brightness)
    val description = stringResource(R.string.cal_color_brightness)
    val hueLabel = stringResource(R.string.cal_color_hue)
    val hexLabel = stringResource(R.string.cal_color_hex)

    fun setFromColor(argb: Int) {
        val hsv = ColorMath.hsvFrom(argb)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        hexText = ColorMath.toHex(argb)
        hexError = false
    }

    PickerDialog(visible = visible, onDismiss = onDismiss) {
        PickerCard(
            title = stringResource(R.string.cal_color_custom),
            onCancel = onDismiss,
            onDone = { onPick(Color(current)) },
        ) {
            // Scrollable because the keyboard can leave less room than the card
            // wants; the field, the presets and Done all have to stay reachable.
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                // Saturation left-to-right, brightness bottom-to-top, over the hue.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(CalinoShapes.Row))
                        .semantics { contentDescription = description }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                val height = size.height.toFloat().coerceAtLeast(1f)
                                fun pick(position: Offset) {
                                    saturation = (position.x / width).coerceIn(0f, 1f)
                                    brightness = 1f - (position.y / height).coerceIn(0f, 1f)
                                    hexText = ColorMath.toHex(ColorMath.argbFrom(hue, saturation, brightness))
                                    hexError = false
                                }
                                pick(awaitFirstDown().position)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    pick(change.position)
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                        drawRect(Color(ColorMath.argbFrom(hue, 1f, 1f)))
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        // The handle, drawn with a light and a dark ring so it stays
                        // findable on any colour underneath it.
                        val handle = Offset(saturation * size.width, (1f - brightness) * size.height)
                        drawCircle(Color.Black.copy(alpha = .35f), radius = 9.dp.toPx(), center = handle, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                        drawCircle(Color.White, radius = 9.dp.toPx(), center = handle, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Hue strip.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = hueLabel }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                fun pick(position: Offset) {
                                    hue = ((position.x / width).coerceIn(0f, 0.9999f) * 360f)
                                    hexText = ColorMath.toHex(ColorMath.argbFrom(hue, saturation, brightness))
                                    hexError = false
                                }
                                pick(awaitFirstDown().position)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    pick(change.position)
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                        // Twelve stops, not the seven named hues: RGB interpolation
                        // between cyan and blue across a wide gap bands visibly.
                        drawRect(Brush.horizontalGradient(HueStops))
                        // A thumb rather than a full-height hairline: a line reads
                        // as a seam in the gradient, which is exactly what it is not.
                        val thumb = Offset((hue / 360f) * size.width, size.height / 2f)
                        drawCircle(Color.Black.copy(alpha = .30f), radius = 11.dp.toPx(), center = thumb)
                        drawCircle(
                            Color.White,
                            radius = 11.dp.toPx(),
                            center = thumb,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()),
                        )
                        drawCircle(Color(ColorMath.argbFrom(hue, 1f, 1f)), radius = 8.dp.toPx(), center = thumb)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Preview: the chosen colour in the role it will actually play.
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(current))
                            .border(1.dp, CalinoColors.Line, RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hexLabel, style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
                        BasicTextField(
                            value = hexText,
                            onValueChange = { typed ->
                                hexText = typed.take(7)
                                val parsed = ColorMath.parseHex(hexText)
                                hexError = parsed == null
                                parsed?.let { setFromColor(it) }
                            },
                            singleLine = true,
                            textStyle = CalinoTypography.bodyLarge.copy(
                                color = CalinoColors.Ink,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(CalinoColors.Accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .semantics { contentDescription = hexLabel },
                            decorationBox = { inner ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(CalinoShapes.Row))
                                        .background(CalinoColors.Ink.copy(alpha = .05f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    if (hexText.isEmpty()) {
                                        Text(
                                            stringResource(R.string.cal_color_hex_hint),
                                            style = CalinoTypography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                            color = CalinoColors.Ink3,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
                if (hexError) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.cal_color_hex_invalid),
                        style = CalinoTypography.bodySmall,
                        color = CalinoColors.Accent,
                    )
                }
                Spacer(Modifier.height(16.dp))
                // One-tap presets, so an accidental custom colour is one tap from undone.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorPickerPresets.forEach { preset ->
                        val hex = ColorMath.toHex(preset.toArgb())
                        val selected = hex == ColorMath.toHex(current)
                        val name = stringResource(R.string.cal_select_color, hex.uppercase())
                        Box(
                            Modifier
                                .size(40.dp)
                                .calinoPressable(role = Role.RadioButton) { setFromColor(preset.toArgb()) }
                                .semantics { contentDescription = name },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    // No border at all when unselected: Compose
                                    // still paints a hairline for a 0.dp border, so
                                    // `border(0.dp, …)` rings every swatch.
                                    .then(
                                        if (selected) {
                                            Modifier.border(2.dp, CalinoColors.Ink.copy(alpha = .55f), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CalinoColors.forEvent(preset)),
                                )
                            }
                        }
                    }
                }
                // Advice, not validation: a colour near the page's own value is
                // invisible as a 13% fill or a thin stripe, but the choice is theirs.
                val hint = visibilityHint(current)
                if (hint != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(hint, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
                }
            }
        }
    }
}

/** Which theme this colour would disappear in, if any. */
@Composable
private fun visibilityHint(argb: Int): String? {
    val light = CalinoThemes.PaperLight.Panel.toArgb()
    val dark = CalinoThemes.PaperDark.Panel.toArgb()
    return when {
        ColorMath.isNearlyInvisible(argb, light) -> stringResource(R.string.cal_color_hard_to_see_light)
        ColorMath.isNearlyInvisible(argb, dark) -> stringResource(R.string.cal_color_hard_to_see_dark)
        else -> null
    }
}

