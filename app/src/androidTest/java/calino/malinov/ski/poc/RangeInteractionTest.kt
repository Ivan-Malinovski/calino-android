package calino.malinov.ski.poc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import calino.malinov.ski.poc.ui.range.RangePagerTag
import org.junit.Assert.assertFalse
import org.junit.Test

class RangeInteractionTest : CalinoUiTest() {
    @Test fun todayReturnsFromAnotherRangePage() {
        compose.openRoute("Range")
        assertFalse(compose.exists(hasContentDescription("Go to today")))

        compose.onNodeWithTag(RangePagerTag).performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Go to today").assertIsDisplayed().performClick()

        compose.onNodeWithText("May 18 – May 20").assertIsDisplayed()
        assertFalse(compose.exists(hasContentDescription("Go to today")))
    }
}
