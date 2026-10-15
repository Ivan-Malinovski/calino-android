package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.DavCredentials
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JmapClientTest {
    private var state = "s1"
    private val fake = FakeJmapServer(sessionState = { state })

    @After
    fun tearDown() = fake.stop()

    private fun client(): JmapClient {
        fake.start()
        return JmapClient(fake.baseUrl, DavCredentials("ann@example.com", "päss🔑"))
    }

    private fun call(name: String, id: String, vararg pairs: Pair<String, Any?>) =
        JmapInvocation(name, jsonObject(*pairs), id)

    @Test
    fun connectLoadsTheSessionAndKeepsTemplates() = runBlocking {
        val client = client()
        client.connect()
        assertEquals("a", client.accountId)
        assertTrue(client.apiUrl.endsWith("/jmap/api"))
        assertTrue(client.downloadUrl.contains("{accountId}/{blobId}/{name}?type={type}"))
        assertTrue(client.eventSourceUrl.contains("types={types}&closeafter={closeafter}&ping={ping}"))
    }

    @Test
    fun loopbackAdvertisementsAreRebasedOntoTheReachedOrigin() = runBlocking {
        val fake = FakeJmapServer(advertisedHost = { "https://localhost" })
        fake.start()
        try {
            val client = JmapClient(fake.baseUrl, DavCredentials("u", "p"))
            client.connect()
            assertTrue(client.apiUrl.startsWith(fake.baseUrl))
        } finally {
            fake.stop()
        }
    }

    @Test
    fun anEndpointOnAnotherHostIsRefused() = runBlocking {
        val fake = FakeJmapServer(advertisedHost = { "http://attacker.example" })
        fake.start()
        try {
            JmapClient(fake.baseUrl, DavCredentials("u", "p")).connect()
            fail("expected a refusal")
        } catch (error: JmapException) {
            assertEquals("invalidSession", error.type)
        } finally {
            fake.stop()
        }
    }

    @Test
    fun resolveTemplateOnlyRebasesLoopback() {
        assertEquals(
            "http://10.0.0.5:8080/jmap/{accountId}/",
            resolveTemplate("https://localhost/jmap/{accountId}/", "http://10.0.0.5:8080/.well-known/jmap"),
        )
        assertEquals(
            "https://other.example/jmap/",
            resolveTemplate("https://other.example/jmap/", "http://10.0.0.5:8080/.well-known/jmap"),
        )
        assertEquals(
            "https://mail.test/api",
            resolveTemplate("/api", "https://mail.test/jmap/session"),
        )
    }

    @Test
    fun sessionCandidatesTryTheOriginThenTheTypedPath() {
        assertEquals(listOf("https://a.test/.well-known/jmap"), sessionCandidates("https://a.test"))
        assertEquals(
            listOf("https://a.test/.well-known/jmap", "https://a.test/mail/.well-known/jmap"),
            sessionCandidates("https://a.test/mail/"),
        )
        assertEquals(listOf("https://a.test/jmap/session"), sessionCandidates("https://a.test/jmap/session"))
    }

    @Test
    fun aSessionWithoutTheCoreCapabilityIsNotJmap() {
        val session = FakeJmapServer().sessionJson()
        session.getJSONObject("capabilities").remove(JmapCore)
        try {
            parseSession(session)
            fail("expected invalid session")
        } catch (error: JmapException) {
            assertEquals("invalidSession", error.type)
        }
    }

    @Test
    fun callsAreSentWithUsingAndBasicAuth() = runBlocking {
        val client = client()
        client.connect()
        fake.respond = { _, _, _ -> jsonObject("ok" to true) }
        client.call(listOf(call("Core/echo", "c1")))
        val body = fake.apiRequests.single()
        assertTrue(body.getJSONArray("using").toList().containsAll(listOf(JmapCore, JmapCalendars)))
        assertTrue(fake.requestHeaders.single()["authorization"]!!.startsWith("Basic "))
    }

    @Test
    fun batchesAreSplitAtMaxCallsInRequest() = runBlocking {
        val client = client()
        client.connect()
        fake.respond = { _, _, _ -> jsonObject("ok" to true) }
        val response = client.call((1..6).map { call("Core/echo", "c$it") })
        assertEquals(listOf(4, 2), fake.apiRequests.map { it.getJSONArray("methodCalls").length() })
        assertEquals((1..6).map { "c$it" }, response.methodResponses.map { it.callId })
    }

    @Test
    fun oversizedGetIsSplitAndMerged() = runBlocking {
        val client = client()
        client.connect()
        fake.respond = { _, args, _ ->
            val ids = args.getJSONArray("ids").toList().map { it as String }
            jsonObject(
                "state" to "x",
                "list" to jsonArray(ids.filter { it != "missing" }.map { jsonObject("id" to it) }),
                "notFound" to jsonArray(ids.filter { it == "missing" }),
            )
        }
        val response = client.call(
            listOf(call("CalendarEvent/get", "g", "accountId" to "a", "ids" to jsonArray(listOf("1", "2", "3", "missing")))),
        )
        assertEquals(2, fake.apiRequests.size)
        val merged = response.result("g")!!
        assertEquals(3, merged.getJSONArray("list").length())
        assertEquals(listOf("missing"), merged.getJSONArray("notFound").toList())
    }

    @Test
    fun resultReferencesStayOnTheWireInOneRequestAndResolveAcrossFlushes() = runBlocking {
        val client = client()
        client.connect()
        fake.respond = { name, args, _ ->
            when (name) {
                "CalendarEvent/query" -> jsonObject("ids" to jsonArray(listOf("1", "2", "3")), "queryState" to "q")
                else -> jsonObject(
                    "state" to "x",
                    "list" to jsonArray((args.optJSONArray("ids")?.toList().orEmpty()).map { jsonObject("id" to it) }),
                )
            }
        }
        val ref = resultReference("ids", "q", "CalendarEvent/query", "/ids")
        val response = client.call(
            listOf(
                call("CalendarEvent/query", "q", "accountId" to "a"),
                call("CalendarEvent/get", "g", "accountId" to "a", ref.first to ref.second),
            ),
        )
        // The get exceeds maxObjectsInGet, so its producer ran first and the ids were inlined.
        assertEquals(3, response.result("g")!!.getJSONArray("list").length())
        assertTrue(fake.apiRequests.drop(1).all { !it.getJSONArray("methodCalls").getJSONArray(0).getJSONObject(1).has("#ids") })
    }

    @Test
    fun pointerHandlesEscapesAndWildcards() {
        val value = JSONObject("""{"a/b":{"~":[{"x":1},{"x":2}]}}""")
        assertEquals(2, (pointer(value, "/a~1b/~0/*/x") as JSONArray).length())
        try {
            pointer(value, "/missing")
            fail()
        } catch (error: JmapException) {
            assertEquals("invalidResultReference", error.type)
        }
    }

    @Test
    fun setIsSplitByCombinedOperationCountWithIfInStateChained() = runBlocking {
        val client = client()
        client.connect()
        var counter = 0
        fake.respond = { _, args, _ ->
            counter += 1
            jsonObject(
                "oldState" to "s${counter - 1}",
                "newState" to "s$counter",
                "created" to JSONObject().also { created ->
                    args.optJSONObject("create")?.keyList()?.forEach { created.put(it, jsonObject("id" to "srv-$it")) }
                },
                "destroyed" to (args.optJSONArray("destroy") ?: JSONArray()),
            )
        }
        val response = client.call(
            listOf(
                call(
                    "CalendarEvent/set", "s",
                    "accountId" to "a",
                    "ifInState" to "s0",
                    "create" to jsonObject("c1" to JSONObject(), "c2" to JSONObject(), "c3" to JSONObject()),
                    "destroy" to jsonArray(listOf("d1")),
                ),
            ),
        )
        assertEquals(2, fake.apiRequests.size)
        val secondArgs = fake.apiRequests[1].getJSONArray("methodCalls").getJSONArray(0).getJSONObject(1)
        assertEquals("s1", secondArgs.getString("ifInState"))
        val merged = response.result("s")!!
        assertEquals(3, merged.getJSONObject("created").length())
        assertEquals(1, merged.getJSONArray("destroyed").length())
        assertEquals("srv-c1", response.createdIds["c1"])
    }

    @Test
    fun aChangedSessionStateRefreshesTheSession() = runBlocking {
        val client = client()
        client.connect()
        fake.respond = { _, _, _ -> jsonObject("ok" to true) }
        state = "s2"
        client.call(listOf(call("Core/echo", "c1")))
        assertEquals("s2", client.session.state)
    }

    @Test
    fun methodErrorsCarryAnHttpStatusForTheQueue() = runBlocking {
        val client = client()
        client.connect()
        fake.respond = { _, _, _ -> jsonObject("@error" to jsonObject("type" to "stateMismatch", "description" to "old")) }
        try {
            client.call(listOf(call("CalendarEvent/set", "s", "accountId" to "a")))
            fail("expected an error")
        } catch (error: JmapException) {
            assertEquals(412, error.status)
            assertEquals(CalDavErrorCode.PreconditionFailed, error.code)
            assertEquals("CalendarEvent/set", error.method)
            assertNotNull(error.details)
        }
    }

    @Test
    fun retryAfterAcceptsSecondsAndDatesAndIsClamped() {
        assertEquals(30, parseRetryAfter("30"))
        assertEquals(3600, parseRetryAfter("999999"))
        assertEquals(null, parseRetryAfter(""))
        assertEquals(0, parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT", nowMillis = 1_900_000_000_000))
    }

    @Test
    fun canonicalJsonSortsKeysAndMatchesJsonStringify() {
        val value = JSONObject("""{"b":[3,{"z":null,"a":"x/\"y\n"}],"a":1.0,"é":true}""")
        assertEquals("""{"a":1,"b":[3,{"a":"x/\"y\n","z":null}],"é":true}""", canonicalJson(value))
    }
}
