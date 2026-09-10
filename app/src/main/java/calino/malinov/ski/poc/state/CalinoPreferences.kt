package calino.malinov.ski.poc.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import calino.malinov.ski.poc.util.CalinoDefaultDuration
import calino.malinov.ski.poc.util.CalinoDefaultReminder
import calino.malinov.ski.poc.util.CalinoDefaultView
import calino.malinov.ski.poc.util.CalinoEventDensity
import calino.malinov.ski.poc.util.CalinoTimeFormat
import calino.malinov.ski.poc.util.CalinoWeekStart

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

    object InMemory : CalinoPreferenceStore {
        private var timeFormat = CalinoTimeFormat.Default
        private var zoomHandle = true
        private var weekStart = CalinoWeekStart.Default
        private var density = CalinoEventDensity.Default
        private var weekNumbers = true
        private var defaultView = CalinoDefaultView.Default
        private var duration = CalinoDefaultDuration.Default
        private var reminder = CalinoDefaultReminder.Default
        private var hideCompleted = false
        private var endTimes = true
        private var locations = true

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

    private companion object {
        const val TimeFormatKey = "time_format"
        const val ShowZoomHandleKey = "show_zoom_handle"
        const val WeekStartKey = "week_start"
        const val EventDensityKey = "event_density"
        const val ShowWeekNumbersKey = "show_week_numbers"
        const val DefaultViewKey = "default_view"
        const val DefaultDurationKey = "default_duration"
        const val DefaultReminderKey = "default_reminder"
        const val HideCompletedTasksKey = "hide_completed_tasks"
        const val ShowEndTimesKey = "show_end_times"
        const val ShowLocationsKey = "show_locations"
    }
}

/**
 * Reads the stored preferences into Compose state and writes changes straight
 * back, so a choice survives both recomposition and a restart.
 */
@Composable
fun rememberCalinoPreferences(store: CalinoPreferenceStore): CalinoPreferences {
    var timeFormat by remember(store) { mutableStateOf(store.loadTimeFormat()) }
    var showZoomHandle by remember(store) { mutableStateOf(store.loadShowZoomHandle()) }
    var weekStart by remember(store) { mutableStateOf(store.loadWeekStart()) }
    var eventDensity by remember(store) { mutableStateOf(store.loadEventDensity()) }
    var showWeekNumbers by remember(store) { mutableStateOf(store.loadShowWeekNumbers()) }
    var defaultView by remember(store) { mutableStateOf(store.loadDefaultView()) }
    var defaultDuration by remember(store) { mutableStateOf(store.loadDefaultDuration()) }
    var defaultReminder by remember(store) { mutableStateOf(store.loadDefaultReminder()) }
    var hideCompletedTasks by remember(store) { mutableStateOf(store.loadHideCompletedTasks()) }
    var showEndTimes by remember(store) { mutableStateOf(store.loadShowEndTimes()) }
    var showLocations by remember(store) { mutableStateOf(store.loadShowLocations()) }
    return CalinoPreferences(
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
    )
}
