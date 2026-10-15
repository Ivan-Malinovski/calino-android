package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import org.json.JSONObject

/**
 * A failed JMAP request. It is a [CalDavException] so the write queue,
 * conflict classification and error UI treat it like any other backend failure:
 * the [status] carries the HTTP meaning of a JMAP error type (see [methodStatus])
 * and is what `classifyWriteError` reads.
 */
class JmapException(
    code: CalDavErrorCode,
    message: String,
    status: Int? = null,
    body: String? = null,
    cause: Throwable? = null,
    /** The JMAP error type URI or short name, e.g. `stateMismatch`. */
    val type: String? = null,
    /** Seconds, already clamped. */
    val retryAfter: Int? = null,
    val method: String? = null,
    val callId: String? = null,
    val details: JSONObject? = null,
    /** Everything the server answered before the failing call, for diagnostics. */
    val methodResponses: List<JmapInvocation> = emptyList(),
) : CalDavException(code, message, cause, status, body)

private val MethodStatus = mapOf(
    "forbidden" to 403,
    "accountNotFound" to 404,
    "notFound" to 404,
    "accountReadOnly" to 403,
    "stateMismatch" to 412,
    "overQuota" to 507,
    "rateLimit" to 429,
    "serverFail" to 500,
    "serverUnavailable" to 503,
    "unknownMethod" to 400,
    "invalidArguments" to 400,
    "invalidResultReference" to 400,
    "requestTooLarge" to 413,
    "tooManyCalls" to 413,
    "tooManyObjects" to 413,
    "limit" to 413,
)

internal fun methodStatus(type: String): Int? = MethodStatus[type.substringAfterLast(':')]

/** The queue and UI reason about [CalDavErrorCode]; derive it from the HTTP-ish status. */
internal fun codeForStatus(status: Int?): CalDavErrorCode = when {
    status == null -> CalDavErrorCode.Unknown
    status == 401 -> CalDavErrorCode.Auth
    status == 403 -> CalDavErrorCode.Forbidden
    status == 404 -> CalDavErrorCode.NotFound
    status == 409 -> CalDavErrorCode.Conflict
    status == 410 -> CalDavErrorCode.Gone
    status == 412 -> CalDavErrorCode.PreconditionFailed
    status == 507 -> CalDavErrorCode.InsufficientStorage
    status == 400 || status == 413 || status == 415 || status == 422 -> CalDavErrorCode.Rejected
    status == 429 || status in 500..599 -> CalDavErrorCode.Server
    else -> CalDavErrorCode.NotCalDav
}

/** `Retry-After` as seconds: a delta, or an HTTP date, clamped to an hour. */
internal fun parseRetryAfter(raw: String?, nowMillis: Long = System.currentTimeMillis()): Int? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val seconds = value.toLongOrNull()
        ?: runCatching {
            val date = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            maxOf(0L, Math.ceil((date.toInstant().toEpochMilli() - nowMillis) / 1000.0).toLong())
        }.getOrNull()
        ?: return null
    return minOf(seconds, 3600L).toInt()
}

internal fun methodError(
    details: JSONObject,
    method: String,
    callId: String,
    retryAfter: Int? = null,
    methodResponses: List<JmapInvocation> = emptyList(),
): JmapException {
    val type = details.stringOrNull("type") ?: "unknown"
    val status = methodStatus(type)
    val description = details.stringOrNull("description").orEmpty()
    return JmapException(
        code = codeForStatus(status),
        message = "JMAP $method: $type" +
            (status?.let { " (HTTP $it)" }.orEmpty()) +
            (if (description.isNotEmpty()) ": $description" else ""),
        status = status,
        body = details.toString().take(400),
        type = type,
        retryAfter = retryAfter,
        method = method,
        callId = callId,
        details = details,
        methodResponses = methodResponses,
    )
}

/** An HTTP-level failure (not a method error): status, body excerpt and any problem+json type. */
internal fun httpError(status: Int, body: String, retryAfterHeader: String?): JmapException {
    val details = runCatching { JSONObject(body) }.getOrNull()
    return JmapException(
        code = codeForStatus(status),
        message = when (status) {
            401 -> "The server rejected that username or password."
            403 -> "The server accepted the sign-in but refused access."
            else -> "JMAP request failed: HTTP $status" + if (body.isNotBlank()) ": ${body.take(200)}" else ""
        },
        status = status,
        body = body.take(400),
        type = details?.stringOrNull("type"),
        retryAfter = parseRetryAfter(retryAfterHeader),
        details = details,
    )
}

internal fun invalidResponse(message: String, type: String = "invalidResponse") =
    JmapException(CalDavErrorCode.NotCalDav, message, type = type)
