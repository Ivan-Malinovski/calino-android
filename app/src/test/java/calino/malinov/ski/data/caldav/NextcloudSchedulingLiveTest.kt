package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.EventOrganizer
import calino.malinov.ski.data.model.RecurrenceEditScope
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Two-user scheduling interop; runs only against a throwaway Nextcloud instance. */
class NextcloudSchedulingLiveTest {
    @Test fun `organizer PUT delivers invitation and attendee PUT replies`() = runBlocking {
        val url = System.getenv("CALINO_NEXTCLOUD_URL")
        val aliceUser = System.getenv("CALINO_NEXTCLOUD_ALICE_USER")
        val alicePass = System.getenv("CALINO_NEXTCLOUD_ALICE_PASS")
        val bobUser = System.getenv("CALINO_NEXTCLOUD_BOB_USER")
        val bobPass = System.getenv("CALINO_NEXTCLOUD_BOB_PASS")
        assumeTrue(listOf(url, aliceUser, alicePass, bobUser, bobPass).all { !it.isNullOrBlank() })
        val alice = DavCredentials(aliceUser!!, alicePass!!)
        val bob = DavCredentials(bobUser!!, bobPass!!)
        val discovery = CalDavDiscovery()
        val a = discovery.discoverAccount(url!!, alice)
        val b = discovery.discoverAccount(url, bob)
        assertTrue(a.autoSchedule && b.autoSchedule)
        val aliceAddress = a.calendarUserAddresses.single { it.startsWith("mailto:") }.substringAfter(':')
        val bobAddress = b.calendarUserAddresses.single { it.startsWith("mailto:") }.substringAfter(':')
        val aliceCalendar = a.calendars.first { !it.readOnly && "VEVENT" in it.components }
        val bobCalendar = b.calendars.first { !it.readOnly && "VEVENT" in it.components }
        val uid = "calino-rsvp-${UUID.randomUUID()}"
        val windowStart = LocalDate.of(2026, 10, 1)
        val windowEnd = LocalDate.of(2026, 11, 1)
        val http = DavHttp()
        val fetcher = CalDavFetcher(http)
        val cache = FileCalendarCache(Files.createTempDirectory("calino-rsvp-nextcloud").toFile())
        val writer = CalDavWriter(http = http, cache = cache)
        val invite = CalEvent(
            id = uid, uid = uid, title = "Calino RSVP live test", color = 1,
            start = LocalDateTime.of(2026, 10, 5, 10, 0), durationMinutes = 60,
            calendarId = aliceCalendar.url,
            organizer = EventOrganizer("Alice", aliceAddress),
            attendees = listOf(Attendee("Bob", bobAddress)),
        )
        val created = writer.putEvent(aliceCalendar, alice, invite)
        var createdRecurring: WrittenCalendarResource? = null
        try {
            var bobResource: CalendarResource? = null
            for (attempt in 0 until 12) {
                bobResource = fetcher.fetchAllEvents(bobCalendar, bob)
                    .firstOrNull { it.ics.contains("UID:$uid") }
                if (bobResource != null) break
                delay(500)
            }
            val copy = bobResource
            assertNotNull("Nextcloud did not deliver the invitation", copy)
            var expected = "NEEDS-ACTION"
            for (response in listOf("ACCEPTED", "TENTATIVE", "DECLINED")) {
                val raw = fetcher.fetchAllEvents(bobCalendar, bob)
                    .single { it.ics.contains("UID:$uid") }
                cache.save(CachedCalendar(bobCalendar.url, Instant.now(), windowStart, windowEnd, listOf(raw)))
                val mapped = ICalMapper().parse(raw.ics, bobCalendar.url, 1, raw.href, raw.etag).events.single()
                assertEquals(expected, mapped.attendees.single { it.email == bobAddress }.participationStatus)
                val prepared = writer.prepareRsvp(
                    bobCalendar, mapped, bobAddress, response, expected, RecurrenceEditScope.All,
                )
                writer.putPrepared(bobCalendar, bob, prepared)
                var organizerStatus: String? = null
                for (attempt in 0 until 12) {
                    val refreshed = fetcher.fetchAllEvents(aliceCalendar, alice)
                        .firstOrNull { it.ics.contains("UID:$uid") }
                    organizerStatus = refreshed?.let {
                        ICalMapper().parse(it.ics, aliceCalendar.url, 1, it.href, it.etag)
                            .events.single().attendees.single { attendee -> attendee.email == bobAddress }.participationStatus
                    }
                    if (organizerStatus == response) break
                    delay(500)
                }
                assertEquals(response, organizerStatus)
                expected = response
            }
            val recurringUid = "calino-rsvp-series-${UUID.randomUUID()}"
            createdRecurring = writer.putEvent(
                aliceCalendar, alice,
                invite.copy(id = recurringUid, uid = recurringUid, recurrence = "FREQ=DAILY;COUNT=3"),
            )
            var recurringCopy: CalendarResource? = null
            for (attempt in 0 until 12) {
                recurringCopy = fetcher.fetchAllEvents(bobCalendar, bob)
                    .firstOrNull { it.ics.contains("UID:$recurringUid") }
                if (recurringCopy != null) break
                delay(500)
            }
            assertNotNull("Nextcloud did not deliver the recurring invitation", recurringCopy)
            val seriesRaw = recurringCopy!!
            cache.save(CachedCalendar(bobCalendar.url, Instant.now(), windowStart, windowEnd, listOf(seriesRaw)))
            val occurrence = ICalMapper().parse(
                seriesRaw.ics, bobCalendar.url, 1, seriesRaw.href, seriesRaw.etag,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7),
            ).events.single { it.start?.toLocalDate() == LocalDate.of(2026, 10, 6) }
            val oneReply = writer.prepareRsvp(
                bobCalendar, occurrence, bobAddress, "ACCEPTED", "NEEDS-ACTION", RecurrenceEditScope.This,
            )
            writer.putPrepared(bobCalendar, bob, oneReply)
            var organizerInstances: List<CalEvent> = emptyList()
            for (attempt in 0 until 12) {
                val refreshed = fetcher.fetchAllEvents(aliceCalendar, alice)
                    .firstOrNull { it.ics.contains("UID:$recurringUid") }
                organizerInstances = refreshed?.let {
                    ICalMapper().parse(it.ics, aliceCalendar.url, 1, it.href, it.etag,
                        LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)).events
                }.orEmpty()
                if (organizerInstances.any {
                    it.start?.toLocalDate() == LocalDate.of(2026, 10, 6) &&
                        it.attendees.single { attendee -> attendee.email == bobAddress }.participationStatus == "ACCEPTED"
                }) break
                delay(500)
            }
            assertEquals("NEEDS-ACTION", organizerInstances.single { it.start?.toLocalDate() == LocalDate.of(2026, 10, 5) }
                .attendees.single { it.email == bobAddress }.participationStatus)
            assertEquals("ACCEPTED", organizerInstances.single { it.start?.toLocalDate() == LocalDate.of(2026, 10, 6) }
                .attendees.single { it.email == bobAddress }.participationStatus)
        } finally {
            http.request("DELETE", created.href, alice)
            createdRecurring?.let { http.request("DELETE", it.href, alice) }
        }
    }
}
