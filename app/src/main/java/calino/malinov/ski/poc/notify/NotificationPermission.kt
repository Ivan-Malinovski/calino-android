package calino.malinov.ski.poc.notify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import calino.malinov.ski.poc.state.CalinoPreferenceStore

/**
 * Asking for `POST_NOTIFICATIONS`, once, at a moment that is not the first
 * frame the user ever sees.
 *
 * A permission dialog on top of a launch screen is asking before there is
 * anything to say yes to. So the request waits for the app to be *returned*
 * to -- the second resume of the process -- and a persisted flag makes sure it
 * happens exactly once, whatever the answer. The Notifications screen is the
 * fallback for anyone who dismissed it.
 */
@Immutable
data class NotificationPermissionState(
    val granted: Boolean,
    /** False once Android will no longer show the dialog; Settings is the only route left. */
    val requestable: Boolean,
    val request: () -> Unit,
    val openSystemSettings: () -> Unit,
)

val LocalNotificationPermission = staticCompositionLocalOf {
    NotificationPermissionState(granted = true, requestable = false, request = {}, openSystemSettings = {})
}

@Composable
fun rememberNotificationPermission(store: CalinoPreferenceStore): NotificationPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationsAllowed(context)) }
    var asked by remember { mutableStateOf(store.loadNotificationPromptShown()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result || notificationsAllowed(context)
        asked = true
        store.saveNotificationPromptShown(true)
    }

    val request: () -> Unit = {
        asked = true
        store.saveNotificationPromptShown(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            granted = notificationsAllowed(context)
        }
    }

    // Re-read on every resume: the user may have changed it in Settings, and
    // the permission is also what decides whether the Notifications screen
    // shows its "turn these on" row.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = notificationsAllowed(context)
                resumes += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The second resume: the user has come back to the app, which is a moment
    // they are looking at it rather than waiting for it to start. In an effect,
    // not in the composition body -- launching a dialog while composing would
    // fire again on every recomposition that beat the state update.
    LaunchedEffect(resumes, asked, granted) {
        if (resumes >= 2 && !asked && !granted) request()
    }

    return NotificationPermissionState(
        granted = granted,
        requestable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted,
        request = request,
        openSystemSettings = { context.startActivity(systemSettingsIntent(context)) },
    )
}

private fun notificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

fun systemSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun channelSettingsIntent(context: Context, channelId: String): Intent =
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
