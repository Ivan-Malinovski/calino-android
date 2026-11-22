package calino.malinov.ski.data.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colour arithmetic that has to be right in the same way a date is: it is
 * written to a server, read back by another client, and shown to a person.
 *
 * Kept free of Android and Compose so it runs in the plain-JUnit suite, and so
 * the picker only decides *where* a finger is while this decides *what colour
 * that is*. Colours are opaque `0xAARRGGBB` ints, the shape the rest of the
 * model already uses.
 */
object ColorMath {

    /** `#rrggbb`, lower-case, alpha dropped: the form iCalendar and the web app carry. */
    fun toHex(argb: Int): String = "#%06x".format(argb and 0xFFFFFF)

    /**
     * Parses `#rrggbb` or `rrggbb`, case-insensitively.
     *
     * Returns null rather than a fallback: a colour the person typed that this
     * cannot read must not silently become some other colour.
     */
    fun parseHex(raw: String): Int? {
        val hex = raw.trim().removePrefix("#")
        if (hex.length != 6) return null
        val rgb = hex.toLongOrNull(16) ?: return null
        return (0xFF000000L or rgb).toInt()
    }

    /** Hue in degrees `[0,360)`, saturation and value in `[0,1]`. */
    fun hsvFrom(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val saturation = if (max == 0f) 0f else delta / max
        return floatArrayOf(hue, saturation, max)
    }

    /** The inverse of [hsvFrom]; hue wraps, saturation and value clamp. */
    fun argbFrom(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        // Rounded, not truncated: truncation loses a step in a channel and the
        // colour that comes back out of the picker is not the one that went in.
        fun channel(input: Float) = (((input + m) * 255f) + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** WCAG relative luminance, the input to every contrast decision here. */
    fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** WCAG contrast ratio, from 1.0 (identical) to 21.0 (black on white). */
    fun contrastRatio(first: Int, second: Int): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        val lighter = max(a, b)
        val darker = min(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Whether [fill] would disappear against [surface].
     *
     * Category styling paints the fill at ~13% and strokes it at full strength,
     * and an event colour becomes a stripe; a colour within [minimum] of the
     * surface is invisible in at least one of those roles. The picker warns
     * rather than refuses -- a person may have a palette that only makes sense
     * on their own screen -- so this is advice, not validation.
     */
    fun isNearlyInvisible(fill: Int, surface: Int, minimum: Double = 1.35): Boolean =
        contrastRatio(fill, surface) < minimum
}
