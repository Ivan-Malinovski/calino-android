package calino.malinov.ski.data.repository

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import calino.malinov.ski.data.model.WebcalSubscription
import calino.malinov.ski.data.webcal.WebcalJson
import java.io.Closeable
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

interface WebcalSubscriptionPersistence {
    fun load(): List<WebcalSubscription>
    fun save(subscriptions: List<WebcalSubscription>)

    object None : WebcalSubscriptionPersistence {
        override fun load(): List<WebcalSubscription> = emptyList()
        override fun save(subscriptions: List<WebcalSubscription>) = Unit
    }
}

class SharedPreferencesWebcalPersistence(context: Context) : WebcalSubscriptionPersistence {
    private val prefs = context.applicationContext
        .getSharedPreferences("calino_webcal_subscriptions", Context.MODE_PRIVATE)

    override fun load(): List<WebcalSubscription> =
        WebcalJson.decode(prefs.getString(Key, null))

    override fun save(subscriptions: List<WebcalSubscription>) {
        prefs.edit().putString(Key, WebcalJson.encode(subscriptions)).apply()
    }

    private companion object { const val Key = "subscriptions" }
}

/**
 * Holder for ICS subscriptions, kept apart from [CalDavAccountStore] so a
 * feed is never mistaken for a CalDAV principal.
 */
class WebcalSubscriptionStore(
    private val persistence: WebcalSubscriptionPersistence = WebcalSubscriptionPersistence.None,
) {
    private val state = mutableStateOf(persistence.load())
    private val listeners = CopyOnWriteArrayList<(List<WebcalSubscription>) -> Unit>()

    fun subscriptions(): List<WebcalSubscription> = state.value

    fun observe(listener: (List<WebcalSubscription>) -> Unit): Closeable {
        listeners += listener
        listener(subscriptions())
        return Closeable { listeners.remove(listener) }
    }

    fun add(
        name: String,
        url: String,
        color: Long,
        refreshIntervalMinutes: Int = WebcalSubscription.DefaultRefreshMinutes,
        notifyReminders: Boolean = false,
    ): WebcalSubscription {
        val id = UUID.randomUUID().toString()
        val subscription = WebcalSubscription(
            id = id,
            calendarId = WebcalSubscription.calendarIdFor(id),
            name = name.trim().ifBlank { "Subscribed calendar" },
            color = color,
            url = url,
            refreshIntervalMinutes = refreshIntervalMinutes.coerceAtLeast(1),
            notifyReminders = notifyReminders,
        )
        update { it + subscription }
        return subscription
    }

    fun remove(id: String) {
        update { it.filterNot { item -> item.id == id } }
    }

    fun markFetched(id: String, at: Instant = Instant.now()) {
        update { list ->
            list.map { if (it.id == id) it.copy(lastFetchedAt = at) else it }
        }
    }

    fun setVisible(id: String, visible: Boolean) {
        update { list -> list.map { if (it.id == id) it.copy(visible = visible) else it } }
    }

    fun setNotifyReminders(id: String, notify: Boolean) {
        update { list -> list.map { if (it.id == id) it.copy(notifyReminders = notify) else it } }
    }

    fun setName(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { list -> list.map { if (it.id == id) it.copy(name = trimmed) else it } }
    }

    fun setNameByCalendarId(calendarId: String, name: String) {
        findByCalendarId(calendarId)?.let { setName(it.id, name) }
    }

    fun setColor(id: String, color: Long) {
        update { list -> list.map { if (it.id == id) it.copy(color = color) else it } }
    }

    fun findByCalendarId(calendarId: String): WebcalSubscription? =
        subscriptions().firstOrNull { it.calendarId == calendarId }

    private fun update(transform: (List<WebcalSubscription>) -> List<WebcalSubscription>) {
        val next = transform(state.value)
        state.value = next
        persistence.save(next)
        listeners.forEach { it(next) }
    }
}
