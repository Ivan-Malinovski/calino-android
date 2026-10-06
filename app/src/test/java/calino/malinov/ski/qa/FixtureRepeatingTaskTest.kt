package calino.malinov.ski.qa

import calino.malinov.ski.data.model.NewTask
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

    private fun FixtureRepository.makeRepeat(rule: String): List<calino.malinov.ski.data.model.CalTask> {
        val task = tasks().first { it.id == "task-inbox" }
        runBlocking {
            updateTask(
                task.id,
                NewTask(title = task.title, due = task.due, color = task.color, recurrence = rule, recurrenceChanged = true),
                done = false,
            )
        }
        return tasks().filter { it.id.startsWith("task-inbox") }
    }

    @Test fun aOneOffSetToRepeatDailyAppearsOnEveryDay() {
        val repository = FixtureRepository()
        val rows = repository.makeRepeat("FREQ=DAILY")

        val days = rows.mapNotNull { it.due }.sorted()
        assertEquals(FixtureRepository.FixtureDate, days.first())
        assertTrue("a year of days, got ${days.size}", days.size >= 365)
        assertEquals(days.size, days.distinct().size)
        assertTrue(days.zipWithNext().all { (a, b) -> b == a.plusDays(1) })
        assertTrue(rows.all { it.uid == "task-inbox" && it.recurrenceDate == it.due })
    }

    @Test fun aCountedRepeatStopsAfterThatManyRows() {
        val rows = FixtureRepository().makeRepeat("FREQ=DAILY;COUNT=5")
        assertEquals(
            (0L..4L).map { FixtureRepository.FixtureDate.plusDays(it) },
            rows.mapNotNull { it.due }.sorted(),
        )
    }

    @Test fun anEndDateStopsTheRowsOnThatDay() {
        val rows = FixtureRepository().makeRepeat("FREQ=DAILY;UNTIL=20260524T235959Z")
        assertEquals(LocalDate.of(2026, 5, 24), rows.mapNotNull { it.due }.max())
        assertEquals(7, rows.size)
    }

    @Test fun editingTheRuleAgainReplacesTheRowsRatherThanAddingToThem() {
        val repository = FixtureRepository()
        repository.makeRepeat("FREQ=DAILY;COUNT=5")
        val rows = repository.tasks().filter { it.id.startsWith("task-inbox@") }.let {
            val first = it.first()
            runBlocking {
                repository.updateTask(
                    first.id,
                    NewTask(title = first.title, due = first.due, recurrence = "FREQ=DAILY;COUNT=3", recurrenceChanged = true, recurrenceDate = first.recurrenceDate),
                    done = false,
                )
            }
            repository.tasks().filter { row -> row.id.startsWith("task-inbox") }
        }
        assertEquals(3, rows.size)
    }

    @Test fun aNewRepeatingTaskIsExpandedToo() {
        val repository = FixtureRepository()
        runBlocking {
            repository.addTask(NewTask(title = "Stretch", due = FixtureRepository.FixtureDate, recurrence = "FREQ=DAILY;COUNT=4"))
        }
        assertEquals(4, repository.tasks().count { it.title == "Stretch" })
    }
}
