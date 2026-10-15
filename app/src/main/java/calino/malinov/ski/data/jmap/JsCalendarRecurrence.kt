package calino.malinov.ski.data.jmap

import biweekly.Biweekly
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

private val Frequencies = setOf("yearly", "monthly", "weekly", "daily", "hourly", "minutely", "secondly")
private val Weekdays = setOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

/** A hostile or runaway series must not freeze the sync thread. */
private const val MaxMembershipSteps = 100_000
private const val MaxExcludedDates = 100_000

private val DateUntil = Regex("""(UNTIL=\d{8})(?!T)""")
private val FloatingUntil = Regex("""(UNTIL=\d{8}T\d{6})(?!Z)""")

/** Prefers the singular spelling when both are given; never duplicates rules. */
internal fun recurrenceRulesOf(event: JSONObject): List<JSONObject> {
    (event.opt("recurrenceRule") as? JSONObject)?.let { return listOf(it) }
    return jsonArrayOf(event.opt("recurrenceRules")).map(::objectOf)
}

private fun numberOf(value: Any?): Number? = when (value) {
    is Number -> value
    is String -> value.trim().toDoubleOrNull()
    else -> null
}

/** The RRULE value for [rule], written against [event]'s zone and day/time kind. */
internal fun recurrenceRuleText(rule: JSONObject, event: JSONObject): String {
    val frequency = stringOf(rule.opt("frequency"))?.lowercase()
    if (frequency == null || frequency !in Frequencies) throw JsCalendarException("Invalid JSCalendar recurrence frequency")
    val scale = rule.opt("rscale")
    if (!isNull(scale) && scale != "" && scale != "gregorian") {
        throw JsCalendarException("Non-Gregorian JSCalendar recurrence is unsupported")
    }
    val parts = mutableListOf("FREQ=${frequency.uppercase()}")
    (rule.opt("interval") as? Number)?.takeIf { it.toDouble() > 0 }?.let { parts += "INTERVAL=${it.toLong()}" }
    val count = rule.opt("count") as? Number
    val until = stringOf(rule.opt("until"))
    if (count != null) {
        parts += "COUNT=${count.toLong()}"
    } else if (until != null) {
        val allDay = event.opt("showWithoutTime") == true
        val tzid = stringOf(event.opt("timeZone"))
        val time = parseTime(until, tzid, allDay)
        val value = when {
            // A zoned DTSTART needs a UTC UNTIL (RFC 5545 3.3.10).
            !allDay && !time.utc && tzid != null ->
                IcsTime(
                    LocalDateTime.ofInstant(localInstant(time.local, tzid), ZoneOffset.UTC),
                    utc = true,
                    dateOnly = false,
                ).icalString()
            else -> time.icalString()
        }
        parts += "UNTIL=$value"
    }
    val byDay = rule.opt("byDay") as? JSONArray
    if (byDay != null) {
        val values = byDay.toList().map { entry ->
            val item = objectOf(entry)
            val day = (stringOf(item.opt("day")) ?: stringOf(entry) ?: "").uppercase()
            if (day !in Weekdays) throw JsCalendarException("Invalid JSCalendar recurrence weekday")
            val nth = (item.opt("nthOfPeriod") as? Number)?.toLong()?.takeIf { it != 0L }
            "${nth ?: ""}$day"
        }
        if (values.isNotEmpty()) parts += "BYDAY=${values.joinToString(",")}"
    }
    for ((key, name) in listOf(
        "byMonthDay" to "BYMONTHDAY",
        "byMonth" to "BYMONTH",
        "bySetPosition" to "BYSETPOS",
        "byYearDay" to "BYYEARDAY",
        "byWeekNo" to "BYWEEKNO",
        "byHour" to "BYHOUR",
        "byMinute" to "BYMINUTE",
        "bySecond" to "BYSECOND",
    )) {
        val values = jsonArrayOf(rule.opt(key))
        if (values.isEmpty()) continue
        val numbers = values.map { numberOf(it)?.takeIf { n -> n.toDouble() == Math.floor(n.toDouble()) } }
        if (numbers.any { it == null }) throw JsCalendarException("Unsupported JSCalendar recurrence $key")
        parts += "$name=${numbers.joinToString(",") { it!!.toLong().toString() }}"
    }
    stringOf(rule.opt("firstDayOfWeek"))?.uppercase()?.takeIf { it in Weekdays }?.let { parts += "WKST=$it" }
    return parts.joinToString(";")
}

/**
 * The local date-times a rule produces from [start], in order. The rule is
 * evaluated in a floating calendar, so a DST fold or gap can neither add nor
 * hide a member: membership is a comparison of LocalDateTimes.
 */
internal fun floatingOccurrences(ruleText: String, start: IcsTime): Sequence<LocalDateTime> = sequence {
    val text = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Calino//JMAP Recurrence//EN\r\nBEGIN:VEVENT\r\nUID:membership\r\n")
        append("DTSTART:")
        // Evaluated as UTC, because a floating or DATE start would be read in the
        // JVM's own zone, whose DST gaps would then move members. A day is
        // midnight, and a DATE UNTIL becomes the end of that day.
        append(IcsTime(start.local, utc = true, dateOnly = false).icalString()).append("\r\n")
        val rule = if (start.dateOnly) DateUntil.replace(ruleText, "$1T235959Z") else FloatingUntil.replace(ruleText, "$1Z")
        append("RRULE:").append(rule).append("\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")
    }
    val event = Biweekly.parse(text).first()?.events?.firstOrNull()
    if (event?.recurrenceRule?.value == null) throw JsCalendarException("Unsupported JSCalendar recurrence rule: $ruleText")
    val iterator = event.getDateIterator(TimeZone.getTimeZone("UTC"))
    while (iterator.hasNext()) {
        yield(LocalDateTime.ofInstant(iterator.next().toInstant(), ZoneOffset.UTC))
    }
}

/** A floating start from a JSCalendar start or override key; a trailing Z is dropped. */
private fun floatingStart(value: String, allDay: Boolean): IcsTime {
    val time = parseTime(value.removeSuffix("Z"), null, allDay)
    return if (allDay) time.copy(local = time.local.toLocalDate().atStartOfDay()) else time
}

/**
 * The override keys that the unmodified recurrence set already generates. Keys
 * are classified against the rule, not against moved DTSTARTs: a key the rules
 * generate needs no RDATE, one they do not (an extra date, or one past COUNT)
 * does.
 */
internal fun generatedOccurrences(event: JSONObject, recurrenceIds: List<String>): Set<String> {
    val generated = mutableSetOf<String>()
    val start = stringOf(event.opt("start"))
    if (start == null || recurrenceIds.isEmpty()) return generated
    val allDay = event.opt("showWithoutTime") == true
    val begin = floatingStart(start, allDay)
    val targets = recurrenceIds.map { it to floatingStart(it, allDay).local }
    for ((id, local) in targets) if (local == begin.local) generated += id
    val latest = targets.maxOf { it.second }
    val wanted = targets.groupBy({ it.second }, { it.first })
    val floating = event.copy().apply { remove("timeZone") }
    for (rule in recurrenceRulesOf(event)) {
        var steps = 0
        for (occurrence in floatingOccurrences(recurrenceRuleText(rule, floating), begin)) {
            if (occurrence > latest) break
            wanted[occurrence]?.let { generated += it }
            if (++steps > MaxMembershipSteps) {
                throw JsCalendarException("JSCalendar recurrence membership exceeds 100000 occurrences")
            }
        }
    }
    return generated
}

/** `RDATE`/`EXDATE`/`RECURRENCE-ID`, always written in the master's zone and kind. */
internal fun recurrenceDate(name: String, id: String, master: JSONObject): String =
    dateProperty(name, id, stringOf(master.opt("timeZone")), master.opt("showWithoutTime") == true)

/**
 * `excludedRecurrenceRules` as EXDATEs when the set is finite, else EXRULE.
 * An infinite set cannot be a finite EXDATE list.
 */
internal fun exclusionLines(event: JSONObject): List<String> {
    val rules = jsonArrayOf(event.opt("excludedRecurrenceRules"))
    if (rules.isEmpty()) return emptyList()
    val start = stringOf(event.opt("start"))
    val allDay = event.opt("showWithoutTime") == true
    val output = LinkedHashSet<String>()
    val floating = event.copy().apply { remove("timeZone") }
    for (value in rules) {
        val rule = objectOf(value)
        val serialized = recurrenceRuleText(rule, event)
        val bounded = rule.opt("count") is Number || rule.opt("until") is String
        if (start == null || !bounded) {
            output += "EXRULE:$serialized"
            continue
        }
        val dates = mutableListOf<String>()
        var truncated = false
        for (occurrence in floatingOccurrences(recurrenceRuleText(rule, floating), floatingStart(start, allDay))) {
            if (dates.size >= MaxExcludedDates) {
                truncated = true
                break
            }
            dates += recurrenceDate(
                "EXDATE",
                IcsTime(occurrence, utc = false, dateOnly = allDay).isoString(),
                event,
            )
        }
        if (truncated) output += "EXRULE:$serialized" else output += dates
    }
    return output.toList()
}
