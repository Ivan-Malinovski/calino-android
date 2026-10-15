package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

const val JmapCore = "urn:ietf:params:jmap:core"
const val JmapCalendars = "urn:ietf:params:jmap:calendars"
const val JmapCalendarsParse = "urn:ietf:params:jmap:calendars:parse"
const val JmapContacts = "urn:ietf:params:jmap:contacts"
const val JmapPrincipals = "urn:ietf:params:jmap:principals"
const val JmapPrincipalsAvailability = "urn:ietf:params:jmap:principals:availability"
const val JmapTasks = "urn:ietf:params:jmap:tasks"

data class JmapInvocation(val name: String, val arguments: JSONObject, val callId: String)

data class JmapResponse(
    val methodResponses: List<JmapInvocation>,
    val sessionState: String,
    val createdIds: Map<String, String> = emptyMap(),
) {
    /** The response of the call with this id, which is always unique within a request. */
    fun result(callId: String): JSONObject? =
        methodResponses.firstOrNull { it.callId == callId && it.name != "error" }?.arguments
}

data class JmapAccount(
    val name: String,
    val isPersonal: Boolean,
    val isReadOnly: Boolean,
    val accountCapabilities: JSONObject,
)

data class JmapSession(
    val capabilities: JSONObject,
    val accounts: Map<String, JmapAccount>,
    val primaryAccounts: Map<String, String>,
    val username: String,
    val apiUrl: String,
    val uploadUrl: String,
    val downloadUrl: String,
    val eventSourceUrl: String,
    val state: String,
) {
    val core: JSONObject get() = capabilities.objectOrNull(JmapCore) ?: JSONObject()

    /** The primary account for a capability, only when that account really offers it. */
    fun accountFor(capability: String): String? {
        val id = primaryAccounts[capability] ?: return null
        if (capabilities.objectOrNull(capability) == null) return null
        return id.takeIf { accounts[it]?.accountCapabilities?.objectOrNull(capability) != null }
    }

    val calendarAccountId: String? get() = accountFor(JmapCalendars)
    val contactsAccountId: String? get() = accountFor(JmapContacts)
}

private val SessionStrings = listOf("username", "apiUrl", "uploadUrl", "downloadUrl", "eventSourceUrl", "state")

/** Validates the session document strictly; a server that is not JMAP fails here. */
fun parseSession(value: Any?): JmapSession {
    fun invalid(): Nothing = throw JmapException(
        CalDavErrorCode.NotCalDav,
        "Invalid JMAP session document or missing core capability",
        type = "invalidSession",
    )
    val json = value as? JSONObject ?: invalid()
    val capabilities = json.objectOrNull("capabilities") ?: invalid()
    if (capabilities.objectOrNull(JmapCore) == null) invalid()
    if (capabilities.keyList().any { capabilities.objectOrNull(it) == null }) invalid()
    val accountsJson = json.objectOrNull("accounts") ?: invalid()
    val primaryJson = json.objectOrNull("primaryAccounts") ?: invalid()
    if (SessionStrings.any { json.stringOrNull(it) == null }) invalid()
    val primary = primaryJson.keyList().associateWith { primaryJson.stringOrNull(it) ?: invalid() }
    val accounts = accountsJson.keyList().associateWith { id ->
        val account = accountsJson.objectOrNull(id) ?: invalid()
        val accountCapabilities = account.objectOrNull("accountCapabilities") ?: invalid()
        if (accountCapabilities.keyList().any { accountCapabilities.objectOrNull(it) == null }) invalid()
        JmapAccount(
            name = account.stringOrNull("name") ?: invalid(),
            isPersonal = account.boolOrNull("isPersonal") ?: invalid(),
            isReadOnly = account.boolOrNull("isReadOnly") ?: invalid(),
            accountCapabilities = accountCapabilities,
        )
    }
    return JmapSession(
        capabilities = capabilities,
        accounts = accounts,
        primaryAccounts = primary,
        username = json.getString("username"),
        apiUrl = json.getString("apiUrl"),
        uploadUrl = json.getString("uploadUrl"),
        downloadUrl = json.getString("downloadUrl"),
        eventSourceUrl = json.getString("eventSourceUrl"),
        state = json.getString("state"),
    )
}

/**
 * Where to look for the session document, most likely first: the origin's
 * `/.well-known/jmap`, then the same path under any path the person typed
 * (a server behind a sub-path proxy).
 */
fun sessionCandidates(serverUrl: String): List<String> {
    val url = serverUrl.trim().toHttpUrlOrNull() ?: throw JmapException(
        CalDavErrorCode.NotCalDav, "Enter an HTTP(S) server URL",
    )
    if (url.encodedPath.trimEnd('/').let { it.endsWith("/jmap/session") || it.endsWith("/.well-known/jmap") }) {
        return listOf(url.newBuilder().fragment(null).build().toString())
    }
    val candidates = mutableListOf(
        url.newBuilder().encodedPath("/.well-known/jmap").query(null).fragment(null).build().toString(),
    )
    if (url.encodedPath != "/") {
        candidates += url.newBuilder()
            .encodedPath(url.encodedPath.trimEnd('/') + "/.well-known/jmap")
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }
    return candidates
}

private fun isLoopbackHost(host: String): Boolean =
    host == "localhost" || host == "::1" || host == "[::1]" || host.startsWith("127.")

/**
 * Resolves a possibly relative session URL against the URL the session came
 * from, keeping `{variable}` templates intact.
 *
 * A server configured as `localhost` (Stalwart's default) advertises
 * `https://localhost/...` even when reached elsewhere. Only *loopback*
 * advertisements are rebased onto the origin already in use; any other host is
 * left alone so a server cannot steer credentials to a third party. Callers
 * still check the origin before sending anything.
 */
fun resolveTemplate(url: String, base: String): String {
    val baseUrl = base.toHttpUrl()
    val resolved = baseUrl.resolve(url) ?: throw JmapException(
        CalDavErrorCode.NotCalDav, "Invalid JMAP endpoint URL", type = "invalidSession",
    )
    val rebased = if (!sameOrigin(resolved, baseUrl) && isLoopbackHost(resolved.host)) {
        resolved.newBuilder().scheme(baseUrl.scheme).host(baseUrl.host).port(baseUrl.port).build()
    } else {
        resolved
    }
    if (rebased.username.isNotEmpty() || rebased.password.isNotEmpty()) {
        throw JmapException(CalDavErrorCode.NotCalDav, "Invalid JMAP endpoint URL", type = "invalidSession")
    }
    return rebased.toString().replace(Regex("%7B", RegexOption.IGNORE_CASE), "{")
        .replace(Regex("%7D", RegexOption.IGNORE_CASE), "}")
}

private fun sameOrigin(first: HttpUrl, second: HttpUrl) =
    first.scheme == second.scheme && first.host == second.host && first.port == second.port

/** Fills `{name}` variables, percent-encoding each value. An unknown variable is an error. */
fun expandTemplate(template: String, values: Map<String, String>): String =
    Regex("\\{([^}]+)}").replace(template) { match ->
        val key = match.groupValues[1]
        val value = values[key] ?: throw JmapException(
            CalDavErrorCode.NotCalDav, "Unsupported JMAP URL template variable: $key",
        )
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

/** True when both URLs share scheme, host and port. */
internal fun sameJmapOrigin(first: String, second: String): Boolean {
    val a = first.toHttpUrlOrNull() ?: return false
    val b = second.toHttpUrlOrNull() ?: return false
    return sameOrigin(a, b)
}

internal fun requireCalDavException(error: Throwable): CalDavException? = error as? CalDavException
