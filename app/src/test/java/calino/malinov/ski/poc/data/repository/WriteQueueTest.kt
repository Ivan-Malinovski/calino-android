package calino.malinov.ski.poc.data.repository

import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.ZoneOffset
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WriteQueueTest {

    private lateinit var root: File
    private lateinit var queueFile: File
    private val start = Instant.parse("2026-09-10T10:00:00Z")
    private val clock = Clock.fixed(start, ZoneOffset.UTC)

    @Before
    fun setUp() {
        root = Files.createTempDirectory("calino-write-queue-test").toFile()
        queueFile = File(root, "pending-writes.json")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `queue survives a new store instance and preserves every operation field and order`() {
        val store = FilePendingChangeStore(queueFile, clock = clock)
        val move = request(
            type = PendingChangeType.MOVE,
            eventId = "event-2",
            data = "BEGIN:VCALENDAR\nEND:VCALENDAR",
            baseData = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:event-2\nEND:VEVENT\nEND:VCALENDAR",
            calendarUrl = "https://dav.example.test/calendars/test-user/target/",
            href = "https://dav.example.test/calendars/test-user/target/event-2.ics",
            etag = "target-etag",
            sourceCalendarId = "source",
            sourceCalendarUrl = "https://dav.example.test/calendars/test-user/source/",
            sourceHref = "https://dav.example.test/calendars/test-user/source/event-2.ics",
            sourceEtag = "source-etag",
            sourceData = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:event-2\nSUMMARY:source\nEND:VEVENT\nEND:VCALENDAR",
        )

        val first = store.enqueue(request(type = PendingChangeType.CREATE, eventId = "event-1"), start)
        val second = store.enqueue(move, start.plusSeconds(1))
        val third = store.enqueue(request(type = PendingChangeType.DELETE_HREF, eventId = "event-3"), start.plusSeconds(2))

        assertTrue(first is PendingChangeEnqueueResult.Enqueued)
        assertTrue(second is PendingChangeEnqueueResult.Enqueued)
        assertTrue(third is PendingChangeEnqueueResult.Enqueued)
        val beforeRestart = store.snapshot()

        val restored = FilePendingChangeStore(queueFile, clock = clock)
        assertEquals(beforeRestart, restored.snapshot())
        assertEquals(
            listOf(PendingChangeType.CREATE, PendingChangeType.MOVE, PendingChangeType.DELETE_HREF),
            restored.snapshot().map { it.type },
        )
        assertEquals("target-etag", restored.snapshot()[1].etag)
        assertEquals("source-etag", restored.snapshot()[1].sourceEtag)
        assertEquals(
            "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:event-2\nSUMMARY:source\nEND:VEVENT\nEND:VCALENDAR",
            restored.snapshot()[1].sourceData,
        )
        assertEquals("BEGIN:VCALENDAR\nEND:VCALENDAR", restored.snapshot()[1].payload)
        assertEquals(
            "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:event-2\nEND:VEVENT\nEND:VCALENDAR",
            restored.snapshot()[1].baseData,
        )
        assertTrue(queueFile.isFile)
        assertFalse(File(queueFile.path + ".tmp").exists())
    }

    @Test
    fun `ready exposes only the FIFO prefix and a waiting head blocks later writes`() {
        val store = FilePendingChangeStore(queueFile, clock = clock)
        val first = enqueued(store, "first", PendingChangeType.UPDATE)
        enqueued(store, "second", PendingChangeType.UPDATE)

        val retried = store.markRetry(
            first.id,
            PendingChangeFailure("offline", at = start),
            counted = false,
            now = start,
        )!!

        assertEquals(PendingChangeState.RETRY, retried.state)
        assertEquals(0, retried.retryCount)
        assertEquals(1, retried.attemptCount)
        assertTrue(retried.nextAttemptAt!!.isAfter(start))
        assertTrue(store.ready(start).isEmpty())
        assertEquals(
            listOf("first", "second"),
            store.ready(start.plusMillis(PENDING_CHANGE_BACKOFF_BASE_MS)).map { it.eventId },
        )
    }

    @Test
    fun `counted retries are bounded and become a durable dead letter`() {
        val store = FilePendingChangeStore(queueFile, clock = clock, maxRetries = 2)
        val first = enqueued(store, "first", PendingChangeType.CREATE)
        enqueued(store, "second", PendingChangeType.CREATE)
        val failure = PendingChangeFailure("HTTP 503", statusCode = 503, at = start)

        val retry = store.markRetry(first.id, failure, now = start)!!
        assertEquals(PendingChangeState.RETRY, retry.state)
        assertEquals(1, retry.retryCount)
        assertEquals(start.plusSeconds(30), retry.nextAttemptAt)

        val dead = store.markRetry(first.id, failure, now = start.plusSeconds(30))!!
        assertEquals(PendingChangeState.DEAD_LETTER, dead.state)
        assertEquals(2, dead.retryCount)
        assertNull(dead.nextAttemptAt)
        assertEquals(listOf("first"), store.deadLetters().map { it.eventId })
        assertTrue(
            "a dead letter must hold the FIFO gate",
            store.ready(start.plus(1, ChronoUnit.DAYS)).isEmpty(),
        )

        val restored = FilePendingChangeStore(queueFile, clock = clock, maxRetries = 2)
        assertEquals(PendingChangeState.DEAD_LETTER, restored.snapshot().first().state)
        assertEquals(503, restored.snapshot().first().lastFailure!!.statusCode)
    }

    @Test
    fun `requeue and discard resolve a dead letter without changing surviving order`() {
        val store = FilePendingChangeStore(queueFile, clock = clock, maxRetries = 1)
        val first = enqueued(store, "first", PendingChangeType.DELETE)
        enqueued(store, "second", PendingChangeType.UPDATE)
        store.markDeadLetter(first.id, PendingChangeFailure("permission denied", at = start), start)

        val requeued = store.requeue(first.id, start.plusSeconds(5))!!
        assertEquals(PendingChangeState.PENDING, requeued.state)
        assertEquals(listOf("first", "second"), store.ready(start.plusSeconds(5)).map { it.eventId })

        assertTrue(store.markDeadLetter(first.id, PendingChangeFailure("still denied", at = start), start) != null)
        assertTrue(store.discard(first.id))
        assertEquals(listOf("second"), store.snapshot().map { it.eventId })
        assertFalse(store.discard(first.id))
    }

    @Test
    fun `acknowledging a write removes it durably`() {
        val store = FilePendingChangeStore(queueFile, clock = clock)
        val first = enqueued(store, "first", PendingChangeType.CREATE)
        enqueued(store, "second", PendingChangeType.UPDATE)

        assertTrue(store.acknowledge(first.id))
        assertFalse(store.acknowledge(first.id))
        assertEquals(listOf("second"), FilePendingChangeStore(queueFile, clock = clock).snapshot().map { it.eventId })
    }

    @Test
    fun `a queued create can absorb a later edit without changing its FIFO slot`() {
        val store = FilePendingChangeStore(queueFile, clock = clock)
        val first = enqueued(store, "first", PendingChangeType.CREATE)
        enqueued(store, "second", PendingChangeType.UPDATE)
        store.markRetry(first.id, PendingChangeFailure("offline", at = start), now = start)

        val replaced = store.replaceCreatePayload(
            id = first.id,
            data = "BEGIN:VCALENDAR\nEND:VCALENDAR",
            href = "https://dav.example.test/calendars/test-user/first.ics",
            now = start.plusSeconds(5),
        )!!

        assertEquals(first.id, replaced.id)
        assertEquals(PendingChangeType.CREATE, replaced.type)
        assertEquals(PendingChangeState.PENDING, replaced.state)
        assertEquals(0, replaced.retryCount)
        assertEquals("BEGIN:VCALENDAR\nEND:VCALENDAR", replaced.data)
        assertEquals(
            listOf("first", "second"),
            FilePendingChangeStore(queueFile, clock = clock).snapshot().map { it.eventId },
        )
    }

    @Test
    fun `dependent cleanup can be inserted before its move slot`() {
        val store = FilePendingChangeStore(queueFile, clock = clock)
        enqueued(store, "before", PendingChangeType.UPDATE)
        val move = enqueued(store, "move", PendingChangeType.MOVE)
        enqueued(store, "dependent", PendingChangeType.UPDATE)

        val cleanup = store.enqueueBefore(
            beforeId = move.id,
            request = request(
                type = PendingChangeType.DELETE_HREF,
                eventId = "move",
                href = "https://dav.example.test/calendars/test-user/source/move.ics",
                etag = "source-etag",
                data = "BEGIN:VCALENDAR\nEND:VCALENDAR",
            ),
            now = start.plusSeconds(5),
        )

        assertTrue(cleanup is PendingChangeEnqueueResult.Enqueued)
        assertEquals(
            listOf("before", "move", "move", "dependent"),
            store.snapshot().map { it.eventId },
        )
        assertEquals(
            listOf(PendingChangeType.UPDATE, PendingChangeType.DELETE_HREF, PendingChangeType.MOVE, PendingChangeType.UPDATE),
            store.snapshot().map { it.type },
        )
    }

    @Test
    fun `queue bound rejects new work without changing the durable document`() {
        val store = FilePendingChangeStore(queueFile, clock = clock, maxEntries = 2)
        enqueued(store, "first", PendingChangeType.CREATE)
        enqueued(store, "second", PendingChangeType.UPDATE)
        val before = queueFile.readText()

        val result = store.enqueue(request(type = PendingChangeType.DELETE, eventId = "third"), start)

        assertEquals(
            PendingChangeEnqueueResult.Rejected("The pending write queue is full (maximum 2 entries)."),
            result,
        )
        assertEquals(before, queueFile.readText())
        assertEquals(2, store.snapshot().size)
    }

    @Test
    fun `malformed records are skipped while valid records remain recoverable`() {
        val store = FilePendingChangeStore(queueFile, clock = clock)
        enqueued(store, "valid", PendingChangeType.CREATE)
        val rootJson = JSONObject(queueFile.readText())
        rootJson.getJSONArray("changes").put(
            JSONObject()
                .put("id", "bad")
                .put("type", "NOT_AN_OPERATION")
                .put("eventId", "bad"),
        )
        queueFile.writeText(rootJson.toString())

        val restored = FilePendingChangeStore(queueFile, clock = clock)

        assertEquals(listOf("valid"), restored.snapshot().map { it.eventId })
    }

    @Test
    fun `a complete orphan temporary document can recover when the primary is absent`() {
        val store = FilePendingChangeStore(queueFile, clock = clock)
        enqueued(store, "recover-me", PendingChangeType.CREATE)
        val completeDocument = queueFile.readText()
        assertTrue(queueFile.delete())
        File(queueFile.path + ".tmp").writeText(completeDocument)

        val restored = FilePendingChangeStore(queueFile, clock = clock)

        assertEquals(listOf("recover-me"), restored.snapshot().map { it.eventId })
        assertTrue(queueFile.isFile)
        assertFalse(File(queueFile.path + ".tmp").exists())
    }

    @Test
    fun `queue rejects credential-bearing payloads and URL user information`() {
        val store = FilePendingChangeStore(queueFile, clock = clock)

        assertFailsWith<IllegalArgumentException> {
            store.enqueue(
                request(
                    type = PendingChangeType.CREATE,
                    eventId = "secret-payload",
                    data = "{\"password\":\"do-not-store\"}",
                ),
                start,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            store.enqueue(
                request(
                    type = PendingChangeType.CREATE,
                    eventId = "secret-url",
                    calendarUrl = "https://test-user:do-not-store@dav.example.test/calendar/",
                ),
                start,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            store.enqueue(
                request(
                    type = PendingChangeType.CREATE,
                    eventId = "secret-query",
                    calendarUrl = "https://dav.example.test/calendar/?password=do-not-store",
                ),
                start,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            store.enqueue(
                request(
                    type = PendingChangeType.CREATE,
                    eventId = "secret-ical",
                    data = "BEGIN:VCALENDAR\nX-PASSWORD:do-not-store\nEND:VCALENDAR",
                ),
                start,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            store.enqueue(
                request(
                    type = PendingChangeType.UPDATE,
                    eventId = "secret-base",
                    baseData = "BEGIN:VCALENDAR\nX-PASSWORD:do-not-store\nEND:VCALENDAR",
                ),
                start,
            )
        }
        assertFalse(queueFile.exists())
    }

    @Test
    fun `failure diagnostics are redacted before persistence`() {
        val store = FilePendingChangeStore(queueFile, clock = clock)
        val change = enqueued(store, "diagnostic", PendingChangeType.UPDATE)

        store.markDeadLetter(
            change.id,
            PendingChangeFailure(
                "password=super-secret Authorization: Bearer also-secret {\"password\":\"json-secret\"}",
                statusCode = 403,
                at = start,
            ),
            start,
        )

        val raw = queueFile.readText()
        assertFalse(raw.contains("super-secret"))
        assertFalse(raw.contains("also-secret"))
        assertFalse(raw.contains("json-secret"))
        val restored = FilePendingChangeStore(queueFile, clock = clock)
        assertNotNull(restored.snapshot().single().lastFailure)
        assertTrue(restored.snapshot().single().lastFailure!!.message.contains("[redacted]"))
    }

    @Test
    fun `backoff doubles and honors retry after as a lower bound`() {
        assertEquals(30_000L, backoffDelayMs(0))
        assertEquals(60_000L, backoffDelayMs(1))
        assertEquals(120_000L, backoffDelayMs(2))
        assertEquals(1_800_000L, backoffDelayMs(6))
        assertEquals(1_800_000L, backoffDelayMs(20))
        assertEquals(120_000L, backoffDelayMs(0, retryAfterSeconds = 120))
        assertEquals(240_000L, backoffDelayMs(3, retryAfterSeconds = 30))
    }

    private fun enqueued(
        store: FilePendingChangeStore,
        eventId: String,
        type: PendingChangeType,
    ): PendingChange = when (val result = store.enqueue(request(type, eventId), start)) {
        is PendingChangeEnqueueResult.Enqueued -> result.change
        is PendingChangeEnqueueResult.Rejected -> error(result.reason)
    }

    private fun request(
        type: PendingChangeType,
        eventId: String,
        data: String? = null,
        baseData: String? = null,
        calendarUrl: String? = null,
        href: String? = null,
        etag: String? = null,
        sourceCalendarId: String? = null,
        sourceCalendarUrl: String? = null,
        sourceHref: String? = null,
        sourceEtag: String? = null,
        sourceData: String? = null,
    ) = PendingChangeRequest(
        type = type,
        eventId = eventId,
        accountId = "account-1",
        calendarId = "calendar-1",
        component = "VEVENT",
        calendarUrl = calendarUrl,
        uid = "uid-$eventId",
        href = href,
        etag = etag,
        data = data,
        baseData = baseData,
        sourceCalendarId = sourceCalendarId,
        sourceCalendarUrl = sourceCalendarUrl,
        sourceHref = sourceHref,
        sourceEtag = sourceEtag,
        sourceData = sourceData,
    )

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return
            throw AssertionError("Expected ${T::class.java.name}, got ${error::class.java.name}", error)
        }
        throw AssertionError("Expected ${T::class.java.name}")
    }
}
