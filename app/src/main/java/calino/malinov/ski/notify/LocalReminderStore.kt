package calino.malinov.ski.notify

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.Reminder
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reminders the person set on events Calino cannot write to.
 *
 * A subscribed feed, a read-only CalDAV collection or an imported device
 * calendar will not take a `VALARM`, but a reminder is only about when this
 * phone speaks up -- it never needs the event itself to change. So these live
 * here, on the device, and never enter the write queue or the cache.
 *
 * Keyed by calendar and series UID, so a reminder set on one occurrence of a
 * repeating event covers the series, and survives the re-expansion that gives
 * every occurrence a new id. An entry replaces the event's own reminders
 * rather than adding to them: the row in the card shows one answer.
 */
class LocalReminderStore(private val file: File) {

    private val json = AtomicJsonFile(file)
    private val listeners = mutableSetOf<() -> Unit>()

    @Volatile
    private var entries: Map<String, List<Reminder>> = load()

    fun all(): Map<String, List<Reminder>> = entries

    fun reminders(event: CalEvent): List<Reminder>? = entries[localReminderKey(event)]

    /** An empty list clears the entry, handing the event its own reminders back. */
    @Synchronized
    fun set(event: CalEvent, reminders: List<Reminder>) {
        val key = localReminderKey(event)
        val next = if (reminders.isEmpty()) entries - key else entries + (key to reminders)
        if (next == entries) return
        entries = next
        json.write(encode(next))
        listeners.toList().forEach { it() }
    }

    fun observe(listener: () -> Unit): AutoCloseable {
        synchronized(this) { listeners += listener }
        return AutoCloseable { synchronized(this) { listeners -= listener } }
    }

    private fun load(): Map<String, List<Reminder>> = try {
        json.read()?.let(::decode).orEmpty()
    } catch (_: Exception) {
        emptyMap()
    }

    companion object {
        internal fun encode(entries: Map<String, List<Reminder>>): String {
            val root = JSONObject()
            entries.forEach { (key, reminders) ->
                root.put(key, JSONArray().apply { reminders.forEach { put(it.minutesBefore) } })
            }
            return root.toString()
        }

        internal fun decode(text: String): Map<String, List<Reminder>> {
            val root = JSONObject(text)
            return root.keys().asSequence().associateWith { key ->
                val minutes = root.getJSONArray(key)
                (0 until minutes.length()).map { Reminder(minutesBefore = minutes.getInt(it)) }
            }.filterValues { it.isNotEmpty() }
        }
    }
}

fun localReminderKey(event: CalEvent): String = "${event.calendarId}\n${event.uid ?: event.id}"
