package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CardFetchFailure
import calino.malinov.ski.data.caldav.CardFetchMode
import calino.malinov.ski.data.caldav.CardFetchResult
import calino.malinov.ski.data.caldav.CardResource
import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CollectionCursor
import calino.malinov.ski.data.caldav.ContactFetching
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DiscoveredAddressBook
import calino.malinov.ski.data.caldav.IncrementalCardFetchResult
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

private const val MaxChangePages = 1000
private const val MaxQueryAttempts = 3

/**
 * The validator for a JMAP card: the SHA-256 of its canonical JSON without
 * `addressBookIds`, so adding or removing a book membership does not look like an edit.
 */
internal fun cardEtag(card: JSONObject): String =
    MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson(card.copy().also { it.remove("addressBookIds") }).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/**
 * RFC 9610 cards are flat. A server that wraps the card in a `jsCard` envelope
 * (older drafts) is read by lifting the envelope's properties beside `id` and
 * `addressBookIds`.
 */
internal fun flatCard(raw: JSONObject): JSONObject {
    val wrapped = raw.objectOrNull("jsCard") ?: return raw
    return wrapped.copy().also { flat ->
        raw.keyList().filter { it != "jsCard" }.forEach { flat.put(it, raw.opt(it)) }
    }
}

/** Reads cards from a JMAP address book as the vCard the rest of the app caches. */
class JmapContactsFetcher(private val clients: JmapClientProvider) : ContactFetching {
    /** Origins whose server answered `inAddressBook` with `unsupportedFilter`. */
    private val inBooksOnly = ConcurrentHashMap.newKeySet<String>()

    override suspend fun fetch(book: DiscoveredAddressBook, credentials: DavCredentials): CardFetchResult {
        val client = clients.client(book.url, credentials)
        val bookId = bookIdOf(book, client)
        return convert(book, client, bookId, getCards(client, queryIds(client, bookId)))
    }

    override suspend fun fetchIncremental(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        cachedResources: List<CardResource>?,
        storedCursor: CollectionCursor?,
    ): IncrementalCardFetchResult {
        // AddressBook state moves with metadata and is never consulted: an equal
        // one must not hide card changes. The cursor is the ContactCard state.
        val previous = storedCursor ?: CollectionCursor(syncToken = book.syncToken)
        if (!previous.hasSyncToken) return full(book, credentials, CardFetchMode.Full)
        if (cachedResources == null) return full(book, credentials, CardFetchMode.FullFallback)
        return try {
            applyChanges(book, credentials, cachedResources, requireNotNull(previous.syncToken))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            full(book, credentials, CardFetchMode.FullFallback)
        }
    }

    /** One card as a resource, or null when it is gone or not in this book. */
    suspend fun fetchResource(book: DiscoveredAddressBook, credentials: DavCredentials, href: String): CardResource? {
        val client = clients.client(book.url, credentials)
        val bookId = bookIdOf(book, client)
        val target = parseJmapContactUrl(href, book.url, client.contactsAccountId, card = true)
        if (target.addressBookId != bookId) return null
        val card = getCards(client, listOf(requireNotNull(target.cardId))).firstOrNull() ?: return null
        return resourceOf(book, client, bookId, card)
    }

    /** Cards in [bookId], unfiltered; used to find a card by uid when the filter is refused. */
    internal suspend fun cardsInBook(client: JmapClient, bookId: String): List<JSONObject> =
        getCards(client, queryIds(client, bookId)).filter { isMember(it, bookId) }

    private fun bookIdOf(book: DiscoveredAddressBook, client: JmapClient): String =
        requireNotNull(parseJmapContactUrl(book.url, book.url, client.contactsAccountId, card = false).addressBookId)

    private fun isMember(card: JSONObject, bookId: String): Boolean =
        card.objectOrNull("addressBookIds")?.boolOrNull(bookId) == true

    private fun resourceOf(book: DiscoveredAddressBook, client: JmapClient, bookId: String, raw: JSONObject): CardResource? {
        val id = raw.stringOrNull("id") ?: return null
        if (!isMember(raw, bookId)) return null
        return CardResource(
            href = jmapCardUrl(book.url, client.contactsAccountId, bookId, id),
            etag = cardEtag(raw),
            vcf = JsContactToVCard.convert(flatCard(raw)),
        )
    }

    private fun convert(book: DiscoveredAddressBook, client: JmapClient, bookId: String, cards: List<JSONObject>): CardFetchResult {
        val resources = mutableListOf<CardResource>()
        val failures = mutableListOf<CardFetchFailure>()
        for (card in cards) {
            if (!isMember(card, bookId)) continue
            try {
                resourceOf(book, client, bookId, card)?.let(resources::add)
            } catch (error: JsContactException) {
                val id = card.stringOrNull("id") ?: continue
                failures += CardFetchFailure(
                    jmapCardUrl(book.url, client.contactsAccountId, bookId, id),
                    error.message ?: "The contact could not be read.",
                )
            }
        }
        return CardFetchResult(resources, failures)
    }

    /** The ContactCard state, captured before a listing so later changes are never missed. */
    private suspend fun currentState(client: JmapClient): String =
        client.invokeContacts("ContactCard/get", JSONObject().put("ids", JSONArray()))
            .stringOrNull("state") ?: throw invalidResponse("ContactCard/get omitted state")

    private suspend fun full(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        mode: CardFetchMode,
    ): IncrementalCardFetchResult {
        val client = clients.client(book.url, credentials)
        val state = currentState(client)
        return IncrementalCardFetchResult(
            fetchResult = fetch(book, credentials),
            cursor = CollectionCursor(syncToken = state),
            mode = mode,
        )
    }

    private suspend fun applyChanges(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        cached: List<CardResource>,
        sinceState: String,
    ): IncrementalCardFetchResult {
        val client = clients.client(book.url, credentials)
        val bookId = bookIdOf(book, client)
        val changed = LinkedHashSet<String>()
        val destroyed = LinkedHashSet<String>()
        var since = sinceState
        var latest: String
        var pages = 0
        while (true) {
            if (++pages > MaxChangePages) throw invalidResponse("JMAP change list did not end")
            val result = client.invokeContacts("ContactCard/changes", JSONObject().put("sinceState", since))
            for (key in listOf("created", "updated")) {
                result.arrayOrNull(key)?.toList()?.filterIsInstance<String>()?.let(changed::addAll)
            }
            result.arrayOrNull("destroyed")?.toList()?.filterIsInstance<String>()?.let(destroyed::addAll)
            latest = result.stringOrNull("newState") ?: throw invalidResponse("ContactCard/changes omitted newState")
            if (result.boolOrNull("hasMoreChanges") != true) break
            if (latest == since) throw invalidResponse("ContactCard/changes made no progress")
            since = latest
        }
        changed.removeAll(destroyed)

        val merged = LinkedHashMap<String, CardResource>(cached.size)
        cached.forEach { merged[it.href] = it }
        fun hrefOf(id: String) = jmapCardUrl(book.url, client.contactsAccountId, bookId, id)
        destroyed.forEach { merged.remove(hrefOf(it)) }

        val present = getCards(client, changed.toList()).associateBy { it.stringOrNull("id") }
        val failures = mutableListOf<CardFetchFailure>()
        for (id in changed) {
            val href = hrefOf(id)
            val card = present[id]
            if (card == null || !isMember(card, bookId)) {
                merged.remove(href)
                continue
            }
            try {
                val resource = resourceOf(book, client, bookId, card)
                if (resource != null) merged[href] = resource else merged.remove(href)
            } catch (error: JsContactException) {
                merged.remove(href)
                failures += CardFetchFailure(href, error.message ?: "The contact could not be read.")
            }
        }
        return IncrementalCardFetchResult(
            fetchResult = CardFetchResult(merged.values.toList(), failures),
            cursor = CollectionCursor(syncToken = latest),
            mode = CardFetchMode.Incremental,
        )
    }

    /** `ContactCard/get` for [ids]; the client splits it to the server's limits. */
    internal suspend fun getCards(client: JmapClient, ids: List<String>): List<JSONObject> {
        if (ids.isEmpty()) return emptyList()
        val result = client.invokeContacts("ContactCard/get", JSONObject().put("ids", jsonArray(ids)))
        return result.arrayOrNull("list")?.toList()?.filterIsInstance<JSONObject>()
            ?: throw invalidResponse("ContactCard/get omitted list")
    }

    private suspend fun queryIds(client: JmapClient, bookId: String): List<String> {
        var lastError: JmapException? = null
        repeat(MaxQueryAttempts) {
            try {
                return queryOnce(client, bookId)
            } catch (error: JmapException) {
                if (error.type != "stateMismatch") throw error
                lastError = error
            }
        }
        throw lastError ?: invalidResponse("ContactCard/query kept changing")
    }

    private suspend fun queryOnce(client: JmapClient, bookId: String): List<String> {
        val origin = serverOrigin(client.apiUrl)
        val ids = ArrayList<String>()
        val seen = HashSet<String>()
        var queryState: String? = null
        while (true) {
            val filter = JSONObject()
            if (origin in inBooksOnly) filter.put("inAddressBooks", jsonArray(listOf(bookId))) else filter.put("inAddressBook", bookId)
            val arguments = JSONObject().put("filter", filter).put("position", ids.size).put("calculateTotal", true)
            val result = try {
                client.invokeContacts("ContactCard/query", arguments)
            } catch (error: JmapException) {
                if (error.type == "unsupportedFilter" && inBooksOnly.add(origin)) {
                    ids.clear()
                    seen.clear()
                    queryState = null
                    continue
                }
                throw error
            }
            val state = result.stringOrNull("queryState")
            if (queryState != null && state != queryState) {
                throw JmapException(
                    CalDavErrorCode.PreconditionFailed,
                    "The address book changed while it was being listed.",
                    status = 412,
                    type = "stateMismatch",
                )
            }
            queryState = state
            if ((result.longOrNull("position") ?: 0L) != ids.size.toLong()) {
                throw invalidResponse("ContactCard/query returned an unexpected page position")
            }
            val page = result.arrayOrNull("ids")?.toList()?.filterIsInstance<String>()
                ?: throw invalidResponse("ContactCard/query omitted ids")
            if (page.isEmpty()) break
            for (id in page) {
                if (!seen.add(id)) throw invalidResponse("ContactCard/query repeated a card")
                ids += id
            }
            val total = result.longOrNull("total")
            if (total != null && ids.size >= total) break
        }
        return ids
    }
}
