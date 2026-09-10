package calino.malinov.ski.poc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoShapes
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.state.LocalCalinoPreferences
import calino.malinov.ski.poc.ui.surfaces.PockRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The root navigator: a floating, card-like sidebar opened from the hamburger
 * in each screen header. It replaces the former bottom dock, so the root
 * surfaces keep their full height and the add pill can float above them.
 */
@Composable
fun NavSidebar(
    visible: Boolean,
    selectedRoute: PockRoute,
    onRoute: (PockRoute) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = LocalCalinoPreferences.current
    // The calendar views come first as a group; the rule separates them from
    // the other surfaces.
    val calendarItems = listOf(
        NavItem(pockRouteLabel(PockRoute.Day), PockRoute.Day, CalinoIcons.Calendar),
        NavItem(pockRouteLabel(PockRoute.Agenda), PockRoute.Agenda, CalinoIcons.AgendaList),
        NavItem(pockRouteLabel(PockRoute.Accounts), PockRoute.Accounts, CalinoIcons.Repeat),
    )
    val items = listOfNotNull(
        NavItem(pockRouteLabel(PockRoute.Tasks), PockRoute.Tasks, CalinoIcons.ListChecks),
        NavItem(pockRouteLabel(PockRoute.Journal), PockRoute.Journal, CalinoIcons.BookOpen)
            .takeIf { preferences.journalEnabled },
        NavItem(pockRouteLabel(PockRoute.Contacts), PockRoute.Contacts, CalinoIcons.Users)
            .takeIf { preferences.contactsEnabled },
        NavItem(pockRouteLabel(PockRoute.Settings), PockRoute.Settings, CalinoIcons.Settings),
    )

    var dragX by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val axisThresholdPx = with(density) { 8.dp.toPx() }
    val travelPx = with(density) { 360.dp.toPx() }

    LaunchedEffect(visible) {
        if (visible) {
            animationJob?.cancel()
            dragX = 0f
            dismissing = false
        } else {
            animationJob?.cancel()
        }
    }

    Box(modifier.fillMaxSize()) {
        CalinoScrim(
            visible = visible && !dismissing,
            onDismiss = onDismiss,
            modifier = Modifier.graphicsLayer { alpha = 1f - (abs(dragX) / travelPx).coerceIn(0f, 1f) },
        )
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(240)) { -it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(200)) { -it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            BoxWithConstraints {
                val cardWidth = if (maxWidth * .8f < 300.dp) maxWidth * .8f else 300.dp
                // Keep the pointer-input node stationary while the card moves,
                // so local pointer coordinates cannot chase the finger.
                Box(
                    Modifier.pointerInput(visible, dismissing) {
                        if (!visible || dismissing) return@pointerInput
                        awaitEachGestureCompat(
                            axisThresholdPx = axisThresholdPx,
                            onDrag = { total ->
                                animationJob?.cancel()
                                dragX = total.coerceIn(-travelPx, 0f)
                            },
                            onRelease = {
                                if (-dragX >= dismissThresholdPx) {
                                    // Hand off at release; the exit animation
                                    // above owns the remaining travel.
                                    dismissing = true
                                    currentOnDismiss()
                                } else {
                                    animationJob?.cancel()
                                    animationJob = scope.launch {
                                        animate(dragX, 0f, animationSpec = spring(dampingRatio = .86f, stiffness = 420f)) { value, _ -> dragX = value }
                                        animationJob = null
                                    }
                                }
                            },
                        )
                    },
                ) {
                    Column(
                        Modifier
                            .offset { IntOffset(dragX.roundToInt(), 0) }
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                            .width(cardWidth)
                            .fillMaxHeight()
                            // The flat-dark rule: a drop shadow is a light-mode
                            // device. In dark the Panel step and the hairline
                            // below carry the elevation instead.
                            .shadow(18.dp * CalinoColors.elevationAlpha, RoundedCornerShape(CalinoShapes.Card), clip = false)
                            .clip(RoundedCornerShape(CalinoShapes.Card))
                            .background(CalinoColors.Panel)
                            .border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card))
                            .padding(horizontal = 12.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Calino",
                            style = CalinoTypography.titleLarge.copy(fontSize = 22.sp),
                            modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
                        )
                        calendarItems.forEach { item ->
                            NavRow(item, selected = selectedRoute == item.route) {
                                onRoute(item.route)
                                onDismiss()
                            }
                        }
                        Box(
                            Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(CalinoColors.Line),
                        )
                        items.forEach { item ->
                            NavRow(item, selected = selectedRoute == item.route) {
                                onRoute(item.route)
                                onDismiss()
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

/**
 * The name a route goes by in the UI. The sidebar and the add pill's swipe
 * preview both point at the same views, so they read from one list rather than
 * drifting apart.
 */
fun pockRouteLabel(route: PockRoute): String = when (route) {
    PockRoute.Day -> "Month"
    PockRoute.Agenda -> "Agenda"
    PockRoute.Accounts -> "Calendars"
    PockRoute.Tasks -> "Tasks"
    PockRoute.Journal -> "Journal"
    PockRoute.Contacts -> "Contacts"
    PockRoute.Settings -> "Settings"
    PockRoute.Detail -> "Event"
    PockRoute.TaskDetail -> "Task"
    PockRoute.QuickAdd -> "Quick add"
    PockRoute.Notifications -> "Notifications"
}

private data class NavItem(
    val label: String,
    val route: PockRoute,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun NavRow(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val foreground by animateColorAsState(if (selected) CalinoColors.Accent else CalinoColors.Ink2, tween(180), label = "nav row tint")
    val background by animateColorAsState(
        if (selected) CalinoColors.AccentSoft else CalinoColors.Panel,
        tween(180),
        label = "nav row background",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalinoShapes.Row))
            .background(background)
            .calinoPressable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.label}${if (selected) ", selected" else ""}"
                stateDescription = if (selected) "Selected" else "Not selected"
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(item.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(19.dp))
        Text(item.label, style = CalinoTypography.bodyLarge, color = foreground)
    }
}

/**
 * Watches for a leftward drag in the Initial pass so the gesture can begin
 * anywhere on the card, including over its rows, while taps keep working.
 */
private suspend fun PointerInputScope.awaitEachGestureCompat(
    axisThresholdPx: Float,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val pointerId = down.id
        var lastPosition = down.position
        var totalX = 0f
        var totalY = 0f
        var horizontal = false
        var axisDecided = false
        var completed = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                completed = true
                break
            }
            val amount = change.position - lastPosition
            lastPosition = change.position
            totalX += amount.x
            totalY += amount.y
            if (!axisDecided && (abs(totalX) > axisThresholdPx || abs(totalY) > axisThresholdPx)) {
                horizontal = abs(totalX) > abs(totalY)
                axisDecided = true
            }
            if (axisDecided && horizontal && totalX < 0f) {
                change.consume()
                onDrag(totalX)
            }
        }

        if (completed && axisDecided && horizontal) onRelease()
    }
}
