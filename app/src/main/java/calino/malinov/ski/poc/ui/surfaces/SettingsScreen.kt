package calino.malinov.ski.poc.ui.surfaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoShapes
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.ui.components.CalinoIcons
import calino.malinov.ski.poc.ui.components.CompactSegmentedControl

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
}

private val SettingsNavLaneHeight = 44.dp
private val SettingsNavPillHeight = 28.dp
private val SettingsRowVerticalPadding = 6.dp
// Keep enough room for a readable label beside any trailing preference value.
// The foldable's outer display can report a wider dp width than the visible
// content area, so use a generous phone breakpoint.
private val SettingsInlineRowMinWidth = 440.dp

private enum class SettingRowControlLayout {
    Inline,
    AdaptiveSegmented,
}

@Composable
fun SettingsSurface(onOpenNotifications: () -> Unit = {}) {
    var sectionName by rememberSaveable { mutableStateOf(SettingsSection.General.name) }
    val section = remember(sectionName) {
        runCatching { SettingsSection.valueOf(sectionName) }.getOrDefault(SettingsSection.General)
    }
    val sectionRailState = rememberLazyListState()

    LaunchedEffect(section) {
        sectionRailState.animateScrollToItem(SettingsSection.entries.indexOf(section))
    }

    Column(
        Modifier.fillMaxSize().background(CalinoColors.Canvas),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text("Settings", style = CalinoTypography.displayLarge)
            Text(
                "Shape Calino around the way you think.",
                style = CalinoTypography.bodyMedium,
                color = CalinoColors.Ink2,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                "UI preview · controls change the preview only; sync, import, and preference persistence are not connected yet.",
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                modifier = Modifier.padding(top = 5.dp),
            )
        }

        val endFadeAlpha by animateFloatAsState(
            targetValue = if (sectionRailState.canScrollForward) 1f else 0f,
            animationSpec = tween(180),
            label = "settings section rail affordance",
        )
        Box(
            Modifier
                .fillMaxWidth()
                // Draw the affordance over the rail without adding a touch
                // target that could steal a horizontal drag from the chips.
                .drawWithCache {
                    val fadeWidth = 34.dp.toPx()
                    val fadeBrush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, CalinoColors.Canvas),
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
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(SettingsSection.entries, key = { it.name }) { entry ->
                    SettingsNavChip(entry, selected = entry == section) { sectionName = entry.name }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line))
        AnimatedContent(
            targetState = section,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(220)) { direction * it / 3 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(180)) { -direction * it / 3 } + fadeOut(tween(140)))
            },
            label = "settings section transition",
        ) { current ->
            SettingsSectionContent(current, onOpenNotifications)
        }
    }
}

@Composable
private fun SettingsNavChip(section: SettingsSection, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) CalinoColors.AccentSoft else CalinoColors.Panel,
        tween(160),
        label = "settings nav background",
    )
    val foreground by animateColorAsState(
        if (selected) CalinoColors.Accent else CalinoColors.Ink2,
        tween(160),
        label = "settings nav foreground",
    )
    val outline by animateColorAsState(
        if (selected) CalinoColors.Accent.copy(.16f) else CalinoColors.Line,
        tween(180),
        label = "settings nav outline",
    )
    Box(
        Modifier
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
                .clip(RoundedCornerShape(999.dp))
                .background(background)
                .border(1.dp, outline, RoundedCornerShape(999.dp))
                .padding(horizontal = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                section.shortTitle,
                color = foreground,
                style = CalinoTypography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingsSectionContent(section: SettingsSection, onOpenNotifications: () -> Unit) {
    when (section) {
        SettingsSection.General -> GeneralSettings()
        SettingsSection.Appearance -> AppearanceSettings()
        SettingsSection.Calendar -> CalendarSettings()
        SettingsSection.Events -> EventSettings()
        SettingsSection.Categories -> CategoriesSettings()
        SettingsSection.Notifications -> NotificationSettings(onOpenNotifications)
        SettingsSection.Sync -> SyncSettings()
        SettingsSection.Data -> DataSettings()
    }
}

@Composable
private fun SettingsPage(title: String, content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Keep the last setting group comfortably scrollable above the fixed
        // root dock rather than letting it finish against the dock boundary.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 96.dp),
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
    SettingsGroup("Regional defaults") {
        SettingRow("Timezone", "Used for event times and reminders") { SettingValue("Copenhagen") }
        SettingRow("Date format", "How dates are written across Calino") { SettingValue("18 May 2026") }
        SettingRow("Time format", "Choose the clock that feels natural", controlLayout = SettingRowControlLayout.AdaptiveSegmented) {
            SettingSegmented("Time format", listOf("12h", "24h"), selected = 1)
        }
        SettingRow("First day of week", "Used by every calendar grid", controlLayout = SettingRowControlLayout.AdaptiveSegmented) {
            SettingSegmented("First day of week", listOf("Monday", "Sunday"), selected = 0)
        }
        SettingRow("Language", "The interface language") { SettingValue("English") }
    }
}

@Composable
private fun AppearanceSettings() {
    var appearance by rememberSaveable { mutableStateOf("Light") }
    var accent by rememberSaveable { mutableStateOf(0) }
    SettingsPage("Appearance") {
        SettingsGroup("Theme") {
            Column(Modifier.padding(18.dp)) {
                Text("Appearance", style = CalinoTypography.labelLarge)
                Text("Paper, night, or follow the system.", style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 3.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("Light" to CalinoColors.Canvas, "System" to CalinoColors.Side, "Dark" to Color(0xFF26231F)).forEach { (name, color) ->
                        ThemeCard(name, color, selected = appearance == name) { appearance = name }
                    }
                }
            }
            SettingDivider()
            Column(Modifier.padding(18.dp)) {
                Text("Accent color", style = CalinoTypography.labelLarge)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "Accent" to CalinoColors.Accent,
                        "Rose" to CalinoColors.Rose,
                        "Blue" to CalinoColors.Blue,
                        "Green" to CalinoColors.Green,
                        "Plum" to CalinoColors.Plum,
                    ).forEachIndexed { index, (name, color) ->
                        AccentSwatch(name, color, selected = accent == index) { accent = index }
                    }
                }
            }
            SettingDivider()
            SettingRow("Font size", "Tune the reading scale", controlLayout = SettingRowControlLayout.AdaptiveSegmented) { SettingSegmented("Font size", listOf("Small", "Default", "Large"), selected = 1) }
        }
    }
}

@Composable
private fun CalendarSettings() {
    var weekNumbers by rememberSaveable { mutableStateOf(false) }
    var recurring by rememberSaveable { mutableStateOf(true) }
    var compactPast by rememberSaveable { mutableStateOf(false) }
    var hideDone by rememberSaveable { mutableStateOf(false) }
    SettingsPage("Calendar") {
        SettingsGroup("Display") {
            SettingRow("Default view", "The view Calino opens first", controlLayout = SettingRowControlLayout.AdaptiveSegmented) { SettingSegmented("Default view", listOf("Month", "Week", "Day"), 0) }
            SettingToggleRow("Show week numbers", "Add ISO week numbers to the grid", weekNumbers) { weekNumbers = it }
            SettingRow("Event density", "How much detail to show in a month", controlLayout = SettingRowControlLayout.AdaptiveSegmented) { SettingSegmented("Event density", listOf("Quiet", "Balanced", "Dense"), 1) }
        }
        SettingsGroup("Grid behaviour") {
            SettingToggleRow("Compact recurring events", "Keep repeated events calm in busy months", recurring) { recurring = it }
            SettingToggleRow("Compact past weeks", "Give more room to the weeks ahead", compactPast) { compactPast = it }
            SettingToggleRow("Hide completed tasks", "Keep finished work out of the calendar", hideDone) { hideDone = it }
        }
    }
}

@Composable
private fun EventSettings() {
    var endTimes by rememberSaveable { mutableStateOf(true) }
    var locations by rememberSaveable { mutableStateOf(true) }
    var snap by rememberSaveable { mutableStateOf(false) }
    SettingsPage("Events") {
        SettingsGroup("New event defaults") {
            SettingRow("Default duration", "Used when a time is parsed without an end", controlLayout = SettingRowControlLayout.AdaptiveSegmented) { SettingSegmented("Default duration", listOf("30m", "60m", "90m"), 1) }
            SettingRow("Default calendar", "Where quick additions are filed") { SettingValue("Personal") }
        }
        SettingsGroup("Display") {
            SettingToggleRow("Show end times", "Keep the agenda easy to scan", endTimes) { endTimes = it }
            SettingToggleRow("Show locations", "Include places below event titles", locations) { locations = it }
            SettingToggleRow("Snap to grid", "Align times to 15-minute increments", snap) { snap = it }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSettings() {
    var categories by remember { mutableStateOf(listOf("Work", "Personal", "Travel", "Admin", "Health")) }
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
    var eventReminders by rememberSaveable { mutableStateOf(true) }
    var taskReminders by rememberSaveable { mutableStateOf(true) }
    var dailyBrief by rememberSaveable { mutableStateOf(false) }
    SettingsPage("Notifications") {
        SettingsGroup("Events") {
            SettingToggleRow("Event reminders", "A quiet nudge before an event begins", eventReminders) { eventReminders = it }
            SettingRow("Default reminder", "Used for newly created events", enabled = eventReminders) { SettingValue("10 minutes before") }
        }
        SettingsGroup("Tasks") {
            SettingToggleRow("Tasks due", "Remind me when a task reaches its date", taskReminders) { taskReminders = it }
            SettingToggleRow("Daily brief", "A calm summary at the start of the day", dailyBrief) { dailyBrief = it }
        }
        SettingsGroup("Preview") {
            SettingActionRow("Notification preview", "See how Calino keeps notification actions focused", "Open", enabled = true, onClick = onOpenPreview)
        }
    }
}

@Composable
private fun SyncSettings() {
    var launchSync by rememberSaveable { mutableStateOf(true) }
    SettingsPage("Sync") {
        SettingsGroup("Connected accounts") {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CalinoColors.AccentSoft), contentAlignment = Alignment.Center) {
                    Icon(CalinoIcons.Calendar, "", tint = CalinoColors.Accent, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text("Personal calendar", style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text("Preview only · CalDAV not connected", style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(CalinoColors.Amber))
            }
            SettingDivider()
            TextButton(enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) { Text("+  Add calendar account", color = CalinoColors.Ink3) }
        }
        SettingsGroup("Sync settings") {
            SettingRow("Sync frequency", "How often the cache would refresh") { SettingValue("When Calino opens") }
            SettingToggleRow("Sync on launch", "Refresh before the first screen appears", launchSync) { launchSync = it }
        }
    }
}

@Composable
private fun DataSettings() = SettingsPage("Data") {
    SettingsGroup("Import & export") {
        SettingActionRow("Export calendar", "Save a local .ics copy of your records", "Export")
        SettingDivider()
        SettingActionRow("Import calendar", "Bring an existing .ics file into Calino", "Choose file")
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
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = SettingsRowVerticalPadding)
            .alphaIfDisabled(enabled),
    ) {
        // On phone-sized settings cards every control gets its own line. This
        // prevents both segmented controls and ordinary values/switches from
        // starving the label into one-character wrapping.
        val stacked = controlLayout == SettingRowControlLayout.AdaptiveSegmented || maxWidth < SettingsInlineRowMinWidth
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

@Composable
private fun SettingToggleRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) = SettingRow(label, description) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CalinoColors.Accent, uncheckedThumbColor = Color.White, uncheckedTrackColor = CalinoColors.Ink3.copy(.26f), uncheckedBorderColor = Color.Transparent),
        modifier = Modifier.semantics { contentDescription = "$label toggle" },
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
        Text(
            value,
            modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun ThemeCard(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
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
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name theme preview"
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(7.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(7.dp)).background(color)) {
            Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(.65f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (name == "Dark") Color.White.copy(.55f) else CalinoColors.Ink.copy(.35f)))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(7) { Box(Modifier.size(7.dp).clip(CircleShape).background(if (name == "Dark") Color.White.copy(.25f) else CalinoColors.Ink.copy(.12f))) } }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(5) { Box(Modifier.size(11.dp, 5.dp).clip(RoundedCornerShape(2.dp)).background(if (name == "Dark") CalinoColors.Accent.copy(.7f) else CalinoColors.Accent.copy(.5f))) } }
            }
        }
        Text(name, style = CalinoTypography.bodySmall.copy(fontWeight = FontWeight.Medium), color = foreground, modifier = Modifier.padding(top = 7.dp, start = 2.dp, bottom = 2.dp))
    }
}

@Composable
private fun AccentSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val outlineWidth by androidx.compose.animation.core.animateDpAsState(
        if (selected) 3.dp else 0.dp,
        tween(180),
        label = "accent selection indicator",
    )
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name accent color"
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
private fun SettingActionRow(title: String, description: String, action: String, danger: Boolean = false, enabled: Boolean = false, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(description, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 2.dp))
        }
        OutlinedButton(enabled = enabled, onClick = onClick, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) CalinoColors.Rose else CalinoColors.Ink2), border = androidx.compose.foundation.BorderStroke(1.dp, if (danger) CalinoColors.Rose.copy(.3f) else CalinoColors.Line), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text(action, fontSize = 12.sp) }
    }
}

@Composable
private fun SettingDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line2))

private fun Modifier.alphaIfDisabled(enabled: Boolean): Modifier = if (enabled) this else alpha(.45f)
