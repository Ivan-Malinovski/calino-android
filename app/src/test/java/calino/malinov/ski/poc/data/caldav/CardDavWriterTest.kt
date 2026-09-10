package calino.malinov.ski.poc.data.caldav

import calino.malinov.ski.poc.data.model.Contact
import calino.malinov.ski.poc.data.model.ContactEmail
import calino.malinov.ski.poc.data.model.ContactType
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CardDavWriterTest {
    private lateinit var server: MockWebServer
    private lateinit var cache: MemoryCache
    private val credentials = DavCredentials("test-user", "test-pass")

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        cache = MemoryCache()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun book() = DiscoveredAddressBook(
        url = server.url("/books/contacts/").toString(),
        displayName = "Contacts",
    )

    private fun contact(
        book: DiscoveredAddressBook,
        href: String? = null,
        etag: String? = null,
    ) = Contact(
        id = "ada:1",
        addressBookId = book.url,
        givenName = "Ada",
        familyName = "Lovelace",
        displayName = "Ada Lovelace",
        emails = listOf(ContactEmail("ada@example.com", ContactType.Home, isPrimary = true)),
        uid = "ada:1",
        href = href,
        etag = etag,
    )

    @Test
    fun resourceFilename_escapesEveryPathUnsafeByte() {
        assertEquals(
            "uid~3Awith~2Fslash~25~7E~C3~A9.vcf",
            CardDavWriter.resourceFilename("uid:with/slash%~é"),
        )
        assertFalse(CardDavWriter.resourceFilename("uid:with/slash%~é").any { it == ':' || it == '/' || it == '%' })
    }

    @Test
    fun createUsesIfNoneMatchAndCachesTheAcceptedCard() = runBlocking {
        val book = book()
        cache.saveAddressBook(CachedAddressBook(book.url, Instant.EPOCH, emptyList()))
        server.enqueue(MockResponse().setResponseCode(201).setHeader("ETag", "\"created\""))

        val result = CardDavWriter(cache = cache, now = { Instant.parse("2026-09-10T12:00:00Z") })
            .createContact(book, credentials, contact(book), uid = "ada:1")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/books/contacts/ada~3A1.vcf", request.requestUrl!!.encodedPath)
        assertEquals("*", request.getHeader("If-None-Match"))
        assertNull(request.getHeader("If-Match"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("text/vcard"))
        assertTrue(request.body.readUtf8().contains("UID:ada:1"))
        assertEquals("created", result.etag)
        assertEquals(result, cache.loadAddressBook(book.url)!!.resources.single())
    }

    @Test
    fun matchingCachedEtagPatchesTheRawCardAndUsesIfMatch() = runBlocking {
        val book = book()
        val href = server.url("/books/contacts/ada.vcf").toString()
        val raw = """BEGIN:VCARD
VERSION:3.0
UID:ada:1
N:Lovelace;Ada;;;
FN:Old Name
X-SERVER-ONLY;X-KEEP=yes:preserve me
END:VCARD
"""
        cache.saveAddressBook(CachedAddressBook(book.url, Instant.EPOCH, listOf(CardResource(href, "old", raw))))
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "W/\"new\""))

        val updated = contact(book, href = href, etag = "\"old\"").copy(displayName = "Ada Updated")
        val result = CardDavWriter(cache = cache, now = { Instant.parse("2026-09-10T12:00:00Z") })
            .updateContact(book, credentials, updated)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("PUT", request.method)
        assertEquals("\"old\"", request.getHeader("If-Match"))
        assertNull(request.getHeader("If-None-Match"))
        assertTrue(body.contains("FN:Ada Updated"))
        assertFalse(body.contains("FN:Old Name"))
        assertTrue(body.contains("X-SERVER-ONLY;X-KEEP=yes:preserve me"))
        assertEquals("new", result.etag)
        assertEquals(result.vcf, cache.loadAddressBook(book.url)!!.resources.single().vcf)
    }

    @Test
    fun staleCachedEtagRebuildsInsteadOfPatchingUnknownProperties() = runBlocking {
        val book = book()
        val href = server.url("/books/contacts/ada.vcf").toString()
        cache.saveAddressBook(
            CachedAddressBook(
                book.url,
                Instant.EPOCH,
                listOf(CardResource(href, "server-version", """BEGIN:VCARD
VERSION:3.0
UID:ada:1
FN:Server Name
X-SERVER-ONLY:do-not-copy
END:VCARD
""")),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "client-version"))

        val updated = contact(book, href = href, etag = "client-version").copy(displayName = "Client Name")
        val result = CardDavWriter(cache = cache).updateContact(book, credentials, updated)

        val request = server.takeRequest()
        assertEquals("\"client-version\"", request.getHeader("If-Match"))
        assertTrue(request.body.readUtf8().contains("FN:Client Name"))
        assertFalse(result.vcf.contains("X-SERVER-ONLY:do-not-copy"))
    }

    @Test
    fun missingPutEtagFallsBackToDepthZeroPropfindAndParsesXml() = runBlocking {
        val book = book()
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """<d:multistatus xmlns:d="DAV:">
                    <d:response><d:href>/books/contacts/ada~3A1.vcf</d:href>
                      <d:propstat><d:prop><d:getetag>&quot;from-propfind&quot;</d:getetag></d:prop>
                      <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                    </d:response>
                </d:multistatus>""".trimIndent(),
            ),
        )

        val result = CardDavWriter(cache = cache).createContact(book, credentials, contact(book), uid = "ada:1")
        val put = server.takeRequest()
        val propfind = server.takeRequest()

        assertNull(put.getHeader("ETag"))
        assertEquals("PROPFIND", propfind.method)
        assertEquals("0", propfind.getHeader("Depth"))
        assertTrue(propfind.body.readUtf8().contains("getetag"))
        assertEquals("from-propfind", result.etag)
    }

    @Test
    fun deleteUsesIfMatchAndRemovesOnlyTheDeletedCachedCard() = runBlocking {
        val book = book()
        val href = server.url("/books/contacts/ada.vcf").toString()
        val other = server.url("/books/contacts/other.vcf").toString()
        cache.saveAddressBook(
            CachedAddressBook(
                book.url,
                Instant.EPOCH,
                listOf(
                    CardResource(href, "ada-etag", "BEGIN:VCARD\nFN:Ada\nEND:VCARD\n"),
                    CardResource(other, "other-etag", "BEGIN:VCARD\nFN:Other\nEND:VCARD\n"),
                ),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        CardDavWriter(cache = cache).deleteContact(book, credentials, contact(book, href, "ada-etag"))

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("\"ada-etag\"", request.getHeader("If-Match"))
        val remaining = cache.loadAddressBook(book.url)!!.resources
        assertEquals(1, remaining.size)
        assertEquals(other, remaining.single().href)
    }

    @Test
    fun preconditionFailureCarriesTheServerStatusAndBody() = runBlocking {
        val book = book()
        val href = server.url("/books/contacts/ada.vcf").toString()
        server.enqueue(MockResponse().setResponseCode(412).setBody("changed elsewhere"))

        val failure = runCatching {
            CardDavWriter().updateContact(book, credentials, contact(book, href, "old"))
        }.exceptionOrNull()

        assertTrue(failure is CalDavException)
        assertEquals(CalDavErrorCode.PreconditionFailed, (failure as CalDavException).code)
        assertEquals(412, failure.status)
        assertTrue(failure.body!!.contains("changed elsewhere"))
    }

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
