package calino.malinov.ski.ui.components

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import calino.malinov.ski.ui.home.DayPagerPageCount
import calino.malinov.ski.ui.home.dateForDayPage
import calino.malinov.ski.ui.home.dayPageFor
import java.time.LocalDate
import kotlinx.coroutines.flow.distinctUntilChanged

/** UI state owned by the host while Month and Year share a stationary pane. */
@Stable
class SplitDayPaneState internal constructor(
    val pager: PagerState,
    private val collapsedState: MutableState<Boolean>,
    private val widthState: State<Dp>,
) {
    var collapsed: Boolean by collapsedState
    val width: Dp get() = widthState.value
}

@Composable
fun rememberSplitDayPaneState(
    day: LocalDate,
    enabled: Boolean,
    onDateChanged: (LocalDate) -> Unit,
): SplitDayPaneState {
    val pager = rememberPagerState(initialPage = dayPageFor(day)) { DayPagerPageCount }
    val collapsed = rememberSaveable { mutableStateOf(false) }
    val width = animatedDayPaneWidthState(collapsed.value)
    val state = remember(pager) { SplitDayPaneState(pager, collapsed, width) }
    val currentDay by rememberUpdatedState(day)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnDateChanged by rememberUpdatedState(onDateChanged)
    var dragOrigin by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) dragOrigin = currentDay
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }
            .distinctUntilChanged()
            .collect { (scrolling, page) ->
                if (scrolling) return@collect
                if (currentEnabled && dragOrigin == currentDay) {
                    val next = dateForDayPage(page)
                    if (next != currentDay) currentOnDateChanged(next)
                }
                dragOrigin = null
            }
    }
    // Programmatic navigation follows an already committed date; intermediate
    // pages never feed back into selection. Route switches do not restart this.
    LaunchedEffect(day, enabled) {
        if (enabled && dayPageFor(day) != pager.settledPage) {
            pager.animateScrollToPage(dayPageFor(day))
        }
    }
    return state
}
