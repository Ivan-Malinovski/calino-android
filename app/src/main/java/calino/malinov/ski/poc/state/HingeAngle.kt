package calino.malinov.ski.poc.state

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How far open the hinge is: 0 shut, 1 flat.
 *
 * `FoldingFeature` only ever says FLAT or HALF_OPENED, which is enough to pick
 * a layout and useless for following the motion. The hinge angle sensor
 * (`Sensor.TYPE_HINGE_ANGLE`, API 30) reports degrees continuously, so the fold
 * can be followed rather than replayed after the fact.
 */
fun hingeOpenness(angleDegrees: Float, maxAngleDegrees: Float): Float {
    val range = if (maxAngleDegrees > 1f) maxAngleDegrees else 180f
    return (angleDegrees / range).coerceIn(0f, 1f)
}

/**
 * Openness at which a fold starts to divide the layout, and the openness by
 * which the division is complete. Roughly 175 degrees to 125 degrees on a
 * hinge that reports a half turn: the split begins the moment the device
 * leaves flat, and is done well before the two halves face each other.
 */
private const val SplitEngageOpenness = .97f
private const val SplitCompleteOpenness = .70f

/**
 * How far a fold in progress has divided the layout: 0 while flat, 1 once the
 * device is properly bent.
 *
 * This is the whole point of reading the angle rather than
 * `FoldingFeature.State`. The panes do not wait for the system to declare
 * HALF_OPENED and then jump; they part as the device parts, and stop wherever
 * the hinge stops.
 */
fun foldSplitProgress(openness: Float): Float {
    val o = openness.coerceIn(0f, 1f)
    val span = SplitEngageOpenness - SplitCompleteOpenness
    return ((SplitEngageOpenness - o) / span).coerceIn(0f, 1f)
}

/**
 * The live hinge, or null on a device without one -- which is most of them,
 * and the reason nothing may read this without a fallback.
 */
val LocalHingeOpenness = staticCompositionLocalOf<State<Float>?> { null }
