package calino.malinov.ski.ui.surfaces

import calino.malinov.ski.data.caldav.uriFileName
import calino.malinov.ski.R
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.EditorDraft
import calino.malinov.ski.data.model.EventAttachment
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
import calino.malinov.ski.data.repository.accepts
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.ui.components.BottomDetailCard
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.CalinoColorSwatchRow
import calino.malinov.ski.ui.components.CalinoSearchField
import calino.malinov.ski.ui.components.CalinoToggleRow
import calino.malinov.ski.util.CalinoZones
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.localizedDateFormatter
import calino.malinov.ski.util.localizedDisplayFormatter
import calino.malinov.ski.util.LocalCalinoLocale
import calino.malinov.ski.util.startOfWeek
import java.time.Instant
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.CalinoMarkdownEditor
import calino.malinov.ski.ui.components.CalinoTextField
import calino.malinov.ski.ui.components.CompactSegmentedControl
import calino.malinov.ski.ui.components.EditorLabel
import calino.malinov.ski.ui.components.EditorReveal
import calino.malinov.ski.ui.components.ModalActionPill
import calino.malinov.ski.ui.components.ModalPillActionTone
import calino.malinov.ski.ui.components.rememberDatePicker
import calino.malinov.ski.ui.components.rememberTimePicker
import calino.malinov.ski.ui.components.WhenHero
import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.util.formatRecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import calino.malinov.ski.state.LocalCalinoPreferences
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.util.formatCalinoDuration
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

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
    val context = LocalContext.current
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
    val pickStartTime = rememberTimePicker({ draft.startTime }, title = if (draft.kind == PocQuickAddKind.Task) stringResource(R.string.ed_editor_due) else stringResource(R.string.ed_editor_starts)) {
        draft = draft.copy(startTime = it, taskDueChanged = draft.taskDueChanged || draft.kind == PocQuickAddKind.Task,
            touched = draft.touched + EditorField.Time)
    }
    val pickEndDate = rememberDatePicker({ draft.endDate }) { picked ->
        draft.endTime?.let { draft = draft.withEnd(picked, it) }
    }
    val pickEndTime = rememberTimePicker({ draft.endTime }, title = stringResource(R.string.ed_editor_ends)) { picked ->
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
                addLabel = addLabelFor(draft, context, localizedDateFormatter("EEE, d MMM")),
                morphFromAddPill = morphFromAddPill,
                // The same flag that widened the add pill on the way in runs
                // the move backwards here, so closing returns the pill to the
                // shape it came from instead of taking it away with the card.
                inPillLane = true,
                expanded = shown,
                cancelLabel = stringResource(R.string.ed_editor_cancel),
                onCancel = dismiss,
                primaryLabel = stringResource(R.string.ed_editor_save),
                primaryTone = ModalPillActionTone.Save,
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
                primaryDescription = stringResource(R.string.ed_editor_save_description),
                cancelDescription = stringResource(R.string.ed_editor_cancel_description),
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
                            calendars = calendars,
                            planningDate = baseDate,
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

private fun addLabelFor(draft: EditorDraft, context: android.content.Context, dateFormat: java.time.format.DateTimeFormatter): String = when (draft.kind) {
    PocQuickAddKind.Event -> if (draft.isEditing) {
        context.getString(R.string.ed_editor_edit_event)
    } else {
        context.getString(R.string.ed_editor_add_on_date, draft.date.format(dateFormat))
    }
    PocQuickAddKind.Task -> context.getString(R.string.ed_editor_new_task)
    PocQuickAddKind.Journal -> context.getString(R.string.ed_editor_new_entry)
}

@Composable
private fun EditorHeader(
    draft: EditorDraft,
    onInput: (String) -> Unit,
    onDismiss: () -> Unit,
    onPhoto: (() -> Unit)?,
) {
    val importPhotoDescription = stringResource(R.string.ed_editor_import_event_photo)
    val closeDescription = stringResource(R.string.ed_editor_close)
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
                    .semantics { contentDescription = importPhotoDescription },
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
                .semantics { contentDescription = closeDescription },
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
    val titleDescription = when (draft.kind) {
        PocQuickAddKind.Event -> stringResource(R.string.ed_editor_title_event)
        PocQuickAddKind.Task -> stringResource(R.string.ed_editor_title_task)
        PocQuickAddKind.Journal -> stringResource(R.string.ed_editor_title_journal)
    }
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
                contentDescription = titleDescription
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
                                PocQuickAddKind.Event -> stringResource(R.string.ed_editor_add_event_title)
                                PocQuickAddKind.Task -> stringResource(R.string.ed_editor_add_task_title)
                                PocQuickAddKind.Journal -> stringResource(R.string.ed_editor_add_note_title)
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
        options = listOf(stringResource(R.string.ed_editor_kind_event), stringResource(R.string.ed_editor_kind_task), stringResource(R.string.ed_editor_kind_journal)),
        selectedIndex = PocQuickAddKind.entries.indexOf(selected),
        onSelected = { onSelect(PocQuickAddKind.entries[it]) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        semanticLabel = stringResource(R.string.ed_editor_entry_kind),
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
    val context = LocalContext.current
    val allDay = stringResource(R.string.ed_editor_all_day)
    EditorSwitchRow(CalinoIcon.Clock, allDay, draft.allDay) { onDraft(draft.copy(allDay = it)) }
    EditorDivider()
    EventDateTimeSection(draft, onDraft, pickStartDate, pickStartTime, pickEndDate, pickEndTime)
    // A zone is a property of a time; an all-day event has none to carry.
    EditorReveal(!draft.allDay) { EventZoneSection(draft, onDraft) }
    EditorDivider()
    EditorTextRow(
        icon = CalinoIcon.Pin,
        label = stringResource(R.string.ed_editor_location),
        value = draft.location.orEmpty(),
        placeholder = stringResource(R.string.ed_editor_add_location),
        onValueChange = { onDraft(draft.copy(location = it, touched = draft.touched + EditorField.Location)) },
    )
    EditorDivider()
    if (calendars.isNotEmpty()) {
        CalendarRow(draft, calendars, onDraft)
        EditorDivider()
    }
    EditorValueRow(
        icon = CalinoIcon.Bell,
        label = stringResource(R.string.ed_editor_reminder),
        value = reminderSummary(draft.reminders, context, LocalTimeFormat, LocalCalinoLocale),
        onClick = onRemindersOpen,
    )
    EditorReveal(remindersOpen) {
        EditorChoiceBlock { ReminderChips(draft.reminders, single = false) { onDraft(draft.copy(reminders = it)) } }
    }
    EditorDivider()
    if (calino.malinov.ski.platform.AndroidCalendarId.isImported(draft.calendarId)) {
        Text(
            if (draft.providerRecurring) stringResource(R.string.ed_editor_provider_repeats)
            else stringResource(R.string.ed_editor_repeat_managed),
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    } else {
        EditorValueRow(
            icon = CalinoIcon.Repeat,
            label = stringResource(R.string.ed_editor_repeat),
            value = draft.recurrence?.let { formatRecurrenceRule(context, it, draft.date) } ?: stringResource(R.string.ed_editor_dont_repeat),
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
    calendars: List<CalinoCalendar>,
    planningDate: LocalDate,
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
    val context = LocalContext.current
    val editorDateFormat = localizedDateFormatter("EEE, d MMM")
    val priorityLabels = listOf(
        0 to stringResource(R.string.ed_priority_none), 1 to stringResource(R.string.ed_priority_high),
        5 to stringResource(R.string.ed_priority_medium), 9 to stringResource(R.string.ed_priority_low),
    )
    val weekStart = calino.malinov.ski.state.LocalCalinoPreferences.current.weekStart
    val recurring = draft.recurrence != null || draft.recurrenceId != null || draft.recurrenceDate != null
    val pickTaskStart = rememberDatePicker({ draft.taskStartDate ?: draft.date.minusDays(1) }) {
        onDraft(draft.copy(taskStartDate = it, taskStartTime = null))
    }
    val due = draft.date.takeUnless { draft.taskDueAbsent && !draft.taskDueChanged }
    TaskRangeFields(
        start = draft.taskStartDate,
        due = due,
        recurring = recurring,
        onStart = pickTaskStart,
        onDue = pickStartDate,
        onClearStart = { onDraft(draft.copy(taskStartDate = null, taskStartTime = null)) },
        onWeek = {
            val first = planningDate.startOfWeek(weekStart)
            onDraft(draft.copy(taskStartDate = first, taskStartTime = null, date = first.plusDays(6),
                startTime = null, allDay = true, taskDueAbsent = false, taskDueChanged = true,
                touched = draft.touched + calino.malinov.ski.data.model.EditorField.Date + calino.malinov.ski.data.model.EditorField.Time))
        },
        folded = true,
        onClearDue = {
            onDraft(draft.copy(taskDueAbsent = true, taskDueChanged = false,
                startTime = null, taskStartDate = null, taskStartTime = null))
        },
        // Clear, Add start and This week sit behind the chevron the block draws.
        // The due time shares the date's line; a range swaps that line for its
        // Start and Due fields, so the time keeps a row of its own beneath.
        dueRow = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    EditorValueRow(CalinoIcon.Calendar, stringResource(R.string.ed_editor_due_date),
                        if (due == null) stringResource(R.string.ed_editor_add_due_date) else draft.date.format(editorDateFormat),
                        pickStartDate)
                }
                DueTimeAction(draft.startTime, pickStartTime)
            }
        },
    )
    if (draft.taskStartDate != null && due != null) {
        EditorDivider()
        EditorValueRow(
            icon = CalinoIcon.Clock,
            label = stringResource(R.string.ed_editor_due_time),
            value = draft.startTime?.let { LocalTimeFormat.format(it, LocalCalinoLocale) } ?: stringResource(R.string.ed_editor_add_time),
            onClick = pickStartTime,
        )
    }
    EditorDivider()
    TaskCalendarRow(draft, calendars, onDraft)
    EditorDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLabel(stringResource(R.string.ed_editor_priority))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            priorityLabels.forEach { (value, label) ->
                CalinoChip(
                    text = label,
                    selected = draft.priority == value,
                    description = stringResource(R.string.ed_editor_set_priority, label.lowercase(LocalCalinoLocale)),
                    semanticsRole = Role.RadioButton,
                    onClick = { onDraft(draft.copy(priority = value)) },
                )
            }
        }
    }
    EditorDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorLabel(stringResource(R.string.ed_editor_progress, draft.percentComplete))
        calino.malinov.ski.ui.components.CalinoProgressSlider(
            percent = draft.percentComplete,
            onPercentChange = { onDraft(draft.copy(percentComplete = it, taskStatus = if (it > 0) "IN-PROCESS" else "NEEDS-ACTION")) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    EditorDivider()
    if (draft.parentTaskId == null) {
        EditorValueRow(CalinoIcon.Repeat, stringResource(R.string.ed_editor_repeat),
            draft.recurrence?.let { formatRecurrenceRule(context, it, draft.date) } ?: stringResource(R.string.ed_editor_dont_repeat), onRecurrenceOpen)
        EditorReveal(recurrenceOpen) { RecurrenceEditor(draft, onDraft, pickUntil) }
    } else {
        Text(
            stringResource(R.string.ed_editor_subtasks_no_repeat),
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
    EditorValueRow(CalinoIcon.Bell, stringResource(R.string.ed_editor_reminder), reminderSummary(draft.reminders, context, LocalTimeFormat, LocalCalinoLocale), onRemindersOpen)
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
    val bodyDescription = stringResource(R.string.ed_journal_body_semantics)
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Top) {
        CalinoIcon(CalinoIcon.Note, tint = CalinoColors.Ink3, modifier = Modifier.size(20.dp), contentDescription = null)
        Spacer(Modifier.size(14.dp))
        BasicTextField(
            value = draft.body,
            onValueChange = onBody,
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).semantics { contentDescription = bodyDescription },
            textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink),
            cursorBrush = SolidColor(CalinoColors.Accent),
            minLines = 6,
            maxLines = 12,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth()) {
                    if (draft.body.isBlank()) Text(stringResource(R.string.ed_journal_body_prompt), color = CalinoColors.Ink3, style = CalinoTypography.bodyLarge)
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
    onDraft: (EditorDraft) -> Unit,
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
        // A long press types the same values the pickers would set.
        onStartTimeTyped = { onDraft(draft.copy(startTime = it, touched = draft.touched + EditorField.Time)) },
        onStartDateTyped = { onDraft(draft.copy(date = it, touched = draft.touched + EditorField.Date)) },
        onEndTimeTyped = { onDraft(draft.withEnd(draft.endDate, it)) },
        onEndDateTyped = { picked -> draft.endTime?.let { onDraft(draft.withEnd(picked, it)) } },
        onSlide = { startBy, endBy -> draft.slid(startBy, endBy)?.let(onDraft) },
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
            label = if (endSeparate) stringResource(R.string.ed_zone_start) else stringResource(R.string.ed_zone),
            value = CalinoZones.label(draft.frameZone(), startAt),
            onClick = { target = if (target == ZoneTarget.Start) null else ZoneTarget.Start },
        )
        EditorReveal(target == ZoneTarget.Start) {
            EditorChoiceBlock {
                ZonePicker(draft.frameZone(), startAt) { zone ->
                    onDraft(draft.withZone(zone))
                    target = null
                }
                CalinoToggleRow(stringResource(R.string.ed_zone_separate_end), endSeparate) { on ->
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
                    label = stringResource(R.string.ed_zone_end),
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
        placeholder = stringResource(R.string.ed_zone_search_hint),
        contentDescription = stringResource(R.string.ed_zone_search),
        modifier = Modifier.fillMaxWidth(),
    )
    Column(Modifier.fillMaxWidth()) {
        results.forEach { zone ->
            ZoneOption(zone, at, selected = zone == selected, device = zone == device) { onPick(zone) }
        }
        if (results.isEmpty()) {
            Text(
                stringResource(R.string.ed_zone_no_match),
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
    val deviceZone = stringResource(R.string.ed_zone_device)
    val selectedDescription = stringResource(R.string.ed_selected)
    val notSelectedDescription = stringResource(R.string.ed_not_selected)
    val thisDevice = stringResource(R.string.ed_this_device)
    val utc = stringResource(R.string.ed_utc)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(city, CalinoZones.longName(zone, at), offset, deviceZone.takeIf { device })
                    .joinToString(", ")
                stateDescription = if (selected) selectedDescription else notSelectedDescription
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(city, style = CalinoTypography.bodyLarge, color = CalinoColors.Ink, maxLines = 1)
            Text(
                listOfNotNull(zone.id.substringBefore('/').takeIf { zone.id.contains('/') }, thisDevice.takeIf { device })
                    .joinToString(" · ").ifEmpty { utc },
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
    val context = LocalContext.current
    val unavailable = stringResource(R.string.ed_unavailable)
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
                contentDescription = if (label == value) label else context.getString(R.string.ed_labeled_value, label, value)
                if (!enabled) stateDescription = unavailable
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
    val onLabel = stringResource(R.string.ed_on)
    val offLabel = stringResource(R.string.ed_off)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = if (checked) onLabel else offLabel
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
    val editableDescription = stringResource(R.string.ed_editable_field, label)
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            CalinoIcon(icon, tint = CalinoColors.Ink3, modifier = Modifier.size(19.dp), contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = editableDescription },
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
        EditorValueRow(CalinoIcon.Calendar, stringResource(R.string.ed_editor_calendar), current.name, onClick = { open = true })
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

/** The due time as a quiet action at the end of the date line. */
@Composable
private fun DueTimeAction(time: LocalTime?, onClick: () -> Unit) {
    val context = LocalContext.current
    val text = time?.let { LocalTimeFormat.format(it, LocalCalinoLocale) } ?: stringResource(R.string.ed_editor_add_time)
    val changeDueTime = stringResource(R.string.ed_editor_change_due_time)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(CalinoShapes.Row))
            .clickable(role = Role.Button, onClickLabel = changeDueTime, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = context.getString(R.string.ed_editor_due_time_description, text) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CalinoIcon(CalinoIcon.Clock, tint = CalinoColors.Ink3, modifier = Modifier.size(17.dp), contentDescription = null)
        Text(text, style = CalinoTypography.bodyMedium, color = if (time == null) CalinoColors.Ink2 else CalinoColors.Ink)
    }
}

/**
 * Where a new task is filed. Only writable, non-device collections that accept
 * VTODO are offered. A saved task, or a subtask that must stay beside its
 * parent, shows its calendar without a menu: moving a VTODO between
 * collections is not supported, and the repository would ignore the choice.
 */
@Composable
private fun TaskCalendarRow(draft: EditorDraft, calendars: List<CalinoCalendar>, onDraft: (EditorDraft) -> Unit) {
    val eligible = remember(calendars) {
        calendars.filter {
            !it.readOnly && it.accepts("VTODO") && !calino.malinov.ski.platform.AndroidCalendarId.isImported(it.id)
        }
    }
    val fixed = draft.isEditing || draft.parentTaskId != null
    // A new draft starts on a placeholder id; name the calendar it will really
    // land in so the row and the saved task agree.
    LaunchedEffect(eligible, draft.calendarId, fixed) {
        if (!fixed && eligible.isNotEmpty() && eligible.none { it.id == draft.calendarId }) {
            onDraft(draft.copy(calendarId = eligible.first().id))
        }
    }
    if (fixed) {
        val current = calendars.firstOrNull { it.id == draft.calendarId } ?: return
        EditorValueRow(CalinoIcon.Calendar, stringResource(R.string.ed_editor_calendar), current.name)
    } else if (eligible.size > 1) {
        CalendarRow(draft, eligible, onDraft)
    } else if (eligible.size == 1) {
        EditorValueRow(CalinoIcon.Calendar, stringResource(R.string.ed_editor_calendar), eligible.first().name)
    }
}

private fun reminderSummary(
    reminders: List<Reminder>,
    context: android.content.Context,
    timeFormat: CalinoTimeFormat,
    locale: Locale,
): String = when {
    reminders.isEmpty() -> context.getString(R.string.ed_editor_add_reminder)
    reminders.size == 1 -> reminders.first().let { reminder ->
        (reminder.absoluteAt?.atZone(ZoneId.systemDefault())?.let { dateTime ->
            context.getString(
                R.string.ed_datetime_date_time,
                dateTime.format(localizedDisplayFormatter("MMM d", locale)),
                timeFormat.format(dateTime.toLocalDateTime(), locale),
            )
        }
            ?: formatReminder(reminder.minutesBefore, context)) +
            (if (reminder.relativeToStart) " · ${context.getString(R.string.ed_reminder_from_start)}" else "") +
            (if (reminder.repeatCount > 0) " · ${context.getString(R.string.ed_reminder_repeats_every, reminder.repeatCount, context.getString(R.string.fmt_minutes, reminder.repeatIntervalMinutes))}" else "")
    }
    else -> context.resources.getQuantityString(R.plurals.ed_reminder_count, reminders.size, reminders.size)
}

@Composable
private fun DescriptionSection(
    draft: EditorDraft,
    open: Boolean,
    onOpen: () -> Unit,
    onDraft: (EditorDraft) -> Unit,
) {
    val fieldLabel = if (draft.kind == PocQuickAddKind.Task) stringResource(R.string.ed_editor_notes) else stringResource(R.string.ed_editor_description)
    EditorValueRow(
        icon = calino.malinov.ski.ui.components.CalinoIcon.Note,
        label = fieldLabel,
        // Once expanded, the editor below owns the value. Repeating its first
        // two raw Markdown lines here made the description appear duplicated.
        value = if (open) {
            fieldLabel
        } else {
            draft.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ed_editor_add_field, fieldLabel.lowercase(LocalCalinoLocale))
        },
        onClick = onOpen,
    )
    EditorReveal(open) {
        CalinoMarkdownEditor(
            value = draft.description.orEmpty(),
            onValueChange = { onDraft(draft.copy(description = it)) },
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 12.dp),
            label = fieldLabel,
            placeholder = stringResource(R.string.ed_editor_more_detail),
            // The expanded row immediately above already names this field.
            showLabel = false,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceEditor(draft: EditorDraft, onDraft: (EditorDraft) -> Unit, pickUntil: () -> Unit) {
    val context = LocalContext.current
    val editorDateFormat = localizedDateFormatter("EEE, d MMM")
    val freq = recurrenceFreqOf(draft.recurrence) ?: RecurrenceFreq.Weekly
    val days = recurrenceDaysOf(draft.recurrence)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLabel(stringResource(R.string.ed_editor_repeat))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CalinoChip(
                text = stringResource(R.string.ed_repeat_never),
                selected = draft.recurrence == null,
                description = stringResource(R.string.ed_repeat_do_not),
                semanticsRole = Role.RadioButton,
                onClick = { onDraft(draft.copy(recurrence = null, recurrenceChanged = true)) },
            )
            RecurrenceFreq.entries.forEach { entry ->
                CalinoChip(
                    text = recurrenceFrequencyName(entry),
                    selected = entry == freq,
                    description = stringResource(R.string.ed_repeat_frequency_description, recurrenceFrequencyName(entry).lowercase(LocalCalinoLocale)),
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
                        text = day.getDisplayName(TextStyle.SHORT, LocalCalinoLocale),
                        selected = on,
                        description = stringResource(R.string.ed_repeat_on_day, day.getDisplayName(TextStyle.FULL, LocalCalinoLocale)),
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
            label = stringResource(R.string.ed_repeat_ends),
            value = untilOf(draft.recurrence)?.format(editorDateFormat) ?: stringResource(R.string.ed_repeat_never),
            onClick = pickUntil,
        )
        Text(formatRecurrenceRule(context, draft.recurrence, draft.date), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
    }
}

@Composable
private fun recurrenceFrequencyName(frequency: RecurrenceFreq): String = when (frequency) {
    RecurrenceFreq.Daily -> stringResource(R.string.ed_repeat_daily)
    RecurrenceFreq.Weekly -> stringResource(R.string.ed_repeat_weekly)
    RecurrenceFreq.Monthly -> stringResource(R.string.ed_repeat_monthly)
    RecurrenceFreq.Yearly -> stringResource(R.string.ed_repeat_yearly)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceScopeSelector(draft: EditorDraft, onDraft: (EditorDraft) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        EditorLabel(stringResource(R.string.ed_repeat_apply_changes))
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
                    RecurrenceEditScope.This -> if (draft.kind == PocQuickAddKind.Task) stringResource(R.string.ed_repeat_this_task) else stringResource(R.string.ed_repeat_this_event)
                    RecurrenceEditScope.Future -> stringResource(R.string.ed_repeat_this_and_future)
                    RecurrenceEditScope.All -> stringResource(R.string.ed_repeat_entire_series)
                }
                CalinoChip(
                    text = label,
                    selected = draft.recurrenceScope == scope,
                    description = context.getString(R.string.ed_repeat_apply_description, label),
                    semanticsRole = Role.RadioButton,
                    onClick = { onDraft(draft.copy(recurrenceScope = scope)) },
                )
            }
        }
        Text(
            when (draft.recurrenceScope) {
                RecurrenceEditScope.This -> stringResource(R.string.ed_repeat_scope_this_hint)
                RecurrenceEditScope.Future -> stringResource(R.string.ed_repeat_scope_future_hint)
                RecurrenceEditScope.All -> stringResource(R.string.ed_repeat_scope_all_hint)
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
    val label = if (single) stringResource(R.string.ed_editor_category) else stringResource(R.string.ed_editor_categories)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            categories.forEach { category ->
                val on = category in draft.categories
                CalinoChip(
                    text = category,
                    selected = on,
                    description = if (single) stringResource(R.string.ed_editor_choose_category) else stringResource(R.string.ed_editor_toggle_category),
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
    val context = LocalContext.current
    val more = stringResource(R.string.ed_editor_more_options)
    val fewer = stringResource(R.string.ed_editor_fewer_options)
    EditorValueRow(
        icon = calino.malinov.ski.ui.components.CalinoIcon.More,
        label = more,
        value = if (open) fewer else more,
        onClick = onToggle,
    )
    EditorReveal(open) {
        Column(
            Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorSwitchRow(CalinoIcon.Clock, stringResource(R.string.ed_editor_available), draft.availability == Availability.Free) { free ->
                onDraft(draft.copy(availability = if (free) Availability.Free else Availability.Busy))
            }
            if (providerOwned) {
                Text(
                    stringResource(R.string.ed_editor_provider_owned_options),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                )
            } else {
                EditorLabel(stringResource(R.string.ed_editor_travel_time))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    TravelTimeChoices.forEach { minutes ->
                        CalinoChip(
                            text = minutes?.let { calino.malinov.ski.util.formatCalinoDuration(context, it) } ?: stringResource(R.string.ed_editor_none),
                            selected = draft.travelTimeMinutes == minutes,
                            description = stringResource(R.string.ed_editor_travel_time),
                            semanticsRole = Role.RadioButton,
                            onClick = { onDraft(draft.copy(travelTimeMinutes = minutes)) },
                        )
                    }
                }
                if (categories.isNotEmpty()) CategoriesSection(draft, categories, single = false, onDraft = onDraft)
                if (relatedCandidates.isNotEmpty()) {
                    EditorLabel(stringResource(R.string.ed_editor_related_to))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        relatedCandidates.forEach { (id, label) ->
                            val on = id in draft.relatedTo
                            CalinoChip(
                                text = label,
                                selected = on,
                                description = stringResource(R.string.ed_editor_attach_task),
                                semanticsRole = Role.Checkbox,
                                onClick = {
                                    onDraft(draft.copy(relatedTo = if (on) draft.relatedTo - id else draft.relatedTo + id))
                                },
                            )
                        }
                    }
                }
                EditorLabel(stringResource(R.string.ed_editor_attendees))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalinoTextField(
                        value = attendeeInput,
                        onValueChange = onAttendeeInput,
                        label = stringResource(R.string.ed_editor_attendee_email),
                        placeholder = stringResource(R.string.ed_editor_add_attendee_email),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = attendeeInput.contains('@'),
                        onClick = {
                            val email = attendeeInput.trim()
                            onDraft(draft.copy(attendees = draft.attendees + Attendee(email.substringBefore('@'), email)))
                            onAttendeeInput("")
                        },
                    ) { Text(stringResource(R.string.ed_editor_add)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    draft.attendees.forEach { attendee ->
                        CalinoChip(
                            text = attendee.name,
                            selected = true,
                            description = stringResource(R.string.ed_editor_remove_attendee, attendee.email),
                            onClick = { onDraft(draft.copy(attendees = draft.attendees - attendee)) },
                        )
                    }
                }
                AttachmentLinksSection(draft, onDraft)
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

internal fun eventReminderSummary(context: android.content.Context, reminders: List<Reminder>, timeFormat: CalinoTimeFormat, locale: Locale): String =
    reminderSummary(reminders, context, timeFormat, locale)

@Composable
internal fun TaskReminderChips(reminder: Reminder?, onChange: (Reminder?) -> Unit) =
    ReminderChips(listOfNotNull(reminder), single = true, singleLine = true) { onChange(it.firstOrNull()) }

internal fun taskReminderSummary(context: android.content.Context, reminder: Reminder?, timeFormat: CalinoTimeFormat, locale: Locale): String =
    reminderSummary(listOfNotNull(reminder), context, timeFormat, locale)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderChips(
    reminders: List<Reminder>,
    single: Boolean,
    singleLine: Boolean = false,
    onChange: (List<Reminder>) -> Unit,
) {
    val context = LocalContext.current
    val timeFormat = LocalTimeFormat
    val locale = LocalCalinoLocale
    // A card that is already tall gets one scrolling strip rather than a
    // block of wrapped chips.
    val chips: @Composable () -> Unit = {
        ReminderChoices.forEach { minutes ->
            val reminder = Reminder(minutes)
            val on = reminders.any { it.absoluteAt == null && it.minutesBefore == minutes }
            CalinoChip(
                text = formatReminder(minutes, context),
                selected = on,
                description = stringResource(R.string.ed_editor_reminder),
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
                text = context.getString(R.string.ed_reminder_at, reminderSummary(reminders, context, timeFormat, locale).substringBefore(" ·")),
                selected = true,
                description = stringResource(R.string.ed_reminder_remove_exact),
                semanticsRole = Role.Checkbox,
                onClick = { onChange(emptyList()) },
            )
        }
        if (single && reminders.isNotEmpty()) {
            val current = reminders.first()
            CalinoChip(
                text = if (current.repeatCount > 0) stringResource(R.string.ed_reminder_repeat_count, current.repeatCount, context.getString(R.string.fmt_minutes, current.repeatIntervalMinutes)) else stringResource(R.string.ed_reminder_repeat_once),
                selected = current.repeatCount > 0,
                description = stringResource(R.string.ed_reminder_repeat_task),
                semanticsRole = Role.Checkbox,
                onClick = {
                    onChange(listOf(if (current.repeatCount > 0) current.copy(repeatCount = 0, repeatIntervalMinutes = 0)
                        else current.copy(repeatCount = 1, repeatIntervalMinutes = 10)))
                },
            )
        }
    }
    if (singleLine) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) { chips() }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { chips() }
    }
}

private fun untilOf(rule: String?): LocalDate? = rule
    ?.uppercase(Locale.US)
    ?.split(';')
    ?.firstNotNullOfOrNull { part -> part.removePrefix("UNTIL=").takeIf { it != part } }
    ?.take(8)
    ?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() }

internal fun formatEditorDuration(minutes: Int): String = formatCalinoDuration(minutes)

private fun formatReminder(minutes: Int, context: android.content.Context): String = when {
    minutes == 0 -> context.getString(R.string.ed_reminder_at_time)
    minutes >= 24 * 60 -> context.getString(R.string.ed_reminder_one_day_before)
    else -> context.getString(R.string.ed_reminder_before, calino.malinov.ski.util.formatCalinoDuration(context, minutes))
}

/** Re-runs the keyword rules only when this edit changed the title. */
private fun EditorDraft.rematchedFrom(previous: EditorDraft, rules: List<AutoCategoryRule>): EditorDraft =
    if (title == previous.title) this else withAutoCategories(rules)

/**
 * Links and files written as `ATTACH`. A picked file is embedded in the event
 * (`VALUE=BINARY`), so every sync of the event carries it -- Calino web's
 * limits apply: a note above [AttachmentWarnBytes], refused above
 * [AttachmentMaxBytes]. Every attachment, including an inline file another
 * client added, is a chip the person can remove.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentLinksSection(draft: EditorDraft, onDraft: (EditorDraft) -> Unit) {
    var input by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    val attachments = draft.attachments.orEmpty()
    val link = input.trim()
    val valid = attachmentLinkValid(link) && attachments.none { it.uri == link }
    val context = androidx.compose.ui.platform.LocalContext.current
    val fileReadError = stringResource(R.string.ed_attachment_read_error)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // The launcher's callback outlives this composition's draft; read the latest.
    val latestDraft by androidx.compose.runtime.rememberUpdatedState(draft)
    val latestOnDraft by androidx.compose.runtime.rememberUpdatedState(onDraft)
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    readPickedFile(context.contentResolver, uri, context.getString(R.string.ed_attachment_fallback))
                }.getOrNull()
            }
            note = when (picked) {
                null -> fileReadError
                is PickedFile.TooLarge -> context.getString(R.string.ed_attachment_too_large, picked.name, AttachmentMaxBytes / MiB)
                is PickedFile.Read -> {
                    val current = latestDraft
                    latestOnDraft(current.copy(attachments = current.attachments.orEmpty() + picked.attachment))
                    if (picked.attachment.data!!.size > AttachmentWarnBytes) {
                        context.getString(R.string.ed_attachment_large_sync, picked.attachment.fileName)
                    } else {
                        null
                    }
                }
            }
        }
    }
    EditorLabel(stringResource(R.string.ed_attachment_section))
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CalinoTextField(
            value = input,
            onValueChange = { input = it },
            label = stringResource(R.string.ed_attachment_link_field),
            placeholder = stringResource(R.string.ed_attachment_add_link),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            enabled = valid,
            onClick = {
                onDraft(draft.copy(attachments = attachments + EventAttachment(uri = link, fileName = uriFileName(link))))
                input = ""
            },
        ) { Text(stringResource(R.string.ed_editor_add)) }
    }
    TextButton(
        onClick = { note = null; picker.launch(arrayOf("*/*")) },
        modifier = Modifier.heightIn(min = 44.dp),
    ) { Text(stringResource(R.string.ed_attachment_add_file)) }
    androidx.compose.animation.AnimatedVisibility(
        visible = note != null,
        enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) + androidx.compose.animation.expandVertically(tween(CalinoMotion.ContentEnterMillis)),
        exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) + androidx.compose.animation.shrinkVertically(tween(CalinoMotion.ContentExitMillis)),
    ) {
        // Keep the last text while the row fades out.
        var shown by remember { mutableStateOf("") }
        note?.let { shown = it }
        Text(shown, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
    }
    if (attachments.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            attachments.forEach { attachment ->
                val name = attachmentLabel(attachment, stringResource(R.string.ed_attachment_fallback))
                CalinoChip(
                    text = name,
                    selected = true,
                    description = stringResource(R.string.ed_attachment_remove, name),
                    onClick = {
                        note = null
                        onDraft(draft.copy(attachments = attachments.filterNot { it === attachment }))
                    },
                )
            }
        }
    }
}

private const val MiB = 1024 * 1024

/** Above this a picked file still attaches, with a note about sync weight. */
internal const val AttachmentWarnBytes = 1 * MiB

/** Calino web's hard limit: larger files are refused. */
internal const val AttachmentMaxBytes = 5 * MiB

internal sealed interface PickedFile {
    data class Read(val attachment: EventAttachment) : PickedFile
    data class TooLarge(val name: String) : PickedFile
}

/**
 * Reads a picked document, stopping one byte past [AttachmentMaxBytes] so a huge
 * file is refused without being loaded whole.
 */
private fun readPickedFile(
    resolver: android.content.ContentResolver,
    uri: android.net.Uri,
    fallbackName: String,
): PickedFile {
    var name: String? = null
    var declaredSize: Long? = null
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                ?.let { name = cursor.getString(it) }
            cursor.getColumnIndex(android.provider.OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { declaredSize = cursor.getLong(it) }
        }
    }
    val fileName = name?.takeIf(String::isNotBlank) ?: fallbackName
    if ((declaredSize ?: 0) > AttachmentMaxBytes) return PickedFile.TooLarge(fileName)
    val bytes = resolver.openInputStream(uri)?.use { stream ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() <= AttachmentMaxBytes) {
            val read = stream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
        ?: error("No stream for $uri")
    if (bytes.size > AttachmentMaxBytes) return PickedFile.TooLarge(fileName)
    return PickedFile.Read(
        EventAttachment(
            fileName = fileName,
            mimeType = resolver.getType(uri),
            sizeBytes = bytes.size,
            data = bytes,
        ),
    )
}

/** Only web links: a `file:` or `content:` URI would mean nothing on another device. */
internal fun attachmentLinkValid(link: String): Boolean =
    runCatching { java.net.URI(link) }.getOrNull()?.let { uri ->
        uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank()
    } == true
