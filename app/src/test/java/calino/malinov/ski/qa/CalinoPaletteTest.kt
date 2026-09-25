package calino.malinov.ski.qa

import androidx.compose.ui.graphics.Color
import calino.malinov.ski.design.CalinoPalette
import calino.malinov.ski.design.CalinoThemes
import calino.malinov.ski.design.priorityLabel
import calino.malinov.ski.design.priorityStripeColor
import calino.malinov.ski.state.CalinoPreferenceStore
import calino.malinov.ski.util.CalinoThemeChoice
import calino.malinov.ski.util.CalinoEventSyncRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
     * unreadable on a phone. Every palette has to clear WCAG AA for body text
     * on the surface it is actually painted on.
     *
     * Light paper's `Ink2`/`Ink3` follow the web's corrected `built-in.css`
     * values. White on the light `Accent` is 3.56:1 and is exempted here
     * knowingly: it is a brand-color decision, not a text token.
     */
    @Test
    fun `every theme clears WCAG AA for text`() {
        CalinoThemes.all.forEach { palette ->
            assertContrast(palette, "Ink on Canvas", palette.Ink, palette.Canvas)
            assertContrast(palette, "Ink2 on Side", palette.Ink2, palette.Side)
            assertContrast(palette, "Ink3 on Canvas", palette.Ink3, palette.Canvas)
            assertContrast(palette, "Ink3 on Side", palette.Ink3, palette.Side)
            if (palette.isDark) {
                assertContrast(palette, "OnAccent on Accent", palette.OnAccent, palette.Accent)
            }
            assertContrast(palette, "OnInk on Ink", palette.OnInk, palette.Ink)
            assertContrast(palette, "OnFloat on FloatFill", palette.OnFloat, palette.FloatFill)
            assertContrast(palette, "OnSelection on SelectionFill", palette.OnSelection, palette.SelectionFill)
        }
    }

    /**
     * A dark selection fill that sits within a hair of the panel leaves only
     * the day number to carry the highlight.
     */
    @Test
    fun `a dark selection fill reads against the surfaces it sits on`() {
        CalinoThemes.all.filter { it.isDark }.forEach { palette ->
            listOf("Panel" to palette.Panel, "Canvas" to palette.Canvas).forEach { (name, surface) ->
                val ratio = contrastRatio(palette.SelectionFill, surface)
                assertTrue(
                    "${palette.id}: SelectionFill on $name is %.2f:1".format(ratio),
                    ratio >= 1.25f,
                )
            }
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
    fun `priority 0 draws no stripe at all`() {
        // Null, not a transparent box: a task list where every undefined
        // priority still reserves the stripe's width reads as ragged.
        assertEquals(null, priorityStripeColor(0, CalinoThemes.PaperLight))
        assertNull(priorityLabel(0))
    }

    @Test
    fun `priority bands map to distinct, named colors`() {
        val palette = CalinoThemes.PaperLight
        assertEquals(palette.Rose, priorityStripeColor(1, palette))
        assertEquals(palette.Rose, priorityStripeColor(3, palette))
        assertEquals(palette.Amber, priorityStripeColor(4, palette))
        assertEquals(palette.Amber, priorityStripeColor(6, palette))
        assertEquals(palette.Ink3, priorityStripeColor(7, palette))
        assertEquals(palette.Ink3, priorityStripeColor(9, palette))
    }

    @Test
    fun `every priority label names a band or is absent for undefined`() {
        assertEquals("high priority", priorityLabel(1))
        assertEquals("medium priority", priorityLabel(5))
        assertEquals("low priority", priorityLabel(9))
        assertNull(priorityLabel(0))
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
