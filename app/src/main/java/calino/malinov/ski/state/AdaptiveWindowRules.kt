package calino.malinov.ski.state

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
    EventPreviewCompact(widthCapDp = 400, heightCapDp = 560),
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
 * Whether this window has an end lane -- the right-hand column the floating
 * pill rides in. It opens with the landscape split, which happens at a
 * narrower window than [CalinoWindowClass.Expanded] begins, and it is the one
 * answer both the pill and the transient surfaces read: anything that
 * continues out of the pill has to be placed where the pill actually is.
 */
fun calinoEndLaneActive(widthDp: Int, heightDp: Int): Boolean = shouldSplit(widthDp, heightDp)

/**
 * Search is a command surface, so it remains a centered window even with an
 * end lane. The calendar/detail/editor surfaces use the logical end edge once
 * there is a lane to sit in, which leaves useful context visible behind them.
 */
fun calinoSurfaceModeFor(
    windowClass: CalinoWindowClass,
    kind: CalinoSurfaceKind,
    endLane: Boolean = windowClass == CalinoWindowClass.Expanded,
): CalinoSurfaceMode = when {
    windowClass == CalinoWindowClass.Compact -> CalinoSurfaceMode.BottomSheet
    !endLane -> CalinoSurfaceMode.FloatingWindow
    // The previews are deliberately short; stretching one down a full-height
    // panel would be a lot of card around very little content. They stay
    // floating windows and are placed in the lane instead.
    kind == CalinoSurfaceKind.Search ||
        kind == CalinoSurfaceKind.Dialog ||
        kind == CalinoSurfaceKind.CompactPreview ||
        kind == CalinoSurfaceKind.Preview -> CalinoSurfaceMode.FloatingWindow
    else -> CalinoSurfaceMode.EndPanel
}

/**
 * Whether a floating surface should be centred on the end lane rather than on
 * the window. Search and the dialogs are window-level commands and stay in the
 * middle; everything else carries a lane pill and belongs under it.
 */
fun calinoFloatsInEndLane(kind: CalinoSurfaceKind): Boolean =
    kind != CalinoSurfaceKind.Search && kind != CalinoSurfaceKind.Dialog

/**
 * Narrowest window that splits into two panes *because of a hinge*. A half-open
 * book already divides the surface for you, so the two-column layout reads well
 * below [SplitPaneMinWidthDp] -- but not on a genuinely small window.
 */
const val BookPostureSplitMinWidthDp = 600

enum class CalinoLayoutMode { Single, SideBySide, HorizontalKeepOut }

data class CalinoPaneBounds(
    val leftDp: Float,
    val topDp: Float,
    val rightDp: Float,
    val bottomDp: Float,
) {
    val widthDp: Float get() = (rightDp - leftDp).coerceAtLeast(0f)
    val heightDp: Float get() = (bottomDp - topDp).coerceAtLeast(0f)
}

/**
 * One answer for "what shape is this window", so the surface rules and the
 * month root cannot disagree about the same device.
 */
data class CalinoLayoutSpec(
    val windowClass: CalinoWindowClass,
    val mode: CalinoLayoutMode,
    val startPane: CalinoPaneBounds,
    val endPane: CalinoPaneBounds?,
    val hinge: CalinoPaneBounds?,
) {
    val splitPanes: Boolean get() = mode == CalinoLayoutMode.SideBySide
    val hingeStartDp: Float?
        get() = hinge?.takeIf { mode == CalinoLayoutMode.SideBySide }?.leftDp
    val hingeEndDp: Float?
        get() = hinge?.takeIf { mode == CalinoLayoutMode.SideBySide }?.rightDp
    /** Width of the band no content should straddle; zero when there is none. */
    val hingeBandDp: Float
        get() = hinge?.let { if (mode == CalinoLayoutMode.SideBySide) it.widthDp else it.heightDp } ?: 0f

    val preferredContentPane: CalinoPaneBounds get() = startPane
    val preferredTransientPane: CalinoPaneBounds
        get() = endPane ?: startPane
}

fun calinoLayoutSpec(
    widthDp: Int,
    heightDp: Int,
    posture: CalinoFoldPosture = CalinoFoldPosture.None,
): CalinoLayoutSpec {
    val bookSplit = posture.isBookPosture && widthDp >= BookPostureSplitMinWidthDp
    val sideBySide = shouldSplit(widthDp, heightDp) || bookSplit
    val full = CalinoPaneBounds(0f, 0f, widthDp.toFloat(), heightDp.toFloat())
    if (posture.isTabletopPosture) {
        val top = posture.hingeStartDp!!.coerceIn(0f, heightDp.toFloat())
        val bottom = posture.hingeEndDp!!.coerceIn(top, heightDp.toFloat())
        return CalinoLayoutSpec(
            windowClass = calinoWindowClassFor(widthDp),
            mode = CalinoLayoutMode.HorizontalKeepOut,
            startPane = CalinoPaneBounds(0f, 0f, widthDp.toFloat(), top),
            endPane = CalinoPaneBounds(0f, bottom, widthDp.toFloat(), heightDp.toFloat()),
            hinge = CalinoPaneBounds(0f, top, widthDp.toFloat(), bottom),
        )
    }
    val realHinge = posture.takeIf { it.isBookPosture }?.let {
        val left = it.hingeStartDp!!.coerceIn(0f, widthDp.toFloat())
        val right = it.hingeEndDp!!.coerceIn(left, widthDp.toFloat())
        CalinoPaneBounds(left, 0f, right, heightDp.toFloat())
    }
    val splitAt = realHinge?.leftDp ?: (widthDp - EndLaneWidthDp).coerceAtLeast(1).toFloat()
    val endAt = realHinge?.rightDp ?: splitAt
    return CalinoLayoutSpec(
        windowClass = calinoWindowClassFor(widthDp),
        mode = if (sideBySide) CalinoLayoutMode.SideBySide else CalinoLayoutMode.Single,
        startPane = if (sideBySide) CalinoPaneBounds(0f, 0f, splitAt, heightDp.toFloat()) else full,
        endPane = if (sideBySide) CalinoPaneBounds(endAt, 0f, widthDp.toFloat(), heightDp.toFloat()) else null,
        hinge = realHinge,
    )
}
