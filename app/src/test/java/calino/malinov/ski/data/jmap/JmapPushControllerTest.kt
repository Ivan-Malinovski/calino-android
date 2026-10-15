package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalendarProtocol
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JmapPushControllerTest {
    private val fake = FakeJmapServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
        fake.stop()
    }

    private fun account(protocol: CalendarProtocol) = CalDavAccount(
        id = "acc", displayName = "x", serverUrl = fake.baseUrl, username = "ann@example.com",
        calendars = emptyList(), protocol = protocol,
    )

    private fun stream(change: String) =
        MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("event: state\ndata: $change\n\n")

    private fun controller(protocol: CalendarProtocol, synced: () -> Unit) = JmapPushController(
        scope = scope,
        accounts = { listOf(account(protocol)) },
        credentials = { DavCredentials("ann@example.com", "secret") },
        clients = JmapClientProvider(),
        requestSync = { synced() },
        timing = EventStreamTiming(initialRetryMillis = 50, maxBackoffMillis = 100, watchdogMillis = { 2_000 }),
        debounceMillis = 20,
    )

    @Test
    fun aCalendarChangeInTheForegroundRequestsASync() = runBlocking {
        fake.events = { stream("""{"@type":"StateChange","changed":{"a":{"CalendarEvent":"s2"}}}""") }
        fake.start()
        val synced = CompletableDeferred<Unit>()
        val push = controller(CalendarProtocol.Jmap) { synced.complete(Unit) }
        push.setForeground(true)
        withTimeout(10_000) { synced.await() }
        push.setForeground(false)
    }

    @Test
    fun otherAccountsAndTypesAreIgnored() = runBlocking {
        fake.events = { stream("""{"@type":"StateChange","changed":{"zzz":{"CalendarEvent":"s2"},"a":{"Email":"x"}}}""") }
        fake.start()
        val syncs = AtomicInteger()
        val push = controller(CalendarProtocol.Jmap) { syncs.incrementAndGet() }
        push.setForeground(true)
        delay(600)
        push.setForeground(false)
        assertEquals(0, syncs.get())
        assertTrue(fake.eventRequests.isNotEmpty())
    }

    @Test
    fun nothingIsOpenedInTheBackgroundOrForCalDavAccounts() = runBlocking {
        fake.events = { stream("""{"@type":"StateChange","changed":{"a":{"CalendarEvent":"s2"}}}""") }
        fake.start()
        val syncs = AtomicInteger()
        val caldav = controller(CalendarProtocol.CalDav) { syncs.incrementAndGet() }
        caldav.setForeground(true)
        val hidden = controller(CalendarProtocol.Jmap) { syncs.incrementAndGet() }
        hidden.reconcile()
        delay(400)
        assertEquals(0, fake.eventRequests.size)
        assertEquals(0, syncs.get())
    }

    @Test
    fun leavingTheForegroundClosesTheStream() = runBlocking {
        fake.events = { MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": hi\n\n").setBodyDelay(1, java.util.concurrent.TimeUnit.SECONDS) }
        fake.start()
        val push = controller(CalendarProtocol.Jmap) { }
        push.setForeground(true)
        withTimeout(5_000) { while (fake.eventRequests.isEmpty()) delay(20) }
        push.setForeground(false)
        delay(400)
        val seen = fake.eventRequests.size
        delay(400)
        assertEquals(seen, fake.eventRequests.size)
    }
}
