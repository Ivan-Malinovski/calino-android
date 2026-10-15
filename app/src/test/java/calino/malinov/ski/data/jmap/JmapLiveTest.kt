package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavWriter
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CardDavWriter
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavPrecondition
import calino.malinov.ski.data.caldav.PreparedCalendarWrite
import calino.malinov.ski.data.caldav.PreparedCardWrite
import calino.malinov.ski.data.model.CalendarProtocol
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The full event and contact lifecycle against a real Stalwart.
 *
 * Skipped unless the variables are set, so the normal suite stays offline. Start the
 * throwaway server and export them (credentials are local-only and never committed):
 *
 * ```
 * scripts/live-jmap/jmap-server.sh up
 * eval "$(scripts/live-jmap/jmap-server.sh env)"
 * ./gradlew :app:testDebugUnitTest --tests "*JmapLiveTest*"
 * ```
 */
class JmapLiveTest {
    private val url: String? = System.getenv("CALINO_JMAP_URL")
    private val user: String? = System.getenv("CALINO_JMAP_USER")
    private val pass: String? = System.getenv("CALINO_JMAP_PASS")

    private lateinit var credentials: DavCredentials
    private val clients = JmapClientProvider()
    private val calendarFetcher = JmapCalendarFetcher(clients)
    private val calendarWriter = JmapCalendarWriter(clients, calendarFetcher, CalDavWriter(cache = CalendarCache.None), CalendarCache.None)
    private val contactFetcher = JmapContactsFetcher(clients)
    private val contactWriter = JmapContactsWriter(clients, contactFetcher, CardDavWriter(cache = CalendarCache.None), CalendarCache.None)

    @Before
    fun requireServer() {
        assumeTrue(
            "Set CALINO_JMAP_URL / _USER / _PASS (scripts/live-jmap) to run the live JMAP test.",
            !url.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank(),
        )
        credentials = DavCredentials(user!!, pass!!)
    }

    @Test
    fun anEventIsCreatedReadEditedAndDeleted() = runBlocking {
        val account = JmapDiscovery().discoverAccount(url!!, credentials)
        val calendar = account.calendars.first { !it.readOnly }
        assertEquals(CalendarProtocol.Jmap, calendar.protocol)
        val uid = "calino-live-${UUID.randomUUID()}"
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Calino//live//EN
            BEGIN:VEVENT
            UID:$uid
            DTSTAMP:20261015T100000Z
            DTSTART:20261020T100000Z
            DTEND:20261020T110000Z
            SUMMARY:Live lifecycle
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n") + "\r\n"

        val created = calendarWriter.putPrepared(calendar, credentials, PreparedCalendarWrite(calendar.url + "$uid.ics", ics, DavPrecondition.New, null))
        try {
            assertTrue(created.ics.contains("SUMMARY:Live lifecycle"))
            // Retrying a create whose answer was lost must not duplicate the event.
            val again = calendarWriter.putPrepared(calendar, credentials, PreparedCalendarWrite(calendar.url + "$uid.ics", ics, DavPrecondition.New, null))
            assertEquals(created.href, again.href)
            val listed = calendarFetcher.fetch(calendar, credentials, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))
            assertEquals(1, listed.resources.count { it.ics.contains("UID:$uid") })

            val edited = calendarWriter.putPrepared(
                calendar, credentials,
                PreparedCalendarWrite(created.href, created.ics.replace("Live lifecycle", "Live edited"), DavPrecondition.Match(requireNotNull(created.etag)), created.etag),
            )
            assertTrue(edited.ics.contains("SUMMARY:Live edited"))
            assertFalse(edited.etag == created.etag)
        } finally {
            val current = calendarWriter.refreshResource(calendar, credentials, created.href, uid)
            calendarWriter.delete(calendar, credentials, current.href, uid, current.etag)
        }
        val after = calendarFetcher.fetch(calendar, credentials, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))
        assertTrue(after.resources.none { it.ics.contains("UID:$uid") })
    }

    @Test
    fun aContactIsCreatedReadEditedAndDeleted() = runBlocking {
        val account = JmapContactsDiscovery().discoverAccount(url!!, credentials)
        assumeTrue("The server advertises no contacts account.", account.addressBooks.isNotEmpty())
        val book = account.addressBooks.first { !it.readOnly }
        val uid = "calino-live-${UUID.randomUUID()}"
        val vcard = "BEGIN:VCARD\r\nVERSION:4.0\r\nUID:$uid\r\nFN:Live Person\r\nN:Person;Live;;;\r\nEMAIL:live@example.org\r\nTEL:+44 20 7946 0000\r\nEND:VCARD\r\n"

        val created = contactWriter.putPrepared(book, credentials, PreparedCardWrite(book.url + "$uid.vcf", vcard, DavPrecondition.New, null))
        try {
            assertTrue(created.vcf.contains("FN:Live Person"))
            val again = contactWriter.putPrepared(book, credentials, PreparedCardWrite(book.url + "$uid.vcf", vcard, DavPrecondition.New, null))
            assertEquals(created.href, again.href)
            val listed = contactFetcher.fetch(book, credentials)
            assertEquals(1, listed.resources.count { it.vcf.contains("UID:$uid") })

            val edited = contactWriter.putPrepared(
                book, credentials,
                PreparedCardWrite(created.href, created.vcf.replace("Live Person", "Live Edited"), DavPrecondition.Match(requireNotNull(created.etag)), created.etag),
            )
            assertTrue(edited.vcf.contains("FN:Live Edited"))
            assertTrue("untouched properties survive", edited.vcf.contains("live@example.org"))
        } finally {
            val current = contactWriter.refreshResource(book, credentials, created.href)
            contactWriter.delete(book, credentials, current.href, current.etag)
        }
        assertTrue(contactFetcher.fetch(book, credentials).resources.none { it.vcf.contains("UID:$uid") })
    }
}
