package calino.malinov.ski.ui.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import calino.malinov.ski.data.model.WebcalForm
import calino.malinov.ski.data.model.WebcalSubscription
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.ui.components.BottomDetailCard
import calino.malinov.ski.ui.components.CalinoTextField
import calino.malinov.ski.ui.components.EditorSection
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.ui.components.calinoPressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WebcalColors = listOf(
    0xFF5B7FB5,
    0xFFC2697F,
    0xFFBF944E,
    0xFF3F8F6B,
    0xFF7A5EA8,
)

@Composable
fun WebcalSubscribeSheet(
    onDismiss: () -> Unit,
    onSubscribe: suspend (WebcalForm) -> Unit,
) {
    var form by remember { mutableStateOf(WebcalForm()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Same exit handshake as AddCalDavAccountSheet: stay composed until the
    // card has animated out, then unmount from Settings.
    var shown by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closeAfterAnimation: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            pendingClose = action
            shown = false
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            delay(CalinoMotion.SurfaceFadeMillis.toLong())
            pendingClose?.invoke()
        }
    }
    val dismiss: () -> Unit = { closeAfterAnimation(onDismiss) }

    BottomDetailCard(visible = shown, onDismiss = dismiss) { cardModifier ->
        Column(cardModifier.fillMaxSize()) {
            Text(
                "Subscribe to calendar",
                style = CalinoTypography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EditorSection("Feed") {
                    CalinoTextField(
                        value = form.url,
                        onValueChange = { form = form.copy(url = it); error = null },
                        label = "Calendar URL",
                        placeholder = "https:// or webcal://",
                        description = "iCalendar subscription URL",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    CalinoTextField(
                        value = form.name,
                        onValueChange = { form = form.copy(name = it) },
                        label = "Name",
                        placeholder = "Optional — the host is used otherwise",
                        description = "Subscription display name",
                    )
                }
                EditorSection("Colour") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WebcalColors.forEach { color ->
                            val selected = form.color == color
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) CalinoColors.Ink else CalinoColors.Line,
                                        CircleShape,
                                    )
                                    .calinoPressable { form = form.copy(color = color) }
                                    .semantics { contentDescription = "Colour" },
                            )
                        }
                    }
                }
                EditorSection("Refresh") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WebcalSubscription.RefreshChoices.forEach { minutes ->
                            val selected = form.refreshIntervalMinutes == minutes
                            val label = when (minutes) {
                                15 -> "15m"
                                60 -> "1h"
                                360 -> "6h"
                                else -> "1d"
                            }
                            Text(
                                label,
                                style = CalinoTypography.bodySmall,
                                color = if (selected) CalinoColors.Canvas else CalinoColors.Ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(CalinoShapes.Pill))
                                    .background(if (selected) CalinoColors.Accent else CalinoColors.Ink.copy(.08f))
                                    .calinoPressable { form = form.copy(refreshIntervalMinutes = minutes) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .semantics { contentDescription = "Refresh $label" },
                            )
                        }
                    }
                }
                EditorSection("Reminders") {
                    val muted = !form.notifyReminders
                    Text(
                        "Subscribed calendars stay quiet unless you opt in. The publisher's alarms stay in the file; they just do not fire.",
                        style = CalinoTypography.bodySmall,
                        color = CalinoColors.Ink2,
                    )
                    TextButton(onClick = { form = form.copy(notifyReminders = !form.notifyReminders) }) {
                        Text(if (muted) "Feed reminders are muted" else "Fire feed reminders")
                    }
                }
                error?.let { message ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CalinoShapes.Card))
                            .background(CalinoColors.Rose.copy(.10f))
                            .border(1.dp, CalinoColors.Rose.copy(.28f), RoundedCornerShape(CalinoShapes.Card))
                            .padding(14.dp),
                    ) {
                        Text(message, style = CalinoTypography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching { onSubscribe(form) }
                                .onSuccess { dismiss() }
                                .onFailure { error = it.message ?: "Could not subscribe." }
                            busy = false
                        }
                    },
                    enabled = !busy && form.url.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CalinoColors.Accent),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CalinoColors.Canvas,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Subscribe")
                    }
                }
            }
        }
    }
}
