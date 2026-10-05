package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/** An inline save keeps its modal open, so its result must appear on that pill. */
class PillInlineFeedbackTest : CalinoUiTest() {
    @Test fun inlineEventSaveShowsSavedThenRestoresItsActions() {
        compose.onNodeWithContentDescription("Lunch with Maya, 12:30 PM, Café Lumen").performClick()
        awaitDescribed("Close event preview")
        val titles = compose.onAllNodesWithContentDescription("Event title")
        val visible = titles.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.left >= 0f && it.boundsInRoot.width > 0f }
        titles[visible].performTextInput(" revised")
        compose.onNodeWithContentDescription("Save event changes").performClick()
        awaitDescribed("Saved")
        compose.onNodeWithContentDescription("Saved").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close event preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save event changes").assertDoesNotExist()
        awaitNoDescribed("Saved")
        compose.onNodeWithContentDescription("Open event").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete event").assertIsDisplayed()
    }
}
