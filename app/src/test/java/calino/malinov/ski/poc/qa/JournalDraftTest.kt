package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.model.JournalDraft
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalDraftTest {
    private val fixtureDate = LocalDate.of(2026, 5, 18)

    @Test
    fun blankDraft_isNotCommitted() {
        assertNull(JournalDraft("draft-1", fixtureDate).commit("  ", "\n"))
    }

    @Test
    fun draftCommit_trimsContent_andKeepsDraftIdentity() {
        val entry = JournalDraft("draft-2", fixtureDate).commit("  Morning  ", "  A thought.\n")

        requireNotNull(entry)
        assertEquals("draft-2", entry.id)
        assertEquals(fixtureDate, entry.date)
        assertEquals("Morning", entry.title)
        assertEquals("A thought.", entry.body)
    }
}
