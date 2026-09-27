package calino.malinov.ski

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.repository.WriteResult
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurringFixtureEditTest : CalinoUiTest() {
    @Test fun editingThisOccurrenceKeepsTheFollowingWeekAtItsOriginalTime() = runBlocking {
        val repository = CalinoContainer.get(ApplicationProvider.getApplicationContext()).fixtureRepository
        val series = repository.events().first { it.id == "evt-design" }
        val monday = LocalDate.of(2026, 5, 25)
        val result = repository.updateEvent(series.id, NewEvent(
            title = series.title,
            date = monday,
            startTime = LocalTime.of(14, 0),
            durationMinutes = series.durationMinutes,
            recurrence = series.recurrence,
            calendarId = series.calendarId,
            recurrenceDate = monday,
            recurrenceScope = RecurrenceEditScope.This,
        ))
        assertTrue(result is WriteResult.Applied)
        val events = repository.events()
        val unchanged = events.first { it.id == series.id }
        assertFalse(unchanged.occursOn(monday))
        assertTrue(unchanged.occursOn(monday.plusWeeks(1)))
        assertEquals(LocalTime.of(10, 0), unchanged.start?.toLocalTime())
        assertEquals(LocalTime.of(14, 0), events.single { it.id != series.id && it.title == series.title }.start?.toLocalTime())
    }
}
