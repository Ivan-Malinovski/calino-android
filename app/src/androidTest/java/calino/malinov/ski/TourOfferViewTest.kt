package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.util.CalinoDefaultView
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The offer is not a calendar-only message. A person whose saved view is Range
 * or Agenda is asked too, and starting the tour from there takes them to the
 * calendar the gestures live on.
 */
@RunWith(AndroidJUnit4::class)
class TourOfferFromRangeTest : CalinoUiTest(CalinoDefaultView.Range) {
    @Test fun theOfferArrivesOnARangeDefaultAndStartShowsTheCalendar() {
        compose.waitUntil(8_000) { compose.hasDescribedNode(OfferAction) }
        compose.onNodeWithContentDescription(OfferAction).performClick()

        // Starting it lands on the calendar, with the first card up.
        compose.waitUntil(8_000) { compose.hasDescribedNode("Agenda for Monday, May 18") }
        compose.waitUntil(8_000) {
            compose.exists(hasContentDescription("Tour step 1 of", substring = true))
        }
        compose.onNodeWithContentDescription("Agenda for Monday, May 18").assertIsDisplayed()
    }

    private companion object {
        const val OfferAction = "Start: Take a 1-minute tour?"
    }
}

@RunWith(AndroidJUnit4::class)
class TourOfferFromAgendaTest : CalinoUiTest(CalinoDefaultView.Agenda) {
    @Test fun theOfferArrivesOnAnAgendaDefault() {
        compose.waitUntil(8_000) { compose.hasDescribedNode(OfferAction) }
        compose.onNodeWithContentDescription(OfferAction).assertIsDisplayed()
    }

    private companion object {
        const val OfferAction = "Start: Take a 1-minute tour?"
    }
}
