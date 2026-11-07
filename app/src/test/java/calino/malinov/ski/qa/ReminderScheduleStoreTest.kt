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
        location = "Studio, Copenhagen",
        meetingUrl = "https://meet.jit.si/design-review",
        minutesBefore = 10,
        anchor = at.plusSeconds(600),
    )

    @Test
    fun `structured reminder labels survive process death without freezing the locale`() {
        val original = firing("localized", now.plusSeconds(3600)).copy(
            displayAllDay = false,
            displayDate = java.time.LocalDate.of(2026, 9, 12),
            displayTime = java.time.LocalTime.of(10, 30),
            displayCategory = "Personal",
        )
        store().replace(listOf(original), now, zone)
        assertEquals(original, store().load().firings.single())
    }

    @Test
    fun `an older schedule retains its fallback subtitle`() {
        val original = firing("legacy", now.plusSeconds(3600))
        val encoded = ReminderScheduleJson.encode(ReminderSchedule(now, zone.id, listOf(original)))
        val decoded = ReminderScheduleJson.decode(encoded)!!.firings.single()
        assertNull(decoded.displayAllDay)
        assertNull(decoded.displayDate)
        assertEquals("10:00 · Studio", decoded.subtitle)
    }

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
    fun `a schedule written before locations were stored still decodes`() {
        val legacy = ReminderScheduleJson.encode(
            ReminderSchedule(now, zone.id, listOf(firing("a", now.plusSeconds(3600)))),
        ).replace("\"location\":\"Studio, Copenhagen\",", "")

        val decoded = ReminderScheduleJson.decode(legacy)
        assertEquals(1, decoded?.firings?.size)
        assertNull(decoded?.firings?.single()?.location)
    }

    @Test
    fun `a schedule written before meeting links were stored still decodes`() {
        val legacy = ReminderScheduleJson.encode(
            ReminderSchedule(now, zone.id, listOf(firing("a", now.plusSeconds(3600)))),
        ).replace(Regex(""""meetingUrl":"[^"]*","""), "")
        check(!legacy.contains("meetingUrl"))

        val decoded = ReminderScheduleJson.decode(legacy)
        assertEquals("Studio, Copenhagen", decoded?.firings?.single()?.location)
        assertNull(decoded?.firings?.single()?.meetingUrl)
    }

    @Test
    fun `the nag flag survives the file and an older document decodes without it`() {
        val nag = firing("nag:t:1", now.plusSeconds(3600), kind = ReminderKind.Task).copy(nag = true)
        store().replace(listOf(nag), now, zone)
        assertTrue(store().load().firings.single().nag)

        val legacy = ReminderScheduleJson.encode(ReminderSchedule(now, zone.id, listOf(nag)))
            .replace("\"nag\":true,", "").replace(",\"nag\":true", "")
        check(!legacy.contains("\"nag\""))
        assertEquals(false, ReminderScheduleJson.decode(legacy)!!.firings.single().nag)
    }

    @Test
    fun `a delivered nag slot is not delivered again after a re-plan, and a snooze moves it`() {
        val slot = firing("nag:t:1", now.plusSeconds(60), kind = ReminderKind.Task).copy(nag = true)
        val s = store()
        s.replace(listOf(slot), now, zone)
        val later = now.plusSeconds(120)
        assertEquals(listOf(slot.key), s.due(later, Duration.ofHours(2)).map { it.key })

        s.markDelivered(slot.key, later)
        s.replace(listOf(slot), later, zone)
        assertTrue(s.due(later, Duration.ofHours(2)).isEmpty())

        val other = firing("nag:t:2", now.plusSeconds(60), kind = ReminderKind.Task).copy(nag = true)
        s.replace(listOf(other), later, zone)
        s.snooze(other.key, later.plusSeconds(300))
        assertTrue(s.due(later.plusSeconds(60), Duration.ofHours(2)).isEmpty())
        assertEquals(listOf(other.key), s.due(later.plusSeconds(301), Duration.ofHours(2)).map { it.key })
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
    fun `a delivered firing survives a re-plan so its notification buttons still resolve`() {
        val delivered = firing("slot-1", now.minusSeconds(60))
        val later = firing("slot-2", now.plusSeconds(3_600))
        store().replace(listOf(delivered, later), now.minusSeconds(120), zone)
        store().markDelivered(delivered.key, now.minusSeconds(60))

        // The planner only emits future moments, so the new plan has no slot-1.
        store().replace(listOf(later), now, zone)

        val loaded = store().load()
        assertEquals(listOf("slot-1", "slot-2"), loaded.firings.map { it.key })
        assertEquals(now.minusSeconds(60), loaded.delivered["slot-1"])
        // Kept for lookup only: it is neither due again nor the next alarm.
        assertEquals(emptyList<ReminderFiring>(), store().due(now, Duration.ofHours(2)))
        assertEquals("slot-2", store().next(now)?.key)
    }

    @Test
    fun `an undelivered past firing is still dropped by a re-plan`() {
        val missed = firing("slot-1", now.minusSeconds(60))
        store().replace(listOf(missed), now.minusSeconds(120), zone)
        store().replace(emptyList(), now, zone)
        assertEquals(emptyList<ReminderFiring>(), store().load().firings)
    }

    @Test
    fun `a delivered firing older than the history window is forgotten`() {
        val old = firing("slot-1", now.minus(Duration.ofDays(9)))
        store(now.minus(Duration.ofDays(9))).replace(listOf(old), now.minus(Duration.ofDays(9)), zone)
        store(now.minus(Duration.ofDays(9))).markDelivered(old.key, now.minus(Duration.ofDays(9)))
        store().replace(emptyList(), now, zone)
        assertEquals(emptyList<ReminderFiring>(), store().load().firings)
    }

    @Test
    fun `a missing file loads as an empty schedule`() {
        assertEquals(emptyList<ReminderFiring>(), store().load().firings)
        assertNull(store().next(now))
        assertEquals(emptyList<ReminderFiring>(), store().due(now, Duration.ofMinutes(5)))
    }
}
