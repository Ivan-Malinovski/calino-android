package calino.malinov.ski.poc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import calino.malinov.ski.poc.ui.components.SwipeDownDismissTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Modal/editor downward dismissal and spring-back.
 *
 * `SwipeDownDismiss` owns the pointer stream and carries no semantics of its
 * own -- the scrim behind it is what a screen reader finds. The gesture is
 * therefore driven through its test tag; the assertion is still behavioural,
 * on whether the editor is still on screen afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ModalDismissalTest : CalinoUiTest() {

    @Test fun eventLocationExposesATouchSizedMapAction() {
        compose.onNodeWithContentDescription("Design review, 10:00 AM, Studio").performClick()
        awaitDescribed("Open event location in maps")

        val actions = compose.onAllNodesWithContentDescription("Open event location in maps")
        val visibleAction = actions.fetchSemanticsNodes().indexOfFirst {
            it.boundsInRoot.left >= 0f && it.boundsInRoot.top >= 0f
        }
        assertTrue("no visible event location action", visibleAction >= 0)
        actions[visibleAction]
            .assertIsDisplayed()
            .assertHeightIsAtLeast(44.dp)
            .assertWidthIsAtLeast(44.dp)
    }

    @Test fun aFullSwipeDownDismissesTheEditor() {
        openQuickAdd()

        compose.onNodeWithTag(SwipeDownDismissTag).performTouchInput {
            swipeDown(startY = top + (height * .1f), endY = bottom)
        }
        compose.waitForIdle()

        assertFalse("editor survived a full swipe down", compose.hasDescribedNode("Title, task"))
    }

    /**
     * A drag that never passes the 112dp threshold springs back. The editor
     * must still be there -- and still be the same editor, with its draft
     * intact rather than a fresh one.
     */
    @Test fun aShortSwipeSpringsBackAndKeepsTheEditor() {
        openQuickAdd()

        compose.onNodeWithTag(SwipeDownDismissTag).performTouchInput {
            swipeDown(startY = top + (height * .1f), endY = top + (height * .16f))
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Title, task").assertIsDisplayed()
    }

    @Test fun theScrimDismissesTheEditor() {
        openQuickAdd()

        // Near the top: the scrim spans the window and its centre is behind the
        // card, where the card's own surface takes the touch.
        compose.onNodeWithContentDescription("Dismiss detail card").performTouchInput {
            click(percentOffset(.5f, .04f))
        }
        compose.waitForIdle()

        assertFalse("editor survived a scrim tap", compose.hasDescribedNode("Title, task"))
    }

    @Test fun theCloseButtonDismissesTheEditor() {
        openQuickAdd()

        compose.onNodeWithContentDescription("Close editor").performClick()
        compose.waitForIdle()

        assertFalse("editor survived the close button", compose.hasDescribedNode("Title, task"))
    }

    /** Dismissing returns to the surface underneath, not to a blank shell. */
    @Test fun dismissalRestoresTheSurfaceUnderneath() {
        openQuickAdd()
        compose.onNodeWithTag(SwipeDownDismissTag).performTouchInput {
            swipeDown(startY = top + (height * .1f), endY = bottom)
        }
        compose.waitForIdle()

        assertTrue(compose.hasDescribedNode("Open task: Buy flowers"))
    }

    @Test fun dismissingAnOpenedEventEditorClosesThePreviewToo() {
        compose.onNodeWithContentDescription("Design review, 10:00 AM, Studio").performClick()
        awaitDescribed("Open event")
        compose.onNodeWithContentDescription("Open event").performClick()
        awaitDescribed("Title, event")

        compose.onNodeWithContentDescription("Close editor").performClick()
        awaitNoDescribed("Title, event")

        assertFalse("event preview returned after editor dismissal", compose.hasDescribedNode("Close event preview"))
        assertTrue("calendar was not restored", compose.hasDescribedNode("Design review, 10:00 AM, Studio"))
    }

    private fun openQuickAdd() {
        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("New task. Swipe up to search").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Title, task").assertIsDisplayed()
    }
}
