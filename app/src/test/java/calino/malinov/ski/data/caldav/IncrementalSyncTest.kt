package calino.malinov.ski.data.caldav

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IncrementalSyncTest {

    private lateinit var server: MockWebServer
    private val credentials = DavCredentials("test-user", "test-pass")

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `initial report has an empty token and the required level one etag property`() {
        val body = IncrementalSync.reportBody(null)

        assertTrue(body.contains("<d:sync-token/>") )
        assertTrue(body.contains("<d:sync-level>1</d:sync-level>"))
        assertTrue(body.contains("<d:prop>"))
        assertTrue(body.contains("<d:getetag/>") )
        assertFalse(body.contains("calendar-data"))
    }

    @Test
    fun `report escapes a token without emitting malformed xml`() {
        val body = IncrementalSync.reportBody("token & <next> \"quoted\" 'apostrophe'\u0001")

        assertTrue(
            body.contains(
                "<d:sync-token>token &amp; &lt;next&gt; &quot;quoted&quot; &apos;apostrophe&apos;</d:sync-token>",
            ),
        )
        assertFalse(body.contains('\u0001'))
    }

    @Test
    fun `prefixed and unprefixed dav responses parse changed resources and tombstones`() {
        val collection = server.url("/cal/").toString()
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>changed%231.ics</href>
                <propstat>
                  <prop><getetag>&quot;etag-1&quot;</getetag></prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
              <response>
                <href>/cal/deleted.ics</href>
                <status>HTTP/1.1 404 Not Found</status>
              </response>
              <sync-token>https://dav.example.test/sync/2</sync-token>
            </multistatus>""".trimIndent()

        val result = IncrementalSync().parseReport(
            collectionUrl = collection,
            xml = xml,
            previousCursor = CollectionCursor(ctag = "ctag-1", syncToken = "old-token"),
        )

        assertFalse(result.tokenInvalidated)
        assertEquals("https://dav.example.test/sync/2", result.newSyncToken)
        assertEquals(CollectionCursor("ctag-1", "https://dav.example.test/sync/2"), result.nextCursor)
        assertEquals(
            listOf(
                SyncCollectionChange.Changed("${collection}changed%231.ics", "etag-1"),
                SyncCollectionChange.Removed("${collection}deleted.ics"),
            ),
            result.changes,
        )
    }

    @Test
    fun `direct 404 and 410 responses are tombstones but propstat status is not`() {
        val collection = server.url("/cal/").toString()
        val xml = """<d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>property-failed.ics</d:href>
                <d:propstat>
                  <d:prop><d:getetag>W/&quot;still-present&quot;</d:getetag></d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>resource-deleted.ics</d:href>
                <d:status>HTTP/2 404 Not Found</d:status>
              </d:response>
              <d:response>
                <d:href>resource-gone.ics</d:href>
                <d:status>HTTP/1.1 410 Gone</d:status>
              </d:response>
              <d:sync-token>next</d:sync-token>
            </d:multistatus>""".trimIndent()

        val result = IncrementalSync().parseReport(collection, xml)

        assertTrue(result.requiresFullSync)
        assertEquals(SyncCollectionFallbackReason.MalformedResponse, result.fallbackReason)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `collection cursor parsing is namespace tolerant and decodes xml entities`() {
        val xml = """<?xml version="1.0"?>
            <x:multistatus xmlns:x="DAV:" xmlns:cs="http://calendarserver.org/ns/">
              <x:response>
                <x:href>/cal/</x:href>
                <x:propstat>
                  <x:prop>
                    <cs:getctag>ctag-&amp;-2</cs:getctag>
                    <x:sync-token>token-&lt;2&gt;</x:sync-token>
                  </x:prop>
                  <x:status>HTTP/1.1 200 OK</x:status>
                </x:propstat>
              </x:response>
            </x:multistatus>""".trimIndent()

        assertEquals(
            CollectionCursor(ctag = "ctag-&-2", syncToken = "token-<2>"),
            IncrementalSync().parseCollectionCursor(xml),
        )
    }

    @Test
    fun `skip requires a nonblank stored token and matching nonnull ctags`() {
        val stored = CollectionCursor(ctag = "same", syncToken = "token")

        assertTrue(stored.canSkipWith(CollectionCursor(ctag = "same", syncToken = "fresh")))
        assertFalse(stored.canSkipWith(CollectionCursor(ctag = "different", syncToken = "fresh")))
        assertFalse(CollectionCursor(ctag = "same", syncToken = null).canSkipWith(stored))
        assertFalse(CollectionCursor(ctag = null, syncToken = "token").canSkipWith(CollectionCursor()))
        assertFalse(CollectionCursor(ctag = null, syncToken = "token").canSkipWith(CollectionCursor(ctag = null)))
    }

    @Test
    fun `non-207 report invalidates the token and requests a full listing`() = runBlocking {
        val collection = server.url("/cal/").toString()
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("<error xmlns=\"DAV:\"><valid-sync-token/></error>"),
        )

        val result = IncrementalSync(DavHttp()).syncCollection(
            collectionUrl = collection,
            credentials = credentials,
            cursor = CollectionCursor(ctag = "ctag-1", syncToken = "stale & token"),
        )
        val request = server.takeRequest(1, TimeUnit.SECONDS)

        assertNotNull(request)
        assertEquals("REPORT", request!!.method)
        assertEquals("1", request.getHeader("Depth"))
        assertTrue(request.body.readUtf8().contains("stale &amp; token"))
        assertTrue(result.tokenInvalidated)
        assertTrue(result.requiresFullSync)
        assertTrue(result.changes.isEmpty())
        assertNull(result.nextCursor)
        assertEquals(SyncCollectionFallbackReason.ReportRejected, result.fallbackReason)
        assertEquals(403, result.httpStatus)
    }

    @Test
    fun `all non-207 statuses use the same safe cursor fallback`() = runBlocking {
        val collection = server.url("/cal/").toString()
        for (status in listOf(400, 403, 409, 507)) {
            server.enqueue(MockResponse().setResponseCode(status))

            val result = IncrementalSync(DavHttp()).syncCollection(
                collectionUrl = collection,
                credentials = credentials,
                cursor = CollectionCursor(ctag = "ctag", syncToken = "token"),
            )

            assertTrue("status $status", result.tokenInvalidated)
            assertNull("status $status", result.nextCursor)
            assertEquals(status, result.httpStatus)
        }
    }

    @Test
    fun `malformed or tokenless 207 responses also require a full listing`() {
        val collection = server.url("/cal/").toString()
        val sync = IncrementalSync()

        val malformed = sync.parseReport(collection, "not xml")
        assertEquals(SyncCollectionFallbackReason.MalformedResponse, malformed.fallbackReason)
        assertTrue(malformed.tokenInvalidated)

        val tokenless = sync.parseReport(
            collection,
            """<d:multistatus xmlns:d="DAV:"><d:response><d:href>x.ics</d:href></d:response></d:multistatus>""",
        )
        assertEquals(SyncCollectionFallbackReason.MissingSyncToken, tokenless.fallbackReason)
        assertTrue(tokenless.tokenInvalidated)
        assertNull(tokenless.nextCursor)
    }

    @Test
    fun `a response without an etag is malformed rather than an invisible change`() {
        val collection = server.url("/cal/").toString()
        val result = IncrementalSync().parseReport(
            collection,
            """<d:multistatus xmlns:d="DAV:">
                <d:response>
                  <d:href>changed.ics</d:href>
                  <d:propstat><d:prop/><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                </d:response>
                <d:sync-token>must-not-commit</d:sync-token>
            </d:multistatus>""".trimIndent(),
        )

        assertEquals(SyncCollectionFallbackReason.MalformedResponse, result.fallbackReason)
        assertTrue(result.tokenInvalidated)
        assertTrue(result.changes.isEmpty())
        assertNull(result.nextCursor)
    }
}
