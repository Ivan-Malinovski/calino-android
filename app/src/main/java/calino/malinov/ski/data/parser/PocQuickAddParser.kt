package calino.malinov.ski.data.parser

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.placementDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PocQuickAddKind { Event, Task, Journal }

data class ParsedQuickAdd(
    val kind: PocQuickAddKind,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val durationMinutes: Int? = null,
    val location: String? = null,
    val recurrence: String? = null,
    val body: String = title,
)

fun CalEvent.toQuickAddDraft(): ParsedQuickAdd = ParsedQuickAdd(
    kind = PocQuickAddKind.Event,
    title = title,
    date = placementDate() ?: error("Event $id has no date"),
    time = start?.toLocalTime(),
    durationMinutes = durationMinutes,
    location = location,
    recurrence = recurrence,
    body = title,
)

/**
 * The natural-language line behind quick add, the editor's typed fields and
 * "go to <date>" search.
 *
 * It is deterministic and offline -- no model, no network, no locale service --
 * and it aims at the same vocabulary the web app's quick add accepts, because
 * the two apps are one product: relative and absolute dates, weekdays, clock
 * times, casual times of day, time ranges, explicit durations, recurrence,
 * a trailing location, and the "remind me to ..." style prefixes that turn a
 * line into a task.
 *
 * Every phrase that is recognized is masked out of the text as it is read, so
 * the title is whatever is left. Masking happens in place (spaces, not
 * removal), which keeps the ranges of later matches valid and stops a date's
 * numbers being read again as a clock time ("Dinner May 20 at 7" is 7 pm on
 * May 20, not 20:00).
 */
fun parseQuickAdd(kind: PocQuickAddKind, input: String, baseDate: LocalDate): ParsedQuickAdd {
    val raw = input.trim().replace(Regex("\\s+"), " ")
    val text = MaskedText(raw)

    val spokenKind = if (kind == PocQuickAddKind.Journal) kind else detectSpokenKind(text, kind)
    // Recourse is found before the date and erased after it: the weekday a
    // series repeats on is also the day it starts ("gym every monday" is next
    // Monday), and the date reader needs to still see that word.
    val recurrence = findRecurrence(text, spokenKind)
    val date = readDate(text, spokenKind, baseDate)
    recurrence?.let { text.erase(it.second) }
    val timeRange = readTimeRange(text) ?: readSingleTime(text)
    val explicitDuration = readDuration(text)
    val location = readLocation(text)
    val duration = explicitDuration ?: timeRange?.durationMinutes

    return ParsedQuickAdd(
        kind = spokenKind,
        title = title(text, spokenKind),
        date = date,
        time = timeRange?.start,
        durationMinutes = duration,
        location = location,
        recurrence = recurrence?.first,
        body = if (spokenKind == PocQuickAddKind.Journal) raw else raw.trim(),
    )
}

/**
 * Parses a complete date phrase; used by search to distinguish navigation from
 * creation.
 */
fun parseDateNavigation(input: String, baseDate: LocalDate): LocalDate? {
    val normalized = input.trim().lowercase(Locale.US).removePrefix("go to ").trim()
    if (normalized.isEmpty()) return null
    val date = parseNaturalDate(normalized, baseDate) ?: return null
    val disposable = normalized
        .replace(Regex("\\b(?:today|tomorrow|yesterday)\\b"), "")
        .replace(Regex("\\b(?:next|last)\\s+(?:week|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b"), "")
        .replace(Regex("\\bin\\s+\\d+\\s+(?:day|week|month|year)s?\\b"), "")
        .replace(Regex("\\b(?:this\\s+)?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b"), "")
        .replace(Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b"), "")
        .replace(Regex("\\b(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+\\d{1,2}(?:,?\\s+\\d{4})?\\b"), "")
        .replace(Regex("\\b\\d{1,2}(?:st|nd|rd|th)?\\s+of\\s+(?:january|february|march|april|may|june|july|august|september|october|november|december)(?:\\s+\\d{4})?\\b"), "")
        .replace(Regex("^(?:january|february|march|april|may|june|july|august|september|october|november|december|\\d{4})$"), "")
        .replace(Regex("[,.]"), "").trim()
    return date.takeIf { disposable.isEmpty() }
}

/**
 * The line as a mutable character buffer that phrases are erased from as they
 * are recognized. Erasing to spaces keeps every later match's offsets valid.
 */
private class MaskedText(private val source: String) {
    private val chars = source.toCharArray()

    fun unmasked(): String = source

    fun text(): String = String(chars)

    fun lower(): String = text().lowercase(Locale.US)

    fun erase(range: IntRange) {
        for (index in range) if (index in chars.indices) chars[index] = ' '
    }

    /** Whether this exact phrase is still unread. */
    fun hasAndErase(regex: Regex): MatchResult? {
        val match = regex.find(text()) ?: return null
        erase(match.range)
        return match
    }
}

// -- Small talk the line can start with ----------------------------------

/**
 * "remind me to ..." and "reminder: ..." say the line is a task before it says
 * anything else, so they switch the editor to a task and drop the instruction
 * from the title. "todo ..." and "task ..." are only stripped when the line is
 * already a task: as calendar text they are ordinary words, and "task force
 * meeting" must not become a task called "force meeting".
 */
private val SpokenKindPrefixes = listOf(
    Regex("^\\s*remind\\s+me\\s+(?:to\\s+)?", RegexOption.IGNORE_CASE),
    Regex("^\\s*(?:reminder|remind)\\s*:\\s*", RegexOption.IGNORE_CASE),
)

private val TaskOnlyPrefixes = listOf(
    Regex("^\\s*(?:todo|to-do)\\s+", RegexOption.IGNORE_CASE),
    Regex("^\\s*task\\s+", RegexOption.IGNORE_CASE),
)

private fun detectSpokenKind(text: MaskedText, current: PocQuickAddKind): PocQuickAddKind {
    SpokenKindPrefixes.firstNotNullOfOrNull { it.find(text.text()) }?.let { match ->
        // A bare "remind me" is a title; the instruction needs a subject.
        if (match.range.first == 0 && text.text().substring(match.range.last + 1).isNotBlank()) {
            text.erase(match.range)
            return PocQuickAddKind.Task
        }
    }
    if (current == PocQuickAddKind.Task) {
        TaskOnlyPrefixes.firstNotNullOfOrNull { it.find(text.text()) }?.let { match ->
            if (match.range.first == 0 && text.text().substring(match.range.last + 1).isNotBlank()) {
                text.erase(match.range)
            }
        }
    }
    return current
}

// -- Dates ---------------------------------------------------------------

private class DatePhrase(val date: LocalDate, val range: IntRange, val phrase: String = "") {
    /** "tonight" names both the day and the hour, so the time reader still needs it. */
    val alsoATime: Boolean get() = phrase.lowercase(Locale.US).contains("tonight")
}

private val WeekdayNames = DayOfWeek.entries.associateBy { it.name.lowercase(Locale.US) }

private val MonthNames: List<Pair<String, Month>> = Month.entries.map { it.name.lowercase(Locale.US) to it } +
    Month.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.US).lowercase(Locale.US) to it }

private fun monthFor(value: String): Month? {
    val normalized = value.lowercase(Locale.US)
    return MonthNames.firstOrNull { (name, _) -> name == normalized }?.second
        ?: MonthNames.firstOrNull { (name, _) -> normalized.length >= 3 && name.startsWith(normalized.take(3)) }?.second
}

/** Reads the first date phrase the line offers, and erases it. */
private fun readDate(text: MaskedText, kind: PocQuickAddKind, baseDate: LocalDate): LocalDate {
    val phrase = findDatePhrase(text.text(), baseDate) ?: return baseDate
    // A journal is prose: "A clear Monday" is what the person wrote, not a
    // booking for next Monday, so a bare weekday is left in the entry.
    if (kind == PocQuickAddKind.Journal && BareWeekday.matches(phrase.phrase)) return baseDate
    if (!phrase.alsoATime) text.erase(phrase.range)
    return phrase.date
}

private val BareWeekday = Regex(
    "^(?:mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\.?$",
    RegexOption.IGNORE_CASE,
)

private fun findDatePhrase(input: String, baseDate: LocalDate): DatePhrase? =
    datePatterns(baseDate).firstNotNullOfOrNull { (regex, resolve) ->
        regex.find(input)?.let { match -> resolve(match)?.let { DatePhrase(it, match.range, match.value.trim()) } }
    }

private fun parseNaturalDate(input: String, baseDate: LocalDate): LocalDate? =
    parseNaturalDateWithRanges(input, baseDate)?.date

private fun parseNaturalDateWithRanges(input: String, baseDate: LocalDate): DatePhrase? =
    findDatePhrase(input.lowercase(Locale.US), baseDate)

/**
 * The phrases that name a day, longest-window first so "next friday" is not
 * read as plain "friday" and "in 2 weeks" is not read as a bare number.
 */
private fun datePatterns(baseDate: LocalDate): List<Pair<Regex, (MatchResult) -> LocalDate?>> = listOf(
    Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b") to { match ->
        runCatching { LocalDate.parse(match.value, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    },
    Regex("\\bin\\s+(\\d+)\\s+(day|week|month|year)s?\\b", RegexOption.IGNORE_CASE) to { match ->
        val amount = match.groupValues[1].toLongOrNull() ?: return@to null
        when (match.groupValues[2]) {
            "day" -> baseDate.plusDays(amount)
            "week" -> baseDate.plusWeeks(amount)
            "month" -> baseDate.plusMonths(amount)
            else -> baseDate.plusYears(amount)
        }
    },
    Regex("\\b(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?([a-z]+)(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE) to { match ->
        val month = monthFor(match.groupValues[2]) ?: return@to null
        val year = match.groupValues[3].toIntOrNull() ?: baseDate.year
        runCatching { LocalDate.of(year, month, match.groupValues[1].toInt()) }.getOrNull()
    },
    Regex("\\b(?:on\\s+)?([a-z]+)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE) to { match ->
        val month = monthFor(match.groupValues[1]) ?: return@to null
        val year = match.groupValues[3].toIntOrNull() ?: baseDate.year
        runCatching { LocalDate.of(year, month, match.groupValues[2].toInt()) }.getOrNull()
    },
    Regex("\\b(?:(next|last|this)\\s+)?(?:on\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\\b", RegexOption.IGNORE_CASE) to { match ->
        val day = weekdayFor(match.groupValues[2]) ?: return@to null
        when (match.groupValues[1]) {
            "last" -> baseDate.with(TemporalAdjusters.previous(day))
            "this" -> baseDate.with(TemporalAdjusters.nextOrSame(day))
            else -> baseDate.with(TemporalAdjusters.next(day))
        }
    },
    Regex("\\b(?:next|last)\\s+(week|month|year)\\b", RegexOption.IGNORE_CASE) to { match ->
        when (match.groupValues[1]) {
            "week" -> if (match.value.startsWith("last")) baseDate.minusWeeks(1) else baseDate.plusWeeks(1)
            "month" -> if (match.value.startsWith("last")) baseDate.minusMonths(1) else baseDate.plusMonths(1)
            else -> if (match.value.startsWith("last")) baseDate.minusYears(1) else baseDate.plusYears(1)
        }
    },
    Regex("\\b(?:end\\s+of\\s+(?:the\\s+)?(week|month))\\b", RegexOption.IGNORE_CASE) to { match ->
        if (match.groupValues[1] == "week") baseDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        else baseDate.with(TemporalAdjusters.lastDayOfMonth())
    },
    Regex("\\b(today|tonight)\\b", RegexOption.IGNORE_CASE) to { baseDate },
    Regex("\\btomorrow\\b") to { baseDate.plusDays(1) },
    Regex("\\byesterday\\b") to { baseDate.minusDays(1) },
    // A month on its own ("March", "next March") starts that month.
    Regex("^\\s*(?:in\\s+)?([a-z]+)\\s*$", RegexOption.IGNORE_CASE) to { match ->
        monthFor(match.groupValues[1])?.let { month ->
            val year = if (month.value < baseDate.monthValue) baseDate.year + 1 else baseDate.year
            LocalDate.of(year, month, 1)
        }
    },
)

private fun weekdayFor(value: String): DayOfWeek? {
    val normalized = value.lowercase(Locale.US)
    WeekdayNames[normalized]?.let { return it }
    return DayOfWeek.entries.firstOrNull { it.name.lowercase(Locale.US).startsWith(normalized) }
}

// -- Times ---------------------------------------------------------------

private class TimeSpan(val start: LocalTime, val durationMinutes: Int?)

/**
 * Time-of-day words, longest first so "tonight" is not read as "night" and
 * "midnight" not as "night" either. The hours are the everyday reading of the
 * word rather than a literal one: a line that says "tonight" means the evening.
 */
private val CasualTimes = listOf(
    Regex("\\b(?:at\\s+|this\\s+)?(?:midnight)\\b", RegexOption.IGNORE_CASE) to LocalTime.of(0, 0),
    Regex("\\b(?:at\\s+|this\\s+)?(?:noon|midday|lunchtime)\\b", RegexOption.IGNORE_CASE) to LocalTime.of(12, 0),
    Regex("\\b(?:at\\s+|this\\s+)?(?:tonight)\\b", RegexOption.IGNORE_CASE) to LocalTime.of(20, 0),
    Regex("\\b(?:in\\s+the\\s+|this\\s+)(morning)\\b", RegexOption.IGNORE_CASE) to LocalTime.of(9, 0),
    Regex("\\b(?:in\\s+the\\s+|this\\s+)(afternoon)\\b", RegexOption.IGNORE_CASE) to LocalTime.of(13, 0),
    Regex("\\b(?:in\\s+the\\s+|this\\s+)(evening)\\b", RegexOption.IGNORE_CASE) to LocalTime.of(19, 0),
    Regex("\\b(?:at\\s+)?(night)\\b", RegexOption.IGNORE_CASE) to LocalTime.of(21, 0),
)

/** A range needs no "for": "10-11", "2pm to 3:30pm", "between 9 and 10". */
private val TimeRangePattern = Regex(
    "(?:\\bfrom\\s+|\\bbetween\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\s*(?:-|–|—|to|until|till|til|and)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b",
    RegexOption.IGNORE_CASE,
)

/**
 * A clock time needs a cue: "at 9", "9:30", "9pm". A bare number is somebody's
 * count ("meeting 3 people"), not a time, and guessing at it put the whole
 * line an hour off.
 */
private val ClockTimePatterns = listOf(
    Regex("\\b(?:at|by)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE),
    Regex("\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE),
    Regex("\\b(\\d{1,2})\\s*(am|pm)\\b", RegexOption.IGNORE_CASE),
)

private val DurationPattern = Regex(
    "\\b(?:for\\s+)?(\\d+(?:[.,]\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m|days?)\\b",
    RegexOption.IGNORE_CASE,
)

private fun readTimeRange(text: MaskedText): TimeSpan? {
    val match = TimeRangePattern.find(text.text()) ?: return null
    val startHour = match.groupValues[1].toIntOrNull() ?: return null
    val startMinute = match.groupValues[2].toIntOrNull() ?: 0
    val endHour = match.groupValues[4].toIntOrNull() ?: return null
    val endMinute = match.groupValues[5].toIntOrNull() ?: 0
    var startMeridiem = match.groupValues[3].lowercase(Locale.US)
    val endMeridiem = match.groupValues[6].lowercase(Locale.US)
    // Two bare numbers are a range only when they could be clock hours on the
    // same half of the day; "18-20" without a cue is someone's dates or a
    // price, and reading it as a booking is worse than leaving it in the title.
    val cue = startMeridiem.isNotEmpty() || endMeridiem.isNotEmpty() || match.value.contains(':')
    val introduced = Regex("(?:from|between)\\s+$", RegexOption.IGNORE_CASE).containsMatchIn(
        text.text().take(match.range.first),
    )
    if (!cue && !introduced && (startHour > 12 || endHour > 12)) return null
    // "7 to 9pm" and "2-3:30pm" apply the one meridiem to both ends; "9am to
    // 5" hands it back to the start.
    if (startMeridiem.isEmpty()) startMeridiem = endMeridiem
    val start = clockTime(startHour, startMinute, startMeridiem) ?: return null
    val end = clockTime(endHour, endMinute, endMeridiem.ifEmpty { startMeridiem }) ?: return null
    var minutes = end.toSecondOfDay() / 60 - start.toSecondOfDay() / 60
    if (minutes <= 0) minutes += 24 * 60
    if (minutes > 24 * 60) return null
    text.erase(match.range)
    return TimeSpan(start, minutes)
}

private fun readSingleTime(text: MaskedText): TimeSpan? {
    // A time-of-day word is read first so it leaves the title, but it only
    // supplies the hour when no clock time is written: "tonight at 9" is 9 pm,
    // and the word is still the instruction, not part of what the event is
    // called.
    val casual = CasualTimes.firstNotNullOfOrNull { (regex, time) ->
        regex.find(text.text())?.let { match -> match to time }
    }
    casual?.let { text.erase(it.first.range) }
    ClockTimePatterns.forEach { regex ->
        val match = regex.find(text.text()) ?: return@forEach
        val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val meridiem = (match.groupValues.getOrNull(3)?.lowercase(Locale.US)) ?: ""
        val time = clockTime(hour, minute, meridiem) ?: return@forEach
        text.erase(match.range)
        return TimeSpan(time, null)
    }
    return casual?.let { TimeSpan(it.second, null) }
}

private fun clockTime(hour: Int, minute: Int, meridiem: String): LocalTime? {
    if (minute !in 0..59) return null
    val normalized = when {
        meridiem == "pm" && hour < 12 -> hour + 12
        meridiem == "am" && hour == 12 -> 0
        else -> hour
    }
    return if (normalized in 0..23) LocalTime.of(normalized, minute) else null
}

private fun readDuration(text: MaskedText): Int? {
    val match = DurationPattern.find(text.text()) ?: return null
    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].lowercase(Locale.US)
    val minutes = when {
        unit.startsWith("h") -> amount * 60
        unit.startsWith("d") -> amount * 24 * 60
        else -> amount
    }.toInt()
    if (minutes <= 0 || minutes > 7 * 24 * 60) return null
    text.erase(match.range)
    return minutes
}

// -- Location and recurrence --------------------------------------------

/**
 * A trailing "at X" is a place only when X cannot be read as a time: "at 2pm"
 * is a time, "at Café Lumen" is where. Leading prepositions are dropped from
 * the title along with the name.
 */
private fun readLocation(text: MaskedText): String? {
    val match = Regex("\\b(?:at|in|@)\\s+([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ0-9 .'&-]*)$", RegexOption.IGNORE_CASE)
        .find(text.text()) ?: return null
    val place = match.groupValues[1].trim().trimEnd(',', '.')
    if (place.isEmpty() || place.firstOrNull()?.isDigit() == true) return null
    if (place.lowercase(Locale.US) in DateWords || monthFor(place) != null) return null
    text.erase(match.range)
    return place
}

private val DateWords = setOf(
    "today", "tomorrow", "yesterday", "tonight", "noon", "midday", "midnight",
    "morning", "afternoon", "evening", "night", "week", "month", "year", "weekend",
)

/** The RRULE a line asks for, and the span it came from. */
private fun findRecurrence(text: MaskedText, kind: PocQuickAddKind): Pair<String, IntRange>? {
    if (kind == PocQuickAddKind.Journal) return null
    val every = Regex(
        "\\b(?:every|each)\\s+(?:(other|\\d+)\\s+)?(day|weekday|week|month|year|monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)s?\\b",
        RegexOption.IGNORE_CASE,
    ).find(text.text())
    if (every != null) {
        val interval = when (val token = every.groupValues[1].lowercase(Locale.US)) {
            "other" -> 2
            "" -> 1
            else -> token.toIntOrNull() ?: 1
        }
        val unit = every.groupValues[2].lowercase(Locale.US)
        val fields = mutableListOf<String>()
        when {
            unit == "day" -> fields += "FREQ=DAILY"
            unit == "weekday" -> { fields += "FREQ=WEEKLY"; fields += "BYDAY=MO,TU,WE,TH,FR" }
            unit == "week" -> fields += "FREQ=WEEKLY"
            unit == "month" -> fields += "FREQ=MONTHLY"
            unit == "year" -> fields += "FREQ=YEARLY"
            else -> {
                val day = weekdayFor(unit) ?: return null
                fields += "FREQ=WEEKLY"
                fields += "BYDAY=${day.name.take(2)}"
            }
        }
        if (interval > 1) fields += "INTERVAL=$interval"
        return fields.joinToString(";") to every.range
    }
    // "daily", "weekly", "monthly", "yearly": the same series without the
    // "every" in front, which is how most lines are actually written.
    val standalone = Regex("\\b(daily|weekly|monthly|yearly|annually)\\b", RegexOption.IGNORE_CASE)
        .find(text.text()) ?: return null
    val frequency = when (standalone.groupValues[1].lowercase(Locale.US)) {
        "daily" -> "DAILY"
        "weekly" -> "WEEKLY"
        "monthly" -> "MONTHLY"
        else -> "YEARLY"
    }
    return "FREQ=$frequency" to standalone.range
}

// -- Title ---------------------------------------------------------------

private val LeadingConnectors = listOf(
    "from", "at", "on", "in", "between", "for", "to", "until", "till", "by", "the", "of",
)

private fun title(text: MaskedText, kind: PocQuickAddKind): String {
    val leftovers = text.text()
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', '.', ';', ':', '-', '–', '—', '@')
    var title = leftovers
    // Removing a phrase can strand the word that introduced it: "meeting from
    // 2 to 3" leaves "meeting from". Drop those connectors at either end.
    var changed = true
    while (changed) {
        changed = false
        for (connector in LeadingConnectors) {
            val trailing = Regex("\\s+$connector$", RegexOption.IGNORE_CASE)
            if (trailing.containsMatchIn(title)) {
                title = title.replace(trailing, "").trim()
                changed = true
            }
        }
    }
    title = title.trim(' ', ',', '.', ';', ':', '-', '–', '—')
    return title.ifEmpty {
        when (kind) {
            PocQuickAddKind.Event -> "New event"
            PocQuickAddKind.Task -> "New task"
            PocQuickAddKind.Journal -> "Journal entry"
        }
    }
}
