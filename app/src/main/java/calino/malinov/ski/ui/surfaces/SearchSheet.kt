package calino.malinov.ski.ui.surfaces

import calino.malinov.ski.R
import androidx.compose.ui.res.stringResource

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import calino.malinov.ski.ui.components.calinoPressable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.data.repository.SyncState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import calino.malinov.ski.util.localizedDateFormatter
import calino.malinov.ski.util.LocalCalinoLocale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect


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
    recentSearches: List<String> = emptyList(),
    onRememberSearch: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var closeRequest by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val contactsEnabled = LocalCalinoPreferences.current.contactsEnabled
    var filtersVisible by remember { mutableStateOf(false) }
    // Owned here so the dismiss gesture can ask whether the list is at its top:
    // a downward drag over a scrolled list has to scroll it back, not close the sheet.
    val resultsState = rememberLazyListState()
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
        val filterHeight = if (filtersVisible) 234 else 0
        val expandedHeight = when {
            // Header 70 + jump chips 76, then the recents (or, with none, the hint).
            query.isBlank() && !filtersVisible ->
                (if (recentSearches.isEmpty()) 200 else 214 + 44 * recentSearches.size).dp
            query.isBlank() -> (80 + filterHeight).dp
            else -> minOf(680.dp, (150 + filterHeight + resultCount * 66).dp)
        }
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
        val resultsAtTop = { !resultsState.canScrollBackward }
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
                        // An offscreen layer would clip the shadow to the card's bounds.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
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
                        SearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                            filtersVisible = filtersVisible,
                            filtersActive = options != CalinoSearchOptions(),
                            onToggleFilters = { filtersVisible = !filtersVisible },
                            focusRequester = focusRequester,
                        )
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
                        SearchResults(
                            state = resultsState,
                            groups = results,
                            query = query,
                            showInitial = !filtersVisible,
                            baseDate = baseDate,
                            recents = recentSearches,
                            onQueryChange = onQueryChange,
                            onClearRecents = onClearRecentSearches,
                            onSelect = { result ->
                                // A query is worth remembering once it led somewhere; dates
                                // and add-event drafts are derived from what was typed.
                                if (result !is CalinoSearchResult.NavigateDate && result !is CalinoSearchResult.AddEvent) {
                                    onRememberSearch(query)
                                }
                                onSelect(result)
                            },
                        )
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
                    SwipeDownDismiss(visible = expanded, onDismiss = ::requestClose, canStartDismiss = resultsAtTop, content = searchContent)
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
                    canStartDismiss = resultsAtTop,
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
                    canStartDismiss = resultsAtTop,
                    content = searchContent,
                )
            }
        }
    }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    filtersVisible: Boolean,
    filtersActive: Boolean,
    onToggleFilters: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || query.isNotEmpty()
    val borderColor by animateColorAsState(if (active) CalinoColors.Accent else CalinoColors.Line, tween(CalinoMotion.ContentEnterMillis), label = "search field border")
    val borderWidth by animateDpAsState(if (active) 1.5.dp else 1.dp, tween(CalinoMotion.ContentEnterMillis), label = "search field border width")
    val filterFill by animateColorAsState(if (filtersVisible) CalinoColors.AccentSoft else Color.Transparent, tween(CalinoMotion.ContentEnterMillis), label = "filter fill")
    val fieldShape = RoundedCornerShape(CalinoShapes.Pill)
    val searchDescription = stringResource(R.string.cal_search_calino)
    val filterDescription = stringResource(if (filtersVisible) R.string.cal_hide_search_filters else R.string.cal_show_search_filters)
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(48.dp)
                .clip(fieldShape)
                .background(CalinoColors.Side)
                .border(borderWidth, borderColor, fieldShape)
                .clickable(interactionSource = null, indication = null) { focusRequester.requestFocus() }
                .padding(start = 16.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(CalinoIcons.Search, contentDescription = null, tint = CalinoColors.Ink3, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.cal_search_or_jump), style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = searchDescription },
                    singleLine = true,
                    textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink),
                    cursorBrush = SolidColor(CalinoColors.Accent),
                )
            }
            // A 44dp lane around the 36dp circle, which still sits 6dp from the field's end.
            Box(
                Modifier.size(44.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onToggleFilters)
                    .semantics { contentDescription = filterDescription },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(filterFill), contentAlignment = Alignment.Center) {
                    Icon(
                        CalinoIcons.Filter,
                        contentDescription = null,
                        tint = if (filtersVisible || filtersActive) CalinoColors.Accent else CalinoColors.Ink2,
                        modifier = Modifier.size(20.dp),
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
    val locale = LocalCalinoLocale
    val searchDateFormat = localizedDateFormatter("EEE, d MMM yyyy")
    val pickStart = rememberDatePicker({ options.customStart ?: baseDate }) { picked ->
        onChange(options.copy(dateMode = CalinoSearchDateMode.Custom, customStart = picked, customEnd = options.customEnd?.coerceAtLeast(picked) ?: picked))
    }
    val pickEnd = rememberDatePicker({ options.customEnd ?: options.customStart ?: baseDate }) { picked ->
        onChange(options.copy(dateMode = CalinoSearchDateMode.Custom, customStart = options.customStart?.coerceAtMost(picked) ?: picked, customEnd = picked))
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SearchFilterLabel(stringResource(R.string.cal_search_type))
        SearchFilterRow {
            CalinoSearchRecordType.entries.filter {
                (contactsEnabled || it != CalinoSearchRecordType.Contacts) &&
                    (journalsEnabled || it != CalinoSearchRecordType.Journal)
            }.forEach { type ->
                val typeLabel = when (type) {
                    CalinoSearchRecordType.Events -> stringResource(R.string.cal_event_type)
                    CalinoSearchRecordType.Tasks -> stringResource(R.string.cal_task_type)
                    CalinoSearchRecordType.Journal -> stringResource(R.string.cal_journal_type)
                    CalinoSearchRecordType.Contacts -> stringResource(R.string.cal_contact_type)
                }
                SearchFilterChip(typeLabel, type in options.recordTypes, stringResource(R.string.cal_filter_by_type, typeLabel.lowercase(locale))) {
                    onChange(options.copy(recordTypes = if (type in options.recordTypes) options.recordTypes - type else options.recordTypes + type))
                }
            }
        }
        SearchFilterLabel(stringResource(R.string.cal_search_calendar))
        SearchFilterRow {
            SearchFilterChip(stringResource(R.string.cal_all), options.calendarIds.isEmpty(), stringResource(R.string.cal_search_every_calendar)) { onChange(options.copy(calendarIds = emptySet())) }
            calendars.forEach { (id, name) ->
                SearchFilterChip(name, id in options.calendarIds, stringResource(R.string.cal_filter_calendar, name)) {
                    val selected = if (id in options.calendarIds) options.calendarIds - id else options.calendarIds + id
                    onChange(options.copy(calendarIds = selected))
                }
            }
        }
        SearchFilterLabel(stringResource(R.string.cal_search_date))
        SearchFilterRow {
            listOf(
                CalinoSearchDateMode.AnyTime to stringResource(R.string.cal_any_time),
                CalinoSearchDateMode.Past to stringResource(R.string.cal_past),
                CalinoSearchDateMode.Upcoming to stringResource(R.string.cal_upcoming),
            ).forEach { (mode, label) ->
                SearchFilterChip(label, options.dateMode == mode, stringResource(R.string.cal_filter_by_type, label.lowercase(locale))) { onChange(options.copy(dateMode = mode)) }
            }
            SearchFilterChip(stringResource(R.string.cal_custom), options.dateMode == CalinoSearchDateMode.Custom, stringResource(R.string.cal_choose_custom_range), pickStart)
            if (options.dateMode == CalinoSearchDateMode.Custom) {
                SearchFilterChip(options.customStart?.format(searchDateFormat) ?: stringResource(R.string.cal_start), false, stringResource(R.string.cal_choose_range_start), pickStart)
                SearchFilterChip(options.customEnd?.format(searchDateFormat) ?: stringResource(R.string.cal_end), false, stringResource(R.string.cal_choose_range_end), pickEnd)
            }
        }
        if (downloadedOnly) {
            Text(
                stringResource(R.string.cal_search_downloaded_data),
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SearchFilterLabel(text: String) {
    SectionLabel(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
}

/** One line that scrolls sideways and is clipped at the sheet's edge, which is what says it scrolls. */
@Composable
private fun SearchFilterRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = CalinoColors.Ink3) {
    Text(text, style = CalinoTypography.labelSmall, color = color, modifier = modifier)
}

@Composable
private fun SearchFilterChip(text: String, selected: Boolean, description: String, onClick: () -> Unit) {
    val fade = tween<Color>(CalinoMotion.ContentEnterMillis)
    val fill by animateColorAsState(if (selected) CalinoColors.AccentSoft else CalinoColors.Side, fade, label = "filter chip fill")
    val edge by animateColorAsState(if (selected) CalinoColors.Accent else Color.Transparent, fade, label = "filter chip edge")
    val ink by animateColorAsState(if (selected) CalinoColors.Ink else CalinoColors.Ink2, fade, label = "filter chip ink")
    val shape = RoundedCornerShape(CalinoShapes.Pill)
    val chipDescription = stringResource(R.string.cal_search_selected, text, description)
    val stateLabel = stringResource(if (selected) R.string.cal_selected_state else R.string.cal_not_selected_state)
    Box(
        Modifier.height(44.dp)
            .calinoPressable(onClick = onClick)
            .semantics {
                contentDescription = chipDescription
                stateDescription = stateLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.height(40.dp).clip(shape).background(fill).border(1.dp, edge, shape).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = CalinoTypography.labelMedium, color = ink, maxLines = 1, softWrap = false)
        }
    }
}

private fun groupsCount(groups: CalinoSearchGroups): Int =
    groups.actions.size + groups.events.size + groups.tasks.size + groups.journals.size + groups.contacts.size

@Composable
private fun SearchResults(
    state: LazyListState,
    groups: CalinoSearchGroups,
    query: String,
    showInitial: Boolean,
    baseDate: LocalDate,
    recents: List<String>,
    onQueryChange: (String) -> Unit,
    onClearRecents: () -> Unit,
    onSelect: (CalinoSearchResult) -> Unit,
) {
    val actionsHeading = stringResource(R.string.cal_search_group_actions)
    val eventsHeading = stringResource(R.string.cal_search_group_events)
    val tasksHeading = stringResource(R.string.cal_search_group_tasks)
    val journalHeading = stringResource(R.string.cal_search_group_journal)
    val contactsHeading = stringResource(R.string.cal_search_group_contacts)
    LazyColumn(
        modifier = Modifier.fillMaxWidth().then(if (query.isBlank()) Modifier else Modifier.heightIn(min = 160.dp)).padding(top = 4.dp),
        state = state,
    ) {
        if (query.isBlank()) {
            // With filters open the groups above are the whole story.
            if (showInitial) {
                item("jump") { SearchJumpTo(baseDate, onSelect) }
                if (recents.isNotEmpty()) {
                    item("recent-header") { SearchRecentsHeader(onClearRecents) }
                    items(recents, key = { "recent:$it" }) { recent -> SearchRecentRow(recent) { onQueryChange(recent) } }
                } else {
                    item("hint") {
                        Text(
                            stringResource(R.string.cal_search_hint),
                            style = CalinoTypography.bodySmall,
                            color = CalinoColors.Ink3,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                        )
                    }
                }
            }
        } else {
            resultGroup(actionsHeading, groups.actions, onSelect)
            if (!groups.hasRecordMatches) item { SearchMessage(stringResource(R.string.cal_no_matching_records, query.trim())) }
            resultGroup(eventsHeading, groups.events, onSelect)
            resultGroup(tasksHeading, groups.tasks, onSelect)
            resultGroup(journalHeading, groups.journals, onSelect)
            resultGroup(contactsHeading, groups.contacts, onSelect)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SearchJumpTo(baseDate: LocalDate, onSelect: (CalinoSearchResult) -> Unit) {
    val pickDate = rememberDatePicker({ baseDate }) { picked -> onSelect(CalinoSearchResult.NavigateDate(picked)) }
    SectionLabel(stringResource(R.string.cal_search_jump_to), Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp))
    // Each chip keeps a 44dp lane around its 40dp body; the row's padding is trimmed to match.
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            stringResource(R.string.cal_today) to baseDate,
            stringResource(R.string.cal_tomorrow) to baseDate.plusDays(1),
            stringResource(R.string.cal_next_week) to baseDate.plusWeeks(1),
        ).forEach { (label, date) ->
            val jumpDescription = when (date) {
                baseDate -> stringResource(R.string.cal_go_today)
                baseDate.plusDays(1) -> stringResource(R.string.cal_go_tomorrow)
                else -> stringResource(R.string.cal_go_next_week)
            }
            SearchJumpChip(Modifier, jumpDescription, { onSelect(CalinoSearchResult.NavigateDate(date)) }) {
                Text(label, style = CalinoTypography.labelMedium, color = CalinoColors.Ink, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 13.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        SearchJumpChip(Modifier, stringResource(R.string.cal_choose_date), pickDate) {
            Icon(CalinoIcons.Calendar, contentDescription = null, tint = CalinoColors.Ink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SearchJumpChip(modifier: Modifier, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(CalinoShapes.DayBlock)
    Box(
        modifier.height(44.dp).calinoPressable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.defaultMinSize(minWidth = 40.dp).height(40.dp).clip(shape).border(1.dp, CalinoColors.Line, shape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun SearchRecentsHeader(onClear: () -> Unit) {
    val clearDescription = stringResource(R.string.cal_clear_recent_searches)
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(R.string.cal_search_recent), Modifier.weight(1f))
        Box(
            Modifier.heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onClear)
                .semantics { contentDescription = clearDescription },
            contentAlignment = Alignment.CenterEnd,
        ) { SectionLabel(stringResource(R.string.cal_search_clear), color = CalinoColors.Ink2) }
    }
}

@Composable
private fun SearchRecentRow(recent: String, onClick: () -> Unit) {
    val recentDescription = stringResource(R.string.cal_search_again, recent)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { contentDescription = recentDescription }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(CalinoIcons.Search, contentDescription = null, tint = CalinoColors.Ink3, modifier = Modifier.size(16.dp))
        Text(recent, style = CalinoTypography.bodyLarge, color = CalinoColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultGroup(heading: String, results: List<CalinoSearchResult>, onSelect: (CalinoSearchResult) -> Unit) {
    if (results.isEmpty()) return
    item(heading) { Text(heading, style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
    items(results, key = { it.stableId }) { SearchResultRow(it, onSelect) }
}

@Composable
private fun SearchResultRow(result: CalinoSearchResult, onSelect: (CalinoSearchResult) -> Unit) {
    val searchDateFormat = localizedDateFormatter("EEE, d MMM yyyy")
    val timeFormat = LocalTimeFormat
    val locale = LocalCalinoLocale
    val (title, detail) = when (result) {
        is CalinoSearchResult.NavigateDate -> stringResource(R.string.cal_search_result_date, result.date.format(searchDateFormat)) to stringResource(R.string.cal_search_open_month_date)
        is CalinoSearchResult.AddEvent -> stringResource(R.string.cal_add_event_title, result.parsed.title) to parsedDetail(result, searchDateFormat)
        is CalinoSearchResult.Event -> result.event.title to buildString {
            append(result.event.placementDate()?.format(searchDateFormat) ?: stringResource(R.string.cal_no_date))
            result.event.start?.let { append(" · ").append(timeFormat.format(it.toLocalTime(), locale)) }
            result.event.location?.let { append(" · $it") }
            result.calendarName?.let { append(" · $it") }
        }
        is CalinoSearchResult.Task -> result.task.title to buildString {
            append(result.task.due?.format(searchDateFormat) ?: stringResource(R.string.cal_no_due_date))
            result.task.category?.let { append(" · $it") }
        }
        is CalinoSearchResult.Journal -> result.journal.title.ifBlank { stringResource(R.string.cal_untitled_note) } to result.journal.date.format(searchDateFormat)
        is CalinoSearchResult.Contact -> result.contact.derivedDisplayName() to
            (result.contact.organization.ifBlank { result.contact.emails.firstOrNull()?.value ?: stringResource(R.string.cal_contact) })
    }
    val resultDescription = stringResource(R.string.cal_result_title_detail, title, detail)
    Row(
        Modifier.fillMaxWidth().testTag(result.searchTestTag()).clickable { onSelect(result) }.semantics { contentDescription = resultDescription }
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

@Composable
private fun parsedDetail(result: CalinoSearchResult.AddEvent, dateFormat: DateTimeFormatter): String = buildString {
    val timeFormat = LocalTimeFormat
    val locale = LocalCalinoLocale
    append(result.parsed.date.format(dateFormat))
    result.parsed.time?.let { append(" · ").append(timeFormat.format(it, locale)) }
    result.parsed.durationMinutes?.let { append(" · ").append(stringResource(R.string.cal_duration_minutes, it)) }
    result.parsed.recurrence?.let { append(" · ").append(stringResource(R.string.cal_repeats)) }
}

@Composable private fun SearchMessage(text: String) {
    Text(text, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(24.dp))
}

/** Matches the pill's own 24px backdrop blur. */
private const val SearchBlurRadiusPx = 24
