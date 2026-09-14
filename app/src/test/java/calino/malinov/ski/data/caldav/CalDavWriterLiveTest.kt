package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.JournalEntry
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in end-to-end calendar write coverage against the throwaway Radicale account. */
class CalDavWriterLiveTest {

    @Test
    fun `Radicale accepts conditional event task and journal round trips`() = runBlocking {
        val url = System.getenv("CALINO_CALDAV_URL")
        val user = System.getenv("CALINO_CALDAV_USER")
        val password = System.getenv("CALINO_CALDAV_PASS")
        assumeTrue(
            "Set CALINO_CALDAV_URL / _USER / _PASS to run the live write test.",
            !url.isNullOrBlank() && !user.isNullOrBlank() && !password.isNullOrBlank(),
        )

        val credentials = DavCredentials(user!!, password!!)
        val discovered = CalDavDiscovery().discoverAccount(url!!, credentials)
        val writable = discovered.calendars.firstOrNull { !it.readOnly }
        assumeTrue("The live account has no writable calendar.", writable != null)
        val home = discovered.homeSetUrl.trimEnd('/')
        val scratchUrl = "$home/calino-write-${UUID.randomUUID()}/"
        val http = DavHttp()
        var collectionCreated = false
        val cache = FileCalendarCache(Files.createTempDirectory("calino-caldav-write").toFile())
        val calendar = DiscoveredCalendar(
            url = scratchUrl,
            displayName = "Calino live write probe",
            color = 0xFF11A602,
            readOnly = false,
            components = setOf("VEVENT", "VTODO", "VJOURNAL"),
        )
        cache.save(
            CachedCalendar(
                calendarUrl = scratchUrl,
                fetchedAt = Instant.now(),
                windowStart = LocalDate.now().minusMonths(1),
                windowEnd = LocalDate.now().plusMonths(1),
                resources = emptyList(),
            ),
        )
        val writer = CalDavWriter(http = http, cache = cache)
        val suffix = UUID.randomUUID().toString()
        var event: WrittenCalendarResource? = null
        var task: WrittenCalendarResource? = null
        var journal: WrittenCalendarResource? = null

        try {
            val makeCalendar = http.request(
                method = "MKCALENDAR",
                url = scratchUrl,
                credentials = credentials,
                contentType = DavHttp.XmlMediaType,
                body = """<?xml version="1.0" encoding="UTF-8"?><c:mkcalendar xmlns:c="urn:ietf:params:xml:ns:caldav"/>""",
            )
            assertTrue("MKCALENDAR failed: ${makeCalendar.status}", makeCalendar.status in 200..299)
            collectionCreated = true

            val eventUid = "calino-live-event-$suffix"
            event = writer.putEvent(
                calendar,
                credentials,
                CalEvent(
                    id = eventUid,
                    uid = eventUid,
                    title = "Calino live event",
                    color = 0L,
                    start = LocalDateTime.of(2030, 1, 2, 10, 0),
                    durationMinutes = 45,
                    calendarId = scratchUrl,
                ),
            )
            assertEquals(eventUid, ICalMapper().mapAll(
                listOf(CalendarResource(event!!.href, event!!.etag, event!!.ics)),
                scratchUrl,
                0L,
                LocalDate.of(2029, 12, 1),
                LocalDate.of(2030, 2, 1),
            ).events.single().uid)

            val updatedEvent = writer.putEvent(
                calendar,
                credentials,
                CalEvent(
                    id = eventUid,
                    uid = eventUid,
                    title = "Calino live event updated",
                    color = 0L,
                    start = LocalDateTime.of(2030, 1, 2, 11, 0),
                    durationMinutes = 60,
                    calendarId = scratchUrl,
                    href = event!!.href,
                    etag = event!!.etag,
                ),
            )
            assertTrue(updatedEvent.ics.contains("SUMMARY:Calino live event updated"))
            event = updatedEvent

            val taskUid = "calino-live-task-$suffix"
            task = writer.putTask(
                calendar,
                credentials,
                CalTask(
                    id = taskUid,
                    uid = taskUid,
                    title = "Calino live task",
                    color = 0L,
                    due = LocalDate.of(2030, 1, 3),
                ),
            )
            assertTrue(task!!.ics.contains("UID:$taskUid"))

            val journalUid = "calino-live-journal-$suffix"
            journal = writer.putJournal(
                calendar,
                credentials,
                JournalEntry(
                    id = journalUid,
                    uid = journalUid,
                    date = LocalDate.of(2030, 1, 4),
                    title = "Calino live journal",
                    body = "Round trip",
                ),
            )
            assertTrue(journal!!.ics.contains("UID:$journalUid"))

            writer.delete(calendar, credentials, event!!.href, eventUid, event!!.etag)
            writer.delete(calendar, credentials, task!!.href, taskUid, task!!.etag)
            writer.delete(calendar, credentials, journal!!.href, journalUid, journal!!.etag)
            assertTrue(cache.load(scratchUrl)?.resources?.none { it.href == event!!.href } == true)
            event = null
            task = null
            journal = null
        } finally {
            runCatching {
                event?.let { writer.delete(calendar, credentials, it.href, "calino-live-event-$suffix", it.etag) }
                task?.let { writer.delete(calendar, credentials, it.href, "calino-live-task-$suffix", it.etag) }
                journal?.let { writer.delete(calendar, credentials, it.href, "calino-live-journal-$suffix", it.etag) }
            }
            if (collectionCreated) {
                runCatching { http.delete(scratchUrl, credentials, DavPrecondition.Unconditional) }
            }
        }
    }
}
