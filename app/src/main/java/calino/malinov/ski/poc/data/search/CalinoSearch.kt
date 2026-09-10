package calino.malinov.ski.poc.data.search

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.Contact
import calino.malinov.ski.poc.state.searchContacts
import calino.malinov.ski.poc.data.model.placementDate
import calino.malinov.ski.poc.data.parser.ParsedQuickAdd
import calino.malinov.ski.poc.data.parser.PocQuickAddKind
import calino.malinov.ski.poc.data.parser.parseDateNavigation
import calino.malinov.ski.poc.data.parser.parseQuickAdd
import java.time.LocalDate
import java.util.Locale

sealed interface CalinoSearchResult {
    val stableId: String
    data class NavigateDate(val date: LocalDate) : CalinoSearchResult { override val stableId = "date:$date" }
    data class AddEvent(val parsed: ParsedQuickAdd, val raw: String) : CalinoSearchResult { override val stableId = "add:$raw" }
    data class Event(val event: CalEvent, val calendarName: String? = null) : CalinoSearchResult { override val stableId = "event:${event.id}" }
    data class Task(val task: CalTask) : CalinoSearchResult { override val stableId = "task:${task.id}" }
    data class Journal(val journal: JournalEntry) : CalinoSearchResult { override val stableId = "journal:${journal.id}" }
    data class Contact(val contact: calino.malinov.ski.poc.data.model.Contact) : CalinoSearchResult { override val stableId = "contact:${contact.id}" }
}

data class CalinoSearchGroups(
    val actions: List<CalinoSearchResult> = emptyList(),
    val events: List<CalinoSearchResult.Event> = emptyList(),
    val tasks: List<CalinoSearchResult.Task> = emptyList(),
    val journals: List<CalinoSearchResult.Journal> = emptyList(),
    val contacts: List<CalinoSearchResult.Contact> = emptyList(),
) {
    val isEmpty: Boolean get() = actions.isEmpty() && events.isEmpty() && tasks.isEmpty() && journals.isEmpty() && contacts.isEmpty()
}

/** Fast, allocation-conscious local search over the repository snapshot. */
fun searchCalino(
    snapshot: CalinoSnapshot,
    query: String,
    baseDate: LocalDate,
    limitPerGroup: Int = 8,
    contactsEnabled: Boolean = true,
): CalinoSearchGroups {
    val normalized = query.trim().replace(Regex("\\s+"), " ")
    if (normalized.isEmpty()) return CalinoSearchGroups()
    val needle = normalized.lowercase(Locale.US)
    val date = parseDateNavigation(normalized, baseDate)
    val actions = if (date != null) {
        listOf(CalinoSearchResult.NavigateDate(date))
    } else {
        listOf(CalinoSearchResult.AddEvent(parseQuickAdd(PocQuickAddKind.Event, normalized, baseDate), normalized))
    }
    val calendarNames = snapshot.calendars.associate { it.id to it.name }

    // Expanded CalDAV occurrences share a UID. One representative is enough in
    // search; prefer the nearest upcoming occurrence, then the latest past one.
    val matchingEvents = snapshot.events.asSequence()
        .filter { event -> listOf(event.title, event.notes, event.location, event.categories.joinToString(" "), calendarNames[event.calendarId])
            .any { it?.lowercase(Locale.US)?.contains(needle) == true } }
        .groupBy { it.uid ?: it.id }
        .values
        .map { series -> series.minWithOrNull(compareBy<CalEvent> { distanceFrom(it.placementDate(), baseDate) }.thenBy { it.placementDate() })!! }
        .sortedWith(compareBy<CalEvent> { relevance(it.title, needle) }.thenBy { distanceFrom(it.placementDate(), baseDate) }.thenBy { it.title.lowercase(Locale.US) })
        .take(limitPerGroup)
        .map { CalinoSearchResult.Event(it, calendarNames[it.calendarId]) }

    val matchingTasks = snapshot.tasks.asSequence()
        .filter { task -> listOf(task.title, task.notes, task.category).any { it?.lowercase(Locale.US)?.contains(needle) == true } }
        .sortedWith(compareBy<CalTask> { relevance(it.title, needle) }.thenBy { distanceFrom(it.due, baseDate) }.thenBy { it.title.lowercase(Locale.US) })
        .take(limitPerGroup).map { CalinoSearchResult.Task(it) }.toList()

    val matchingJournals = snapshot.journals.asSequence()
        .filter { it.title.lowercase(Locale.US).contains(needle) || it.body.lowercase(Locale.US).contains(needle) }
        .sortedWith(compareBy<JournalEntry> { relevance(it.title, needle) }.thenByDescending { it.date })
        .take(limitPerGroup).map { CalinoSearchResult.Journal(it) }.toList()

    val matchingContacts = if (contactsEnabled) {
        searchContacts(snapshot.contacts, normalized)
            .take(limitPerGroup)
            .map { CalinoSearchResult.Contact(it) }
    } else {
        emptyList()
    }

    return CalinoSearchGroups(actions, matchingEvents, matchingTasks, matchingJournals, matchingContacts)
}

private fun relevance(title: String, needle: String): Int {
    val value = title.lowercase(Locale.US)
    return when { value == needle -> 0; value.startsWith(needle) -> 1; else -> 2 }
}

private fun distanceFrom(date: LocalDate?, base: LocalDate): Long =
    date?.toEpochDay()?.minus(base.toEpochDay())?.let { kotlin.math.abs(it) } ?: Long.MAX_VALUE
