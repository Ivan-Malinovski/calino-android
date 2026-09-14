package calino.malinov.ski.data.caldav

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

data class CachedAddressBook(
    val addressBookUrl: String,
    val fetchedAt: Instant,
    val resources: List<CardResource>,
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

    /**
     * One resource as the server last sent it.
     *
     * This is what makes an edit a patch rather than a rebuild: [ICalPatcher]
     * needs the original bytes to rewrite only the properties Calino models and
     * leave the rest of somebody else's file alone. The ETag comes back with it
     * so the caller can check the original is still current -- patching a stale
     * copy would resurrect whatever the other client changed in between.
     */
    fun loadResource(calendarUrl: String, href: String): CalendarResource? =
        load(calendarUrl)?.resources?.firstOrNull { it.href == href }

    /**
     * Records a resource Calino has just written, so the next edit can patch it
     * without a refetch. A no-op unless the collection is already cached: a
     * single resource is not a calendar, and inventing an entry from one would
     * make the cache claim a coverage window it never fetched.
     */
    fun saveResource(calendarUrl: String, resource: CalendarResource) = Unit

    /** Forgets one resource, after a delete or a failed write. */
    fun deleteResource(calendarUrl: String, href: String) = Unit

    /** Drops every cached calendar outside [calendarUrls]. */
    fun evictExcept(calendarUrls: Set<String>)

    fun loadAddressBook(addressBookUrl: String): CachedAddressBook? = null
    fun saveAddressBook(entry: CachedAddressBook) = Unit
    fun evictAddressBooksExcept(addressBookUrls: Set<String>) = Unit

    /** Keeps the pre-cache behaviour, for tests and for the fixture path. */
    object None : CalendarCache {
        override fun load(calendarUrl: String): CachedCalendar? = null
        override fun save(entry: CachedCalendar) = Unit
        override fun evictExcept(calendarUrls: Set<String>) = Unit
        override fun loadResource(calendarUrl: String, href: String): CalendarResource? = null
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

    /**
     * The repository can refresh while a write updates one resource. The
     * resource methods are read-modify-write operations, so serialize the
     * whole file transaction; otherwise two concurrent saves can lose a
     * sibling or restore an older ETag.
     */
    @Synchronized
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

    @Synchronized
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

    /**
     * Rewrites one resource inside its collection entry.
     *
     * A read-modify-write of the whole collection file rather than a per-href
     * store of its own: the bytes are already here, and a second copy would
     * drift from this one the moment a refetch replaced only one of them.
     */
    @Synchronized
    override fun saveResource(calendarUrl: String, resource: CalendarResource) {
        val entry = load(calendarUrl) ?: return
        val without = entry.resources.filterNot { it.href == resource.href }
        save(entry.copy(resources = without + resource))
    }

    @Synchronized
    override fun deleteResource(calendarUrl: String, href: String) {
        val entry = load(calendarUrl) ?: return
        val without = entry.resources.filterNot { it.href == href }
        if (without.size == entry.resources.size) return
        save(entry.copy(resources = without))
    }

    @Synchronized
    override fun evictExcept(calendarUrls: Set<String>) {
        val keep = calendarUrls.map(::fileName).toSet()
        root.listFiles()?.forEach { file ->
            // Calendar and address-book entries share the private directory.
            // Calendar eviction must not erase contact caches before the
            // address-book eviction pass gets a chance to filter them.
            if (!file.name.startsWith(AddressBookPrefix) && file.name !in keep) file.delete()
        }
    }

    @Synchronized
    override fun loadAddressBook(addressBookUrl: String): CachedAddressBook? {
        val file = addressBookFileFor(addressBookUrl)
        if (!file.isFile) return null
        return runCatching {
            val raw = GZIPInputStream(file.inputStream().buffered()).use { it.readBytes() }
            CalendarCacheJson.decodeAddressBook(raw.toString(Charsets.UTF_8))
        }.getOrNull()
    }

    @Synchronized
    override fun saveAddressBook(entry: CachedAddressBook) {
        val kept = entry.resources.filter { it.vcf.length <= MaxResourceChars }
        val encoded = CalendarCacheJson.encodeAddressBook(entry.copy(resources = kept))
        if (encoded.length > MaxPayloadChars) return
        runCatching {
            root.mkdirs()
            val file = addressBookFileFor(entry.addressBookUrl)
            val temp = File(file.parentFile, file.name + ".tmp")
            GZIPOutputStream(temp.outputStream().buffered()).use {
                it.write(encoded.toByteArray(Charsets.UTF_8))
            }
            if (!temp.renameTo(file)) temp.delete()
        }
    }

    @Synchronized
    override fun evictAddressBooksExcept(addressBookUrls: Set<String>) {
        val keep = addressBookUrls.map(::addressBookFileName).toSet()
        root.listFiles()?.forEach { file ->
            if (file.name.startsWith(AddressBookPrefix) && file.name !in keep) file.delete()
        }
    }

    private fun fileFor(calendarUrl: String) = File(root, fileName(calendarUrl))
    private fun addressBookFileFor(url: String) = File(root, addressBookFileName(url))

    private companion object {
        const val MaxResourceChars = 1_000_000
        const val MaxPayloadChars = 8_000_000
        const val AddressBookPrefix = "addressbook-"

        /** Calendar URLs contain slashes, so the digest is the file name. */
        fun fileName(calendarUrl: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(calendarUrl.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) } + ".json.gz"
        }

        fun addressBookFileName(url: String): String = AddressBookPrefix + fileName(url)
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

    fun encodeAddressBook(entry: CachedAddressBook): String {
        val resources = JSONArray()
        entry.resources.forEach { resource ->
            resources.put(
                JSONObject().put("href", resource.href)
                    .put("etag", resource.etag ?: JSONObject.NULL)
                    .put("vcf", resource.vcf),
            )
        }
        return JSONObject()
            .put("version", Version)
            .put("addressBookUrl", entry.addressBookUrl)
            .put("fetchedAt", entry.fetchedAt.toString())
            .put("resources", resources)
            .toString()
    }

    fun decodeAddressBook(raw: String?): CachedAddressBook? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version", -1) != Version) return null
            val url = json.optString("addressBookUrl").takeIf { it.isNotEmpty() } ?: return null
            val resourcesJson = json.optJSONArray("resources") ?: JSONArray()
            val resources = (0 until resourcesJson.length()).mapNotNull { index ->
                resourcesJson.optJSONObject(index)?.let { resource ->
                    val href = resource.optString("href").takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val vcf = resource.optString("vcf").takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    CardResource(href, normalizeEtag(resource.optString("etag")), vcf)
                }
            }
            CachedAddressBook(url, Instant.parse(json.getString("fetchedAt")), resources)
        }.getOrNull()
    }
}
