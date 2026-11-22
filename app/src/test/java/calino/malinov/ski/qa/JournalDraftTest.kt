package calino.malinov.ski.qa

import calino.malinov.ski.data.model.JournalDraft
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

    @Test
    fun draftCommit_carriesTheTagsTheEditorCollected() {
        // A new entry is written through this commit, not straight from the
        // editor state: the tags used to stop here, so the first write of a
        // tagged entry reached the server without its CATEGORIES.
        val entry = JournalDraft("draft-3", fixtureDate)
            .commit("Morning", "A thought.", listOf(" Focus ", "", "Work", "Focus"))

        requireNotNull(entry)
        assertEquals(listOf("Focus", "Work"), entry.categories)
    }
}
