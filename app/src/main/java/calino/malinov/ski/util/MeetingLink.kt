package calino.malinov.ski.util

import calino.malinov.ski.data.model.CalEvent
import java.net.URI

/**
 * A conference link found on an event, and a short name for its service.
 *
 * Detection is local and read-only: Calino never adds or rewrites a meeting
 * link, it only offers to open one the event already carries.
 */
data class MeetingLink(val url: String, val service: String)

private val LinkPattern = Regex("""https://[^\s<>"'()\[\]{}]+""", RegexOption.IGNORE_CASE)

/**
 * The event's meeting link, if any, in RFC 7986 order: a `CONFERENCE` (or
 * `URL`) value first, then the first recognised link in the location, then in
 * the notes. A `CONFERENCE` value counts even for an unknown service; links
 * found in free text must match a known meeting service.
 */
fun meetingLink(event: CalEvent): MeetingLink? =
    meetingLink(event.conferenceUrl, event.location, event.notes)

fun meetingLink(conferenceUrl: String?, location: String?, notes: String?): MeetingLink? {
    conferenceUrl?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }?.let { url ->
        return MeetingLink(url, meetingService(url) ?: "Meeting")
    }
    return sequenceOf(location, notes)
        .filterNotNull()
        .flatMap { text -> LinkPattern.findAll(text).map { it.value.trimEnd('.', ',', ';', ':', '!', '?') } }
        .firstNotNullOfOrNull { url -> meetingService(url)?.let { MeetingLink(url, it) } }
}

/** The service a link belongs to, or null when it is not a known meeting link. */
internal fun meetingService(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val host = uri.host?.lowercase() ?: return null
    val path = uri.rawPath.orEmpty()
    fun hostIs(domain: String) = host == domain || host.endsWith(".$domain")
    return when {
        host == "meet.jit.si" || host.startsWith("jitsi.") -> "Jitsi"
        hostIs("zoom.us") && (path.startsWith("/j/") || path.startsWith("/my/") || path.startsWith("/w/")) -> "Zoom"
        host == "meet.google.com" && path.length > 1 -> "Google Meet"
        host == "teams.microsoft.com" && path.startsWith("/l/meetup-join") -> "Teams"
        host == "teams.live.com" && path.startsWith("/meet") -> "Teams"
        hostIs("webex.com") && (path.contains("/j.php") || path.startsWith("/meet/") || path.contains("/wbxmjs/")) -> "Webex"
        path.contains("/call/") && path.substringAfter("/call/").isNotEmpty() -> "Nextcloud Talk"
        else -> null
    }
}
