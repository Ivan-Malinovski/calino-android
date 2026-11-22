package calino.malinov.ski.qa

import calino.malinov.ski.ui.surfaces.taskNotesExcerpt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The subtitle a task row shows: the first line of its notes as prose.
 * Everything here is markdown the person did not write for a 6 pt caption.
 */
class TaskNotesExcerptTest {

    @Test fun theFirstRealLineIsTheSubtitle() {
        assertEquals("Prepare performance review notes", taskNotesExcerpt("Prepare performance review notes"))
        // The first non-blank line is the subtitle, blank lines and all.
        assertEquals("Warm water first", taskNotesExcerpt("Warm water first\n\nAsk about the kitchen tap"))
    }

    @Test fun headingBulletAndQuoteMarksAreNotPartOfTheLine() {
        assertEquals("Groceries", taskNotesExcerpt("### Groceries\n- Tulips"))
        assertEquals("Tulips", taskNotesExcerpt("> - Tulips"))
    }

    @Test fun checklistBoxesAreNotWhatTheLineSays() {
        assertEquals("Tulips", taskNotesExcerpt("- [ ] Tulips\n- [ ] Oat milk"))
        assertEquals("Oat milk", taskNotesExcerpt("- [x] Oat milk"))
    }

    @Test fun aLinkReadsAsItsOwnText() {
        assertEquals(
            "Read the CalDAV notes and add comments.",
            taskNotesExcerpt("Read the [CalDAV notes](https://example.com/caldav-notes) and add comments."),
        )
    }

    @Test fun emphasisAndCodeMarksAreStripped() {
        assertEquals("Book the ferry before noon", taskNotesExcerpt("**Book the ferry** before *noon*"))
        assertEquals("Run the migration", taskNotesExcerpt("Run the `migration`"))
        assertEquals("snake_case stays", taskNotesExcerpt("snake_case stays"))
    }

    @Test fun rulesAndFencesAreSkippedEntirely() {
        assertEquals("Real first line", taskNotesExcerpt("---\n```\nReal first line"))
        assertNull(taskNotesExcerpt("---\n***\n"))
    }

    @Test fun nothingToSayIsNoSubtitle() {
        assertNull(taskNotesExcerpt(null))
        assertNull(taskNotesExcerpt(""))
        assertNull(taskNotesExcerpt("   \n\n  "))
    }
}
