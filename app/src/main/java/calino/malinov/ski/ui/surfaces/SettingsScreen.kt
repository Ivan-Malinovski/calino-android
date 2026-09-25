package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import calino.malinov.ski.platform.assistant.AssistantAccess
import calino.malinov.ski.platform.search.PhoneSearchAccess
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.repository.FixtureCategories
import androidx.compose.foundation.isSystemInDarkTheme
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoPalette
import calino.malinov.ski.design.CalinoThemes
import calino.malinov.ski.util.CalinoThemeChoice
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.ui.components.MenuButton
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.CalinoSearchField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.sync.BackgroundSyncCadence
import calino.malinov.ski.data.sync.BackgroundSyncStatus
import androidx.compose.ui.platform.testTag
import calino.malinov.ski.notify.LocalNotificationPermission
import calino.malinov.ski.notify.systemSettingsIntent
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.LocalFoldPosture
import calino.malinov.ski.state.calinoLayoutSpec
import calino.malinov.ski.ui.components.CompactSegmentedControl
import calino.malinov.ski.util.CalinoDefaultDuration
import calino.malinov.ski.util.CalinoDefaultReminder
import calino.malinov.ski.util.CalinoDefaultView
import calino.malinov.ski.util.CalinoEventDensity
import calino.malinov.ski.util.CalinoEventSyncRange
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.CalinoWeekStart
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class SettingsSection(val title: String, val shortTitle: String) {
    Display("Display", "Display"),
    EventsTasks("Events & tasks", "Events & tasks"),
    Reminders("Reminders", "Reminders"),
    CalendarsSync("Calendars & sync", "Calendars & sync"),
    DataAccess("Data & access", "Data & access"),
}

private val SettingsNavLaneHeight = 44.dp

/** The landscape rail: wide enough for the longest section name and no wider. */
private val SettingsRailWidth = 232.dp
private val SettingsNavPillHeight = 28.dp
private val SettingsContentMaxWidth = 720.dp
private val SettingsRowHorizontalPadding = 18.dp
private val SettingsRowVerticalPadding = 14.dp
private val SettingsGroupSpacing = 20.dp
private data class SettingsSearchTarget(val title: String, val group: String, val request: Int)
private val LocalSettingsSearchTarget = compositionLocalOf<SettingsSearchTarget?> { null }

private data class SettingsSearchEntry(
    val title: String,
    val section: SettingsSection,
    val group: String,
    val description: String = "",
)

private val SettingsSearchEntries = listOf(
    SettingsSearchEntry("Time format", SettingsSection.Display, "Regional defaults", "clock 12 24 hour"),
    SettingsSearchEntry("Journal", SettingsSection.Display, "Surfaces", "navigation"),
    SettingsSearchEntry("Contacts", SettingsSection.Display, "Surfaces", "navigation"),
    SettingsSearchEntry("Theme", SettingsSection.Display, "Theme", "appearance light dark system"),
    SettingsSearchEntry("Default view", SettingsSection.Display, "Display", "calendar start"),
    SettingsSearchEntry("First day of week", SettingsSection.Display, "Display", "calendar grid"),
    SettingsSearchEntry("Show week numbers", SettingsSection.Display, "Display"),
    SettingsSearchEntry("Show pull bar", SettingsSection.Display, "Display", "zoom"),
    SettingsSearchEntry("Menu pill", SettingsSection.Display, "Display", "navigation"),
    SettingsSearchEntry("Event density", SettingsSection.Display, "Display", "month"),
    SettingsSearchEntry("Hide completed tasks", SettingsSection.EventsTasks, "Tasks in calendar"),
    SettingsSearchEntry("Default duration", SettingsSection.EventsTasks, "New event defaults"),
    SettingsSearchEntry("Show end times", SettingsSection.EventsTasks, "Display"),
    SettingsSearchEntry("Show locations", SettingsSection.EventsTasks, "Display"),
    SettingsSearchEntry("Categories", SettingsSection.EventsTasks, "Labels used by your records", "labels"),
    SettingsSearchEntry("Default reminder", SettingsSection.Reminders, "New event reminder"),
    SettingsSearchEntry("Event reminders", SettingsSection.Reminders, "Events"),
    SettingsSearchEntry("Tasks due", SettingsSection.Reminders, "Tasks"),
    SettingsSearchEntry("Let another app remind me", SettingsSection.Reminders, "System calendar"),
    SettingsSearchEntry("Reminders and channels", SettingsSection.Reminders, "Delivery", "notifications"),
    SettingsSearchEntry("Android notification settings", SettingsSection.Reminders, "Delivery", "sounds"),
    SettingsSearchEntry("Calendars and accounts", SettingsSection.CalendarsSync, "Connected accounts", "CalDAV address books"),
    SettingsSearchEntry("Subscribed calendars", SettingsSection.CalendarsSync, "Subscribed calendars", "ics"),
    SettingsSearchEntry("Background sync", SettingsSection.CalendarsSync, "Sync settings", "refresh frequency"),
    SettingsSearchEntry("Event sync range", SettingsSection.CalendarsSync, "Sync settings", "offline search"),
    SettingsSearchEntry("Import calendar", SettingsSection.DataAccess, "Import & export", "ics file"),
    SettingsSearchEntry("Export calendar", SettingsSection.DataAccess, "Import & export", "ics file"),
    SettingsSearchEntry("Show in phone search", SettingsSection.DataAccess, "Search & assistants", "Samsung Finder"),
    SettingsSearchEntry("Let assistants use Calino", SettingsSection.DataAccess, "Search & assistants", "Gemini AppFunctions"),
    SettingsSearchEntry("AI Photo Import", SettingsSection.DataAccess, "AI Photo Import", "provider API key model"),
)

private enum class SettingRowControlLayout {
    Inline,
    AdaptiveTrailing,
    AdaptiveSegmented,
}

@Composable
fun SettingsSurface(
    onOpenNotifications: () -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
    calDavAccounts: List<CalDavAccount> = emptyList(),
    // A non-null id asks the calendars surface to scroll that account into
    // view; `startAdding` asks it to open the add sheet on arrival.
    onOpenAccounts: (startAdding: Boolean, focusAccountId: String?) -> Unit = { _, _ -> },
    openAiVisionRequest: Int = 0,
    onImportCalendar: () -> Unit = {},
    onExportCalendar: () -> Unit = {},
    webcalSubscriptions: List<calino.malinov.ski.data.model.WebcalSubscription> = emptyList(),
    onSubscribeWebcal: suspend (calino.malinov.ski.data.model.WebcalForm) -> Unit = {},
    onRemoveWebcal: (String) -> Unit = {},
    onSyncWebcal: (String) -> Unit = {},
    onToggleWebcalNotify: (String, Boolean) -> Unit = { _, _ -> },
    backgroundSyncCadence: BackgroundSyncCadence = BackgroundSyncCadence.Hourly,
    backgroundSyncStatus: BackgroundSyncStatus = BackgroundSyncStatus(),
    onBackgroundSyncCadenceChanged: (BackgroundSyncCadence) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var sectionName by rememberSaveable { mutableStateOf(SettingsSection.Display.name) }
    var subscribeOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchTarget by remember { mutableStateOf<SettingsSearchTarget?>(null) }
    var searchRequest by remember { mutableIntStateOf(0) }
    val section = remember(sectionName) {
        runCatching { SettingsSection.valueOf(sectionName) }.getOrDefault(SettingsSection.Display)
    }
    val currentSection by rememberUpdatedState(section)
    val sectionRailState = rememberLazyListState()
    val sectionPagerState = rememberPagerState(initialPage = section.ordinal) { SettingsSection.entries.size }

    LaunchedEffect(openAiVisionRequest) {
        if (openAiVisionRequest > 0) {
            sectionName = SettingsSection.DataAccess.name
            searchRequest += 1
            searchTarget = SettingsSearchTarget("AI Photo Import", "AI Photo Import", searchRequest)
        }
    }

    LaunchedEffect(section) {
        sectionRailState.animateScrollToItem(SettingsSection.entries.indexOf(section))
        // A chip tap is an explicit destination. Let animateScrollToPage use
        // the pager's scroll mutex to replace an in-progress finger settle;
        // skipping while scrolling lets the old settle overwrite the tap.
        if (sectionPagerState.currentPage != section.ordinal ||
            kotlin.math.abs(sectionPagerState.currentPageOffsetFraction) > .001f
        ) {
            sectionPagerState.animateScrollToPage(section.ordinal)
        }
    }

    // The section rail remains an accessible shortcut, while the pager is the
    // primary touch path for moving between settings categories. Commit the
    // section only after the page settles so the selected chip and page body
    // cannot disagree during a swipe.
    LaunchedEffect(sectionPagerState) {
        snapshotFlow { sectionPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val next = SettingsSection.entries[page.coerceIn(0, SettingsSection.entries.lastIndex)]
                if (next != currentSection) sectionName = next.name
            }
    }

    // Same rule the calendar uses for its split: a window wide enough for two
    // columns, and wider than it is tall. Landscape alone is not enough -- a
    // phone turned sideways is still too narrow to carry a rail beside the
    // settings card.
    BoxWithConstraints(Modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        val layoutSpec = calinoLayoutSpec(
            maxWidth.value.toInt(),
            maxHeight.value.toInt(),
            LocalFoldPosture.current,
        )
        val sideRail = layoutSpec.splitPanes

        // The burger sits beside the title, as on Journal and Contacts, so the
        // header costs one line rather than a third of a phone screen.
        val header: @Composable (Modifier) -> Unit = { headerModifier ->
            Row(headerModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                onOpenMenu?.let {
                    MenuButton(onClick = it, modifier = Modifier.padding(end = 6.dp))
                }
                Text(
                    "Settings",
                    modifier = Modifier.weight(1f),
                    style = if (sideRail) CalinoTypography.headlineMedium else CalinoTypography.displayLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) {
                            searchQuery = ""
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (searchOpen) CalinoIcons.X else CalinoIcons.Search,
                        contentDescription = if (searchOpen) "Close settings search" else "Search settings",
                        tint = CalinoColors.Ink2,
                    )
                }
            }
        }

        // One pager in both layouts: the rail and the swipe stay two ways of
        // driving the same selection rather than two code paths that can
        // disagree.
        val pager: @Composable (Modifier) -> Unit = { pagerModifier ->
            HorizontalPager(
                state = sectionPagerState,
                modifier = pagerModifier,
                beyondViewportPageCount = 1,
                key = { page -> SettingsSection.entries[page].name },
            ) { page ->
                CompositionLocalProvider(
                    LocalSettingsSearchTarget provides searchTarget.takeIf {
                        sectionPagerState.settledPage == page && sectionPagerState.currentPage == page
                    },
                ) {
                    SettingsSectionContent(
                        SettingsSection.entries[page],
                        onOpenNotifications,
                        calDavAccounts,
                        onOpenAccounts,
                        onImportCalendar,
                        onExportCalendar,
                        webcalSubscriptions,
                        onSubscribeWebcal,
                        onRemoveWebcal,
                        onSyncWebcal,
                        onToggleWebcalNotify,
                        backgroundSyncCadence,
                        backgroundSyncStatus,
                        onBackgroundSyncCadenceChanged,
                        onOpenSubscribe = { subscribeOpen = true },
                    )
                }
            }
        }
        val searchField: @Composable () -> Unit = {
            CalinoSearchField(
                query = searchQuery,
                onQueryChanged = { searchQuery = it },
                placeholder = "Search settings…",
                contentDescription = "Search settings field",
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = if (sideRail) 12.dp else 20.dp, vertical = 4.dp),
                inputModifier = Modifier.testTag("Search settings"),
            )
        }
        val results: @Composable () -> Unit = {
            SettingsSearchResults(searchQuery) { entry ->
                searchRequest += 1
                searchTarget = SettingsSearchTarget(entry.title, entry.group, searchRequest)
                sectionName = entry.section.name
                searchOpen = false
                searchQuery = ""
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }

        if (sideRail) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(minOf(SettingsRailWidth, layoutSpec.startPane.widthDp.dp))
                        .fillMaxHeight()
                        .background(CalinoColors.Side),
                ) {
                    header(Modifier.padding(horizontal = 12.dp, vertical = 14.dp))
                    AnimatedVisibility(searchOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        searchField()
                    }
                    LazyColumn(
                        state = sectionRailState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        items(SettingsSection.entries, key = { it.name }) { entry ->
                            SettingsNavChip(
                                section = entry,
                                selected = entry.ordinal == sectionPagerState.currentPage,
                                modifier = Modifier.fillMaxWidth(),
                                useFullTitle = true,
                            ) { sectionName = entry.name }
                        }
                    }
                }
                if (layoutSpec.hingeBandDp > 0f) {
                    Spacer(Modifier.fillMaxHeight().width(layoutSpec.hingeBandDp.dp).background(CalinoColors.Canvas))
                } else {
                    Box(Modifier.fillMaxHeight().width(1.dp).background(CalinoColors.Line))
                }
                if (!searchOpen || searchQuery.isBlank()) pager(Modifier.weight(1f).fillMaxHeight())
                else Box(Modifier.weight(1f).fillMaxHeight()) { results() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header(Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
                AnimatedVisibility(searchOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    searchField()
                }

                val endFadeAlpha by animateFloatAsState(
                    targetValue = if (sectionRailState.canScrollForward) 1f else 0f,
                    animationSpec = tween(180),
                    label = "settings section rail affordance",
                )
                // Hoisted: a draw scope cannot read the palette's composition local.
                val canvas = CalinoColors.Canvas
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Draw the affordance over the rail without adding a touch
                        // target that could steal a horizontal drag from the chips.
                        .drawWithCache {
                            val fadeWidth = 34.dp.toPx()
                            val fadeBrush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, canvas),
                                startX = size.width - fadeWidth,
                                endX = size.width,
                            )
                            onDrawWithContent {
                                drawContent()
                                if (endFadeAlpha > 0f) {
                                    drawRect(
                                        brush = fadeBrush,
                                        topLeft = Offset(size.width - fadeWidth, 0f),
                                        size = Size(fadeWidth, size.height),
                                        alpha = endFadeAlpha,
                                    )
                                }
                            }
                        },
                ) {
                    LazyRow(
                        state = sectionRailState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("Settings section rail"),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(SettingsSection.entries, key = { it.name }) { entry ->
                            SettingsNavChip(entry, selected = entry.ordinal == sectionPagerState.currentPage) { sectionName = entry.name }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line))
                if (!searchOpen || searchQuery.isBlank()) pager(Modifier.weight(1f).fillMaxWidth())
                else Box(Modifier.weight(1f).fillMaxWidth()) { results() }
            }
        }
        // Only compose the sheet while it is open. BottomDetailCard's host is a
        // full-window overlay; leaving it mounted with visible=false still
        // intercepts every tap on Settings.
        if (subscribeOpen) {
            WebcalSubscribeSheet(
                onDismiss = { subscribeOpen = false },
                onSubscribe = onSubscribeWebcal,
            )
        }
    }
}

@Composable
private fun SettingsNavChip(
    section: SettingsSection,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** The side rail has room for the real section name; the top rail does not. */
    useFullTitle: Boolean = false,
    onClick: () -> Unit,
) {
    val background = if (selected) CalinoColors.AccentSoft else CalinoColors.Panel
    val foreground = if (selected) CalinoColors.Accent else CalinoColors.Ink2
    val outline = if (selected) CalinoColors.Accent.copy(.16f) else CalinoColors.Line
    Box(
        modifier
            // Keep the tab's touch target comfortable while the pill itself
            // stays compact in the horizontal rail.
            .height(SettingsNavLaneHeight)
            .widthIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${section.title} settings"
                role = Role.Tab
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(SettingsNavPillHeight)
                .then(if (useFullTitle) Modifier.fillMaxWidth() else Modifier)
                .clip(RoundedCornerShape(999.dp))
                .background(background)
                .border(1.dp, outline, RoundedCornerShape(999.dp))
                .padding(horizontal = 15.dp),
            contentAlignment = if (useFullTitle) Alignment.CenterStart else Alignment.Center,
        ) {
            Text(
                if (useFullTitle) section.title else section.shortTitle,
                color = foreground,
                style = CalinoTypography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingsSearchResults(query: String, onSelect: (SettingsSearchEntry) -> Unit) {
    val context = LocalContext.current
    val hasProjectedCalendars = remember { CalinoContainer.get(context).projectedCalendars().isNotEmpty() }
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val matches = SettingsSearchEntries.filter { entry ->
        val searchable = "${entry.title} ${entry.section.title} ${entry.group} ${entry.description}"
        (entry.title != "Let assistants use Calino" || android.os.Build.VERSION.SDK_INT >= 36) &&
            (entry.title != "Let another app remind me" || hasProjectedCalendars) &&
            words.all { searchable.contains(it, ignoreCase = true) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CalinoSpacing.Screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (matches.isEmpty()) item { Text("No settings found", color = CalinoColors.Ink2) }
        items(matches, key = { "${it.section.name}:${it.title}" }) { entry ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(CalinoShapes.Card))
                    .background(CalinoColors.Panel)
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Open ${entry.title} in ${entry.section.title} settings"
                    },
            ) {
                Text(entry.title, style = CalinoTypography.bodyLarge)
                Text(entry.section.title, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2)
            }
        }
    }
}

@Composable
private fun SettingsSectionContent(
    section: SettingsSection,
    onOpenNotifications: () -> Unit,
    calDavAccounts: List<CalDavAccount>,
    onOpenAccounts: (Boolean, String?) -> Unit,
    onImportCalendar: () -> Unit,
    onExportCalendar: () -> Unit,
    webcalSubscriptions: List<calino.malinov.ski.data.model.WebcalSubscription>,
    onSubscribeWebcal: suspend (calino.malinov.ski.data.model.WebcalForm) -> Unit,
    onRemoveWebcal: (String) -> Unit,
    onSyncWebcal: (String) -> Unit,
    onToggleWebcalNotify: (String, Boolean) -> Unit,
    backgroundSyncCadence: BackgroundSyncCadence,
    backgroundSyncStatus: BackgroundSyncStatus,
    onBackgroundSyncCadenceChanged: (BackgroundSyncCadence) -> Unit,
    onOpenSubscribe: () -> Unit,
) {
    when (section) {
        SettingsSection.Display -> SettingsPage {
            GeneralSettings()
            AppearanceSettings()
            CalendarSettings()
        }
        SettingsSection.EventsTasks -> SettingsPage {
            EventSettings()
            CategoriesSettings()
        }
        SettingsSection.Reminders -> SettingsPage { NotificationSettings(onOpenNotifications) }
        SettingsSection.CalendarsSync -> SettingsPage {
            SyncSettings(
                calDavAccounts,
                onOpenAccounts,
                webcalSubscriptions,
                onRemoveWebcal,
                onSyncWebcal,
                onToggleWebcalNotify,
                backgroundSyncCadence,
                backgroundSyncStatus,
                onBackgroundSyncCadenceChanged,
                onOpenSubscribe,
            )
        }
        SettingsSection.DataAccess -> SettingsPage {
            DataSettings(onImportCalendar, onExportCalendar)
            SettingsGroup("AI Photo Import") { AiVisionSettingsContent() }
        }
    }
}

@Composable
private fun SettingsPage(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Keep the last setting group comfortably scrollable rather than
        // letting it finish against the screen edge.
        contentPadding = PaddingValues(
            start = CalinoSpacing.Screen,
            end = CalinoSpacing.Screen,
            top = 18.dp,
            bottom = CalinoSpacing.PillClearance,
        ),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = SettingsContentMaxWidth),
                verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun GeneralSettings() {
    val preferences = LocalCalinoPreferences.current
    SettingsGroup("Regional defaults") {
        SettingRow("Time format", "Choose the clock that feels natural", controlLayout = SettingRowControlLayout.AdaptiveSegmented) {
            // Unlike its neighbours this one is wired through: it drives every
            // clock face in the app, not just its own segmented control.
            val preferences = LocalCalinoPreferences.current
            CompactSegmentedControl(
                options = CalinoTimeFormat.entries.map { it.label },
                selectedIndex = preferences.timeFormatChoice.ordinal,
                onSelected = { preferences.setTimeFormat(CalinoTimeFormat.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
                semanticLabel = "Time format",
            )
        }
    }
    SettingsGroup("Surfaces") {
        SettingToggleRow(
            "Journal",
            "Show dated notes in the main navigation",
            preferences.journalEnabled,
            preferences.setJournalEnabled,
        )
        SettingToggleRow(
            "Contacts",
            "Show your neighbor directory in the main navigation",
            preferences.contactsEnabled,
            preferences.setContactsEnabled,
        )
    }
}

@Composable
private fun AppearanceSettings() {
    val preferences = LocalCalinoPreferences.current
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsGroup("Theme") {
            Column(Modifier.padding(18.dp)) {
                Text("Appearance", style = CalinoTypography.labelLarge)
                Text("Paper, night, or follow the system.", style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 3.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CalinoThemeChoice.entries.forEach { choice ->
                        // System previews whichever palette the phone is
                        // currently in, so the card shows what choosing it
                        // would actually do rather than a third invented look.
                        val preview = when (choice) {
                            CalinoThemeChoice.Light -> CalinoThemes.PaperLight
                            CalinoThemeChoice.Dark -> CalinoThemes.PaperDark
                            CalinoThemeChoice.System ->
                                if (isSystemInDarkTheme()) CalinoThemes.PaperDark else CalinoThemes.PaperLight
                        }
                        ThemeCard(
                            choice.label,
                            preview,
                            selected = choice == preferences.themeChoice,
                        ) { preferences.setThemeChoice(choice) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSettings() {
    val preferences = LocalCalinoPreferences.current
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsGroup("Display") {
            SettingChoiceRow(
                label = "Default view",
                description = "The view Calino opens first",
                options = CalinoDefaultView.entries,
                selected = preferences.defaultView,
                labelOf = { it.label },
                onSelected = preferences.setDefaultView,
            )
            SettingChoiceRow(
                label = "First day of week",
                description = "Where every calendar grid begins",
                options = CalinoWeekStart.entries,
                selected = preferences.weekStartChoice,
                labelOf = { it.label },
                onSelected = preferences.setWeekStart,
            )
            SettingToggleRow(
                "Show week numbers",
                "An ISO week-number rail down the left of the month grid",
                preferences.showWeekNumbers,
                preferences.setShowWeekNumbers,
            )
            SecondaryZoneSetting(preferences.secondaryZoneId, preferences.setSecondaryZoneId)
            SettingToggleRow(
                "Show pull bar",
                "The zoom bar between the calendar and the day",
                preferences.showZoomHandle,
                preferences.setShowZoomHandle,
            )
            SettingToggleRow(
                "Menu pill",
                "Tap, swipe up or hold the view icon to jump between views. Off keeps the swipe-only add pill",
                preferences.menuPill,
                preferences.setMenuPill,
            )
            SettingChoiceRow(
                label = "Event density",
                description = "How much of a busy day a month cell shows",
                options = CalinoEventDensity.entries,
                selected = preferences.eventDensity,
                labelOf = { it.label },
                onSelected = preferences.setEventDensity,
            )
        }
    }
}

@Composable
private fun EventSettings() {
    val preferences = LocalCalinoPreferences.current
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsGroup("New event defaults") {
            SettingChoiceRow(
                label = "Default duration",
                description = "Used when a time is parsed without an end",
                options = CalinoDefaultDuration.entries,
                selected = preferences.defaultDuration,
                labelOf = { it.label },
                onSelected = preferences.setDefaultDuration,
            )
        }
        SettingsGroup("Display") {
            SettingToggleRow(
                "Show end times",
                "Include how long an event runs",
                preferences.showEndTimes,
                preferences.setShowEndTimes,
            )
            SettingToggleRow(
                "Show locations",
                "Include places below event titles",
                preferences.showLocations,
                preferences.setShowLocations,
            )
        }
        SettingsGroup("Tasks in calendar") {
            SettingToggleRow(
                "Hide completed tasks",
                "Keep finished work out of the calendar",
                preferences.hideCompletedTasks,
                preferences.setHideCompletedTasks,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSettings() {
    // The editor offers the same list, so both read one fixture.
    var categories by remember { mutableStateOf(FixtureCategories) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val colors = listOf(CalinoColors.Blue, CalinoColors.Rose, CalinoColors.Amber, CalinoColors.Plum, CalinoColors.Green)
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsGroup("Labels used by your records") {
            val existingCategories = if (adding) categories.dropLast(1) else categories
            existingCategories.forEachIndexed { index, category ->
                CategoryRow(category, index, colors)
                if (index < existingCategories.lastIndex) SettingDivider()
            }
            AnimatedVisibility(
                visible = adding && categories.isNotEmpty(),
                enter = expandVertically(tween(240)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
            ) {
                Column {
                    SettingDivider()
                    CategoryRow(categories.last(), categories.lastIndex, colors)
                }
            }
            SettingDivider()
            TextButton(
                onClick = {
                    if (!adding) {
                        categories = categories + "New category"
                        adding = true
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            ) { Text("+  Add category", color = CalinoColors.Accent) }
        }
    }
}

@Composable
private fun CategoryRow(category: String, index: Int, colors: List<Color>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(colors[index % colors.size]))
        Text(category, Modifier.weight(1f).padding(start = 13.dp), style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Text("${(index + 1) * 2} records", style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
    }
}

@Composable
private fun NotificationSettings(onOpenPreview: () -> Unit) {
    val preferences = LocalCalinoPreferences.current
    val permission = LocalNotificationPermission.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        if (!permission.granted) {
            SettingsGroup("Permission") {
                SettingActionRow(
                    title = "Notifications are off",
                    description = "Calino cannot deliver a reminder until Android allows it",
                    action = if (permission.requestable) "Allow" else "Settings",
                    enabled = true,
                    onClick = {
                        if (permission.requestable) permission.request() else permission.openSystemSettings()
                    },
                )
            }
        }
        SettingsGroup("Events") {
            SettingToggleRow(
                "Event reminders",
                "A quiet nudge before an event begins",
                preferences.eventRemindersEnabled,
                preferences.setEventRemindersEnabled,
            )
        }
        SettingsGroup("Tasks") {
            SettingToggleRow(
                "Tasks due",
                "Remind me when a task reaches its date",
                preferences.taskRemindersEnabled,
                preferences.setTaskRemindersEnabled,
            )
        }
        // Only while there is something projected. A switch offering to hand
        // reminders to an app that has been given no calendars would hand
        // them to nobody, and the reminders would simply stop.
        if (remember { CalinoContainer.get(context).projectedCalendars().isNotEmpty() }) {
            SettingsGroup("System calendar") {
                SettingToggleRow(
                    "Let another app remind me",
                    "Your calendar app notifies for the calendars Calino publishes to " +
                        "Android, instead of Calino. Tasks stay with Calino either way.",
                    preferences.providerRemindersEnabled,
                    preferences.setProviderRemindersEnabled,
                )
            }
        }
        // "Daily brief" used to sit here as a planned row. It is not a delivery
        // of a reminder the user set, it is a separate feature nobody has
        // built, and a switch that promises a summary and delivers nothing is
        // worse than no switch. Removed rather than left lying.
        SettingsGroup("Delivery") {
            SettingActionRow(
                title = "Reminders and channels",
                description = "What is scheduled next, and how each channel is set",
                action = "Open",
                enabled = true,
                onClick = onOpenPreview,
            )
            SettingDivider()
            SettingActionRow(
                title = "Android notification settings",
                description = "Sounds, importance, and lock-screen behaviour",
                action = "Open",
                enabled = true,
                onClick = { context.startActivity(systemSettingsIntent(context)) },
            )
        }
        SettingsGroup("If a reminder never arrives") {
            SettingNote(
                "Some phones -- Samsung, Xiaomi, Huawei, OnePlus and others -- shut background " +
                    "apps down aggressively to save battery, which stops alarms from firing at all. " +
                    "If reminders go missing, exempt Calino from battery optimisation. " +
                    "dontkillmyapp.com lists the exact steps for each manufacturer.",
            )
        }
        SettingsGroup("New event reminder") {
            SettingChoiceRow(
                label = "Default reminder",
                description = "What a new event reminds you with",
                options = CalinoDefaultReminder.entries,
                selected = preferences.defaultReminder,
                labelOf = { it.label },
                onSelected = preferences.setDefaultReminder,
            )
        }
    }
}

@Composable
private fun SyncSettings(
    accounts: List<CalDavAccount>,
    onOpenAccounts: (Boolean, String?) -> Unit,
    webcalSubscriptions: List<calino.malinov.ski.data.model.WebcalSubscription>,
    onRemoveWebcal: (String) -> Unit,
    onSyncWebcal: (String) -> Unit,
    onToggleWebcalNotify: (String, Boolean) -> Unit,
    backgroundSyncCadence: BackgroundSyncCadence,
    backgroundSyncStatus: BackgroundSyncStatus,
    onBackgroundSyncCadenceChanged: (BackgroundSyncCadence) -> Unit,
    onOpenSubscribe: () -> Unit,
) {
    val preferences = LocalCalinoPreferences.current
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsGroup("Connected accounts") {
            SettingActionRow(
                title = "Calendars and accounts",
                description = "Manage connected accounts, calendars, and address books",
                action = "Open",
                actionContentDescription = "Open calendars and accounts",
                enabled = true,
                onClick = { onOpenAccounts(false, null) },
            )
            SettingDivider()
            if (accounts.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CalinoColors.AccentSoft), contentAlignment = Alignment.Center) {
                        Icon(CalinoIcons.Calendar, "", tint = CalinoColors.Accent, modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text("No calendar accounts", style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text("Local records only · nothing connected", style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
                    }
                    Box(Modifier.size(8.dp).clip(CircleShape).background(CalinoColors.Amber))
                }
            } else {
                accounts.forEachIndexed { index, account ->
                    if (index > 0) SettingDivider()
                    val active = account.calendars.count { it.enabled }
                    SettingActionRow(
                        title = account.displayName,
                        description = "${account.username} · $active of ${account.calendars.size} calendars on",
                        action = "Manage",
                        enabled = true,
                        onClick = { onOpenAccounts(false, account.id) },
                    )
                }
            }
            SettingDivider()
            TextButton(
                onClick = { onOpenAccounts(true, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    .semantics { contentDescription = "Add calendar account" },
            ) { Text("+  Add calendar account", color = CalinoColors.Accent) }
        }
        SettingsGroup("Subscribed calendars") {
            if (webcalSubscriptions.isEmpty()) {
                Text(
                    "Read-only overlays from a public .ics or webcal URL. They are not a CalDAV account.",
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink2,
                    modifier = Modifier.padding(18.dp),
                )
            } else {
                webcalSubscriptions.forEachIndexed { index, subscription ->
                    if (index > 0) SettingDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(subscription.name, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text(
                            calino.malinov.ski.data.webcal.WebcalUrl.hostOf(subscription.url),
                            style = CalinoTypography.bodySmall,
                            color = CalinoColors.Ink2,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = { onSyncWebcal(subscription.id) }) { Text("Sync now") }
                            TextButton(
                                onClick = { onToggleWebcalNotify(subscription.id, !subscription.notifyReminders) },
                            ) {
                                Text(if (subscription.notifyReminders) "Mute reminders" else "Fire reminders")
                            }
                            TextButton(onClick = { onRemoveWebcal(subscription.id) }) {
                                Text("Remove", color = CalinoColors.Rose)
                            }
                        }
                    }
                }
            }
            SettingDivider()
            TextButton(
                onClick = onOpenSubscribe,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    .semantics { contentDescription = "Subscribe to calendar" },
            ) { Text("+  Subscribe to calendar (.ics)", color = CalinoColors.Accent) }
        }
        SettingsGroup("Sync settings") {
            SettingChoiceRow(
                label = "Background sync",
                description = "Requested refresh frequency for connected accounts",
                options = BackgroundSyncCadence.entries,
                selected = backgroundSyncCadence,
                labelOf = { it.shortLabel },
                onSelected = onBackgroundSyncCadenceChanged,
            )
            SettingNote("Android controls when background work runs and may defer it to protect battery or data.")
            val dateFormatter = remember {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            }
            val zone = remember { ZoneId.systemDefault() }
            SettingNote(
                "Last background attempt: ${backgroundSyncStatus.lastAttemptEpochMs?.let { formatBackgroundSyncTime(it, dateFormatter, zone) } ?: "Not yet"}\n" +
                    "Last successful sync: ${backgroundSyncStatus.lastSuccessEpochMs?.let { formatBackgroundSyncTime(it, dateFormatter, zone) } ?: "Not yet"}",
            )
            backgroundSyncStatus.lastFailure?.let { failure ->
                SettingNote("Last sync issue: $failure")
            }
            SettingDivider()
            SettingChoiceRow(
                label = "Event sync range",
                description = "Past and future events kept available offline and in search",
                options = CalinoEventSyncRange.entries,
                selected = preferences.eventSyncRange,
                labelOf = { it.label },
                onSelected = preferences.setEventSyncRange,
            )
        }
    }
}

@Composable
private fun DataSettings(onImport: () -> Unit, onExport: () -> Unit) {
    SettingsGroup("Import & export") {
        SettingActionRow("Export calendar", "Save a local .ics copy of one calendar", "Export", enabled = true, onClick = onExport)
        SettingDivider()
        SettingActionRow("Import calendar", "Review events from an existing .ics file", "Choose file", enabled = true, onClick = onImport)
    }
    val context = LocalContext.current
    var phoneSearch by remember { mutableStateOf(PhoneSearchAccess.isEnabled(context)) }
    var assistants by remember { mutableStateOf(AssistantAccess.isEnabled(context)) }
    SettingsGroup("Search & assistants") {
        SettingToggleRow(
            "Show in phone search",
            "Find your events and tasks from the phone's search, such as Samsung Finder. " +
                "Stays on this phone. Journals and contacts stay private",
            phoneSearch,
        ) { enabled ->
            PhoneSearchAccess.setEnabled(context, enabled)
            phoneSearch = enabled
        }
        // AppFunctions exist from Android 16. Below that the row would switch
        // on a service nothing can call.
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            SettingDivider()
            SettingToggleRow(
                "Let assistants use Calino",
                "Assistants such as Gemini can read your events and tasks, which may leave the phone, " +
                    "and prepare new ones for you to save. Journals and contacts stay private",
                assistants,
            ) { enabled ->
                AssistantAccess.setEnabled(context, enabled)
                assistants = enabled
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val target = LocalSettingsSearchTarget.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(target) {
        if (target?.group == title && (target.title == title || target.title == "Categories")) {
            bringIntoViewRequester.bringIntoView()
        }
    }
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester)
            .clip(RoundedCornerShape(CalinoShapes.Card)).background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card)),
    ) {
        Text(
            title.uppercase(),
            style = CalinoTypography.labelSmall,
            color = CalinoColors.Ink3,
            modifier = Modifier.padding(
                start = SettingsRowHorizontalPadding,
                end = SettingsRowHorizontalPadding,
                top = 16.dp,
                bottom = 12.dp,
            ),
        )
        SettingDivider()
        content()
    }
}

@Composable
private fun SettingRow(
    label: String,
    description: String,
    enabled: Boolean = true,
    controlLayout: SettingRowControlLayout = SettingRowControlLayout.Inline,
    control: @Composable () -> Unit,
) {
    val target = LocalSettingsSearchTarget.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(target) {
        if (target?.title == label) bringIntoViewRequester.bringIntoView()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(horizontal = SettingsRowHorizontalPadding, vertical = SettingsRowVerticalPadding)
            .alphaIfDisabled(enabled)
            // Dimming alone used to leave the control live, so a row that meant
            // nothing could still be toggled -- and a screen reader was offered
            // a switch that could not move.
            .semantics { if (!enabled) disabled() },
    ) {
        // Only a segmented control takes a line of its own; it needs the full
        // width to stay legible. Switches, value pills and tags stay inline on
        // the right, where the label's weight keeps them from starving it.
        val stacked = controlLayout == SettingRowControlLayout.AdaptiveSegmented ||
            (controlLayout == SettingRowControlLayout.AdaptiveTrailing && maxWidth < 360.dp)
        if (stacked) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Without an explicit width, Column measures this child at
                // its intrinsic width; long labels then wrap one character
                // per line on the phone-sized Settings card.
                SettingRowLabel(label, description, Modifier.fillMaxWidth())
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (controlLayout == SettingRowControlLayout.AdaptiveSegmented) {
                        Alignment.Center
                    } else {
                        Alignment.CenterEnd
                    },
                ) { control() }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingRowLabel(label, description, Modifier.weight(1f).padding(end = 12.dp))
                control()
            }
        }
    }
}

@Composable
private fun SettingRowLabel(label: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Text(description, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * A segmented row bound to a real preference.
 *
 * Deliberately not [SettingSegmented]: that helper keeps the selection in its
 * own `rememberSaveable`, which is exactly what made most of this screen move
 * without meaning anything.
 */
@Composable
private fun <T> SettingChoiceRow(
    label: String,
    description: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
) = SettingRow(label, description, controlLayout = SettingRowControlLayout.AdaptiveSegmented) {
    CompactSegmentedControl(
        options = options.map(labelOf),
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        onSelected = { index -> onSelected(options[index]) },
        modifier = Modifier.fillMaxWidth(),
        semanticLabel = label,
    )
}

@Composable
private fun SettingToggleRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) = SettingRow(label, description) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(checkedThumbColor = CalinoColors.OnAccent, checkedTrackColor = CalinoColors.Accent, uncheckedThumbColor = CalinoColors.Panel, uncheckedTrackColor = CalinoColors.Ink3.copy(.26f), uncheckedBorderColor = Color.Transparent),
        // Merged, not layered: a bare `semantics {}` here produces a node that
        // owns the label while the switch's own toggleable node underneath owns
        // the state, so a screen reader reads the two separately and neither
        // node is the whole control. Merging makes it one switch again.
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label toggle" },
    )
}

/**
 * A miniature of what a theme actually looks like.
 *
 * It paints itself from [preview]'s own values rather than from `name == "Dark"`
 * branches, so registering another palette in `CalinoThemes` gives it a correct
 * preview for free.
 */
@Composable
private fun ThemeCard(name: String, preview: CalinoPalette, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val outline by animateColorAsState(
        if (selected) CalinoColors.Accent else CalinoColors.Line,
        tween(180),
        label = "theme selection outline",
    )
    val foreground by animateColorAsState(
        if (selected) CalinoColors.Accent else CalinoColors.Ink,
        tween(160),
        label = "theme selection label",
    )
    Column(
        Modifier
            .width(112.dp)
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, outline, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name theme preview"
                role = Role.RadioButton
                this.selected = selected
                if (!enabled) disabled()
            }
            .padding(7.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(7.dp)).background(preview.Canvas)) {
            Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(.65f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(preview.Ink.copy(.35f)))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(7) { Box(Modifier.size(7.dp).clip(CircleShape).background(preview.Ink.copy(.12f))) } }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(5) { Box(Modifier.size(11.dp, 5.dp).clip(RoundedCornerShape(2.dp)).background(preview.Accent.copy(.55f))) } }
            }
        }
        Text(name, style = CalinoTypography.bodySmall.copy(fontWeight = FontWeight.Medium), color = foreground, modifier = Modifier.padding(top = 7.dp, start = 2.dp, bottom = 2.dp))
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    description: String,
    action: String,
    danger: Boolean = false,
    enabled: Boolean = false,
    actionContentDescription: String? = null,
    onClick: () -> Unit = {},
) {
    val target = LocalSettingsSearchTarget.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(target) {
        if (target?.title == title) bringIntoViewRequester.bringIntoView()
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester).padding(
            horizontal = SettingsRowHorizontalPadding,
            vertical = SettingsRowVerticalPadding,
        ),
    ) {
        val stackAction = maxWidth < 300.dp
        val arrangement = if (stackAction) Arrangement.spacedBy(12.dp) else Arrangement.spacedBy(0.dp)
        Column(Modifier.fillMaxWidth(), verticalArrangement = arrangement) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = if (stackAction) 0.dp else 12.dp)) {
                    Text(title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text(description, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 3.dp))
                }
                if (!stackAction) {
                    SettingActionButton(action, danger, enabled, actionContentDescription, onClick)
                }
            }
            if (stackAction) {
                SettingActionButton(
                    action,
                    danger,
                    enabled,
                    actionContentDescription,
                    onClick,
                    Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun SettingActionButton(
    action: String,
    danger: Boolean,
    enabled: Boolean,
    actionContentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 44.dp)
            .then(
                if (actionContentDescription != null) {
                    Modifier.semantics { contentDescription = actionContentDescription }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) CalinoColors.Rose else CalinoColors.Ink2),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (danger) CalinoColors.Rose.copy(.3f) else CalinoColors.Line),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) { Text(action, fontSize = 12.sp) }
}

@Composable
private fun SettingDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line2))

/** A paragraph inside a settings group, for the things a row cannot say. */
@Composable
private fun SettingNote(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        style = CalinoTypography.bodySmall,
        color = CalinoColors.Ink3,
    )
}

private fun formatBackgroundSyncTime(
    epochMillis: Long,
    formatter: DateTimeFormatter,
    zone: ZoneId,
): String = formatter.withZone(zone).format(Instant.ofEpochMilli(epochMillis))

private fun Modifier.alphaIfDisabled(enabled: Boolean): Modifier = if (enabled) this else alpha(.45f)

/**
 * A second zone labelled beside the hour rail, as the web app has. Turning it
 * on opens the picker; nothing is saved until a zone is chosen.
 */
@Composable
private fun SecondaryZoneSetting(zoneId: String?, onChange: (String?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val zone = zoneId?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() }
    SettingToggleRow(
        "Second time zone",
        zone?.let { "Hour labels also show ${calino.malinov.ski.util.CalinoZones.label(it)}" }
            ?: "Label the day and week hours in another zone too",
        zone != null || picking,
    ) { on ->
        if (on) picking = true else { picking = false; onChange(null) }
    }
    calino.malinov.ski.ui.components.EditorReveal(picking) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZonePicker(zone ?: java.time.ZoneId.systemDefault(), java.time.Instant.now()) { picked ->
                onChange(picked.id)
                picking = false
            }
        }
    }
}
