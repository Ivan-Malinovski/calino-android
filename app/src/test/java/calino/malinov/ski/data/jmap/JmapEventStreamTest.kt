package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.DavCredentials
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JmapEventStreamTest {
    private val fake = FakeJmapServer()

    @After
    fun tearDown() = fake.stop()

    private fun parse(vararg chunks: String): Pair<List<SseEvent>, List<Long>> {
        val events = mutableListOf<SseEvent>()
        val retries = mutableListOf<Long>()
        val parser = SseParser({ events += it }, { retries += it })
        chunks.forEach(parser::feed)
        return events to retries
    }

    @Test
    fun theParserFramesEventsAcrossChunksAndLineEndings() {
        val (events, _) = parse("\uFEFFevent: state\r", "\ndata: {\"a\":", "1}\r\nid: 7\r\n\r", "\n: comment\ndata: x\ndata: y\n\n")
        assertEquals(2, events.size)
        assertEquals(SseEvent("state", "{\"a\":1}", "7"), events[0])
        assertEquals(SseEvent("message", "x\ny", "7"), events[1])
    }

    @Test
    fun theParserIgnoresCommentsUnknownFieldsAndEventsWithoutData() {
        val (events, retries) = parse(": keep-alive\n\nevent: ping\n\nfoo: bar\nretry: 2500\nretry: nope\ndata\n\n")
        assertEquals(listOf(SseEvent("message", "", null)), events)
        assertEquals(listOf(2500L), retries)
    }

    @Test
    fun aStateChangeIsValidatedStrictly() {
        assertEquals(
            mapOf("a" to mapOf("CalendarEvent" to "s2")),
            parseStateChange("""{"@type":"StateChange","changed":{"a":{"CalendarEvent":"s2"}}}""")?.changed,
        )
        assertNull(parseStateChange("""{"@type":"StateChange","changed":{"a":{"CalendarEvent":2}}}"""))
        assertNull(parseStateChange("""{"@type":"Other","changed":{}}"""))
        assertNull(parseStateChange("not json"))
        val change = parseStateChange("""{"@type":"StateChange","changed":{"a":{"CalendarEvent":"s"}}}""")!!
        assertTrue(change.touches("CalendarEvent", "a"))
        assertFalse(change.touches("CalendarEvent", "b"))
        assertFalse(change.touches("Calendar"))
    }

    private fun stream(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setChunkedBody(body, 7)

    private val fast = EventStreamTiming(initialRetryMillis = 20, maxBackoffMillis = 80, watchdogMillis = { 300 })

    private fun change(state: String) =
        """{"@type":"StateChange","changed":{"a":{"CalendarEvent":"$state"}}}"""

    private fun watch(onChange: (JmapStateChange) -> Unit, until: CompletableDeferred<Unit>) = runBlocking {
        fake.start()
        val client = JmapClient(fake.baseUrl, DavCredentials("ann@example.com", "secret"))
        client.connect()
        val job = launch(Dispatchers.Default) { client.watchStateChanges(listOf("Calendar", "CalendarEvent"), fast, onChange) }
        try {
            withTimeout(10_000) { until.await() }
        } finally {
            job.cancel()
            job.join()
        }
    }

    @Test
    fun stateChangesAreDeliveredAndMalformedEventsAreIgnored() {
        fake.events = {
            stream(
                "event: ping\ndata: {\"interval\":30}\n\n" +
                    "event: state\ndata: nope\n\n" +
                    "event: state\nid: 1\ndata: ${change("s2")}\n\n",
            )
        }
        val seen = mutableListOf<JmapStateChange>()
        val done = CompletableDeferred<Unit>()
        watch({ seen += it; done.complete(Unit) }, done)
        assertEquals("s2", seen.first().changed.getValue("a").getValue("CalendarEvent"))
        val url = fake.eventRequests.first().requestUrl!!
        assertEquals("Calendar,CalendarEvent", url.queryParameter("types"))
        assertEquals("no", url.queryParameter("closeafter"))
        assertEquals("30", url.queryParameter("ping"))
        assertTrue(fake.eventRequests.first().getHeader("Authorization")!!.startsWith("Basic "))
        assertEquals("text/event-stream", fake.eventRequests.first().getHeader("Accept"))
    }

    @Test
    fun aDroppedStreamReconnectsAndResumesFromTheLastEventId() {
        val connections = AtomicInteger()
        fake.events = {
            if (connections.getAndIncrement() == 0) stream("event: state\nid: 41\ndata: ${change("s1")}\n\n")
            else stream("event: state\nid: 42\ndata: ${change("s2")}\n\n")
        }
        val states = mutableListOf<String>()
        val done = CompletableDeferred<Unit>()
        watch({ change ->
            states += change.changed.getValue("a").getValue("CalendarEvent")
            if (states.size == 2) done.complete(Unit)
        }, done)
        assertEquals(listOf("s1", "s2"), states)
        assertNull(fake.eventRequests[0].getHeader("Last-Event-ID"))
        assertEquals("41", fake.eventRequests[1].getHeader("Last-Event-ID"))
    }

    @Test
    fun aSilentStreamIsAbandonedByTheWatchdogAndReopened() {
        val connections = AtomicInteger()
        fake.events = {
            if (connections.getAndIncrement() == 0) {
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody("event: ping\ndata: {}\n\n")
                    .setBodyDelay(2, TimeUnit.SECONDS)
            } else {
                stream("event: state\ndata: ${change("s9")}\n\n")
            }
        }
        val done = CompletableDeferred<Unit>()
        watch({ done.complete(Unit) }, done)
        assertTrue(connections.get() >= 2)
    }

    @Test
    fun cancellingTheWatcherClosesAStreamThatIsBlockedInARead() = runBlocking {
        // A quiet server and a patient watchdog: only closing the connection can end the read.
        fake.events = {
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody("event: ping\ndata: {}\n\n")
                .setBodyDelay(4, TimeUnit.SECONDS)
        }
        fake.start()
        val client = JmapClient(fake.baseUrl, DavCredentials("ann@example.com", "secret"))
        client.connect()
        val patient = EventStreamTiming(watchdogMillis = { 60_000 })
        val job = launch(Dispatchers.Default) { client.watchStateChanges(listOf("CalendarEvent"), patient) { } }
        withTimeout(5_000) { while (fake.eventRequests.isEmpty()) kotlinx.coroutines.delay(20) }
        kotlinx.coroutines.delay(300)
        // Well inside the server's four-second silence, so only closing the call can explain it.
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }

    @Test
    fun aRejectedLoginEndsTheLoopWithoutRetrying() = runBlocking {
        fake.events = { MockResponse().setResponseCode(401) }
        fake.start()
        val client = JmapClient(fake.baseUrl, DavCredentials("ann@example.com", "secret"))
        client.connect()
        withTimeout(5_000) { client.watchStateChanges(listOf("*"), fast) { } }
        assertEquals(1, fake.eventRequests.size)
    }

    @Test
    fun aServerErrorBacksOffAndRetries() {
        val connections = AtomicInteger()
        fake.events = {
            when (connections.getAndIncrement()) {
                0 -> MockResponse().setResponseCode(503)
                1 -> MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html").setBody("<html>")
                else -> stream("event: state\ndata: ${change("s3")}\n\n")
            }
        }
        val done = CompletableDeferred<Unit>()
        watch({ done.complete(Unit) }, done)
        assertEquals(3, connections.get().coerceAtMost(3))
    }
}
