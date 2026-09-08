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
}

class CalDavException(
    val code: CalDavErrorCode,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Maps an HTTP status to a code.
 *
 * Order matters and is load-bearing: 403 is checked before 401 because the two
 * mean opposite things for recovery. A 401 means the credentials were not
 * accepted and re-entering them may help; a 403 means they *were* accepted and
 * the operation was refused, so retyping the password will not.
 */
fun calDavErrorForStatus(status: Int, url: String): CalDavException = when {
    status == 403 -> CalDavException(
        CalDavErrorCode.Forbidden,
        "The server accepted the sign-in but refused access to that calendar.",
    )
    status == 401 -> CalDavException(
        CalDavErrorCode.Auth,
        "The server rejected that username or password.",
    )
    status == 404 -> CalDavException(
        CalDavErrorCode.NotFound,
        "That calendar is no longer on the server.",
    )
    status in 500..599 -> CalDavException(
        CalDavErrorCode.Server,
        "The server reported an error ($status). Try again in a moment.",
    )
    else -> CalDavException(
        CalDavErrorCode.NotCalDav,
        "${hostOf(url)} answered, but not like a CalDAV server.",
    )
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

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull() ?: url
