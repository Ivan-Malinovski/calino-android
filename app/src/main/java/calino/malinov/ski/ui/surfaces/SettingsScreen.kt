package calino.malinov.ski.ui.surfaces

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.res.stringResource
import calino.malinov.ski.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import calino.malinov.ski.data.model.AutoCategoryRule
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.autoCategoriesFor
import androidx.compose.foundation.isSystemInDarkTheme
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoPalette
import calino.malinov.ski.design.CalinoThemes
import calino.malinov.ski.util.CalinoThemeChoice
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.ui.components.MenuButton
import calino.malinov.ski.ui.components.pockRouteKey
import calino.malinov.ski.ui.components.pockRouteLabel
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.CalinoSearchField
import calino.malinov.ski.ui.components.CalinoTextField
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
import calino.malinov.ski.util.LocalCalinoLocale
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

enum class SettingsSection(val titleRes: Int, val shortTitleRes: Int) {
    Display(R.string.set_display, R.string.set_display),
    EventsTasks(R.string.set_events_tasks, R.string.set_events_tasks),
    Reminders(R.string.set_reminders, R.string.set_reminders),
    CalendarsSync(R.string.set_calendars_sync, R.string.set_calendars_sync),
    DataAccess(R.string.set_data_access, R.string.set_data_access),
    Advanced(R.string.set_advanced, R.string.set_advanced),
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
    val titleRes: Int,
    val section: SettingsSection,
    val groupRes: Int,
    val description: String = "",
    val localizedSearchTermsRes: Int? = null,
)

private val SettingsSearchEntries = listOf(
    SettingsSearchEntry(R.string.set_time_format, SettingsSection.Display, R.string.set_regional_defaults, "clock 12 24 hour", R.string.set_search_terms_time_format),
    SettingsSearchEntry(R.string.set_journal, SettingsSection.Display, R.string.set_surfaces, "navigation", R.string.set_search_terms_journal),
    SettingsSearchEntry(R.string.set_contacts, SettingsSection.Display, R.string.set_surfaces, "navigation", R.string.set_search_terms_contacts),
    SettingsSearchEntry(R.string.set_theme, SettingsSection.Display, R.string.set_theme, "appearance light dark system", R.string.set_search_terms_theme),
    SettingsSearchEntry(R.string.set_default_view, SettingsSection.Display, R.string.set_display, "calendar start", R.string.set_search_terms_default_view),
    SettingsSearchEntry(R.string.set_first_day_of_week, SettingsSection.Display, R.string.set_display, "calendar grid", R.string.set_search_terms_first_day),
    SettingsSearchEntry(R.string.set_show_week_numbers, SettingsSection.Display, R.string.set_display, "Show week numbers"),
    SettingsSearchEntry(R.string.set_show_pull_bar, SettingsSection.Display, R.string.set_display, "zoom", R.string.set_search_terms_pull_bar),
    SettingsSearchEntry(R.string.set_calendar_edge_swipes, SettingsSection.Display, R.string.set_display, "calendar zoom gestures transitions swipe year agenda", R.string.set_calendar_edge_swipes_search),
    SettingsSearchEntry(R.string.set_menu_pill, SettingsSection.Display, R.string.set_display, "navigation", R.string.set_search_terms_menu_pill),
    SettingsSearchEntry(R.string.set_event_density, SettingsSection.Display, R.string.set_display, "month", R.string.set_search_terms_event_density),
    SettingsSearchEntry(R.string.set_sometime_this_week, SettingsSection.Display, R.string.set_display, "week tasks range badge sheet shelf", R.string.set_search_terms_sometime_this_week),
    SettingsSearchEntry(R.string.set_hide_completed_tasks, SettingsSection.EventsTasks, R.string.set_tasks_in_calendar, "Hide completed tasks"),
    SettingsSearchEntry(R.string.set_default_duration, SettingsSection.EventsTasks, R.string.set_new_event_defaults, "Default duration"),
    SettingsSearchEntry(R.string.set_show_end_times, SettingsSection.EventsTasks, R.string.set_display, "Show end times"),
    SettingsSearchEntry(R.string.set_range_multi_day_events_in_header, SettingsSection.EventsTasks, R.string.set_display, "Multi-day events in Range header"),
    SettingsSearchEntry(R.string.set_show_locations, SettingsSection.EventsTasks, R.string.set_display, "Show locations"),
    SettingsSearchEntry(R.string.set_categories, SettingsSection.EventsTasks, R.string.set_categories, "labels tags", R.string.set_search_terms_categories),
    SettingsSearchEntry(R.string.set_keyword_rules, SettingsSection.EventsTasks, R.string.set_keyword_rules, "auto categorize categories labels", R.string.set_search_terms_keyword_rules),
    SettingsSearchEntry(R.string.set_default_reminder, SettingsSection.Reminders, R.string.set_new_event_reminder, "Default reminder"),
    SettingsSearchEntry(R.string.set_event_reminders, SettingsSection.Reminders, R.string.set_events, "Event reminders"),
    SettingsSearchEntry(R.string.set_tasks_due, SettingsSection.Reminders, R.string.set_tasks, "Tasks due"),
    SettingsSearchEntry(R.string.set_let_another_app_remind_me, SettingsSection.Reminders, R.string.set_system_calendar, "Let another app remind me"),
    SettingsSearchEntry(R.string.set_reminders_and_channels, SettingsSection.Reminders, R.string.set_delivery, "notifications", R.string.set_search_terms_reminders),
    SettingsSearchEntry(R.string.set_android_notification_settings, SettingsSection.Reminders, R.string.set_delivery, "sounds", R.string.set_search_terms_notification_settings),
    SettingsSearchEntry(R.string.set_calendars_and_accounts, SettingsSection.CalendarsSync, R.string.set_connected_accounts, "CalDAV address books", R.string.set_search_terms_calendars_accounts),
    SettingsSearchEntry(R.string.set_subscribed_calendars, SettingsSection.CalendarsSync, R.string.set_subscribed_calendars, "ics", R.string.set_search_terms_subscribed_calendars),
    SettingsSearchEntry(R.string.set_background_sync, SettingsSection.CalendarsSync, R.string.set_sync_settings, "refresh frequency", R.string.set_search_terms_background_sync),
    SettingsSearchEntry(R.string.set_event_sync_range, SettingsSection.CalendarsSync, R.string.set_sync_settings, "offline search", R.string.set_search_terms_event_sync_range),
    SettingsSearchEntry(R.string.set_import_calendar, SettingsSection.DataAccess, R.string.set_import_export, "ics file", R.string.set_search_terms_import_calendar),
    SettingsSearchEntry(R.string.set_export_calendar, SettingsSection.DataAccess, R.string.set_import_export, "ics file", R.string.set_search_terms_export_calendar),
    SettingsSearchEntry(R.string.set_show_in_phone_search, SettingsSection.DataAccess, R.string.set_search_assistants, "Samsung Finder", R.string.set_search_terms_phone_search),
    SettingsSearchEntry(R.string.set_let_assistants_use_calino, SettingsSection.DataAccess, R.string.set_search_assistants, "Gemini AppFunctions", R.string.set_search_terms_assistants),
    SettingsSearchEntry(R.string.set_ai_photo_import, SettingsSection.DataAccess, R.string.set_ai_photo_import, "provider API key model", R.string.set_search_terms_ai_photo_import),
    SettingsSearchEntry(R.string.set_grow_details_from_events, SettingsSection.Advanced, R.string.set_motion, "animation container transform", R.string.set_search_terms_advanced_motion)
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
    categoryCatalog: CategoryCatalog = CategoryCatalog(),
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
    val aiPhotoImportLabel = t(R.string.set_ai_photo_import)
    val currentSection by rememberUpdatedState(section)
    val sectionRailState = rememberLazyListState()
    val sectionPagerState = rememberPagerState(initialPage = section.ordinal) { SettingsSection.entries.size }

    LaunchedEffect(openAiVisionRequest, aiPhotoImportLabel) {
        if (openAiVisionRequest > 0) {
            sectionName = SettingsSection.DataAccess.name
            searchRequest += 1
            searchTarget = SettingsSearchTarget(aiPhotoImportLabel, aiPhotoImportLabel, searchRequest)
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
                    t(R.string.set_settings),
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
                        contentDescription = if (searchOpen) t(R.string.set_close_settings_search) else t(R.string.set_search_settings),
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
                        categoryCatalog = categoryCatalog,
                    )
                }
            }
        }
        val searchField: @Composable () -> Unit = {
            CalinoSearchField(
                query = searchQuery,
                onQueryChanged = { searchQuery = it },
                placeholder = t(R.string.set_search_settings_hint),
                contentDescription = t(R.string.set_search_settings_field),
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = if (sideRail) 12.dp else 20.dp, vertical = 4.dp),
                inputModifier = Modifier.testTag("Search settings"),
            )
        }
        val results: @Composable () -> Unit = {
            SettingsSearchResults(searchQuery) { entry, title, group ->
                searchRequest += 1
                searchTarget = SettingsSearchTarget(title, group, searchRequest)
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
    val title = t(section.titleRes)
    val sectionDescription = t(R.string.set_section_settings, title)
    val background = if (selected) CalinoColors.AccentSoft else CalinoColors.Panel
    val foreground = if (selected) CalinoColors.Accent else CalinoColors.Ink2
    val outline = if (selected) CalinoColors.Accent.copy(.16f) else CalinoColors.Line
    Box(
        modifier
            // Keep the tab's touch target comfortable while the pill itself
            // stays compact in the horizontal rail.
            .height(SettingsNavLaneHeight)
            .widthIn(min = 48.dp)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = sectionDescription
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
                if (useFullTitle) title else t(section.shortTitleRes),
                color = foreground,
                style = CalinoTypography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingsSearchResults(query: String, onSelect: (SettingsSearchEntry, String, String) -> Unit) {
    val context = LocalContext.current
    val hasProjectedCalendars = remember { CalinoContainer.get(context).projectedCalendars().isNotEmpty() }
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val matches = SettingsSearchEntries.filter { entry ->
        val localizedSearchTerms = entry.localizedSearchTermsRes?.let(context::getString).orEmpty()
        val searchable = "${context.getString(entry.titleRes)} ${context.getString(entry.section.titleRes)} ${context.getString(entry.groupRes)} $localizedSearchTerms ${entry.description}"
        (entry.titleRes != R.string.set_let_assistants_use_calino || android.os.Build.VERSION.SDK_INT >= 36) &&
            (entry.titleRes != R.string.set_let_another_app_remind_me || hasProjectedCalendars) &&
            words.all { searchable.contains(it, ignoreCase = true) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CalinoSpacing.Screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (matches.isEmpty()) item { Text(t(R.string.set_no_settings_found), color = CalinoColors.Ink2) }
        items(matches, key = { "${it.section.name}:${it.titleRes}" }) { entry ->
            val title = t(entry.titleRes)
            val section = t(entry.section.titleRes)
            val group = t(entry.groupRes)
            val openDescription = t(R.string.set_open_1_s_in_2_s_settings, title, section)
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(CalinoShapes.Card))
                    .background(CalinoColors.Panel)
                    .clickable { onSelect(entry, title, group) }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = openDescription
                    },
            ) {
                Text(title, style = CalinoTypography.bodyLarge)
                Text(section, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2)
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
    categoryCatalog: CategoryCatalog,
) {
    when (section) {
        SettingsSection.Display -> SettingsPage {
            GeneralSettings()
            AppearanceSettings()
            CalendarSettings()
        }
        SettingsSection.EventsTasks -> SettingsPage {
            EventSettings()
            CategoriesSettings(categoryCatalog)
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
            SettingsGroup(t(R.string.set_ai_photo_import)) { AiVisionSettingsContent() }
        }
        SettingsSection.Advanced -> SettingsPage { AdvancedSettings() }
    }
}

@Composable
private fun AdvancedSettings() {
    val preferences = LocalCalinoPreferences.current
    SettingsGroup(t(R.string.set_motion)) {
        SettingToggleRow(
            t(R.string.set_grow_details_from_events),
            t(R.string.set_an_event_s_details_open_out_of_the_event_you_tapped_off_they),
            preferences.growDetailFromEvent,
            preferences.setGrowDetailFromEvent,
        )
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
    val context = LocalContext.current
    SettingsGroup(t(R.string.set_regional_defaults)) {
        SettingRow(t(R.string.set_time_format), t(R.string.set_choose_the_clock_that_feels_natural), controlLayout = SettingRowControlLayout.AdaptiveSegmented) {
            // Unlike its neighbours this one is wired through: it drives every
            // clock face in the app, not just its own segmented control.
            val preferences = LocalCalinoPreferences.current
            CompactSegmentedControl(
                options = CalinoTimeFormat.entries.map { it.localizedLabel() },
                selectedIndex = preferences.timeFormatChoice.ordinal,
                onSelected = { preferences.setTimeFormat(CalinoTimeFormat.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
                semanticLabel = t(R.string.set_time_format),
            )
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            SettingActionRow(
                title = t(R.string.set_language),
                description = t(R.string.set_choose_language),
                action = t(R.string.set_open),
                enabled = true,
                onClick = {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
        }
    }
    SettingsGroup(t(R.string.set_surfaces)) {
        SettingToggleRow(
            t(R.string.set_journal),
            t(R.string.set_show_dated_notes_in_the_main_navigation),
            preferences.journalEnabled,
            preferences.setJournalEnabled,
        )
        SettingToggleRow(
            t(R.string.set_contacts),
            t(R.string.set_show_your_neighbor_directory_in_the_main_navigation),
            preferences.contactsEnabled,
            preferences.setContactsEnabled,
        )
    }
}

@Composable
private fun AppearanceSettings() {
    val preferences = LocalCalinoPreferences.current
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsGroup(t(R.string.set_theme)) {
            Column(Modifier.padding(18.dp)) {
                Text(t(R.string.set_appearance), style = CalinoTypography.labelLarge)
                Text(t(R.string.set_paper_night_or_follow_the_system), style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 3.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
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
                            when (choice) {
                                CalinoThemeChoice.Light -> t(R.string.set_light)
                                CalinoThemeChoice.System -> t(R.string.set_system)
                                CalinoThemeChoice.Dark -> t(R.string.set_dark)
                            },
                            preview,
                            selected = choice == preferences.themeChoice,
                            modifier = Modifier.weight(1f),
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
        SettingsGroup(t(R.string.set_display)) {
            SettingChoiceRow(
                label = t(R.string.set_default_view),
                description = t(R.string.set_the_view_calino_opens_first),
                options = CalinoDefaultView.entries,
                selected = preferences.defaultView,
                labelOf = { it.localizedLabel() },
                onSelected = preferences.setDefaultView,
            )
            SettingChoiceRow(
                label = t(R.string.set_first_day_of_week),
                description = t(R.string.set_where_every_calendar_grid_begins),
                options = CalinoWeekStart.entries,
                selected = preferences.weekStartChoice,
                labelOf = { it.localizedLabel() },
                onSelected = preferences.setWeekStart,
            )
            SettingToggleRow(
                t(R.string.set_show_week_numbers),
                t(R.string.set_an_iso_week_number_rail_down_the_left_of_the_month_grid),
                preferences.showWeekNumbers,
                preferences.setShowWeekNumbers,
            )
            SecondaryZoneSetting(preferences.secondaryZoneId, preferences.setSecondaryZoneId)
            SettingToggleRow(
                t(R.string.set_show_pull_bar),
                t(R.string.set_the_zoom_bar_between_the_calendar_and_the_day),
                preferences.showZoomHandle,
                preferences.setShowZoomHandle,
            )
            SettingToggleRow(
                t(R.string.set_calendar_edge_swipes),
                t(R.string.set_calendar_edge_swipes_description),
                preferences.calendarEdgeSwipes,
                preferences.setCalendarEdgeSwipes,
            )
            SettingToggleRow(
                t(R.string.set_menu_pill),
                t(R.string.set_tap_swipe_up_or_hold_the_view_icon_to_jump_between_views_off),
                preferences.menuPill,
                preferences.setMenuPill,
            )
            PillViewsSetting()
            SettingChoiceRow(
                label = t(R.string.set_event_density),
                description = t(R.string.set_how_much_of_a_busy_day_a_month_cell_shows),
                options = CalinoEventDensity.entries,
                selected = preferences.eventDensity,
                labelOf = { it.localizedLabel() },
                onSelected = preferences.setEventDensity,
            )
        }
    }
}

/**
 * Which views the root pill carries, as one row of chips. Month is not offered:
 * it always stays, so the pill can always get home. A view left off is still in
 * the sidebar, and the pill's swipe skips it too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillViewsSetting() {
    val preferences = LocalCalinoPreferences.current
    SettingRow(
        label = t(R.string.set_views_on_the_pill),
        description = t(R.string.set_tap_to_hide_one_it_stays_in_the_sidebar),
        controlLayout = SettingRowControlLayout.AdaptiveSegmented,
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOfNotNull(
                PockRoute.Year,
                PockRoute.Range,
                PockRoute.Agenda,
                PockRoute.Tasks,
                PockRoute.Journal.takeIf { preferences.journalEnabled },
                PockRoute.Contacts.takeIf { preferences.contactsEnabled },
            ).forEach { route ->
                val key = pockRouteKey(route)
                val shown = key !in preferences.pillHiddenViews
                CalinoChip(
                    text = pockRouteLabel(route),
                    selected = shown,
                    description = t(R.string.set_show_1_s_on_the_pill, pockRouteLabel(route)),
                    semanticsRole = Role.Checkbox,
                    onClick = { preferences.setPillViewHidden(key, shown) },
                )
            }
        }
    }
}

@Composable
private fun EventSettings() {
    val preferences = LocalCalinoPreferences.current
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsGroup(t(R.string.set_new_event_defaults)) {
            SettingChoiceRow(
                label = t(R.string.set_default_duration),
                description = t(R.string.set_used_when_a_time_is_parsed_without_an_end),
                options = CalinoDefaultDuration.entries,
                selected = preferences.defaultDuration,
                labelOf = { it.localizedLabel() },
                onSelected = preferences.setDefaultDuration,
            )
        }
        SettingsGroup(t(R.string.set_display)) {
            SettingToggleRow(
                t(R.string.set_range_multi_day_events_in_header),
                t(R.string.set_range_multi_day_events_in_header_description),
                preferences.rangeMultiDayEventsInHeader,
                preferences.setRangeMultiDayEventsInHeader,
            )
            SettingToggleRow(
                t(R.string.set_show_end_times),
                t(R.string.set_include_how_long_an_event_runs),
                preferences.showEndTimes,
                preferences.setShowEndTimes,
            )
            SettingToggleRow(
                t(R.string.set_show_locations),
                t(R.string.set_include_places_below_event_titles),
                preferences.showLocations,
                preferences.setShowLocations,
            )
        }
        SettingsGroup(t(R.string.set_tasks_in_calendar)) {
            SettingToggleRow(
                t(R.string.set_hide_completed_tasks),
                t(R.string.set_keep_finished_work_out_of_the_calendar),
                preferences.hideCompletedTasks,
                preferences.setHideCompletedTasks,
            )
        }
    }
}

/**
 * What the Categories page needs from the loaded records: the names already in
 * use, and each record's title and categories for the live counts. Titles stay
 * in memory on this page; nothing here is written anywhere.
 */
@Immutable
data class CategoryCatalog(
    val knownCategories: List<String> = emptyList(),
    val records: List<Pair<String, List<String>>> = emptyList(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSettings(catalog: CategoryCatalog) {
    val preferences = LocalCalinoPreferences.current
    val userCategories = preferences.userCategories
    val rules = preferences.autoCategoryRules
    val offered = remember(userCategories, catalog.knownCategories) {
        (userCategories + catalog.knownCategories).distinct()
    }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var newKeywords by rememberSaveable { mutableStateOf("") }
    var newRuleCategory by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsGroup(t(R.string.set_categories)) {
            SettingNote(
                t(R.string.set_tags_like_work_or_health_that_you_can_put_on_events_and_task),
            )
            SettingDivider()
            AnimatedSettingRows(offered, keyOf = { it }) { category ->
                val count = catalog.records.count { (_, categories) -> category in categories }
                // Only a name added here and on no record can go: removing a
                // category from records would be a server edit, not a setting.
                val removable = category in userCategories && count == 0
                SettingActionRow(
                    title = category,
                    description = when {
                        count == 1 -> t(R.string.set_1_record)
                        count > 0 -> t(R.string.set_1_d_records, count)
                        category in userCategories -> t(R.string.set_added_here_not_used_yet)
                        else -> t(R.string.set_0_records)
                    },
                    action = if (removable) t(R.string.set_remove) else "",
                    danger = true,
                    enabled = removable,
                    actionContentDescription = t(R.string.set_remove_category_1_s, category),
                    onClick = { preferences.setUserCategories(userCategories - category) },
                )
            }
            if (offered.isNotEmpty()) SettingDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val name = newCategory.trim()
                val canAdd = name.isNotEmpty() && offered.none { it.equals(name, ignoreCase = true) }
                CalinoTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = t(R.string.set_new_category),
                    modifier = Modifier.weight(1f),
                )
                SettingActionButton(
                    t(R.string.set_add),
                    danger = false,
                    enabled = canAdd,
                    actionContentDescription = t(R.string.set_add_category),
                    onClick = {
                        preferences.setUserCategories(userCategories + name)
                        newCategory = ""
                    },
                )
            }
        }
        SettingsGroup(t(R.string.set_keyword_rules)) {
            SettingNote(
                t(R.string.set_when_a_title_you_type_contains_a_keyword_its_category_is_sel),
            )
            SettingDivider()
            AnimatedSettingRows(rules, keyOf = { it.id }) { rule ->
                val matches = catalog.records.count { (title, _) -> autoCategoriesFor(title, listOf(rule)).isNotEmpty() }
                SettingActionRow(
                    title = "${rule.keywords.joinToString(", ")} → ${rule.category}",
                    description = when (matches) {
                        0 -> t(R.string.set_no_loaded_titles_match)
                        1 -> t(R.string.set_1_loaded_title_matches)
                        else -> t(R.string.set_1_d_loaded_titles_match, matches)
                    },
                    action = t(R.string.set_remove),
                    danger = true,
                    enabled = true,
                    actionContentDescription = t(R.string.set_remove_rule_for_1_s, rule.category),
                    onClick = { preferences.setAutoCategoryRules(rules.filterNot { it.id == rule.id }) },
                )
            }
            if (rules.isNotEmpty()) SettingDivider()
            Column(
                Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CalinoTextField(
                    value = newKeywords,
                    onValueChange = { newKeywords = it },
                    label = t(R.string.set_keywords),
                    placeholder = t(R.string.set_standup_sync),
                    description = t(R.string.set_keywords_separated_by_commas),
                )
                if (offered.isEmpty()) {
                    Text(t(R.string.set_add_a_category_first), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        offered.forEach { category ->
                            CalinoChip(
                                text = category,
                                selected = category == newRuleCategory,
                                description = t(R.string.set_category_for_this_rule),
                                semanticsRole = Role.RadioButton,
                                onClick = { newRuleCategory = category.takeUnless { it == newRuleCategory } },
                            )
                        }
                    }
                }
                val keywords = newKeywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                val chosen = newRuleCategory?.takeIf { it in offered }
                SettingActionButton(
                    t(R.string.set_add_rule),
                    danger = false,
                    enabled = keywords.isNotEmpty() && chosen != null,
                    actionContentDescription = t(R.string.set_add_keyword_rule),
                    onClick = {
                        if (chosen != null) {
                            preferences.setAutoCategoryRules(
                                rules + AutoCategoryRule(UUID.randomUUID().toString(), keywords, chosen),
                            )
                            newKeywords = ""
                            newRuleCategory = null
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

/**
 * Rows that expand in and collapse out as [items] change, rather than popping.
 * A removed row is kept in its old place until its exit has finished; rows
 * present when the page first composes appear without animating.
 */
@Composable
private fun <T> AnimatedSettingRows(items: List<T>, keyOf: (T) -> String, row: @Composable (T) -> Unit) {
    val firstKeys = remember { items.map(keyOf).toSet() }
    val states = remember { mutableMapOf<String, MutableTransitionState<Boolean>>() }
    var shown by remember { mutableStateOf(items) }
    val currentKeys = items.map(keyOf).toSet()
    val merged = items.toMutableList()
    shown.forEachIndexed { index, item ->
        val state = states[keyOf(item)]
        val leaving = keyOf(item) !in currentKeys && state != null && !(state.isIdle && !state.currentState)
        if (leaving) merged.add(minOf(index, merged.size), item)
    }
    SideEffect { shown = merged.toList() }
    merged.forEachIndexed { index, item ->
        val itemKey = keyOf(item)
        val state = states.getOrPut(itemKey) { MutableTransitionState(itemKey in firstKeys) }
        state.targetState = itemKey in currentKeys
        key(itemKey) {
            AnimatedVisibility(
                visibleState = state,
                enter = expandVertically(CalinoMotion.standardSpatial()) + fadeIn(tween(CalinoMotion.ContentEnterMillis)),
                exit = shrinkVertically(CalinoMotion.standardSpatial()) + fadeOut(tween(CalinoMotion.ContentExitMillis)),
            ) {
                Column {
                    if (index > 0) SettingDivider()
                    row(item)
                }
            }
        }
    }
}

@Composable
private fun NotificationSettings(onOpenPreview: () -> Unit) {
    val preferences = LocalCalinoPreferences.current
    val permission = LocalNotificationPermission.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        if (!permission.granted) {
            SettingsGroup(t(R.string.set_permission)) {
                SettingActionRow(
                    title = t(R.string.set_notifications_are_off),
                    description = t(R.string.set_calino_cannot_deliver_a_reminder_until_android_allows_it),
                    action = if (permission.requestable) t(R.string.set_allow) else t(R.string.set_settings),
                    enabled = true,
                    onClick = {
                        if (permission.requestable) permission.request() else permission.openSystemSettings()
                    },
                )
            }
        }
        SettingsGroup(t(R.string.set_events)) {
            SettingToggleRow(
                t(R.string.set_event_reminders),
                t(R.string.set_a_quiet_nudge_before_an_event_begins),
                preferences.eventRemindersEnabled,
                preferences.setEventRemindersEnabled,
            )
        }
        SettingsGroup(t(R.string.set_tasks)) {
            SettingToggleRow(
                t(R.string.set_tasks_due),
                t(R.string.set_remind_me_when_a_task_reaches_its_date),
                preferences.taskRemindersEnabled,
                preferences.setTaskRemindersEnabled,
            )
        }
        // Only while there is something projected. A switch offering to hand
        // reminders to an app that has been given no calendars would hand
        // them to nobody, and the reminders would simply stop.
        if (remember { CalinoContainer.get(context).projectedCalendars().isNotEmpty() }) {
            SettingsGroup(t(R.string.set_system_calendar)) {
                SettingToggleRow(
                    t(R.string.set_let_another_app_remind_me),
                    t(R.string.set_your_calendar_app_notifies_for_the_calendars_calino_publishe),
                    preferences.providerRemindersEnabled,
                    preferences.setProviderRemindersEnabled,
                )
            }
        }
        // "Daily brief" used to sit here as a planned row. It is not a delivery
        // of a reminder the user set, it is a separate feature nobody has
        // built, and a switch that promises a summary and delivers nothing is
        // worse than no switch. Removed rather than left lying.
        SettingsGroup(t(R.string.set_delivery)) {
            SettingActionRow(
                title = t(R.string.set_reminders_and_channels),
                description = t(R.string.set_what_is_scheduled_next_and_how_each_channel_is_set),
                action = t(R.string.set_open),
                enabled = true,
                onClick = onOpenPreview,
            )
            SettingDivider()
            SettingActionRow(
                title = t(R.string.set_android_notification_settings),
                description = t(R.string.set_sounds_importance_and_lock_screen_behaviour),
                action = t(R.string.set_open),
                enabled = true,
                onClick = { context.startActivity(systemSettingsIntent(context)) },
            )
        }
        SettingsGroup(t(R.string.set_if_a_reminder_never_arrives)) {
            SettingNote(
                t(R.string.set_some_phones_samsung_xiaomi_huawei_oneplus_and_others_shut_ba),
            )
        }
        SettingsGroup(t(R.string.set_new_event_reminder)) {
            SettingChoiceRow(
                label = t(R.string.set_default_reminder),
                description = t(R.string.set_what_a_new_event_reminds_you_with),
                options = CalinoDefaultReminder.entries,
                selected = preferences.defaultReminder,
                labelOf = { it.localizedLabel() },
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
    val addAccountDescription = t(R.string.set_add_calendar_account)
    val subscribeDescription = t(R.string.set_subscribe_to_calendar)
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsGroup(t(R.string.set_connected_accounts)) {
            SettingActionRow(
                title = t(R.string.set_calendars_and_accounts),
                description = t(R.string.set_manage_connected_accounts_calendars_and_address_books),
                action = t(R.string.set_open),
                actionContentDescription = t(R.string.set_open_calendars_and_accounts),
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
                        Text(t(R.string.set_no_calendar_accounts), style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text(t(R.string.set_local_records_only_nothing_connected), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
                    }
                    Box(Modifier.size(8.dp).clip(CircleShape).background(CalinoColors.Amber))
                }
            } else {
                accounts.forEachIndexed { index, account ->
                    if (index > 0) SettingDivider()
                    val active = account.calendars.count { it.enabled }
                    SettingActionRow(
                        title = account.displayName,
                        description = t(
                            R.string.set_1_s_2_d_of_3_d_calendars_on,
                            account.username,
                            active,
                            account.calendars.size,
                        ),
                        action = t(R.string.set_manage),
                        enabled = true,
                        onClick = { onOpenAccounts(false, account.id) },
                    )
                }
            }
            SettingDivider()
            TextButton(
                onClick = { onOpenAccounts(true, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    .semantics { contentDescription = addAccountDescription },
            ) { Text(t(R.string.set_add_calendar_account_9e0b181), color = CalinoColors.Accent) }
        }
        SettingsGroup(t(R.string.set_subscribed_calendars)) {
            if (webcalSubscriptions.isEmpty()) {
                Text(
                    t(R.string.set_read_only_overlays_from_a_public_ics_or_webcal_url_they_are_),
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
                            TextButton(onClick = { onSyncWebcal(subscription.id) }) { Text(t(R.string.set_sync_now)) }
                            TextButton(
                                onClick = { onToggleWebcalNotify(subscription.id, !subscription.notifyReminders) },
                            ) {
                                Text(if (subscription.notifyReminders) t(R.string.set_mute_reminders) else t(R.string.set_fire_reminders))
                            }
                            TextButton(onClick = { onRemoveWebcal(subscription.id) }) {
                                Text(t(R.string.set_remove), color = CalinoColors.Rose)
                            }
                        }
                    }
                }
            }
            SettingDivider()
            TextButton(
                onClick = onOpenSubscribe,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    .semantics { contentDescription = subscribeDescription },
            ) { Text(t(R.string.set_subscribe_to_calendar_ics), color = CalinoColors.Accent) }
        }
        SettingsGroup(t(R.string.set_sync_settings)) {
            SettingChoiceRow(
                label = t(R.string.set_background_sync),
                description = t(R.string.set_requested_refresh_frequency_for_connected_accounts),
                options = BackgroundSyncCadence.entries,
                selected = backgroundSyncCadence,
                labelOf = { it.localizedShortLabel() },
                onSelected = onBackgroundSyncCadenceChanged,
            )
            SettingNote(t(R.string.set_android_controls_when_background_work_runs_and_may_defer_it_))
            val locale = LocalCalinoLocale
            val dateFormatter = remember(locale) {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
            }
            val zone = remember { ZoneId.systemDefault() }
            SettingNote(t(
                R.string.set_last_background_attempt_1_s_last_successful_sync_2_s,
                backgroundSyncStatus.lastAttemptEpochMs?.let { formatBackgroundSyncTime(it, dateFormatter, zone) } ?: t(R.string.set_not_yet),
                backgroundSyncStatus.lastSuccessEpochMs?.let { formatBackgroundSyncTime(it, dateFormatter, zone) } ?: t(R.string.set_not_yet),
            ))
            backgroundSyncStatus.lastFailure?.let { failure ->
                SettingNote(t(R.string.set_last_sync_issue_1_s, failure))
            }
            SettingDivider()
            SettingChoiceRow(
                label = t(R.string.set_event_sync_range),
                description = t(R.string.set_past_and_future_events_kept_available_offline_and_in_search),
                options = CalinoEventSyncRange.entries,
                selected = preferences.eventSyncRange,
                labelOf = { it.localizedLabel() },
                onSelected = preferences.setEventSyncRange,
            )
        }
    }
}

@Composable
private fun DataSettings(onImport: () -> Unit, onExport: () -> Unit) {
    SettingsGroup(t(R.string.set_import_export)) {
        SettingActionRow(t(R.string.set_export_calendar), t(R.string.set_save_a_local_ics_copy_of_one_calendar), t(R.string.set_export), enabled = true, onClick = onExport)
        SettingDivider()
        SettingActionRow(t(R.string.set_import_calendar), t(R.string.set_review_events_from_an_existing_ics_file), t(R.string.set_choose_file), enabled = true, onClick = onImport)
    }
    val context = LocalContext.current
    var phoneSearch by remember { mutableStateOf(PhoneSearchAccess.isEnabled(context)) }
    var assistants by remember { mutableStateOf(AssistantAccess.isEnabled(context)) }
    SettingsGroup(t(R.string.set_search_assistants)) {
        SettingToggleRow(
            t(R.string.set_show_in_phone_search),
            t(R.string.set_find_your_events_and_tasks_from_the_phone_s_search_such_as_s),
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
                t(R.string.set_let_assistants_use_calino),
                t(R.string.set_assistants_such_as_gemini_can_read_your_events_and_tasks_whi),
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
    val categoriesTitle = t(R.string.set_categories)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(target) {
        if (target?.group == title && (target.title == title || target.title == categoriesTitle)) {
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
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) = SettingRow(label, description, controlLayout = SettingRowControlLayout.AdaptiveSegmented) {
    CompactSegmentedControl(
        options = options.map { labelOf(it) },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        onSelected = { index -> onSelected(options[index]) },
        modifier = Modifier.fillMaxWidth(),
        semanticLabel = label,
    )
}

@Composable
private fun SettingToggleRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val toggleDescription = t(R.string.set_1_s_toggle, label)
    SettingRow(label, description) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CalinoColors.OnAccent, checkedTrackColor = CalinoColors.Accent, uncheckedThumbColor = CalinoColors.Panel, uncheckedTrackColor = CalinoColors.Ink3.copy(.26f), uncheckedBorderColor = Color.Transparent),
            // Merged, not layered: a bare `semantics {}` here produces a node that
            // owns the label while the switch's own toggleable node underneath owns
            // the state, so a screen reader reads the two separately and neither
            // node is the whole control. Merging makes it one switch again.
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = toggleDescription
            },
        )
    }
}

/**
 * A miniature of what a theme actually looks like.
 *
 * It paints itself from [preview]'s own values rather than from the localized theme name
 * branches, so registering another palette in `CalinoThemes` gives it a correct
 * preview for free.
 */
@Composable
private fun ThemeCard(name: String, preview: CalinoPalette, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val previewDescription = t(R.string.set_1_s_theme_preview, name)
    val outline by animateColorAsState(
        if (selected) CalinoColors.Accent else CalinoColors.Line,
        tween(CalinoMotion.ContentEnterMillis),
        label = "theme selection outline",
    )
    val foreground by animateColorAsState(
        if (selected) CalinoColors.Accent else CalinoColors.Ink,
        tween(CalinoMotion.ContentExitMillis),
        label = "theme selection label",
    )
    Column(
        modifier
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, outline, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = previewDescription
                role = Role.RadioButton
                this.selected = selected
                if (!enabled) disabled()
            }
            .padding(7.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(7.dp)).background(preview.Canvas)) {
            Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(.65f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(preview.Ink.copy(.35f)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { Box(Modifier.weight(1f).height(7.dp).clip(CircleShape).background(preview.Ink.copy(.12f))) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(5) { Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(preview.Accent.copy(.55f))) }
                }
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
                if (!stackAction && action.isNotEmpty()) {
                    SettingActionButton(action, danger, enabled, actionContentDescription, onClick)
                }
            }
            // An empty action is an informational row with no button.
            if (stackAction && action.isNotEmpty()) {
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

@Composable
private fun CalinoTimeFormat.localizedLabel(): String = when (this) {
    CalinoTimeFormat.System -> t(R.string.set_system)
    CalinoTimeFormat.TwelveHour -> t(R.string.set_12h)
    CalinoTimeFormat.TwentyFourHour -> t(R.string.set_24h)
}

@Composable
private fun CalinoDefaultView.localizedLabel(): String = when (this) {
    CalinoDefaultView.Month -> t(R.string.set_month)
    CalinoDefaultView.Week -> t(R.string.set_week)
    CalinoDefaultView.Day -> t(R.string.set_day)
    CalinoDefaultView.Range -> t(R.string.set_range)
    CalinoDefaultView.Agenda -> t(R.string.set_agenda)
}

@Composable
private fun CalinoWeekStart.localizedLabel(): String = when (this) {
    CalinoWeekStart.System -> t(R.string.set_system)
    CalinoWeekStart.Monday -> t(R.string.set_monday)
    CalinoWeekStart.Sunday -> t(R.string.set_sunday)
}

@Composable
private fun CalinoEventDensity.localizedLabel(): String = when (this) {
    CalinoEventDensity.Quiet -> t(R.string.set_quiet)
    CalinoEventDensity.Balanced -> t(R.string.set_balanced)
    CalinoEventDensity.Dense -> t(R.string.set_dense)
}

@Composable
private fun CalinoDefaultDuration.localizedLabel(): String = when (this) {
    CalinoDefaultDuration.Half -> t(R.string.set_30m)
    CalinoDefaultDuration.Hour -> t(R.string.set_60m)
    CalinoDefaultDuration.HourAndHalf -> t(R.string.set_90m)
}

@Composable
private fun CalinoDefaultReminder.localizedLabel(): String = when (this) {
    CalinoDefaultReminder.None -> t(R.string.set_none)
    CalinoDefaultReminder.AtStart -> t(R.string.set_at_start)
    CalinoDefaultReminder.TenMinutes -> t(R.string.set_10_min)
    CalinoDefaultReminder.ThirtyMinutes -> t(R.string.set_30_min)
    CalinoDefaultReminder.OneHour -> t(R.string.set_1_hour)
}

@Composable
private fun BackgroundSyncCadence.localizedShortLabel(): String = when (this) {
    BackgroundSyncCadence.Hourly -> t(R.string.set_1_h)
    BackgroundSyncCadence.FourHours -> t(R.string.set_4_h)
    BackgroundSyncCadence.TwelveHours -> t(R.string.set_12_h)
    BackgroundSyncCadence.Daily -> t(R.string.set_24_h)
    BackgroundSyncCadence.Off -> t(R.string.set_off)
}

@Composable
private fun CalinoEventSyncRange.localizedLabel(): String = when (this) {
    CalinoEventSyncRange.SixMonths -> t(R.string.set_6_mo)
    CalinoEventSyncRange.OneYear -> t(R.string.set_1_yr)
    CalinoEventSyncRange.TwoYears -> t(R.string.set_2_yr)
    CalinoEventSyncRange.FiveYears -> t(R.string.set_5_yr)
}

/**
 * A second zone labelled beside the hour rail, as the web app has. Turning it
 * on opens the picker; nothing is saved until a zone is chosen.
 */
@Composable
private fun SecondaryZoneSetting(zoneId: String?, onChange: (String?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val zone = zoneId?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() }
    SettingToggleRow(
        t(R.string.set_second_time_zone),
        zone?.let { t(R.string.set_hour_labels_also_show_1_s, calino.malinov.ski.util.CalinoZones.label(it)) }
            ?: t(R.string.set_label_the_day_and_week_hours_in_another_zone_too),
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
@Composable
private fun t(id: Int, vararg args: Any): String = stringResource(id, *args)
