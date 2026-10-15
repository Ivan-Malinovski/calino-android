package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface ProtocolDetection {
    /** [sessionUrl] is where the session was finally served from, after same-origin redirects. */
    data class Jmap(val session: JmapSession, val sessionUrl: String) : ProtocolDetection
    data object CalDav : ProtocolDetection
}

private val JsonContentType = Regex("""application/json|\+json""", RegexOption.IGNORE_CASE)

/**
 * Probes for a JMAP calendars server before CalDAV discovery runs.
 *
 * Anything that merely does not look like JMAP (a login page, a 404, a session
 * without the calendars capability, a timeout) falls through to CalDAV. A JSON
 * 401 that carries `WWW-Authenticate` is a real JMAP authentication failure and
 * stops here, so the person is not shown a misleading CalDAV error. A server
 * offering both protocols is treated as JMAP.
 */
suspend fun detectProtocol(
    serverUrl: String,
    credentials: DavCredentials,
    http: DavHttp = DavHttp(),
    timeoutMillis: Long = 8_000,
): ProtocolDetection {
    val candidates = runCatching { sessionCandidates(serverUrl) }.getOrNull() ?: return ProtocolDetection.CalDav
    for (url in candidates) {
        try {
            val response = withTimeout(timeoutMillis) {
                http.request(
                    method = "GET",
                    url = url,
                    credentials = credentials,
                    headers = mapOf("Accept" to "application/json"),
                )
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (response.status == 401 && JsonContentType.containsMatchIn(contentType) &&
                response.header("WWW-Authenticate") != null
            ) {
                throw httpError(401, response.body, response.header("Retry-After"))
            }
            if (response.status !in 200..299) continue
            val session = parseSession(runCatching { JSONObject(response.body) }.getOrNull())
            if (session.calendarAccountId == null) continue
            return ProtocolDetection.Jmap(session, response.url)
        } catch (error: CancellationException) {
            if (error !is TimeoutCancellationException) throw error
        } catch (error: CalDavException) {
            if (error.code == CalDavErrorCode.Auth) throw error
        } catch (_: Throwable) {
            // HTML, redirects off the origin, network errors and the like mean "not JMAP".
        }
    }
    return ProtocolDetection.CalDav
}
