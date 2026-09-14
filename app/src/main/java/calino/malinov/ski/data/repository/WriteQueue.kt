package calino.malinov.ski.data.repository

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * The operations that can be replayed by a later CalDAV integration.
 *
 * MOVE and DELETE_HREF intentionally do not collapse into UPDATE and DELETE:
 * they carry different source/target semantics and are replayed differently.
 */
enum class PendingChangeType {
    CREATE,
    UPDATE,
    DELETE,
    MOVE,
    DELETE_HREF,
}

/** Durable lifecycle state for a queued write. */
enum class PendingChangeState {
    PENDING,
    RETRY,
    DEAD_LETTER,
}

/** A safe diagnostic attached to a retry or dead-lettered change. */
data class PendingChangeFailure(
    val message: String,
    val statusCode: Int? = null,
    val retryAfterSeconds: Long? = null,
    val at: Instant = Instant.now(),
)

/**
 * The durable unit of work.
 *
 * `data` is deliberately an opaque protocol payload. For calendar writes it
 * can contain raw iCalendar text; DELETE operations can leave it null. The
 * optional `baseData` is the raw resource the payload was built from. It lets
 * a later replay rebase local fields onto a newer server resource after a
 * stale ETag, instead of sending an old full-resource snapshot unchanged.
 * DELETE operations do not use it: their conditional cleanup deliberately
 * refuses to delete a resource that changed underneath them.
 * model has no credentials field and the store never receives a password.
 * `calendarUrl` and the source URL are optional hints for the replay layer;
 * stable account/calendar identifiers remain available when discovery must
 * resolve the URL again.
 */
data class PendingChange(
    val id: String,
    val type: PendingChangeType,
    val eventId: String,
    val accountId: String,
    val calendarId: String,
    val component: String,
    val calendarUrl: String? = null,
    val uid: String? = null,
    val href: String? = null,
    val etag: String? = null,
    val data: String? = null,
    val baseData: String? = null,
    val sourceAccountId: String? = null,
    val sourceCalendarId: String? = null,
    val sourceCalendarUrl: String? = null,
    val sourceHref: String? = null,
    val sourceEtag: String? = null,
    val sourceData: String? = null,
    val timestamp: Instant,
    val updatedAt: Instant,
    val retryCount: Int = 0,
    val attemptCount: Int = 0,
    val state: PendingChangeState = PendingChangeState.PENDING,
    val nextAttemptAt: Instant? = null,
    val lastFailure: PendingChangeFailure? = null,
) {
    /** Alias used by callers that describe the resource as a payload. */
    val payload: String?
        get() = data

    /** Alias used by the Kotlin CalDAV code, where a resource is called href. */
    val resourceHref: String?
        get() = href
}

/** Fields supplied by a writer when it needs to enqueue a change. */
data class PendingChangeRequest(
    val type: PendingChangeType,
    val eventId: String,
    val accountId: String,
    val calendarId: String,
    val component: String,
    val calendarUrl: String? = null,
    val uid: String? = null,
    val href: String? = null,
    val etag: String? = null,
    val data: String? = null,
    val baseData: String? = null,
    val sourceAccountId: String? = null,
    val sourceCalendarId: String? = null,
    val sourceCalendarUrl: String? = null,
    val sourceHref: String? = null,
    val sourceEtag: String? = null,
    val sourceData: String? = null,
)

/** Result of an enqueue attempt when the configured bound has been reached. */
sealed interface PendingChangeEnqueueResult {
    data class Enqueued(val change: PendingChange) : PendingChangeEnqueueResult

    data class Rejected(val reason: String) : PendingChangeEnqueueResult
}

/**
 * Minimal persistence/replay contract for the future CalDAV writer.
 *
 * Implementations must keep the returned lists in queue order. [ready] is
 * deliberately FIFO: a delayed or dead-lettered head blocks later writes so a
 * replay cannot silently apply operations out of order. A caller can resolve a
 * dead letter with [requeue] or [discard].
 */
interface PendingChangeStore {
    fun snapshot(): List<PendingChange>

    fun pending(): List<PendingChange>

    fun retrying(): List<PendingChange>

    fun deadLetters(): List<PendingChange>

    /** Returns the ready FIFO prefix, stopping at the first blocked record. */
    fun ready(now: Instant): List<PendingChange>

    fun enqueue(
        request: PendingChangeRequest,
        now: Instant = Instant.now(),
    ): PendingChangeEnqueueResult

    /** Inserts dependent work immediately before an existing queue record. */
    fun enqueueBefore(
        beforeId: String,
        request: PendingChangeRequest,
        now: Instant = Instant.now(),
    ): PendingChangeEnqueueResult

    /**
     * Replaces the payload of a queued CREATE while keeping its FIFO slot.
     * An edit made before that create reaches the server must update the one
     * create operation rather than become an UPDATE with no server ETag.
     */
    fun replaceCreatePayload(
        id: String,
        data: String,
        href: String,
        now: Instant = Instant.now(),
    ): PendingChange?

    /**
     * Records a failed attempt. Uncounted failures retain [retryCount], while
     * counted failures increment it and become dead letters at the retry cap.
     */
    fun markRetry(
        id: String,
        failure: PendingChangeFailure,
        counted: Boolean = true,
        now: Instant = Instant.now(),
    ): PendingChange?

    fun markDeadLetter(
        id: String,
        failure: PendingChangeFailure,
        now: Instant = Instant.now(),
    ): PendingChange?

    /** Removes a successfully applied change. */
    fun acknowledge(id: String): Boolean

    /** Explicitly removes a dead-lettered change after user resolution. */
    fun discard(id: String): Boolean

    /** Makes a dead letter eligible again without losing its queue position. */
    fun requeue(id: String, now: Instant = Instant.now()): PendingChange?
}

/** Short names for integration code that prefers to speak in queue terms. */
typealias WriteQueue = PendingChangeStore
typealias FileWriteQueue = FilePendingChangeStore

/** A failure while replacing the durable queue file. */
class PendingChangeStoreException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

const val PENDING_CHANGE_MAX_RETRIES: Int = 10
const val PENDING_CHANGE_BACKOFF_BASE_MS: Long = 30_000L
const val PENDING_CHANGE_BACKOFF_CAP_MS: Long = 30L * 60L * 1_000L

/**
 * Port of the write handoff's retry schedule. The retry count is the count
 * before the next counted attempt, so zero means 30 seconds. Retry-After is a
 * lower bound, never a replacement for the exponential schedule.
 */
fun backoffDelayMs(retryCount: Int, retryAfterSeconds: Long? = null): Long {
    val safeCount = retryCount.coerceAtLeast(0)
    var exponential = PENDING_CHANGE_BACKOFF_BASE_MS
    repeat(minOf(safeCount, 6)) {
        exponential = (exponential * 2L).coerceAtMost(PENDING_CHANGE_BACKOFF_CAP_MS)
    }
    if (retryAfterSeconds == null || retryAfterSeconds <= 0L) return exponential

    val retryAfterMs = if (retryAfterSeconds > Long.MAX_VALUE / 1_000L) {
        Long.MAX_VALUE
    } else {
        retryAfterSeconds * 1_000L
    }
    return maxOf(exponential, retryAfterMs)
}

/**
 * File-backed queue using a versioned JSON document and same-directory atomic
 * replacement. Every mutation writes and fsyncs a temporary file before the
 * rename, so a process death leaves either the previous complete document or
 * the next complete document, never a partially written JSON file.
 *
 * This class is intentionally synchronous. The existing repository already
 * owns its IO coroutine scope; callers can invoke it there without making the
 * queue itself depend on Android lifecycle or WorkManager APIs.
 */
class FilePendingChangeStore(
    private val file: File,
    private val clock: Clock = Clock.systemUTC(),
    private val maxEntries: Int = 100,
    private val maxRetries: Int = PENDING_CHANGE_MAX_RETRIES,
) : PendingChangeStore {

    private val lock = Any()
    private var entries: List<PendingChange>

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxRetries > 0) { "maxRetries must be positive" }
        entries = loadEntries()
    }

    override fun snapshot(): List<PendingChange> = synchronized(lock) { entries.toList() }

    override fun pending(): List<PendingChange> = synchronized(lock) {
        entries.filter { it.state != PendingChangeState.DEAD_LETTER }
    }

    override fun retrying(): List<PendingChange> = synchronized(lock) {
        entries.filter { it.state == PendingChangeState.RETRY }
    }

    override fun deadLetters(): List<PendingChange> = synchronized(lock) {
        entries.filter { it.state == PendingChangeState.DEAD_LETTER }
    }

    override fun ready(now: Instant): List<PendingChange> = synchronized(lock) {
        val ready = ArrayList<PendingChange>()
        for (entry in entries) {
            when (entry.state) {
                PendingChangeState.PENDING -> ready += entry
                PendingChangeState.RETRY -> {
                    if (entry.nextAttemptAt != null && !entry.nextAttemptAt.isAfter(now)) {
                        ready += entry
                    } else {
                        break
                    }
                }
                PendingChangeState.DEAD_LETTER -> break
            }
        }
        ready
    }

    override fun enqueue(
        request: PendingChangeRequest,
        now: Instant,
    ): PendingChangeEnqueueResult = synchronized(lock) {
        validateRequest(request)
        if (entries.size >= maxEntries) {
            return@synchronized PendingChangeEnqueueResult.Rejected(
                "The pending write queue is full (maximum $maxEntries entries).",
            )
        }

        val id = nextId(entries)
        val change = PendingChange(
            id = id,
            type = request.type,
            eventId = request.eventId.trim(),
            accountId = request.accountId.trim(),
            calendarId = request.calendarId.trim(),
            component = request.component.trim().uppercase(Locale.US),
            calendarUrl = request.calendarUrl?.let(::safeUrl),
            uid = request.uid?.trim()?.takeIf { it.isNotEmpty() },
            href = request.href?.trim()?.takeIf { it.isNotEmpty() },
            etag = request.etag?.trim()?.takeIf { it.isNotEmpty() },
            data = request.data,
            baseData = request.baseData,
            sourceAccountId = request.sourceAccountId?.trim()?.takeIf { it.isNotEmpty() },
            sourceCalendarId = request.sourceCalendarId?.trim()?.takeIf { it.isNotEmpty() },
            sourceCalendarUrl = request.sourceCalendarUrl?.let(::safeUrl),
            sourceHref = request.sourceHref?.trim()?.takeIf { it.isNotEmpty() },
            sourceEtag = request.sourceEtag?.trim()?.takeIf { it.isNotEmpty() },
            sourceData = request.sourceData,
            timestamp = now,
            updatedAt = now,
        )
        replaceEntries(entries + change)
        PendingChangeEnqueueResult.Enqueued(change)
    }

    override fun enqueueBefore(
        beforeId: String,
        request: PendingChangeRequest,
        now: Instant,
    ): PendingChangeEnqueueResult = synchronized(lock) {
        validateRequest(request)
        if (entries.size >= maxEntries) {
            return@synchronized PendingChangeEnqueueResult.Rejected(
                "The pending write queue is full (maximum $maxEntries entries).",
            )
        }
        val index = entries.indexOfFirst { it.id == beforeId }
        if (index == -1) {
            return@synchronized PendingChangeEnqueueResult.Rejected(
                "The queue dependency no longer exists.",
            )
        }
        val id = nextId(entries)
        val change = PendingChange(
            id = id,
            type = request.type,
            eventId = request.eventId.trim(),
            accountId = request.accountId.trim(),
            calendarId = request.calendarId.trim(),
            component = request.component.trim().uppercase(Locale.US),
            calendarUrl = request.calendarUrl?.let(::safeUrl),
            uid = request.uid?.trim()?.takeIf { it.isNotEmpty() },
            href = request.href?.trim()?.takeIf { it.isNotEmpty() },
            etag = request.etag?.trim()?.takeIf { it.isNotEmpty() },
            data = request.data,
            baseData = request.baseData,
            sourceAccountId = request.sourceAccountId?.trim()?.takeIf { it.isNotEmpty() },
            sourceCalendarId = request.sourceCalendarId?.trim()?.takeIf { it.isNotEmpty() },
            sourceCalendarUrl = request.sourceCalendarUrl?.let(::safeUrl),
            sourceHref = request.sourceHref?.trim()?.takeIf { it.isNotEmpty() },
            sourceEtag = request.sourceEtag?.trim()?.takeIf { it.isNotEmpty() },
            sourceData = request.sourceData,
            timestamp = now,
            updatedAt = now,
        )
        replaceEntries(entries.toMutableList().also { it.add(index, change) })
        PendingChangeEnqueueResult.Enqueued(change)
    }

    override fun replaceCreatePayload(
        id: String,
        data: String,
        href: String,
        now: Instant,
    ): PendingChange? = synchronized(lock) {
        require(data.isNotBlank()) { "A queued create needs a payload" }
        validateUrl(href)
        rejectCredentialLikePayload(data)
        val index = entries.indexOfFirst { it.id == id }
        if (index == -1) return@synchronized null
        val current = entries[index]
        if (current.type != PendingChangeType.CREATE || current.state == PendingChangeState.DEAD_LETTER) {
            return@synchronized null
        }
        val updated = current.copy(
            href = href.trim().takeIf { it.isNotEmpty() },
            etag = null,
            data = data,
            baseData = null,
            updatedAt = now,
            retryCount = 0,
            state = PendingChangeState.PENDING,
            nextAttemptAt = null,
            lastFailure = null,
        )
        replaceEntries(entries.toMutableList().also { it[index] = updated })
        updated
    }

    /** Convenience overload for callers that use the store's clock. */
    fun enqueue(request: PendingChangeRequest): PendingChangeEnqueueResult =
        enqueue(request, clock.instant())

    override fun markRetry(
        id: String,
        failure: PendingChangeFailure,
        counted: Boolean,
        now: Instant,
    ): PendingChange? = synchronized(lock) {
        val index = entries.indexOfFirst { it.id == id }
        if (index == -1) return@synchronized null

        val current = entries[index]
        if (current.state == PendingChangeState.DEAD_LETTER) return@synchronized current

        val nextRetryCount = if (counted) current.retryCount + 1 else current.retryCount
        val nextAttemptCount = current.attemptCount + 1
        val dead = counted && nextRetryCount >= maxRetries
        val normalizedFailure = failure.copy(
            message = safeDiagnostic(failure.message),
            at = failure.at,
        )
        val updated = current.copy(
            updatedAt = now,
            retryCount = nextRetryCount,
            attemptCount = nextAttemptCount,
            state = if (dead) PendingChangeState.DEAD_LETTER else PendingChangeState.RETRY,
            nextAttemptAt = if (dead) {
                null
            } else {
                now.plusMillisSafely(backoffDelayMs(current.retryCount, failure.retryAfterSeconds))
            },
            lastFailure = normalizedFailure,
        )
        val replacement = entries.toMutableList().also { it[index] = updated }
        replaceEntries(replacement)
        updated
    }

    /** Convenience overload for callers that use the store's clock. */
    fun markRetry(
        id: String,
        failure: PendingChangeFailure,
        counted: Boolean = true,
    ): PendingChange = markRetry(id, failure, counted, clock.instant())
        ?: throw NoSuchElementException("No pending change with id $id")

    override fun markDeadLetter(
        id: String,
        failure: PendingChangeFailure,
        now: Instant,
    ): PendingChange? = synchronized(lock) {
        val index = entries.indexOfFirst { it.id == id }
        if (index == -1) return@synchronized null

        val current = entries[index]
        if (current.state == PendingChangeState.DEAD_LETTER) return@synchronized current

        val updated = current.copy(
            updatedAt = now,
            attemptCount = current.attemptCount + 1,
            state = PendingChangeState.DEAD_LETTER,
            nextAttemptAt = null,
            lastFailure = failure.copy(message = safeDiagnostic(failure.message), at = failure.at),
        )
        val replacement = entries.toMutableList().also { it[index] = updated }
        replaceEntries(replacement)
        updated
    }

    /** Convenience overload for callers that use the store's clock. */
    fun markDeadLetter(
        id: String,
        failure: PendingChangeFailure,
    ): PendingChange = markDeadLetter(id, failure, clock.instant())
        ?: throw NoSuchElementException("No pending change with id $id")

    override fun acknowledge(id: String): Boolean = synchronized(lock) {
        val index = entries.indexOfFirst { it.id == id }
        if (index == -1) return@synchronized false
        replaceEntries(entries.toMutableList().also { it.removeAt(index) })
        true
    }

    override fun discard(id: String): Boolean = synchronized(lock) {
        val index = entries.indexOfFirst {
            it.id == id && it.state == PendingChangeState.DEAD_LETTER
        }
        if (index == -1) return@synchronized false
        replaceEntries(entries.toMutableList().also { it.removeAt(index) })
        true
    }

    override fun requeue(id: String, now: Instant): PendingChange? = synchronized(lock) {
        val index = entries.indexOfFirst { it.id == id }
        if (index == -1) return@synchronized null
        val current = entries[index]
        if (current.state != PendingChangeState.DEAD_LETTER) return@synchronized current

        val updated = current.copy(
            updatedAt = now,
            retryCount = 0,
            state = PendingChangeState.PENDING,
            nextAttemptAt = null,
            lastFailure = null,
        )
        replaceEntries(entries.toMutableList().also { it[index] = updated })
        updated
    }

    /** Convenience overload for callers that use the store's clock. */
    fun requeue(id: String): PendingChange = requeue(id, clock.instant())
        ?: throw NoSuchElementException("No pending change with id $id")

    private fun replaceEntries(replacement: List<PendingChange>) {
        // Persist first. If the disk write fails, the in-memory queue remains
        // the old committed state and the caller can surface the exception.
        persist(replacement)
        entries = replacement.toList()
    }

    private fun loadEntries(): List<PendingChange> {
        val primary = readDocument(file)
        if (primary != null) {
            // A temp file can survive a crash after fsync but before rename.
            // Once the primary is valid it is an uncommitted stale write.
            temporaryFile().delete()
            return primary.take(maxEntries)
        }

        // If the process died after fsyncing the temporary document but before
        // the rename, recover it when there is no usable primary document.
        val temporary = temporaryFile()
        val recovered = readDocument(temporary)
        if (recovered != null) {
            try {
                atomicReplace(temporary, file)
            } catch (_: IOException) {
                // The valid temporary document is still readable on this
                // launch; the next mutation will try the replacement again.
            }
            return recovered.take(maxEntries)
        }
        temporaryFile().delete()
        return emptyList()
    }

    private fun readDocument(candidate: File): List<PendingChange>? {
        if (!candidate.isFile) return null
        return try {
            PendingChangeJson.decode(candidate.readText(StandardCharsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun persist(changes: List<PendingChange>) {
        val parent = file.absoluteFile.parentFile
            ?: throw PendingChangeStoreException("Queue file has no parent directory: $file")
        if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
            throw PendingChangeStoreException("Could not create queue directory: $parent")
        }

        val temporary = temporaryFile()
        try {
            FileOutputStream(temporary).use { output ->
                output.write(PendingChangeJson.encode(changes).toByteArray(StandardCharsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            atomicReplace(temporary, file)
        } catch (error: Exception) {
            throw if (error is PendingChangeStoreException) {
                error
            } else {
                PendingChangeStoreException("Could not persist pending writes to $file", error)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun atomicReplace(temporary: File, destination: File) {
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            if (!temporary.renameTo(destination)) {
                throw IOException("Could not atomically replace $destination")
            }
        } catch (_: UnsupportedOperationException) {
            if (!temporary.renameTo(destination)) {
                throw IOException("Could not atomically replace $destination")
            }
        } catch (error: IOException) {
            // Android's file provider may report an unsupported combination of
            // ATOMIC_MOVE and REPLACE_EXISTING as a generic IOException. The
            // temp file is in the same directory, so renameTo is the platform
            // fallback for the same atomic replacement operation.
            if (!temporary.renameTo(destination)) throw error
        }
    }

    private fun temporaryFile(): File = File(file.absolutePath + ".tmp")

    private fun nextId(existing: List<PendingChange>): String {
        var id: String
        do {
            id = UUID.randomUUID().toString()
        } while (existing.any { it.id == id })
        return id
    }

    private fun validateRequest(request: PendingChangeRequest) {
        require(request.eventId.isNotBlank()) { "eventId must not be blank" }
        require(request.accountId.isNotBlank()) { "accountId must not be blank" }
        require(request.calendarId.isNotBlank()) { "calendarId must not be blank" }
        require(request.component.isNotBlank()) { "component must not be blank" }
        validateUrl(request.calendarUrl)
        validateUrl(request.sourceCalendarUrl)
        validateUrl(request.href)
        validateUrl(request.sourceHref)
        rejectCredentialLikePayload(request.data)
        rejectCredentialLikePayload(request.baseData)
        rejectCredentialLikePayload(request.sourceData)
    }

    private fun safeUrl(value: String): String {
        validateUrl(value)
        return value.trim()
    }

    private fun validateUrl(value: String?) {
        if (value == null) return
        val uri = try {
            URI(value.trim())
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid queue URL", error)
        }
        require(uri.rawUserInfo == null) {
            "Queue URLs must not contain user information or passwords"
        }
        require(!CREDENTIAL_FIELD_RE.containsMatchIn(value)) {
            "Queue URLs must not contain credential fields"
        }
    }

    private fun rejectCredentialLikePayload(value: String?) {
        if (value == null) return
        require(!CREDENTIAL_FIELD_RE.containsMatchIn(value)) {
            "Queue payloads must not contain credential fields"
        }
    }

}

private val CREDENTIAL_FIELD_RE = Regex(
    """(?i)(?:x[-_])?(?:password|passwd|passphrase|client[_-]?secret|access[_-]?token|refresh[_-]?token|authorization)\"?\s*[:=]""",
)

private val CREDENTIAL_VALUE_RE = Regex(
    """(?i)\"?(?:x[-_])?(?:password|passwd|passphrase|client[_-]?secret|access[_-]?token|refresh[_-]?token)\"?\s*[:=]\s*(?:\"[^\"]*\"|[^\s,;}]+)""",
)

private val AUTHORIZATION_VALUE_RE = Regex(
    """(?i)(authorization\s*[:=]\s*)(?:basic|bearer)\s+[^\s,;}]+""",
)

private const val MAX_DIAGNOSTIC_LENGTH = 1_000

private fun safeDiagnostic(message: String): String {
    // Server diagnostics are useful for a later UI, but a response should
    // never turn into a credential store. Redact common key/value forms before
    // JSON persistence, including quoted JSON fields.
    return message
        .replace(CREDENTIAL_VALUE_RE, "[redacted]")
        .replace(AUTHORIZATION_VALUE_RE, "$1[redacted]")
        .take(MAX_DIAGNOSTIC_LENGTH)
}

private fun Instant.plusMillisSafely(millis: Long): Instant = try {
    plusMillis(millis)
} catch (_: RuntimeException) {
    Instant.MAX
}

private object PendingChangeJson {
    private const val VERSION = 1

    fun encode(changes: List<PendingChange>): String {
        val array = JSONArray()
        changes.forEach { change ->
            array.put(
                JSONObject()
                    .put("id", change.id)
                    .put("type", change.type.name)
                    .put("eventId", change.eventId)
                    .put("accountId", change.accountId)
                    .put("calendarId", change.calendarId)
                    .put("component", change.component)
                    .putNullable("calendarUrl", change.calendarUrl)
                    .putNullable("uid", change.uid)
                    .putNullable("href", change.href)
                    .putNullable("etag", change.etag)
                    .putNullable("data", change.data)
                    .putNullable("baseData", change.baseData)
                    .putNullable("sourceAccountId", change.sourceAccountId)
                    .putNullable("sourceCalendarId", change.sourceCalendarId)
                    .putNullable("sourceCalendarUrl", change.sourceCalendarUrl)
                    .putNullable("sourceHref", change.sourceHref)
                    .putNullable("sourceEtag", change.sourceEtag)
                    .putNullable("sourceData", change.sourceData)
                    .put("timestamp", change.timestamp.toString())
                    .put("updatedAt", change.updatedAt.toString())
                    .put("retryCount", change.retryCount)
                    .put("attemptCount", change.attemptCount)
                    .put("state", change.state.name)
                    .putNullable("nextAttemptAt", change.nextAttemptAt?.toString())
                    .putNullable("lastFailure", encodeFailure(change.lastFailure)),
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("changes", array)
            .toString()
    }

    fun decode(raw: String): List<PendingChange> {
        val root = JSONObject(raw)
        if (root.optInt("version", -1) != VERSION) return emptyList()
        val array = root.optJSONArray("changes") ?: return emptyList()
        val decoded = ArrayList<PendingChange>(array.length())
        for (index in 0 until array.length()) {
            val value = array.optJSONObject(index) ?: continue
            decodeChange(value)?.let(decoded::add)
        }
        return decoded
    }

    private fun decodeChange(json: JSONObject): PendingChange? {
        return try {
            val id = requiredString(json, "id") ?: return null
            val type = enumOrNull<PendingChangeType>(requiredString(json, "type")) ?: return null
            val eventId = requiredString(json, "eventId") ?: return null
            val accountId = requiredString(json, "accountId") ?: return null
            val calendarId = requiredString(json, "calendarId") ?: return null
            val component = requiredString(json, "component") ?: return null
            val timestamp = Instant.parse(requiredString(json, "timestamp") ?: return null)
            val updatedAt = Instant.parse(
                optionalString(json, "updatedAt") ?: timestamp.toString(),
            )
            val retryCount = json.optInt("retryCount", 0).coerceAtLeast(0)
            val attemptCount = json.optInt("attemptCount", retryCount).coerceAtLeast(0)
            val decodedState = enumOrNull<PendingChangeState>(optionalString(json, "state"))
                ?: PendingChangeState.PENDING
            val nextAttemptAt = optionalString(json, "nextAttemptAt")?.let(Instant::parse)
            val state = if (decodedState == PendingChangeState.RETRY && nextAttemptAt == null) {
                // A hand-edited/old record must not create a permanently
                // blocked FIFO head just because its retry deadline is absent.
                PendingChangeState.PENDING
            } else {
                decodedState
            }
            val data = optionalString(json, "data")
            val baseData = optionalString(json, "baseData")
            val sourceData = optionalString(json, "sourceData")
            if ((data != null && CREDENTIAL_FIELD_RE.containsMatchIn(data)) ||
                (baseData != null && CREDENTIAL_FIELD_RE.containsMatchIn(baseData)) ||
                (sourceData != null && CREDENTIAL_FIELD_RE.containsMatchIn(sourceData))
            ) return null
            val calendarUrl = optionalString(json, "calendarUrl")
            val href = optionalString(json, "href")
            val sourceCalendarUrl = optionalString(json, "sourceCalendarUrl")
            val sourceHref = optionalString(json, "sourceHref")
            if (!isSafeQueueUrl(calendarUrl) || !isSafeQueueUrl(href) ||
                !isSafeQueueUrl(sourceCalendarUrl) || !isSafeQueueUrl(sourceHref)
            ) return null
            PendingChange(
                id = id,
                type = type,
                eventId = eventId,
                accountId = accountId,
                calendarId = calendarId,
                component = component,
                calendarUrl = calendarUrl,
                uid = optionalString(json, "uid"),
                href = href,
                etag = optionalString(json, "etag"),
                data = data,
                baseData = baseData,
                sourceAccountId = optionalString(json, "sourceAccountId"),
                sourceCalendarId = optionalString(json, "sourceCalendarId"),
                sourceCalendarUrl = sourceCalendarUrl,
                sourceHref = sourceHref,
                sourceEtag = optionalString(json, "sourceEtag"),
                sourceData = sourceData,
                timestamp = timestamp,
                updatedAt = updatedAt,
                retryCount = retryCount,
                attemptCount = attemptCount,
                state = state,
                nextAttemptAt = if (state == PendingChangeState.RETRY) nextAttemptAt else null,
                lastFailure = decodeFailure(json.optJSONObject("lastFailure")),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeFailure(failure: PendingChangeFailure?): JSONObject? {
        if (failure == null) return null
        return JSONObject()
            .put("message", failure.message)
            .putNullable("statusCode", failure.statusCode)
            .putNullable("retryAfterSeconds", failure.retryAfterSeconds)
            .put("at", failure.at.toString())
    }

    private fun decodeFailure(json: JSONObject?): PendingChangeFailure? {
        if (json == null) return null
        return try {
            PendingChangeFailure(
                message = safeDiagnostic(optionalString(json, "message") ?: return null),
                statusCode = json.optionalInt("statusCode"),
                retryAfterSeconds = json.optionalLong("retryAfterSeconds"),
                at = Instant.parse(optionalString(json, "at") ?: return null),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun requiredString(json: JSONObject, key: String): String? =
        optionalString(json, key)?.takeIf { it.isNotBlank() }

    private fun optionalString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optString(key).takeIf { it.isNotEmpty() && it != "null" }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
        value?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } }

    private fun JSONObject.optionalInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optionalLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)
}

private fun isSafeQueueUrl(value: String?): Boolean {
    if (value == null) return true
    return try {
        URI(value).rawUserInfo == null && !CREDENTIAL_FIELD_RE.containsMatchIn(value)
    } catch (_: Exception) {
        false
    }
}
