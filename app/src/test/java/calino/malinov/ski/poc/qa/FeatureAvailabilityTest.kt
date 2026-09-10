package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import calino.malinov.ski.poc.data.repository.FixtureRepository
import calino.malinov.ski.poc.state.FeatureAvailability
import calino.malinov.ski.poc.state.featureAvailabilityAfter
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureAvailabilityTest {

    private val empty = CalinoSnapshot(emptyList<CalEvent>(), emptyList<CalTask>(), emptyList<JournalEntry>())

    @Test
    fun contentDetection_enablesTheMatchingFeature() {
        val journalOnly = empty.copy(journals = listOf(JournalEntry("j", LocalDate.of(2026, 5, 1), "", "note")))
        val contactOnly = empty.copy(contacts = listOf(FixtureRepository().contacts().first()))

        assertEquals(FeatureAvailability(journalEnabled = true), featureAvailabilityAfter(journalOnly, FeatureAvailability()))
        assertEquals(FeatureAvailability(contactsEnabled = true), featureAvailabilityAfter(contactOnly, FeatureAvailability()))
    }

    @Test
    fun detection_neverDisables_andIsIdempotent() {
        val current = FeatureAvailability(journalEnabled = true, contactsEnabled = true)

        assertEquals(current, featureAvailabilityAfter(empty, current))
        assertEquals(current, featureAvailabilityAfter(empty, featureAvailabilityAfter(empty, current)))
    }

    @Test
    fun fixtureSnapshot_enablesBothSampleSurfaces() {
        val enabled = featureAvailabilityAfter(FixtureRepository().snapshot(), FeatureAvailability())

        assertEquals(FeatureAvailability(journalEnabled = true, contactsEnabled = true), enabled)
    }
}
