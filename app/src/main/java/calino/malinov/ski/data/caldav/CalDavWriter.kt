package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.JournalEntry
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.coroutines.CancellationException

/** The result of a successful PUT, including the bytes now held in the cache. */
data class WrittenCalendarResource(
    val href: String,
    val etag: String?,
    val ics: String,
)

/** A serialized write ready to be sent now or persisted in the offline queue. */
data class PreparedCalendarWrite(
    val href: String,
    val body: String,
    val precondition: DavPrecondition,
    val expectedEtag: String?,
)

/**
 * The small, conditional CalDAV write surface used by [CalDavRepository].
 *
 * The writer intentionally writes one resource at a time. Existing resources are
 * patched only when the raw cache carries the same ETag as the mapped record.
 * If those bytes are missing, stale, or cannot be patched, an existing resource
 * is rejected instead of being rebuilt from the partial model. Deletes are
 * similarly strict: without a current raw resource there is no safe way to know
 * whether the resource contains another component.
 */
class CalDavWriter(
    private val http: DavHttp = DavHttp(),
    private val cache: CalendarCache = CalendarCache.None,
    private val writer: ICalWriter = ICalWriter(),
    private val patcher: ICalPatcher = ICalPatcher(writer),
    private val now: () -> Instant = { Instant.now() },
) {

    suspend fun putEvent(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        event: CalEvent,
    ): WrittenCalendarResource {
        return putPrepared(calendar, credentials, prepareEvent(calendar, event))
    }

    fun prepareEvent(calendar: DiscoveredCalendar, event: CalEvent): PreparedCalendarWrite {
        val record = event.copy(uid = event.uid ?: event.id)
        return prepare(
            calendar = calendar,
            href = hrefFor(calendar.url, record.href, record.uid!!),
            uid = record.uid,
            etag = record.etag,
            forceCreate = record.href == null && record.etag == null,
            build = { writer.writeEvent(record, now = now()) },
            patch = { original -> patcher.patchEvents(original, listOf(record), now()) },
        )
    }

    suspend fun putTask(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        task: CalTask,
    ): WrittenCalendarResource {
        return putPrepared(calendar, credentials, prepareTask(calendar, task))
    }

    fun prepareTask(calendar: DiscoveredCalendar, task: CalTask): PreparedCalendarWrite {
        val record = task.copy(uid = task.uid ?: task.id)
        return prepare(
            calendar = calendar,
            href = hrefFor(calendar.url, record.href, record.uid!!),
            uid = record.uid,
            etag = record.etag,
            forceCreate = record.href == null && record.etag == null,
            build = { writer.writeTask(record, now = now()) },
            patch = { original -> patcher.patchTask(original, record, now()) },
        )
    }

    suspend fun putJournal(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        entry: JournalEntry,
    ): WrittenCalendarResource {
        return putPrepared(calendar, credentials, prepareJournal(calendar, entry))
    }

    fun prepareJournal(calendar: DiscoveredCalendar, entry: JournalEntry): PreparedCalendarWrite {
        val record = entry.copy(uid = entry.uid ?: entry.id)
        return prepare(
            calendar = calendar,
            href = hrefFor(calendar.url, record.href, record.uid!!),
            uid = record.uid,
            etag = record.etag,
            forceCreate = record.href == null && record.etag == null,
            build = { writer.writeJournal(record, now = now()) },
            patch = { original -> patcher.patchJournal(original, record, now()) },
        )
    }

    suspend fun putPrepared(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        prepared: PreparedCalendarWrite,
    ): WrittenCalendarResource {
        val response = http.put(
            url = prepared.href,
            credentials = credentials,
            body = prepared.body,
            contentType = DavHttp.CalendarMediaType,
            precondition = prepared.precondition,
        )
        requireSuccess(response)
        val stored = storedAfterPut(response, prepared.href, credentials, prepared.body)
        val result = WrittenCalendarResource(
            href = prepared.href,
            // A successful PUT creates a new validator. Reusing the
            // precondition when the response and readback omit it would make
            // the cache claim that old bytes are current.
            // The next edit will safely require a refresh instead.
            etag = stored.etag,
            ics = stored.ics,
        )
        cache.saveResource(calendar.url, CalendarResource(result.href, result.etag, result.ics))
        return result
    }

    /**
     * Re-reads one resource after a lost-update response and refreshes the
     * raw cache.  A fresh ETag without the fresh bytes is not enough for a
     * patch: using the old cached component with the new tag could overwrite
     * another client's fields.
     */
    suspend fun refreshResource(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        href: String,
        uid: String,
    ): CalendarResource {
        val resourceUrl = hrefFor(calendar.url, href, uid)
        val response = http.request(
            method = "GET",
            url = resourceUrl,
            credentials = credentials,
        )
        requireSuccess(response)
        val resource = CalendarResource(
            href = resourceUrl,
            etag = responseEtag(response, resourceUrl, credentials),
            ics = response.body,
        )
        cache.saveResource(calendar.url, resource)
        return resource
    }

    /** Reads only the current validator, for queue/retry code that already has the body. */
    suspend fun currentEtag(url: String, credentials: DavCredentials): String? =
        fetchEtag(url, credentials)

    /**
     * Removes a component from its resource, or deletes the resource when it
     * was the last schedulable component in it.
     */
    suspend fun delete(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        href: String,
        uid: String,
        etag: String?,
        component: String? = null,
    ) {
        val resourceUrl = hrefFor(calendar.url, href, uid)
        val expectedEtag = requireEtag(resourceUrl, etag)
        val cached = cache.loadResource(calendar.url, resourceUrl)
        val original = cached?.takeIf { sameEtag(it.etag, expectedEtag) }
            ?: throw staleResource(resourceUrl)

        when (val removal = patcher.removeComponent(original.ics, uid, component)) {
            null -> throw CalDavException(
                CalDavErrorCode.NotCalDav,
                "That calendar item could not be read safely. Refresh and try again.",
            )
            ICalPatcher.PatchRemoval.Emptied -> {
                val response = http.delete(
                    url = resourceUrl,
                    credentials = credentials,
                    precondition = DavPrecondition.Match(expectedEtag),
                )
                if (response.status == 404 || response.status == 410) {
                    cache.deleteResource(calendar.url, resourceUrl)
                    return
                }
                requireSuccess(response)
                cache.deleteResource(calendar.url, resourceUrl)
            }
            is ICalPatcher.PatchRemoval.Patched -> {
                val response = http.put(
                    url = resourceUrl,
                    credentials = credentials,
                    body = removal.ics,
                    contentType = DavHttp.CalendarMediaType,
                    precondition = DavPrecondition.Match(expectedEtag),
                )
                requireSuccess(response)
                val stored = storedAfterPut(response, resourceUrl, credentials, removal.ics)
                cache.saveResource(
                    calendar.url,
                    CalendarResource(resourceUrl, stored.etag, stored.ics),
                )
            }
        }
    }

    private fun prepare(
        calendar: DiscoveredCalendar,
        href: String,
        uid: String,
        etag: String?,
        forceCreate: Boolean,
        build: () -> biweekly.component.ICalComponent,
        patch: (String) -> String?,
        validatePatch: ((String) -> Unit)? = null,
    ): PreparedCalendarWrite {
        val resourceUrl = hrefFor(calendar.url, href, uid)
        val cached = if (forceCreate) null else cache.loadResource(calendar.url, resourceUrl)
        val expectedEtag = if (forceCreate) null else etag ?: cached?.etag
        val body = if (forceCreate) {
            writer.buildCalendar(listOf(build()))
        } else {
            val current = cached ?: throw staleResource(resourceUrl)
            val currentEtag = expectedEtag ?: throw staleResource(resourceUrl)
            if (!sameEtag(current.etag, currentEtag)) throw staleResource(resourceUrl)
            validatePatch?.invoke(current.ics)
            patch(current.ics) ?: throw invalidWrite(
                "That calendar item could not be patched safely. Refresh and try again.",
            )
        }

        val precondition = if (forceCreate) {
            DavPrecondition.New
        } else {
            DavPrecondition.Match(
                expectedEtag ?: throw staleResource(resourceUrl),
            )
        }
        return PreparedCalendarWrite(
            href = resourceUrl,
            body = body,
            precondition = precondition,
            expectedEtag = expectedEtag,
        )
    }

    private suspend fun responseEtag(
        response: DavResponse,
        resourceUrl: String,
        credentials: DavCredentials,
    ): String? {
        normalizeEtag(response.header("ETag"))?.let { return it }
        return try {
            fetchEtag(resourceUrl, credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun storedAfterPut(
        response: DavResponse,
        url: String,
        credentials: DavCredentials,
        submitted: String,
    ): CalendarResource {
        normalizeEtag(response.header("ETag"))?.let { return CalendarResource(url, it, submitted) }
        // A later PROPFIND validator does not prove that the server retained
        // our exact bytes. Read the representation paired with that validator.
        return try {
            val fetched = http.request("GET", url, credentials, headers = mapOf("Accept" to "text/calendar"))
            if (fetched.status in 200..299 && fetched.body.isNotBlank()) {
                CalendarResource(url, normalizeEtag(fetched.header("ETag")), fetched.body)
            } else {
                CalendarResource(url, null, submitted)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            CalendarResource(url, null, submitted)
        }
    }

    private suspend fun fetchEtag(url: String, credentials: DavCredentials): String? {
        val response = http.request(
            method = "PROPFIND",
            url = url,
            credentials = credentials,
            headers = mapOf(
                "Depth" to "0",
                "Content-Type" to "application/xml; charset=utf-8",
            ),
            body = PropfindEtag,
        )
        requireSuccess(response)
        val root = DavXml.parse(response.body) ?: return null
        return DavXml.elements(root, DavNs.Dav, "response")
            .asSequence()
            .mapNotNull { DavXml.text(it, DavNs.Dav, "getetag") }
            .mapNotNull(::normalizeEtag)
            .firstOrNull()
    }

    private fun requireSuccess(response: DavResponse) {
        if (response.status !in 200..299) {
            throw calDavErrorForStatus(response.status, response.url, response.body)
        }
    }

    private fun requireEtag(resourceUrl: String, etag: String?): String =
        normalizeEtag(etag) ?: throw CalDavException(
            CalDavErrorCode.PreconditionFailed,
            "That item has no current server version. Refresh and try again.",
            status = 412,
        )

    private fun staleResource(resourceUrl: String) = CalDavException(
        CalDavErrorCode.PreconditionFailed,
        "That item changed on the server. Refresh and try again.",
        status = 412,
    )

    private fun sameEtag(left: String?, right: String?): Boolean =
        normalizeEtag(left) != null && normalizeEtag(left) == normalizeEtag(right)

    private fun hrefFor(collectionUrl: String, existingHref: String?, uid: String): String {
        existingHref?.let { href ->
            val resolved = resolveHref(collectionUrl, href)
            if (resourceIsInCollection(resolved, collectionUrl)) return resolved
            throw invalidWrite("That calendar item does not belong to this calendar.")
        }
        return resolveHref(
            collectionUrl,
            eventResourceFilename(uid),
        )
    }

    private fun invalidWrite(message: String) = CalDavException(
        CalDavErrorCode.NotCalDav,
        message,
    )

    companion object {
        private const val PropfindEtag = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:getetag/></d:prop></d:propfind>"""

        /**
         * The same filename policy used by the Calino web client. `~XX` is a
         * percent-escape with a filesystem/WebDAV-safe marker: Radicale
         * rejects both `:` and literal `%` in resource names.
         */
        fun eventResourceFilename(uid: String): String = buildString {
            uid.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
                val value = byte.toInt() and 0xFF
                if (value in 'a'.code..'z'.code ||
                    value in 'A'.code..'Z'.code ||
                    value in '0'.code..'9'.code ||
                    value == '-'.code || value == '_'.code || value == '.'.code
                ) {
                    append(value.toChar())
                } else {
                    append('~')
                    append(value.toString(16).uppercase().padStart(2, '0'))
                }
            }
            append(".ics")
        }

        fun resolveHref(base: String, href: String): String =
            runCatching { URI(base).resolve(href).toString() }.getOrDefault(href)

        fun resourceIsInCollection(resource: String, collection: String): Boolean = runCatching {
            val resourceUri = URI(resource)
            val collectionUri = URI(collection)
            if (resourceUri.scheme != collectionUri.scheme || resourceUri.authority != collectionUri.authority) {
                return@runCatching false
            }
            val collectionPath = collectionUri.path.orEmpty().trimEnd('/') + "/"
            val resourcePath = resourceUri.path.orEmpty()
            resourcePath.startsWith(collectionPath) && resourcePath != collectionPath
        }.getOrDefault(false)
    }
}
