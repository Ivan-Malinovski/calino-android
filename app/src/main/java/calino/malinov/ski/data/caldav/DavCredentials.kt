package calino.malinov.ski.data.caldav

import java.util.Base64

/**
 * Basic-auth credentials for one account.
 *
 * The header is built over UTF-8 bytes per RFC 7617. This is deliberate: the
 * Calino web app had to bypass its DAV library's built-in Basic support because
 * that library encoded Latin-1 and threw outright on any character above
 * U+00FF, which quietly locked out every non-ASCII password.
 */
data class DavCredentials(val username: String, val password: String) {

    fun basicAuthHeader(): String {
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        // java.util.Base64 rather than android.util.Base64: available from API
        // 26, and it works unchanged in plain JVM unit tests, where the Android
        // stub would throw.
        return "Basic " + Base64.getEncoder().encodeToString(raw)
    }

    /** Keeps the password out of logs and crash reports. */
    override fun toString(): String = "DavCredentials(username=$username, password=***)"
}
