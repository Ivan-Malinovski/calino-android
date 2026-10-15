package calino.malinov.ski.data.jmap

import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject

/** A scripted JMAP server: serves a session and answers each method call from a lambda. */
class FakeJmapServer(
    private val sessionState: () -> String = { "s1" },
    private val coreCapabilities: JSONObject = jsonObject(
        "maxCallsInRequest" to 4, "maxObjectsInGet" to 2, "maxObjectsInSet" to 2,
    ),
    private val advertisedHost: (base: String) -> String = { it },
) : Dispatcher() {
    val server = MockWebServer()

    /** Every API request body, in order. */
    val apiRequests = CopyOnWriteArrayList<JSONObject>()
    val requestHeaders = CopyOnWriteArrayList<Map<String, String>>()
    var respond: (name: String, args: JSONObject, callId: String) -> JSONObject? = { _, _, _ -> null }
    /** Answers `/events` requests; each request is also recorded in [eventRequests]. */
    var events: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }
    val eventRequests = CopyOnWriteArrayList<RecordedRequest>()
    var sessionOverride: ((JSONObject) -> Unit)? = null

    val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    fun start() {
        server.dispatcher = this
        server.start()
    }

    fun stop() = server.shutdown()

    fun sessionJson(): JSONObject {
        val base = advertisedHost(baseUrl)
        return JSONObject().apply {
            put(
                "capabilities",
                jsonObject(JmapCore to coreCapabilities, JmapCalendars to JSONObject()),
            )
            put(
                "accounts",
                jsonObject(
                    "a" to jsonObject(
                        "name" to "Personal", "isPersonal" to true, "isReadOnly" to false,
                        "accountCapabilities" to jsonObject(JmapCalendars to JSONObject()),
                    ),
                ),
            )
            put("primaryAccounts", jsonObject(JmapCalendars to "a"))
            put("username", "ann@example.com")
            put("apiUrl", "$base/jmap/api")
            put("uploadUrl", "$base/upload/{accountId}")
            put("downloadUrl", "$base/download/{accountId}/{blobId}/{name}?type={type}")
            put("eventSourceUrl", "$base/events?types={types}&closeafter={closeafter}&ping={ping}")
            put("state", sessionState())
            sessionOverride?.invoke(this)
        }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty()
        return when {
            path.startsWith("/.well-known/jmap") || path.startsWith("/jmap/session") ->
                MockResponse().setHeader("Content-Type", "application/json").setBody(sessionJson().toString())
            path.startsWith("/jmap/api") -> {
                requestHeaders += request.headers.toMultimap().mapValues { it.value.joinToString(",") }
                val body = JSONObject(request.body.readUtf8())
                apiRequests += body
                val calls = body.getJSONArray("methodCalls")
                val out = JSONArray()
                for (index in 0 until calls.length()) {
                    val call = calls.getJSONArray(index)
                    val name = call.getString(0)
                    val args = call.getJSONObject(1)
                    val id = call.getString(2)
                    val answer = respond(name, args, id)
                    if (answer == null) {
                        out.put(JSONArray().put("error").put(jsonObject("type" to "unknownMethod")).put(id))
                    } else if (answer.has("@error")) {
                        out.put(JSONArray().put("error").put(answer.getJSONObject("@error")).put(id))
                    } else {
                        out.put(JSONArray().put(name).put(answer).put(id))
                    }
                }
                MockResponse().setHeader("Content-Type", "application/json").setBody(
                    JSONObject().put("methodResponses", out).put("sessionState", sessionState()).toString(),
                )
            }
            path.startsWith("/events") -> {
                eventRequests += request
                events(request)
            }
            else -> MockResponse().setResponseCode(404)
        }
    }
}
