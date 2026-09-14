package calino.malinov.ski.qa

import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.state.CalinoSurfaceMode
import calino.malinov.ski.state.CalinoWindowClass
import calino.malinov.ski.state.CompactWindowMaxWidthDp
import calino.malinov.ski.state.MediumWindowMaxWidthDp
import calino.malinov.ski.state.calinoEndLaneActive
import calino.malinov.ski.state.calinoFloatsInEndLane
import calino.malinov.ski.state.calinoSurfaceModeFor
import calino.malinov.ski.state.calinoWindowClassFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWindowRulesTest {
    @Test
    fun `compact boundary keeps the bottom sheet`() {
        assertEquals(CalinoWindowClass.Compact, calinoWindowClassFor(CompactWindowMaxWidthDp))
        assertEquals(CalinoSurfaceMode.BottomSheet, calinoSurfaceModeFor(CalinoWindowClass.Compact, CalinoSurfaceKind.Editor))
    }

    @Test
    fun `medium window uses a centered floating surface`() {
        assertEquals(CalinoWindowClass.Medium, calinoWindowClassFor(CompactWindowMaxWidthDp + 1))
        assertEquals(CalinoWindowClass.Medium, calinoWindowClassFor(MediumWindowMaxWidthDp))
        assertEquals(CalinoSurfaceMode.FloatingWindow, calinoSurfaceModeFor(CalinoWindowClass.Medium, CalinoSurfaceKind.Detail))
    }

    @Test
    fun `expanded detail surfaces use the end panel`() {
        assertEquals(CalinoWindowClass.Expanded, calinoWindowClassFor(MediumWindowMaxWidthDp + 1))
        assertEquals(CalinoSurfaceMode.EndPanel, calinoSurfaceModeFor(CalinoWindowClass.Expanded, CalinoSurfaceKind.Day))
        assertEquals(CalinoSurfaceMode.EndPanel, calinoSurfaceModeFor(CalinoWindowClass.Expanded, CalinoSurfaceKind.Editor))
    }

    @Test
    fun `compact overlays remain centered when expanded`() {
        assertEquals(CalinoSurfaceMode.FloatingWindow, calinoSurfaceModeFor(CalinoWindowClass.Expanded, CalinoSurfaceKind.Search))
        assertEquals(CalinoSurfaceMode.FloatingWindow, calinoSurfaceModeFor(CalinoWindowClass.Expanded, CalinoSurfaceKind.Dialog))
        assertEquals(CalinoSurfaceMode.FloatingWindow, calinoSurfaceModeFor(CalinoWindowClass.Expanded, CalinoSurfaceKind.CompactPreview))
        assertEquals(CalinoSurfaceMode.FloatingWindow, calinoSurfaceModeFor(CalinoWindowClass.Expanded, CalinoSurfaceKind.Preview))
    }

    @Test
    fun `the landscape split opens the end lane before expanded does`() {
        assertTrue(calinoEndLaneActive(800, 600))
        assertEquals(CalinoWindowClass.Medium, calinoWindowClassFor(800))
        assertEquals(
            CalinoSurfaceMode.EndPanel,
            calinoSurfaceModeFor(CalinoWindowClass.Medium, CalinoSurfaceKind.Editor, endLane = true),
        )
    }

    @Test
    fun `a wide portrait window has no lane to anchor to`() {
        assertFalse(calinoEndLaneActive(900, 1400))
        assertEquals(
            CalinoSurfaceMode.FloatingWindow,
            calinoSurfaceModeFor(CalinoWindowClass.Expanded, CalinoSurfaceKind.Day, endLane = false),
        )
    }

    @Test
    fun `previews follow the pill into the lane, search stays centered`() {
        assertTrue(calinoFloatsInEndLane(CalinoSurfaceKind.Preview))
        assertTrue(calinoFloatsInEndLane(CalinoSurfaceKind.CompactPreview))
        assertFalse(calinoFloatsInEndLane(CalinoSurfaceKind.Search))
        assertFalse(calinoFloatsInEndLane(CalinoSurfaceKind.Dialog))
    }

    @Test
    fun `a landscape phone is still too narrow for a lane`() {
        assertFalse(calinoEndLaneActive(640, 360))
    }
}
