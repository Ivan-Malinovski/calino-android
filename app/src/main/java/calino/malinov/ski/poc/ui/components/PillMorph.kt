package calino.malinov.ski.poc.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer

/**
 * The pill lane: the one place on screen the add affordance lives, and the
 * handoff between the root [AddPill] and the [ModalActionPill] a modal shows
 * in the same spot.
 *
 * The two are separate composables in separate subtrees, so nothing but this
 * shared state can make them read as a single object that changes shape. The
 * lane carries the root pill's measured width, so a modal pill can start from
 * the exact shape the person just tapped, and a claim count, so the root pill
 * knows to leave and return without its own slide -- the morph is the whole
 * transition, and a second one underneath it would read as two controls.
 */
@Stable
class CalinoPillLane {
    /**
     * Where the root add pill last sat, in root coordinates, or null before it
     * has laid out. A modal pill is placed against this rather than against a
     * rule of its own: the root pill is centred in portrait but rides a
     * right-hand lane on a wide screen, and re-deriving that from layout
     * constants is how a modal pill ends up somewhere the root pill is not.
     */
    var addPillBounds by mutableStateOf<Rect?>(null)
        internal set

    /**
     * The label the root pill is showing right now, published by whoever owns
     * that pill whether or not it is currently on screen. A modal pill morphs
     * back into *this*, not into a label of its own: event detail used to name
     * the event's own date there, so the shape that handed the lane back read
     * a different day than the pill that took it, and changed width as it was
     * swapped out.
     */
    var addPillLabel by mutableStateOf<String?>(null)
        internal set

    /**
     * The label [addPillBounds] were measured with. The bounds are only worth
     * trusting while the root pill still says the same thing; past that the
     * modal pill measures the label itself rather than snapping to a size
     * recorded for different text.
     */
    var addPillBoundsLabel by mutableStateOf<String?>(null)
        internal set

    /**
     * Records where the root pill is resting. Ignored while a modal holds the
     * lane: the root pill is only the anchor while it owns the lane, and a
     * root pill that is on its way out is still laid out on the frames it
     * spends leaving. Taking those, the modal pill chases the departing pill
     * off the bottom of the screen and stays wherever it last saw it.
     */
    internal fun setAddPill(bounds: Rect, label: String) {
        if (claimedByModal) return
        addPillBounds = bounds
        addPillBoundsLabel = label
    }

    private var claims by mutableIntStateOf(0)

    /** True while a modal pill stands in the lane. */
    val claimedByModal: Boolean get() = claims > 0

    /**
     * True for the frames right after the last modal pill left. The root pill
     * takes the lane back silently here: the modal pill has already morphed
     * back into its shape, so an entry animation would replay the arrival.
     */
    var handingBack by mutableStateOf(false)
        internal set

    /**
     * How far the card holding the modal pill has been dragged toward its
     * dismissal, 0 until the gesture starts and 1 at the point where letting
     * go closes the card. The pill returns to its add shape along this, so the
     * morph tracks the finger and reverses with it when the drag is abandoned,
     * instead of waiting for the release to decide.
     */
    var dismissDrag by mutableFloatStateOf(0f)
        internal set

    /**
     * What the modal pill is standing over, recorded by the surface hosting
     * it, so it can be made of the same glass as the root pill instead of
     * turning into an opaque slab the moment a card opens.
     */
    var backdrop by mutableStateOf<GraphicsLayer?>(null)
        internal set
    var backdropOrigin by mutableStateOf(Offset.Zero)
        internal set

    /**
     * How far the pill standing in the lane has morphed, 0 at the add shape
     * and 1 at the modal's actions. A card that puts something beside its
     * pill -- event detail's overflow button -- fades it in against this.
     */
    var morphProgress by mutableFloatStateOf(0f)
        internal set

    internal fun setBackdrop(layer: GraphicsLayer?, origin: Offset) {
        backdrop = layer
        backdropOrigin = origin
    }

    internal fun claim() {
        claims += 1
        handingBack = false
    }

    internal fun release() {
        claims = (claims - 1).coerceAtLeast(0)
        if (claims == 0) {
            handingBack = true
            dismissDrag = 0f
        }
    }
}

/** The lane the surrounding screen provides; a default keeps previews working. */
val LocalCalinoPillLane = staticCompositionLocalOf { CalinoPillLane() }
