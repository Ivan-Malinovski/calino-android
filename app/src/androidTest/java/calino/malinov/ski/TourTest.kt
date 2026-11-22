package calino.malinov.ski

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first-run tour as a person meets it: the offer in the pill lane, the
 * cards, a step that only completes from its own gesture, and an answer that
 * stops the asking for good.
 *
 * `CalinoResetRule` puts the app back to a first-launch state before each test,
 * so every one of these starts with the tour never offered. The offer lands
 * 1.8 s after the calendar is quiet, which is a real-time delay and therefore
 * waited for rather than assumed.
 */
@RunWith(AndroidJUnit4::class)
class TourTest : CalinoUiTest() {

    @Test fun theOfferArrivesOnACalmCalendarAndStartBeginsTheTour() {
        awaitOffer()

        compose.onNodeWithContentDescription(OfferAction).performClick()
        awaitTourStep(1)
        // The first card teaches the zoom swipe, so it sits low, clear of the
        // week strip that the gesture starts on.
        compose.onNodeWithContentDescription(TourStep1, substring = true).assertIsDisplayed()
    }

    @Test fun theZoomCardCompletesOnlyWhenTheCalendarActuallyZooms() {
        awaitOffer()
        compose.onNodeWithContentDescription(OfferAction).performClick()
        awaitTourStep(1)

        // The card asks for the gesture first; it answers only once the
        // calendar has really zoomed.
        compose.waitUntil(WaitMillis) { compose.hasTextNode(ZoomLine) }
        assertFalse("the card claimed the gesture before it happened", compose.hasTextNode(ZoomDoneLine))

        // A deliberate pull on the zoom handle, the travel the edge-gesture
        // suite uses to open the month.
        compose.onNodeWithContentDescription(CalinoTestActions.zoomHandleLabel(0)).performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 900f), 900)
        }
        // The card answers in its line, not its description: the description
        // names the step, the line is what the step is saying right now.
        compose.waitUntil(WaitMillis) { compose.hasTextNode(ZoomDoneLine) }
    }

    @Test fun skippingStopsTheTourAndTheOfferDoesNotComeBack() {
        awaitOffer()
        compose.onNodeWithContentDescription(OfferAction).performClick()
        awaitTourStep(1)

        compose.onNodeWithText(Skip).performClick()
        compose.waitForIdle()
        compose.waitUntil(WaitMillis) { !compose.hasDescriptionContaining(TourCardPrefix) }

        // The offer is written as answered, not only dismissed: waiting past
        // its 1.8 s delay must not bring it back.
        Thread.sleep(OfferDelayMillis + 1_200L)
        compose.waitForIdle()
        assertFalse(compose.hasDescriptionContaining(OfferMessage))
    }

    /**
     * Walking every card with Next, including the two that open a real editor
     * and the two that touch the pill, has to leave the app where it started:
     * no card, no editor, and no offer again. Next is always available, so this
     * does not depend on performing each gesture.
     */
    @Test fun walkingToTheEndCompletesTheTourAndLeavesNothingOpen() {
        awaitOffer()
        compose.onNodeWithContentDescription(OfferAction).performClick()
        awaitTourStep(1)

        // Five cards on a menu pill with Journal and Contacts off, but the walk
        // follows whatever this install actually offers.
        repeat(8) {
            if (compose.hasTextNode(Done)) return@repeat
            compose.onNodeWithText(Next).performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText(Done).performClick()
        compose.waitForIdle()

        compose.waitUntil(WaitMillis) { !compose.hasDescriptionContaining(TourCardPrefix) }
        // The editor a step opened is closed by the step leaving, so the
        // calendar is the surface on screen again.
        compose.waitUntil(WaitMillis) { compose.hasDescribedNode("Views, current: Month") }
        assertFalse(compose.hasDescriptionContaining("Cancel editor"))

        Thread.sleep(OfferDelayMillis + 1_500L)
        compose.waitForIdle()
        assertFalse("the tour was offered again after finishing", compose.hasDescriptionContaining(OfferMessage))
    }

    /**
     * An unanswered question lets go of the lane by itself: it is a question,
     * not the app's primary action, and it cannot stand over the add pill
     * forever.
     */
    @Test fun anIgnoredOfferLetsGoOfTheLaneOnItsOwn() {
        awaitOffer()

        compose.waitUntil(OfferTimeoutMillis + LingerMillis) {
            !compose.hasDescriptionContaining(OfferMessage)
        }
        // The add pill is back, and the question is not asked again afterwards.
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views").assertIsDisplayed()
        Thread.sleep(OfferDelayMillis + 1_200L)
        compose.waitForIdle()
        assertFalse(compose.hasDescriptionContaining(OfferMessage))
    }

    /**
     * The question does not travel with the person. Leaving the calendar before
     * answering takes the offer out of the lane, so it cannot hide the label of
     * the page they moved to.
     */
    @Test fun leavingTheCalendarWithdrawsTheUnansweredOffer() {
        awaitOffer()

        compose.openRoute("Tasks")
        compose.waitForIdle()
        compose.waitUntil(WaitMillis) { !compose.hasDescriptionContaining(OfferMessage) }
        // Tasks' own pill is back, and nothing asked the question again there.
        compose.onNodeWithContentDescription("New task. Swipe up for views").assertIsDisplayed()
    }

    private fun awaitOffer() {
        compose.waitUntil(OfferTimeoutMillis) { compose.hasDescribedNode(OfferAction) }
    }

    private fun awaitTourStep(index: Int) {
        compose.waitUntil(WaitMillis) { compose.hasDescriptionContaining("Tour step $index of") }
    }

    /** Whether any node's content description contains [text]. */
    private fun SemanticsNodeInteractionsProvider.hasDescriptionContaining(text: String): Boolean =
        exists(hasContentDescription(text, substring = true))

    private companion object {
        const val OfferMessage = "Take a 1-minute tour?"

        /** The offer's action reads "<action>: <message>", naming what it does. */
        const val OfferAction = "Start: $OfferMessage"
        const val TourStep1 = "Tour step 1 of"
        const val TourCardPrefix = "Tour step "
        const val ZoomLine = "Swipe the calendar down to see more of the month, up to see more of the day."
        const val ZoomDoneLine = "That's it. Swipe either way whenever you want more or less."
        const val Skip = "Skip"
        const val Next = "Next"
        const val Done = "Done"
        const val OfferDelayMillis = 1_800L
        /** `TourOfferLingerMillis` in MainActivity: how long the offer may hold the lane. */
        const val LingerMillis = 12_000L
        const val OfferTimeoutMillis = 8_000L
        const val WaitMillis = 9_000L
    }
}
