package calino.malinov.ski.notify

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.lastCoveredDate
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.model.placementDate
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.visibleCalendarIds
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turning records into the moments a notification must be posted.
 *
 * This file is deliberately free of Android. A reminder has to be rescheduled
 * from a boot receiver, where there is no Activity, no ViewModel and no
 * repository, and it has to be testable in the project's plain-JUnit suite,
 * where touching a framework class throws. So the decision of *what* fires and
 * *when* lives here, as a pure function of a snapshot and a clock, and every
 * Android-facing part of the feature only reads its result.
 */

enum class ReminderKind { Event, Task }

/**
 * One notification to post at one moment.
 *
 * [anchor] is the thing being reminded about -- the event start, or the task's
 * due moment -- and [at] is [anchor] less the lead time. Both are kept: the
 * notification shows the anchor while the alarm is set for [at].
 */
data class ReminderFiring(
    /** Stable across syncs and reboots; the dedup and delivery-watermark key. */
    val key: String,
    val at: Instant,
    val kind: ReminderKind,
    /** `CalEvent.id` / `CalTask.id` as the snapshot currently spells it. */
    val recordId: String,
    /** Series UID, which survives a re-expansion that changes [recordId]. */
    val uid: String? = null,
    /** Epoch day of the occurrence; disambiguates one instance of a series. */
    val occurrenceDay: Long? = null,
    val title: String,
    val subtitle: String,
    /** Event LOCATION, retained so a receiver can offer directions after process death. */
    val location: String? = null,
    val minutesBefore: Int,
    val anchor: Instant,
) {
    /**
     * Notification id and alarm request code.
     *
     * Derived from [key] rather than counted, because a counter would not
     * survive the reboot that this whole design exists to survive. A collision
     * means one reminder replaces another's notification, which is survivable;
     * a counter that restarts at zero after a reboot is not.
     */
    val notificationId: Int get() = key.hashCode() and 0x7fffffff
}

data class ReminderPlanOptions(
    val eventRemindersEnabled: Boolean = true,
    val taskRemindersEnabled: Boolean = true,
    /**
     * When a record with no time of day is treated as happening.
     *
     * All-day records must not anchor at midnight: a "0 minutes before"
     * reminder on an all-day event would then arrive in the middle of the
     * night before.
     */
    val allDayAnchor: LocalTime = LocalTime.of(9, 0),
    val horizon: Duration = Duration.ofDays(7),
    val maxFirings: Int = 200,
)

object ReminderPlanner {

    /** Convenience overload: visibility is read from the snapshot's calendars. */
    fun plan(
        snapshot: CalinoSnapshot,
        now: Instant,
        zone: ZoneId,
        options: ReminderPlanOptions = ReminderPlanOptions(),
    ): List<ReminderFiring> = plan(
        events = snapshot.events,
        tasks = snapshot.tasks,
        visibleCalendarIds = visibleCalendarIds(snapshot.calendars),
        now = now,
        zone = zone,
        options = options,
    )

    /**
     * Every reminder due between [now] and [now] plus the horizon, in order.
     *
     * Firings already in the past are dropped. Whether one was *delivered* is
     * not asked here: only the durable schedule knows that, because only it
     * survives the process.
     */
    fun plan(
        events: List<CalEvent>,
        tasks: List<CalTask>,
        visibleCalendarIds: Set<String>,
        now: Instant,
        zone: ZoneId,
        options: ReminderPlanOptions = ReminderPlanOptions(),
    ): List<ReminderFiring> {
        val until = now.plus(options.horizon)
        val firings = mutableListOf<ReminderFiring>()

        if (options.eventRemindersEnabled) {
            events.asSequence()
                .filter { it.reminders.isNotEmpty() }
                .filter { it.calendarId in visibleCalendarIds }
                .forEach { event -> firings += event.firings(now, until, zone, options) }
        }

        if (options.taskRemindersEnabled) {
            tasks.asSequence()
                .filter { !it.done && it.reminder != null && it.due != null }
                .filter { it.calendarId in visibleCalendarIds }
                .forEach { task -> firings += task.firings(now, until, zone, options) }
        }

        // Deduplicate on the key, keeping the earliest, then order
        // deterministically: the same snapshot must always produce the same
        // list, or every sync would look like a schedule change.
        return firings
            .groupBy { it.key }
            .map { (_, group) -> group.minBy { it.at } }
            .sortedWith(compareBy({ it.at }, { it.key }))
            .take(options.maxFirings)
    }

    private fun CalEvent.firings(
        now: Instant,
        until: Instant,
        zone: ZoneId,
        options: ReminderPlanOptions,
    ): List<ReminderFiring> = occurrenceStarts(now, until, zone, options)
        .flatMap { start ->
            val anchor = start.atZone(zone).toInstant()
            reminders.map { reminder ->
                ReminderFiring(
                    key = "event:$id:${reminder.minutesBefore}:${start.toLocalDate().toEpochDay()}",
                    at = anchor.minusSeconds(reminder.minutesBefore * 60L),
                    kind = ReminderKind.Event,
                    recordId = id,
                    uid = uid,
                    occurrenceDay = start.toLocalDate().toEpochDay(),
                    title = title,
                    subtitle = eventSubtitle(start, zone),
                    location = location?.trim()?.takeIf { it.isNotEmpty() },
                    minutesBefore = reminder.minutesBefore,
                    anchor = anchor,
                )
            }
        }
        .filter { it.at > now && it.at <= until }

    /**
     * The local start of each occurrence of this event inside the window.
     *
     * `ICalMapper` expands a series inside the fetch window and gives every
     * occurrence its own id, so the common case is a single concrete start and
     * this function does not parse `RRULE` at all. The day walk below is for
     * the records that arrive unexpanded -- the fixture repository, and the
     * unbounded-master fallthrough described in TODO item 5 -- and it uses
     * [CalEvent.occursOn], the same predicate the calendar grid uses, so a
     * notification cannot land on a day the grid does not show. It therefore
     * inherits that engine's gaps (no `INTERVAL`, no `COUNT`, no `EXDATE`);
     * that is item 5's to fix, in one place, for both callers.
     */
    private fun CalEvent.occurrenceStarts(
        now: Instant,
        until: Instant,
        zone: ZoneId,
        options: ReminderPlanOptions,
    ): List<java.time.LocalDateTime> {
        val timeOfDay = if (allDay) options.allDayAnchor else start?.toLocalTime() ?: options.allDayAnchor
        val anchorDate = placementDate() ?: return emptyList()

        if (recurrence.isNullOrBlank()) {
            val startAt = if (allDay) anchorDate.atTime(timeOfDay) else start ?: return emptyList()
            return listOf(startAt)
        }

        // Walk back one day: a reminder for tomorrow's occurrence can be due
        // today, and the window is measured on the firing, not the day.
        val first = now.atZone(zone).toLocalDate().minusDays(1)
        val last = until.atZone(zone).toLocalDate().plusDays(1)
        val spanEnd = lastCoveredDate()
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .filter { day -> occursOn(day) }
            // `occursOn` is also true for the days a multi-day span merely
            // covers. Those are not fresh occurrences and must not each get
            // their own reminder.
            .filter { day -> spanEnd == null || day == anchorDate || day.isAfter(spanEnd) }
            .map { day -> day.atTime(timeOfDay) }
            .toList()
    }

    private fun CalEvent.eventSubtitle(start: java.time.LocalDateTime, zone: ZoneId): String {
        val head = if (allDay) "All day" else TimeLabel.format(start.toLocalTime())
        return listOfNotNull(head, location?.takeIf { it.isNotBlank() }).joinToString(" · ")
    }

    private fun CalTask.firings(
        now: Instant,
        until: Instant,
        zone: ZoneId,
        options: ReminderPlanOptions,
    ): List<ReminderFiring> {
        val reminder = reminder ?: return emptyList()
        val dueDate = due ?: return emptyList()
        val anchorLocal = dueDate.atTime(dueTime ?: options.allDayAnchor)
        val anchor = anchorLocal.atZone(zone).toInstant()
        val firing = ReminderFiring(
            key = "task:$id:${reminder.minutesBefore}",
            at = anchor.minusSeconds(reminder.minutesBefore * 60L),
            kind = ReminderKind.Task,
            recordId = id,
            uid = uid,
            occurrenceDay = dueDate.toEpochDay(),
            title = title,
            subtitle = taskSubtitle(dueDate, now, zone),
            minutesBefore = reminder.minutesBefore,
            anchor = anchor,
        )
        return listOf(firing).filter { it.at > now && it.at <= until }
    }

    private fun CalTask.taskSubtitle(dueDate: LocalDate, now: Instant, zone: ZoneId): String {
        val today = now.atZone(zone).toLocalDate()
        val whenLabel = when (dueDate) {
            today -> "Due today"
            today.plusDays(1) -> "Due tomorrow"
            else -> "Due ${DateLabel.format(dueDate)}"
        }
        val time = dueTime?.let { TimeLabel.format(it) }
        return listOfNotNull(whenLabel, time, category?.takeIf { it.isNotBlank() }).joinToString(" · ")
    }

    /**
     * Labels are plain and 24-hour here on purpose: this runs where the clock
     * preference and the composition are both out of reach. A surface that has
     * the preference is free to reformat from [ReminderFiring.anchor].
     */
    private val TimeLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DateLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
}
