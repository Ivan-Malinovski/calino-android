package calino.malinov.ski.notify

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.model.lastCoveredDate
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.model.placementDate
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.reminderCalendarIds
import calino.malinov.ski.util.meetingLink
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
 * How often an overdue task with a reminder is mentioned again.
 *
 * Slots are fixed wall-clock times, not "N hours after the last one": a plan
 * rebuilt after every sync must land on the same slots, or each sync would
 * look like a schedule change and a reboot would restart the clock.
 */
enum class TaskNagCadence { Off, Daily, Hourly }

/**
 * Which posted nags no longer have a reason to be in the shade.
 *
 * A task that was being nagged under the stored schedule and has no nag in the
 * new plan is done, rescheduled, deleted, or the setting was turned off.
 */
data class NagResolution(val notificationIds: List<Int>, val allGone: Boolean) {
    companion object {
        fun between(stored: List<ReminderFiring>, planned: List<ReminderFiring>): NagResolution {
            val before = stored.filter { it.nag }
            if (before.isEmpty()) return NagResolution(emptyList(), allGone = false)
            val still = planned.filter { it.nag }.map { it.recordId }.toSet()
            return NagResolution(
                notificationIds = before.filter { it.recordId !in still }.map { it.notificationId }.distinct(),
                allGone = still.isEmpty(),
            )
        }
    }
}

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
    /** Structured display facts; null in schedules written before localization. */
    val displayAllDay: Boolean? = null,
    val displayDate: LocalDate? = null,
    val displayTime: LocalTime? = null,
    val displayCategory: String? = null,
    /** Event LOCATION, retained so a receiver can offer directions after process death. */
    val location: String? = null,
    /** Meeting link, retained so the notification can offer Join after process death. */
    val meetingUrl: String? = null,
    val minutesBefore: Int,
    val anchor: Instant,
    /**
     * A repeat mention of a task that is already overdue. One per slot, so
     * [key] differs, but they all share [notificationId] and replace each other.
     */
    val nag: Boolean = false,
) {
    /**
     * Notification id and alarm request code.
     *
     * Derived from [key] rather than counted, because a counter would not
     * survive the reboot that this whole design exists to survive. A collision
     * means one reminder replaces another's notification, which is survivable;
     * a counter that restarts at zero after a reboot is not.
     */
    val notificationId: Int
        get() = (if (nag) "nag:$recordId" else key).hashCode() and 0x7fffffff
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
    /**
     * Calendars whose event reminders another calendar app has been made
     * responsible for, and which Calino therefore skips.
     *
     * Empty unless the person has said so. The convention across the Android
     * CalDAV ecosystem is a division of labour between two apps -- DAVx5
     * writes reminder rows into the provider and deliberately never notifies,
     * leaving that to whichever calendar app the person uses -- and it works
     * because a sync adapter has no UI of its own. Calino is both halves at
     * once, so nothing about the projection can tell it which app should
     * speak. Guessing from "is a calendar app installed" gets it wrong in both
     * directions: an installed app may never post a notification, and the
     * person may want Calino's regardless. So this is asked, not inferred.
     *
     * The same set also carries the inbound case, which is why this is named
     * for who owns delivery rather than for the projection. A calendar
     * imported from the device belongs to the app that syncs it, and that app
     * is already notifying for it -- so an imported calendar is in here by
     * default and leaves only when the person asks Calino to speak too.
     *
     * **Events only.** A task has no provider representation at all, so
     * handing its reminder over would hand it to nobody. See
     * `docs/calendar-provider.md`.
     */
    val providerOwnedCalendarIds: Set<String> = emptySet(),
    /**
     * Device-only reminders on events whose calendar cannot be written to,
     * keyed by [localReminderKey]. See [LocalReminderStore].
     *
     * An entry replaces the event's own reminders and is planned for any
     * calendar in [localReminderCalendarIds]: the person set it on this phone
     * for this event, so neither a feed's alarm mute nor another app's
     * ownership of the calendar's own reminders speaks for it.
     */
    val localEventReminders: Map<String, List<Reminder>> = emptyMap(),
    val localReminderCalendarIds: Set<String> = emptySet(),
    /** Repeat mentions of overdue tasks that have a reminder; tasks only. */
    val taskNagCadence: TaskNagCadence = TaskNagCadence.Off,
    /** The one slot of a [TaskNagCadence.Daily] nag. */
    val nagDailyAt: LocalTime = LocalTime.of(9, 0),
    /** First and last hourly slot of the day, so nothing buzzes at night. */
    val nagFromHour: Int = 8,
    val nagToHour: Int = 21,
    /**
     * Hourly slots are planned this far ahead only. Background sync re-plans
     * and extends them, and a long run of them must not crowd real reminders
     * out of [maxFirings].
     */
    val nagHourlyHorizon: Duration = Duration.ofHours(24),
    val maxNags: Int = 60,
)

object ReminderPlanner {

    /** Convenience overload: visibility and mute are read from the snapshot's calendars. */
    fun plan(
        snapshot: CalinoSnapshot,
        now: Instant,
        zone: ZoneId,
        options: ReminderPlanOptions = ReminderPlanOptions(),
    ): List<ReminderFiring> = plan(
        events = snapshot.events,
        tasks = snapshot.tasks,
        visibleCalendarIds = reminderCalendarIds(snapshot.calendars),
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
                .map { event ->
                    val local = options.localEventReminders[localReminderKey(event)]
                        ?.takeIf { event.calendarId in options.localReminderCalendarIds }
                    if (local != null) event.copy(reminders = local) to true else event to false
                }
                .filter { (event, _) -> event.reminders.isNotEmpty() }
                .filter { (event, local) ->
                    local || (event.calendarId in visibleCalendarIds &&
                        event.calendarId !in options.providerOwnedCalendarIds)
                }
                .forEach { (event, _) -> firings += event.firings(now, until, zone, options) }
        }

        val reminderTasks = if (options.taskRemindersEnabled) {
            tasks.asSequence()
                .filter { task -> !task.done && task.reminder != null &&
                    (task.reminder.absoluteAt != null || if (task.reminder.relativeToStart) task.startDate != null else task.due != null) }
                .filter { it.calendarId in visibleCalendarIds }
                .toList()
        } else emptyList()
        reminderTasks.forEach { task -> firings += task.firings(now, until, zone) }

        // Deduplicate on the key, keeping the earliest, then order
        // deterministically: the same snapshot must always produce the same
        // list, or every sync would look like a schedule change.
        val planned = firings
            .groupBy { it.key }
            .map { (_, group) -> group.minBy { it.at } }
            .sortedWith(compareBy({ it.at }, { it.key }))
            .take(options.maxFirings)

        // Nags are added after the cap and have their own, so a run of them
        // can never push a real reminder out of the plan.
        if (options.taskNagCadence == TaskNagCadence.Off) return planned
        val nags = reminderTasks
            .flatMap { task -> task.nags(now, zone, options) }
            .sortedWith(compareBy({ it.at }, { it.key }))
            .take(options.maxNags)
        return planned + nags
    }

    private fun CalEvent.firings(
        now: Instant,
        until: Instant,
        zone: ZoneId,
        options: ReminderPlanOptions,
    ): List<ReminderFiring> = occurrenceStarts(now, until, zone, options)
        .flatMap { start ->
            val anchor = start.atZone(zone).toInstant()
            reminders.flatMap { reminder -> (0..reminder.repeatCount.coerceIn(0, 24)).map { repeatIndex ->
                ReminderFiring(
                    key = "event:$id:${reminder.minutesBefore}:${start.toLocalDate().toEpochDay()}:$repeatIndex",
                    at = anchor.minusSeconds(reminder.minutesBefore * 60L)
                        .plusSeconds(repeatIndex.toLong() * reminder.repeatIntervalMinutes * 60L),
                    kind = ReminderKind.Event,
                    recordId = id,
                    uid = uid,
                    occurrenceDay = start.toLocalDate().toEpochDay(),
                    title = title,
                    subtitle = eventSubtitle(start, zone),
                    displayAllDay = allDay,
                    displayDate = start.toLocalDate(),
                    displayTime = start.toLocalTime(),
                    location = location?.trim()?.takeIf { it.isNotEmpty() },
                    meetingUrl = meetingLink(this@firings)?.url,
                    minutesBefore = reminder.minutesBefore,
                    anchor = anchor,
                )
            } }
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
    ): List<ReminderFiring> {
        val reminder = reminder ?: return emptyList()
        val dueDate = due
        val anchorDate = if (reminder.relativeToStart) startDate else dueDate
        val (anchor, baseAt) = reminderTimes(zone) ?: return emptyList()
        return (0..reminder.repeatCount.coerceIn(0, 24)).map { repeatIndex -> ReminderFiring(
            key = if (reminder.absoluteAt != null) {
                // RFC 5545: an absolute alarm on a recurring VTODO fires once,
                // even when the mapper supplied several expanded occurrences.
                "task:${uid ?: id}:absolute:${reminder.absoluteAt}:$repeatIndex"
            } else "task:$id:${reminder.minutesBefore}:${reminder.relativeToStart}:$repeatIndex",
            at = baseAt.plusSeconds(repeatIndex.toLong() * reminder.repeatIntervalMinutes * 60L),
            kind = ReminderKind.Task,
            recordId = id,
            uid = uid,
            occurrenceDay = anchorDate?.toEpochDay(),
            title = title,
            subtitle = dueDate?.let { taskSubtitle(it, now, zone) } ?: "Task reminder",
            displayAllDay = dueTime == null,
            displayDate = dueDate,
            displayTime = dueTime,
            displayCategory = category,
            minutesBefore = reminder.minutesBefore,
            anchor = anchor,
        ) }.filter { it.at > now && it.at <= until }
    }

    /** The reminder's anchor and its first trigger, or null when it has neither. */
    private fun CalTask.reminderTimes(zone: ZoneId): Pair<Instant, Instant>? {
        val reminder = reminder ?: return null
        val anchorDate = if (reminder.relativeToStart) startDate else due
        val anchorTime = if (reminder.relativeToStart) startTime else dueTime
        // RFC 5545 anchors a DATE-valued VTODO DUE at 00:00, not the
        // user-facing all-day reminder hour used for calendar events.
        val anchor = anchorDate?.atTime(anchorTime ?: LocalTime.MIDNIGHT)?.atZone(zone)?.toInstant()
            ?: reminder.absoluteAt ?: return null
        return anchor to (reminder.absoluteAt ?: anchor.minusSeconds(reminder.minutesBefore * 60L))
    }

    /**
     * When the task becomes overdue, which is also when nagging may start.
     *
     * Matches the Tasks list: a task with a due time is late from that moment,
     * a date-only task only once its day is over, so a task due today is not
     * mentioned on the morning it is due. Never earlier than the last firing
     * of its own reminder.
     */
    private fun CalTask.overdueFrom(zone: ZoneId): Instant? {
        val reminder = reminder ?: return null
        val (anchor, baseAt) = reminderTimes(zone) ?: return null
        val lastReminder = baseAt.plusSeconds(
            reminder.repeatCount.coerceIn(0, 24).toLong() * reminder.repeatIntervalMinutes * 60L,
        )
        val dueDate = due
        val dueAt = when {
            dueDate == null -> anchor
            dueTime != null -> dueDate.atTime(dueTime).atZone(zone).toInstant()
            else -> dueDate.plusDays(1).atStartOfDay(zone).toInstant()
        }
        return maxOf(dueAt, lastReminder)
    }

    private fun CalTask.nags(
        now: Instant,
        zone: ZoneId,
        options: ReminderPlanOptions,
    ): List<ReminderFiring> {
        val from = overdueFrom(zone) ?: return emptyList()
        val after = maxOf(from, now)
        val reminder = reminder ?: return emptyList()
        val anchorDate = if (reminder.relativeToStart) startDate else due
        val dueDate = due
        return nagSlots(after, now, zone, options).map { slot ->
            ReminderFiring(
                key = "nag:$id:${slot.epochSecond}",
                at = slot,
                kind = ReminderKind.Task,
                recordId = id,
                uid = uid,
                occurrenceDay = anchorDate?.toEpochDay(),
                title = title,
                subtitle = dueDate?.let { "Overdue since ${DateLabel.format(it)}" } ?: "Overdue",
                displayAllDay = dueTime == null,
                displayDate = dueDate,
                displayTime = dueTime,
                displayCategory = category,
                minutesBefore = reminder.minutesBefore,
                anchor = from,
                nag = true,
            )
        }
    }

    /** Slots strictly after [after], from [now], in the local zone. */
    private fun nagSlots(
        after: Instant,
        now: Instant,
        zone: ZoneId,
        options: ReminderPlanOptions,
    ): List<Instant> {
        val daily = options.taskNagCadence == TaskNagCadence.Daily
        val until = now.plus(if (daily) options.horizon else minOf(options.horizon, options.nagHourlyHorizon))
        val firstDay = after.atZone(zone).toLocalDate()
        val lastDay = until.atZone(zone).toLocalDate()
        val times = if (daily) {
            listOf(options.nagDailyAt)
        } else {
            (options.nagFromHour..options.nagToHour).map { LocalTime.of(it, 0) }
        }
        return generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .flatMap { day -> times.map { day.atTime(it).atZone(zone).toInstant() } }
            .filter { it > after && it <= until }
            .toList()
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
