package calino.malinov.ski.platform

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.data.repository.WriteResult
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The inbound half of the provider integration: edits another calendar app
 * made to a Calino-owned row, routed back into Calino's own write pipeline.
 *
 * The rule `docs/calendar-provider.md` settles, and the one this file exists
 * to enforce, is that **a provider edit is a request, not a fact**. Nothing
 * here writes to a CalDAV server, patches a resource, or touches the durable
 * queue. It reads rows, turns each into a call on [CalinoRepository], and
 * lets the existing pipeline decide -- so conditional writes, `ICalPatcher`
 * rebasing, `WriteQueue` durability and dead-lettering all apply unchanged.
 * There is no second writer here by construction, not by discipline.
 *
 * Two consequences of that stance are visible in the code:
 *
 * - An update is built by taking the **snapshot's** [CalEvent] and overwriting
 *   only the handful of fields `CalendarContract` can actually hold. Attendees,
 *   categories, the recurrence rule and every foreign iCalendar property the
 *   provider never saw are carried across untouched, which is what keeps an
 *   edit from quietly destroying data the provider cannot represent.
 * - Every ingested row has its content hash cleared, so the next projection
 *   pass rewrites it from Calino's snapshot whatever the repository decided.
 *   An accepted edit is rewritten in its canonical form; a **rejected** one is
 *   reverted without needing a separate revert path. The provider is never
 *   left holding a change Calino refused.
 *
 * Ownership scoping is the same as the projection's: only calendars under
 * Calino's own account type and name are ever read or written.
 */
object CalendarIngest {

    /** One foreign edit, read out of the provider. */
    data class Change(
        val rowId: Long,
        /** The Calino calendar id, from the owning calendar's `_SYNC_ID`. */
        val calendarId: String,
        /** The Calino event id, or null for a row a foreign app created. */
        val eventId: String?,
        val deleted: Boolean,
        val title: String,
        val description: String?,
        val location: String?,
        val startMillis: Long,
        val endMillis: Long,
        val allDay: Boolean,
        val free: Boolean,
        val reminderMinutes: List<Int>,
    )

    /** What one ingest pass did. Mostly for the instrumented tests to assert on. */
    data class Report(
        val added: Int = 0,
        val updated: Int = 0,
        val deleted: Int = 0,
        val rejected: Int = 0,
        val unresolved: Int = 0,
    ) {
        val touched: Int get() = added + updated + deleted + rejected + unresolved
    }

    private val EventColumns = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events._SYNC_ID,
        CalendarContract.Events.CALENDAR_ID,
        CalendarContract.Events.DELETED,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.DURATION,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.AVAILABILITY,
    )

    /**
     * Reads every dirty or deleted row in the calendars [account] owns.
     *
     * Queried through the sync-adapter URI, because the ordinary one hides
     * `DELETED = 1` rows -- which are exactly the deletions we are here for.
     */
    fun collect(context: Context, account: Account): List<Change> = runCatching {
        val calendars = ownedCalendars(context, account)
        if (calendars.isEmpty()) return@runCatching emptyList()

        val uri = syncAdapterUri(CalendarContract.Events.CONTENT_URI, account)
        val changes = mutableListOf<Change>()
        context.contentResolver.query(
            uri,
            EventColumns,
            "(${CalendarContract.Events.DIRTY} = 1 OR ${CalendarContract.Events.DELETED} = 1)" +
                " AND ${CalendarContract.Events.CALENDAR_ID} IN (${calendars.keys.joinToString(",")})",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(0)
                val calendarId = calendars[cursor.getLong(2)] ?: continue
                val deleted = cursor.getInt(3) == 1
                val start = if (cursor.isNull(7)) continue else cursor.getLong(7)
                val allDay = cursor.getInt(10) == 1
                val end = when {
                    !cursor.isNull(8) -> cursor.getLong(8)
                    // A foreign app may write DURATION instead of DTEND. An
                    // unparseable one is not a reason to drop the edit; an
                    // hour is the same default the editor uses.
                    else -> start + (durationMillis(cursor.getString(9)) ?: DefaultDurationMillis)
                }
                changes += Change(
                    rowId = rowId,
                    calendarId = calendarId,
                    eventId = cursor.getString(1),
                    deleted = deleted,
                    title = cursor.getString(4).orEmpty(),
                    description = cursor.getString(5),
                    location = cursor.getString(6),
                    startMillis = start,
                    endMillis = end,
                    allDay = allDay,
                    free = cursor.getInt(11) == CalendarContract.Events.AVAILABILITY_FREE,
                    // A deleted row's reminders are of no interest, and
                    // reading them is a query per row.
                    reminderMinutes = if (deleted) emptyList() else reminders(context, rowId),
                )
            }
        }
        changes
    }.getOrDefault(emptyList())

    /**
     * Routes [changes] through [repository] and settles the provider rows.
     *
     * Each row is settled the moment its own write returns, rather than in a
     * second sweep at the end: a pass interrupted halfway then leaves the rows
     * it already applied clean, and the ones it did not still dirty for the
     * next pass, instead of replaying every write.
     */
    suspend fun apply(
        context: Context,
        account: Account,
        repository: CalinoRepository,
        changes: List<Change>,
    ): Report {
        var report = Report()
        val zone = ZoneId.systemDefault()

        for (change in changes) {
            val existing = change.eventId?.let { id ->
                repository.snapshot().events.firstOrNull { it.id == id }
            }

            report = when {
                change.deleted -> {
                    // A row a foreign app both created and deleted before we
                    // ever saw it never reached Calino, so there is nothing to
                    // delete but the tombstone.
                    val result = if (existing == null) {
                        null
                    } else {
                        repository.deleteEvent(
                            id = existing.id,
                            scope = scopeFor(existing),
                            occurrenceDate = existing.date ?: existing.start?.toLocalDate(),
                        )
                    }
                    if (result is WriteResult.Rejected) {
                        // Refused: leave the tombstone for the next pass
                        // rather than purging a deletion that never happened.
                        report.copy(rejected = report.rejected + 1)
                    } else {
                        purge(context, account, change.rowId)
                        report.copy(deleted = report.deleted + 1)
                    }
                }

                change.eventId == null -> {
                    val result = repository.addEvent(insertOf(change, zone))
                    if (result is WriteResult.Rejected) {
                        report.copy(rejected = report.rejected + 1)
                    } else {
                        // The foreign row carries no identity Calino can key
                        // off. Rather than back-fill one, it is dropped and
                        // the next projection inserts the canonical row.
                        purge(context, account, change.rowId)
                        report.copy(added = report.added + 1)
                    }
                }

                // Dirty, but naming a record the snapshot does not hold: most
                // often an edit that arrived while the account was being
                // removed. Left alone; the next projection deletes the row.
                existing == null -> report.copy(unresolved = report.unresolved + 1)

                else -> {
                    val result = repository.updateEvent(existing.id, editOf(existing, change, zone))
                    unmark(context, account, change.rowId)
                    if (result is WriteResult.Rejected) {
                        report.copy(rejected = report.rejected + 1)
                    } else {
                        report.copy(updated = report.updated + 1)
                    }
                }
            }
        }
        return report
    }

    // ---------------------------------------------------------------- mapping

    /**
     * An edit of a record Calino already holds.
     *
     * Everything the provider has no column for is taken from [existing] and
     * not from the row -- this is the "patch, never rebuild" rule of the
     * review doc, applied one level above `ICalPatcher`. If a field is absent
     * here, it is because `CalendarContract` genuinely cannot express it, and
     * losing it would be the defect.
     */
    internal fun editOf(existing: CalEvent, change: Change, zone: ZoneId): NewEvent {
        val placement = placement(change, zone)
        return NewEvent(
            title = change.title.ifBlank { existing.title },
            date = placement.date,
            startTime = placement.startTime,
            durationMinutes = placement.durationMinutes,
            allDay = change.allDay,
            color = existing.color,
            recurrence = existing.recurrence,
            location = change.location?.ifBlank { null },
            notes = change.description?.ifBlank { null },
            attendees = existing.attendees,
            calendarId = existing.calendarId,
            availability = if (change.free) Availability.Free else Availability.Busy,
            categories = existing.categories,
            reminders = change.reminderMinutes.map { Reminder(it) },
            travelTimeMinutes = existing.travelTimeMinutes,
            relatedTo = existing.relatedTo,
            url = existing.url,
            uid = existing.uid,
            href = existing.href,
            etag = existing.etag,
            recurrenceId = existing.recurrenceId,
            recurrenceDate = existing.recurrenceDate,
            sequence = existing.sequence,
            endDate = placement.endDate,
            // The provider was never given the rule, so an edit through it
            // cannot have changed one.
            recurrenceChanged = false,
            recurrenceScope = scopeFor(existing),
        )
    }

    /** A record Calino has never seen, created in another calendar app. */
    internal fun insertOf(change: Change, zone: ZoneId): NewEvent {
        val placement = placement(change, zone)
        return NewEvent(
            title = change.title.ifBlank { "(no title)" },
            date = placement.date,
            startTime = placement.startTime,
            durationMinutes = placement.durationMinutes,
            allDay = change.allDay,
            location = change.location?.ifBlank { null },
            notes = change.description?.ifBlank { null },
            calendarId = change.calendarId,
            availability = if (change.free) Availability.Free else Availability.Busy,
            reminders = change.reminderMinutes.map { Reminder(it) },
            endDate = placement.endDate,
        )
    }

    private data class Placement(
        val date: java.time.LocalDate,
        val startTime: java.time.LocalTime?,
        val durationMinutes: Int?,
        val endDate: java.time.LocalDate?,
    )

    /**
     * Undoes the projection's placement rules.
     *
     * All-day rows are anchored at UTC midnight with an exclusive end, which
     * is the provider's contract and not a choice either side may vary; timed
     * rows are wall-clock in the device zone, because a [CalEvent] carries no
     * zone of its own.
     */
    private fun placement(change: Change, zone: ZoneId): Placement = if (change.allDay) {
        val first = Instant.ofEpochMilli(change.startMillis).atZone(ZoneOffset.UTC).toLocalDate()
        // DTEND is exclusive on the wire and inclusive in the model.
        val last = Instant.ofEpochMilli(change.endMillis).atZone(ZoneOffset.UTC)
            .toLocalDate().minusDays(1)
        Placement(
            date = first,
            startTime = null,
            durationMinutes = null,
            endDate = if (last.isAfter(first)) last else null,
        )
    } else {
        val start = Instant.ofEpochMilli(change.startMillis).atZone(zone).toLocalDateTime()
        Placement(
            date = start.toLocalDate(),
            startTime = start.toLocalTime(),
            durationMinutes = ((change.endMillis - change.startMillis) / 60_000L)
                .coerceAtLeast(0).toInt(),
            endDate = null,
        )
    }

    /**
     * Which occurrences an inbound edit reaches.
     *
     * Always one, for an expanded occurrence. Projected rows are standalone
     * and carry no `RRULE` (see `docs/calendar-provider.md`), so a foreign app
     * has no way to express "and every following Tuesday" and must not be read
     * as having done so.
     */
    private fun scopeFor(event: CalEvent): RecurrenceEditScope =
        if (event.recurrenceId != null || event.recurrenceDate != null) {
            RecurrenceEditScope.This
        } else {
            RecurrenceEditScope.All
        }

    /**
     * An iCalendar `DURATION`, in milliseconds, or null when it is not one we
     * understand. Weeks and the date/time parts only; a duration is minutes on
     * the Calino side either way.
     */
    internal fun durationMillis(duration: String?): Long? {
        val text = duration?.trim()?.uppercase() ?: return null
        val match = DurationPattern.matchEntire(text) ?: return null
        val (sign, weeks, days, hours, minutes, seconds) = match.destructured
        val total = weeks.digits() * 7 * 86_400 +
            days.digits() * 86_400 +
            hours.digits() * 3_600 +
            minutes.digits() * 60 +
            seconds.digits()
        if (total == 0L) return null
        return total * 1_000 * if (sign == "-") -1 else 1
    }

    private fun String.digits(): Long = dropLast(1).toLongOrNull() ?: 0L

    // --------------------------------------------------------------- provider

    private fun reminders(context: Context, eventRowId: Long): List<Int> {
        val values = mutableListOf<Int>()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventRowId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (!cursor.isNull(0)) values += cursor.getInt(0)
            }
        }
        // Negative minutes mean "after the start", which ICalAlarms does not
        // model; dropping them is the same call the projection makes.
        return values.filter { it >= 0 }.distinct().sorted()
    }

    /** Provider calendar row id to Calino calendar id, for one account. */
    private fun ownedCalendars(context: Context, account: Account): Map<Long, String> {
        val owned = mutableMapOf<Long, String>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars._SYNC_ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND" +
                " ${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
            arrayOf(account.type, account.name),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val syncId = cursor.getString(1) ?: continue
                owned[cursor.getLong(0)] = syncId
            }
        }
        return owned
    }

    /**
     * Marks a row as ingested: no longer dirty, and with its content hash
     * cleared so the next projection rewrites it from Calino's snapshot.
     *
     * Clearing the hash is what makes a rejected write revert itself. It also
     * costs an accepted edit one rewrite, which is the price of having exactly
     * one path back to agreement rather than two.
     */
    private fun unmark(context: Context, account: Account, rowId: Long) {
        runCatching {
            val values = ContentValues().apply {
                put(CalendarContract.Events.DIRTY, 0)
                putNull(ProviderIdentity.Columns.Hash)
            }
            context.contentResolver.update(
                ContentUris.withAppendedId(
                    syncAdapterUri(CalendarContract.Events.CONTENT_URI, account),
                    rowId,
                ),
                values,
                null,
                null,
            )
        }
    }

    /** Removes a row outright. Only a sync-adapter delete actually removes one. */
    private fun purge(context: Context, account: Account, rowId: Long) {
        runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(
                    syncAdapterUri(CalendarContract.Events.CONTENT_URI, account),
                    rowId,
                ),
                null,
                null,
            )
        }
    }

    private fun syncAdapterUri(uri: Uri, account: Account): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
        .build()

    private const val DefaultDurationMillis = 60L * 60 * 1000

    private val DurationPattern =
        Regex("""([+-]?)P(\d+W)?(\d+D)?(?:T(\d+H)?(\d+M)?(\d+S)?)?""")
}
