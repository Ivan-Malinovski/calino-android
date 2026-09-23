package calino.malinov.ski.platform.assistant

import androidx.appfunctions.AppFunctionSerializable
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.taskCalendarIds
import calino.malinov.ski.data.repository.visibleCalendarIds
import calino.malinov.ski.data.search.CalinoSearchOptions
import calino.malinov.ski.data.search.CalinoSearchRecordType
import calino.malinov.ski.data.search.searchCalino
import calino.malinov.ski.notify.ReminderDeepLink
import calino.malinov.ski.notify.ReminderDeepLinks
import calino.malinov.ski.notify.ReminderKind
import calino.malinov.ski.state.tasksDueOn
import calino.malinov.ski.util.EventDateIndex
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One event occurrence or task, as an assistant sees it.
 *
 * Deliberately narrow: no notes, attendees, raw iCalendar, server URLs or
 * account identity. [itemId] is opaque to the caller; it is Calino's own
 * record link, so [CalinoAssistant.openItem] can hand it straight to the same
 * resolution a notification tap uses.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CalendarItem(
    /** Pass to openItem to show this item in Calino. */
    val itemId: String,
    /** "event" or "task". */
    val kind: String,
    val title: String,
    /** The day this occurrence or due date falls on. */
    val date: LocalDate,
    /** Event start; null for all-day events and tasks. */
    val start: LocalDateTime?,
    /** Event end; null for all-day events and tasks. */
    val end: LocalDateTime?,
    val allDay: Boolean,
    /** Task due time, when the task has one. */
    val dueTime: LocalTime?,
    val done: Boolean,
    val location: String?,
    /** The calendar or task list the item belongs to. */
    val calendarName: String?,
)

/** The items on one day, events in time order and then tasks. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AgendaDay(
    val date: LocalDate,
    val items: List<CalendarItem>,
)

/**
 * The read side of assistant access, as plain functions of a snapshot.
 *
 * Events and tasks only: journals and contacts are never offered to an
 * assistant. Visibility follows the calendar views exactly -- a hidden
 * calendar, or task list kept off the views, is invisible here too.
 */
object AssistantCalendar {

    const val MaxAgendaDays = 31
    const val MaxSearchResults = 20

    fun agenda(snapshot: CalinoSnapshot, startDate: LocalDate, days: Int): List<AgendaDay> {
        val count = days.coerceIn(1, MaxAgendaDays)
        val names = snapshot.calendars.associate { it.id to it.name }
        val eventCalendars = visibleCalendarIds(snapshot.calendars)
        val index = EventDateIndex.build(snapshot.events.filter { it.calendarId in eventCalendars })
        val taskCalendars = taskCalendarIds(snapshot.calendars)
        val tasks = snapshot.tasks.filter { it.calendarId in taskCalendars }
        return (0 until count).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val events = index.eventsOn(date)
                .sortedWith(compareBy<CalEvent> { !it.allDay }.thenBy { it.start?.toLocalTime() }.thenBy { it.id })
                .map { it.item(date, names) }
            AgendaDay(date, events + tasksDueOn(tasks, date).map { it.item(date, names) })
        }
    }

    /** Calino's own ranked search, limited to events and tasks. */
    fun search(snapshot: CalinoSnapshot, query: String, today: LocalDate): List<CalendarItem> {
        val names = snapshot.calendars.associate { it.id to it.name }
        val groups = searchCalino(
            snapshot = snapshot,
            query = query,
            baseDate = today,
            limitPerGroup = MaxSearchResults,
            contactsEnabled = false,
            journalsEnabled = false,
            options = CalinoSearchOptions(
                recordTypes = setOf(CalinoSearchRecordType.Events, CalinoSearchRecordType.Tasks),
            ),
        )
        val events = groups.events.map { result ->
            val event = result.event
            event.item(event.date ?: event.start?.toLocalDate() ?: today, names)
        }
        val tasks = groups.tasks.map { result -> result.task.item(result.task.due ?: today, names) }
        return (events + tasks).take(MaxSearchResults)
    }

    private fun CalEvent.item(date: LocalDate, names: Map<String, String>): CalendarItem {
        val timed = !allDay && start != null
        // A recurring master carries its first start; this occurrence keeps
        // the time and takes the day being listed.
        val occurrenceStart = when {
            !timed -> null
            recurrence != null -> LocalDateTime.of(date, start!!.toLocalTime())
            else -> start
        }
        return CalendarItem(
            itemId = ReminderDeepLinks.uri(ReminderDeepLink(ReminderKind.Event, id, uid, date.toEpochDay())),
            kind = "event",
            title = title.ifBlank { "(No title)" },
            date = date,
            start = occurrenceStart,
            end = occurrenceStart?.plusMinutes((durationMinutes ?: 0).toLong()),
            allDay = !timed,
            dueTime = null,
            done = false,
            location = location?.takeIf { it.isNotBlank() },
            calendarName = names[calendarId],
        )
    }

    private fun CalTask.item(date: LocalDate, names: Map<String, String>) = CalendarItem(
        itemId = ReminderDeepLinks.uri(ReminderDeepLink(ReminderKind.Task, id, uid, date.toEpochDay())),
        kind = "task",
        title = title.ifBlank { "(No title)" },
        date = date,
        start = null,
        end = null,
        allDay = false,
        dueTime = dueTime,
        done = done,
        location = null,
        calendarName = names[calendarId],
    )
}
