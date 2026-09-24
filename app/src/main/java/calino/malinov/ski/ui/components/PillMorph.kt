package calino.malinov.ski.ui.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
     * The glyph the root pill leads with -- the current view's, when the menu
     * pill is on -- or null for the label-only pill. Published beside
     * [addPillLabel] for the same reason: a modal pill morphs back into the
     * face the root pill actually has, not a label-only shape of its own.
     */
    var addPillLeadingIcon by mutableStateOf<ImageVector?>(null)
        internal set

    /** The leading glyph [addPillBounds] were measured with; see [addPillBoundsLabel]. */
    var addPillBoundsLeadingIcon by mutableStateOf<ImageVector?>(null)
        internal set

    /**
     * The views the root pill shows while it rests as the dock, or null when
     * it rests as the add pill. A modal pill morphs back into the dock then.
     */
    var addPillDock by mutableStateOf<List<PillRoute>?>(null)
        internal set

    /** The dock [addPillBounds] were measured with. */
    var addPillBoundsDock by mutableStateOf<List<PillRoute>?>(null)
        internal set

    /**
     * Records where the root pill is resting. Ignored while a modal holds the
     * lane: the root pill is only the anchor while it owns the lane, and a
     * root pill that is on its way out is still laid out on the frames it
     * spends leaving. Taking those, the modal pill chases the departing pill
     * off the bottom of the screen and stays wherever it last saw it.
     */
    internal fun setAddPill(bounds: Rect, label: String, leadingIcon: ImageVector?, dock: List<PillRoute>?) {
        if (claimedByModal) return
        addPillBounds = bounds
        addPillBoundsLabel = label
        addPillBoundsLeadingIcon = leadingIcon
        addPillBoundsDock = dock
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

    /**
     * Publishes the root surface behind the add pill while the root owns the
     * lane. A modal reads this retained layer on its very first draw, before
     * its own card backdrop effect has run, so the pill never falls back to
     * an opaque fill during the ownership handoff.
     */
    internal fun setRootBackdrop(layer: GraphicsLayer, origin: Offset) {
        if (claimedByModal) return
        backdrop = layer
        backdropOrigin = origin
    }

    /**
     * Where the lane is in the write cycle. The pill that started the record is
     * the thing that reports on it: the border traces while the write is in
     * flight and the label holds the outcome for a beat once it lands, rather
     * than a toast appearing somewhere else on screen.
     */
    var saveState by mutableStateOf(PillSaveState.Idle)
        private set

    /** Whether the write in the lane is putting a record down or taking one away. */
    var writeKind by mutableStateOf(PillWriteKind.Save)
        private set

    /** Writes currently in flight. The lane only settles when this reaches 0. */
    private var writesInFlight = 0
    private var savingSinceMillis = 0L
    /** Whether any write in the current run failed; the run cannot end on "Saved" then. */
    private var runFailed = false
    private var settleJob: Job? = null

    /**
     * A record started being written. Nested or overlapping writes share one
     * indicator: two saves in a row read as one continuous run rather than
     * restarting the trace.
     */
    fun saveStarted(kind: PillWriteKind = PillWriteKind.Save) {
        // A deliberate save is the more urgent thing happening on the pill.
        // An undo offer from elsewhere on screen folds away rather than
        // fighting it for the same label.
        undoJob?.cancel()
        undo = null
        settleJob?.cancel()
        settleJob = null
        writesInFlight += 1
        // The last thing asked for names the run. Removing something and then
        // saving something else inside one beat is rare, and reporting it as
        // the older of the two would name the wrong record.
        writeKind = kind
        if (saveState != PillSaveState.Saving) {
            runFailed = false
            savingSinceMillis = System.currentTimeMillis()
            saveState = PillSaveState.Saving
        }
    }

    /**
     * That write finished. [scope] drives the tail: the saving state is held
     * for [MinSavingMillis] from when it started so a write that returns in a
     * frame still reads as one beat instead of a flicker, and "Saved" is held
     * for [SavedHoldMillis] before the pill goes back to being an add button.
     */
    fun saveFinished(scope: CoroutineScope, success: Boolean) {
        writesInFlight = (writesInFlight - 1).coerceAtLeast(0)
        if (!success) runFailed = true
        if (writesInFlight > 0) return
        val landed = !runFailed
        settleJob?.cancel()
        settleJob = scope.launch {
            val elapsed = System.currentTimeMillis() - savingSinceMillis
            delay((MinSavingMillis - elapsed).coerceAtLeast(0L))
            if (!landed) {
                // A failure has its own message in the feedback lane. The pill
                // just stops rather than claiming something landed.
                saveState = PillSaveState.Idle
                return@launch
            }
            saveState = PillSaveState.Saved
            delay(SavedHoldMillis)
            saveState = PillSaveState.Idle
        }
    }

    /**
     * An outcome from elsewhere on screen -- a task checked off, an event
     * reopened -- named on the add pill instead of a floating banner, with
     * the one way back for [UndoHoldMillis] before it settles into whatever
     * the pill would otherwise be showing.
     */
    var undo by mutableStateOf<PillUndoState?>(null)
        private set

    private var undoJob: Job? = null

    /**
     * Shows [message] on the pill with an Undo action. A second call
     * replaces whatever the pill currently names rather than queuing behind
     * it -- only the most recent change is ever one tap away, matching the
     * banner this replaced.
     */
    fun showUndo(message: String, scope: CoroutineScope, onUndo: () -> Unit) {
        undoJob?.cancel()
        undo = PillUndoState(message) {
            undo = null
            onUndo()
        }
        undoJob = scope.launch {
            delay(UndoHoldMillis)
            undo = null
        }
    }

    internal fun claim() {
        claims += 1
        handingBack = false
        // The retained root layer contains the route subtree that is about to
        // host this modal. If the modal pill draws that layer on its first
        // frame, HWUI can see the layer as its own descendant and recurse
        // until RenderThread overflows its stack. The modal surface publishes
        // its sibling-only backdrop immediately after composition; use the
        // opaque glass fallback for the handoff frame instead of risking a
        // cyclic RenderNode tree.
        backdrop = null
        backdropOrigin = Offset.Zero
    }

    internal fun release() {
        claims = (claims - 1).coerceAtLeast(0)
        if (claims == 0) {
            handingBack = true
            dismissDrag = 0f
        }
    }
}

/** Where a write is in the lane's one-object report on it. */
enum class PillSaveState { Idle, Saving, Saved }

/** An undo offer standing in the lane: what to say, and the one way back. */
data class PillUndoState(val message: String, val onUndo: () -> Unit)

/**
 * What the write is doing to the record, which is all that separates the two
 * reports: same trace, same timing, different word and different colour on the
 * ring that closes it.
 */
enum class PillWriteKind { Save, Remove }

/**
 * The least time the saving state stays up. Local writes usually return in a
 * frame or two, and without a floor the trace would appear and vanish inside
 * the same blink -- which reads as a glitch, not as work being done.
 */
const val MinSavingMillis = 450L

/** How long the pill holds its outcome before turning back into "Add event". */
const val SavedHoldMillis = 1600L

/** How long an undo offer stands in the lane before it withdraws itself. */
const val UndoHoldMillis = 5_000L

/** The lane the surrounding screen provides; a default keeps previews working. */
val LocalCalinoPillLane = staticCompositionLocalOf { CalinoPillLane() }
