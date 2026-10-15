package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavWriter
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavPrecondition
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.PreparedCalendarWrite
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Two throwaway users on one real Stalwart: invitations, replies, cancellations and
 * the event stream. Skipped unless `CALINO_JMAP_*` (including `_USER2`/`_PASS2`) is set;
 * see [JmapLiveTest] and `scripts/live-jmap/README.md`.
 */
class JmapLiveSchedulingTest {
    private val url: String? = System.getenv("CALINO_JMAP_URL")
    private val aliceName: String? = System.getenv("CALINO_JMAP_USER")
    private val alicePass: String? = System.getenv("CALINO_JMAP_PASS")
    private val bobName: String? = System.getenv("CALINO_JMAP_USER2")
    private val bobPass: String? = System.getenv("CALINO_JMAP_PASS2")

    private lateinit var alice: DavCredentials
    private lateinit var bob: DavCredentials
    private val clients = JmapClientProvider()
    private val fetcher = JmapCalendarFetcher(clients)
    private val writer = JmapCalendarWriter(clients, fetcher, CalDavWriter(cache = CalendarCache.None), CalendarCache.None)

    @Before
    fun requireServer() {
        assumeTrue(
            "Set CALINO_JMAP_URL / _USER / _PASS / _USER2 / _PASS2 (scripts/live-jmap).",
            listOf(url, aliceName, alicePass, bobName, bobPass).none { it.isNullOrBlank() },
        )
        alice = DavCredentials(aliceName!!, alicePass!!)
        bob = DavCredentials(bobName!!, bobPass!!)
    }

    private fun ics(uid: String, summary: String, bobStatus: String = "NEEDS-ACTION", sequence: Int = 0) = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Calino//live//EN
        BEGIN:VEVENT
        UID:$uid
        DTSTAMP:20261015T100000Z
        DTSTART:20261021T100000Z
        DTEND:20261021T110000Z
        SEQUENCE:$sequence
        SUMMARY:$summary
        ORGANIZER;CN=Alice:mailto:$aliceName
        ATTENDEE;CN=Alice;PARTSTAT=ACCEPTED;ROLE=CHAIR:mailto:$aliceName
        ATTENDEE;CN=Bob;PARTSTAT=$bobStatus;RSVP=TRUE:mailto:$bobName
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n") + "\r\n"

    private fun String.unfolded() = replace("\r\n ", "").replace("\n ", "")

    private suspend fun writableCalendar(credentials: DavCredentials): DiscoveredCalendar =
        JmapDiscovery().discoverAccount(url!!, credentials).calendars.first { !it.readOnly }

    private suspend fun eventsOf(calendar: DiscoveredCalendar, credentials: DavCredentials, uid: String) =
        fetcher.fetch(calendar, credentials, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))
            .resources.filter { it.ics.contains("UID:$uid") }

    /** Delivery between local users is asynchronous, so wait for [check] instead of sleeping. */
    private suspend fun <T> eventually(what: String, check: suspend () -> T?): T {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (true) {
            check()?.let { return it }
            if (System.nanoTime() > deadline) throw AssertionError("Timed out waiting for $what")
            delay(500)
        }
    }

    @Test
    fun anInvitationIsDeliveredRepliedToAndCancelled() = runBlocking {
        val aliceCalendar = writableCalendar(alice)
        val bobCalendar = writableCalendar(bob)
        val uid = "calino-live-sched-${UUID.randomUUID()}"

        val created = writer.putPrepared(
            aliceCalendar, alice,
            PreparedCalendarWrite(aliceCalendar.url + "$uid.ics", ics(uid, "Sync with Bob"), DavPrecondition.New, null),
        )
        try {
            // 1. Bob receives the invitation as an event of his own, still awaiting his answer.
            val invited = eventually("Bob's copy of the invitation") { eventsOf(bobCalendar, bob, uid).firstOrNull() }
            println("BOB COPY:\n${invited.ics}")
            assertTrue(invited.ics.contains("SUMMARY:Sync with Bob"))
            assertTrue(invited.ics.unfolded().contains("mailto:$bobName", ignoreCase = true))

            // 2. Bob accepts; the answer reaches Alice's copy of the event.
            val accepted = invited.ics.replace("PARTSTAT=NEEDS-ACTION", "PARTSTAT=ACCEPTED")
            assertTrue("the invitation carries Bob's attendee line", accepted != invited.ics)
            writer.putPrepared(
                bobCalendar, bob,
                PreparedCalendarWrite(invited.href, accepted, DavPrecondition.Match(requireNotNull(invited.etag)), invited.etag),
            )
            val answered = eventually("Alice to see Bob's reply") {
                eventsOf(aliceCalendar, alice, uid).firstOrNull { copy ->
                    Regex("""ATTENDEE[^\r\n]*PARTSTAT=ACCEPTED[^\r\n]*mailto:$bobName""", RegexOption.IGNORE_CASE).containsMatchIn(copy.ics.unfolded())
                }
            }
            println("ALICE COPY AFTER REPLY:\n${answered.ics}")

            // 3. Alice cancels; Bob's copy goes away or is marked cancelled.
            val current = writer.refreshResource(aliceCalendar, alice, created.href, uid)
            writer.delete(aliceCalendar, alice, current.href, uid, current.etag)
            val cancelled = eventually("Bob's copy to be cancelled or removed") {
                val copies = eventsOf(bobCalendar, bob, uid)
                if (copies.isEmpty() || copies.all { it.ics.contains("STATUS:CANCELLED") }) copies else null
            }
            println("BOB AFTER CANCEL: ${if (cancelled.isEmpty()) "removed" else "marked cancelled"}")
        } finally {
            // Leave nothing behind for either user.
            for ((calendar, credentials) in listOf(aliceCalendar to alice, bobCalendar to bob)) {
                eventsOf(calendar, credentials, uid).forEach {
                    runCatching { writer.delete(calendar, credentials, it.href, uid, it.etag) }
                }
            }
        }
    }

    @Test
    fun aReplyFromAnotherUserWakesTheOrganisersStream() = runBlocking {
        val aliceCalendar = writableCalendar(alice)
        val bobCalendar = writableCalendar(bob)
        val uid = "calino-live-reply-${UUID.randomUUID()}"
        val created = writer.putPrepared(
            aliceCalendar, alice,
            PreparedCalendarWrite(aliceCalendar.url + "$uid.ics", ics(uid, "Reply wakes stream"), DavPrecondition.New, null),
        )
        val changed = CompletableDeferred<JmapStateChange>()
        val armed = java.util.concurrent.atomic.AtomicBoolean(false)
        val job = launch(Dispatchers.IO) {
            clients.client(aliceCalendar.url, alice).watchStateChanges(listOf("CalendarEvent")) { change ->
                // Only a change after Bob is about to reply counts, not noise from setting up.
                if (armed.get() && change.touches("CalendarEvent")) changed.complete(change)
            }
        }
        try {
            val invited = eventually("Bob's copy of the invitation") { eventsOf(bobCalendar, bob, uid).firstOrNull() }
            delay(2_000) // let Alice's stream connect and settle
            armed.set(true)
            val replied = JmapCalendarWriter(JmapClientProvider(), JmapCalendarFetcher(JmapClientProvider()), CalDavWriter(cache = CalendarCache.None), CalendarCache.None)
            replied.putPrepared(
                bobCalendar, bob,
                PreparedCalendarWrite(invited.href, invited.ics.replace("PARTSTAT=NEEDS-ACTION", "PARTSTAT=ACCEPTED"), DavPrecondition.Match(requireNotNull(invited.etag)), invited.etag),
            )
            eventually("Alice to see Bob's reply") {
                eventsOf(aliceCalendar, alice, uid).firstOrNull { it.ics.unfolded().contains("PARTSTAT=ACCEPTED;RSVP=TRUE:mailto:$bobName") }
            }
            withTimeout(15_000) { changed.await() }
            println("REPLY WOKE STREAM: ${changed.getCompleted().changed}")
        } finally {
            withTimeout(10_000) { job.cancelAndJoin() }
            for ((calendar, credentials) in listOf(aliceCalendar to alice, bobCalendar to bob)) {
                eventsOf(calendar, credentials, uid).forEach {
                    runCatching { writer.delete(calendar, credentials, it.href, uid, it.etag) }
                }
            }
        }
        assertTrue(created.href.isNotEmpty())
    }

    @Test
    fun theEventStreamReportsAChangeMadeByAnotherClient() = runBlocking {
        val calendar = writableCalendar(alice)
        val watcher = clients.client(calendar.url, alice)
        val changed = CompletableDeferred<JmapStateChange>()
        val job = launch(Dispatchers.IO) {
            watcher.watchStateChanges(listOf("CalendarEvent")) { change ->
                if (change.touches("CalendarEvent")) changed.complete(change)
            }
        }
        val uid = "calino-live-stream-${UUID.randomUUID()}"
        val plain = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Calino//live//EN
            BEGIN:VEVENT
            UID:$uid
            DTSTAMP:20261015T100000Z
            DTSTART:20261022T100000Z
            DTEND:20261022T110000Z
            SUMMARY:Stream probe
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
        try {
            // Give the stream a moment to connect, then change data through a second client.
            delay(2_000)
            val otherClient = JmapCalendarWriter(JmapClientProvider(), JmapCalendarFetcher(JmapClientProvider()), CalDavWriter(cache = CalendarCache.None), CalendarCache.None)
            val created = otherClient.putPrepared(calendar, alice, PreparedCalendarWrite(calendar.url + "$uid.ics", plain, DavPrecondition.New, null))
            try {
                val change = withTimeout(15_000) { changed.await() }
                println("STREAM CHANGE: ${change.changed}")
                assertTrue(change.touches("CalendarEvent"))
            } finally {
                val current = otherClient.refreshResource(calendar, alice, created.href, uid)
                otherClient.delete(calendar, alice, current.href, uid, current.etag)
            }
        } finally {
            // Cancelling the watcher must release its connection promptly, not at the next server ping.
            withTimeout(10_000) { job.cancelAndJoin() }
        }
        assertEquals(0, eventsOf(calendar, alice, uid).size)
    }
}
