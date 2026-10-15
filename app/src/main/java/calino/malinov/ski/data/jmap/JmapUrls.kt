package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import java.net.URLDecoder
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * JMAP has no resource URLs, but the rest of the app keys its cache, hrefs and
 * same-origin checks on them. Calendars and events therefore get synthetic,
 * absolute, nested URLs on the server's own origin:
 *
 *     <origin>/.jmap/<accountId>/<calendarId>/            calendar
 *     <origin>/.jmap/<accountId>/<calendarId>/<eventId>   event
 *
 * Ids are percent-encoded like `encodeURIComponent`. The event id is a server
 * id and is **not** the iCalendar UID.
 */
internal data class JmapResourceId(val calendarId: String, val eventId: String?)

internal fun serverOrigin(serverUrl: String): String {
    val url = serverUrl.toHttpUrl()
    val default = if (url.scheme == "https") 443 else 80
    return buildString {
        append(url.scheme).append("://")
        append(if (url.host.contains(':')) "[${url.host}]" else url.host)
        if (url.port != default) append(':').append(url.port)
    }
}

/** `encodeURIComponent`: everything except unreserved marks is escaped, UTF-8. */
internal fun encodeUriComponent(value: String): String = buildString {
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val c = byte.toInt() and 0xFF
        val plain = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
            c.toChar() in "-_.!~*'()"
        if (plain) append(c.toChar()) else append('%').append("%02X".format(c))
    }
}

private fun decodeUriComponent(value: String): String =
    // URLDecoder turns '+' into a space; a literal '+' is always escaped on our side.
    URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")

internal fun jmapAccountPrefix(serverUrl: String, accountId: String): String =
    "${serverOrigin(serverUrl)}/.jmap/${encodeUriComponent(accountId)}/"

internal fun jmapCalendarUrl(serverUrl: String, accountId: String, calendarId: String): String =
    jmapAccountPrefix(serverUrl, accountId) + encodeUriComponent(calendarId) + "/"

internal fun jmapEventUrl(serverUrl: String, accountId: String, calendarId: String, eventId: String): String =
    jmapCalendarUrl(serverUrl, accountId, calendarId) + encodeUriComponent(eventId)

/**
 * Splits a synthetic URL back into ids. A URL from another origin or account, or
 * one with a query, fragment or wrong depth, is refused rather than guessed at.
 */
internal fun parseJmapUrl(
    href: String,
    serverUrl: String,
    accountId: String,
    event: Boolean,
): JmapResourceId {
    fun invalid(): Nothing = throw JmapException(
        CalDavErrorCode.Rejected, "Resource is outside this JMAP account",
        status = 400, type = "invalidArguments",
    )
    val prefix = jmapAccountPrefix(serverUrl, accountId)
    val url = runCatching { href.toHttpUrl() }.getOrNull() ?: invalid()
    if (!href.startsWith(prefix) || url.query != null || url.fragment != null) invalid()
    val parts = href.substring(prefix.length).split('/')
    if (parts.size != 2 || parts[0].isEmpty()) invalid()
    if (event == parts[1].isEmpty()) invalid()
    return JmapResourceId(
        calendarId = decodeUriComponent(parts[0]),
        eventId = if (event) decodeUriComponent(parts[1]) else null,
    )
}

// --- address books ----------------------------------------------------------------------

/**
 * Contacts live beside calendars under the same synthetic root, behind an `ab`
 * segment, in the *contacts* account (which can differ from the calendars one):
 *
 *     <origin>/.jmap/<accountId>/ab/<addressBookId>/            address book
 *     <origin>/.jmap/<accountId>/ab/<addressBookId>/<cardId>    card
 *
 * Calendar URLs have two segments after the account and these have three, so the
 * shapes cannot be confused whatever a calendar is called.
 */
internal data class JmapContactId(val addressBookId: String, val cardId: String?)

internal fun jmapAddressBookUrl(serverUrl: String, accountId: String, bookId: String): String =
    jmapAccountPrefix(serverUrl, accountId) + "ab/" + encodeUriComponent(bookId) + "/"

internal fun jmapCardUrl(serverUrl: String, accountId: String, bookId: String, cardId: String): String =
    jmapAddressBookUrl(serverUrl, accountId, bookId) + encodeUriComponent(cardId)

internal fun parseJmapContactUrl(
    href: String,
    serverUrl: String,
    accountId: String,
    card: Boolean,
): JmapContactId {
    fun invalid(): Nothing = throw JmapException(
        CalDavErrorCode.Rejected, "Contact is outside this JMAP account",
        status = 400, type = "invalidArguments",
    )
    val prefix = jmapAccountPrefix(serverUrl, accountId) + "ab/"
    val url = runCatching { href.toHttpUrl() }.getOrNull() ?: invalid()
    if (!href.startsWith(prefix) || url.query != null || url.fragment != null) invalid()
    val parts = href.substring(prefix.length).split('/')
    if (parts.size != 2 || parts[0].isEmpty()) invalid()
    if (card == parts[1].isEmpty()) invalid()
    return JmapContactId(
        addressBookId = decodeUriComponent(parts[0]),
        cardId = if (card) decodeUriComponent(parts[1]) else null,
    )
}
