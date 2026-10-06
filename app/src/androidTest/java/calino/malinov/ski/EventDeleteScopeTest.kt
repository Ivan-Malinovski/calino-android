package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.surfaces.EventDetailSurface
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deleting a repeating event asks which occurrences to remove, with the same buttons as a task. */
@RunWith(AndroidJUnit4::class)
class EventDeleteScopeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val event = CalEvent(
        id = "standup", title = "Standup", color = 0xff5b7fb5,
        start = LocalDateTime.of(2026, 10, 8, 9, 0), durationMinutes = 30,
        calendarId = "work", recurrence = "FREQ=WEEKLY;BYDAY=TH",
    )

    @Test fun deleteOffersEachScopeAsItsOwnButton() {
        compose.setContent {
            CalinoTheme { EventDetailSurface(event = event, occurrenceDate = LocalDate.of(2026, 10, 15)) }
        }
        compose.onNodeWithTag("event-delete-this").assertDoesNotExist()

        compose.onNodeWithContentDescription("Delete event").performClick()
        compose.waitUntil(5_000L) { compose.onAllNodesWithTag("event-delete-this").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("event-delete-this").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("event-delete-future").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("event-delete-all").assertIsDisplayed().assertHasClickAction()
    }
}
