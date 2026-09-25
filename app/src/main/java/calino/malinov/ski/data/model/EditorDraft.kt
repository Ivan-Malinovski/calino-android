package calino.malinov.ski.data.model

import calino.malinov.ski.data.parser.PocQuickAddKind
import calino.malinov.ski.data.parser.parseQuickAdd
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The fields the natural-language line can fill, and so also give up to a hand edit. */
enum class EditorField { Date, Time, Duration, Location }

enum class RecurrenceFreq { Daily, Weekly, Monthly, Yearly }

/**
 * The whole editor state as one value, kept out of Compose so its merge and
 * mapping rules can be tested directly. The composable owns a single
 * `EditorDraft` and replaces it wholesale on every edit.
 */
data class EditorDraft(
    val kind: PocQuickAddKind,
    val editingId: String? = null,
    val rawInput: String = "",
    val title: String = "",
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val durationMinutes: Int? = null,
    val allDay: Boolean = false,
    val availability: Availability = Availability.Busy,
    val recurrence: String? = null,
    val location: String? = null,
    val calendarId: String = "personal",
    val categories: List<String> = emptyList(),
    val description: String? = null,
    val reminders: List<Reminder> = emptyList(),
    val travelTimeMinutes: Int? = null,
    val relatedTo: List<String> = emptyList(),
    /** Immediate parent when this draft creates or edits a subtask. */
    val parentTaskId: String? = null,
    val priority: Int = 0,
    val percentComplete: Int = 0,
    val taskStatus: String? = null,
    val completedAt: Instant? = null,
    val attendees: List<Attendee> = emptyList(),
    val color: Long = DefaultEventColor,
    /** Journal body; unused by the other two kinds. */
    val body: String = "",
    /**
     * Fields the person has set by hand. The parser stops writing to them so a
     * later keystroke in the natural-language line cannot undo a deliberate edit.
     */
    val touched: Set<EditorField> = emptySet(),
    /** CalDAV identity retained while editing an expanded occurrence. */
    val uid: String? = null,
    val href: String? = null,
    val etag: String? = null,
    val recurrenceId: Instant? = null,
    val recurrenceDate: LocalDate? = null,
    val sequence: Int? = null,
    val recurrenceChanged: Boolean = false,
    val recurrenceScope: RecurrenceEditScope = RecurrenceEditScope.All,
    /** CalendarContract recurrence without borrowing CalDAV identity fields. */
    val providerRecurring: Boolean = false,
    /** Event zone; see [CalEvent.zoneId]. Null keeps a floating/UTC original. */
    val zoneId: String? = null,
    val endZoneId: String? = null,
    /** Hidden VTODO DTSTART fields retained while the editor changes DUE. */
    val taskStartDate: LocalDate? = null,
    val taskStartTime: LocalTime? = null,
    /** An imported task with no DUE stays undated until its date/time is picked. */
    val taskDueAbsent: Boolean = false,
    val taskDueChanged: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null

    /** End of a timed event, derived from start plus duration. */
    val endTime: LocalTime?
        get() = startTime?.plusMinutes((durationMinutes ?: DefaultDurationMinutes).toLong())

    /** The end can spill past midnight, so it carries its own date. */
    val endDate: LocalDate
        get() {
            val start = startTime ?: return date
            val minutes = start.toSecondOfDay() / 60 + (durationMinutes ?: DefaultDurationMinutes)
            return date.plusDays((minutes / MinutesPerDay).toLong())
        }

    fun canSave(): Boolean = title.isNotBlank() &&
        (kind != PocQuickAddKind.Event || allDay || startTime == null || (durationMinutes ?: DefaultDurationMinutes) > 0) &&
        !(kind == PocQuickAddKind.Task && recurrence != null && parentTaskId != null) &&
        !(kind == PocQuickAddKind.Task && recurrenceScope == RecurrenceEditScope.Future &&
            (recurrence != null || recurrenceId != null || recurrenceDate != null))

    /** Moves the end, expressed as a duration so the rest of the app stays unchanged. */
    fun withEnd(endDate: LocalDate, endTime: LocalTime): EditorDraft {
        val start = startTime ?: return this
        val minutes = Duration.between(date.atTime(start), endDate.atTime(endTime)).toMinutes().toInt()
        return copy(durationMinutes = minutes, touched = touched + EditorField.Duration)
    }

    fun toNewEvent(): NewEvent = NewEvent(
        title = title.trim(),
        date = date,
        startTime = if (allDay) null else startTime,
        durationMinutes = if (allDay) null else durationMinutes ?: DefaultDurationMinutes,
        allDay = allDay || startTime == null,
        color = color,
        recurrence = recurrence,
        location = location?.trim()?.ifEmpty { null },
        notes = description?.trim()?.ifEmpty { null },
        attendees = attendees,
        calendarId = calendarId,
        availability = availability,
        categories = categories,
        reminders = reminders,
        travelTimeMinutes = travelTimeMinutes,
        zoneId = zoneId,
        endZoneId = endZoneId,
        relatedTo = relatedTo,
        uid = uid,
        href = href,
        etag = etag,
        recurrenceId = recurrenceId,
        recurrenceDate = recurrenceDate,
        sequence = sequence,
        recurrenceChanged = recurrenceChanged,
        recurrenceScope = recurrenceScope,
    )

    fun toNewTask(): NewTask = NewTask(
        title = title.trim(),
        due = date.takeUnless { taskDueAbsent && !taskDueChanged },
        color = color,
        category = categories.firstOrNull(),
        dueTime = startTime,
        startDate = taskStartDate,
        startTime = taskStartTime,
        notes = description?.trim()?.ifEmpty { null },
        reminder = reminders.firstOrNull(),
        calendarId = calendarId,
        parentTaskId = parentTaskId,
        priority = priority,
        percentComplete = percentComplete,
        status = taskStatus,
        completedAt = completedAt,
        recurrence = recurrence,
        uid = uid,
        href = href,
        etag = etag,
        recurrenceId = recurrenceId,
        recurrenceDate = recurrenceDate,
        sequence = sequence,
        recurrenceChanged = recurrenceChanged,
        recurrenceScope = recurrenceScope,
    )

    fun toNewJournal(): NewJournal = NewJournal(date = date, title = title.trim(), body = body.trim())

    companion object {
        const val DefaultDurationMinutes = 60
        const val DefaultEventColor = 0xFFC2697FL
        private const val MinutesPerDay = 24 * 60
    }
}

/**
 * Re-parses the natural-language line and writes the result into every field
 * the person has not edited by hand. The title always follows the line, since
 * the line *is* the title field.
 */
/**
 * Re-derives the parsed fields from what the person has typed.
 *
 * [defaultDurationMinutes] is the fallback for an event whose line says nothing
 * about length. It is a parameter rather than the constant because this runs on
 * every keystroke: seeding a draft and then calling this would overwrite the
 * user's chosen default a moment later.
 */
fun EditorDraft.applyInput(
    input: String,
    baseDate: LocalDate,
    defaultDurationMinutes: Int = EditorDraft.DefaultDurationMinutes,
): EditorDraft {
    val parsed = parseQuickAdd(kind, input, baseDate)
    return copy(
        rawInput = input,
        title = if (input.isBlank()) "" else parsed.title,
        date = if (EditorField.Date in touched) date else parsed.date,
        startTime = when {
            EditorField.Time in touched -> startTime
            kind == PocQuickAddKind.Event || kind == PocQuickAddKind.Task -> parsed.time
            else -> null
        },
        durationMinutes = when {
            EditorField.Duration in touched -> durationMinutes
            kind != PocQuickAddKind.Event -> durationMinutes
            else -> parsed.durationMinutes ?: defaultDurationMinutes
        },
        location = if (EditorField.Location in touched) location else parsed.location ?: location,
        recurrence = parsed.recurrence ?: recurrence,
        recurrenceChanged = recurrenceChanged || parsed.recurrence != null,
        body = if (kind == PocQuickAddKind.Journal) body else input.trim(),
    )
}

/** True when the natural-language line, rather than a hand edit, produced this field. */
fun EditorDraft.isParsed(field: EditorField, baseDate: LocalDate): Boolean {
    if (field in touched) return false
    val parsed = parseQuickAdd(kind, rawInput, baseDate)
    return when (field) {
        EditorField.Date -> parsed.date != baseDate
        EditorField.Time -> parsed.time != null
        EditorField.Duration -> parsed.durationMinutes != null
        EditorField.Location -> parsed.location != null
    }
}

/**
 * A new draft, seeded from the user's new-event defaults.
 *
 * The defaults are passed in rather than read here: this is a data model with
 * no view of the composition, and the parser may still overwrite either field
 * from what the person actually typed.
 */
fun blankEditorDraft(
    kind: PocQuickAddKind,
    date: LocalDate,
    title: String = "",
    defaultDurationMinutes: Int = EditorDraft.DefaultDurationMinutes,
    defaultReminderMinutes: Int? = null,
): EditorDraft = EditorDraft(
    kind = kind,
    date = date,
    durationMinutes = defaultDurationMinutes,
    reminders = defaultReminderMinutes?.let { listOf(Reminder(minutesBefore = it)) } ?: emptyList(),
).applyInput(title, date, defaultDurationMinutes)

/** Seeds the editor from a saved event so editing cannot silently drop a field. */
fun editorDraftFor(event: CalEvent): EditorDraft {
    val anchor = event.placementDate() ?: error("Event ${event.id} has no date")
    return EditorDraft(
        kind = PocQuickAddKind.Event,
        editingId = event.id,
        rawInput = event.title,
        title = event.title,
        date = anchor,
        startTime = event.start?.toLocalTime(),
        durationMinutes = event.durationMinutes,
        allDay = event.allDay,
        availability = event.availability,
        recurrence = event.recurrence,
        location = event.location,
        calendarId = event.calendarId,
        categories = event.categories,
        description = event.notes,
        reminders = event.reminders,
        travelTimeMinutes = event.travelTimeMinutes,
        zoneId = event.zoneId,
        endZoneId = event.endZoneId,
        relatedTo = event.relatedTo,
        attendees = event.attendees,
        color = event.color,
        body = event.title,
        // Everything came from a saved record, so nothing here is the parser's to overwrite.
        touched = EditorField.entries.toSet(),
        uid = event.uid,
        href = event.href,
        etag = event.etag,
        recurrenceId = event.recurrenceId,
        recurrenceDate = event.recurrenceDate,
        sequence = event.sequence,
        recurrenceChanged = false,
        // An expanded occurrence is a concrete detached target. Editing it
        // should affect that occurrence unless the person explicitly chooses
        // a wider scope; a series master still defaults to the whole series.
        recurrenceScope = if (event.providerRecurring || event.recurrenceId != null || event.recurrenceDate != null) {
            RecurrenceEditScope.This
        } else {
            RecurrenceEditScope.All
        },
        providerRecurring = event.providerRecurring,
    )
}

fun editorDraftFor(task: CalTask, fallbackDate: LocalDate): EditorDraft = EditorDraft(
    kind = PocQuickAddKind.Task,
    editingId = task.id,
    rawInput = task.title,
    title = task.title,
    date = task.due ?: fallbackDate,
    startTime = task.dueTime,
    categories = listOfNotNull(task.category),
    description = task.notes,
    reminders = listOfNotNull(task.reminder),
    priority = task.priority,
    percentComplete = task.percentComplete,
    taskStatus = task.status,
    completedAt = task.completedAt,
    recurrence = task.recurrence,
    color = task.color,
    body = task.title,
    touched = EditorField.entries.toSet(),
    uid = task.uid,
    href = task.href,
    etag = task.etag,
    recurrenceId = task.recurrenceId,
    recurrenceDate = task.recurrenceDate,
    sequence = task.sequence,
    taskStartDate = task.startDate,
    taskStartTime = task.startTime,
    taskDueAbsent = task.due == null,
    recurrenceScope = if (task.recurrenceId != null || task.recurrenceDate != null) RecurrenceEditScope.This else RecurrenceEditScope.All,
)

fun editorDraftFor(entry: JournalEntry): EditorDraft = EditorDraft(
    kind = PocQuickAddKind.Journal,
    editingId = entry.id,
    rawInput = entry.title,
    title = entry.title,
    date = entry.date,
    body = entry.body,
    touched = EditorField.entries.toSet(),
    uid = entry.uid,
    href = entry.href,
    etag = entry.etag,
)

private val UntilFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)

/** Builds the RRULE subset `occursOn` and `nextOccurrences` understand. */
fun recurrenceRule(freq: RecurrenceFreq, byDays: Set<DayOfWeek> = emptySet(), until: LocalDate? = null): String =
    buildString {
        append("FREQ=").append(freq.name.uppercase(Locale.US))
        if (freq == RecurrenceFreq.Weekly && byDays.isNotEmpty()) {
            append(";BYDAY=").append(byDays.sorted().joinToString(",", transform = ::dayCode))
        }
        if (until != null) append(";UNTIL=").append(until.atTime(23, 59, 59).format(UntilFormat))
    }

fun recurrenceFreqOf(rule: String?): RecurrenceFreq? {
    val value = rule?.uppercase(Locale.US)?.split(';')?.firstNotNullOfOrNull { part ->
        part.removePrefix("FREQ=").takeIf { it != part }
    } ?: return null
    return RecurrenceFreq.entries.firstOrNull { it.name.uppercase(Locale.US) == value }
}

fun recurrenceDaysOf(rule: String?): Set<DayOfWeek> = rule
    ?.uppercase(Locale.US)
    ?.split(';')
    ?.firstNotNullOfOrNull { part -> part.removePrefix("BYDAY=").takeIf { it != part } }
    ?.split(',')
    ?.mapNotNull(::dayForCode)
    ?.toSet()
    .orEmpty()

fun dayCode(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "MO"
    DayOfWeek.TUESDAY -> "TU"
    DayOfWeek.WEDNESDAY -> "WE"
    DayOfWeek.THURSDAY -> "TH"
    DayOfWeek.FRIDAY -> "FR"
    DayOfWeek.SATURDAY -> "SA"
    DayOfWeek.SUNDAY -> "SU"
}

private fun dayForCode(code: String): DayOfWeek? =
    DayOfWeek.entries.firstOrNull { dayCode(it) == code.trim() }
