package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.JournalDraft
import calino.malinov.ski.R
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoSegmented
import calino.malinov.ski.ui.components.MenuButton
import calino.malinov.ski.ui.components.calinoPressable
import calino.malinov.ski.ui.components.CalinoMarkdown
import calino.malinov.ski.util.LocalCalinoLocale
import calino.malinov.ski.util.localizedDateFormatter
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.CompactSegmentedControl
import calino.malinov.ski.ui.components.AdaptiveDetailCard
import calino.malinov.ski.ui.components.BottomDetailOverlay
import calino.malinov.ski.ui.components.LocalCalinoSurfaceMode
import calino.malinov.ski.ui.components.calinoSurfaceEdgeFade
import calino.malinov.ski.ui.components.calinoSurfaceShadowBleed
import calino.malinov.ski.ui.components.rememberDatePicker
import calino.malinov.ski.ui.components.CalinoMonthCalendar
import calino.malinov.ski.ui.components.ModalActionPill
import calino.malinov.ski.ui.components.ModalPillActionTone
import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.state.CalinoSurfaceMode
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.util.dayOfWeekForColumn
import calino.malinov.ski.util.gridStart
import calino.malinov.ski.util.weekdayLetters
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import kotlinx.coroutines.launch

/** The overview handle's touch lane, matching [ZoomHandleTouchHeight] on the calendar. */
private val JournalTouchLane = 44.dp

/** How far the handle drag travels before the grid is fully unrolled. */
private val JournalHandleDragRange = 160.dp

/** The read modal's entry pager, driven directly in the device tests. */
const val JournalEntryPagerTag = "journal-entry-pager"

private fun YearMonth.journalEpochMonth(): Long = year.toLong() * 12L + monthValue - 1L
private fun journalMonthFromEpoch(epochMonth: Long): YearMonth =
    YearMonth.of(Math.floorDiv(epochMonth, 12L).toInt(), Math.floorMod(epochMonth, 12L).toInt() + 1)

/** A calm journal surface; fixture entries are local, DAV entries sync through the repository. */
@Composable
fun JournalSurface(
    entries: List<JournalEntry>,
    onAdd: () -> Unit = {},
    onCreate: (JournalEntry) -> Unit = {},
    onUpdate: (JournalEntry) -> Unit = {},
    onDelete: (JournalEntry) -> Unit = {},
    onEditingChanged: (Boolean) -> Unit = {},
    openEntryId: String? = null,
    onOpenEntryConsumed: () -> Unit = {},
    newEntryDate: LocalDate = LocalDate.of(2026, 5, 18),
    onOpenMenu: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    startEntryRequest: Int = 0,
) {
    val monthFormat = localizedDateFormatter("MMMM yyyy")
    val journalSearchDescription = stringResource(R.string.ed_journal_search)
    var overviewLevel by rememberSaveable { mutableStateOf(0) }
    var draftSequence by rememberSaveable { mutableStateOf(0) }
    var draftId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftDateEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val draft = draftId?.let { id ->
        JournalDraft(id, draftDateEpochDay?.let(LocalDate::ofEpochDay) ?: newEntryDate)
    }
    val draftEntry = draft?.let { JournalEntry(it.id, it.date, "", "") }
    val visibleEntries = if (draftEntry != null && entries.none { it.id == draftEntry.id }) {
        entries + draftEntry
    } else {
        entries
    }
    val sorted = remember(visibleEntries) { visibleEntries.sortedByDescending { it.date } }
    val initialMonth = YearMonth.from(sorted.firstOrNull()?.date ?: newEntryDate)
    var visibleEpochMonth by rememberSaveable { mutableStateOf(initialMonth.journalEpochMonth()) }
    val visibleMonth = journalMonthFromEpoch(visibleEpochMonth)
    val dragProgress = remember { Animatable(overviewLevel.toFloat()) }
    var directDragProgress by remember { mutableStateOf<Float?>(null) }
    val overviewProgress = directDragProgress ?: dragProgress.value
    val listState = rememberLazyListState()
    var programmaticScroll by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = visibleEntries.firstOrNull { it.id == editingId }
    fun selectEditor(id: String?) {
        editingId = id
        // Keep the shell's root-pill visibility in the same state write as
        // the editor itself. Reporting this from a LaunchedEffect leaves the
        // modal pill unmounted for a few frames before the root pill is told
        // to return, which is visible as a short blink after dismissal.
        onEditingChanged(id != null)
    }
    LaunchedEffect(openEntryId, entries) {
        if (openEntryId != null && entries.any { it.id == openEntryId }) {
            selectEditor(openEntryId)
            onOpenEntryConsumed()
        }
    }
    fun startNewEntry() {
        val id = "draft-journal-${draftSequence + 1}"
        draftSequence += 1
        draftId = id
        draftDateEpochDay = newEntryDate.toEpochDay()
        selectEditor(id)
        // Keep the host callback as a notification, but do not persist until
        // the editor explicitly saves a non-empty draft.
        onAdd()
    }

    // The add affordance now lives in the shell's floating pill, which asks
    // for a new entry by bumping this counter.
    LaunchedEffect(startEntryRequest) {
        if (startEntryRequest > 0) startNewEntry()
    }

    fun closeEntry(entry: JournalEntry) {
        if (draft?.id == entry.id) {
            draftId = null
            draftDateEpochDay = null
        }
        selectEditor(null)
    }

    Box(Modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = CalinoSpacing.Screen, vertical = 18.dp), verticalAlignment = Alignment.Top) {
                onOpenMenu?.let {
                    MenuButton(onClick = it, modifier = Modifier.padding(end = 6.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ed_journal_title), style = CalinoTypography.displayLarge)
                    val oldest = sorted.lastOrNull()?.date
                    val subtitle = when (sorted.size) {
                        0 -> stringResource(R.string.ed_journal_no_entries_yet)
                        else -> pluralStringResource(R.plurals.ed_journal_entries_since, sorted.size, sorted.size, oldest!!.format(monthFormat))
                    }
                    Text(subtitle, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 3.dp))
                }
                onOpenSearch?.let { openSearch ->
                    IconButton(
                        onClick = openSearch,
                        modifier = Modifier.size(JournalTouchLane).semantics { contentDescription = journalSearchDescription },
                    ) {
                        CalinoIcon(CalinoIcon.Search, tint = CalinoColors.Ink2, modifier = Modifier.size(20.dp), contentDescription = null)
                    }
                }
            }

            if (sorted.isEmpty()) {
                JournalEmptyState()
            } else {
                JournalOverview(
                    modifier = Modifier.weight(1f),
                    entries = sorted,
                    visibleMonth = visibleMonth,
                    overviewLevel = overviewLevel,
                    overviewProgress = overviewProgress,
                    listState = listState,
                    onVisibleMonth = { visibleEpochMonth = it.journalEpochMonth() },
                    onProgrammaticScroll = { targetMonth, entryIndex ->
                        visibleEpochMonth = targetMonth.journalEpochMonth()
                        programmaticScroll = true
                        scope.launch {
                            listState.animateScrollToItem(entryIndex + 1)
                            programmaticScroll = false
                        }
                    },
                    onEntry = { selectEditor(it.id) },
                    onToggleLevel = {
                        val target = if (overviewLevel == 0) 1 else 0
                        overviewLevel = target
                        scope.launch { dragProgress.animateTo(target.toFloat(), CalinoMotion.expressiveSpatial()) }
                    },
                    onDragStart = {
                        scope.launch { dragProgress.stop() }
                        directDragProgress = dragProgress.value
                    },
                    onDrag = { delta ->
                        directDragProgress = ((directDragProgress ?: dragProgress.value) + delta).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val released = directDragProgress ?: dragProgress.value
                        directDragProgress = null
                        val target = if (released >= .5f) 1 else 0
                        val returning = target == overviewLevel
                        overviewLevel = target
                        scope.launch {
                            dragProgress.snapTo(released)
                            dragProgress.animateTo(
                                target.toFloat(),
                                if (returning) CalinoMotion.gestureReturn() else CalinoMotion.expressiveSpatial(),
                            )
                        }
                    },
                    onDragCancel = {
                        val abandoned = directDragProgress ?: dragProgress.value
                        directDragProgress = null
                        scope.launch {
                            dragProgress.snapTo(abandoned)
                            dragProgress.animateTo(overviewLevel.toFloat(), CalinoMotion.gestureReturn())
                        }
                    },
                )

                LaunchedEffect(listState, sorted) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .collect { index ->
                            // The sticky rule is item 0, so the entry under it
                            // is one back -- and at the very top of the list
                            // that is the first entry itself. Clamping rather
                            // than skipping index 0 is what re-labels the rule
                            // when an entry is moved into a new month and
                            // lands at the head of the list.
                            if (!programmaticScroll) {
                                sorted.getOrNull((index - 1).coerceAtLeast(0))
                                    ?.let { visibleEpochMonth = YearMonth.from(it.date).journalEpochMonth() }
                            }
                        }
                }
            }
        }

        val currentEntry = visibleEntries.firstOrNull { it.id == editingId }
        if (currentEntry != null) {
            JournalEditor(
                entry = currentEntry,
                entries = sorted,
                onNavigate = { selectEditor(it.id) },
                onDismiss = { closeEntry(currentEntry) },
                // A draft's date lives in the host, which rebuilds the
                // synthetic entry from it, so the editor hands it back here.
                onDraftDateChange = { picked -> draftDateEpochDay = picked.toEpochDay() },
                onSave = { updated ->
                    if (draft?.id == updated.id) {
                        draft?.commit(updated.title, updated.body)?.let(onCreate)
                        draftId = null
                        draftDateEpochDay = null
                    } else {
                        onUpdate(updated)
                    }
                    selectEditor(null)
                },
                onDelete = { deleted ->
                    if (draft?.id == deleted.id) {
                        draftId = null
                        draftDateEpochDay = null
                    } else {
                        onDelete(deleted)
                    }
                    selectEditor(null)
                },
            )

        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JournalOverview(
    modifier: Modifier = Modifier,
    entries: List<JournalEntry>,
    visibleMonth: YearMonth,
    overviewLevel: Int,
    overviewProgress: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onVisibleMonth: (YearMonth) -> Unit,
    onProgrammaticScroll: (YearMonth, Int) -> Unit,
    onEntry: (JournalEntry) -> Unit,
    onToggleLevel: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val oldestMonth = YearMonth.from(entries.last().date)
    val newestMonth = YearMonth.from(entries.first().date)
    val months = remember(oldestMonth, newestMonth) {
        List(ChronoUnit.MONTHS.between(oldestMonth, newestMonth).toInt() + 1) { oldestMonth.plusMonths(it.toLong()) }
    }
    val monthCounts = remember(entries) { entries.groupingBy { YearMonth.from(it.date) }.eachCount() }
    val scrubberState = rememberLazyListState(initialFirstVisibleItemIndex = months.lastIndex.coerceAtLeast(0))

    fun entryIndexFor(month: YearMonth): Int? =
        entries.indexOfFirst { YearMonth.from(it.date) == month }.takeIf { it >= 0 }

    LaunchedEffect(visibleMonth, months) {
        months.indexOf(visibleMonth).takeIf { it >= 0 }?.let { scrubberState.animateScrollToItem(it) }
    }

    Column(modifier.fillMaxWidth()) {
        JournalMonthScrubber(
            months = months,
            counts = monthCounts,
            visibleMonth = visibleMonth,
            state = scrubberState,
            modifier = Modifier.alpha(1f - overviewProgress * .55f),
            onMonth = { month ->
                val index = entryIndexFor(month)
                    ?: entries.indexOfFirst { YearMonth.from(it.date) < month }.takeIf { it >= 0 }
                    ?: entries.lastIndex
                onProgrammaticScroll(month, index)
            },
        )
        // The month block is this surface's header, so it closes on the same
        // hairline the day group headers elsewhere draw with.
        Box(
            Modifier.fillMaxWidth().padding(horizontal = CalinoSpacing.Screen)
                .height(1.dp).background(CalinoColors.Line2),
        )
        JournalMonthGrid(
            month = visibleMonth,
            entries = entries,
            progress = overviewProgress,
            onMonth = onVisibleMonth,
            onDay = { date -> entries.indexOfFirst { it.date == date }.takeIf { it >= 0 }?.let { onProgrammaticScroll(YearMonth.from(date), it) } },
        )
        JournalOverviewHandle(
            level = overviewLevel,
            progress = overviewProgress,
            onClick = onToggleLevel,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - overviewProgress * .45f },
            contentPadding = PaddingValues(start = CalinoSpacing.Screen, end = CalinoSpacing.Screen, bottom = CalinoSpacing.PillClearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            stickyHeader(key = "journal-month-rule") {
                JournalMonthRule(visibleMonth, monthCounts[visibleMonth] ?: 0, overviewLevel)
            }
            itemsIndexed(entries, key = { _, entry -> "journal:${entry.id}" }) { index, entry ->
                JournalCard(
                    entry = entry,
                    mostRecent = index == 0,
                    modifier = Modifier.animateItem(),
                    onClick = { onEntry(entry) },
                )
            }
        }
    }
}

@Composable
private fun JournalMonthScrubber(
    months: List<YearMonth>,
    counts: Map<YearMonth, Int>,
    visibleMonth: YearMonth,
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    onMonth: (YearMonth) -> Unit,
) {
    val monthFormat = localizedDateFormatter("MMMM yyyy")
    val shortMonthFormat = localizedDateFormatter("MMM")
    val selectedMonthFormat = localizedDateFormatter("MMM yy")
    val context = LocalContext.current
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().height(CalinoSegmented.LaneHeight),
        contentPadding = PaddingValues(horizontal = CalinoSpacing.Screen),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(months, key = { it.toString() }) { month ->
            val selected = month == visibleMonth
            val count = counts[month] ?: 0
            val distance = kotlin.math.abs(ChronoUnit.MONTHS.between(month, visibleMonth).toInt())
            val monthDescription = if (count == 0) {
                context.getString(R.string.ed_journal_month_empty, month.format(monthFormat))
            } else {
                context.resources.getQuantityString(R.plurals.ed_journal_month_entries, count, count, month.format(monthFormat))
            }
            Column(
                Modifier
                    .heightIn(min = JournalTouchLane)
                    .alpha(if (distance > 2) .55f else 1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = monthDescription
                    }
                    .clickable { onMonth(month) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    (if (selected) month.format(selectedMonthFormat) else month.format(shortMonthFormat)).uppercase(LocalCalinoLocale),
                    style = CalinoTypography.labelSmall,
                    color = when { selected -> CalinoColors.OnSelection; count > 0 -> CalinoColors.Ink2; else -> CalinoColors.Ink3 },
                    modifier = Modifier
                        .clip(RoundedCornerShape(CalinoSegmented.SegmentRadius))
                        .background(if (selected) CalinoColors.SelectionFill else Color.Transparent)
                        .padding(
                            horizontal = if (selected) 11.dp else 9.dp,
                            vertical = 5.dp,
                        ),
                )
                Box(
                    Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape)
                        .background(when { selected -> CalinoColors.SelectionFill; count > 0 -> CalinoColors.Accent; else -> Color.Transparent }),
                )
            }
        }
    }
}

@Composable
private fun JournalMonthGrid(
    month: YearMonth,
    entries: List<JournalEntry>,
    progress: Float,
    onMonth: (YearMonth) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    if (progress <= 0f) return
    val entryDates = remember(entries) { entries.mapTo(mutableSetOf()) { it.date } }
    Column(
        Modifier
            .fillMaxWidth()
            // The clip has to sit OUTSIDE the layout below. The layout reports a
            // shrunken height while the grid unrolls but still places the
            // full-height panel; a clip placed after it only ever sees that
            // full-height placeable, so the grid bleeds through the handle, the
            // month rule and the list for the whole drag.
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val shownHeight = (placeable.height * progress.coerceIn(0f, 1f)).toInt()
                layout(placeable.width, shownHeight) { placeable.placeRelative(0, 0) }
            }
            .padding(horizontal = CalinoSpacing.Screen + 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(CalinoShapes.Card))
            .background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card))
            .padding(top = 12.dp, start = 22.dp, end = 22.dp, bottom = 16.dp),
    ) {
        CalinoMonthCalendar(
            month = month,
            onMonth = onMonth,
            onPick = onDay,
            marked = entryDates,
            pickable = { it in entryDates },
        )
    }
}

@Composable
private fun JournalOverviewHandle(
    level: Int,
    progress: Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val description = stringResource(R.string.ed_journal_overview_level, level + 1)
    val pullUp = stringResource(R.string.ed_journal_pull_up_collapse)
    val releaseCollapse = stringResource(R.string.ed_journal_release_collapse)
    val releaseMonth = stringResource(R.string.ed_journal_release_month)
    val pullMonth = stringResource(R.string.ed_journal_pull_month)
    val dragRange = JournalHandleDragRange
    val gesture = Modifier.pointerInput(dragRange) {
        detectVerticalDragGestures(
            onDragStart = { onDragStart() },
            onVerticalDrag = { change, amount -> change.consume(); onDrag(amount / dragRange.toPx()) },
            onDragCancel = onDragCancel,
            onDragEnd = onDragEnd,
        )
    }
    Row(
        Modifier.fillMaxWidth().requiredHeight(JournalTouchLane).then(gesture)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = CalinoSpacing.Screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(26.dp).height(3.dp).background(CalinoColors.Ink.copy(.25f)))
        Text(
            when {
                level == 1 && progress >= .5f -> pullUp
                level == 1 -> releaseCollapse
                progress >= .5f -> releaseMonth
                else -> pullMonth
            },
            style = CalinoTypography.labelSmall.copy(letterSpacing = 1.sp),
            color = CalinoColors.Ink3,
        )
        repeat(2) { index ->
            Box(Modifier.width(if (index == level) 14.dp else 6.dp)
                .height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (index == level) CalinoColors.Accent else CalinoColors.Ink3.copy(.3f)))
        }
        Box(Modifier.width(26.dp).height(3.dp).background(CalinoColors.Ink.copy(.25f)))
    }
}

@Composable
private fun JournalMonthRule(month: YearMonth, count: Int, level: Int) {
    val monthFormat = localizedDateFormatter("MMMM yyyy")
    val shortMonthFormat = localizedDateFormatter("MMM")
    Row(
        Modifier.fillMaxWidth().background(CalinoColors.Canvas).padding(horizontal = CalinoSpacing.Screen, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (level == 1) pluralStringResource(R.plurals.ed_journal_entries_in_month, count, count, month.format(shortMonthFormat)).uppercase(LocalCalinoLocale) else month.format(monthFormat).uppercase(LocalCalinoLocale),
            style = CalinoTypography.labelSmall,
            color = CalinoColors.Ink,
        )
        Box(Modifier.weight(1f).height(1.dp).background(CalinoColors.Line))
        if (level == 0) Text(pluralStringResource(R.plurals.ed_journal_entries_count, count, count).uppercase(LocalCalinoLocale), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
    }
}

@Composable
private fun JournalCard(entry: JournalEntry, mostRecent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dayName = entry.date.dayOfWeek.getDisplayName(TextStyle.SHORT, LocalCalinoLocale)
    val title = entry.title.ifBlank { stringResource(R.string.ed_journal_untitled_note) }
    val openDescription = stringResource(R.string.ed_journal_open_entry, title)
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalinoShapes.Card))
            .background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card))
            .semantics(mergeDescendants = true) { contentDescription = openDescription }
            .calinoPressable(onClick = onClick),
    ) {
        val spineColor = if (mostRecent) CalinoColors.Accent else CalinoColors.AccentSoft
        Row(
            Modifier.fillMaxWidth().drawBehind {
                drawRect(spineColor, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            }.padding(15.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.width(45.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayName.uppercase(), style = CalinoTypography.labelSmall.copy(fontSize = CalinoTypography.labelSmall.fontSize * .9f), color = CalinoColors.Ink3)
                Text(entry.date.dayOfMonth.toString(), style = CalinoTypography.titleLarge, color = CalinoColors.Accent, modifier = Modifier.padding(top = 2.dp))
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(title, style = CalinoTypography.titleSmall.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(entry.body, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun JournalEmptyState() {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(CalinoColors.AccentSoft), contentAlignment = Alignment.Center) { Text("✦", color = CalinoColors.Accent, fontSize = 22.sp) }
        Text(stringResource(R.string.ed_journal_nothing_written), style = CalinoTypography.titleMedium, modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.ed_journal_first_note_hint), style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 4.dp))
    }
}

private val WordBoundaryPattern = Regex("\\s+")

/** A rememberSaveable saver for the editor's working date. */
private val EpochDaySaver = androidx.compose.runtime.saveable.Saver<LocalDate, Long>(
    save = { it.toEpochDay() },
    restore = LocalDate::ofEpochDay,
)

@Composable
private fun JournalEditor(
    entry: JournalEntry,
    entries: List<JournalEntry>,
    onNavigate: (JournalEntry) -> Unit,
    onDismiss: () -> Unit,
    onSave: (JournalEntry) -> Unit,
    onDelete: (JournalEntry) -> Unit,
    onDraftDateChange: (LocalDate) -> Unit,
) {
    var title by rememberSaveable(entry.id) { mutableStateOf(entry.title) }
    var body by rememberSaveable(entry.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(entry.body)) }
    val isNewEntry = entry.id.startsWith("draft-journal-")
    // A draft's date belongs to the host, which rebuilds the synthetic entry
    // from it; an existing entry's date is editor state until it is saved.
    var editedDate by rememberSaveable(entry.id, stateSaver = EpochDaySaver) { mutableStateOf(entry.date) }
    val date = if (isNewEntry) entry.date else editedDate
    var isEditing by rememberSaveable(entry.id) { mutableStateOf(isNewEntry) }
    var editorMode by rememberSaveable(entry.id) { mutableStateOf(0) }
    var confirmDelete by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showDiscard by rememberSaveable(entry.id) { mutableStateOf(false) }
    val dirty = title != entry.title || body.text != entry.body || date != entry.date
    val canSave = title.isNotBlank() || body.text.isNotBlank()
    val focusTitle = isNewEntry
    val titleFocusRequester = remember { FocusRequester() }
    val wordCount = remember(body.text) { body.text.trim().let { if (it.isEmpty()) 0 else it.split(WordBoundaryPattern).size } }
    val headerTint = CalinoColors.AccentSoft.copy(alpha = .42f)

    // Not keyed on the entry: the card stays on screen while the pager moves
    // between entries, and re-seeding this at every settle would restart the
    // entry animation under a finger that never asked for one.
    var shown by remember { mutableStateOf(true) }
    var closeAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun closeAnimated(action: () -> Unit) {
        if (shown) { closeAction = action; shown = false }
    }
    LaunchedEffect(shown) {
        if (!shown) { kotlinx.coroutines.delay(220); closeAction?.invoke() }
    }

    fun cancelEditing() {
        when {
            dirty -> showDiscard = true
            isNewEntry -> closeAnimated(onDismiss)
            else -> isEditing = false
        }
    }

    fun dismissEditor() {
        if (isEditing && dirty) showDiscard = true else closeAnimated(onDismiss)
    }

    LaunchedEffect(focusTitle) {
        if (focusTitle) {
            kotlinx.coroutines.delay(280)
            titleFocusRequester.requestFocus()
        }
    }

    // An uncommitted draft is the only entry on offer: swiping off it would
    // either strand unsaved text or page into a neighbour the host would then
    // have to reconcile with a draft it still holds.
    val pages = if (isNewEntry) listOf(entry) else entries
    val pager = rememberPagerState(
        initialPage = pages.indexOfFirst { it.id == entry.id }.coerceAtLeast(0),
        pageCount = { pages.size },
    )
    val currentNavigate by rememberUpdatedState(onNavigate)
    val currentEntryId by rememberUpdatedState(entry.id)
    LaunchedEffect(pager, pages) {
        snapshotFlow { pager.settledPage }.collect { page ->
            pages.getOrNull(page)?.takeIf { it.id != currentEntryId }?.let(currentNavigate)
        }
    }
    // The host can also change the open entry on its own -- a deep link, or a
    // save that re-sorts the list -- so follow it, but never mid-gesture.
    LaunchedEffect(entry.id, pages) {
        val target = pages.indexOfFirst { it.id == entry.id }
        if (target >= 0 && target != pager.currentPage && !pager.isScrollInProgress) {
            pager.scrollToPage(target)
        }
    }
    // Derived, so the fade is switched on and off once per swipe instead of
    // recomposing every page on every frame of one.
    val paging by remember(pager) {
        derivedStateOf { pager.isScrollInProgress || pager.currentPageOffsetFraction != 0f }
    }
    val mode = LocalCalinoSurfaceMode.current
    val cancelEditLabel = stringResource(R.string.ed_journal_cancel_editing)
    val closeEntryLabel = stringResource(R.string.ed_journal_close_entry)
    val saveEntryLabel = stringResource(R.string.ed_journal_save)
    val editEntryLabel = stringResource(R.string.ed_journal_edit)
    val deleteEntryLabel = stringResource(R.string.ed_journal_delete)

    BottomDetailOverlay(
        visible = shown,
        onDismiss = ::dismissEditor,
        modifier = Modifier.fillMaxSize(),
        surfaceKind = CalinoSurfaceKind.Editor,
        pill = {
            ModalActionPill(
                addLabel = if (focusTitle) stringResource(R.string.ed_journal_new_entry) else stringResource(R.string.ed_journal_edit_entry),
                morphFromAddPill = true,
                inPillLane = true,
                expanded = shown,
                cancelLabel = stringResource(R.string.ed_journal_cancel),
                onCancel = if (isEditing) ::cancelEditing else ::dismissEditor,
                cancelDescription = if (isEditing) cancelEditLabel else closeEntryLabel,
                primaryLabel = if (isEditing) saveEntryLabel else editEntryLabel,
                primaryTone = if (isEditing) ModalPillActionTone.Save else ModalPillActionTone.Neutral,
                onPrimary = {
                    if (isEditing) closeAnimated { onSave(entry.copy(date = date, title = title.trim(), body = body.text.trim())) }
                    else isEditing = true
                },
                // Reading an entry, Edit is always on offer; writing one, Save
                // appears only once there is a change to write.
                primaryVisible = !isEditing || dirty || isNewEntry,
                primaryEnabled = !isEditing || canSave,
                primaryDescription = if (isEditing) stringResource(R.string.ed_journal_save_entry_description) else stringResource(R.string.ed_journal_edit_entry_description),
                deleteLabel = deleteEntryLabel,
                onDelete = { confirmDelete = false; closeAnimated { onDelete(entry) } },
                deleteDescription = stringResource(R.string.ed_journal_delete_entry_description),
                deleteConfirmationActive = confirmDelete,
                onDeleteConfirmationChange = { confirmDelete = it },
                deleteHoldToConfirm = true,
            )
        },
    ) { overlayModifier ->
        HorizontalPager(
            state = pager,
            key = { pages[it].id },
            beyondViewportPageCount = 1,
            // Paging is a reading gesture. A modal that is being written to,
            // or that is asking a question, keeps its pointer stream.
            userScrollEnabled = shown && pages.size > 1 && !isEditing && !showDiscard && !confirmDelete,
            // The page that leaves is clipped by the pager at the panel's
            // leading edge; fade it out on that line instead of cutting it.
            modifier = overlayModifier.testTag(JournalEntryPagerTag).calinoSurfaceEdgeFade(active = paging),
        ) { page ->
            val pageEntry = pages[page]
            val current = pageEntry.id == entry.id
            // A pager clips its pages along the scroll axis, so the card's
            // shadow has to fit inside this padding or it ends at a hard
            // vertical line. The end-panel host reserves the bleed itself.
            Box(
                Modifier.fillMaxSize().calinoSurfaceShadowBleed(
                    horizontal = if (mode == CalinoSurfaceMode.BottomSheet) 10.dp else 0.dp,
                ),
            ) {
                AdaptiveDetailCard(
                    visible = shown,
                    onDismiss = ::dismissEditor,
                    modifier = Modifier.fillMaxSize(),
                    dismissDistance = 720.dp,
                    // A dirty-editor dismissal opens the confirmation bar instead of
                    // leaving the editor. Changing this key asks the gesture surface to
                    // spring back underneath that prompt.
                    resetKey = if (current) showDiscard else false,
                    handleColor = headerTint,
                ) { cardModifier ->
                    Column(
                        cardModifier.fillMaxSize().background(CalinoColors.Canvas),
                    ) {
                        if (!current) {
                            // A neighbour is only ever read: the editing state
                            // above belongs to the entry the card opened on.
                            JournalReadPane(
                                entry = pageEntry,
                                title = pageEntry.title,
                                body = pageEntry.body,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                labelTitle = false,
                            )
                            Spacer(Modifier.height(CalinoSpacing.PillClearance))
                            return@Column
                        }
                        JournalEditorContent(
                            entry = entry,
                            title = title,
                            onTitleChange = { title = it },
                            body = body,
                            onBodyChange = { body = it },
                            date = date,
                            onDateChange = { picked ->
                                if (isNewEntry) onDraftDateChange(picked) else editedDate = picked
                            },
                            wordCount = wordCount,
                            isEditing = isEditing,
                            editorMode = editorMode,
                            onModeChange = { editorMode = it },
                            headerTint = headerTint,
                            focusTitle = focusTitle,
                            titleFocusRequester = titleFocusRequester,
                            showDiscard = showDiscard,
                            onKeepEditing = { showDiscard = false },
                            onDiscard = {
                                title = entry.title
                                body = TextFieldValue(entry.body)
                                editedDate = entry.date
                                showDiscard = false
                                if (isNewEntry) closeAnimated(onDismiss) else isEditing = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The card's own contents for the entry the editor is actually working on. */
@Composable
private fun ColumnScope.JournalEditorContent(
    entry: JournalEntry,
    title: String,
    onTitleChange: (String) -> Unit,
    body: TextFieldValue,
    onBodyChange: (TextFieldValue) -> Unit,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    wordCount: Int,
    isEditing: Boolean,
    editorMode: Int,
    onModeChange: (Int) -> Unit,
    headerTint: Color,
    focusTitle: Boolean,
    titleFocusRequester: FocusRequester,
    showDiscard: Boolean,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    val discardQuestion = stringResource(R.string.ed_journal_discard_question)
    val keepEditingDescription = stringResource(R.string.ed_journal_keep_editing)
    val discardDescription = stringResource(R.string.ed_journal_discard_changes)
    AnimatedContent(
        targetState = isEditing,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(tween(220)) { direction * it / 3 } + fadeIn(tween(170))) togetherWith
                (slideOutHorizontally(tween(180)) { -direction * it / 3 } + fadeOut(tween(130)))
        },
        label = "journal view edit transition",
    ) { editing ->
        if (editing) {
            JournalEditPane(
                title = title,
                onTitleChange = onTitleChange,
                body = body,
                onBodyChange = onBodyChange,
                date = date,
                onDateChange = onDateChange,
                wordCount = wordCount,
                mode = editorMode,
                onModeChange = onModeChange,
                headerTint = headerTint,
                focusTitle = focusTitle,
                titleFocusRequester = titleFocusRequester,
            )
        } else {
            JournalReadPane(
                entry = entry.copy(date = date),
                title = title,
                body = body.text,
            )
        }
    }

    AnimatedVisibility(
        visible = showDiscard,
        enter = fadeIn(tween(150)) + expandVertically(tween(180)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(CalinoColors.Ink).padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(discardQuestion, style = CalinoTypography.bodyMedium, color = CalinoColors.Panel, modifier = Modifier.weight(1f))
            TextButton(
                onClick = onKeepEditing,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = keepEditingDescription },
            ) { Text(stringResource(R.string.ed_journal_keep_editing_short), color = CalinoColors.Panel) }
            TextButton(
                onClick = onDiscard,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = discardDescription },
            ) { Text(stringResource(R.string.ed_journal_discard), color = CalinoColors.AccentSoft) }
        }
    }

    // The pill itself lives in the pill lane, outside this card; this
    // reserves the room it occupies over the card's tail.
    Spacer(Modifier.height(CalinoSpacing.PillClearance))
}

@Composable
private fun JournalEditPane(
    title: String,
    onTitleChange: (String) -> Unit,
    body: TextFieldValue,
    onBodyChange: (TextFieldValue) -> Unit,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    wordCount: Int,
    mode: Int,
    onModeChange: (Int) -> Unit,
    headerTint: Color,
    focusTitle: Boolean,
    titleFocusRequester: FocusRequester,
) {
    val titleDescription = stringResource(R.string.ed_journal_title_semantics)
    val bodyDescription = stringResource(R.string.ed_journal_body_semantics)
    val wordCountText = pluralStringResource(R.plurals.ed_journal_words, wordCount, wordCount)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 78.dp).background(headerTint).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalinoIcon(CalinoIcon.Note, tint = CalinoColors.Accent, modifier = Modifier.size(23.dp), contentDescription = null)
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.weight(1f).padding(start = 14.dp)
                    .then(if (focusTitle) Modifier.focusRequester(titleFocusRequester) else Modifier)
                    .semantics { contentDescription = titleDescription },
                textStyle = CalinoTypography.headlineSmall.copy(color = CalinoColors.Ink),
                maxLines = 2,
                decorationBox = { innerTextField ->
                    Box {
                        if (title.isBlank()) Text(stringResource(R.string.ed_journal_add_title), style = CalinoTypography.headlineSmall, color = CalinoColors.Ink3)
                        innerTextField()
                    }
                },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "editor-mode") {
                // The gaps are reserved by the arrangement, not by spacers: the
                // segmented control takes whatever width is left, so a spacer
                // measured after it would have nothing to occupy.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JournalDateChip(date = date, onDateChange = onDateChange)
                    Text(
                        wordCountText,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(CalinoColors.Panel)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        style = CalinoTypography.labelSmall,
                        color = CalinoColors.Ink3,
                    )
                    Spacer(Modifier.weight(1f))
                    CompactSegmentedControl(
                        options = listOf(stringResource(R.string.ed_journal_write), stringResource(R.string.ed_journal_preview)),
                        selectedIndex = mode,
                        onSelected = onModeChange,
                        semanticLabel = stringResource(R.string.ed_journal_editor_mode),
                        maxControlWidth = 168.dp,
                    )
                }
            }
            item(key = "formatting-toolbar") {
                JournalFormattingToolbar(
                    value = body,
                    enabled = mode == 0,
                    onValueChange = onBodyChange,
                )
            }
            item(key = "editor-body") {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 260.dp)
                        .clip(RoundedCornerShape(CalinoShapes.Card)).background(CalinoColors.Panel)
                        .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card))
                        .padding(18.dp),
                ) {
                    if (mode == 0) {
                        BasicTextField(
                            value = body,
                            onValueChange = onBodyChange,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 224.dp)
                                .semantics { contentDescription = bodyDescription },
                            textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink, lineHeight = 25.sp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (body.text.isBlank()) Text(stringResource(R.string.ed_journal_body_prompt), style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3)
                                    innerTextField()
                                }
                            },
                        )
                    } else if (body.text.isBlank()) {
                        Text(stringResource(R.string.ed_journal_no_note_text), style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3)
                    } else {
                        CalinoMarkdown(body.text)
                    }
                }
            }
        }
    }
}

/**
 * The entry's day, opening the shared date picker.
 *
 * Compact on purpose: it shares its line with the word count and the
 * Write/Preview control, so it states the day the way the list cards do and
 * leaves the full date to the semantics.
 */
@Composable
private fun JournalDateChip(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val pickDate = rememberDatePicker({ date }, onDateChange)
    val dateFormat = localizedDateFormatter("EEE, d MMM")
    val fullDateFormat = localizedDateFormatter("EEEE d MMMM yyyy")
    val description = stringResource(R.string.ed_journal_date_description, date.format(fullDateFormat))
    Box(
        Modifier.heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = pickDate)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalinoIcon(CalinoIcon.Calendar, tint = CalinoColors.Ink3, modifier = Modifier.size(16.dp), contentDescription = null)
            Text(
                date.format(dateFormat),
                modifier = Modifier.padding(start = 7.dp),
                style = CalinoTypography.labelSmall,
                color = CalinoColors.Ink2,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun JournalReadPane(
    entry: JournalEntry,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    // Only the entry the card opened on answers to "Journal title": a pager
    // keeps its neighbours composed, and two nodes wearing the same label
    // make the title ambiguous to a screen reader and to the tests.
    labelTitle: Boolean = true,
) {
    val fullDateFormat = localizedDateFormatter("EEEE d MMMM yyyy")
    val titleDescription = stringResource(R.string.ed_journal_title_semantics)
    val untitledNote = stringResource(R.string.ed_journal_untitled_note)
    val wordCount = remember(body) { body.trim().let { if (it.isEmpty()) 0 else it.split(WordBoundaryPattern).size } }
    val readingMinutes = maxOf(1, (wordCount + 199) / 200)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "read-date") {
            Text(entry.date.format(fullDateFormat).uppercase(LocalCalinoLocale), style = CalinoTypography.labelSmall, color = CalinoColors.Accent)
        }
        item(key = "read-title") {
            Text(
                title.ifBlank { untitledNote },
                style = CalinoTypography.headlineLarge,
                color = if (title.isBlank()) CalinoColors.Ink3 else CalinoColors.Ink,
                modifier = if (labelTitle) Modifier.semantics { contentDescription = titleDescription } else Modifier,
            )
        }
        if (wordCount > 0) {
            item(key = "read-meta") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.ed_journal_words, wordCount, wordCount).uppercase(LocalCalinoLocale), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
                    Box(Modifier.padding(horizontal = 8.dp).size(3.dp).clip(CircleShape).background(CalinoColors.Ink3))
                    Text(pluralStringResource(R.plurals.ed_journal_minutes_read, readingMinutes, readingMinutes).uppercase(LocalCalinoLocale), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
                }
            }
        }
        item(key = "read-rule") { Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line)) }
        item(key = "read-preview") {
            if (body.isBlank()) Text(stringResource(R.string.ed_journal_no_note_text), style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3)
            else CalinoMarkdown(body)
        }
    }
}

private enum class JournalFormat(val descriptionResId: Int, val prefix: String? = null) {
    Heading(R.string.ed_journal_format_heading, "## "), Bold(R.string.ed_journal_format_bold), Italic(R.string.ed_journal_format_italic), Bullet(R.string.ed_journal_format_bullet, "- "), Checklist(R.string.ed_journal_format_checklist, "- [ ] "), Quote(R.string.ed_journal_format_quote, "> ")
}

@Composable
private fun JournalFormattingToolbar(value: TextFieldValue, enabled: Boolean, onValueChange: (TextFieldValue) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CalinoShapes.Row)).background(CalinoColors.Side)
            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Row)).padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        JournalFormat.entries.forEach { format ->
            val active = journalFormatActive(value, format)
            val description = stringResource(format.descriptionResId)
            Box(
                Modifier.size(44.dp).alpha(if (enabled) 1f else .4f)
                    .semantics { contentDescription = description; if (!enabled) disabled() }
                    .then(if (enabled) Modifier.clickable { onValueChange(applyJournalFormat(value, format)) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(40.dp).height(34.dp).clip(RoundedCornerShape(9.dp))
                        .then(if (active) Modifier.background(CalinoColors.Panel).border(1.dp, CalinoColors.Line, RoundedCornerShape(9.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    when (format) {
                        JournalFormat.Heading -> Text("H", style = CalinoTypography.titleSmall, color = CalinoColors.Ink)
                        JournalFormat.Bold -> Text("B", style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = CalinoColors.Ink)
                        JournalFormat.Italic -> Text("I", style = CalinoTypography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = CalinoColors.Ink)
                        JournalFormat.Bullet -> androidx.compose.material3.Icon(CalinoIcons.AgendaList, tint = CalinoColors.Ink2, modifier = Modifier.size(17.dp), contentDescription = null)
                        JournalFormat.Checklist -> androidx.compose.material3.Icon(CalinoIcons.ListChecks, tint = CalinoColors.Ink2, modifier = Modifier.size(17.dp), contentDescription = null)
                        JournalFormat.Quote -> Text("“", style = CalinoTypography.titleSmall, color = CalinoColors.Ink)
                    }
                }
            }
        }
    }
}

private fun journalFormatActive(value: TextFieldValue, format: JournalFormat): Boolean {
    val selection = value.selection
    return when (format) {
        JournalFormat.Bold -> selection.length > 0 && selection.min >= 2 && selection.max + 2 <= value.text.length &&
            value.text.substring(selection.min - 2, selection.min) == "**" && value.text.substring(selection.max, selection.max + 2) == "**"
        JournalFormat.Italic -> selection.length > 0 && selection.min >= 1 && selection.max < value.text.length &&
            value.text[selection.min - 1] == '*' && value.text[selection.max] == '*'
        else -> touchedLines(value).all { value.text.substring(it.first, it.second).startsWith(format.prefix!!) }
    }
}

private fun applyJournalFormat(value: TextFieldValue, format: JournalFormat): TextFieldValue = when (format) {
    JournalFormat.Bold -> wrapJournalSelection(value, "**")
    JournalFormat.Italic -> wrapJournalSelection(value, "*")
    else -> toggleJournalLinePrefix(value, format.prefix!!)
}

private fun wrapJournalSelection(value: TextFieldValue, marker: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val selected = value.text.substring(start, end)
    val replacement = marker + selected + marker
    val text = value.text.replaceRange(start, end, replacement)
    val selection = if (start == end) TextRange(start + marker.length) else TextRange(start + marker.length, end + marker.length)
    return TextFieldValue(text, selection)
}

private fun toggleJournalLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val ranges = touchedLines(value)
    val remove = ranges.all { value.text.substring(it.first, it.second).startsWith(prefix) }
    var text = value.text
    var adjustment = 0
    ranges.forEach { range ->
        val position = range.first + adjustment
        if (remove) {
            text = text.removeRange(position, position + prefix.length)
            adjustment -= prefix.length
        } else {
            text = text.substring(0, position) + prefix + text.substring(position)
            adjustment += prefix.length
        }
    }
    val originalStart = value.selection.min
    val originalEnd = value.selection.max
    val deltaBeforeStart = ranges.count { it.first <= originalStart } * prefix.length * if (remove) -1 else 1
    val deltaBeforeEnd = ranges.count { it.first <= originalEnd } * prefix.length * if (remove) -1 else 1
    return TextFieldValue(text, TextRange((originalStart + deltaBeforeStart).coerceIn(0, text.length), (originalEnd + deltaBeforeEnd).coerceIn(0, text.length)))
}

private fun touchedLines(value: TextFieldValue): List<Pair<Int, Int>> {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val selectionEnd = value.selection.max.coerceIn(start, value.text.length)
    val effectiveEnd = if (selectionEnd > start && value.text.getOrNull(selectionEnd - 1) == '\n') selectionEnd - 1 else selectionEnd
    val lineStart = value.text.lastIndexOf('\n', maxOf(0, start - 1)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = value.text.indexOf('\n', effectiveEnd).let { if (it < 0) value.text.length else it }
    val ranges = mutableListOf<Pair<Int, Int>>()
    var cursor = lineStart
    while (cursor <= lineEnd) {
        val end = value.text.indexOf('\n', cursor).let { if (it < 0 || it > lineEnd) lineEnd else it }
        ranges += cursor to end
        if (end >= lineEnd) break
        cursor = end + 1
    }
    return ranges
}
