package calino.malinov.ski.poc.qa

import androidx.compose.ui.graphics.Color
import calino.malinov.ski.poc.design.CalinoPalette
import calino.malinov.ski.poc.design.CalinoThemes
import calino.malinov.ski.poc.state.CalinoPreferenceStore
import calino.malinov.ski.poc.util.CalinoThemeChoice
import calino.malinov.ski.poc.util.CalinoEventSyncRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The palette became a provided value so the app could have more than one
 * theme. These are the contracts that keeps: a theme is addressable by a stable
 * id, an unknown id degrades rather than crashes, and a dark theme is actually
 * readable.
 */
class CalinoPaletteTest {

    @Test
    fun `every registered theme has a unique id`() {
        val ids = CalinoThemes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `an id that no longer exists falls back to the light paper`() {
        // Ids are persisted, so a build that drops or renames a theme must
        // still open for someone who was using it.
        assertSame(CalinoThemes.PaperLight, CalinoThemes.byId(null))
        assertSame(CalinoThemes.PaperLight, CalinoThemes.byId("a-theme-from-a-later-build"))
        assertSame(CalinoThemes.PaperDark, CalinoThemes.byId(CalinoThemes.PaperDark.id))
    }

    @Test
    fun `an unknown stored theme choice reads back as the default`() {
        assertEquals(CalinoThemeChoice.Default, CalinoThemeChoice.fromName(null))
        assertEquals(CalinoThemeChoice.Default, CalinoThemeChoice.fromName("Sepia"))
        assertEquals(CalinoThemeChoice.Dark, CalinoThemeChoice.fromName("Dark"))
    }

    @Test
    fun `the theme choice survives a round trip through the store`() {
        val store = CalinoPreferenceStore.InMemory
        store.saveThemeChoice(CalinoThemeChoice.Dark)
        assertEquals(CalinoThemeChoice.Dark, store.loadThemeChoice())
        store.saveThemeChoice(CalinoThemeChoice.Default)
    }

    @Test fun `event sync range defaults safely and survives storage`() {
        assertEquals(CalinoEventSyncRange.TwoYears, CalinoEventSyncRange.fromName(null))
        assertEquals(CalinoEventSyncRange.TwoYears, CalinoEventSyncRange.fromName("FutureRange"))
        val store = CalinoPreferenceStore.InMemory
        store.saveEventSyncRange(CalinoEventSyncRange.FiveYears)
        assertEquals(CalinoEventSyncRange.FiveYears, store.loadEventSyncRange())
        store.saveEventSyncRange(CalinoEventSyncRange.Default)
    }

    /**
     * The reason the registry is worth having is also the reason it is worth
     * guarding: a ported theme that looks plausible in a CSS file can be
     * unreadable on a phone. Every dark palette has to clear WCAG AA for body
     * text on the surface it is actually painted on.
     *
     * Not applied to [CalinoThemes.PaperLight] on purpose. Its `Ink3` measures
     * about 2.7:1 today, which fails. The web's `built-in.css` has since
     * corrected the same tokens to `#655F57` and `#756D62`; adopting them here
     * changes how light mode looks everywhere and is a deliberate follow-up,
     * not something to smuggle in with dark mode.
     */
    @Test
    fun `dark themes clear WCAG AA for text`() {
        CalinoThemes.all.filter { it.isDark }.forEach { palette ->
            assertContrast(palette, "Ink on Canvas", palette.Ink, palette.Canvas)
            assertContrast(palette, "Ink2 on Side", palette.Ink2, palette.Side)
            assertContrast(palette, "Ink3 on Canvas", palette.Ink3, palette.Canvas)
            assertContrast(palette, "OnAccent on Accent", palette.OnAccent, palette.Accent)
            assertContrast(palette, "OnInk on Ink", palette.OnInk, palette.Ink)
            assertContrast(palette, "OnFloat on FloatFill", palette.OnFloat, palette.FloatFill)
            assertContrast(palette, "OnSelection on SelectionFill", palette.OnSelection, palette.SelectionFill)
        }
    }

    @Test
    fun `a dark theme lifts a stored event color off its background`() {
        val dark = CalinoThemes.PaperDark
        val stored = Color(CalinoThemes.PaperLight.Rose.value)
        // The six hues the app ships have designed counterparts rather than
        // the generic lift, so Rose at night is the value that was chosen for
        // ink, not a mechanical blend of the one chosen for paper.
        assertEquals(dark.Rose, dark.forEvent(stored))
        // Anything else -- a color a CalDAV server picked against a white
        // calendar -- still has to come off the canvas.
        val serverPicked = Color(0xFF203040)
        assertTrue(luminance(dark.forEvent(serverPicked)) > luminance(serverPicked))
        // Light mode leaves stored data exactly as it found it.
        assertEquals(serverPicked, CalinoThemes.PaperLight.forEvent(serverPicked))
    }

    @Test
    fun `a tint that reads over paper is pushed harder over ink`() {
        val hue = CalinoThemes.PaperLight.Blue
        val light = CalinoThemes.PaperLight.tint(hue, .10f)
        val dark = CalinoThemes.PaperDark.tint(hue, .10f)
        assertNotEquals(light, dark)
        // The dark chip has to separate from its canvas at least as much as
        // the light chip separates from its own, or it vanishes.
        assertTrue(
            distance(dark, CalinoThemes.PaperDark.Canvas) >=
                distance(light, CalinoThemes.PaperLight.Canvas),
        )
    }

    @Test
    fun `a raised surface that cannot rely on its fill gets an edge`() {
        // Light fills the add pill and the selected day with ink on paper,
        // which defines itself. A theme that keeps them close to its own
        // canvas has to carry the shape some other way.
        CalinoThemes.all.forEach { palette ->
            listOf(
                Triple("floating", palette.FloatFill, palette.FloatBorder),
                Triple("selection", palette.SelectionFill, palette.SelectionBorder),
            ).forEach { (what, fill, border) ->
                val standsOutOnItsOwn = contrastRatio(fill, palette.Canvas) >= 3f
                assertTrue(
                    "${palette.id}: the $what fill neither contrasts with the canvas nor has a border",
                    standsOutOnItsOwn || border.alpha > 0f,
                )
            }
        }
    }

    @Test
    fun `dark does not answer a loud fill with a louder one`() {
        // The bug this replaced: inverting Ink literally made the selected day
        // and the add pill the brightest things on a dark screen. Whatever a
        // dark theme paints them, it must not out-shout its own body text.
        CalinoThemes.all.filter { it.isDark }.forEach { palette ->
            listOf("floating" to palette.FloatFill, "selection" to palette.SelectionFill)
                .forEach { (what, fill) ->
                    assertTrue(
                        "${palette.id}: the $what fill is brighter than the ink it sits among",
                        luminance(fill) < luminance(palette.Ink),
                    )
                }
        }
    }

    @Test
    fun `dark drops the shadows that light draws`() {
        // The flat-dark rule: elevation is a surface step at night, not a blur.
        assertEquals(1f, CalinoThemes.PaperLight.elevationAlpha, 0f)
        assertEquals(0f, CalinoThemes.PaperDark.elevationAlpha, 0f)
    }

    private fun assertContrast(palette: CalinoPalette, what: String, fg: Color, bg: Color) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "${palette.id}: $what is %.2f:1, below the 4.5:1 floor".format(ratio),
            ratio >= 4.5f,
        )
    }

    private fun distance(a: Color, b: Color): Float =
        kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)

    /** WCAG 2.1 relative luminance. */
    private fun luminance(color: Color): Float {
        fun channel(value: Float): Float =
            if (value <= .03928f) value / 12.92f else ((value + .055f) / 1.055f).toDouble().pow(2.4).toFloat()
        return .2126f * channel(color.red) + .7152f * channel(color.green) + .0722f * channel(color.blue)
    }

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val a = luminance(fg)
        val b = luminance(bg)
        return (max(a, b) + .05f) / (min(a, b) + .05f)
    }
}
