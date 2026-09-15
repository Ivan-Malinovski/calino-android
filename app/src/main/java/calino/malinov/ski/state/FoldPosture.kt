package calino.malinov.ski.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How far open the device is. A book-style foldable spends most of its life at
 * one of the two extremes; [HalfOpen] is the pose worth reacting to, because
 * there the crease is a physical edge running through the layout.
 */
enum class CalinoFoldState { Flat, HalfOpen }

enum class CalinoHingeOrientation { Vertical, Horizontal }

/**
 * What the window knows about the hinge, reduced to the few numbers the layout
 * rules actually use. [hingeStartDp]/[hingeEndDp] are only set when the hinge
 * separates the window into two logical halves -- a flat inner display reports
 * a fold feature too, but nothing needs to move out of its way.
 */
@Immutable
data class CalinoFoldPosture(
    val state: CalinoFoldState,
    val isVerticalHinge: Boolean,
    val hingeStartDp: Float?,
    val hingeEndDp: Float?,
    /** True only when WindowManager has identified a physical folding feature. */
    val hasFoldingFeature: Boolean = false,
) {
    val isSeparating: Boolean get() = hingeStartDp != null && hingeEndDp != null

    /** True for the tabletop/book pose the displacement rules care about. */
    val isBookPosture: Boolean
        get() = state == CalinoFoldState.HalfOpen && isVerticalHinge && isSeparating

    val isTabletopPosture: Boolean
        get() = state == CalinoFoldState.HalfOpen && !isVerticalHinge && isSeparating

    val orientation: CalinoHingeOrientation
        get() = if (isVerticalHinge) CalinoHingeOrientation.Vertical else CalinoHingeOrientation.Horizontal

    companion object {
        /** A phone, a tablet, or a foldable lying flat: no hinge to dodge. */
        val None = CalinoFoldPosture(CalinoFoldState.Flat, isVerticalHinge = false, null, null)
    }
}

/**
 * Reduce a window layout report to [CalinoFoldPosture]. Deliberately takes
 * plain values rather than a `FoldingFeature` so the rule is testable on the
 * JVM, the way the rest of the adaptive rules are.
 */
fun foldPostureOf(
    isVerticalHinge: Boolean,
    isHalfOpen: Boolean,
    isSeparating: Boolean,
    hingeStartDp: Float,
    hingeEndDp: Float,
): CalinoFoldPosture {
    val separating = isSeparating && hingeEndDp >= hingeStartDp
    return CalinoFoldPosture(
        state = if (isHalfOpen) CalinoFoldState.HalfOpen else CalinoFoldState.Flat,
        isVerticalHinge = isVerticalHinge,
        hingeStartDp = if (separating) hingeStartDp else null,
        hingeEndDp = if (separating) hingeEndDp else null,
        hasFoldingFeature = true,
    )
}

val LocalFoldPosture = staticCompositionLocalOf { CalinoFoldPosture.None }
