package calino.malinov.ski.util

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * Reads a time typed by hand: "14", "1400", "14:00", "930", "9.30",
 * "2pm", "2:30 PM". Null when it is not a time.
 */
fun parseTypedTime(input: String, locale: Locale = Locale.US): LocalTime? {
    fun normalized(value: String) = value.lowercase(Locale.ROOT)
        .filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
    var text = normalized(input.trim())
    val localizedMeridiems = DateFormatSymbols.getInstance(locale).amPmStrings
        .mapIndexed { index, label -> normalized(label) to if (index == 0) "am" else "pm" }
        .filter { it.first.isNotEmpty() }
        .toMutableList()
    if (locale.language == "de") {
        localizedMeridiems += listOf("vorm." to "am", "nachm." to "pm")
    }
    localizedMeridiems.sortedByDescending { it.first.length }.firstOrNull {
        text.endsWith(it.first)
    }?.let { (suffix, meridiem) -> text = text.dropLast(suffix.length) + meridiem }
    val match = Regex("""^(\d{1,4})(?:[:.h](\d{1,2}))?(am|pm|a|p)?$""").matchEntire(text) ?: return null
    val (lead, tail, meridiem) = match.destructured
    val (hour, minute) = when {
        tail.isNotEmpty() -> {
            if (lead.length > 2 || tail.length != 2) return null
            lead.toInt() to tail.toInt()
        }
        lead.length <= 2 -> lead.toInt() to 0
        else -> lead.dropLast(2).toInt() to lead.takeLast(2).toInt()
    }
    if (minute > 59) return null
    val hour24 = when (meridiem.firstOrNull()) {
        null -> hour.takeIf { it in 0..23 } ?: return null
        else -> {
            if (hour !in 1..12) return null
            hour % 12 + if (meridiem.first() == 'p') 12 else 0
        }
    }
    return LocalTime.of(hour24, minute)
}

// Accept the abbreviated and full month names shown by all supported UI languages.
private val MonthNames = listOf(
    listOf("jan"), listOf("feb"), listOf("mar", "mär", "maer"), listOf("apr"),
    listOf("may", "maj", "mai"), listOf("jun"), listOf("jul"), listOf("aug"),
    listOf("sep"), listOf("oct", "okt"), listOf("nov"), listOf("dec", "dez"),
)

/**
 * Reads a date typed by hand, day first: "18", "18/5", "18.5.26",
 * "2026-05-18", "18 May", "May 18 2026". Missing month and year come
 * from [reference]. Null when it is not a date.
 */
fun parseTypedDate(input: String, reference: LocalDate): LocalDate? {
    val text = input.trim().lowercase(Locale.ROOT).replace(",", " ")
    if (text.isEmpty()) return null
    val words = text.split(Regex("""[\s/.\-]+""")).filter { it.isNotEmpty() }
    val month = words.firstNotNullOfOrNull { word ->
        if (word.first().isLetter() && word.length >= 3) {
            MonthNames.indexOfFirst { aliases -> aliases.any { word.startsWith(it) } }.takeIf { it >= 0 }?.plus(1)
        } else {
            null
        }
    }
    val letters = words.filter { it.first().isLetter() }
    if (letters.size > (if (month != null) 1 else 0)) return null
    val numbers = words.filter { word -> word.all(Char::isDigit) }
    if (numbers.size + letters.size != words.size) return null
    val values = numbers.map { it.toInt() }

    val (day, monthValue, year) = when {
        month != null -> when (values.size) {
            1 -> Triple(values[0], month, null)
            2 -> Triple(values[0], month, values[1])
            else -> return null
        }
        numbers.firstOrNull()?.length == 4 -> {
            if (values.size != 3) return null
            Triple(values[2], values[1], values[0])
        }
        else -> when (values.size) {
            1 -> Triple(values[0], null, null)
            2 -> Triple(values[0], values[1], null)
            3 -> Triple(values[0], values[1], values[2])
            else -> return null
        }
    }
    val fullYear = when {
        year == null -> reference.year
        year < 100 -> 2000 + year
        else -> year
    }
    return try {
        LocalDate.of(fullYear, monthValue ?: reference.monthValue, day)
    } catch (_: DateTimeException) {
        null
    }
}
