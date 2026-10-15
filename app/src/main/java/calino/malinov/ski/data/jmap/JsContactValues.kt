package calino.malinov.ski.data.jmap

import org.json.JSONArray
import org.json.JSONObject

/** A card that cannot be translated. Reported like any other unreadable item, never half-converted. */
class JsContactException(message: String) : IllegalArgumentException(message)

/** One vCard content line: upper-cased name without any group, its decoded parameters, and the raw value. */
internal class VCardProperty(val name: String, val params: Map<String, List<String>>, val value: String) {
    fun param(name: String): String? = params[name.uppercase()]?.firstOrNull()
}

/** Entries of a JSON map whose values are objects; anything else is ignored, as the web converter does. */
internal fun objectEntries(value: Any?): List<Pair<String, JSONObject>> =
    (value as? JSONObject)?.entryList()?.mapNotNull { (key, item) -> (item as? JSONObject)?.let { key to it } }.orEmpty()

internal fun objectValues(value: Any?): List<JSONObject> = objectEntries(value).map { it.second }

internal fun textOf(value: Any?): String = value as? String ?: ""

internal fun vCardEscape(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        when (val char = value[index]) {
            '\\' -> append("\\\\")
            '\r' -> {
                if (index + 1 < value.length && value[index + 1] == '\n') index++
                append("\\n")
            }
            '\n' -> append("\\n")
            ';' -> append("\\;")
            ',' -> append("\\,")
            else -> append(char)
        }
        index++
    }
}

internal fun vCardUnescape(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '\\' && index + 1 < value.length) {
            val next = value[index + 1]
            if (next == 'n' || next == 'N') {
                append('\n')
                index += 2
                continue
            }
            if (next == ',' || next == ';' || next == '\\') {
                append(next)
                index += 2
                continue
            }
        }
        append(char)
        index++
    }
}

/** Splits on [delimiter] outside backslash escapes and double quotes. */
internal fun vCardSplit(value: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    var start = 0
    var quoted = false
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '\\') {
            index += 2
            continue
        }
        if (char == '"') quoted = !quoted
        if (char == delimiter && !quoted) {
            result += value.substring(start, index)
            start = index + 1
        }
        index++
    }
    result += value.substring(start.coerceAtMost(value.length))
    return result
}

private fun caretDecode(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '^' && index + 1 < value.length) {
            when (value[index + 1]) {
                '^' -> { append('^'); index += 2; continue }
                '\'' -> { append('"'); index += 2; continue }
                'n', 'N' -> { append('\n'); index += 2; continue }
            }
        }
        append(char)
        index++
    }
}

/** The properties of exactly one complete vCard. */
internal fun parseVCardProperties(vcard: String): List<VCardProperty> {
    val lines = mutableListOf<StringBuilder>()
    for (raw in vcard.removePrefix("\uFEFF").split("\r\n", "\n", "\r")) {
        if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
            lines.last().append(raw, 1, raw.length)
        } else {
            lines += StringBuilder(raw)
        }
    }
    val text = lines.map { it.toString() }
    if (text.count { it.equals("BEGIN:VCARD", ignoreCase = true) } != 1 ||
        text.none { it.equals("END:VCARD", ignoreCase = true) }
    ) {
        throw JsContactException("Expected one complete vCard")
    }
    return text.filter { it.isNotEmpty() }.map { line ->
        var colon = -1
        var quoted = false
        for (index in line.indices) {
            if (line[index] == '"') quoted = !quoted
            if (line[index] == ':' && !quoted) {
                colon = index
                break
            }
        }
        if (colon < 0) throw JsContactException("Malformed vCard content line")
        val head = vCardSplit(line.substring(0, colon), ';')
        val params = LinkedHashMap<String, MutableList<String>>()
        for (parameter in head.drop(1)) {
            val equals = parameter.indexOf('=')
            val key = if (equals < 0) "TYPE" else parameter.substring(0, equals).uppercase()
            val raw = if (equals < 0) parameter else parameter.substring(equals + 1)
            val unquoted = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
            params.getOrPut(key) { mutableListOf() } += vCardSplit(caretDecode(unquoted), ',')
        }
        VCardProperty(head[0].substringAfterLast('.').uppercase(), params, line.substring(colon + 1))
    }
}

/** `contexts` and `pref` from TYPE/PREF parameters (RFC 9555 section 3.3). */
internal fun contactMetadata(property: VCardProperty): JSONObject {
    val types = property.params["TYPE"].orEmpty().map { it.lowercase() }
    val result = JSONObject()
    val contexts = JSONObject()
    types.filter { it in listOf("home", "work", "private") }
        .forEach { contexts.put(if (it == "home") "private" else it, true) }
    if (contexts.length() > 0) result.put("contexts", contexts)
    val pref = property.params["PREF"]?.firstOrNull()?.toDoubleOrNull() ?: if ("pref" in types) 1.0 else 0.0
    if (pref >= 1 && pref <= 100) result.put("pref", pref.toLong())
    return result
}

/** TYPE/PREF parameters for a JSContact value, for vCard [version] "3.0" or "4.0". */
internal fun contactParameters(value: JSONObject, version: String, types: MutableList<String> = mutableListOf()): String {
    val contexts = value.optJSONObject("contexts")
    if (contexts?.optBoolean("private") == true || contexts?.optBoolean("home") == true) types += "home"
    if (contexts?.optBoolean("work") == true) types += "work"
    val pref = (value.opt("pref") as? Number)?.toLong()?.takeIf { it in 1..100 }
    // Calino's parser understands PREF presence (not its numeric priority) in 3.0.
    if (pref != null && version == "3.0") types += "pref"
    return (if (types.isNotEmpty()) ";TYPE=${types.joinToString(",")}" else "") +
        (if (pref != null && version == "4.0") ";PREF=$pref" else "")
}

/** Folds a content line at 75 octets without splitting a character. */
internal fun foldVCardLine(line: String): String {
    val out = StringBuilder()
    var bytes = 0
    var index = 0
    while (index < line.length) {
        val point = line.codePointAt(index)
        val size = String(Character.toChars(point)).toByteArray(Charsets.UTF_8).size
        if (bytes + size > 75) {
            out.append("\r\n ")
            bytes = 1
        }
        out.appendCodePoint(point)
        bytes += size
        index += Character.charCount(point)
    }
    return out.toString()
}

/** A JSON number that is whole is written without a fraction, like `JSON.stringify`. */
internal fun jsonArrayOfStrings(values: Iterable<String>): JSONArray = jsonArray(values)
