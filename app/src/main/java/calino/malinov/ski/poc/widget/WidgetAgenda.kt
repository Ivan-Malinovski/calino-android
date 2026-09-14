package calino.malinov.ski.poc.widget

import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import calino.malinov.ski.poc.data.repository.taskCalendarIds
import calino.malinov.ski.poc.data.repository.visibleCalendarIds
import calino.malinov.ski.poc.state.tasksDueOn
import calino.malinov.ski.poc.util.CalinoTimeFormat
import calino.malinov.ski.poc.util.EventDateIndex
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the home screen widget shows, decided without any Android in scope.
 *
 * The same reasoning as `notify/ReminderPlan.kt`: a Glance composable cannot be
 * exercised by the plain-JUnit suite, and this runs in whatever process the
 * launcher happens to wake -- often one with no Activity. So the decision of
 * *what* appears lives here as a pure function of a snapshot and a date, and
 * the widget only draws its result.
 *
 * Every rule here is deliberately the same rule a calendar surface applies, and
 * borrowed from the same place: [EventDateIndex] for recurrence and multi-day
 * spans, [tasksDueOn] for due tasks, and the shared visibility helpers. A
 * widget that disagreed with the grid behind it would be worse than no widget.
 */

enum class WidgetRowKind { Event, Task }

/** One line in the widget: an event occurrence, or a task due that day. */
data class WidgetAgendaRow(
    val kind: WidgetRowKind,
    /** As the snapshot currently spells it; the deep link carries [uid] too. */
    val recordId: String,
    val uid: String?,
    val day: LocalDate,
    val title: String,
    /** Null for an all-day event or a task with no due time. */
    val timeLabel: String?,
    /**
     * The occurrence's own start, unformatted. [timeLabel] is this rendered
     * through the user's time format; the layouts need the label, and
     * [markNext] needs the value.
     */
    val startTime: LocalTime? = null,
    val location: String?,
    val color: Long,
    val allDay: Boolean = false,
    val done: Boolean = false,
    /**
     * The next thing today that has not started yet, and the only row a layout
     * is allowed to emphasise. False on every row when the caller passes no
     * clock, and on every day but the first -- "next" on a future day would be
     * that day's first event, which is not the same claim.
     */
    val isNext: Boolean = false,
)

data class WidgetAgendaDay(val date: LocalDate, val rows: List<WidgetAgendaRow>)

data class WidgetAgenda(
    val days: List<WidgetAgendaDay>,
    /** True when the window held nothing at all, so the widget can say so once. */
    val empty: Boolean,
) {
    companion object {
        val Empty = WidgetAgenda(days = emptyList(), empty = true)
    }
}

data class WidgetAgendaOptions(
    /** Today plus the next [dayCount] - 1 days. */
    val dayCount: Int = 2,
    /** Hard ceiling on rows across the whole window; the widget is small. */
    val maxRows: Int = 12,
    val hideCompletedTasks: Boolean = false,
    val showLocations: Boolean = true,
    val timeFormat: CalinoTimeFormat = CalinoTimeFormat.Default,
    /**
     * Wired, and currently gating nothing: the widget shows events and tasks
     * only. Kept so the flags are read in one place if it ever shows more --
     * see the note in HANDOFF.md rather than assuming this is dead weight.
     */
    val journalEnabled: Boolean = false,
    val contactsEnabled: Boolean = false,
)

/** The empty-day string, kept identical to the agenda surface's. */
const val WidgetNothingScheduled = "Nothing scheduled"

object WidgetAgendaBuilder {

    /**
     * @param now the wall clock, used only to mark [WidgetAgendaRow.isNext].
     *   Null leaves every row unmarked, which is what the tests want unless
     *   they are pinning that rule specifically.
     */
    fun build(
        snapshot: CalinoSnapshot,
        today: LocalDate,
        options: WidgetAgendaOptions = WidgetAgendaOptions(),
        now: LocalTime? = null,
    ): WidgetAgenda {
        if (options.dayCount <= 0 || options.maxRows <= 0) return WidgetAgenda.Empty

        val eventIds = visibleCalendarIds(snapshot.calendars)
        val taskIds = taskCalendarIds(snapshot.calendars)
        val index = EventDateIndex.build(snapshot.events.filter { it.calendarId in eventIds })
        val tasks = snapshot.tasks
            .filter { it.calendarId in taskIds }
            .filterNot { options.hideCompletedTasks && it.done }

        var budget = options.maxRows
        val days = mutableListOf<WidgetAgendaDay>()
        var total = 0

        for (offset in 0 until options.dayCount) {
            val date = today.plusDays(offset.toLong())
            val rows = mutableListOf<WidgetAgendaRow>()

            // Same ordering as AgendaScreen's day block: all-day first, then by
            // start time, with the id as the tiebreak so a redraw cannot
            // reshuffle two events that begin at the same minute.
            index.eventsOn(date)
                .sortedWith(
                    compareBy<CalEvent> { !it.allDay }
                        .thenBy { it.start?.toLocalTime() }
                        .thenBy { it.id },
                )
                .forEach { rows += it.row(date, options) }

            // Tasks after events: a task is date-only and has no place in the
            // timed ordering above.
            tasksDueOn(tasks, date).forEach { rows += it.row(date, options) }

            total += rows.size
            val kept = rows.take(budget)
            budget -= kept.size
            days += WidgetAgendaDay(date, kept)
            if (budget == 0) break
        }

        return WidgetAgenda(days = markNext(days, today, now), empty = total == 0)
    }

    /**
     * Marks the earliest event today that has not begun.
     *
     * Events only. A task carries a due time, not a start, and "next" against a
     * deadline means something different enough that bolding it would be a
     * lie. An event already under way is not next either -- it is current, and
     * the row above it being bold while it runs reads as the widget being
     * behind.
     */
    private fun markNext(
        days: List<WidgetAgendaDay>,
        today: LocalDate,
        now: LocalTime?,
    ): List<WidgetAgendaDay> {
        if (now == null) return days
        val first = days.firstOrNull()?.takeIf { it.date == today } ?: return days

        val next = first.rows.firstOrNull { row ->
            row.kind == WidgetRowKind.Event && !row.allDay && row.startsAfter(now)
        } ?: return days

        return days.map { day ->
            if (day != first) day
            else day.copy(rows = day.rows.map { if (it === next) it.copy(isNext = true) else it })
        }
    }

    private fun WidgetAgendaRow.startsAfter(now: LocalTime): Boolean =
        startTime?.isAfter(now) == true

    private fun CalEvent.row(date: LocalDate, options: WidgetAgendaOptions): WidgetAgendaRow {
        // A multi-day event keeps its own start time on its first day only; on
        // a later day the time would be a lie, so it reads as all-day there.
        val startsToday = !allDay && start?.toLocalDate() == date
        val startsAt = start?.takeIf { startsToday }?.toLocalTime()
        return WidgetAgendaRow(
            kind = WidgetRowKind.Event,
            recordId = id,
            uid = uid,
            day = date,
            title = title.ifBlank { "(No title)" },
            timeLabel = startsAt?.let { options.timeFormat.format(it) },
            startTime = startsAt,
            location = location?.takeIf { options.showLocations && it.isNotBlank() },
            color = color,
            allDay = !startsToday,
        )
    }

    private fun CalTask.row(date: LocalDate, options: WidgetAgendaOptions) = WidgetAgendaRow(
        kind = WidgetRowKind.Task,
        recordId = id,
        uid = uid,
        day = date,
        title = title.ifBlank { "(No title)" },
        timeLabel = dueTime?.let { options.timeFormat.format(it) },
        startTime = dueTime,
        location = null,
        color = color,
        done = done,
    )
}
