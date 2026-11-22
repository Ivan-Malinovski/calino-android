package calino.malinov.ski.qa

import calino.malinov.ski.data.model.ColorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Colour arithmetic: the picker decides where a finger is, this decides what
 * colour that is and whether anyone will be able to see it.
 */
class ColorMathTest {

    @Test
    fun `hex round-trips`() {
        assertEquals("#2f6f4f", ColorMath.toHex(0xFF2F6F4F.toInt()))
        assertEquals(0xFF2F6F4F.toInt(), ColorMath.parseHex("#2f6f4f"))
        assertEquals(0xFF2F6F4F.toInt(), ColorMath.parseHex("2F6F4F"))
        assertEquals(0xFF2F6F4F.toInt(), ColorMath.parseHex("  #2F6F4F "))
    }

    @Test
    fun `an unreadable hex is null, never a guessed colour`() {
        assertNull(ColorMath.parseHex(""))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#1234567"))
        assertNull(ColorMath.parseHex("#gggggg"))
        assertNull(ColorMath.parseHex("blue"))
    }

    @Test
    fun `hsv and argb are inverses`() {
        listOf(0xFF2F6F4F, 0xFFB64248, 0xFF607FA3, 0xFFC2697F).map { it.toInt() }.forEach { argb ->
            val (h, s, v) = ColorMath.hsvFrom(argb).let { Triple(it[0], it[1], it[2]) }
            assertEquals("round trip of ${ColorMath.toHex(argb)}", argb, ColorMath.argbFrom(h, s, v))
        }
    }

    @Test
    fun `greys keep their lightness and have no hue`() {
        val grey = 0xFF808080.toInt()
        val (h, s, v) = ColorMath.hsvFrom(grey).let { Triple(it[0], it[1], it[2]) }
        assertEquals(0f, h, 0.001f)
        assertEquals(0f, s, 0.001f)
        assertEquals(0.502f, v, 0.01f)
        assertEquals(grey, ColorMath.argbFrom(0f, 0f, 0.502f))
    }

    @Test
    fun `hues wrap instead of clamping`() {
        assertEquals(ColorMath.argbFrom(0f, 1f, 1f), ColorMath.argbFrom(360f, 1f, 1f))
        assertEquals(ColorMath.argbFrom(10f, 1f, 1f), ColorMath.argbFrom(370f, 1f, 1f))
        assertEquals(ColorMath.argbFrom(350f, 1f, 1f), ColorMath.argbFrom(-10f, 1f, 1f))
    }

    @Test
    fun `saturation and value clamp rather than overflow`() {
        // Hue 0 at full saturation and value is red; out-of-range input clamps to it.
        assertEquals(0xFFFF0000.toInt(), ColorMath.argbFrom(0f, 5f, 5f))
        assertEquals(0xFF000000.toInt(), ColorMath.argbFrom(0f, -1f, -1f))
    }

    @Test
    fun `luminance matches known anchors`() {
        assertEquals(1.0, ColorMath.relativeLuminance(0xFFFFFFFF.toInt()), 0.0001)
        assertEquals(0.0, ColorMath.relativeLuminance(0xFF000000.toInt()), 0.0001)
        assertEquals(21.0, ColorMath.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.001)
        assertEquals(1.0, ColorMath.contrastRatio(0xFF2F6F4F.toInt(), 0xFF2F6F4F.toInt()), 0.001)
    }

    @Test
    fun `a colour that hides on the page is reported`() {
        // The paper panel, and a near-white and near-black that would vanish on it.
        val paperPanel = 0xFFFFFDFA.toInt()
        assertTrue(ColorMath.isNearlyInvisible(0xFFFDFBF6.toInt(), paperPanel))
        // Ink on the panel is unmistakable, and so is a saturated colour.
        assertFalse(ColorMath.isNearlyInvisible(0xFF2C2823.toInt(), paperPanel))
        assertFalse(ColorMath.isNearlyInvisible(0xFFB64248.toInt(), paperPanel))
        // Dark mode: the same colour that was invisible on paper is fine at night.
        val nightPanel = 0xFF22252A.toInt()
        assertFalse(ColorMath.isNearlyInvisible(0xFFFDFBF6.toInt(), nightPanel))
        assertTrue(ColorMath.isNearlyInvisible(0xFF1E2126.toInt(), nightPanel))
    }
}
