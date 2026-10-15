package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DiscoveredAddressBook
import calino.malinov.ski.data.caldav.DiscoveredCardDavAccount
import calino.malinov.ski.data.model.CalendarProtocol
import org.json.JSONObject

private val ContactWriteRights = listOf("mayWrite", "mayWriteAll", "mayWriteOwn", "mayAddItems", "mayModifyItems", "mayRemoveItems")

/** Lists the address books of the primary JMAP contacts account. */
class JmapContactsDiscovery(
    private val http: DavHttp = DavHttp(),
    private val clients: JmapClientProvider? = null,
) {
    suspend fun discoverAccount(serverUrl: String, credentials: DavCredentials): DiscoveredCardDavAccount {
        val client = clients?.client(serverUrl, credentials) ?: JmapClient(serverUrl, credentials, http).also { it.connect() }
        return discoverAccount(client, serverUrl)
    }

    internal suspend fun discoverAccount(client: JmapClient, serverUrl: String): DiscoveredCardDavAccount {
        val origin = serverOrigin(serverUrl)
        // A server without contacts is a calendar-only account, not an error.
        val accountId = client.session.contactsAccountId
            ?: return DiscoveredCardDavAccount(origin, origin, origin, emptyList())
        val books = client.invokeContacts("AddressBook/get", JSONObject().put("ids", JSONObject.NULL))
            .arrayOrNull("list")?.toList()?.filterIsInstance<JSONObject>()
            ?: throw invalidResponse("AddressBook/get omitted list")
        val accountReadOnly = client.session.accounts[accountId]?.isReadOnly == true
        val found = books.mapNotNull { book ->
            val id = book.stringOrNull("id") ?: return@mapNotNull null
            val rights = book.objectOrNull("myRights")
            val writable = rights == null || ContactWriteRights.any { rights.boolOrNull(it) == true }
            DiscoveredAddressBook(
                url = jmapAddressBookUrl(serverUrl, accountId, id),
                displayName = book.stringOrNull("name") ?: "Contacts",
                description = book.stringOrNull("description"),
                readOnly = accountReadOnly || !writable,
                // AddressBook state moves with metadata; the fetcher captures
                // the ContactCard state itself, so neither may skip a sync.
                ctag = null,
                syncToken = null,
                protocol = CalendarProtocol.Jmap,
            )
        }
        val prefix = jmapAccountPrefix(serverUrl, accountId)
        return DiscoveredCardDavAccount(origin, prefix, prefix, found)
    }
}
