package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DiscoveredAccount
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.normalizeColor
import calino.malinov.ski.data.model.CalendarProtocol
import org.json.JSONObject

/** Any one of these rights makes a calendar writable (current drafts and older per-item ones). */
private val WriteRights = listOf("mayWriteAll", "mayWriteOwn", "mayAddItems", "mayModifyItems", "mayRemoveItems")

/** Lists the calendars of the primary JMAP calendars account as [DiscoveredCalendar]s. */
class JmapDiscovery(private val http: DavHttp = DavHttp()) {

    suspend fun discoverAccount(serverUrl: String, credentials: DavCredentials): DiscoveredAccount {
        val client = JmapClient(serverUrl, credentials, http)
        client.connect()
        return discoverAccount(client, serverUrl)
    }

    internal suspend fun discoverAccount(client: JmapClient, serverUrl: String): DiscoveredAccount {
        val accountId = client.accountId
        // The event state is captured *before* listing, so the first full sync
        // starts from a cursor that any later change moves past.
        val eventState = client.invoke("CalendarEvent/get", JSONObject().put("ids", org.json.JSONArray()))
            .stringOrNull("state") ?: throw invalidResponse("CalendarEvent/get omitted state")
        val calendars = client.invoke("Calendar/get", JSONObject().put("ids", JSONObject.NULL))
            .arrayOrNull("list")?.toList()?.filterIsInstance<JSONObject>()
            ?: throw invalidResponse("Calendar/get omitted list")
        val accountReadOnly = client.session.accounts[accountId]?.isReadOnly == true
        val found = calendars.mapNotNull { calendar ->
            val id = calendar.stringOrNull("id") ?: return@mapNotNull null
            discoveredCalendar(serverUrl, accountId, id, calendar, accountReadOnly, eventState)
        }
        val prefix = jmapAccountPrefix(serverUrl, accountId)
        return DiscoveredAccount(
            baseUrl = serverOrigin(serverUrl),
            principalUrl = prefix,
            homeSetUrl = prefix,
            calendars = found,
        )
    }
}

internal fun discoveredCalendar(
    serverUrl: String,
    accountId: String,
    id: String,
    calendar: JSONObject,
    accountReadOnly: Boolean,
    eventState: String,
): DiscoveredCalendar {
    val rights = calendar.objectOrNull("myRights")
    val writable = rights == null || WriteRights.any { rights.boolOrNull(it) == true }
    // A subscribed calendar is somebody else's feed: never write to it.
    val subscribed = calendar.boolOrNull("isSubscribed") == true
    return DiscoveredCalendar(
        url = jmapCalendarUrl(serverUrl, accountId, id),
        displayName = calendar.stringOrNull("name") ?: "Unnamed Calendar",
        color = normalizeColor(calendar.stringOrNull("color")),
        readOnly = accountReadOnly || !writable || subscribed,
        components = setOf("VEVENT"),
        // Calendar state moves with calendar metadata, so it must not act as a
        // ctag: the sync layer would skip event changes. The cursor is the
        // CalendarEvent state.
        ctag = null,
        syncToken = eventState,
        protocol = CalendarProtocol.Jmap,
    )
}
