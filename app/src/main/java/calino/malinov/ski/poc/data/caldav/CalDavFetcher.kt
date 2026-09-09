package calino.malinov.ski.poc.data.caldav

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import org.w3c.dom.Element

/**
 * One calendar resource exactly as the server sent it.
 *
 * The raw iCalendar text is the currency the fetch deals in, not the mapped
 * models. Expansion depends on the window it is asked for, so occurrences are
 * derived at use time by `ICalMapper` -- which is also what lets a cached copy
 * be re-expanded for a different window later.
 */
data class CalendarResource(val href: String, val etag: String?, val ics: String)

/** What one fetch produced, plus how complete it is. */
data class FetchResult(
    val resources: List<CalendarResource> = emptyList(),
    /**
     * Component queries that failed while another succeeded. A partial result
     * must never be treated as authoritative -- it is a view, not a statement
     * about what the server no longer holds.
     */
    val failures: List<ComponentFailure> = emptyList(),
) {
    val isEmpty: Boolean get() = resources.isEmpty()
    val hadComponentFailures: Boolean get() = failures.isNotEmpty()
}

/** One component query that did not come back, and why. */
data class ComponentFailure(val component: String, val error: CalDavException) {
    /** Phrased for a person: "events (the server rejected...)". */
    fun describe(): String = "${componentLabel(component)}: ${error.message}"

    private fun componentLabel(component: String) = when (component) {
        "VEVENT" -> "Events"
        "VTODO" -> "Tasks"
        "VJOURNAL" -> "Journal entries"
        else -> component
    }
}

/** Reads events, tasks and journal entries out of calendar collections. */
class CalDavFetcher(private val http: DavHttp = DavHttp()) {

    suspend fun fetch(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): FetchResult = supervisorScope {
        // Every component is requested regardless of what the collection
        // advertises in supported-calendar-component-set.
        //
        // That property is a hint, not a guarantee about content. Baikal lets a
        // calendar advertise a narrow set -- a calendar created by a task app
        // commonly reports VTODO only -- while still holding events that a
        // plain query returns perfectly well. Gating on it silently hid the
        // user's entire calendar and, because nothing had *failed*, reported
        // the result as complete. One extra request that comes back empty is
        // far cheaper than that.
        val requests = listOf(
            async { Vevent to runCatching { fetchEvents(calendar, credentials, windowStart, windowEnd) } },
            async { Vtodo to runCatching { fetchTasks(calendar, credentials) } },
            async { Vjournal to runCatching { fetchJournals(calendar, credentials) } },
        )

        val settled = requests.awaitAll()
        val succeeded = settled.mapNotNull { it.second.getOrNull() }
        val failures = settled.mapNotNull { (component, result) ->
            result.exceptionOrNull()?.let { error ->
                ComponentFailure(component, calDavErrorForThrowable(error, calendar.url))
            }
        }

        // Fail only when everything failed. Letting one component's failure
        // abort the whole fetch used to drop every event whenever a calendar's
        // empty task query errored.
        if (succeeded.isEmpty()) {
            failures.firstOrNull()?.let { throw it.error }
            return@supervisorScope FetchResult()
        }

        // Deduplicated by href: a resource holding more than one component type
        // comes back from more than one query, and mapping the same text twice
        // would place its records twice.
        FetchResult(resources = succeeded.flatten().distinctBy { it.href }, failures = failures)
    }

    /**
     * Events over the requested window.
     *
     * The query deliberately does not ask for `<c:expand>`. Server-side
     * expansion is not dependable -- sabre (Baikal) returns HTTP 500 for any
     * collection whose `calendar-timezone` holds a bare zone id rather than a
     * VCALENDAR, and other servers ignore the element and return the master
     * with its RRULE intact. Both failures render a repeating event on its
     * first date only. `ICalMapper` expands instead, identically everywhere,
     * which is why the window is handed to it here.
     */
    private suspend fun fetchEvents(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<CalendarResource> {
        val start = windowStart.atStartOfDay().toInstant(ZoneOffset.UTC).format()
        val end = windowEnd.atStartOfDay().toInstant(ZoneOffset.UTC).format()
        // The time-range filter selects resources whose series overlaps the
        // window; the master it returns still carries the whole rule.
        val body = """<?xml version="1.0" encoding="UTF-8"?>
<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:prop><d:getetag/><c:calendar-data/></d:prop>
  <c:filter>
    <c:comp-filter name="VCALENDAR">
      <c:comp-filter name="VEVENT"><c:time-range start="$start" end="$end"/></c:comp-filter>
    </c:comp-filter>
  </c:filter>
</c:calendar-query>"""
        return report(calendar, credentials, body)
    }

    /**
     * Tasks, unbounded.
     *
     * No time-range filter: a VTODO may carry no DTSTART or DUE at all, and a
     * time-ranged query drops exactly those.
     */
    private suspend fun fetchTasks(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
    ): List<CalendarResource> = report(calendar, credentials, componentQuery(Vtodo))

    private suspend fun fetchJournals(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
    ): List<CalendarResource> = report(calendar, credentials, componentQuery(Vjournal))

    private fun componentQuery(component: String) = """<?xml version="1.0" encoding="UTF-8"?>
<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:prop><d:getetag/><c:calendar-data/></d:prop>
  <c:filter>
    <c:comp-filter name="VCALENDAR"><c:comp-filter name="$component"/></c:comp-filter>
  </c:filter>
</c:calendar-query>"""

    private suspend fun report(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        body: String,
    ): List<CalendarResource> {
        val response = http.request(
            method = "REPORT",
            url = calendar.url,
            credentials = credentials,
            headers = mapOf("Depth" to "1", "Content-Type" to "application/xml; charset=utf-8"),
            body = body,
        )
        if (!response.isMultiStatus) throw calDavErrorForStatus(response.status, calendar.url)
        val root = DavXml.parse(response.body)
            ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The server's reply could not be read.")
        return parseResources(root, calendar)
    }

    /** Pulls the href, ETag and calendar text out of a multistatus reply. */
    private fun parseResources(root: Element, calendar: DiscoveredCalendar): List<CalendarResource> =
        DavXml.elements(root, DavNs.Dav, "response").mapNotNull { entry ->
            val href = DavXml.text(entry, DavNs.Dav, "href")?.let { resolveHref(calendar.url, it) }
                ?: return@mapNotNull null
            val data = DavXml.text(entry, DavNs.CalDav, "calendar-data") ?: return@mapNotNull null
            CalendarResource(
                href = href,
                etag = DavXml.text(entry, DavNs.Dav, "getetag")?.trim('"'),
                ics = data,
            )
        }

    private companion object {
        const val Vevent = "VEVENT"
        const val Vtodo = "VTODO"
        const val Vjournal = "VJOURNAL"
        val UtcStamp: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        fun Instant.format(): String = UtcStamp.format(this)
    }
}
