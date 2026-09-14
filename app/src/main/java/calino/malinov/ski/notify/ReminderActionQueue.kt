package calino.malinov.ski.notify

import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** The buttons a reminder notification offers. */
object ReminderActions {
    const val Snooze = "calino.reminder.SNOOZE"
    const val MarkDone = "calino.reminder.MARK_DONE"
    const val Tomorrow = "calino.reminder.TOMORROW"
    const val ExtraKey = "calino.reminder.KEY"
}

/**
 * A shade action that could not be applied yet.
 *
 * "Mark done" is a CalDAV write, and the receiver that handles it may be
 * running in a process that has no account restored, no snapshot loaded, or no
 * network. The CalDAV write queue cannot hold the intent, because the intent is
 * not yet a write -- the task it refers to has not even been resolved. So it is
 * parked here, and drained when the app next has a repository that knows about
 * the record.
 *
 * Entries expire: applying a three-week-old "mark done" to a task the user has
 * since edited would be worse than dropping it.
 */
data class ReminderAction(
    val action: String,
    val firingKey: String,
    val recordId: String,
    val kind: ReminderKind,
    val requestedAt: Instant,
)

class ReminderActionQueue(
    file: File,
    private val clock: Clock = Clock.systemUTC(),
    private val retention: Duration = Duration.ofDays(2),
    private val maxEntries: Int = 50,
) {

    private val document = AtomicJsonFile(file)
    private val lock = Any()

    fun pending(): List<ReminderAction> = synchronized(lock) { read() }

    fun enqueue(action: ReminderAction) = synchronized(lock) {
        val now = clock.instant()
        // One pending action per firing: pressing "Tomorrow" after "Mark done"
        // means the user changed their mind, not that both should apply.
        val next = (read().filter { it.firingKey != action.firingKey } + action)
            .filter { it.requestedAt.isAfter(now.minus(retention)) }
            .takeLast(maxEntries)
        write(next)
    }

    fun remove(firingKey: String) = synchronized(lock) {
        write(read().filter { it.firingKey != firingKey })
    }

    fun clear() = synchronized(lock) { write(emptyList()) }

    /** Entries still worth applying, oldest first; expired ones are dropped. */
    fun drainable(): List<ReminderAction> = synchronized(lock) {
        val floor = clock.instant().minus(retention)
        val live = read().filter { it.requestedAt.isAfter(floor) }
        write(live)
        live
    }

    private fun read(): List<ReminderAction> {
        val raw = document.read() ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != Version) return emptyList()
            val array = root.optJSONArray("actions") ?: return emptyList()
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::decode)
            }
        }.getOrDefault(emptyList())
    }

    private fun write(actions: List<ReminderAction>) {
        val root = JSONObject()
            .put("version", Version)
            .put("actions", JSONArray().apply { actions.forEach { put(encode(it)) } })
        document.write(root.toString())
    }

    private fun encode(action: ReminderAction): JSONObject = JSONObject()
        .put("action", action.action)
        .put("firingKey", action.firingKey)
        .put("recordId", action.recordId)
        .put("kind", action.kind.name)
        .put("requestedAt", action.requestedAt.toString())

    private fun decode(json: JSONObject): ReminderAction? = runCatching {
        ReminderAction(
            action = json.getString("action"),
            firingKey = json.getString("firingKey"),
            recordId = json.getString("recordId"),
            kind = ReminderKind.valueOf(json.getString("kind")),
            requestedAt = Instant.parse(json.getString("requestedAt")),
        )
    }.getOrNull()

    private companion object {
        const val Version = 1
    }
}
