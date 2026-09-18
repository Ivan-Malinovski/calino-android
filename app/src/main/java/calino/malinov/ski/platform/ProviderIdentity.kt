package calino.malinov.ski.platform

import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.repository.CalinoSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * What Calino puts into the calendar provider, and how a provider row is
 * recognised as ours again afterwards.
 *
 * Kept apart from [CalendarProjection] because it is pure: no `Context`, no
 * `ContentResolver`, nothing that needs a device. That is what lets the
 * mapping and the hash be unit-tested, which matters more here than usual --
 * a wrong hash is invisible until it churns every alarm on the device, and a
 * wrong identity column is invisible until an inbound edit lands on the wrong
 * record.
 *
 * See `docs/calendar-provider.md` for the ownership rules this expresses.
 */
object ProviderIdentity {

    /**
     * How far either side of today one-off events are projected.
     *
     * The same numbers the sister app uses. Past events stay in range because
     * a calendar that begins at today reads as broken in another app, and
     * because widgets and Android Auto show "earlier today".
     */
    const val PastDays: Long = 365
    const val FutureDays: Long = 730

    private const val DayMillis = 24L * 60 * 60 * 1000

    /**
     * The sync columns, named once.
     *
     * `_SYNC_ID` is [CalEvent.id] and nothing else. For an occurrence that id
     * is already `uid@instant` (`ICalMapper.occurrenceId`), so it names the
     * occurrence without a second identity scheme and without the provider's
     * `ORIGINAL_ID` machinery. The rest are the fields an inbound edit needs
     * in order to become a conditional write, carried on the row so ingest
     * never needs a side table that could fall out of step with it.
     */
    object Columns {
        /** Content hash; lets a pass skip rows that did not change. */
        const val Hash = android.provider.CalendarContract.Events.SYNC_DATA1
        /** CalDAV resource URL. */
        const val Href = android.provider.CalendarContract.Events.SYNC_DATA2
        /** Server ETag, for the conditional write. */
        const val Etag = android.provider.CalendarContract.Events.SYNC_DATA3
        /** iCalendar UID. */
        const val Uid = android.provider.CalendarContract.Events.SYNC_DATA4
        /** RECURRENCE-ID, as text, when this row is one occurrence of a series. */
        const val RecurrenceId = android.provider.CalendarContract.Events.SYNC_DATA5
    }

    /** A calendar to create under the Android account standing for [accountId]. */
    data class ProjectedCalendar(
        val id: String,
        val accountId: String,
        val name: String,
        val color: Int,
        val readOnly: Boolean,
    )

    /** One provider event row, already reduced to the fields we write. */
    data class ProjectedEvent(
        val id: String,
        val calendarId: String,
        val title: String,
        val description: String?,
        val location: String?,
        val startMillis: Long,
        val endMillis: Long,
        val allDay: Boolean,
        val timeZone: String,
        val free: Boolean,
        val reminderMinutes: List<Int>,
        val href: String?,
        val etag: String?,
        val uid: String?,
        val recurrenceId: String?,
    ) {
        /**
         * Content hash over exactly the fields above.
         *
         * Deliberately excludes [href], [etag], [uid] and [recurrenceId]: an
         * ETag changes on every server write, and rewriting an event because
         * its ETag moved would re-create its reminder rows and hand the
         * provider a fresh alarm for an event that did not change.
         */
        val hash: String = hash(
            calendarId, title, description.orEmpty(), location.orEmpty(),
            startMillis.toString(), endMillis.toString(), allDay.toString(),
            timeZone, free.toString(), reminderMinutes.joinToString(","),
        )
    }

    /** Everything one projection pass should make true. */
    data class Projection(
        val calendars: List<ProjectedCalendar>,
        val events: List<ProjectedEvent>,
    )

    /**
     * FNV-1a, 32 bits, over the given fields joined by a separator that cannot
     * occur in them unescaped.
     *
     * A collision costs one missed update, on an event whose content changed
     * to another value hashing identically, and it self-corrects on the next
     * edit. That is a fair price for a column the provider can index.
     */
    fun hash(vararg fields: String): String {
        var value = -0x7ee3623b // 0x811c9dc5
        for (field in fields) {
            for (character in field) {
                value = (value xor character.code) * 0x01000193
            }
            value = (value xor 0x1f) * 0x01000193
        }
        return (value.toLong() and 0xffffffffL).toString(16)
    }

    /**
     * Reduces a snapshot to what belongs in the provider.
     *
     * [optedIn] is the set of Calino calendar ids the person has opted in;
     * nothing outside it is projected, and neither is a calendar the snapshot
     * has hidden. [accounts] is what attributes a calendar to an Android
     * account -- a calendar whose account is gone has no owner and is dropped
     * rather than orphaned under someone else's.
     */
    fun project(
        snapshot: CalinoSnapshot,
        accounts: List<CalDavAccount>,
        optedIn: Set<String>,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): Projection {
        val accountByCalendar = buildMap {
            for (account in accounts) {
                for (calendar in account.calendars) put(calendar.id, account.id)
            }
        }

        val calendars = snapshot.calendars
            // An imported calendar is never projected back. It already lives
            // in the provider, owned by another app; re-publishing it under
            // Calino's account would show the person two of everything and
            // grow by one copy per pass. AndroidCalendarSource excludes our
            // own account type on the way in, and this is the same guard from
            // the other side -- stated rather than left to the incidental
            // fact that an imported calendar has no CalDAV account to map to.
            .filterNot { AndroidCalendarId.isImported(it.id) }
            .filter { it.id in optedIn && it.visible }
            .mapNotNull { calendar ->
                val accountId = accountByCalendar[calendar.id] ?: return@mapNotNull null
                ProjectedCalendar(
                    id = calendar.id,
                    accountId = accountId,
                    name = calendar.name,
                    color = calendar.color.toInt(),
                    readOnly = calendar.readOnly,
                )
            }

        val projectable = calendars.map { it.id }.toSet()
        val windowStart = now.toEpochMilli() - PastDays * DayMillis
        val windowEnd = now.toEpochMilli() + FutureDays * DayMillis

        val events = snapshot.events
            .filter { it.calendarId in projectable }
            .mapNotNull { event -> projectEvent(event, zone) }
            .filter { it.endMillis >= windowStart && it.startMillis <= windowEnd }

        return Projection(calendars, events)
    }

    /**
     * One event, or null when it has no placement to project.
     *
     * No `RRULE` is ever written, and that is not an omission. `ICalMapper`
     * expands every series before the snapshot exists, so a snapshot event is
     * always a single occurrence carrying `ICalMapper.occurrenceId` as its id.
     * Writing the series rule onto each of those rows would ask the provider
     * to re-expand a series once per occurrence. The cost of expanding here is
     * that the projection reaches only as far as the fetch window; the gain is
     * that an inbound edit names one occurrence, which is exactly what
     * `RecurrenceEditScope.This` needs.
     */
    fun projectEvent(event: CalEvent, zone: ZoneId): ProjectedEvent? {
        val allDay = event.allDay
        val startMillis: Long
        val endMillis: Long

        if (allDay) {
            val date = event.date ?: return null
            // The provider's all-day contract: midnight UTC, zone "UTC". Calino
            // stores the last covered day inclusively; DTEND is exclusive.
            startMillis = utcMidnight(date)
            endMillis = utcMidnight((event.endDate ?: date).plusDays(1))
        } else {
            val start = event.start ?: return null
            startMillis = start.atZone(zone).toInstant().toEpochMilli()
            endMillis = startMillis + (event.durationMinutes ?: 0).coerceAtLeast(0) * 60_000L
        }

        return ProjectedEvent(
            id = event.id,
            calendarId = event.calendarId,
            title = event.title.ifBlank { "(no title)" },
            description = event.notes?.takeIf { it.isNotBlank() },
            location = event.location?.takeIf { it.isNotBlank() },
            startMillis = startMillis,
            endMillis = endMillis,
            allDay = allDay,
            timeZone = if (allDay) "UTC" else zone.id,
            free = event.availability == Availability.Free,
            // Sorted and de-duplicated so two equivalent reminder lists hash
            // alike; a reorder upstream must not rewrite the row.
            reminderMinutes = event.reminders.map { it.minutesBefore }.distinct().sorted(),
            href = event.href,
            etag = event.etag,
            uid = event.uid,
            recurrenceId = event.recurrenceId?.toString() ?: event.recurrenceDate?.toString(),
        )
    }

    private fun utcMidnight(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
