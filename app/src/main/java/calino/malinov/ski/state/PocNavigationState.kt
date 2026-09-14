package calino.malinov.ski.state

/** Origin carried by an overlay so dismiss/back restores the visible surface. */
enum class PocReturnTarget { Calendar, Range, Agenda, DayModal, Tasks, Journal, Contacts, Settings, Accounts, Detail, TaskDetail, Search }

fun PocReturnTarget.restoresDayModal(): Boolean = this == PocReturnTarget.DayModal

fun PocReturnTarget.restoresTasks(): Boolean = this == PocReturnTarget.Tasks

fun PocReturnTarget.restoresJournal(): Boolean = this == PocReturnTarget.Journal

fun PocReturnTarget.restoresAgenda(): Boolean = this == PocReturnTarget.Agenda

fun PocReturnTarget.restoresContacts(): Boolean = this == PocReturnTarget.Contacts

/**
 * Narrowest window that can carry a readable seven-column month grid *and* an
 * agenda column beside it. Below this the portrait zoom surface is the better
 * layout, even in landscape.
 */
const val SplitPaneMinWidthDp = 720

/** The day pane's own width once the window is wide enough to show it. */
const val SplitPaneWidthDp = 360

/**
 * The end lane: the day pane's column plus the gutter around it. The floating
 * pill and anything that continues out of it are centred on this lane.
 */
const val EndLaneWidthDp = SplitPaneWidthDp + 44
const val ContactsListPaneWidthDp = 360

enum class ContactsPaneMode { Sheet, FloatingWindow, EndPanel, Split }

fun contactsPaneModeFor(widthDp: Int, heightDp: Int): ContactsPaneMode {
    if (shouldSplit(widthDp, heightDp)) return ContactsPaneMode.Split
    return when (calinoWindowClassFor(widthDp)) {
        CalinoWindowClass.Compact -> ContactsPaneMode.Sheet
        CalinoWindowClass.Medium -> ContactsPaneMode.FloatingWindow
        CalinoWindowClass.Expanded -> ContactsPaneMode.EndPanel
    }
}

/**
 * Whether the month root should render as two panes. Landscape alone is not
 * enough: a compact phone turned sideways is still too narrow for both columns.
 */
fun shouldSplit(widthDp: Int, heightDp: Int): Boolean =
    widthDp >= SplitPaneMinWidthDp && widthDp > heightDp
