package calino.malinov.ski.data.model

import java.time.Instant

/**
 * A read-only calendar filled from a public iCalendar URL.
 *
 * This is not a CalDAV account. There is no principal, no REPORT, and nothing
 * to PUT. The feed is fetched as a document, parsed, and shown as a calendar
 * the editor will not write to. [url] is a capability URL on some publishers
 * (Google's "secret address") and must not be logged in full.
 *
 * [notifyReminders] defaults to false: a subscription is an overlay, and the
 * publisher's VALARMs are not the user's choice. Same default as the web app.
 */
data class WebcalSubscription(
    val id: String,
    val calendarId: String,
    val name: String,
    val color: Long,
    val url: String,
    val refreshIntervalMinutes: Int = DefaultRefreshMinutes,
    val notifyReminders: Boolean = false,
    val visible: Boolean = true,
    val lastFetchedAt: Instant? = null,
) {
    companion object {
        const val IdPrefix = "webcal:"
        const val AccountId = "webcal"
        const val DefaultRefreshMinutes = 60
        val RefreshChoices = listOf(15, 60, 360, 1440)

        fun calendarIdFor(id: String): String = "$IdPrefix$id"

        fun isWebcalCalendarId(calendarId: String): Boolean = calendarId.startsWith(IdPrefix)
    }
}

data class WebcalForm(
    val name: String = "",
    val url: String = "",
    val color: Long = 0xFF5B7FB5,
    val refreshIntervalMinutes: Int = WebcalSubscription.DefaultRefreshMinutes,
    val notifyReminders: Boolean = false,
)
