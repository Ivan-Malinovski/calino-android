package calino.malinov.ski.poc.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.glance.color.ColorProvider as dayNightColor
import androidx.glance.unit.ColorProvider
import calino.malinov.ski.poc.design.CalinoPalette
import calino.malinov.ski.poc.design.CalinoThemes

/**
 * The widget's colours.
 *
 * Glance runs in the launcher's process and cannot see the app's Compose theme
 * or its composition locals, so the palette has to be restated here as
 * explicit day/night pairs -- but the values are *read* from [CalinoThemes]
 * rather than retyped, so a palette edit reaches the widget with no second
 * place to remember. (Glance's resource-id colour overload would be the other
 * route, but it is `RestrictTo` its own library group.)
 *
 * The static loading and preview layouts in `res/layout/` cannot reach Kotlin
 * at all, so they restate the few tokens they need in `values/colors.xml` and
 * `values-night/colors.xml`, each with the palette field named beside it.
 *
 * Day/night follows the system rather than the app's theme preference: the
 * widget sits on the launcher's wallpaper, not on Calino's canvas, and a light
 * widget on a dark home screen reads as a bug. Honouring the in-app choice
 * would need a durable flag the launcher's process can read, which is not worth
 * the file.
 *
 * `Ink3` therefore keeps light's 2.7:1 value: correcting it only here would
 * make the widget disagree with every other surface. That is TODO item 16's to
 * fix, across all of them at once.
 */
internal object CalinoWidgetColors {

    private val light = CalinoThemes.PaperLight
    private val dark = CalinoThemes.PaperDark

    private fun pair(day: Color, night: Color): ColorProvider = dayNightColor(day, night)

    val canvas = pair(light.Canvas, dark.Canvas)
    val ink = pair(light.Ink, dark.Ink)
    val ink2 = pair(light.Ink2, dark.Ink2)
    val ink3 = pair(light.Ink3, dark.Ink3)
    val accent = pair(light.Accent, dark.Accent)

    /**
     * The rule between ledger rows.
     *
     * Composited onto the canvas rather than left translucent: Glance hands the
     * colour to a `RemoteViews` background, and a 9%-alpha fill over the
     * launcher's wallpaper is a different colour than the same fill over the
     * widget's own paper. Flattening it here keeps the hairline the one the
     * palette designed.
     */
    val line = pair(
        light.Line.compositeOver(light.Canvas),
        dark.Line.compositeOver(dark.Canvas),
    )

    /**
     * A record's own colour, made fit for the theme it lands in.
     *
     * A calendar colour is data -- whatever the server chose -- and one picked
     * against a white calendar sinks into a dark widget. [CalinoPalette.forEvent]
     * is the same correction every calendar surface applies, so the widget's
     * colours are the app's colours rather than a second, brighter set.
     */
    fun record(value: Long): ColorProvider =
        pair(light.forEvent(Color(value)), dark.forEvent(Color(value)))

    /**
     * A card's fill: [value] laid over the canvas at a whisper.
     *
     * Glance cannot blend two colours, so the mix happens here, through the
     * palette's own [CalinoPalette.tint] -- which means dark mixes harder
     * (`eventTintScale`) exactly as it does in the app, instead of producing a
     * tint that vanishes into ink.
     */
    fun recordTint(value: Long): ColorProvider = pair(
        light.tint(Color(value), CardTintPercent),
        dark.tint(Color(value), CardTintPercent),
    )

    /** How far a card's fill is pushed toward its record's colour. */
    private const val CardTintPercent = .13f
}
