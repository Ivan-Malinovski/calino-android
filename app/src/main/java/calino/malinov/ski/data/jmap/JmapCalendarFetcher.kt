package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.CalendarFetchMode
import calino.malinov.ski.data.caldav.CalendarFetching
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.CollectionCursor
import calino.malinov.ski.data.caldav.ComponentFailure
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.FetchResult
import calino.malinov.ski.data.caldav.IncrementalFetchResult
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private val UtcSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

/** A runaway `/changes` chain or query must end rather than spin. */
private const val MaxChangePages = 1000
private const val MaxQueryAttempts = 3

/**
 * The strong validator for a JMAP event: the lowercase hex SHA-256 of its
 * canonical JSON. JMAP has no per-object ETag, and the content hash changes
 * exactly when the object does; the writer recomputes it from a fresh read to
 * make a `If-Match`-style comparison. It is stored bare, like any other ETag.
 */
internal fun eventEtag(event: JSONObject): String =
    MessageDigest.getInstance("SHA-256").digest(canonicalJson(hashedFields(event)).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/**
 * Calendar membership is not part of the validator. A move into another
 * calendar adds a membership first and removes the old one afterwards, and the
 * second step must still match the validator the first step started from.
 */
private fun hashedFields(event: JSONObject): JSONObject = event.copy().also { it.remove("calendarIds") }

internal fun utcQueryTime(instant: Instant): String = UtcSeconds.format(instant)

/** One shared, connected [JmapClient] per server and login. */
class JmapClientProvider(private val http: DavHttp = DavHttp()) {
    private val clients = ConcurrentHashMap<String, JmapClient>()
    private val lock = Mutex()

    /** The client for the origin of [url]; connects on first use. */
    suspend fun client(url: String, credentials: DavCredentials): JmapClient {
        val origin = serverOrigin(url)
        val key = "$origin\u0000${credentials.username}\u0000${credentials.password}"
        clients[key]?.let { return it }
        return lock.withLock {
            clients[key] ?: JmapClient(origin, credentials, http).also {
                it.connect()
                clients[key] = it
            }
        }
    }

    /** Forgets connections, e.g. after an account is removed or its password changes. */
    fun clear() = clients.clear()
}

/**
 * Reads events from a JMAP calendar as the iCalendar the rest of the app caches.
 *
 * A listing is `CalendarEvent/query` (paged, and checked so that a page cannot
 * be skipped, repeated or taken from a different query state) followed by
 * `CalendarEvent/get`. Each event is translated with [JsCalendarToIcs]; an
 * event that cannot be translated is left out and reported as a failure, which
 * keeps the result from being treated as authoritative.
 */
class JmapCalendarFetcher(
    private val clients: JmapClientProvider,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : CalendarFetching {
    /** Origins whose server answered `inCalendar` with `unsupportedFilter`. */
    private val inCalendarsOnly = ConcurrentHashMap.newKeySet<String>()

    override suspend fun fetchAllEvents(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
    ): List<CalendarResource> = listResources(calendar, credentials, null).resources

    override suspend fun fetch(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): FetchResult = listResources(calendar, credentials, window(windowStart, windowEnd))

    override suspend fun fetchIncremental(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        cachedResources: List<CalendarResource>?,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        storedCursor: CollectionCursor?,
    ): IncrementalFetchResult {
        val previous = storedCursor ?: CollectionCursor(syncToken = calendar.syncToken)
        // A ctag is never consulted: the calendar state moves with metadata, and
        // an equal one must not hide event changes.
        if (!previous.hasSyncToken) {
            return fullWithState(calendar, credentials, windowStart, windowEnd, CalendarFetchMode.Full)
        }
        if (cachedResources == null) {
            return fullWithState(calendar, credentials, windowStart, windowEnd, CalendarFetchMode.FullFallback)
        }
        return try {
            applyChanges(calendar, credentials, cachedResources, requireNotNull(previous.syncToken))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Any trouble with the delta (an expired state, a stuck page chain,
            // a transient error) is answered by a complete listing.
            fullWithState(calendar, credentials, windowStart, windowEnd, CalendarFetchMode.FullFallback)
        }
    }

    /** One event as a resource, or null when it is gone or not in this calendar. */
    suspend fun fetchResource(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        href: String,
    ): CalendarResource? {
        val client = clients.client(calendar.url, credentials)
        val target = parseJmapUrl(href, calendar.url, client.accountId, event = true)
        val calendarId = parseJmapUrl(calendar.url, calendar.url, client.accountId, event = false).calendarId
        if (target.calendarId != calendarId) return null
        val event = getEvents(client, listOf(requireNotNull(target.eventId))).firstOrNull() ?: return null
        return resourceOf(calendar, client.accountId, calendarId, event)
    }

    /** Looks an event up by iCalendar UID; the server id is not the UID. */
    suspend fun findByUid(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        uid: String,
    ): CalendarResource? =
        listResources(calendar, credentials, null).resources.firstOrNull { resource ->
            resource.ics.lineSequence().any { it.trimEnd('\r') == "UID:$uid" }
        }

    /** Every event in [calendarId], unfiltered by time. */
    internal suspend fun eventsInCalendar(client: JmapClient, calendarId: String): List<JSONObject> =
        getEvents(client, queryIds(client, calendarId, null)).filter { isMember(it, calendarId) }

    private fun window(start: LocalDate, end: LocalDate) = QueryWindow(
        after = utcQueryTime(start.atStartOfDay(zone).toInstant()),
        before = utcQueryTime(end.atStartOfDay(zone).toInstant()),
    )

    private class QueryWindow(val after: String, val before: String)

    private class Converted(val resources: List<CalendarResource>, val failed: List<String>)

    private suspend fun listResources(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        window: QueryWindow?,
    ): FetchResult {
        val client = clients.client(calendar.url, credentials)
        val calendarId = parseJmapUrl(calendar.url, calendar.url, client.accountId, event = false).calendarId
        val events = getEvents(client, queryIds(client, calendarId, window))
        return convert(calendar, client.accountId, calendarId, events)
    }

    private fun convert(
        calendar: DiscoveredCalendar,
        accountId: String,
        calendarId: String,
        events: List<JSONObject>,
    ): FetchResult {
        val resources = mutableListOf<CalendarResource>()
        val failed = mutableListOf<String>()
        for (event in events) {
            if (!isMember(event, calendarId)) continue
            try {
                resourceOf(calendar, accountId, calendarId, event)?.let(resources::add)
            } catch (error: JsCalendarException) {
                failed += "${event.stringOrNull("title") ?: event.stringOrNull("id") ?: "An event"}: ${error.message}"
            }
        }
        return FetchResult(resources, failures(failed))
    }

    private fun failures(failed: List<String>): List<ComponentFailure> {
        if (failed.isEmpty()) return emptyList()
        val summary = if (failed.size == 1) {
            "One event could not be read (${failed.first()})."
        } else {
            "${failed.size} events could not be read (${failed.first()})."
        }
        return listOf(ComponentFailure("VEVENT", CalDavException(CalDavErrorCode.Rejected, summary)))
    }

    private fun isMember(event: JSONObject, calendarId: String): Boolean =
        event.objectOrNull("calendarIds")?.boolOrNull(calendarId) == true

    private fun resourceOf(
        calendar: DiscoveredCalendar,
        accountId: String,
        calendarId: String,
        event: JSONObject,
    ): CalendarResource? {
        val id = event.stringOrNull("id") ?: return null
        if (!isMember(event, calendarId)) return null
        return CalendarResource(
            href = jmapEventUrl(calendar.url, accountId, calendarId, id),
            etag = eventEtag(event),
            ics = JsCalendarToIcs.convert(event),
        )
    }

    /** The CalendarEvent state, captured before a listing so later changes are never missed. */
    private suspend fun currentState(client: JmapClient): String =
        client.invoke("CalendarEvent/get", JSONObject().put("ids", JSONArray()))
            .stringOrNull("state") ?: throw invalidResponse("CalendarEvent/get omitted state")

    private suspend fun fullWithState(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        mode: CalendarFetchMode,
    ): IncrementalFetchResult {
        val client = clients.client(calendar.url, credentials)
        val state = currentState(client)
        return IncrementalFetchResult(
            fetchResult = fetch(calendar, credentials, windowStart, windowEnd),
            cursor = CollectionCursor(syncToken = state),
            mode = mode,
        )
    }

    private suspend fun applyChanges(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        cached: List<CalendarResource>,
        sinceState: String,
    ): IncrementalFetchResult {
        val client = clients.client(calendar.url, credentials)
        val calendarId = parseJmapUrl(calendar.url, calendar.url, client.accountId, event = false).calendarId
        val changed = LinkedHashSet<String>()
        val destroyed = LinkedHashSet<String>()
        var since = sinceState
        var latest: String
        var pages = 0
        while (true) {
            if (++pages > MaxChangePages) throw invalidResponse("JMAP change list did not end")
            val result = client.invoke("CalendarEvent/changes", JSONObject().put("sinceState", since))
            for (key in listOf("created", "updated")) {
                result.arrayOrNull(key)?.toList()?.filterIsInstance<String>()?.let(changed::addAll)
            }
            result.arrayOrNull("destroyed")?.toList()?.filterIsInstance<String>()?.let(destroyed::addAll)
            latest = result.stringOrNull("newState") ?: throw invalidResponse("CalendarEvent/changes omitted newState")
            if (result.boolOrNull("hasMoreChanges") != true) break
            if (latest == since) throw invalidResponse("CalendarEvent/changes made no progress")
            since = latest
        }
        changed.removeAll(destroyed)

        val merged = LinkedHashMap<String, CalendarResource>(cached.size)
        cached.forEach { merged[it.href] = it }
        fun hrefOf(id: String) = jmapEventUrl(calendar.url, client.accountId, calendarId, id)
        destroyed.forEach { merged.remove(hrefOf(it)) }

        val present = getEvents(client, changed.toList()).associateBy { it.stringOrNull("id") }
        val failed = mutableListOf<String>()
        for (id in changed) {
            val href = hrefOf(id)
            val event = present[id]
            if (event == null || !isMember(event, calendarId)) {
                // Deleted since, or moved out of this calendar.
                merged.remove(href)
                continue
            }
            try {
                val resource = resourceOf(calendar, client.accountId, calendarId, event)
                if (resource != null) merged[href] = resource else merged.remove(href)
            } catch (error: JsCalendarException) {
                merged.remove(href)
                failed += "${event.stringOrNull("title") ?: id}: ${error.message}"
            }
        }
        return IncrementalFetchResult(
            fetchResult = FetchResult(merged.values.toList(), failures(failed)),
            cursor = CollectionCursor(syncToken = latest),
            mode = CalendarFetchMode.Incremental,
        )
    }

    /** `CalendarEvent/get` for [ids]; the client splits it to the server's limits. */
    internal suspend fun getEvents(client: JmapClient, ids: List<String>): List<JSONObject> {
        if (ids.isEmpty()) return emptyList()
        val result = client.invoke("CalendarEvent/get", JSONObject().put("ids", jsonArray(ids)))
        return result.arrayOrNull("list")?.toList()?.filterIsInstance<JSONObject>()
            ?: throw invalidResponse("CalendarEvent/get omitted list")
    }

    /**
     * Every event id in the calendar, optionally within a window. Pages are
     * accepted only while they continue exactly where the last one ended and
     * the query state holds still; otherwise the query is run again.
     */
    private suspend fun queryIds(client: JmapClient, calendarId: String, window: QueryWindow?): List<String> {
        var lastError: JmapException? = null
        repeat(MaxQueryAttempts) {
            try {
                return queryOnce(client, calendarId, window)
            } catch (error: JmapException) {
                if (error.type != "stateMismatch") throw error
                lastError = error
            }
        }
        throw lastError ?: invalidResponse("CalendarEvent/query kept changing")
    }

    private suspend fun queryOnce(client: JmapClient, calendarId: String, window: QueryWindow?): List<String> {
        val origin = serverOrigin(client.apiUrl)
        val ids = ArrayList<String>()
        val seen = HashSet<String>()
        var queryState: String? = null
        while (true) {
            val filter = JSONObject()
            if (origin in inCalendarsOnly) {
                filter.put("inCalendars", jsonArray(listOf(calendarId)))
            } else {
                filter.put("inCalendar", calendarId)
            }
            window?.let { filter.put("after", it.after).put("before", it.before) }
            val arguments = JSONObject()
                .put("filter", filter)
                .put("position", ids.size)
                .put("calculateTotal", true)
            val result = try {
                client.invoke("CalendarEvent/query", arguments)
            } catch (error: JmapException) {
                if (error.type == "unsupportedFilter" && inCalendarsOnly.add(origin)) {
                    ids.clear()
                    seen.clear()
                    queryState = null
                    continue
                }
                throw error
            }
            val state = result.stringOrNull("queryState")
            if (queryState != null && state != queryState) {
                throw JmapException(
                    CalDavErrorCode.PreconditionFailed,
                    "The calendar changed while it was being listed.",
                    status = 412,
                    type = "stateMismatch",
                )
            }
            queryState = state
            if ((result.longOrNull("position") ?: 0L) != ids.size.toLong()) {
                throw invalidResponse("CalendarEvent/query returned an unexpected page position")
            }
            val page = result.arrayOrNull("ids")?.toList()?.filterIsInstance<String>()
                ?: throw invalidResponse("CalendarEvent/query omitted ids")
            if (page.isEmpty()) break
            for (id in page) {
                if (!seen.add(id)) throw invalidResponse("CalendarEvent/query repeated an event")
                ids += id
            }
            val total = result.longOrNull("total")
            if (total != null && ids.size >= total) break
        }
        return ids
    }
}
