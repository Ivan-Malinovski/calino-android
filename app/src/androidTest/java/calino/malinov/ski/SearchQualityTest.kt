package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import calino.malinov.ski.ui.components.SwipeDownDismissTag
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchQualityTest : CalinoUiTest() {

    @Test fun typoSearchFindsAndOpensTheEvent() {
        openSearch()
        compose.onNodeWithContentDescription("Search Calino").performTextInput("Desgn")
        compose.onNodeWithTag("event:evt-design").assertIsDisplayed().performClick()
        compose.waitUntil(3_000) { !compose.hasDescribedNode("Search Calino") }
    }

    @Test fun typeFilterChangesResultsAndResetsAfterClose() {
        openSearch()
        compose.onNodeWithContentDescription("Search Calino").performTextInput("Design")
        compose.onNodeWithTag("event:evt-design").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show search filters").performClick()
        compose.onNodeWithContentDescription("Events, filter search by events").performClick()
        assertFalse(compose.exists(hasTestTag("event:evt-design")))

        // The first back closes the keyboard, the second the sheet.
        Espresso.pressBack()
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitUntil(3_000) { !compose.hasDescribedNode("Search Calino") }
        openSearch()
        compose.onNodeWithContentDescription("Search Calino").performTextInput("Design")
        compose.onNodeWithTag("event:evt-design").assertIsDisplayed()
    }

    @Test fun initialStateOffersDateJumpsAndTheHint() {
        openSearch()
        compose.onNodeWithContentDescription("Today, go to today").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tomorrow, go to tomorrow").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next week, go to next week").assertIsDisplayed()
        compose.onNodeWithText("Or type an event to add it — “lunch tue 12:30”.").assertIsDisplayed()
        // Filters replace the jump chips rather than stacking under them.
        compose.onNodeWithContentDescription("Show search filters").performClick()
        compose.waitUntil(3_000) { !compose.hasDescribedNode("Today, go to today") }
    }

    @Test fun jumpChipNavigatesAndClosesSearch() {
        openSearch()
        compose.onNodeWithContentDescription("Tomorrow, go to tomorrow").performClick()
        compose.waitUntil(3_000) { !compose.hasDescribedNode("Search Calino") }
    }

    @Test fun draggingDownOverScrolledResultsScrollsBackInsteadOfClosing() {
        openSearch()
        compose.onNodeWithContentDescription("Search Calino").performTextInput("e")
        compose.waitForIdle()
        val sheet = compose.onNodeWithTag(SwipeDownDismissTag)
        repeat(2) {
            sheet.performTouchInput { swipe(Offset(centerX, height * .8f), Offset(centerX, height * .3f), 300) }
        }
        // A drag past the dismiss threshold, but the list is not at its top.
        sheet.performTouchInput { swipe(Offset(centerX, height * .4f), Offset(centerX, height * .8f), 600) }
        compose.waitForIdle()
        assertTrue(compose.hasDescribedNode("Search Calino"))
    }

    private fun openSearch() {
        // Search is the first row of the pill's view menu.
        compose.onNodeWithContentDescription("Views, current: Month").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Search").performClick()
        compose.waitUntil(3_000) { compose.hasDescribedNode("Search Calino") }
    }
}
