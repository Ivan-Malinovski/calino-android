package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.settings.SettingsEvent
import java.time.Instant

/** Where the settings calendar lives, once discovery or creation has found it. */
data class SettingsCollection(
    val url: String,
    /** The single settings resource inside it. */
    val resourceUrl: String,
)

/** One read of the remote settings resource. */
data class RemoteSettings(
    val ics: String,
    val etag: String?,
) {
    val stamp: Instant? get() = SettingsEvent.stamp(ics)
    val payload: String? get() = SettingsEvent.payload(ics)
}

/**
 * The remote half of settings sync, as the syncer needs it: find or make the
 * collection, read the resource, write it back under a precondition. An
 * interface so the conflict rules can be tested without a server.
 */
interface SettingsRemote {
    suspend fun ensure(homeUrl: String, credentials: DavCredentials): SettingsCollection?
    suspend fun read(collection: SettingsCollection, credentials: DavCredentials): RemoteSettings?
    suspend fun write(
        collection: SettingsCollection,
        credentials: DavCredentials,
        ics: String,
        etag: String?,
    ): SettingsCalendarClient.WriteOutcome
}

/**
 * The Calino Settings collection over plain WebDAV.
 *
 * The web app stores its settings in a hidden calendar rather than a property or
 * a loose file, because CalDAV collections reject non-iCalendar resources
 * outright. This follows the same shape: a `calino-settings` collection holding
 * one `calino-settings.ics`, marked with `X-CALINO-SETTINGS-CALENDAR: 1` so a
 * second device can find it without guessing at names.
 *
 * Everything is best-effort by design: settings sync is a convenience, so a
 * server that refuses MKCOL or PROPPATCH leaves the device's own preferences
 * untouched instead of failing a calendar sync.
 */
class SettingsCalendarClient(private val http: DavHttp) : SettingsRemote {

    /** The settings collection under [homeUrl], or null when this server has none. */
    suspend fun find(homeUrl: String, credentials: DavCredentials): SettingsCollection? {
        val response = http.request(
            method = "PROPFIND",
            url = homeUrl,
            credentials = credentials,
            headers = mapOf("Depth" to "1", "Content-Type" to "application/xml; charset=utf-8"),
            body = PropfindCollections,
        )
        if (!response.isMultiStatus) return null
        val root = DavXml.parse(response.body) ?: return null
        val entry = DavXml.elements(root, DavNs.Dav, "response").firstOrNull { candidate ->
            val href = DavXml.directText(candidate, DavNs.Dav, "href") ?: return@firstOrNull false
            val url = resolveDavHref(homeUrl, href)
            isSettingsCollection(candidate, url)
        } ?: return null
        val href = DavXml.directText(entry, DavNs.Dav, "href") ?: return null
        return collectionOf(resolveDavHref(homeUrl, href))
    }

    /**
     * Creates the settings collection, or returns the existing one.
     *
     * A server that already holds the collection answers MKCOL with 405, which
     * is a success for this purpose: the point is that the collection is there
     * afterwards, not who created it.
     */
    override suspend fun ensure(homeUrl: String, credentials: DavCredentials): SettingsCollection? {
        find(homeUrl, credentials)?.let { return it }
        val url = homeUrl.trimEnd('/') + "/" + SettingsEvent.CollectionName + "/"
        val created = http.request(
            method = "MKCOL",
            url = url,
            credentials = credentials,
            headers = mapOf("Content-Type" to "application/xml; charset=utf-8"),
            body = MkColBody,
        )
        if (created.status !in setOf(201, 200, 405, 301, 302)) return null
        // Best-effort decoration: the name is what a person sees in other
        // clients, the marker is what Calino finds it by. A server that refuses
        // both still has a usable collection, so neither is fatal.
        http.request(
            method = "PROPPATCH",
            url = url,
            credentials = credentials,
            headers = mapOf("Content-Type" to "application/xml; charset=utf-8"),
            body = markCollectionBody(),
        )
        return find(homeUrl, credentials)
            ?: SettingsCollection(url = url, resourceUrl = url.trimEnd('/') + "/" + SettingsEvent.FileName)
    }

    /** The remote settings resource, or null when it has never been written. */
    override suspend fun read(collection: SettingsCollection, credentials: DavCredentials): RemoteSettings? {
        val response = http.request(
            method = "GET",
            url = collection.resourceUrl,
            credentials = credentials,
            headers = mapOf("Accept" to "text/calendar"),
        )
        if (response.status == 404) return null
        if (response.status !in 200..299) return null
        return RemoteSettings(response.body, response.header("ETag"))
    }

    /**
     * Writes the resource, conditional on the ETag it was read at.
     *
     * [etag] null asserts the resource does not exist yet, which is the correct
     * precondition for a first push. A 412 means another device wrote in
     * between; the caller re-reads and decides, rather than overwriting.
     */
    override suspend fun write(
        collection: SettingsCollection,
        credentials: DavCredentials,
        ics: String,
        etag: String?,
    ): WriteOutcome {
        val precondition = if (etag != null) DavPrecondition.Match(etag) else DavPrecondition.New
        val response = http.put(
            url = collection.resourceUrl,
            credentials = credentials,
            body = ics,
            contentType = DavHttp.CalendarMediaType,
            precondition = precondition,
        )
        return when {
            response.status == 412 -> WriteOutcome.Conflict
            response.status in 200..299 -> WriteOutcome.Written(response.header("ETag") ?: etag)
            else -> WriteOutcome.Failed(response.status)
        }
    }

    sealed interface WriteOutcome {
        data class Written(val etag: String?) : WriteOutcome
        data object Conflict : WriteOutcome
        data class Failed(val status: Int) : WriteOutcome
    }

    private fun isSettingsCollection(entry: org.w3c.dom.Element, url: String): Boolean {
        // Only a *successful* propstat proves the marker exists. Radicale answers
        // a PROPFIND for a property it does not have with an empty element in a
        // 404 propstat, and matching that would make every collection -- the
        // calendar home first of all -- look like the settings calendar.
        val marker = DavXml.successfulProperty(entry, SettingsMarkerNs, SettingsEvent.MarkerPropertyLocalName) != null
        if (marker) return true
        val displayName = DavXml.successfulText(entry, DavNs.Dav, "displayname")
        if (displayName == SettingsEvent.CollectionDisplayName) return true
        return url.trimEnd('/').endsWith("/" + SettingsEvent.CollectionName)
    }

    private fun collectionOf(url: String) = SettingsCollection(
        url = url.trimEnd('/') + "/",
        resourceUrl = url.trimEnd('/') + "/" + SettingsEvent.FileName,
    )

    companion object {
        /** The namespace the marker property is written in. */
        const val SettingsMarkerNs = "urn:calino:settings"

        private const val PropfindCollections = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav" xmlns:x="urn:calino:settings">
  <d:prop>
    <d:resourcetype/>
    <d:displayname/>
    <x:${SettingsEvent.MarkerPropertyLocalName}/>
  </d:prop>
</d:propfind>"""

        private const val MkColBody = """<?xml version="1.0" encoding="utf-8"?>
<mkcol xmlns="DAV:">
  <set>
    <prop>
      <resourcetype><collection/><calendar xmlns="urn:ietf:params:xml:ns:caldav"/></resourcetype>
    </prop>
  </set>
</mkcol>"""

        private fun markCollectionBody(): String = """<?xml version="1.0" encoding="utf-8"?>
<propertyupdate xmlns="DAV:" xmlns:x="urn:calino:settings">
  <set>
    <prop>
      <displayname>${SettingsEvent.CollectionDisplayName}</displayname>
      <x:${SettingsEvent.MarkerPropertyLocalName}>1</x:${SettingsEvent.MarkerPropertyLocalName}>
    </prop>
  </set>
</propertyupdate>"""
    }
}
