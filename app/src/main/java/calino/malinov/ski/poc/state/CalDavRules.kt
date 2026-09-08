package calino.malinov.ski.poc.state

import calino.malinov.ski.poc.data.model.CalDavCalendar
import calino.malinov.ski.poc.data.model.CalDavForm
import java.util.Locale

/** The three steps the add-account sheet moves through. */
enum class CalDavStep { Credentials, Connecting, ChooseCalendars }

/** Which field a validation complaint belongs to, so the sheet can mark it. */
enum class CalDavField { ServerUrl, Username, Password }

data class CalDavFieldError(val field: CalDavField, val message: String)

/** The outcome of a discovery attempt. */
sealed interface CalDavConnectResult {
    data class Discovered(val calendars: List<CalDavCalendar>) : CalDavConnectResult
    data class Failed(val message: String) : CalDavConnectResult
}

/**
 * Accept what a person would actually type. A bare host becomes `https://`,
 * a trailing slash is dropped so two spellings of one server compare equal,
 * and anything without a plausible host is rejected.
 */
fun normalizeServerUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val lower = trimmed.lowercase(Locale.US)
    val withScheme = when {
        lower.startsWith("https://") -> "https://" + trimmed.substring(8)
        lower.startsWith("http://") -> "http://" + trimmed.substring(7)
        trimmed.contains("://") -> return null
        else -> "https://" + trimmed
    }
    val body = withScheme.substringAfter("://")
    val host = body.substringBefore('/').substringBefore('?')
    if (host.isEmpty() || host.startsWith('.') || host.endsWith('.')) return null
    if (host.any { it.isWhitespace() }) return null
    // A host has to look like a host: a dotted name, or an explicit localhost.
    if (!host.contains('.') && !host.substringBefore(':').equals("localhost", ignoreCase = true)) return null
    return withScheme.trimEnd('/')
}

/** The host portion of a normalized URL, used for labels and default names. */
fun serverHost(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore(':')

/**
 * Per-field complaints rather than one banner, so the sheet can point at the
 * field that needs attention. An empty list means the form can be submitted.
 */
fun CalDavForm.validate(): List<CalDavFieldError> = buildList {
    when {
        serverUrl.isBlank() ->
            add(CalDavFieldError(CalDavField.ServerUrl, "Enter your CalDAV server address"))
        normalizeServerUrl(serverUrl) == null ->
            add(CalDavFieldError(CalDavField.ServerUrl, "That does not look like a server address"))
    }
    if (username.isBlank()) add(CalDavFieldError(CalDavField.Username, "Enter the account username"))
    if (password.isBlank()) add(CalDavFieldError(CalDavField.Password, "Enter the account password"))
}

fun List<CalDavFieldError>.messageFor(field: CalDavField): String? =
    firstOrNull { it.field == field }?.message

fun CalDavForm.canConnect(): Boolean = validate().isEmpty()

/**
 * What the account is called when the person left the name blank. The host is
 * more recognisable than the raw URL once several accounts are listed.
 */
fun defaultDisplayName(form: CalDavForm): String {
    val typed = form.displayName.trim()
    if (typed.isNotEmpty()) return typed
    val host = normalizeServerUrl(form.serverUrl)?.let(::serverHost)
    return host ?: form.username.trim().ifEmpty { "CalDAV account" }
}

/** Stable id, so re-adding the same login replaces the account instead of duplicating it. */
fun accountId(form: CalDavForm): String {
    val url = normalizeServerUrl(form.serverUrl) ?: form.serverUrl.trim()
    return url.lowercase(Locale.US) + "|" + form.username.trim().lowercase(Locale.US)
}
