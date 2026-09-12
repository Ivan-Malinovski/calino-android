package calino.malinov.ski.poc.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider as dayNightColor
import androidx.glance.unit.ColorProvider
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

    /**
     * A record's own colour. Not a day/night pair -- a calendar's colour comes
     * from the server and means the same thing at night, exactly as it does
     * inside the app.
     */
    fun record(value: Long): ColorProvider = ColorProvider(Color(value))
}
