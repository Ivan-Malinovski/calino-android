package calino.malinov.ski.data.webcal

import calino.malinov.ski.data.caldav.DavHttp
import okhttp3.MediaType.Companion.toMediaType

/**
 * GET an iCalendar document. No principal, no REPORT, no credentials.
 *
 * The whole body is read because the parser needs the document; [MaxBytes]
 * is the backstop so a misconfigured URL cannot fill the cache with HTML.
 */
class WebcalFetcher(
    private val http: DavHttp = DavHttp(),
) {
    suspend fun fetchIcs(url: String): String {
        val uri = WebcalUrl.requireHttpUrl(url)
        val response = runCatching {
            http.request(
                method = "GET",
                url = uri.toString(),
                credentials = null,
                headers = mapOf("Accept" to "text/calendar, text/plain, */*"),
                contentType = TextMediaType,
            )
        }.getOrElse { error ->
            throw WebcalException(
                "Could not reach the calendar URL: ${error.message ?: "network error"}",
                error,
            )
        }
        if (response.status !in 200..299) {
            throw WebcalException("Calendar URL returned status ${response.status}")
        }
        val text = response.body
        if (text.length > MaxBytes) {
            throw WebcalException("That calendar is larger than 5 MB.")
        }
        if (text.isBlank()) {
            throw WebcalException("Calendar URL returned an empty response.")
        }
        if (!text.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
            throw WebcalException("That URL did not return a valid iCalendar (.ics) file.")
        }
        return text
    }

    companion object {
        const val MaxBytes = 5 * 1024 * 1024
        private val TextMediaType = "text/plain; charset=utf-8".toMediaType()
    }
}
