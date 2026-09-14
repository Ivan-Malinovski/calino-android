package calino.malinov.ski.qa

import calino.malinov.ski.state.SplitPaneMinWidthDp
import calino.malinov.ski.state.shouldSplit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The month root splits only when the window can carry both a readable
 * seven-column grid and an agenda column beside it. Landscape alone is not the
 * test: a compact phone turned sideways stays on the portrait zoom surface.
 */
class SplitPaneRulesTest {
    @Test
    fun `unfolded foldable in landscape splits`() {
        assertTrue(shouldSplit(widthDp = 1148, heightDp = 862))
    }

    @Test
    fun `portrait never splits`() {
        assertFalse(shouldSplit(widthDp = 862, heightDp = 1148))
        assertFalse(shouldSplit(widthDp = 411, heightDp = 891))
    }

    @Test
    fun `narrow phone in landscape stays on the portrait surface`() {
        assertFalse(shouldSplit(widthDp = 640, heightDp = 360))
    }

    @Test
    fun `square window is not landscape enough`() {
        assertFalse(shouldSplit(widthDp = 800, heightDp = 800))
    }

    @Test
    fun `the width floor is inclusive`() {
        assertTrue(shouldSplit(widthDp = SplitPaneMinWidthDp, heightDp = 400))
        assertFalse(shouldSplit(widthDp = SplitPaneMinWidthDp - 1, heightDp = 400))
    }
}
