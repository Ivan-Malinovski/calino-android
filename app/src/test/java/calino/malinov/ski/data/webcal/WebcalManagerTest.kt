package calino.malinov.ski.data.webcal

import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.model.WebcalForm
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.WebcalSubscriptionStore
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebcalManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    @Test fun `unreadable feed is not added as an empty subscription`() {
        val store = WebcalSubscriptionStore()
        val repo = CalDavRepository(CalDavFetcher(DavHttp()), scope)
        val manager = WebcalManager(
            store, repo, WebcalFetcher(DavHttp()), WebcalCache.None, scope,
            today = { LocalDate.of(2026, 9, 16) },
        )
        server.enqueue(MockResponse().setBody("BEGIN:VCALENDAR"))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.add(WebcalForm(url = server.url("/broken.ics").toString())) }
        }
        assertTrue(store.subscriptions().isEmpty())
    }

    @Test fun `unreadable refresh retains the last good feed and cache`() = runBlocking {
        val store = WebcalSubscriptionStore()
        val cache = MemoryCache()
        val repo = CalDavRepository(CalDavFetcher(DavHttp()), scope)
        val manager = WebcalManager(
            store, repo, WebcalFetcher(DavHttp()), cache, scope,
            today = { LocalDate.of(2026, 9, 16) },
        )
        val valid = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:good
            DTSTART:20260916T100000Z
            DTEND:20260916T110000Z
            SUMMARY:Good
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        server.enqueue(MockResponse().setBody(valid))
        val subscription = manager.add(WebcalForm(url = server.url("/feed.ics").toString()))
        val fetchedAt = store.subscriptions().single().lastFetchedAt

        server.enqueue(MockResponse().setBody("BEGIN:VCALENDAR"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.sync(subscription.id) }
        }

        assertEquals(listOf("good"), repo.snapshot().events.map { it.uid })
        assertEquals(valid, cache.load(subscription.id))
        assertEquals(fetchedAt, store.subscriptions().single().lastFetchedAt)
    }

    private class MemoryCache : WebcalCache {
        private val values = mutableMapOf<String, String>()
        override fun save(id: String, ics: String) { values[id] = ics }
        override fun load(id: String): String? = values[id]
        override fun delete(id: String) { values.remove(id) }
    }
}
