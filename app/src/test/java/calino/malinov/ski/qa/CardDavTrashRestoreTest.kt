package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.CardDavFetcher
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DiscoveredAddressBook
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CardDavSource
import calino.malinov.ski.data.repository.FilePendingChangeStore
import calino.malinov.ski.data.repository.PendingChangeType
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.data.repository.UndoableChange
import calino.malinov.ski.data.repository.WriteResult
import java.io.File
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Deleting a contact can be taken back: the vCard is PUT back as the server last sent it. */
class CardDavTrashRestoreTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val credentials = DavCredentials("test-user", "test-pass")
    private val store = ConcurrentHashMap<String, String>()
    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    @Volatile private var deleteCode = 204

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path.orEmpty()
                return when (request.method) {
                    "PUT" -> {
                        if (request.getHeader("If-None-Match") == "*" && store.containsKey(path)) {
                            request.body.readUtf8()
                            MockResponse().setResponseCode(412)
                        } else {
                            store[path] = request.body.readUtf8()
                            MockResponse().setResponseCode(201).setHeader("ETag", "\"c${store.size}\"")
                        }
                    }
                    "DELETE" -> if (deleteCode == 204) {
                        store.remove(path)
                        MockResponse().setResponseCode(204)
                    } else {
                        MockResponse().setResponseCode(deleteCode)
                    }
                    "GET" -> store[path]?.let { MockResponse().setResponseCode(200).setHeader("ETag", "\"c1\"").setBody(it) }
                        ?: MockResponse().setResponseCode(404)
                    "REPORT" -> {
                        request.body.readUtf8()
                        MockResponse().setResponseCode(207)
                            .setHeader("Content-Type", "application/xml; charset=utf-8")
                            .setBody(
                                "<multistatus xmlns=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:carddav\">" +
                                    store.entries.joinToString("") {
                                        "<response><href>${it.key}</href><propstat><prop><getetag>\"c1\"</getetag>" +
                                            "<C:address-data>${it.value}</C:address-data></prop>" +
                                            "<status>HTTP/1.1 200 OK</status></propstat></response>"
                                    } + "</multistatus>",
                            )
                    }
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun vcard(uid: String, name: String, extra: String = "") =
        "BEGIN:VCARD\nVERSION:3.0\nUID:$uid\nFN:$name\nN:;$name;;;\n$extra" + "END:VCARD\n"

    private suspend fun connected(queue: FilePendingChangeStore? = null): CalDavRepository {
        val repository = CalDavRepository(
            fetcher = CalDavFetcher(DavHttp()),
            cardFetcher = CardDavFetcher(DavHttp()),
            scope = scope,
            today = { LocalDate.of(2026, 9, 8) },
            pendingStore = queue,
        )
        val book = DiscoveredAddressBook(server.url("/books/").toString(), "Contacts", readOnly = false)
        repository.setSources(
            sources = emptyList(),
            addressBookSources = listOf(CardDavSource(book, credentials, "account")),
        )
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val state = repository.snapshot().sync
            if (state is SyncState.Ready || state is SyncState.Failed) break
            Thread.sleep(10)
        }
        return repository
    }

    private fun change(result: WriteResult<UndoableChange?>): UndoableChange {
        val value = when (result) {
            is WriteResult.Applied -> result.record
            is WriteResult.Queued -> result.record
            else -> null
        }
        return assertNotNull("delete should offer an undo: $result", value).let { value!! }
    }

    @Test
    fun `deleted contact is recreated at its href with its raw vcard`() = runBlocking {
        store["/books/ada.vcf"] = vcard("ada", "Ada", "X-FOREIGN;X-PARAM=1:keep me\n")
        val repository = connected()
        val contact = repository.contacts().single()

        val offered = change(repository.deleteContact(contact.id))
        assertTrue(store.isEmpty())
        assertEquals(1, repository.trashedItems().size)
        requests.clear()

        assertTrue(repository.undo(offered) is WriteResult.Applied)

        val put = requests.single { it.method == "PUT" }
        assertEquals("/books/ada.vcf", put.path)
        assertEquals("*", put.getHeader("If-None-Match"))
        assertTrue(store.getValue("/books/ada.vcf").contains("X-FOREIGN;X-PARAM=1:keep me"))
        assertTrue(repository.trashedItems().isEmpty())
        assertEquals("Ada", repository.contacts().single().displayName)
    }

    @Test
    fun `restoring a contact while offline queues a create`() = runBlocking {
        store["/books/bob.vcf"] = vcard("bob", "Bob")
        val queueFile = File.createTempFile("calino-card-trash", ".json").also { it.delete() }
        val queue = FilePendingChangeStore(queueFile)
        val repository = connected(queue)
        val offered = change(repository.deleteContact(repository.contacts().single().id))

        server.shutdown()
        val result = repository.undo(offered)

        assertTrue("expected queued restore: $result", result is WriteResult.Queued)
        assertTrue(queue.snapshot().any { it.type == PendingChangeType.CREATE && it.href?.endsWith("bob.vcf") == true })
        assertTrue(repository.trashedItems().isEmpty())
        assertEquals("Bob", repository.contacts().single().displayName)
        queueFile.delete()
        Unit
    }

    @Test
    fun `undo of a contact delete still waiting in the queue replays the create after it`() = runBlocking {
        store["/books/cy.vcf"] = vcard("cy", "Cy", "X-KEEP:3\n")
        deleteCode = 503
        val queueFile = File.createTempFile("calino-card-trash", ".json").also { it.delete() }
        val queue = FilePendingChangeStore(queueFile)
        val repository = connected(queue)

        val offered = change(repository.deleteContact(repository.contacts().single().id))
        assertTrue(queue.snapshot().any { it.type == PendingChangeType.DELETE })

        val result = repository.undo(offered)

        assertTrue("expected queued restore: $result", result is WriteResult.Queued)
        assertEquals(listOf(PendingChangeType.DELETE, PendingChangeType.CREATE), queue.snapshot().map { it.type })
        deleteCode = 204
        repository.drainPendingWrites()
        val deadline = System.currentTimeMillis() + 10_000
        while (queue.snapshot().isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(queue.snapshot().isEmpty())
        assertTrue(store.getValue("/books/cy.vcf").contains("X-KEEP:3"))
        queueFile.delete()
        Unit
    }

    @Test
    fun `a purged contact entry cannot be restored`() = runBlocking {
        store["/books/di.vcf"] = vcard("di", "Di")
        val repository = connected()
        val offered = change(repository.deleteContact(repository.contacts().single().id))
        repository.emptyTrash()

        assertTrue(repository.undo(offered) is WriteResult.Rejected)
        assertTrue(store.isEmpty())
    }
}
