package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.ContactAddressBook
import java.net.URI
import org.w3c.dom.Element

/** A CardDAV address book collection as advertised by its server. */
data class DiscoveredAddressBook(
    val url: String,
    val displayName: String,
    val description: String? = null,
    val readOnly: Boolean = false,
    val ctag: String? = null,
    val syncToken: String? = null,
)

/** The CardDAV half of an account discovery walk. */
data class DiscoveredCardDavAccount(
    val baseUrl: String,
    val principalUrl: String,
    val homeSetUrl: String,
    val addressBooks: List<DiscoveredAddressBook>,
)

/** Independent CardDAV discovery; the address-book home is not calendar-home. */
class CardDavDiscovery(private val http: DavHttp = DavHttp()) {

    suspend fun discoverAccount(enteredUrl: String, credentials: DavCredentials): DiscoveredCardDavAccount {
        val base = resolveBaseUrl(enteredUrl, credentials)
        val principal = findPrincipal(base, credentials) ?: base
        val home = findAddressBookHome(principal, credentials) ?: principal
        return DiscoveredCardDavAccount(base, principal, home, listAddressBooks(home, credentials))
    }

    suspend fun listAddressBooks(homeUrl: String, credentials: DavCredentials): List<DiscoveredAddressBook> {
        val response = propfind(homeUrl, credentials, "1", PropfindAddressBooks)
        if (!response.isMultiStatus) throw calDavErrorForStatus(response.status, homeUrl)
        val root = DavXml.parse(response.body)
            ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The server's reply could not be read.")
        return DavXml.elements(root, DavNs.Dav, "response").mapNotNull { parseAddressBookResponse(it, homeUrl) }
    }

    private suspend fun resolveBaseUrl(enteredUrl: String, credentials: DavCredentials): String {
        probeWellKnown(enteredUrl, credentials)?.let { return it }
        return enteredUrl
    }

    private suspend fun probeWellKnown(baseUrl: String, credentials: DavCredentials): String? {
        val response = runCatching {
            propfind(joinUrl(baseUrl, "/.well-known/carddav"), credentials, "0", CalDavDiscovery.PropfindDisplayName)
        }.getOrNull() ?: return null
        if (!CalDavDiscovery.isDavStatus(response.status)) return null
        if (response.url.contains("/.well-known/")) return null
        requireSameDavOrigin(baseUrl, response.url)
        return stripTrailingSlash(response.url)
    }

    private suspend fun findPrincipal(baseUrl: String, credentials: DavCredentials): String? {
        val response = propfind(baseUrl, credentials, "0", CalDavDiscovery.PropfindCurrentUserPrincipal)
        if (response.status == 401 || response.status == 403) throw calDavErrorForStatus(response.status, baseUrl)
        if (!response.isMultiStatus) throw calDavErrorForStatus(response.status, baseUrl)
        val root = DavXml.parse(response.body) ?: return null
        val holder = DavXml.element(root, DavNs.Dav, "current-user-principal") ?: return null
        return DavXml.text(holder, DavNs.Dav, "href")?.let { resolveDavHref(baseUrl, it) }
    }

    private suspend fun findAddressBookHome(principalUrl: String, credentials: DavCredentials): String? {
        val response = runCatching {
            propfind(principalUrl, credentials, "0", PropfindAddressBookHomeSet)
        }.getOrNull() ?: return null
        if (!response.isMultiStatus) return null
        val root = DavXml.parse(response.body) ?: return null
        val holder = DavXml.element(root, DavNs.CardDav, "addressbook-home-set") ?: return null
        return DavXml.text(holder, DavNs.Dav, "href")?.let { resolveDavHref(principalUrl, it) }
    }

    private fun parseAddressBookResponse(entry: Element, homeUrl: String): DiscoveredAddressBook? {
        val href = DavXml.text(entry, DavNs.Dav, "href") ?: return null
        val resourceType = DavXml.element(entry, DavNs.Dav, "resourcetype") ?: return null
        if (!DavXml.hasElement(resourceType, "addressbook")) return null
        if (DavXml.hasElement(resourceType, "calendar")) return null
        val url = resolveDavHref(homeUrl, href)
        return DiscoveredAddressBook(
            url = url,
            displayName = DavXml.text(entry, DavNs.Dav, "displayname")
                ?: url.trimEnd('/').substringAfterLast('/'),
            description = DavXml.text(entry, DavNs.CardDav, "addressbook-description"),
            readOnly = isReadOnly(entry),
            ctag = DavXml.text(entry, DavNs.CalendarServer, "getctag"),
            syncToken = DavXml.text(entry, DavNs.Dav, "sync-token")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
        )
    }

    /** Missing privilege metadata means writable, matching CalDAV discovery. */
    private fun isReadOnly(entry: Element): Boolean {
        val privileges = DavXml.element(entry, DavNs.Dav, "current-user-privilege-set") ?: return false
        val granted = DavXml.elements(privileges, DavNs.Dav, "privilege")
            .flatMap { privilege ->
                val nodes = privilege.getElementsByTagName("*")
                (0 until nodes.length).mapNotNull { index ->
                    (nodes.item(index) as? Element)?.let { it.localName ?: it.tagName.substringAfterLast(':') }
                }
            }
            .map { it.lowercase() }
            .toSet()
        if (granted.isEmpty()) return false
        return setOf("write", "all", "write-content", "write-properties", "bind", "unlocked").none { it in granted }
    }

    private suspend fun propfind(url: String, credentials: DavCredentials, depth: String, body: String): DavResponse =
        http.request(
            method = "PROPFIND",
            url = url,
            credentials = credentials,
            headers = mapOf("Depth" to depth, "Content-Type" to "application/xml; charset=utf-8"),
            body = body,
        )

    companion object {
        const val PropfindAddressBookHomeSet = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav"><d:prop><card:addressbook-home-set/></d:prop></d:propfind>"""

        const val PropfindAddressBooks = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav" xmlns:cs="http://calendarserver.org/ns/">
  <d:prop><d:resourcetype/><d:displayname/><d:current-user-privilege-set/><d:sync-token/><cs:getctag/><card:addressbook-description/></d:prop>
</d:propfind>"""
    }
}

internal fun DiscoveredAddressBook.toContactAddressBook(accountId: String) = ContactAddressBook(
    id = url,
    accountId = accountId,
    url = url,
    name = displayName,
    description = description,
    ctag = ctag,
    syncToken = syncToken,
    readOnly = readOnly,
)
