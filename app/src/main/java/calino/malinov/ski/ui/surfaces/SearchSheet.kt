package calino.malinov.ski.ui.surfaces

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import calino.malinov.ski.data.model.placementDate
import calino.malinov.ski.data.model.derivedDisplayName
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.search.*
import calino.malinov.ski.design.*
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.SwipeDownDismiss
import calino.malinov.ski.ui.components.rememberDatePicker
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.SideEffect
import calino.malinov.ski.ui.components.LocalCalinoPillLane
import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.state.LocalHingeOpenness
import calino.malinov.ski.state.foldSplitProgress
import calino.malinov.ski.state.CalinoSurfaceMode
import calino.malinov.ski.state.calinoSurfaceModeFor
import calino.malinov.ski.state.calinoWindowClassFor
import calino.malinov.ski.state.LocalFoldPosture
import calino.malinov.ski.state.calinoLayoutSpec
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.data.repository.SyncState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

private val SearchDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/** Floating capsule which grows from, and reverses back into, the add pill. */
@Composable
fun CalinoSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    snapshot: CalinoSnapshot,
    baseDate: LocalDate,
    journalsEnabled: Boolean,
    searchRecords: suspend (
        query: String,
        journalsEnabled: Boolean,
        contactsEnabled: Boolean,
    ) -> Set<String>?,
    onSelect: (CalinoSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var closeRequest by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val contactsEnabled = LocalCalinoPreferences.current.contactsEnabled
    var filtersVisible by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(CalinoSearchOptions()) }
    var candidateQuery by remember { mutableStateOf<String?>(null) }
    var candidateSnapshot by remember { mutableStateOf<CalinoSnapshot?>(null) }
    var candidateRevision by remember { mutableLongStateOf(-1L) }
    var candidateIds by remember { mutableStateOf<Set<String>?>(null) }
    var candidateLoading by remember { mutableStateOf(false) }
    LaunchedEffect(query, snapshot, baseDate, journalsEnabled, contactsEnabled, options) {
        candidateLoading = true
        candidateQuery = query
        candidateSnapshot = snapshot
        candidateRevision = snapshot.revision
        candidateIds = if (query.isBlank()) {
            emptySet()
        } else {
            val indexed = searchRecords(query, journalsEnabled, contactsEnabled)
            if (indexed == null) {
                null
            } else {
                val indexedMatches = searchCalino(
                    snapshot = snapshot,
                    query = query,
                    baseDate = baseDate,
                    contactsEnabled = contactsEnabled,
                    journalsEnabled = journalsEnabled,
                    options = options,
                    candidateRecordIds = indexed,
                )
                val sparseTypes = buildSet {
                    if (CalinoSearchRecordType.Events in options.recordTypes && indexedMatches.events.size < 8) add(CalinoSearchRecordType.Events)
                    if (CalinoSearchRecordType.Tasks in options.recordTypes && indexedMatches.tasks.size < 8) add(CalinoSearchRecordType.Tasks)
                    if (journalsEnabled && CalinoSearchRecordType.Journal in options.recordTypes && indexedMatches.journals.size < 8) add(CalinoSearchRecordType.Journal)
                    if (contactsEnabled && CalinoSearchRecordType.Contacts in options.recordTypes && indexedMatches.contacts.size < 8) add(CalinoSearchRecordType.Contacts)
                }
                val fallback = if (sparseTypes.isNotEmpty()) {
                    searchCalino(
                        snapshot = snapshot,
                        query = query,
                        baseDate = baseDate,
                        contactsEnabled = contactsEnabled,
                        journalsEnabled = journalsEnabled,
                        options = options.copy(recordTypes = sparseTypes),
                    )
                } else {
                    CalinoSearchGroups()
                }
                indexedMatches.withSparseSearchFallback(fallback, sparseTypes)
            }
        }
        candidateLoading = false
    }
    val candidatesReady = !candidateLoading && candidateQuery == query && candidateSnapshot == snapshot &&
        candidateRevision == snapshot.revision
    val results = remember(query, snapshot, baseDate, contactsEnabled, journalsEnabled, options, candidateIds, candidatesReady) {
        searchCalino(
            snapshot = snapshot,
            query = query,
            baseDate = baseDate,
            contactsEnabled = contactsEnabled,
            journalsEnabled = journalsEnabled,
            options = options,
            // Keep current-snapshot fuzzy results visible while the private
            // index answers asynchronously. Replacing them with an empty set
            // made search appear blank between keystrokes.
            candidateRecordIds = if (candidatesReady) candidateIds else null,
        )
    }
    val duration = CalinoMotion.SurfaceFadeMillis
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    fun requestClose() { closeRequest += 1 }

    var windowShown by remember { mutableStateOf(false) }
    // Grow only once the dialog window is on screen: expanding on first
    // composition finishes before the window is drawn, so the capsule would
    // appear already open instead of growing out of the pill.
    LaunchedEffect(windowShown) {
        if (!windowShown) return@LaunchedEffect
        expanded = true
        delay(duration.toLong())
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(closeRequest) {
        if (closeRequest > 0) {
            expanded = false
            keyboard?.hide()
            delay(duration.toLong())
            onDismiss()
        }
    }
    PredictiveBackHandler { events ->
        try {
            events.collect { predictiveBackProgress = it.progress.coerceIn(0f, 1f) }
            predictiveBackProgress = 1f
            requestClose()
        } catch (cancelled: CancellationException) {
            animate(
                predictiveBackProgress,
                0f,
                animationSpec = CalinoMotion.gestureReturn(),
            ) { value, _ -> predictiveBackProgress = value }
            throw cancelled
        }
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
    // The capsule and scrim animate themselves; the window's own fade would
    // hide the capsule growing out of the pill behind a crossfade.
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    // The window's own dim stacks with the scrim below and turned the glass
    // grey; the scrim alone is enough.
    SideEffect {
        dialogWindow?.setWindowAnimations(0)
        dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
    val dialogView = LocalView.current
    LaunchedEffect(dialogView) {
        while (dialogView.windowVisibility != android.view.View.VISIBLE) withFrameNanos { }
        repeat(2) { withFrameNanos { } }
        windowShown = true
    }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
        val layoutSpec = calinoLayoutSpec(
            maxWidth.value.roundToInt(), maxHeight.value.roundToInt(), LocalFoldPosture.current,
        )
        val searchPane = layoutSpec.preferredTransientPane
        val paneWidth = searchPane.widthDp.dp.coerceAtLeast(1.dp)
        val paneHeight = searchPane.heightDp.dp.coerceAtLeast(1.dp)
        val mode = calinoSurfaceModeFor(
            layoutSpec.windowClass,
            CalinoSurfaceKind.Search,
            layoutSpec.splitPanes,
        )
        val compact = mode == CalinoSurfaceMode.BottomSheet
        val resultCount = groupsCount(results)
        val filterHeight = if (filtersVisible) 214 else 0
        val expandedHeight = if (query.isBlank() && !filtersVisible) 190.dp else minOf(680.dp, (150 + filterHeight + resultCount * 66).dp)
        val expandedWidth = if (compact) {
            (paneWidth - 24.dp).coerceAtLeast(1.dp)
        } else {
            minOf((paneWidth - 48.dp).coerceAtLeast(1.dp), CalinoSurfaceKind.Search.widthCapDp.dp)
        }
        val expandedHeightTarget = if (compact) {
            minOf(paneHeight * .72f, expandedHeight)
        } else {
            minOf((paneHeight - 48.dp).coerceAtLeast(1.dp), expandedHeight, CalinoSurfaceKind.Search.heightCapDp.dp)
        }
        // On a phone the capsule is the add pill grown upward: it starts on
        // the pill's own rect and keeps its side and bottom edges, so search
        // reads as the same object rather than a second bar beside it. The
        // keyboard can still push the bottom up; imePadding shrinks maxHeight.
        val density = LocalDensity.current
        val pillRect = LocalCalinoPillLane.current.addPillBounds
            ?.takeIf { compact }
            ?.let { with(density) { DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) } }
        val collapsedWidth = pillRect?.let { it.right - it.left } ?: 224.dp
        val collapsedHeight = pillRect?.let { it.bottom - it.top } ?: 54.dp
        // Open, it shares the pill's edges when the pill is wide (the dock);
        // a narrow rest pill would squeeze the field, so it widens around the
        // pill's centre instead, never past the sheet's own width.
        val openWidth = pillRect?.let { minOf(maxOf(it.right - it.left, 320.dp), expandedWidth) } ?: expandedWidth
        val targetWidth = if (expanded) openWidth else collapsedWidth
        val capsuleWidth by animateDpAsState(targetWidth, tween(duration), label = "search capsule width")
        val capsuleHeight by animateDpAsState(if (expanded) expandedHeightTarget else collapsedHeight, tween(duration), label = "search capsule height")
        val capsuleRadius by animateDpAsState(if (expanded) 30.dp else CalinoShapes.Pill, tween(duration), label = "search capsule radius")
        // The same glass as the pill it grows from: a blurred backdrop under
        // the pill's wash and outline. The dialog is its own window and can't
        // sample the app's layer, so the blur is the window's blur-behind,
        // raised with the capsule. Without cross-window blur (before API 31,
        // or turned off) the wash stays opaque so results remain legible.
        val blurProgress by animateFloatAsState(if (expanded) 1f else 0f, tween(duration), label = "search blur")
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            dialogWindow?.windowManager?.isCrossWindowBlurEnabled == true
        if (canBlur) {
            SideEffect {
                dialogWindow?.let { window ->
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply { blurBehindRadius = (SearchBlurRadiusPx * blurProgress).roundToInt() }
                }
            }
        }
        // It leaves the pill in the pill's fill and settles into the page's
        // panel: in light mode the pill is a dark lozenge, and a whole dark
        // search sheet on paper reads as a different theme.
        // Open, it is a reading surface: denser than the pill's glass so the
        // panel reads as the page, with only a hint of the blur behind it.
        val capsuleFill by animateColorAsState(
            when {
                !canBlur -> if (expanded) CalinoColors.Panel else CalinoColors.FloatFill
                expanded -> CalinoColors.Panel.copy(alpha = .9f)
                else -> CalinoColors.FloatFill.copy(alpha = .68f)
            },
            tween(duration),
            label = "search capsule fill",
        )

        AnimatedVisibility(expanded, enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 1f - predictiveBackProgress }
                    .background(CalinoColors.scrim(.18f))
                    // No indication: a full-screen ripple reads as a second scrim.
                    .clickable(interactionSource = null, indication = null, onClick = ::requestClose),
            )
        }
        val searchContent: @Composable (Modifier) -> Unit = { dragModifier ->
            Surface(
                modifier = dragModifier
                    .width(capsuleWidth)
                    .height(capsuleHeight)
                    .graphicsLayer {
                        translationY = size.height * .09f * predictiveBackProgress
                        val predictiveScale = 1f - .06f * predictiveBackProgress
                        scaleX = predictiveScale
                        scaleY = predictiveScale
                        alpha = 1f - .14f * predictiveBackProgress
                    }
                    .clickable(onClick = {}),
                shape = RoundedCornerShape(capsuleRadius),
                color = capsuleFill,
                border = BorderStroke(1.dp, CalinoColors.FloatBorder),
                // A shadow shows through translucent glass as a dark slab.
                shadowElevation = if (CalinoColors.elevationAlpha > 0f && !canBlur) 14.dp else 0.dp,
            ) {
                AnimatedVisibility(expanded, enter = fadeIn(tween(duration, delayMillis = 70)), exit = fadeOut(tween(90))) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = query,
                                onValueChange = onQueryChange,
                                modifier = Modifier.weight(1f).focusRequester(focusRequester).semantics { contentDescription = "Search Calino" },
                                placeholder = { Text("Search, go to a date, or add an event…") },
                                leadingIcon = { Icon(CalinoIcons.Search, contentDescription = null) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = CalinoColors.Accent,
                                    unfocusedIndicatorColor = CalinoColors.Line,
                                ),
                            )
                            IconButton(
                                onClick = { filtersVisible = !filtersVisible },
                                modifier = Modifier.semantics {
                                    contentDescription = if (filtersVisible) "Hide search filters" else "Show search filters"
                                },
                            ) {
                                Icon(
                                    CalinoIcons.Filter,
                                    contentDescription = null,
                                    tint = if (options != CalinoSearchOptions()) CalinoColors.Accent else CalinoColors.Ink2,
                                )
                            }
                            Text(
                                "×",
                                style = CalinoTypography.headlineMedium,
                                color = CalinoColors.Ink2,
                                modifier = Modifier.size(44.dp).clickable(onClick = ::requestClose).padding(8.dp)
                                    .semantics { contentDescription = "Close search" },
                            )
                        }
                        AnimatedVisibility(
                            visible = filtersVisible,
                            enter = expandVertically(tween(CalinoMotion.ContentEnterMillis)) + fadeIn(tween(CalinoMotion.FadeThroughMillis)),
                            exit = shrinkVertically(tween(CalinoMotion.ContentExitMillis)) + fadeOut(tween(CalinoMotion.FadeThroughMillis)),
                        ) {
                            SearchFilters(
                                options = options,
                                calendars = snapshot.calendars.map { it.id to it.name },
                                baseDate = baseDate,
                                contactsEnabled = contactsEnabled,
                                journalsEnabled = journalsEnabled,
                                downloadedOnly = snapshot.sync !is SyncState.Idle,
                                onChange = { options = it },
                            )
                        }
                        SearchResults(results, query, onSelect)
                    }
                }
            }
        }
        if (compact) {
            // The pane offset belongs to the gesture container alone.
            // SwipeDownDismiss also hands its modifier to the content, so an
            // offset passed in there is applied twice and parks the capsule a
            // whole pane below the screen -- leaving only the scrim on screen.
            if (pillRect != null) {
                val bottom = minOf(pillRect.bottom, maxHeight - 20.dp)
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = ((pillRect.left + pillRect.right) / 2 - capsuleWidth / 2)
                                .coerceIn(12.dp, (maxWidth - capsuleWidth - 12.dp).coerceAtLeast(12.dp)),
                            y = (bottom - capsuleHeight).coerceAtLeast(0.dp),
                        ),
                ) {
                    SwipeDownDismiss(visible = expanded, onDismiss = ::requestClose, content = searchContent)
                }
            } else Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = searchPane.leftDp.dp, y = searchPane.topDp.dp + (paneHeight - capsuleHeight - 20.dp).coerceAtLeast(0.dp))
                    .width(paneWidth),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SwipeDownDismiss(
                    visible = expanded,
                    onDismiss = ::requestClose,
                    modifier = Modifier.padding(bottom = 20.dp),
                    content = searchContent,
                )
            }
        } else {
            AnimatedVisibility(
                visible = expanded,
                enter = scaleIn(tween(duration), initialScale = .94f) + fadeIn(tween(duration)),
                exit = scaleOut(tween(duration), targetScale = .94f) + fadeOut(tween(duration)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = searchPane.leftDp.dp + (paneWidth - capsuleWidth) / 2f,
                        y = searchPane.topDp.dp + (paneHeight - capsuleHeight) / 2f,
                    ),
            ) {
                SwipeDownDismiss(
                    visible = expanded,
                    onDismiss = ::requestClose,
                    content = searchContent,
                )
            }
        }
    }
    }
}

@Composable
private fun SearchFilters(
    options: CalinoSearchOptions,
    calendars: List<Pair<String, String>>,
    baseDate: LocalDate,
    contactsEnabled: Boolean,
    journalsEnabled: Boolean,
    downloadedOnly: Boolean,
    onChange: (CalinoSearchOptions) -> Unit,
) {
    val pickStart = rememberDatePicker({ options.customStart ?: baseDate }) { picked ->
        onChange(options.copy(dateMode = CalinoSearchDateMode.Custom, customStart = picked, customEnd = options.customEnd?.coerceAtLeast(picked) ?: picked))
    }
    val pickEnd = rememberDatePicker({ options.customEnd ?: options.customStart ?: baseDate }) { picked ->
        onChange(options.copy(dateMode = CalinoSearchDateMode.Custom, customStart = options.customStart?.coerceAtMost(picked) ?: picked, customEnd = picked))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SearchFilterLabel("TYPE")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalinoSearchRecordType.entries.filter {
                (contactsEnabled || it != CalinoSearchRecordType.Contacts) &&
                    (journalsEnabled || it != CalinoSearchRecordType.Journal)
            }.forEach { type ->
                CalinoChip(type.name, type in options.recordTypes, "filter search by ${type.name.lowercase()}", {
                    onChange(options.copy(recordTypes = if (type in options.recordTypes) options.recordTypes - type else options.recordTypes + type))
                }, Modifier.heightIn(min = 44.dp))
            }
        }
        SearchFilterLabel("CALENDAR · EVENTS & TASKS")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalinoChip("All", options.calendarIds.isEmpty(), "search every calendar", { onChange(options.copy(calendarIds = emptySet())) }, Modifier.heightIn(min = 44.dp))
            calendars.forEach { (id, name) ->
                CalinoChip(name, id in options.calendarIds, "filter events and tasks by $name", {
                    val selected = if (id in options.calendarIds) options.calendarIds - id else options.calendarIds + id
                    onChange(options.copy(calendarIds = selected))
                }, Modifier.heightIn(min = 44.dp))
            }
        }
        SearchFilterLabel("DATE")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                CalinoSearchDateMode.AnyTime to "Any time",
                CalinoSearchDateMode.Past to "Past",
                CalinoSearchDateMode.Upcoming to "Upcoming",
            ).forEach { (mode, label) ->
                CalinoChip(label, options.dateMode == mode, "filter search by ${label.lowercase()}", { onChange(options.copy(dateMode = mode)) }, Modifier.heightIn(min = 44.dp))
            }
            CalinoChip("Custom", options.dateMode == CalinoSearchDateMode.Custom, "choose a custom date range", pickStart, Modifier.heightIn(min = 44.dp))
            if (options.dateMode == CalinoSearchDateMode.Custom) {
                CalinoChip(options.customStart?.format(SearchDateFormat) ?: "Start", false, "choose range start", pickStart, Modifier.heightIn(min = 44.dp))
                CalinoChip(options.customEnd?.format(SearchDateFormat) ?: "End", false, "choose range end", pickEnd, Modifier.heightIn(min = 44.dp))
            }
        }
        if (downloadedOnly) {
            Text("Search covers downloaded data.", style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
        }
    }
}

@Composable
private fun SearchFilterLabel(text: String) {
    Text(text, style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 2.dp))
}

private fun groupsCount(groups: CalinoSearchGroups): Int =
    groups.actions.size + groups.events.size + groups.tasks.size + groups.journals.size + groups.contacts.size

@Composable
private fun SearchResults(groups: CalinoSearchGroups, query: String, onSelect: (CalinoSearchResult) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(top = 4.dp)) {
        if (query.isBlank()) item { SearchMessage("Find events, tasks and journal entries — or type a date.") }
        else {
            resultGroup("ACTIONS", groups.actions, onSelect)
            if (!groups.hasRecordMatches) item { SearchMessage("No matching records for “${query.trim()}”") }
            resultGroup("EVENTS", groups.events, onSelect)
            resultGroup("TASKS", groups.tasks, onSelect)
            resultGroup("JOURNAL", groups.journals, onSelect)
            resultGroup("CONTACTS", groups.contacts, onSelect)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultGroup(heading: String, results: List<CalinoSearchResult>, onSelect: (CalinoSearchResult) -> Unit) {
    if (results.isEmpty()) return
    item(heading) { Text(heading, style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
    items(results, key = { it.stableId }) { SearchResultRow(it, onSelect) }
}

@Composable
private fun SearchResultRow(result: CalinoSearchResult, onSelect: (CalinoSearchResult) -> Unit) {
    val (title, detail) = when (result) {
        is CalinoSearchResult.NavigateDate -> "Go to ${result.date.format(SearchDateFormat)}" to "Open Month on this date"
        is CalinoSearchResult.AddEvent -> "Add event · ${result.parsed.title}" to parsedDetail(result)
        is CalinoSearchResult.Event -> result.event.title to buildString {
            append(result.event.placementDate()?.format(SearchDateFormat) ?: "No date")
            result.event.start?.let { append(" · ${it.toLocalTime()}") }
            result.event.location?.let { append(" · $it") }
            result.calendarName?.let { append(" · $it") }
        }
        is CalinoSearchResult.Task -> result.task.title to buildString {
            append(result.task.due?.format(SearchDateFormat) ?: "No due date")
            result.task.category?.let { append(" · $it") }
        }
        is CalinoSearchResult.Journal -> result.journal.title.ifBlank { "Untitled note" } to result.journal.date.format(SearchDateFormat)
        is CalinoSearchResult.Contact -> result.contact.derivedDisplayName() to
            (result.contact.organization.ifBlank { result.contact.emails.firstOrNull()?.value ?: "Contact" })
    }
    Row(
        Modifier.fillMaxWidth().testTag(result.searchTestTag()).clickable { onSelect(result) }.semantics { contentDescription = "$title, $detail" }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(resultColor(result)))
        Column(Modifier.weight(1f)) {
            Text(title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = CalinoTypography.bodySmall, color = CalinoColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Keep the existing record-id tags used by UI tests; AppSearch uses hashed IDs internally. */
private fun CalinoSearchResult.searchTestTag(): String = when (this) {
    is CalinoSearchResult.NavigateDate -> stableId
    is CalinoSearchResult.AddEvent -> stableId
    is CalinoSearchResult.Event -> "event:${event.id}"
    is CalinoSearchResult.Task -> "task:${task.id}"
    is CalinoSearchResult.Journal -> "journal:${journal.id}"
    is CalinoSearchResult.Contact -> "contact:${contact.id}"
}

@Composable private fun resultColor(result: CalinoSearchResult): Color = when (result) {
    is CalinoSearchResult.Event -> CalinoColors.forEvent(Color(result.event.color))
    is CalinoSearchResult.Task -> CalinoColors.forEvent(Color(result.task.color))
    else -> CalinoColors.Accent
}

private fun parsedDetail(result: CalinoSearchResult.AddEvent): String = buildString {
    append(result.parsed.date.format(SearchDateFormat))
    result.parsed.time?.let { append(" · $it") }
    result.parsed.durationMinutes?.let { append(" · ${it}m") }
    result.parsed.recurrence?.let { append(" · Repeats") }
}

@Composable private fun SearchMessage(text: String) {
    Text(text, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(24.dp))
}

/** Matches the pill's own 24px backdrop blur. */
private const val SearchBlurRadiusPx = 24
