package calino.malinov.ski.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import calino.malinov.ski.design.CalinoMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The two destinations beyond the calendar's three density stops. */
enum class CalendarEdge { Year, Agenda }

/** Piecewise screen distance keeps crossing and reversing an endpoint exactly reversible. */
internal fun calendarZoomAfterDrag(zoom: Float, deltaDp: Float): Float {
    val distance = when {
        zoom < 0f -> zoom * 220f
        zoom > 2f -> 560f + (zoom - 2f) * 220f
        else -> zoom * 280f
    }
    val next = (distance + deltaDp).coerceIn(-220f, 780f)
    return when {
        next < 0f -> next / 220f
        next > 560f -> 2f + (next - 560f) / 220f
        else -> next / 280f
    }
}

internal fun commitCalendarEdge(progress: Float, outwardVelocity: Float): Boolean = when {
    outwardVelocity <= -650f -> false
    outwardVelocity >= 650f -> progress >= .06f
    else -> progress >= .45f
}

/** The host owns route settlement; Home owns the pointer stream all the way to release. */
@Stable
class CalendarEdgeTransition(private val scope: CoroutineScope) {
    var target by mutableStateOf<CalendarEdge?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var settling by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun drag(edge: CalendarEdge?, fraction: Float) {
        job?.cancel()
        job = null
        settling = false
        target = edge
        progress = fraction.coerceIn(0f, 1f)
    }

    fun release(outwardVelocity: Float, cancelled: Boolean, commit: (CalendarEdge) -> Unit) {
        val edge = target ?: return
        val complete = !cancelled && commitCalendarEdge(progress, outwardVelocity)
        job?.cancel()
        settling = true
        job = scope.launch {
            animate(
                initialValue = progress,
                targetValue = if (complete) 1f else 0f,
                animationSpec = if (complete) CalinoMotion.standardSpatial() else CalinoMotion.gestureReturn(),
            ) { value, _ -> progress = value.coerceIn(0f, 1f) }
            if (complete) commit(edge)
            target = null
            progress = 0f
            settling = false
        }
    }
}
