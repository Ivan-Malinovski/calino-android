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
import androidx.test.espresso.Espresso
import calino.malinov.ski.ui.year.YearPagerTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The clock is frozen at 2026-05-18, so the tiles and the pill can be named exactly. */
class YearViewTest : CalinoUiTest() {
    // The pager pre-composes the neighbouring years, so a tile label cannot say
    // which year is showing; the animated title can (both years exist only
    // while it slides).
    private fun awaitYear(year: Int) = compose.waitUntil(YearAwaitMillis) { compose.hasTextNode("$year") }

    private fun awaitNotYear(year: Int) = compose.waitUntil(YearAwaitMillis) { !compose.hasTextNode("$year") }

    private fun openYear() {
        compose.openRoute("Year")
        awaitDescribed("Previous year")
    }

    @Test fun showsTwelveMonthTilesForTheCurrentYear() {
        openYear()
        compose.onNodeWithText("2026").assertIsDisplayed()
        val tiles = compose.onAllNodesWithContentDescription("2026, ", substring = true)
        assertEquals(12, tiles.fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("May 2026, ", substring = true).assertIsDisplayed()
        // The fixtures put events in May, so its heat is not empty.
        assertFalse(compose.hasDescribedNode("May 2026, 0 events"))
    }

    @Test fun theStepButtonsMoveByYear() {
        openYear()
        compose.onNodeWithContentDescription("Next year").performClick()
        awaitYear(2027)
        awaitNotYear(2026)
        compose.onNodeWithContentDescription("Previous year").performClick()
        awaitYear(2026)
        awaitNotYear(2027)
    }

    @Test fun swipingPagesToTheNextYearAndTheAddPillFollowsIt() {
        openYear()
        awaitDescribed("Add on Mon, 18 May. Swipe up for views")
        compose.onNodeWithTag(YearPagerTag).performTouchInput { swipeLeft() }
        awaitYear(2027)
        // 18 May 2027 is a Tuesday: the pill names the same day in the year now showing.
        awaitDescribed("Add on Tue, 18 May. Swipe up for views")
    }

    @Test fun tappingATileOpensThatMonthAndBackReturnsToTheYear() {
        openYear()
        compose.onNodeWithContentDescription("May 2026, ", substring = true).performClick()
        awaitDescribed("Change calendar zoom, level 3 of 3")
        assertFalse(compose.exists(hasContentDescription("Previous year")))

        Espresso.pressBack()
        awaitDescribed("Previous year")
        assertTrue(compose.hasDescribedNode("Previous year"))
    }

    @Test fun theAddPillNamesTheSelectedDayAndOpensQuickAdd() {
        openYear()
        compose.onNodeWithContentDescription("Add on Mon, 18 May. Swipe up for views", substring = true)
            .performClick()
        awaitDescribed("Title, event")
    }

    @Test fun survivesRecreation() {
        openYear()
        compose.onNodeWithContentDescription("Next year").performClick()
        awaitYear(2027)
        awaitNotYear(2026)

        compose.activityRule.scenario.recreate()
        awaitDescribed("Previous year")
        awaitYear(2027)
        awaitNotYear(2026)
    }

    private companion object {
        const val YearAwaitMillis = 5_000L
    }
}
