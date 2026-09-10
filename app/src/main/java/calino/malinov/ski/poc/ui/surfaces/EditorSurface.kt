package calino.malinov.ski.poc.ui.surfaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.data.model.Attendee
import calino.malinov.ski.poc.data.model.Availability
import calino.malinov.ski.poc.data.model.EditorDraft
import calino.malinov.ski.poc.data.model.EditorField
import calino.malinov.ski.poc.data.model.RecurrenceFreq
import calino.malinov.ski.poc.data.model.Reminder
import calino.malinov.ski.poc.data.model.applyInput
import calino.malinov.ski.poc.data.model.recurrenceDaysOf
import calino.malinov.ski.poc.data.model.recurrenceFreqOf
import calino.malinov.ski.poc.data.model.recurrenceRule
import calino.malinov.ski.poc.data.parser.PocQuickAddKind
import calino.malinov.ski.poc.data.repository.CalinoCalendar
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.design.CalinoShapes
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.ui.components.BottomDetailCard
import calino.malinov.ski.poc.ui.components.CalinoChip
import calino.malinov.ski.poc.ui.components.CalinoColorSwatchRow
import calino.malinov.ski.poc.ui.components.CalinoIcon
import calino.malinov.ski.poc.ui.components.CalinoTextField
import calino.malinov.ski.poc.ui.components.EditorLabel
import calino.malinov.ski.poc.ui.components.EditorReveal
import calino.malinov.ski.poc.ui.components.rememberDatePicker
import calino.malinov.ski.poc.ui.components.rememberTimePicker
import calino.malinov.ski.poc.state.CalinoSurfaceKind
import calino.malinov.ski.poc.util.formatRecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import calino.malinov.ski.poc.state.LocalCalinoPreferences
import calino.malinov.ski.poc.state.LocalTimeFormat
import calino.malinov.ski.poc.util.formatCalinoDuration
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
    onDismiss: () -> Unit = {},
    onSave: (EditorDraft) -> Unit = {},
    visible: Boolean = true,
    morphFromAddPill: Boolean = false,
) {
    // The length a line with no stated end falls back to, which the parser
    // re-applies on every keystroke.
    val defaultDurationMinutes = LocalCalinoPreferences.current.defaultDuration.minutes
    var draft by remember(initial.editingId, initial.kind) { mutableStateOf(initial) }
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

    val isEvent = draft.kind == PocQuickAddKind.Event
    val isTask = draft.kind == PocQuickAddKind.Task

    val pickStartDate = rememberDatePicker({ draft.date }) {
        draft = draft.copy(date = it, touched = draft.touched + EditorField.Date)
    }
    val pickStartTime = rememberTimePicker({ draft.startTime }) {
        draft = draft.copy(startTime = it, touched = draft.touched + EditorField.Time)
    }
    val pickEndDate = rememberDatePicker({ draft.endDate }) { picked ->
        draft.endTime?.let { draft = draft.withEnd(picked, it) }
    }
    val pickEndTime = rememberTimePicker({ draft.endTime }) { picked ->
        draft = draft.withEnd(draft.endDate, picked)
    }
    val pickUntil = rememberDatePicker({ draft.date.plusMonths(3) }) { picked ->
        val freq = recurrenceFreqOf(draft.recurrence) ?: RecurrenceFreq.Weekly
        draft = draft.copy(recurrence = recurrenceRule(freq, recurrenceDaysOf(draft.recurrence), picked))
    }

    BottomDetailCard(
        visible = shown,
        onDismiss = dismiss,
        modifier = Modifier.fillMaxSize(),
        canStartDismiss = { editorScrollState.value == 0 },
        surfaceKind = CalinoSurfaceKind.Editor,
    ) { detailModifier ->
        Column(detailModifier.fillMaxSize().background(CalinoColors.Canvas)) {
            EditorHeader(draft, dismiss)

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
                        }
                    }

                    EditorTitleField(draft) { input ->
                        draft = draft.applyInput(input, baseDate, defaultDurationMinutes)
                    }
                    EditorDivider()

                    when {
                        isEvent -> EventEditorFields(
                            draft = draft,
                            calendars = calendars,
                            categories = categories,
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
                        isTask -> TaskEditorFields(
                            draft = draft,
                            categories = categories,
                            descriptionOpen = descriptionOpen,
                            remindersOpen = remindersOpen,
                            onDescriptionOpen = { descriptionOpen = !descriptionOpen },
                            onRemindersOpen = { remindersOpen = !remindersOpen },
                            onDraft = { draft = it },
                            pickStartDate = pickStartDate,
                            pickStartTime = pickStartTime,
                        )
                        else -> JournalEditorFields(draft) { body -> draft = draft.copy(body = body) }
                    }

                    // The action pill floats above this reserved tail, matching
                    // the main add pill without hiding the final form row.
                    Spacer(Modifier.height(CalinoSpacing.PillClearance))
                }

                EditorActions(
                    canSave = draft.canSave(),
                    addLabel = when (draft.kind) {
                        PocQuickAddKind.Event -> "Add on ${draft.date.format(EditorDateFormat)}"
                        PocQuickAddKind.Task -> "New task"
                        PocQuickAddKind.Journal -> "New entry"
                    },
                    morphFromAddPill = morphFromAddPill && !draft.isEditing,
                    onCancel = dismiss,
                    onSave = { val saved = draft; closeAfterAnimation { onSave(saved) } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun EditorHeader(draft: EditorDraft, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (draft.isEditing) "Edit ${draft.kind.name.lowercase(Locale.US)}" else "New ${draft.kind.name.lowercase(Locale.US)}",
            modifier = Modifier.weight(1f),
            style = CalinoTypography.titleSmall.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                color = CalinoColors.Accent,
            ),
        )
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
private fun EditorActions(
    canSave: Boolean,
    addLabel: String,
    morphFromAddPill: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingAddPill by remember(morphFromAddPill, addLabel) { mutableStateOf(morphFromAddPill) }
    LaunchedEffect(morphFromAddPill, addLabel) {
        showingAddPill = morphFromAddPill
        if (morphFromAddPill) {
            delay((CalinoMotion.ContentEnterMillis / 3).toLong())
            showingAddPill = false
        }
    }
    val pillWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (showingAddPill) 218.dp else 242.dp,
        animationSpec = androidx.compose.animation.core.tween(
            CalinoMotion.ContentEnterMillis + CalinoMotion.FadeThroughMillis,
        ),
        label = "editor action pill width",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(pillWidth)
                .height(56.dp)
                .shadow(14.dp * CalinoColors.elevationAlpha, RoundedCornerShape(CalinoShapes.Pill), clip = false)
                .clip(RoundedCornerShape(CalinoShapes.Pill))
                .background(CalinoColors.FloatFill)
                .border(1.dp, CalinoColors.FloatBorder, RoundedCornerShape(CalinoShapes.Pill)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = showingAddPill,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(CalinoMotion.FadeThroughMillis)) + scaleIn(initialScale = .94f)) togetherWith
                        (fadeOut(tween(CalinoMotion.FadeThroughMillis)) + scaleOut(targetScale = .94f))
                },
                label = "add pill to editor actions",
            ) { addMode ->
                if (addMode) {
                    Row(
                        Modifier.fillMaxSize().padding(start = 16.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CalinoIcon(CalinoIcon.Plus, tint = CalinoColors.OnFloat, modifier = Modifier.size(19.dp), contentDescription = null)
                        Text(
                            addLabel,
                            color = CalinoColors.OnFloat,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                } else {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Cancel editor" },
                        ) {
                            Text("Cancel", color = CalinoColors.OnFloat, style = CalinoTypography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        Box(Modifier.width(1.dp).height(22.dp).background(CalinoColors.OnFloat.copy(alpha = .28f)))
                        TextButton(
                            enabled = canSave,
                            onClick = onSave,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Save editor" },
                        ) {
                            Text(
                                "Save",
                                style = CalinoTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = CalinoColors.OnFloat.copy(alpha = if (canSave) 1f else .45f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorTitleField(draft: EditorDraft, onInput: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(vertical = 10.dp),
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
    PocQuickAddKind.Event -> calino.malinov.ski.poc.ui.components.CalinoIcon.Calendar
    PocQuickAddKind.Task -> calino.malinov.ski.poc.ui.components.CalinoIcon.Check
    PocQuickAddKind.Journal -> calino.malinov.ski.poc.ui.components.CalinoIcon.Note
}

@Composable
private fun KindSelector(selected: PocQuickAddKind, onSelect: (PocQuickAddKind) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(CalinoShapes.Pill))
            .background(CalinoColors.Ink.copy(.05f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PocQuickAddKind.entries.forEach { entry ->
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(CalinoShapes.Pill))
                    .background(if (selected == entry) CalinoColors.Panel else Color.Transparent)
                    .clickable(onClick = { onSelect(entry) })
                    .semantics {
                        contentDescription = entry.name
                        stateDescription = if (selected == entry) "Selected" else "Not selected"
                        role = Role.RadioButton
                    },
                contentAlignment = Alignment.Center,
            ) { Text(entry.name, style = CalinoTypography.labelMedium, color = CalinoColors.Ink) }
        }
    }
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
    EditorValueRow(
        icon = CalinoIcon.Repeat,
        label = "Repeat",
        value = draft.recurrence?.let { formatRecurrenceRule(it, draft.date) } ?: "Don't repeat",
        onClick = onRecurrenceOpen,
    )
    EditorReveal(recurrenceOpen) {
        EditorChoiceBlock { RecurrenceEditor(draft, onDraft, pickUntil) }
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
    )
}

@Composable
private fun TaskEditorFields(
    draft: EditorDraft,
    categories: List<String>,
    descriptionOpen: Boolean,
    remindersOpen: Boolean,
    onDescriptionOpen: () -> Unit,
    onRemindersOpen: () -> Unit,
    onDraft: (EditorDraft) -> Unit,
    pickStartDate: () -> Unit,
    pickStartTime: () -> Unit,
) {
    EditorValueRow(CalinoIcon.Calendar, "Due date", draft.date.format(EditorDateFormat), pickStartDate)
    EditorDivider()
    EditorValueRow(
        icon = CalinoIcon.Clock,
        label = "Due time",
        value = draft.startTime?.let { LocalTimeFormat.format(it) } ?: "Add time",
        onClick = pickStartTime,
    )
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
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTimeColumn(
            label = "Start",
            date = draft.date,
            time = if (draft.allDay) "All day" else draft.startTime?.let { LocalTimeFormat.format(it) } ?: "Add time",
            modifier = Modifier.weight(1f),
            dateEnabled = true,
            timeEnabled = !draft.allDay,
            onDate = pickStartDate,
            onTime = pickStartTime,
        )
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            Text("→", color = CalinoColors.Ink3, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
        DateTimeColumn(
            label = "End",
            date = draft.endDate,
            time = if (draft.allDay) "All day" else draft.endTime?.let { LocalTimeFormat.format(it) } ?: "—",
            modifier = Modifier.weight(1f),
            dateEnabled = !draft.allDay && draft.startTime != null,
            timeEnabled = !draft.allDay && draft.startTime != null,
            onDate = pickEndDate,
            onTime = pickEndTime,
        )
    }
}

@Composable
private fun DateTimeColumn(
    label: String,
    date: LocalDate,
    time: String,
    modifier: Modifier,
    dateEnabled: Boolean,
    timeEnabled: Boolean,
    onDate: () -> Unit,
    onTime: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            date.format(EditorDateFormat),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(enabled = dateEnabled, role = Role.Button, onClick = onDate)
                .semantics { contentDescription = "$label date, ${date.format(EditorDateFormat)}" },
            style = CalinoTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = if (dateEnabled) CalinoColors.Ink else CalinoColors.Ink3,
            textAlign = TextAlign.Center,
        )
        Text(
            time,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(enabled = timeEnabled, role = Role.Button, onClick = onTime)
                .semantics { contentDescription = "$label time, $time" },
            style = CalinoTypography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 23.sp),
            color = if (timeEnabled) CalinoColors.Ink else CalinoColors.Ink3,
            textAlign = TextAlign.Center,
        )
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
    icon: calino.malinov.ski.poc.ui.components.CalinoIcon,
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
                contentDescription = "$label: $value"
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
private fun EditorSwitchRow(icon: calino.malinov.ski.poc.ui.components.CalinoIcon, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
    icon: calino.malinov.ski.poc.ui.components.CalinoIcon,
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
                        onDraft(draft.copy(calendarId = calendar.id))
                        open = false
                    },
                )
            }
        }
    }
}

private fun reminderSummary(reminders: List<Reminder>): String = when {
    reminders.isEmpty() -> "Add reminder"
    reminders.size == 1 -> formatReminder(reminders.first().minutesBefore)
    else -> "${reminders.size} reminders"
}

@Composable
private fun DescriptionSection(
    draft: EditorDraft,
    open: Boolean,
    onOpen: () -> Unit,
    onDraft: (EditorDraft) -> Unit,
) {
    EditorValueRow(
        icon = calino.malinov.ski.poc.ui.components.CalinoIcon.Note,
        label = "Description",
        value = draft.description?.takeIf { it.isNotBlank() } ?: "Add description",
        onClick = onOpen,
    )
    EditorReveal(open) {
        BasicTextField(
            value = draft.description.orEmpty(),
            onValueChange = { onDraft(draft.copy(description = it)) },
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 12.dp).heightIn(min = 104.dp).semantics { contentDescription = "Description, editable" },
            textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink),
            cursorBrush = SolidColor(CalinoColors.Accent),
            minLines = 4,
            maxLines = 8,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth()) {
                    if (draft.description.isNullOrBlank()) Text("Add more detail", color = CalinoColors.Ink3, style = CalinoTypography.bodyLarge)
                    innerTextField()
                }
            },
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
            RecurrenceFreq.entries.forEach { entry ->
                CalinoChip(
                    text = entry.name,
                    selected = entry == freq,
                    description = "Repeat ${entry.name.lowercase(Locale.US)}",
                    semanticsRole = Role.RadioButton,
                    onClick = { onDraft(draft.copy(recurrence = recurrenceRule(entry, days, untilOf(draft.recurrence)))) },
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
                                ),
                            )
                        },
                    )
                }
            }
        }
        EditorValueRow(
            icon = calino.malinov.ski.poc.ui.components.CalinoIcon.Calendar,
            label = "Ends",
            value = untilOf(draft.recurrence)?.format(EditorDateFormat) ?: "Never",
            onClick = pickUntil,
        )
        Text(formatRecurrenceRule(draft.recurrence, draft.date), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
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
                    onClick = {
                        val next = when {
                            single -> if (on) emptyList() else listOf(category)
                            on -> draft.categories - category
                            else -> draft.categories + category
                        }
                        onDraft(draft.copy(categories = next))
                    },
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
) {
    EditorValueRow(
        icon = calino.malinov.ski.poc.ui.components.CalinoIcon.More,
        label = "More options",
        value = if (open) "Fewer options" else "More options",
        onClick = onToggle,
    )
    EditorReveal(open) {
        Column(Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EditorSwitchRow(CalinoIcon.Clock, "Available", draft.availability == Availability.Free) { free ->
                onDraft(draft.copy(availability = if (free) Availability.Free else Availability.Busy))
            }

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderChips(reminders: List<Reminder>, single: Boolean, onChange: (List<Reminder>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ReminderChoices.forEach { minutes ->
            val reminder = Reminder(minutes)
            val on = reminder in reminders
            CalinoChip(
                text = formatReminder(minutes),
                selected = on,
                description = "Reminder",
                semanticsRole = if (single) Role.RadioButton else Role.Checkbox,
                onClick = {
                    onChange(
                        when {
                            single -> if (on) emptyList() else listOf(reminder)
                            on -> reminders - reminder
                            else -> reminders + reminder
                        },
                    )
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
