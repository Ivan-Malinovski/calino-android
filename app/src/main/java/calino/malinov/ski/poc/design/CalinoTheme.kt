package calino.malinov.ski.poc.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.R

object CalinoColors {
    val Canvas = Color(0xFFFAF8F3)
    val Panel = Color.White
    val Side = Color(0xFFF6F3ED)
    val Ink = Color(0xFF2C2823)
    val Ink2 = Color(0xFF6F6A62)
    val Ink3 = Color(0xFFA39D93)
    val Accent = Color(0xFFB07D4F)
    val AccentSoft = Color(0xFFEFE7DB)
    val Line = Ink.copy(alpha = .09f)
    val Line2 = Ink.copy(alpha = .05f)
    val Rose = Color(0xFFC2697F)
    val Blue = Color(0xFF5B7FB5)
    val Green = Color(0xFF5D9A78)
    val Amber = Color(0xFFBF944E)
    val Plum = Color(0xFF8A6AA8)
    val Teal = Color(0xFF4A9B96)

    /**
     * Washes that give the month grid a readable structure. Every cell used to
     * be the same paper, so a Saturday, a Wednesday and a day belonging to the
     * next month were indistinguishable until you read the number. These are
     * warm greys drawn from [Ink], deliberately faint: they should register as
     * rhythm rather than as boxes, and they are painted as bands that abut
     * rather than overlap: stacking two of them on one cell compounds into a
     * patch far darker than either was meant to be.
     */
    val WeekendWash = Ink.copy(alpha = .045f)
    val OutsideMonthWash = Ink.copy(alpha = .022f)
}

object CalinoSpacing {
    val Base = 4.dp
    val Screen = 20.dp
    val Section = 24.dp

    /**
     * Room reserved at the bottom of every scrollable root surface so the
     * floating add pill never covers the last row.
     */
    val PillClearance = 96.dp
}

object CalinoShapes {
    val Chip = 6.dp
    val Row = 11.dp
    val DayBlock = 10.dp
    val Button = 15.dp
    val Fab = 16.dp
    val Card = 22.dp
    val Sheet = 26.dp
    val Pill = 999.dp
}

private val Display = FontFamily(Font(R.font.newsreader, FontWeight.Normal))
private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

val CalinoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = (-.8).sp,
        fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 33.sp, lineHeight = 37.sp, letterSpacing = (-.5).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 33.sp, lineHeight = 37.sp, letterSpacing = (-.5).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 23.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 23.sp, lineHeight = 28.sp),
    titleSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 14.5.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.5.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.5.sp, lineHeight = 18.75.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.2.sp),
)

/** Shared timings for the restrained, editorial motion language. */
object CalinoMotion {
    const val PressMillis = 90
    const val FadeThroughMillis = 120
    const val ContentEnterMillis = 180
    const val ContentExitMillis = 160
    const val SurfaceFadeMillis = 220
}

@Composable
fun CalinoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = CalinoColors.Accent,
            onPrimary = CalinoColors.Panel,
            primaryContainer = CalinoColors.AccentSoft,
            onPrimaryContainer = CalinoColors.Ink,
            secondary = CalinoColors.Ink2,
            onSecondary = CalinoColors.Panel,
            secondaryContainer = CalinoColors.Side,
            onSecondaryContainer = CalinoColors.Ink,
            tertiary = CalinoColors.Rose,
            onTertiary = CalinoColors.Panel,
            background = CalinoColors.Canvas,
            surface = CalinoColors.Panel,
            surfaceVariant = CalinoColors.Side,
            onBackground = CalinoColors.Ink,
            onSurface = CalinoColors.Ink,
            onSurfaceVariant = CalinoColors.Ink2,
            outline = CalinoColors.Line,
            outlineVariant = CalinoColors.Line2,
            scrim = CalinoColors.Ink.copy(alpha = .38f),
        ),
        typography = CalinoTypography,
        shapes = Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(CalinoShapes.Chip),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(CalinoShapes.Row),
            large = androidx.compose.foundation.shape.RoundedCornerShape(CalinoShapes.Card),
        ),
        content = content,
    )
}

fun eventTint(color: Color, percent: Float, over: Color = CalinoColors.Canvas): Color =
    androidx.compose.ui.graphics.lerp(over, color, percent.coerceIn(0f, 1f))
