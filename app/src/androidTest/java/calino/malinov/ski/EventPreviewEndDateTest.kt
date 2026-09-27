package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.surfaces.EventDetailSurface
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventPreviewEndDateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun timedEndDateEditsOnlyTheEndAndSavesCorrectDuration() {
        val start = LocalDateTime.of(2026, 10, 8, 15, 15)
        val originalEnd = LocalDateTime.of(2026, 10, 18, 5, 15)
        val event = CalEvent(
            id = "flight", title = "Flight", color = 0xff5b7fb5,
            start = start, durationMinutes = Duration.between(start, originalEnd).toMinutes().toInt(),
            calendarId = "work",
        )
        val saved = AtomicReference<NewEvent?>()
        compose.setContent {
            CalinoTheme {
                EventDetailSurface(
                    event = event,
                    occurrenceDate = LocalDate.of(2026, 10, 17),
                    onInlineSave = { _, input, _ -> saved.set(input); true },
                )
            }
        }

        compose.onNodeWithContentDescription("End date, Sun, 18 Oct").performClick()
        compose.onNodeWithContentDescription("Sunday, October 18, 2026").assertIsSelected()
        compose.onNodeWithContentDescription("Saturday, October 17, 2026").performTouchInput { click() }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Start date, Thu, 8 Oct").assertIsDisplayed()
        compose.onNodeWithContentDescription("End date, Sat, 17 Oct").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save event changes").performClick()
        compose.waitUntil(5_000) { saved.get() != null }
        assertEquals(LocalDate.of(2026, 10, 8), saved.get()?.date)
        assertEquals(LocalTime.of(15, 15), saved.get()?.startTime)
        assertEquals(Duration.between(start, LocalDateTime.of(2026, 10, 17, 5, 15)).toMinutes().toInt(), saved.get()?.durationMinutes)
    }

    @Test fun allDayEndDateSavesInclusiveLastDay() {
        val event = CalEvent(
            id = "vacation", title = "Vacation", color = 0xff5b7fb5,
            start = null, durationMinutes = null, allDay = true,
            date = LocalDate.of(2026, 5, 24), endDate = LocalDate.of(2026, 5, 26),
            calendarId = "personal",
        )
        val saved = AtomicReference<NewEvent?>()
        compose.setContent {
            CalinoTheme {
                EventDetailSurface(
                    event = event,
                    occurrenceDate = LocalDate.of(2026, 5, 25),
                    onInlineSave = { _, input, _ -> saved.set(input); true },
                )
            }
        }

        compose.onNodeWithContentDescription("End date, Tuesday 26 May").performClick()
        compose.onNodeWithContentDescription("Tuesday, May 26, 2026").assertIsSelected()
        compose.onNodeWithContentDescription("Monday, May 25, 2026").performTouchInput { click() }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Start date, Sunday 24 May").assertIsDisplayed()
        compose.onNodeWithContentDescription("End date, Monday 25 May").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save event changes").performClick()
        compose.waitUntil(5_000) { saved.get() != null }
        assertEquals(LocalDate.of(2026, 5, 24), saved.get()?.date)
        assertEquals(LocalDate.of(2026, 5, 25), saved.get()?.endDate)
    }
}
