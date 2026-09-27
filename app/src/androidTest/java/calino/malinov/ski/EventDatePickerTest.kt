package calino.malinov.ski

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.toPixelMap
import calino.malinov.ski.util.CalinoThemeChoice
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertNotEquals

@RunWith(AndroidJUnit4::class)
class EventDatePickerTest : CalinoUiTest(theme = CalinoThemeChoice.Dark) {
    @Test fun existingEventDateCanBeChangedInPicker() {
        compose.onNodeWithContentDescription("Design review, 10:00 AM, Studio").performClick()
        awaitDescribed("Open event")
        compose.onNodeWithContentDescription("Open event").performClick()
        awaitDescribed("Start date, Mon, 18 May")
        compose.onNodeWithContentDescription("Start date, Mon, 18 May").performClick()
        compose.onNodeWithContentDescription("Monday, May 18, 2026").assertIsSelected()
        assertSelectedDayIsPainted("Monday, May 18, 2026", "Tuesday, May 19, 2026")
        compose.onNodeWithContentDescription("Tuesday, May 19, 2026").performTouchInput { click() }
        compose.onNodeWithContentDescription("Tuesday, May 19, 2026").assertIsSelected()
        assertSelectedDayIsPainted("Tuesday, May 19, 2026", "Monday, May 18, 2026")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Start date, Mon, 18 May").assertIsDisplayed()
        compose.onNodeWithContentDescription("Start date, Mon, 18 May").performClick()
        compose.onNodeWithContentDescription("Monday, May 18, 2026").assertIsSelected()
        compose.onNodeWithContentDescription("Tuesday, May 19, 2026").performTouchInput { click() }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Start date, Tue, 19 May").assertIsDisplayed()
    }

    private fun assertSelectedDayIsPainted(selected: String, unselected: String) {
        // A dark-theme regression made the selected circle exactly the panel
        // color. Semantics still said "selected", so check a paint-only point
        // above each numeral as well as the semantic state.
        fun topOfCell(description: String) = compose.onNodeWithContentDescription(description)
            .captureToImage().let { image ->
                image.toPixelMap()[image.width / 2, image.height / 8]
            }
        assertNotEquals("selected day has no visible fill", topOfCell(unselected), topOfCell(selected))
    }

    @Test fun previewDatePickerAcceptsTouchSelection() {
        compose.onNodeWithContentDescription("Design review, 10:00 AM, Studio").performClick()
        awaitDescribed("Start date, Mon, 18 May")
        compose.onAllNodesWithContentDescription("Start date, Mon, 18 May")[0].performClick()
        compose.onNodeWithContentDescription("Monday, May 18, 2026").assertIsSelected()
        compose.onNodeWithContentDescription("Tuesday, May 19, 2026").performTouchInput { click() }
        compose.onNodeWithContentDescription("Tuesday, May 19, 2026").assertIsSelected()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Start date, Tue, 19 May").assertIsDisplayed()
    }

    @Test fun editorAndPickerOpenOnDateOfEventAwayFromSelectedDay() {
        zoomTo(2)
        compose.onNodeWithContentDescription("Flight to Berlin").performClick()
        awaitDescribed("Start date, Sunday 24 May")
        awaitDescribed("Open event")
        compose.onNodeWithContentDescription("Open event").performClick()
        awaitDescribed("Start date, Sun, 24 May")
        compose.onNodeWithContentDescription("Start date, Sun, 24 May").performClick()
        compose.onNodeWithContentDescription("Sunday, May 24, 2026").assertIsSelected()
    }

}
