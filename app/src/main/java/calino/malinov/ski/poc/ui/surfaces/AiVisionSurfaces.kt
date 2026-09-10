package calino.malinov.ski.poc.ui.surfaces

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calino.malinov.ski.poc.MainActivity
import calino.malinov.ski.poc.data.ai.AiEventCandidate
import calino.malinov.ski.poc.data.ai.AiProvider
import calino.malinov.ski.poc.data.ai.AiVisionClient
import calino.malinov.ski.poc.data.ai.AiVisionSettingsStore
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoSpacing
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.state.LocalTimeFormat
import calino.malinov.ski.poc.ui.components.CalinoIcons
import calino.malinov.ski.poc.util.CalinoTimeFormat
import calino.malinov.ski.poc.util.formatCalinoDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun AiVisionSettingsPage() {
    val context = LocalContext.current
    val store = remember { AiVisionSettingsStore(context) }
    val client = remember { AiVisionClient() }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(store.load()) }
    var keyDraft by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun persist() {
        store.saveConfig(settings.provider, settings.baseUrl, settings.model)
        if (keyDraft.isNotBlank()) { store.saveApiKey(keyDraft); keyDraft = "" }
        settings = store.load()
        updateAiShortcut(context, settings.hasApiKey)
    }
    fun fetchModels() {
        persist()
        val key = store.apiKey() ?: run { status = "Save an API key first."; return }
        busy = true; status = "Loading models…"
        scope.launch {
            runCatching { client.listModels(settings, key).sorted() }
                .onSuccess { models = it; status = if (it.isEmpty()) "No models were returned." else "${it.size} models available" }
                .onFailure { status = it.message ?: "Could not load models." }
            busy = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 18.dp, 20.dp, CalinoSpacing.PillClearance),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("AI Photo Import", style = CalinoTypography.headlineMedium) }
        item { Text("Use your own API key to fill an event or task from a photo. The image is sent directly to your selected provider.", color = CalinoColors.Ink2) }
        item {
            Column(Modifier.fillMaxWidth().background(CalinoColors.Panel, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Provider", fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(settings.provider.label) }
                    DropdownMenu(providerMenu, onDismissRequest = { providerMenu = false }) {
                        AiProvider.entries.forEach { provider -> DropdownMenuItem(text = { Text(provider.label) }, onClick = {
                            settings = settings.copy(provider = provider, baseUrl = provider.defaultUrl, model = if (provider == AiProvider.Custom) "mimo-v2.5" else "")
                            providerMenu = false
                        }) }
                    }
                }
                TextField(settings.baseUrl, { settings = settings.copy(baseUrl = it) }, label = { Text("Base URL") }, supportingText = { Text("Full API root including /v1; used exactly as entered") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextField(keyDraft, { keyDraft = it }, label = { Text("API key") }, placeholder = { Text(if (settings.hasApiKey) "••••••••••••" else "Enter API key") }, visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { revealKey = !revealKey }) { Text(if (revealKey) "Hide" else "Show") } }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (settings.hasApiKey) TextButton(onClick = { store.clearApiKey(); updateAiShortcut(context, false); settings = store.load(); status = "API key cleared" }) { Text("Clear saved key") }
                Text("Model", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TextField(settings.model, { settings = settings.copy(model = it) }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        DropdownMenu(modelMenu, onDismissRequest = { modelMenu = false }) { models.forEach { id -> DropdownMenuItem(text = { Text(id) }, onClick = { settings = settings.copy(model = id); modelMenu = false }) } }
                    }
                    OutlinedButton(onClick = { if (models.isEmpty()) fetchModels() else modelMenu = true }, enabled = !busy) { Text(if (models.isEmpty()) "Load" else "Choose") }
                }
                Button(onClick = { persist(); status = "Saved" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CalinoColors.Ink)) { Text("Save settings") }
                Button(onClick = {
                    persist(); val key = store.apiKey() ?: run { status = "Save an API key first."; return@Button }
                    busy = true; status = "Testing connection…"
                    scope.launch { val result = client.test(settings, key); status = result.message; if (result.ok) { store.saveVerification(result.visionCapable == true); settings = store.load() }; busy = false }
                }, enabled = !busy && settings.model.isNotBlank() && settings.baseUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp)) else Text("Test connection")
                }
                status?.let { Text(it, color = CalinoColors.Ink2) }
            }
        }
    }
}

@Composable
fun AiProcessingOverlay(visible: Boolean, stage: String) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "AI photo import in progress: $stage" },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 320.dp),
                shape = RoundedCornerShape(22.dp),
                color = CalinoColors.Panel.copy(alpha = .94f),
                shadowElevation = if (CalinoColors.elevationAlpha > 0f) 16.dp else 0.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = CalinoColors.Accent, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    Text(stage, color = CalinoColors.Ink, style = CalinoTypography.titleSmall)
                    Text("Keep Calino open while the photo is read", color = CalinoColors.Ink2, style = CalinoTypography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AiCandidateReview(
    candidates: List<AiEventCandidate>?,
    onCancel: () -> Unit,
    onUse: (List<AiEventCandidate>) -> Unit,
) {
    val values = candidates.orEmpty()
    val timeFormat = LocalTimeFormat
    val compactTitle = if (values.size > 1) "What did we find?" else "Confirm details"
    val compactSubtitle = if (values.size > 1) {
        "Everything read from the photo is selected below. Tap any item to leave it out."
    } else {
        "Here’s what was read from the photo. You can still edit everything in the next step."
    }

    BackHandler(enabled = candidates != null, onBack = onCancel)
    AnimatedVisibility(
        visible = candidates != null,
        enter = fadeIn() + scaleIn(initialScale = .96f),
        exit = fadeOut() + scaleOut(targetScale = .96f),
    ) {
        val selected = remember(values) { mutableStateListOf(*values.indices.map { true }.toTypedArray()) }
        val kinds = remember(values) { mutableStateListOf(*values.map { it.kind }.toTypedArray()) }
        val multiple = values.size > 1
        val selectedCount = selected.count { it }
        val confirmLabel = when {
            selectedCount == 0 -> "Select items to add"
            selectedCount == values.size && multiple -> "Add all $selectedCount"
            multiple -> "Add $selectedCount selected"
            else -> "Use this"
        }

        BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()) {
            val isCompact = maxWidth < 600.dp
            Box(
                Modifier.fillMaxSize()
                    .clickable(onClick = onCancel),
            )
            val sheetShape = if (isCompact) {
                RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            } else {
                RoundedCornerShape(24.dp)
            }
            Surface(
                modifier = if (isCompact) {
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * .88f)
                        .clickable(onClick = {})
                } else {
                    Modifier.align(Alignment.Center)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(.92f)
                        .heightIn(max = (maxHeight - 32.dp).coerceAtLeast(240.dp))
                        .clickable(onClick = {})
                },
                shape = sheetShape,
                color = CalinoColors.Panel,
                shadowElevation = if (CalinoColors.elevationAlpha > 0f) 18.dp else 0.dp,
            ) {
                val candidateListMaxHeight = maxHeight * .48f
                Column(
                    Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = if (isCompact) 8.dp else 20.dp, bottom = 12.dp),
                ) {
                    if (isCompact) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally)
                                .padding(bottom = 12.dp)
                                .size(width = 38.dp, height = 4.dp)
                                .background(CalinoColors.Ink.copy(alpha = .16f), RoundedCornerShape(2.dp)),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(compactTitle, style = CalinoTypography.titleMedium, color = CalinoColors.Ink)
                            Spacer(Modifier.height(4.dp))
                            Text(compactSubtitle, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2)
                        }
                        Text(
                            "×",
                            style = CalinoTypography.titleMedium,
                            color = CalinoColors.Ink2,
                            modifier = Modifier.size(44.dp)
                                .clickable(onClick = onCancel)
                                .semantics { contentDescription = "Cancel photo import" },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    if (multiple) {
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = candidateListMaxHeight),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            itemsIndexed(values) { index, item ->
                                AiCandidateCard(
                                    item = item,
                                    kind = kinds[index],
                                    selected = selected[index],
                                    multiple = true,
                                    timeFormat = timeFormat,
                                    onToggle = { selected[index] = !selected[index] },
                                    onKindChange = { kinds[index] = it },
                                )
                            }
                        }
                    } else if (values.isNotEmpty()) {
                        AiCandidateCard(
                            item = values.first(),
                            kind = kinds.firstOrNull() ?: "event",
                            selected = true,
                            multiple = false,
                            timeFormat = timeFormat,
                            onToggle = {},
                            onKindChange = { if (kinds.isNotEmpty()) kinds[0] = it },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Text("Cancel", style = CalinoTypography.labelLarge)
                        }
                        Button(
                            onClick = {
                                onUse(values.mapIndexedNotNull { index, item ->
                                    if (!multiple || selected[index]) item.copy(kind = kinds[index]) else null
                                })
                            },
                            enabled = !multiple || selectedCount > 0,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CalinoColors.Accent),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) { Text(confirmLabel, style = CalinoTypography.labelLarge) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiCandidateCard(
    item: AiEventCandidate,
    kind: String,
    selected: Boolean,
    multiple: Boolean,
    timeFormat: CalinoTimeFormat,
    onToggle: () -> Unit,
    onKindChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) CalinoColors.Accent else CalinoColors.Line
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .clickable(enabled = multiple, onClick = onToggle)
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (multiple) {
                Box(
                    Modifier.padding(end = 10.dp).size(20.dp)
                        .border(1.5.dp, if (selected) CalinoColors.Accent else CalinoColors.Ink3, RoundedCornerShape(6.dp))
                        .background(if (selected) CalinoColors.Accent else CalinoColors.Panel, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(CalinoIcons.Check, contentDescription = null, tint = CalinoColors.OnAccent, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                item.title ?: if (kind == "task") "Untitled task" else "Untitled event",
                style = CalinoTypography.labelLarge,
                color = CalinoColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        aiDateLabel(item, timeFormat, kind)?.let {
            AiMetaRow(CalinoIcons.Calendar, it)
        }
        item.location?.takeIf(String::isNotBlank)?.let {
            AiMetaRow(CalinoIcons.Pin, it)
        }
        item.description?.takeIf(String::isNotBlank)?.let {
            Text(it, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink3, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        if (item.confidence != null && item.confidence != "high") {
            Text("${item.confidence} confidence", style = CalinoTypography.labelSmall, color = CalinoColors.Accent, modifier = Modifier.padding(top = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 9.dp)) {
            listOf("event", "task").forEach { option ->
                val active = kind == option
                Text(
                    option.replaceFirstChar(Char::uppercase),
                    style = CalinoTypography.labelMedium,
                    color = if (active) CalinoColors.Ink else CalinoColors.Ink2,
                    modifier = Modifier
                        .background(if (active) CalinoColors.AccentSoft else CalinoColors.Canvas, RoundedCornerShape(20.dp))
                        .clickable { onKindChange(option) }
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = "${option.replaceFirstChar(Char::uppercase)}"
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun AiMetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CalinoColors.Ink2, modifier = Modifier.size(15.dp))
        Text(text, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun aiDateLabel(item: AiEventCandidate, timeFormat: CalinoTimeFormat, kind: String): String? {
    val start = item.start ?: return null
    if (item.allDay) return (if (kind == "task") "Due " else "") + formatCalinoDate(start.toLocalDate())
    val date = start.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
    val startText = "$date · ${timeFormat.format(start)}"
    val end = item.end
    if (end == null) return (if (kind == "task") "Due " else "") + startText
    val endText = if (start.toLocalDate() == end.toLocalDate()) {
        timeFormat.format(end)
    } else {
        end.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)) + " · " + timeFormat.format(end)
    }
    return (if (kind == "task") "Due " else "") + "$startText – $endText"
}

fun updateAiShortcut(context: Context, enabled: Boolean) {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return
    if (!enabled) { manager.removeDynamicShortcuts(listOf("ai-photo-import")); return }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("calino.malinov.ski.poc://ai-photo-import"), context, MainActivity::class.java)
    val shortcut = ShortcutInfo.Builder(context, "ai-photo-import").setShortLabel("Photo import").setLongLabel("Import event from photo")
        .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_camera)).setIntent(intent).build()
    manager.dynamicShortcuts = listOf(shortcut)
}
