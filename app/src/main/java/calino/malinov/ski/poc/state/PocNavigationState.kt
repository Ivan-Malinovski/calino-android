package calino.malinov.ski.poc.state

/** Origin carried by an overlay so dismiss/back restores the visible surface. */
enum class PocReturnTarget { Calendar, Agenda, DayModal, Tasks, Journal, Settings, Detail, TaskDetail }

fun PocReturnTarget.restoresDayModal(): Boolean = this == PocReturnTarget.DayModal

fun PocReturnTarget.restoresTasks(): Boolean = this == PocReturnTarget.Tasks

fun PocReturnTarget.restoresJournal(): Boolean = this == PocReturnTarget.Journal

fun PocReturnTarget.restoresAgenda(): Boolean = this == PocReturnTarget.Agenda

/**
 * Narrowest window that can carry a readable seven-column month grid *and* an
 * agenda column beside it. Below this the portrait zoom surface is the better
 * layout, even in landscape.
 */
const val SplitPaneMinWidthDp = 720

/** The day pane's own width once the window is wide enough to show it. */
const val SplitPaneWidthDp = 360

/**
 * Whether the month root should render as two panes. Landscape alone is not
 * enough: a compact phone turned sideways is still too narrow for both columns.
 */
fun shouldSplit(widthDp: Int, heightDp: Int): Boolean =
    widthDp >= SplitPaneMinWidthDp && widthDp > heightDp
