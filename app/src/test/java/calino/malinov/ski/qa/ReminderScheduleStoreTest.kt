package calino.malinov.ski.qa

import calino.malinov.ski.notify.FileReminderScheduleStore
import calino.malinov.ski.notify.ReminderFiring
import calino.malinov.ski.notify.ReminderKind
import calino.malinov.ski.notify.ReminderSchedule
import calino.malinov.ski.notify.ReminderScheduleJson
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The durable reminder schedule.
 *
 * Every failure here is a failure a boot receiver would hit, with no UI to
 * report it and no user watching. So the bias is the same as the read cache's:
 * an unreadable schedule yields an empty one and the app re-plans; it never
 * throws on the way in.
 */
class ReminderScheduleStoreTest {

    private lateinit var root: File
    private lateinit var file: File

    private val zone: ZoneId = ZoneId.of("Europe/Copenhagen")
    private val now: Instant = Instant.parse("2026-09-12T08:00:00Z")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("calino-reminder-schedule").toFile()
        file = File(root, "reminder-schedule.json")
    }

    private fun store(at: Instant = now) =
        FileReminderScheduleStore(file, Clock.fixed(at, ZoneOffset.UTC))

    private fun firing(
        key: String,
        at: Instant,
        kind: ReminderKind = ReminderKind.Event,
        occurrenceDay: Long? = 20709L,
        uid: String? = "uid-1",
    ) = ReminderFiring(
        key = key,
        at = at,
        kind = kind,
        recordId = "record-$key",
        uid = uid,
        occurrenceDay = occurrenceDay,
        title = "Design review",
        subtitle = "10:00 · Studio",
        minutesBefore = 10,
        anchor = at.plusSeconds(600),
    )

    @Test
    fun `a plan round trips through the file`() {
        val firings = listOf(
            firing("a", now.plusSeconds(3600)),
            firing("b", now.plusSeconds(7200), kind = ReminderKind.Task, occurrenceDay = null, uid = null),
        )
        store().replace(firings, now, zone)

        val reloaded = store().load()
        assertEquals(firings, reloaded.firings)
        assertEquals(zone.id, reloaded.zoneId)
        assertEquals(now, reloaded.generatedAt)
    }

    @Test
    fun `a truncated document loads as empty rather than throwing`() {
        store().replace(listOf(firing("a", now.plusSeconds(60))), now, zone)
        val whole = file.readText()
        file.writeText(whole.substring(0, whole.length / 2))

        assertEquals(emptyList<ReminderFiring>(), store().load().firings)
    }

    @Test
    fun `a document from an unknown version is discarded`() {
        file.writeText("""{"version":99,"generatedAt":"$now","zoneId":"${zone.id}","firings":[]}""")
        assertNull(ReminderScheduleJson.decode(file.readText()))
        assertEquals(ReminderSchedule.Empty.firings, store().load().firings)
    }

    @Test
    fun `next skips a delivered firing`() {
        val first = firing("a", now.plusSeconds(600))
        val second = firing("b", now.plusSeconds(1200))
        store().replace(listOf(first, second), now, zone)

        assertEquals("a", store().next(now)?.key)
        store().markDelivered("a", now)
        assertEquals("b", store().next(now)?.key)
    }

    @Test
    fun `a snooze reopens a delivered firing at its new time`() {
        val only = firing("a", now.plusSeconds(60))
        store().replace(listOf(only), now, zone)
        store().markDelivered("a", now)
        assertNull(store().next(now))

        store().snooze("a", now.plusSeconds(300))
        assertEquals("a", store().next(now)?.key)
        assertEquals(emptyList<ReminderFiring>(), store().due(now, Duration.ofMinutes(5)))
        assertEquals(listOf("a"), store().due(now.plusSeconds(301), Duration.ofMinutes(5)).map { it.key })
    }

    @Test
    fun `due returns the backlog inside the grace window and nothing older`() {
        val recent = firing("recent", now.minusSeconds(120))
        val ancient = firing("ancient", now.minus(Duration.ofDays(3)))
        val future = firing("future", now.plusSeconds(600))
        store().replace(listOf(recent, ancient, future), now, zone)

        val due = store().due(now, Duration.ofMinutes(5))
        assertEquals(listOf("recent"), due.map { it.key })
    }

    @Test
    fun `history older than the retention window is pruned on the next plan`() {
        val stale = firing("stale", now.minus(Duration.ofDays(20)))
        store().replace(listOf(stale), now.minus(Duration.ofDays(20)), zone)
        store(now.minus(Duration.ofDays(20))).markDelivered("stale", now.minus(Duration.ofDays(20)))

        // Re-planning today keeps the firing but drops its ancient watermark.
        store().replace(listOf(stale), now, zone)
        assertTrue(store().load().delivered.isEmpty())
    }

    @Test
    fun `history for a firing that no longer exists is dropped`() {
        store().replace(listOf(firing("a", now.plusSeconds(60))), now, zone)
        store().markDelivered("a", now)

        store().replace(listOf(firing("b", now.plusSeconds(60))), now, zone)
        assertTrue(store().load().delivered.isEmpty())
    }

    @Test
    fun `a missing file loads as an empty schedule`() {
        assertEquals(emptyList<ReminderFiring>(), store().load().firings)
        assertNull(store().next(now))
        assertEquals(emptyList<ReminderFiring>(), store().due(now, Duration.ofMinutes(5)))
    }
}
