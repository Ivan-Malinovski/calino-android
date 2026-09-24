package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.CalDavForm
import calino.malinov.ski.state.CalDavConnectResult
import calino.malinov.ski.state.normalizeServerUrl
import calino.malinov.ski.data.repository.CalDavClient
import java.net.URI
import org.w3c.dom.Element

/**
 * A calendar collection as the server describes it.
 *
 * [url] is the absolute collection URL and doubles as the stable id, so it
 * survives a rename of the display name.
 */
data class DiscoveredCalendar(
    val url: String,
    val displayName: String,
    val color: Long,
    val readOnly: Boolean,
    val components: Set<String>,
    val ctag: String? = null,
    val syncToken: String? = null,
)

/** Everything a fetch needs after a successful connect. */
data class DiscoveredAccount(
    val baseUrl: String,
    val principalUrl: String,
    val homeSetUrl: String,
    val calendars: List<DiscoveredCalendar>,
)

/**
 * Real CalDAV discovery: well-known probe, principal lookup, calendar-home
 * lookup, then the collection listing.
 *
 * This is the HTTP implementation of the [CalDavClient] seam that
 * `FixtureCalDavClient` previously simulated.
 */
class CalDavDiscovery(private val http: DavHttp = DavHttp()) : CalDavClient {

    override suspend fun discover(form: CalDavForm): CalDavConnectResult {
        val entered = normalizeServerUrl(form.serverUrl)
            ?: return CalDavConnectResult.Failed("That server address could not be read.")
        return try {
            val account = discoverAccount(entered, DavCredentials(form.username, form.password))
            if (account.calendars.isEmpty()) {
                CalDavConnectResult.Failed("Connected, but that account has no calendars.")
            } else {
                CalDavConnectResult.Discovered(account.calendars.map { it.toCalDavCalendar() })
            }
        } catch (error: Throwable) {
            CalDavConnectResult.Failed(calDavErrorForThrowable(error, entered).message)
        }
    }

    /** The full walk, returning the URLs a later fetch needs. */
    suspend fun discoverAccount(enteredUrl: String, credentials: DavCredentials): DiscoveredAccount {
        val base = resolveBaseUrl(enteredUrl, credentials)
        val principal = findPrincipal(base, credentials) ?: base
        val home = findCalendarHome(principal, credentials) ?: principal
        return DiscoveredAccount(
            baseUrl = base,
            principalUrl = principal,
            homeSetUrl = home,
            calendars = listCalendars(home, credentials),
        )
    }

    // --- step 1: where does this server actually live -------------------------

    /**
     * Resolves the CalDAV root from whatever the user typed.
     *
     * The well-known probe is judged by *status*, never by the URL it lands on.
     * Radicale redirects `/.well-known/caldav` to `/` and then on to its web UI
     * at `/.web`; a path-based check would accept that HTML page as the DAV
     * root. A 207 or a 401 both prove the endpoint understood `PROPFIND` --
     * the 401 case matters, since an auth challenge is itself evidence that
     * something DAV-shaped is listening.
     */
    private suspend fun resolveBaseUrl(enteredUrl: String, credentials: DavCredentials): String {
        probeWellKnown(enteredUrl, credentials)?.let { return it }
        caldavSubdomain(enteredUrl)?.let { candidate ->
            if (isDavEndpoint(candidate, credentials)) return candidate
        }
        // Fall back to what the user typed. If it is wrong, the principal
        // lookup below reports a specific failure rather than a vague one.
        return enteredUrl
    }

    private suspend fun probeWellKnown(baseUrl: String, credentials: DavCredentials): String? {
        val wellKnown = joinUrl(baseUrl, "/.well-known/caldav")
        val response = runCatching { propfind(wellKnown, credentials, depth = "0", body = PropfindDisplayName) }
            .getOrNull() ?: return null
        if (!isDavStatus(response.status)) return null
        val landed = response.url
        // A redirect that ends back on /.well-known/ means the server has no
        // opinion; treat it as unsupported rather than as the calendar root.
        if (landed.contains("/.well-known/")) return null
        return stripTrailingSlash(landed)
    }

    private suspend fun isDavEndpoint(url: String, credentials: DavCredentials): Boolean =
        runCatching { propfind(url, credentials, depth = "0", body = PropfindDisplayName) }
            .getOrNull()?.let { isDavStatus(it.status) } ?: false

    // --- step 2 and 3: principal, then calendar home --------------------------

    private suspend fun findPrincipal(baseUrl: String, credentials: DavCredentials): String? {
        val response = propfind(baseUrl, credentials, depth = "0", body = PropfindCurrentUserPrincipal)
        if (response.status == 401 || response.status == 403) {
            throw calDavErrorForStatus(response.status, baseUrl)
        }
        if (!response.isMultiStatus) throw calDavErrorForStatus(response.status, baseUrl)
        val root = DavXml.parse(response.body) ?: return null
        val holder = DavXml.elements(root, DavNs.Dav, "response").firstNotNullOfOrNull {
            DavXml.successfulProperty(it, DavNs.Dav, "current-user-principal")
        } ?: return null
        val href = DavXml.text(holder, DavNs.Dav, "href") ?: return null
        return resolveHref(baseUrl, href)
    }

    private suspend fun findCalendarHome(principalUrl: String, credentials: DavCredentials): String? {
        val response = runCatching {
            propfind(principalUrl, credentials, depth = "0", body = PropfindCalendarHomeSet)
        }.getOrNull() ?: return null
        if (!response.isMultiStatus) return null
        val root = DavXml.parse(response.body) ?: return null
        val holder = DavXml.elements(root, DavNs.Dav, "response").firstNotNullOfOrNull {
            DavXml.successfulProperty(it, DavNs.CalDav, "calendar-home-set")
        } ?: return null
        val href = DavXml.text(holder, DavNs.Dav, "href") ?: return null
        return resolveHref(principalUrl, href)
    }

    // --- step 4: the collection listing ---------------------------------------

    suspend fun listCalendars(homeUrl: String, credentials: DavCredentials): List<DiscoveredCalendar> {
        val response = propfind(homeUrl, credentials, depth = "1", body = PropfindCalendars)
        if (!response.isMultiStatus) throw calDavErrorForStatus(response.status, homeUrl)
        val root = DavXml.parse(response.body)
            ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The server's reply could not be read.")

        return DavXml.elements(root, DavNs.Dav, "response").mapNotNull { entry ->
            parseCalendarResponse(entry, homeUrl)
        }
    }

    private fun parseCalendarResponse(entry: Element, homeUrl: String): DiscoveredCalendar? {
        val href = DavXml.directText(entry, DavNs.Dav, "href") ?: return null
        val url = resolveHref(homeUrl, href)

        // Only calendar collections. The account home also lists the principal
        // itself and, on this server, address books -- which are DAV
        // collections too and would otherwise show up as empty calendars.
        val resourceType = DavXml.successfulProperty(entry, DavNs.Dav, "resourcetype") ?: return null
        if (!DavXml.hasElement(resourceType, "calendar")) return null
        if (DavXml.hasElement(resourceType, "addressbook")) return null
        // Scheduling collections are not user-facing calendars.
        if (SchedulingTypes.any { DavXml.hasElement(resourceType, it) }) return null

        val components = DavXml.successfulProperty(entry, DavNs.CalDav, "supported-calendar-component-set")
            ?.let { set ->
                DavXml.elements(set, DavNs.CalDav, "comp")
                    .mapNotNull { it.getAttribute("name")?.trim()?.uppercase()?.takeIf(String::isNotEmpty) }
                    .toSet()
            }
            .orEmpty()

        return DiscoveredCalendar(
            url = url,
            displayName = DavXml.successfulText(entry, DavNs.Dav, "displayname")
                ?: url.trimEnd('/').substringAfterLast('/'),
            color = normalizeColor(DavXml.successfulText(entry, DavNs.Apple, "calendar-color")),
            readOnly = isReadOnly(entry),
            components = components,
            ctag = DavXml.successfulText(entry, DavNs.CalendarServer, "getctag")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            syncToken = DavXml.successfulText(entry, DavNs.Dav, "sync-token")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Read-only unless the privilege set says otherwise.
     *
     * Two rules carried over from the web app. A collection advertised as
     * `subscribed` is always read-only. And a collection reporting *no*
     * privilege information at all is treated as writable, not read-only:
     * absent metadata is not a denial, and assuming otherwise marks every
     * calendar on a terse server read-only.
     */
    private fun isReadOnly(entry: Element): Boolean {
        if (DavXml.successfulText(entry, DavNs.CalendarServer, "subscribed")?.lowercase() == "true") return true
        val privileges = DavXml.successfulProperty(entry, DavNs.Dav, "current-user-privilege-set") ?: return false
        val granted = DavXml.elements(privileges, DavNs.Dav, "privilege")
            .flatMap { privilege ->
                privilege.getElementsByTagName("*").let { nodes ->
                    (0 until nodes.length).mapNotNull { index ->
                        (nodes.item(index) as? Element)?.let { it.localName ?: it.tagName.substringAfterLast(':') }
                    }
                }
            }
            .map { it.lowercase() }
            .toSet()
        if (granted.isEmpty()) return false
        return WritePrivileges.none { it in granted }
    }

    private suspend fun propfind(
        url: String,
        credentials: DavCredentials,
        depth: String,
        body: String,
    ): DavResponse = http.request(
        method = "PROPFIND",
        url = url,
        credentials = credentials,
        headers = mapOf("Depth" to depth, "Content-Type" to "application/xml; charset=utf-8"),
        body = body,
    )

    companion object {
        private val SchedulingTypes = listOf("schedule-inbox", "schedule-outbox", "inbox", "outbox")
        private val WritePrivileges =
            setOf("write", "all", "write-content", "write-properties", "bind", "unlocked")

        /** 207 or 401 both prove a DAV endpoint. See [probeWellKnown]. */
        fun isDavStatus(status: Int): Boolean = status == 207 || status == 401

        const val PropfindDisplayName =
            """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:displayname/></d:prop></d:propfind>"""

        const val PropfindCurrentUserPrincipal =
            """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>"""

        const val PropfindCalendarHomeSet =
            """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><c:calendar-home-set/></d:prop></d:propfind>"""

        const val PropfindCalendars =
            """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/" xmlns:ic="http://apple.com/ns/ical/">
  <d:prop>
    <d:resourcetype/>
    <d:displayname/>
    <d:current-user-privilege-set/>
    <d:sync-token/>
    <cs:getctag/>
    <cs:subscribed/>
    <ic:calendar-color/>
    <c:supported-calendar-component-set/>
    <c:calendar-description/>
  </d:prop>
</d:propfind>"""
    }
}

/** The app's palette uses 0xAARRGGBB packed into a Long. */
internal const val DefaultCalendarColor: Long = 0xFF5B7FB5

/**
 * Parses a CalDAV colour into the app's packed form.
 *
 * Handles `#RRGGBBAA` (Apple's form, and what this server emits: `#11a602ff`),
 * plain `#RRGGBB`, and `#RGB` shorthand. The trailing alpha is dropped rather
 * than honoured -- a calendar colour is a hue, and a server-supplied alpha of
 * `00` would otherwise render the calendar invisible.
 */
internal fun normalizeColor(raw: String?): Long {
    val value = raw?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() } ?: return DefaultCalendarColor
    val hex = when (value.length) {
        3 -> value.map { "$it$it" }.joinToString("")
        6 -> value
        8 -> value.substring(0, 6)
        else -> return DefaultCalendarColor
    }
    val rgb = hex.toLongOrNull(16) ?: return DefaultCalendarColor
    return 0xFF000000L or rgb
}

internal fun DiscoveredCalendar.toCalDavCalendar(): CalDavCalendar = CalDavCalendar(
    id = url,
    name = displayName,
    color = color,
    readOnly = readOnly,
    ctag = ctag,
    syncToken = syncToken,
)

/**
 * Resolves a raw href against a base URL.
 *
 * The href is resolved *as given*, never percent-decoded first: decoding turns
 * a resource named `ev%231.ics` into `ev#1.ics`, where everything after the
 * `#` becomes a fragment and the path silently truncates.
 */
internal fun resolveHref(baseUrl: String, href: String): String =
    runCatching { URI(baseUrl).resolve(href).toString() }.getOrElse { href }

internal fun joinUrl(baseUrl: String, path: String): String =
    runCatching { URI(baseUrl).resolve(path).toString() }.getOrElse { baseUrl.trimEnd('/') + path }

internal fun stripTrailingSlash(url: String): String =
    if (url.length > 1 && url.endsWith('/')) url.dropLast(1) else url

/** `https://dav.example.com` -> `https://caldav.example.com`, or null. */
internal fun caldavSubdomain(url: String): String? = runCatching {
    val uri = URI(url)
    val host = uri.host ?: return null
    if (host.startsWith("caldav.")) return null
    val parts = host.split('.')
    if (parts.size < 2) return null
    val bare = parts.takeLast(2).joinToString(".")
    "${uri.scheme}://caldav.$bare"
}.getOrNull()
