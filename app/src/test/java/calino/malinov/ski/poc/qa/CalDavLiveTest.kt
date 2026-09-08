package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.CalDavDiscovery
import calino.malinov.ski.poc.data.caldav.CalDavFetcher
import calino.malinov.ski.poc.data.caldav.DavCredentials
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
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

        assertTrue("no calendar returned anything", results.any { !it.isEmpty })
        assertFalse(
            "the server ignored expand; recurring events would render as one",
            results.any { it.expandUnsupported },
        )
        results.flatMap { it.events }.forEach { event ->
            assertTrue("every event needs a title", event.title.isNotBlank())
            assertTrue("every fetched event needs a UID", event.uid != null)
        }
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
