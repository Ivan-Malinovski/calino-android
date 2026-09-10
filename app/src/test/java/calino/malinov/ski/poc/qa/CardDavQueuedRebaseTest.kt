package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.CardDavFetcher
import calino.malinov.ski.poc.data.caldav.DavCredentials
import calino.malinov.ski.poc.data.caldav.DavHttp
import calino.malinov.ski.poc.data.caldav.DiscoveredAddressBook
import calino.malinov.ski.poc.data.model.Contact
import calino.malinov.ski.poc.data.repository.CalDavRepository
import calino.malinov.ski.poc.data.repository.CardDavSource
import calino.malinov.ski.poc.data.repository.FilePendingChangeStore
import calino.malinov.ski.poc.data.repository.PendingChangeEnqueueResult
import calino.malinov.ski.poc.data.repository.PendingChangeRequest
import calino.malinov.ski.poc.data.repository.PendingChangeType
import calino.malinov.ski.poc.data.repository.SyncState
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CardDavQueuedRebaseTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var queueFile: File
    private val credentials = DavCredentials("test-user", "test-pass")

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        queueFile = File.createTempFile("calino-card-queued", ".json").also { it.delete() }
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
        queueFile.delete()
    }

    @Test
    fun `a queued contact create with an identical server card is acknowledged after a 412`() = runBlocking {
        val bookUrl = server.url("/books/contacts/").toString()
        val href = server.url("/books/contacts/queued.vcf").toString()
        val payload = """BEGIN:VCARD
VERSION:3.0
UID:queued-contact
FN:Queued Contact
N:Contact;Queued;;;
END:VCARD"""
        val putCount = AtomicInteger()
        val getCount = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                request.body.readUtf8()
                return when {
                    request.method == "REPORT" -> emptyReport()
                    request.method == "PUT" -> {
                        putCount.incrementAndGet()
                        MockResponse().setResponseCode(412)
                    }
                    request.method == "GET" && request.path == "/books/contacts/queued.vcf" -> {
                        getCount.incrementAndGet()
                        MockResponse().setResponseCode(200)
                            .setHeader("ETag", "\"card-v1\"")
                            .setBody(payload)
                    }
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val queue = FilePendingChangeStore(queueFile)
        val enqueued = queue.enqueue(
            PendingChangeRequest(
                type = PendingChangeType.CREATE,
                eventId = "queued-contact",
                accountId = "account",
                calendarId = bookUrl,
                component = "VCARD",
                calendarUrl = bookUrl,
                uid = "queued-contact",
                href = href,
                data = payload,
            ),
        )
        assertTrue(enqueued is PendingChangeEnqueueResult.Enqueued)

        val repository = CalDavRepository(
            fetcher = calino.malinov.ski.poc.data.caldav.CalDavFetcher(DavHttp()),
            cardFetcher = CardDavFetcher(DavHttp()),
            scope = scope,
            today = { LocalDate.of(2026, 9, 8) },
            pendingStore = queue,
        )
        val book = DiscoveredAddressBook(
            url = bookUrl,
            displayName = "Contacts",
            readOnly = false,
        )
        repository.setSources(
            sources = emptyList(),
            addressBookSources = listOf(CardDavSource(book, credentials, "account")),
        )
        repository.awaitSync()
        awaitQueue(queue)

        assertTrue("the already-applied contact create should be acknowledged", queue.snapshot().isEmpty())
        assertEquals(1, putCount.get())
        assertEquals(1, getCount.get())
        val contact: Contact = repository.contacts().single()
        assertEquals(href, contact.href)
        assertEquals("card-v1", contact.etag)
        assertEquals("Queued Contact", contact.displayName)
    }

    private fun emptyReport() = MockResponse()
        .setResponseCode(207)
        .setHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody("<multistatus xmlns=\"DAV:\"/>")

    private fun CalDavRepository.awaitSync(): SyncState {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val state = snapshot().sync
            if (state is SyncState.Ready || state is SyncState.Failed) return state
            Thread.sleep(10)
        }
        error("repository fetch did not settle: ${snapshot().sync}")
    }

    private fun awaitQueue(queue: FilePendingChangeStore) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (queue.snapshot().isEmpty()) return
            Thread.sleep(10)
        }
        error("queue did not drain: ${queue.snapshot()}")
    }
}
