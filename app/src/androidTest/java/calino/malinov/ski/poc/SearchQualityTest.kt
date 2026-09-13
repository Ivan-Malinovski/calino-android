package calino.malinov.ski.poc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.assertFalse
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

        compose.onNodeWithContentDescription("Close search").performClick()
        compose.waitUntil(3_000) { !compose.hasDescribedNode("Search Calino") }
        openSearch()
        compose.onNodeWithContentDescription("Search Calino").performTextInput("Design")
        compose.onNodeWithTag("event:evt-design").assertIsDisplayed()
    }

    private fun openSearch() {
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up to search")
            .performTouchInput { swipe(Offset(center.x, center.y), Offset(center.x, center.y - 240f), 300L) }
        compose.waitUntil(3_000) { compose.hasDescribedNode("Search Calino") }
    }
}
