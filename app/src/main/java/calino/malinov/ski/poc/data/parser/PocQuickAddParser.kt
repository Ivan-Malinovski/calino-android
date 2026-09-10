package calino.malinov.ski.poc.data.parser

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.placementDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
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
 * Small deterministic parser for the visual POC. It deliberately handles the
 * handoff vocabulary (today/tomorrow, clock time, duration and a trailing
 * location) without pretending to be the production natural-language engine.
 */
fun parseQuickAdd(kind: PocQuickAddKind, input: String, baseDate: LocalDate): ParsedQuickAdd {
    val raw = input.trim().replace(Regex("\\s+"), " ")
    val lower = raw.lowercase(Locale.US)
    val date = when {
        Regex("\\btomorrow\\b").containsMatchIn(lower) -> baseDate.plusDays(1)
        Regex("\\btoday\\b").containsMatchIn(lower) -> baseDate
        else -> if (kind == PocQuickAddKind.Event) parseNaturalDate(lower, baseDate) ?: baseDate
            else parseExplicitDate(lower, baseDate) ?: baseDate
    }
    val timePattern = "\\b(?:at\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b(?!\\s*(?:h|hr|hrs|hour|hours|min|mins|minute|minutes)\\b)"
    // Date day numbers are not clock times: mask them before looking for the
    // first time token ("Dinner May 20 at 7" must choose 7, not 20:00).
    val timeSource = raw
        .replace(Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b"), "")
        .replace(Regex("\\b(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:,?\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b\\d{1,2}(?:st|nd|rd|th)?\\s+of\\s+[A-Za-z]+(?:\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE), "")
    val timeMatch = Regex(timePattern, RegexOption.IGNORE_CASE).find(timeSource)
    val time = timeMatch?.let { match ->
        val hour = match.groupValues[1].toIntOrNull() ?: return@let null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3].lowercase(Locale.US)
        val normalizedHour = when {
            meridiem == "pm" && hour < 12 -> hour + 12
            meridiem == "am" && hour == 12 -> 0
            meridiem.isEmpty() -> hour
            else -> hour
        }
        if (normalizedHour in 0..23 && minute in 0..59) LocalTime.of(normalizedHour, minute) else null
    }
    val duration = Regex("\\b(?:for\\s+)?(\\d+)\\s*(h|hr|hrs|hour|hours|min|mins|minute|minutes)\\b", RegexOption.IGNORE_CASE)
        .find(raw)
        ?.let { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return@let null
            if (match.groupValues[2].lowercase(Locale.US).startsWith("h")) amount * 60 else amount
        }
    val location = Regex("\\b(?:at|in|@)\\s+([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ0-9 .'-]+)$", RegexOption.IGNORE_CASE)
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.matches(Regex("\\d.*")) }
    val recurrence = parseRecurrence(lower, date)

    val title = raw
        .replace(Regex("\\b(?:today|tomorrow)\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:next|last)\\s+(?:week|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:on|this)\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:every|each)\\s+(?:(?:other|\\d+)\\s+)?(?:day|weekday|week|month|year|monday|tuesday|wednesday|thursday|friday|saturday|sunday)s?\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:on\\s+)?(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:,?\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex(timePattern, RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:for\\s+)?\\d+\\s*(?:h|hr|hrs|hour|hours|min|mins|minute|minutes)\\b", RegexOption.IGNORE_CASE), "")
        .let { value -> if (location != null) value.removeSuffix(location).removeSuffix("at ").removeSuffix("in ").removeSuffix("@ ") else value }
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', '.', ';', ':', '-')
        .removeSuffix(" for")
        .trim()
        .ifEmpty { when (kind) { PocQuickAddKind.Event -> "New event"; PocQuickAddKind.Task -> "New task"; PocQuickAddKind.Journal -> "Journal entry" } }

    return ParsedQuickAdd(kind, title, date, time, duration, location, recurrence, raw)
}

/** Parses a complete date phrase; used by search to distinguish navigation from creation. */
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

private fun parseNaturalDate(input: String, baseDate: LocalDate): LocalDate? {
    if (Regex("\\btomorrow\\b").containsMatchIn(input)) return baseDate.plusDays(1)
    if (Regex("\\byesterday\\b").containsMatchIn(input)) return baseDate.minusDays(1)
    if (Regex("\\btoday\\b").containsMatchIn(input)) return baseDate
    if (Regex("\\bnext week\\b").containsMatchIn(input)) return baseDate.plusWeeks(1)
    if (Regex("\\blast week\\b").containsMatchIn(input)) return baseDate.minusWeeks(1)
    if (Regex("\\bnext month\\b").containsMatchIn(input)) return baseDate.plusMonths(1)
    if (Regex("\\blast month\\b").containsMatchIn(input)) return baseDate.minusMonths(1)
    Regex("\\bin\\s+(\\d+)\\s+(day|week|month|year)s?\\b").find(input)?.let { match ->
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2]) {
            "day" -> baseDate.plusDays(amount)
            "week" -> baseDate.plusWeeks(amount)
            "month" -> baseDate.plusMonths(amount)
            else -> baseDate.plusYears(amount)
        }
    }
    Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b").find(input)?.groupValues?.get(1)?.let {
        runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()?.let { date -> return date }
    }
    val weekdays = DayOfWeek.entries.associateBy { it.name.lowercase(Locale.US) }
    Regex("\\b(?:(next|last|this)\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")
        .find(input)?.let { match ->
            val day = weekdays.getValue(match.groupValues[2])
            return when (match.groupValues[1]) {
                "last" -> baseDate.with(TemporalAdjusters.previous(day))
                "this" -> baseDate.with(TemporalAdjusters.nextOrSame(day))
                else -> baseDate.with(TemporalAdjusters.next(day))
            }
        }
    Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+of\\s+([a-z]+)(?:\\s+(\\d{4}))?\\b").find(input)?.let { match ->
        val month = monthFor(match.groupValues[2]) ?: return@let
        val year = match.groupValues[3].toIntOrNull() ?: baseDate.year
        runCatching { LocalDate.of(year, month, match.groupValues[1].toInt()) }.getOrNull()?.let { return it }
    }
    Regex("^([a-z]+)$").matchEntire(input.trim())?.groupValues?.get(1)?.let { word ->
        monthFor(word)?.let { month ->
            val year = if (month.value < baseDate.monthValue) baseDate.year + 1 else baseDate.year
            return LocalDate.of(year, month, 1)
        }
    }
    Regex("^(\\d{4})$").matchEntire(input.trim())?.groupValues?.get(1)?.toIntOrNull()?.let {
        return LocalDate.of(it, 1, 1)
    }
    return parseExplicitDate(input, baseDate)
}

private fun parseRecurrence(input: String, anchor: LocalDate): String? {
    val match = Regex("\\b(?:every|each)\\s+(?:(other|\\d+)\\s+)?(day|weekday|week|month|year|monday|tuesday|wednesday|thursday|friday|saturday|sunday)s?\\b")
        .find(input) ?: return null
    val interval = when (val token = match.groupValues[1]) { "other" -> 2; "" -> 1; else -> token.toIntOrNull() ?: 1 }
    val unit = match.groupValues[2]
    val fields = mutableListOf<String>()
    when (unit) {
        "day" -> fields += "FREQ=DAILY"
        "weekday" -> { fields += "FREQ=WEEKLY"; fields += "BYDAY=MO,TU,WE,TH,FR" }
        "week" -> { fields += "FREQ=WEEKLY"; fields += "BYDAY=${dayCode(anchor.dayOfWeek)}" }
        "month" -> fields += "FREQ=MONTHLY"
        "year" -> fields += "FREQ=YEARLY"
        else -> { fields += "FREQ=WEEKLY"; fields += "BYDAY=${dayCode(DayOfWeek.valueOf(unit.uppercase(Locale.US)))}" }
    }
    if (interval > 1) fields += "INTERVAL=$interval"
    return fields.joinToString(";")
}

private fun dayCode(day: DayOfWeek): String = day.name.take(2)

private fun parseExplicitDate(input: String, baseDate: LocalDate): LocalDate? {
    val match = Regex("\\b(?:on\\s+)?([a-z]+)\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE).find(input) ?: return null
    val month = monthFor(match.groupValues[1]) ?: return null
    val day = match.groupValues[2].toIntOrNull() ?: return null
    val year = match.groupValues[3].toIntOrNull() ?: baseDate.year
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

private fun monthFor(value: String): Month? = Month.entries.firstOrNull {
    it.name.startsWith(value.take(3).uppercase(Locale.US))
}
