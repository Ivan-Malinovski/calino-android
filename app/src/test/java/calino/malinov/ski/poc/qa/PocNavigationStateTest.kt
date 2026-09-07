package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.state.PocReturnTarget
import calino.malinov.ski.poc.state.restoresDayModal
import calino.malinov.ski.poc.state.restoresTasks
import calino.malinov.ski.poc.state.restoresJournal
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
    }
}
