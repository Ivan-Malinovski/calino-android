package calino.malinov.ski.data.webcal

import calino.malinov.ski.data.caldav.ICalMapper
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.WebcalForm
import calino.malinov.ski.data.model.WebcalSubscription
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.WebcalOverlay
import calino.malinov.ski.data.repository.WebcalSubscriptionStore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fetches ICS feeds and publishes them as read-only calendars on the live
 * repository. Cache first, network second: a cold start should paint last
 * time's events without waiting on Google.
 *
 * Parsed events are kept in memory. Name, colour, visibility and mute only
 * rewrite the calendar list (and remap event colours). Re-parsing a Google
 * dump on the UI thread is what froze the sidebar on Save.
 */
class WebcalManager(
    private val store: WebcalSubscriptionStore,
    private val repository: CalDavRepository,
    private val fetcher: WebcalFetcher,
    private val cache: WebcalCache,
    private val scope: CoroutineScope,
    private val mapper: ICalMapper = ICalMapper(),
    private val today: () -> LocalDate = { LocalDate.now() },
    private val windowMonths: Long = 24L,
) {
    /** Last successful parse per subscription id. */
    private var parsedById: Map<String, List<CalEvent>> = emptyMap()

    fun restoreFromCache() {
        republishParsed()
    }

    fun restore() {
        restoreFromCache()
        scope.launch { syncDue() }
    }

    suspend fun add(form: WebcalForm): WebcalSubscription {
        val url = WebcalUrl.requireHttpUrl(form.url).toString()
        val name = form.name.trim().ifBlank { WebcalUrl.hostOf(url) }
        val ics = fetcher.fetchIcs(url)
        // Validate before storing a subscription so a wholly unreadable feed
        // cannot be accepted and then displayed as an empty calendar.
        parseEvents("feed-preview", form.color, ics)
        val subscription = store.add(
            name = name,
            url = url,
            color = form.color,
            refreshIntervalMinutes = form.refreshIntervalMinutes,
            notifyReminders = form.notifyReminders,
        )
        val events = parseEvents(subscription.calendarId, subscription.color, ics)
        cache.save(subscription.id, ics)
        store.markFetched(subscription.id)
        putParsed(subscription, events)
        return subscription
    }

    fun remove(id: String) {
        cache.delete(id)
        store.remove(id)
        parsedById = parsedById - id
        publishMetadata()
    }

    suspend fun sync(id: String) {
        val subscription = store.subscriptions().firstOrNull { it.id == id } ?: return
        val ics = fetcher.fetchIcs(subscription.url)
        val events = parseEvents(subscription.calendarId, subscription.color, ics)
        cache.save(subscription.id, ics)
        store.markFetched(subscription.id)
        putParsed(subscription, events)
    }

    suspend fun syncDue() {
        store.subscriptions().filter(::isDue).forEach { subscription ->
            runCatching { sync(subscription.id) }
        }
    }

    suspend fun syncAll() {
        store.subscriptions().forEach { subscription ->
            runCatching { sync(subscription.id) }
        }
    }

    fun setVisible(id: String, visible: Boolean) {
        store.setVisible(id, visible)
        publishMetadata()
    }

    fun setNotifyReminders(id: String, notify: Boolean) {
        store.setNotifyReminders(id, notify)
        publishMetadata()
    }

    fun setName(id: String, name: String) {
        store.setName(id, name)
        publishMetadata()
    }

    fun setNameByCalendarId(calendarId: String, name: String) {
        store.setNameByCalendarId(calendarId, name)
        publishMetadata()
    }

    fun setColor(id: String, color: Long) {
        store.setColor(id, color)
        publishMetadata()
    }

    private fun isDue(subscription: WebcalSubscription): Boolean {
        val last = subscription.lastFetchedAt ?: return true
        val interval = subscription.refreshIntervalMinutes.coerceAtLeast(1)
        return Instant.now().isAfter(last.plusSeconds(interval * 60L))
    }

    private fun putParsed(subscription: WebcalSubscription, events: List<CalEvent>) {
        parsedById = parsedById + (subscription.id to events)
        publishMetadata()
    }

    private fun republishParsed() {
        val parsed = mutableMapOf<String, List<CalEvent>>()
        store.subscriptions().forEach { subscription ->
            val ics = cache.load(subscription.id) ?: return@forEach
            parsed[subscription.id] = runCatching {
                parseEvents(subscription.calendarId, subscription.color, ics)
            }.getOrNull() ?: parsedById[subscription.id].orEmpty()
        }
        parsedById = parsed
        publishMetadata()
    }

    private fun parseEvents(calendarId: String, color: Long, ics: String): List<CalEvent> {
        val windowStart = today().minusMonths(windowMonths)
        val windowEnd = today().plusMonths(windowMonths)
        return mapper.parseFeedEvents(ics, calendarId, color, windowStart, windowEnd)
    }

    private fun publishMetadata() {
        repository.setWebcalOverlay(
            WebcalOverlay(
                calendars = webcalCalendars(store.subscriptions()),
                events = webcalEvents(store.subscriptions(), parsedById),
            ),
        )
    }
}

internal fun webcalCalendars(subscriptions: List<WebcalSubscription>): List<CalinoCalendar> =
    subscriptions.map { subscription ->
        CalinoCalendar(
            id = subscription.calendarId,
            name = subscription.name,
            color = subscription.color,
            readOnly = true,
            components = setOf("VEVENT"),
            visible = subscription.visible,
            showTasksInViews = false,
            notifyReminders = subscription.notifyReminders,
        )
    }

/** Reuses the last parse; only colour is rewritten when the subscription colour changed. */
internal fun webcalEvents(
    subscriptions: List<WebcalSubscription>,
    parsedById: Map<String, List<CalEvent>>,
): List<CalEvent> {
    val byId = subscriptions.associateBy { it.id }
    return subscriptions.flatMap { subscription ->
        val events = parsedById[subscription.id] ?: return@flatMap emptyList()
        if (events.firstOrNull()?.color == subscription.color) events
        else events.map { it.copy(color = subscription.color) }
    }.filter { event ->
        byId.values.any { it.calendarId == event.calendarId }
    }
}
