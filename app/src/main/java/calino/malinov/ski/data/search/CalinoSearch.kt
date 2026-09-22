package calino.malinov.ski.data.search

import calino.malinov.ski.data.model.*
import calino.malinov.ski.data.parser.*
import calino.malinov.ski.data.repository.CalinoSnapshot
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

enum class CalinoSearchRecordType { Events, Tasks, Journal, Contacts }
enum class CalinoSearchDateMode { AnyTime, Past, Upcoming, Custom }

data class CalinoSearchOptions(
    val recordTypes: Set<CalinoSearchRecordType> = CalinoSearchRecordType.entries.toSet(),
    val calendarIds: Set<String> = emptySet(),
    val dateMode: CalinoSearchDateMode = CalinoSearchDateMode.AnyTime,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
)

sealed interface CalinoSearchResult {
    val stableId: String
    data class NavigateDate(val date: LocalDate) : CalinoSearchResult { override val stableId = "date:$date" }
    data class AddEvent(val parsed: ParsedQuickAdd, val raw: String) : CalinoSearchResult { override val stableId = "add:$raw" }
    data class Event(val event: CalEvent, val calendarName: String? = null) : CalinoSearchResult { override val stableId = eventSearchId(event) }
    data class Task(val task: CalTask) : CalinoSearchResult { override val stableId = taskSearchId(task) }
    data class Journal(val journal: JournalEntry) : CalinoSearchResult { override val stableId = journalSearchId(journal) }
    data class Contact(val contact: calino.malinov.ski.data.model.Contact) : CalinoSearchResult { override val stableId = contactSearchId(contact) }
}

data class CalinoSearchGroups(
    val actions: List<CalinoSearchResult> = emptyList(),
    val events: List<CalinoSearchResult.Event> = emptyList(),
    val tasks: List<CalinoSearchResult.Task> = emptyList(),
    val journals: List<CalinoSearchResult.Journal> = emptyList(),
    val contacts: List<CalinoSearchResult.Contact> = emptyList(),
) {
    val isEmpty get() = actions.isEmpty() && events.isEmpty() && tasks.isEmpty() && journals.isEmpty() && contacts.isEmpty()
    val hasRecordMatches get() = events.isNotEmpty() || tasks.isNotEmpty() || journals.isNotEmpty() || contacts.isNotEmpty()
    val recordStableIds: Set<String>
        get() = (events + tasks + journals + contacts).mapTo(linkedSetOf()) { it.stableId }
}

/**
 * Prefix hits cover dense groups. Sparse groups also take the established
 * fuzzy scorer's best current-snapshot results, which preserves substring,
 * typo and ordered-subsequence matches that token-prefix search cannot see.
 */
fun CalinoSearchGroups.withSparseSearchFallback(
    fallback: CalinoSearchGroups,
    enabledTypes: Set<CalinoSearchRecordType>,
    limitPerGroup: Int = 8,
): Set<String> = buildSet {
    addAll(recordStableIds)
    if (CalinoSearchRecordType.Events in enabledTypes && events.size < limitPerGroup) {
        addAll(fallback.events.map { it.stableId })
    }
    if (CalinoSearchRecordType.Tasks in enabledTypes && tasks.size < limitPerGroup) {
        addAll(fallback.tasks.map { it.stableId })
    }
    if (CalinoSearchRecordType.Journal in enabledTypes && journals.size < limitPerGroup) {
        addAll(fallback.journals.map { it.stableId })
    }
    if (CalinoSearchRecordType.Contacts in enabledTypes && contacts.size < limitPerGroup) {
        addAll(fallback.contacts.map { it.stableId })
    }
}

/** Ranked local search over records currently present in the repository snapshot. */
fun searchCalino(
    snapshot: CalinoSnapshot,
    query: String,
    baseDate: LocalDate,
    limitPerGroup: Int = 8,
    contactsEnabled: Boolean = true,
    journalsEnabled: Boolean = true,
    options: CalinoSearchOptions = CalinoSearchOptions(),
    /** AppSearch result identities. Null keeps the pure in-memory search path. */
    candidateRecordIds: Set<String>? = null,
): CalinoSearchGroups {
    val raw = query.trim().replace(Regex("\\s+"), " ")
    if (raw.isEmpty()) return CalinoSearchGroups()
    val needle = normalizeSearchText(raw)
    val navigationDate = parseDateNavigation(raw, baseDate)
    val actions = if (navigationDate != null) listOf(CalinoSearchResult.NavigateDate(navigationDate))
    else listOf(CalinoSearchResult.AddEvent(parseQuickAdd(PocQuickAddKind.Event, raw, baseDate), raw))
    val calendarNames = snapshot.calendars.associate { it.id to it.name }
    val visibleCalendars = snapshot.calendars.filter { it.visible }.associateBy { it.id }
    fun calendarAllowed(id: String) = id in visibleCalendars && (options.calendarIds.isEmpty() || id in options.calendarIds)

    val events = if (CalinoSearchRecordType.Events in options.recordTypes) snapshot.events.asSequence()
        .filter {
            calendarAllowed(it.calendarId) && dateAllowed(it.placementDate(), options, baseDate) &&
                (candidateRecordIds == null || eventSearchId(it) in candidateRecordIds)
        }
        .mapNotNull { event -> scoreFields(needle, event.title, listOf(event.notes, event.location, event.categories.joinToString(" "), calendarNames[event.calendarId]))?.let { event to it } }
        .groupBy { it.first.uid ?: it.first.id }
        .values.mapNotNull { series -> series.minWithOrNull(compareBy<Pair<CalEvent, Int>> { it.second }.thenBy { distanceFrom(it.first.placementDate(), baseDate) }) }
        .sortedWith(compareBy<Pair<CalEvent, Int>> { it.second }.thenBy { distanceFrom(it.first.placementDate(), baseDate) }.thenBy { normalizeSearchText(it.first.title) }.thenBy { it.first.id })
        .take(limitPerGroup).map { CalinoSearchResult.Event(it.first, calendarNames[it.first.calendarId]) }.toList()
    else emptyList()

    val tasks = if (CalinoSearchRecordType.Tasks in options.recordTypes) snapshot.tasks.asSequence()
        .filter {
            calendarAllowed(it.calendarId) && visibleCalendars[it.calendarId]?.showTasksInViews == true &&
                dateAllowed(it.due, options, baseDate) &&
                (candidateRecordIds == null || taskSearchId(it) in candidateRecordIds)
        }
        .mapNotNull { task -> scoreFields(needle, task.title, listOf(task.notes, task.category))?.let { task to it } }
        .sortedWith(compareBy<Pair<CalTask, Int>> { it.second }.thenBy { distanceFrom(it.first.due, baseDate) }.thenBy { normalizeSearchText(it.first.title) }.thenBy { it.first.id })
        .take(limitPerGroup).map { CalinoSearchResult.Task(it.first) }.toList()
    else emptyList()

    val journals = if (journalsEnabled && CalinoSearchRecordType.Journal in options.recordTypes) snapshot.journals.asSequence()
        .filter {
            dateAllowed(it.date, options, baseDate) && isJournalVisible(snapshot, it) &&
                (candidateRecordIds == null || journalSearchId(it) in candidateRecordIds)
        }
        .mapNotNull { journal -> scoreFields(needle, journal.title, listOf(journal.body))?.let { journal to it } }
        .sortedWith(compareBy<Pair<JournalEntry, Int>> { it.second }.thenByDescending { it.first.date }.thenBy { it.first.id })
        .take(limitPerGroup).map { CalinoSearchResult.Journal(it.first) }.toList()
    else emptyList()

    val contacts = if (contactsEnabled && CalinoSearchRecordType.Contacts in options.recordTypes && options.dateMode == CalinoSearchDateMode.AnyTime) {
        snapshot.contacts.asSequence()
            .filter { it.isSearchAvailable(snapshot) }
            .filter { candidateRecordIds == null || contactSearchId(it) in candidateRecordIds }
            .mapNotNull { contact -> scoreContact(needle, contact)?.let { contact to it } }
            .sortedWith(compareBy<Pair<Contact, Int>> { it.second }.thenBy { normalizeSearchText(it.first.derivedDisplayName()) }.thenBy { it.first.id })
            .take(limitPerGroup).map { CalinoSearchResult.Contact(it.first) }.toList()
    } else emptyList()

    return CalinoSearchGroups(actions, events, tasks, journals, contacts)
}

/**
 * Resolve a displayed record against the latest repository snapshot before
 * navigation. A search row can outlive the sync or visibility state that
 * produced it, so the copied model on the row is never the navigation source.
 */
fun resolveCurrentSearchResult(
    snapshot: CalinoSnapshot,
    result: CalinoSearchResult,
    journalsEnabled: Boolean,
    contactsEnabled: Boolean,
): CalinoSearchResult? {
    return when (result) {
        is CalinoSearchResult.NavigateDate, is CalinoSearchResult.AddEvent -> result
        is CalinoSearchResult.Event -> {
            val current = snapshot.events.firstOrNull {
                it.id == result.event.id && it.calendarId == result.event.calendarId
            } ?: return null
            if (snapshot.calendars.none { it.id == current.calendarId && it.visible }) return null
            CalinoSearchResult.Event(current, snapshot.calendars.firstOrNull { it.id == current.calendarId }?.name)
        }
        is CalinoSearchResult.Task -> {
            val current = snapshot.tasks.firstOrNull {
                it.id == result.task.id && it.calendarId == result.task.calendarId
            } ?: return null
            if (snapshot.calendars.none { it.id == current.calendarId && it.visible && it.showTasksInViews }) return null
            CalinoSearchResult.Task(current)
        }
        is CalinoSearchResult.Journal -> {
            if (!journalsEnabled) return null
            val current = snapshot.journals.firstOrNull { journalSearchId(it) == result.stableId } ?: return null
            if (!isJournalVisible(snapshot, current)) return null
            CalinoSearchResult.Journal(current)
        }
        is CalinoSearchResult.Contact -> {
            if (!contactsEnabled) return null
            val current = snapshot.contacts.firstOrNull { contactSearchId(it) == result.stableId } ?: return null
            if (!current.isSearchAvailable(snapshot)) return null
            CalinoSearchResult.Contact(current)
        }
    }
}

private fun isJournalVisible(snapshot: CalinoSnapshot, journal: JournalEntry): Boolean {
    val resource = journal.href ?: return true
    val source = snapshot.calendars.filter { calendar ->
        val collection = calendar.id.trimEnd('/')
        resource == collection || resource.startsWith("$collection/")
    }.maxByOrNull { it.id.length }
    return source?.visible == true
}

private fun Contact.isSearchAvailable(snapshot: CalinoSnapshot): Boolean {
    if (accountId.isBlank()) return true
    return snapshot.addressBooks.any { it.enabled && it.id == addressBookId }
}

private fun dateAllowed(date: LocalDate?, options: CalinoSearchOptions, baseDate: LocalDate) = when (options.dateMode) {
    CalinoSearchDateMode.AnyTime -> true
    CalinoSearchDateMode.Past -> date?.isBefore(baseDate) == true
    CalinoSearchDateMode.Upcoming -> date != null && !date.isBefore(baseDate)
    CalinoSearchDateMode.Custom -> date != null &&
        (options.customStart == null || !date.isBefore(options.customStart)) &&
        (options.customEnd == null || !date.isAfter(options.customEnd))
}

private fun scoreContact(needle: String, contact: Contact) = scoreFields(needle, contact.derivedDisplayName(), listOf(
    contact.emails.joinToString(" ") { it.value }, contact.phones.joinToString(" ") { it.value }, contact.nickname,
    contact.organization, contact.department, contact.title,
    contact.addresses.joinToString(" ") { listOf(it.street, it.city, it.region, it.postalCode, it.country).joinToString(" ") },
    contact.note, contact.categories.joinToString(" "), contact.urls.joinToString(" ") { it.value },
))

private fun scoreFields(needle: String, title: String, metadata: List<String?>): Int? {
    fuzzyScore(needle, normalizeSearchText(title))?.let { return it }
    return metadata.asSequence().mapNotNull { it?.let(::normalizeSearchText)?.let { value -> fuzzyScore(needle, value) } }.minOrNull()?.plus(100)
}

/** Smaller is better. Null deliberately rejects loose or noisy matches. */
internal fun fuzzyScore(needle: String, value: String): Int? {
    if (needle.isEmpty() || value.isEmpty()) return null
    if (value == needle) return 0
    if (value.startsWith(needle)) return 10
    val words = value.split(' ')
    if (words.any { it.startsWith(needle) }) return 20
    value.indexOf(needle).takeIf { it >= 0 }?.let { return 30 + it.coerceAtMost(20) }
    val maxEdits = if (needle.length < 5) 1 else 2
    val needleWords = needle.split(' ')
    if (needleWords.size > 1) {
        val tokenScores = needleWords.map { queryWord ->
            words.minOfOrNull { valueWord ->
                when {
                    valueWord == queryWord -> 0
                    valueWord.startsWith(queryWord) -> 1
                    else -> boundedEditDistance(queryWord, valueWord, if (queryWord.length < 5) 1 else 2) + 1
                }
            }
        }
        if (tokenScores.all { it != null && it <= 3 }) return 35 + tokenScores.filterNotNull().sum()
    }
    words.minOfOrNull { boundedEditDistance(needle, it, maxEdits) }?.takeIf { it <= maxEdits }?.let { return 45 + it * 5 }
    if (needle.length < 3) return null
    var at = 0
    var first = -1
    var last = -1
    value.forEachIndexed { index, char -> if (at < needle.length && char == needle[at]) { if (first < 0) first = index; last = index; at++ } }
    if (at != needle.length) return null
    val gaps = last - first + 1 - needle.length
    return if (gaps <= maxOf(2, needle.length / 2)) 70 + gaps else null
}

internal fun normalizeSearchText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

private fun boundedEditDistance(left: String, right: String, maximum: Int): Int {
    if (kotlin.math.abs(left.length - right.length) > maximum) return maximum + 1
    var previous = IntArray(right.length + 1) { it }
    left.forEachIndexed { leftIndex, leftChar ->
        val current = IntArray(right.length + 1); current[0] = leftIndex + 1
        var rowMinimum = current[0]
        right.forEachIndexed { rightIndex, rightChar ->
            current[rightIndex + 1] = minOf(current[rightIndex] + 1, previous[rightIndex + 1] + 1, previous[rightIndex] + if (leftChar == rightChar) 0 else 1)
            rowMinimum = minOf(rowMinimum, current[rightIndex + 1])
        }
        if (rowMinimum > maximum) return maximum + 1
        previous = current
    }
    return previous[right.length]
}

private fun distanceFrom(date: LocalDate?, base: LocalDate) = date?.toEpochDay()?.minus(base.toEpochDay())?.let { kotlin.math.abs(it) } ?: Long.MAX_VALUE
