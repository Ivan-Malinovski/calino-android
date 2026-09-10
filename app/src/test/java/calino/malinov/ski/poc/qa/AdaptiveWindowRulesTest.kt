package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.state.CalinoSurfaceKind
import calino.malinov.ski.poc.state.CalinoSurfaceMode
import calino.malinov.ski.poc.state.CalinoWindowClass
import calino.malinov.ski.poc.state.CompactWindowMaxWidthDp
import calino.malinov.ski.poc.state.MediumWindowMaxWidthDp
import calino.malinov.ski.poc.state.calinoSurfaceModeFor
import calino.malinov.ski.poc.state.calinoWindowClassFor
import org.junit.Assert.assertEquals
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
    fun `search and dialogs remain centered when expanded`() {
        assertEquals(CalinoSurfaceMode.FloatingWindow, calinoSurfaceModeFor(CalinoWindowClass.Expanded, CalinoSurfaceKind.Search))
        assertEquals(CalinoSurfaceMode.FloatingWindow, calinoSurfaceModeFor(CalinoWindowClass.Expanded, CalinoSurfaceKind.Dialog))
    }
}
