package calino.malinov.ski.qa

import calino.malinov.ski.data.repository.FixtureRepository
import calino.malinov.ski.data.repository.TrashKind
import calino.malinov.ski.data.repository.UndoableChange
import calino.malinov.ski.data.repository.WriteResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureDeleteUndoTest {
    private fun offered(result: WriteResult<UndoableChange?>): UndoableChange {
        assertTrue("delete should apply: $result", result is WriteResult.Applied)
        return assertNotNull((result as WriteResult.Applied).record).let { result.record!! }
    }

    @Test fun `deleted event comes back unchanged`() = runBlocking {
        val repository = FixtureRepository()
        val before = repository.events()
        val target = before.first()
        val change = offered(repository.deleteEvent(target.id))
        assertTrue(repository.events().none { it.id == target.id })
        assertTrue(repository.undo(change) is WriteResult.Applied)
        assertEquals(before.toSet(), repository.events().toSet())
    }

    @Test fun `deleted task comes back unchanged`() = runBlocking {
        val repository = FixtureRepository()
        val before = repository.tasks()
        val target = before.first()
        val change = offered(repository.deleteTask(target.id))
        assertTrue(repository.tasks().none { it.id == target.id })
        assertTrue(repository.undo(change) is WriteResult.Applied)
        assertEquals(before.toSet(), repository.tasks().toSet())
    }

    @Test fun `deleted journal comes back unchanged`() = runBlocking {
        val repository = FixtureRepository()
        val before = repository.journals()
        val target = before.first()
        val change = offered(repository.deleteJournal(target.id))
        assertTrue(repository.journals().none { it.id == target.id })
        assertTrue(repository.undo(change) is WriteResult.Applied)
        assertEquals(before.toSet(), repository.journals().toSet())
    }

    @Test fun `deleted contact comes back with its derived reminder events`() = runBlocking {
        val repository = FixtureRepository()
        val events = repository.events()
        val contacts = repository.contacts()
        if (contacts.isEmpty()) return@runBlocking
        val change = offered(repository.deleteContact(contacts.first().id))
        assertTrue(repository.contacts().none { it.id == contacts.first().id })
        assertTrue(repository.undo(change) is WriteResult.Applied)
        assertEquals(contacts.toSet(), repository.contacts().toSet())
        assertEquals(events.toSet(), repository.events().toSet())
    }

    @Test fun `two deletes can be undone in either order`() = runBlocking {
        val repository = FixtureRepository()
        val tasks = repository.tasks()
        val first = offered(repository.deleteTask(tasks[0].id))
        val second = offered(repository.deleteTask(tasks[1].id))
        assertTrue(repository.undo(first) is WriteResult.Applied)
        assertTrue(repository.undo(second) is WriteResult.Applied)
        assertEquals(tasks.toSet(), repository.tasks().toSet())
    }

    @Test fun `a sample delete is listed in recently deleted, newest first, with its kind`() = runBlocking {
        val repository = FixtureRepository()
        val event = repository.events().first()
        val task = repository.tasks().first()
        offered(repository.deleteEvent(event.id))
        offered(repository.deleteTask(task.id))
        val listed = repository.trashedItems()
        assertEquals(listOf(TrashKind.Task, TrashKind.Event), listed.map { it.kind })
        assertEquals(listOf(task.title, event.title), listed.map { it.title })
    }

    @Test fun `restoring from recently deleted puts the event back and empties its row`() = runBlocking {
        val repository = FixtureRepository()
        val before = repository.events()
        offered(repository.deleteEvent(before.first().id))
        val entry = repository.trashedItems().single()
        assertTrue(repository.restoreFromTrash(entry.id) is WriteResult.Applied)
        assertEquals(before.toSet(), repository.events().toSet())
        assertTrue(repository.trashedItems().isEmpty())
    }

    @Test fun `the pill's undo also clears the recently deleted row`() = runBlocking {
        val repository = FixtureRepository()
        val change = offered(repository.deleteEvent(repository.events().first().id))
        assertEquals(1, repository.trashedItems().size)
        assertTrue(repository.undo(change) is WriteResult.Applied)
        assertTrue(repository.trashedItems().isEmpty())
    }

    @Test fun `delete forever and empty drop rows without restoring`() = runBlocking {
        val repository = FixtureRepository()
        val events = repository.events()
        offered(repository.deleteEvent(events[0].id))
        offered(repository.deleteEvent(events[1].id))
        repository.deleteTrashed(repository.trashedItems().first().id)
        assertEquals(1, repository.trashedItems().size)
        repository.emptyTrash()
        assertTrue(repository.trashedItems().isEmpty())
        assertTrue(repository.events().none { it.id == events[0].id || it.id == events[1].id })
    }
}
