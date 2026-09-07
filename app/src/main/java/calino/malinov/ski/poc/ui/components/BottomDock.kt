package calino.malinov.ski.poc.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoTypography
import calino.malinov.ski.poc.ui.surfaces.PockRoute

/** A quiet, dock-like root navigator replacing the temporary floating menu. */
@Composable
fun BottomDock(
    selectedRoute: PockRoute,
    onRoute: (PockRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        DockItem("Calendar", PockRoute.Day, CalinoIcons.Calendar),
        DockItem("Tasks", PockRoute.Tasks, CalinoIcons.ListChecks),
        DockItem("Journal", PockRoute.Journal, CalinoIcons.BookOpen),
        DockItem("Settings", PockRoute.Settings, CalinoIcons.Settings),
    )
    BoxWithConstraints(
        modifier.fillMaxWidth().height(78.dp).background(CalinoColors.Panel).border(1.dp, CalinoColors.Line).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val dockItemGap = 6.dp
        val slotWidth = (maxWidth - dockItemGap * (items.size - 1)) / items.size
        val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
        val indicatorWidth = 66.dp
        val dockRowHeight = 60.dp
        val iconSlotHeight = 29.dp
        // DockItemView centers its 29dp icon slot together with the 3dp label
        // gap and the 12dp label line inside a 60dp item. Keep the animated
        // indicator on that same icon slot instead of at the row's top edge.
        val iconSlotTop = ((maxHeight - dockRowHeight) / 2).coerceAtLeast(0.dp) +
            (dockRowHeight - (iconSlotHeight + 3.dp + 12.dp)) / 2
        val indicatorX by animateDpAsState(
            // Match the Row's real item origin, including the gaps between
            // weighted slots. Omitting these gaps shifted later selections
            // left; Settings was visibly offset by three gaps.
            targetValue = (slotWidth + dockItemGap) * selectedIndex + (slotWidth - indicatorWidth) / 2,
            animationSpec = tween(240),
            label = "dock indicator position",
        )
        Box(
            Modifier
                .offset(x = indicatorX, y = iconSlotTop)
                .width(indicatorWidth)
                .height(iconSlotHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(CalinoColors.AccentSoft),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dockItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                DockItemView(item, selected = selectedRoute == item.route, onClick = { onRoute(item.route) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

private data class DockItem(val label: String, val route: PockRoute, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun DockItemView(item: DockItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val foreground by animateColorAsState(if (selected) CalinoColors.Accent else CalinoColors.Ink3, tween(180), label = "dock tint")
    val iconScale by animateFloatAsState(if (selected) 1.08f else 1f, tween(180), label = "dock icon scale")
    Column(
            modifier
            .height(60.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.label}${if (selected) ", selected" else ""}"
                stateDescription = if (selected) "Selected" else "Not selected"
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.width(66.dp).height(29.dp).clip(RoundedCornerShape(999.dp)).background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(19.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        }
        Text(item.label, style = CalinoTypography.labelSmall.copy(fontSize = 9.5.sp, letterSpacing = .35.sp), color = foreground, modifier = Modifier.padding(top = 3.dp))
    }
}
