package calino.malinov.ski.widget

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.taskCalendarIds
import calino.malinov.ski.data.repository.visibleCalendarIds
import calino.malinov.ski.state.tasksDueOn
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.EventDateIndex
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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

/** What a widget is for. */
enum class WidgetContent {
    /** Events and tasks together, as the day runs. */
    Agenda,

    /**
     * Tasks alone, and with them everything already late. A deadline that has
     * passed is the thing a task list exists to surface, so [WidgetContent] is
     * what decides whether [WidgetAgenda.overdue] is gathered at all -- an
     * agenda is a view of a day and has no business showing last Tuesday.
     */
    Tasks,
}

data class WidgetAgenda(
    val days: List<WidgetAgendaDay>,
    /** True when the window held nothing at all, so the widget can say so once. */
    val empty: Boolean,
    /**
     * Tasks due before today and not done, most overdue last so the list reads
     * forwards in time into today. Always empty for [WidgetContent.Agenda].
     */
    val overdue: List<WidgetAgendaRow> = emptyList(),
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
    /** Whether this is the agenda or the task list. */
    val content: WidgetContent = WidgetContent.Agenda,
    /**
     * How far back [WidgetContent.Tasks] looks for something still open. A
     * bound rather than "everything ever": a task a year late is noise on a
     * home screen, and the row ceiling would spend itself on history before it
     * reached today.
     */
    val overdueDays: Long = 30,
)

/** The empty-day string, kept identical to the agenda surface's. */
const val WidgetNothingScheduled = "Nothing scheduled"

/** Its equivalent for a task list, which is never "scheduled". */
const val WidgetNothingDue = "Nothing due"

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

        // Spent before the window, and deliberately: a task list that pushed
        // what is already late below the fold to make room for next Thursday
        // would be hiding the only rows that need an answer today.
        val overdue = overdueRows(tasks, today, options)
        total += overdue.size
        val keptOverdue = overdue.take(budget)
        budget -= keptOverdue.size

        for (offset in 0 until options.dayCount) {
            if (budget == 0) break
            val date = today.plusDays(offset.toLong())
            val rows = mutableListOf<WidgetAgendaRow>()

            if (options.content == WidgetContent.Agenda) {
                // Same ordering as AgendaScreen's day block: all-day first,
                // then by start time, with the id as the tiebreak so a redraw
                // cannot reshuffle two events that begin at the same minute.
                index.eventsOn(date)
                    .sortedWith(
                        compareBy<CalEvent> { !it.allDay }
                            .thenBy { it.start?.toLocalTime() }
                            .thenBy { it.id },
                    )
                    .forEach { rows += it.row(date, options) }
            }

            // Tasks after events: a task is date-only and has no place in the
            // timed ordering above.
            tasksDueOn(tasks, date).forEach { rows += it.row(date, options) }

            total += rows.size
            val kept = rows.take(budget)
            budget -= kept.size
            days += WidgetAgendaDay(date, kept)
        }

        return WidgetAgenda(
            days = markNext(days, today, now),
            empty = total == 0,
            overdue = keptOverdue,
        )
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

    /**
     * Open tasks whose due date has passed, oldest first.
     *
     * Completed ones never appear regardless of `hideCompletedTasks`: that
     * preference is about tidying a day's list, while a finished task that was
     * once late is simply finished, and listing it under "Overdue" would be
     * wrong rather than merely noisy.
     */
    private fun overdueRows(
        tasks: List<CalTask>,
        today: LocalDate,
        options: WidgetAgendaOptions,
    ): List<WidgetAgendaRow> {
        if (options.content != WidgetContent.Tasks) return emptyList()
        val earliest = today.minusDays(options.overdueDays)
        return tasks
            .filterNot { it.done }
            .filter { it.due != null && it.due < today && it.due >= earliest }
            .sortedWith(compareBy({ it.due }, { it.title.lowercase(Locale.US) }, { it.id }))
            .map { task ->
                // The date replaces the time: "17:00" on a row that was due
                // last Tuesday answers the wrong question.
                task.row(task.due!!, options).copy(
                    timeLabel = task.due!!.format(OverdueFormat),
                    startTime = null,
                )
            }
    }

    /** Short enough for the ledger's time column, which is where it lands. */
    private val OverdueFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

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
