package calino.malinov.ski.state

import org.junit.Assert.*
import org.junit.Test

class WeekShelfLayoutTest {
    private fun layout(w: Int, h: Int, tasks: Int = 2) = weekShelfLayoutFor(w, h, tasks)

    @Test fun portraitWindowsUseTheBadge() {
        assertEquals(WeekShelfKind.Badge, layout(412, 915).kind)
        assertEquals(WeekShelfKind.Badge, layout(700, 1100).kind)
    }

    @Test fun badgeStaysWhenEmptyButStripHides() {
        assertTrue(layout(412, 915, tasks = 0).visible)
        assertFalse(layout(900, 400, tasks = 0).visible)
        assertTrue(layout(900, 400, tasks = 1).visible)
    }

    @Test fun landscapePhoneAndTabletUseTheStrip() {
        val phone = layout(800, 360)
        assertEquals(WeekShelfKind.Strip, phone.kind)
        assertEquals(WeekStripPhoneHeightDp, phone.stripHeightDp)
        val tablet = layout(1280, 800)
        assertEquals(WeekShelfKind.Strip, tablet.kind)
        assertEquals(WeekStripTabletHeightDp, tablet.stripHeightDp)
    }
}
