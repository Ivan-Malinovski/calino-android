package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The root pill's view button, its menu and its dock.
 *
 * Arrival is asserted through the view button itself: it names the view the
 * app is on, so "Views, current: Journal" is the pill's own answer to where
 * the person landed.
 */
@RunWith(AndroidJUnit4::class)
class PillMenuTest : CalinoUiTest() {

    private fun viewButton(route: String) = compose.onNodeWithContentDescription("Views, current: $route")

    /** A node's centre in root pixels, which is what touch input on the root takes. */
    private fun DpRect.centrePx(): Offset = with(compose.density) {
        Offset(((left + right) / 2).toPx(), ((top + bottom) / 2).toPx())
    }

    private fun dpPx(value: Int): Float = with(compose.density) { value.dp.toPx() }

    @Test fun tappingTheViewButtonOpensTheMenuAndAPickNavigates() {
        viewButton("Month").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Contacts").performClick()
        compose.waitForIdle()

        viewButton("Contacts").assertIsDisplayed()
        assertFalse(compose.hasTextNode("Search"))
    }

    @Test fun scrubbingUpAndReleasingOnAViewNavigatesThere() {
        val centre = viewButton("Month").getBoundsInRoot().centrePx()
        compose.onRoot().performTouchInput {
            down(centre)
            // Past the 14dp scrub threshold, in steps like a real finger.
            repeat(4) { moveBy(Offset(0f, -dpPx(8))) }
        }
        compose.waitForIdle()
        val target = compose.onNodeWithText("Journal").getBoundsInRoot().centrePx()
        compose.onRoot().performTouchInput {
            moveTo(target)
            up()
        }
        compose.waitForIdle()

        viewButton("Journal").assertIsDisplayed()
    }

    @Test fun scrubbingThenReleasingOverNothingLeavesTheMenuOpen() {
        val centre = viewButton("Month").getBoundsInRoot().centrePx()
        compose.onRoot().performTouchInput {
            down(centre)
            repeat(4) { moveBy(Offset(0f, -dpPx(8))) }
            // Up and out past the menu's edge before letting go.
            moveBy(Offset(-dpPx(160), -dpPx(500)))
            up()
        }
        compose.waitForIdle()

        compose.onNodeWithText("Search").assertIsDisplayed()
    }

    @Test fun swipingDownOnTheMenuDismissesIt() {
        viewButton("Month").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Agenda").performTouchInput { swipeDown(startY = centerY, endY = centerY + 400f) }
        compose.waitForIdle()

        assertFalse(compose.hasTextNode("Search"))
        viewButton("Month").assertIsDisplayed()
    }

    @Test fun backClosesTheMenuWithoutLeavingTheView() {
        viewButton("Month").performClick()
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()

        assertFalse(compose.hasTextNode("Search"))
        viewButton("Month").assertIsDisplayed()
    }

    @Test fun holdingTheViewButtonSavesTheDockUntilItIsHeldAgain() {
        viewButton("Month").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Tasks").performClick()
        compose.waitForIdle()

        // The dock is a saved resting shape: it stays up on the new view.
        compose.onNodeWithContentDescription("Tasks").assertIsSelected()

        // Holding it is the way back to the add pill.
        compose.onNodeWithContentDescription("Tasks").performTouchInput { longClick() }
        compose.waitForIdle()
        viewButton("Tasks").assertIsDisplayed()
    }

    @Test fun theDockAddOpensTheChosenEditor() {
        viewButton("Month").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("New task").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Title, task").assertIsDisplayed()
    }

    @Test fun theLabelKeepsItsSideSwipe() {
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views", substring = true)
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()

        viewButton("Range").assertIsDisplayed()
    }

    @Test fun swipingUpOnTheLabelOpensTheMenu() {
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views", substring = true)
            .performTouchInput { down(center); moveBy(Offset(0f, -dpPx(40))) }
        compose.waitForIdle()

        // The menu, not the search screen the label used to open. The finger
        // is still down: releasing would pick the row under it.
        assertTrue(compose.hasTextNode("Agenda"))
        assertFalse(compose.hasDescribedNode("Search Calino"))
        compose.onRoot().performTouchInput { up() }
    }
}

/** With the menu pill turned off in Settings the root pill is the swipe-only add pill. */
@RunWith(AndroidJUnit4::class)
class ClassicPillTest : CalinoUiTest(menuPill = false) {

    @Test fun theClassicPillHasNoViewButton() {
        compose.waitForIdle()
        assertFalse(compose.hasDescribedNode("Views, current: Month"))
        assertTrue(compose.hasDescribedNode("Add on Mon, 18 May. Swipe up to search"))
    }
}
