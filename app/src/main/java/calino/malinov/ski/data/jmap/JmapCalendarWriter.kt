package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.CalDavWriter
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.CalendarWriting
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavPrecondition
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.PreparedCalendarWrite
import calino.malinov.ski.data.caldav.WrittenCalendarResource
import calino.malinov.ski.data.caldav.normalizeEtag
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

private const val Method = "CalendarEvent/set"

/**
 * Writes JMAP events behind the iCalendar-shaped [CalendarWriting] seam.
 *
 * Preparing a write is CalDAV's job and works on the cached `.ics`: the body is
 * a patched copy of the bytes the server last sent, so everything Calino does
 * not model survives. Sending is where this class differs. The body is
 * converted to JSCalendar, diffed against a fresh read of the event, and only
 * the changed properties are sent as a PatchObject. The ETag is the hash of
 * that fresh read, so a stale edit is refused (412) before anything is sent.
 *
 * A JMAP server assigns the event id, so a create returns a different href than
 * the placeholder it was prepared with; the repository re-keys its overlay from
 * the returned href.
 */
class JmapCalendarWriter(
    private val clients: JmapClientProvider,
    private val fetcher: JmapCalendarFetcher,
    private val inner: CalDavWriter,
    private val cache: CalendarCache = CalendarCache.None,
) : CalendarWriting by inner {
    /** Origins whose server rejected `sendSchedulingMessages`; later writes omit it. */
    private val schedulingRejected = ConcurrentHashMap.newKeySet<String>()

    /** Origins whose server rejected a `uid` query filter; those scan the calendar instead. */
    private val uidFilterRejected = ConcurrentHashMap.newKeySet<String>()

    override suspend fun putPrepared(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        prepared: PreparedCalendarWrite,
    ): WrittenCalendarResource {
        val client = clients.client(calendar.url, credentials)
        val calendarId = calendarIdOf(calendar, client)
        val next = convert(prepared.body)
        val event = when (val precondition = prepared.precondition) {
            is DavPrecondition.Match -> update(client, calendar, calendarId, prepared.href, precondition.etag, next.event)
            else -> create(client, calendar, calendarId, next)
        }
        val written = WrittenCalendarResource(
            href = jmapEventUrl(calendar.url, client.accountId, calendarId, event.getString("id")),
            etag = eventEtag(event),
            ics = JsCalendarToIcs.convert(event),
        )
        cache.saveResource(calendar.url, CalendarResource(written.href, written.etag, written.ics))
        return written
    }

    override suspend fun refreshResource(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        href: String,
        uid: String,
    ): CalendarResource {
        val client = clients.client(calendar.url, credentials)
        val calendarId = calendarIdOf(calendar, client)
        val resource = fetcher.fetchResource(calendar, credentials, href)
            ?: lookup(client, calendarId, uid)
                ?.let { CalendarResource(eventHref(calendar, client, calendarId, it), eventEtag(it), JsCalendarToIcs.convert(it)) }
            ?: throw notFound()
        cache.saveResource(calendar.url, resource)
        return resource
    }

    override suspend fun delete(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        href: String,
        uid: String,
        etag: String?,
        component: String?,
    ) {
        val client = clients.client(calendar.url, credentials)
        val calendarId = calendarIdOf(calendar, client)
        val target = parseJmapUrl(href, calendar.url, client.accountId, event = true)
        val expected = normalizeEtag(etag) ?: throw stale()
        val previous = read(client, requireNotNull(target.eventId))
        if (previous == null || !isMember(previous, target.calendarId)) {
            // Gone already, or moved out by an earlier attempt of this same delete.
            cache.deleteResource(calendar.url, href)
            return
        }
        val others = previous.objectOrNull("calendarIds")?.entryList()
            ?.any { (id, member) -> id != target.calendarId && member == true } == true
        if (others) {
            // Only this calendar's membership goes; the event lives on elsewhere.
            // Its content is untouched, so a changed validator is not a conflict:
            // a move that also edited the event legitimately changed it after
            // the caller read the source.
            val patch = JSONObject().put(membershipKey(target.calendarId), JSONObject.NULL)
            set(client, "update", requireNotNull(target.eventId), patch, scheduling = false)
        } else {
            if (eventEtag(previous) != expected) throw stale()
            try {
                set(client, "destroy", requireNotNull(target.eventId), null, scheduling = hasParticipants(previous))
            } catch (error: JmapException) {
                if (error.type != "notFound") throw error
            }
        }
        cache.deleteResource(calendar.url, href)
    }

    private suspend fun create(
        client: JmapClient,
        calendar: DiscoveredCalendar,
        calendarId: String,
        next: IcsToJsCalendar.Converted,
    ): JSONObject {
        val known = lookupAll(client, calendarId, next.uid)
        val here = known.firstOrNull { isMember(it, calendarId) }
        if (here != null) {
            // The same event already sits in the destination. If it says what we
            // are about to say, this is a retry whose response was lost.
            if (eventPatch(here, next.event).length() == 0) return here
            throw stale()
        }
        known.firstOrNull()?.let { existing ->
            // Same account, other calendar: this create is the first half of a
            // move. Add the membership now; the caller removes the old one next.
            val patch = eventPatch(existing, next.event).put(membershipKey(calendarId), true)
            return updateWith(client, existing, patch)
        }
        val value = next.event.copy().put("calendarIds", JSONObject().put(calendarId, true))
        val created = set(client, "create", "event", value, scheduling = hasParticipants(next.event))
        val id = created.stringOrNull("id") ?: throw invalidResponse("$Method omitted the created id")
        return read(client, id) ?: throw invalidResponse("The created event could not be read back")
    }

    private suspend fun update(
        client: JmapClient,
        calendar: DiscoveredCalendar,
        calendarId: String,
        href: String,
        etag: String,
        next: JSONObject,
    ): JSONObject {
        val target = parseJmapUrl(href, calendar.url, client.accountId, event = true)
        if (target.calendarId != calendarId) throw outsideCalendar()
        val previous = read(client, requireNotNull(target.eventId))
        if (previous == null || !isMember(previous, calendarId)) throw notFound()
        if (normalizeEtag(etag) != eventEtag(previous)) throw stale()
        return updateWith(client, previous, eventPatch(previous, next))
    }

    private suspend fun updateWith(client: JmapClient, previous: JSONObject, patch: JSONObject): JSONObject {
        val id = previous.getString("id")
        if (patch.length() == 0) return previous
        val scheduling = !onlyMembership(patch) && hasParticipants(previous)
        set(client, "update", id, patch, scheduling)
        return read(client, id) ?: throw invalidResponse("The updated event could not be read back")
    }

    // --- looking events up ----------------------------------------------------------------

    private suspend fun read(client: JmapClient, id: String): JSONObject? =
        fetcher.getEvents(client, listOf(id)).firstOrNull()

    /** Events in this account carrying [uid], wherever they sit. */
    private suspend fun lookupAll(client: JmapClient, calendarId: String, uid: String): List<JSONObject> {
        val origin = serverOrigin(client.apiUrl)
        if (origin !in uidFilterRejected) {
            try {
                val result = client.invoke(
                    "CalendarEvent/query",
                    JSONObject().put("filter", JSONObject().put("uid", uid)),
                )
                val ids = result.arrayOrNull("ids")?.toList()?.filterIsInstance<String>().orEmpty()
                return fetcher.getEvents(client, ids).filter { it.stringOrNull("uid") == uid }
            } catch (error: JmapException) {
                if (error.type != "unsupportedFilter" && error.type != "invalidArguments") throw error
                uidFilterRejected += origin
            }
        }
        return fetcher.eventsInCalendar(client, calendarId).filter { it.stringOrNull("uid") == uid }
    }

    private suspend fun lookup(client: JmapClient, calendarId: String, uid: String): JSONObject? =
        lookupAll(client, calendarId, uid).firstOrNull { isMember(it, calendarId) }

    // --- the set call ---------------------------------------------------------------------

    /**
     * One `CalendarEvent/set` operation; returns the created object for a create,
     * and an empty object otherwise. Servers do not send invitations, replies or
     * cancellations on their own, so `sendSchedulingMessages` is requested when
     * attendees are involved. A server that refuses the argument is remembered
     * and the write retried without it: scheduling is best effort, never a blocker.
     */
    private suspend fun set(
        client: JmapClient,
        operation: String,
        id: String,
        value: JSONObject?,
        scheduling: Boolean,
    ): JSONObject {
        val origin = serverOrigin(client.apiUrl)
        fun arguments(withScheduling: Boolean): JSONObject {
            val args = JSONObject()
            if (operation == "destroy") args.put("destroy", jsonArray(listOf(id))) else args.put(operation, JSONObject().put(id, value))
            if (withScheduling) args.put("sendSchedulingMessages", true)
            return args
        }
        val result = if (!scheduling || origin in schedulingRejected) {
            client.invoke(Method, arguments(false))
        } else {
            try {
                client.invoke(Method, arguments(true))
            } catch (error: JmapException) {
                if (error.type?.endsWith("invalidArguments") != true) throw error
                schedulingRejected += origin
                client.invoke(Method, arguments(false))
            }
        }
        val failures = result.objectOrNull(
            when (operation) {
                "create" -> "notCreated"
                "update" -> "notUpdated"
                else -> "notDestroyed"
            },
        )
        failures?.objectOrNull(id)?.let { throw setFailure(it) }
        return if (operation == "create") result.objectOrNull("created")?.objectOrNull(id) ?: JSONObject() else JSONObject()
    }

    private fun setFailure(failure: JSONObject): JmapException {
        val type = failure.stringOrNull("type") ?: "unknown"
        val short = type.substringAfterLast(':')
        val status = when (short) {
            "invalidProperties", "invalidPatch" -> 400
            "alreadyExists" -> 409
            "tooLarge" -> 413
            else -> methodStatus(type)
        }
        val properties = failure.arrayOrNull("properties")?.toList()?.filterIsInstance<String>().orEmpty()
        val description = failure.stringOrNull("description").orEmpty()
        return JmapException(
            code = codeForStatus(status),
            message = "JMAP $Method: $type" +
                (if (properties.isNotEmpty()) " (${properties.joinToString()})" else "") +
                (if (description.isNotEmpty()) ": $description" else ""),
            status = status,
            body = failure.toString().take(400),
            type = short,
            method = Method,
            details = failure,
        )
    }

    // --- small helpers --------------------------------------------------------------------

    private fun convert(ics: String): IcsToJsCalendar.Converted = try {
        IcsToJsCalendar.convert(ics)
    } catch (error: CancellationException) {
        throw error
    } catch (error: JsCalendarException) {
        throw CalDavException(
            CalDavErrorCode.Rejected,
            error.message ?: "That item cannot be stored on a JMAP server.",
            error,
            status = 400,
        )
    }

    private fun calendarIdOf(calendar: DiscoveredCalendar, client: JmapClient): String =
        requireNotNull(parseJmapUrl(calendar.url, calendar.url, client.accountId, event = false).calendarId)

    private fun eventHref(calendar: DiscoveredCalendar, client: JmapClient, calendarId: String, event: JSONObject) =
        jmapEventUrl(calendar.url, client.accountId, calendarId, event.getString("id"))

    private fun isMember(event: JSONObject, calendarId: String): Boolean =
        event.objectOrNull("calendarIds")?.boolOrNull(calendarId) == true

    private fun hasParticipants(event: JSONObject): Boolean =
        (event.objectOrNull("participants")?.length() ?: 0) > 0

    private fun membershipKey(calendarId: String): String =
        "calendarIds/" + calendarId.replace("~", "~0").replace("/", "~1")

    private fun stale() = JmapException(
        CalDavErrorCode.PreconditionFailed,
        "That item changed on the server. Refresh and try again.",
        status = 412,
        type = "stateMismatch",
    )

    private fun notFound() = JmapException(
        CalDavErrorCode.NotFound,
        "That item is no longer on the server.",
        status = 404,
        type = "notFound",
    )

    private fun outsideCalendar() = JmapException(
        CalDavErrorCode.Rejected,
        "That item does not belong to this calendar.",
        status = 400,
        type = "invalidArguments",
    )
}
