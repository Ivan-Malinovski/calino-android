package calino.malinov.ski.qa

import calino.malinov.ski.notify.ReminderAction
import calino.malinov.ski.notify.ReminderActionQueue
import calino.malinov.ski.notify.ReminderActions
import calino.malinov.ski.notify.ReminderKind
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Shade actions that had nowhere to go yet.
 *
 * The queue exists because "Mark done" can be pressed by someone whose phone
 * is offline, in a process with no account restored. Losing one is losing a
 * user's deliberate act, so the rules here are about not losing it -- and
 * about not applying a stale one weeks later either.
 */
class ReminderActionQueueTest {

    private lateinit var file: File
    private val now: Instant = Instant.parse("2026-09-12T08:00:00Z")

    @Before
    fun setUp() {
        file = File(Files.createTempDirectory("calino-reminder-actions").toFile(), "actions.json")
    }

    private fun queue(at: Instant = now) = ReminderActionQueue(file, Clock.fixed(at, ZoneOffset.UTC))

    private fun action(
        key: String,
        what: String = ReminderActions.MarkDone,
        at: Instant = now,
    ) = ReminderAction(what, key, "task-$key", ReminderKind.Task, at)

    @Test
    fun `an action round trips through the file`() {
        queue().enqueue(action("a"))
        assertEquals(listOf(action("a")), queue().pending())
    }

    @Test
    fun `a second action on the same firing replaces the first`() {
        queue().enqueue(action("a", ReminderActions.MarkDone))
        queue().enqueue(action("a", ReminderActions.Tomorrow))

        val pending = queue().pending()
        assertEquals(1, pending.size)
        assertEquals(ReminderActions.Tomorrow, pending.single().action)
    }

    @Test
    fun `removing one leaves the others`() {
        queue().enqueue(action("a"))
        queue().enqueue(action("b"))
        queue().remove("a")

        assertEquals(listOf("b"), queue().pending().map { it.firingKey })
    }

    @Test
    fun `an expired action is dropped when the queue is drained`() {
        queue(now.minus(Duration.ofDays(5))).enqueue(action("old", at = now.minus(Duration.ofDays(5))))
        queue().enqueue(action("fresh"))

        assertEquals(listOf("fresh"), queue().drainable().map { it.firingKey })
        // The drain also persists the pruning, so the next read agrees.
        assertEquals(listOf("fresh"), queue().pending().map { it.firingKey })
    }

    @Test
    fun `a corrupt file reads as empty rather than throwing`() {
        queue().enqueue(action("a"))
        file.writeText("{ this is not json")

        assertTrue(queue().pending().isEmpty())
        // And it recovers: a later write lands on a readable document.
        queue().enqueue(action("b"))
        assertEquals(listOf("b"), queue().pending().map { it.firingKey })
    }

    @Test
    fun `a missing file is an empty queue`() {
        assertTrue(queue().pending().isEmpty())
        assertTrue(queue().drainable().isEmpty())
    }
}
