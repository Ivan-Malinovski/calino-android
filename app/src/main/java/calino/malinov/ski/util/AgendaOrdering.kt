package calino.malinov.ski.util

import calino.malinov.ski.data.model.CalEvent

/**
 * The chronological order used by the day-agenda cards.
 *
 * All-day records lead the list; timed records then follow their local start
 * time, with the id making equal starts deterministic.
 */
fun sortAgendaEvents(events: List<CalEvent>): List<CalEvent> = events.sortedWith(
    compareBy<CalEvent> { !it.allDay }
        .thenBy { it.start?.toLocalTime() }
        .thenBy { it.id },
)
