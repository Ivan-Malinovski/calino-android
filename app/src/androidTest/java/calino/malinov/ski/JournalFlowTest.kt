package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Journal create / edit / delete and mode changes.
 *
 * The Journal surface is reachable without setting a preference first, because
 * the fixture data contains entries and `featureAvailabilityAfter` turns a
 * surface on once records exist -- detection is one-way and never hides it.
 *
 * Journal has its own editor rather than the shared Quick Add one: its fields
 * are "Journal title"/"Journal body" and its actions "Save journal entry" /
 * "Cancel journal editing". Opening an existing entry enters that editor
 * directly.
 */
@RunWith(AndroidJUnit4::class)
class JournalFlowTest : CalinoUiTest() {

    @Test fun listsTheFixtureEntries() {
        openJournal()

        compose.onNodeWithContentDescription("Open journal entry A clear Monday").assertIsDisplayed()
    }

    @Test fun opensAnExistingEntryInReadMode() {
        openEntry()

        compose.onNodeWithContentDescription("Edit journal entry").assertIsDisplayed()
        compose.onNodeWithContentDescription("Journal title").assertIsDisplayed()
    }

    @Test fun createsAnEntry() {
        openJournal()
        compose.onNodeWithContentDescription("New entry. Swipe up to search").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Journal title").performTextInput(NewTitle)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save journal entry").performClick()

        awaitDescribed("Open journal entry $NewTitle")
    }

    @Test fun cancellingACreateLeavesNothingBehind() {
        openJournal()
        compose.onNodeWithContentDescription("New entry. Swipe up to search").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Journal title").performTextInput(NewTitle)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Cancel journal editing").performClick()
        compose.waitForIdle()
        // An abandoned draft asks before it is thrown away.
        compose.onNodeWithText("Discard your changes?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Discard journal changes").performClick()
        compose.waitForIdle()

        awaitNoDescribed("Open journal entry $NewTitle")
    }

    @Test fun editsAnExistingEntry() {
        openEntry()
        beginEditing()

        // Cleared first: performTextInput inserts at the cursor, and where the
        // cursor lands in a pre-filled field is not this test's subject.
        compose.onNodeWithContentDescription("Journal title").performTextClearance()
        compose.onNodeWithContentDescription("Journal title").performTextInput(Edited)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save journal entry").performClick()

        awaitDescribed("Open journal entry $Edited")
        assertFalse(compose.hasDescribedNode("Open journal entry $Existing"))
    }

    /** Backing out of an edit keeps the entry as it was. */
    @Test fun discardingAnEditKeepsTheOriginal() {
        openEntry()
        beginEditing()
        compose.onNodeWithContentDescription("Journal title").performTextInput(Edited)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Cancel journal editing").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Discard journal changes").performClick()

        awaitDescribed("Open journal entry $Existing")
    }

    @Test fun deletesAnEntryAfterConfirming() {
        openEntry()

        compose.onNodeWithContentDescription("Delete journal entry").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remove this note?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Confirm delete journal entry").performClick()

        awaitNoDescribed("Open journal entry $Existing")
    }

    @Test fun switchesBetweenListModes() {
        openJournal()

        compose.onNodeWithContentDescription("Journal list view: By month").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("MAY 2026").assertIsDisplayed()

        compose.onNodeWithContentDescription("Journal list view: All entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open journal entry $Existing").assertIsDisplayed()
    }

    private fun openJournal() {
        compose.openRoute("Journal")
        compose.waitForIdle()
    }

    /**
     * Open an entry by tapping the top of its card.
     *
     * Not the centre: the floating "New entry" pill is drawn over the bottom of
     * the list, and a card whose centre falls under it takes the pill's click
     * instead -- which silently opens a *new* draft rather than the entry, and
     * makes an edit look like a duplicate.
     */
    private fun openEntry() {
        openJournal()
        compose.onNodeWithContentDescription("Open journal entry $Existing").performTouchInput {
            click(percentOffset(.5f, .15f))
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Journal title").assertTextContains(Existing)
    }

    /** An opened entry lands in the read pane; editing is an explicit step. */
    private fun beginEditing() {
        compose.onNodeWithContentDescription("Edit journal entry").performClick()
        compose.waitForIdle()
    }

    private companion object {
        const val Existing = "Lunch with Mom"
        const val NewTitle = "A test entry"
        const val Edited = "Edited note"
    }
}
