package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.CalDavDiscovery
import calino.malinov.ski.poc.data.caldav.CalDavFetcher
import calino.malinov.ski.poc.data.caldav.CachedCalendar
import calino.malinov.ski.poc.data.caldav.DavCredentials
import calino.malinov.ski.poc.data.caldav.DiscoveredCalendar
import calino.malinov.ski.poc.data.caldav.FetchResult
import calino.malinov.ski.poc.data.caldav.FileCalendarCache
import calino.malinov.ski.poc.data.caldav.ICalMapper
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end against a real CalDAV server.
 *
 * Skipped unless the three environment variables are set, so the normal suite
 * stays offline and deterministic. Credentials are never committed -- they are
 * supplied at run time:
 *
 * ```
 * CALINO_CALDAV_URL=https://example.com CALINO_CALDAV_USER=you CALINO_CALDAV_PASS=... \
 *   ./gradlew test --tests "*CalDavLiveTest*"
 * ```
 */
class CalDavLiveTest {

    private val url: String? = System.getenv("CALINO_CALDAV_URL")
    private val user: String? = System.getenv("CALINO_CALDAV_USER")
    private val pass: String? = System.getenv("CALINO_CALDAV_PASS")

    @Before
    fun requireCredentials() {
        assumeTrue(
            "Set CALINO_CALDAV_URL / _USER / _PASS to run the live CalDAV test.",
            !url.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank(),
        )
    }

    private fun credentials() = DavCredentials(user!!, pass!!)

    private val mapper = ICalMapper()

    private fun FetchResult.mapped(
        calendar: DiscoveredCalendar,
        start: LocalDate,
        end: LocalDate,
    ): ICalMapper.Parsed = mapper.mapAll(resources, calendar.url, calendar.color, start, end)

    @Test
    fun `discovery finds calendars and excludes address books`() = runBlocking {
        val account = CalDavDiscovery().discoverAccount(url!!, credentials())
        assertTrue("expected at least one calendar", account.calendars.isNotEmpty())
        assertTrue(
            "address books must not be listed as calendars",
            account.calendars.none { it.displayName.contains("contacts", ignoreCase = true) },
        )
        account.calendars.forEach { calendar ->
            assertTrue("a calendar needs a name", calendar.displayName.isNotBlank())
            assertTrue("a calendar URL should be absolute", calendar.url.startsWith("http"))
        }
    }

    @Test
    fun `a real fetch returns readable records`() = runBlocking {
        val account = CalDavDiscovery().discoverAccount(url!!, credentials())
        val fetcher = CalDavFetcher()
        val today = LocalDate.now()

        val results = account.calendars.map { calendar ->
            fetcher.fetch(calendar, credentials(), today.minusMonths(6), today.plusMonths(6))
        }

        // Deliberately per-kind. Asserting only "something came back" passes
        // when every event is missing but a single task arrives -- which is
        // exactly the shape a broken event query produces.
        val mapped = account.calendars.zip(results) { calendar, result ->
            result.mapped(calendar, today.minusMonths(6), today.plusMonths(6))
        }
        val events = mapped.flatMap { it.events }
        val tasks = mapped.flatMap { it.tasks }
        val journals = mapped.flatMap { it.journals }
        println(
            "LIVE: ${account.calendars.size} calendars, " +
                "${events.size} events, ${tasks.size} tasks, ${journals.size} journals, " +
                "componentFailures=${results.any { it.hadComponentFailures }}",
        )
        account.calendars.forEach { println("LIVE:   ${it.displayName} components=${it.components} url=${it.url}") }

        assertFalse(
            "a component query failed; the result is not the whole calendar",
            results.any { it.hadComponentFailures },
        )
        assertTrue("no calendar returned anything", results.any { !it.isEmpty })
        assertTrue("no events were read", events.isNotEmpty())

        // Recurrence is expanded on the client, so a repeating series must
        // reach more than one date. This is the assertion that would have
        // caught the sabre expand failure: the server 500s on <c:expand> for
        // two of this account's three calendars.
        // Keyed by calendar too: the same UID legitimately appears in two
        // collections when an event has been copied between them.
        val bySeries = events.filter { it.uid != null }.groupBy { it.calendarId to it.uid }
        val repeating = bySeries.values.filter { it.size > 1 }
        assertTrue(
            "no series reached more than one date; recurrence is not being expanded",
            repeating.isNotEmpty(),
        )
        repeating.forEach { occurrences ->
            assertEquals(
                "occurrences of one series must be individually addressable: " +
                    occurrences.first().title,
                occurrences.size,
                occurrences.map { it.id }.distinct().size,
            )
        }
        println("LIVE: ${repeating.size} repeating series, largest ${repeating.maxOf { it.size }} occurrences")

        events.forEach { event ->
            assertTrue("every event needs a title", event.title.isNotBlank())
            assertTrue("every fetched event needs a UID", event.uid != null)
        }
    }

    @Test
    fun `a cached copy maps back to the same records`() = runBlocking {
        // The point of caching the server's own text rather than mapped
        // occurrences: what comes back off disk must be indistinguishable from
        // what came off the wire, for the same window.
        val account = CalDavDiscovery().discoverAccount(url!!, credentials())
        val today = LocalDate.now()
        val start = today.minusMonths(6)
        val end = today.plusMonths(6)
        val cache = FileCalendarCache(Files.createTempDirectory("calino-cache").toFile())

        val fromServer = account.calendars.flatMap { calendar ->
            val result = CalDavFetcher().fetch(calendar, credentials(), start, end)
            cache.save(
                CachedCalendar(calendar.url, Instant.now(), start, end, result.resources),
            )
            result.mapped(calendar, start, end).events
        }

        val fromDisk = account.calendars.flatMap { calendar ->
            val entry = cache.load(calendar.url)
            assertTrue("nothing was cached for ${calendar.displayName}", entry != null)
            mapper.mapAll(entry!!.resources, calendar.url, calendar.color, start, end).events
        }

        println("LIVE: ${fromServer.size} events from the server, ${fromDisk.size} from the cache")
        assertTrue("the cache read nothing back", fromDisk.isNotEmpty())
        assertEquals(fromServer.map { it.id }.sorted(), fromDisk.map { it.id }.sorted())
        assertEquals(fromServer.sortedBy { it.id }, fromDisk.sortedBy { it.id })
    }

    @Test
    fun `a wrong password is rejected as an auth failure`() = runBlocking {
        val result = CalDavDiscovery().discover(
            calino.malinov.ski.poc.data.model.CalDavForm(
                serverUrl = url!!,
                username = user!!,
                password = "definitely-not-the-password",
            ),
        )
        val failed = result as calino.malinov.ski.poc.state.CalDavConnectResult.Failed
        assertTrue(
            "expected a credentials message, got: ${failed.message}",
            failed.message.contains("username or password", ignoreCase = true),
        )
    }
}
