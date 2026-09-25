package calino.malinov.ski.data.caldav

import biweekly.component.VEvent
import calino.malinov.ski.util.meetingService

/**
 * The event's conference address: the first RFC 7986 `CONFERENCE` URI, else
 * the event `URL` when it points at a known meeting service (a plain `URL`
 * is often just a web page). Read-only; writes preserve both as foreign
 * properties.
 */
internal fun VEvent.readConferenceUrl(): String? =
    conferences.firstNotNullOfOrNull { it.uri?.trim()?.takeIf(String::isNotEmpty) }
        ?: url?.value?.trim()?.takeIf { it.isNotEmpty() && meetingService(it) != null }
