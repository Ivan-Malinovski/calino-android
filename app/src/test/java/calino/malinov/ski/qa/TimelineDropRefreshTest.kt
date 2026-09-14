package calino.malinov.ski.qa

import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.FixtureRepository
import calino.malinov.ski.data.repository.moveEventToDateTime
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A timeline drop must reach the observers the UI is built on, not just the
 * store behind them. The reported symptom was a card springing back to its old
 * hour while the server had in fact taken the move.
 */
class TimelineDropRefreshTest {

    @Test
    fun `moving a timed event publishes the new start to observers`() = runBlocking {
        val repository = FixtureRepository()
        val event = repository.events().first { it.start != null && it.recurrence == null }
        val target = event.start!!.plusMinutes(45)

        var observed: CalinoSnapshot? = null
        val subscription = repository.observe { observed = it }

        repository.moveEventToDateTime(event, target)

        assertNotNull("no snapshot was published", observed)
        val moved = observed!!.events.firstOrNull { it.id == event.id }
        assertNotNull("event vanished from the published snapshot", moved)
        assertEquals("published snapshot kept the old start", target, moved!!.start)
        subscription.close()
    }

    @Test
    fun `the published events list is a new instance so compose re-reads it`() = runBlocking {
        val repository = FixtureRepository()
        val event = repository.events().first { it.start != null && it.recurrence == null }
        val before = repository.events()

        repository.moveEventToDateTime(event, event.start!!.plusMinutes(45))

        val after = repository.events()
        assertEquals(
            "lists compare equal, so a remember() keyed on them will not recompute",
            false,
            before == after,
        )
    }
    /**
     * `repository.events()` is a plain field read, not observed state. A screen
     * that calls it during composition subscribes to nothing, so a write stays
     * invisible until something unrelated recomposes it -- the bug behind a
     * timeline card springing back to its old hour. HomeScreen must therefore
     * be handed its events by a caller reading observed state.
     */
    @Test
    fun `HomeScreen takes its events as a parameter rather than reading the repository`() {
        val home = File("src/main/java/calino/malinov/ski/ui/home/HomeScreen.kt").readText()
        assertTrue(
            "HomeScreen must accept the events it draws, so a publish can invalidate it",
            home.contains("sourceEvents: List<CalEvent>"),
        )
        assertEquals(
            "HomeScreen must not read repository.events() during composition",
            false,
            home.contains("val events = repository.events()"),
        )

        val host = File("src/main/java/calino/malinov/ski/MainActivity.kt").readText()
        assertTrue(
            "the Day route must pass the observed snapshot's events into HomeScreen",
            host.contains("sourceEvents = snapshot.events"),
        )
    }
}
