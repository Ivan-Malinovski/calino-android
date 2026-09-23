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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import calino.malinov.ski.ui.components.SwipeDownDismissTag
import calino.malinov.ski.ui.surfaces.JournalEntryPagerTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Journal create / edit / delete and overview changes.
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

    /**
     * The reader pages by swipe. Entries are sorted newest first, so a swipe
     * to the right -- back a page -- lands on the next entry *up* the list,
     * which is the day after the one that was open.
     */
    @Test fun swipingBackPagesToTheNewerEntry() {
        openEntry()

        compose.onNodeWithTag(JournalEntryPagerTag).performTouchInput { swipeRight() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Journal title").assertTextContains("Design Sprint — Day 1")
        compose.onNodeWithContentDescription("Edit journal entry").assertIsDisplayed()
    }

    @Test fun swipingForwardPagesToTheOlderEntry() {
        openEntry()

        compose.onNodeWithTag(JournalEntryPagerTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        // The oldest fixture entry has no title, so the reader states that.
        compose.onNodeWithContentDescription("Journal title").assertTextContains("Untitled note")
    }

    /** There is nothing past the oldest entry, and the reader must not leave it. */
    @Test fun swipingPastTheLastEntryStaysPut() {
        openEntry()
        compose.onNodeWithTag(JournalEntryPagerTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithTag(JournalEntryPagerTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Journal title").assertTextContains("Untitled note")
        compose.onNodeWithContentDescription("Edit journal entry").assertIsDisplayed()
    }

    /** The pager must not have taken the modal's downward dismissal with it. */
    @Test fun aFullSwipeDownStillDismissesTheReader() {
        openEntry()

        // Every page carries its own card, so pick the one on screen rather
        // than the neighbours parked to either side of it. A page the pager
        // has clipped away reports empty bounds, not offset ones, so width is
        // what separates the card on screen from the two that are not.
        val cards = compose.onAllNodesWithTag(SwipeDownDismissTag)
        val visible = cards.fetchSemanticsNodes().indexOfFirst {
            val bounds = it.boundsInRoot
            bounds.width > 1f && bounds.height > 1f
        }
        assertTrue("no visible journal card", visible >= 0)
        cards[visible].performTouchInput {
            swipeDown(startY = top + (height * .1f), endY = bottom)
        }
        compose.waitForIdle()

        // The card leaves on its own close animation, so wait it out rather
        // than reading the frame the gesture happened to end on.
        awaitNoDescribed("Edit journal entry")
    }

    /** An editor being written to keeps its pointer stream instead of paging. */
    @Test fun aDirtyEditorDoesNotPage() {
        openEntry()
        beginEditing()
        compose.onNodeWithContentDescription("Journal title").performTextInput(Edited)
        compose.waitForIdle()

        compose.onNodeWithTag(JournalEntryPagerTag).performTouchInput { swipeRight() }
        compose.waitForIdle()

        // Still the same entry, still mid-edit: the date row is the entry's
        // own, and an unsaved change is still waiting to be saved.
        compose.onNodeWithContentDescription("Save journal entry").assertIsDisplayed()
        compose.onNodeWithContentDescription("Date: Friday 15 May 2026").assertIsDisplayed()
    }

    /** An entry can be moved to another day, so the edit pane states its date. */
    @Test fun theEditPaneCarriesTheEntryDate() {
        openEntry()

        compose.onNodeWithContentDescription("Date: Friday 15 May 2026").assertDoesNotExist()

        beginEditing()

        compose.onNodeWithContentDescription("Date: Friday 15 May 2026").assertIsDisplayed()
    }

    @Test fun createsAnEntry() {
        openJournal()
        compose.onNodeWithContentDescription("New entry. Swipe up for views").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Journal title").performTextInput(NewTitle)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save journal entry").performClick()

        awaitDescribed("Open journal entry $NewTitle")
    }

    @Test fun cancellingACreateLeavesNothingBehind() {
        openJournal()
        compose.onNodeWithContentDescription("New entry. Swipe up for views").performClick()
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

    @Test fun previewsTheJournalBody() {
        openEntry()
        beginEditing()

        compose.onNodeWithContentDescription("Journal editor mode: Preview").performClick()
        compose.waitForIdle()

        // Preview swaps the editable field out for rendered markdown, so the
        // body field's absence is what actually distinguishes the two modes;
        // the excerpt text alone also matches the list card behind the modal.
        compose.onNodeWithContentDescription("Journal body").assertDoesNotExist()

        compose.onNodeWithContentDescription("Journal editor mode: Write").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Journal body").assertExists()
    }

    @Test fun boldFormattingWrapsInsertedText() {
        openJournal()
        compose.onNodeWithContentDescription("New entry. Swipe up for views").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Journal body").performTextInput("formatted")

        compose.onNodeWithContentDescription("Bold").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Journal body").assertTextContains("formatted****")
    }

    @Test fun togglesTheMonthOverview() {
        openJournal()

        compose.onNodeWithContentDescription("Change journal overview, level 1 of 2").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Previous month").assertIsDisplayed()

        compose.onNodeWithContentDescription("Change journal overview, level 2 of 2").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Change journal overview, level 1 of 2").assertIsDisplayed()
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
