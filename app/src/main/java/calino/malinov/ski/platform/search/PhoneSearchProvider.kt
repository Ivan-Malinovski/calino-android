package calino.malinov.ski.platform.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import android.text.format.DateFormat
import calino.malinov.ski.R
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.platform.assistant.AssistantCalendar
import calino.malinov.ski.platform.assistant.CalendarItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Calino's answers to the phone's own search (Samsung Finder and other
 * holders of the system GLOBAL_SEARCH permission).
 *
 * Read-only, on-device, and the same narrow view the assistant functions
 * get: events and tasks on visible calendars, never journals, contacts,
 * notes or fixture data. Ships disabled with its searchable alias; see
 * [PhoneSearchAccess] and docs/assistant-functions.md.
 */
class PhoneSearchProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(Columns)
        val context = context ?: return cursor
        // Disabling the component stops new binds, but a provider already
        // running in this process stays reachable until the process dies.
        if (!PhoneSearchAccess.isEnabled(context)) return cursor
        val query = uri.lastPathSegment
            ?.takeUnless { it == SearchManager.SUGGEST_URI_PATH_QUERY }
            ?: selectionArgs?.firstOrNull()
        if (query.isNullOrBlank()) return cursor

        val container = CalinoContainer.get(context)
        // Fixture mode is sample data, not the person's calendar.
        if (!container.hasAccounts) return cursor
        container.ensureCachedData()
        val limit = uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT)?.toIntOrNull()
            ?: AssistantCalendar.MaxSearchResults
        val clock24 = DateFormat.is24HourFormat(context)
        AssistantCalendar.search(container.activeRepository.snapshot(), query, LocalDate.now())
            .take(limit)
            .forEachIndexed { index, item ->
                cursor.addRow(
                    arrayOf<Any>(
                        index.toLong(),
                        item.title,
                        item.subtitle(clock24),
                        item.itemId,
                        if (item.kind == "task") R.drawable.ic_search_task else R.drawable.ic_search_event,
                    ),
                )
            }
        return cursor
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        val Columns = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_ICON_1,
        )
    }
}

/** "Thu 24 Sep · 17:00 · Work": when, then which calendar. */
internal fun CalendarItem.subtitle(clock24: Boolean, locale: Locale = Locale.getDefault()): String {
    val day = date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    val timeFormat = DateTimeFormatter.ofPattern(if (clock24) "HH:mm" else "h:mm a", locale)
    val time = when {
        start != null -> start.toLocalTime().format(timeFormat)
        dueTime != null -> dueTime.format(timeFormat)
        allDay -> "All day"
        else -> null
    }
    return listOfNotNull(day, time, calendarName).joinToString(" · ")
}
