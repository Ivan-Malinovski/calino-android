package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.ContactAddressBook
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.w3c.dom.Element

/** One CardDAV resource exactly as received; mapping is deliberately separate. */
data class CardResource(val href: String, val etag: String?, val vcf: String)

data class CardFetchFailure(val href: String, val message: String)

data class CardFetchResult(
    val resources: List<CardResource> = emptyList(),
    val failures: List<CardFetchFailure> = emptyList(),
) {
    val partialFailure: Boolean get() = failures.isNotEmpty()
    /** A partial book must not be used as proof that missing cards were deleted. */
    val isAuthoritative: Boolean get() = !partialFailure
}

enum class CardFetchMode {
    Full,
    Skipped,
    Incremental,
    FullFallback,
}

data class IncrementalCardFetchResult(
    val fetchResult: CardFetchResult,
    val cursor: CollectionCursor,
    val mode: CardFetchMode,
    val fallbackReason: SyncCollectionFallbackReason? = null,
) {
    val resources: List<CardResource> get() = fetchResult.resources
    val usedIncremental: Boolean get() = mode == CardFetchMode.Incremental
    val fellBackToFull: Boolean get() = mode == CardFetchMode.FullFallback
}

class CardDavFetcher(
    private val http: DavHttp = DavHttp(),
    private val incrementalSync: IncrementalSync = IncrementalSync(http),
) {

    suspend fun fetch(book: DiscoveredAddressBook, credentials: DavCredentials): CardFetchResult {
        val response = http.request(
            method = "REPORT",
            url = book.url,
            credentials = credentials,
            headers = mapOf("Depth" to "1", "Content-Type" to "application/xml; charset=utf-8"),
            body = AddressBookQuery,
        )
        if (!response.isMultiStatus) throw calDavErrorForStatus(response.status, book.url)
        val root = DavXml.parse(response.body)
            ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The server's reply could not be read.")
        return parseResources(root, book)
    }

    /**
     * Applies an RFC 6578 delta to a complete address-book snapshot. CardDAV
     * reports hrefs and ETags only, so changed cards are read with GET before
     * the new token is returned to the repository for persistence.
     */
    suspend fun fetchIncremental(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        cachedResources: List<CardResource>?,
        storedCursor: CollectionCursor? = null,
    ): IncrementalCardFetchResult {
        val previous = storedCursor ?: CollectionCursor(book.ctag, book.syncToken)
        if (!previous.hasSyncToken) {
            return fullFetch(
                book = book,
                credentials = credentials,
                cursor = CollectionCursor(ctag = book.ctag),
                mode = CardFetchMode.Full,
            )
        }
        if (cachedResources == null) {
            return fullFetch(
                book = book,
                credentials = credentials,
                cursor = previous.copy(ctag = book.ctag),
                mode = CardFetchMode.FullFallback,
            )
        }
        if (storedCursor != null && previous.canSkipWith(CollectionCursor(ctag = book.ctag))) {
            return IncrementalCardFetchResult(
                fetchResult = CardFetchResult(resources = cachedResources),
                cursor = previous.copy(ctag = book.ctag),
                mode = CardFetchMode.Skipped,
            )
        }

        val report = incrementalSync.syncCollection(
            collectionUrl = book.url,
            credentials = credentials,
            cursor = previous,
        )
        if (report.tokenInvalidated || report.nextCursor?.syncToken.isNullOrBlank()) {
            return fullFetch(
                book = book,
                credentials = credentials,
                cursor = CollectionCursor(ctag = book.ctag),
                mode = CardFetchMode.FullFallback,
                fallbackReason = report.fallbackReason
                    ?: SyncCollectionFallbackReason.MalformedResponse,
            )
        }

        val merged = LinkedHashMap<String, CardResource>(cachedResources.size)
        cachedResources.forEach { merged[it.href] = it }
        try {
            val changed = report.changes
                .filterIsInstance<SyncCollectionChange.Changed>()
                .map { change -> resolveIncrementalHref(book.url, change.href) to change.etag }
                .distinctBy { it.first }
            val changedResources = fetchChangedResources(credentials, changed)
            report.changes.forEach { change ->
                val href = resolveIncrementalHref(book.url, change.href)
                when (change) {
                    is SyncCollectionChange.Removed -> merged.remove(href)
                    is SyncCollectionChange.Changed -> {
                        changedResources[href]
                            ?.let { merged[href] = it }
                            ?: merged.remove(href)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return fullFetch(
                book = book,
                credentials = credentials,
                cursor = previous.copy(ctag = book.ctag),
                mode = CardFetchMode.FullFallback,
            )
        }

        return IncrementalCardFetchResult(
            fetchResult = CardFetchResult(resources = merged.values.toList()),
            cursor = CollectionCursor(
                ctag = book.ctag,
                syncToken = requireNotNull(report.nextCursor).syncToken,
            ),
            mode = CardFetchMode.Incremental,
        )
    }

    /** Reads changed cards in parallel, while keeping the server bounded. */
    private suspend fun fetchChangedResources(
        credentials: DavCredentials,
        changed: List<Pair<String, String>>,
    ): Map<String, CardResource?> = supervisorScope {
        val limiter = Semaphore(MaxConcurrentResourceReads)
        changed.map { (href, etag) ->
            async {
                href to limiter.withPermit {
                    fetchChangedResource(credentials, href, etag)
                }
            }
        }.awaitAll().toMap()
    }

    private fun parseResources(root: Element, book: DiscoveredAddressBook): CardFetchResult {
        val resources = mutableListOf<CardResource>()
        val failures = mutableListOf<CardFetchFailure>()
        DavXml.elements(root, DavNs.Dav, "response").forEach { entry ->
            val rawHref = DavXml.text(entry, DavNs.Dav, "href")
            if (rawHref == null) {
                failures += CardFetchFailure("<unknown>", "The server returned a contact response without a resource URL.")
                return@forEach
            }
            val href = resolveDavHref(book.url, rawHref)
            val status = DavXml.text(entry, DavNs.Dav, "status")
            if (status != null && !status.contains(" 200 ") && !status.endsWith(" 200 OK")) {
                failures += CardFetchFailure(href, status)
                return@forEach
            }
            val data = DavXml.text(entry, DavNs.CardDav, "address-data")
            if (data == null) {
                failures += CardFetchFailure(href, "The server returned no address-data.")
                return@forEach
            }
            if (parseSingleVCard(data) == null) {
                failures += CardFetchFailure(
                    href,
                    "The server returned invalid or multiple vCards in one resource.",
                )
                return@forEach
            }
            resources += CardResource(
                href = href,
                etag = normalizeEtag(DavXml.text(entry, DavNs.Dav, "getetag")),
                vcf = data,
            )
        }
        return CardFetchResult(resources, failures)
    }

    private suspend fun fetchChangedResource(
        credentials: DavCredentials,
        href: String,
        reportEtag: String,
    ): CardResource? {
        val response = http.request(
            method = "GET",
            url = href,
            credentials = credentials,
            headers = mapOf("Accept" to "text/vcard"),
        )
        if (response.status == 404 || response.status == 410) return null
        if (response.status !in 200..299) {
            throw calDavErrorForStatus(response.status, href, response.body)
        }
        if (response.body.isBlank()) {
            throw CalDavException(
                CalDavErrorCode.NotCalDav,
                "The server returned an empty contact resource.",
                status = response.status,
                body = response.body,
            )
        }
        if (parseSingleVCard(response.body) == null) {
            throw CalDavException(
                CalDavErrorCode.NotCalDav,
                "The server returned invalid or multiple vCards in one resource.",
                status = response.status,
                body = response.body,
            )
        }
        return CardResource(
            href = href,
            etag = normalizeEtag(response.header("ETag")) ?: reportEtag,
            vcf = response.body,
        )
    }

    private suspend fun fullFetch(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        cursor: CollectionCursor,
        mode: CardFetchMode,
        fallbackReason: SyncCollectionFallbackReason? = null,
    ): IncrementalCardFetchResult {
        val result = fetch(book, credentials)
        val safeCursor = if (result.partialFailure) cursor.copy(syncToken = null) else cursor
        return IncrementalCardFetchResult(
            fetchResult = result,
            cursor = safeCursor,
            mode = mode,
            fallbackReason = fallbackReason,
        )
    }

    private fun resolveIncrementalHref(collectionUrl: String, rawHref: String): String {
        val resolved = resolveHref(collectionUrl, rawHref)
        val collection = runCatching { URI(collectionUrl) }.getOrNull()
            ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The address-book URL could not be read.")
        val target = runCatching { URI(resolved) }.getOrNull()
            ?: throw CalDavException(CalDavErrorCode.NotCalDav, "The server returned an invalid contact URL.")
        val collectionPort = if (collection.port != -1) collection.port else defaultPort(collection.scheme)
        val targetPort = if (target.port != -1) target.port else defaultPort(target.scheme)
        if (!collection.scheme.equals(target.scheme, ignoreCase = true) ||
            !collection.host.equals(target.host, ignoreCase = true) ||
            collectionPort != targetPort
        ) {
            throw CalDavException(
                CalDavErrorCode.NotCalDav,
                "The server returned a contact resource outside its address book.",
            )
        }
        if (!CalDavWriter.resourceIsInCollection(resolved, collectionUrl)) {
            throw CalDavException(
                CalDavErrorCode.NotCalDav,
                "The server returned a contact resource outside its address book.",
            )
        }
        return resolved
    }

    private fun defaultPort(scheme: String?): Int = when (scheme?.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }

    companion object {
        private const val MaxConcurrentResourceReads = 4
        const val AddressBookQuery = """<?xml version="1.0" encoding="UTF-8"?>
<card:addressbook-query xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
  <d:prop><d:getetag/><card:address-data/></d:prop>
  <card:filter><card:prop-filter name="FN"/></card:filter>
</card:addressbook-query>"""
    }
}

internal fun ContactAddressBook.toDiscoveredAddressBook() = DiscoveredAddressBook(
    url = url,
    displayName = name,
    description = description,
    readOnly = readOnly,
    ctag = ctag,
)
