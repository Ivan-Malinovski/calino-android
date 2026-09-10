package calino.malinov.ski.poc.ui.surfaces

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import calino.malinov.ski.poc.data.model.placementDate
import calino.malinov.ski.poc.data.model.derivedDisplayName
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import calino.malinov.ski.poc.data.search.*
import calino.malinov.ski.poc.design.*
import calino.malinov.ski.poc.ui.components.CalinoIcons
import calino.malinov.ski.poc.ui.components.SwipeDownDismiss
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.lerp as lerpDp
import calino.malinov.ski.poc.state.CalinoSurfaceKind
import calino.malinov.ski.poc.state.LocalHingeOpenness
import calino.malinov.ski.poc.state.foldSplitProgress
import calino.malinov.ski.poc.state.CalinoSurfaceMode
import calino.malinov.ski.poc.state.calinoSurfaceModeFor
import calino.malinov.ski.poc.state.calinoWindowClassFor
import calino.malinov.ski.poc.state.LocalCalinoPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val SearchDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/** Floating capsule which grows from, and reverses back into, the add pill. */
@Composable
fun CalinoSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    snapshot: CalinoSnapshot,
    baseDate: LocalDate,
    onSelect: (CalinoSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var closeRequest by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val contactsEnabled = LocalCalinoPreferences.current.contactsEnabled
    val results = remember(query, snapshot.revision, baseDate, contactsEnabled) {
        searchCalino(snapshot, query, baseDate, contactsEnabled = contactsEnabled)
    }
    val duration = CalinoMotion.SurfaceFadeMillis
    fun requestClose() { closeRequest += 1 }

    LaunchedEffect(Unit) {
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
    BackHandler(onBack = ::requestClose)

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
        val mode = calinoSurfaceModeFor(
            calinoWindowClassFor(maxWidth.value.roundToInt()),
            CalinoSurfaceKind.Search,
        )
        val compact = mode == CalinoSurfaceMode.BottomSheet
        // Same rule as the other transient surfaces: a fold in progress halves
        // the room, so search does not straddle the crease.
        val hingeOpenness = LocalHingeOpenness.current
        val splitProgress by remember(hingeOpenness) {
            derivedStateOf {
                val openness = hingeOpenness?.value ?: return@derivedStateOf 0f
                (foldSplitProgress(openness) * 100f).roundToInt() / 100f
            }
        }
        val foldWidthCap = lerpDp(
            maxWidth,
            ((maxWidth - 44.dp) / 2f).coerceAtLeast(1.dp),
            splitProgress,
        )
        val resultCount = groupsCount(results)
        val expandedHeight = if (query.isBlank()) 190.dp else minOf(580.dp, (150 + resultCount * 66).dp)
        val expandedWidth = if (compact) {
            (maxWidth - 24.dp).coerceAtLeast(1.dp)
        } else {
            minOf((maxWidth - 48.dp).coerceAtLeast(1.dp), CalinoSurfaceKind.Search.widthCapDp.dp, foldWidthCap)
        }
        val expandedHeightTarget = if (compact) {
            minOf(maxHeight * .72f, expandedHeight)
        } else {
            minOf((maxHeight - 48.dp).coerceAtLeast(1.dp), expandedHeight, CalinoSurfaceKind.Search.heightCapDp.dp)
        }
        val capsuleWidth by animateDpAsState(if (expanded) expandedWidth else 224.dp, tween(duration), label = "search capsule width")
        val capsuleHeight by animateDpAsState(if (expanded) expandedHeightTarget else 54.dp, tween(duration), label = "search capsule height")
        val capsuleRadius by animateDpAsState(if (expanded) 30.dp else CalinoShapes.Pill, tween(duration), label = "search capsule radius")
        val capsuleFill by animateColorAsState(if (expanded) CalinoColors.Panel else CalinoColors.FloatFill, tween(duration), label = "search capsule fill")

        AnimatedVisibility(expanded, enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
            Box(Modifier.fillMaxSize().background(CalinoColors.scrim(.18f)).clickable(onClick = ::requestClose))
        }
        val searchContent: @Composable (Modifier) -> Unit = { dragModifier ->
            Surface(
                modifier = dragModifier.width(capsuleWidth).height(capsuleHeight).clickable(onClick = {}),
                shape = RoundedCornerShape(capsuleRadius),
                color = capsuleFill,
                shadowElevation = if (CalinoColors.elevationAlpha > 0f) 14.dp else 0.dp,
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
                            Text(
                                "×",
                                style = CalinoTypography.headlineMedium,
                                color = CalinoColors.Ink2,
                                modifier = Modifier.size(44.dp).clickable(onClick = ::requestClose).padding(8.dp)
                                    .semantics { contentDescription = "Close search" },
                            )
                        }
                        SearchResults(results, query, onSelect)
                    }
                }
            }
        }
        if (compact) {
            SwipeDownDismiss(visible = expanded, onDismiss = ::requestClose, modifier = Modifier.padding(bottom = 20.dp), content = searchContent)
        } else {
            AnimatedVisibility(
                visible = expanded,
                enter = scaleIn(tween(duration), initialScale = .94f) + fadeIn(tween(duration)),
                exit = scaleOut(tween(duration), targetScale = .94f) + fadeOut(tween(duration)),
                modifier = Modifier.align(Alignment.Center),
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

private fun groupsCount(groups: CalinoSearchGroups): Int =
    groups.actions.size + groups.events.size + groups.tasks.size + groups.journals.size + groups.contacts.size

@Composable
private fun SearchResults(groups: CalinoSearchGroups, query: String, onSelect: (CalinoSearchResult) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(top = 4.dp)) {
        if (query.isBlank()) item { SearchMessage("Find events, tasks and journal entries — or type a date.") }
        else if (groups.isEmpty) item { SearchMessage("No results for “${query.trim()}”") }
        else {
            resultGroup("ACTIONS", groups.actions, onSelect)
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
        Modifier.fillMaxWidth().clickable { onSelect(result) }.semantics { contentDescription = "$title, $detail" }
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
