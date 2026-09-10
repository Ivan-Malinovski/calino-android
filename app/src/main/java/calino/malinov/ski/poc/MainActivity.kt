package calino.malinov.ski.poc

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
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
import calino.malinov.ski.poc.data.caldav.CalDavConnectionManager
import calino.malinov.ski.poc.data.caldav.CalDavDiscovery
import calino.malinov.ski.poc.data.caldav.CalDavFetcher
import calino.malinov.ski.poc.data.caldav.CalDavWriter
import calino.malinov.ski.poc.data.caldav.CardDavWriter
import calino.malinov.ski.poc.data.caldav.CredentialStore
import calino.malinov.ski.poc.data.caldav.DavHttp
import calino.malinov.ski.poc.data.caldav.KeystoreCredentialStore
import calino.malinov.ski.poc.data.caldav.SharedPreferencesAccountPersistence
import calino.malinov.ski.poc.data.model.CalDavCalendar
import calino.malinov.ski.poc.data.model.CalDavForm
import calino.malinov.ski.poc.data.caldav.FileCalendarCache
import calino.malinov.ski.poc.data.repository.CalDavRepository
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.occursOn
import calino.malinov.ski.poc.data.model.placementDate
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.Contact
import calino.malinov.ski.poc.data.model.NewContact
import calino.malinov.ski.poc.data.model.toNewContact
import calino.malinov.ski.poc.data.model.derivedDisplayName
import calino.malinov.ski.poc.data.model.contactReminderEvent
import calino.malinov.ski.poc.data.model.NewEvent
import calino.malinov.ski.poc.data.model.NewJournal
import calino.malinov.ski.poc.data.model.NewTask
import calino.malinov.ski.poc.data.model.EditorDraft
import calino.malinov.ski.poc.data.model.blankEditorDraft
import calino.malinov.ski.poc.data.model.editorDraftFor
import calino.malinov.ski.poc.data.ai.AiEventCandidate
import calino.malinov.ski.poc.data.ai.AiVisionClient
import calino.malinov.ski.poc.data.ai.AiVisionSettingsStore
import calino.malinov.ski.poc.data.parser.PocQuickAddKind
import calino.malinov.ski.poc.data.search.CalinoSearchResult
import calino.malinov.ski.poc.data.repository.CalDavAccountStore
import calino.malinov.ski.poc.data.repository.CalDavClient
import calino.malinov.ski.poc.data.repository.CalinoRepository
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import calino.malinov.ski.poc.data.repository.FixtureRepository
import calino.malinov.ski.poc.data.repository.UndoableChange
import calino.malinov.ski.poc.data.repository.WriteResult
import calino.malinov.ski.poc.data.repository.PendingChange
import calino.malinov.ski.poc.data.model.RecurrenceEditScope
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoSpacing
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
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
import calino.malinov.ski.poc.design.CalinoTheme
import calino.malinov.ski.poc.design.CalinoThemes
import calino.malinov.ski.poc.util.CalinoThemeChoice
import calino.malinov.ski.poc.state.FixtureNow
import calino.malinov.ski.poc.state.CalinoFoldPosture
import calino.malinov.ski.poc.state.LocalFoldPosture
import calino.malinov.ski.poc.state.LocalHingeOpenness
import calino.malinov.ski.poc.state.hingeOpenness
import calino.malinov.ski.poc.state.foldPostureOf
import calino.malinov.ski.poc.state.LocalCalinoNow
import calino.malinov.ski.poc.state.LocalCalinoPreferences
import calino.malinov.ski.poc.state.SharedPreferencesPreferenceStore
import calino.malinov.ski.poc.state.rememberCalinoPreferences
import calino.malinov.ski.poc.state.rememberCalinoNow
import calino.malinov.ski.poc.state.FeatureAvailability
import calino.malinov.ski.poc.state.featureAvailabilityAfter
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.state.SplitPaneWidthDp
import calino.malinov.ski.poc.state.PocReturnTarget
import calino.malinov.ski.poc.ui.components.AddPill
import calino.malinov.ski.poc.ui.components.NavSidebar
import calino.malinov.ski.poc.ui.components.pockRouteLabel
import calino.malinov.ski.poc.ui.home.HomeScreen
import calino.malinov.ski.poc.ui.components.SwipeDownDismiss
import calino.malinov.ski.poc.ui.surfaces.DayModalSurface
import calino.malinov.ski.poc.ui.surfaces.EventDetail
import calino.malinov.ski.poc.ui.surfaces.TaskDetail
import calino.malinov.ski.poc.ui.surfaces.NotificationPreview
import calino.malinov.ski.poc.ui.surfaces.AgendaScreen
import calino.malinov.ski.poc.ui.surfaces.CalendarAccountsSurface
import calino.malinov.ski.poc.ui.surfaces.PockRoute
import calino.malinov.ski.poc.ui.surfaces.QuickAddKind
import calino.malinov.ski.poc.ui.surfaces.QuickAddSheet
import calino.malinov.ski.poc.ui.surfaces.QuickAddSheetState
import calino.malinov.ski.poc.ui.surfaces.toParserKind
import calino.malinov.ski.poc.ui.surfaces.JournalSurface
import calino.malinov.ski.poc.ui.surfaces.ContactsSurface
import calino.malinov.ski.poc.ui.surfaces.SettingsSurface
import calino.malinov.ski.poc.ui.surfaces.CalinoSearchSheet
import calino.malinov.ski.poc.ui.surfaces.Tasks
import calino.malinov.ski.poc.ui.surfaces.AiCandidateReview
import calino.malinov.ski.poc.ui.surfaces.AiProcessingOverlay
import calino.malinov.ski.poc.ui.surfaces.updateAiShortcut
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val DateLabel = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)

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
    PockRoute.Agenda -> 1
    PockRoute.Tasks -> 2
    PockRoute.Journal -> 3
    PockRoute.Contacts -> 4
    PockRoute.Settings -> 5
    PockRoute.Accounts -> 6
    // Detail and notification previews are pushed destinations. Keeping them
    // after the root destinations makes opening them enter from the right and
    // returning from them reverse the same motion, instead of treating them
    // as another instance of the calendar route.
    PockRoute.Detail -> 7
    PockRoute.TaskDetail -> 7
    PockRoute.Notifications -> 7
    PockRoute.QuickAdd -> 7
}

class MainActivity : ComponentActivity() {
    var incomingImage by mutableStateOf<Uri?>(null)
        private set
    var aiShortcutRequest by mutableIntStateOf(0)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeAiIntent(intent)
        enableEdgeToEdge()
        setContent { CalinoApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeAiIntent(intent)
    }

    fun consumeIncomingImage() { incomingImage = null }

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
}

/**
 * Holds the data layer across configuration changes.
 *
 * Two repositories exist and one is active at a time. With no account
 * connected the fixture repository serves the frozen May 2026 sample data, so
 * the app is never an empty shell; connecting an account switches to the
 * CalDAV-backed one. [activeRepository] is Compose state, so the swap
 * recomposes and the observer bridge re-subscribes on its own.
 */
class PocRepositoryViewModel(application: Application) : AndroidViewModel(application) {

    private val fixtureRepository = FixtureRepository()

    private val credentialStore: CredentialStore = KeystoreCredentialStore(application)

    val accountStore = CalDavAccountStore(SharedPreferencesAccountPersistence(application))

    /** Display preferences (clock, and whatever joins it), persisted. */
    val preferenceStore = SharedPreferencesPreferenceStore(application)

    /** Real discovery. This is the seam `FixtureCalDavClient` used to fill. */
    val calDavClient: CalDavClient = CalDavDiscovery(sharedHttp)

    private val calendarCache = FileCalendarCache(File(application.filesDir, "caldav-cache"))
    private val pendingChangeStore = calino.malinov.ski.poc.data.repository.FilePendingChangeStore(
        File(application.filesDir, "caldav-write-queue.json"),
    )

    private val calDavRepository = CalDavRepository(
        fetcher = CalDavFetcher(sharedHttp),
        scope = viewModelScope,
        cache = calendarCache,
        writer = CalDavWriter(sharedHttp, calendarCache),
        cardWriter = CardDavWriter(sharedHttp, calendarCache),
        pendingStore = pendingChangeStore,
    )

    private val connections = CalDavConnectionManager(
        accountStore = accountStore,
        credentialStore = credentialStore,
        repository = calDavRepository,
        discovery = CalDavDiscovery(sharedHttp),
        scope = viewModelScope,
    )

    private val repositoryState = mutableStateOf<CalinoRepository>(fixtureRepository)
    val activeRepository: CalinoRepository get() = repositoryState.value

    private val hasAccountsState = mutableStateOf(accountStore.accounts().isNotEmpty())

    /** Whether any CalDAV account is connected. Drives the calendar's anchor date. */
    val hasAccounts: Boolean get() = hasAccountsState.value

    init {
        // A persisted account restores and refetches without asking for the
        // password again; the credential store still holds it.
        connections.restore()
        updateActiveRepository()
        viewModelScope.launch {
            while (isActive) {
                calDavRepository.drainPendingWrites()
                delay(60_000)
            }
        }
    }

    fun onAccountConnected(form: CalDavForm, calendars: List<CalDavCalendar>) {
        val account = accountStore.addAccount(form, calendars)
        connections.onAccountConnected(account, form.password)
        updateActiveRepository()
    }

    fun onCalendarEnabled(accountId: String, calendarId: String, enabled: Boolean) {
        accountStore.setCalendarEnabled(accountId, calendarId, enabled)
        connections.onCalendarsToggled()
    }

    fun onAddressBookEnabled(accountId: String, addressBookId: String, enabled: Boolean) {
        accountStore.setAddressBookEnabled(accountId, addressBookId, enabled)
        connections.onCalendarsToggled()
    }

    fun onAccountRemoved(accountId: String) {
        accountStore.removeAccount(accountId)
        connections.onAccountRemoved(accountId)
        updateActiveRepository()
    }

    fun refresh() = calDavRepository.refresh()

    fun drainPendingWrites() = calDavRepository.drainPendingWrites()

    fun pendingChanges(): List<PendingChange> = calDavRepository.pendingChanges()

    fun retryPendingChange(id: String): Boolean = calDavRepository.retryPendingChange(id)

    fun discardPendingChange(id: String): Boolean = calDavRepository.discardPendingChange(id)

    fun setEventWindowMonths(months: Long) = calDavRepository.setWindowMonths(months)

    private fun updateActiveRepository() {
        val connected = accountStore.accounts().isNotEmpty()
        hasAccountsState.value = connected
        repositoryState.value = if (connected) calDavRepository else fixtureRepository
    }

    private companion object {
        /** One OkHttp instance so discovery and fetching share the pool. */
        val sharedHttp = DavHttp()
    }
}

/** The launch shell for the native app. No WebView or Capacitor is involved. */
@Composable
fun CalinoApp() {
    val pocViewModel = viewModel<PocRepositoryViewModel>()
    // The clock runs for real once an account is connected; with only the
    // fixture data it stays frozen so the sample stays deterministic.
    val now by rememberCalinoNow(live = pocViewModel.hasAccounts)
    // Read before the theme, not inside it: the palette is a function of a
    // preference, so the preference has to exist first.
    val preferences = rememberCalinoPreferences(pocViewModel.preferenceStore)
    LaunchedEffect(preferences.eventSyncRange) {
        pocViewModel.setEventWindowMonths(preferences.eventSyncRange.months)
    }
    val dark = when (preferences.themeChoice) {
        CalinoThemeChoice.System -> isSystemInDarkTheme()
        CalinoThemeChoice.Light -> false
        CalinoThemeChoice.Dark -> true
    }
    CalinoTheme(if (dark) CalinoThemes.PaperDark else CalinoThemes.PaperLight) {
        SystemBarAppearance(light = !dark)
        CompositionLocalProvider(
            LocalCalinoNow provides now,
            LocalCalinoPreferences provides preferences,
            LocalFoldPosture provides rememberFoldPosture(),
            LocalHingeOpenness provides rememberHingeOpenness(),
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
private fun SystemBarAppearance(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? android.app.Activity)?.window ?: return
    SideEffect {
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
    val pendingChanges = remember(snapshot.revision) { pocViewModel.pendingChanges() }
    val calDavAccounts = rememberCalDavAccounts(accountStore)
    val saveableStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf<PockRoute>(PockRoute.Day) }
    var selectedContactId by rememberSaveable { mutableStateOf<String?>(null) }
    // The fixture data lives around May 2026, so that is where the sample
    // app opens. Real calendars are anchored on the actual date instead --
    // landing a connected account on the fixture month shows an empty
    // calendar and reads as a broken integration.
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(if (pocViewModel.hasAccounts) LocalDate.now() else FixtureNow.today)
    }
    // Connecting the first account mid-session moves the calendar to today
    // for the same reason.
    LaunchedEffect(pocViewModel.hasAccounts) {
        if (pocViewModel.hasAccounts && selectedDate == FixtureNow.today) {
            selectedDate = LocalDate.now()
        }
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
    var quickAddMorphFromAddPill by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchOriginRoute by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf<PockRoute>(PockRoute.Day) }
    var notificationOrigin by rememberSaveable(stateSaver = ReturnTargetSaver) { mutableStateOf(PocReturnTarget.Calendar) }
    // Calendars is reachable from the sidebar and from Settings, so back
    // has to return to whichever one opened it.
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
    // The large landscape month root reserves a right-side lane for the pill,
    // even while the day pane itself is collapsed. That keeps the affordance
    // anchored when the pane opens or closes.
    var splitMonthLayoutVisible by remember { mutableStateOf(false) }
    var journalEntryRequest by rememberSaveable { mutableIntStateOf(0) }
    var contactRequest by rememberSaveable { mutableIntStateOf(0) }
    var pendingUndo by remember { mutableStateOf<UndoableChange?>(null) }
    var displayedUndo by remember { mutableStateOf<UndoableChange?>(null) }
    var undoNonce by remember { mutableIntStateOf(0) }
    var writeError by remember { mutableStateOf<String?>(null) }
    val writeScope = androidx.compose.runtime.rememberCoroutineScope()
    val activity = LocalActivity.current as? MainActivity ?: return
    val aiSettingsStore = remember { AiVisionSettingsStore(activity) }
    val aiClient = remember { AiVisionClient() }
    LaunchedEffect(Unit) { updateAiShortcut(activity, aiSettingsStore.load().hasApiKey) }
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

    LaunchedEffect(pendingUndo) {
        pendingUndo?.let { displayedUndo = it }
    }

    val selectedEvent = snapshot.events.firstOrNull { it.id == selectedEventId }
    val selectedTask = snapshot.tasks.firstOrNull { it.id == selectedTaskId }
    val selectedContact = snapshot.contacts.firstOrNull { it.id == selectedContactId }
    val calendarDayModalVisible = showDayModal && (
        route == PockRoute.Day ||
            (route == PockRoute.QuickAdd && quickAddOrigin == PocReturnTarget.DayModal)
        )

    fun openQuickAdd(kind: QuickAddKind, origin: PocReturnTarget, morphFromAddPill: Boolean = false) {
        editEventId = null
        quickAddSeed = ""
        quickAddMorphFromAddPill = morphFromAddPill
        quickAddKind = kind
        quickAddOrigin = origin
        route = PockRoute.QuickAdd
    }

    /** The same editor, seeded from a record that already exists. */
    fun openEditor(event: CalEvent, origin: PocReturnTarget) {
        editEventId = event.id
        quickAddSeed = ""
        // The detail card's edit action is the source pill for the editor,
        // just like the root add pill is when creating a new event.
        quickAddMorphFromAddPill = true
        quickAddKind = QuickAddKind.Event
        quickAddOrigin = origin
        route = PockRoute.QuickAdd
    }

    fun restoreDetailOrigin() {
        when (detailOrigin) {
            PocReturnTarget.DayModal -> {
                route = PockRoute.Day
                showDayModal = true
            }
            PocReturnTarget.Agenda -> route = PockRoute.Agenda
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
            PocReturnTarget.Tasks -> PockRoute.Tasks
            PocReturnTarget.Agenda -> PockRoute.Agenda
            PocReturnTarget.Search -> searchOriginRoute.also { searchVisible = true }
            else -> PockRoute.Day
        }
    }

    fun dismissQuickAdd() {
        editEventId = null
        aiDraft = null
        aiQueue = emptyList()
        quickAddMorphFromAddPill = false
        when (quickAddOrigin) {
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

    fun showUndo(change: UndoableChange) {
        pendingUndo = change
        undoNonce += 1
    }

    fun <T> launchWrite(operation: suspend () -> WriteResult<T>, onApplied: (T) -> Unit = {}) {
        writeError = null
        writeScope.launch {
            try {
                when (val result = operation()) {
                    is WriteResult.Applied -> onApplied(result.record)
                    is WriteResult.Queued -> onApplied(result.record)
                    is WriteResult.Rejected -> writeError = result.reason
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                writeError = error.message ?: "That change could not be saved."
            }
        }
    }

    BackHandler(enabled = sidebarVisible || (route != PockRoute.Detail && route != PockRoute.TaskDetail &&
        !journalEditorVisible && (journalReviewVisible || route != PockRoute.Day || showDayModal))) {
        when {
            sidebarVisible -> sidebarVisible = false
            journalReviewVisible -> journalReviewVisible = false
            route == PockRoute.QuickAdd -> dismissQuickAdd()
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
                route = if (accountsOrigin == PocReturnTarget.Settings) PockRoute.Settings else PockRoute.Day
            }
            showDayModal -> showDayModal = false
            else -> route = PockRoute.Day
        }
    }

    val aiContextBlur by animateDpAsState(
        targetValue = if (aiBusy || aiCandidates != null) 10.dp else 0.dp,
        animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
        label = "AI context blur",
    )
    // Keep the blur on the calendar/content sibling only. AI surfaces are
    // drawn after this block and must stay crisp above the blurred context.
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize()
            .background(CalinoColors.Canvas)
            .blur(aiContextBlur)
            .padding(WindowInsets.safeDrawing.asPaddingValues()),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        val rootRoute = when (visibleRoute) {
            PockRoute.Day -> PockRoute.Day
            PockRoute.Agenda -> PockRoute.Agenda
            PockRoute.Tasks -> PockRoute.Tasks
            PockRoute.Journal -> PockRoute.Journal
            PockRoute.Contacts -> PockRoute.Contacts
            PockRoute.Settings -> PockRoute.Settings
            PockRoute.Accounts -> PockRoute.Accounts
            PockRoute.Detail -> when (detailOrigin) {
                PocReturnTarget.Agenda -> PockRoute.Agenda
                PocReturnTarget.Tasks -> PockRoute.Tasks
                PocReturnTarget.Journal -> PockRoute.Journal
                PocReturnTarget.Contacts -> PockRoute.Contacts
                PocReturnTarget.Search -> searchOriginRoute
                else -> PockRoute.Day
            }
            PockRoute.TaskDetail -> when (taskDetailOrigin) {
                PocReturnTarget.Agenda -> PockRoute.Agenda
                PocReturnTarget.Tasks -> PockRoute.Tasks
                PocReturnTarget.Search -> searchOriginRoute
                else -> PockRoute.Day
            }
            PockRoute.QuickAdd -> when (quickAddOrigin) {
                PocReturnTarget.Agenda -> PockRoute.Agenda
                PocReturnTarget.Tasks -> PockRoute.Tasks
                PocReturnTarget.Journal -> PockRoute.Journal
                PocReturnTarget.Contacts -> PockRoute.Contacts
                PocReturnTarget.Settings -> PockRoute.Settings
                PocReturnTarget.Detail -> PockRoute.Day
                PocReturnTarget.Search -> searchOriginRoute
                else -> PockRoute.Day
            }
            PockRoute.Notifications -> PockRoute.Notifications
        }
        // The add pill is frosted glass over whatever surface is behind it, so
        // that surface is recorded here and the pill draws a blurred copy of
        // its own patch of it. The pill is a sibling of this stack, never a
        // child, so nothing recurses.
        val surfaceLayer = rememberGraphicsLayer()
        var surfaceOrigin by remember { mutableStateOf(Offset.Zero) }
        AnimatedContent(
            targetState = rootRoute,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { surfaceOrigin = it.positionInRoot() }
                .drawWithContent {
                    surfaceLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(surfaceLayer)
                },
            transitionSpec = {
                val direction = if (targetState.rootOrder() >= initialState.rootOrder()) 1 else -1
                (slideInHorizontally(tween(260)) { direction * it / 4 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(210)) { -direction * it / 4 } + fadeOut(tween(140)))
            },
            label = "root destination transition",
        ) { currentRoute ->
            saveableStateHolder.SaveableStateProvider("root:${currentRoute.saveableKey()}") {
                when (currentRoute) {
                    PockRoute.Day -> HomeScreen(
                        repository = repository,
                        journals = snapshot.journals,
                        tasks = snapshot.tasks,
                        modifier = Modifier.fillMaxSize(),
                        interactionEnabled = route == PockRoute.Day && !showDayModal && !journalReviewVisible,
                        initialDate = selectedDate,
                        onOpenMenu = { sidebarVisible = true },
                        onDateChanged = { selectedDate = it },
                        onDayClick = { date -> selectedDate = date; showDayModal = true; route = PockRoute.Day },
                        onEventClick = { event ->
                            selectedEventId = event.id
                            selectedEventOccurrenceDay = selectedDate.toEpochDay()
                            detailOrigin = PocReturnTarget.Calendar
                            showDayModal = false
                            route = PockRoute.Detail
                        },
                        onTaskDone = { task, done ->
                            launchWrite({ repository.setTaskDone(task.id, done) }) { showUndo(it) }
                        },
                        onTaskRescheduleTo = { task, date ->
                            launchWrite({ repository.rescheduleTask(task.id, date) }) { showUndo(it) }
                        },
                        onTaskClick = { task ->
                            selectedTaskId = task.id
                            taskDetailOrigin = PocReturnTarget.Calendar
                            route = PockRoute.TaskDetail
                        },
                        onSplitPaneChanged = { splitMonthLayoutVisible = it },
                    )
                    PockRoute.Agenda -> AgendaScreen(
                        // The snapshot rather than a direct repository
                        // read: a plain read inside composition does not
                        // subscribe, so an async refresh would not repaint.
                        events = snapshot.events,
                        tasks = snapshot.tasks,
                        modifier = Modifier.fillMaxSize(),
                        initialDate = selectedDate,
                        onOpenMenu = { sidebarVisible = true },
                        onDateChanged = { selectedDate = it },
                        onEventClick = { day, event ->
                            selectedEventId = event.id
                            selectedEventOccurrenceDay = day.toEpochDay()
                            detailOrigin = PocReturnTarget.Agenda
                            route = PockRoute.Detail
                        },
                        onTaskClick = { task ->
                            selectedTaskId = task.id
                            taskDetailOrigin = PocReturnTarget.Agenda
                            route = PockRoute.TaskDetail
                        },
                        onTaskDone = { task, done ->
                            launchWrite({ repository.setTaskDone(task.id, done) }) { showUndo(it) }
                        },
                        onAddOn = { date ->
                            selectedDate = date
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
                            selectedTaskId = task.id
                            taskDetailOrigin = PocReturnTarget.Tasks
                            route = PockRoute.TaskDetail
                        },
                        onUndoComplete = { task -> launchWrite({ repository.setTaskDone(task.id, false) }) },
                        onOpenMenu = { sidebarVisible = true },
                    )
                    PockRoute.Journal -> JournalSurface(
                        entries = snapshot.journals,
                        newEntryDate = selectedDate,
                        onCreate = { entry ->
                            launchWrite(operation = { repository.addJournal(NewJournal(entry.date, entry.title, entry.body)) })
                        },
                        onUpdate = { entry ->
                            launchWrite(operation = { repository.updateJournal(entry.id, NewJournal(entry.date, entry.title, entry.body)) })
                        },
                        onDelete = { entry -> launchWrite(operation = { repository.deleteJournal(entry.id) }) },
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
                            launchWrite(operation = { repository.deleteContact(contact.id) }) {
                                if (selectedContactId == contact.id) selectedContactId = null
                            }
                        },
                        onAddBirthday = { contact, date, anniversary ->
                            repository.addLocalEvent(
                                contactReminderEvent(
                                    contact = contact,
                                    date = date,
                                    calendarId = snapshot.calendars.firstOrNull()?.id ?: "personal",
                                    anniversary = anniversary,
                                ),
                            )
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
                    PockRoute.Notifications -> NotificationPreview(
                        data = calino.malinov.ski.poc.ui.surfaces.NotificationPreviewData(
                            "Design review", "10:00 AM · Studio · with 2 others",
                        ),
                        onAction = { notificationOrigin = PocReturnTarget.Settings; route = PockRoute.Settings },
                    )
                    PockRoute.QuickAdd -> Unit
                }
            }
        }

        when (route) {
            PockRoute.Detail -> selectedEvent?.let { event ->
                EventDetail(
                    event = event,
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
                        // Leave the detail route as soon as its exit animation
                        // completes; the write itself may be queued and must
                        // not cause the detail surface to remount underneath.
                        selectedEventId = null
                        selectedEventOccurrenceDay = null
                        restoreDetailOrigin()
                        launchWrite({ repository.deleteEvent(target.id, scope) })
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
                    onBack = {
                        selectedTaskId = null
                        restoreTaskDetailOrigin()
                    },
                    onSave = { input, done ->
                        launchWrite({ repository.updateTask(task.id, input, done) }) {
                            selectedTaskId = null
                            restoreTaskDetailOrigin()
                        }
                    },
                )
            }
            else -> Unit
        }

        if (calendarDayModalVisible) {
            DayModalSurface(
                date = selectedDate,
                events = snapshot.events,
                journals = snapshot.journals,
                onDateChanged = { selectedDate = it },
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
                QuickAddSheet(
                    state = QuickAddSheetState(
                        visible = true,
                        kind = quickAddKind,
                        date = selectedDate,
                        morphFromAddPill = quickAddMorphFromAddPill,
                        draft = editing
                            ?.let(::editorDraftFor)
                            ?: aiDraft
                            ?: run {
                                val defaults = LocalCalinoPreferences.current
                                blankEditorDraft(
                                    kind = quickAddKind.toParserKind(),
                                    date = selectedDate,
                                    title = quickAddSeed,
                                    defaultDurationMinutes = defaults.defaultDuration.minutes,
                                    defaultReminderMinutes = defaults.defaultReminder.minutesBefore,
                                )
                            },
                    ),
                    calendars = snapshot.calendars,
                    categories = snapshot.categories,
                    relatedCandidates = remember(snapshot.tasks) {
                        snapshot.tasks.filterNot { it.done }.map { it.id to it.title }
                    },
                    onPhoto = if (quickAddKind == QuickAddKind.Event && aiSettingsStore.load().hasApiKey) ::requestPhotoImport else null,
                    onDismiss = ::dismissQuickAdd,
                    // The editor owns every field now, so the host only
                    // decides between creating and updating a record.
                    onSave = { draft ->
                        launchWrite({ saveEditorDraft(repository, draft) }) {
                            if (draft.kind == PocQuickAddKind.Journal) {
                                journalReviewVisible = false
                                quickAddOrigin = PocReturnTarget.Journal
                            }
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

        androidx.compose.animation.AnimatedVisibility(
            visible = pendingUndo != null,
            enter = slideInVertically(tween(200), initialOffsetY = { it / 2 }) + fadeIn(tween(170)),
            exit = slideOutVertically(tween(170), targetOffsetY = { it / 2 }) + fadeOut(tween(130)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = CalinoSpacing.PillClearance),
        ) {
            displayedUndo?.let { change ->
                PocUndoBanner(
                    change = change,
                    nonce = undoNonce,
                    onUndo = {
                        launchWrite({ repository.undo(change) }) { pendingUndo = null }
                    },
                    onExpired = { if (pendingUndo == change) pendingUndo = null },
                    modifier = Modifier,
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = writeError != null,
            enter = slideInVertically(tween(200), initialOffsetY = { it / 2 }) + fadeIn(tween(170)),
            exit = slideOutVertically(tween(170), targetOffsetY = { it / 2 }) + fadeOut(tween(130)),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = CalinoSpacing.PillClearance + 62.dp),
        ) {
            writeError?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth(.92f),
                    shape = RoundedCornerShape(16.dp),
                    color = CalinoColors.Ink,
                    contentColor = CalinoColors.OnInk,
                ) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(message, Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { writeError = null }) {
                            Text("Dismiss", color = CalinoColors.AccentSoft)
                        }
                    }
                }
            }
        }

        // The add affordance floats over the surfaces instead of taking
        // layout space; every scrollable root reserves PillClearance for it.
        val pillVisible = when (rootRoute) {
            PockRoute.Day -> route == PockRoute.Day && !showDayModal && !journalReviewVisible && editEventId == null
            PockRoute.Agenda -> route == PockRoute.Agenda
            PockRoute.Tasks -> route == PockRoute.Tasks
            PockRoute.Journal -> route == PockRoute.Journal && !journalEditorVisible
            PockRoute.Contacts -> route == PockRoute.Contacts && selectedContactId == null
            else -> false
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = pillVisible && !sidebarVisible && !searchVisible,
            enter = slideInVertically(tween(240), initialOffsetY = { it }) + fadeIn(tween(180)),
            exit = slideOutVertically(tween(200), targetOffsetY = { it }) + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 20.dp),
            label = "add pill visibility",
        ) {
            // In the large landscape split the pill rides in the right-side
            // lane. Keep that lane even when the day pane is collapsed, so
            // toggling the pane does not recenter the pill.
            val pillLaneWidth by animateDpAsState(
                targetValue = if (splitMonthLayoutVisible) (SplitPaneWidthDp + 44).dp else 0.dp,
                animationSpec = tween(CalinoMotion.SurfaceFadeMillis),
                label = "add pill lane",
            )
            // The pill also carries the three main views: a horizontal
            // drag steps through them in the same order the sidebar lists.
            val pillRoutes = listOfNotNull(
                PockRoute.Day,
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
                label = when (rootRoute) {
                    PockRoute.Tasks -> "New task"
                    PockRoute.Journal -> "New entry"
                    PockRoute.Contacts -> "New contact"
                    else -> "Add on ${selectedDate.format(DateLabel)}"
                },
                onClick = {
                    when (rootRoute) {
                        PockRoute.Tasks -> openQuickAdd(QuickAddKind.Task, PocReturnTarget.Tasks, morphFromAddPill = true)
                        PockRoute.Journal -> journalEntryRequest += 1
                        PockRoute.Contacts -> contactRequest += 1
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
private fun PocUndoBanner(
    change: UndoableChange,
    nonce: Int,
    onUndo: () -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(nonce) {
        delay(5_000)
        onExpired()
    }
    Surface(
        modifier = modifier.fillMaxWidth(.92f),
        shape = RoundedCornerShape(16.dp),
        color = CalinoColors.Ink,
        contentColor = CalinoColors.OnInk,
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(change.description, Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = onUndo) { Text("Undo", color = CalinoColors.AccentSoft) }
        }
    }
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
        touched = calino.malinov.ski.poc.data.model.EditorField.entries.toSet(),
    )
}
