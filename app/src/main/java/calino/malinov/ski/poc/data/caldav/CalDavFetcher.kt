package calino.malinov.ski.poc.data.caldav

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

/** Which read strategy produced an [IncrementalFetchResult]. */
enum class CalendarFetchMode {
    /** No usable sync token was supplied, so the existing full fetch ran. */
    Full,

    /** A fresh ctag matched the committed ctag, so the complete cache is current. */
    Skipped,

    /** The sync-collection report and all changed-resource reads succeeded. */
    Incremental,

    /** A delta was not safe to apply, so a complete fetch was used instead. */
    FullFallback,
}

/**
 * A complete resource snapshot plus the cursor that describes it.
 *
 * [fetchResult] is deliberately the same [FetchResult] returned by [fetch],
 * so callers can map either path identically. [cursor] is safe to persist only
 * after the caller has accepted this result; a rejected sync report returns a
 * cursor with a null token, which prevents the invalid token being reused.
 * [fallbackReason] is populated when [CalendarFetchMode.FullFallback] came
 * from [IncrementalSync]; failures while reading a changed resource still
 * fall back to a full fetch but have no report-level reason.
 */
data class IncrementalFetchResult(
    val fetchResult: FetchResult,
    val cursor: CollectionCursor,
    val mode: CalendarFetchMode,
    val fallbackReason: SyncCollectionFallbackReason? = null,
) {
    val resources: List<CalendarResource> get() = fetchResult.resources
    val usedIncremental: Boolean get() = mode == CalendarFetchMode.Incremental
    val fellBackToFull: Boolean get() = mode == CalendarFetchMode.FullFallback
}

/** Reads events, tasks and journal entries out of calendar collections. */
class CalDavFetcher(
    private val http: DavHttp = DavHttp(),
    private val incrementalSync: IncrementalSync = IncrementalSync(http),
) {

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
     * Reads one collection incrementally when a committed sync token and a
     * complete resource snapshot are available.
     *
     * `sync-collection` reports hrefs and ETags, not calendar data. Changed
     * hrefs are therefore fetched with ordinary GET requests and merged into
     * [cachedResources]; tombstones remove only the named href. The old
     * snapshot is never mutated while the delta is being built.
     *
     * Passing `null` for [cachedResources] means that no complete snapshot is
     * available. That case, a missing token, a rejected/malformed report, or a
     * failed changed-resource GET all use [fetch]'s existing full-fetch path.
     * An empty list is valid and means the caller has a complete empty
     * snapshot, not that the cache is missing.
     *
     * [storedCursor] is normally the cursor persisted after the last complete
     * read. If omitted, the cursor fields on [calendar] are used. The
     * calendar's ctag is treated as the current discovery metadata and is
     * carried onto a successful result; this lets a caller pass an older
     * stored cursor while discovery supplies a fresh ctag.
     */
    suspend fun fetchIncremental(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        cachedResources: List<CalendarResource>?,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        storedCursor: CollectionCursor? = null,
    ): IncrementalFetchResult {
        val previous = storedCursor ?: CollectionCursor(
            ctag = calendar.ctag,
            syncToken = calendar.syncToken,
        )

        if (!previous.hasSyncToken) {
            return fullFetch(
                calendar = calendar,
                credentials = credentials,
                windowStart = windowStart,
                windowEnd = windowEnd,
                cursor = CollectionCursor(ctag = calendar.ctag),
                mode = CalendarFetchMode.Full,
            )
        }

        if (cachedResources == null) {
            // A full read re-establishes a complete snapshot. The old token is
            // still valid because it was never consumed, so retaining it is
            // safe when the full read itself has no component failures.
            return fullFetch(
                calendar = calendar,
                credentials = credentials,
                windowStart = windowStart,
                windowEnd = windowEnd,
                cursor = previous.copy(ctag = calendar.ctag),
                mode = CalendarFetchMode.FullFallback,
            )
        }

        // A ctag is only a hint when used by itself. With a committed sync
        // token and a complete snapshot, however, an equal fresh ctag is the
        // conservative skip rule shared by IncrementalSync. The explicit
        // parameter is important: when it is omitted, the calendar's fields
        // are one cursor and cannot be compared with themselves.
        if (storedCursor != null && previous.canSkipWith(CollectionCursor(ctag = calendar.ctag))) {
            return IncrementalFetchResult(
                fetchResult = FetchResult(resources = cachedResources),
                cursor = previous.copy(ctag = calendar.ctag),
                mode = CalendarFetchMode.Skipped,
            )
        }

        val report = incrementalSync.syncCollection(
            collectionUrl = calendar.url,
            credentials = credentials,
            cursor = previous,
        )
        if (report.tokenInvalidated || report.nextCursor?.syncToken.isNullOrBlank()) {
            // The old token is explicitly discarded. A full fetch cannot
            // manufacture a new RFC 6578 token; rediscovery will provide one
            // on a later cycle.
            return fullFetch(
                calendar = calendar,
                credentials = credentials,
                windowStart = windowStart,
                windowEnd = windowEnd,
                cursor = CollectionCursor(ctag = calendar.ctag),
                mode = CalendarFetchMode.FullFallback,
                fallbackReason = report.fallbackReason
                    ?: SyncCollectionFallbackReason.MalformedResponse,
            )
        }

        val merged = LinkedHashMap<String, CalendarResource>(cachedResources.size)
        cachedResources.forEach { resource -> merged[resource.href] = resource }

        try {
            val changed = report.changes
                .filterIsInstance<SyncCollectionChange.Changed>()
                .map { change ->
                    resolveIncrementalHref(calendar.url, change.href) to change.etag
                }
                .distinctBy { it.first }
            val changedResources = fetchChangedResources(credentials, changed)
            report.changes.forEach { change ->
                val href = resolveIncrementalHref(calendar.url, change.href)
                when (change) {
                    is SyncCollectionChange.Removed -> merged.remove(href)
                    is SyncCollectionChange.Changed -> {
                        // A resource can disappear in the small race between
                        // REPORT and GET. Treat that as a second tombstone,
                        // exactly as the server's explicit removal response.
                        changedResources[href]
                            ?.let { merged[href] = it }
                            ?: merged.remove(href)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Never advance a cursor past a delta we could not apply. A full
            // fetch is authoritative for the caller's existing semantics and
            // can still succeed when only one resource GET was transiently
            // unavailable.
            return fullFetch(
                calendar = calendar,
                credentials = credentials,
                windowStart = windowStart,
                windowEnd = windowEnd,
                cursor = previous.copy(ctag = calendar.ctag),
                mode = CalendarFetchMode.FullFallback,
            )
        }

        return IncrementalFetchResult(
            fetchResult = FetchResult(resources = merged.values.toList()),
            cursor = CollectionCursor(
                ctag = calendar.ctag,
                syncToken = requireNotNull(report.nextCursor).syncToken,
            ),
            mode = CalendarFetchMode.Incremental,
        )
    }

    /** Reads changed resources in parallel, while keeping the server bounded. */
    private suspend fun fetchChangedResources(
        credentials: DavCredentials,
        changed: List<Pair<String, String>>,
    ): Map<String, CalendarResource?> = supervisorScope {
        val limiter = Semaphore(MaxConcurrentResourceReads)
        changed.map { (href, etag) ->
            async {
                href to limiter.withPermit {
                    fetchChangedResource(credentials, href, etag)
                }
            }
        }.awaitAll().toMap()
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

    private suspend fun fetchChangedResource(
        credentials: DavCredentials,
        href: String,
        reportEtag: String,
    ): CalendarResource? {
        val response = http.request(
            method = "GET",
            url = href,
            credentials = credentials,
            headers = mapOf("Accept" to "text/calendar"),
        )
        if (response.status == 404 || response.status == 410) return null
        if (response.status !in 200..299) {
            throw calDavErrorForStatus(response.status, href, response.body)
        }
        if (response.body.isBlank()) {
            throw CalDavException(
                CalDavErrorCode.NotCalDav,
                "The server returned an empty calendar resource.",
                status = response.status,
                body = response.body,
            )
        }
        return CalendarResource(
            href = href,
            etag = normalizeEtag(response.header("ETag")) ?: reportEtag,
            ics = response.body,
        )
    }

    private suspend fun fullFetch(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        cursor: CollectionCursor,
        mode: CalendarFetchMode,
        fallbackReason: SyncCollectionFallbackReason? = null,
    ): IncrementalFetchResult {
        val result = fetch(calendar, credentials, windowStart, windowEnd)
        // A partial full fetch cannot truthfully retire changes behind a
        // token. Keep the ctag as metadata, but clear the token so the next
        // caller is forced back through a full path.
        val safeCursor = if (result.hadComponentFailures) {
            cursor.copy(syncToken = null)
        } else {
            cursor
        }
        return IncrementalFetchResult(
            fetchResult = result,
            cursor = safeCursor,
            mode = mode,
            fallbackReason = fallbackReason,
        )
    }

    /** Pulls the href, ETag and calendar text out of a multistatus reply. */
    private fun parseResources(root: Element, calendar: DiscoveredCalendar): List<CalendarResource> =
        DavXml.elements(root, DavNs.Dav, "response").map { entry ->
            val rawHref = DavXml.text(entry, DavNs.Dav, "href")
                ?: throw CalDavException(
                    CalDavErrorCode.NotCalDav,
                    "The server returned a calendar response without a resource URL.",
                )
            val data = DavXml.text(entry, DavNs.CalDav, "calendar-data")
                ?: throw CalDavException(
                    CalDavErrorCode.NotCalDav,
                    "The server returned a calendar response without calendar data.",
                )
            CalendarResource(
                href = resolveHref(calendar.url, rawHref),
                etag = normalizeEtag(DavXml.text(entry, DavNs.Dav, "getetag")),
                ics = data,
            )
        }

    /**
     * A sync response is server input, so never send credentials to another
     * origin merely because it supplied an absolute href.
     */
    private fun resolveIncrementalHref(collectionUrl: String, rawHref: String): String {
        val resolved = resolveHref(collectionUrl, rawHref)
        val collection = runCatching { URI(collectionUrl) }.getOrNull()
            ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The calendar URL could not be read.")
        val target = runCatching { URI(resolved) }.getOrNull()
            ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The server returned an invalid resource URL.")
        if (!sameOrigin(collection, target)) {
            throw CalDavException(
                CalDavErrorCode.NotCalDav,
                "The server returned a calendar resource outside its collection.",
            )
        }
        if (!resourceIsInCollection(resolved, collectionUrl)) {
            throw CalDavException(
                CalDavErrorCode.NotCalDav,
                "The server returned a calendar resource outside its collection.",
            )
        }
        return resolved
    }

    private fun sameOrigin(first: URI, second: URI): Boolean {
        if (!first.scheme.equals(second.scheme, ignoreCase = true)) return false
        if (!first.host.equals(second.host, ignoreCase = true)) return false
        val firstPort = if (first.port != -1) first.port else defaultPort(first.scheme)
        val secondPort = if (second.port != -1) second.port else defaultPort(second.scheme)
        return firstPort == secondPort
    }

    private fun defaultPort(scheme: String?): Int = when (scheme?.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }

    private fun resourceIsInCollection(resource: String, collection: String): Boolean =
        CalDavWriter.resourceIsInCollection(resource, collection)

    private companion object {
        const val Vevent = "VEVENT"
        const val Vtodo = "VTODO"
        const val Vjournal = "VJOURNAL"
        val UtcStamp: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        const val MaxConcurrentResourceReads = 4

        fun Instant.format(): String = UtcStamp.format(this)
    }
}
