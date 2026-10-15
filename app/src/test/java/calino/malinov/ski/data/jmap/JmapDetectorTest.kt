package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.model.CalDavForm
import calino.malinov.ski.data.model.CalendarProtocol
import calino.malinov.ski.state.CalDavConnectResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JmapDetectorTest {
    private val credentials = DavCredentials("ann@example.com", "secret")
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() = servers.forEach { runCatching { it.shutdown() } }

    private fun serve(handler: (RecordedRequest) -> MockResponse): String {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = handler(request)
        }
        server.start()
        servers += server
        return server.url("/").toString().trimEnd('/')
    }

    @Test
    fun aJmapCalendarsServerIsDetected() = runBlocking {
        val fake = FakeJmapServer()
        fake.start()
        val detection = detectProtocol(fake.baseUrl, credentials)
        fake.stop()
        assertTrue(detection is ProtocolDetection.Jmap)
        assertEquals("a", (detection as ProtocolDetection.Jmap).session.calendarAccountId)
    }

    @Test
    fun aSessionWithoutCalendarsFallsThroughToCalDav() = runBlocking {
        val fake = FakeJmapServer()
        fake.sessionOverride = { session ->
            session.getJSONObject("capabilities").remove(JmapCalendars)
            session.getJSONObject("primaryAccounts").remove(JmapCalendars)
        }
        fake.start()
        val detection = detectProtocol(fake.baseUrl, credentials)
        fake.stop()
        assertEquals(ProtocolDetection.CalDav, detection)
    }

    @Test
    fun htmlLoginPagesAndNotFoundFallThrough() = runBlocking {
        val html = serve { MockResponse().setHeader("Content-Type", "text/html").setBody("<html>sign in</html>") }
        assertEquals(ProtocolDetection.CalDav, detectProtocol(html, credentials))
        val missing = serve { MockResponse().setResponseCode(404) }
        assertEquals(ProtocolDetection.CalDav, detectProtocol(missing, credentials))
        val json = serve { MockResponse().setHeader("Content-Type", "application/json").setBody("[1,2]") }
        assertEquals(ProtocolDetection.CalDav, detectProtocol(json, credentials))
    }

    @Test
    fun aJsonUnauthorizedWithChallengeStopsWithAnAuthError() = runBlocking {
        val url = serve {
            MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("WWW-Authenticate", "Basic realm=\"jmap\"")
                .setBody("""{"type":"about:blank","status":401}""")
        }
        try {
            detectProtocol(url, credentials)
            fail("expected an auth error")
        } catch (error: CalDavException) {
            assertEquals(CalDavErrorCode.Auth, error.code)
        }
    }

    @Test
    fun anUnauthorizedWithAnotherContentTypeIsNotConclusive() = runBlocking {
        val url = serve {
            MockResponse().setResponseCode(401).setHeader("Content-Type", "text/html")
                .setHeader("WWW-Authenticate", "Basic").setBody("nope")
        }
        assertEquals(ProtocolDetection.CalDav, detectProtocol(url, credentials))
    }

    @Test
    fun aSlowServerTimesOutAndFallsThrough() = runBlocking {
        val url = serve { MockResponse().setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS).setBody("{}") }
        assertEquals(ProtocolDetection.CalDav, detectProtocol(url, credentials, timeoutMillis = 100))
    }

    @Test
    fun anUnreachableHostFallsThrough() = runBlocking {
        assertEquals(ProtocolDetection.CalDav, detectProtocol("http://127.0.0.1:1", credentials, timeoutMillis = 500))
    }

    @Test
    fun discoveryListsCalendarsWithSyntheticUrlsAndTheEventStateAsCursor() = runBlocking {
        val fake = FakeJmapServer()
        fake.respond = { name, _, _ ->
            when (name) {
                "CalendarEvent/get" -> jsonObject("accountId" to "a", "state" to "ev-7", "list" to org.json.JSONArray(), "notFound" to org.json.JSONArray())
                "Calendar/get" -> jsonObject(
                    "state" to "cal-1",
                    "list" to jsonArray(
                        listOf(
                            jsonObject("id" to "c1", "name" to "Work", "color" to "#ff0000", "myRights" to jsonObject("mayWriteAll" to true)),
                            jsonObject("id" to "c/2", "name" to "Family", "myRights" to jsonObject("mayWriteAll" to false, "mayReadItems" to true)),
                            jsonObject("id" to "c3", "name" to "Feed", "isSubscribed" to true),
                            // Stalwart flags the person's own default calendar as subscribed too.
                            jsonObject("id" to "c4", "name" to "Own", "isSubscribed" to true, "myRights" to jsonObject("mayWriteAll" to true)),
                        ),
                    ),
                )
                else -> null
            }
        }
        fake.start()
        val account = JmapDiscovery().discoverAccount(fake.baseUrl, credentials)
        fake.stop()
        assertEquals(4, account.calendars.size)
        val (work, family, feed, own) = account.calendars
        assertFalse("a subscribed calendar with write rights is the person's own", own.readOnly)
        assertEquals("${fake.baseUrl}/.jmap/a/c1/", work.url)
        assertEquals("${fake.baseUrl}/.jmap/a/c%2F2/", family.url)
        assertEquals(0xFFFF0000, work.color)
        assertEquals(CalendarProtocol.Jmap, work.protocol)
        assertEquals(null, work.ctag)
        assertEquals("ev-7", work.syncToken)
        assertEquals(false, work.readOnly)
        assertEquals(true, family.readOnly)
        assertEquals(true, feed.readOnly)
    }

    @Test
    fun theSheetDiscoveryPrefersJmapAndOtherwiseDelegatesToCalDav() = runBlocking {
        val fake = FakeJmapServer()
        fake.respond = { name, _, _ ->
            when (name) {
                "CalendarEvent/get" -> jsonObject("state" to "s", "list" to org.json.JSONArray())
                "Calendar/get" -> jsonObject("list" to jsonArray(listOf(jsonObject("id" to "c1", "name" to "Work"))))
                else -> null
            }
        }
        fake.start()
        val http = calino.malinov.ski.data.caldav.DavHttp()
        val discovery = ProtocolDiscovery(
            calino.malinov.ski.data.caldav.CalDavDiscovery(http), JmapDiscovery(http), http,
        )
        val result = discovery.discover(CalDavForm(fake.baseUrl, "ann", "pw"))
        fake.stop()
        val found = result as CalDavConnectResult.Discovered
        assertEquals(CalendarProtocol.Jmap, found.protocol)
        assertEquals("Work", found.calendars.single().name)
    }

    @Test
    fun syntheticUrlsRoundTripAndRefuseForeignOnes() {
        val server = "https://mail.example.com/jmap"
        val url = jmapEventUrl(server, "acc 1", "cal/1", "ev+é")
        assertEquals("https://mail.example.com/.jmap/acc%201/cal%2F1/ev%2B%C3%A9", url)
        val parsed = parseJmapUrl(url, server, "acc 1", event = true)
        assertEquals(JmapResourceId("cal/1", "ev+é"), parsed)
        assertEquals("cal/1", parseJmapUrl(jmapCalendarUrl(server, "acc 1", "cal/1"), server, "acc 1", event = false).calendarId)
        listOf(
            "https://other.example/.jmap/acc%201/cal/ev",
            "https://mail.example.com/.jmap/other/cal/ev",
            "https://mail.example.com/.jmap/acc%201/cal/ev?x=1",
            "https://mail.example.com/.jmap/acc%201/cal/",
        ).forEach {
            try {
                parseJmapUrl(it, server, "acc 1", event = true)
                fail("accepted $it")
            } catch (error: JmapException) {
                assertEquals(400, error.status)
            }
        }
    }
}
