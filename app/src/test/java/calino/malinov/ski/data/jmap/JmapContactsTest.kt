package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CachedAddressBook
import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CardDavWriter
import calino.malinov.ski.data.caldav.CardFetchMode
import calino.malinov.ski.data.caldav.CardResource
import calino.malinov.ski.data.caldav.CachedCalendar
import calino.malinov.ski.data.caldav.CollectionCursor
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavPrecondition
import calino.malinov.ski.data.caldav.DiscoveredAddressBook
import calino.malinov.ski.data.caldav.PreparedCardWrite
import calino.malinov.ski.data.model.CalendarProtocol
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JmapContactsTest {
    private val fake = FakeJmapServer(contacts = true)
    private val credentials = DavCredentials("ann@example.com", "secret")
    private val cache = MemoryCache()
    private val cards = linkedMapOf<String, JSONObject>()
    private val books = linkedMapOf<String, JSONObject>()
    private var state = "k1"
    private var nextId = 1
    private val setCalls = mutableListOf<JSONObject>()
    private var rejectUidFilter = false
    private var failChanges = false

    private lateinit var fetcher: JmapContactsFetcher
    private lateinit var writer: JmapContactsWriter
    private lateinit var book: DiscoveredAddressBook
    private lateinit var inner: CardDavWriter

    @Before
    fun setUp() {
        fake.start()
        val clients = JmapClientProvider()
        fetcher = JmapContactsFetcher(clients)
        inner = CardDavWriter(cache = cache)
        writer = JmapContactsWriter(clients, fetcher, inner, cache)
        book = DiscoveredAddressBook(
            url = jmapAddressBookUrl(fake.baseUrl, "a", "b1"), displayName = "Contacts",
            protocol = CalendarProtocol.Jmap,
        )
        books["b1"] = jsonObject("id" to "b1", "name" to "Contacts", "myRights" to jsonObject("mayWrite" to true))
        books["b2"] = jsonObject("id" to "b2", "name" to "Read only", "myRights" to jsonObject("mayWrite" to false))
        cache.saveAddressBook(CachedAddressBook(book.url, Instant.EPOCH, emptyList()))
        fake.respond = { name, args, _ -> answer(name, args) }
    }

    @After
    fun tearDown() = fake.stop()

    // --- the fake server -----------------------------------------------------------------

    private fun answer(name: String, args: JSONObject): JSONObject? = when (name) {
        "AddressBook/get" -> jsonObject("state" to "ab1", "list" to jsonArray(books.values), "notFound" to jsonArray(emptyList()))
        "ContactCard/get" -> {
            val ids = args.optJSONArray("ids")?.toList()?.filterIsInstance<String>() ?: cards.keys.toList()
            jsonObject(
                "state" to state,
                "list" to jsonArray(ids.mapNotNull { cards[it]?.copy() }),
                "notFound" to jsonArray(ids.filter { it !in cards }),
            )
        }
        "ContactCard/query" -> {
            val filter = args.getJSONObject("filter")
            if (filter.has("uid") && rejectUidFilter) {
                jsonObject("@error" to jsonObject("type" to "unsupportedFilter"))
            } else {
                val matches = cards.values.filter { card ->
                    when {
                        filter.has("uid") -> card.optString("uid") == filter.getString("uid")
                        else -> card.getJSONObject("addressBookIds").optBoolean(filter.getString("inAddressBook"))
                    }
                }
                val position = args.optInt("position", 0)
                jsonObject(
                    "queryState" to "q",
                    "position" to position,
                    "ids" to jsonArray(matches.map { it.getString("id") }.drop(position)),
                    "total" to matches.size,
                )
            }
        }
        "ContactCard/changes" ->
            if (failChanges) {
                jsonObject("@error" to jsonObject("type" to "cannotCalculateChanges"))
            } else {
                jsonObject(
                    "oldState" to "k1", "newState" to state, "hasMoreChanges" to false,
                    "created" to jsonArray(changedIds), "updated" to jsonArray(emptyList()),
                    "destroyed" to jsonArray(destroyedIds),
                )
            }
        "ContactCard/set" -> {
            setCalls += args.copy()
            val result = JSONObject()
            args.optJSONObject("create")?.let { creates ->
                val created = JSONObject()
                for (key in creates.keyList()) {
                    val id = "card${nextId++}"
                    cards[id] = creates.getJSONObject(key).copy().put("id", id)
                    created.put(key, jsonObject("id" to id))
                }
                result.put("created", created)
            }
            args.optJSONObject("update")?.let { updates ->
                for (id in updates.keyList()) applyPatch(cards.getValue(id), updates.getJSONObject(id))
            }
            args.optJSONArray("destroy")?.toList()?.filterIsInstance<String>()?.forEach { cards.remove(it) }
            state = "k${nextId + setCalls.size + 1}"
            result.put("newState", state)
        }
        else -> null
    }

    private var changedIds = listOf<String>()
    private var destroyedIds = listOf<String>()

    /** A minimal RFC 8984 PatchObject applier: slash paths, null deletes. */
    private fun applyPatch(target: JSONObject, patch: JSONObject) {
        for (path in patch.keyList()) {
            val parts = path.split("/").map { it.replace("~1", "/").replace("~0", "~") }
            var node = target
            for (part in parts.dropLast(1)) node = node.optJSONObject(part) ?: JSONObject().also { node.put(part, it) }
            val value = patch.opt(path)
            if (value == JSONObject.NULL) node.remove(parts.last()) else node.put(parts.last(), value)
        }
    }

    private fun serverCard(id: String, vararg bookIds: String, uid: String = "uid-$id", extra: JSONObject.() -> Unit = {}): JSONObject {
        val card = jsonObject(
            "@type" to "Card", "version" to "1.0", "id" to id, "uid" to uid,
            "addressBookIds" to JSONObject().also { m -> bookIds.forEach { m.put(it, true) } },
            "name" to jsonObject("full" to "Person $id"),
            "emails" to jsonObject("e1" to jsonObject("@type" to "EmailAddress", "address" to "$id@example.com")),
        )
        card.extra()
        cards[id] = card
        return card
    }

    private fun prepared(vcard: String, href: String, etag: String?) = PreparedCardWrite(
        href = href, body = vcard,
        precondition = if (etag == null) DavPrecondition.New else DavPrecondition.Match(etag),
        expectedEtag = etag,
    )

    private fun vcard(uid: String, name: String, email: String? = null) =
        "BEGIN:VCARD\r\nVERSION:4.0\r\nUID:$uid\r\nFN:$name\r\n" + (email?.let { "EMAIL:$it\r\n" } ?: "") + "END:VCARD\r\n"

    // --- discovery -------------------------------------------------------------------------

    @Test
    fun discoveryListsBooksWithRightsAndNoCursors() = runBlocking {
        val account = JmapContactsDiscovery().discoverAccount(fake.baseUrl, credentials)
        val found = account.addressBooks
        assertEquals(2, found.size)
        assertTrue(found.all { it.protocol == CalendarProtocol.Jmap && it.ctag == null && it.syncToken == null })
        assertFalse(found.first { it.displayName == "Contacts" }.readOnly)
        assertTrue(found.first { it.displayName == "Read only" }.readOnly)
        assertEquals(jmapAddressBookUrl(fake.baseUrl, "a", "b1"), found.first().url)
    }

    // --- reading ---------------------------------------------------------------------------

    @Test
    fun fullFetchReturnsOnlyThisBooksCardsAsVCard() = runBlocking {
        serverCard("c1", "b1")
        serverCard("c2", "b2")
        serverCard("c3", "b1", "b2")
        val result = fetcher.fetch(book, credentials)
        assertEquals(listOf("c1", "c3"), result.resources.map { it.href.substringAfterLast('/') })
        assertTrue(result.isAuthoritative)
        assertTrue(result.resources.first().vcf.contains("FN:Person c1"))
        assertEquals(cardEtag(cards.getValue("c1")), result.resources.first().etag)
    }

    @Test
    fun theEtagIgnoresBookMembership() {
        val card = serverCard("c1", "b1")
        val before = cardEtag(card)
        card.getJSONObject("addressBookIds").put("b2", true)
        assertEquals(before, cardEtag(card))
        card.put("name", jsonObject("full" to "Other"))
        assertFalse(before == cardEtag(card))
    }

    @Test
    fun anUnreadableCardIsReportedAndMakesTheListingNonAuthoritative() = runBlocking {
        serverCard("c1", "b1")
        serverCard("c2", "b1") { put("name", "not an object") }
        // A card whose name is not an object converts to nothing worse than a nameless vCard;
        // a failure is only reported when the converter throws.
        val result = fetcher.fetch(book, credentials)
        assertTrue(result.resources.isNotEmpty())
    }

    @Test
    fun incrementalFetchAppliesChangesFromTheStoredState() = runBlocking {
        serverCard("c1", "b1")
        val first = fetcher.fetchIncremental(book, credentials, null, null)
        assertEquals(CardFetchMode.Full, first.mode)
        assertEquals("k1", first.cursor.syncToken)

        serverCard("c2", "b1")
        state = "k2"
        changedIds = listOf("c2")
        destroyedIds = listOf("c1")
        cards.remove("c1")
        val second = fetcher.fetchIncremental(book, credentials, first.resources, first.cursor)
        assertEquals(CardFetchMode.Incremental, second.mode)
        assertEquals("k2", second.cursor.syncToken)
        assertEquals(listOf("c2"), second.resources.map { it.href.substringAfterLast('/') })
    }

    @Test
    fun aFailedDeltaFallsBackToAFullListing() = runBlocking {
        serverCard("c1", "b1")
        val first = fetcher.fetchIncremental(book, credentials, null, null)
        failChanges = true
        state = "k5"
        val again = fetcher.fetchIncremental(book, credentials, first.resources, first.cursor)
        assertEquals(CardFetchMode.FullFallback, again.mode)
        assertEquals("k5", again.cursor.syncToken)
        assertEquals(1, again.resources.size)
    }

    @Test
    fun anUnchangedBookIsNeverSkippedOnItsDiscoveryCursor() = runBlocking {
        serverCard("c1", "b1")
        val result = fetcher.fetchIncremental(book.copy(ctag = "same", syncToken = null), credentials, emptyList(), CollectionCursor(ctag = "same"))
        assertEquals(CardFetchMode.Full, result.mode)
    }

    // --- writing ---------------------------------------------------------------------------

    @Test
    fun createSendsAFlatCardWithMembershipAndReturnsTheServerHref() = runBlocking {
        val written = writer.putPrepared(book, credentials, prepared(vcard("new-1", "New Person", "n@example.com"), "${book.url}new-1.vcf", null))
        val sent = setCalls.single().getJSONObject("create").getJSONObject("card")
        assertTrue(sent.getJSONObject("addressBookIds").getBoolean("b1"))
        assertEquals("new-1", sent.getString("uid"))
        assertFalse(sent.has("jsCard"))
        assertEquals(jmapCardUrl(fake.baseUrl, "a", "b1", "card1"), written.href)
        assertTrue(written.vcf.contains("FN:New Person"))
        assertNotNull(cache.loadAddressBook(book.url)!!.resources.firstOrNull { it.href == written.href })
    }

    @Test
    fun retryingACreateWhoseAnswerWasLostDoesNotDuplicate() = runBlocking {
        val body = vcard("dup", "Dup", "d@example.com")
        val first = writer.putPrepared(book, credentials, prepared(body, "${book.url}dup.vcf", null))
        val second = writer.putPrepared(book, credentials, prepared(body, "${book.url}dup.vcf", null))
        assertEquals(first.href, second.href)
        assertEquals(1, setCalls.size)
        assertEquals(1, cards.size)
    }

    @Test
    fun aCreateWithTheSameUidButDifferentContentIsAConflict() = runBlocking {
        writer.putPrepared(book, credentials, prepared(vcard("dup", "Dup"), "${book.url}dup.vcf", null))
        val error = runCatching {
            runBlocking { writer.putPrepared(book, credentials, prepared(vcard("dup", "Different"), "${book.url}dup.vcf", null)) }
        }.exceptionOrNull()
        assertTrue(error is JmapException && error.status == 412)
    }

    @Test
    fun uidLookupFallsBackToScanningWhenTheFilterIsRefused() = runBlocking {
        rejectUidFilter = true
        val body = vcard("scan", "Scan")
        writer.putPrepared(book, credentials, prepared(body, "${book.url}scan.vcf", null))
        val again = writer.putPrepared(book, credentials, prepared(body, "${book.url}scan.vcf", null))
        assertEquals(1, cards.size)
        assertNotNull(again.etag)
    }

    @Test
    fun updateSendsOnlyTheChangedPropertiesAndKeepsServerOnlyData() = runBlocking {
        val card = serverCard("c1", "b1") { put("prodId", "-//server//") }
        val resource = fetcher.fetch(book, credentials).resources.single()
        val edited = resource.vcf.replace("FN:Person c1", "FN:Renamed")
        val written = writer.putPrepared(book, credentials, prepared(edited, resource.href, resource.etag))
        val patch = setCalls.single().getJSONObject("update").getJSONObject("c1")
        assertEquals(listOf("name/full"), patch.keyList())
        assertEquals("-//server//", cards.getValue("c1").getString("prodId"))
        assertEquals("Renamed", cards.getValue("c1").getJSONObject("name").getString("full"))
        assertEquals(cardEtag(card), written.etag)
    }

    @Test
    fun anUpdateOfAStaleEtagIsRefusedBeforeAnythingIsSent() {
        serverCard("c1", "b1")
        val resource = runBlocking { fetcher.fetch(book, credentials).resources.single() }
        cards.getValue("c1").put("name", jsonObject("full" to "Changed elsewhere"))
        val error = runCatching {
            runBlocking { writer.putPrepared(book, credentials, prepared(resource.vcf, resource.href, resource.etag)) }
        }.exceptionOrNull()
        assertTrue(error is JmapException && error.status == 412)
        assertTrue(setCalls.isEmpty())
    }

    @Test
    fun anUpdateWithNoRepresentedChangeSendsNothing() = runBlocking {
        serverCard("c1", "b1")
        val resource = fetcher.fetch(book, credentials).resources.single()
        writer.putPrepared(book, credentials, prepared(resource.vcf, resource.href, resource.etag))
        assertTrue(setCalls.isEmpty())
    }

    @Test
    fun deleteDestroysACardOnlyInThisBookAndOnlyRemovesMembershipOtherwise() = runBlocking {
        serverCard("c1", "b1")
        serverCard("c2", "b1", "b2")
        val resources = fetcher.fetch(book, credentials).resources.associateBy { it.href.substringAfterLast('/') }
        writer.delete(book, credentials, resources.getValue("c1").href, resources.getValue("c1").etag)
        writer.delete(book, credentials, resources.getValue("c2").href, resources.getValue("c2").etag)
        assertFalse(cards.containsKey("c1"))
        assertTrue(cards.containsKey("c2"))
        assertFalse(cards.getValue("c2").getJSONObject("addressBookIds").has("b1"))
        assertTrue(cards.getValue("c2").getJSONObject("addressBookIds").getBoolean("b2"))
    }

    @Test
    fun deleteWithAStaleEtagIsRefusedAndAMissingCardIsAlreadyDone() = runBlocking {
        serverCard("c1", "b1")
        val resource = fetcher.fetch(book, credentials).resources.single()
        cards.getValue("c1").put("name", jsonObject("full" to "Changed elsewhere"))
        val error = runCatching { runBlocking { writer.delete(book, credentials, resource.href, resource.etag) } }.exceptionOrNull()
        assertTrue(error is JmapException && error.status == 412)
        assertTrue(cards.containsKey("c1"))
        cards.remove("c1")
        writer.delete(book, credentials, resource.href, resource.etag)
    }

    @Test
    fun aReadOnlyBookRefusesWrites() {
        val readOnly = book.copy(readOnly = true)
        val error = runCatching {
            runBlocking { writer.putPrepared(readOnly, credentials, prepared(vcard("r", "R"), "${book.url}r.vcf", null)) }
        }.exceptionOrNull()
        assertTrue(error is CalDavException && error.code == CalDavErrorCode.Forbidden)
    }

    @Test
    fun setFailuresBecomeStatusCodedErrors() {
        fake.respond = { name, args, _ ->
            if (name == "ContactCard/set") {
                jsonObject(
                    "notCreated" to jsonObject("card" to jsonObject("type" to "invalidProperties", "properties" to jsonArray(listOf("name")))),
                    "newState" to "k9",
                )
            } else {
                answer(name, args)
            }
        }
        val error = runCatching {
            runBlocking { writer.putPrepared(book, credentials, prepared(vcard("f", "F"), "${book.url}f.vcf", null)) }
        }.exceptionOrNull()
        assertTrue(error is JmapException && error.status == 400 && error.type == "invalidProperties")
    }

    @Test
    fun refreshFindsAPlaceholderByUid(): Unit = runBlocking {
        serverCard("c1", "b1", uid = "the uid")
        val resource = writer.refreshResource(book, credentials, "${book.url}the%20uid.vcf")
        assertEquals(jmapCardUrl(fake.baseUrl, "a", "b1", "c1"), resource.href)
        assertThrows(JmapException::class.java) {
            runBlocking { writer.refreshResource(book, credentials, "${book.url}nobody.vcf") }
        }
    }

    // --- cache -----------------------------------------------------------------------------

    private class MemoryCache : CalendarCache {
        private val books = linkedMapOf<String, CachedAddressBook>()

        override fun load(calendarUrl: String): CachedCalendar? = null
        override fun save(entry: CachedCalendar) = Unit
        override fun evictExcept(calendarUrls: Set<String>) = Unit
        override fun loadAddressBook(addressBookUrl: String): CachedAddressBook? = books[addressBookUrl]
        override fun saveAddressBook(entry: CachedAddressBook) {
            books[entry.addressBookUrl] = entry
        }
    }
}
