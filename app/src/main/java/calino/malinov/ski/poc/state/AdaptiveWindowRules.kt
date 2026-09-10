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
    CalinoWindowClass.Expanded -> if (kind == CalinoSurfaceKind.Search || kind == CalinoSurfaceKind.Dialog) {
        CalinoSurfaceMode.FloatingWindow
    } else {
        CalinoSurfaceMode.EndPanel
    }
}
