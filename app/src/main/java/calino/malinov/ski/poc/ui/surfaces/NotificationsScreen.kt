package calino.malinov.ski.poc.ui.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.notify.LocalNotificationPermission
import calino.malinov.ski.poc.notify.ReminderChannelState
import calino.malinov.ski.poc.notify.ReminderChannels
import calino.malinov.ski.poc.notify.ReminderFiring
import calino.malinov.ski.poc.notify.ReminderKind
import calino.malinov.ski.poc.notify.Reminders
import calino.malinov.ski.poc.notify.channelSettingsIntent
import calino.malinov.ski.poc.state.LocalTimeFormat
import calino.malinov.ski.poc.util.formatCalinoDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What Calino has actually scheduled, and whether Android will let it through.
 *
 * This surface used to be a mock: two illustrative cards and three literal
 * channel rows that described a system nothing was wired to. The cards stay,
 * under a heading that admits what they are; everything above them is now read
 * from the durable schedule and from Android.
 *
 * It is also where the vendor battery-killer caveat lives. That paragraph is
 * not decoration: on several manufacturers' phones an exact alarm simply does
 * not fire, and a person whose reminders go missing needs to be told where to
 * look rather than concluding the app is broken.
 */
@Immutable
data class NotificationSurfaceState(
    val permissionGranted: Boolean,
    val permissionRequestable: Boolean,
    val exactAlarmsAllowed: Boolean,
    val channels: List<ReminderChannelState>,
    val upcoming: List<ReminderFiring>,
    val remindersScheduled: Int,
)

/**
 * Reads the live state, and re-reads it on resume: every one of these values
 * can be changed from outside the app, in Android's own settings.
 */
@Composable
fun rememberNotificationSurfaceState(): NotificationSurfaceState {
    val context = LocalContext.current
    val permission = LocalNotificationPermission.current
    var nonce by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) nonce += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(nonce, permission.granted) {
        val schedule = Reminders.scheduleStore(context).load()
        val now = Instant.now()
        val upcoming = schedule.firings
            .filter { it.at.isAfter(now) }
            .sortedBy { it.at }
        NotificationSurfaceState(
            permissionGranted = permission.granted,
            permissionRequestable = permission.requestable,
            exactAlarmsAllowed = Reminders.scheduler(context).exactAlarmsAllowed(),
            channels = ReminderChannels.state(context),
            upcoming = upcoming.take(5),
            remindersScheduled = upcoming.size,
        )
    }
}

@Composable
fun NotificationsSurface(
    state: NotificationSurfaceState,
    onOpenFiring: (ReminderFiring) -> Unit = {},
) {
    val context = LocalContext.current
    val permission = LocalNotificationPermission.current
    val timeFormat = LocalTimeFormat
    val zone = remember { ZoneId.systemDefault() }

    Column(
        Modifier
            .fillMaxSize()
            .background(CalinoColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", modifier = Modifier.weight(1f), style = CalinoTypography.displayLarge)
        }

        if (!state.permissionGranted) {
            Notice(
                title = "Notifications are off",
                body = "Calino can schedule reminders but Android will not show them.",
                action = if (state.permissionRequestable) "Allow" else "Open settings",
                onAction = {
                    if (state.permissionRequestable) permission.request() else permission.openSystemSettings()
                },
            )
        } else if (!state.exactAlarmsAllowed) {
            Notice(
                title = "Exact timing is off",
                body = "Reminders may arrive up to about 15 minutes late, and later while the " +
                    "phone is dozing. Allowing exact alarms fixes the timing.",
                action = "Allow",
                onAction = {
                    Reminders.scheduler(context).exactAlarmSettingsIntent()?.let(context::startActivity)
                },
            )
        }

        SectionLabel("Next reminders")
        if (state.upcoming.isEmpty()) {
            Text(
                "Nothing scheduled in the next week. A reminder set on an event or a task " +
                    "appears here once it syncs.",
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            state.upcoming.forEach { firing ->
                UpcomingRow(
                    firing = firing,
                    label = firingLabel(firing, zone, timeFormat),
                    onClick = { onOpenFiring(firing) },
                )
            }
            if (state.remindersScheduled > state.upcoming.size) {
                Text(
                    "and ${state.remindersScheduled - state.upcoming.size} more",
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        SectionLabel("Channels")
        state.channels.forEach { channel ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { context.startActivity(channelSettingsIntent(context, channel.id)) }
                    .semantics {
                        contentDescription =
                            "${channel.name}, ${channel.importanceLabel}, " +
                                if (channel.enabled) "on" else "off"
                    }
                    .padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (channel.enabled) CalinoColors.Accent else CalinoColors.Ink3),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(channel.name, style = CalinoTypography.bodyLarge)
                    Text(
                        if (channel.enabled) channel.importanceLabel else "Turned off in Android",
                        style = CalinoTypography.bodySmall,
                        color = CalinoColors.Ink3,
                    )
                }
            }
        }

        SectionLabel("If a reminder never arrives")
        Text(
            "Some phones shut background apps down to save battery, which stops alarms firing " +
                "at all. Samsung, Xiaomi, Huawei and OnePlus are the usual ones. If reminders go " +
                "missing, exempt Calino from battery optimisation; dontkillmyapp.com has the " +
                "exact steps for each manufacturer.",
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
            modifier = Modifier.padding(top = 4.dp),
        )

        SectionLabel("Preview")
        Text(
            "An illustration of how a reminder looks and how few actions it offers.",
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
        )
        NotificationCardPreview("Design review", "10:00 · Studio", ReminderKind.Event)
        NotificationCardPreview("Buy flowers", "Due today · Personal", ReminderKind.Task)
        Spacer(Modifier.height(24.dp))
    }
}

private fun firingLabel(firing: ReminderFiring, zone: ZoneId, format: calino.malinov.ski.poc.util.CalinoTimeFormat): String {
    val at = firing.at.atZone(zone).toLocalDateTime()
    val today = LocalDate.now(zone)
    val dayLabel = when (at.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> formatCalinoDate(at.toLocalDate())
    }
    return "$dayLabel · ${format.format(at.toLocalTime())}"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = CalinoTypography.labelSmall,
        color = CalinoColors.Ink3,
        modifier = Modifier.padding(top = 22.dp, bottom = 2.dp),
    )
}

@Composable
private fun Notice(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CalinoColors.AccentSoft)
            .clickable(onClick = onAction)
            .heightIn(min = 44.dp)
            .padding(16.dp),
    ) {
        Text(title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Text(body, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 4.dp))
        Text(action, style = CalinoTypography.bodyLarge, color = CalinoColors.Accent, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun UpcomingRow(firing: ReminderFiring, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${firing.title}, $label" }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (firing.kind == ReminderKind.Event) CalinoColors.Accent else CalinoColors.Ink3),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(firing.title, style = CalinoTypography.bodyLarge)
            Text(firing.subtitle, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
        }
        Text(label, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3)
    }
}

@Composable
private fun NotificationCardPreview(title: String, body: String, kind: ReminderKind) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CalinoColors.Panel)
            .border(1.dp, CalinoColors.Ink.copy(alpha = .07f), RoundedCornerShape(18.dp))
            .padding(15.dp),
    ) {
        Text(title, style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Text(body, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 2.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Snooze 5 min", style = CalinoTypography.bodySmall, color = CalinoColors.Accent)
            if (kind == ReminderKind.Task) {
                Text("Mark done", style = CalinoTypography.bodySmall, color = CalinoColors.Accent)
                Text("Tomorrow", style = CalinoTypography.bodySmall, color = CalinoColors.Accent)
            }
        }
    }
}
