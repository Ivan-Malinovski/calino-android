package calino.malinov.ski.data.jmap

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * A JSCalendar value that cannot be expressed as iCalendar, or a PatchObject
 * that is not valid. The event is skipped rather than written wrongly.
 */
class JsCalendarException(message: String) : IllegalArgumentException(message)

/** Read-side helpers that treat a wrong type as absent, as the web converter does. */
internal fun objectOf(value: Any?): JSONObject = value as? JSONObject ?: JSONObject()

internal fun stringOf(value: Any?): String? = value as? String

/** The entries of an object sorted by key, so output never depends on server key order. */
internal fun sortedEntries(value: Any?): List<Pair<String, Any?>> =
    objectOf(value).entryList().sortedBy { it.first }

/** The keys of a `{key: true}` set, sorted. */
internal fun enabledKeys(value: Any?): List<String> =
    sortedEntries(value).filter { it.second == true }.map { it.first }

/** RFC 5545 TEXT escaping. */
internal fun icsText(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char == '\\' -> append("\\\\")
            char == '\r' -> {
                append("\\n")
                if (index + 1 < value.length && value[index + 1] == '\n') index++
            }
            char == '\n' -> append("\\n")
            char == ';' -> append("\\;")
            char == ',' -> append("\\,")
            else -> append(char)
        }
        index++
    }
}

/** RFC 6868 parameter escaping, followed by RFC 5545 quoting. */
internal fun icsParameter(value: String): String {
    val escaped = buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char == '^' -> append("^^")
                char == '\r' -> {
                    append("^n")
                    if (index + 1 < value.length && value[index + 1] == '\n') index++
                }
                char == '\n' -> append("^n")
                char == '"' -> append("^'")
                else -> append(char)
            }
            index++
        }
    }
    return if (escaped.any { it == ',' || it == ':' || it == ';' || it.isWhitespace() }) "\"$escaped\"" else escaped
}

/** A URI value must not be able to start a new content line. */
internal fun icsUri(value: String): String = value.replace("\r", "%0D").replace("\n", "%0A")

/** Folds at 75 UTF-8 octets, counting the continuation space, and never splits a code point. */
internal fun foldLine(line: String): String {
    val out = StringBuilder()
    var length = 0
    var index = 0
    while (index < line.length) {
        val codePoint = line.codePointAt(index)
        val size = when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }
        if (length + size > 75) {
            out.append("\r\n ")
            length = 1
        }
        out.appendCodePoint(codePoint)
        length += size
        index += Character.charCount(codePoint)
    }
    return out.toString()
}

private val DateTimePattern =
    Regex("""^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z)?)?$""")

internal fun isUtcName(tzid: String?): Boolean = tzid == "UTC" || tzid == "Etc/UTC"

/** The IANA zone for [tzid]; an unknown name is unsupported rather than guessed. */
internal fun zoneFor(tzid: String): ZoneId {
    if (isUtcName(tzid)) return ZoneOffset.UTC
    return try {
        ZoneId.of(tzid)
    } catch (_: DateTimeException) {
        throw JsCalendarException("Unsupported JSCalendar timeZone: $tzid")
    }
}

/**
 * A parsed JSCalendar LocalDateTime or UTCDateTime. [utc] is a trailing `Z`
 * (or an explicit UTC zone); a date-only value carries midnight.
 */
internal data class IcsTime(val local: LocalDateTime, val utc: Boolean, val dateOnly: Boolean) {
    /** The iCalendar value text: `20261010`, `20261010T100000` or `20261010T100000Z`. */
    fun icalString(): String = if (dateOnly) {
        "%04d%02d%02d".format(local.year, local.monthValue, local.dayOfMonth)
    } else {
        "%04d%02d%02dT%02d%02d%02d%s".format(
            local.year, local.monthValue, local.dayOfMonth,
            local.hour, local.minute, local.second, if (utc) "Z" else "",
        )
    }

    /** The JSCalendar text form, as the web converter's `toString()`. */
    fun isoString(): String = if (dateOnly) {
        "%04d-%02d-%02d".format(local.year, local.monthValue, local.dayOfMonth)
    } else {
        "%04d-%02d-%02dT%02d:%02d:%02d".format(
            local.year, local.monthValue, local.dayOfMonth, local.hour, local.minute, local.second,
        ) + if (utc) "Z" else ""
    }
}

internal fun parseTime(value: String, tzid: String? = null, dateOnly: Boolean = false): IcsTime {
    val match = DateTimePattern.matchEntire(value)
        ?: throw JsCalendarException("Invalid JSCalendar date-time: $value")
    val groups = match.groupValues
    val numbers = groups.map { it.toIntOrNull() ?: 0 }
    val utc = !dateOnly && (groups[7].isNotEmpty() || isUtcName(tzid))
    // A named zone must exist even when the value turns out to be UTC.
    if (!dateOnly && !utc && tzid != null) zoneFor(tzid)
    val local = try {
        if (dateOnly) {
            LocalDate.of(numbers[1], numbers[2], numbers[3]).atStartOfDay()
        } else {
            LocalDateTime.of(numbers[1], numbers[2], numbers[3], numbers[4], numbers[5], numbers[6])
        }
    } catch (_: DateTimeException) {
        throw JsCalendarException("Invalid JSCalendar date-time: $value")
    }
    return IcsTime(local, utc, dateOnly)
}

internal fun dateProperty(name: String, value: String, tzid: String? = null, dateOnly: Boolean = false): String {
    val time = parseTime(value, tzid, dateOnly)
    val params = when {
        dateOnly -> ";VALUE=DATE"
        tzid != null && !isUtcName(tzid) && !value.endsWith("Z") -> ";TZID=${icsParameter(tzid)}"
        else -> ""
    }
    return "$name$params:${time.icalString()}"
}

/** A UTCDateTime as `20261010T100000Z`. */
internal fun utcStamp(value: String): String = parseTime(value, "UTC").icalString()

/**
 * RFC 8984: the first instant of a fold, and the pre-gap offset in a gap.
 * `ZonedDateTime.ofLocal` with no preferred offset does exactly that.
 */
internal fun localInstant(local: LocalDateTime, tzid: String): Instant =
    ZonedDateTime.ofLocal(local, zoneFor(tzid), null).toInstant()

internal data class IcsDuration(val days: Long, val seconds: Double)

private val DurationPattern =
    Regex("""^\+?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$""")

internal fun parseDuration(value: String): IcsDuration {
    val match = DurationPattern.matchEntire(value)
    if (match == null || match.groups.drop(1).all { it == null }) {
        throw JsCalendarException("Invalid JSCalendar duration: $value")
    }
    fun number(index: Int) = match.groupValues[index].toDoubleOrNull() ?: 0.0
    return IcsDuration(
        days = (number(1) * 7 + number(2)).toLong(),
        seconds = number(3) * 3600 + number(4) * 60 + number(5),
    )
}

/**
 * The end as JSCalendar text. Nominal days are added first, then elapsed
 * seconds in the start zone (RFC 8984 section 1.4.6), then the instant is
 * shown in the end zone.
 */
internal fun endTime(
    start: String,
    length: String,
    tzid: String? = null,
    endTzid: String? = tzid,
    allDay: Boolean = false,
): String {
    val duration = parseDuration(length)
    val begin = parseTime(start, tzid, allDay)
    var local = begin.local.plusDays(duration.days)
    if (allDay) {
        local = local.plusDays(Math.ceil(duration.seconds / 86400).toLong())
        // A DATE-valued DTEND is exclusive and must be later than DTSTART.
        if (duration.days == 0L && duration.seconds == 0.0) local = local.plusDays(1)
        return IcsTime(local, utc = false, dateOnly = true).isoString()
    }
    if (tzid == null) {
        return IcsTime(local.plusSeconds(duration.seconds.toLong()), utc = false, dateOnly = false).isoString()
    }
    val instant = localInstant(local, tzid).plusMillis((duration.seconds * 1000).toLong())
    if (endTzid == null || isUtcName(endTzid)) {
        return IcsTime(LocalDateTime.ofInstant(instant, ZoneOffset.UTC), utc = true, dateOnly = false).isoString()
    }
    return IcsTime(LocalDateTime.ofInstant(instant, zoneFor(endTzid)), utc = false, dateOnly = false).isoString()
}

/**
 * Applies a PatchObject (RFC 8984 section 1.4.9) to a copy of [master].
 *
 * The whole patch is validated first -- paths with a bad escape, two paths
 * where one is the prefix of the other, a path whose parent is missing or not
 * an object -- so a bad patch changes nothing. A null value deletes.
 */
internal fun applyPatch(master: JSONObject, patch: JSONObject): JSONObject {
    class Step(val path: String, val parts: List<String>, val value: Any?)

    val steps = sortedEntries(patch).map { (path, value) ->
        if (Regex("~(?![01])").containsMatchIn(path)) throw JsCalendarException("Invalid JSCalendar patch path: $path")
        Step(path, path.split('/').map { it.replace("~1", "/").replace("~0", "~") }, value)
    }
    for (step in steps) {
        for (other in steps) {
            if (other.path != step.path && other.path.startsWith("${step.path}/")) {
                throw JsCalendarException("Conflicting JSCalendar patch paths: ${step.path}, ${other.path}")
            }
        }
        var parent = master
        for (part in step.parts.dropLast(1)) {
            parent = parent.opt(part) as? JSONObject
                ?: throw JsCalendarException("Invalid JSCalendar patch parent: ${step.path}")
        }
    }
    val result = master.copy()
    for (step in steps) {
        var parent = result
        for (part in step.parts.dropLast(1)) parent = parent.getJSONObject(part)
        val key = step.parts.last()
        if (isNull(step.value)) parent.remove(key) else parent.put(key, deepCopy(step.value))
    }
    return result
}

internal fun jsonArrayOf(value: Any?): List<Any?> = (value as? JSONArray)?.toList().orEmpty()
