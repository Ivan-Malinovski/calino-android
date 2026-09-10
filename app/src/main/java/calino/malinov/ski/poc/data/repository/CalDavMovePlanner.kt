package calino.malinov.ski.poc.data.repository

import calino.malinov.ski.poc.data.caldav.CalDavWriter
import calino.malinov.ski.poc.data.caldav.DavPrecondition
import calino.malinov.ski.poc.data.caldav.DiscoveredCalendar
import calino.malinov.ski.poc.data.caldav.normalizeEtag
import calino.malinov.ski.poc.data.model.CalEvent

/** The already-read source resource that a move may remove after the PUT. */
data class CalDavMoveSource(
    val calendar: DiscoveredCalendar,
    val href: String,
    val etag: String,
)

/** Inputs to the pure move validator/planner. [payload] is serialized ICS. */
data class CalDavMoveRequest(
    val members: List<CalEvent>,
    val target: DiscoveredCalendar,
    val payload: String,
    val source: CalDavMoveSource? = null,
    val targetHref: String? = null,
)

sealed interface CalDavMoveStep {
    /** Must be executed before [DeleteSource], if a source exists. */
    data class PutDestination(
        val calendar: DiscoveredCalendar,
        val href: String,
        val uid: String,
        val memberIds: List<String>,
        val payload: String,
        /** Unconditional makes a retried destination write idempotent. */
        val precondition: DavPrecondition = DavPrecondition.Unconditional,
    ) : CalDavMoveStep

    /** Conditional cleanup of the old resource after destination success. */
    data class DeleteSource(
        val calendar: DiscoveredCalendar,
        val href: String,
        val uid: String,
        val etag: String,
        val precondition: DavPrecondition,
    ) : CalDavMoveStep
}

data class CalDavMovePlan(
    val uid: String,
    val memberIds: List<String>,
    val destination: CalDavMoveStep.PutDestination,
    val sourceDeletion: CalDavMoveStep.DeleteSource?,
) {
    /** Execution order is part of the safety contract, not an implementation detail. */
    val steps: List<CalDavMoveStep>
        get() = listOfNotNull(destination, sourceDeletion)
}

enum class CalDavMoveValidationError {
    EmptyGroup,
    MissingUid,
    MixedUids,
    EmptyPayload,
    InvalidTarget,
    TargetReadOnly,
    TargetDoesNotSupportEvents,
    MissingSourceMetadata,
    SourceReadOnly,
    SourceHrefOutsideCollection,
    MissingSourceEtag,
    SourceAndTargetAreSameCollection,
    TargetHrefOutsideCollection,
}

data class CalDavMovePlanResult(
    val plan: CalDavMovePlan?,
    val error: CalDavMoveValidationError? = null,
    val message: String? = null,
) {
    val isValid: Boolean get() = plan != null

    init {
        require((plan == null) != (error == null)) {
            "A move result must contain either a plan or a validation error."
        }
    }

    companion object {
        fun valid(plan: CalDavMovePlan) = CalDavMovePlanResult(plan = plan)

        fun invalid(error: CalDavMoveValidationError, message: String) =
            CalDavMovePlanResult(plan = null, error = error, message = message)
    }
}

/**
 * Pure move planning. It performs no network or cache operation.
 *
 * A valid plan always contains an unconditional destination PUT first.  When
 * there is a server-backed source it then contains an `If-Match` DELETE.  A
 * caller can therefore execute the steps sequentially and safely queue only
 * the source cleanup if the second step fails.
 */
object CalDavMovePlanner {

    fun plan(request: CalDavMoveRequest): CalDavMovePlanResult {
        if (request.members.isEmpty()) {
            return invalid(CalDavMoveValidationError.EmptyGroup, "A move needs at least one event.")
        }
        val uid = request.members.first().uid ?: request.members.first().id
        if (uid.isBlank()) {
            return invalid(CalDavMoveValidationError.MissingUid, "The event has no UID to move.")
        }
        if (request.members.any { (it.uid ?: it.id) != uid }) {
            return invalid(
                CalDavMoveValidationError.MixedUids,
                "All members of a recurrence move must share one UID.",
            )
        }
        if (request.payload.isBlank()) {
            return invalid(CalDavMoveValidationError.EmptyPayload, "The destination calendar data is empty.")
        }
        if (request.target.url.isBlank()) {
            return invalid(CalDavMoveValidationError.InvalidTarget, "The destination calendar has no URL.")
        }
        if (request.target.readOnly) {
            return invalid(
                CalDavMoveValidationError.TargetReadOnly,
                "The destination calendar is read-only.",
            )
        }
        if (!request.target.acceptsEvents()) {
            return invalid(
                CalDavMoveValidationError.TargetDoesNotSupportEvents,
                "The destination calendar does not accept VEVENTs.",
            )
        }

        val source = request.source
        if (source == null && request.members.any { it.href != null }) {
            return invalid(
                CalDavMoveValidationError.MissingSourceMetadata,
                "A server-backed event needs source calendar and ETag metadata.",
            )
        }
        if (source != null) {
            if (source.calendar.readOnly) {
                return invalid(
                    CalDavMoveValidationError.SourceReadOnly,
                    "The source calendar is read-only, so its old copy cannot be removed.",
                )
            }
            if (sameCollection(source.calendar.url, request.target.url)) {
                return invalid(
                    CalDavMoveValidationError.SourceAndTargetAreSameCollection,
                    "The source and destination are the same calendar.",
                )
            }
            val sourceHref = CalDavWriter.resolveHref(source.calendar.url, source.href)
            if (!CalDavWriter.resourceIsInCollection(sourceHref, source.calendar.url)) {
                return invalid(
                    CalDavMoveValidationError.SourceHrefOutsideCollection,
                    "The source resource is outside its source calendar.",
                )
            }
            if (normalizeEtag(source.etag) == null) {
                return invalid(
                    CalDavMoveValidationError.MissingSourceEtag,
                    "A source ETag is required for a safe move cleanup.",
                )
            }
        }

        val destinationHref = request.targetHref
            ?.let { CalDavWriter.resolveHref(request.target.url, it) }
            ?: CalDavWriter.resolveHref(request.target.url, CalDavWriter.eventResourceFilename(uid))
        if (!CalDavWriter.resourceIsInCollection(destinationHref, request.target.url)) {
            return invalid(
                CalDavMoveValidationError.TargetHrefOutsideCollection,
                "The destination resource is outside the destination calendar.",
            )
        }

        val destination = CalDavMoveStep.PutDestination(
            calendar = request.target,
            href = destinationHref,
            uid = uid,
            memberIds = request.members.map(CalEvent::id),
            payload = request.payload,
        )
        val sourceDeletion = source?.let {
            CalDavMoveStep.DeleteSource(
                calendar = it.calendar,
                href = CalDavWriter.resolveHref(it.calendar.url, it.href),
                uid = uid,
                etag = normalizeEtag(it.etag)!!,
                precondition = DavPrecondition.Match(normalizeEtag(it.etag)!!),
            )
        }
        return CalDavMovePlanResult.valid(
            CalDavMovePlan(
                uid = uid,
                memberIds = request.members.map(CalEvent::id),
                destination = destination,
                sourceDeletion = sourceDeletion,
            ),
        )
    }

    private fun invalid(error: CalDavMoveValidationError, message: String) =
        CalDavMovePlanResult.invalid(error, message)

    private fun sameCollection(left: String, right: String): Boolean =
        left.trimEnd('/') == right.trimEnd('/')

    private fun DiscoveredCalendar.acceptsEvents(): Boolean =
        components.isEmpty() || components.any { it.equals("VEVENT", ignoreCase = true) }
}
