package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import calino.malinov.ski.ui.range.RangePagerTag
import org.junit.Assert.assertFalse
import org.junit.Test

class RangeInteractionTest : CalinoUiTest() {
    @Test fun eventDetailReturnsToTheRangePageAnchor() {
        compose.openRoute("Range")
        repeat(2) {
            compose.onNodeWithTag(RangePagerTag).performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }
        compose.onNodeWithText("May 24 – May 26").assertIsDisplayed()

        val designReviews = compose.onAllNodesWithContentDescription("Design review", substring = true)
        val visibleEvent = designReviews.fetchSemanticsNodes().indexOfFirst {
            it.boundsInRoot.left >= 0f && it.boundsInRoot.top >= 0f
        }
        designReviews[visibleEvent].performClick()
        awaitDescribed("Close event preview")
        compose.onNodeWithText("Cancel").performClick()
        awaitNoDescribed("Close event preview")

        compose.onNodeWithText("May 24 – May 26").assertIsDisplayed()
        awaitDescribed("Add on Sun, 24 May. Swipe up to search")
        compose.onNodeWithContentDescription("Add on Sun, 24 May. Swipe up to search").assertIsDisplayed()
    }

    @Test fun todayReturnsFromAnotherRangePage() {
        compose.openRoute("Range")
        assertFalse(compose.exists(hasContentDescription("Go to today")))

        compose.onNodeWithTag(RangePagerTag).performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Go to today").assertIsDisplayed().performClick()

        compose.onNodeWithText("May 18 – May 20").assertIsDisplayed()
        assertFalse(compose.exists(hasContentDescription("Go to today")))
    }

    @Test fun oneDayModePagesByOneDayAndPersists() {
        compose.openRoute("Range")
        compose.onNodeWithContentDescription("Range size in days: 1").performClick()
        compose.onNodeWithText("May 18").assertIsDisplayed()

        compose.onNodeWithTag(RangePagerTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("May 19").assertIsDisplayed()

        compose.openRoute("Agenda")
        compose.openRoute("Range")
        compose.onNodeWithContentDescription("Range size in days: 1").assertIsDisplayed()
        compose.onNodeWithText("May 19").assertIsDisplayed()
    }

    @Test fun rangeOffersOneThreeAndSevenDayModes() {
        compose.openRoute("Range")
        listOf("1", "3", "7").forEach { option ->
            compose.onNodeWithContentDescription("Range size in days: $option").assertIsDisplayed()
        }
    }
}
