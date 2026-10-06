package calino.malinov.ski.qa

import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.repository.FixtureRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sample data carries repeating tasks, and deleting one honours its scope. */
class FixtureRepeatingTaskTest {

    private fun FixtureRepository.plants() = tasks().filter { it.id.startsWith("task-plants@") }

    private fun FixtureRepository.delete(id: String, scope: RecurrenceEditScope) =
        runBlocking { deleteTask(id, scope) }

    @Test fun sampleHasRepeatingTasksAsOneRowPerOccurrence() {
        val repository = FixtureRepository()
        assertEquals(4, repository.plants().size)
        assertTrue(repository.plants().all { it.recurrence == "FREQ=WEEKLY;BYDAY=MO,TH" && it.recurrenceDate == it.due })
        assertEquals(2, repository.tasks().count { it.id.startsWith("task-review@") })
    }

    @Test fun thisRemovesOnlyThatOccurrence() {
        val repository = FixtureRepository()
        repository.delete("task-plants@2026-05-25", RecurrenceEditScope.This)
        assertEquals(3, repository.plants().size)
        assertTrue(repository.plants().none { it.recurrenceDate == LocalDate.of(2026, 5, 25) })
    }

    @Test fun futureKeepsEarlierOccurrences() {
        val repository = FixtureRepository()
        repository.delete("task-plants@2026-05-25", RecurrenceEditScope.Future)
        assertEquals(
            listOf(LocalDate.of(2026, 5, 21)),
            repository.plants().map { it.recurrenceDate },
        )
    }

    @Test fun allRemovesTheSeriesAndNothingElse() {
        val repository = FixtureRepository()
        val before = repository.tasks().size
        repository.delete("task-plants@2026-05-25", RecurrenceEditScope.All)
        assertEquals(0, repository.plants().size)
        assertEquals(before - 4, repository.tasks().size)
    }
}
