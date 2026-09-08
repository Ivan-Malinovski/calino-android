package calino.malinov.ski.poc.ui.surfaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.model.CalDavCalendar
import calino.malinov.ski.poc.data.model.CalDavForm
import calino.malinov.ski.poc.data.repository.CalDavClient
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion
import calino.malinov.ski.poc.design.CalinoShapes
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.state.CalDavConnectResult
import calino.malinov.ski.poc.state.CalDavField
import calino.malinov.ski.poc.state.CalDavStep
import calino.malinov.ski.poc.state.canConnect
import calino.malinov.ski.poc.state.messageFor
import calino.malinov.ski.poc.state.serverHost
import calino.malinov.ski.poc.state.validate
import calino.malinov.ski.poc.ui.components.BottomDetailCard
import calino.malinov.ski.poc.ui.components.CalinoIcons
import calino.malinov.ski.poc.ui.components.CalinoTextField
import calino.malinov.ski.poc.ui.components.CalinoToggleRow
import calino.malinov.ski.poc.ui.components.EditorLabel
import calino.malinov.ski.poc.ui.components.EditorSection
import calino.malinov.ski.poc.ui.components.MenuButton
import calino.malinov.ski.poc.ui.components.calinoPressable
import kotlinx.coroutines.delay

private const val SheetExitMillis = CalinoMotion.SurfaceFadeMillis.toLong()

/**
 * The connected-calendars surface: the accounts already added, the collections
 * under each, and the entry point into the add flow.
 *
 * The prototype never contacts a server. [client] is the seam a real CalDAV
 * implementation would fill; today it is a fixture that simulates discovery.
 */
@Composable
fun CalendarAccountsSurface(
    accounts: List<CalDavAccount>,
    client: CalDavClient,
    onAddAccount: (CalDavForm, List<CalDavCalendar>) -> Unit,
    onCalendarEnabled: (accountId: String, calendarId: String, enabled: Boolean) -> Unit,
    onRemoveAccount: (accountId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMenu: (() -> Unit)? = null,
    startAdding: Boolean = false,
    onStartAddingConsumed: () -> Unit = {},
    focusAccountId: String? = null,
    onFocusAccountConsumed: () -> Unit = {},
) {
    // Whether the sheet is open survives rotation; the credentials inside it
    // deliberately do not.
    var adding by rememberSaveable { mutableStateOf(false) }
    // Arriving from the Settings add button opens the sheet on entry. Clearing
    // the request keeps a later visit to this surface from reopening it.
    LaunchedEffect(startAdding) {
        if (startAdding) {
            adding = true
            onStartAddingConsumed()
        }
    }
    // The list keeps its scroll offset across visits. Arriving from a Settings
    // "Manage" row has to bring that account into view rather than restoring
    // wherever the list happened to be left.
    val listState = rememberLazyListState()
    LaunchedEffect(focusAccountId, accounts) {
        val index = accounts.indexOfFirst { it.id == focusAccountId }
        if (focusAccountId != null && index >= 0) {
            listState.animateScrollToItem(index)
            onFocusAccountConsumed()
        }
    }

    Box(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                onOpenMenu?.let { MenuButton(onClick = it, modifier = Modifier.padding(bottom = 2.dp)) }
                Text("Calendars", style = CalinoTypography.displayLarge)
                Text(
                    "Connect a CalDAV server and choose which of its calendars Calino shows.",
                    style = CalinoTypography.bodyMedium,
                    color = CalinoColors.Ink2,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    "UI preview · connecting is simulated. No server is contacted and no password is stored.",
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = CalinoSpacing.PillClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (accounts.isEmpty()) {
                    item { EmptyAccountsCard() }
                } else {
                    items(accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            onCalendarEnabled = { calendarId, enabled ->
                                onCalendarEnabled(account.id, calendarId, enabled)
                            },
                            onRemove = { onRemoveAccount(account.id) },
                        )
                    }
                }
                item {
                    Button(
                        onClick = { adding = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                            .semantics { contentDescription = "Add calendar account" },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(CalinoColors.Ink),
                    ) {
                        Icon(CalinoIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Add calendar account", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        if (adding) {
            AddCalDavAccountSheet(
                client = client,
                onDismiss = { adding = false },
                onConnected = { form, calendars ->
                    onAddAccount(form, calendars)
                    adding = false
                },
            )
        }
    }
}

@Composable
private fun EmptyAccountsCard() = EditorSection("No accounts yet") {
    Text(
        "Calino is showing its local records only. Add a CalDAV account to bring an " +
            "existing calendar in.",
        style = CalinoTypography.bodyMedium,
        color = CalinoColors.Ink2,
    )
}

@Composable
private fun AccountCard(
    account: CalDavAccount,
    onCalendarEnabled: (calendarId: String, enabled: Boolean) -> Unit,
    onRemove: () -> Unit,
) = EditorSection(null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CalinoColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CalinoIcons.Calendar, contentDescription = null, tint = CalinoColors.Accent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(account.displayName, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(
                "${account.username} · ${serverHost(account.serverUrl)}",
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
            )
        }
    }
    HorizontalDivider(color = CalinoColors.Line)
    EditorLabel("Calendars")
    account.calendars.forEach { calendar ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(calendar.color)))
            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                CalinoToggleRow(
                    label = if (calendar.readOnly) "${calendar.name} · read only" else calendar.name,
                    checked = calendar.enabled,
                    onCheckedChange = { onCalendarEnabled(calendar.id, it) },
                )
            }
        }
    }
    HorizontalDivider(color = CalinoColors.Line)
    TextButton(
        onClick = onRemove,
        modifier = Modifier.heightIn(min = 44.dp)
            .semantics { contentDescription = "Remove ${account.displayName}" },
    ) {
        Icon(CalinoIcons.Trash, contentDescription = null, tint = CalinoColors.Rose, modifier = Modifier.size(16.dp))
        Text("Remove account", color = CalinoColors.Rose, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The three-step add flow. The typed [CalDavForm] lives in plain `remember`
 * state so the password is never written into saved instance state, and it is
 * discarded with the sheet.
 */
@Composable
fun AddCalDavAccountSheet(
    client: CalDavClient,
    onDismiss: () -> Unit,
    onConnected: (CalDavForm, List<CalDavCalendar>) -> Unit,
) {
    var form by remember { mutableStateOf(CalDavForm()) }
    var step by remember { mutableStateOf(CalDavStep.Credentials) }
    // Validation stays quiet until the first Connect press, so the form does
    // not scold someone who has not finished typing.
    var showErrors by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var discovered by remember { mutableStateOf<List<CalDavCalendar>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var revealPassword by remember { mutableStateOf(false) }

    var shown by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    var pendingCloseAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closeAfterAnimation: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            pendingCloseAction = action
            shown = false
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            delay(SheetExitMillis)
            pendingCloseAction?.invoke()
        }
    }

    LaunchedEffect(step) {
        if (step != CalDavStep.Connecting) return@LaunchedEffect
        when (val result = client.discover(form)) {
            is CalDavConnectResult.Discovered -> {
                discovered = result.calendars
                selected = result.calendars.map { it.id }.toSet()
                failure = null
                step = CalDavStep.ChooseCalendars
            }
            is CalDavConnectResult.Failed -> {
                failure = result.message
                step = CalDavStep.Credentials
            }
        }
    }

    val errors = form.validate()
    val dismiss: () -> Unit = { closeAfterAnimation(onDismiss) }

    BottomDetailCard(visible = shown, onDismiss = dismiss) { cardModifier ->
        Column(cardModifier.fillMaxSize()) {
            SheetHeader(
                title = when (step) {
                    CalDavStep.Credentials -> "Add calendar account"
                    CalDavStep.Connecting -> "Connecting"
                    CalDavStep.ChooseCalendars -> "Choose calendars"
                },
                onDismiss = dismiss,
            )
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(CalinoMotion.ContentEnterMillis)) { direction * it / 5 } +
                        fadeIn(tween(CalinoMotion.ContentEnterMillis))) togetherWith
                        (slideOutHorizontally(tween(CalinoMotion.ContentExitMillis)) { -direction * it / 5 } +
                            fadeOut(tween(CalinoMotion.FadeThroughMillis)))
                },
                label = "caldav step",
            ) { current ->
                when (current) {
                    CalDavStep.Credentials -> CredentialsStep(
                        form = form,
                        failure = failure,
                        errorFor = { field -> if (showErrors) errors.messageFor(field) else null },
                        revealPassword = revealPassword,
                        onRevealPassword = { revealPassword = !revealPassword },
                        onForm = { form = it; failure = null },
                    )
                    CalDavStep.Connecting -> ConnectingStep(form)
                    CalDavStep.ChooseCalendars -> ChooseCalendarsStep(
                        calendars = discovered,
                        selected = selected,
                        onToggle = { id ->
                            selected = if (id in selected) selected - id else selected + id
                        },
                        onSelectAll = { selected = discovered.map { it.id }.toSet() },
                    )
                }
            }
            HorizontalDivider(color = CalinoColors.Line)
            SheetAction(
                step = step,
                canConnect = form.canConnect(),
                selectedCount = selected.size,
                onConnect = {
                    showErrors = true
                    // The display name is left as typed. The store derives one
                    // from the host at save time, so a corrected URL after a
                    // failed attempt is not shadowed by the old host's name.
                    if (form.canConnect()) step = CalDavStep.Connecting
                },
                onAdd = {
                    val chosen = discovered.map { it.copy(enabled = it.id in selected) }
                    val saved = form
                    closeAfterAnimation { onConnected(saved, chosen) }
                },
            )
        }
    }
}

@Composable
private fun SheetHeader(title: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = CalinoTypography.titleLarge)
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .calinoPressable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = "Close add calendar account" },
            contentAlignment = Alignment.Center,
        ) { Text("×", fontSize = 22.sp, color = CalinoColors.Ink2) }
    }
}

@Composable
private fun CredentialsStep(
    form: CalDavForm,
    failure: String?,
    errorFor: (CalDavField) -> String?,
    revealPassword: Boolean,
    onRevealPassword: () -> Unit,
    onForm: (CalDavForm) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        failure?.let { message ->
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(CalinoShapes.Card))
                        .background(CalinoColors.Rose.copy(.10f))
                        .border(1.dp, CalinoColors.Rose.copy(.28f), RoundedCornerShape(CalinoShapes.Card))
                        .padding(14.dp),
                ) {
                    Text(
                        message,
                        style = CalinoTypography.bodyMedium,
                        color = CalinoColors.Ink,
                        modifier = Modifier.semantics { contentDescription = "Connection failed. $message" },
                    )
                }
            }
        }
        item {
            EditorSection("Server") {
                CalinoTextField(
                    value = form.serverUrl,
                    onValueChange = { onForm(form.copy(serverUrl = it)) },
                    label = "Server URL",
                    placeholder = "dav.example.com",
                    description = "CalDAV server URL",
                    errorText = errorFor(CalDavField.ServerUrl),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Text(
                    "https:// is assumed when the address has no scheme.",
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                )
            }
        }
        item {
            EditorSection("Sign in") {
                CalinoTextField(
                    value = form.username,
                    onValueChange = { onForm(form.copy(username = it)) },
                    label = "Username",
                    description = "CalDAV username",
                    errorText = errorFor(CalDavField.Username),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                CalinoTextField(
                    value = form.password,
                    onValueChange = { onForm(form.copy(password = it)) },
                    label = "Password",
                    description = "CalDAV password",
                    errorText = errorFor(CalDavField.Password),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (revealPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = onRevealPassword,
                            modifier = Modifier.heightIn(min = 44.dp).semantics {
                                contentDescription = if (revealPassword) "Hide password" else "Show password"
                            },
                        ) { Text(if (revealPassword) "Hide" else "Show", style = CalinoTypography.bodySmall) }
                    },
                )
            }
        }
        item {
            EditorSection("Name") {
                CalinoTextField(
                    value = form.displayName,
                    onValueChange = { onForm(form.copy(displayName = it)) },
                    label = "Display name",
                    placeholder = "Optional — the server host is used otherwise",
                    description = "Account display name",
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun ConnectingStep(form: CalDavForm) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = CalinoColors.Accent)
        Text(
            "Looking for calendars on ${form.serverUrl.trim().ifEmpty { "the server" }}…",
            style = CalinoTypography.bodyMedium,
            color = CalinoColors.Ink2,
            modifier = Modifier.padding(top = 18.dp)
                .semantics { contentDescription = "Connecting to the CalDAV server" },
        )
    }
}

@Composable
private fun ChooseCalendarsStep(
    calendars: List<CalDavCalendar>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${calendars.size} calendars found",
                    modifier = Modifier.weight(1f),
                    style = CalinoTypography.bodyMedium,
                    color = CalinoColors.Ink2,
                )
                OutlinedButton(
                    onClick = onSelectAll,
                    modifier = Modifier.heightIn(min = 44.dp)
                        .semantics { contentDescription = "Select every calendar" },
                ) { Text("Select all", style = CalinoTypography.bodySmall) }
            }
        }
        item {
            EditorSection(null) {
                calendars.forEach { calendar ->
                    val checked = calendar.id in selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .calinoPressable(role = Role.Checkbox) { onToggle(calendar.id) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = if (calendar.readOnly) {
                                    "${calendar.name}, read only"
                                } else {
                                    calendar.name
                                }
                                stateDescription = if (checked) "Selected" else "Not selected"
                                role = Role.Checkbox
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = CalinoColors.Accent),
                        )
                        Box(
                            Modifier.padding(start = 4.dp).size(10.dp).clip(CircleShape)
                                .background(Color(calendar.color)),
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(calendar.name, style = CalinoTypography.bodyLarge)
                            if (calendar.readOnly) {
                                Text("Read only", style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    step: CalDavStep,
    canConnect: Boolean,
    selectedCount: Int,
    onConnect: () -> Unit,
    onAdd: () -> Unit,
) {
    val isChoosing = step == CalDavStep.ChooseCalendars
    Button(
        // Connect stays pressable while invalid so it can reveal which field
        // is at fault, rather than leaving a dead button with no explanation.
        enabled = if (isChoosing) selectedCount > 0 else step != CalDavStep.Connecting,
        onClick = if (isChoosing) onAdd else onConnect,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(if (canConnect || isChoosing) CalinoColors.Ink else CalinoColors.Ink2),
    ) {
        Text(
            when {
                isChoosing && selectedCount == 1 -> "Add 1 calendar"
                isChoosing -> "Add $selectedCount calendars"
                step == CalDavStep.Connecting -> "Connecting…"
                else -> "Connect"
            },
        )
    }
}
