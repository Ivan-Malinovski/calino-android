package calino.malinov.ski.data.caldav

import java.net.URI
import kotlinx.coroutines.CancellationException
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * The two server cursors associated with one collection.
 *
 * A ctag is only a change hint. It is safe to use it to skip a collection
 * when, and only when, a stored sync token is also present and the fresh ctag
 * is the same. A sync token is the cursor consumed by [IncrementalSync].
 *
 * The REPORT response does not carry a ctag, so a successful incremental
 * result preserves the previous ctag and replaces only [syncToken].
 */
data class CollectionCursor(
    val ctag: String? = null,
    val syncToken: String? = null,
) {

    val hasSyncToken: Boolean
        get() = !syncToken.isNullOrBlank()

    /** The conservative collection-skip rule from RFC 6578 integration. */
    fun canSkipWith(fresh: CollectionCursor): Boolean =
        IncrementalSync.shouldSkipCollection(stored = this, fresh = fresh)
}

/** Whether a sync-collection response changed or removed a resource. */
sealed interface SyncCollectionChange {
    val href: String
    val etag: String?

    /** A resource that was added or modified; sync-collection cannot tell which. */
    data class Changed(
        override val href: String,
        override val etag: String,
    ) : SyncCollectionChange

    /** A resource removed since the supplied token. */
    data class Removed(
        override val href: String,
    ) : SyncCollectionChange {
        override val etag: String? = null
    }
}

/** Why a report could not safely advance its collection cursor. */
enum class SyncCollectionFallbackReason {
    /** The server returned anything other than the required 207 multistatus. */
    ReportRejected,

    /** The HTTP request failed before a DAV response was available. */
    TransportFailure,

    /** The server returned 207, but its body was not a usable multistatus. */
    MalformedResponse,

    /** A 207 response omitted or blanked the required DAV:sync-token. */
    MissingSyncToken,
}

/**
 * The safe outcome of one sync-collection REPORT.
 *
 * When [tokenInvalidated] is true, [nextCursor] is always null and the caller
 * must discard its old cursor and perform a full collection listing. The
 * [changes] list is empty in that case: a rejected report is not authoritative
 * and must not partially mutate local state.
 */
data class SyncCollectionResult(
    val changes: List<SyncCollectionChange> = emptyList(),
    val nextCursor: CollectionCursor? = null,
    val tokenInvalidated: Boolean = false,
    val fallbackReason: SyncCollectionFallbackReason? = null,
    val httpStatus: Int? = null,
) {

    val requiresFullSync: Boolean
        get() = tokenInvalidated

    /** Compatibility with the web client's result vocabulary. */
    val newSyncToken: String?
        get() = nextCursor?.syncToken
}

/**
 * Builds and parses the RFC 6578 `sync-collection` REPORT, and performs the
 * request through the app's existing DAV transport.
 *
 * This helper deliberately does not fetch changed resources or mutate a
 * cache. Its caller owns those operations and must commit [SyncCollectionResult
 * .nextCursor] only after all changed hrefs have been stored successfully.
 */
class IncrementalSync(
    private val http: DavHttp = DavHttp(),
) {

    /**
     * Runs a level-1 sync for one collection.
     *
     * A REPORT is accepted only at HTTP 207. Status codes are intentionally
     * not enumerated: Radicale uses 403 for DAV:valid-sync-token, while other
     * servers use 400, 409, or 507. Every non-207 response invalidates the
     * cursor and asks the caller for a full listing.
     */
    suspend fun syncCollection(
        collectionUrl: String,
        credentials: DavCredentials,
        cursor: CollectionCursor? = null,
    ): SyncCollectionResult {
        val response = try {
            http.request(
                method = "REPORT",
                url = collectionUrl,
                credentials = credentials,
                headers = mapOf("Depth" to "1"),
                body = reportBody(cursor?.syncToken),
                contentType = DavHttp.XmlMediaType,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return fallback(SyncCollectionFallbackReason.TransportFailure)
        }

        if (response.status != MultiStatus) {
            return fallback(
                reason = SyncCollectionFallbackReason.ReportRejected,
                httpStatus = response.status,
            )
        }

        return parseReport(
            collectionUrl = collectionUrl,
            xml = response.body,
            previousCursor = cursor,
        ).copy(httpStatus = response.status)
    }

    /** Parses a 207 sync-collection body without performing network I/O. */
    fun parseReport(
        collectionUrl: String,
        xml: String,
        previousCursor: CollectionCursor? = null,
    ): SyncCollectionResult {
        val root = DavXml.parse(xml)
            ?: return fallback(SyncCollectionFallbackReason.MalformedResponse)
        if (localNameOrTag(root) != "multistatus") {
            return fallback(SyncCollectionFallbackReason.MalformedResponse)
        }

        val token = DavXml.text(root, DavNs.Dav, "sync-token")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return fallback(SyncCollectionFallbackReason.MissingSyncToken)

        val responses = DavXml.elements(root, DavNs.Dav, "response")
        // A response that has an href but neither a direct tombstone status nor
        // a readable ETag is malformed input, not an unchanged resource. Do
        // not drop it with mapNotNull: doing so would advance the cursor past a
        // change we never applied.
        val changes = responses.map { response ->
            parseResponse(response, collectionUrl)
                ?: return fallback(SyncCollectionFallbackReason.MalformedResponse)
        }

        return SyncCollectionResult(
            changes = changes,
            nextCursor = CollectionCursor(
                ctag = previousCursor?.ctag,
                syncToken = token,
            ),
            tokenInvalidated = false,
        )
    }

    /**
     * Parses a collection PROPFIND response into its ctag and sync token.
     *
     * A syntactically valid multistatus with neither property is represented
     * by an empty cursor. That is different from malformed XML (`null`) and
     * lets the caller decide whether a server without either cursor can still
     * be read by a full listing.
     */
    fun parseCollectionCursor(xml: String): CollectionCursor? {
        val root = DavXml.parse(xml) ?: return null
        if (localNameOrTag(root) != "multistatus") return null
        return CollectionCursor(
            ctag = DavXml.text(root, DavNs.CalendarServer, "getctag")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            syncToken = DavXml.text(root, DavNs.Dav, "sync-token")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
        )
    }

    companion object {
        private const val MultiStatus = 207

        /** The exact level-1 report body used for an initial or incremental sync. */
        fun reportBody(syncToken: String?): String {
            val token = syncToken
                ?.takeIf { it.isNotBlank() }
                ?.let { "<d:sync-token>${escapeXml(it)}</d:sync-token>" }
                ?: "<d:sync-token/>"
            return """<?xml version="1.0" encoding="UTF-8"?>
<d:sync-collection xmlns:d="DAV:">
  $token
  <d:sync-level>1</d:sync-level>
  <d:prop>
    <d:getetag/>
  </d:prop>
</d:sync-collection>"""
        }

        /**
         * Returns true only when a stored token exists and both ctags are
         * present and equal. Two null ctags are not evidence that nothing
         * changed, so they never authorize a skip.
         */
        fun shouldSkipCollection(
            stored: CollectionCursor?,
            fresh: CollectionCursor,
        ): Boolean = stored?.hasSyncToken == true &&
            stored.ctag != null &&
            fresh.ctag != null &&
            stored.ctag == fresh.ctag

        private fun parseResponse(
            response: Element,
            collectionUrl: String,
        ): SyncCollectionChange? {
            // Href is a protocol child of response. Reading it as a direct
            // child also prevents an unrelated nested property href from
            // being attached to this change.
            val rawHref = directChildText(response, "href") ?: return null
            val href = resolveHrefWithoutDecoding(collectionUrl, rawHref)

            // RFC 6578 tombstones are the one special response shape whose
            // status is directly under <response>. A propstat/status 404 is a
            // property-level failure and is deliberately not a tombstone.
            val directStatus = directChildText(response, "status")
            if (parseHttpStatusCode(directStatus) == 404 || parseHttpStatusCode(directStatus) == 410) {
                return SyncCollectionChange.Removed(href)
            }

            // getetag normally sits in propstat/prop. DavXml performs the
            // namespace-aware lookup first and then the local-name fallback,
            // which covers both prefixed and Radicale's default-DAV replies.
            val etag = DavXml.text(response, DavNs.Dav, "getetag")
                ?.let(::normalizeEtag)
                ?: return null
            return SyncCollectionChange.Changed(href, etag)
        }

        private fun fallback(
            reason: SyncCollectionFallbackReason,
            httpStatus: Int? = null,
        ) = SyncCollectionResult(
            changes = emptyList(),
            nextCursor = null,
            tokenInvalidated = true,
            fallbackReason = reason,
            httpStatus = httpStatus,
        )

        private fun directChildText(scope: Element, localName: String): String? =
            directChild(scope, localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

        private fun directChild(scope: Element, localName: String): Element? {
            val children = scope.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child.nodeType != Node.ELEMENT_NODE) continue
                val element = child as Element
                if (localNameOrTag(element) == localName) return element
            }
            return null
        }

        /** Accepts HTTP/1.1 and HTTP/2 status lines, but not arbitrary text. */
        private fun parseHttpStatusCode(status: String?): Int? {
            if (status.isNullOrBlank()) return null
            val fields = status.trim().split(Whitespace, limit = 3)
            if (fields.size < 2 || !fields[0].startsWith("HTTP/", ignoreCase = true)) return null
            return fields[1].toIntOrNull()
        }

        private fun resolveHrefWithoutDecoding(base: String, href: String): String =
            runCatching { URI(base).resolve(href).toString() }.getOrDefault(href)

        private fun localNameOrTag(element: Element): String =
            element.localName ?: element.tagName.substringAfterLast(':')

        /** XML escaping without APIs unavailable on the app's minimum SDK. */
        private fun escapeXml(value: String): String = buildString(value.length) {
            value.forEach { character ->
                when {
                    character == '&' -> append("&amp;")
                    character == '<' -> append("&lt;")
                    character == '>' -> append("&gt;")
                    character == '"' -> append("&quot;")
                    character == '\'' -> append("&apos;")
                    character == '\t' || character == '\n' || character == '\r' || character.code >= 0x20 ->
                        append(character)
                    // XML 1.0 does not permit other control characters in a
                    // token. Dropping them is safer than emitting malformed XML.
                }
            }
        }

        private val Whitespace = Regex("\\s+")
    }
}
