package calino.malinov.ski.poc.data.parser

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.placementDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.util.Locale

enum class PocQuickAddKind { Event, Task, Journal }

data class ParsedQuickAdd(
    val kind: PocQuickAddKind,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val durationMinutes: Int? = null,
    val location: String? = null,
    val body: String = title,
)

fun CalEvent.toQuickAddDraft(): ParsedQuickAdd = ParsedQuickAdd(
    kind = PocQuickAddKind.Event,
    title = title,
    date = placementDate() ?: error("Event $id has no date"),
    time = start?.toLocalTime(),
    durationMinutes = durationMinutes,
    location = location,
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
        else -> parseExplicitDate(lower, baseDate) ?: baseDate
    }
    val timePattern = "\\b(?:at\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b(?!\\s*(?:h|hr|hrs|hour|hours|min|mins|minute|minutes)\\b)"
    val timeMatch = Regex(timePattern, RegexOption.IGNORE_CASE)
        .find(raw)
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

    val title = raw
        .replace(Regex("\\b(?:today|tomorrow)\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:on\\s+)?(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:,?\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex(timePattern, RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:for\\s+)?\\d+\\s*(?:h|hr|hrs|hour|hours|min|mins|minute|minutes)\\b", RegexOption.IGNORE_CASE), "")
        .let { value -> if (location != null) value.removeSuffix(location).removeSuffix("at ").removeSuffix("in ").removeSuffix("@ ") else value }
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', '.', ';', ':', '-')
        .removeSuffix(" for")
        .trim()
        .ifEmpty { when (kind) { PocQuickAddKind.Event -> "New event"; PocQuickAddKind.Task -> "New task"; PocQuickAddKind.Journal -> "Journal entry" } }

    return ParsedQuickAdd(kind, title, date, time, duration, location, raw)
}

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
