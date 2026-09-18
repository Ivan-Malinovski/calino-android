package calino.malinov.ski.platform

import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import java.util.TimeZone

/**
 * Writes a [ProviderIdentity.Projection] into Android's calendar provider.
 *
 * Modelled on the sister app's `CalendarMirrorWriter`, and it reuses that
 * file's hard-won mechanics: `CALLER_IS_SYNCADAPTER` tagging, `_SYNC_ID` as
 * the only ownership marker, batch chunking, `withValueBackReference` for
 * reminder rows, an explicit `ALLOWED_REMINDERS`, and the content-hash skip
 * that keeps an unchanged pass from churning the provider's alarms.
 *
 * It diverges in the two ways `docs/calendar-provider.md` settles: calendars
 * hang off Calino's own account type rather than `ACCOUNT_TYPE_LOCAL`, and
 * they may be writable, because edits made elsewhere are meant to come back.
 *
 * **Every** query, update and delete here is scoped by our account type, and
 * per-account work additionally by account name. That scoping is the only
 * thing standing between a bug in this file and the person's Google calendar,
 * so it is not a convention to relax for convenience.
 */
object CalendarProjection {

    /**
     * The provider rejects oversized transactions and a first pass can produce
     * thousands of operations, so batches are chunked. Each event contributes
     * one insert plus one op per reminder, which keeps this well under the
     * limit.
     */
    private const val BatchSize = 400

    private val CalendarProjectionColumns = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars._SYNC_ID,
        CalendarContract.Calendars.ACCOUNT_NAME,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.CALENDAR_COLOR,
        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
    )

    private val EventProjectionColumns = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events._SYNC_ID,
        ProviderIdentity.Columns.Hash,
        CalendarContract.Events.CALENDAR_ID,
    )

    /** What one pass did, for logging and for the instrumented tests to assert on. */
    data class Result(
        val calendars: Int = 0,
        val written: Int = 0,
        val removed: Int = 0,
    )

    /**
     * Makes the provider match [projection].
     *
     * [writable] decides whether projected calendars are offered to other apps
     * as editable. It stays false until inbound ingest exists: a calendar a
     * person can edit but whose edits nothing reads would silently discard
     * them on the next pass, which is worse than not offering the affordance.
     * A calendar Calino itself cannot write is read-only regardless.
     *
     * Returns null when the provider refuses us -- most often a missing
     * calendar permission, which is a supported state and not an error.
     */
    fun sync(
        context: Context,
        projection: ProviderIdentity.Projection,
        writable: Boolean = false,
    ): Result? = runCatching {
        val accounts = projection.calendars
            .map { it.accountId }
            .distinct()
            .mapNotNull { id -> CalinoAccounts.find(context, id)?.let { id to it } }
            .toMap()

        val rowIds = reconcileCalendars(context, projection.calendars, accounts, writable)
        val counts = reconcileEvents(context, projection.events, rowIds, accounts)
        Result(calendars = rowIds.size, written = counts.first, removed = counts.second)
    }.getOrNull()

    /**
     * Removes every calendar this app owns, and with it every event and
     * reminder inside one.
     *
     * This is what turning projection off runs, and what removing an account
     * runs for its own calendars. It touches nothing on the CalDAV server.
     */
    fun clear(context: Context): Int = runCatching {
        var removed = 0
        for (account in ownedAccounts(context)) {
            removed += context.contentResolver.delete(
                syncAdapterUri(CalendarContract.Calendars.CONTENT_URI, account),
                null,
                null,
            )
        }
        removed
    }.getOrDefault(0)

    /** Removes the calendars belonging to one Calino account, leaving the rest. */
    fun clearAccount(context: Context, accountId: String): Int = runCatching {
        val account = CalinoAccounts.find(context, accountId) ?: return@runCatching 0
        context.contentResolver.delete(
            syncAdapterUri(CalendarContract.Calendars.CONTENT_URI, account),
            null,
            null,
        )
    }.getOrDefault(0)

    // ------------------------------------------------------------- calendars

    /**
     * Creates, updates and deletes projected calendars so they match
     * [desired].
     *
     * @return Calino calendar id to provider row id, for the event pass.
     */
    private fun reconcileCalendars(
        context: Context,
        desired: List<ProviderIdentity.ProjectedCalendar>,
        accounts: Map<String, Account>,
        writable: Boolean,
    ): Map<String, Long> {
        val wanted = desired.associateBy { it.id }
        val resolved = mutableMapOf<String, Long>()
        val resolver = context.contentResolver
        val ourType = CalinoAccounts.accountType(context)

        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            CalendarProjectionColumns,
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ourType),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(0)
                val syncId = cursor.getString(1)
                val accountName = cursor.getString(2)
                val calendar = syncId?.let { wanted[it] }
                val account = calendar?.let { accounts[it.accountId] }

                // A calendar whose account moved cannot be updated in place:
                // the provider keys rows by account, so it is dropped here and
                // re-created below under the right owner.
                if (calendar == null || account == null || account.name != accountName) {
                    resolver.delete(
                        ContentUris.withAppendedId(
                            syncAdapterUri(
                                CalendarContract.Calendars.CONTENT_URI,
                                Account(accountName, ourType),
                            ),
                            rowId,
                        ),
                        null,
                        null,
                    )
                    continue
                }

                val access = accessLevel(calendar, writable)
                if (calendar.name != cursor.getString(3) ||
                    calendar.color != cursor.getInt(4) ||
                    access != cursor.getInt(5)
                ) {
                    val values = ContentValues().apply {
                        put(CalendarContract.Calendars.NAME, calendar.name)
                        put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendar.name)
                        put(CalendarContract.Calendars.CALENDAR_COLOR, calendar.color)
                        put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, access)
                    }
                    resolver.update(
                        ContentUris.withAppendedId(
                            syncAdapterUri(CalendarContract.Calendars.CONTENT_URI, account),
                            rowId,
                        ),
                        values,
                        null,
                        null,
                    )
                }
                resolved[syncId] = rowId
            }
        }

        for ((id, calendar) in wanted) {
            if (id in resolved) continue
            val account = accounts[calendar.accountId] ?: continue
            insertCalendar(context, calendar, account, writable)?.let { resolved[id] = it }
        }

        return resolved
    }

    private fun insertCalendar(
        context: Context,
        calendar: ProviderIdentity.ProjectedCalendar,
        account: Account,
        writable: Boolean,
    ): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars._SYNC_ID, calendar.id)
            put(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, account.name)
            put(CalendarContract.Calendars.NAME, calendar.name)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendar.name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, calendar.color)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, accessLevel(calendar, writable))
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            // Without an explicit allow-list some calendar apps decline to
            // surface our reminder rows at all.
            put(
                CalendarContract.Calendars.ALLOWED_REMINDERS,
                CalendarContract.Reminders.METHOD_ALERT.toString(),
            )
            put(CalendarContract.Calendars.ALLOWED_AVAILABILITY, "0,1")
            put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES, "0,1,2")
        }
        val inserted = context.contentResolver.insert(
            syncAdapterUri(CalendarContract.Calendars.CONTENT_URI, account),
            values,
        ) ?: return null
        return ContentUris.parseId(inserted)
    }

    /**
     * A CalDAV collection Calino may not write is [CAL_ACCESS_READ] whatever
     * [writable] says: a foreign app must not be offered an edit that is
     * certain to be refused. That is a stronger guarantee than rejecting the
     * write later, and it is the same one the review doc commits to.
     */
    private fun accessLevel(
        calendar: ProviderIdentity.ProjectedCalendar,
        writable: Boolean,
    ): Int = if (writable && !calendar.readOnly) {
        CalendarContract.Calendars.CAL_ACCESS_OWNER
    } else {
        CalendarContract.Calendars.CAL_ACCESS_READ
    }

    // ---------------------------------------------------------------- events

    /** @return written to removed. */
    private fun reconcileEvents(
        context: Context,
        desired: List<ProviderIdentity.ProjectedEvent>,
        calendarRowIds: Map<String, Long>,
        accounts: Map<String, Account>,
    ): Pair<Int, Int> {
        if (calendarRowIds.isEmpty()) return 0 to 0

        // Any Calino account will do to tag the batch: the provider takes the
        // account from the row's calendar, and the URI parameters only have to
        // mark us as a sync adapter.
        val tag = accounts.values.firstOrNull() ?: return 0 to 0
        val eventsUri = syncAdapterUri(CalendarContract.Events.CONTENT_URI, tag)

        val wanted = desired.filter { it.calendarId in calendarRowIds }.associateBy { it.id }
        val ops = mutableListOf<ContentProviderOperation>()
        val upToDate = mutableSetOf<String>()
        var removed = 0

        context.contentResolver.query(
            eventsUri,
            EventProjectionColumns,
            "${CalendarContract.Events.CALENDAR_ID} IN (${calendarRowIds.values.joinToString(",")})",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(0)
                val syncId = cursor.getString(1)
                val event = syncId?.let { wanted[it] }
                val wantedCalendar = event?.let { calendarRowIds[it.calendarId] }

                val unchanged = event != null &&
                    wantedCalendar == cursor.getLong(3) &&
                    event.hash == cursor.getString(2)
                if (unchanged) {
                    upToDate += syncId
                    continue
                }

                // Changed events are deleted and re-inserted rather than
                // updated: reminder rows hang off the event id and would
                // otherwise need a diff of their own for no practical gain.
                ops += ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(eventsUri, rowId))
                    .build()
                if (event == null) removed++
            }
        }

        var written = 0
        for ((id, event) in wanted) {
            if (id in upToDate) continue
            appendInsert(ops, eventsUri, event, calendarRowIds.getValue(event.calendarId), tag)
            written++
            if (ops.size >= BatchSize) flush(context, ops)
        }
        flush(context, ops)

        return written to removed
    }

    private fun appendInsert(
        ops: MutableList<ContentProviderOperation>,
        eventsUri: Uri,
        event: ProviderIdentity.ProjectedEvent,
        calendarRowId: Long,
        account: Account,
    ) {
        val eventOpIndex = ops.size

        val builder = ContentProviderOperation.newInsert(eventsUri)
            .withValue(CalendarContract.Events._SYNC_ID, event.id)
            .withValue(ProviderIdentity.Columns.Hash, event.hash)
            .withValue(ProviderIdentity.Columns.Href, event.href)
            .withValue(ProviderIdentity.Columns.Etag, event.etag)
            .withValue(ProviderIdentity.Columns.Uid, event.uid)
            .withValue(ProviderIdentity.Columns.RecurrenceId, event.recurrenceId)
            // Our own writes must never look like a foreign edit. The
            // sync-adapter URI already leaves DIRTY alone; setting it to 0
            // explicitly says so at the one place a reader will look.
            .withValue(CalendarContract.Events.DIRTY, 0)
            .withValue(CalendarContract.Events.CALENDAR_ID, calendarRowId)
            .withValue(CalendarContract.Events.TITLE, event.title)
            .withValue(CalendarContract.Events.DTSTART, event.startMillis)
            .withValue(CalendarContract.Events.DTEND, event.endMillis)
            .withValue(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            .withValue(CalendarContract.Events.EVENT_TIMEZONE, event.timeZone)
            .withValue(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT)
            .withValue(
                CalendarContract.Events.AVAILABILITY,
                if (event.free) {
                    CalendarContract.Events.AVAILABILITY_FREE
                } else {
                    CalendarContract.Events.AVAILABILITY_BUSY
                },
            )
            .withValue(CalendarContract.Events.HAS_ALARM, if (event.reminderMinutes.isEmpty()) 0 else 1)

        event.description?.let { builder.withValue(CalendarContract.Events.DESCRIPTION, it) }
        event.location?.let { builder.withValue(CalendarContract.Events.EVENT_LOCATION, it) }

        ops += builder.build()

        for (minutes in event.reminderMinutes) {
            ops += ContentProviderOperation
                .newInsert(syncAdapterUri(CalendarContract.Reminders.CONTENT_URI, account))
                // Back-reference: the event row id does not exist until the
                // batch is applied.
                .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOpIndex)
                .withValue(CalendarContract.Reminders.MINUTES, minutes)
                .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                .build()
        }
    }

    private fun flush(context: Context, ops: MutableList<ContentProviderOperation>) {
        if (ops.isEmpty()) return
        val results = context.contentResolver.applyBatch(
            CalendarContract.AUTHORITY,
            ArrayList(ops),
        )
        check(results.size == ops.size) {
            "Calendar provider applied ${results.size} of ${ops.size} operations"
        }
        ops.clear()
    }

    // --------------------------------------------------------------- helpers

    /** Every Android account this build owns. */
    private fun ownedAccounts(context: Context): List<Account> =
        runCatching {
            android.accounts.AccountManager
                .get(context)
                .getAccountsByType(CalinoAccounts.accountType(context))
                .toList()
        }.getOrDefault(emptyList())

    /**
     * Tags a URI as a sync-adapter call.
     *
     * Required to create a calendar at all, to set the sync columns ownership
     * is keyed off, and so that a delete actually removes the row instead of
     * tombstoning it as `DELETED = 1` for a sync adapter to pick up -- which,
     * now that Calino *is* that sync adapter, would otherwise come straight
     * back as a phantom inbound deletion.
     */
    private fun syncAdapterUri(uri: Uri, account: Account): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
        .build()
}
