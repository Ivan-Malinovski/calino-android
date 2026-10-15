package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.calDavErrorForThrowable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject

private val JsonMediaType = "application/json; charset=utf-8".toMediaType()

/**
 * A JMAP connection to the one server the person entered.
 *
 * Every endpoint the session advertises must sit on the session's own origin
 * (loopback advertisements are rebased onto it by [resolveTemplate]); anything
 * else is refused, so a server cannot steer the credentials to another host.
 * Requests go through [DavHttp], which follows redirects only within an origin.
 */
class JmapClient(
    private val serverUrl: String,
    private val credentials: DavCredentials,
    private val http: DavHttp = DavHttp(),
) {
    private var current: JmapSession? = null
    private var sessionUrl: String? = null
    private val sessionLock = Mutex()

    val session: JmapSession
        get() = current ?: throw JmapException(CalDavErrorCode.Unknown, "Client not connected. Call connect() first.")

    /** The primary calendars account, or a 404-shaped error when the server offers none. */
    val accountId: String
        get() = session.calendarAccountId ?: throw JmapException(
            CalDavErrorCode.NotFound,
            "JMAP session has no primary calendar account",
            status = 404,
            type = "accountNotFound",
        )

    val username: String get() = session.username
    val apiUrl: String get() = session.apiUrl
    val uploadUrl: String get() = session.uploadUrl
    val downloadUrl: String get() = session.downloadUrl
    val eventSourceUrl: String get() = session.eventSourceUrl

    /** Fetches the session from the first candidate that answers like JMAP. */
    suspend fun connect() {
        var lastError: Throwable? = null
        for (url in sessionUrl?.let { listOf(it) } ?: sessionCandidates(serverUrl)) {
            try {
                val loaded = fetchSession(http, credentials, url)
                val origin = loaded.sessionUrl
                val resolved = loaded.session.copy(
                    apiUrl = resolveTemplate(loaded.session.apiUrl, origin),
                    uploadUrl = resolveTemplate(loaded.session.uploadUrl, origin),
                    downloadUrl = resolveTemplate(loaded.session.downloadUrl, origin),
                    eventSourceUrl = resolveTemplate(loaded.session.eventSourceUrl, origin),
                )
                listOf(resolved.apiUrl, resolved.uploadUrl, resolved.downloadUrl, resolved.eventSourceUrl).forEach {
                    if (!sameJmapOrigin(it, origin)) {
                        throw JmapException(
                            CalDavErrorCode.NotCalDav,
                            "The server advertised a JMAP endpoint on a different host, so it was not used.",
                            type = "invalidSession",
                        )
                    }
                }
                current = resolved
                sessionUrl = origin
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // A rejected login is final; anything else may be fixed by the next candidate.
                if (error is CalDavException && error.code in setOf(CalDavErrorCode.Auth, CalDavErrorCode.Forbidden)) {
                    throw error
                }
                lastError = error
            }
        }
        throw (lastError as? CalDavException)
            ?: lastError?.let { calDavErrorForThrowable(it, serverUrl) }
            ?: JmapException(CalDavErrorCode.NotCalDav, "Invalid JMAP session document", type = "invalidSession")
    }

    private suspend fun refreshSession(state: String) {
        if (state == session.state) return
        sessionLock.withLock {
            if (state != session.state) connect()
        }
    }

    /**
     * Runs the calls and returns one logical response per call, with the
     * caller's own call ids, even when an oversized `/get` or `/set` had to be
     * split or the request exceeded `maxCallsInRequest`.
     */
    suspend fun call(
        methodCalls: List<JmapInvocation>,
        using: List<String> = listOf(JmapCore, JmapCalendars),
    ): JmapResponse {
        if (methodCalls.map { it.callId }.toSet().size != methodCalls.size) {
            throw JmapException(CalDavErrorCode.Rejected, "Duplicate JMAP call ids", status = 400, type = "invalidArguments")
        }
        val responses = mutableListOf<JmapInvocation>()
        val createdIds = linkedMapOf<String, String>()
        var sessionState = session.state

        suspend fun send(calls: List<JmapInvocation>): List<JmapInvocation> {
            val payload = JSONObject()
                .put("using", JSONArray((listOf(JmapCore) + using).distinct()))
                .put(
                    "methodCalls",
                    JSONArray().also { array ->
                        calls.forEach { call ->
                            array.put(JSONArray().put(call.name).put(call.arguments).put(call.callId))
                        }
                    },
                )
                .put("createdIds", JSONObject().also { ids -> createdIds.forEach { (k, v) -> ids.put(k, v) } })
            val response = try {
                http.request(
                    method = "POST",
                    url = apiUrl,
                    credentials = credentials,
                    headers = mapOf("Accept" to "application/json"),
                    body = payload.toString(),
                    contentType = JsonMediaType,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw calDavErrorForThrowable(error, apiUrl)
            }
            if (response.status !in 200..299) throw httpError(response.status, response.body, response.header("Retry-After"))
            val body = runCatching { JSONObject(response.body) }.getOrNull()
            val retryAfter = parseRetryAfter(response.header("Retry-After"))
            val methodResponses = body?.arrayOrNull("methodResponses")
            val state = body?.stringOrNull("sessionState")
            val parsed = methodResponses?.let { array ->
                (0 until array.length()).map { index ->
                    val item = array.opt(index) as? JSONArray
                    if (item == null || item.length() != 3) null
                    else {
                        val name = item.opt(0) as? String
                        val args = item.opt(1) as? JSONObject
                        val id = item.opt(2) as? String
                        if (name == null || args == null || id == null) null else JmapInvocation(name, args, id)
                    }
                }
            }
            if (body == null || parsed == null || state == null || parsed.any { it == null }) {
                // A request-level problem (RFC 7807) is reported as a JSON object with a type.
                if (body?.stringOrNull("type") != null) throw methodError(body, "request", "", retryAfter)
                throw invalidResponse("Invalid JMAP API response")
            }
            val result = parsed.filterNotNull()
            for (call in calls) {
                if (result.none { it.callId == call.callId }) throw invalidResponse("JMAP response omitted a call")
            }
            sessionState = state
            body.objectOrNull("createdIds")?.let { ids ->
                ids.keyList().forEach { key -> ids.stringOrNull(key)?.let { createdIds[key] = it } }
            }
            result.forEach { invocation ->
                invocation.arguments.objectOrNull("created")?.let { created ->
                    created.keyList().forEach { key ->
                        created.objectOrNull(key)?.stringOrNull("id")?.let { createdIds[key] = it }
                    }
                }
            }
            refreshSession(state)
            result.firstOrNull { it.name == "error" }?.let { failure ->
                val call = calls.firstOrNull { it.callId == failure.callId }
                throw methodError(failure.arguments, call?.name ?: "unknown", failure.callId, retryAfter, responses + result)
            }
            return result
        }

        var pending = mutableListOf<JmapInvocation>()
        suspend fun flush() {
            if (pending.isEmpty()) return
            responses += send(pending)
            pending = mutableListOf()
        }

        for (call in methodCalls) {
            var arguments = resolveReferences(call.arguments, responses)
            // A reference may yield more ids than a /get accepts, so its producer must run first.
            if ((call.name.endsWith("/get") || call.name.endsWith("/set")) && arguments.keyList().any { it.startsWith("#") }) {
                flush()
                arguments = resolveReferences(call.arguments, responses)
            }
            val core = session.core
            val parts = splitArguments(
                call.name, arguments,
                positiveLimit(core.opt("maxObjectsInGet")),
                positiveLimit(core.opt("maxObjectsInSet")),
            )
            if (parts.size > 1) {
                flush()
                val results = mutableListOf<JSONObject>()
                var ifInState = arguments.stringOrNull("ifInState")
                for (part in parts) {
                    ifInState?.let { part.put("ifInState", it) }
                    val result = send(listOf(JmapInvocation(call.name, part, call.callId)))
                    val primary = result.firstOrNull { it.name == call.name && it.callId == call.callId }
                        ?: throw invalidResponse("JMAP response omitted split method result")
                    results += primary.arguments
                    if (arguments.stringOrNull("ifInState") != null) {
                        ifInState = primary.arguments.stringOrNull("newState")
                            ?: throw invalidResponse("JMAP /set response omitted newState")
                    }
                }
                responses += JmapInvocation(call.name, mergeResponses(call.name, arguments, results), call.callId)
            } else {
                pending += JmapInvocation(call.name, arguments, call.callId)
                if (pending.size >= positiveLimit(core.opt("maxCallsInRequest"))) flush()
            }
        }
        flush()
        return JmapResponse(responses, sessionState, createdIds)
    }
}

internal data class LoadedSession(val session: JmapSession, val sessionUrl: String)

/** One bounded session GET. A non-2xx answer is an error; a body that is not a session is "not JMAP". */
internal suspend fun fetchSession(
    http: DavHttp,
    credentials: DavCredentials,
    url: String,
    timeoutMillis: Long = 8_000,
): LoadedSession {
    val response = try {
        withTimeout(timeoutMillis) {
            http.request(
                method = "GET",
                url = url,
                credentials = credentials,
                headers = mapOf("Accept" to "application/json"),
            )
        }
    } catch (error: CancellationException) {
        if (error is kotlinx.coroutines.TimeoutCancellationException) {
            throw CalDavException(CalDavErrorCode.Timeout, "The server took too long to answer.", error)
        }
        throw error
    } catch (error: Throwable) {
        throw calDavErrorForThrowable(error, url)
    }
    if (response.status !in 200..299) throw httpError(response.status, response.body, response.header("Retry-After"))
    val session = parseSession(runCatching { JSONObject(response.body) }.getOrNull())
    return LoadedSession(session, response.url)
}
