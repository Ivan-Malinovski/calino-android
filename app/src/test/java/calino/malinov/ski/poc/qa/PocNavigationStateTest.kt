package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.state.PocReturnTarget
import calino.malinov.ski.poc.state.restoresDayModal
import calino.malinov.ski.poc.state.restoresTasks
import calino.malinov.ski.poc.state.restoresJournal
import calino.malinov.ski.poc.state.restoresAgenda
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocNavigationStateTest {
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
