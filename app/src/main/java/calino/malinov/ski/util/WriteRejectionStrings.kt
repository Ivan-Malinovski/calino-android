package calino.malinov.ski.util

import android.content.Context
import calino.malinov.ski.R
import calino.malinov.ski.data.repository.WriteRejectionCode
import calino.malinov.ski.data.repository.WriteResult

/** Localizes only repository-authored typed refusals; raw diagnostics remain untouched. */
fun WriteResult.Rejected.localizedReason(context: Context): String =
    presentationCode?.let { context.getString(it.stringResource()) } ?: reason

/** Localizes only exact messages authored by PendingChangePolicy; server diagnostics stay raw. */
fun localizedPendingFailure(context: Context, message: String): String {
    val status = QUEUE_INVALID_STATUS.matchEntire(message)?.groupValues?.get(1)?.toIntOrNull()
    val resource = when {
        status != null -> R.string.err_queue_server_refused_item_status
        message == "The change could not be saved." -> R.string.err_queue_fallback_save_failure
        message == "That item already exists on the server." -> R.string.err_queue_item_exists_on_server
        message == "The server rejected this calendar item as invalid." -> R.string.err_queue_invalid_calendar_item
        message == "The server is out of space for this calendar." -> R.string.err_queue_server_out_of_space
        message == "The server could not find that calendar or task. Refresh and try again." ->
            R.string.err_queue_calendar_or_task_not_found
        message == "That calendar is no longer on the server." -> R.string.err_queue_calendar_no_longer_on_server
        message == "That item is no longer on the server." -> R.string.err_queue_item_no_longer_on_server
        else -> return message
    }
    return if (status != null) context.getString(resource, status) else context.getString(resource)
}

private val QUEUE_INVALID_STATUS = Regex("The server refused this item as invalid \\((\\d+)\\)\\.")

private fun WriteRejectionCode.stringResource(): Int = when (this) {
    WriteRejectionCode.INVITATION_ALREADY_WAITING -> R.string.err_invitation_already_waiting
    WriteRejectionCode.REPEATING_TASK_NEEDS_DUE_DATE -> R.string.err_repeating_task_needs_due_date
    WriteRejectionCode.SUBTASK_CANNOT_REPEAT -> R.string.err_subtask_cannot_repeat
    WriteRejectionCode.TASK_CANNOT_CONTAIN_ITSELF -> R.string.err_task_cannot_contain_itself
    WriteRejectionCode.TASK_WITH_SUBTASKS_CANNOT_REPEAT -> R.string.err_task_with_subtasks_cannot_repeat
    WriteRejectionCode.FUTURE_FIXTURE_OCCURRENCES_UNSUPPORTED -> R.string.err_future_fixture_occurrences_unsupported
    WriteRejectionCode.DEVICE_CALENDAR_EDITING_DISABLED -> R.string.err_device_calendar_editing_disabled
    WriteRejectionCode.INVITATIONS_REQUIRE_SCHEDULING_CALENDAR -> R.string.err_invitations_require_scheduling_calendar
    WriteRejectionCode.NO_CALENDAR_CONNECTED -> R.string.err_no_calendar_connected
    WriteRejectionCode.NO_WRITABLE_ADDRESS_BOOK -> R.string.err_no_writable_address_book
    WriteRejectionCode.ONLY_ORGANIZER_CAN_EDIT_INVITATION -> R.string.err_only_organizer_can_edit_invitation
    WriteRejectionCode.ONLY_TIMED_EVENTS_CAN_BE_RESIZED -> R.string.err_only_timed_events_can_be_resized
    WriteRejectionCode.RECURRING_EVENT_MOVE_SINGLE_OCCURRENCE -> R.string.err_recurring_event_move_single_occurrence
    WriteRejectionCode.RECURRING_EVENT_EDIT_FROM_DETAIL -> R.string.err_recurring_event_edit_from_detail
    WriteRejectionCode.RECURRING_TASK_SCOPE_UNSUPPORTED -> R.string.err_recurring_task_scope_unsupported
    WriteRejectionCode.INVITATION_RESPONSE_SCOPE -> R.string.err_invitation_response_scope
    WriteRejectionCode.CALENDAR_EVENT_UNAVAILABLE -> R.string.err_calendar_event_unavailable
    WriteRejectionCode.CHANGE_CANNOT_BE_UNDONE -> R.string.err_change_cannot_be_undone
    WriteRejectionCode.CONTACT_NO_SERVER_RESOURCE_URL -> R.string.err_contact_no_server_resource_url
    WriteRejectionCode.CONTACT_UNAVAILABLE -> R.string.err_contact_unavailable
    WriteRejectionCode.CONTACT_ADDRESS_BOOK_UNCONNECTED -> R.string.err_contact_address_book_unconnected
    WriteRejectionCode.DESTINATION_CALENDAR_UNCONNECTED -> R.string.err_destination_calendar_unconnected
    WriteRejectionCode.DETACHED_OCCURRENCE_UNAVAILABLE -> R.string.err_detached_occurrence_unavailable
    WriteRejectionCode.EVENT_MOVE_UNSAFE -> R.string.err_event_move_unsafe
    WriteRejectionCode.EVENT_NO_CURRENT_SERVER_VERSION -> R.string.err_event_no_current_server_version
    WriteRejectionCode.EVENT_NO_DATE_FOR_DUE -> R.string.err_event_no_date_for_due
    WriteRejectionCode.EVENT_RAW_CALENDAR_UNAVAILABLE -> R.string.err_event_raw_calendar_unavailable
    WriteRejectionCode.EVENT_CALENDAR_UNCONNECTED -> R.string.err_event_calendar_unconnected
    WriteRejectionCode.INVITATION_UNAVAILABLE -> R.string.err_invitation_unavailable
    WriteRejectionCode.INVITATION_RESPONSE_UNSUPPORTED -> R.string.err_invitation_response_unsupported
    WriteRejectionCode.INVITATION_CALENDAR_UNCONNECTED -> R.string.err_invitation_calendar_unconnected
    WriteRejectionCode.JOURNAL_ENTRY_UNAVAILABLE -> R.string.err_journal_entry_unavailable
    WriteRejectionCode.JOURNAL_CALENDAR_UNCONNECTED -> R.string.err_journal_calendar_unconnected
    WriteRejectionCode.OCCURRENCE_NO_LONGER_IN_SERIES -> R.string.err_occurrence_no_longer_in_series
    WriteRejectionCode.RECURRING_EVENT_MOVE_UNSAFE -> R.string.err_recurring_event_move_unsafe
    WriteRejectionCode.TASK_CHANGE_NOT_UNDONE -> R.string.err_task_change_not_undone
    WriteRejectionCode.TASK_NO_DUE_DATE_FOR_EVENT -> R.string.err_task_no_due_date_for_event
    WriteRejectionCode.TASK_UNAVAILABLE -> R.string.err_task_unavailable
    WriteRejectionCode.TASK_CALENDAR_UNCONNECTED -> R.string.err_task_calendar_unconnected
    WriteRejectionCode.DESTINATION_ITEM_CONFLICT -> R.string.err_destination_item_conflict
    WriteRejectionCode.PENDING_WRITE_QUEUE_UNAVAILABLE -> R.string.err_pending_write_queue_unavailable
    WriteRejectionCode.RECURRING_EVENT_UNREADABLE -> R.string.err_recurring_event_unreadable
    WriteRejectionCode.RECURRING_EVENT_RAW_CACHE_UNAVAILABLE -> R.string.err_recurring_event_raw_cache_unavailable
    WriteRejectionCode.INVITATION_ADDRESS_UNIDENTIFIED -> R.string.err_invitation_address_unidentified
    WriteRejectionCode.INVITATION_ADDRESS_UNVERIFIED -> R.string.err_invitation_address_unverified
    WriteRejectionCode.CALENDAR_SERVER_INVITATION_SCHEDULING_UNSUPPORTED ->
        R.string.err_calendar_server_invitation_scheduling_unsupported
    WriteRejectionCode.DETACHED_OCCURRENCE_NO_SERIES_RULE -> R.string.err_detached_occurrence_no_series_rule
    WriteRejectionCode.EVENT_NO_OCCURRENCE_TO_ANSWER -> R.string.err_event_no_occurrence_to_answer
    WriteRejectionCode.INVITATION_RAW_CACHE_UNAVAILABLE -> R.string.err_invitation_raw_cache_unavailable
    WriteRejectionCode.SERVER_INVITATION_SCHEDULING_UNSUPPORTED ->
        R.string.err_server_invitation_scheduling_unsupported
    WriteRejectionCode.ATTENDEE_ADDRESS_NOT_UNIQUE -> R.string.err_attendee_address_not_unique
    WriteRejectionCode.ATTENDEE_LINE_UNREADABLE -> R.string.err_attendee_line_unreadable
}
