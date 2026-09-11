package calino.malinov.ski.poc.ui.surfaces

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.data.model.JournalEntry
import calino.malinov.ski.poc.data.model.JournalDraft
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.ui.components.MenuButton
import calino.malinov.ski.poc.ui.components.CalinoMarkdown
import calino.malinov.ski.poc.design.CalinoShapes
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.ui.components.CalinoIcons
import calino.malinov.ski.poc.ui.components.CompactSegmentedControl
import calino.malinov.ski.poc.ui.components.BottomDetailCard
import calino.malinov.ski.poc.ui.components.ModalActionPill
import calino.malinov.ski.poc.state.CalinoSurfaceKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val JournalDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
private val JournalMonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

private enum class JournalMode { All, Month }
private enum class JournalEditorMode { Write, Read }

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
    startEntryRequest: Int = 0,
) {
    var modeName by rememberSaveable { mutableStateOf(JournalMode.Month.name) }
    val mode = remember(modeName) { runCatching { JournalMode.valueOf(modeName) }.getOrDefault(JournalMode.Month) }
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
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = visibleEntries.firstOrNull { it.id == editingId }
    LaunchedEffect(editingId) { onEditingChanged(editingId != null) }
    LaunchedEffect(openEntryId, entries) {
        if (openEntryId != null && entries.any { it.id == openEntryId }) {
            editingId = openEntryId
            onOpenEntryConsumed()
        }
    }
    fun startNewEntry() {
        val id = "draft-journal-${draftSequence + 1}"
        draftSequence += 1
        draftId = id
        draftDateEpochDay = newEntryDate.toEpochDay()
        editingId = id
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
        editingId = null
    }

    Box(Modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.Top) {
                onOpenMenu?.let {
                    MenuButton(onClick = it, modifier = Modifier.padding(end = 6.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Journal", style = CalinoTypography.displayLarge)
                    Text("A place for what the calendar cannot hold.", style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 3.dp))
                }
            }

            CompactSegmentedControl(
                options = JournalMode.entries.map { if (it == JournalMode.All) "All entries" else "By month" },
                selectedIndex = JournalMode.entries.indexOf(mode),
                onSelected = { modeName = JournalMode.entries[it].name },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                semanticLabel = "Journal list view",
                maxControlWidth = 248.dp,
            )

            AnimatedContent(
                targetState = mode,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(210)) { direction * it / 3 } + fadeIn(tween(170))) togetherWith
                        (slideOutHorizontally(tween(170)) { -direction * it / 3 } + fadeOut(tween(130)))
                },
                label = "journal mode transition",
            ) { currentMode ->
                when (currentMode) {
                    JournalMode.All -> JournalRecentList(sorted) { editingId = it.id }
                    JournalMode.Month -> JournalMonthList(sorted) { editingId = it.id }
                }
            }
        }

        val currentEntry = visibleEntries.firstOrNull { it.id == editingId }
        if (currentEntry != null) {
            JournalEditor(
                entry = currentEntry,
                onDismiss = { closeEntry(currentEntry) },
                onSave = { updated ->
                    if (draft?.id == updated.id) {
                        draft?.commit(updated.title, updated.body)?.let(onCreate)
                        draftId = null
                        draftDateEpochDay = null
                    } else {
                        onUpdate(updated)
                    }
                    editingId = null
                },
                onDelete = { deleted ->
                    if (draft?.id == deleted.id) {
                        draftId = null
                        draftDateEpochDay = null
                    } else {
                        onDelete(deleted)
                    }
                    editingId = null
                },
            )

        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JournalRecentList(entries: List<JournalEntry>, onEntry: (JournalEntry) -> Unit) {
    if (entries.isEmpty()) {
        JournalEmptyState()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = CalinoSpacing.PillClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { "journal:${it.id}" }) { entry ->
            JournalCard(
                entry = entry,
                modifier = Modifier.animateItem(),
                onClick = { onEntry(entry) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JournalMonthList(entries: List<JournalEntry>, onEntry: (JournalEntry) -> Unit) {
    if (entries.isEmpty()) {
        JournalEmptyState()
        return
    }
    val groups = entries.groupBy { it.date.withDayOfMonth(1) }.toSortedMap(reverseOrder())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Leave a deliberate breathing pocket so the final card can scroll
        // fully clear of the floating add pill.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = CalinoSpacing.PillClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        groups.forEach { (month, monthEntries) ->
            item(key = "month:$month") { Text(month.format(JournalMonthFormat).uppercase(), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 3.dp, bottom = 2.dp)) }
            items(monthEntries, key = { "journal:${it.id}" }) { entry ->
                JournalCard(
                    entry = entry,
                    modifier = Modifier.animateItem(),
                    onClick = { onEntry(entry) },
                )
            }
        }
    }
}

@Composable
private fun JournalCard(entry: JournalEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dayName = entry.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalinoShapes.Card))
            .background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card))
            .semantics(mergeDescendants = true) { contentDescription = "Open journal entry ${entry.title.ifBlank { "Untitled note" }}" }
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(45.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dayName.uppercase(), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, fontSize = 9.sp)
            Text(entry.date.dayOfMonth.toString(), style = CalinoTypography.titleLarge, color = CalinoColors.Accent, modifier = Modifier.padding(top = 2.dp))
            Box(Modifier.padding(top = 5.dp).size(5.dp).clip(CircleShape).background(CalinoColors.Accent))
        }
        Box(Modifier.padding(horizontal = 13.dp).width(1.dp).height(64.dp).background(CalinoColors.Line2))
        Column(Modifier.weight(1f)) {
            Text(entry.title.ifBlank { "Untitled note" }, style = CalinoTypography.titleSmall.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entry.date.format(JournalDateFormat), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 3.dp))
            Text(entry.body, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun JournalEmptyState() {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(CalinoColors.AccentSoft), contentAlignment = Alignment.Center) { Text("✦", color = CalinoColors.Accent, fontSize = 22.sp) }
        Text("Nothing written yet", style = CalinoTypography.titleMedium, modifier = Modifier.padding(top = 14.dp))
        Text("Your first note will appear here.", style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 4.dp))
    }
}

private val WordBoundaryPattern = Regex("\\s+")

@Composable
private fun JournalEditor(
    entry: JournalEntry,
    onDismiss: () -> Unit,
    onSave: (JournalEntry) -> Unit,
    onDelete: (JournalEntry) -> Unit,
) {
    var title by rememberSaveable(entry.id) { mutableStateOf(entry.title) }
    var body by rememberSaveable(entry.id) { mutableStateOf(entry.body) }
    var editorModeName by rememberSaveable(entry.id) { mutableStateOf(JournalEditorMode.Write.name) }
    var confirmDelete by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showDiscard by rememberSaveable(entry.id) { mutableStateOf(false) }
    val editorMode = remember(editorModeName) {
        runCatching { JournalEditorMode.valueOf(editorModeName) }.getOrDefault(JournalEditorMode.Write)
    }
    val dirty = title != entry.title || body != entry.body
    val canSave = title.isNotBlank() || body.isNotBlank()
    val focusTitle = entry.title.isBlank() && entry.body.isBlank()
    val titleFocusRequester = remember { FocusRequester() }
    val wordCount = remember(body) { body.trim().let { if (it.isEmpty()) 0 else it.split(WordBoundaryPattern).size } }
    val statusColor by animateColorAsState(
        targetValue = if (dirty) CalinoColors.Rose else CalinoColors.Accent,
        animationSpec = tween(180),
        label = "journal save status color",
    )

    var shown by remember(entry.id) { mutableStateOf(true) }
    var closeAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun closeAnimated(action: () -> Unit) {
        if (shown) { closeAction = action; shown = false }
    }
    LaunchedEffect(shown) {
        if (!shown) { kotlinx.coroutines.delay(220); closeAction?.invoke() }
    }

    fun dismissEditor() {
        if (dirty) showDiscard = true else closeAnimated(onDismiss)
    }

    BackHandler {
        dismissEditor()
    }
    LaunchedEffect(focusTitle) {
        if (focusTitle) {
            kotlinx.coroutines.delay(280)
            titleFocusRequester.requestFocus()
        }
    }

    BottomDetailCard(
        visible = shown,
        onDismiss = ::dismissEditor,
        modifier = Modifier.fillMaxSize(),
        dismissDistance = 720.dp,
        surfaceKind = CalinoSurfaceKind.Editor,
        // A dirty-editor dismissal opens the confirmation bar instead of
        // leaving the editor. Changing this key asks the gesture surface to
        // spring back underneath that prompt.
        resetKey = showDiscard,
        pill = {
            ModalActionPill(
                addLabel = if (focusTitle) "New entry" else "Edit entry",
                morphFromAddPill = true,
                inPillLane = true,
                expanded = shown,
                cancelLabel = "Cancel",
                onCancel = ::dismissEditor,
                cancelDescription = "Cancel journal editing",
                primaryLabel = "Save",
                onPrimary = { closeAnimated { onSave(entry.copy(title = title.trim(), body = body.trim())) } },
                primaryEnabled = canSave,
                primaryDescription = "Save journal entry",
                secondaryLabel = "Delete",
                onSecondary = { confirmDelete = !confirmDelete },
                secondaryDescription = "Delete journal entry",
            )
        },
    ) { editorModifier ->
        Column(
            editorModifier.fillMaxSize().background(CalinoColors.Canvas),
        ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = ::dismissEditor,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Back to journal" },
            ) {
                Icon(CalinoIcons.ChevronLeft, contentDescription = null, tint = CalinoColors.Ink)
            }
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text("Journal entry", style = CalinoTypography.titleMedium)
                Text(
                    entry.date.format(JournalDateFormat),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line))

        Row(
            Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(CalinoColors.Ink.copy(alpha = .05f))
                .padding(3.dp)
                .semantics { contentDescription = "Journal editor mode" },
        ) {
            JournalEditorMode.entries.forEach { entryMode ->
                val selected = entryMode == editorMode
                val color by animateColorAsState(
                    targetValue = if (selected) CalinoColors.Ink else CalinoColors.Ink2,
                    animationSpec = tween(140),
                    label = "journal editor mode color",
                )
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) CalinoColors.Panel else Color.Transparent)
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (entryMode == JournalEditorMode.Write) "Write mode" else "Read mode"
                            role = Role.Tab
                            this.selected = selected
                        }
                        .clickable { editorModeName = entryMode.name }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (entryMode == JournalEditorMode.Write) "Write" else "Read", style = CalinoTypography.labelMedium, color = color)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$wordCount ${if (wordCount == 1) "word" else "words"}",
                style = CalinoTypography.labelSmall,
                color = CalinoColors.Ink3,
            )
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = dirty,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(110)) },
                label = "journal save status",
            ) { hasChanges ->
                Text(
                    if (hasChanges) "Unsaved changes" else "Saved locally",
                    style = CalinoTypography.labelSmall,
                    color = statusColor,
                )
            }
        }

        AnimatedContent(
            targetState = editorMode,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val direction = if (targetState == JournalEditorMode.Read) 1 else -1
                (slideInHorizontally(tween(220)) { direction * it / 3 } + fadeIn(tween(170))) togetherWith
                    (slideOutHorizontally(tween(180)) { -direction * it / 3 } + fadeOut(tween(130)))
            },
            label = "journal write read transition",
        ) { currentMode ->
            when (currentMode) {
                JournalEditorMode.Write -> JournalWritePane(
                    title = title,
                    body = body,
                    onTitleChange = { title = it },
                    onBodyChange = { body = it },
                    titleFocusRequester = titleFocusRequester,
                )
                JournalEditorMode.Read -> JournalReadPane(title = title, body = body)
            }
        }

        AnimatedVisibility(
            visible = confirmDelete,
            enter = fadeIn(tween(150)) + expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(CalinoShapes.Row))
                    .background(CalinoColors.Rose.copy(alpha = .09f))
                    .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Remove this note?", style = CalinoTypography.bodyMedium, color = CalinoColors.Ink, modifier = Modifier.weight(1f))
                TextButton(onClick = { closeAnimated { onDelete(entry) } }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Confirm delete journal entry" }) {
                    Text("Delete", color = CalinoColors.Rose)
                }
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
                Text("Discard your changes?", style = CalinoTypography.bodyMedium, color = CalinoColors.Panel, modifier = Modifier.weight(1f))
                TextButton(onClick = { showDiscard = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Keep editing", color = CalinoColors.Panel) }
                TextButton(onClick = { closeAnimated(onDismiss) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Discard journal changes" }) { Text("Discard", color = CalinoColors.AccentSoft) }
            }
        }

        // The pill itself lives in the pill lane, outside this card; this
        // reserves the room it occupies over the card's tail.
        Spacer(Modifier.height(CalinoSpacing.PillClearance))
        }
    }
}

@Composable
private fun JournalWritePane(
    title: String,
    body: String,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    titleFocusRequester: FocusRequester? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "editor-intro") {
            Column {
                Text("A quiet place for the details", style = CalinoTypography.labelSmall, color = CalinoColors.Accent)
                Text("Write freely. You can always shape it later.", style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item(key = "editor-title") {
            TextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(titleFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .semantics { contentDescription = "Journal title" },
                textStyle = CalinoTypography.headlineMedium,
                label = { Text("Title") },
                placeholder = { Text("Untitled note", color = CalinoColors.Ink3) },
                singleLine = false,
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CalinoColors.Panel,
                    unfocusedContainerColor = CalinoColors.Panel,
                    focusedIndicatorColor = CalinoColors.Accent,
                    unfocusedIndicatorColor = CalinoColors.Line,
                    cursorColor = CalinoColors.Accent,
                ),
            )
        }
        item(key = "editor-body") {
            TextField(
                value = body,
                onValueChange = onBodyChange,
                modifier = Modifier.fillMaxWidth().height(260.dp).semantics { contentDescription = "Journal body" },
                textStyle = CalinoTypography.bodyLarge.copy(lineHeight = 25.sp),
                label = { Text("Note") },
                placeholder = { Text("What is on your mind?", color = CalinoColors.Ink3) },
                minLines = 8,
                maxLines = 14,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CalinoColors.Panel,
                    unfocusedContainerColor = CalinoColors.Panel,
                    focusedIndicatorColor = CalinoColors.Accent,
                    unfocusedIndicatorColor = CalinoColors.Line,
                    cursorColor = CalinoColors.Accent,
                ),
            )
        }
    }
}

@Composable
private fun JournalReadPane(title: String, body: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "read-preview") {
            Column {
                Text("READ MODE", style = CalinoTypography.labelSmall, color = CalinoColors.Accent)
                Text(title.ifBlank { "Untitled note" }, style = CalinoTypography.displaySmall, modifier = Modifier.padding(top = 7.dp))
                Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(CalinoColors.Line))
                CalinoMarkdown(body, modifier = Modifier.padding(top = 18.dp))
            }
        }
    }
}
