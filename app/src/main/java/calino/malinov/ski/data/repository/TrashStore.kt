package calino.malinov.ski.data.repository

import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

enum class TrashKind { Event, Task, Journal, Contact }

/**
 * One deleted resource, held on the device so a delete can be taken back.
 *
 * [body] is the resource exactly as the server last sent it (iCalendar or
 * vCard text), not a rebuild from Calino's model, so properties Calino does not
 * edit come back untouched. A scoped delete of a series (one occurrence, or
 * this and following) rewrites a surviving resource; restoring then writes
 * [body] over it, and a whole removal is recreated instead.
 */
data class TrashEntry(
    val id: String,
    val kind: TrashKind,
    val title: String,
    val accountId: String,
    val collectionUrl: String,
    val href: String,
    val uid: String,
    /** Ids the deleted records had in the snapshot, so their tombstones can be lifted. */
    val recordIds: List<String>,
    val body: String,
    val deletedAt: Instant,
)

/**
 * The on-device trash behind Undo and Settings > Recently deleted.
 *
 * Local only: nothing here is uploaded. It holds text the server already
 * returned for the account, so it is cleared with the account.
 */
interface TrashStore {
    fun list(): List<TrashEntry>
    fun get(id: String): TrashEntry?
    fun add(entry: TrashEntry)
    fun remove(id: String)
    fun clear()
    fun removeAccountsExcept(accountIds: Set<String>)

    companion object {
        val Retention: Duration = Duration.ofDays(30)
        const val MaxEntries = 200
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/** Keeps entries in memory only: the fixture path, and tests. */
class InMemoryTrashStore(private val now: () -> Instant = Instant::now) : TrashStore {
    private val entries = ArrayList<TrashEntry>()

    @Synchronized override fun list(): List<TrashEntry> = pruned().asReversed()

    @Synchronized override fun get(id: String): TrashEntry? = pruned().firstOrNull { it.id == id }

    @Synchronized override fun add(entry: TrashEntry) {
        entries.removeAll { it.id == entry.id }
        entries += entry
        pruned()
    }

    @Synchronized override fun remove(id: String) {
        entries.removeAll { it.id == id }
    }

    @Synchronized override fun clear() = entries.clear()

    @Synchronized override fun removeAccountsExcept(accountIds: Set<String>) {
        entries.removeAll { it.accountId !in accountIds }
    }

    private fun pruned(): List<TrashEntry> {
        entries.removeAll { TrashPolicy.expired(it, now()) }
        while (entries.size > TrashStore.MaxEntries) entries.removeAt(0)
        return entries
    }
}

/**
 * A [TrashStore] over one gzipped JSON file in the app's private storage, in
 * the style of [calino.malinov.ski.data.caldav.FileCalendarCache]. Takes a
 * plain [File] so it runs in a JVM test against a temp directory.
 */
class FileTrashStore(
    private val file: File,
    private val now: () -> Instant = Instant::now,
) : TrashStore {
    private var entries: MutableList<TrashEntry> = load().toMutableList()

    @Synchronized override fun list(): List<TrashEntry> {
        prune()
        return entries.asReversed().toList()
    }

    @Synchronized override fun get(id: String): TrashEntry? {
        prune()
        return entries.firstOrNull { it.id == id }
    }

    @Synchronized override fun add(entry: TrashEntry) {
        entries.removeAll { it.id == entry.id }
        entries += entry
        prune(force = true)
    }

    @Synchronized override fun remove(id: String) {
        if (entries.removeAll { it.id == id }) save()
    }

    @Synchronized override fun clear() {
        entries.clear()
        file.delete()
    }

    @Synchronized override fun removeAccountsExcept(accountIds: Set<String>) {
        if (entries.removeAll { it.accountId !in accountIds }) save()
    }

    private fun prune(force: Boolean = false) {
        val before = entries.size
        entries.removeAll { TrashPolicy.expired(it, now()) }
        while (entries.size > TrashStore.MaxEntries) entries.removeAt(0)
        if (force || entries.size != before) save()
    }

    private fun save() {
        runCatching {
            if (entries.isEmpty()) {
                file.delete()
                return
            }
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            GZIPOutputStream(temp.outputStream().buffered()).use {
                it.write(encode(entries).toByteArray(Charsets.UTF_8))
            }
            // Written aside and renamed, so a kill mid-write keeps the previous copy.
            if (!temp.renameTo(file)) temp.delete()
        }
    }

    private fun load(): List<TrashEntry> {
        if (!file.isFile) return emptyList()
        // An unreadable trash is not worth a crash on launch; it starts empty.
        return runCatching {
            val raw = GZIPInputStream(file.inputStream().buffered()).use { it.readBytes() }
            decode(raw.toString(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    private fun encode(list: List<TrashEntry>): String = JSONObject()
        .put("version", 1)
        .put(
            "entries",
            JSONArray().also { array ->
                list.forEach { entry ->
                    array.put(
                        JSONObject()
                            .put("id", entry.id)
                            .put("kind", entry.kind.name)
                            .put("title", entry.title)
                            .put("accountId", entry.accountId)
                            .put("collectionUrl", entry.collectionUrl)
                            .put("href", entry.href)
                            .put("uid", entry.uid)
                            .put("recordIds", JSONArray(entry.recordIds))
                            .put("body", entry.body)
                            .put("deletedAt", entry.deletedAt.toString()),
                    )
                }
            },
        )
        .toString()

    private fun decode(text: String): List<TrashEntry> {
        val array = JSONObject(text).getJSONArray("entries")
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val o = array.getJSONObject(index)
                val ids = o.getJSONArray("recordIds")
                TrashEntry(
                    id = o.getString("id"),
                    kind = TrashKind.valueOf(o.getString("kind")),
                    title = o.getString("title"),
                    accountId = o.getString("accountId"),
                    collectionUrl = o.getString("collectionUrl"),
                    href = o.getString("href"),
                    uid = o.getString("uid"),
                    recordIds = (0 until ids.length()).map(ids::getString),
                    body = o.getString("body"),
                    deletedAt = Instant.parse(o.getString("deletedAt")),
                )
            }.getOrNull()
        }
    }
}

internal object TrashPolicy {
    fun expired(entry: TrashEntry, now: Instant): Boolean =
        entry.deletedAt.plus(TrashStore.Retention).isBefore(now)
}
