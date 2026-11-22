package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The search sheet also answers a command. The web's command palette lists
 * these beside search; here the same surface has to both find a record and do
 * the thing the person typed.
 */
@RunWith(AndroidJUnit4::class)
class SearchCommandsUiTest : CalinoUiTest() {

    @Test fun theSheetOffersItsThreeCommandsBeforeAnythingIsTyped() {
        openSearch()

        compose.onNodeWithContentDescription("New event").assertIsDisplayed()
        compose.onNodeWithContentDescription("New task").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sync now").assertIsDisplayed()
    }

    @Test fun typingASettingsCommandReachesTheRoute() {
        openSearch()

        compose.onNodeWithContentDescription("Search Calino").performTextInput("settings")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Go to Settings").assertIsDisplayed()
    }

    @Test fun typingACommandOpensTheTaskEditor() {
        openSearch()

        compose.onNodeWithContentDescription("Search Calino").performTextInput("new task")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("New task").performClick()

        // The editor is open on the task it says it would make.
        compose.waitUntil(WaitMillis) { compose.hasDescribedNode("Title, task") }
        // The quick-add editor's own pill, not the detail card's: this is the
        // full editor a "New task" command opens.
        compose.onNodeWithContentDescription("Save editor").assertIsDisplayed()
    }

    @Test fun aRouteCommandNavigates() {
        openSearch()

        compose.onNodeWithContentDescription("Search Calino").performTextInput("go to tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Go to Tasks").performClick()

        compose.waitUntil(WaitMillis) { compose.hasDescribedNode("Task filter: All") }
    }

    private fun openSearch() {
        // Search is the first row of the pill's view menu.
        compose.onNodeWithContentDescription("Views, current: Month").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Search").performClick()
        compose.waitUntil(WaitMillis) { compose.hasDescribedNode("Search Calino") }
    }

    private companion object {
        const val WaitMillis = 8_000L
    }
}
