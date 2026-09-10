package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.repository.FixtureRepository
import calino.malinov.ski.poc.data.repository.WriteResult
import calino.malinov.ski.poc.data.model.Availability
import calino.malinov.ski.poc.data.model.NewEvent
import calino.malinov.ski.poc.data.model.Reminder
import calino.malinov.ski.poc.data.model.NewJournal
import calino.malinov.ski.poc.data.model.NewTask
import calino.malinov.ski.poc.data.model.occursOn
import java.time.LocalTime
import calino.malinov.ski.poc.util.formatCalinoDate
import calino.malinov.ski.poc.util.formatCalinoTime
import calino.malinov.ski.poc.util.formatRecurrenceSummary
import calino.malinov.ski.poc.util.nextOccurrences
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PocFixturesTest {
    private val fixtureDate = LocalDate.of(2026, 5, 18)

    @Test
    fun repository_carriesTheEditorsNewFields() = runBlocking {
        val repository = FixtureRepository()

        val event = repository.addEvent(
            NewEvent(
                title = "Planning",
                date = fixtureDate,
                startTime = LocalTime.of(9, 0),
                durationMinutes = 45,
                availability = Availability.Free,
                categories = listOf("Work"),
                reminders = listOf(Reminder(15)),
                travelTimeMinutes = 30,
                relatedTo = listOf("task-inbox"),
            ),
        ).applied()
        assertEquals(Availability.Free, event.availability)
        assertEquals(listOf("Work"), event.categories)
        assertEquals(listOf(Reminder(15)), event.reminders)
        assertEquals(30, event.travelTimeMinutes)
        assertEquals(listOf("task-inbox"), event.relatedTo)

        val task = repository.addTask(
            NewTask(
                title = "Send itinerary",
                due = fixtureDate,
                dueTime = LocalTime.of(17, 0),
                notes = "Attach the tickets",
                reminder = Reminder(60),
            ),
        ).applied()
        assertEquals(LocalTime.of(17, 0), task.dueTime)
        assertEquals("Attach the tickets", task.notes)
        assertEquals(Reminder(60), task.reminder)

        val reopened = repository.updateTask(task.id, NewTask(title = "Send itinerary", due = fixtureDate), done = true).applied()
        assertTrue(reopened.done)
        assertNull(reopened.dueTime)
    }

    @Test
    fun snapshot_exposesTheCalendarAndCategoryFixtures() {
        val snapshot = FixtureRepository().snapshot()

        assertEquals(listOf("personal", "work", "travel"), snapshot.calendars.map { it.id })
        assertTrue(snapshot.categories.containsAll(listOf("Work", "Personal", "Travel")))
    }

    @Test
    fun snapshot_exposesTheContactFixtureContract() {
        val snapshot = FixtureRepository().snapshot()

        assertEquals(listOf("fixture-contacts"), snapshot.addressBooks.map { it.id })
        assertTrue(snapshot.contacts.any { it.id == "contact-ada" && it.birthday == LocalDate.of(1988, 5, 24) })
        assertTrue(snapshot.contacts.any { it.isGroup && it.memberUids.isNotEmpty() })
        assertTrue(snapshot.contacts.any { it.displayName.isBlank() && it.photo == null })
    }

    @Test
    fun fixtureRepository_isFrozenToMay2026() {
        val repository = FixtureRepository()

        assertEquals(fixtureDate, repository.events().first { it.id == "evt-design" }.start?.toLocalDate())
        assertEquals(fixtureDate, repository.tasks().first { it.id == "task-inbox" }.due)
        assertEquals(fixtureDate, repository.journals().first { it.id == "journal-1" }.date)
        assertEquals(listOf("evt-design", "evt-lunch", "evt-flight"), repository.events().take(3).map { it.id })
        assertTrue(repository.events().size > 20)
        assertTrue(repository.events().any { it.start?.toLocalDate()?.monthValue == 4 })
        assertTrue(repository.events().any { it.start?.toLocalDate()?.monthValue == 6 })
        assertTrue(repository.journals().size > 1)
        assertEquals(LocalDate.of(2026, 5, 24), repository.events().first { it.id == "evt-flight" }.date)
        assertTrue(repository.events().first { it.id == "evt-flight" }.occursOn(LocalDate.of(2026, 5, 24)))
        assertTrue(!repository.events().first { it.id == "evt-flight" }.occursOn(fixtureDate))
        assertEquals(null, repository.events().first { it.id == "evt-lunch" }.recurrence)
    }

    @Test
    fun recurringEvents_areVisibleOnWeeklyInstances_untilTheirEndDate() {
        val event = FixtureRepository().events().first { it.id == "evt-design" }

        assertTrue(event.occursOn(LocalDate.of(2026, 6, 1)))
        assertTrue(!event.occursOn(LocalDate.of(2026, 6, 2)))
        assertTrue(!event.occursOn(LocalDate.of(2026, 7, 6)))
    }

    @Test
    fun recurringFixture_doesNotDuplicateMaterializedOccurrences() {
        val events = FixtureRepository().events()

        fun matching(title: String, day: LocalDate) = events.filter { it.title == title && it.occursOn(day) }

        assertEquals(listOf("evt-gym-05"), matching("Gym Session", LocalDate.of(2026, 5, 12)).map { it.id })
        assertEquals(listOf("evt-gym-05-14"), matching("Gym Session", LocalDate.of(2026, 5, 14)).map { it.id })
        assertEquals(listOf("evt-gym-05"), matching("Gym Session", LocalDate.of(2026, 6, 2)).map { it.id })
        assertEquals(listOf("evt-client-05-28"), matching("Client Call · Acme Corp", LocalDate.of(2026, 5, 28)).map { it.id })
        assertEquals(listOf("evt-jun-call"), matching("Client Call · Acme Corp", LocalDate.of(2026, 6, 11)).map { it.id })

        assertEquals(
            listOf("evt-design", "evt-jun-call", "evt-gym-05", "evt-yoga", "evt-manager"),
            events.filter { it.recurrence != null }.map { it.id },
        )
    }

    @Test
    fun localMutations_areObservable_andUndoable() = runBlocking {
        val repository = FixtureRepository()
        val revisions = mutableListOf<Long>()
        val subscription = repository.observe { revisions += it.revision }

        val event = repository.addEvent(NewEvent("Coffee", fixtureDate, LocalTime.of(15, 0))).applied()
        val task = repository.addTask(NewTask("Pack charger", fixtureDate)).applied()
        val journal = repository.addJournal(NewJournal(fixtureDate, "After work", "A useful note.")).applied()
        val completed = repository.setTaskDone(task.id, true).applied()

        assertTrue(repository.events().contains(event))
        assertTrue(repository.journals().contains(journal))
        assertTrue(repository.tasks().first { it.id == task.id }.done)
        val updatedJournal = repository.updateJournal(journal.id, NewJournal(fixtureDate.plusDays(1), "After work", "A revised note.")).applied()
        assertEquals(fixtureDate.plusDays(1), updatedJournal.date)
        assertEquals("A revised note.", repository.journals().first { it.id == journal.id }.body)
        repository.deleteJournal(journal.id).applied()
        assertTrue(repository.journals().none { it.id == journal.id })
        repository.undo(completed).applied()
        assertTrue(!repository.tasks().first { it.id == task.id }.done)
        assertTrue(revisions.size >= 7)
        subscription.close()
    }

    @Test
    fun twoTaskCompletions_canBothBeUndoneWithoutClobberingEachOther() = runBlocking {
        val repository = FixtureRepository()
        val first = repository.setTaskDone("task-inbox", true).applied()
        val second = repository.setTaskDone("task-overdue", true).applied()

        assertTrue(repository.tasks().first { it.id == "task-inbox" }.done)
        assertTrue(repository.tasks().first { it.id == "task-overdue" }.done)
        repository.undo(second).applied()
        repository.undo(first).applied()
        assertTrue(!repository.tasks().first { it.id == "task-inbox" }.done)
        assertTrue(!repository.tasks().first { it.id == "task-overdue" }.done)
    }

    @Test
    fun reschedule_isReversible_andDoesNotClobberLaterEdits() = runBlocking {
        val repository = FixtureRepository()
        val change = repository.rescheduleTask("task-inbox", fixtureDate.plusDays(2)).applied()

        assertEquals(fixtureDate.plusDays(2), repository.tasks().first { it.id == "task-inbox" }.due)
        repository.undo(change).applied()
        assertEquals(fixtureDate, repository.tasks().first { it.id == "task-inbox" }.due)
        val stale = repository.rescheduleTask("task-inbox", fixtureDate.plusDays(3)).applied()
        repository.rescheduleTask("task-inbox", fixtureDate.plusDays(4)).applied()
        assertTrue(repository.undo(stale) is WriteResult.Rejected)
    }

    @Test
    fun updateEvent_replacesExistingRecordWithoutChangingItsId() = runBlocking {
        val repository = FixtureRepository()
        val original = repository.events().first { it.id == "evt-lunch" }

        val updated = repository.updateEvent(
            original.id,
            NewEvent(
                title = "Lunch with Ivo",
                date = fixtureDate,
                startTime = LocalTime.of(13, 0),
                durationMinutes = 45,
                color = original.color,
                location = "Café Lumen",
                calendarId = original.calendarId,
            ),
        ).applied()

        assertEquals(original.id, updated.id)
        assertEquals(1, repository.events().count { it.id == original.id })
        assertEquals("Lunch with Ivo", repository.events().first { it.id == original.id }.title)
    }

    @Test
    fun updateTask_replacesEditableFields_withoutChangingItsId() = runBlocking {
        val repository = FixtureRepository()
        val original = repository.tasks().first { it.id == "task-inbox" }

        val updated = repository.updateTask(
            original.id,
            NewTask(
                title = "Review the revised calendar notes",
                due = fixtureDate.plusDays(1),
                color = original.color,
                category = "Personal",
            ),
            done = true,
        ).applied()

        assertEquals(original.id, updated.id)
        assertEquals("Review the revised calendar notes", updated.title)
        assertEquals(fixtureDate.plusDays(1), updated.due)
        assertTrue(updated.done)
        assertEquals("Personal", updated.category)
        assertEquals(1, repository.tasks().count { it.id == original.id })
    }

    @Test
    fun recurringFixture_hasReadableSummary_andHonorsEndDate() {
        val event = FixtureRepository().events().first { it.id == "evt-design" }

        assertEquals("Every Monday until 30 Jun", formatRecurrenceSummary(event))
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 5, 18, 10, 0),
                LocalDateTime.of(2026, 5, 25, 10, 0),
                LocalDateTime.of(2026, 6, 1, 10, 0),
            ),
            nextOccurrences(event, fixtureDate),
        )
        assertTrue(nextOccurrences(event, LocalDate.of(2026, 7, 1)).isEmpty())
    }

    @Test
    fun recurringOccurrences_canStartAtTheTappedOccurrence() {
        val event = FixtureRepository().events().first { it.id == "evt-design" }

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 8, 10, 0),
                LocalDateTime.of(2026, 6, 15, 10, 0),
            ),
            nextOccurrences(event, LocalDate.of(2026, 6, 1)),
        )
    }

    @Test
    fun oneOffEvents_haveNoRecurrenceSummaryOrOccurrences() {
        val event = FixtureRepository().events().first { it.id == "evt-lunch" }

        assertEquals("", formatRecurrenceSummary(event))
        assertTrue(nextOccurrences(event, fixtureDate).isEmpty())
    }

    @Test
    fun formatting_isLocaleIndependentForScreenshotFixtures() {
        assertEquals("Mon, May 18", formatCalinoDate(fixtureDate))
        assertEquals("10:00 AM", formatCalinoTime(LocalDateTime.of(2026, 5, 18, 10, 0)))
    }

    @Test
    fun recurrence_expandsOnlyMaterializedFutureOccurrences() {
        val event = FixtureRepository().events().first().copy(
            recurrence = "weekly",
            start = LocalDateTime.of(2026, 5, 14, 9, 0),
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 5, 21, 9, 0),
                LocalDateTime.of(2026, 5, 28, 9, 0),
                LocalDateTime.of(2026, 6, 4, 9, 0),
            ),
            nextOccurrences(event, fixtureDate),
        )
        assertTrue(nextOccurrences(event, fixtureDate, limit = 0).isEmpty())
    }
}

private fun <T> WriteResult<T>.applied(): T =
    (this as? WriteResult.Applied<T>)?.record
        ?: error("Expected an applied fixture write, got $this")
