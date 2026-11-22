package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The typed line can say what it is. "remind me to ..." is a task however the
 * editor was opened, and the editor has to follow -- the kind chips included --
 * while "task force meeting" stays an event, because "task" is a word.
 */
@RunWith(AndroidJUnit4::class)
class QuickAddKindSwitchTest : CalinoUiTest() {

    @Test fun typingAReminderSwitchesTheDraftToATask() {
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Title, event").assertIsDisplayed()

        compose.onNodeWithContentDescription("Title, event").performTextInput("remind me to call the bank tomorrow at 9")
        compose.waitForIdle()

        // The editor is now a task. The line itself keeps what was typed -- it
        // is the field, and the parsed title is what the record will be called
        // (PocQuickAddParserNaturalLanguageTest pins that half).
        compose.waitUntil(WaitMillis) { compose.hasDescribedNode("Title, task") }
        compose.onNodeWithContentDescription("Title, task").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel editor").performClick()
    }

    @Test fun anEventThatMerelyStartsWithTheWordTaskStaysAnEvent() {
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Title, event").performTextInput("task force meeting at 3pm")
        compose.waitForIdle()

        // Still an event, and the kind never flickers to Task.
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Title, event").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel editor").performClick()
    }

    private companion object {
        const val WaitMillis = 6_000L
    }
}
