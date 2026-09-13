package calino.malinov.ski.poc.ui.surfaces

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.repository.FixtureCategories
import androidx.compose.foundation.isSystemInDarkTheme
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoPalette
import calino.malinov.ski.poc.design.CalinoThemes
import calino.malinov.ski.poc.util.CalinoThemeChoice
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.ui.components.MenuButton
import calino.malinov.ski.poc.design.CalinoShapes
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.ui.components.CalinoIcons
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import calino.malinov.ski.poc.notify.LocalNotificationPermission
import calino.malinov.ski.poc.notify.systemSettingsIntent
import calino.malinov.ski.poc.state.LocalCalinoPreferences
import calino.malinov.ski.poc.state.shouldSplit
import calino.malinov.ski.poc.ui.components.CompactSegmentedControl
import calino.malinov.ski.poc.util.CalinoDefaultDuration
import calino.malinov.ski.poc.util.CalinoDefaultReminder
import calino.malinov.ski.poc.util.CalinoDefaultView
import calino.malinov.ski.poc.util.CalinoEventDensity
import calino.malinov.ski.poc.util.CalinoEventSyncRange
import calino.malinov.ski.poc.util.CalinoTimeFormat
import calino.malinov.ski.poc.util.CalinoWeekStart
import kotlinx.coroutines.flow.distinctUntilChanged

/** The mobile settings sections mirror the eight-section web handoff. */
enum class SettingsSection(val title: String, val shortTitle: String) {
    General("General", "General"),
    Appearance("Appearance", "Look"),
    Calendar("Calendar", "Calendar"),
    Events("Events", "Events"),
    Categories("Categories", "Categories"),
    Notifications("Notifications", "Alerts"),
    Sync("Sync", "Sync"),
    Data("Data", "Data"),
    AiVision("AI Photo Import", "AI Photo"),
}

private val SettingsNavLaneHeight = 44.dp

/** The landscape rail: wide enough for the longest section name and no wider. */
private val SettingsRailWidth = 232.dp
private val SettingsNavPillHeight = 28.dp
private val SettingsRowVerticalPadding = 6.dp

private enum class SettingRowControlLayout {
    Inline,
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
) {
    var sectionName by rememberSaveable { mutableStateOf(SettingsSection.General.name) }
    val section = remember(sectionName) {
        runCatching { SettingsSection.valueOf(sectionName) }.getOrDefault(SettingsSection.General)
    }
    val currentSection by rememberUpdatedState(section)
    val sectionRailState = rememberLazyListState()
    val sectionPagerState = rememberPagerState(initialPage = section.ordinal) { SettingsSection.entries.size }

    LaunchedEffect(openAiVisionRequest) {
        if (openAiVisionRequest > 0) sectionName = SettingsSection.AiVision.name
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
        val sideRail = shouldSplit(maxWidth.value.toInt(), maxHeight.value.toInt())

        // The burger sits beside the title, as on Journal and Contacts, so the
        // header costs one line rather than a third of a phone screen.
        val header: @Composable (Modifier) -> Unit = { headerModifier ->
            Row(headerModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                onOpenMenu?.let {
                    MenuButton(onClick = it, modifier = Modifier.padding(end = 6.dp))
                }
                Text(
                    "Settings",
                    style = if (sideRail) CalinoTypography.headlineLarge else CalinoTypography.displayLarge,
                )
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
                SettingsSectionContent(SettingsSection.entries[page], onOpenNotifications, calDavAccounts, onOpenAccounts, onImportCalendar, onExportCalendar)
            }
        }

        if (sideRail) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(SettingsRailWidth)
                        .fillMaxHeight()
                        .background(CalinoColors.Side),
                ) {
                    header(Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
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
                Box(Modifier.fillMaxHeight().width(1.dp).background(CalinoColors.Line))
                pager(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header(Modifier.padding(horizontal = 20.dp, vertical = 14.dp))

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
                pager(Modifier.weight(1f).fillMaxWidth())
            }
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
private fun SettingsSectionContent(
    section: SettingsSection,
    onOpenNotifications: () -> Unit,
    calDavAccounts: List<CalDavAccount>,
    onOpenAccounts: (Boolean, String?) -> Unit,
    onImportCalendar: () -> Unit,
    onExportCalendar: () -> Unit,
) {
    when (section) {
        SettingsSection.General -> GeneralSettings()
        SettingsSection.Appearance -> AppearanceSettings()
        SettingsSection.Calendar -> CalendarSettings()
        SettingsSection.Events -> EventSettings()
        SettingsSection.Categories -> CategoriesSettings()
        SettingsSection.Notifications -> NotificationSettings(onOpenNotifications)
        SettingsSection.Sync -> SyncSettings(calDavAccounts, onOpenAccounts)
        SettingsSection.Data -> DataSettings(onImportCalendar, onExportCalendar)
        SettingsSection.AiVision -> AiVisionSettingsPage()
    }
}

@Composable
private fun SettingsPage(title: String, content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Keep the last setting group comfortably scrollable rather than
        // letting it finish against the screen edge.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = CalinoSpacing.PillClearance),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(title, style = CalinoTypography.headlineMedium)
        }
        item { content() }
    }
}

@Composable
private fun GeneralSettings() = SettingsPage("General") {
    val preferences = LocalCalinoPreferences.current
    SettingsGroup("Regional defaults") {
        PlannedRow("Timezone", "Used for event times and reminders", value = "Copenhagen")
        PlannedRow("Date format", "How dates are written across Calino", value = "18 May 2026")
        SettingRow("Time format", "Choose the clock that feels natural", controlLayout = SettingRowControlLayout.AdaptiveSegmented) {
            // Unlike its neighbours this one is wired through: it drives every
            // clock face in the app, not just its own segmented control.
            val preferences = LocalCalinoPreferences.current
            CompactSegmentedControl(
                options = CalinoTimeFormat.entries.map { it.label },
                selectedIndex = preferences.timeFormat.ordinal,
                onSelected = { preferences.setTimeFormat(CalinoTimeFormat.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
                semanticLabel = "Time format",
            )
        }
        PlannedRow("Language", "The interface language", value = "English")
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
    SettingsPage("Appearance") {
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
            SettingDivider()
            Column(Modifier.padding(18.dp).alphaIfDisabled(false)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Accent color", style = CalinoTypography.labelLarge)
                    PlannedTag()
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "Accent" to CalinoColors.Accent,
                        "Rose" to CalinoColors.Rose,
                        "Blue" to CalinoColors.Blue,
                        "Green" to CalinoColors.Green,
                        "Plum" to CalinoColors.Plum,
                    ).forEachIndexed { index, (name, color) ->
                        AccentSwatch(name, color, selected = index == 0, enabled = false) {}
                    }
                }
            }
            SettingDivider()
            PlannedRow("Font size", "Tune the reading scale", value = "Default")
        }
    }
}

@Composable
private fun CalendarSettings() {
    val preferences = LocalCalinoPreferences.current
    SettingsPage("Calendar") {
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
                selected = preferences.weekStart,
                labelOf = { it.label },
                onSelected = preferences.setWeekStart,
            )
            SettingToggleRow(
                "Show week numbers",
                "An ISO week-number rail down the left of the month grid",
                preferences.showWeekNumbers,
                preferences.setShowWeekNumbers,
            )
            SettingToggleRow(
                "Show pull bar",
                "The zoom bar between the calendar and the day",
                preferences.showZoomHandle,
                preferences.setShowZoomHandle,
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
        SettingsGroup("Grid behaviour") {
            PlannedToggleRow("Compact recurring events", "Keep repeated events calm in busy months", checked = true)
            PlannedToggleRow("Compact past weeks", "Give more room to the weeks ahead", checked = false)
            SettingToggleRow(
                "Hide completed tasks",
                "Keep finished work out of the calendar",
                preferences.hideCompletedTasks,
                preferences.setHideCompletedTasks,
            )
        }
    }
}

@Composable
private fun EventSettings() {
    val preferences = LocalCalinoPreferences.current
    SettingsPage("Events") {
        SettingsGroup("New event defaults") {
            SettingChoiceRow(
                label = "Default duration",
                description = "Used when a time is parsed without an end",
                options = CalinoDefaultDuration.entries,
                selected = preferences.defaultDuration,
                labelOf = { it.label },
                onSelected = preferences.setDefaultDuration,
            )
            SettingChoiceRow(
                label = "Default reminder",
                description = "What a new event reminds you with",
                options = CalinoDefaultReminder.entries,
                selected = preferences.defaultReminder,
                labelOf = { it.label },
                onSelected = preferences.setDefaultReminder,
            )
            PlannedRow("Default calendar", "Where quick additions are filed", value = "Personal")
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
            PlannedToggleRow("Snap to grid", "Align times to 15-minute increments", checked = false)
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
    SettingsPage("Categories") {
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
    SettingsPage("Notifications") {
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
        SettingsGroup("Default reminder") {
            SettingNote("What a new event reminds you with is set under Events.")
        }
    }
}

@Composable
private fun SyncSettings(accounts: List<CalDavAccount>, onOpenAccounts: (Boolean, String?) -> Unit) {
    val preferences = LocalCalinoPreferences.current
    SettingsPage("Sync") {
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
        SettingsGroup("Sync settings") {
            SettingChoiceRow(
                label = "Event sync range",
                description = "Past and future events kept available offline and in search",
                options = CalinoEventSyncRange.entries,
                selected = preferences.eventSyncRange,
                labelOf = { it.label },
                onSelected = preferences.setEventSyncRange,
            )
            SettingDivider()
            PlannedRow("Sync frequency", "How often the cache refreshes", value = "When Calino opens")
            PlannedToggleRow("Sync on launch", "Refresh before the first screen appears", checked = true)
        }
    }
}

@Composable
private fun DataSettings(onImport: () -> Unit, onExport: () -> Unit) = SettingsPage("Data") {
    SettingsGroup("Import & export") {
        SettingActionRow("Export calendar", "Save a local .ics copy of one calendar", "Export", enabled = true, onClick = onExport)
        SettingDivider()
        SettingActionRow("Import calendar", "Review events from an existing .ics file", "Choose file", enabled = true, onClick = onImport)
    }
    SettingsGroup("Danger zone") {
        Text("These actions are intentionally disabled in the UI-only POC.", style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(18.dp))
        SettingDivider()
        SettingActionRow("Delete all records", "Remove the local fixture data", "Delete", danger = true)
        SettingDivider()
        SettingActionRow("Reset Calino", "Return every preference to its defaults", "Reset", danger = true)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CalinoShapes.Card)).background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card)),
    ) {
        Text(title.uppercase(), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp))
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
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = SettingsRowVerticalPadding)
            .alphaIfDisabled(enabled)
            // Dimming alone used to leave the control live, so a row that meant
            // nothing could still be toggled -- and a screen reader was offered
            // a switch that could not move.
            .semantics { if (!enabled) disabled() },
    ) {
        // Only a segmented control takes a line of its own; it needs the full
        // width to stay legible. Switches, value pills and tags stay inline on
        // the right, where the label's weight keeps them from starving it.
        val stacked = controlLayout == SettingRowControlLayout.AdaptiveSegmented
        if (stacked) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Without an explicit width, Column measures this child at
                // its intrinsic width; long labels then wrap one character
                // per line on the phone-sized Settings card.
                SettingRowLabel(label, description, Modifier.fillMaxWidth())
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { control() }
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

/**
 * A row for a setting that has no behaviour behind it yet.
 *
 * It reads as deliberately unavailable rather than broken: dimmed, tagged, and
 * genuinely inert -- the control cannot be moved by touch, and accessibility is
 * told the row is disabled rather than being offered a switch that does
 * nothing.
 */
@Composable
private fun PlannedRow(label: String, description: String, value: String? = null) = SettingRow(
    label = label,
    description = description,
    enabled = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        value?.let { SettingValue(it) }
        PlannedTag()
    }
}

@Composable
private fun PlannedTag() = Text(
    "PLANNED",
    style = CalinoTypography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp),
    color = CalinoColors.Ink3,
    modifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(CalinoColors.Ink.copy(alpha = .05f))
        .padding(horizontal = 6.dp, vertical = 3.dp),
)

@Composable
private fun PlannedToggleRow(label: String, description: String, checked: Boolean) = SettingRow(
    label = label,
    description = description,
    enabled = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlannedTag()
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = false,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CalinoColors.Canvas,
                checkedTrackColor = CalinoColors.Accent,
                uncheckedThumbColor = CalinoColors.Canvas,
                uncheckedTrackColor = CalinoColors.Ink3.copy(alpha = .45f),
                disabledCheckedThumbColor = CalinoColors.Canvas,
                disabledCheckedTrackColor = CalinoColors.Accent,
                disabledUncheckedThumbColor = CalinoColors.Canvas,
                disabledUncheckedTrackColor = CalinoColors.Ink3.copy(alpha = .45f),
            ),
        )
    }
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

@Composable
private fun SettingValue(value: String) {
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(CalinoColors.Canvas)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The inline row gives the label a weight and the control its
        // intrinsic width. Filling the width here starves the label into
        // one-character wrapping on a wide (landscape) settings card.
        Text(
            value,
            style = CalinoTypography.bodyMedium,
            color = CalinoColors.Ink2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingSegmented(label: String, options: List<String>, selected: Int) {
    var activeIndex by rememberSaveable(options) { mutableIntStateOf(selected.coerceIn(0, options.lastIndex)) }
    LaunchedEffect(selected) {
        activeIndex = selected.coerceIn(0, options.lastIndex)
    }
    CompactSegmentedControl(
        options = options,
        selectedIndex = activeIndex,
        onSelected = { activeIndex = it },
        modifier = Modifier.fillMaxWidth(),
        semanticLabel = label,
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
private fun AccentSwatch(name: String, color: Color, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val outlineWidth by androidx.compose.animation.core.animateDpAsState(
        if (selected) 3.dp else 0.dp,
        tween(180),
        label = "accent selection indicator",
    )
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name accent color"
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(31.dp)
                .clip(CircleShape)
                .background(color)
                .border(outlineWidth, CalinoColors.Panel, CircleShape),
        )
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(description, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 2.dp))
        }
        OutlinedButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) { Text(action, fontSize = 12.sp) }
    }
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

private fun Modifier.alphaIfDisabled(enabled: Boolean): Modifier = if (enabled) this else alpha(.45f)
