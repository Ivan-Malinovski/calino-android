package calino.malinov.ski

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Test

class PillGestureCancellationTest : CalinoUiTest() {
    @Test fun cancelledRouteSwipeReturnsToTheSameView() {
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views").performTouchInput {
            down(center)
            moveBy(Offset(-80f, 0f), delayMillis = 150)
            cancel()
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Views, current: Month").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views").assertIsDisplayed()
    }

    @Test fun cancelledDeleteHoldKeepsTheEventAndItsActions() {
        compose.onNodeWithContentDescription("Design review, 10:00 AM, Studio").performClick()
        awaitDescribed("Delete event")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Delete event").performTouchInput {
            down(center)
            advanceEventTime(300)
            cancel()
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithContentDescription("Close event preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete event").assertIsDisplayed()
        compose.onNodeWithContentDescription("Confirm Delete event").assertDoesNotExist()
    }
}

class ClassicPillGestureCancellationTest : CalinoUiTest(menuPill = false) {
    @Test fun cancelledSearchDragReturnsToTheAddPill() {
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up to search").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f), delayMillis = 150)
            cancel()
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up to search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search Calino").assertDoesNotExist()
    }
}
