package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.state.ContactsPaneMode
import calino.malinov.ski.poc.state.contactsPaneModeFor
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactsPaneRulesTest {
    @Test
    fun modeChangesAtInclusiveSplitBoundary() {
        assertEquals(ContactsPaneMode.FloatingWindow, contactsPaneModeFor(719, 500))
        assertEquals(ContactsPaneMode.Split, contactsPaneModeFor(720, 500))
        assertEquals(ContactsPaneMode.FloatingWindow, contactsPaneModeFor(720, 900))
        assertEquals(ContactsPaneMode.EndPanel, contactsPaneModeFor(840, 900))
        assertEquals(ContactsPaneMode.Sheet, contactsPaneModeFor(400, 800))
        assertEquals(ContactsPaneMode.FloatingWindow, contactsPaneModeFor(600, 800))
    }
}
