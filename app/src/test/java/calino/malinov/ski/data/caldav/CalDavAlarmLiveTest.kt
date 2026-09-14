package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.Reminder
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The live half of the VALARM round trip.
 *
 * The unit tests prove the mapping against fixtures this repository wrote
 * itself, which is exactly the weakness: the reader's ownership predicate has
 * never seen bytes from a client that was not us. This test plants a resource
 * shaped the way Thunderbird and friends actually emit one -- a display alarm
 * Calino can own, sitting beside an email alarm it must not touch -- and drives
 * a real edit through a real server against it.
 *
 * It works inside a throwaway collection it creates and removes, like
 * [CalDavWriterLiveTest]; it never writes to a real calendar.
 */
class CalDavAlarmLiveTest {

    @Test
    fun `a foreign alarm survives a Calino edit on a real server`() = runBlocking {
        val url = System.getenv("CALINO_CALDAV_URL")
        val user = System.getenv("CALINO_CALDAV_USER")
        val password = System.getenv("CALINO_CALDAV_PASS")
        assumeTrue(
            "Set CALINO_CALDAV_URL / _USER / _PASS to run the live alarm test.",
            !url.isNullOrBlank() && !user.isNullOrBlank() && !password.isNullOrBlank(),
        )

        val credentials = DavCredentials(user!!, password!!)
        val discovered = CalDavDiscovery().discoverAccount(url!!, credentials)
        assumeTrue("The live account has no writable calendar.", discovered.calendars.any { !it.readOnly })
        val home = discovered.homeSetUrl.trimEnd('/')
        val scratchUrl = "$home/calino-alarm-${UUID.randomUUID()}/"
        val http = DavHttp()
        val cache = FileCalendarCache(Files.createTempDirectory("calino-caldav-alarm").toFile())
        val calendar = DiscoveredCalendar(
            url = scratchUrl,
            displayName = "Calino live alarm probe",
            color = 0xFF11A602,
            readOnly = false,
            components = setOf("VEVENT"),
        )
        val windowStart = LocalDate.of(2029, 12, 1)
        val windowEnd = LocalDate.of(2030, 2, 1)
        cache.save(
            CachedCalendar(
                calendarUrl = scratchUrl,
                fetchedAt = Instant.now(),
                windowStart = windowStart,
                windowEnd = windowEnd,
                resources = emptyList(),
            ),
        )
        val writer = CalDavWriter(http = http, cache = cache)
        val fetcher = CalDavFetcher(http)
        val mapper = ICalMapper()
        val uid = "calino-live-alarm-${UUID.randomUUID()}"
        val href = "$scratchUrl$uid.ics"
        var collectionCreated = false

        // Fetch *and* populate the raw cache, because that is what the app does
        // and the writer patches from the cache. Skipping this step is not a
        // shortcut: the writer falls back to rebuilding the resource from the
        // model, which is the one path that legitimately drops another client's
        // properties -- and the test would then be measuring the fallback
        // rather than the patch.
        suspend fun sync(): List<CalendarResource> {
            val resources = fetcher.fetch(calendar, credentials, windowStart, windowEnd).resources
            cache.save(
                CachedCalendar(
                    calendarUrl = scratchUrl,
                    fetchedAt = Instant.now(),
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    resources = resources,
                ),
            )
            return resources
        }

        suspend fun readBack() =
            mapper.mapAll(sync(), scratchUrl, 0L, windowStart, windowEnd).events.single { it.uid == uid }

        suspend fun serverText(): String = sync().single { it.href.endsWith("$uid.ics") }.ics

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

            // A resource as another client would leave it: one alarm Calino can
            // state, one it cannot, plus properties it does not model.
            val foreign = listOf(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Another Client//EN",
                "BEGIN:VEVENT",
                "UID:$uid",
                "DTSTAMP:20291201T000000Z",
                "DTSTART:20300102T100000Z",
                "DTEND:20300102T104500Z",
                "SUMMARY:Planted by another client",
                "ORGANIZER;CN=Boss:mailto:boss@example.com",
                "X-CUSTOM-THING;X-PARAM=7:preserve this",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "TRIGGER:-PT15M",
                "DESCRIPTION:their wording",
                "END:VALARM",
                "BEGIN:VALARM",
                "ACTION:EMAIL",
                "TRIGGER;RELATED=END:-PT45M",
                "DESCRIPTION:body",
                "SUMMARY:subject",
                "ATTENDEE:mailto:ada@example.com",
                "REPEAT:2",
                "DURATION:PT5M",
                "END:VALARM",
                "END:VEVENT",
                "END:VCALENDAR",
            ).joinToString("\r\n") + "\r\n"

            val planted = http.put(href, credentials, foreign, DavHttp.CalendarMediaType, DavPrecondition.New)
            assertTrue("PUT failed: ${planted.status}", planted.status in 200..299)

            // Direction 1: another client -> Calino.
            val read = readBack()
            assertEquals(listOf(Reminder(15)), read.reminders)

            // Direction 2: Calino -> another client. An edit that does not touch
            // the reminders must leave both alarms exactly as they were.
            val retitled = writer.putEvent(
                calendar,
                credentials,
                read.copy(title = "Edited by Calino"),
            )
            assertTrue(retitled.ics.contains("SUMMARY:Edited by Calino"))
            serverText().let { text ->
                assertTrue(text, text.contains("ACTION:EMAIL"))
                assertTrue(text, text.contains("REPEAT:2"))
                assertTrue(text, text.contains("ATTENDEE:mailto:ada@example.com"))
                assertTrue(text, text.contains("X-CUSTOM-THING;X-PARAM=7:preserve this"))
                // Our own alarm was not rebuilt, so their wording stands.
                assertTrue(text, text.contains("TRIGGER:-PT15M"))
                assertTrue(text, text.contains("DESCRIPTION:their wording"))
                assertEquals(text, 2, text.split("BEGIN:VALARM").size - 1)
            }

            // Changing the reminder rewrites ours and still not theirs.
            writer.putEvent(
                calendar,
                credentials,
                readBack().copy(reminders = listOf(Reminder(90))),
            )
            serverText().let { text ->
                // Asserted through the reader rather than on trigger text:
                // biweekly renders 90 minutes as "-PT1H30M", which is the same
                // duration and not something worth pinning a string to.
                assertFalse(text, text.contains("TRIGGER:-PT15M"))
                assertTrue(text, text.contains("ACTION:EMAIL"))
                assertEquals(text, 2, text.split("BEGIN:VALARM").size - 1)
            }
            assertEquals(listOf(Reminder(90)), readBack().reminders)

            // Clearing removes only ours.
            writer.putEvent(calendar, credentials, readBack().copy(reminders = emptyList()))
            serverText().let { text ->
                assertFalse(text, text.contains("ACTION:DISPLAY"))
                assertTrue(text, text.contains("ACTION:EMAIL"))
                assertEquals(text, 1, text.split("BEGIN:VALARM").size - 1)
            }
            assertTrue(readBack().reminders.isEmpty())
        } finally {
            runCatching { http.delete(href, credentials, DavPrecondition.Unconditional) }
            if (collectionCreated) {
                runCatching { http.delete(scratchUrl, credentials, DavPrecondition.Unconditional) }
            }
        }
    }
}
