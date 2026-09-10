package calino.malinov.ski.poc.data.repository

import calino.malinov.ski.poc.data.caldav.CalDavErrorCode
import calino.malinov.ski.poc.data.caldav.CalDavException

/** The destination failure classes that affect whether source deletion is safe. */
enum class CalDavMoveFailureKind {
    /** 409 or a 403 carrying the CalDAV duplicate-UID precondition. */
    UidConflict,
    /** A 403 without the duplicate-UID marker: preserve the source. */
    Forbidden,
    /** A different protocol or transport failure. */
    Other,
}

data class CalDavMoveFailure(
    val kind: CalDavMoveFailureKind,
    val status: Int?,
    val message: String,
) {
    /** Only this class permits the caller's explicit UID-conflict fallback. */
    val mayUseUidConflictFallback: Boolean get() = kind == CalDavMoveFailureKind.UidConflict
}

/**
 * Purely classifies a failed destination write. In particular, a bare 403 is
 * never treated as a duplicate UID: deleting the source in that case can lose
 * the event when the destination is simply read-only or permission denied.
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
