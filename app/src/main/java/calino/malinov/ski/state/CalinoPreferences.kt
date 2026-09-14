package calino.malinov.ski.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import calino.malinov.ski.util.CalinoDefaultDuration
import calino.malinov.ski.util.CalinoDefaultReminder
import calino.malinov.ski.util.CalinoDefaultView
import calino.malinov.ski.util.CalinoEventDensity
import calino.malinov.ski.util.CalinoEventSyncRange
import calino.malinov.ski.util.CalinoRangeMode
import calino.malinov.ski.util.CalinoThemeChoice
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.CalinoWeekStart

/**
 * The user's display preferences, and the callbacks that change them.
 *
 * Settings rows used to hold their own `rememberSaveable` state, so choosing a
 * clock moved a segmented control and nothing else. Reading the preference
 * from one composition local means every surface that writes a time --
 * agenda, day rail, editor, event detail -- follows the choice.
 *
 * Every default here is what the app did before the setting existed, so a fresh
 * install behaves exactly as it used to.
 */
@Immutable
data class CalinoPreferences(
    /**
     * Which palette the app paints itself in. Resolved to a
     * `CalinoPalette` once, at the root, rather than by each surface.
     */
    val themeChoice: CalinoThemeChoice = CalinoThemeChoice.Default,
    val setThemeChoice: (CalinoThemeChoice) -> Unit = {},
    val timeFormat: CalinoTimeFormat = CalinoTimeFormat.Default,
    val setTimeFormat: (CalinoTimeFormat) -> Unit = {},
    /**
     * Whether the calendar shows the pull bar between the grid and the day
     * surface. On by default: it is the discoverable way to change zoom, and
     * turning it off leaves the vertical drag on the grid itself.
     */
    val showZoomHandle: Boolean = true,
    val setShowZoomHandle: (Boolean) -> Unit = {},
    val weekStart: CalinoWeekStart = CalinoWeekStart.Default,
    val setWeekStart: (CalinoWeekStart) -> Unit = {},
    val eventDensity: CalinoEventDensity = CalinoEventDensity.Default,
    val setEventDensity: (CalinoEventDensity) -> Unit = {},
    val showWeekNumbers: Boolean = true,
    val setShowWeekNumbers: (Boolean) -> Unit = {},
    val defaultView: CalinoDefaultView = CalinoDefaultView.Default,
    val setDefaultView: (CalinoDefaultView) -> Unit = {},
    val rangeMode: CalinoRangeMode = CalinoRangeMode.Default,
    val setRangeMode: (CalinoRangeMode) -> Unit = {},
    val defaultDuration: CalinoDefaultDuration = CalinoDefaultDuration.Default,
    val setDefaultDuration: (CalinoDefaultDuration) -> Unit = {},
    val defaultReminder: CalinoDefaultReminder = CalinoDefaultReminder.Default,
    val setDefaultReminder: (CalinoDefaultReminder) -> Unit = {},
    val hideCompletedTasks: Boolean = false,
    val setHideCompletedTasks: (Boolean) -> Unit = {},
    val showEndTimes: Boolean = true,
    val setShowEndTimes: (Boolean) -> Unit = {},
    val showLocations: Boolean = true,
    val setShowLocations: (Boolean) -> Unit = {},
    val eventSyncRange: CalinoEventSyncRange = CalinoEventSyncRange.Default,
    val setEventSyncRange: (CalinoEventSyncRange) -> Unit = {},
    val journalEnabled: Boolean = false,
    val setJournalEnabled: (Boolean) -> Unit = {},
    val contactsEnabled: Boolean = false,
    val setContactsEnabled: (Boolean) -> Unit = {},
    /** Whether a reminder on an event is delivered to the device at all. */
    val eventRemindersEnabled: Boolean = true,
    val setEventRemindersEnabled: (Boolean) -> Unit = {},
    val taskRemindersEnabled: Boolean = true,
    val setTaskRemindersEnabled: (Boolean) -> Unit = {},
    /** Whether the sidebar's compact month calendar disclosure is open. */
    val sidebarCalendarExpanded: Boolean = false,
    val setSidebarCalendarExpanded: (Boolean) -> Unit = {},
    /**
     * Whether the day rail's "Tasks due" disclosure, under the week strip, is
     * open. One setting for every day rather than per-day: the point is the
     * choice the user made last time they toggled it, not a memory of which
     * particular days they happened to leave open.
     */
    val dayTasksExpanded: Boolean = true,
    val setDayTasksExpanded: (Boolean) -> Unit = {},
)

val LocalCalinoPreferences = staticCompositionLocalOf { CalinoPreferences() }

/** Shorthand for the common case: the clock the app is currently set to. */
val LocalTimeFormat: CalinoTimeFormat
    @Composable get() = LocalCalinoPreferences.current.timeFormat

/**
 * Preference storage. Backed by [android.content.SharedPreferences] in the
 * app; the in-memory default keeps unit tests and previews off disk.
 */
interface CalinoPreferenceStore {
    fun loadThemeChoice(): CalinoThemeChoice
    fun saveThemeChoice(choice: CalinoThemeChoice)
    fun loadTimeFormat(): CalinoTimeFormat
    fun saveTimeFormat(format: CalinoTimeFormat)
    fun loadShowZoomHandle(): Boolean
    fun saveShowZoomHandle(show: Boolean)
    fun loadWeekStart(): CalinoWeekStart
    fun saveWeekStart(weekStart: CalinoWeekStart)
    fun loadEventDensity(): CalinoEventDensity
    fun saveEventDensity(density: CalinoEventDensity)
    fun loadShowWeekNumbers(): Boolean
    fun saveShowWeekNumbers(show: Boolean)
    fun loadDefaultView(): CalinoDefaultView
    fun saveDefaultView(view: CalinoDefaultView)
    fun loadRangeMode(): CalinoRangeMode
    fun saveRangeMode(mode: CalinoRangeMode)
    fun loadDefaultDuration(): CalinoDefaultDuration
    fun saveDefaultDuration(duration: CalinoDefaultDuration)
    fun loadDefaultReminder(): CalinoDefaultReminder
    fun saveDefaultReminder(reminder: CalinoDefaultReminder)
    fun loadHideCompletedTasks(): Boolean
    fun saveHideCompletedTasks(hide: Boolean)
    fun loadShowEndTimes(): Boolean
    fun saveShowEndTimes(show: Boolean)
    fun loadShowLocations(): Boolean
    fun saveShowLocations(show: Boolean)
    fun loadEventSyncRange(): CalinoEventSyncRange
    fun saveEventSyncRange(range: CalinoEventSyncRange)
    fun loadJournalEnabled(): Boolean
    fun saveJournalEnabled(enabled: Boolean)
    fun loadContactsEnabled(): Boolean
    fun saveContactsEnabled(enabled: Boolean)
    fun loadEventRemindersEnabled(): Boolean
    fun saveEventRemindersEnabled(enabled: Boolean)
    fun loadTaskRemindersEnabled(): Boolean
    fun saveTaskRemindersEnabled(enabled: Boolean)
    fun loadSidebarCalendarExpanded(): Boolean
    fun saveSidebarCalendarExpanded(expanded: Boolean)
    fun loadDayTasksExpanded(): Boolean
    fun saveDayTasksExpanded(expanded: Boolean)
    /**
     * Whether the notification permission has already been asked for once.
     *
     * Read by the app shell, not by a settings row: the request is made on a
     * resume after the update rather than on the very first frame of a first
     * launch, and this is what keeps it to exactly one asking.
     */
    fun loadNotificationPromptShown(): Boolean
    fun saveNotificationPromptShown(shown: Boolean)

    object InMemory : CalinoPreferenceStore {
        private var themeChoice = CalinoThemeChoice.Default
        private var timeFormat = CalinoTimeFormat.Default
        private var zoomHandle = true
        private var weekStart = CalinoWeekStart.Default
        private var density = CalinoEventDensity.Default
        private var weekNumbers = true
        private var defaultView = CalinoDefaultView.Default
        private var rangeMode = CalinoRangeMode.Default
        private var duration = CalinoDefaultDuration.Default
        private var reminder = CalinoDefaultReminder.Default
        private var hideCompleted = false
        private var endTimes = true
        private var locations = true
        private var syncRange = CalinoEventSyncRange.Default
        private var journal = false
        private var contacts = false

        override fun loadThemeChoice() = themeChoice
        override fun saveThemeChoice(choice: CalinoThemeChoice) { themeChoice = choice }
        override fun loadTimeFormat() = timeFormat
        override fun saveTimeFormat(format: CalinoTimeFormat) { timeFormat = format }
        override fun loadShowZoomHandle() = zoomHandle
        override fun saveShowZoomHandle(show: Boolean) { zoomHandle = show }
        override fun loadWeekStart() = weekStart
        override fun saveWeekStart(weekStart: CalinoWeekStart) { this.weekStart = weekStart }
        override fun loadEventDensity() = density
        override fun saveEventDensity(density: CalinoEventDensity) { this.density = density }
        override fun loadShowWeekNumbers() = weekNumbers
        override fun saveShowWeekNumbers(show: Boolean) { weekNumbers = show }
        override fun loadDefaultView() = defaultView
        override fun saveDefaultView(view: CalinoDefaultView) { defaultView = view }
        override fun loadRangeMode() = rangeMode
        override fun saveRangeMode(mode: CalinoRangeMode) { rangeMode = mode }
        override fun loadDefaultDuration() = duration
        override fun saveDefaultDuration(duration: CalinoDefaultDuration) { this.duration = duration }
        override fun loadDefaultReminder() = reminder
        override fun saveDefaultReminder(reminder: CalinoDefaultReminder) { this.reminder = reminder }
        override fun loadHideCompletedTasks() = hideCompleted
        override fun saveHideCompletedTasks(hide: Boolean) { hideCompleted = hide }
        override fun loadShowEndTimes() = endTimes
        override fun saveShowEndTimes(show: Boolean) { endTimes = show }
        override fun loadShowLocations() = locations
        override fun saveShowLocations(show: Boolean) { locations = show }
        override fun loadEventSyncRange() = syncRange
        override fun saveEventSyncRange(range: CalinoEventSyncRange) { syncRange = range }
        override fun loadJournalEnabled() = journal
        override fun saveJournalEnabled(enabled: Boolean) { journal = enabled }
        override fun loadContactsEnabled() = contacts
        override fun saveContactsEnabled(enabled: Boolean) { contacts = enabled }
        private var eventReminders = true
        private var taskReminders = true
        private var notificationPrompt = false
        private var sidebarCalendarExpanded = false
        private var dayTasksExpanded = true
        override fun loadEventRemindersEnabled() = eventReminders
        override fun saveEventRemindersEnabled(enabled: Boolean) { eventReminders = enabled }
        override fun loadTaskRemindersEnabled() = taskReminders
        override fun saveTaskRemindersEnabled(enabled: Boolean) { taskReminders = enabled }
        override fun loadSidebarCalendarExpanded() = sidebarCalendarExpanded
        override fun saveSidebarCalendarExpanded(expanded: Boolean) { sidebarCalendarExpanded = expanded }
        override fun loadDayTasksExpanded() = dayTasksExpanded
        override fun saveDayTasksExpanded(expanded: Boolean) { dayTasksExpanded = expanded }
        override fun loadNotificationPromptShown() = notificationPrompt
        override fun saveNotificationPromptShown(shown: Boolean) { notificationPrompt = shown }
    }
}

class SharedPreferencesPreferenceStore(context: Context) : CalinoPreferenceStore {

    private val prefs = context.applicationContext
        .getSharedPreferences("calino_preferences", Context.MODE_PRIVATE)

    // Enums are stored by name and read back through their own tolerant
    // parser, so a value written by a build that knew a name this one does not
    // falls back to the default rather than crashing on the way in.
    private fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    private fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun name(key: String): String? = prefs.getString(key, null)

    override fun loadThemeChoice(): CalinoThemeChoice = CalinoThemeChoice.fromName(name(ThemeChoiceKey))
    override fun saveThemeChoice(choice: CalinoThemeChoice) = putString(ThemeChoiceKey, choice.name)

    override fun loadTimeFormat(): CalinoTimeFormat = CalinoTimeFormat.fromName(name(TimeFormatKey))
    override fun saveTimeFormat(format: CalinoTimeFormat) = putString(TimeFormatKey, format.name)

    override fun loadShowZoomHandle(): Boolean = prefs.getBoolean(ShowZoomHandleKey, true)
    override fun saveShowZoomHandle(show: Boolean) = putBoolean(ShowZoomHandleKey, show)

    override fun loadWeekStart(): CalinoWeekStart = CalinoWeekStart.fromName(name(WeekStartKey))
    override fun saveWeekStart(weekStart: CalinoWeekStart) = putString(WeekStartKey, weekStart.name)

    override fun loadEventDensity(): CalinoEventDensity = CalinoEventDensity.fromName(name(EventDensityKey))
    override fun saveEventDensity(density: CalinoEventDensity) = putString(EventDensityKey, density.name)

    override fun loadShowWeekNumbers(): Boolean = prefs.getBoolean(ShowWeekNumbersKey, true)
    override fun saveShowWeekNumbers(show: Boolean) = putBoolean(ShowWeekNumbersKey, show)

    override fun loadDefaultView(): CalinoDefaultView = CalinoDefaultView.fromName(name(DefaultViewKey))
    override fun saveDefaultView(view: CalinoDefaultView) = putString(DefaultViewKey, view.name)
    override fun loadRangeMode(): CalinoRangeMode = CalinoRangeMode.fromName(name(RangeModeKey))
    override fun saveRangeMode(mode: CalinoRangeMode) = putString(RangeModeKey, mode.name)

    override fun loadDefaultDuration(): CalinoDefaultDuration =
        CalinoDefaultDuration.fromName(name(DefaultDurationKey))
    override fun saveDefaultDuration(duration: CalinoDefaultDuration) =
        putString(DefaultDurationKey, duration.name)

    override fun loadDefaultReminder(): CalinoDefaultReminder =
        CalinoDefaultReminder.fromName(name(DefaultReminderKey))
    override fun saveDefaultReminder(reminder: CalinoDefaultReminder) =
        putString(DefaultReminderKey, reminder.name)

    override fun loadHideCompletedTasks(): Boolean = prefs.getBoolean(HideCompletedTasksKey, false)
    override fun saveHideCompletedTasks(hide: Boolean) = putBoolean(HideCompletedTasksKey, hide)

    override fun loadShowEndTimes(): Boolean = prefs.getBoolean(ShowEndTimesKey, true)
    override fun saveShowEndTimes(show: Boolean) = putBoolean(ShowEndTimesKey, show)

    override fun loadShowLocations(): Boolean = prefs.getBoolean(ShowLocationsKey, true)
    override fun saveShowLocations(show: Boolean) = putBoolean(ShowLocationsKey, show)
    override fun loadEventSyncRange(): CalinoEventSyncRange =
        CalinoEventSyncRange.fromName(name(EventSyncRangeKey))
    override fun saveEventSyncRange(range: CalinoEventSyncRange) = putString(EventSyncRangeKey, range.name)
    override fun loadJournalEnabled(): Boolean = prefs.getBoolean(JournalEnabledKey, false)
    override fun saveJournalEnabled(enabled: Boolean) = putBoolean(JournalEnabledKey, enabled)
    override fun loadContactsEnabled(): Boolean = prefs.getBoolean(ContactsEnabledKey, false)
    override fun saveContactsEnabled(enabled: Boolean) = putBoolean(ContactsEnabledKey, enabled)
    override fun loadEventRemindersEnabled(): Boolean = prefs.getBoolean(EventRemindersKey, true)
    override fun saveEventRemindersEnabled(enabled: Boolean) = putBoolean(EventRemindersKey, enabled)
    override fun loadTaskRemindersEnabled(): Boolean = prefs.getBoolean(TaskRemindersKey, true)
    override fun saveTaskRemindersEnabled(enabled: Boolean) = putBoolean(TaskRemindersKey, enabled)
    override fun loadSidebarCalendarExpanded(): Boolean = prefs.getBoolean(SidebarCalendarExpandedKey, false)
    override fun saveSidebarCalendarExpanded(expanded: Boolean) = putBoolean(SidebarCalendarExpandedKey, expanded)
    override fun loadDayTasksExpanded(): Boolean = prefs.getBoolean(DayTasksExpandedKey, true)
    override fun saveDayTasksExpanded(expanded: Boolean) = putBoolean(DayTasksExpandedKey, expanded)
    override fun loadNotificationPromptShown(): Boolean = prefs.getBoolean(NotificationPromptKey, false)
    override fun saveNotificationPromptShown(shown: Boolean) = putBoolean(NotificationPromptKey, shown)

    private companion object {
        const val ThemeChoiceKey = "theme_choice"
        const val TimeFormatKey = "time_format"
        const val ShowZoomHandleKey = "show_zoom_handle"
        const val WeekStartKey = "week_start"
        const val EventDensityKey = "event_density"
        const val ShowWeekNumbersKey = "show_week_numbers"
        const val DefaultViewKey = "default_view"
        const val RangeModeKey = "range_mode"
        const val DefaultDurationKey = "default_duration"
        const val DefaultReminderKey = "default_reminder"
        const val HideCompletedTasksKey = "hide_completed_tasks"
        const val ShowEndTimesKey = "show_end_times"
        const val ShowLocationsKey = "show_locations"
        const val EventSyncRangeKey = "event_sync_range"
        const val JournalEnabledKey = "journal_enabled"
        const val ContactsEnabledKey = "contacts_enabled"
        const val EventRemindersKey = "event_reminders_enabled"
        const val TaskRemindersKey = "task_reminders_enabled"
        const val SidebarCalendarExpandedKey = "sidebar_calendar_expanded"
        const val DayTasksExpandedKey = "day_tasks_expanded"
        const val NotificationPromptKey = "notification_prompt_shown"
    }
}

/**
 * Reads the stored preferences into Compose state and writes changes straight
 * back, so a choice survives both recomposition and a restart.
 */
@Composable
fun rememberCalinoPreferences(
    store: CalinoPreferenceStore,
    /** Called when a reminder preference changes, so the schedule is re-planned. */
    onRemindersChanged: () -> Unit = {},
): CalinoPreferences {
    var themeChoice by remember(store) { mutableStateOf(store.loadThemeChoice()) }
    var timeFormat by remember(store) { mutableStateOf(store.loadTimeFormat()) }
    var showZoomHandle by remember(store) { mutableStateOf(store.loadShowZoomHandle()) }
    var weekStart by remember(store) { mutableStateOf(store.loadWeekStart()) }
    var eventDensity by remember(store) { mutableStateOf(store.loadEventDensity()) }
    var showWeekNumbers by remember(store) { mutableStateOf(store.loadShowWeekNumbers()) }
    var defaultView by remember(store) { mutableStateOf(store.loadDefaultView()) }
    var rangeMode by remember(store) { mutableStateOf(store.loadRangeMode()) }
    var defaultDuration by remember(store) { mutableStateOf(store.loadDefaultDuration()) }
    var defaultReminder by remember(store) { mutableStateOf(store.loadDefaultReminder()) }
    var hideCompletedTasks by remember(store) { mutableStateOf(store.loadHideCompletedTasks()) }
    var showEndTimes by remember(store) { mutableStateOf(store.loadShowEndTimes()) }
    var showLocations by remember(store) { mutableStateOf(store.loadShowLocations()) }
    var eventSyncRange by remember(store) { mutableStateOf(store.loadEventSyncRange()) }
    var journalEnabled by remember(store) { mutableStateOf(store.loadJournalEnabled()) }
    var contactsEnabled by remember(store) { mutableStateOf(store.loadContactsEnabled()) }
    var eventRemindersEnabled by remember(store) { mutableStateOf(store.loadEventRemindersEnabled()) }
    var taskRemindersEnabled by remember(store) { mutableStateOf(store.loadTaskRemindersEnabled()) }
    var sidebarCalendarExpanded by remember(store) { mutableStateOf(store.loadSidebarCalendarExpanded()) }
    var dayTasksExpanded by remember(store) { mutableStateOf(store.loadDayTasksExpanded()) }
    return CalinoPreferences(
        themeChoice = themeChoice,
        setThemeChoice = { value -> themeChoice = value; store.saveThemeChoice(value) },
        timeFormat = timeFormat,
        setTimeFormat = { value -> timeFormat = value; store.saveTimeFormat(value) },
        showZoomHandle = showZoomHandle,
        setShowZoomHandle = { value -> showZoomHandle = value; store.saveShowZoomHandle(value) },
        weekStart = weekStart,
        setWeekStart = { value -> weekStart = value; store.saveWeekStart(value) },
        eventDensity = eventDensity,
        setEventDensity = { value -> eventDensity = value; store.saveEventDensity(value) },
        showWeekNumbers = showWeekNumbers,
        setShowWeekNumbers = { value -> showWeekNumbers = value; store.saveShowWeekNumbers(value) },
        defaultView = defaultView,
        setDefaultView = { value -> defaultView = value; store.saveDefaultView(value) },
        rangeMode = rangeMode,
        setRangeMode = { value -> rangeMode = value; store.saveRangeMode(value) },
        defaultDuration = defaultDuration,
        setDefaultDuration = { value -> defaultDuration = value; store.saveDefaultDuration(value) },
        defaultReminder = defaultReminder,
        setDefaultReminder = { value -> defaultReminder = value; store.saveDefaultReminder(value) },
        hideCompletedTasks = hideCompletedTasks,
        setHideCompletedTasks = { value -> hideCompletedTasks = value; store.saveHideCompletedTasks(value) },
        showEndTimes = showEndTimes,
        setShowEndTimes = { value -> showEndTimes = value; store.saveShowEndTimes(value) },
        showLocations = showLocations,
        setShowLocations = { value -> showLocations = value; store.saveShowLocations(value) },
        eventSyncRange = eventSyncRange,
        setEventSyncRange = { value -> eventSyncRange = value; store.saveEventSyncRange(value) },
        journalEnabled = journalEnabled,
        setJournalEnabled = { value -> journalEnabled = value; store.saveJournalEnabled(value) },
        contactsEnabled = contactsEnabled,
        setContactsEnabled = { value -> contactsEnabled = value; store.saveContactsEnabled(value) },
        eventRemindersEnabled = eventRemindersEnabled,
        setEventRemindersEnabled = { value ->
            eventRemindersEnabled = value
            store.saveEventRemindersEnabled(value)
            onRemindersChanged()
        },
        taskRemindersEnabled = taskRemindersEnabled,
        setTaskRemindersEnabled = { value ->
            taskRemindersEnabled = value
            store.saveTaskRemindersEnabled(value)
            onRemindersChanged()
        },
        sidebarCalendarExpanded = sidebarCalendarExpanded,
        setSidebarCalendarExpanded = { value ->
            sidebarCalendarExpanded = value
            store.saveSidebarCalendarExpanded(value)
        },
        dayTasksExpanded = dayTasksExpanded,
        setDayTasksExpanded = { value ->
            dayTasksExpanded = value
            store.saveDayTasksExpanded(value)
        },
    )
}
