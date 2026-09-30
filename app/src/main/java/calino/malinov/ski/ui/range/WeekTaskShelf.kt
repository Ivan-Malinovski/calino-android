package calino.malinov.ski.ui.range

import androidx.compose.animation.animateContentSize
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.design.*
import calino.malinov.ski.ui.components.AgendaRow
import calino.malinov.ski.ui.components.EditorReveal
import calino.malinov.ski.ui.components.CalinoIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun WeekTaskShelf(
    first: LocalDate, last: LocalDate, tasks: List<CalTask>, hovering: Boolean,
    onOpen: (CalTask) -> Unit, onDone: (CalTask, Boolean) -> Unit,
    onLongClick: (CalTask) -> Unit,
    taskModifier: @Composable (CalTask) -> Modifier,
    onAdd: suspend (String) -> Boolean, onDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable(first) { mutableStateOf(false) }
    var title by rememberSaveable(first) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    BackHandler(enabled = adding) { adding = false }
    fun save() {
        if (title.isBlank() || saving) return
        saving = true
        scope.launch {
            try { if (onAdd(title.trim())) { title = ""; adding = false } }
            finally { saving = false }
        }
    }
    val dateFormat = remember { DateTimeFormatter.ofPattern("MMM d") }
    Column(modifier.fillMaxWidth().background(if (hovering) CalinoColors.AccentSoft else CalinoColors.Panel)
        .animateContentSize(CalinoMotion.standardSpatial())) {
        HorizontalDivider(color = if (hovering) CalinoColors.Accent else CalinoColors.Line)
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("Sometime this week", style = CalinoTypography.titleSmall, color = CalinoColors.Ink)
                Text(if (hovering) "Release to plan for this week" else "${first.format(dateFormat)} – ${last.format(dateFormat)} · ${tasks.size} ${if (tasks.size == 1) "task" else "tasks"}",
                    style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
            }
            IconButton(onClick = { adding = !adding }, modifier = Modifier.semantics { contentDescription = "Add week task"; stateDescription = if (adding) "Expanded" else "Collapsed" }) {
                CalinoIcon(if (adding) CalinoIcon.Down else CalinoIcon.Plus, tint = CalinoColors.Ink2)
            }
        }
        if (tasks.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 152.dp).padding(horizontal = 10.dp)) {
            items(tasks, key = { it.id }) { task ->
                AgendaRow(task = task, modifier = taskModifier(task).heightIn(min = 44.dp).animateItem(), compact = true, checkboxTouchSize = 44.dp,
                    onLongClick = { onLongClick(task) },
                    onClick = { onOpen(task) }, onCheckedChange = { onDone(task, it) })
            }
        }
        EditorReveal(adding) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Column(Modifier.padding(start = 18.dp, end = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(title, { title = it }, enabled = !saving, singleLine = true,
                        textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp).padding(vertical = 12.dp).focusRequester(focusRequester)
                            .semantics { contentDescription = "Week task title" },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        decorationBox = { field -> Box { if (title.isEmpty()) Text("What would you like to get done?", color = CalinoColors.Ink3,
                            style = CalinoTypography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); field() } })
                    TextButton(onClick = { save() }, enabled = title.isNotBlank() && !saving) { Text(if (saving) "Saving…" else "Add") }
                }
                TextButton(onClick = { onDetails(title) }, enabled = !saving) { Text("More details") }
            }
        }
    }
}
