package calino.malinov.ski.data.caldav

/**
 * A phone number as text, from whatever shape a vCard carries it in.
 *
 * Two shapes reach us. The plain one — `TEL;TYPE=HOME:01234 456789` — is the
 * number itself. The URI one — `TEL;VALUE=uri;TYPE=HOME:tel:+4512345678` — is
 * what Nextcloud writes, and its number lives inside a `tel:` URI. Calino stores
 * and writes numbers as text, so both collapse to the number here; anything that
 * is not a `tel:` URI is returned exactly as it arrived, because reformatting a
 * number we do not understand is how a number gets mangled.
 */
internal fun plainPhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("tel:", ignoreCase = true)) return trimmed
    return trimmed.substring(4).trim()
        .replace(Regex("(?i);ext="), " ext. ")
        .replace("%20", " ")
        .trim()
}
