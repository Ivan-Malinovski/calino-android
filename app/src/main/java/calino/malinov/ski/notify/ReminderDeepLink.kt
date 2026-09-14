package calino.malinov.ski.notify

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.placementDate
import java.net.URI
import java.time.LocalDate
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The address a notification taps through to.
 *
 * Written and parsed here rather than with `android.net.Uri` so both halves can
 * be tested in the plain-JUnit suite, and so the format is stated in exactly
 * one place.
 *
 * The hard part is not the format, it is [ReminderDeepLinks.resolveEvent]. A
 * notification can be tapped days after it was posted, and by then the record
 * id may have changed: `ICalMapper` ids an expanded occurrence `uid@instant`,
 * and a re-expansion with a moved `DTSTART` produces a different id for what
 * the user still thinks of as the same meeting. So the link carries the UID and
 * the occurrence day as well, and resolution degrades through them.
 */
data class ReminderDeepLink(
    val kind: ReminderKind,
    val recordId: String,
    val uid: String? = null,
    val occurrenceDay: Long? = null,
)

object ReminderDeepLinks {

    const val Scheme = "calino.malinov.ski"
    const val Host = "reminder"

    private const val EventPath = "event"
    private const val TaskPath = "task"

    fun uri(firing: ReminderFiring): String = uri(
        ReminderDeepLink(
            kind = firing.kind,
            recordId = firing.recordId,
            uid = firing.uid,
            occurrenceDay = firing.occurrenceDay,
        ),
    )

    fun uri(link: ReminderDeepLink): String {
        val path = if (link.kind == ReminderKind.Event) EventPath else TaskPath
        val query = buildList {
            add("id=" + encode(link.recordId))
            link.uid?.let { add("uid=" + encode(it)) }
            link.occurrenceDay?.let { add("day=$it") }
        }.joinToString("&")
        return "$Scheme://$Host/$path?$query"
    }

    /** Null for anything that is not one of ours; never throws on rubbish. */
    fun parse(raw: String?): ReminderDeepLink? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!Scheme.equals(uri.scheme, ignoreCase = true)) return null
        if (!Host.equals(uri.host, ignoreCase = true)) return null

        val kind = when (uri.path?.trim('/')?.lowercase()) {
            EventPath -> ReminderKind.Event
            TaskPath -> ReminderKind.Task
            else -> return null
        }
        val params = queryParameters(uri.rawQuery)
        val recordId = params["id"]?.takeIf { it.isNotBlank() } ?: return null
        return ReminderDeepLink(
            kind = kind,
            recordId = recordId,
            uid = params["uid"]?.takeIf { it.isNotBlank() },
            occurrenceDay = params["day"]?.toLongOrNull(),
        )
    }

    /**
     * The event this link meant, by decreasing confidence.
     *
     * An unresolvable link is not an error the user should be shown -- the
     * caller lands them on the calendar at [ReminderDeepLink.occurrenceDay]
     * instead, which is very nearly always where they were trying to go.
     */
    fun resolveEvent(link: ReminderDeepLink, events: List<CalEvent>): CalEvent? {
        if (link.kind != ReminderKind.Event) return null
        events.firstOrNull { it.id == link.recordId }?.let { return it }

        val uid = link.uid ?: return null
        val sameSeries = events.filter { it.uid == uid }
        if (sameSeries.isEmpty()) return null
        val day = link.occurrenceDay ?: return sameSeries.first()

        sameSeries.firstOrNull { it.placementDate()?.toEpochDay() == day }?.let { return it }
        // The series re-expanded around the day we wanted; the next occurrence
        // is closer to the user's intent than the first one of the window.
        return sameSeries
            .filter { (it.placementDate()?.toEpochDay() ?: Long.MIN_VALUE) >= day }
            .minByOrNull { it.placementDate()?.toEpochDay() ?: Long.MAX_VALUE }
    }

    fun resolveTask(link: ReminderDeepLink, tasks: List<CalTask>): CalTask? {
        if (link.kind != ReminderKind.Task) return null
        tasks.firstOrNull { it.id == link.recordId }?.let { return it }
        val uid = link.uid ?: return null
        return tasks.firstOrNull { it.uid == uid }
    }

    private fun queryParameters(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val key = part.substring(0, separator)
            val value = runCatching { decode(part.substring(separator + 1)) }.getOrNull()
                ?: return@mapNotNull null
            key to value
        }.toMap()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

/**
 * "Open the calendar on this date."
 *
 * The widget's day headers and its background need a target that is not a
 * record. It lives beside the reminder link rather than in the widget package
 * so the app's URI scheme stays written down in exactly one file, and it is
 * built on `java.net.URI` for the same reason its neighbour is: both halves are
 * then testable in the plain-JUnit suite.
 */
object AgendaDeepLinks {

    const val Host = "agenda"

    fun uri(day: LocalDate): String =
        "${ReminderDeepLinks.Scheme}://$Host?day=${day.toEpochDay()}"

    /** The date this link meant, or null for anything that is not one of ours. */
    fun parse(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!ReminderDeepLinks.Scheme.equals(uri.scheme, ignoreCase = true)) return null
        if (!Host.equals(uri.host, ignoreCase = true)) return null
        val day = uri.rawQuery
            ?.split('&')
            ?.firstOrNull { it.startsWith("day=") }
            ?.removePrefix("day=")
            ?.toLongOrNull()
            ?: return null
        return runCatching { LocalDate.ofEpochDay(day) }.getOrNull()
    }
}
