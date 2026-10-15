package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavDiscovery
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.calDavErrorForThrowable
import calino.malinov.ski.data.caldav.toCalDavCalendar
import calino.malinov.ski.data.model.CalDavForm
import calino.malinov.ski.data.model.CalendarProtocol
import calino.malinov.ski.data.repository.CalDavClient
import calino.malinov.ski.state.CalDavConnectResult
import calino.malinov.ski.state.normalizeServerUrl
import kotlinx.coroutines.CancellationException

/**
 * The add-account sheet's discovery: JMAP first, then CalDAV.
 *
 * Detection is a bounded probe that falls through quietly for anything that is
 * not JMAP, so a CalDAV-only server behaves exactly as before. A server that
 * offers both is treated as JMAP.
 */
class ProtocolDiscovery(
    private val caldav: CalDavDiscovery,
    private val jmap: JmapDiscovery,
    private val http: DavHttp,
) : CalDavClient {

    override suspend fun discover(form: CalDavForm): CalDavConnectResult {
        val entered = normalizeServerUrl(form.serverUrl)
            ?: return CalDavConnectResult.Failed("That server address could not be read.")
        val credentials = DavCredentials(form.username, form.password)
        val detection = try {
            detectProtocol(entered, credentials, http)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return CalDavConnectResult.Failed(calDavErrorForThrowable(error, entered).message)
        }
        if (detection !is ProtocolDetection.Jmap) return caldav.discover(form)
        return try {
            val account = jmap.discoverAccount(entered, credentials)
            if (account.calendars.isEmpty()) {
                CalDavConnectResult.Failed("Connected, but that account has no calendars.")
            } else {
                CalDavConnectResult.Discovered(account.calendars.map { it.toCalDavCalendar() }, CalendarProtocol.Jmap)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            CalDavConnectResult.Failed(calDavErrorForThrowable(error, entered).message)
        }
    }
}
