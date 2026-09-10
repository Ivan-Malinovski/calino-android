package calino.malinov.ski.poc.data.caldav

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Why a CalDAV request failed, in terms the UI can phrase for a person.
 *
 * Ported from the Calino web app's classifier, minus the browser-only cases: a
 * native HTTP stack has no CORS layer, so those verdicts either do not arise or
 * become directly observable rather than inferred.
 */
enum class CalDavErrorCode {
    Network,
    Timeout,
    Tls,
    Auth,
    Forbidden,
    NotFound,
    Server,
    NotCalDav,
    ExpandUnsupported,
    Unknown,

    // Write-path codes. None of these can arise from a read, which is why they
    // were absent until writes existed.

    /** 412. An `If-Match` did not match, or an `If-None-Match: *` found a resource. */
    PreconditionFailed,

    /** 409. Most often a UID already in use elsewhere in the collection. */
    Conflict,

    /** 507. The collection or the account is out of space. */
    InsufficientStorage,

    /** 410. The resource is gone; for a delete that is success, not failure. */
    Gone,
}

/**
 * A failed CalDAV request.
 *
 * [status] and [body] are carried because the write path cannot classify on the
 * status alone: sabre-family servers and iCloud express a UID collision as a
 * **403** whose body holds `<C:no-uid-conflict/>`, and a bare 403 means the
 * opposite -- permission denied, do not retry, and above all do not treat it as
 * a collision and delete the source of a move. [body] is truncated because it is
 * only ever pattern-matched, never displayed.
 */
class CalDavException(
    val code: CalDavErrorCode,
    override val message: String,
    override val cause: Throwable? = null,
    val status: Int? = null,
    val body: String? = null,
) : Exception(message, cause) {

    /** True when the response body names a DAV precondition, e.g. `no-uid-conflict`. */
    fun hasPrecondition(name: String): Boolean =
        body?.contains(name, ignoreCase = true) == true
}

/**
 * Maps an HTTP status to a code.
 *
 * Order matters and is load-bearing: 403 is checked before 401 because the two
 * mean opposite things for recovery. A 401 means the credentials were not
 * accepted and re-entering them may help; a 403 means they *were* accepted and
 * the operation was refused, so retyping the password will not.
 */
fun calDavErrorForStatus(status: Int, url: String, body: String? = null): CalDavException {
    val excerpt = body?.take(BodyExcerptChars)
    fun fail(code: CalDavErrorCode, message: String) =
        CalDavException(code, message, status = status, body = excerpt)

    return when {
        status == 403 -> fail(
            CalDavErrorCode.Forbidden,
            "The server accepted the sign-in but refused access to that calendar.",
        )
        status == 401 -> fail(
            CalDavErrorCode.Auth,
            "The server rejected that username or password.",
        )
        status == 404 -> fail(
            CalDavErrorCode.NotFound,
            "That calendar is no longer on the server.",
        )
        status == 409 -> fail(
            CalDavErrorCode.Conflict,
            "The server refused that change because it conflicts with what is already there.",
        )
        status == 410 -> fail(
            CalDavErrorCode.Gone,
            "That item is no longer on the server.",
        )
        status == 412 -> fail(
            CalDavErrorCode.PreconditionFailed,
            "That item changed on the server since it was last read.",
        )
        status == 507 -> fail(
            CalDavErrorCode.InsufficientStorage,
            "The server is out of space for that calendar.",
        )
        status in 500..599 -> fail(
            CalDavErrorCode.Server,
            "The server reported an error ($status). Try again in a moment.",
        )
        else -> fail(
            CalDavErrorCode.NotCalDav,
            "${hostOf(url)} answered, but not like a CalDAV server.",
        )
    }
}

/** Maps a transport-level throwable to a code. */
fun calDavErrorForThrowable(error: Throwable, url: String): CalDavException {
    if (error is CalDavException) return error
    val host = hostOf(url)
    return when (error) {
        // Timeout before the generic network case: a socket timeout is also an
        // IOException, and the generic message would hide the more useful one.
        is SocketTimeoutException -> CalDavException(
            CalDavErrorCode.Timeout, "$host took too long to answer.", error,
        )
        is SSLException -> CalDavException(
            CalDavErrorCode.Tls,
            "The secure connection to $host could not be established. Check the server's certificate.",
            error,
        )
        is UnknownHostException -> CalDavException(
            CalDavErrorCode.Network, "Could not find $host. Check the address and try again.", error,
        )
        is IOException -> CalDavException(
            CalDavErrorCode.Network, "Could not reach $host. Check your connection and try again.", error,
        )
        else -> CalDavException(
            CalDavErrorCode.Unknown, error.message ?: "Something went wrong talking to $host.", error,
        )
    }
}

/**
 * Every ETag entering the app is stored in one canonical form: bare, without
 * quotes or a weak-comparison prefix. A writer sending `If-Match` re-quotes it
 * once at the header, so a tag that arrived quoted cannot go back out doubled.
 */
fun normalizeEtag(raw: String?): String? = raw?.trim()?.let { value ->
    value.removePrefix("W/").trim().removeSurrounding("\"").takeIf { it.isNotEmpty() }
}

private const val BodyExcerptChars = 400

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull() ?: url
