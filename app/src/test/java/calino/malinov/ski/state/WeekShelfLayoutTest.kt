package calino.malinov.ski.state

import calino.malinov.ski.util.CalinoWeekShelf
import org.junit.Assert.*
import org.junit.Test

class WeekShelfLayoutTest {
    private fun layout(w: Int, h: Int, pref: CalinoWeekShelf = CalinoWeekShelf.Badge, tasks: Int = 2) =
        weekShelfLayoutFor(w, h, pref, tasks)

    @Test fun compactPortraitFollowsThePreference() {
        assertEquals(WeekShelfKind.Badge, layout(412, 915).kind)
        assertEquals(WeekShelfKind.Sheet, layout(412, 915, CalinoWeekShelf.Sheet).kind)
    }

    @Test fun badgeStaysWhenEmptyButSheetAndStripHide() {
        assertTrue(layout(412, 915, tasks = 0).visible)
        assertFalse(layout(412, 915, CalinoWeekShelf.Sheet, tasks = 0).visible)
        assertFalse(layout(900, 400, tasks = 0).visible)
        assertTrue(layout(900, 400, tasks = 1).visible)
    }

    @Test fun landscapePhoneAndTabletUseTheStripWhateverThePreference() {
        val phone = layout(800, 360, CalinoWeekShelf.Sheet)
        assertEquals(WeekShelfKind.Strip, phone.kind)
        assertEquals(WeekStripPhoneHeightDp, phone.stripHeightDp)
        val tablet = layout(1280, 800)
        assertEquals(WeekShelfKind.Strip, tablet.kind)
        assertEquals(WeekStripTabletHeightDp, tablet.stripHeightDp)
    }

    @Test fun tabletPortraitUsesACappedSheetWithoutAPreference() {
        val tablet = layout(700, 1100)
        assertEquals(WeekShelfKind.Sheet, tablet.kind)
        assertEquals(WeekSheetMaxWidthDp, tablet.sheetMaxWidthDp)
    }
}
