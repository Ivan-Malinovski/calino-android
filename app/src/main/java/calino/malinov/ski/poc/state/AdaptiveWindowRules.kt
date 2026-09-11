package calino.malinov.ski.poc.state

/** Width buckets are based on the window the composition actually receives. */
enum class CalinoWindowClass {
    Compact,
    Medium,
    Expanded,
}
/** The visual treatment used for transient surfaces at a given window size. */
enum class CalinoSurfaceMode {
    BottomSheet,
    FloatingWindow,
    EndPanel,
}

/** Surface content types with deliberately different readable widths. */
enum class CalinoSurfaceKind(val widthCapDp: Int, val heightCapDp: Int) {
    Day(widthCapDp = 440, heightCapDp = 760),
    CompactPreview(widthCapDp = 400, heightCapDp = 360),
    Preview(widthCapDp = 420, heightCapDp = 560),
    Detail(widthCapDp = 520, heightCapDp = 760),
    Editor(widthCapDp = 640, heightCapDp = 820),
    Search(widthCapDp = 720, heightCapDp = 680),
    Dialog(widthCapDp = 520, heightCapDp = 620),
}

const val CompactWindowMaxWidthDp = 599
const val MediumWindowMaxWidthDp = 839

fun calinoWindowClassFor(widthDp: Int): CalinoWindowClass = when {
    widthDp <= CompactWindowMaxWidthDp -> CalinoWindowClass.Compact
    widthDp <= MediumWindowMaxWidthDp -> CalinoWindowClass.Medium
    else -> CalinoWindowClass.Expanded
}

/**
 * Search is a command surface, so it remains a centered window even on an
 * expanded display. The calendar/detail/editor surfaces use the logical end
 * edge once there is enough room to leave useful context visible behind them.
 */
fun calinoSurfaceModeFor(
    windowClass: CalinoWindowClass,
    kind: CalinoSurfaceKind,
): CalinoSurfaceMode = when (windowClass) {
    CalinoWindowClass.Compact -> CalinoSurfaceMode.BottomSheet
    CalinoWindowClass.Medium -> CalinoSurfaceMode.FloatingWindow
    CalinoWindowClass.Expanded -> if (
        kind == CalinoSurfaceKind.Search ||
        kind == CalinoSurfaceKind.Dialog ||
        kind == CalinoSurfaceKind.CompactPreview ||
        kind == CalinoSurfaceKind.Preview
    ) {
        CalinoSurfaceMode.FloatingWindow
    } else {
        CalinoSurfaceMode.EndPanel
    }
}

/**
 * Narrowest window that splits into two panes *because of a hinge*. A half-open
 * book already divides the surface for you, so the two-column layout reads well
 * below [SplitPaneMinWidthDp] -- but not on a genuinely small window.
 */
const val BookPostureSplitMinWidthDp = 600

/**
 * One answer for "what shape is this window", so the surface rules and the
 * month root cannot disagree about the same device.
 */
data class CalinoLayoutSpec(
    val windowClass: CalinoWindowClass,
    val splitPanes: Boolean,
    val hingeStartDp: Float?,
    val hingeEndDp: Float?,
) {
    /** Width of the band no content should straddle; zero when there is none. */
    val hingeBandDp: Float
        get() = if (hingeStartDp != null && hingeEndDp != null) hingeEndDp - hingeStartDp else 0f
}

fun calinoLayoutSpec(
    widthDp: Int,
    heightDp: Int,
    posture: CalinoFoldPosture = CalinoFoldPosture.None,
): CalinoLayoutSpec {
    val bookSplit = posture.isBookPosture && widthDp >= BookPostureSplitMinWidthDp
    // The keep-out band is only meaningful across a vertical hinge in the pose
    // where the crease is a real edge. Flat, the fold is just a seam.
    val separating = posture.isBookPosture
    return CalinoLayoutSpec(
        windowClass = calinoWindowClassFor(widthDp),
        splitPanes = shouldSplit(widthDp, heightDp) || bookSplit,
        hingeStartDp = if (separating) posture.hingeStartDp else null,
        hingeEndDp = if (separating) posture.hingeEndDp else null,
    )
}
