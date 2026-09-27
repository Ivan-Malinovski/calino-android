package calino.malinov.ski.ui.components

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp

/**
 * Where the record a surface is about to show was last touched on screen.
 *
 * A detail card grows out of the event it describes rather than arriving
 * from nowhere. The card cannot know where that was: the tap happened on
 * whichever calendar surface the person was looking at, and the detail is
 * opened later through route state. So every event visual publishes its
 * bounds on pointer down, and the detail takes them -- once, and only when
 * fresh and for the same record. Anything else (an accessibility click, a
 * notification, a restored route) finds nothing and keeps the default motion.
 *
 * This is transient UI state on the main thread; it is never persisted.
 */
class CalinoSurfaceOrigin {
    private var key: String? = null
    private var rect: Rect? = null
    private var cornerRadius: Dp? = null
    private var atMillis = 0L

    fun record(key: String, rect: Rect, cornerRadius: Dp) {
        this.key = key
        this.rect = rect
        this.cornerRadius = cornerRadius
        atMillis = SystemClock.uptimeMillis()
    }

    /** The recorded origin for [key] if it is recent, clearing it either way. */
    fun take(key: String, maxAgeMillis: Long = OriginMaxAgeMillis): SurfaceOriginBounds? {
        val fresh = this.key == key && SystemClock.uptimeMillis() - atMillis <= maxAgeMillis
        val result = if (fresh) rect?.let { SurfaceOriginBounds(it, cornerRadius!!) } else null
        this.key = null
        rect = null
        cornerRadius = null
        return result
    }
}

/** A root-coordinate rectangle a surface grows from and returns to. */
data class SurfaceOriginBounds(val rect: Rect, val cornerRadius: Dp)

/**
 * Long enough to cover a day card's own close animation before the detail it
 * hands over to is composed; short enough that a stale touch is never reused.
 */
private const val OriginMaxAgeMillis = 900L

val LocalCalinoSurfaceOrigin = staticCompositionLocalOf { CalinoSurfaceOrigin() }

/**
 * Publishes this element's bounds as the origin for [key] when it is pressed.
 *
 * It only observes the pointer stream: nothing is consumed, so the element's
 * own click, long press and drag handling are unaffected. When [enabled] is
 * false nothing is recorded and the surface slides up from the bottom instead;
 * list rows and single-day rails span the width, so growing from them reads
 * as a jump rather than a transform.
 */
fun Modifier.calinoSurfaceOrigin(key: String, cornerRadius: Dp, enabled: Boolean = true): Modifier = if (!enabled) this else composed {
    val recorder = LocalCalinoSurfaceOrigin.current
    // A plain holder: bounds change on every scroll frame and nothing needs
    // to recompose when they do.
    val bounds = remember { arrayOfNulls<Rect>(1) }
    this
        .onGloballyPositioned { coords ->
            val size = coords.size
            bounds[0] = Rect(coords.positionInRoot(), Size(size.width.toFloat(), size.height.toFloat()))
        }
        .pointerInput(key) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                bounds[0]?.let { recorder.record(key, it, cornerRadius) }
            }
        }
}
