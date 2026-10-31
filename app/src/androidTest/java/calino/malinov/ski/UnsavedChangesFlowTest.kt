package calino.malinov.ski

import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso
import org.junit.Test

/** The existing journal/contact editors use the same decision as events/tasks. */
class UnsavedChangesFlowTest : CalinoUiTest() {
    @Test fun journalPillKeepsTheDraftThenSavesOnDismissal() {
        compose.openRoute("Journal")
        compose.onNodeWithContentDescription("Open journal entry A clear Monday").performTouchInput {
            click(percentOffset(.5f, .15f))
        }
        compose.onNodeWithContentDescription("Edit journal entry").performClick()
        compose.onNodeWithContentDescription("Journal title").performTextReplacement("Keep this thought")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Cancel journal editing").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithContentDescription("Journal title").assertTextContains("Keep this thought")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Cancel journal editing").performClick()
        compose.onNodeWithText("Save changes").performClick()
        awaitDescribed("Open journal entry Keep this thought")
    }

    @Test fun contactDiscardLeavesTheOriginalContact() {
        compose.openRoute("Contacts")
        compose.onNodeWithContentDescription("Open contact Ada Lovelace").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit contact").performClick()
        compose.onNodeWithContentDescription("Display name").performTextReplacement("Edited Ada")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Cancel contact editing").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Edited Ada").assertIsDisplayed()
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Cancel contact editing").performClick()
        compose.onNodeWithContentDescription("Discard changes and close").performClick()
        awaitNoDescribed("Cancel contact editing")
        compose.onNodeWithText("Edited Ada").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open contact Ada Lovelace").assertExists()
    }
}
