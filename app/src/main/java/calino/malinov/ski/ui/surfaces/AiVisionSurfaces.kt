package calino.malinov.ski.ui.surfaces

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import calino.malinov.ski.MainActivity
import calino.malinov.ski.data.ai.AiEventCandidate
import calino.malinov.ski.data.ai.AiProvider
import calino.malinov.ski.data.ai.AiVisionClient
import calino.malinov.ski.data.ai.AiVisionSettingsStore
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoMotion
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.ui.components.CompactSegmentedControl
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.SwipeDownDismiss
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.formatCalinoDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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
                .background(CalinoColors.scrim(.18f))
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

private data class AiReviewCandidate(
    val id: Int,
    val item: AiEventCandidate,
    val kind: String,
    val selected: Boolean,
)

@OptIn(ExperimentalFoundationApi::class)
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
        var reviewCandidates by remember(values) {
            mutableStateOf(
                values.mapIndexed { index, item ->
                    AiReviewCandidate(index, item, item.kind, selected = true)
                },
            )
        }
        val multiple = values.size > 1
        val selectedCount = reviewCandidates.count { it.selected }
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
                    .background(CalinoColors.scrim(.22f))
                    .clickable(onClick = onCancel),
            )
            val sheetShape = if (isCompact) {
                RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            } else {
                RoundedCornerShape(24.dp)
            }
            SwipeDownDismiss(
                visible = candidates != null,
                onDismiss = onCancel,
                modifier = if (isCompact) {
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                } else {
                    Modifier.align(Alignment.Center)
                },
            ) { dragModifier ->
                val surfaceModifier = dragModifier.then(
                    if (isCompact) {
                        Modifier.fillMaxWidth().heightIn(max = maxHeight * .88f)
                    } else {
                        Modifier.widthIn(max = 560.dp)
                            .fillMaxWidth(.92f)
                            .heightIn(max = (maxHeight - 32.dp).coerceAtLeast(240.dp))
                    },
                ).clickable(onClick = {})
                Surface(
                    modifier = surfaceModifier,
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
                            items(
                                items = reviewCandidates,
                                key = { it.id },
                            ) { candidate ->
                                AiCandidateCard(
                                    item = candidate.item,
                                    kind = candidate.kind,
                                    selected = candidate.selected,
                                    multiple = true,
                                    timeFormat = timeFormat,
                                    modifier = Modifier.animateItem(),
                                    onToggle = {
                                        reviewCandidates = reviewCandidates.map { current ->
                                            if (current.id == candidate.id) current.copy(selected = !current.selected) else current
                                        }
                                    },
                                    onKindChange = { kind ->
                                        reviewCandidates = reviewCandidates.map { current ->
                                            if (current.id == candidate.id) current.copy(kind = kind) else current
                                        }
                                    },
                                    onDismiss = {
                                        reviewCandidates = reviewCandidates.filterNot { it.id == candidate.id }
                                    },
                                )
                            }
                        }
                    } else if (reviewCandidates.isNotEmpty()) {
                        val candidate = reviewCandidates.first()
                        AiCandidateCard(
                            item = candidate.item,
                            kind = candidate.kind,
                            selected = candidate.selected,
                            multiple = false,
                            timeFormat = timeFormat,
                            onToggle = {},
                            onKindChange = { kind ->
                                reviewCandidates = reviewCandidates.map { current ->
                                    if (current.id == candidate.id) current.copy(kind = kind) else current
                                }
                            },
                            onDismiss = {
                                reviewCandidates = reviewCandidates.filterNot { it.id == candidate.id }
                            },
                        )
                    } else {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("No results left", style = CalinoTypography.titleSmall, color = CalinoColors.Ink)
                            Text("The swiped-away items will not be added.", style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2)
                        }
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
                                onUse(reviewCandidates.filter { it.selected }.map { it.item.copy(kind = it.kind) })
                            },
                            enabled = reviewCandidates.isNotEmpty() && (!multiple || selectedCount > 0),
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
}

@Composable
private fun AiCandidateCard(
    item: AiEventCandidate,
    kind: String,
    selected: Boolean,
    multiple: Boolean,
    timeFormat: CalinoTimeFormat,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onKindChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val shape = RoundedCornerShape(11.dp)
    val accent = if (kind == "task") CalinoColors.Green else CalinoColors.Accent
    val title = item.title ?: if (kind == "task") "Untitled task" else "Untitled event"
    var dragX by remember(item, kind) { mutableFloatStateOf(0f) }
    var isDragging by remember(item, kind) { mutableStateOf(false) }
    var dismissing by remember(item, kind) { mutableStateOf(false) }
    var dismissDirection by remember(item, kind) { mutableStateOf(0f) }
    val dismissThresholdPx = with(density) { 108.dp.toPx() }
    val maxDragPx = with(density) { 180.dp.toPx() }
    val dismissDistancePx = with(density) { 520.dp.toPx() }
    val animatedOffset by animateFloatAsState(
        targetValue = when {
            dismissing -> dismissDirection * dismissDistancePx
            isDragging -> dragX
            else -> 0f
        },
        animationSpec = spring(dampingRatio = .86f, stiffness = 520f),
        label = "AI candidate swipe",
    )
    val offset = if (isDragging) dragX else animatedOffset
    val actionProgress = (abs(offset) / dismissThresholdPx).coerceIn(0f, 1f)
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = .38f) else CalinoColors.Ink.copy(alpha = .07f),
        animationSpec = tween(CalinoMotion.FadeThroughMillis),
        label = "AI candidate selection border",
    )

    LaunchedEffect(dismissing) {
        if (dismissing) {
            delay(220)
            currentOnDismiss()
        }
    }

    Box(modifier.fillMaxWidth().clip(shape)) {
        Row(
            Modifier
                .matchParentSize()
                .background(CalinoColors.Rose.copy(alpha = actionProgress * .92f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (offset < 0f) Arrangement.End else Arrangement.Start,
        ) {
            Icon(CalinoIcons.Trash, contentDescription = null, tint = CalinoColors.OnAccent.copy(alpha = actionProgress.coerceAtLeast(.72f)), modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Remove", color = CalinoColors.OnAccent.copy(alpha = actionProgress.coerceAtLeast(.72f)), style = CalinoTypography.bodyMedium)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .clip(shape)
                .background(CalinoColors.Panel)
                .border(BorderStroke(1.dp, borderColor), shape)
                .clickable(enabled = multiple && !dismissing, onClick = onToggle)
                .pointerInput(item, kind, dismissing) {
                    if (!dismissing) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                if (abs(dragX) > dismissThresholdPx) {
                                    dismissDirection = if (dragX < 0f) -1f else 1f
                                    dismissing = true
                                    isDragging = false
                                } else {
                                    isDragging = false
                                    dragX = 0f
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                dragX = 0f
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragX = (dragX + amount).coerceIn(-maxDragPx, maxDragPx)
                            },
                        )
                    }
                }
                .semantics {
                    contentDescription = "$title, ${if (selected) "selected" else "not selected"}. Swipe left or right to remove."
                }
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = CalinoTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = CalinoColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                aiDateLabel(item, timeFormat, kind)?.let { AiMetaRow(CalinoIcons.Calendar, it) }
                item.location?.takeIf(String::isNotBlank)?.let { AiMetaRow(CalinoIcons.Pin, it) }
                item.description?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = CalinoTypography.bodySmall, color = CalinoColors.Ink3, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                if (item.confidence != null && item.confidence != "high") {
                    Text("${item.confidence} confidence", style = CalinoTypography.labelSmall, color = CalinoColors.Accent, modifier = Modifier.padding(top = 4.dp))
                }
                CompactSegmentedControl(
                    options = listOf("Event", "Task"),
                    selectedIndex = if (kind == "task") 1 else 0,
                    onSelected = { index -> onKindChange(if (index == 1) "task" else "event") },
                    modifier = Modifier.align(Alignment.End).width(148.dp),
                    semanticLabel = "Result type",
                    maxControlWidth = 148.dp,
                )
            }
            if (multiple) {
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent.copy(alpha = .14f) else CalinoColors.Canvas)
                        .border(1.dp, if (selected) accent else CalinoColors.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(CalinoIcons.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                }
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
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("calino.malinov.ski://ai-photo-import"), context, MainActivity::class.java)
    val shortcut = ShortcutInfo.Builder(context, "ai-photo-import").setShortLabel("Photo import").setLongLabel("Import event from photo")
        .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_camera)).setIntent(intent).build()
    manager.dynamicShortcuts = listOf(shortcut)
}
