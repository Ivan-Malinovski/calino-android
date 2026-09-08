package calino.malinov.ski.poc.data.caldav

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Minimal WebDAV transport.
 *
 * OkHttp rather than [java.net.HttpURLConnection] because the platform stack
 * rejects the verbs this needs: `PROPFIND` and `REPORT` both raise
 * `ProtocolException`. The Calino web app hit the same wall and carries an
 * Android OkHttp plugin for exactly this reason.
 */
class DavHttp(client: OkHttpClient? = null) {

    private val client: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(TimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(TimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(TimeoutSeconds, TimeUnit.SECONDS)
        // Redirects are followed because well-known probing depends on it, and
        // across schemes because a server may bounce http -> https mid-chain.
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun request(
        method: String,
        url: String,
        credentials: DavCredentials?,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): DavResponse {
        val requestBody = body?.toRequestBody(XmlMediaType)
        val builder = Request.Builder().url(url).method(method, requestBody)
        headers.forEach { (name, value) -> builder.header(name, value) }
        credentials?.let { builder.header("Authorization", it.basicAuthHeader()) }
        return client.newCall(builder.build()).await()
    }

    private suspend fun Call.await(): DavResponse = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val resolved = response.use {
                    DavResponse(
                        status = it.code,
                        body = it.body?.string().orEmpty(),
                        // The final URL after redirects. Well-known discovery
                        // reads this rather than assuming the request URL.
                        url = it.request.url.toString(),
                        headers = it.headers.toMultimap()
                            .mapValues { entry -> entry.value.joinToString(", ") },
                    )
                }
                if (continuation.isActive) continuation.resume(resolved)
            }
        })
    }

    private companion object {
        const val TimeoutSeconds = 15L
        val XmlMediaType = "application/xml; charset=utf-8".toMediaType()
    }
}

data class DavResponse(
    val status: Int,
    val body: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    /** A 207 carries the multistatus body; anything else is not a DAV answer. */
    val isMultiStatus: Boolean get() = status == 207

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}
