package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.util.CalinoDefaultView
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The offer belongs to the calendar. A person whose saved view is Range is not
 * asked on that page -- the pill there is naming Range's own thing -- and is
 * asked as soon as they open the calendar, which is where the gestures live.
 */
@RunWith(AndroidJUnit4::class)
class TourOfferFromRangeTest : CalinoUiTest(CalinoDefaultView.Range) {
    @Test fun aRangeDefaultIsAskedWhenItOpensTheCalendar() {
        // Nothing on the Range page itself.
        Thread.sleep(OfferDelayMillis + 1_500L)
        compose.waitForIdle()
        assertFalse(
            "the tour was offered over Range's own pill",
            compose.exists(hasContentDescription(OfferMessage, substring = true)),
        )

        compose.openRoute("Month")
        compose.waitUntil(8_000) { compose.hasDescribedNode(OfferAction) }
        compose.onNodeWithContentDescription(OfferAction).assertIsDisplayed()
    }

    private companion object {
        const val OfferMessage = "Take a 1-minute tour?"
        const val OfferAction = "Start: Take a 1-minute tour?"
        const val OfferDelayMillis = 1_800L
    }
}
