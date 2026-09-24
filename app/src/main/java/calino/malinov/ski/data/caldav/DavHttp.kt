package calino.malinov.ski.data.caldav

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
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

    private val client: OkHttpClient = (client ?: OkHttpClient.Builder()
        .connectTimeout(TimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(TimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(TimeoutSeconds, TimeUnit.SECONDS)
        .build())
        // Follow redirects ourselves so a DAV server cannot redirect an
        // authenticated request (or its body) to another origin.
        .newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun request(
        method: String,
        url: String,
        credentials: DavCredentials?,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        contentType: MediaType = XmlMediaType,
    ): DavResponse {
        val requestBody = body?.toRequestBody(contentType)
        val builder = Request.Builder().url(url).method(method, requestBody)
        headers.forEach { (name, value) -> builder.header(name, value) }
        credentials?.let { builder.header("Authorization", it.basicAuthHeader()) }
        val initial = builder.build()
        var current = initial
        for (redirectCount in 0..MaxRedirects) {
            val response = client.newCall(current).await()
            val location = response.header("Location")
            val nextUrl = location?.let(current.url::resolve)
            if (response.status !in RedirectStatuses || nextUrl == null) return response
            if (!hasSameDavOrigin(initial.url.toString(), nextUrl.toString())) {
                throw CalDavException(
                    CalDavErrorCode.NotCalDav,
                    "The server redirected a DAV request to a different origin.",
                    status = response.status,
                    body = response.body,
                )
            }
            if (redirectCount == MaxRedirects) {
                throw CalDavException(
                    CalDavErrorCode.NotCalDav,
                    "The server redirected a DAV request too many times.",
                    status = response.status,
                )
            }

            val redirected = current.newBuilder().url(nextUrl)
            if (response.status == SeeOther && current.method != "HEAD") {
                redirected.method("GET", null)
                    .removeHeader("Content-Type")
                    .removeHeader("Content-Length")
                    .removeHeader("Transfer-Encoding")
            }
            current = redirected.build()
        }
        error("Redirect loop exited unexpectedly")
    }

    /**
     * Writes a calendar or contact resource.
     *
     * The precondition is the whole safety story of a write, so it is a required
     * argument rather than an optional header: an update carries the ETag it was
     * read at, a create asserts the resource does not exist, and an idempotent
     * retry (the destination half of a move) deliberately carries neither. See
     * [DavPrecondition].
     */
    suspend fun put(
        url: String,
        credentials: DavCredentials?,
        body: String,
        contentType: MediaType,
        precondition: DavPrecondition,
    ): DavResponse = request(
        method = "PUT",
        url = url,
        credentials = credentials,
        headers = precondition.headers(),
        body = body,
        contentType = contentType,
    )

    /** Deletes a resource, conditionally when [precondition] carries an ETag. */
    suspend fun delete(
        url: String,
        credentials: DavCredentials?,
        precondition: DavPrecondition,
    ): DavResponse = request(
        method = "DELETE",
        url = url,
        credentials = credentials,
        headers = precondition.headers(),
    )

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

    companion object {
        private const val TimeoutSeconds = 15L
        private const val MaxRedirects = 20
        private const val SeeOther = 303
        private val RedirectStatuses = setOf(300, 301, 302, SeeOther, 307, 308)
        val XmlMediaType: MediaType = "application/xml; charset=utf-8".toMediaType()
        val CalendarMediaType: MediaType = "text/calendar; charset=utf-8".toMediaType()
        val VCardMediaType: MediaType = "text/vcard; charset=utf-8".toMediaType()
    }
}

/** Origin comparison for authenticated DAV traffic; default ports are normalized by HttpUrl. */
internal fun hasSameDavOrigin(firstUrl: String, secondUrl: String): Boolean {
    val first = firstUrl.toHttpUrlOrNull() ?: return false
    val second = secondUrl.toHttpUrlOrNull() ?: return false
    return first.scheme == second.scheme && first.host == second.host && first.port == second.port
}

/** Rejects server-supplied hrefs that would move authenticated DAV access to another origin. */
internal fun requireSameDavOrigin(referenceUrl: String, targetUrl: String): String {
    if (!hasSameDavOrigin(referenceUrl, targetUrl)) {
        throw CalDavException(
            CalDavErrorCode.NotCalDav,
            "The server returned a DAV URL on a different origin.",
        )
    }
    return targetUrl
}

/**
 * The `If-Match` / `If-None-Match` a write goes out with.
 *
 * [Match] is the conditional update: it is what makes a lost update visible as a
 * 412 instead of silently clobbering somebody else's edit. [New] asserts the
 * resource is genuinely new. [Unconditional] exists for one narrow case -- the
 * destination write of a move, which must survive being retried and so cannot
 * assert absence. Anything unconditional gives up conflict detection, so the
 * caller compensates by refusing to patch a stale original.
 */
sealed interface DavPrecondition {

    fun headers(): Map<String, String>

    data class Match(val etag: String) : DavPrecondition {
        // Re-quoted here, once, at the only place a tag goes back on the wire.
        // Everything upstream stores tags bare; see `normalizeEtag`.
        override fun headers(): Map<String, String> {
            val bare = normalizeEtag(etag) ?: etag
            return mapOf("If-Match" to "\"" + bare + "\"")
        }
    }

    data object New : DavPrecondition {
        override fun headers() = mapOf("If-None-Match" to "*")
    }

    data object Unconditional : DavPrecondition {
        override fun headers(): Map<String, String> = emptyMap()
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
