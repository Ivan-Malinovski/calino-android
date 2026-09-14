package calino.malinov.ski.poc.design

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.R

/**
 * One theme's colors.
 *
 * The property names are deliberately capitalised. They were the members of an
 * `object CalinoColors` read statically from every surface in the app, and
 * keeping the spelling is what let the palette become a provided value without
 * touching the hundreds of call sites that read it. Read them through
 * [CalinoColors], never by holding a palette in a field.
 *
 * Values track `src/themes/built-in.css` in the Calino web repository, which is
 * the same design system. Two of its rules matter here:
 *
 * - **The warm neutral rule.** No flat grey, no pure black, no pure white.
 *   Every neutral carries the paper's warmth, in both directions.
 * - **The flat-dark rule.** Shadows are a light-mode device. A dark theme
 *   expresses elevation through the [Canvas]/[Panel]/[Side] steps and its
 *   hairlines instead, which is what [elevationAlpha] gates.
 */
@Immutable
data class CalinoPalette(
    /** Stable key for persistence. Never rename a shipped one. */
    val id: String,
    /** What the settings picker calls it. */
    val name: String,
    val isDark: Boolean,
    val Canvas: Color,
    val Panel: Color,
    val Side: Color,
    val Ink: Color,
    val Ink2: Color,
    val Ink3: Color,
    val Accent: Color,
    val AccentSoft: Color,
    /**
     * What reads legibly *on* [Accent]. Paper white in light; in dark it flips
     * back to the canvas, because white on the lightened accent measures
     * 2.63:1 where ink measures 6.74:1.
     */
    val OnAccent: Color,
    /** What reads legibly on a surface painted with [Ink] at full weight. */
    val OnInk: Color,
    /**
     * A control that floats over the canvas -- the add pill.
     *
     * Light fills it with [Ink] and needs no edge: a dark lozenge on paper
     * defines itself. Inverting that literally at night gives a solid cream
     * slab, which is the loudest thing on the screen and reads as a hole
     * punched in the page. So dark keeps the pill dark and lets a brighter
     * hairline draw its shape instead.
     */
    val FloatFill: Color,
    val OnFloat: Color,
    val FloatBorder: Color,
    /**
     * The selected day in the week strip, and its compact pill.
     *
     * The same problem as [FloatFill], and the same answer. Light paints a
     * solid [Ink] block, which is quiet on paper because paper is bright. The
     * literal inversion is a cream slab sitting in a dark week, louder than
     * the day it is marking. Dark takes a surface step and an edge instead,
     * and keeps its numerals in [Ink] rather than flipping them.
     */
    val SelectionFill: Color,
    val OnSelection: Color,
    val SelectionBorder: Color,
    val Rose: Color,
    val Blue: Color,
    val Green: Color,
    val Amber: Color,
    val Plum: Color,
    val Teal: Color,
    /**
     * How much heavier a scrim has to be in this theme. A veil that reads
     * clearly over paper barely registers over ink, because there is far less
     * brightness left to take away.
     */
    val scrimBoost: Float,
    /** The wash a pressed row takes on. */
    val PressWash: Color,
    /**
     * How far to push [eventTint]'s mix. A tint that reads as a whisper over
     * paper disappears entirely over ink, so dark mixes harder.
     */
    val eventTintScale: Float,
    /** Multiplier on drop-shadow alpha. Zero in dark, per the flat-dark rule. */
    val elevationAlpha: Float,
) {
    // Derived from Ink exactly as they always were, so they follow it into a
    // dark palette without a second set of literals to keep in step.
    val Line: Color get() = Ink.copy(alpha = .09f)
    val Line2: Color get() = Ink.copy(alpha = .05f)

    /**
     * Washes that give the month grid a readable structure. Every cell used to
     * be the same paper, so a Saturday, a Wednesday and a day belonging to the
     * next month were indistinguishable until you read the number. These are
     * drawn from [Ink], deliberately faint: they should register as rhythm
     * rather than as boxes, and they are painted as bands that abut rather
     * than overlap: stacking two of them on one cell compounds into a patch
     * far darker than either was meant to be.
     */
    val WeekendWash: Color get() = Ink.copy(alpha = .045f)
    val OutsideMonthWash: Color get() = Ink.copy(alpha = .022f)

    /**
     * A calendar color, made fit for this theme's background.
     *
     * Event colors are *data*: they come from the fixtures and from whatever a
     * CalDAV server chose, as raw ARGB longs, so a palette cannot simply
     * rename them. The six the app ships have designed dark counterparts;
     * anything else a server sends is lifted toward the ink so a color picked
     * against a white calendar does not sink into a dark one.
     */
    fun forEvent(color: Color): Color {
        if (!isDark) return color
        return DarkHues[color.value] ?: lerp(color, Ink, .26f)
    }

    /**
     * [color] laid over [over] at [percent] strength, scaled for this theme.
     *
     * The member form exists for draw scopes, which cannot call the composable
     * [eventTint] but do hold a hoisted palette. Same algorithm, one place.
     */
    /**
     * A veil at [alpha], weighted for this theme.
     *
     * Each surface keeps the strength it chose; only the theme's boost differs.
     * Never build a scrim out of [Ink]: at night that is nearly white, and a
     * scrim made of it lightens the very thing it is meant to push back.
     */
    fun scrim(alpha: Float): Color =
        Color.Black.copy(alpha = (alpha * scrimBoost).coerceIn(0f, 1f))

    /** The standard modal veil. */
    val Scrim: Color get() = scrim(.38f)

    fun tint(color: Color, percent: Float, over: Color = Canvas): Color =
        lerp(over, forEvent(color), (percent * eventTintScale).coerceIn(0f, 1f))

    private companion object {
        /**
         * Keyed on the light palette's own hues, so a fixture written against
         * paper resolves to the value designed for ink rather than to the
         * generic lift.
         */
        val DarkHues: Map<ULong, Color> by lazy {
            val light = CalinoThemes.PaperLight
            val dark = CalinoThemes.PaperDark
            mapOf(
                light.Rose.value to dark.Rose,
                light.Blue.value to dark.Blue,
                light.Green.value to dark.Green,
                light.Amber.value to dark.Amber,
                light.Plum.value to dark.Plum,
                light.Teal.value to dark.Teal,
                light.Accent.value to dark.Accent,
            )
        }
    }
}

/**
 * The themes the app can be set to.
 *
 * Two ship. The registry exists because the web app carries a dozen more --
 * Slate, Mist, Catppuccin, Bauhaus -- and porting one should be an entry here
 * and nothing else.
 */
object CalinoThemes {

    /** The paper the app has always been. */
    val PaperLight = CalinoPalette(
        id = "paper-light",
        name = "Light",
        isDark = false,
        Canvas = Color(0xFFFAF8F3),
        Panel = Color.White,
        Side = Color(0xFFF6F3ED),
        Ink = Color(0xFF2C2823),
        Ink2 = Color(0xFF6F6A62),
        Ink3 = Color(0xFFA39D93),
        Accent = Color(0xFFB07D4F),
        AccentSoft = Color(0xFFEFE7DB),
        OnAccent = Color.White,
        OnInk = Color.White,
        FloatFill = Color(0xFF2C2823),
        OnFloat = Color(0xFFFAF8F3),
        FloatBorder = Color.Transparent,
        SelectionFill = Color(0xFF2C2823),
        OnSelection = Color.White,
        SelectionBorder = Color.Transparent,
        Rose = Color(0xFFC2697F),
        Blue = Color(0xFF5B7FB5),
        Green = Color(0xFF5D9A78),
        Amber = Color(0xFFBF944E),
        Plum = Color(0xFF8A6AA8),
        Teal = Color(0xFF4A9B96),
        scrimBoost = 1f,
        PressWash = Color(0xFF2C2823).copy(alpha = .06f),
        eventTintScale = 1f,
        elevationAlpha = 1f,
    )

    /**
     * The same paper at night. Values are the web's `[data-theme='dark']`
     * block; the three status hues are its measured ones, and Blue, Plum and
     * Teal are lifted by the move the accent makes (#B07D4F -> #C9956A).
     */
    val PaperDark = CalinoPalette(
        id = "paper-dark",
        name = "Dark",
        isDark = true,
        Canvas = Color(0xFF1A1816),
        Panel = Color(0xFF242220),
        Side = Color(0xFF2A2826),
        Ink = Color(0xFFF0ECE6),
        Ink2 = Color(0xFFA8A29A),
        Ink3 = Color(0xFF989086),
        Accent = Color(0xFFC9956A),
        AccentSoft = Color(0xFF33291F),
        OnAccent = Color(0xFF1A1816),
        OnInk = Color(0xFF1A1816),
        FloatFill = Color(0xFF242220),
        OnFloat = Color(0xFFF0ECE6),
        FloatBorder = Color(0xFFF0ECE6).copy(alpha = .22f),
        SelectionFill = Color(0xFF2F2C29),
        OnSelection = Color(0xFFF0ECE6),
        SelectionBorder = Color(0xFFF0ECE6).copy(alpha = .26f),
        Rose = Color(0xFFD4877F),
        Blue = Color(0xFF7D9FD1),
        Green = Color(0xFF6AAA85),
        Amber = Color(0xFFD4A54F),
        Plum = Color(0xFFA98BC4),
        Teal = Color(0xFF6FB8B3),
        scrimBoost = 1.55f,
        PressWash = Color(0xFFF0ECE6).copy(alpha = .06f),
        eventTintScale = 1.6f,
        elevationAlpha = 0f,
    )

    val all: List<CalinoPalette> = listOf(PaperLight, PaperDark)

    /** Falls back rather than throwing: an id may come from an older install. */
    fun byId(id: String?): CalinoPalette = all.firstOrNull { it.id == id } ?: PaperLight
}

val LocalCalinoPalette = staticCompositionLocalOf { CalinoThemes.PaperLight }

/**
 * The palette in force.
 *
 * This used to be `object CalinoColors`, a set of static literals. It reads the
 * same at every call site and is now whatever theme the user chose. Draw code
 * cannot read a composition local, so hoist this into a `val` in the enclosing
 * composable and capture that in the lambda.
 */
val CalinoColors: CalinoPalette
    @Composable @ReadOnlyComposable get() = LocalCalinoPalette.current

object CalinoSpacing {
    val Base = 4.dp
    val Screen = 20.dp
    val Section = 24.dp

    /**
     * Room reserved at the bottom of every scrollable root surface so the
     * floating add pill never covers the last row.
     */
    val PillClearance = 96.dp

    /** The action pill's settled height, once it has grown out of the add shape. */
    val ActionPillHeight = 56.dp
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

/**
 * The segmented control's geometry.
 *
 * Named here rather than left as literals inside the component because the
 * numbers are what makes one segmented control read as the same object as the
 * next. Every hand-rolled lookalike the app grew -- an entry-kind picker at a
 * full pill radius, a journal mode toggle eighteen dips taller -- diverged on
 * exactly these values while believing it matched.
 */
object CalinoSegmented {
    /** The touch lane the control claims; the painted track is shorter. */
    val LaneHeight = 48.dp
    val TrackHeight = 36.dp
    val TrackRadius = 12.dp
    val SegmentRadius = 9.dp
    /**
     * The gutter between the track's edge and the indicator, on all four
     * sides, and the gap between segments. The indicator used to be given the
     * track's full height, so it sat flush against the top and bottom rails
     * and read as wedged into the track rather than resting in it; the track
     * grew by the same amount the gutter took, so the label keeps its room.
     */
    val Inset = 3.dp
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

/**
 * Shared specifications for Calino's restrained, editorial motion language.
 *
 * Effects such as color and alpha keep fixed timings. Spatial movement uses
 * springs so an interrupted state change carries its velocity into the new
 * target. Expressive motion is reserved for small, prominent feedback; the
 * standard spring never overshoots, and gesture return preserves the existing
 * cancelled-drag feel.
 */
object CalinoMotion {
    const val PressMillis = 90
    const val FadeThroughMillis = 120
    const val ContentEnterMillis = 180
    const val ContentExitMillis = 160
    const val SurfaceFadeMillis = 220

    /**
     * The add pill changing shape into a modal's actions, and back. It has to
     * finish inside [SurfaceFadeMillis] on the way out, so the pill has
     * settled into its add form by the time the card it belonged to is gone.
     */
    const val PillMorphMillis = 260
    const val PillUnmorphMillis = 200

    /**
     * Unfolding is a bigger move than any in-app transition, so it gets the
     * longest timing in the app -- long enough to read as the same surface
     * growing, short enough that the device is not still animating once the
     * hinge has stopped.
     */
    const val FoldMorphMillis = 320

    fun <T> expressiveSpatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = .78f,
        stiffness = 520f,
    )

    fun <T> standardSpatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> gestureReturn(): FiniteAnimationSpec<T> = spring(
        dampingRatio = .86f,
        stiffness = 420f,
    )
}

@Composable
fun CalinoTheme(
    palette: CalinoPalette = CalinoThemes.PaperLight,
    content: @Composable () -> Unit,
) {
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.Accent,
            onPrimary = palette.OnAccent,
            primaryContainer = palette.AccentSoft,
            onPrimaryContainer = palette.Ink,
            secondary = palette.Ink2,
            onSecondary = palette.OnInk,
            secondaryContainer = palette.Side,
            onSecondaryContainer = palette.Ink,
            tertiary = palette.Rose,
            onTertiary = palette.OnAccent,
            background = palette.Canvas,
            surface = palette.Panel,
            surfaceVariant = palette.Side,
            onBackground = palette.Ink,
            onSurface = palette.Ink,
            onSurfaceVariant = palette.Ink2,
            outline = palette.Line,
            outlineVariant = palette.Line2,
            scrim = palette.Scrim,
        )
    } else {
        lightColorScheme(
            primary = palette.Accent,
            onPrimary = palette.OnAccent,
            primaryContainer = palette.AccentSoft,
            onPrimaryContainer = palette.Ink,
            secondary = palette.Ink2,
            onSecondary = palette.OnInk,
            secondaryContainer = palette.Side,
            onSecondaryContainer = palette.Ink,
            tertiary = palette.Rose,
            onTertiary = palette.OnAccent,
            background = palette.Canvas,
            surface = palette.Panel,
            surfaceVariant = palette.Side,
            onBackground = palette.Ink,
            onSurface = palette.Ink,
            onSurfaceVariant = palette.Ink2,
            outline = palette.Line,
            outlineVariant = palette.Line2,
            scrim = palette.Scrim,
        )
    }
    // Material defaults LocalContentColor to black outside a Surface, and most
    // of the app's Text has no explicit color because black was right on paper.
    // Providing it here is what carries all of those into a dark theme.
    CompositionLocalProvider(
        LocalCalinoPalette provides palette,
        LocalContentColor provides palette.Ink,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CalinoTypography,
            shapes = Shapes(
                small = androidx.compose.foundation.shape.RoundedCornerShape(CalinoShapes.Chip),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(CalinoShapes.Row),
                large = androidx.compose.foundation.shape.RoundedCornerShape(CalinoShapes.Card),
            ),
            content = content,
        )
    }
}

/**
 * An event's color laid over a background at [percent] strength.
 *
 * Composable so that [over] can default to the current canvas and so the mix
 * can carry the palette's [CalinoPalette.eventTintScale]: the same 10% that
 * reads as a whisper over paper is invisible over ink.
 */
@Composable
@ReadOnlyComposable
fun eventTint(color: Color, percent: Float, over: Color = CalinoColors.Canvas): Color =
    CalinoColors.tint(color, percent, over)
