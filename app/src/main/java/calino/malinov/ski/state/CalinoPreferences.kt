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
import kotlin.math.min

/** Independent Range-view preference buckets for adaptive window shapes. */
enum class CalinoRangeProfile {
    PhonePortrait,
    PhoneLandscape,
    TabletPortrait,
    TabletLandscape,
}

fun rangeProfileFor(device: CalinoDeviceDefaults): CalinoRangeProfile {
    val tablet = min(device.windowWidthDp, device.windowHeightDp) >= 600
    val landscape = device.orientation == CalinoOrientation.Landscape ||
        device.orientation == CalinoOrientation.Unknown && device.windowWidthDp > device.windowHeightDp
    return when {
        tablet && landscape -> CalinoRangeProfile.TabletLandscape
        tablet -> CalinoRangeProfile.TabletPortrait
        landscape -> CalinoRangeProfile.PhoneLandscape
        else -> CalinoRangeProfile.PhonePortrait
    }
}

/**
 * The user's display preferences, and the callbacks that change them.
 *
 * Settings rows used to hold their own `rememberSaveable` state, so choosing a
 * clock moved a segmented control and nothing else. Reading the preference
 * from one composition local means every surface that writes a time --
 * agenda, day rail, editor, event detail -- follows the choice.
 *
 * User-facing defaults that can be derived from Android use a System choice;
 * the effective values are resolved once at the app shell against the current
 * device profile. Other defaults remain explicit product defaults.
 */
@Immutable
data class CalinoPreferences(
    /**
     * Which palette the app paints itself in. Resolved to a
     * `CalinoPalette` once, at the root, rather than by each surface.
     */
    val themeChoice: CalinoThemeChoice = CalinoThemeChoice.Default,
    val setThemeChoice: (CalinoThemeChoice) -> Unit = {},
    /** The effective clock used by every surface. */
    val timeFormat: CalinoTimeFormat = CalinoTimeFormat.TwelveHour,
    /** The stored choice, which may be [CalinoTimeFormat.System]. */
    val timeFormatChoice: CalinoTimeFormat = CalinoTimeFormat.Default,
    val setTimeFormat: (CalinoTimeFormat) -> Unit = {},
    /**
     * Whether the calendar shows the pull bar between the grid and the day
     * surface. On by default: it is the discoverable way to change zoom, and
     * turning it off leaves the vertical drag on the grid itself.
     */
    val showZoomHandle: Boolean = true,
    val setShowZoomHandle: (Boolean) -> Unit = {},
    /** The effective week start used by every grid and pager. */
    val weekStart: CalinoWeekStart = CalinoWeekStart.Monday,
    /** The stored choice, which may be [CalinoWeekStart.System]. */
    val weekStartChoice: CalinoWeekStart = CalinoWeekStart.Default,
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
    /**
     * Whether another calendar app delivers the reminders for projected
     * calendars, instead of Calino.
     *
     * Off by default, and never inferred from what is installed: the person
     * is the only one who knows which app they want to hear from.
     */
    val providerRemindersEnabled: Boolean = false,
    val setProviderRemindersEnabled: (Boolean) -> Unit = {},
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
    fun loadRangeMode(profile: CalinoRangeProfile): CalinoRangeMode
    fun saveRangeMode(profile: CalinoRangeProfile, mode: CalinoRangeMode)
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
    fun loadProviderRemindersEnabled(): Boolean
    fun saveProviderRemindersEnabled(enabled: Boolean)

    /**
     * The CalDAV calendars published into Android's calendar store.
     *
     * Deliberately not a field on [CalinoPreferences]: this is not a setting
     * the Settings screen renders, it is per-calendar state that
     * `CalinoContainer` owns and the calendar accounts surface edits. An empty
     * set means projection is off entirely.
     */
    fun loadProjectedCalendarIds(): Set<String>
    fun saveProjectedCalendarIds(ids: Set<String>)

    /**
     * The device's own calendars that Calino shows, and the subset of those
     * it also reminds for.
     *
     * Store-only for the same reason as the projected set above: this is
     * per-calendar state `CalinoContainer` owns, not a row the Settings
     * screen renders. An empty import set means the feature is off entirely
     * and the composite repository is not even in the graph.
     *
     * The reminder set is always a subset of the import set and is off by
     * default, because the app that owns an imported calendar is already
     * notifying for it -- and Calino cannot stop it doing so.
     */
    fun loadImportedCalendarIds(): Set<String>
    fun saveImportedCalendarIds(ids: Set<String>)
    fun loadImportedReminderCalendarIds(): Set<String>
    fun saveImportedReminderCalendarIds(ids: Set<String>)
    fun loadWritableImportedCalendarIds(): Set<String>
    fun hasWritableImportedCalendarPreference(): Boolean
    fun saveWritableImportedCalendarIds(ids: Set<String>)
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
        private val rangeModes = mutableMapOf<CalinoRangeProfile, CalinoRangeMode>()
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
        override fun loadRangeMode(profile: CalinoRangeProfile) = rangeModes[profile] ?: CalinoRangeMode.Default
        override fun saveRangeMode(profile: CalinoRangeProfile, mode: CalinoRangeMode) { rangeModes[profile] = mode }
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
        private var providerReminders = false
        private var projected = emptySet<String>()
        private var notificationPrompt = false
        private var sidebarCalendarExpanded = false
        private var dayTasksExpanded = true
        private var imported = emptySet<String>()
        private var importedReminders = emptySet<String>()
        private var writableImported = emptySet<String>()
        private var hasWritableImportedPreference = false
        override fun loadEventRemindersEnabled() = eventReminders
        override fun saveEventRemindersEnabled(enabled: Boolean) { eventReminders = enabled }
        override fun loadTaskRemindersEnabled() = taskReminders
        override fun saveTaskRemindersEnabled(enabled: Boolean) { taskReminders = enabled }
        override fun loadProviderRemindersEnabled() = providerReminders
        override fun saveProviderRemindersEnabled(enabled: Boolean) { providerReminders = enabled }
        override fun loadProjectedCalendarIds() = projected
        override fun saveProjectedCalendarIds(ids: Set<String>) { projected = ids }
        override fun loadImportedCalendarIds() = imported
        override fun saveImportedCalendarIds(ids: Set<String>) { imported = ids }
        override fun loadImportedReminderCalendarIds() = importedReminders
        override fun saveImportedReminderCalendarIds(ids: Set<String>) { importedReminders = ids }
        override fun loadWritableImportedCalendarIds() = writableImported
        override fun hasWritableImportedCalendarPreference() = hasWritableImportedPreference
        override fun saveWritableImportedCalendarIds(ids: Set<String>) {
            writableImported = ids
            hasWritableImportedPreference = true
        }
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
    override fun loadRangeMode(profile: CalinoRangeProfile): CalinoRangeMode =
        CalinoRangeMode.fromName(name("${RangeModeKey}_${profile.name}") ?: name(RangeModeKey))
    override fun saveRangeMode(profile: CalinoRangeProfile, mode: CalinoRangeMode) =
        putString("${RangeModeKey}_${profile.name}", mode.name)

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
    override fun loadProviderRemindersEnabled(): Boolean = prefs.getBoolean(ProviderRemindersKey, false)
    override fun saveProviderRemindersEnabled(enabled: Boolean) = putBoolean(ProviderRemindersKey, enabled)
    // Copied on the way out: SharedPreferences hands back the live stored set,
    // and holding on to it is documented as undefined behaviour.
    override fun loadProjectedCalendarIds(): Set<String> =
        prefs.getStringSet(ProjectedCalendarsKey, emptySet()).orEmpty().toSet()
    override fun saveProjectedCalendarIds(ids: Set<String>) {
        prefs.edit().putStringSet(ProjectedCalendarsKey, ids).apply()
    }
    override fun loadImportedCalendarIds(): Set<String> =
        prefs.getStringSet(ImportedCalendarsKey, emptySet()).orEmpty().toSet()
    override fun saveImportedCalendarIds(ids: Set<String>) {
        prefs.edit().putStringSet(ImportedCalendarsKey, ids).apply()
    }
    override fun loadImportedReminderCalendarIds(): Set<String> =
        prefs.getStringSet(ImportedReminderCalendarsKey, emptySet()).orEmpty().toSet()
    override fun saveImportedReminderCalendarIds(ids: Set<String>) {
        prefs.edit().putStringSet(ImportedReminderCalendarsKey, ids).apply()
    }
    override fun loadWritableImportedCalendarIds(): Set<String> =
        prefs.getStringSet(WritableImportedCalendarsKey, emptySet()).orEmpty().toSet()
    override fun hasWritableImportedCalendarPreference(): Boolean =
        prefs.contains(WritableImportedCalendarsKey)
    override fun saveWritableImportedCalendarIds(ids: Set<String>) {
        prefs.edit().putStringSet(WritableImportedCalendarsKey, ids).apply()
    }
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
        const val ProviderRemindersKey = "provider_reminders_enabled"
        const val ProjectedCalendarsKey = "projected_calendar_ids"
        const val ImportedCalendarsKey = "imported_calendar_ids"
        const val ImportedReminderCalendarsKey = "imported_reminder_calendar_ids"
        const val WritableImportedCalendarsKey = "writable_imported_calendar_ids"
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
    deviceDefaults: CalinoDeviceDefaults = CalinoDeviceDefaults.Fallback,
    /** Called when a reminder preference changes, so the schedule is re-planned. */
    onRemindersChanged: () -> Unit = {},
    /** Called when Journal or Contacts availability changes so private search can be reconciled. */
    onSearchAvailabilityChanged: (journalsEnabled: Boolean, contactsEnabled: Boolean) -> Unit = { _, _ -> },
): CalinoPreferences {
    var themeChoice by remember(store) { mutableStateOf(store.loadThemeChoice()) }
    var timeFormatChoice by remember(store) { mutableStateOf(store.loadTimeFormat()) }
    var showZoomHandle by remember(store) { mutableStateOf(store.loadShowZoomHandle()) }
    var weekStartChoice by remember(store) { mutableStateOf(store.loadWeekStart()) }
    var eventDensity by remember(store) { mutableStateOf(store.loadEventDensity()) }
    var showWeekNumbers by remember(store) { mutableStateOf(store.loadShowWeekNumbers()) }
    var defaultView by remember(store) { mutableStateOf(store.loadDefaultView()) }
    val rangeProfile = rangeProfileFor(deviceDefaults)
    var rangeMode by remember(store, rangeProfile) { mutableStateOf(store.loadRangeMode(rangeProfile)) }
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
    var providerRemindersEnabled by remember(store) { mutableStateOf(store.loadProviderRemindersEnabled()) }
    var sidebarCalendarExpanded by remember(store) { mutableStateOf(store.loadSidebarCalendarExpanded()) }
    var dayTasksExpanded by remember(store) { mutableStateOf(store.loadDayTasksExpanded()) }
    return CalinoPreferences(
        themeChoice = themeChoice,
        setThemeChoice = { value -> themeChoice = value; store.saveThemeChoice(value) },
        timeFormat = timeFormatChoice.resolved(deviceDefaults.timeFormat),
        timeFormatChoice = timeFormatChoice,
        setTimeFormat = { value -> timeFormatChoice = value; store.saveTimeFormat(value) },
        showZoomHandle = showZoomHandle,
        setShowZoomHandle = { value -> showZoomHandle = value; store.saveShowZoomHandle(value) },
        weekStart = weekStartChoice.resolved(deviceDefaults.weekStart),
        weekStartChoice = weekStartChoice,
        setWeekStart = { value -> weekStartChoice = value; store.saveWeekStart(value) },
        eventDensity = eventDensity,
        setEventDensity = { value -> eventDensity = value; store.saveEventDensity(value) },
        showWeekNumbers = showWeekNumbers,
        setShowWeekNumbers = { value -> showWeekNumbers = value; store.saveShowWeekNumbers(value) },
        defaultView = defaultView,
        setDefaultView = { value -> defaultView = value; store.saveDefaultView(value) },
        rangeMode = rangeMode,
        setRangeMode = { value -> rangeMode = value; store.saveRangeMode(rangeProfile, value) },
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
        setJournalEnabled = { value ->
            journalEnabled = value
            store.saveJournalEnabled(value)
            onSearchAvailabilityChanged(value, contactsEnabled)
        },
        contactsEnabled = contactsEnabled,
        setContactsEnabled = { value ->
            contactsEnabled = value
            store.saveContactsEnabled(value)
            onSearchAvailabilityChanged(journalEnabled, value)
        },
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
        providerRemindersEnabled = providerRemindersEnabled,
        setProviderRemindersEnabled = { value ->
            providerRemindersEnabled = value
            store.saveProviderRemindersEnabled(value)
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
