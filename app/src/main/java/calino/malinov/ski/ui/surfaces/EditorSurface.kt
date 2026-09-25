package calino.malinov.ski.ui.surfaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.EditorDraft
import calino.malinov.ski.data.model.EditorField
import calino.malinov.ski.data.model.RecurrenceFreq
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.model.AutoCategoryRule
import calino.malinov.ski.data.model.applyInput
import calino.malinov.ski.data.model.recurrenceDaysOf
import calino.malinov.ski.data.model.recurrenceFreqOf
import calino.malinov.ski.data.model.recurrenceRule
import calino.malinov.ski.data.model.withAutoCategories
import calino.malinov.ski.data.model.withCategoryToggled
import calino.malinov.ski.data.parser.PocQuickAddKind
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.ui.components.BottomDetailCard
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.CalinoColorSwatchRow
import calino.malinov.ski.ui.components.CalinoSearchField
import calino.malinov.ski.ui.components.CalinoToggleRow
import calino.malinov.ski.util.CalinoZones
import java.time.Instant
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.CalinoMarkdownEditor
import calino.malinov.ski.ui.components.CalinoTextField
import calino.malinov.ski.ui.components.CompactSegmentedControl
import calino.malinov.ski.ui.components.EditorLabel
import calino.malinov.ski.ui.components.EditorReveal
import calino.malinov.ski.ui.components.ModalActionPill
import calino.malinov.ski.ui.components.rememberDatePicker
import calino.malinov.ski.ui.components.rememberTimePicker
import calino.malinov.ski.ui.components.WhenHero
import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.util.formatRecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.util.formatCalinoDuration
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

private val EditorDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
/** Matches the shared detail card's own removal timing. */
private const val EditorExitMillis = CalinoMotion.SurfaceFadeMillis.toLong()
private val TravelTimeChoices = listOf<Int?>(null, 5, 15, 30, 60)
private val ReminderChoices = listOf(0, 5, 10, 30, 60, 24 * 60)

/**
 * The full task/event/journal editor. The natural-language line stays at the
 * top and keeps pre-filling the form below it; a field the person edits by hand
 * is marked touched in [EditorDraft] so later typing cannot undo it.
 *
 * The same surface creates and edits: [initial] carries an editingId when it
 * was seeded from a saved record, and the host decides between add and update.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorSurface(
    initial: EditorDraft,
    baseDate: LocalDate,
    calendars: List<CalinoCalendar> = emptyList(),
    categories: List<String> = emptyList(),
    relatedCandidates: List<Pair<String, String>> = emptyList(),
    onPhoto: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    onSave: (EditorDraft) -> Unit = {},
    /**
     * Called the moment Save is pressed, before the card starts leaving. The
     * pill morphs back into whatever add pill the host is about to show, and
     * it has to know which one that is before the morph starts, not when the
     * record has been written -- a kind switched to Journal goes back to a
     * different screen than the one this editor was opened from.
     */
    onSaveStarted: (EditorDraft) -> Unit = {},
    visible: Boolean = true,
    morphFromAddPill: Boolean = false,
) {
    // The length a line with no stated end falls back to, which the parser
    // re-applies on every keystroke.
    val defaultDurationMinutes = LocalCalinoPreferences.current.defaultDuration.minutes
    val autoCategoryRules = LocalCalinoPreferences.current.autoCategoryRules
    // A new draft is matched once as it opens (a quick-add line or AI import
    // arrives with a title); a saved record is matched only if its title is edited.
    var draft by remember(initial.editingId, initial.kind) {
        mutableStateOf(if (initial.isEditing) initial else initial.withAutoCategories(autoCategoryRules))
    }
    var shown by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    var pendingCloseAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var descriptionOpen by remember(initial.editingId) { mutableStateOf(false) }
    var moreOpen by remember(initial.editingId) { mutableStateOf(false) }
    var remindersOpen by remember(initial.editingId) { mutableStateOf(false) }
    var recurrenceOpen by remember(initial.editingId) { mutableStateOf(false) }
    var attendeeInput by remember(initial.editingId) { mutableStateOf("") }
    val editorScrollState = rememberScrollState()

    val closeAfterAnimation: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            pendingCloseAction = action
            shown = false
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            delay(EditorExitMillis)
            pendingCloseAction?.invoke()
        }
    }
    LaunchedEffect(visible) { if (!visible) closeAfterAnimation(onDismiss) }

    val dismiss: () -> Unit = { closeAfterAnimation(onDismiss) }


    val pickStartDate = rememberDatePicker({ draft.date }) {
        draft = draft.copy(date = it, taskDueChanged = draft.taskDueChanged || draft.kind == PocQuickAddKind.Task,
            touched = draft.touched + EditorField.Date)
    }
    val pickStartTime = rememberTimePicker({ draft.startTime }) {
        draft = draft.copy(startTime = it, taskDueChanged = draft.taskDueChanged || draft.kind == PocQuickAddKind.Task,
            touched = draft.touched + EditorField.Time)
    }
    val pickEndDate = rememberDatePicker({ draft.endDate }) { picked ->
        draft.endTime?.let { draft = draft.withEnd(picked, it) }
    }
    val pickEndTime = rememberTimePicker({ draft.endTime }) { picked ->
        draft = draft.withEnd(draft.endDate, picked)
    }
    val pickUntil = rememberDatePicker({ draft.date.plusMonths(3) }) { picked ->
        val freq = recurrenceFreqOf(draft.recurrence) ?: RecurrenceFreq.Weekly
        draft = draft.copy(
            recurrence = recurrenceRule(freq, recurrenceDaysOf(draft.recurrence), picked),
            recurrenceChanged = true,
        )
    }

    BottomDetailCard(
        visible = shown,
        onDismiss = dismiss,
        modifier = Modifier.fillMaxSize(),
        canStartDismiss = { editorScrollState.value == 0 },
        surfaceKind = CalinoSurfaceKind.Editor,
        pill = {
            ModalActionPill(
                addLabel = addLabelFor(draft),
                morphFromAddPill = morphFromAddPill,
                // The same flag that widened the add pill on the way in runs
                // the move backwards here, so closing returns the pill to the
                // shape it came from instead of taking it away with the card.
                inPillLane = true,
                expanded = shown,
                cancelLabel = "Cancel",
                onCancel = dismiss,
                primaryLabel = "Save",
                onPrimary = {
                    // A second press while the card is already leaving would
                    // start another write report that no write ever finishes,
                    // leaving the pill tracing forever.
                    if (closing) return@ModalActionPill
                    val saved = draft
                    onSaveStarted(saved)
                    closeAfterAnimation { onSave(saved) }
                },
                // A record that exists and has not been touched has nothing
                // to save; the pill then offers only the way out.
                primaryVisible = !draft.isEditing || draft != initial,
                primaryEnabled = draft.canSave(),
                primaryDescription = "Save editor",
                cancelDescription = "Cancel editor",
            )
        },
    ) { detailModifier ->
        Column(detailModifier.fillMaxSize().background(CalinoColors.Canvas)) {
            EditorHeader(
                draft = draft,
                onInput = { input ->
                    draft = draft.applyInput(input, baseDate, defaultDurationMinutes).rematchedFrom(draft, autoCategoryRules)
                },
                onDismiss = dismiss,
                onPhoto = onPhoto,
            )
            HorizontalDivider(color = CalinoColors.Ink.copy(alpha = .09f))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(editorScrollState)
                        .padding(horizontal = 20.dp),
                ) {
                    if (!draft.isEditing) {
                        KindSelector(draft.kind) { entry ->
                            draft = draft.copy(kind = entry).applyInput(draft.rawInput, baseDate, defaultDurationMinutes)
                                .withAutoCategories(autoCategoryRules)
                        }
                    }

                    EditorDivider()

                    // Picking a different kind replaces every field below the
                    // title. Fading through keeps it one form changing rather
                    // than one form being swapped for another; the size follows
                    // so the pill clearance below does not jump with it.
                    AnimatedContent(
                        targetState = draft.kind,
                        transitionSpec = {
                            (fadeIn(tween(CalinoMotion.ContentEnterMillis)) togetherWith
                                fadeOut(tween(CalinoMotion.FadeThroughMillis)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "editor kind",
                    ) { kind ->
                        // The field composables emit a run of sibling rows and
                        // expect a column to stack them; the slot here is a box.
                        Column(Modifier.fillMaxWidth()) {
                    when {
                        kind == PocQuickAddKind.Event -> EventEditorFields(
                            draft = draft,
                            calendars = calendars,
                            categories = (categories + draft.categories).distinct(),
                            descriptionOpen = descriptionOpen,
                            remindersOpen = remindersOpen,
                            recurrenceOpen = recurrenceOpen,
                            moreOpen = moreOpen,
                            relatedCandidates = relatedCandidates,
                            attendeeInput = attendeeInput,
                            onDescriptionOpen = { descriptionOpen = !descriptionOpen },
                            onRemindersOpen = { remindersOpen = !remindersOpen },
                            onRecurrenceOpen = { recurrenceOpen = !recurrenceOpen },
                            onMoreOpen = { moreOpen = !moreOpen },
                            onAttendeeInput = { attendeeInput = it },
                            onDraft = { draft = it },
                            pickStartDate = pickStartDate,
                            pickStartTime = pickStartTime,
                            pickEndDate = pickEndDate,
                            pickEndTime = pickEndTime,
                            pickUntil = pickUntil,
                        )
                        kind == PocQuickAddKind.Task -> TaskEditorFields(
                            draft = draft,
                            categories = (categories + draft.categories).distinct(),
                            descriptionOpen = descriptionOpen,
                            remindersOpen = remindersOpen,
                            recurrenceOpen = recurrenceOpen,
                            onDescriptionOpen = { descriptionOpen = !descriptionOpen },
                            onRemindersOpen = { remindersOpen = !remindersOpen },
                            onRecurrenceOpen = { recurrenceOpen = !recurrenceOpen },
                            onDraft = { draft = it },
                            pickStartDate = pickStartDate,
                            pickStartTime = pickStartTime,
                            pickUntil = pickUntil,
                        )
                        else -> JournalEditorFields(draft) { body -> draft = draft.copy(body = body) }
                    }
                        }
                    }

                    // The action pill floats above this reserved tail, matching
                    // the main add pill without hiding the final form row.
                    Spacer(Modifier.height(CalinoSpacing.PillClearance))
                }
            }
        }
    }
}

private fun addLabelFor(draft: EditorDraft): String = when (draft.kind) {
    PocQuickAddKind.Event -> if (draft.isEditing) {
        "Edit event"
    } else {
        "Add on ${draft.date.format(EditorDateFormat)}"
    }
    PocQuickAddKind.Task -> "New task"
    PocQuickAddKind.Journal -> "New entry"
}

@Composable
private fun EditorHeader(
    draft: EditorDraft,
    onInput: (String) -> Unit,
    onDismiss: () -> Unit,
    onPhoto: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorTitleField(draft, onInput, Modifier.weight(1f))
        if (onPhoto != null && !draft.isEditing && draft.kind == PocQuickAddKind.Event) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPhoto)
                    .semantics { contentDescription = "Import event from photo" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(CalinoIcons.Camera, contentDescription = null, tint = CalinoColors.Ink2, modifier = Modifier.size(20.dp))
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "Close editor" },
            contentAlignment = Alignment.Center,
        ) { Text("×", fontSize = 24.sp, color = CalinoColors.Ink2) }
    }
}

@Composable
private fun EditorTitleField(
    draft: EditorDraft,
    onInput: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.heightIn(min = 70.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalinoIcon(draft.kind.editorIcon(), tint = CalinoColors.Accent, modifier = Modifier.size(23.dp), contentDescription = null)
        Spacer(Modifier.size(14.dp))
        BasicTextField(
            value = draft.rawInput,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Title, ${draft.kind.name.lowercase(Locale.US)}"
            },
            textStyle = CalinoTypography.titleMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontSize = 23.sp,
                lineHeight = 28.sp,
                color = CalinoColors.Ink,
            ),
            cursorBrush = SolidColor(CalinoColors.Accent),
            singleLine = true,
            maxLines = 1,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth()) {
                    if (draft.rawInput.isBlank()) {
                        Text(
                            when (draft.kind) {
                                PocQuickAddKind.Event -> "Add event title"
                                PocQuickAddKind.Task -> "Add task title"
                                PocQuickAddKind.Journal -> "Add note title"
                            },
                            style = CalinoTypography.titleMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 23.sp,
                                lineHeight = 28.sp,
                                color = CalinoColors.Ink3,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

private fun PocQuickAddKind.editorIcon() = when (this) {
    PocQuickAddKind.Event -> calino.malinov.ski.ui.components.CalinoIcon.Calendar
    PocQuickAddKind.Task -> calino.malinov.ski.ui.components.CalinoIcon.Check
    PocQuickAddKind.Journal -> calino.malinov.ski.ui.components.CalinoIcon.Note
}

@Composable
private fun KindSelector(selected: PocQuickAddKind, onSelect: (PocQuickAddKind) -> Unit) {
    CompactSegmentedControl(
        options = PocQuickAddKind.entries.map { it.name },
        selectedIndex = PocQuickAddKind.entries.indexOf(selected),
        onSelected = { onSelect(PocQuickAddKind.entries[it]) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        semanticLabel = "Entry kind",
    )
}

@Composable
private fun EditorDivider() {
    HorizontalDivider(color = CalinoColors.Line)
}

@Composable
private fun EventEditorFields(
    draft: EditorDraft,
    calendars: List<CalinoCalendar>,
    categories: List<String>,
    descriptionOpen: Boolean,
    remindersOpen: Boolean,
    recurrenceOpen: Boolean,
    moreOpen: Boolean,
    relatedCandidates: List<Pair<String, String>>,
    attendeeInput: String,
    onDescriptionOpen: () -> Unit,
    onRemindersOpen: () -> Unit,
    onRecurrenceOpen: () -> Unit,
    onMoreOpen: () -> Unit,
    onAttendeeInput: (String) -> Unit,
    onDraft: (EditorDraft) -> Unit,
    pickStartDate: () -> Unit,
    pickStartTime: () -> Unit,
    pickEndDate: () -> Unit,
    pickEndTime: () -> Unit,
    pickUntil: () -> Unit,
) {
    EditorSwitchRow(CalinoIcon.Clock, "All day", draft.allDay) { onDraft(draft.copy(allDay = it)) }
    EditorDivider()
    EventDateTimeSection(draft, pickStartDate, pickStartTime, pickEndDate, pickEndTime)
    // A zone is a property of a time; an all-day event has none to carry.
    EditorReveal(!draft.allDay) { EventZoneSection(draft, onDraft) }
    EditorDivider()
    EditorTextRow(
        icon = CalinoIcon.Pin,
        label = "Location",
        value = draft.location.orEmpty(),
        placeholder = "Add location",
        onValueChange = { onDraft(draft.copy(location = it, touched = draft.touched + EditorField.Location)) },
    )
    EditorDivider()
    if (calendars.isNotEmpty()) {
        CalendarRow(draft, calendars, onDraft)
        EditorDivider()
    }
    EditorValueRow(
        icon = CalinoIcon.Bell,
        label = "Reminder",
        value = reminderSummary(draft.reminders),
        onClick = onRemindersOpen,
    )
    EditorReveal(remindersOpen) {
        EditorChoiceBlock { ReminderChips(draft.reminders, single = false) { onDraft(draft.copy(reminders = it)) } }
    }
    EditorDivider()
    if (calino.malinov.ski.platform.AndroidCalendarId.isImported(draft.calendarId)) {
        Text(
            if (draft.providerRecurring) "Repeats · rule managed by the owning calendar"
            else "Repeat rules are managed by the owning calendar",
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    } else {
        EditorValueRow(
            icon = CalinoIcon.Repeat,
            label = "Repeat",
            value = draft.recurrence?.let { formatRecurrenceRule(it, draft.date) } ?: "Don't repeat",
            onClick = onRecurrenceOpen,
        )
        EditorReveal(recurrenceOpen) {
            EditorChoiceBlock { RecurrenceEditor(draft, onDraft, pickUntil) }
        }
    }
    // Turning repeat on while editing conjures this whole block. It is the
    // same kind of optional detail as the reveals above it, so it arrives the
    // same way rather than displacing the rows below in one frame.
    EditorReveal(draft.isEditing && (draft.providerRecurring || draft.recurrence != null || draft.recurrenceId != null || draft.recurrenceDate != null)) {
        RecurrenceScopeSelector(draft, onDraft)
    }
    EditorDivider()
    DescriptionSection(draft, descriptionOpen, onDescriptionOpen, onDraft)
    EditorDivider()
    MoreSection(
        draft = draft,
        open = moreOpen,
        categories = categories,
        onToggle = onMoreOpen,
        relatedCandidates = relatedCandidates,
        attendeeInput = attendeeInput,
        onAttendeeInput = onAttendeeInput,
        onDraft = onDraft,
        providerOwned = calino.malinov.ski.platform.AndroidCalendarId.isImported(draft.calendarId),
    )
}

@Composable
private fun TaskEditorFields(
    draft: EditorDraft,
    categories: List<String>,
    descriptionOpen: Boolean,
    remindersOpen: Boolean,
    recurrenceOpen: Boolean,
    onDescriptionOpen: () -> Unit,
    onRemindersOpen: () -> Unit,
    onRecurrenceOpen: () -> Unit,
    onDraft: (EditorDraft) -> Unit,
    pickStartDate: () -> Unit,
    pickStartTime: () -> Unit,
    pickUntil: () -> Unit,
) {
    EditorValueRow(CalinoIcon.Calendar, "Due date",
        if (draft.taskDueAbsent && !draft.taskDueChanged) "Add due date" else draft.date.format(EditorDateFormat),
        pickStartDate)
    EditorDivider()
    EditorValueRow(
        icon = CalinoIcon.Clock,
        label = "Due time",
        value = draft.startTime?.let { LocalTimeFormat.format(it) } ?: "Add time",
        onClick = pickStartTime,
    )
    EditorDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLabel("Priority")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(0 to "None", 1 to "High", 5 to "Medium", 9 to "Low").forEach { (value, label) ->
                CalinoChip(
                    text = label,
                    selected = draft.priority == value,
                    description = "Set task priority to ${label.lowercase(Locale.US)}",
                    semanticsRole = Role.RadioButton,
                    onClick = { onDraft(draft.copy(priority = value)) },
                )
            }
        }
    }
    EditorDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorLabel("Progress · ${draft.percentComplete}%")
        androidx.compose.material3.Slider(
            value = draft.percentComplete.toFloat(),
            onValueChange = { onDraft(draft.copy(percentComplete = it.toInt(), taskStatus = if (it > 0f) "IN-PROCESS" else "NEEDS-ACTION")) },
            valueRange = 0f..100f,
            steps = 9,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = "Task progress, ${draft.percentComplete} percent" },
        )
    }
    EditorDivider()
    if (draft.parentTaskId == null) {
        EditorValueRow(CalinoIcon.Repeat, "Repeat", formatRecurrenceRule(draft.recurrence, draft.date), onRecurrenceOpen)
        EditorReveal(recurrenceOpen) { RecurrenceEditor(draft, onDraft, pickUntil) }
    } else {
        Text(
            "Subtasks cannot repeat.",
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 12.dp),
            color = CalinoColors.Ink3,
            style = CalinoTypography.bodySmall,
        )
    }
    if (draft.isEditing && (draft.recurrence != null || draft.recurrenceId != null || draft.recurrenceDate != null)) {
        RecurrenceScopeSelector(draft, onDraft)
    }
    EditorDivider()
    if (categories.isNotEmpty()) {
        CategoriesSection(draft, categories, single = true, onDraft = onDraft)
        EditorDivider()
    }
    EditorValueRow(CalinoIcon.Bell, "Reminder", reminderSummary(draft.reminders), onRemindersOpen)
    EditorReveal(remindersOpen) {
        EditorChoiceBlock { ReminderChips(draft.reminders, single = true) { onDraft(draft.copy(reminders = it)) } }
    }
    EditorDivider()
    DescriptionSection(draft, descriptionOpen, onDescriptionOpen, onDraft)
    EditorDivider()
    CalinoColorSwatchRow(Color(draft.color)) { picked ->
        onDraft(draft.copy(color = picked.toArgb().toLong() and 0xffffffffL))
    }
}

@Composable
private fun JournalEditorFields(draft: EditorDraft, onBody: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Top) {
        CalinoIcon(CalinoIcon.Note, tint = CalinoColors.Ink3, modifier = Modifier.size(20.dp), contentDescription = null)
        Spacer(Modifier.size(14.dp))
        BasicTextField(
            value = draft.body,
            onValueChange = onBody,
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).semantics { contentDescription = "Journal note" },
            textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink),
            cursorBrush = SolidColor(CalinoColors.Accent),
            minLines = 6,
            maxLines = 12,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth()) {
                    if (draft.body.isBlank()) Text("What is on your mind?", color = CalinoColors.Ink3, style = CalinoTypography.bodyLarge)
                    innerTextField()
                }
            },
        )
    }
    EditorDivider()
}

@Composable
private fun EventDateTimeSection(
    draft: EditorDraft,
    pickStartDate: () -> Unit,
    pickStartTime: () -> Unit,
    pickEndDate: () -> Unit,
    pickEndTime: () -> Unit,
) {
    // The all-day switch sits directly above this block, so the hero is given
    // no toggle of its own: two controls for one fact is what makes a form
    // feel guessed at.
    WhenHero(
        startDate = draft.date,
        startTime = if (draft.allDay) null else draft.startTime,
        endDate = draft.endDate,
        endTime = if (draft.allDay) null else draft.endTime,
        accent = CalinoColors.Accent,
        allDay = draft.allDay,
        // Starts on the rows' text column -- the 28dp icon box plus its 12dp
        // spacer -- so the when-block reads as one of the fields rather than
        // as something hanging in the icon gutter, and ends the same distance
        // in. The block is symmetric about its own middle, so an inset on one
        // side only pushed the whole thing -- and the span rule with it --
        // off the card's centre.
        modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 14.dp, bottom = 14.dp),
        onStartDate = pickStartDate,
        onStartTime = if (draft.allDay) null else pickStartTime,
        // An end has nothing to hang off until the start is set.
        onEndDate = if (!draft.allDay && draft.startTime != null) pickEndDate else null,
        onEndTime = if (!draft.allDay && draft.startTime != null) pickEndTime else null,
        // Across two zones the wall clocks no longer subtract to the length.
        spanMinutes = draft.durationMinutes.takeIf { draft.endZoneId != null },
    )
}

private enum class ZoneTarget { Start, End }

/** Results shown per query; a typed word narrows 400-odd zones to a handful. */
private const val ZoneResultLimit = 24

/**
 * The event's time zone, and optionally a separate one for its end.
 *
 * The times above are wall times in these zones, so changing one keeps the
 * numbers and moves the moment -- what a person fixing "this call is 09:00
 * New York time" means.
 */
@Composable
private fun EventZoneSection(draft: EditorDraft, onDraft: (EditorDraft) -> Unit) {
    var target by remember(draft.editingId) { mutableStateOf<ZoneTarget?>(null) }
    var endSeparate by remember(draft.editingId) { mutableStateOf(draft.endZoneId != null) }
    val startAt = draft.startTime?.let { draft.date.atTime(it).atZone(draft.frameZone()).toInstant() } ?: Instant.now()
    val endAt = draft.endTime?.let { draft.endDate.atTime(it).atZone(draft.endFrameZone()).toInstant() } ?: startAt
    Column(Modifier.fillMaxWidth()) {
        EditorDivider()
        EditorValueRow(
            icon = CalinoIcon.Globe,
            label = if (endSeparate) "Start time zone" else "Time zone",
            value = CalinoZones.label(draft.frameZone(), startAt),
            onClick = { target = if (target == ZoneTarget.Start) null else ZoneTarget.Start },
        )
        EditorReveal(target == ZoneTarget.Start) {
            EditorChoiceBlock {
                ZonePicker(draft.frameZone(), startAt) { zone ->
                    onDraft(draft.withZone(zone))
                    target = null
                }
                CalinoToggleRow("Separate end time zone", endSeparate) { on ->
                    endSeparate = on
                    if (on) {
                        target = ZoneTarget.End
                    } else {
                        onDraft(draft.withEndZone(null))
                    }
                }
            }
        }
        EditorReveal(endSeparate) {
            Column(Modifier.fillMaxWidth()) {
                EditorValueRow(
                    icon = CalinoIcon.Globe,
                    label = "End time zone",
                    value = CalinoZones.label(draft.endFrameZone(), endAt),
                    onClick = { target = if (target == ZoneTarget.End) null else ZoneTarget.End },
                )
                EditorReveal(target == ZoneTarget.End) {
                    EditorChoiceBlock {
                        ZonePicker(draft.endFrameZone(), endAt) { zone ->
                            onDraft(draft.withEndZone(zone))
                            target = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ZonePicker(selected: ZoneId, at: Instant, onPick: (ZoneId) -> Unit) {
    var query by remember { mutableStateOf("") }
    val device = remember { ZoneId.systemDefault() }
    val results = remember(query) {
        if (query.isBlank()) listOf(device, selected).distinct() else CalinoZones.search(query, at).take(ZoneResultLimit)
    }
    CalinoSearchField(
        query = query,
        onQueryChanged = { query = it },
        placeholder = "Search city or zone",
        contentDescription = "Search time zones",
        modifier = Modifier.fillMaxWidth(),
    )
    Column(Modifier.fillMaxWidth()) {
        results.forEach { zone ->
            ZoneOption(zone, at, selected = zone == selected, device = zone == device) { onPick(zone) }
        }
        if (results.isEmpty()) {
            Text(
                "No matching time zone",
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                modifier = Modifier.heightIn(min = 44.dp).padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ZoneOption(zone: ZoneId, at: Instant, selected: Boolean, device: Boolean, onClick: () -> Unit) {
    val city = CalinoZones.city(zone)
    val offset = CalinoZones.offsetLabel(zone, at)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(city, CalinoZones.longName(zone, at), offset, "device zone".takeIf { device })
                    .joinToString(", ")
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(city, style = CalinoTypography.bodyLarge, color = CalinoColors.Ink, maxLines = 1)
            Text(
                listOfNotNull(zone.id.substringBefore('/').takeIf { zone.id.contains('/') }, "This device".takeIf { device })
                    .joinToString(" · ").ifEmpty { "Coordinated Universal Time" },
                style = CalinoTypography.labelSmall,
                color = CalinoColors.Ink3,
                maxLines = 1,
            )
        }
        Text(offset, style = CalinoTypography.labelSmall, color = CalinoColors.Ink2, maxLines = 1)
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) {
            if (selected) {
                CalinoIcon(CalinoIcon.Check, tint = CalinoColors.Accent, modifier = Modifier.size(16.dp), contentDescription = null)
            }
        }
    }
}

@Composable
private fun EditorChoiceBlock(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@Composable
private fun EditorValueRow(
    icon: calino.malinov.ski.ui.components.CalinoIcon,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val pressModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(pressModifier)
            .semantics(mergeDescendants = true) {
                contentDescription = if (label == value) label else "$label: $value"
                if (!enabled) stateDescription = "Unavailable"
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            CalinoIcon(icon, tint = CalinoColors.Ink3, modifier = Modifier.size(19.dp), contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            Modifier.weight(1f),
            style = CalinoTypography.bodyLarge.copy(fontSize = 15.5.sp),
            color = if (enabled) CalinoColors.Ink else CalinoColors.Ink3,
            maxLines = 2,
        )
        trailing()
    }
}

@Composable
private fun EditorSwitchRow(icon: calino.malinov.ski.ui.components.CalinoIcon, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = if (checked) "On" else "Off"
                role = Role.Switch
            }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            CalinoIcon(icon, tint = CalinoColors.Ink3, modifier = Modifier.size(19.dp), contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = CalinoTypography.bodyLarge.copy(fontSize = 15.5.sp))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = CalinoColors.Accent,
                checkedThumbColor = CalinoColors.Panel,
                uncheckedTrackColor = CalinoColors.Ink3.copy(.26f),
                uncheckedBorderColor = Color.Transparent,
                uncheckedThumbColor = CalinoColors.Panel,
            ),
        )
    }
}

@Composable
private fun EditorTextRow(
    icon: calino.malinov.ski.ui.components.CalinoIcon,
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            CalinoIcon(icon, tint = CalinoColors.Ink3, modifier = Modifier.size(19.dp), contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label, editable" },
            textStyle = CalinoTypography.bodyLarge.copy(fontSize = 15.5.sp, color = CalinoColors.Ink),
            cursorBrush = SolidColor(CalinoColors.Accent),
            singleLine = true,
            maxLines = 1,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isBlank()) Text(placeholder, color = CalinoColors.Ink3, style = CalinoTypography.bodyLarge.copy(fontSize = 15.5.sp))
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun CalendarRow(draft: EditorDraft, calendars: List<CalinoCalendar>, onDraft: (EditorDraft) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = calendars.firstOrNull { it.id == draft.calendarId } ?: calendars.first()
    Box(Modifier.fillMaxWidth()) {
        EditorValueRow(CalinoIcon.Calendar, "Calendar", current.name, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            calendars.forEach { calendar ->
                DropdownMenuItem(
                    text = { Text(calendar.name) },
                    onClick = {
                        onDraft(
                            draft.copy(
                                calendarId = calendar.id,
                                color = if (calino.malinov.ski.platform.AndroidCalendarId.isImported(calendar.id)) {
                                    calendar.color
                                } else draft.color,
                            ),
                        )
                        open = false
                    },
                )
            }
        }
    }
}

private fun reminderSummary(reminders: List<Reminder>): String = when {
    reminders.isEmpty() -> "Add reminder"
    reminders.size == 1 -> reminders.first().let { reminder ->
        (reminder.absoluteAt?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US))
            ?: formatReminder(reminder.minutesBefore)) +
            (if (reminder.relativeToStart) " · from start" else "") +
            (if (reminder.repeatCount > 0) " · repeats ${reminder.repeatCount}× every ${reminder.repeatIntervalMinutes} min" else "")
    }
    else -> "${reminders.size} reminders"
}

@Composable
private fun DescriptionSection(
    draft: EditorDraft,
    open: Boolean,
    onOpen: () -> Unit,
    onDraft: (EditorDraft) -> Unit,
) {
    val fieldLabel = if (draft.kind == PocQuickAddKind.Task) "Notes" else "Description"
    EditorValueRow(
        icon = calino.malinov.ski.ui.components.CalinoIcon.Note,
        label = fieldLabel,
        // Once expanded, the editor below owns the value. Repeating its first
        // two raw Markdown lines here made the description appear duplicated.
        value = if (open) {
            fieldLabel
        } else {
            draft.description?.takeIf { it.isNotBlank() } ?: "Add ${fieldLabel.lowercase(Locale.US)}"
        },
        onClick = onOpen,
    )
    EditorReveal(open) {
        CalinoMarkdownEditor(
            value = draft.description.orEmpty(),
            onValueChange = { onDraft(draft.copy(description = it)) },
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 12.dp),
            label = fieldLabel,
            placeholder = "Add more detail",
            // The expanded row immediately above already names this field.
            showLabel = false,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceEditor(draft: EditorDraft, onDraft: (EditorDraft) -> Unit, pickUntil: () -> Unit) {
    val freq = recurrenceFreqOf(draft.recurrence) ?: RecurrenceFreq.Weekly
    val days = recurrenceDaysOf(draft.recurrence)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLabel("Repeat")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CalinoChip(
                text = "Never",
                selected = draft.recurrence == null,
                description = "Do not repeat",
                semanticsRole = Role.RadioButton,
                onClick = { onDraft(draft.copy(recurrence = null, recurrenceChanged = true)) },
            )
            RecurrenceFreq.entries.forEach { entry ->
                CalinoChip(
                    text = entry.name,
                    selected = entry == freq,
                    description = "Repeat ${entry.name.lowercase(Locale.US)}",
                    semanticsRole = Role.RadioButton,
                    onClick = {
                        onDraft(
                            draft.copy(
                                recurrence = recurrenceRule(entry, days, untilOf(draft.recurrence)),
                                recurrenceChanged = true,
                            ),
                        )
                    },
                )
            }
        }
        EditorReveal(freq == RecurrenceFreq.Weekly) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val on = day in days
                    CalinoChip(
                        text = day.getDisplayName(TextStyle.SHORT, Locale.US),
                        selected = on,
                        description = "Repeat on ${day.getDisplayName(TextStyle.FULL, Locale.US)}",
                        semanticsRole = Role.Checkbox,
                        onClick = {
                            val next = if (on) days - day else days + day
                            onDraft(
                                draft.copy(
                                    recurrence = recurrenceRule(
                                        RecurrenceFreq.Weekly,
                                        next.ifEmpty { setOf(draft.date.dayOfWeek) },
                                        untilOf(draft.recurrence),
                                    ),
                                    recurrenceChanged = true,
                                ),
                            )
                        },
                    )
                }
            }
        }
        EditorValueRow(
            icon = calino.malinov.ski.ui.components.CalinoIcon.Calendar,
            label = "Ends",
            value = untilOf(draft.recurrence)?.format(EditorDateFormat) ?: "Never",
            onClick = pickUntil,
        )
        Text(formatRecurrenceRule(draft.recurrence, draft.date), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceScopeSelector(draft: EditorDraft, onDraft: (EditorDraft) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        EditorLabel("Apply changes to")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            val scopes = if (draft.kind == PocQuickAddKind.Task || draft.providerRecurring) {
                listOf(RecurrenceEditScope.This, RecurrenceEditScope.All)
            } else {
                RecurrenceEditScope.entries
            }
            scopes.forEach { scope ->
                val label = when (scope) {
                    RecurrenceEditScope.This -> if (draft.kind == PocQuickAddKind.Task) "This task" else "This event"
                    RecurrenceEditScope.Future -> "This and future"
                    RecurrenceEditScope.All -> "Entire series"
                }
                CalinoChip(
                    text = label,
                    selected = draft.recurrenceScope == scope,
                    description = "Apply recurrence edit to $label",
                    semanticsRole = Role.RadioButton,
                    onClick = { onDraft(draft.copy(recurrenceScope = scope)) },
                )
            }
        }
        Text(
            when (draft.recurrenceScope) {
                RecurrenceEditScope.This -> "Only this occurrence changes."
                RecurrenceEditScope.Future -> "This occurrence and later occurrences change."
                RecurrenceEditScope.All -> "Every occurrence in the series changes."
            },
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSection(
    draft: EditorDraft,
    categories: List<String>,
    single: Boolean,
    onDraft: (EditorDraft) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLabel(if (single) "Category" else "Categories")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            categories.forEach { category ->
                val on = category in draft.categories
                CalinoChip(
                    text = category,
                    selected = on,
                    description = if (single) "Choose category" else "Toggle category",
                    semanticsRole = if (single) Role.RadioButton else Role.Checkbox,
                    onClick = { onDraft(draft.withCategoryToggled(category, single)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreSection(
    draft: EditorDraft,
    open: Boolean,
    categories: List<String>,
    onToggle: () -> Unit,
    relatedCandidates: List<Pair<String, String>>,
    attendeeInput: String,
    onAttendeeInput: (String) -> Unit,
    onDraft: (EditorDraft) -> Unit,
    providerOwned: Boolean,
) {
    EditorValueRow(
        icon = calino.malinov.ski.ui.components.CalinoIcon.More,
        label = "More options",
        value = if (open) "Fewer options" else "More options",
        onClick = onToggle,
    )
    EditorReveal(open) {
        Column(
            Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorSwitchRow(CalinoIcon.Clock, "Available", draft.availability == Availability.Free) { free ->
                onDraft(draft.copy(availability = if (free) Availability.Free else Availability.Busy))
            }
            if (providerOwned) {
                Text(
                    "Travel time, categories, task relationships, attendees, and event color are managed by the owning app.",
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                )
            } else {
                EditorLabel("Travel time")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    TravelTimeChoices.forEach { minutes ->
                        CalinoChip(
                            text = minutes?.let(::formatEditorDuration) ?: "None",
                            selected = draft.travelTimeMinutes == minutes,
                            description = "Travel time",
                            semanticsRole = Role.RadioButton,
                            onClick = { onDraft(draft.copy(travelTimeMinutes = minutes)) },
                        )
                    }
                }
                if (categories.isNotEmpty()) CategoriesSection(draft, categories, single = false, onDraft = onDraft)
                if (relatedCandidates.isNotEmpty()) {
                    EditorLabel("Related to")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        relatedCandidates.forEach { (id, label) ->
                            val on = id in draft.relatedTo
                            CalinoChip(
                                text = label,
                                selected = on,
                                description = "Attach task",
                                semanticsRole = Role.Checkbox,
                                onClick = {
                                    onDraft(draft.copy(relatedTo = if (on) draft.relatedTo - id else draft.relatedTo + id))
                                },
                            )
                        }
                    }
                }
                EditorLabel("Attendees")
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalinoTextField(
                        value = attendeeInput,
                        onValueChange = onAttendeeInput,
                        label = "Attendee email",
                        placeholder = "Add attendee email…",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = attendeeInput.contains('@'),
                        onClick = {
                            val email = attendeeInput.trim()
                            onDraft(draft.copy(attendees = draft.attendees + Attendee(email.substringBefore('@'), email)))
                            onAttendeeInput("")
                        },
                    ) { Text("Add") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    draft.attendees.forEach { attendee ->
                        CalinoChip(
                            text = attendee.name,
                            selected = true,
                            description = "Remove ${attendee.email}",
                            onClick = { onDraft(draft.copy(attendees = draft.attendees - attendee)) },
                        )
                    }
                }
                CalinoColorSwatchRow(Color(draft.color)) { picked ->
                    onDraft(draft.copy(color = picked.toArgb().toLong() and 0xffffffffL))
                }
            }
        }
    }
}

/** The editor's event reminder choices, for the read-only card's device-only reminder. */
@Composable
internal fun EventReminderChips(reminders: List<Reminder>, onChange: (List<Reminder>) -> Unit) =
    ReminderChips(reminders, single = false, onChange = onChange)

internal fun eventReminderSummary(reminders: List<Reminder>): String = reminderSummary(reminders)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderChips(reminders: List<Reminder>, single: Boolean, onChange: (List<Reminder>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ReminderChoices.forEach { minutes ->
            val reminder = Reminder(minutes)
            val on = reminders.any { it.absoluteAt == null && it.minutesBefore == minutes }
            CalinoChip(
                text = formatReminder(minutes),
                selected = on,
                description = "Reminder",
                semanticsRole = if (single) Role.RadioButton else Role.Checkbox,
                onClick = {
                    onChange(
                        when {
                            single -> if (on) emptyList() else listOf(reminder)
                            on -> reminders.filterNot { it.absoluteAt == null && it.minutesBefore == minutes }
                            else -> reminders + reminder
                        },
                    )
                },
            )
        }
        if (single && reminders.firstOrNull()?.absoluteAt != null) {
            CalinoChip(
                text = "At ${reminderSummary(reminders).substringBefore(" · repeats")}",
                selected = true,
                description = "Remove exact-time reminder",
                semanticsRole = Role.Checkbox,
                onClick = { onChange(emptyList()) },
            )
        }
        if (single && reminders.isNotEmpty()) {
            val current = reminders.first()
            CalinoChip(
                text = if (current.repeatCount > 0) "Repeat ${current.repeatCount}× / ${current.repeatIntervalMinutes} min" else "Repeat once / 10 min",
                selected = current.repeatCount > 0,
                description = "Repeat task reminder",
                semanticsRole = Role.Checkbox,
                onClick = {
                    onChange(listOf(if (current.repeatCount > 0) current.copy(repeatCount = 0, repeatIntervalMinutes = 0)
                        else current.copy(repeatCount = 1, repeatIntervalMinutes = 10)))
                },
            )
        }
    }
}

private fun untilOf(rule: String?): LocalDate? = rule
    ?.uppercase(Locale.US)
    ?.split(';')
    ?.firstNotNullOfOrNull { part -> part.removePrefix("UNTIL=").takeIf { it != part } }
    ?.take(8)
    ?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() }

internal fun formatEditorDuration(minutes: Int): String = formatCalinoDuration(minutes)

private fun formatReminder(minutes: Int): String = when {
    minutes == 0 -> "At time"
    minutes >= 24 * 60 -> "1 day before"
    else -> "${formatEditorDuration(minutes)} before"
}

/** Re-runs the keyword rules only when this edit changed the title. */
private fun EditorDraft.rematchedFrom(previous: EditorDraft, rules: List<AutoCategoryRule>): EditorDraft =
    if (title == previous.title) this else withAutoCategories(rules)
