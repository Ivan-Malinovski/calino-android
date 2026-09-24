package calino.malinov.ski.data.repository

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException

/** Destination failure classes used to report why a move was rejected. */
enum class CalDavMoveFailureKind {
    /** 409 or a 403 carrying the CalDAV duplicate-UID precondition. */
    UidConflict,
    /** A 403 without the duplicate-UID marker. */
    Forbidden,
    /** A different protocol or transport failure. */
    Other,
}

data class CalDavMoveFailure(
    val kind: CalDavMoveFailureKind,
    val status: Int?,
    val message: String,
)

/**
 * Purely classifies a failed destination write. A bare 403 means permission
 * denied; all destination failures leave the source in place.
 */
object CalDavMoveFailureClassifier {
    fun classify(error: Throwable): CalDavMoveFailure {
        val dav = error as? CalDavException
        val status = dav?.status
        // A status-bearing 403 is authoritative: even if a malformed caller
        // supplied a Conflict code, a bare 403 must remain permission denied.
        val uidConflict = status == 409 ||
            (status == null && dav?.code == CalDavErrorCode.Conflict) ||
            (status == 403 && dav?.hasPrecondition("no-uid-conflict") == true)
        return when {
            uidConflict -> CalDavMoveFailure(
                kind = CalDavMoveFailureKind.UidConflict,
                status = status,
                message = dav?.message ?: "The destination already contains this UID.",
            )
            status == 403 -> CalDavMoveFailure(
                kind = CalDavMoveFailureKind.Forbidden,
                status = status,
                message = dav?.message ?: "The destination calendar refused the move.",
            )
            else -> CalDavMoveFailure(
                kind = CalDavMoveFailureKind.Other,
                status = status,
                message = dav?.message ?: (error.message ?: "The destination write failed."),
            )
        }
    }
}
