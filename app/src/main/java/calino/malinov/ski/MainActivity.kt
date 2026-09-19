package calino.malinov.ski

import android.app.Application
import android.content.Intent
import android.provider.CalendarContract
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import calino.malinov.ski.data.caldav.CalDavConnectionManager
import calino.malinov.ski.data.caldav.CalDavDiscovery
import calino.malinov.ski.data.caldav.CalDavFetcher
import calino.malinov.ski.data.caldav.CalDavWriter
import calino.malinov.ski.data.caldav.CardDavWriter
import calino.malinov.ski.data.caldav.CredentialStore
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.KeystoreCredentialStore
import calino.malinov.ski.data.caldav.SharedPreferencesAccountPersistence
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.CalDavForm
import calino.malinov.ski.data.ical.IcsImportBatch
import calino.malinov.ski.data.ical.IcsInterop
import calino.malinov.ski.data.caldav.FileCalendarCache
import calino.malinov.ski.data.repository.CalDavRepository
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.WebcalForm
import calino.malinov.ski.data.model.WebcalSubscription
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.model.placementDate
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.NewContact
import calino.malinov.ski.data.model.toNewContact
import calino.malinov.ski.data.model.derivedDisplayName
import calino.malinov.ski.data.model.contactReminderEvent
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.NewJournal
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.EditorDraft
import calino.malinov.ski.data.model.blankEditorDraft
import calino.malinov.ski.data.model.editorDraftFor
import calino.malinov.ski.data.ai.AiEventCandidate
import calino.malinov.ski.data.ai.AiVisionClient
import calino.malinov.ski.data.ai.AiVisionSettingsStore
import calino.malinov.ski.data.parser.PocQuickAddKind
import calino.malinov.ski.data.search.CalinoSearchResult
import calino.malinov.ski.data.repository.CalDavAccountStore
import calino.malinov.ski.data.repository.CalDavClient
import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.notify.LocalNotificationPermission
import calino.malinov.ski.notify.ReminderChannels
import calino.malinov.ski.notify.AgendaDeepLinks
import calino.malinov.ski.notify.ReminderDeepLink
import calino.malinov.ski.notify.ReminderDeepLinks
import calino.malinov.ski.notify.ReminderKind
import calino.malinov.ski.notify.rememberNotificationPermission
import calino.malinov.ski.data.repository.FixtureRepository
import calino.malinov.ski.data.repository.UndoableChange
import calino.malinov.ski.data.repository.WriteResult
import calino.malinov.ski.data.repository.PendingChange
import calino.malinov.ski.data.repository.reparentTask
import calino.malinov.ski.data.repository.duplicateTask
import calino.malinov.ski.data.repository.duplicateEvent
import calino.malinov.ski.data.repository.convertTaskToEvent
import calino.malinov.ski.data.repository.convertEventToTask
import calino.malinov.ski.data.repository.moveEventToDate
import calino.malinov.ski.data.repository.moveEventToDateTime
import calino.malinov.ski.data.repository.accepts
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoSpacing
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.core.view.WindowCompat
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.design.CalinoThemes
import calino.malinov.ski.util.CalinoThemeChoice
import calino.malinov.ski.state.LocalTaskLookup
import calino.malinov.ski.state.FixtureNow
import calino.malinov.ski.state.CalinoFoldPosture
import calino.malinov.ski.state.LocalFoldPosture
import calino.malinov.ski.state.LocalHingeOpenness
import calino.malinov.ski.state.hingeOpenness
import calino.malinov.ski.state.foldPostureOf
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.state.CalinoSyncStatus
import calino.malinov.ski.state.LocalCalinoSync
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.LocalCalinoDeviceDefaults
import calino.malinov.ski.state.rememberCalinoDeviceDefaults
import calino.malinov.ski.state.SharedPreferencesPreferenceStore
import calino.malinov.ski.state.rememberCalinoPreferences
import calino.malinov.ski.state.rememberCalinoNow
import calino.malinov.ski.state.FeatureAvailability
import calino.malinov.ski.state.featureAvailabilityAfter
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.EndLaneWidthDp
import calino.malinov.ski.state.shouldSplit
import calino.malinov.ski.state.PocReturnTarget
import calino.malinov.ski.ui.components.AddPill
import calino.malinov.ski.ui.components.CalinoPillLane
import calino.malinov.ski.ui.components.LocalCalinoPillLane
import calino.malinov.ski.ui.components.PillWriteKind
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.CalinoToast
import calino.malinov.ski.ui.components.NavSidebar
import calino.malinov.ski.ui.components.pockRouteLabel
import calino.malinov.ski.ui.home.HomeScreen
import calino.malinov.ski.ui.home.PillSwipeDays
import calino.malinov.ski.ui.range.RangeScreen
import calino.malinov.ski.ui.components.SwipeDownDismiss
import calino.malinov.ski.ui.surfaces.DayModalSurface
import calino.malinov.ski.ui.surfaces.EventDetail
import calino.malinov.ski.ui.surfaces.TaskDetail
import calino.malinov.ski.ui.surfaces.NotificationsSurface
import calino.malinov.ski.ui.surfaces.rememberNotificationSurfaceState
import calino.malinov.ski.ui.surfaces.AgendaScreen
import calino.malinov.ski.ui.surfaces.CalendarAccountsSurface
import calino.malinov.ski.ui.surfaces.PockRoute
import calino.malinov.ski.ui.surfaces.detailOriginRootRoute
import calino.malinov.ski.ui.surfaces.QuickAddKind
import calino.malinov.ski.ui.surfaces.QuickAddSheet
import calino.malinov.ski.ui.surfaces.QuickAddSheetState
import calino.malinov.ski.ui.surfaces.toParserKind
import calino.malinov.ski.ui.surfaces.JournalSurface
import calino.malinov.ski.ui.surfaces.ContactsSurface
import calino.malinov.ski.ui.surfaces.SettingsSurface
import calino.malinov.ski.ui.surfaces.CalinoSearchSheet
import calino.malinov.ski.ui.surfaces.Tasks
import calino.malinov.ski.ui.surfaces.TaskMenuAction
import calino.malinov.ski.ui.surfaces.EventMenuAction
import calino.malinov.ski.ui.surfaces.AiCandidateReview
import calino.malinov.ski.ui.surfaces.AiProcessingOverlay
import calino.malinov.ski.ui.surfaces.defaultEventDeleteScope
import calino.malinov.ski.ui.surfaces.updateLauncherShortcuts
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

private val DateLabel = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
private val PredictiveBackEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
private const val PredictiveBackFadeThreshold = .35f

private fun fallbackRescheduleDate(
    taskDate: LocalDate?,
    selectedDate: LocalDate,
    today: LocalDate,
): LocalDate = maxOf(
    today.plusDays(1),
    taskDate?.plusDays(1) ?: selectedDate.plusDays(1),
)

private val LocalDateSaver = Saver<LocalDate, Long>(
    save = { it.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) },
)

private val RouteSaver = Saver<PockRoute, String>(
    save = { it.saveableKey() },
    restore = { key ->
        when (key) {
            "agenda" -> PockRoute.Agenda
            "range" -> PockRoute.Range
            "detail" -> PockRoute.Detail
            "task-detail" -> PockRoute.TaskDetail
            "tasks" -> PockRoute.Tasks
            "journal" -> PockRoute.Journal
            "contacts" -> PockRoute.Contacts
            "settings" -> PockRoute.Settings
            "accounts" -> PockRoute.Accounts
            "quick-add" -> PockRoute.QuickAdd
            "notifications" -> PockRoute.Notifications
            else -> PockRoute.Day
        }
    },
)

private val ReturnTargetSaver = Saver<PocReturnTarget, String>(
    save = { it.name },
    restore = { value -> runCatching { PocReturnTarget.valueOf(value) }.getOrDefault(PocReturnTarget.Calendar) },
)

private val QuickAddKindSaver = Saver<QuickAddKind, String>(
    save = { it.name },
    restore = { value -> runCatching { QuickAddKind.valueOf(value) }.getOrDefault(QuickAddKind.Event) },
)

private fun PockRoute.saveableKey(): String = when (this) {
    PockRoute.Day -> "calendar"
    PockRoute.Range -> "range"
    PockRoute.Agenda -> "agenda"
    PockRoute.Detail -> "detail"
    PockRoute.TaskDetail -> "task-detail"
    PockRoute.Tasks -> "tasks"
    PockRoute.Journal -> "journal"
    PockRoute.Contacts -> "contacts"
    PockRoute.Settings -> "settings"
    PockRoute.Accounts -> "accounts"
    PockRoute.QuickAdd -> "quick-add"
    PockRoute.Notifications -> "notifications"
}

private fun PockRoute.rootOrder(): Int = when (this) {
    PockRoute.Day -> 0
    PockRoute.Range -> 1
    PockRoute.Agenda -> 2
    PockRoute.Tasks -> 3
    PockRoute.Journal -> 4
    PockRoute.Contacts -> 5
    PockRoute.Settings -> 6
    PockRoute.Accounts -> 7
    // Detail and notification previews are pushed destinations. Keeping them
    // after the root destinations makes opening them enter from the right and
    // returning from them reverse the same motion, instead of treating them
    // as another instance of the calendar route.
    PockRoute.Detail -> 8
    PockRoute.TaskDetail -> 8
    PockRoute.Notifications -> 8
    PockRoute.QuickAdd -> 8
}

class MainActivity : ComponentActivity() {
    var incomingImage by mutableStateOf<Uri?>(null)
        private set
    var incomingCalendar by mutableStateOf<Uri?>(null)
        private set
    var incomingText by mutableStateOf<String?>(null)
        private set
    var incomingEventDraft by mutableStateOf<EditorDraft?>(null)
        private set
    var incomingEventKey by mutableStateOf<String?>(null)
        private set
    var aiShortcutRequest by mutableIntStateOf(0)
        private set
    var searchShortcutPending by mutableStateOf(false)
        private set

    /**
     * A notification tap, waiting for a snapshot that can resolve it.
     *
     * Held rather than acted on: a cold start arrives here before the calendar
     * data does, and the record the link names may not exist yet.
     */
    var pendingReminderLink by mutableStateOf<ReminderDeepLink?>(null)
        private set

    /**
     * A widget tap on a day header: "show me the calendar on this date".
     *
     * Separate from [pendingReminderLink] because it needs no snapshot to
     * resolve -- a date is a date -- but it is still held rather than applied,
     * because the composition that owns the selected date does not exist yet
     * when a cold start parses the intent.
     */
    var pendingAgendaDate by mutableStateOf<LocalDate?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderChannels.ensure(this)
        consumeLauncherShortcut(intent)
        consumeAiIntent(intent)
        consumeReminderIntent(intent)
        consumeInteropIntent(intent)
        enableEdgeToEdge()
        setContent { CalinoApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLauncherShortcut(intent)
        consumeAiIntent(intent)
        consumeReminderIntent(intent)
        consumeInteropIntent(intent)
    }

    fun consumeIncomingImage() { incomingImage = null }
    fun consumeIncomingCalendar() { incomingCalendar = null }
    fun acceptIncomingCalendar(uri: Uri) { incomingCalendar = uri }
    fun consumeIncomingText() { incomingText = null }
    fun consumeIncomingEventDraft() { incomingEventDraft = null; incomingEventKey = null }

    fun consumeReminderLink() { pendingReminderLink = null }

    fun consumeAgendaDate() { pendingAgendaDate = null }

    fun consumeSearchShortcut() { searchShortcutPending = false }

    private fun consumeLauncherShortcut(intent: Intent?) {
        if (intent?.action == ActionSearch) searchShortcutPending = true
    }

    private fun consumeReminderIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val raw = intent.data?.toString()
        ReminderDeepLinks.parse(raw)?.let { pendingReminderLink = it }
        AgendaDeepLinks.parse(raw)?.let { pendingAgendaDate = it }
    }

    private fun consumeAiIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            val single = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            @Suppress("DEPRECATION")
            val multiple = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            incomingImage = single ?: multiple?.firstOrNull()
        } else if (intent.data?.host == "ai-photo-import") {
            aiShortcutRequest += 1
        }
    }

    private fun consumeInteropIntent(intent: Intent?) {
        intent ?: return
        when {
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                incomingText = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            intent.action == Intent.ACTION_VIEW && intent.data != null &&
                (intent.type?.contains("calendar", ignoreCase = true) == true ||
                    intent.data?.lastPathSegment?.endsWith(".ics", ignoreCase = true) == true) ->
                incomingCalendar = intent.data
            intent.action == Intent.ACTION_INSERT || intent.action == Intent.ACTION_EDIT -> {
                incomingEventKey = if (intent.action == Intent.ACTION_EDIT) {
                    intent.getStringExtra("calino.malinov.ski.extra.EVENT_ID")
                        ?: intent.getStringExtra("calino.malinov.ski.extra.EVENT_UID")
                } else null
                val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L)
                val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L)
                val zone = java.time.ZoneId.systemDefault()
                val start = begin.takeIf { it >= 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
                val allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
                incomingEventDraft = EditorDraft(
                    kind = PocQuickAddKind.Event,
                    rawInput = intent.getStringExtra(CalendarContract.Events.TITLE).orEmpty(),
                    title = intent.getStringExtra(CalendarContract.Events.TITLE).orEmpty(),
                    date = start?.toLocalDate() ?: java.time.LocalDate.now(),
                    startTime = if (allDay) null else start?.toLocalTime(),
                    durationMinutes = if (begin >= 0 && end > begin) ((end - begin) / 60_000L).toInt() else 60,
                    allDay = allDay,
                    location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION),
                    description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION),
                    attendees = intent.getStringExtra(Intent.EXTRA_EMAIL)?.split(',')
                        ?.map(String::trim)?.filter(String::isNotEmpty)
                        ?.map { calino.malinov.ski.data.model.Attendee(it, it) }.orEmpty(),
                    touched = calino.malinov.ski.data.model.EditorField.entries.toSet(),
                )
            }
        }
    }

    private companion object {
        const val ActionSearch = "calino.malinov.ski.action.SEARCH"
    }
}

/**
 * Compose-state facade over the process-wide [CalinoContainer].
 *
 * Two repositories exist and one is active at a time. With no account
 * connected the fixture repository serves the frozen May 2026 sample data, so
 * the app is never an empty shell; connecting an account switches to the
 * CalDAV-backed one. [activeRepository] is Compose state, so the swap
 * recomposes and the observer bridge re-subscribes on its own.
 *
 * The data layer itself no longer lives here. A notification action arrives in
 * a receiver with no Activity and must write through the same repository and
 * the same durable queue, so construction moved to [CalinoContainer] and this
 * class kept only the job Compose actually needs: mirroring that state into
 * the composition. See the comment on the container for the full reasoning.
 */
class PocRepositoryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = CalinoContainer.get(application)

    val accountStore get() = container.accountStore

    val webcalStore get() = container.webcalStore

    /** Display preferences (clock, and whatever joins it), persisted. */
    val preferenceStore get() = container.preferenceStore

    /** Real discovery. This is the seam `FixtureCalDavClient` used to fill. */
    val calDavClient: CalDavClient get() = container.calDavClient

    private val repositoryState = mutableStateOf(container.activeRepository)
    val activeRepository: CalinoRepository get() = repositoryState.value

    private val hasAccountsState = mutableStateOf(container.hasAccounts)
    private val hasLiveDataState = mutableStateOf(container.hasLiveData)

    /** Whether any CalDAV account is connected. Drives the calendar's anchor date. */
    val hasAccounts: Boolean get() = hasAccountsState.value

    /** CalDAV or a webcal overlay: not the frozen fixture. */
    val hasLiveData: Boolean get() = hasLiveDataState.value

    private val repositorySubscription = container.observeRepository { repository ->
        repositoryState.value = repository
        hasAccountsState.value = container.hasAccounts
        hasLiveDataState.value = container.hasLiveData
    }

    init {
        // A persisted account restores and refetches without asking for the
        // password again; the credential store still holds it.
        container.ensureConnected()
        container.startWriteQueueDrain()
        container.startReminderScheduling()
        container.startWidgetUpdates()
        syncState()
    }

    override fun onCleared() {
        super.onCleared()
        repositorySubscription.close()
    }

    fun onAccountConnected(form: CalDavForm, calendars: List<CalDavCalendar>) {
        container.onAccountConnected(form, calendars)
        syncState()
    }

    fun onCalendarEnabled(accountId: String, calendarId: String, enabled: Boolean) {
        container.accountStore.setCalendarEnabled(accountId, calendarId, enabled)
        container.onCalendarsToggled()
    }

    fun onCalendarVisibilityChanged(accountId: String, calendarId: String, visible: Boolean) {
        container.accountStore.updateCalendarPresentation(accountId, calendarId, visible = visible)
        container.onCalendarsToggled()
    }

    fun onCalendarTasksChanged(accountId: String, calendarId: String, show: Boolean) {
        container.accountStore.updateCalendarPresentation(accountId, calendarId, showTasksInViews = show)
        container.onCalendarsToggled()
    }

    fun onCalendarRenamed(accountId: String, calendarId: String, name: String) {
        container.accountStore.updateCalendarPresentation(accountId, calendarId, name = name)
        container.onCalendarsToggled()
    }

    fun onCalendarColorChanged(accountId: String, calendarId: String, color: Long) {
        container.accountStore.updateCalendarPresentation(accountId, calendarId, color = color)
        container.onCalendarsToggled()
    }

    fun onAddressBookEnabled(accountId: String, addressBookId: String, enabled: Boolean) {
        container.accountStore.setAddressBookEnabled(accountId, addressBookId, enabled)
        container.onCalendarsToggled()
    }

    fun onAccountRemoved(accountId: String) {
        container.onAccountRemoved(accountId)
        syncState()
    }

    fun drainPendingWrites() = container.calDavRepository.drainPendingWrites()

    fun pendingChanges(): List<PendingChange> = container.calDavRepository.pendingChanges()

    fun retryPendingChange(id: String): Boolean = container.calDavRepository.retryPendingChange(id)

    fun discardPendingChange(id: String): Boolean = container.calDavRepository.discardPendingChange(id)

    fun setEventWindowMonths(months: Long) = container.calDavRepository.setWindowMonths(months)

    /** Starts a wider CalDAV read when calendar navigation reaches an unseen month. */
    fun extendEventWindowToInclude(date: LocalDate): Boolean =
        if (container.hasAccounts) container.calDavRepository.extendWindowToInclude(date) else false

    suspend fun exportCalendarEvents(calendarId: String): String =
        container.calDavRepository.exportCalendarEvents(calendarId)

    /** Re-plan the reminder schedule from the latest snapshot. */
    fun replanReminders() = container.reminderBridge.refresh()

    /**
     * The container notifies only when the repository actually swaps, which is
     * the point of the listener; `hasAccounts` can change without a swap (a
     * second account on an already-connected app), so it is re-read here too.
     */
    private fun syncState() {
        repositoryState.value = container.activeRepository
        hasAccountsState.value = container.hasAccounts
        hasLiveDataState.value = container.hasLiveData
    }

    suspend fun addWebcalSubscription(form: WebcalForm) {
        container.addWebcalSubscription(form)
        syncState()
    }

    fun removeWebcalSubscription(id: String) {
        container.removeWebcalSubscription(id)
        syncState()
    }

    fun onWebcalVisibilityChanged(id: String, visible: Boolean) =
        container.onWebcalVisibilityChanged(id, visible)

    fun onWebcalNotifyRemindersChanged(id: String, notify: Boolean) =
        container.onWebcalNotifyRemindersChanged(id, notify)

    fun onWebcalRenamed(id: String, name: String) = container.onWebcalRenamed(id, name)

    fun onWebcalRenamedByCalendarId(calendarId: String, name: String) =
        container.onWebcalRenamedByCalendarId(calendarId, name)

    fun onWebcalColorChanged(id: String, color: Long) = container.onWebcalColorChanged(id, color)

    fun syncWebcal(id: String) {
        container.scope.launch { runCatching { container.syncWebcal(id) } }
    }

    fun refresh() {
        container.calDavRepository.refresh()
        container.scope.launch { runCatching { container.syncWebcalAll() } }
    }
}

/** The launch shell for the native app. No WebView or Capacitor is involved. */
@Composable
fun CalinoApp() {
    val pocViewModel = viewModel<PocRepositoryViewModel>()
    // The clock runs for real once an account is connected; with only the
    // fixture data it stays frozen so the sample stays deterministic.
    val now by rememberCalinoNow(live = pocViewModel.hasLiveData)
    val deviceDefaults = rememberCalinoDeviceDefaults()
    // Read before the theme, not inside it: the palette is a function of a
    // preference, so the preference has to exist first.
    val preferences = rememberCalinoPreferences(
        store = pocViewModel.preferenceStore,
        deviceDefaults = deviceDefaults,
        // A reminder switched off must stop arriving now, not after the next
        // sync happens to publish something.
        onRemindersChanged = { pocViewModel.replanReminders() },
    )
    val notificationPermission = rememberNotificationPermission(pocViewModel.preferenceStore)
    LaunchedEffect(preferences.eventSyncRange) {
        pocViewModel.setEventWindowMonths(preferences.eventSyncRange.months)
    }
    val dark = when (preferences.themeChoice) {
        CalinoThemeChoice.System -> deviceDefaults.isDarkMode
        CalinoThemeChoice.Light -> false
        CalinoThemeChoice.Dark -> true
    }
    CalinoTheme(if (dark) CalinoThemes.PaperDark else CalinoThemes.PaperLight) {
        SystemBarAppearance(light = !dark)
        CompositionLocalProvider(
            LocalCalinoNow provides now,
            LocalCalinoPreferences provides preferences,
            LocalCalinoDeviceDefaults provides deviceDefaults,
            LocalNotificationPermission provides notificationPermission,
            LocalFoldPosture provides rememberFoldPosture(),
            LocalHingeOpenness provides rememberHingeOpenness(),
            // One pill lane for the whole app: the root add pill and every
            // modal's action pill are the same object changing shape in it.
            LocalCalinoPillLane provides remember { CalinoPillLane() },
        ) {
            CalinoAppContent(pocViewModel)
        }
    }
}

/**
 * The hinge, as the layout rules want it: in dp, and reduced to the few facts
 * that change a layout. `BoxWithConstraints` cannot see a fold, so this is the
 * one place the app asks the platform about the device's shape.
 */
@Composable
private fun rememberFoldPosture(): CalinoFoldPosture {
    val activity = LocalActivity.current ?: return CalinoFoldPosture.None
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tracker = remember(activity) { WindowInfoTracker.getOrCreate(activity) }
    var posture by remember { mutableStateOf(CalinoFoldPosture.None) }
    LaunchedEffect(tracker, density, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            tracker.windowLayoutInfo(activity).collect { info ->
                val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                posture = if (fold == null) {
                    CalinoFoldPosture.None
                } else {
                    val vertical = fold.orientation == FoldingFeature.Orientation.VERTICAL
                    // A vertical hinge divides the window left/right; a
                    // horizontal one divides it top/bottom.
                    val start = if (vertical) fold.bounds.left else fold.bounds.top
                    val end = if (vertical) fold.bounds.right else fold.bounds.bottom
                    with(density) {
                        foldPostureOf(
                            isVerticalHinge = vertical,
                            isHalfOpen = fold.state == FoldingFeature.State.HALF_OPENED,
                            isSeparating = fold.isSeparating,
                            hingeStartDp = start.toDp().value,
                            hingeEndDp = end.toDp().value,
                        )
                    }
                }
            }
        }
    }
    return posture
}

/**
 * The hinge angle as a 0..1 openness, or null where there is no such sensor.
 *
 * It is an on-change wake-up sensor, so it costs nothing while the device sits
 * still and delivers a stream while it moves -- which is exactly the shape the
 * fold morph wants. The value is a plain `MutableFloatState` read inside a
 * `graphicsLayer` block, so a fold repaints without recomposing the calendar.
 */
@Composable
private fun rememberHingeOpenness(): State<Float>? {
    val context = LocalActivity.current ?: return null
    val lifecycleOwner = LocalLifecycleOwner.current
    val sensorManager = remember(context) {
        context.getSystemService(SensorManager::class.java)
    }
    val hinge = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    } ?: return null
    val openness = remember { mutableFloatStateOf(1f) }
    DisposableEffect(sensorManager, hinge, lifecycleOwner) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val degrees = event.values.firstOrNull() ?: return
                openness.floatValue = hingeOpenness(degrees, hinge.maximumRange)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START ->
                    sensorManager?.registerListener(listener, hinge, SensorManager.SENSOR_DELAY_GAME)
                Lifecycle.Event.ON_STOP -> sensorManager?.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager?.unregisterListener(listener)
        }
    }
    return openness
}

/**
 * Keeps the status and navigation bar icons legible against whatever the app is
 * painted in.
 *
 * The manifest theme used to assert dark icons unconditionally, which is right
 * for paper and unreadable over ink. It has to be the insets controller rather
 * than a resource qualifier, because an in-app Light or Dark choice must beat
 * the system's night setting -- and `values-night` cannot see that choice.
 */
@Composable
@Suppress("DEPRECATION") // Required below API 35 to clear the system navigation-bar fill.
private fun SystemBarAppearance(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? android.app.Activity)?.window ?: return
    SideEffect {
        // Keep the gesture-navigation lane visually continuous with the app.
        // Some platform versions otherwise add their own contrast scrim behind
        // the navigation pill even though this window is edge-to-edge.
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

@Composable
private fun CalinoAppContent(pocViewModel: PocRepositoryViewModel) {
    val now = LocalCalinoNow.current
    val preferences = LocalCalinoPreferences.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = pocViewModel.activeRepository
    val accountStore = pocViewModel.accountStore
    val snapshot = rememberRepositorySnapshot(repository)
    var fixtureHiddenCalendarIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fixtureHiddenTaskCalendarIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val visibleCalendarIds = remember(snapshot.calendars, fixtureHiddenCalendarIds) {
        snapshot.calendars.filter { it.visible && it.id !in fixtureHiddenCalendarIds }.map { it.id }.toSet()
    }
    val taskCalendarIds = remember(snapshot.calendars, fixtureHiddenCalendarIds, fixtureHiddenTaskCalendarIds) {
        snapshot.calendars.filter {
            it.visible && it.id !in fixtureHiddenCalendarIds &&
                it.showTasksInViews && it.id !in fixtureHiddenTaskCalendarIds
        }.map { it.id }.toSet()
    }
    val calendarEvents = remember(snapshot.events, visibleCalendarIds) {
        snapshot.events.filter { event -> event.calendarId in visibleCalendarIds }
    }
    /**
     * Calendars nothing may be written to.
     *
     * Derived beside the visible set for the same reason that one was
     * extracted: every surface that offers an edit has to agree about which
     * events it may offer it for, and four copies of the rule is how they
     * stop agreeing. Covers imported device calendars, subscribed .ics feeds
     * and CalDAV collections the server grants no write privilege on -- the
     * last of which the app previously only checked when *choosing* a
     * calendar, never when editing something already in one.
     */
    val readOnlyCalendarIds = remember(snapshot.calendars) {
        snapshot.calendars.filter { it.readOnly }.map { it.id }.toSet()
    }
    /**
     * Why the calendar will not take the edit, in the words that fit it.
     * A device calendar belongs to another app on the phone; a subscribed
     * feed belongs to whoever publishes the .ics, and there is no server
     * holding it that Calino could write back to at all.
     */
    fun readOnlyRefusal(calendarId: String?): String =
        if (calendarId != null && WebcalSubscription.isWebcalCalendarId(calendarId)) {
            "That calendar is a subscription. Calino can show what the " +
                "publisher sends, but not change it."
        } else {
            "That calendar belongs to another app on this device. " +
                "Calino can show it, but not change it."
        }
    val calendarTasks = remember(snapshot.tasks, taskCalendarIds) {
        snapshot.tasks.filter { it.calendarId in taskCalendarIds }
    }
    val pendingChanges = remember(snapshot.revision) { pocViewModel.pendingChanges() }
    val calDavAccounts = rememberCalDavAccounts(accountStore)
    val webcalSubscriptions = rememberWebcalSubscriptions(pocViewModel.webcalStore)
    val saveableStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var route by rememberSaveable(stateSaver = RouteSaver) {
        mutableStateOf<PockRoute>(if (preferences.defaultView == calino.malinov.ski.util.CalinoDefaultView.Range) PockRoute.Range else PockRoute.Day)
    }
    var selectedContactId by rememberSaveable { mutableStateOf<String?>(null) }
    // The fixture data lives around May 2026, so that is where the sample
    // app opens. Real calendars are anchored on the actual date instead --
    // landing a connected account on the fixture month shows an empty
    // calendar and reads as a broken integration.
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(if (pocViewModel.hasLiveData) LocalDate.now() else FixtureNow.today)
    }
    // The two days a live swipe has the add pill's label between, ahead of
    // the commit. Only chrome that merely names the day reads this; the
    // calendar itself still follows the committed [selectedDate].
    var swipeLabelDays by remember { mutableStateOf<PillSwipeDays?>(null) }
    var agendaPillLabelDirection by remember { mutableIntStateOf(0) }
    // Where between them it is. A lambda rather than a value, and held in its
    // own state: the pill reads it inside its own draw and measure passes, so
    // a drag moves the label without recomposing this screen per frame.
    var swipeLabelTravel by remember { mutableStateOf<() -> Float>({ 0f }) }
    // The agenda keeps its own pair and travel rather than sharing the
    // calendar's. One screen leaves as the other arrives, and the departing
    // one clears the lane on its way out -- into whatever the arriving one had
    // just put there.
    var agendaSwipeLabelDays by remember { mutableStateOf<PillSwipeDays?>(null) }
    var agendaSwipeLabelTravel by remember { mutableStateOf<() -> Float>({ 0f }) }
    // Connecting the first account mid-session moves the calendar to today
    // for the same reason.
    LaunchedEffect(pocViewModel.hasLiveData) {
        if (pocViewModel.hasLiveData && selectedDate == FixtureNow.today) {
            selectedDate = LocalDate.now()
        }
    }

    /**
     * Calendar navigation is the point at which an out-of-range one-off can
     * become visible. Keep the local route state responsive, then let the
     * connected repository widen its server window in the background.
     */
    fun selectCalendarDate(date: LocalDate) {
        selectedDate = date
        pocViewModel.extendEventWindowToInclude(date)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Every foreground is an explicit retry opportunity in addition
            // to the account-connect and periodic ViewModel triggers.
            pocViewModel.drainPendingWrites()
            kotlinx.coroutines.awaitCancellation()
        }
    }
    var showDayModal by rememberSaveable { mutableStateOf(false) }
    var selectedEventId by rememberSaveable { mutableStateOf<String?>(null) }
    // Keep the calendar occurrence separate from the event's series
    // anchor. Detail can then show the occurrence the user actually
    // tapped while retaining the original event identity for editing.
    var selectedEventOccurrenceDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var editEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var taskDetailOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
    var quickAddOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
    var quickAddKind by rememberSaveable(stateSaver = QuickAddKindSaver) { mutableStateOf(QuickAddKind.Event) }
    var quickAddSeed by rememberSaveable { mutableStateOf("") }
    var quickAddStartMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    // A multi-day range has no selected day to speak of, so its add pill opens
    // an editor anchored on the first day on screen rather than on whichever
    // day the pager last settled on. Held as an epoch day so it survives the
    // same process death the rest of the quick-add state does.
    var quickAddDateEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var rangeFirstVisibleEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var quickAddParentTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var quickAddMorphFromAddPill by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchOriginRoute by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf<PockRoute>(PockRoute.Day) }
    var notificationOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
    // Calendar management is reached from Settings and from a calendar root's
    // sync marker, so Back returns to whichever surface opened it.
    var accountsOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
    // Set when Settings opens the surface via its add button, so the add
    // sheet is already showing on arrival.
    var accountsAutoAdd by rememberSaveable { mutableStateOf(false) }
    // Which account a Settings "Manage" row asked to be brought into view.
    var accountsFocusId by rememberSaveable { mutableStateOf<String?>(null) }
    var journalReviewVisible by rememberSaveable { mutableStateOf(false) }
    var journalEditorVisible by rememberSaveable { mutableStateOf(false) }
    var journalOpenEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var journalSearchReturn by rememberSaveable { mutableStateOf(false) }
    var sidebarVisible by rememberSaveable { mutableStateOf(false) }
    var rootBackProgress by remember { mutableFloatStateOf(0f) }
    var rootBackInProgress by remember { mutableStateOf(false) }
    var predictiveRouteCommit by remember { mutableStateOf(false) }
    // The large landscape month root reserves a right-side lane for the pill,
    // even while the day pane itself is collapsed. That keeps the affordance
    // anchored when the pane opens or closes.
    var splitMonthLayoutVisible by remember { mutableStateOf(false) }
    // Whether the calendar has a day in focus. At the fully expanded month it
    // does not, and the add pill stops naming one.
    var calendarDayInFocus by remember { mutableStateOf(true) }
    var journalEntryRequest by rememberSaveable { mutableIntStateOf(0) }
    var contactRequest by rememberSaveable { mutableIntStateOf(0) }
    var writeError by remember { mutableStateOf<String?>(null) }
    var importBatch by remember { mutableStateOf<IcsImportBatch?>(null) }
    var importCalendarId by remember { mutableStateOf<String?>(null) }
    var exportCalendarPicker by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var externalDraft by remember { mutableStateOf<EditorDraft?>(null) }
    // The overflow menu can act on one occurrence of a series, so its delete
    // asks for a scope instead of defaulting to the whole series.
    var pendingEventDelete by remember { mutableStateOf<CalEvent?>(null) }
    var pendingTaskDelete by remember { mutableStateOf<CalTask?>(null) }
    val writeScope = androidx.compose.runtime.rememberCoroutineScope()
    // The lane the add pill lives in, so a deliberate save can be reported on
    // the pill that started it.
    val savePillLane = LocalCalinoPillLane.current
    val activity = LocalActivity.current as? MainActivity ?: return
    val aiSettingsStore = remember { AiVisionSettingsStore(activity) }
    val aiClient = remember { AiVisionClient() }
    LaunchedEffect(Unit) { updateLauncherShortcuts(activity, aiSettingsStore.load().hasApiKey) }
    var aiCandidates by remember { mutableStateOf<List<AiEventCandidate>?>(null) }
    var aiQueue by remember { mutableStateOf<List<AiEventCandidate>>(emptyList()) }
    var aiDraft by remember { mutableStateOf<EditorDraft?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiStage by remember { mutableStateOf("Sending photo…") }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiErrorNeedsSettings by remember { mutableStateOf(false) }
    var showPhotoSource by remember { mutableStateOf(false) }
    var openAiSettingsRequest by remember { mutableIntStateOf(0) }
    var pickedImage by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = runCatching { activity.contentResolver.openInputStream(it)?.use(java.io.InputStream::readBytes) }.getOrNull()
            if (bytes != null) pickedImage = bytes to (activity.contentResolver.getType(it) ?: "image/jpeg")
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { image ->
            pickedImage = ByteArrayOutputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() } to "image/jpeg"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) activity.acceptIncomingCalendar(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        val text = pendingExportText
        if (uri != null && text != null) {
            runCatching { activity.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } }
                .onFailure { writeError = "The calendar export could not be written." }
        }
        pendingExportText = null
    }

    LaunchedEffect(activity.incomingCalendar) {
        val uri = activity.incomingCalendar ?: return@LaunchedEffect
        activity.consumeIncomingCalendar()
        runCatching {
            val bytes = activity.contentResolver.openInputStream(uri)?.use(IcsInterop::readLimited)
                ?: error("That calendar file could not be read.")
            IcsInterop.withDuplicates(IcsInterop.parseEvents(bytes.toString(Charsets.UTF_8)), snapshot.events)
        }.onSuccess { batch ->
            importBatch = batch
            importCalendarId = snapshot.calendars.firstOrNull { !it.readOnly && it.accepts("VEVENT") }?.id
        }.onFailure { writeError = it.message ?: "That calendar file could not be read." }
    }

    fun requestPhotoImport() {
        if (!aiSettingsStore.load().hasApiKey) {
            aiError = "Set up AI Photo Import in Settings first."
            aiErrorNeedsSettings = true
            openAiSettingsRequest += 1
            route = PockRoute.Settings
        } else showPhotoSource = true
    }

    LaunchedEffect(activity.incomingImage) {
        val uri = activity.incomingImage ?: return@LaunchedEffect
        activity.consumeIncomingImage()
        if (!aiSettingsStore.load().hasApiKey) {
            aiError = "Set up AI Photo Import in Settings first."
            aiErrorNeedsSettings = true
            openAiSettingsRequest += 1
            route = PockRoute.Settings
            return@LaunchedEffect
        }
        val bytes = runCatching { activity.contentResolver.openInputStream(uri)?.use(java.io.InputStream::readBytes) }.getOrNull()
        if (bytes == null) { aiErrorNeedsSettings = false; aiError = "Could not read the shared photo." }
        else pickedImage = bytes to (activity.contentResolver.getType(uri) ?: "image/jpeg")
    }
    LaunchedEffect(activity.aiShortcutRequest) { if (activity.aiShortcutRequest > 0) requestPhotoImport() }
    LaunchedEffect(pickedImage) {
        val image = pickedImage ?: return@LaunchedEffect
        val key = aiSettingsStore.apiKey() ?: return@LaunchedEffect
        aiBusy = true
        aiStage = "Sending photo…"
        val stageJob = launch { delay(1500); aiStage = "Reading details…"; delay(7500); aiStage = "Still working…" }
        runCatching { aiClient.extract(aiSettingsStore.load(), key, image.first, image.second) }
            .onSuccess { found -> if (found.any(AiEventCandidate::isUsable)) aiCandidates = found else { aiErrorNeedsSettings = false; aiError = "No event or task details were found. Try a clearer photo." } }
            .onFailure { error ->
                aiErrorNeedsSettings = Regex("authentication|401|403", RegexOption.IGNORE_CASE).containsMatchIn(error.message.orEmpty())
                android.util.Log.e("CalinoAiVision", "Photo extraction failed: ${error::class.java.simpleName}: ${error.message}")
                aiError = if (aiErrorNeedsSettings) {
                    "Your AI API key looks invalid or expired."
                } else {
                    val detail = error.message?.trim()?.take(240).orEmpty()
                    if (detail.isBlank()) "Could not read event details from that photo."
                    else "Could not read event details: $detail"
                }
            }
        stageJob.cancel()
        aiBusy = false
        // Clearing the effect key before the request completed cancelled this
        // coroutine immediately. Consume the image only after all result state
        // has been committed.
        pickedImage = null
    }

    LaunchedEffect(snapshot.revision) {
        val detected = featureAvailabilityAfter(
            snapshot,
            FeatureAvailability(preferences.journalEnabled, preferences.contactsEnabled),
        )
        if (detected.journalEnabled != preferences.journalEnabled) preferences.setJournalEnabled(detected.journalEnabled)
        if (detected.contactsEnabled != preferences.contactsEnabled) preferences.setContactsEnabled(detected.contactsEnabled)
    }
    LaunchedEffect(preferences.journalEnabled, preferences.contactsEnabled) {
        if (route == PockRoute.Journal && !preferences.journalEnabled) route = PockRoute.Day
        if (route == PockRoute.Contacts && !preferences.contactsEnabled) route = PockRoute.Day
    }

    // RouteSaver can restore a destination before the preference effect above
    // gets its first frame. Do not compose a surface that is currently hidden.
    val visibleRoute = when {
        route == PockRoute.Journal && !preferences.journalEnabled -> PockRoute.Day
        route == PockRoute.Contacts && !preferences.contactsEnabled -> PockRoute.Day
        else -> route
    }

    LaunchedEffect(writeError) {
        val message = writeError ?: return@LaunchedEffect
        delay(5_000)
        if (writeError == message) writeError = null
    }

    val selectedEvent = snapshot.events.firstOrNull { it.id == selectedEventId }
    val selectedTask = snapshot.tasks.firstOrNull { it.id == selectedTaskId }
    val selectedContact = snapshot.contacts.firstOrNull { it.id == selectedContactId }
    val calendarDayModalVisible = showDayModal && (
        route == PockRoute.Day ||
            (route == PockRoute.QuickAdd && quickAddOrigin == PocReturnTarget.DayModal)
        )

    fun openQuickAdd(
        kind: QuickAddKind,
        origin: PocReturnTarget,
        morphFromAddPill: Boolean = false,
        parentTaskId: String? = null,
        startMinute: Int? = null,
        date: LocalDate? = null,
    ) {
        externalDraft = null
        editEventId = null
        quickAddSeed = ""
        quickAddStartMinute = startMinute
        quickAddDateEpoch = date?.toEpochDay()
        quickAddParentTaskId = parentTaskId
        quickAddMorphFromAddPill = morphFromAddPill
        quickAddKind = kind
        quickAddOrigin = origin
        route = PockRoute.QuickAdd
    }

    LaunchedEffect(activity.searchShortcutPending) {
        if (!activity.searchShortcutPending) return@LaunchedEffect
        activity.consumeSearchShortcut()
        searchOriginRoute = route
        sidebarVisible = false
        searchVisible = true
    }

    /** The same editor, seeded from a record that already exists. */
    fun openEditor(event: CalEvent, origin: PocReturnTarget) {
        // The last gate. The detail card already hides its Open action for a
        // read-only event, but the editor can be reached from a menu and a
        // deep link too, and an editor whose Save can only fail is worse than
        // no editor.
        if (event.calendarId in readOnlyCalendarIds) {
            writeError = readOnlyRefusal(event.calendarId)
            return
        }
        externalDraft = null
        editEventId = event.id
        quickAddSeed = ""
        quickAddStartMinute = null
        quickAddDateEpoch = null
        quickAddParentTaskId = null
        // The detail card's edit action is the source pill for the editor,
        // just like the root add pill is when creating a new event.
        quickAddMorphFromAddPill = true
        quickAddKind = QuickAddKind.Event
        quickAddOrigin = origin
        route = PockRoute.QuickAdd
    }

    LaunchedEffect(activity.incomingText) {
        val text = activity.incomingText ?: return@LaunchedEffect
        activity.consumeIncomingText()
        selectedDate = now.today
        openQuickAdd(QuickAddKind.Event, PocReturnTarget.Calendar)
        quickAddSeed = text
    }
    LaunchedEffect(activity.incomingEventDraft) {
        val draft = activity.incomingEventDraft ?: return@LaunchedEffect
        val existing = activity.incomingEventKey?.let { key -> snapshot.events.firstOrNull { it.id == key || it.uid == key } }
        activity.consumeIncomingEventDraft()
        selectedDate = draft.date
        openQuickAdd(QuickAddKind.Event, PocReturnTarget.Calendar)
        externalDraft = draft.copy(
            editingId = existing?.id,
            calendarId = existing?.calendarId ?: snapshot.calendars.firstOrNull { !it.readOnly && it.accepts("VEVENT") }?.id ?: draft.calendarId,
            uid = existing?.uid, href = existing?.href, etag = existing?.etag,
            recurrence = existing?.recurrence, recurrenceId = existing?.recurrenceId,
            recurrenceDate = existing?.recurrenceDate, sequence = existing?.sequence,
        )
    }

    /**
     * Every person-initiated repository mutation reports through the pill by
     * default. [indicatorAlreadyStarted] lets an editor begin that report at
     * the exact Save press, before focus clearing and its exit animation, while
     * this gateway still owns finishing it from the real repository result.
     * Background writes can explicitly pass a null [indicate].
     */
    fun <T> launchWrite(
        operation: suspend () -> WriteResult<T>,
        indicate: PillWriteKind? = PillWriteKind.Save,
        indicatorAlreadyStarted: Boolean = false,
        onApplied: (T) -> Unit = {},
    ) {
        writeError = null
        if (indicate != null && !indicatorAlreadyStarted) savePillLane.saveStarted(indicate)
        writeScope.launch {
            var landed = false
            try {
                when (val result = operation()) {
                    is WriteResult.Applied -> { landed = true; onApplied(result.record) }
                    is WriteResult.Queued -> { landed = true; onApplied(result.record) }
                    is WriteResult.Rejected -> writeError = result.reason
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                writeError = error.message ?: "That change could not be saved."
            } finally {
                if (indicate != null) savePillLane.saveFinished(writeScope, success = landed)
            }
        }
    }

    fun showUndo(change: UndoableChange) {
        // Named on the add pill rather than a banner of its own: the pill
        // already narrates the deliberate saves it starts, and an undoable
        // change from elsewhere on screen is the same kind of outcome.
        savePillLane.showUndo(change.description, writeScope) {
            launchWrite({ repository.undo(change) })
        }
    }

    fun openTaskDetail(task: CalTask, origin: PocReturnTarget) {
        selectedTaskId = task.id
        taskDetailOrigin = origin
        route = PockRoute.TaskDetail
    }

    /**
     * A notification tap, landed.
     *
     * Keyed on the snapshot revision as well as the link: a cold start opens
     * before the calendar data arrives, so the first attempt usually fails and
     * the second, once a snapshot exists, succeeds. An unresolvable link still
     * moves the calendar to the day it named -- a stale notification should
     * take you to roughly the right place rather than nowhere.
     */
    LaunchedEffect(activity.pendingReminderLink, snapshot.revision) {
        val link = activity.pendingReminderLink ?: return@LaunchedEffect
        when (link.kind) {
            ReminderKind.Event -> {
                val event = ReminderDeepLinks.resolveEvent(link, snapshot.events)
                if (event != null) {
                    selectedEventId = event.id
                    selectedEventOccurrenceDay = link.occurrenceDay ?: event.placementDate()?.toEpochDay()
                    detailOrigin = PocReturnTarget.Calendar
                    route = PockRoute.Detail
                    activity.consumeReminderLink()
                } else if (snapshot.events.isNotEmpty()) {
                    link.occurrenceDay?.let { selectedDate = LocalDate.ofEpochDay(it) }
                    route = PockRoute.Day
                    activity.consumeReminderLink()
                }
            }
            ReminderKind.Task -> {
                val task = ReminderDeepLinks.resolveTask(link, snapshot.tasks)
                if (task != null) {
                    openTaskDetail(task, PocReturnTarget.Tasks)
                    activity.consumeReminderLink()
                } else if (snapshot.tasks.isNotEmpty()) {
                    route = PockRoute.Tasks
                    activity.consumeReminderLink()
                }
            }
        }
    }

    /**
     * A widget tap on a day header, landed.
     *
     * Needs no snapshot, so it applies immediately -- but it must not fight the
     * reminder link above for the route when an intent somehow carries both.
     */
    LaunchedEffect(activity.pendingAgendaDate) {
        val date = activity.pendingAgendaDate ?: return@LaunchedEffect
        if (activity.pendingReminderLink != null) return@LaunchedEffect
        selectCalendarDate(date)
        route = PockRoute.Day
        activity.consumeAgendaDate()
    }

    fun handleTaskAction(action: TaskMenuAction, task: CalTask) {
        when (action) {
            TaskMenuAction.Edit -> openTaskDetail(task, when (route) {
                PockRoute.Range -> PocReturnTarget.Range
                PockRoute.Agenda -> PocReturnTarget.Agenda
                PockRoute.Tasks -> PocReturnTarget.Tasks
                else -> PocReturnTarget.Calendar
            })
            TaskMenuAction.AddSubtask -> openQuickAdd(
                QuickAddKind.Task,
                when (route) {
                    PockRoute.Range -> PocReturnTarget.Range
                    PockRoute.Agenda -> PocReturnTarget.Agenda
                    else -> PocReturnTarget.Tasks
                },
                morphFromAddPill = true,
                parentTaskId = task.id,
            )
            TaskMenuAction.Promote -> launchWrite({ repository.reparentTask(task, null) })
            TaskMenuAction.Today -> launchWrite({ repository.rescheduleTask(task.id, now.today) }) { showUndo(it) }
            TaskMenuAction.Tomorrow -> launchWrite({ repository.rescheduleTask(task.id, now.today.plusDays(1)) }) { showUndo(it) }
            TaskMenuAction.NextWeek -> launchWrite({ repository.rescheduleTask(task.id, now.today.plusDays(7)) }) { showUndo(it) }
            TaskMenuAction.ToggleDone -> launchWrite({ repository.setTaskDone(task.id, !task.done) }) { showUndo(it) }
            TaskMenuAction.Duplicate -> launchWrite({ repository.duplicateTask(task) })
            TaskMenuAction.ConvertToEvent -> launchWrite({ repository.convertTaskToEvent(task) })
            TaskMenuAction.Delete -> pendingTaskDelete = task
        }
    }

    fun handleEventAction(action: EventMenuAction, event: CalEvent) {
        when (action) {
            EventMenuAction.Edit -> openEditor(event, when (route) {
                PockRoute.Range -> PocReturnTarget.Range
                PockRoute.Agenda -> PocReturnTarget.Agenda
                else -> PocReturnTarget.Calendar
            })
            EventMenuAction.Share -> runCatching {
                val text = IcsInterop.export(listOf(event))
                val directory = java.io.File(activity.cacheDir, "calendar-exports").also { it.mkdirs() }
                val file = java.io.File(directory, "${event.title.ifBlank { "event" }.replace(Regex("[^A-Za-z0-9._-]"), "-").take(48)}.ics")
                file.writeText(text)
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
                activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/calendar"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("Calendar event", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share event"))
            }.onFailure { writeError = "That event could not be shared." }
            EventMenuAction.Duplicate -> launchWrite({ repository.duplicateEvent(event) })
            EventMenuAction.ConvertToTask -> launchWrite({ repository.convertEventToTask(event) })
            EventMenuAction.Delete ->
                if (event.calendarId in readOnlyCalendarIds) {
                    writeError = readOnlyRefusal(event.calendarId)
                } else {
                    pendingEventDelete = event
                }
        }
    }

    /**
     * Whether a drag may move [event] at all.
     *
     * Checked before the write rather than after: the grid animates an event
     * to the finger, so a move that the repository then refuses leaves the
     * card sitting on a day it was never actually moved to until the next
     * snapshot snaps it back.
     */
    fun eventIsMovable(event: CalEvent) = event.calendarId !in readOnlyCalendarIds

    fun handleEventDrop(event: CalEvent, date: LocalDate) {
        if (event.placementDate() == date || !eventIsMovable(event)) return
        launchWrite({ repository.moveEventToDate(event, date) }, indicate = PillWriteKind.Save)
    }

    fun handleEventTimeDrop(event: CalEvent, start: java.time.LocalDateTime) {
        if (event.start == start || !eventIsMovable(event)) return
        launchWrite({ repository.moveEventToDateTime(event, start) }, indicate = PillWriteKind.Save)
    }

    fun restoreDetailOrigin() {
        when (detailOrigin) {
            PocReturnTarget.DayModal -> {
                route = PockRoute.Day
                showDayModal = true
            }
            PocReturnTarget.Agenda -> route = PockRoute.Agenda
            PocReturnTarget.Range -> route = PockRoute.Range
            PocReturnTarget.Tasks -> route = PockRoute.Tasks
            PocReturnTarget.Journal -> route = PockRoute.Journal
            PocReturnTarget.Search -> {
                route = searchOriginRoute
                searchVisible = true
            }
            else -> {
                route = PockRoute.Day
                showDayModal = false
            }
        }
    }

    fun restoreTaskDetailOrigin() {
        route = when (taskDetailOrigin) {
            PocReturnTarget.Range -> PockRoute.Range
            PocReturnTarget.Tasks -> PockRoute.Tasks
            PocReturnTarget.Agenda -> PockRoute.Agenda
            PocReturnTarget.Search -> searchOriginRoute.also { searchVisible = true }
            else -> PockRoute.Day
        }
    }

    fun dismissQuickAdd(closeDetailStack: Boolean = false) {
        editEventId = null
        aiDraft = null
        aiQueue = emptyList()
        quickAddParentTaskId = null
        quickAddStartMinute = null
        quickAddDateEpoch = null
        quickAddMorphFromAddPill = false
        if (closeDetailStack && quickAddOrigin == PocReturnTarget.Detail) {
            selectedEventId = null
            selectedEventOccurrenceDay = null
            restoreDetailOrigin()
            return
        }
        when (quickAddOrigin) {
            PocReturnTarget.Range -> {
                route = PockRoute.Range
                showDayModal = false
            }
            PocReturnTarget.DayModal -> {
                route = PockRoute.Day
                showDayModal = true
            }
            PocReturnTarget.Tasks -> {
                route = PockRoute.Tasks
                showDayModal = false
            }
            PocReturnTarget.Journal -> {
                route = PockRoute.Journal
                showDayModal = false
            }
            PocReturnTarget.Contacts -> {
                route = PockRoute.Contacts
                showDayModal = false
            }
            PocReturnTarget.Settings -> {
                route = PockRoute.Settings
                showDayModal = false
            }
            PocReturnTarget.Accounts -> {
                route = PockRoute.Accounts
                showDayModal = false
            }
            PocReturnTarget.Detail -> route = PockRoute.Detail
            PocReturnTarget.TaskDetail -> route = PockRoute.TaskDetail
            PocReturnTarget.Agenda -> {
                route = PockRoute.Agenda
                showDayModal = false
            }
            PocReturnTarget.Search -> {
                route = searchOriginRoute
                searchVisible = true
            }
            PocReturnTarget.Calendar -> {
                route = PockRoute.Day
                showDayModal = false
            }
        }
    }

    fun navigateRoot(next: PockRoute) {
        showDayModal = false
        journalReviewVisible = false
        editEventId = null
        selectedEventId = null
        selectedEventOccurrenceDay = null
        route = next
    }

    fun commitRootBack() {
        when {
            sidebarVisible -> sidebarVisible = false
            journalReviewVisible -> journalReviewVisible = false
            route == PockRoute.QuickAdd -> dismissQuickAdd(closeDetailStack = true)
            route == PockRoute.Detail -> {
                selectedEventId = null
                selectedEventOccurrenceDay = null
                restoreDetailOrigin()
            }
            route == PockRoute.TaskDetail -> {
                selectedTaskId = null
                restoreTaskDetailOrigin()
            }
            route == PockRoute.Notifications -> route = if (notificationOrigin == PocReturnTarget.Settings) PockRoute.Settings else PockRoute.Day
            route == PockRoute.Accounts -> {
                accountsAutoAdd = false
                route = when (accountsOrigin) {
                    PocReturnTarget.Settings -> PockRoute.Settings
                    PocReturnTarget.Range -> PockRoute.Range
                    PocReturnTarget.Agenda -> PockRoute.Agenda
                    else -> PockRoute.Day
                }
            }
            showDayModal -> showDayModal = false
            else -> route = PockRoute.Day
        }
    }
    val currentRootRoute = when (visibleRoute) {
        PockRoute.Day -> PockRoute.Day
        PockRoute.Range -> PockRoute.Range
        PockRoute.Agenda -> PockRoute.Agenda
        PockRoute.Tasks -> PockRoute.Tasks
        PockRoute.Journal -> PockRoute.Journal
        PockRoute.Contacts -> PockRoute.Contacts
        PockRoute.Settings -> PockRoute.Settings
        PockRoute.Accounts -> PockRoute.Accounts
        PockRoute.Detail -> detailOriginRootRoute(detailOrigin, searchOriginRoute)
        PockRoute.TaskDetail -> when (taskDetailOrigin) {
            PocReturnTarget.Agenda -> PockRoute.Agenda
            PocReturnTarget.Range -> PockRoute.Range
            PocReturnTarget.Tasks -> PockRoute.Tasks
            PocReturnTarget.Search -> searchOriginRoute
            else -> PockRoute.Day
        }
        PockRoute.QuickAdd -> when (quickAddOrigin) {
            PocReturnTarget.Agenda -> PockRoute.Agenda
            PocReturnTarget.Range -> PockRoute.Range
            PocReturnTarget.Tasks -> PockRoute.Tasks
            PocReturnTarget.Journal -> PockRoute.Journal
            PocReturnTarget.Contacts -> PockRoute.Contacts
            PocReturnTarget.Settings -> PockRoute.Settings
            PocReturnTarget.Detail -> detailOriginRootRoute(detailOrigin, searchOriginRoute)
            PocReturnTarget.Search -> searchOriginRoute
            else -> PockRoute.Day
        }
        PockRoute.Notifications -> PockRoute.Notifications
    }
    fun computePredictiveBackDestination() = when (route) {
        PockRoute.Notifications -> if (notificationOrigin == PocReturnTarget.Settings) PockRoute.Settings else PockRoute.Day
        PockRoute.Accounts -> when (accountsOrigin) {
            PocReturnTarget.Settings -> PockRoute.Settings
            PocReturnTarget.Range -> PockRoute.Range
            PocReturnTarget.Agenda -> PockRoute.Agenda
            else -> PockRoute.Day
        }
        else -> PockRoute.Day
    }
    // Captured once per gesture, at its first frame, and held there through
    // the whole drag and the settle handoff below -- never re-derived from
    // `route` after `commitRootBack()` changes it. `route` itself is what
    // the destination formula above switches on, so recomputing live after
    // commit can name a *different* screen (e.g. back from Notifications
    // lands on Settings, whose own back destination is Day): the movable
    // content keyed on this value would then be recreated mid-handoff,
    // reintroducing the exact jump this is meant to prevent.
    var predictiveBackDestination by remember { mutableStateOf<PockRoute>(PockRoute.Day) }
    val rootPredictiveBackEnabled = !sidebarVisible && !journalReviewVisible && !journalEditorVisible &&
        !showDayModal && route != PockRoute.Day && route != PockRoute.Detail &&
        route != PockRoute.TaskDetail && route != PockRoute.QuickAdd
    PredictiveBackHandler(enabled = rootPredictiveBackEnabled) { events ->
        try {
            rootBackInProgress = true
            predictiveBackDestination = computePredictiveBackDestination()
            events.collect { event ->
                rootBackProgress = PredictiveBackEasing.transform(event.progress.coerceIn(0f, 1f))
            }
            rootBackProgress = 1f
            predictiveRouteCommit = true
            commitRootBack()
            // Snapshot writes are applied together: the departing route's
            // final gesture frame is followed by the destination at rest,
            // without replaying the ordinary quarter-width route animation.
            rootBackProgress = 0f
            rootBackInProgress = false
        } catch (cancelled: CancellationException) {
            androidx.compose.animation.core.animate(
                rootBackProgress,
                0f,
                animationSpec = CalinoMotion.gestureReturn(),
            ) { value, _ -> rootBackProgress = value }
            rootBackInProgress = false
            throw cancelled
        }
    }

    val aiContextBlur by animateDpAsState(
        targetValue = if (aiBusy || aiCandidates != null) 10.dp else 0.dp,
        animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
        label = "AI context blur",
    )
    // One sync marker for the whole app. The calendar headings read it from
    // here; the Calendars screen remains the place that explains and retries.
    // Both halves are remembered: this is a static local wrapping the entire
    // app, so a value with a fresh identity on every recomposition would
    // invalidate every surface under it.
    val syncRoute = rememberUpdatedState(route)
    val openSyncDetail: () -> Unit = remember {
        {
            // Come back to the calendar the marker was tapped from, not to
            // Day: the marker is on three different roots.
            accountsOrigin = when (syncRoute.value) {
                PockRoute.Range -> PocReturnTarget.Range
                PockRoute.Agenda -> PocReturnTarget.Agenda
                else -> PocReturnTarget.Calendar
            }
            route = PockRoute.Accounts
        }
    }
    val syncStatus = remember(snapshot.sync, openSyncDetail) {
        CalinoSyncStatus(state = snapshot.sync, onOpenDetail = openSyncDetail)
    }
    // Every surface shows a slice of the tasks; a subtask's parent can sit
    // outside the slice, and still has to be nameable there.
    val taskLookup: (String) -> CalTask? = remember(calendarTasks) {
        val byId = calendarTasks.associateBy { it.id }
        ({ id: String -> byId[id] })
    }
    CompositionLocalProvider(
        LocalCalinoSync provides syncStatus,
        LocalTaskLookup provides taskLookup,
    ) {
    // Keep the blur on the calendar/content sibling only. AI surfaces are
    // drawn after this block and must stay crisp above the blurred context.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // The add pill follows the month split's right-side lane on every root
    // that exposes it. Use the same width/height rule here so changing roots
    // does not make the pill jump back to the center on a tablet in landscape.
    val tabletLandscape = shouldSplit(maxWidth.value.toInt(), maxHeight.value.toInt())
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize()
            .background(CalinoColors.Canvas)
            .blur(aiContextBlur)
            // Let each active surface paint behind the gesture-navigation
            // lane. Interactive floating controls still apply that bottom
            // inset themselves below.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        val rootRoute = currentRootRoute
        // The add pill is frosted glass over whatever surface is behind it, so
        // that surface is recorded here and the pill draws a blurred copy of
        // its own patch of it. The pill is a sibling of this stack, never a
        // child, so nothing recurses.
        val surfaceLayer = rememberGraphicsLayer()
        var surfaceOrigin by remember { mutableStateOf(Offset.Zero) }
        val rootDestination: @Composable (PockRoute) -> Unit = { currentRoute ->
            saveableStateHolder.SaveableStateProvider("root:${currentRoute.saveableKey()}") {
                when (currentRoute) {
                    PockRoute.Day -> HomeScreen(
                        repository = repository,
                        journals = snapshot.journals,
                        tasks = calendarTasks,
                        sourceEvents = snapshot.events,
                        visibleCalendarIds = visibleCalendarIds,
                        filterCalendarVisibility = true,
                        modifier = Modifier.fillMaxSize(),
                        interactionEnabled = route == PockRoute.Day && !showDayModal && !journalReviewVisible,
                        initialDate = selectedDate,
                        onOpenMenu = { sidebarVisible = true },
                        onDateChanged = ::selectCalendarDate,
                        onSwipeLabelDaysChanged = { swipeLabelDays = it },
                        onSwipeLabelTravel = { swipeLabelTravel = it },
                        onDayClick = { date -> selectCalendarDate(date); showDayModal = true; route = PockRoute.Day },
                        onEventClick = { event ->
                            selectedEventId = event.id
                            selectedEventOccurrenceDay = selectedDate.toEpochDay()
                            detailOrigin = PocReturnTarget.Calendar
                            showDayModal = false
                            route = PockRoute.Detail
                        },
                        onEventAction = ::handleEventAction,
                        onEventDrop = ::handleEventDrop,
                        onEventTimeDrop = ::handleEventTimeDrop,
                        onCreateEventAt = { start ->
                            selectCalendarDate(start.toLocalDate())
                            openQuickAdd(
                                QuickAddKind.Event,
                                PocReturnTarget.Calendar,
                                startMinute = start.toLocalTime().toSecondOfDay() / 60,
                            )
                        },
                        onTaskDrop = { task, date ->
                            launchWrite({ repository.rescheduleTask(task.id, date) }) { showUndo(it) }
                        },
                        onTaskDone = { task, done ->
                            launchWrite({ repository.setTaskDone(task.id, done) }) { showUndo(it) }
                        },
                        onTaskClick = { task ->
                            openTaskDetail(task, PocReturnTarget.Calendar)
                        },
                        onTaskAction = ::handleTaskAction,
                        onSplitPaneChanged = { splitMonthLayoutVisible = it },
                        onDayInFocusChanged = { calendarDayInFocus = it },
                    )
                    PockRoute.Range -> RangeScreen(
                        events = calendarEvents,
                        tasks = calendarTasks,
                        initialDate = selectedDate,
                        modifier = Modifier.fillMaxSize(),
                        onOpenMenu = { sidebarVisible = true },
                        onDateChanged = ::selectCalendarDate,
                        onFirstVisibleDayChanged = { rangeFirstVisibleEpoch = it.toEpochDay() },
                        onEventClick = { day, event ->
                            selectedEventId = event.id
                            selectedEventOccurrenceDay = day.toEpochDay()
                            detailOrigin = PocReturnTarget.Range
                            route = PockRoute.Detail
                        },
                        onEventAction = ::handleEventAction,
                        onEventDrop = ::handleEventDrop,
                        onEventTimeDrop = ::handleEventTimeDrop,
                        onCreateEventAt = { start ->
                            selectCalendarDate(start.toLocalDate())
                            openQuickAdd(
                                QuickAddKind.Event,
                                PocReturnTarget.Range,
                                startMinute = start.toLocalTime().toSecondOfDay() / 60,
                            )
                        },
                        onTaskClick = { task -> openTaskDetail(task, PocReturnTarget.Range) },
                        onTaskAction = ::handleTaskAction,
                        onTaskDone = { task, done ->
                            launchWrite({ repository.setTaskDone(task.id, done) }) { showUndo(it) }
                        },
                    )
                    PockRoute.Agenda -> AgendaScreen(
                        // The snapshot rather than a direct repository
                        // read: a plain read inside composition does not
                        // subscribe, so an async refresh would not repaint.
                        events = calendarEvents,
                        tasks = calendarTasks,
                        modifier = Modifier.fillMaxSize(),
                        initialDate = selectedDate,
                        onOpenMenu = { sidebarVisible = true },
                        onDateChanged = {
                            agendaPillLabelDirection = it.compareTo(selectedDate)
                            selectCalendarDate(it)
                        },
                        onSwipeLabelDaysChanged = { agendaSwipeLabelDays = it },
                        onSwipeLabelTravel = { agendaSwipeLabelTravel = it },
                        onEventClick = { day, event ->
                            selectedEventId = event.id
                            selectedEventOccurrenceDay = day.toEpochDay()
                            detailOrigin = PocReturnTarget.Agenda
                            route = PockRoute.Detail
                        },
                        onEventAction = ::handleEventAction,
                        onEventDrop = ::handleEventDrop,
                        onTaskDrop = { task, date ->
                            launchWrite({ repository.rescheduleTask(task.id, date) }) { showUndo(it) }
                        },
                        onTaskClick = { task ->
                            openTaskDetail(task, PocReturnTarget.Agenda)
                        },
                        onTaskAction = ::handleTaskAction,
                        onTaskDone = { task, done ->
                            launchWrite({ repository.setTaskDone(task.id, done) }) { showUndo(it) }
                        },
                        onAddOn = { date ->
                            selectCalendarDate(date)
                            openQuickAdd(QuickAddKind.Event, PocReturnTarget.Agenda)
                        },
                    )
                    PockRoute.Tasks -> Tasks(
                        tasks = snapshot.tasks,
                        onComplete = { task -> launchWrite({ repository.setTaskDone(task.id, true) }) },
                        onReschedule = { task ->
                            launchWrite({ repository.rescheduleTask(task.id, fallbackRescheduleDate(task.due, selectedDate, now.today)) }) {
                                showUndo(it)
                            }
                        },
                        onRescheduleTo = { task, date ->
                            launchWrite({ repository.rescheduleTask(task.id, date) }) { showUndo(it) }
                        },
                        onTaskClick = { task ->
                            openTaskDetail(task, PocReturnTarget.Tasks)
                        },
                        onUndoComplete = { task -> launchWrite({ repository.setTaskDone(task.id, false) }) },
                        onOpenMenu = { sidebarVisible = true },
                        onTaskAction = ::handleTaskAction,
                        onTaskDrop = { task, target ->
                            launchWrite({ repository.reparentTask(task, target?.id) }, indicate = PillWriteKind.Save)
                        },
                    )
                    PockRoute.Journal -> JournalSurface(
                        entries = snapshot.journals,
                        newEntryDate = selectedDate,
                        onCreate = { entry ->
                            launchWrite(operation = { repository.addJournal(NewJournal(entry.date, entry.title, entry.body)) }, indicate = PillWriteKind.Save)
                        },
                        onUpdate = { entry ->
                            launchWrite(operation = { repository.updateJournal(entry.id, NewJournal(entry.date, entry.title, entry.body)) }, indicate = PillWriteKind.Save)
                        },
                        onDelete = { entry -> launchWrite(operation = { repository.deleteJournal(entry.id) }, indicate = PillWriteKind.Remove) },
                        onEditingChanged = { editing ->
                            journalEditorVisible = editing
                            if (!editing && journalSearchReturn && journalOpenEntryId == null) {
                                journalSearchReturn = false
                                route = searchOriginRoute
                                searchVisible = true
                            }
                        },
                        openEntryId = journalOpenEntryId,
                        onOpenEntryConsumed = { journalOpenEntryId = null },
                        onOpenMenu = { sidebarVisible = true },
                        startEntryRequest = journalEntryRequest,
                    )
                    PockRoute.Contacts -> ContactsSurface(
                        contacts = snapshot.contacts,
                        addressBooks = snapshot.addressBooks,
                        events = snapshot.events,
                        selectedContactId = selectedContactId,
                        onSelectedContactChanged = { selectedContactId = it },
                        onCreate = { input ->
                            launchWrite(operation = { repository.addContact(input) }) {
                                selectedContactId = it.id
                            }
                        },
                        onUpdate = { contact ->
                            launchWrite(operation = { repository.updateContact(contact.id, contact.toNewContact()) }) {
                                selectedContactId = it.id
                            }
                        },
                        onDelete = { contact ->
                            launchWrite(
                                operation = { repository.deleteContact(contact.id) },
                                indicate = PillWriteKind.Remove,
                            ) {
                                if (selectedContactId == contact.id) selectedContactId = null
                            }
                        },
                        onAddBirthday = { contact, date, anniversary ->
                            savePillLane.saveStarted(PillWriteKind.Save)
                            var landed = false
                            try {
                                repository.addLocalEvent(
                                    contactReminderEvent(
                                        contact = contact,
                                        date = date,
                                        calendarId = snapshot.calendars.firstOrNull()?.id ?: "personal",
                                        anniversary = anniversary,
                                    ),
                                )
                                landed = true
                            } catch (error: Throwable) {
                                writeError = error.message ?: "That reminder could not be saved."
                            } finally {
                                savePillLane.saveFinished(writeScope, success = landed)
                            }
                        },
                        onOpenMenu = { sidebarVisible = true },
                        startEntryRequest = contactRequest,
                    )
                    PockRoute.Settings -> SettingsSurface(
                        onOpenNotifications = {
                            notificationOrigin = PocReturnTarget.Settings
                            route = PockRoute.Notifications
                        },
                        onOpenMenu = { sidebarVisible = true },
                        calDavAccounts = calDavAccounts,
                        onOpenAccounts = { startAdding, focusAccountId ->
                            accountsOrigin = PocReturnTarget.Settings
                            accountsAutoAdd = startAdding
                            accountsFocusId = focusAccountId
                            route = PockRoute.Accounts
                        },
                        openAiVisionRequest = openAiSettingsRequest,
                        onImportCalendar = { importLauncher.launch(arrayOf("text/calendar", "application/ics", "application/octet-stream")) },
                        onExportCalendar = { exportCalendarPicker = true },
                        webcalSubscriptions = webcalSubscriptions,
                        onSubscribeWebcal = { form -> pocViewModel.addWebcalSubscription(form) },
                        onRemoveWebcal = pocViewModel::removeWebcalSubscription,
                        onSyncWebcal = pocViewModel::syncWebcal,
                        onToggleWebcalNotify = pocViewModel::onWebcalNotifyRemindersChanged,
                    )
                    PockRoute.Accounts -> CalendarAccountsSurface(
                        accounts = calDavAccounts,
                        client = pocViewModel.calDavClient,
                        onAddAccount = { form, calendars -> pocViewModel.onAccountConnected(form, calendars) },
                        onCalendarEnabled = { accountId, calendarId, enabled ->
                            pocViewModel.onCalendarEnabled(accountId, calendarId, enabled)
                        },
                        onAddressBookEnabled = { accountId, addressBookId, enabled ->
                            pocViewModel.onAddressBookEnabled(accountId, addressBookId, enabled)
                        },
                        onRemoveAccount = { pocViewModel.onAccountRemoved(it) },
                        syncState = snapshot.sync,
                        onRefresh = { pocViewModel.refresh() },
                        pendingChanges = pendingChanges,
                        onRetryPendingChange = { pocViewModel.retryPendingChange(it) },
                        onDiscardPendingChange = { pocViewModel.discardPendingChange(it) },
                        modifier = Modifier.fillMaxSize(),
                        onOpenMenu = { sidebarVisible = true },
                        startAdding = accountsAutoAdd,
                        onStartAddingConsumed = { accountsAutoAdd = false },
                        focusAccountId = accountsFocusId,
                        onFocusAccountConsumed = { accountsFocusId = null },
                    )
                    PockRoute.Detail, PockRoute.TaskDetail -> Unit
                    PockRoute.Notifications -> NotificationsSurface(
                        state = rememberNotificationSurfaceState(),
                        onOpenFiring = { firing ->
                            // The surface lists what is scheduled, so a row is
                            // a way into the record just as the notification is.
                            val link = ReminderDeepLink(
                                kind = firing.kind,
                                recordId = firing.recordId,
                                uid = firing.uid,
                                occurrenceDay = firing.occurrenceDay,
                            )
                            when (firing.kind) {
                                ReminderKind.Event -> ReminderDeepLinks.resolveEvent(link, snapshot.events)?.let { event ->
                                    selectedEventId = event.id
                                    selectedEventOccurrenceDay = firing.occurrenceDay ?: event.placementDate()?.toEpochDay()
                                    detailOrigin = PocReturnTarget.Settings
                                    route = PockRoute.Detail
                                }
                                ReminderKind.Task -> ReminderDeepLinks.resolveTask(link, snapshot.tasks)?.let { task ->
                                    openTaskDetail(task, PocReturnTarget.Settings)
                                }
                            }
                        },
                    )
                    PockRoute.QuickAdd -> Unit
                }
            }
        }
        // The predictive-back destination is composed once, through a movable
        // reference, and only ever migrates between the live preview below
        // and the settled AnimatedContent slot -- never disposed and
        // recreated between them. Two separate `rootDestination(...)` call
        // sites here would each mount their own composition of "the same"
        // screen: `remember` state built up while the preview tracked the
        // finger (scroll position, in-flight layout) would be discarded the
        // instant the gesture committed and a fresh instance took over,
        // which read as a one-frame jump, and dragged the header controls
        // (menu button) along with it since they live inside that subtree.
        val predictiveDestinationContent = remember(predictiveBackDestination) {
            movableContentOf { rootDestination(predictiveBackDestination) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { surfaceOrigin = it.positionInRoot() }
                .drawWithContent {
                    surfaceLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(surfaceLayer)
                },
        ) {
            if (rootBackInProgress) {
                val destinationProgress = ((rootBackProgress - PredictiveBackFadeThreshold) /
                    (1f - PredictiveBackFadeThreshold)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.1f - .1f * destinationProgress
                            scaleY = scaleX
                            alpha = destinationProgress
                        },
                ) {
                    predictiveDestinationContent()
                }
            }
            AnimatedContent(
                targetState = rootRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1f - .1f * rootBackProgress
                        scaleY = scaleX
                        alpha = 1f - (rootBackProgress / PredictiveBackFadeThreshold)
                            .coerceIn(0f, 1f)
                        shape = RoundedCornerShape(28.dp)
                        clip = rootBackProgress > 0f
                    },
                transitionSpec = {
                    if (predictiveRouteCommit) {
                        return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None
                    }
                    val direction = if (targetState.rootOrder() >= initialState.rootOrder()) 1 else -1
                    (slideInHorizontally(tween(260)) { direction * it / 4 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(210)) { -direction * it / 4 } + fadeOut(tween(140)))
                },
                label = "root destination transition",
            ) { currentRoute ->
                if (currentRoute == predictiveBackDestination) {
                    predictiveDestinationContent()
                } else {
                    rootDestination(currentRoute)
                }
            }
            LaunchedEffect(rootRoute, predictiveRouteCommit) {
                if (predictiveRouteCommit) {
                    // Keep the no-transition decision stable until the new
                    // destination has owned complete frames. Clearing this
                    // from transitionSpec itself raced recomposition on real
                    // devices and let the ordinary slide replay after commit.
                    withFrameNanos {}
                    withFrameNanos {}
                    predictiveRouteCommit = false
                }
            }
        }

        when (route) {
            PockRoute.Detail -> selectedEvent?.let { event ->
                EventDetail(
                    event = event,
                    readOnly = event.calendarId in readOnlyCalendarIds,
                    events = remember(snapshot.events, selectedEventOccurrenceDay) {
                        snapshot.events.filter {
                            it.occursOn(selectedEventOccurrenceDay?.let(LocalDate::ofEpochDay) ?: selectedDate)
                        }.sortedWith(compareBy<CalEvent> { !it.allDay }
                            .thenBy { it.start?.toLocalTime() }.thenBy { it.id })
                            .ifEmpty { listOf(event) }
                    },
                    onEventSelected = { selectedEventId = it.id },
                    onEditEvent = { openEditor(it, PocReturnTarget.Detail) },
                    onDeleteEvent = { target, scope ->
                        // Which occurrence the card was showing, read before
                        // the selection is cleared: a scoped delete that lost
                        // it would fall back to the series anchor and remove
                        // the wrong day.
                        val occurrence = selectedEventOccurrenceDay?.let(LocalDate::ofEpochDay)
                        // Leave the detail route as soon as its exit animation
                        // completes; the write itself may be queued and must
                        // not cause the detail surface to remount underneath.
                        selectedEventId = null
                        selectedEventOccurrenceDay = null
                        restoreDetailOrigin()
                        launchWrite(
                            { repository.deleteEvent(target.id, scope, occurrence) },
                            indicate = PillWriteKind.Remove,
                        )
                    },
                    onEventAction = ::handleEventAction,
                    onInlineSave = { target, input, scope ->
                        writeError = null
                        savePillLane.saveStarted(PillWriteKind.Save)
                        var landed = false
                        try {
                            when (val result = repository.updateEvent(target.id, input.copy(recurrenceScope = scope))) {
                                is WriteResult.Applied -> true.also { landed = true }
                                is WriteResult.Queued -> true.also { landed = true }
                                is WriteResult.Rejected -> { writeError = result.reason; false }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            writeError = error.message ?: "That change could not be saved."
                            false
                        } finally {
                            savePillLane.saveFinished(writeScope, success = landed)
                        }
                    },
                    occurrenceDate = selectedEventOccurrenceDay?.let(LocalDate::ofEpochDay),
                    onBack = {
                        selectedEventId = null
                        selectedEventOccurrenceDay = null
                        restoreDetailOrigin()
                    },
                    // The editor is an overlay route, so the selected
                    // event stays set and a save lands back on this
                    // detail surface.
                    onPrimaryAction = {
                        selectedEventId = event.id
                        openEditor(event, PocReturnTarget.Detail)
                    },
                )
            }
            PockRoute.TaskDetail -> selectedTask?.let { task ->
                TaskDetail(
                    task = task,
                    tasks = snapshot.tasks,
                    onBack = {
                        selectedTaskId = null
                        restoreTaskDetailOrigin()
                    },
                    onSave = { input, done ->
                        launchWrite({ repository.updateTask(task.id, input, done) }, indicate = PillWriteKind.Save) {
                            selectedTaskId = null
                            restoreTaskDetailOrigin()
                        }
                    },
                    onAddSubtask = {
                        openQuickAdd(QuickAddKind.Task, PocReturnTarget.TaskDetail, morphFromAddPill = true, parentTaskId = task.id)
                    },
                )
            }
            else -> Unit
        }

        if (calendarDayModalVisible) {
            DayModalSurface(
                date = selectedDate,
                events = calendarEvents,
                journals = snapshot.journals,
                onDateChanged = ::selectCalendarDate,
                onDismiss = { showDayModal = false; route = PockRoute.Day },
                onAdd = {
                    // Unmount the day sheet while Quick Add owns the
                    // overlay. The return target restores a fresh sheet,
                    // avoiding a hidden modal left behind the editor.
                    showDayModal = false
                    openQuickAdd(QuickAddKind.Event, PocReturnTarget.DayModal)
                },
                onEvent = { event ->
                    selectedEventId = event.id
                    selectedEventOccurrenceDay = selectedDate.toEpochDay()
                    detailOrigin = PocReturnTarget.DayModal
                    showDayModal = false
                    route = PockRoute.Detail
                },
                onJournal = { journal ->
                    journalOpenEntryId = journal.id
                    showDayModal = false
                    route = PockRoute.Journal
                },
            )
        }

        when (route) {
            PockRoute.QuickAdd -> {
                val editing = editEventId?.let { id -> snapshot.events.firstOrNull { it.id == id } }
                val quickAddDate = quickAddDateEpoch?.let(LocalDate::ofEpochDay) ?: selectedDate
                QuickAddSheet(
                    state = QuickAddSheetState(
                        visible = true,
                        kind = quickAddKind,
                        date = quickAddDate,
                        morphFromAddPill = quickAddMorphFromAddPill,
                        draft = editing
                            ?.let(::editorDraftFor)
                            ?: aiDraft
                            ?: externalDraft
                            ?: run {
                                val defaults = LocalCalinoPreferences.current
                                blankEditorDraft(
                                    kind = quickAddKind.toParserKind(),
                                    date = quickAddDate,
                                    title = quickAddSeed,
                                    defaultDurationMinutes = defaults.defaultDuration.minutes,
                                    defaultReminderMinutes = defaults.defaultReminder.minutesBefore,
                                ).copy(
                                    startTime = quickAddStartMinute?.let {
                                        java.time.LocalTime.MIDNIGHT.plusMinutes(it.toLong())
                                    },
                                    parentTaskId = quickAddParentTaskId,
                                    touched = if (quickAddStartMinute != null) {
                                        setOf(calino.malinov.ski.data.model.EditorField.Time)
                                    } else {
                                        emptySet()
                                    },
                                )
                            },
                    ),
                    calendars = snapshot.calendars,
                    categories = snapshot.categories,
                    relatedCandidates = remember(snapshot.tasks) {
                        snapshot.tasks.filterNot { it.done }.map { it.id to it.title }
                    },
                    onPhoto = if (quickAddKind == QuickAddKind.Event && aiSettingsStore.load().hasApiKey) ::requestPhotoImport else null,
                    // Opening the full editor replaces the compact event
                    // preview. Cancelling it closes that whole modal stack;
                    // successful saves still use dismissQuickAdd() below to
                    // return to the refreshed event preview.
                    onDismiss = { dismissQuickAdd(closeDetailStack = true) },
                    // The editor owns every field now, so the host only
                    // decides between creating and updating a record.
                    // Where this editor is going back to, decided as Save is
                    // pressed rather than once the record is written: the pill
                    // starts morphing back immediately, and it morphs into the
                    // add pill of the screen it will land on.
                    onSaveStarted = { draft ->
                        savePillLane.saveStarted(PillWriteKind.Save)
                        if (draft.kind == PocQuickAddKind.Journal) {
                            journalReviewVisible = false
                            quickAddOrigin = PocReturnTarget.Journal
                        }
                    },
                    onSave = { draft ->
                        launchWrite(
                            operation = { saveEditorDraft(repository, draft) },
                            indicate = PillWriteKind.Save,
                            indicatorAlreadyStarted = true,
                        ) {
                            selectedDate = draft.date
                            if (aiQueue.isNotEmpty()) {
                                val next = aiQueue.first()
                                aiQueue = aiQueue.drop(1)
                                aiDraft = aiDraftFor(next, selectedDate)
                                quickAddKind = if (next.kind == "task") QuickAddKind.Task else QuickAddKind.Event
                            } else {
                                aiDraft = null
                                dismissQuickAdd()
                            }
                        }
                    },
                )
            }
            else -> Unit
        }

        // Keep feedback in one compact lane above the add pill. The old
        // full-width banners made ordinary list content feel blocked, and the
        // error variant had no automatic expiry.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = CalinoSpacing.PillClearance + 8.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = writeError != null,
                enter = slideInVertically(tween(200), initialOffsetY = { it / 2 }) + fadeIn(tween(170)),
                exit = slideOutVertically(tween(170), targetOffsetY = { it / 2 }) + fadeOut(tween(130)),
            ) {
                writeError?.let { message ->
                    CalinoToast(
                        message = message,
                        icon = CalinoIcon.Bell,
                        accent = CalinoColors.Rose,
                        actionLabel = "Dismiss",
                        onAction = { writeError = null },
                    )
                }
            }
        }

        // The add affordance floats over the surfaces instead of taking
        // layout space; every scrollable root reserves PillClearance for it.
        val pillLane = LocalCalinoPillLane.current
        // Keep the root glass source in the lane before any modal exists. A
        // modal can then draw its first collapsed frame from the same source;
        // its own recorded card backdrop replaces this on the next effect.
        androidx.compose.runtime.SideEffect {
            pillLane.setRootBackdrop(surfaceLayer, surfaceOrigin)
        }
        // The silent handback lasts only until this pill is back on screen.
        LaunchedEffect(pillLane.handingBack) {
            if (pillLane.handingBack) {
                withFrameNanos {}
                withFrameNanos {}
                pillLane.handingBack = false
            }
        }
        val pillVisible = when (rootRoute) {
            PockRoute.Day -> route == PockRoute.Day && !showDayModal && !journalReviewVisible && editEventId == null
            PockRoute.Range -> route == PockRoute.Range
            PockRoute.Agenda -> route == PockRoute.Agenda
            PockRoute.Tasks -> route == PockRoute.Tasks
            PockRoute.Journal -> route == PockRoute.Journal && !journalEditorVisible
            PockRoute.Contacts -> route == PockRoute.Contacts && selectedContactId == null
            else -> false
        }
        // A predictive-back gesture fades the pill continuously with the
        // outgoing surface instead of toggling `pillVisible`: the origin and
        // destination routes are both pill-eligible almost every time this
        // gesture runs, so a boolean gate never actually changes value across
        // the gesture -- it only flips once, right as the gesture completes,
        // which made `AnimatedVisibility` replay a full slide/fade entrance
        // at the exact moment everything else had already settled. Deriving
        // the alpha straight from `rootBackProgress` keeps it locked to the
        // same frame-by-frame value the content behind it already fades on.
        val predictiveBackPillAlpha = 1f - (rootBackProgress / PredictiveBackFadeThreshold).coerceIn(0f, 1f)
        // A modal's pill takes this lane over and morphs out of this pill's
        // shape, so the handoff in both directions has to be silent: sliding
        // this one away under the modal's, or back in under its returning
        // shape, would show two pills where the whole point is one.
        // The claim decides how this pill leaves and returns, never whether:
        // `pillVisible` is already false for every surface that hosts a lane
        // pill, and it flips in the same composition as the route, while the
        // claim is released a frame later, at disposal. Gating visibility on
        // the claim as well left one frame with no pill in the lane at all,
        // which is the blink at the end of the morph.
        // What the root pill says, published whether or not it is on screen:
        // a modal pill morphs back into this exact label, and it can change
        // (the selected day moves) while a modal holds the lane.
        // A 3- or 7-day range shows no selected day, so naming one here would
        // name a day the person never picked. The pill stays generic and the
        // editor it opens anchors on the first day on screen.
        // The fully expanded month is the same story: the grid fills the
        // surface, no day is picked out of it, and only the tablet split --
        // which keeps a day pane beside the grid -- still has a day to name.
        val rangeSpansDays = rootRoute == PockRoute.Range && preferences.rangeMode.dayCount > 1
        val monthNamesNoDay = rootRoute == PockRoute.Day && !calendarDayInFocus
        val addPillLabel = when {
            rootRoute == PockRoute.Tasks -> "New task"
            rootRoute == PockRoute.Journal -> "New entry"
            rootRoute == PockRoute.Contacts -> "New contact"
            rangeSpansDays || monthNamesNoDay -> "New event"
            else -> "Add on ${selectedDate.format(DateLabel)}"
        }
        androidx.compose.runtime.SideEffect { pillLane.addPillLabel = addPillLabel }
        val laneHandoff = pillLane.claimedByModal || pillLane.handingBack
        androidx.compose.animation.AnimatedVisibility(
            visible = pillVisible && !sidebarVisible && !searchVisible,
            enter = if (laneHandoff) EnterTransition.None else slideInVertically(tween(240), initialOffsetY = { it }) + fadeIn(tween(180)),
            exit = if (laneHandoff) ExitTransition.None else slideOutVertically(tween(200), targetOffsetY = { it }) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .graphicsLayer { alpha = predictiveBackPillAlpha },
            label = "add pill visibility",
        ) {
            // In the large landscape split the pill rides in the right-side
            // lane. Keep that lane even when the day pane is collapsed, so
            // toggling the pane does not recenter the pill.
            val pillLaneWidth by animateDpAsState(
                targetValue = if (splitMonthLayoutVisible || tabletLandscape) EndLaneWidthDp.dp else 0.dp,
                animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
                label = "add pill lane",
            )
            // The pill also carries the three main views: a horizontal
            // drag steps through them in the same order the sidebar lists.
            val pillRoutes = listOfNotNull(
                PockRoute.Day,
                PockRoute.Range,
                PockRoute.Agenda,
                PockRoute.Tasks,
                PockRoute.Journal.takeIf { preferences.journalEnabled },
                PockRoute.Contacts.takeIf { preferences.contactsEnabled },
            )
            val pillIndex = pillRoutes.indexOf(rootRoute)
            Box(
                if (pillLaneWidth > 0.dp) Modifier.width(pillLaneWidth) else Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
            AddPill(
                // AnimatedVisibility can retain its outgoing content for one
                // draw after a modal has claimed the lane. The modal pill is
                // already drawing at the same coordinates on that frame, so
                // stacking the two translucent surfaces doubles both the
                // shadow and the glass fill. Keep the root pill measured for
                // its handoff anchor, but let only the lane owner paint.
                modifier = Modifier.graphicsLayer {
                    alpha = if (pillLane.claimedByModal) 0f else 1f
                },
                backdrop = surfaceLayer,
                backdropOrigin = { surfaceOrigin },
                canSwipe = { direction -> pillIndex >= 0 && (pillIndex + direction) in pillRoutes.indices },
                destinationLabel = { direction ->
                    pillRoutes.getOrNull(pillIndex + direction)?.let(::pockRouteLabel)
                },
                onSwipe = { direction ->
                    pillRoutes.getOrNull(pillIndex + direction)?.let(::navigateRoot)
                },
                onSearch = {
                    searchOriginRoute = rootRoute
                    searchVisible = true
                },
                label = addPillLabel,
                labelSlideDirection = if (rootRoute == PockRoute.Agenda) agendaPillLabelDirection else 0,
                // A swipe names both of the days it is between and lets the
                // pill carry them across the gesture, rather than renaming
                // itself once everything has settled.
                swipeLabels = when {
                    rangeSpansDays || monthNamesNoDay -> null
                    rootRoute == PockRoute.Agenda -> agendaSwipeLabelDays
                    rootRoute == PockRoute.Day || rootRoute == PockRoute.Range -> swipeLabelDays
                    else -> null
                }?.let { "Add on ${it.from.format(DateLabel)}" to "Add on ${it.to.format(DateLabel)}" },
                swipeTravel = {
                    if (rootRoute == PockRoute.Agenda) agendaSwipeLabelTravel() else swipeLabelTravel()
                },
                confirmationActive = pendingEventDelete != null || pendingTaskDelete != null,
                onConfirmationExpired = {
                    pendingEventDelete = null
                    pendingTaskDelete = null
                },
                onConfirmed = {
                    val event = pendingEventDelete
                    val task = pendingTaskDelete
                    pendingEventDelete = null
                    pendingTaskDelete = null
                    when {
                        event != null -> launchWrite(
                            {
                                repository.deleteEvent(
                                    event.id,
                                    defaultEventDeleteScope(event),
                                    event.placementDate(),
                                )
                            },
                            indicate = PillWriteKind.Remove,
                        )
                        task != null -> launchWrite(
                            { repository.deleteTask(task.id, task.recurrenceScope) },
                            indicate = PillWriteKind.Remove,
                        )
                    }
                },
                onClick = {
                    when (rootRoute) {
                        PockRoute.Tasks -> openQuickAdd(QuickAddKind.Task, PocReturnTarget.Tasks, morphFromAddPill = true)
                        PockRoute.Journal -> journalEntryRequest += 1
                        PockRoute.Contacts -> contactRequest += 1
                        PockRoute.Range -> openQuickAdd(
                            QuickAddKind.Event,
                            PocReturnTarget.Range,
                            morphFromAddPill = true,
                            date = if (rangeSpansDays) {
                                rangeFirstVisibleEpoch?.let(LocalDate::ofEpochDay)
                            } else {
                                null
                            },
                        )
                        // The agenda used to fall through to the calendar's
                        // origin, so finishing an event there put the person
                        // in the month view they never asked for. Every root
                        // that can add names itself.
                        PockRoute.Agenda -> openQuickAdd(QuickAddKind.Event, PocReturnTarget.Agenda, morphFromAddPill = true)
                        else -> openQuickAdd(QuickAddKind.Event, PocReturnTarget.Calendar, morphFromAddPill = true)
                    }
                },
            )
            }
        }

        NavSidebar(
            visible = sidebarVisible,
            selectedRoute = rootRoute,
            onRoute = { next -> navigateRoot(next) },
            onDismiss = { sidebarVisible = false },
            snapshot = snapshot,
            accounts = calDavAccounts,
            selectedDate = selectedDate,
            onDateChanged = ::selectCalendarDate,
            onToggleCalendar = { accountId, calendarId, visible ->
                if (accountId == WebcalSubscription.AccountId) {
                    pocViewModel.webcalStore.findByCalendarId(calendarId)?.let {
                        pocViewModel.onWebcalVisibilityChanged(it.id, visible)
                    }
                } else {
                    pocViewModel.onCalendarVisibilityChanged(accountId, calendarId, visible)
                }
            },
            onToggleCalendarTasks = { accountId, calendarId, show ->
                if (accountId != WebcalSubscription.AccountId) {
                    pocViewModel.onCalendarTasksChanged(accountId, calendarId, show)
                }
            },
            fixtureHiddenCalendarIds = fixtureHiddenCalendarIds,
            fixtureHiddenTaskCalendarIds = fixtureHiddenTaskCalendarIds,
            onToggleFixtureCalendar = { calendarId, visible ->
                fixtureHiddenCalendarIds = if (visible) fixtureHiddenCalendarIds - calendarId else fixtureHiddenCalendarIds + calendarId
            },
            onToggleFixtureCalendarTasks = { calendarId, visible ->
                fixtureHiddenTaskCalendarIds = if (visible) fixtureHiddenTaskCalendarIds - calendarId else fixtureHiddenTaskCalendarIds + calendarId
            },
            onRenameCalendar = { accountId, calendarId, name ->
                if (accountId == WebcalSubscription.AccountId ||
                    WebcalSubscription.isWebcalCalendarId(calendarId)
                ) {
                    pocViewModel.onWebcalRenamedByCalendarId(calendarId, name)
                } else {
                    pocViewModel.onCalendarRenamed(accountId, calendarId, name)
                }
            },
            onColorCalendar = { accountId, calendarId, color ->
                if (accountId == WebcalSubscription.AccountId) {
                    pocViewModel.webcalStore.findByCalendarId(calendarId)?.let {
                        pocViewModel.onWebcalColorChanged(it.id, color)
                    }
                } else {
                    pocViewModel.onCalendarColorChanged(accountId, calendarId, color)
                }
            },
            onSyncAll = { pocViewModel.refresh() },
            onSyncCalendar = { accountId, calendarId ->
                if (accountId == WebcalSubscription.AccountId) {
                    pocViewModel.webcalStore.findByCalendarId(calendarId)?.let {
                        pocViewModel.syncWebcal(it.id)
                    }
                } else {
                    pocViewModel.refresh()
                }
            },
            onTaskClick = { task ->
                sidebarVisible = false
                openTaskDetail(task, PocReturnTarget.Tasks)
            },
            onTaskComplete = { task, done -> launchWrite({ repository.setTaskDone(task.id, done) }) },
            onTaskAction = { action, task ->
                if (action == TaskMenuAction.Edit || action == TaskMenuAction.AddSubtask || action == TaskMenuAction.Delete) {
                    sidebarVisible = false
                }
                handleTaskAction(action, task)
            },
        )
    }
    }

    if (searchVisible) {
        CalinoSearchSheet(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            snapshot = snapshot,
            baseDate = selectedDate,
            onDismiss = { searchVisible = false; searchQuery = "" },
            onSelect = { result ->
                searchVisible = false
                when (result) {
                    is CalinoSearchResult.NavigateDate -> {
                        selectedDate = result.date
                        navigateRoot(PockRoute.Day)
                        searchQuery = ""
                    }
                    is CalinoSearchResult.AddEvent -> {
                        selectedDate = result.parsed.date
                        quickAddSeed = result.raw
                        quickAddKind = QuickAddKind.Event
                        quickAddMorphFromAddPill = false
                        quickAddOrigin = PocReturnTarget.Search
                        editEventId = null
                        route = PockRoute.QuickAdd
                    }
                    is CalinoSearchResult.Event -> {
                        selectedEventId = result.event.id
                        selectedEventOccurrenceDay = result.event.placementDate()?.toEpochDay()
                        detailOrigin = PocReturnTarget.Search
                        route = PockRoute.Detail
                    }
                    is CalinoSearchResult.Task -> {
                        selectedTaskId = result.task.id
                        taskDetailOrigin = PocReturnTarget.Search
                        route = PockRoute.TaskDetail
                    }
                    is CalinoSearchResult.Journal -> {
                        journalOpenEntryId = result.journal.id
                        journalSearchReturn = true
                        route = PockRoute.Journal
                    }
                    is CalinoSearchResult.Contact -> {
                        selectedContactId = result.contact.id
                        route = PockRoute.Contacts
                    }
                }
            },
        )
    }

    if (journalReviewVisible) {
        JournalReviewDialog(
            journals = snapshot.journals,
            onDismiss = { journalReviewVisible = false },
        )
    }

    importBatch?.let { batch ->
        val writable = snapshot.calendars.filter { !it.readOnly && it.accepts("VEVENT") }
        AlertDialog(
            onDismissRequest = { importBatch = null },
            title = { Text("Import ${batch.events.size} event${if (batch.events.size == 1) "" else "s"}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (batch.unsupportedComponents > 0) Text("${batch.unsupportedComponents} task or journal component(s) will be ignored.")
                    if (batch.duplicateUids.isNotEmpty()) Text("${batch.duplicateUids.size} duplicate event(s) will be skipped.")
                    Text("Choose a destination calendar:")
                    writable.forEach { calendar ->
                        TextButton(onClick = { importCalendarId = calendar.id }) {
                            Text(if (importCalendarId == calendar.id) "✓ ${calendar.name}" else calendar.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = importCalendarId != null, onClick = {
                    val target = importCalendarId ?: return@TextButton
                    val candidates = batch.events.filterNot { it.uid in batch.duplicateUids }
                    importBatch = null
                    savePillLane.saveStarted(PillWriteKind.Save)
                    writeScope.launch {
                        var imported = 0; var queued = 0; var failed = 0
                        try {
                            candidates.forEach { event ->
                                when (repository.addEvent(IcsInterop.asNewEvent(event, target))) {
                                    is WriteResult.Applied -> imported++
                                    is WriteResult.Queued -> queued++
                                    is WriteResult.Rejected -> failed++
                                }
                            }
                        } finally {
                            savePillLane.saveFinished(
                                writeScope,
                                success = failed == 0 && imported + queued == candidates.size,
                            )
                        }
                        writeError = "Imported $imported${if (queued > 0) ", queued $queued" else ""}${if (batch.duplicateUids.isNotEmpty()) ", skipped ${batch.duplicateUids.size}" else ""}${if (failed > 0) ", failed $failed" else ""}."
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { importBatch = null }) { Text("Cancel") } },
        )
    }
    if (exportCalendarPicker) AlertDialog(
        onDismissRequest = { exportCalendarPicker = false },
        title = { Text("Export calendar") },
        text = {
            Column {
                snapshot.calendars.forEach { calendar ->
                    TextButton(onClick = {
                        exportCalendarPicker = false
                        writeScope.launch {
                            runCatching {
                                if (pocViewModel.hasAccounts) pocViewModel.exportCalendarEvents(calendar.id)
                                else IcsInterop.export(snapshot.events.filter { it.calendarId == calendar.id })
                            }.onSuccess { text ->
                                pendingExportText = text
                                exportLauncher.launch("${calendar.name.replace(Regex("[^A-Za-z0-9._-]"), "-")}.ics")
                            }.onFailure { writeError = it.message ?: "A complete calendar export could not be read." }
                        }
                    }) { Text(calendar.name) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { exportCalendarPicker = false }) { Text("Cancel") } },
    )

    AiProcessingOverlay(aiBusy, aiStage)
    AiCandidateReview(aiCandidates, onCancel = { aiCandidates = null }) { selected ->
        aiCandidates = null
        if (selected.isNotEmpty()) {
            val first = selected.first()
            aiQueue = selected.drop(1)
            aiDraft = aiDraftFor(first, selectedDate)
            selectedDate = aiDraft!!.date
            openQuickAdd(if (first.kind == "task") QuickAddKind.Task else QuickAddKind.Event, PocReturnTarget.Calendar)
        }
    }
    if (showPhotoSource) AlertDialog(
        onDismissRequest = { showPhotoSource = false },
        title = { Text("Import from photo") },
        text = { Text("Take a photo or choose one already on this device.") },
        confirmButton = { TextButton(onClick = { showPhotoSource = false; cameraLauncher.launch(null) }) { Text("Camera") } },
        dismissButton = { TextButton(onClick = { showPhotoSource = false; galleryLauncher.launch("image/*") }) { Text("Photos") } },
    )
    aiError?.let { message ->
        AlertDialog(
            onDismissRequest = { aiError = null },
            title = { Text("AI Photo Import") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = {
                aiError = null
                if (aiErrorNeedsSettings) { openAiSettingsRequest += 1; route = PockRoute.Settings }
                else openQuickAdd(QuickAddKind.Event, PocReturnTarget.Calendar)
            }) { Text(if (aiErrorNeedsSettings) "Open settings" else "Add manually") } },
            dismissButton = { TextButton(onClick = { aiError = null }) { Text("Cancel") } },
        )
    }

}
}
}
}

@Composable
private fun rememberWebcalSubscriptions(
    store: calino.malinov.ski.data.repository.WebcalSubscriptionStore,
): List<WebcalSubscription> {
    var subscriptions by remember(store) { mutableStateOf(store.subscriptions()) }
    DisposableEffect(store) {
        val subscription = store.observe { subscriptions = it }
        onDispose { subscription.close() }
    }
    return subscriptions
}

@Composable
private fun rememberCalDavAccounts(store: CalDavAccountStore): List<CalDavAccount> {
    var accounts by remember(store) { mutableStateOf(store.accounts()) }
    DisposableEffect(store) {
        val subscription = store.observe { accounts = it }
        onDispose { subscription.close() }
    }
    return accounts
}

@Composable
private fun rememberRepositorySnapshot(repository: CalinoRepository): CalinoSnapshot {
    var snapshot by remember(repository) { mutableStateOf(repository.snapshot()) }
    DisposableEffect(repository) {
        val subscription = repository.observe { snapshot = it }
        onDispose { subscription.close() }
    }
    return snapshot
}

@Composable
private fun JournalReviewDialog(journals: List<JournalEntry>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = CalinoColors.Panel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Journal", style = CalinoTypography.titleLarge)
                        Text("Local entries", color = CalinoColors.Ink3, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Text("×", fontSize = 22.sp, color = CalinoColors.Ink2) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(journals.asReversed(), key = { it.id }) { journal ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(CalinoColors.Canvas, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(journal.title.ifBlank { "Untitled note" }, style = CalinoTypography.bodyLarge)
                            Text(journal.date.format(DateLabel), color = CalinoColors.Ink3, fontSize = 11.sp)
                            Text(journal.body, color = CalinoColors.Ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(CalinoColors.Ink),
                ) { Text("Done") }
            }
        }
    }
}

/** Routes a finished draft to the add or update call for its kind. */
private suspend fun saveEditorDraft(repository: CalinoRepository, draft: EditorDraft): WriteResult<*> {
    val id = draft.editingId
    return when (draft.kind) {
        PocQuickAddKind.Event ->
            if (id == null) repository.addEvent(draft.toNewEvent())
            else repository.updateEvent(id, draft.toNewEvent())
        PocQuickAddKind.Task -> {
            val done = id?.let { taskId -> repository.tasks().firstOrNull { it.id == taskId }?.done } ?: false
            if (id == null) repository.addTask(draft.toNewTask())
            else repository.updateTask(id, draft.toNewTask(), done)
        }
        PocQuickAddKind.Journal ->
            if (id == null) repository.addJournal(draft.toNewJournal())
            else repository.updateJournal(id, draft.toNewJournal())
    }
}

private fun aiDraftFor(candidate: AiEventCandidate, fallbackDate: LocalDate): EditorDraft {
    val start = candidate.start
    val minutes = if (start != null && candidate.end != null) {
        java.time.Duration.between(start, candidate.end).toMinutes().toInt().takeIf { it > 0 }
    } else null
    val kind = if (candidate.kind == "task") PocQuickAddKind.Task else PocQuickAddKind.Event
    return EditorDraft(
        kind = kind,
        rawInput = candidate.title.orEmpty(),
        title = candidate.title.orEmpty(),
        date = start?.toLocalDate() ?: fallbackDate,
        startTime = if (candidate.allDay) null else start?.toLocalTime(),
        durationMinutes = minutes ?: if (kind == PocQuickAddKind.Event) EditorDraft.DefaultDurationMinutes else null,
        allDay = candidate.allDay,
        location = candidate.location,
        description = candidate.description,
        touched = calino.malinov.ski.data.model.EditorField.entries.toSet(),
    )
}
