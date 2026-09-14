package calino.malinov.ski.qa

import calino.malinov.ski.state.PocReturnTarget
import calino.malinov.ski.state.restoresDayModal
import calino.malinov.ski.state.restoresTasks
import calino.malinov.ski.state.restoresJournal
import calino.malinov.ski.state.restoresAgenda
import calino.malinov.ski.ui.surfaces.PockRoute
import calino.malinov.ski.ui.surfaces.detailOriginRootRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocNavigationStateTest {
    @Test
    fun detailOriginRootIsReusedByQuickAddBackground() {
        assertEquals(
            PockRoute.Range,
            detailOriginRootRoute(PocReturnTarget.Range, PockRoute.Day),
        )
        assertEquals(
            PockRoute.Agenda,
            detailOriginRootRoute(PocReturnTarget.Agenda, PockRoute.Day),
        )
    }

    @Test
    fun overlayOrigins_restoreTheSurfaceThatOpenedThem() {
        assertTrue(PocReturnTarget.DayModal.restoresDayModal())
        assertTrue(PocReturnTarget.Tasks.restoresTasks())
        assertTrue(PocReturnTarget.Journal.restoresJournal())
        assertFalse(PocReturnTarget.Calendar.restoresDayModal())
        assertFalse(PocReturnTarget.Detail.restoresTasks())
        // Calendars is its own root, so it restores none of the overlay hosts.
        assertFalse(PocReturnTarget.Accounts.restoresDayModal())
        assertFalse(PocReturnTarget.Accounts.restoresTasks())
        assertFalse(PocReturnTarget.Accounts.restoresJournal())
        assertFalse(PocReturnTarget.Accounts.restoresAgenda())
    }
}
