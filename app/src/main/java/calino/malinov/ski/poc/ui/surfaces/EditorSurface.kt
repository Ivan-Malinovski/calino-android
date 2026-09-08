package calino.malinov.ski.poc.ui.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.data.model.Attendee
import calino.malinov.ski.poc.data.model.Availability
import calino.malinov.ski.poc.data.model.EditorDraft
import calino.malinov.ski.poc.data.model.EditorField
import calino.malinov.ski.poc.data.model.RecurrenceFreq
import calino.malinov.ski.poc.data.model.Reminder
import calino.malinov.ski.poc.data.model.applyInput
import calino.malinov.ski.poc.data.model.isParsed
import calino.malinov.ski.poc.data.model.recurrenceDaysOf
import calino.malinov.ski.poc.data.model.recurrenceFreqOf
import calino.malinov.ski.poc.data.model.recurrenceRule
import calino.malinov.ski.poc.data.parser.PocQuickAddKind
import calino.malinov.ski.poc.data.repository.CalinoCalendar
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.ui.components.BottomDetailCard
import calino.malinov.ski.poc.ui.components.CalinoChip
import calino.malinov.ski.poc.ui.components.CalinoColorSwatchRow
import calino.malinov.ski.poc.ui.components.CalinoTextField
import calino.malinov.ski.poc.ui.components.CalinoToggleRow
import calino.malinov.ski.poc.ui.components.EditorLabel
import calino.malinov.ski.poc.ui.components.EditorReveal
import calino.malinov.ski.poc.ui.components.EditorSection
import calino.malinov.ski.poc.ui.components.EditorValueField
import calino.malinov.ski.poc.ui.components.rememberDatePicker
import calino.malinov.ski.poc.ui.components.rememberTimePicker
import calino.malinov.ski.poc.util.formatRecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

private val EditorDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
private val EditorTimeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
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
) {
    var draft by remember(initial.editingId, initial.kind) { mutableStateOf(initial) }
    var shown by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    var pendingCloseAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var descriptionOpen by remember(initial.editingId) { mutableStateOf(!initial.description.isNullOrBlank()) }
    var moreOpen by remember(initial.editingId) { mutableStateOf(false) }
    var attendeeInput by remember(initial.editingId) { mutableStateOf("") }

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
    val isJournal = draft.kind == PocQuickAddKind.Journal

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
    ) { detailModifier ->
        Column(detailModifier.fillMaxSize().background(CalinoColors.Canvas)) {
            EditorHeader(draft, dismiss)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!draft.isEditing) {
                    KindSelector(draft.kind) { entry ->
                        draft = draft.copy(kind = entry).applyInput(draft.rawInput, baseDate)
                    }
                }

                EditorSection(null) {
                    CalinoTextField(
                        value = draft.rawInput,
                        onValueChange = { draft = draft.applyInput(it, baseDate) },
                        label = when (draft.kind) {
                            PocQuickAddKind.Event -> "What is happening?"
                            PocQuickAddKind.Task -> "What needs doing?"
                            PocQuickAddKind.Journal -> "Give this note a title"
                        },
                        description = "Title, ${draft.kind.name.lowercase(Locale.US)}",
                        singleLine = false,
                        maxLines = 3,
                        textStyle = CalinoTypography.titleMedium.copy(fontSize = 26.sp),
                    )

                    EditorReveal(isJournal) {
                        CalinoTextField(
                            value = draft.body,
                            onValueChange = { draft = draft.copy(body = it) },
                            label = "Note",
                            placeholder = "What is on your mind?",
                            description = "Journal note",
                            singleLine = false,
                            minLines = 4,
                            maxLines = 7,
                            modifier = Modifier.height(150.dp),
                        )
                    }
                }

                // A saved record was never parsed from a typed line,
                // so the chip row would be claiming something untrue.
                if (!isJournal && !draft.isEditing) {
                    ParsedChips(draft, baseDate, pickStartDate, pickStartTime) { minutes ->
                        draft = draft.copy(
                            durationMinutes = minutes,
                            touched = draft.touched + EditorField.Duration,
                        )
                    }
                }

                WhenSection(
                    draft = draft,
                    isEvent = isEvent,
                    onDraft = { draft = it },
                    pickStartDate = pickStartDate,
                    pickStartTime = pickStartTime,
                    pickEndDate = pickEndDate,
                    pickEndTime = pickEndTime,
                    pickUntil = pickUntil,
                )

                if (isEvent) {
                    PlaceSection(
                        draft = draft,
                        calendars = calendars,
                        onDraft = { draft = it },
                    )
                }

                if (!isJournal && categories.isNotEmpty()) {
                    CategoriesSection(draft, categories, isTask) { draft = it }
                }

                if (!isJournal) {
                    DescriptionSection(
                        draft = draft,
                        open = descriptionOpen,
                        onOpen = { descriptionOpen = true },
                        onDraft = { draft = it },
                    )
                }

                if (isTask) {
                    EditorSection("Reminder") {
                        ReminderChips(draft.reminders, single = true) { reminders ->
                            draft = draft.copy(reminders = reminders)
                        }
                    }
                }

                if (isEvent) {
                    MoreSection(
                        draft = draft,
                        open = moreOpen,
                        onToggle = { moreOpen = !moreOpen },
                        relatedCandidates = relatedCandidates,
                        attendeeInput = attendeeInput,
                        onAttendeeInput = { attendeeInput = it },
                        onDraft = { draft = it },
                    )
                }

                // A journal entry carries no colour of its own.
                if (!isJournal) {
                    CalinoColorSwatchRow(Color(draft.color)) { picked ->
                        draft = draft.copy(color = picked.toArgb().toLong() and 0xffffffffL)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = CalinoColors.Line)
                Button(
                    enabled = draft.canSave(),
                    onClick = { val saved = draft; closeAfterAnimation { onSave(saved) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(CalinoColors.Ink),
                ) {
                    Text(
                        if (draft.isEditing) "Save changes"
                        else "Save ${draft.kind.name.lowercase(Locale.US)}",
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorHeader(draft: EditorDraft, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (draft.isEditing) "Edit ${draft.kind.name.lowercase(Locale.US)}" else "New",
                modifier = Modifier.weight(1f),
                style = CalinoTypography.titleLarge,
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = "Close editor" },
                contentAlignment = Alignment.Center,
            ) { Text("×", fontSize = 22.sp, color = CalinoColors.Ink2) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorLabel("Date")
            Text(
                "${draft.date.format(EditorDateFormat)} · ${draft.kind.name}",
                color = CalinoColors.Ink2,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun KindSelector(selected: PocQuickAddKind, onSelect: (PocQuickAddKind) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PocQuickAddKind.entries.forEach { entry ->
            TextButton(
                onClick = { onSelect(entry) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selected == entry) CalinoColors.Ink else CalinoColors.Ink.copy(.06f),
                    contentColor = if (selected == entry) Color.White else CalinoColors.Ink,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.semantics {
                    contentDescription = entry.name
                    stateDescription = if (selected == entry) "Selected" else "Not selected"
                    role = Role.RadioButton
                },
            ) { Text(entry.name) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParsedChips(
    draft: EditorDraft,
    baseDate: LocalDate,
    pickDate: () -> Unit,
    pickTime: () -> Unit,
    onDuration: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        EditorLabel("Parsed from what you typed · tap to change")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CalinoChip(
                text = draft.date.shortEditorLabel(baseDate),
                selected = draft.isParsed(EditorField.Date, baseDate),
                description = "Change date",
                onClick = pickDate,
            )
            if (draft.kind == PocQuickAddKind.Event) {
                CalinoChip(
                    text = draft.startTime?.format(EditorTimeFormat) ?: "Add time",
                    selected = draft.isParsed(EditorField.Time, baseDate),
                    description = "Change time",
                    onClick = pickTime,
                )
                CalinoChip(
                    text = formatEditorDuration(draft.durationMinutes ?: EditorDraft.DefaultDurationMinutes),
                    selected = draft.isParsed(EditorField.Duration, baseDate),
                    description = "Change duration",
                    onClick = {
                        onDuration(
                            when (draft.durationMinutes) {
                                null, 30 -> 60
                                60 -> 90
                                else -> 30
                            },
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenSection(
    draft: EditorDraft,
    isEvent: Boolean,
    onDraft: (EditorDraft) -> Unit,
    pickStartDate: () -> Unit,
    pickStartTime: () -> Unit,
    pickEndDate: () -> Unit,
    pickEndTime: () -> Unit,
    pickUntil: () -> Unit,
) {
    if (draft.kind == PocQuickAddKind.Journal) return
    EditorSection(if (isEvent) "When" else "Due") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorValueField(
                label = if (isEvent) "Start" else "Due date",
                value = draft.date.format(EditorDateFormat),
                onClick = pickStartDate,
                modifier = Modifier.weight(1.4f),
            )
            EditorValueField(
                label = if (isEvent) "Start time" else "Due time",
                value = if (draft.allDay) "All day" else draft.startTime?.format(EditorTimeFormat) ?: "Add",
                onClick = pickStartTime,
                enabled = !draft.allDay,
                modifier = Modifier.weight(1f),
            )
        }
        if (isEvent) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorValueField(
                    label = "End",
                    value = draft.endDate.format(EditorDateFormat),
                    onClick = pickEndDate,
                    enabled = !draft.allDay && draft.startTime != null,
                    modifier = Modifier.weight(1.4f),
                )
                EditorValueField(
                    label = "End time",
                    value = if (draft.allDay) "All day" else draft.endTime?.format(EditorTimeFormat) ?: "—",
                    onClick = pickEndTime,
                    enabled = !draft.allDay && draft.startTime != null,
                    modifier = Modifier.weight(1f),
                )
            }
            CalinoToggleRow("All day", draft.allDay) { onDraft(draft.copy(allDay = it)) }
            CalinoToggleRow("Available", draft.availability == Availability.Free) { free ->
                onDraft(draft.copy(availability = if (free) Availability.Free else Availability.Busy))
            }
            CalinoToggleRow("Recurring", draft.recurrence != null) { on ->
                onDraft(
                    draft.copy(
                        recurrence = if (on) recurrenceRule(RecurrenceFreq.Weekly, setOf(draft.date.dayOfWeek)) else null,
                    ),
                )
            }
            EditorReveal(draft.recurrence != null) {
                RecurrenceEditor(draft, onDraft, pickUntil)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceEditor(draft: EditorDraft, onDraft: (EditorDraft) -> Unit, pickUntil: () -> Unit) {
    val freq = recurrenceFreqOf(draft.recurrence) ?: RecurrenceFreq.Weekly
    val days = recurrenceDaysOf(draft.recurrence)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
        EditorValueField(
            label = "Ends",
            value = untilOf(draft.recurrence)?.format(EditorDateFormat) ?: "Never",
            onClick = pickUntil,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            formatRecurrenceRule(draft.recurrence, draft.date),
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
        )
    }
}

@Composable
private fun PlaceSection(draft: EditorDraft, calendars: List<CalinoCalendar>, onDraft: (EditorDraft) -> Unit) {
    EditorSection(null) {
        CalinoTextField(
            value = draft.location.orEmpty(),
            onValueChange = { onDraft(draft.copy(location = it, touched = draft.touched + EditorField.Location)) },
            label = "Location",
            placeholder = "Add a location",
        )
        if (calendars.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            val current = calendars.firstOrNull { it.id == draft.calendarId } ?: calendars.first()
            Box(Modifier.fillMaxWidth()) {
                EditorValueField(
                    label = "Calendar",
                    value = current.name,
                    onClick = { open = true },
                    modifier = Modifier.fillMaxWidth(),
                )
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
    EditorSection("Categories") {
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

@Composable
private fun DescriptionSection(
    draft: EditorDraft,
    open: Boolean,
    onOpen: () -> Unit,
    onDraft: (EditorDraft) -> Unit,
) {
    if (open) {
        EditorSection("Description") {
            CalinoTextField(
                value = draft.description.orEmpty(),
                onValueChange = { onDraft(draft.copy(description = it)) },
                label = "Description",
                placeholder = "Add more detail",
                singleLine = false,
                minLines = 3,
                maxLines = 8,
            )
        }
    } else {
        TextButton(
            onClick = onOpen,
            modifier = Modifier.semantics { contentDescription = "Add description" },
        ) { Text("+ Add description", color = CalinoColors.Accent) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreSection(
    draft: EditorDraft,
    open: Boolean,
    onToggle: () -> Unit,
    relatedCandidates: List<Pair<String, String>>,
    attendeeInput: String,
    onAttendeeInput: (String) -> Unit,
    onDraft: (EditorDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(onClick = onToggle)
                .semantics {
                    contentDescription = "More options"
                    stateDescription = if (open) "Expanded" else "Collapsed"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(if (open) "Less  ⌃" else "More  ⌄", color = CalinoColors.Accent, fontSize = 13.sp)
        }
        EditorReveal(open) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EditorSection("Travel time") {
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
                }
                EditorSection("Reminders") {
                    ReminderChips(draft.reminders, single = false) { onDraft(draft.copy(reminders = it)) }
                }
                if (relatedCandidates.isNotEmpty()) {
                    EditorSection("Related to") {
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
                }
                EditorSection("Attendees") {
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
                }
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

private fun LocalDate.shortEditorLabel(baseDate: LocalDate): String = when (this) {
    baseDate -> "Today"
    baseDate.plusDays(1) -> "Tomorrow"
    else -> format(EditorDateFormat)
}

internal fun formatEditorDuration(minutes: Int): String = when {
    minutes <= 0 -> "0 min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    minutes < 60 -> "$minutes min"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun formatReminder(minutes: Int): String = when {
    minutes == 0 -> "At time"
    minutes >= 24 * 60 -> "1 day before"
    else -> "${formatEditorDuration(minutes)} before"
}
