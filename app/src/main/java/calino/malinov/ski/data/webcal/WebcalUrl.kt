package calino.malinov.ski.data.webcal

import java.net.URI

/**
 * `webcal://` and `webcals://` are aliases for HTTP(S). Publishers still hand
 * them out so the OS opens a calendar app; the transport is ordinary GET.
 */
object WebcalUrl {
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("webcals://", ignoreCase = true) ->
                "https://" + trimmed.substring("webcals://".length)
            trimmed.startsWith("webcal://", ignoreCase = true) ->
                "https://" + trimmed.substring("webcal://".length)
            else -> trimmed
        }
    }

    fun requireHttpUrl(raw: String): URI {
        val normalized = normalize(raw)
        val uri = runCatching { URI(normalized) }.getOrElse {
            throw WebcalException("Enter a valid calendar URL (https:// or webcal://).")
        }
        if (uri.scheme != "https" && uri.scheme != "http") {
            throw WebcalException("Only http(s):// and webcal:// URLs are supported.")
        }
        if (uri.host.isNullOrBlank()) {
            throw WebcalException("Enter a valid calendar URL (https:// or webcal://).")
        }
        return uri
    }

    /** Host only, for logs and UI. The path of a Google secret address is a credential. */
    fun hostOf(raw: String): String =
        runCatching { requireHttpUrl(raw).host }.getOrNull() ?: "calendar URL"
}

class WebcalException(message: String, cause: Throwable? = null) : Exception(message, cause)
