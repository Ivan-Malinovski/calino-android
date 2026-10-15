package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CachedAddressBook
import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CardResource
import calino.malinov.ski.data.caldav.ContactWriting
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavPrecondition
import calino.malinov.ski.data.caldav.DiscoveredAddressBook
import calino.malinov.ski.data.caldav.PreparedCardWrite
import calino.malinov.ski.data.caldav.normalizeEtag
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

private const val Method = "ContactCard/set"

/**
 * Writes JMAP cards behind the vCard-shaped [ContactWriting] seam, the way
 * [JmapCalendarWriter] does for events: preparing is CardDAV's job (a patch of
 * the cached vCard), sending converts to JSContact, diffs against a fresh read
 * and sends only the changed properties. The validator is the hash of that
 * fresh read, so a stale edit is refused (412) before anything is sent.
 *
 * A server assigns the card id, so a create returns a different href than the
 * placeholder it was prepared with.
 */
class JmapContactsWriter(
    private val clients: JmapClientProvider,
    private val fetcher: JmapContactsFetcher,
    private val inner: ContactWriting,
    private val cache: CalendarCache = CalendarCache.None,
) : ContactWriting by inner {
    /** Origins whose server rejected a `uid` query filter; those scan the book instead. */
    private val uidFilterRejected = ConcurrentHashMap.newKeySet<String>()

    override suspend fun putPrepared(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        prepared: PreparedCardWrite,
    ): CardResource {
        ensureWritable(book)
        val client = clients.client(book.url, credentials)
        val bookId = bookIdOf(book, client)
        val next = convert(prepared.body)
        val card = when (val precondition = prepared.precondition) {
            is DavPrecondition.Match -> update(client, book, bookId, prepared.href, precondition.etag, next)
            else -> create(client, bookId, next)
        }
        val written = resourceOf(book, client, bookId, card)
        saveCached(book.url, written)
        return written
    }

    override suspend fun refreshResource(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        href: String,
    ): CardResource {
        val client = clients.client(book.url, credentials)
        val bookId = bookIdOf(book, client)
        val resource = fetcher.fetchResource(book, credentials, href)
            ?: placeholderUid(href)?.let { uid -> lookup(client, bookId, uid) }?.let { resourceOf(book, client, bookId, it) }
            ?: throw notFound()
        saveCached(book.url, resource)
        return resource
    }

    override suspend fun delete(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        href: String,
        etag: String?,
    ) {
        ensureWritable(book)
        val client = clients.client(book.url, credentials)
        val bookId = bookIdOf(book, client)
        val target = parseJmapContactUrl(href, book.url, client.contactsAccountId, card = true)
        if (target.addressBookId != bookId) throw outsideBook()
        val cardId = requireNotNull(target.cardId)
        val expected = normalizeEtag(etag)
            ?: cache.loadAddressBook(book.url)?.resources?.firstOrNull { it.href == href }?.etag?.let(::normalizeEtag)
            ?: throw stale()
        val previous = read(client, cardId)
        if (previous == null || !isMember(previous, bookId)) {
            deleteCached(book.url, href)
            return
        }
        val others = previous.objectOrNull("addressBookIds")?.entryList()
            ?.any { (id, member) -> id != bookId && member == true } == true
        if (others) {
            // Only this book's membership goes; the card lives on elsewhere.
            set(client, "update", cardId, JSONObject().put(membershipKey(bookId), JSONObject.NULL))
        } else {
            if (cardEtag(previous) != expected) throw stale()
            try {
                set(client, "destroy", cardId, null)
            } catch (error: JmapException) {
                if (error.type != "notFound") throw error
            }
        }
        deleteCached(book.url, href)
    }

    private suspend fun create(client: JmapClient, bookId: String, next: JSONObject): JSONObject {
        val uid = next.stringOrNull("uid")
        val known = if (uid == null) emptyList() else lookupAll(client, bookId, uid)
        val here = known.firstOrNull { isMember(it, bookId) }
        if (here != null) {
            // The same card already sits here. If it says what we are about to
            // say, this is a retry whose response was lost.
            if (contactPatch(flatCard(here), next).length() == 0) return here
            throw stale()
        }
        known.firstOrNull()?.let { existing ->
            // Another book of this account: the first half of a move.
            val patch = contactPatch(flatCard(existing), next).put(membershipKey(bookId), true)
            return updateWith(client, existing, patch)
        }
        val value = next.copy().put("addressBookIds", JSONObject().put(bookId, true))
        val created = set(client, "create", "card", value)
        val id = created.stringOrNull("id") ?: throw invalidResponse("$Method omitted the created id")
        return read(client, id) ?: throw invalidResponse("The created contact could not be read back")
    }

    private suspend fun update(
        client: JmapClient,
        book: DiscoveredAddressBook,
        bookId: String,
        href: String,
        etag: String,
        next: JSONObject,
    ): JSONObject {
        val target = parseJmapContactUrl(href, book.url, client.contactsAccountId, card = true)
        if (target.addressBookId != bookId) throw outsideBook()
        val previous = read(client, requireNotNull(target.cardId))
        if (previous == null || !isMember(previous, bookId)) throw notFound()
        if (normalizeEtag(etag) != cardEtag(previous)) throw stale()
        return updateWith(client, previous, contactPatch(flatCard(previous), next))
    }

    /** [patch] is relative to the flat card; an enveloped server holds it under `jsCard/`. */
    private suspend fun updateWith(client: JmapClient, previous: JSONObject, patch: JSONObject): JSONObject {
        val id = previous.getString("id")
        if (patch.length() == 0) return previous
        val wrapped = previous.objectOrNull("jsCard") != null
        val wire = JSONObject()
        for (key in patch.keyList()) {
            val membership = key == "addressBookIds" || key.startsWith("addressBookIds/")
            wire.put(if (wrapped && !membership) "jsCard/$key" else key, patch.opt(key))
        }
        set(client, "update", id, wire)
        return read(client, id) ?: throw invalidResponse("The updated contact could not be read back")
    }

    // --- looking cards up ------------------------------------------------------------------

    private suspend fun read(client: JmapClient, id: String): JSONObject? =
        fetcher.getCards(client, listOf(id)).firstOrNull()

    private suspend fun lookupAll(client: JmapClient, bookId: String, uid: String): List<JSONObject> {
        val origin = serverOrigin(client.apiUrl)
        if (origin !in uidFilterRejected) {
            try {
                val result = client.invokeContacts(
                    "ContactCard/query",
                    JSONObject().put("filter", JSONObject().put("uid", uid)),
                )
                val ids = result.arrayOrNull("ids")?.toList()?.filterIsInstance<String>().orEmpty()
                return fetcher.getCards(client, ids).filter { flatCard(it).stringOrNull("uid") == uid }
            } catch (error: JmapException) {
                if (error.type != "unsupportedFilter" && error.type != "invalidArguments") throw error
                uidFilterRejected += origin
            }
        }
        return fetcher.cardsInBook(client, bookId).filter { flatCard(it).stringOrNull("uid") == uid }
    }

    private suspend fun lookup(client: JmapClient, bookId: String, uid: String): JSONObject? =
        lookupAll(client, bookId, uid).firstOrNull { isMember(it, bookId) }

    /** `uid.vcf` (percent-encoded) is the placeholder a new card is prepared under. */
    private fun placeholderUid(href: String): String? {
        val last = href.substringAfterLast('/').takeIf { it.endsWith(".vcf") } ?: return null
        return runCatching { URLDecoder.decode(last.removeSuffix(".vcf").replace("+", "%2B"), "UTF-8") }.getOrNull()
    }

    // --- the set call ----------------------------------------------------------------------

    /** One `ContactCard/set` operation; returns the created object for a create, else an empty one. */
    private suspend fun set(client: JmapClient, operation: String, id: String, value: JSONObject?): JSONObject {
        val arguments = JSONObject()
        if (operation == "destroy") {
            arguments.put("destroy", jsonArray(listOf(id)))
        } else {
            arguments.put(operation, JSONObject().put(id, value))
        }
        val result = client.invokeContacts(Method, arguments)
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

    // --- small helpers ---------------------------------------------------------------------

    private fun convert(vcard: String): JSONObject = try {
        VCardToJsContact.convert(vcard)
    } catch (error: CancellationException) {
        throw error
    } catch (error: JsContactException) {
        throw CalDavException(
            CalDavErrorCode.Rejected,
            error.message ?: "That contact cannot be stored on a JMAP server.",
            error,
            status = 400,
        )
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

    private fun resourceOf(book: DiscoveredAddressBook, client: JmapClient, bookId: String, card: JSONObject) = CardResource(
        href = jmapCardUrl(book.url, client.contactsAccountId, bookId, card.getString("id")),
        etag = cardEtag(card),
        vcf = JsContactToVCard.convert(flatCard(card)),
    )

    private fun bookIdOf(book: DiscoveredAddressBook, client: JmapClient): String =
        requireNotNull(parseJmapContactUrl(book.url, book.url, client.contactsAccountId, card = false).addressBookId)

    private fun isMember(card: JSONObject, bookId: String): Boolean =
        card.objectOrNull("addressBookIds")?.boolOrNull(bookId) == true

    private fun membershipKey(bookId: String): String =
        "addressBookIds/" + bookId.replace("~", "~0").replace("/", "~1")

    private fun saveCached(bookUrl: String, resource: CardResource) {
        val entry = cache.loadAddressBook(bookUrl) ?: return
        cache.saveAddressBook(entry.copy(resources = entry.resources.filterNot { it.href == resource.href } + resource))
    }

    private fun deleteCached(bookUrl: String, href: String) {
        val entry: CachedAddressBook = cache.loadAddressBook(bookUrl) ?: return
        cache.saveAddressBook(entry.copy(resources = entry.resources.filterNot { it.href == href }))
    }

    private fun stale() = JmapException(
        CalDavErrorCode.PreconditionFailed,
        "That contact changed on the server. Refresh and try again.",
        status = 412,
        type = "stateMismatch",
    )

    private fun notFound() = JmapException(
        CalDavErrorCode.NotFound,
        "That contact is no longer on the server.",
        status = 404,
        type = "notFound",
    )

    private fun outsideBook() = JmapException(
        CalDavErrorCode.Rejected,
        "That contact does not belong to this address book.",
        status = 400,
        type = "invalidArguments",
    )
}
