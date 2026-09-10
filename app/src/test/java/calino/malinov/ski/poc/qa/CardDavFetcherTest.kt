package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.CardDavFetcher
import calino.malinov.ski.poc.data.caldav.CardFetchMode
import calino.malinov.ski.poc.data.caldav.CardResource
import calino.malinov.ski.poc.data.caldav.CollectionCursor
import calino.malinov.ski.poc.data.caldav.DavCredentials
import calino.malinov.ski.poc.data.caldav.DavHttp
import calino.malinov.ski.poc.data.caldav.DiscoveredAddressBook
import calino.malinov.ski.poc.data.caldav.normalizeEtag
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CardDavFetcherTest {
    private lateinit var server: MockWebServer
    private val credentials = DavCredentials("test-user", "test-pass")

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun book() = DiscoveredAddressBook(server.url("/book/").toString(), "Neighbors")
    private fun response(body: String) = MockResponse().setResponseCode(207).setBody(body)
        .setHeader("Content-Type", "application/xml; charset=utf-8")

    @Test
    fun reportReturnsRawCards_andNormalizesAlreadyQuotedEtags() = runBlocking {
        server.enqueue(response(
            """<multistatus xmlns="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
                <response><href>/book/ada.vcf</href><propstat><prop><getetag>&quot;abc&quot;</getetag><card:address-data>BEGIN:VCARD
VERSION:3.0
FN:Ada
END:VCARD</card:address-data></prop><status>HTTP/1.1 200 OK</status></propstat></response>
            </multistatus>""",
        ))

        val result = CardDavFetcher(DavHttp()).fetch(book(), credentials)
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("REPORT", request.method)
        assertEquals("1", request.getHeader("Depth"))
        assertTrue(body.contains("addressbook-query"))
        assertTrue(body.contains("address-data"))
        assertEquals("abc", result.resources.single().etag)
        assertEquals("BEGIN:VCARD\nVERSION:3.0\nFN:Ada\nEND:VCARD", result.resources.single().vcf)
        assertTrue(result.isAuthoritative)
    }

    @Test
    fun partialFailure_isNotAuthoritative() = runBlocking {
        server.enqueue(response(
            """<multistatus xmlns="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
                <response><href>/book/ok.vcf</href><propstat><prop><getetag>&quot;ok&quot;</getetag><card:address-data>BEGIN:VCARD
FN:Okay
END:VCARD</card:address-data></prop><status>HTTP/1.1 200 OK</status></propstat></response>
                <response><href>/book/broken.vcf</href><status>HTTP/1.1 404 Not Found</status></response>
            </multistatus>""",
        ))

        val result = CardDavFetcher(DavHttp()).fetch(book(), credentials)

        assertEquals(1, result.resources.size)
        assertEquals(1, result.failures.size)
        assertFalse(result.isAuthoritative)
    }

    @Test
    fun reportTreatsMultipleVCardsInOneResourceAsAPartialFailure() = runBlocking {
        server.enqueue(response(
            """<multistatus xmlns="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
                <response><href>/book/combined.vcf</href><propstat><prop><getetag>&quot;combined&quot;</getetag><card:address-data>BEGIN:VCARD
VERSION:3.0
UID:first
FN:First
END:VCARD
BEGIN:VCARD
VERSION:3.0
UID:second
FN:Second
END:VCARD</card:address-data></prop><status>HTTP/1.1 200 OK</status></propstat></response>
            </multistatus>""",
        ))

        val result = CardDavFetcher(DavHttp()).fetch(book(), credentials)

        assertTrue(result.resources.isEmpty())
        assertEquals(1, result.failures.size)
        assertFalse(result.isAuthoritative)
    }

    @Test
    fun incrementalReport_mergesChangedAndRemovedCards_andAdvancesCursor() = runBlocking {
        val collection = server.url("/book/").toString()
        val changed = "${collection}changed.vcf"
        val removed = "${collection}removed.vcf"
        server.enqueue(response(
            """<multistatus xmlns="DAV:">
                <response><href>$changed</href><propstat><prop><getetag>&quot;report&quot;</getetag></prop><status>HTTP/1.1 200 OK</status></propstat></response>
                <response><href>$removed</href><status>HTTP/1.1 404 Not Found</status></response>
                <sync-token>next-token</sync-token>
            </multistatus>""",
        ))
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("ETag", "\"new\"")
                .setBody("BEGIN:VCARD\nVERSION:3.0\nUID:changed\nFN:Changed\nEND:VCARD"),
        )

        val result = CardDavFetcher(DavHttp()).fetchIncremental(
            book = DiscoveredAddressBook(
                url = collection,
                displayName = "Book",
                ctag = "fresh-ctag",
                syncToken = "fresh-token",
            ),
            credentials = credentials,
            cachedResources = listOf(
                CardResource(removed, "old-removed", "BEGIN:VCARD\nFN:Removed\nEND:VCARD"),
                CardResource("${collection}unchanged.vcf", "unchanged", "BEGIN:VCARD\nFN:Same\nEND:VCARD"),
            ),
            storedCursor = CollectionCursor("old-ctag", "old-token"),
        )

        assertEquals(CardFetchMode.Incremental, result.mode)
        assertEquals("next-token", result.cursor.syncToken)
        assertEquals(listOf("unchanged.vcf", "changed.vcf"), result.resources.map { it.href.substringAfterLast('/') })
        assertEquals("new", result.resources.single { it.href == changed }.etag)
        assertEquals(2, server.requestCount)
        assertTrue(server.takeRequest().body.readUtf8().contains("old-token"))
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun normalizeEtag_doesNotDoubleQuote() {
        assertEquals("abc", normalizeEtag("\"abc\""))
        assertEquals("abc", normalizeEtag("W/\"abc\""))
        assertEquals(null, normalizeEtag("  "))
    }
}
