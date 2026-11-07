package calino.malinov.ski.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.R
import calino.malinov.ski.data.model.CategoryAppearance
import calino.malinov.ski.data.model.CategoryStyleFont
import calino.malinov.ski.data.model.resolveCategoryAppearance
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.eventTint
import calino.malinov.ski.state.LocalCalinoPreferences
import kotlin.math.sqrt

/**
 * Category styling for event surfaces, the Android half of the web app's
 * `CategoryEventStyle`. [resolveCategoryAppearance] decides *what* an event's
 * categories ask for; this file draws it: a tinted fill, diagonal hatching, a
 * dashed edge, a pen loop, a marker underline, strike-through and handwriting.
 *
 * Every piece is separate so a surface takes only what its layout can carry,
 * and an event with no matching rule draws exactly what it drew before.
 */
@Composable
fun rememberCategoryAppearance(categories: List<String>): CategoryAppearance {
    val rules = LocalCalinoPreferences.current.categoryStyleRules
    return remember(categories, rules) { resolveCategoryAppearance(categories, rules) }
}

/** A rule color as it should be painted: lifted at night like every other event color. */
@Composable
private fun accent(argb: Int): Color = CalinoColors.forEvent(Color(argb))

/** The card background: a 13% mix of the rule's fill into the panel, or [default] when there is none. */
@Composable
fun CategoryAppearance.cardFill(default: Color): Color =
    fill?.let { eventTint(Color(it), .13f, CalinoColors.Panel) } ?: default

/**
 * The card edge. A dashed rule replaces the solid hairline rather than
 * drawing over it, as the web's `border: 1px dashed` does.
 */
@Composable
fun Modifier.categoryEdge(
    appearance: CategoryAppearance,
    width: Dp,
    color: Color,
    shape: Shape,
): Modifier {
    val dashed = appearance.dashed ?: return border(width, color, shape)
    val dashColor = accent(dashed)
    return drawBehind {
        val stroke = width.toPx()
        // Half the stroke sits outside the outline, so inset by half to keep it on the card.
        val inset = stroke / 2f
        val inner = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        translate(inset, inset) {
            drawOutline(
                shape.createOutline(inner, layoutDirection, this),
                dashColor,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
    }
}

/**
 * Diagonal hatching: the web's `repeating-linear-gradient(135deg, transparent
 * 0 7px, color 18% 7px 9px)`, a 2dp line every 9dp measured across the stripes.
 */
@Composable
fun Modifier.categoryTexture(appearance: CategoryAppearance, shape: Shape = RectangleShape): Modifier {
    val texture = appearance.texture ?: return this
    val line = accent(texture).copy(alpha = .18f)
    // The stripes are clipped to the card's shape as they are drawn, not by a
    // clip modifier: that would clip the title's pen loop, which is meant to
    // spill outside the card.
    return drawBehind {
        val period = 9.dp.toPx() * SQRT2
        val stroke = 2.dp.toPx()
        // Stripes run along x + y = c; the first one is centred 8dp in.
        var c = 8.dp.toPx() * SQRT2
        val limit = size.width + size.height
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
        clipPath(outline) {
            while (c < limit + stroke) {
                drawLine(line, Offset(c, 0f), Offset(0f, c), strokeWidth = stroke)
                c += period
            }
        }
    }
}

private val SQRT2 = sqrt(2f)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Handwriting = FontFamily(
    Font(R.font.caveat, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/**
 * A title in its category's lettering. With nothing to say it is a plain
 * [Text] with the caller's own parameters, so unstyled events are untouched.
 *
 * [compact] is for the narrow week columns: strike-through, underline and
 * pencil ink survive, but the pen loop and handwriting need room to read and
 * are left out there.
 */
@Composable
fun CategoryStyledText(
    text: String,
    appearance: CategoryAppearance,
    modifier: Modifier = Modifier,
    color: Color,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    compact: Boolean = false,
    /**
     * How far the pen loop may reach left of the title. A card that sits the
     * title close to a clipped edge (the timeline, beside its hour column) asks
     * for less; the loop keeps its width by reaching further right instead.
     */
    loopReachStart: Dp = LoopReach,
    /** The caller's own text style, kept whole so an unstyled title is exactly what it was. */
    style: TextStyle = TextStyle.Default,
) {
    val sketch = appearance.sketch.takeIf { !compact }
    val font = appearance.font.takeIf { !compact }
    val marker = appearance.marker
    if (sketch == null && marker == null && !appearance.done && font == null && !appearance.pencil) {
        Text(text, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, maxLines = maxLines, softWrap = softWrap, overflow = overflow, style = style)
        return
    }
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    val markerColor = marker?.let { accent(it) }
    val sketchColor = sketch?.let { accent(it) }
    val hand = font == CategoryStyleFont.Hand
    val baseSize = if (fontSize.isSpecified) fontSize else style.fontSize
    val textStyle = style.merge(TextStyle(
        color = if (appearance.pencil) CalinoColors.Ink2 else color,
        // Caveat has a small x-height, so it runs at 1.35x the line's size in
        // the same line box: it reads at the weight of its neighbours without
        // making the card taller.
        fontSize = if (hand && baseSize.isSpecified) baseSize * 1.35f else baseSize,
        lineHeight = lineHeight,
        fontWeight = if (hand) FontWeight.SemiBold else fontWeight,
        fontFamily = when (font) {
            CategoryStyleFont.Hand -> Handwriting
            CategoryStyleFont.Standard -> FontFamily.SansSerif
            else -> null
        },
        textDecoration = if (appearance.done) TextDecoration.LineThrough else TextDecoration.None,
    ))
    val textModifier = if (markerColor == null) Modifier else Modifier.drawBehind {
        val result = layout[0] ?: return@drawBehind
        for (line in 0 until result.lineCount) {
            val y = result.getLineBaseline(line) + 3.dp.toPx()
            drawLine(
                markerColor,
                Offset(result.getLineLeft(line), y),
                Offset(result.getLineRight(line), y),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
    val inner: @Composable () -> Unit = {
        Text(
            text,
            modifier = textModifier,
            style = textStyle,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow,
            onTextLayout = { layout[0] = it },
        )
    }
    if (sketchColor == null) {
        Box(modifier, contentAlignment = Alignment.CenterStart) { inner() }
    } else {
        // The loop belongs to the lettering, not the card. It is drawn clear of each side of
        // the text's own box (the web's padding) instead of padding the text, so
        // the title keeps its place and lines up with its neighbours.
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            Box(Modifier.sketchLoop(sketchColor, text.length % 2 == 1, loopReachStart)) {
                inner()
            }
        }
    }
}

/** How far the pen loop reaches past each side of the title by default. */
private val LoopReach = 16.dp

private const val LoopOdd =
    "M168 10 C148 -3 127 7 108 2 C83 -5 57 8 36 11 C18 16 3 30 7 46 C3 63 19 77 35 83 C56 99 78 90 100 96 C127 104 151 91 172 85 C194 76 202 58 194 42 C193 27 176 17 158 12 C143 5 125 7 110 10"
private const val LoopEven =
    "M165 9 C143 -5 119 5 101 1 C76 -3 51 5 32 15 C12 20 2 35 7 51 C4 68 23 82 41 87 C65 101 86 92 107 97 C131 101 158 91 176 80 C198 69 199 49 190 34 C182 18 164 14 146 9 C132 4 118 9 104 10"
private const val LoopTail =
    "M126 6 C99 10 73 -1 49 10 C20 14 5 31 10 51 C9 72 34 89 61 90 C89 102 118 94 143 94 C175 91 196 73 197 55 C200 32 180 18 159 14 C142 7 126 8 113 12"

/**
 * The web's hand-drawn loop, a 200x100 outline stretched over the text box
 * with a pen stroke that stays the same width however it is stretched, and a
 * fainter second pass for the tail.
 */
private fun Modifier.sketchLoop(color: Color, odd: Boolean, reachStart: Dp): Modifier = drawWithCache {
    fun scaled(d: String): Path {
        val path = PathParser().parsePathString(d).toPath()
        // The loop stands well clear of the lettering: 16dp past each side and
        // 5dp above and below, plus the web's 1px inset top and bottom. It
        // paints outside the text's box (and the card's, where nothing clips).
        // Total width is fixed; the reach to the left is what a card can spare.
        val start = reachStart.coerceIn(0.dp, LoopReach * 2).toPx()
        val end = (LoopReach * 2).toPx() - start
        val padY = 5.dp.toPx() + 1.dp.toPx()
        path.transform(Matrix().apply {
            translate(-start, -padY)
            scale((size.width + start + end) / 200f, (size.height + 2 * padY) / 100f)
        })
        return path
    }
    val loop = scaled(if (odd) LoopOdd else LoopEven)
    val tail = scaled(LoopTail)
    val main = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val faint = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    onDrawWithContent {
        drawContent()
        drawPath(loop, color, style = main)
        drawPath(tail, color.copy(alpha = .65f), style = faint)
    }
}
