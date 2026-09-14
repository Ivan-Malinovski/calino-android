package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.notify.ReminderDeepLink
import calino.malinov.ski.notify.ReminderDeepLinks
import calino.malinov.ski.notify.ReminderKind
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a notification tap lands.
 *
 * The resolution cases matter more than the format ones: a notification can be
 * tapped long after it was posted, by which time the snapshot may have
 * re-expanded the series under a different id.
 */
class ReminderDeepLinkTest {

    private val day = LocalDate.of(2026, 9, 14).toEpochDay()

    private fun event(id: String, uid: String?, on: LocalDate) = CalEvent(
        id = id,
        title = "Design review",
        color = 0xFFC2697F,
        start = LocalDateTime.of(on, java.time.LocalTime.of(10, 0)),
        durationMinutes = 60,
        calendarId = "work",
        uid = uid,
    )

    private fun task(id: String, uid: String?) = CalTask(
        id = id,
        title = "Buy flowers",
        color = 0xFFC2697F,
        due = LocalDate.of(2026, 9, 14),
        uid = uid,
    )

    @Test
    fun `an event link round trips`() {
        val link = ReminderDeepLink(ReminderKind.Event, "uid-1@2026-09-14T08:00:00Z", "uid-1", day)
        assertEquals(link, ReminderDeepLinks.parse(ReminderDeepLinks.uri(link)))
    }

    @Test
    fun `a task link round trips without an occurrence day`() {
        val link = ReminderDeepLink(ReminderKind.Task, "task-1", "uid-2", null)
        assertEquals(link, ReminderDeepLinks.parse(ReminderDeepLinks.uri(link)))
    }

    @Test
    fun `an id needing escaping survives the round trip`() {
        val link = ReminderDeepLink(ReminderKind.Event, "uid with spaces&=?/#", null, null)
        assertEquals(link, ReminderDeepLinks.parse(ReminderDeepLinks.uri(link)))
    }

    @Test
    fun `rubbish and foreign uris parse to null`() {
        assertNull(ReminderDeepLinks.parse(null))
        assertNull(ReminderDeepLinks.parse(""))
        assertNull(ReminderDeepLinks.parse("not a uri at all ::::"))
        assertNull(ReminderDeepLinks.parse("https://example.com/reminder/event?id=1"))
        assertNull(ReminderDeepLinks.parse("calino.malinov.ski://ai-photo-import"))
        assertNull(ReminderDeepLinks.parse("calino.malinov.ski://reminder/journal?id=1"))
        assertNull(ReminderDeepLinks.parse("calino.malinov.ski://reminder/event"))
    }

    @Test
    fun `an exact id wins`() {
        val wanted = event("uid-1@a", "uid-1", LocalDate.of(2026, 9, 14))
        val other = event("uid-1@b", "uid-1", LocalDate.of(2026, 9, 21))
        val link = ReminderDeepLink(ReminderKind.Event, "uid-1@a", "uid-1", day)
        assertEquals(wanted, ReminderDeepLinks.resolveEvent(link, listOf(other, wanted)))
    }

    @Test
    fun `a re-expanded series resolves by uid and occurrence day`() {
        val onTheDay = event("uid-1@fresh", "uid-1", LocalDate.of(2026, 9, 14))
        val later = event("uid-1@later", "uid-1", LocalDate.of(2026, 9, 21))
        val link = ReminderDeepLink(ReminderKind.Event, "uid-1@stale", "uid-1", day)
        assertEquals(onTheDay, ReminderDeepLinks.resolveEvent(link, listOf(later, onTheDay)))
    }

    @Test
    fun `a moved occurrence resolves to the next one at or after the wanted day`() {
        val before = event("uid-1@before", "uid-1", LocalDate.of(2026, 9, 7))
        val after = event("uid-1@after", "uid-1", LocalDate.of(2026, 9, 16))
        val link = ReminderDeepLink(ReminderKind.Event, "uid-1@stale", "uid-1", day)
        assertEquals(after, ReminderDeepLinks.resolveEvent(link, listOf(before, after)))
    }

    @Test
    fun `an unresolvable event link is null`() {
        val link = ReminderDeepLink(ReminderKind.Event, "gone", "uid-gone", day)
        assertNull(ReminderDeepLinks.resolveEvent(link, listOf(event("other", "uid-other", LocalDate.of(2026, 9, 14)))))
        assertNull(ReminderDeepLinks.resolveEvent(ReminderDeepLink(ReminderKind.Event, "gone"), emptyList()))
    }

    @Test
    fun `a task resolves by id then by uid`() {
        val byId = task("task-1", "uid-2")
        assertEquals(byId, ReminderDeepLinks.resolveTask(ReminderDeepLink(ReminderKind.Task, "task-1"), listOf(byId)))
        assertEquals(
            byId,
            ReminderDeepLinks.resolveTask(ReminderDeepLink(ReminderKind.Task, "stale", "uid-2"), listOf(byId)),
        )
        assertNull(ReminderDeepLinks.resolveTask(ReminderDeepLink(ReminderKind.Task, "stale", "uid-x"), listOf(byId)))
    }

    @Test
    fun `a kind mismatch does not resolve`() {
        val link = ReminderDeepLink(ReminderKind.Task, "task-1", "uid-2")
        assertNull(ReminderDeepLinks.resolveEvent(link, listOf(event("task-1", "uid-2", LocalDate.of(2026, 9, 14)))))
    }
}
