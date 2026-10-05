package calino.malinov.ski.ui.surfaces

import androidx.compose.ui.res.stringResource
import calino.malinov.ski.R

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import calino.malinov.ski.platform.AndroidCalendarSource
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
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.LocalCalinoLocale
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.CalDavForm
import calino.malinov.ski.data.repository.CalDavClient
import calino.malinov.ski.data.repository.PendingChange
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.CalDavConnectResult
import calino.malinov.ski.state.CalDavField
import calino.malinov.ski.state.CalDavStep
import calino.malinov.ski.state.canConnect
import calino.malinov.ski.state.serverHost
import calino.malinov.ski.state.validate
import calino.malinov.ski.ui.components.BottomDetailCard
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.CalinoTextField
import calino.malinov.ski.ui.components.CalinoToggleRow
import calino.malinov.ski.ui.components.EditorLabel
import calino.malinov.ski.ui.components.EditorSection
import calino.malinov.ski.ui.components.MenuButton
import calino.malinov.ski.ui.components.calinoPressable
import kotlinx.coroutines.delay

private val CalendarPermissions = arrayOf(
    android.Manifest.permission.READ_CALENDAR,
    android.Manifest.permission.WRITE_CALENDAR,
)
private val ImportPermissions = arrayOf(
    android.Manifest.permission.READ_CALENDAR,
    android.Manifest.permission.WRITE_CALENDAR,
)
private val ImportWritePermissions = arrayOf(android.Manifest.permission.WRITE_CALENDAR)

private const val SheetExitMillis = CalinoMotion.SurfaceFadeMillis.toLong()

/**
 * The connected-account surface: the accounts already added, their calendar
 * and address-book collections, and the entry point into the add flow. Reads
 * and writes use real CalDAV/CardDAV; unavailable writes remain in a durable
 * queue until they can be replayed.
 */
@Composable
fun CalendarAccountsSurface(
    accounts: List<CalDavAccount>,
    client: CalDavClient,
    onAddAccount: (CalDavForm, List<CalDavCalendar>) -> Unit,
    onCalendarEnabled: (accountId: String, calendarId: String, enabled: Boolean) -> Unit,
    onAddressBookEnabled: (accountId: String, addressBookId: String, enabled: Boolean) -> Unit = { _, _, _ -> },
    onRemoveAccount: (accountId: String) -> Unit,
    /** The calendars currently published into Android's calendar store. */
    projectedCalendarIds: Set<String> = emptySet(),
    onProjectedCalendarsChanged: (Set<String>) -> Unit = {},
    /** The device's own calendars, discovered from the provider. */
    availableDeviceCalendars: List<AndroidCalendarSource.ImportableCalendar> = emptyList(),
    /** Those of them Calino shows, and those it also reminds for. */
    importedCalendarIds: Set<String> = emptySet(),
    onImportedCalendarsChanged: (Set<String>) -> Unit = {},
    importedReminderCalendarIds: Set<String> = emptySet(),
    onImportedReminderCalendarsChanged: (Set<String>) -> Unit = {},
    writableImportedCalendarIds: Set<String> = emptySet(),
    onWritableImportedCalendarsChanged: (Set<String>) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenMenu: (() -> Unit)? = null,
    startAdding: Boolean = false,
    onStartAddingConsumed: () -> Unit = {},
    focusAccountId: String? = null,
    onFocusAccountConsumed: () -> Unit = {},
    syncState: SyncState = SyncState.Idle,
    onRefresh: () -> Unit = {},
    pendingChanges: List<PendingChange> = emptyList(),
    onRetryPendingChange: (String) -> Unit = {},
    onDiscardPendingChange: (String) -> Unit = {},
    onRetryAllPendingChanges: () -> Unit = {},
    /** Arriving from a sync alert: bring the queued changes into view. */
    focusPendingWrites: Boolean = false,
    onFocusPendingWritesConsumed: () -> Unit = {},
) {
    val addAccountDescription = t(R.string.set_add_calendar_account)
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
    // Manage row has to bring that account into view rather than restoring
    // wherever the list happened to be left.
    val listState = rememberLazyListState()

    // Publishing to the calendar store needs the calendar permissions, and
    // they are asked for here -- at the moment of opting in -- rather than at
    // launch, because until now there was nothing to publish. Refusal is a
    // supported state: the set is left alone, so the toggle springs back.
    val context = LocalContext.current
    var pendingProjection by remember { mutableStateOf<Set<String>?>(null) }
    val calendarPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val requested = pendingProjection
        pendingProjection = null
        if (requested != null && granted.values.all { it }) onProjectedCalendarsChanged(requested)
    }
    val setProjected: (Set<String>) -> Unit = { next ->
        val hasPermission = CalendarPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        // Only opting *in* needs the permission. Opting out must work even
        // after a revoke, or the projection could never be turned off.
        if (hasPermission || next.size <= projectedCalendarIds.size) {
            onProjectedCalendarsChanged(next)
        } else {
            pendingProjection = next
            calendarPermissions.launch(CalendarPermissions)
        }
    }

    // Imported calendars are writable by default when their provider permits
    // it, so the initial opt-in asks for both capabilities. The edit toggle
    // remains independent and can still turn writing off afterward.
    var pendingImport by remember { mutableStateOf<Set<String>?>(null) }
    val importPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val requested = pendingImport
        pendingImport = null
        if (requested != null && granted.values.all { it }) onImportedCalendarsChanged(requested)
    }
    val setImported: (Set<String>) -> Unit = { next ->
        val hasPermission = ImportPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermission || next.size <= importedCalendarIds.size) {
            onImportedCalendarsChanged(next)
        } else {
            pendingImport = next
            importPermissions.launch(ImportPermissions)
        }
    }

    var pendingWritableImport by remember { mutableStateOf<Set<String>?>(null) }
    val importWritePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val requested = pendingWritableImport
        pendingWritableImport = null
        if (requested != null && granted.values.all { it }) onWritableImportedCalendarsChanged(requested)
    }
    val setWritableImported: (Set<String>) -> Unit = { next ->
        val hasPermission = ImportWritePermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermission || next.size <= writableImportedCalendarIds.size) {
            onWritableImportedCalendarsChanged(next)
        } else {
            pendingWritableImport = next
            importWritePermission.launch(ImportWritePermissions)
        }
    }

    LaunchedEffect(focusAccountId, accounts) {
        val index = accounts.indexOfFirst { it.id == focusAccountId }
        if (focusAccountId != null && index >= 0) {
            listState.animateScrollToItem(index)
            onFocusAccountConsumed()
        }
    }

    // Queued changes are the first item whenever they exist, so an alert can
    // land on them regardless of where the list was last left.
    LaunchedEffect(focusPendingWrites) {
        if (!focusPendingWrites) return@LaunchedEffect
        if (pendingChanges.isNotEmpty()) listState.animateScrollToItem(0)
        onFocusPendingWritesConsumed()
    }

    Box(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                onOpenMenu?.let { MenuButton(onClick = it, modifier = Modifier.padding(bottom = 2.dp)) }
                Text(t(R.string.set_calendars), style = CalinoTypography.displayLarge)
                Text(
                    t(R.string.set_connect_a_caldav_server_and_choose_which_of_its_calendars_ca),
                    style = CalinoTypography.bodyMedium,
                    color = CalinoColors.Ink2,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    t(R.string.set_caldav_and_carddav_are_connected_for_reading_and_writing_off),
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
                if (pendingChanges.isNotEmpty()) {
                    item(key = "pending-writes") {
                        PendingWritesCard(
                            changes = pendingChanges,
                            collections = remember(accounts) {
                                accounts.flatMap { account ->
                                    account.calendars.map { it.id to CollectionLabel(it.name, Color(it.color)) } +
                                        account.addressBooks.flatMap {
                                            val label = CollectionLabel(it.name, null)
                                            listOf(it.id to label, it.url to label)
                                        }
                                }.toMap()
                            },
                            onRetry = onRetryPendingChange,
                            onRetryAll = onRetryAllPendingChanges,
                            onDiscard = onDiscardPendingChange,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (accounts.isNotEmpty()) {
                    item { SyncStatusCard(syncState, onRefresh) }
                }
                if (accounts.isEmpty()) {
                    item { EmptyAccountsCard() }
                } else {
                    items(accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            onCalendarEnabled = { calendarId, enabled ->
                                onCalendarEnabled(account.id, calendarId, enabled)
                            },
                            onAddressBookEnabled = { addressBookId, enabled ->
                                onAddressBookEnabled(account.id, addressBookId, enabled)
                            },
                            onRemove = { onRemoveAccount(account.id) },
                            projectedCalendarIds = projectedCalendarIds,
                            onProjectionChanged = { calendarId, published ->
                                setProjected(
                                    if (published) projectedCalendarIds + calendarId
                                    else projectedCalendarIds - calendarId,
                                )
                            },
                        )
                    }
                }
                // Shown when there is something to offer, and also when
                // something is already imported but the roster came back
                // empty -- which is what a revoked permission looks like.
                // Hiding it then would strand the import switched on with no
                // way to reach it, the exact thing the "opting out never
                // needs permission" rule exists to prevent.
                if (availableDeviceCalendars.isNotEmpty() || importedCalendarIds.isNotEmpty()) {
                    item {
                        DeviceCalendarsCard(
                            calendars = availableDeviceCalendars,
                            importedCalendarIds = importedCalendarIds,
                            onImportChanged = { calendarId, imported ->
                                setImported(
                                    if (imported) importedCalendarIds + calendarId
                                    else importedCalendarIds - calendarId,
                                )
                            },
                            writableCalendarIds = writableImportedCalendarIds,
                            onWritableChanged = { calendarId, writable ->
                                setWritableImported(
                                    if (writable) writableImportedCalendarIds + calendarId
                                    else writableImportedCalendarIds - calendarId,
                                )
                            },
                            reminderCalendarIds = importedReminderCalendarIds,
                            onReminderChanged = { calendarId, remind ->
                                onImportedReminderCalendarsChanged(
                                    if (remind) importedReminderCalendarIds + calendarId
                                    else importedReminderCalendarIds - calendarId,
                                )
                            },
                        )
                    }
                }
                item {
                    Button(
                        onClick = { adding = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                            .semantics { contentDescription = addAccountDescription },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(CalinoColors.Ink),
                    ) {
                        Icon(CalinoIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(t(R.string.set_add_calendar_account), modifier = Modifier.padding(start = 8.dp))
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

/**
 * How the last read went, and a way to try again.
 *
 * A failure keeps whatever was already fetched on screen and says so, rather
 * than blanking the calendar: stale data with a visible warning is more useful
 * than nothing.
 */
@Composable
private fun SyncStatusCard(state: SyncState, onRefresh: () -> Unit) {
    val locale = LocalCalinoLocale
    val (label, detail) = when (state) {
        SyncState.Idle -> t(R.string.set_not_connected) to t(R.string.set_no_calendars_are_being_read_yet)
        is SyncState.Loading -> {
            val cachedAt = state.cachedAt
            if (cachedAt == null) {
                t(R.string.set_reading_calendars) to t(R.string.set_fetching_events_tasks_and_journal_entries)
            } else {
                // Saying only "reading" over a full calendar reads as though
                // what is on screen might be wrong. It is the last good read.
                val timeFormat = LocalTimeFormat
                val stamp = remember(cachedAt, timeFormat, locale) { formatSyncTime(cachedAt, timeFormat, locale) }
                t(R.string.set_refreshing) to t(R.string.set_showing_what_was_read_1_s_while_the_server_is_read_again, stamp)
            }
        }
        is SyncState.Ready -> {
            val timeFormat = LocalTimeFormat
            val stamp = remember(state.fetchedAt, timeFormat, locale) { formatSyncTime(state.fetchedAt, timeFormat, locale) }
            if (state.partial) {
                // Name what is missing. "Some of this could not be read" left
                // the user to guess which part of their calendar was absent.
                t(R.string.set_updated_1_s_with_gaps, stamp) to state.warnings.joinToString("\n")
            } else {
                t(R.string.set_updated_1_s, stamp) to t(R.string.set_events_tasks_and_journal_entries_are_current)
            }
        }
        is SyncState.Failed -> t(R.string.set_could_not_update) to if (state.hadPreviousData) {
            "${state.message} ${t(R.string.set_showing_the_last_data_that_was_read)}"
        } else {
            state.message
        }
    }
    val accent = when {
        state is SyncState.Failed -> CalinoColors.Rose
        state is SyncState.Ready && state.partial -> CalinoColors.Rose
        else -> CalinoColors.Ink3
    }
    val statusDescription = t(R.string.set_1_s_2_s, label, detail)

    EditorSection(null) {
        Column(Modifier.fillMaxWidth().semantics { contentDescription = statusDescription }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (state is SyncState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = CalinoColors.Ink3,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    label,
                    style = CalinoTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = accent,
                )
                Spacer(Modifier.weight(1f))
                if (state !is SyncState.Loading) {
                    TextButton(onClick = onRefresh) {
                        Text(t(R.string.set_refresh), style = CalinoTypography.bodyMedium, color = CalinoColors.Accent)
                    }
                }
            }
            Text(
                detail,
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                t(R.string.set_edits_sync_directly_when_online_offline_changes_stay_in_pend),
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun formatSyncTime(instant: java.time.Instant, timeFormat: CalinoTimeFormat, locale: java.util.Locale): String =
    timeFormat.format(
        java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()),
        locale,
    )

/**
 * The calendars already on the device, and whether Calino shows them.
 *
 * Its own card rather than a section of [AccountCard]: these belong to
 * Google, Exchange or whatever else is installed, and have no CalDAV account
 * to hang off. Grouped by owning account so a person can tell whose calendar
 * each one is before deciding.
 */
@Composable
private fun DeviceCalendarsCard(
    calendars: List<AndroidCalendarSource.ImportableCalendar>,
    importedCalendarIds: Set<String>,
    onImportChanged: (calendarId: String, imported: Boolean) -> Unit,
    reminderCalendarIds: Set<String>,
    onReminderChanged: (calendarId: String, remind: Boolean) -> Unit,
    writableCalendarIds: Set<String>,
    onWritableChanged: (calendarId: String, writable: Boolean) -> Unit,
) = EditorSection(null) {
    EditorLabel(t(R.string.set_on_this_device))
    Text(
        t(R.string.set_calino_can_show_calendars_other_apps_on_this_phone_already_s),
        style = CalinoTypography.bodySmall,
        color = CalinoColors.Ink3,
    )
    if (calendars.isEmpty()) {
        // Reached only with something still imported, so say what happened
        // and offer the way out rather than leaving a card with nothing in
        // it.
        HorizontalDivider(color = CalinoColors.Line)
        Text(
            t(R.string.set_calino_cannot_read_this_device_s_calendars_without_the_calen),
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink2,
        )
        val stopShowingDescription = t(R.string.set_stop_showing_this_device_s_calendars)
        TextButton(
            onClick = { importedCalendarIds.forEach { onImportChanged(it, false) } },
            modifier = Modifier.heightIn(min = 44.dp)
                .semantics { contentDescription = stopShowingDescription },
        ) {
            Text(t(R.string.set_stop_showing_them), color = CalinoColors.Rose)
        }
    }
    calendars.groupBy { it.accountName }.forEach { (accountName, owned) ->
        HorizontalDivider(color = CalinoColors.Line)
        EditorLabel(accountName.ifBlank { t(R.string.set_this_device) })
        owned.forEach { calendar ->
            val imported = calendar.id in importedCalendarIds
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    CalinoIcons.Calendar,
                    contentDescription = null,
                    tint = Color(calendar.color),
                    modifier = Modifier.size(18.dp),
                )
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    CalinoToggleRow(
                        label = calendar.name,
                        checked = imported,
                        onCheckedChange = { onImportChanged(calendar.id, it) },
                    )
                }
            }
            if (imported) {
                if (calendar.canWrite) {
                    Box(Modifier.padding(start = 28.dp)) {
                        CalinoToggleRow(
                            label = t(R.string.set_allow_editing_in_calino),
                            checked = calendar.id in writableCalendarIds,
                            onCheckedChange = { onWritableChanged(calendar.id, it) },
                        )
                    }
                } else {
                    Text(
                        t(R.string.set_read_only_in_the_owning_provider),
                        style = CalinoTypography.bodySmall,
                        color = CalinoColors.Ink3,
                        modifier = Modifier.padding(start = 28.dp),
                    )
                }
                Box(Modifier.padding(start = 28.dp)) {
                    CalinoToggleRow(
                        label = t(R.string.set_also_remind_me_in_calino),
                        checked = calendar.id in reminderCalendarIds,
                        onCheckedChange = { onReminderChanged(calendar.id, it) },
                    )
                }
                Text(
                    // Said plainly, because it is the one thing about this
                    // feature Calino cannot fix. The owning app's own
                    // notification is not ours to switch off.
                    t(
                        R.string.set_off_by_default_1_s_already_notifies_for_this_calendar_so_tur,
                        accountName.ifBlank { t(R.string.set_the_owning_app) },
                    ),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                    modifier = Modifier.padding(start = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyAccountsCard() = EditorSection(t(R.string.set_no_accounts_yet)) {
    Text(
        t(R.string.set_calino_is_showing_its_local_records_only_add_a_caldav_accoun),
        style = CalinoTypography.bodyMedium,
        color = CalinoColors.Ink2,
    )
}

@Composable
private fun AccountCard(
    account: CalDavAccount,
    onCalendarEnabled: (calendarId: String, enabled: Boolean) -> Unit,
    onAddressBookEnabled: (addressBookId: String, enabled: Boolean) -> Unit,
    onRemove: () -> Unit,
    projectedCalendarIds: Set<String>,
    onProjectionChanged: (calendarId: String, published: Boolean) -> Unit,
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
    EditorLabel(t(R.string.set_calendars))
    account.calendars.forEach { calendar ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(calendar.color)))
            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                CalinoToggleRow(
                    label = if (calendar.readOnly) t(R.string.set_1_s_read_only, calendar.name) else calendar.name,
                    checked = calendar.enabled,
                    onCheckedChange = { onCalendarEnabled(calendar.id, it) },
                )
            }
        }
    }
    if (account.addressBooks.isNotEmpty()) {
        HorizontalDivider(color = CalinoColors.Line)
        EditorLabel(t(R.string.set_address_books))
        account.addressBooks.forEach { addressBook ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(CalinoIcons.Users, contentDescription = null, tint = CalinoColors.Accent, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    CalinoToggleRow(
                        label = if (addressBook.readOnly) t(R.string.set_1_s_read_only, addressBook.name) else addressBook.name,
                        checked = addressBook.enabled,
                        onCheckedChange = { onAddressBookEnabled(addressBook.id, it) },
                    )
                }
            }
        }
    }
    val publishable = account.calendars.filter { it.enabled }
    if (publishable.isNotEmpty()) {
        HorizontalDivider(color = CalinoColors.Line)
        EditorLabel(t(R.string.set_publish_to_android))
        Text(
            t(R.string.set_a_published_calendar_appears_in_the_device_s_calendar_store_),
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
        )
        publishable.forEach { calendar ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    CalinoIcons.Calendar,
                    contentDescription = null,
                    tint = CalinoColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    CalinoToggleRow(
                        label = calendar.name,
                        checked = calendar.id in projectedCalendarIds,
                        onCheckedChange = { onProjectionChanged(calendar.id, it) },
                    )
                }
            }
        }
    }
    HorizontalDivider(color = CalinoColors.Line)
    val removeDescription = t(R.string.set_remove_1_s, account.displayName)
    TextButton(
        onClick = onRemove,
        modifier = Modifier.heightIn(min = 44.dp)
            .semantics { contentDescription = removeDescription },
    ) {
        Icon(CalinoIcons.Trash, contentDescription = null, tint = CalinoColors.Rose, modifier = Modifier.size(16.dp))
        Text(t(R.string.set_remove_account), color = CalinoColors.Rose, modifier = Modifier.padding(start = 8.dp))
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
    val emptyServerAddressError = t(R.string.set_enter_your_caldav_server_address)
    val invalidServerAddressError = t(R.string.set_that_does_not_look_like_a_server_address)
    val emptyUsernameError = t(R.string.set_enter_the_account_username)
    val emptyPasswordError = t(R.string.set_enter_the_account_password)
    val dismiss: () -> Unit = { closeAfterAnimation(onDismiss) }

    BottomDetailCard(visible = shown, onDismiss = dismiss) { cardModifier ->
        Column(cardModifier.fillMaxSize()) {
            SheetHeader(
                title = when (step) {
                    CalDavStep.Credentials -> t(R.string.set_add_calendar_account)
                    CalDavStep.Connecting -> t(R.string.set_connecting)
                    CalDavStep.ChooseCalendars -> t(R.string.set_choose_calendars)
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
                        errorFor = { field ->
                            if (!showErrors || errors.none { it.field == field }) null
                            else when (field) {
                                CalDavField.ServerUrl -> if (form.serverUrl.isBlank()) emptyServerAddressError
                                else invalidServerAddressError
                                CalDavField.Username -> emptyUsernameError
                                CalDavField.Password -> emptyPasswordError
                            }
                        },
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
    val closeDescription = t(R.string.set_close_add_calendar_account)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = CalinoTypography.titleLarge)
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .calinoPressable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = closeDescription },
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
    val stageWarningDescription = t(R.string.set_early_stage_warning_calino_is_still_in_a_very_early_stage_an)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(CalinoShapes.Card))
                    .background(CalinoColors.Rose.copy(.10f))
                    .border(1.dp, CalinoColors.Rose.copy(.32f), RoundedCornerShape(CalinoShapes.Card))
                    .padding(16.dp)
                    .semantics { contentDescription = stageWarningDescription },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    t(R.string.set_early_stage_warning),
                    style = CalinoTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CalinoColors.Rose,
                )
                Text(
                    t(R.string.set_calino_is_still_in_a_very_early_stage_and_has_not_been_thoro),
                    style = CalinoTypography.bodyLarge,
                    color = CalinoColors.Ink,
                )
            }
        }
        failure?.let { message ->
            item {
                val failureDescription = t(R.string.set_connection_failed_1_s, message)
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
                        modifier = Modifier.semantics { contentDescription = failureDescription },
                    )
                }
            }
        }
        item {
            EditorSection(t(R.string.set_server)) {
                CalinoTextField(
                    value = form.serverUrl,
                    onValueChange = { onForm(form.copy(serverUrl = it)) },
                    label = t(R.string.set_server_url),
                    placeholder = t(R.string.set_dav_example_com),
                    description = t(R.string.set_caldav_server_url),
                    errorText = errorFor(CalDavField.ServerUrl),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Text(
                    t(R.string.set_https_is_assumed_when_the_address_has_no_scheme),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                )
            }
        }
        item {
            val hidePasswordDescription = t(R.string.set_hide_password)
            val showPasswordDescription = t(R.string.set_show_password)
            val hidePasswordLabel = t(R.string.set_hide)
            val showPasswordLabel = t(R.string.set_show)
            EditorSection(t(R.string.set_sign_in)) {
                CalinoTextField(
                    value = form.username,
                    onValueChange = { onForm(form.copy(username = it)) },
                    label = t(R.string.set_username),
                    description = t(R.string.set_caldav_username),
                    errorText = errorFor(CalDavField.Username),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                CalinoTextField(
                    value = form.password,
                    onValueChange = { onForm(form.copy(password = it)) },
                    label = t(R.string.set_password),
                    description = t(R.string.set_caldav_password),
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
                                contentDescription = if (revealPassword) hidePasswordDescription else showPasswordDescription
                            },
                        ) { Text(if (revealPassword) hidePasswordLabel else showPasswordLabel, style = CalinoTypography.bodySmall) }
                    },
                )
            }
        }
        item {
            EditorSection(t(R.string.set_name)) {
                CalinoTextField(
                    value = form.displayName,
                    onValueChange = { onForm(form.copy(displayName = it)) },
                    label = t(R.string.set_display_name),
                    placeholder = t(R.string.set_optional_the_server_host_is_used_otherwise),
                    description = t(R.string.set_account_display_name),
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun ConnectingStep(form: CalDavForm) {
    val connectingDescription = t(R.string.set_connecting_to_the_caldav_server)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = CalinoColors.Accent)
        Text(
            t(R.string.set_looking_for_calendars_on_1_s, form.serverUrl.trim().ifEmpty { t(R.string.set_the_server) }),
            style = CalinoTypography.bodyMedium,
            color = CalinoColors.Ink2,
            modifier = Modifier.padding(top = 18.dp)
                .semantics { contentDescription = connectingDescription },
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
            val selectAllDescription = t(R.string.set_select_every_calendar)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t(R.string.set_1_d_calendars_found, calendars.size),
                    modifier = Modifier.weight(1f),
                    style = CalinoTypography.bodyMedium,
                    color = CalinoColors.Ink2,
                )
                OutlinedButton(
                    onClick = onSelectAll,
                    modifier = Modifier.heightIn(min = 44.dp)
                        .semantics { contentDescription = selectAllDescription },
                ) { Text(t(R.string.set_select_all), style = CalinoTypography.bodySmall) }
            }
        }
        item {
            EditorSection(null) {
                calendars.forEach { calendar ->
                    val checked = calendar.id in selected
                    val calendarDescription = if (calendar.readOnly) {
                        t(R.string.set_1_s_read_only, calendar.name)
                    } else {
                        calendar.name
                    }
                    val calendarStateDescription = if (checked) t(R.string.set_selected) else t(R.string.set_not_selected)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .calinoPressable(role = Role.Checkbox) { onToggle(calendar.id) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = calendarDescription
                                stateDescription = calendarStateDescription
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
                                Text(t(R.string.set_read_only), style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
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
                isChoosing && selectedCount == 1 -> t(R.string.set_add_1_calendar)
                isChoosing -> t(R.string.set_add_1_d_calendars, selectedCount)
                step == CalDavStep.Connecting -> t(R.string.set_connecting_progress)
                else -> t(R.string.set_connect)
            },
        )
    }
}
@Composable
private fun t(id: Int, vararg args: Any): String = stringResource(id, *args)
