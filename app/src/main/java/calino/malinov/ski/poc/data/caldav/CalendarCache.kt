package calino.malinov.ski.poc.data.caldav

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * One calendar's last successful read, as the server sent it.
 *
 * The window is recorded because the event query is time-ranged: only
 * resources overlapping [windowStart]..[windowEnd] were ever asked for. A
 * recurring series re-expands correctly for any later window -- the master
 * carries the whole rule -- but a one-off event outside the cached window was
 * never fetched and cannot appear until a refresh succeeds.
 */
data class CachedCalendar(
    val calendarUrl: String,
    val fetchedAt: Instant,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val resources: List<CalendarResource>,
)

/**
 * Where a calendar's fetched resources are kept between launches.
 *
 * This is the only place calendar *content* is written to disk. It holds no
 * credentials: the password stays in [CredentialStore], encrypted, and the
 * account list stays in [CalDavAccountJson]. Everything here is text the
 * server already returned, in the app's private storage.
 */
interface CalendarCache {
    fun load(calendarUrl: String): CachedCalendar?
    fun save(entry: CachedCalendar)

    /** Drops every cached calendar outside [calendarUrls]. */
    fun evictExcept(calendarUrls: Set<String>)

    /** Keeps the pre-cache behaviour, for tests and for the fixture path. */
    object None : CalendarCache {
        override fun load(calendarUrl: String): CachedCalendar? = null
        override fun save(entry: CachedCalendar) = Unit
        override fun evictExcept(calendarUrls: Set<String>) = Unit
    }
}

/**
 * A [CalendarCache] over one gzipped JSON file per calendar.
 *
 * Takes a plain [File] rather than a `Context` so it runs in a JVM unit test
 * against a temp directory. iCalendar text compresses hard, which matters
 * because a busy collection is a few hundred kilobytes of it.
 */
class FileCalendarCache(private val root: File) : CalendarCache {

    override fun load(calendarUrl: String): CachedCalendar? {
        val file = fileFor(calendarUrl)
        if (!file.isFile) return null
        // A cache that will not read is not an error worth surfacing: the
        // refresh already under way replaces it. Failing loudly here would
        // turn a corrupt file into a crash on launch.
        return runCatching {
            val raw = GZIPInputStream(file.inputStream().buffered()).use { it.readBytes() }
            CalendarCacheJson.decode(raw.toString(Charsets.UTF_8))
        }.getOrNull()
    }

    override fun save(entry: CachedCalendar) {
        // Refuse rather than let a pathological collection grow the app's data
        // directory without bound. Skipping the write costs a refetch next
        // launch; the alternative has no ceiling.
        val kept = entry.resources.filter { it.ics.length <= MaxResourceChars }
        val encoded = CalendarCacheJson.encode(entry.copy(resources = kept))
        if (encoded.length > MaxPayloadChars) return

        runCatching {
            root.mkdirs()
            val file = fileFor(entry.calendarUrl)
            val temp = File(file.parentFile, file.name + ".tmp")
            GZIPOutputStream(temp.outputStream().buffered()).use {
                it.write(encoded.toByteArray(Charsets.UTF_8))
            }
            // Written aside and renamed, so a kill mid-write leaves the
            // previous copy intact rather than a truncated one.
            if (!temp.renameTo(file)) {
                temp.delete()
            }
        }
    }

    override fun evictExcept(calendarUrls: Set<String>) {
        val keep = calendarUrls.map(::fileName).toSet()
        root.listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
    }

    private fun fileFor(calendarUrl: String) = File(root, fileName(calendarUrl))

    private companion object {
        const val MaxResourceChars = 1_000_000
        const val MaxPayloadChars = 8_000_000

        /** Calendar URLs contain slashes, so the digest is the file name. */
        fun fileName(calendarUrl: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(calendarUrl.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) } + ".json.gz"
        }
    }
}

/**
 * JSON encoding for a cached calendar.
 *
 * Pure functions, so the round trip is unit-testable, and tolerant on the way
 * in for the same reason [CalDavAccountJson] is: an unreadable payload yields
 * null and the calendar refetches, rather than crashing on launch.
 */
object CalendarCacheJson {

    /** Bumped whenever the shape changes; an older file is discarded, not migrated. */
    const val Version = 1

    fun encode(entry: CachedCalendar): String {
        val resources = JSONArray()
        entry.resources.forEach { resource ->
            resources.put(
                JSONObject()
                    .put("href", resource.href)
                    .put("etag", resource.etag ?: JSONObject.NULL)
                    .put("ics", resource.ics),
            )
        }
        return JSONObject()
            .put("version", Version)
            .put("calendarUrl", entry.calendarUrl)
            .put("fetchedAt", entry.fetchedAt.toString())
            .put("windowStart", entry.windowStart.toString())
            .put("windowEnd", entry.windowEnd.toString())
            .put("resources", resources)
            .toString()
    }

    fun decode(raw: String?): CachedCalendar? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version", -1) != Version) return null
            val url = json.optString("calendarUrl").takeIf { it.isNotEmpty() } ?: return null
            val resourcesJson = json.optJSONArray("resources") ?: JSONArray()
            val resources = (0 until resourcesJson.length()).mapNotNull { index ->
                resourcesJson.optJSONObject(index)?.let { resource ->
                    val href = resource.optString("href").takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val ics = resource.optString("ics").takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    CalendarResource(
                        href = href,
                        etag = resource.optString("etag").takeIf { it.isNotEmpty() },
                        ics = ics,
                    )
                }
            }
            CachedCalendar(
                calendarUrl = url,
                fetchedAt = Instant.parse(json.getString("fetchedAt")),
                windowStart = LocalDate.parse(json.getString("windowStart")),
                windowEnd = LocalDate.parse(json.getString("windowEnd")),
                resources = resources,
            )
        }.getOrNull()
    }
}
