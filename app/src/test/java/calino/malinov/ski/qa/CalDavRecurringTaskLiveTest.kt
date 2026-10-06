package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CalDavDiscovery
import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DavPrecondition
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.FileCalendarCache
import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalDavSource
import calino.malinov.ski.data.repository.WriteResult
import calino.malinov.ski.data.repository.asUpdate
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Deleting and re-ruling a repeating task through the real [CalDavRepository]
 * against a real server (see scripts/live-caldav). Each case plants a weekly
 * VTODO written by "another client", with a property Calino does not model,
 * in a throwaway collection that is removed afterwards.
 */
class CalDavRecurringTaskLiveTest {

    private val today = LocalDate.of(2026, 3, 1)

    private class Harness(
        val repository: CalDavRepository,
        val http: DavHttp,
        val credentials: DavCredentials,
        val calendar: DiscoveredCalendar,
        val href: String,
    ) {
        suspend fun server(): String = http.request("GET", href, credentials).let { if (it.status == 404) "" else it.body }
        suspend fun sync() { repository.synchronize() }
        fun tasks(): List<CalTask> = repository.snapshot().tasks.filter { it.uid == UID }
        fun occurrence(date: LocalDate): CalTask = tasks().single { it.due == date }
    }

    private fun withSeries(rrule: String = "FREQ=WEEKLY;BYDAY=TU", block: suspend Harness.() -> Unit) = runBlocking {
        val url = System.getenv("CALINO_CALDAV_URL")
        val user = System.getenv("CALINO_CALDAV_USER")
        val password = System.getenv("CALINO_CALDAV_PASS")
        assumeTrue(
            "Set CALINO_CALDAV_URL / _USER / _PASS to run the live recurring-task test.",
            !url.isNullOrBlank() && !user.isNullOrBlank() && !password.isNullOrBlank(),
        )
        val credentials = DavCredentials(user!!, password!!)
        val discovered = CalDavDiscovery().discoverAccount(url!!, credentials)
        assumeTrue("The live account has no writable calendar.", discovered.calendars.any { !it.readOnly })
        val scratchUrl = "${discovered.homeSetUrl.trimEnd('/')}/calino-recurring-${UUID.randomUUID()}/"
        val http = DavHttp()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calendar = DiscoveredCalendar(
            url = scratchUrl, displayName = "Calino recurring probe", color = 0xFF11A602,
            readOnly = false, components = setOf("VTODO"),
        )
        val made = http.request(
            "MKCALENDAR", scratchUrl, credentials, body = """<?xml version="1.0" encoding="UTF-8"?><c:mkcalendar xmlns:c="urn:ietf:params:xml:ns:caldav"/>""",
        )
        assertTrue("MKCALENDAR failed: ${made.status}", made.status in 200..299)
        try {
            val href = "$scratchUrl$UID.ics"
            val planted = http.put(
                href, credentials,
                listOf(
                    "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Another Client//EN", "BEGIN:VTODO", "UID:$UID",
                    "DTSTAMP:20260301T000000Z", "DTSTART;VALUE=DATE:20260303", "DUE;VALUE=DATE:20260303",
                    "RRULE:$rrule", "SUMMARY:Exercise", "X-FOREIGN-THING:keep me", "END:VTODO", "END:VCALENDAR",
                ).joinToString("\r\n") + "\r\n",
                DavHttp.CalendarMediaType, DavPrecondition.New,
            )
            assertTrue("PUT failed: ${planted.status}", planted.status in 200..299)
            val repository = CalDavRepository(
                fetcher = CalDavFetcher(http),
                scope = scope,
                cache = FileCalendarCache(Files.createTempDirectory("calino-recurring-live").toFile()),
                mapper = ICalMapper(ZoneId.of("Europe/Copenhagen")),
                today = { today },
            )
            repository.setSources(listOf(CalDavSource(calendar, credentials, "account")), refreshAfterSourceChange = false)
            val harness = Harness(repository, http, credentials, calendar, href)
            harness.sync()
            harness.block()
        } finally {
            http.request("DELETE", scratchUrl, credentials)
            scope.cancel()
        }
    }

    @Test
    fun `deleting one occurrence leaves the series and a foreign property`() = withSeries {
        assertTrue(tasks().size > 20)
        val result = repository.deleteTask(occurrence(LocalDate.of(2026, 3, 10)).id, RecurrenceEditScope.This)
        assertTrue(result.toString(), result is WriteResult.Applied)
        sync()
        val text = server()
        assertTrue(text, text.contains("EXDATE;VALUE=DATE:20260310"))
        assertTrue(text, text.contains("X-FOREIGN-THING:keep me"))
        assertEquals(1, Regex("BEGIN:VTODO").findAll(text).count())
        val due = tasks().map { it.due }
        assertFalse(due.contains(LocalDate.of(2026, 3, 10)))
        assertTrue(due.containsAll(listOf(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 17))))
    }

    @Test
    fun `deleting this and future ends the series the day before`() = withSeries {
        val result = repository.deleteTask(occurrence(LocalDate.of(2026, 3, 17)).id, RecurrenceEditScope.Future)
        assertTrue(result.toString(), result is WriteResult.Applied)
        sync()
        val text = server()
        assertTrue(text, text.contains("UNTIL=20260316"))
        assertTrue(text, text.contains("X-FOREIGN-THING:keep me"))
        assertEquals(listOf(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 10)), tasks().mapNotNull { it.due }.sorted())
    }

    @Test
    fun `this and future from the first occurrence removes the resource`() = withSeries {
        val result = repository.deleteTask(occurrence(LocalDate.of(2026, 3, 3)).id, RecurrenceEditScope.Future)
        assertTrue(result.toString(), result is WriteResult.Applied)
        sync()
        assertEquals("", server())
        assertTrue(tasks().isEmpty())
    }

    @Test
    fun `deleting the entire series from a later occurrence removes the resource`() = withSeries {
        val result = repository.deleteTask(occurrence(LocalDate.of(2026, 4, 7)).id, RecurrenceEditScope.All)
        assertTrue(result.toString(), result is WriteResult.Applied)
        sync()
        assertEquals("", server())
        assertTrue(tasks().isEmpty())
    }

    @Test
    fun `changing the repeat days and end date rewrites the series in place`() = withSeries {
        val occurrence = occurrence(LocalDate.of(2026, 3, 10))
        val result = repository.updateTask(
            occurrence.id,
            occurrence.asUpdate().copy(
                recurrence = "FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20260320T235959Z",
                recurrenceChanged = true,
                recurrenceScope = RecurrenceEditScope.All,
            ),
            occurrence.done,
        )
        assertTrue(result.toString(), result is WriteResult.Applied)
        sync()
        val text = server()
        assertTrue(text, text.contains("BYDAY=TU,TH"))
        assertTrue(text, Regex("UNTIL=20260320(?!T)").containsMatchIn(text))
        assertTrue(text, text.contains("DTSTART;VALUE=DATE:20260303"))
        assertTrue(text, text.contains("X-FOREIGN-THING:keep me"))
        assertEquals(1, Regex("BEGIN:VTODO").findAll(text).count())
        assertEquals(
            listOf(3, 5, 10, 12, 17, 19).map { LocalDate.of(2026, 3, it) },
            tasks().mapNotNull { it.due }.sorted(),
        )
    }

    private companion object {
        const val UID = "calino-live-recurring-task"
    }
}
