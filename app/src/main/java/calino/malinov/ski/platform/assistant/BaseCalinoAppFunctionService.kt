package calino.malinov.ski.platform.assistant

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionDisabledException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionNotSupportedException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import calino.malinov.ski.MainActivity
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.notify.AgendaDeepLinks
import calino.malinov.ski.notify.ReminderDeepLinks
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Calino's app functions: read the calendar, open things in Calino, and
 * prepare new events and tasks for the person to save.
 *
 * Nothing here writes. A draft opens Calino's own editor, prefilled, and only
 * the person's Save commits it -- through the repository and its durable
 * queue like any other edit. Reads cover downloaded events and tasks on
 * visible calendars, never journals, contacts, notes, attendees, credentials
 * or server data. See docs/assistant-functions.md.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "CalinoAppFunctionService",
    appFunctionXmlFileName = "calino_app_function_service",
)
abstract class BaseCalinoAppFunctionService : AppFunctionService() {

    /**
     * Lists the events and tasks on one or more days, in time order.
     *
     * @param startDate The first day to list.
     * @param days How many days to list, from 1 to 31. Defaults to 1.
     * @return One entry per day, including days with nothing on them.
     * @throws AppFunctionNotSupportedException If no calendar account is connected to Calino.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getAgenda(startDate: LocalDate, days: Int = 1): List<AgendaDay> {
        if (days !in 1..AssistantCalendar.MaxAgendaDays) {
            throw AppFunctionInvalidArgumentException("days must be between 1 and ${AssistantCalendar.MaxAgendaDays}.")
        }
        return AssistantCalendar.agenda(snapshot(), startDate, days)
    }

    /**
     * Searches event and task titles, locations and categories. Tolerates typos.
     * Covers data Calino has downloaded, which may not include the distant past or future.
     *
     * @param query Words to look for. Cannot be blank.
     * @return Up to 20 matching events and tasks, best match first.
     * @throws AppFunctionInvalidArgumentException If the query is blank.
     * @throws AppFunctionNotSupportedException If no calendar account is connected to Calino.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchCalendar(query: String): List<CalendarItem> {
        if (query.isBlank()) throw AppFunctionInvalidArgumentException("Query cannot be blank.")
        return AssistantCalendar.search(snapshot(), query, LocalDate.now())
    }

    /**
     * Opens an event or task in Calino.
     *
     * @param itemId The itemId of an item returned by getAgenda or searchCalendar.
     * @return An intent that shows the item in Calino.
     * @throws AppFunctionInvalidArgumentException If itemId did not come from getAgenda or searchCalendar.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun openItem(itemId: String): PendingIntent {
        requireEnabled()
        ReminderDeepLinks.parse(itemId)
            ?: throw AppFunctionInvalidArgumentException("Unknown itemId. Use an itemId from getAgenda or searchCalendar.")
        return activity(RequestOpenItem, view(itemId))
    }

    /**
     * Opens Calino's agenda on a day.
     *
     * @param date The day to show.
     * @return An intent that shows the day in Calino.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun openDay(date: LocalDate): PendingIntent {
        requireEnabled()
        return activity(RequestOpenDay, view(AgendaDeepLinks.uri(date)))
    }

    /**
     * Prepares a new event in Calino's editor for the user to review and save.
     * Nothing is saved until the user taps Save.
     *
     * @param title The event title. Cannot be blank.
     * @param start When the event starts. For an all-day event only the date is used.
     * @param end When the event ends. Defaults to one hour after start.
     * @param allDay Whether the event lasts all day. Defaults to false.
     * @param location Where the event takes place.
     * @return An intent that opens the prefilled editor in Calino.
     * @throws AppFunctionInvalidArgumentException If the title is blank or end is before start.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun draftEvent(
        title: String,
        start: LocalDateTime,
        end: LocalDateTime? = null,
        allDay: Boolean = false,
        location: String? = null,
    ): PendingIntent {
        requireEnabled()
        if (title.isBlank()) throw AppFunctionInvalidArgumentException("Title cannot be blank.")
        if (end != null && end.isBefore(start)) throw AppFunctionInvalidArgumentException("end is before start.")
        val zone = ZoneId.systemDefault()
        val begin = start.atZone(zone).toInstant().toEpochMilli()
        val finish = (end ?: start.plusHours(1)).atZone(zone).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .setClass(this, MainActivity::class.java)
            .putExtra(CalendarContract.Events.TITLE, title.trim())
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, finish)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
            .apply { location?.takeIf { it.isNotBlank() }?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it.trim()) } }
        return activity(RequestDraftEvent, intent)
    }

    /**
     * Prepares a new task in Calino's editor for the user to review and save.
     * Nothing is saved until the user taps Save.
     *
     * @param title The task title. Cannot be blank.
     * @param dueDate The day the task is due. Defaults to today.
     * @param dueTime The time the task is due, if it has one.
     * @return An intent that opens the prefilled editor in Calino.
     * @throws AppFunctionInvalidArgumentException If the title is blank.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun draftTask(
        title: String,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
    ): PendingIntent {
        requireEnabled()
        if (title.isBlank()) throw AppFunctionInvalidArgumentException("Title cannot be blank.")
        val intent = Intent(MainActivity.ActionDraftTask)
            .setClass(this, MainActivity::class.java)
            .putExtra(MainActivity.ExtraTaskTitle, title.trim())
            .putExtra(MainActivity.ExtraTaskDueDay, (dueDate ?: LocalDate.now()).toEpochDay())
            .apply { dueTime?.let { putExtra(MainActivity.ExtraTaskDueMinute, it.toSecondOfDay() / 60) } }
        return activity(RequestDraftTask, intent)
    }

    /**
     * The person's own data, or a refusal. Fixture mode is the frozen May 2026
     * sample calendar; handing it to an assistant as the person's schedule
     * would be a lie, the same reason the widget declines to render it.
     */
    private fun snapshot(): CalinoSnapshot {
        requireEnabled()
        val container = CalinoContainer.get(this)
        if (!container.hasAccounts) {
            throw AppFunctionNotSupportedException("No calendar account is connected to Calino.")
        }
        container.ensureCachedData()
        return container.activeRepository.snapshot()
    }

    /** Defence in depth: the disabled component should never be bound at all. */
    private fun requireEnabled() {
        if (!AssistantAccess.isEnabled(this)) {
            throw AppFunctionDisabledException("Assistant access is turned off in Calino's settings.")
        }
    }

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setClass(this, MainActivity::class.java)

    private fun activity(requestCode: Int, intent: Intent): PendingIntent = PendingIntent.getActivity(
        this,
        requestCode,
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val RequestOpenItem = 7101
        const val RequestOpenDay = 7102
        const val RequestDraftEvent = 7103
        const val RequestDraftTask = 7104
    }
}
