package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.NewContact
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** A serialized vCard write that can be sent now or replayed later. */
data class PreparedCardWrite(
    val href: String,
    val body: String,
    val precondition: DavPrecondition,
    val expectedEtag: String?,
)

/**
 * Conditional CardDAV writes for one address-book resource at a time.
 *
 * The writer deliberately owns only the protocol boundary. Repository state
 * reconciliation is a later layer: this class returns the exact href, ETag,
 * and vCard bytes that were accepted by the server and updates the raw cache
 * when that address book was already cached.
 */
class CardDavWriter(
    private val http: DavHttp = DavHttp(),
    private val cache: CalendarCache = CalendarCache.None,
    private val vCardWriter: VCardWriter = VCardWriter(),
    private val now: () -> Instant = { Instant.now() },
) {

    /** Creates or updates based on whether the contact has a server href. */
    suspend fun putContact(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: Contact,
    ): CardResource {
        val uid = contact.uid?.trim()?.takeIf { it.isNotEmpty() }
            ?: contact.id.trim()
        require(uid.isNotEmpty()) { "A contact UID is required." }

        val existingHref = contact.href?.takeIf(String::isNotBlank)?.let {
            checkedExistingHref(book.url, it)
        }
        val isUpdate = existingHref != null || contact.etag != null
        return putPrepared(book, credentials, prepareContact(book, contact))
    }

    /** Creates a new card with an explicit UID when a caller has one. */
    suspend fun createContact(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: NewContact,
        uid: String = UUID.randomUUID().toString(),
    ): CardResource {
        val normalizedUid = uid.trim()
        require(normalizedUid.isNotEmpty()) { "A contact UID is required." }
        return put(
            book = book,
            credentials = credentials,
            contact = contact,
            uid = normalizedUid,
            href = null,
            expectedEtag = null,
            forceCreate = true,
        )
    }

    /** Creates a new card from the full model, ignoring any existing href. */
    suspend fun createContact(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: Contact,
        uid: String = contact.uid?.takeIf { it.isNotBlank() } ?: contact.id,
    ): CardResource {
        val uid = uid.trim()
        require(uid.isNotEmpty()) { "A contact UID is required." }
        return put(
            book = book,
            credentials = credentials,
            contact = contact,
            uid = uid,
            href = null,
            expectedEtag = null,
            forceCreate = true,
        )
    }

    /** Updates an existing card and requires its href to remain in [book]. */
    suspend fun updateContact(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: Contact,
    ): CardResource {
        val href = contact.href?.takeIf(String::isNotBlank)
            ?: throw invalidWrite("An existing contact needs its server href.")
        val uid = contact.uid?.trim()?.takeIf { it.isNotEmpty() }
            ?: contact.id.trim()
        require(uid.isNotEmpty()) { "A contact UID is required." }
        return putPrepared(book, credentials, prepareContact(book, contact))
    }

    /** Alias convenient for callers that use the transport verb as the API. */
    suspend fun put(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: Contact,
    ): CardResource = putContact(book, credentials, contact)

    /** New-contact overload always uses create semantics. */
    suspend fun put(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: NewContact,
        uid: String = UUID.randomUUID().toString(),
    ): CardResource = createContact(book, credentials, contact, uid)

    /** Prepares a full-model contact using conditional create/update semantics. */
    fun prepareContact(
        book: DiscoveredAddressBook,
        contact: Contact,
    ): PreparedCardWrite {
        ensureWritable(book)
        val uid = contact.uid?.trim()?.takeIf { it.isNotEmpty() } ?: contact.id.trim()
        require(uid.isNotEmpty()) { "A contact UID is required." }
        val existingHref = contact.href?.takeIf(String::isNotBlank)?.let {
            checkedExistingHref(book.url, it)
        }
        val isUpdate = existingHref != null || contact.etag != null
        val resourceUrl = existingHref ?: resolveHref(book.url, resourceFilename(uid))
        val cached = if (isUpdate) cachedResource(book.url, resourceUrl) else null
        val expectedEtag = normalizeEtag(contact.etag) ?: cached?.etag?.let(::normalizeEtag)
        if (isUpdate && expectedEtag == null) throw missingEtag(resourceUrl)
        val stamp = now()
        val body = cached
            ?.takeIf { expectedEtag != null && sameEtag(it.etag, expectedEtag) }
            ?.let { vCardWriter.patch(it.vcf, contact, uid, stamp) }
            ?: vCardWriter.write(contact, uid = uid, now = stamp)
        return PreparedCardWrite(
            href = resourceUrl,
            body = body,
            precondition = if (isUpdate) {
                DavPrecondition.Match(expectedEtag!!)
            } else {
                DavPrecondition.New
            },
            expectedEtag = expectedEtag,
        )
    }

    /** Sends a previously prepared vCard and updates the raw address-book cache. */
    suspend fun putPrepared(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        prepared: PreparedCardWrite,
    ): CardResource = putBody(
        book = book,
        credentials = credentials,
        resourceUrl = prepared.href,
        body = prepared.body,
        precondition = prepared.precondition,
    )

    /** Re-reads both vCard bytes and ETag before a stale conditional retry. */
    suspend fun refreshResource(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        href: String,
    ): CardResource {
        val resourceUrl = checkedExistingHref(book.url, href)
        val response = http.request("GET", resourceUrl, credentials)
        requireSuccess(response)
        val resource = CardResource(
            href = resourceUrl,
            etag = responseEtag(response, resourceUrl, credentials),
            vcf = response.body,
        )
        saveCached(book.url, resource)
        return resource
    }

    suspend fun currentEtag(url: String, credentials: DavCredentials): String? = fetchEtag(url, credentials)

    /** Deletes the complete vCard resource with an If-Match precondition. */
    suspend fun deleteContact(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: Contact,
    ) {
        val href = contact.href?.takeIf(String::isNotBlank)
            ?: throw invalidWrite("An existing contact needs its server href.")
        deleteResource(
            book = book,
            credentials = credentials,
            href = checkedExistingHref(book.url, href),
            etag = contact.etag,
        )
    }

    suspend fun delete(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: Contact,
    ) = deleteContact(book, credentials, contact)

    /**
     * Deletes a resource by href. If [etag] is absent, the matching cached
     * resource can provide it; without either source, deletion is refused.
     */
    suspend fun delete(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        href: String,
        etag: String? = null,
    ) {
        deleteResource(
            book = book,
            credentials = credentials,
            href = checkedExistingHref(book.url, href),
            etag = etag,
        )
    }

    private suspend fun put(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: Contact,
        uid: String,
        href: String?,
        expectedEtag: String?,
        forceCreate: Boolean,
    ): CardResource {
        ensureWritable(book)
        val resourceUrl = href ?: resolveHref(book.url, resourceFilename(uid))
        val cached = if (forceCreate) null else cachedResource(book.url, resourceUrl)
        val currentEtag = normalizeEtag(expectedEtag) ?: cached?.etag?.let(::normalizeEtag)
        if (!forceCreate && currentEtag == null) throw missingEtag(resourceUrl)

        val stamp = now()
        val body = cached
            ?.takeIf { currentEtag != null && sameEtag(it.etag, currentEtag) }
            ?.let { vCardWriter.patch(it.vcf, contact, uid, stamp) }
            ?: vCardWriter.write(contact, uid = uid, now = stamp)

        return putBody(
            book = book,
            credentials = credentials,
            resourceUrl = resourceUrl,
            body = body,
            precondition = if (forceCreate) {
                DavPrecondition.New
            } else {
                DavPrecondition.Match(currentEtag!!)
            },
        )
    }

    private suspend fun put(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        contact: NewContact,
        uid: String,
        href: String?,
        expectedEtag: String?,
        forceCreate: Boolean,
    ): CardResource {
        ensureWritable(book)
        require(uid.isNotBlank()) { "A contact UID is required." }
        val resourceUrl = href ?: resolveHref(book.url, resourceFilename(uid))
        check(forceCreate && expectedEtag == null) { "A new contact cannot carry an update precondition." }
        val stamp = now()
        return putBody(
            book = book,
            credentials = credentials,
            resourceUrl = resourceUrl,
            body = vCardWriter.write(contact, uid = uid, now = stamp),
            precondition = DavPrecondition.New,
        )
    }

    private suspend fun putBody(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        resourceUrl: String,
        body: String,
        precondition: DavPrecondition,
    ): CardResource {
        val response = http.put(
            url = resourceUrl,
            credentials = credentials,
            body = body,
            contentType = DavHttp.VCardMediaType,
            precondition = precondition,
        )
        requireSuccess(response)
        // Do not reuse the precondition if the server omitted a new validator
        // and the Depth: 0 recovery also failed. That would pair new bytes
        // with an old ETag and make a later patch unsafe.
        val etag = responseEtag(response, resourceUrl, credentials)
        val result = CardResource(resourceUrl, etag, body)
        saveCached(book.url, result)
        return result
    }

    private suspend fun deleteResource(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        href: String,
        etag: String?,
    ) {
        ensureWritable(book)
        val cached = cachedResource(book.url, href)
        val currentEtag = normalizeEtag(etag) ?: cached?.etag?.let(::normalizeEtag)
            ?: throw missingEtag(href)
        val response = http.delete(
            url = href,
            credentials = credentials,
            precondition = DavPrecondition.Match(currentEtag),
        )
        if (response.status != 404 && response.status != 410) requireSuccess(response)
        deleteCached(book.url, href)
    }

    private suspend fun responseEtag(
        response: DavResponse,
        resourceUrl: String,
        credentials: DavCredentials,
    ): String? {
        normalizeEtag(response.header("ETag"))?.let { return it }
        return try {
            fetchEtag(resourceUrl, credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun fetchEtag(url: String, credentials: DavCredentials): String? {
        val response = http.request(
            method = "PROPFIND",
            url = url,
            credentials = credentials,
            headers = mapOf(
                "Depth" to "0",
                "Content-Type" to "application/xml; charset=utf-8",
            ),
            body = PropfindEtag,
        )
        requireSuccess(response)
        val root = DavXml.parse(response.body) ?: return null
        val entries = DavXml.elements(root, DavNs.Dav, "response")
        val scopes = if (entries.isEmpty()) listOf(root) else entries
        return scopes.asSequence()
            .mapNotNull { DavXml.text(it, DavNs.Dav, "getetag") }
            .mapNotNull(::normalizeEtag)
            .firstOrNull()
    }

    private fun cachedResource(bookUrl: String, href: String): CardResource? =
        cache.loadAddressBook(bookUrl)?.resources?.firstOrNull { resource ->
            resource.href == href || resolveHref(bookUrl, resource.href) == href
        }

    private fun saveCached(bookUrl: String, resource: CardResource) {
        val entry = cache.loadAddressBook(bookUrl) ?: return
        val without = entry.resources.filterNot {
            it.href == resource.href || resolveHref(bookUrl, it.href) == resource.href
        }
        cache.saveAddressBook(entry.copy(resources = without + resource))
    }

    private fun deleteCached(bookUrl: String, href: String) {
        val entry = cache.loadAddressBook(bookUrl) ?: return
        cache.saveAddressBook(
            entry.copy(resources = entry.resources.filterNot {
                it.href == href || resolveHref(bookUrl, it.href) == href
            }),
        )
    }

    private fun checkedExistingHref(collectionUrl: String, href: String): String {
        val resolved = resolveHref(collectionUrl, href)
        if (!resourceIsInCollection(resolved, collectionUrl)) {
            throw invalidWrite("That contact does not belong to this address book.")
        }
        return resolved
    }

    private fun ensureWritable(book: DiscoveredAddressBook) {
        if (book.readOnly) {
            throw CalDavException(
                CalDavErrorCode.Forbidden,
                "The server marked this address book as read-only.",
                status = 403,
            )
        }
    }

    private fun requireSuccess(response: DavResponse) {
        if (response.status !in 200..299) {
            throw calDavErrorForStatus(response.status, response.url, response.body)
        }
    }

    private fun missingEtag(url: String) = CalDavException(
        CalDavErrorCode.PreconditionFailed,
        "That contact has no current server version. Refresh and try again.",
        status = 412,
    )

    private fun invalidWrite(message: String) = CalDavException(
        CalDavErrorCode.NotCalDav,
        message,
    )

    private fun sameEtag(left: String?, right: String?): Boolean =
        normalizeEtag(left) != null && normalizeEtag(left) == normalizeEtag(right)

    companion object {
        private const val PropfindEtag = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:getetag/></d:prop></d:propfind>"""

        /** A WebDAV-safe, deterministic resource name for a vCard UID. */
        fun resourceFilename(uid: String): String = buildString {
            require(uid.isNotBlank()) { "A contact UID is required." }
            uid.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
                val value = byte.toInt() and 0xFF
                if (value in 'a'.code..'z'.code ||
                    value in 'A'.code..'Z'.code ||
                    value in '0'.code..'9'.code ||
                    value == '-'.code || value == '_'.code || value == '.'.code
                ) {
                    append(value.toChar())
                } else {
                    append('~')
                    append(value.toString(16).uppercase().padStart(2, '0'))
                }
            }
            append(".vcf")
        }

        /** Alias naming the policy after the resource type. */
        fun cardResourceFilename(uid: String): String = resourceFilename(uid)

        fun resolveHref(base: String, href: String): String = runCatching {
            val collection = if (base.endsWith('/')) base else "$base/"
            URI(collection).resolve(href).toString()
        }.getOrDefault(href)

        fun resourceIsInCollection(resource: String, collection: String): Boolean = runCatching {
            val resourceUri = URI(resource)
            val collectionUri = URI(collection)
            if (resourceUri.scheme != collectionUri.scheme ||
                resourceUri.authority != collectionUri.authority
            ) return@runCatching false
            val collectionPath = collectionUri.path.orEmpty().trimEnd('/') + "/"
            val resourcePath = resourceUri.path.orEmpty()
            resourcePath.startsWith(collectionPath) && resourcePath != collectionPath
        }.getOrDefault(false)
    }
}
