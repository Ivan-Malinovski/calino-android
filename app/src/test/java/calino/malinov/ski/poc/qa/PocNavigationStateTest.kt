package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.state.PocReturnTarget
import calino.malinov.ski.poc.state.restoresDayModal
import calino.malinov.ski.poc.state.restoresTasks
import calino.malinov.ski.poc.state.restoresJournal
import calino.malinov.ski.poc.state.restoresAgenda
import calino.malinov.ski.poc.ui.surfaces.PockRoute
import calino.malinov.ski.poc.ui.surfaces.detailOriginRootRoute
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
