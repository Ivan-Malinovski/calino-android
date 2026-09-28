package calino.malinov.ski.data.repository

/**
 * Normalizes iCalendar/vCard text to the wire form servers expect: CRLF line
 * endings, no blank lines, and a trailing CRLF. Content lines are unchanged,
 * so this is safe to apply before resending a payload a server refused.
 */
internal fun normalizeICalendarText(text: String): String =
    text.replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .filter { it.isNotBlank() }
        .joinToString("\r\n", postfix = "\r\n")

/**
 * The item's name for the pending-writes list, read from the queued payload
 * or, for deletes, the server copy it was based on. Returns null when no
 * payload carries one.
 */
fun PendingChange.displayTitle(): String? {
    val property = if (component.equals("VCARD", ignoreCase = true)) "FN" else "SUMMARY"
    return sequenceOf(data, sourceData, baseData)
        .filterNotNull()
        .mapNotNull { firstPropertyValue(it, property) }
        .firstOrNull { it.isNotBlank() }
}

private fun firstPropertyValue(text: String, name: String): String? {
    // Unfold continuation lines (RFC 5545 3.1) before looking for the property.
    val unfolded = text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "")
    val line = unfolded.lineSequence().firstOrNull { line ->
        line.startsWith("$name:", ignoreCase = true) || line.startsWith("$name;", ignoreCase = true)
    } ?: return null
    // Parameter values may be quoted and contain ':' (e.g. ALTREP="https://...").
    var quoted = false
    val colon = line.indices.firstOrNull { index ->
        when (line[index]) {
            '"' -> { quoted = !quoted; false }
            ':' -> !quoted
            else -> false
        }
    } ?: return null
    return line.substring(colon + 1)
        .replace("\\n", " ")
        .replace("\\N", " ")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
        .trim()
}
