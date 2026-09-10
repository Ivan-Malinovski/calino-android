package calino.malinov.ski.poc.data.repository

import calino.malinov.ski.poc.data.caldav.CalDavErrorCode
import calino.malinov.ski.poc.data.caldav.CalDavException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** The recovery action for one failed queued write. */
sealed interface WriteDisposition {
    /** The transport did not produce an HTTP answer; retry without spending a slot. */
    data object Retry : WriteDisposition

    /** Retry after backoff and count this attempt toward the dead-letter cap. */
    data object RetryCounted : WriteDisposition

    /** Refresh the resource ETag and re-apply this operation once. */
    data object StaleEtag : WriteDisposition

    /** The operation cannot become valid by retrying. */
    data class Drop(val reason: String) : WriteDisposition
}

/** A classified error plus the person-facing text to retain in the queue. */
data class WriteErrorClassification(
    val disposition: WriteDisposition,
    val message: String,
    val statusCode: Int? = null,
)

/**
 * Classifies the hard cases from the web client's pending-change policy.
 * HTTP status is authoritative when present; a transport failure is retryable
 * without consuming the ten counted attempts, while an unknown application
 * failure is counted so it cannot loop forever.
 */
fun classifyWriteError(error: Throwable, changeType: PendingChangeType): WriteErrorClassification {
    val dav = error as? CalDavException
    val status = dav?.status
    val message = dav?.message ?: error.message ?: "The change could not be saved."
    val disposition = when {
        status == 412 && changeType == PendingChangeType.CREATE -> WriteDisposition.Drop(
            "That item already exists on the server.",
        )
        status == 412 && changeType in setOf(
            PendingChangeType.UPDATE,
            PendingChangeType.DELETE,
            PendingChangeType.DELETE_HREF,
        ) -> WriteDisposition.StaleEtag
        status == 403 && (dav?.hasPrecondition("no-uid-conflict") == true ||
            dav?.hasPrecondition("valid-calendar") == true
            ) -> WriteDisposition.Drop("The server rejected this calendar item as invalid.")
        status == 403 -> WriteDisposition.Drop(message)
        status == 507 -> WriteDisposition.Drop("The server is out of space for this calendar.")
        status == 404 || status == 410 -> WriteDisposition.Drop(
            if (changeType == PendingChangeType.CREATE || changeType == PendingChangeType.UPDATE) {
                "That calendar is no longer on the server."
            } else {
                "That item is no longer on the server."
            },
        )
        status == 401 || status == 429 || status in 500..599 -> WriteDisposition.RetryCounted
        status == null && isNetworkish(error, dav) -> WriteDisposition.Retry
        else -> WriteDisposition.RetryCounted
    }
    return WriteErrorClassification(
        disposition = disposition,
        message = when (disposition) {
            is WriteDisposition.Drop -> disposition.reason
            else -> message
        },
        statusCode = status,
    )
}

private fun isNetworkish(error: Throwable, dav: CalDavException?): Boolean =
    dav?.code in setOf(CalDavErrorCode.Network, CalDavErrorCode.Timeout, CalDavErrorCode.Tls) ||
        error is IOException || error is SocketTimeoutException ||
        error is UnknownHostException || error is SSLException
