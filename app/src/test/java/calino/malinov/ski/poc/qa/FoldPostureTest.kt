package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.state.CalinoFoldState
import calino.malinov.ski.poc.state.CalinoFoldPosture
import calino.malinov.ski.poc.state.CalinoWindowClass
import calino.malinov.ski.poc.state.calinoLayoutSpec
import calino.malinov.ski.poc.state.foldPostureOf
import calino.malinov.ski.poc.state.hingeOpenness
import calino.malinov.ski.poc.state.foldSplitProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldPostureTest {

    private fun bookPosture(start: Float = 400f, end: Float = 440f) = foldPostureOf(
        isVerticalHinge = true,
        isHalfOpen = true,
        isSeparating = true,
        hingeStartDp = start,
        hingeEndDp = end,
    )

    @Test
    fun `a flat inner display reports no band to dodge`() {
        val posture = foldPostureOf(
            isVerticalHinge = true,
            isHalfOpen = false,
            isSeparating = false,
            hingeStartDp = 400f,
            hingeEndDp = 400f,
        )
        assertEquals(CalinoFoldState.Flat, posture.state)
        assertNull(posture.hingeStartDp)
        assertFalse(posture.isBookPosture)
    }

    @Test
    fun `half open across a vertical hinge is book posture`() {
        assertTrue(bookPosture().isBookPosture)
    }

    @Test
    fun `a horizontal hinge is not book posture`() {
        val posture = foldPostureOf(
            isVerticalHinge = false,
            isHalfOpen = true,
            isSeparating = true,
            hingeStartDp = 300f,
            hingeEndDp = 340f,
        )
        assertFalse(posture.isBookPosture)
    }

    @Test
    fun `book posture splits below the width rule but not on a small window`() {
        assertTrue(calinoLayoutSpec(640, 900, bookPosture()).splitPanes)
        assertFalse(calinoLayoutSpec(560, 900, bookPosture()).splitPanes)
    }

    @Test
    fun `the width rule still decides without a hinge`() {
        assertTrue(calinoLayoutSpec(840, 500, CalinoFoldPosture.None).splitPanes)
        assertFalse(calinoLayoutSpec(700, 500, CalinoFoldPosture.None).splitPanes)
        // Portrait stays a single pane however wide it is.
        assertFalse(calinoLayoutSpec(840, 1200, CalinoFoldPosture.None).splitPanes)
    }

    @Test
    fun `the keep-out band is only reported in book posture`() {
        val open = calinoLayoutSpec(840, 500, bookPosture(start = 400f, end = 440f))
        assertEquals(40f, open.hingeBandDp, .001f)

        val flat = foldPostureOf(
            isVerticalHinge = true,
            isHalfOpen = false,
            isSeparating = true,
            hingeStartDp = 400f,
            hingeEndDp = 440f,
        )
        assertEquals(0f, calinoLayoutSpec(840, 500, flat).hingeBandDp, .001f)
    }

    @Test
    fun `the spec keeps the existing width buckets`() {
        assertEquals(CalinoWindowClass.Compact, calinoLayoutSpec(400, 800).windowClass)
        assertEquals(CalinoWindowClass.Medium, calinoLayoutSpec(700, 800).windowClass)
        assertEquals(CalinoWindowClass.Expanded, calinoLayoutSpec(900, 800).windowClass)
    }
}

class HingeAngleTest {

    @Test
    fun `openness spans the sensor's own range`() {
        assertEquals(0f, hingeOpenness(0f, 180f), .001f)
        assertEquals(.5f, hingeOpenness(90f, 180f), .001f)
        assertEquals(1f, hingeOpenness(180f, 180f), .001f)
        // Some hinges report up to 360; the range comes from the sensor.
        assertEquals(.5f, hingeOpenness(180f, 360f), .001f)
    }

    @Test
    fun `angles outside the range clamp rather than run past the ends`() {
        assertEquals(0f, hingeOpenness(-5f, 180f), .001f)
        assertEquals(1f, hingeOpenness(200f, 180f), .001f)
    }

    @Test
    fun `a nonsense range falls back to a half turn`() {
        assertEquals(.5f, hingeOpenness(90f, 0f), .001f)
    }

    @Test
    fun `flat does not divide the layout`() {
        assertEquals(0f, foldSplitProgress(1f), .001f)
        assertEquals(0f, foldSplitProgress(.98f), .001f)
    }

    @Test
    fun `the split follows the hinge between the two thresholds`() {
        // Leaving flat starts it, and it is complete well before the halves
        // face each other, so the last stretch of the fold moves nothing.
        assertTrue(foldSplitProgress(.9f) > 0f)
        assertTrue(foldSplitProgress(.9f) < 1f)
        assertTrue(foldSplitProgress(.8f) > foldSplitProgress(.9f))
        assertEquals(1f, foldSplitProgress(.7f), .001f)
        assertEquals(1f, foldSplitProgress(.2f), .001f)
    }

    @Test
    fun `progress is monotonic as the device closes`() {
        var previous = -1f
        for (step in 0..100) {
            val progress = foldSplitProgress(1f - step / 100f)
            assertTrue(progress >= previous)
            previous = progress
        }
    }
}
