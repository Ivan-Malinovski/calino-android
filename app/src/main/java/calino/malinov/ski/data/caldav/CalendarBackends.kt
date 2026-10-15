package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.CalendarProtocol
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.RecurrenceEditScope
import java.time.LocalDate

/**
 * The read half of a calendar backend: what `CalDavRepository` needs from a
 * server, with no assumption about the protocol that produced it.
 *
 * Resources are opaque `(href, etag, ics)` triples. A CalDAV backend returns
 * the server's bytes; a JMAP backend returns iCalendar it translated from
 * JSCalendar, under a synthetic href and a content-hash ETag. The cache, queue
 * and recurrence code only ever see that shape.
 */
interface CalendarFetching {
    /** Complete VEVENT collection for an explicit user export; never windowed. */
    suspend fun fetchAllEvents(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
    ): List<CalendarResource>

    suspend fun fetch(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): FetchResult

    suspend fun fetchIncremental(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        cachedResources: List<CalendarResource>?,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        storedCursor: CollectionCursor? = null,
    ): IncrementalFetchResult
}

/**
 * The conditional write surface. `prepare*` builds the exact body and
 * precondition that is persisted in the offline queue; [putPrepared] sends it.
 * The prepare half is protocol-neutral (it patches cached iCalendar), so a
 * JMAP backend reuses it and converts at send time.
 */
interface CalendarWriting {
    fun prepareEvent(calendar: DiscoveredCalendar, event: CalEvent): PreparedCalendarWrite

    fun prepareRsvp(
        calendar: DiscoveredCalendar,
        event: CalEvent,
        address: String,
        status: String,
        expectedStatus: String,
        scope: RecurrenceEditScope,
        etag: String? = event.etag,
    ): PreparedCalendarWrite

    fun prepareTask(calendar: DiscoveredCalendar, task: CalTask): PreparedCalendarWrite

    fun prepareJournal(calendar: DiscoveredCalendar, entry: JournalEntry): PreparedCalendarWrite

    suspend fun putPrepared(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        prepared: PreparedCalendarWrite,
    ): WrittenCalendarResource

    suspend fun refreshResource(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        href: String,
        uid: String,
    ): CalendarResource

    suspend fun delete(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        href: String,
        uid: String,
        etag: String?,
        component: String? = null,
    )
}

/** Sends each call to the backend for the calendar's protocol. */
class ProtocolCalendarFetcher(
    private val caldav: CalendarFetching,
    private val jmap: CalendarFetching? = null,
) : CalendarFetching {
    private fun backend(calendar: DiscoveredCalendar): CalendarFetching = when (calendar.protocol) {
        CalendarProtocol.CalDav -> caldav
        CalendarProtocol.Jmap -> jmap ?: throw CalDavException(
            CalDavErrorCode.NotCalDav,
            "This build cannot read JMAP calendars.",
        )
    }

    override suspend fun fetchAllEvents(calendar: DiscoveredCalendar, credentials: DavCredentials) =
        backend(calendar).fetchAllEvents(calendar, credentials)

    override suspend fun fetch(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ) = backend(calendar).fetch(calendar, credentials, windowStart, windowEnd)

    override suspend fun fetchIncremental(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        cachedResources: List<CalendarResource>?,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        storedCursor: CollectionCursor?,
    ) = backend(calendar).fetchIncremental(
        calendar, credentials, cachedResources, windowStart, windowEnd, storedCursor,
    )
}

class ProtocolCalendarWriter(
    private val caldav: CalendarWriting,
    private val jmap: CalendarWriting? = null,
) : CalendarWriting {
    private fun backend(calendar: DiscoveredCalendar): CalendarWriting = when (calendar.protocol) {
        CalendarProtocol.CalDav -> caldav
        CalendarProtocol.Jmap -> jmap ?: throw CalDavException(
            CalDavErrorCode.NotCalDav,
            "This build cannot write JMAP calendars.",
        )
    }

    override fun prepareEvent(calendar: DiscoveredCalendar, event: CalEvent) =
        backend(calendar).prepareEvent(calendar, event)

    override fun prepareRsvp(
        calendar: DiscoveredCalendar,
        event: CalEvent,
        address: String,
        status: String,
        expectedStatus: String,
        scope: RecurrenceEditScope,
        etag: String?,
    ) = backend(calendar).prepareRsvp(calendar, event, address, status, expectedStatus, scope, etag)

    override fun prepareTask(calendar: DiscoveredCalendar, task: CalTask) =
        backend(calendar).prepareTask(calendar, task)

    override fun prepareJournal(calendar: DiscoveredCalendar, entry: JournalEntry) =
        backend(calendar).prepareJournal(calendar, entry)

    override suspend fun putPrepared(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        prepared: PreparedCalendarWrite,
    ) = backend(calendar).putPrepared(calendar, credentials, prepared)

    override suspend fun refreshResource(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        href: String,
        uid: String,
    ) = backend(calendar).refreshResource(calendar, credentials, href, uid)

    override suspend fun delete(
        calendar: DiscoveredCalendar,
        credentials: DavCredentials,
        href: String,
        uid: String,
        etag: String?,
        component: String?,
    ) = backend(calendar).delete(calendar, credentials, href, uid, etag, component)
}
