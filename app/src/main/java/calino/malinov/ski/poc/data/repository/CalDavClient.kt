package calino.malinov.ski.poc.data.repository

import calino.malinov.ski.poc.data.model.CalDavCalendar
import calino.malinov.ski.poc.data.model.CalDavForm
import calino.malinov.ski.poc.state.CalDavConnectResult
import calino.malinov.ski.poc.state.normalizeServerUrl
import calino.malinov.ski.poc.state.serverHost
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The seam a real CalDAV implementation would fill: principal lookup followed
 * by calendar-home discovery.
 *
 * `CalDavDiscovery` is the real implementation the app now uses.
 * [FixtureCalDavClient] remains for tests and for exercising the sheet's
 * states without a server.
 */
interface CalDavClient {
    suspend fun discover(form: CalDavForm): CalDavConnectResult
}

/** How long the simulated round trip takes, so the connecting state is visible. */
const val FixtureDiscoveryMillis = 900L

/**
 * Discovery is decided by the typed input rather than at random, so a failure
 * can be reproduced by hand and asserted in a test. A host containing `bad` or
 * `invalid` fails as unreachable; the password `wrong` fails as rejected
 * credentials.
 */
class FixtureCalDavClient(private val delayMillis: Long = FixtureDiscoveryMillis) : CalDavClient {
    override suspend fun discover(form: CalDavForm): CalDavConnectResult {
        delay(delayMillis)
        val url = normalizeServerUrl(form.serverUrl)
            ?: return CalDavConnectResult.Failed("That server address could not be read.")
        val host = serverHost(url).lowercase(Locale.US)
        if (host.contains("bad") || host.contains("invalid")) {
            return CalDavConnectResult.Failed("Could not reach $host. Check the address and try again.")
        }
        if (form.password.trim().equals("wrong", ignoreCase = true)) {
            return CalDavConnectResult.Failed("The server rejected that username or password.")
        }
        return CalDavConnectResult.Discovered(fixtureCalendarsFor(host))
    }
}

/** The discovered set, reusing the fixture calendar palette so colours stay coherent. */
fun fixtureCalendarsFor(host: String): List<CalDavCalendar> = listOf(
    CalDavCalendar("$host/personal", "Personal", 0xFFC2697F),
    CalDavCalendar("$host/work", "Work", 0xFF5B7FB5),
    CalDavCalendar("$host/travel", "Travel", 0xFFBF944E),
    CalDavCalendar("$host/holidays", "Holidays", 0xFF6E8F72, readOnly = true),
)
