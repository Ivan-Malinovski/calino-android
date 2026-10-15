package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavAccountJson
import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.CardDavFetcher
import calino.malinov.ski.data.caldav.CardDavWriter
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DiscoveredAddressBook
import calino.malinov.ski.data.caldav.ProtocolContactFetcher
import calino.malinov.ski.data.caldav.ProtocolContactWriter
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.ProtocolCalendarFetcher
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalendarProtocol
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolSeamTest {
    private val account = CalDavAccount(
        id = "a", displayName = "A", serverUrl = "https://x", username = "u", calendars = emptyList(),
    )

    @Test
    fun accountProtocolSurvivesTheJsonRoundTrip() {
        val jmap = account.copy(protocol = CalendarProtocol.Jmap)
        assertEquals(CalendarProtocol.Jmap, CalDavAccountJson.decode(CalDavAccountJson.encode(listOf(jmap))).single().protocol)
    }

    @Test
    fun accountsSavedBeforeJmapDecodeAsCalDav() {
        val legacy = """[{"id":"a","displayName":"A","serverUrl":"https://x","username":"u"}]"""
        assertEquals(CalendarProtocol.CalDav, CalDavAccountJson.decode(legacy).single().protocol)
    }

    @Test
    fun jmapCalendarWithoutABackendFailsLoudlyInsteadOfSpeakingCalDav() {
        val calendar = DiscoveredCalendar(
            url = "https://x/.jmap/a/b/", displayName = "B", color = 0, readOnly = false,
            components = emptySet(), protocol = CalendarProtocol.Jmap,
        )
        val error = runCatching {
            runBlocking {
                ProtocolCalendarFetcher(CalDavFetcher())
                    .fetch(calendar, DavCredentials("u", "p"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))
            }
        }.exceptionOrNull()
        assertTrue(error is CalDavException && error.code == CalDavErrorCode.NotCalDav)
    }

    private val jmapBook = DiscoveredAddressBook(
        url = "https://x/.jmap/a/ab/b/", displayName = "B", protocol = CalendarProtocol.Jmap,
    )

    @Test
    fun jmapAddressBookWithoutABackendFailsLoudlyInsteadOfSpeakingCardDav() {
        val read = runCatching {
            runBlocking { ProtocolContactFetcher(CardDavFetcher()).fetch(jmapBook, DavCredentials("u", "p")) }
        }.exceptionOrNull()
        assertTrue(read is CalDavException && read.code == CalDavErrorCode.NotCalDav)
        val write = runCatching {
            runBlocking {
                val writer = ProtocolContactWriter(CardDavWriter())
                writer.refreshResource(jmapBook, DavCredentials("u", "p"), "https://x/.jmap/a/ab/b/c")
            }
        }.exceptionOrNull()
        assertTrue(write is CalDavException && write.code == CalDavErrorCode.NotCalDav)
    }
}
