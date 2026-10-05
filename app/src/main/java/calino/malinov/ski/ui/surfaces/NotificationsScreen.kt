package calino.malinov.ski.ui.surfaces

import androidx.compose.ui.res.stringResource
import calino.malinov.ski.R

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
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.notify.LocalNotificationPermission
import calino.malinov.ski.notify.ReminderChannelState
import calino.malinov.ski.notify.ReminderChannels
import calino.malinov.ski.notify.ReminderFiring
import calino.malinov.ski.notify.ReminderKind
import calino.malinov.ski.notify.Reminders
import calino.malinov.ski.notify.channelSettingsIntent
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.state.SharedPreferencesPreferenceStore
import calino.malinov.ski.util.formatCalinoDate
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
    /**
     * How many calendars another app has been made responsible for.
     *
     * Stated rather than inferred: with delivery handed over, this screen
     * would otherwise show an empty schedule and look broken.
     */
    val providerOwnedCalendars: Int,
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
        val store = SharedPreferencesPreferenceStore(context)
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
            providerOwnedCalendars = if (store.loadProviderRemindersEnabled()) {
                CalinoContainer.get(context).projectedCalendars().size
            } else {
                0
            },
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
            Text(t(R.string.set_notifications), modifier = Modifier.weight(1f), style = CalinoTypography.displayLarge)
        }

        if (!state.permissionGranted) {
            Notice(
                title = t(R.string.set_notifications_are_off),
                body = t(R.string.set_calino_can_schedule_reminders_but_android_will_not_show_them),
                action = if (state.permissionRequestable) t(R.string.set_allow) else t(R.string.set_open_settings),
                onAction = {
                    if (state.permissionRequestable) permission.request() else permission.openSystemSettings()
                },
            )
        } else if (!state.exactAlarmsAllowed) {
            Notice(
                title = t(R.string.set_exact_timing_is_off),
                body = t(R.string.set_reminders_may_arrive_up_to_about_15_minutes_late_and_later_w),
                action = t(R.string.set_allow),
                onAction = {
                    Reminders.scheduler(context).exactAlarmSettingsIntent()?.let(context::startActivity)
                },
            )
        }

        if (state.providerOwnedCalendars > 0) {
            Text(
                t(
                    if (state.providerOwnedCalendars == 1) R.string.set_1_calendar_published_to_android
                    else R.string.set_1_d_calendars_published_to_android,
                    state.providerOwnedCalendars,
                ),
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink3,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        SectionLabel(t(R.string.set_next_reminders))
        if (state.upcoming.isEmpty()) {
            Text(
                t(R.string.set_nothing_scheduled_in_the_next_week_a_reminder_set_on_an_even),
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
                    t(R.string.set_and_1_d_more, state.remindersScheduled - state.upcoming.size),
                    style = CalinoTypography.bodySmall,
                    color = CalinoColors.Ink3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        SectionLabel(t(R.string.set_channels))
        state.channels.forEach { channel ->
            val channelDescription = t(
                R.string.set_1_s_2_s_3_s,
                channel.name,
                channel.importanceLabel,
                if (channel.enabled) t(R.string.set_on) else t(R.string.set_off_da7a687),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { context.startActivity(channelSettingsIntent(context, channel.id)) }
                    .semantics { contentDescription = channelDescription }
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
                        if (channel.enabled) channel.importanceLabel else t(R.string.set_turned_off_in_android),
                        style = CalinoTypography.bodySmall,
                        color = CalinoColors.Ink3,
                    )
                }
            }
        }

        SectionLabel(t(R.string.set_if_a_reminder_never_arrives))
        Text(
            t(R.string.set_some_phones_shut_background_apps_down_to_save_battery_which),
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
            modifier = Modifier.padding(top = 4.dp),
        )

        SectionLabel(t(R.string.set_preview))
        Text(
            t(R.string.set_an_illustration_of_how_a_reminder_looks_and_how_few_actions),
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink3,
        )
        NotificationCardPreview(t(R.string.set_design_review), t(R.string.set_10_00_studio), ReminderKind.Event)
        NotificationCardPreview(t(R.string.set_buy_flowers), t(R.string.set_due_today_personal), ReminderKind.Task)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun firingLabel(firing: ReminderFiring, zone: ZoneId, format: calino.malinov.ski.util.CalinoTimeFormat): String {
    val locale = calino.malinov.ski.util.LocalCalinoLocale
    val at = firing.at.atZone(zone).toLocalDateTime()
    val today = LocalDate.now(zone)
    val dayLabel = when (at.toLocalDate()) {
        today -> t(R.string.set_today)
        today.plusDays(1) -> t(R.string.set_tomorrow)
        else -> at.toLocalDate().format(calino.malinov.ski.util.localizedDateFormatter("EEE, MMM d"))
    }
    return t(R.string.set_1_s_2_s, dayLabel, format.format(at.toLocalTime(), locale))
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
    val rowDescription = t(R.string.set_1_s_2_s, firing.title, label)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = rowDescription }
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
            Text(t(R.string.set_snooze_5_min), style = CalinoTypography.bodySmall, color = CalinoColors.Accent)
            if (kind == ReminderKind.Task) {
                Text(t(R.string.set_mark_done), style = CalinoTypography.bodySmall, color = CalinoColors.Accent)
                Text(t(R.string.set_tomorrow), style = CalinoTypography.bodySmall, color = CalinoColors.Accent)
            }
        }
    }
}
@Composable
private fun t(id: Int, vararg args: Any): String = stringResource(id, *args)
