package calino.malinov.ski.poc.data.repository

import calino.malinov.ski.poc.data.caldav.normalizeEtag
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry

/**
 * The protocol version fields needed to compare two copies of one CalDAV
 * record.  [sequence] is the iCalendar logical version; a missing SEQUENCE is
 * RFC 5545's initial value, zero.
 *
 * ETags are resource versions, not logical event versions.  They are useful
 * here as a proof that two observations are literally the same server
 * resource.  The resolver never uses timestamps or start dates as a tie
 * breaker.
 */
data class CalDavRecordVersion(
    val uid: String,
    val etag: String? = null,
    val sequence: Int? = null,
) {
    init {
        require(uid.isNotBlank()) { "A CalDAV record UID is required." }
        require(sequence == null || sequence >= 0) {
            "A CalDAV SEQUENCE cannot be negative."
        }
    }

    /** Missing SEQUENCE has the RFC-defined initial value. */
    val effectiveSequence: Int get() = sequence ?: 0

    /** ETags are compared in the same canonical form used by the DAV writer. */
    val normalizedEtag: String? get() = normalizeEtag(etag)
}

/** A record plus its protocol version, useful when a caller wants a winner. */
data class VersionedCalDavRecord<T>(
    val record: T,
    val version: CalDavRecordVersion,
)

enum class CalDavEtagRelation {
    /** Both sides have the same canonical ETag. */
    Same,
    /** Both sides have ETags, and they identify different resources. */
    Different,
    /** At least one side has no ETag, so ETag equality cannot be established. */
    Unknown,
}

/** The pure comparison result consumed by sync or a conflict-resolution UI. */
enum class CalDavVersionDecision {
    /** ETag and SEQUENCE establish the same version. */
    SameVersion,
    /** Local has the higher SEQUENCE. */
    LocalNewer,
    /** Remote has the higher SEQUENCE. */
    RemoteNewer,
    /** Different/unknown ETags but equal SEQUENCE: neither side is newer. */
    EqualVersionConflict,
}

data class CalDavConflictReport(
    val uid: String,
    val decision: CalDavVersionDecision,
    val etagRelation: CalDavEtagRelation,
    val localSequence: Int,
    val remoteSequence: Int,
) {
    val hasConflict: Boolean get() = decision == CalDavVersionDecision.EqualVersionConflict
    val equalLogicalVersion: Boolean get() = localSequence == remoteSequence
}

/** The explicit choice for a true equal-version conflict. */
enum class CalDavConflictPolicy {
    Ask,
    LocalWins,
    RemoteWins,
}

sealed interface CalDavResolution<out T> {
    data class Resolved<T>(
        val record: T,
        val chosen: ChosenSide,
        val report: CalDavConflictReport,
    ) : CalDavResolution<T>

    data class Conflict<T>(
        val local: T,
        val remote: T,
        val report: CalDavConflictReport,
    ) : CalDavResolution<T>
}

enum class ChosenSide { Local, Remote }

/**
 * Pure CalDAV version comparison and policy application.
 *
 * Equal SEQUENCE is deliberately not treated as "remote is newer".  If the
 * ETags prove the same resource version, the copies are equal.  Otherwise the
 * equal logical version is an actual conflict, including the conservative case
 * where one ETag is absent.
 */
object CalDavConflictResolver {

    fun inspect(
        local: CalDavRecordVersion,
        remote: CalDavRecordVersion,
    ): CalDavConflictReport {
        require(local.uid == remote.uid) {
            "Cannot compare different CalDAV records: ${local.uid} and ${remote.uid}."
        }

        val etagRelation = when {
            local.normalizedEtag != null && local.normalizedEtag == remote.normalizedEtag ->
                CalDavEtagRelation.Same
            local.normalizedEtag != null && remote.normalizedEtag != null ->
                CalDavEtagRelation.Different
            else -> CalDavEtagRelation.Unknown
        }
        val sequenceComparison = local.effectiveSequence.compareTo(remote.effectiveSequence)
        val decision = when {
            etagRelation == CalDavEtagRelation.Same && sequenceComparison == 0 ->
                CalDavVersionDecision.SameVersion
            sequenceComparison > 0 -> CalDavVersionDecision.LocalNewer
            sequenceComparison < 0 -> CalDavVersionDecision.RemoteNewer
            else -> CalDavVersionDecision.EqualVersionConflict
        }

        return CalDavConflictReport(
            uid = local.uid,
            decision = decision,
            etagRelation = etagRelation,
            localSequence = local.effectiveSequence,
            remoteSequence = remote.effectiveSequence,
        )
    }

    fun <T> resolve(
        local: VersionedCalDavRecord<T>,
        remote: VersionedCalDavRecord<T>,
        policy: CalDavConflictPolicy = CalDavConflictPolicy.Ask,
    ): CalDavResolution<T> {
        val report = inspect(local.version, remote.version)
        return when (report.decision) {
            CalDavVersionDecision.RemoteNewer ->
                CalDavResolution.Resolved(remote.record, ChosenSide.Remote, report)
            CalDavVersionDecision.LocalNewer ->
                CalDavResolution.Resolved(local.record, ChosenSide.Local, report)
            CalDavVersionDecision.SameVersion ->
                CalDavResolution.Resolved(remote.record, ChosenSide.Remote, report)
            CalDavVersionDecision.EqualVersionConflict -> when (policy) {
                CalDavConflictPolicy.Ask -> CalDavResolution.Conflict(
                    local = local.record,
                    remote = remote.record,
                    report = report,
                )
                CalDavConflictPolicy.LocalWins ->
                    CalDavResolution.Resolved(local.record, ChosenSide.Local, report)
                CalDavConflictPolicy.RemoteWins ->
                    CalDavResolution.Resolved(remote.record, ChosenSide.Remote, report)
            }
        }
    }
}

/** Adapters for the record models already used by the repository. */
fun CalEvent.toCalDavRecordVersion(sequence: Int? = null): CalDavRecordVersion =
    CalDavRecordVersion(uid = uid ?: id, etag = etag, sequence = sequence)

fun CalTask.toCalDavRecordVersion(sequence: Int? = null): CalDavRecordVersion =
    CalDavRecordVersion(uid = uid ?: id, etag = etag, sequence = sequence)

fun JournalEntry.toCalDavRecordVersion(sequence: Int? = null): CalDavRecordVersion =
    CalDavRecordVersion(uid = uid ?: id, etag = etag, sequence = sequence)
