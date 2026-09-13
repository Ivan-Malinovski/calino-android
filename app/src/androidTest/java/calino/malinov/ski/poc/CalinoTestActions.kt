package calino.malinov.ski.poc

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared finders for the device tests.
 *
 * Everything a test aims at goes through here, so a string the app owns is
 * written down once. The strings are deliberately re-derived rather than
 * imported: `FullDateFormatter` and friends are private to their composables,
 * and what these tests pin is the *user-visible* text, not an internal constant
 * that could be renamed without anyone noticing.
 */
object CalinoTestActions {

    /** The frozen "today". Matches `FixtureRepository.FixtureDate` and `PagerEpoch`. */
    val FixtureDate: LocalDate = LocalDate.of(2026, 5, 18)

    /** Mirrors `HomeScreen.FullDateFormatter`. Renders e.g. "Monday, May 18". */
    private val DayLabelFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

    const val MonthPager = "month-pager"
    const val WeekPager = "week-pager"
    const val DayPager = "day-pager"

    fun dayLabel(date: LocalDate): String = date.format(DayLabelFormatter)

    /** The zoom handle's description at [level], which it reports one-based. */
    fun zoomHandleLabel(level: Int): String = "Change calendar zoom, level ${level + 1} of 3"
}

/**
 * A day cell inside one pager, addressed by its visible date.
 *
 * Scoping to a pager is not optional: during a morph the month canvas, the week
 * strip and the day surface can all be mounted, and four separate composables
 * emit a day description.
 */
fun SemanticsNodeInteractionsProvider.dayCellIn(
    pagerTag: String,
    date: LocalDate,
): SemanticsNodeInteraction = onNode(inPager(pagerTag, CalinoTestActions.dayLabel(date)))

/**
 * Tap a day cell where its date number is, rather than at its centre.
 *
 * A cell's centre is over its event list, and an event chip is its own
 * clickable -- a centre tap on a busy day opens that event instead of selecting
 * the day. The date number at the top of the cell is where a person aims when
 * they mean "this day", and it is the cell's own click target.
 */
fun SemanticsNodeInteractionsProvider.selectDayIn(pagerTag: String, date: LocalDate) {
    dayCellIn(pagerTag, date).performTouchInput { click(percentOffset(.5f, DateNumberHeightFraction)) }
}

/**
 * Assert that [date] is the committed selection, by reading the cell's own
 * description rather than by hunting for "the selected node".
 *
 * The distinction matters: the pager keeps the adjacent page composed, and that
 * page marks its own equivalent day selected so the selection reads as
 * continuous while paging. "Exactly one selected cell exists" is therefore
 * false by design; "this cell is the selected one" is the real claim.
 */
fun SemanticsNodeInteractionsProvider.assertDaySelected(pagerTag: String, date: LocalDate) {
    dayCellIn(pagerTag, date).assertContentDescriptionContains(", selected", substring = true)
}

/** The opposite: this day is on screen and is *not* the committed selection. */
fun SemanticsNodeInteractionsProvider.assertDayNotSelected(pagerTag: String, date: LocalDate) {
    val description = dayCellIn(pagerTag, date)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.ContentDescription)
        .orEmpty()
        .joinToString()
    check(!description.contains(", selected")) { "expected $date not to be selected, but read: $description" }
}

private fun inPager(pagerTag: String, description: String): SemanticsMatcher =
    hasAnyAncestor(hasTestTag(pagerTag)) and hasContentDescription(description, substring = true)

/** Where the date number sits within a day cell, as a fraction of its height. */
private const val DateNumberHeightFraction = .12f

/**
 * Open a root destination through the navigation sidebar.
 *
 * [label] comes from `pockRouteLabel`: "Month", "Range", "Agenda", "Tasks",
 * "Journal", "Contacts", "Calendars", "Settings". A row reads its own label
 * when unselected and "<label>, selected" when it is the current route, so an
 * exact match on the bare label is what finds a destination you are not
 * already on.
 */
fun SemanticsNodeInteractionsProvider.openRoute(label: String) {
    onNodeWithContentDescription("Open navigation").performClick()
    // The sidebar scrolls, and Settings sits below the calendar list on a
    // phone. Clicking a row that is off-screen lands nowhere and fails
    // silently, which is a far more confusing failure than a scroll that was
    // not needed.
    onNodeWithContentDescription(label).performScrollTo().performClick()
}

/** Whether any node matches, without failing when none does. */
fun SemanticsNodeInteractionsProvider.exists(matcher: SemanticsMatcher): Boolean =
    onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

/** Whether a node with this exact visible text exists. */
fun SemanticsNodeInteractionsProvider.hasTextNode(text: String): Boolean =
    exists(hasText(text))

/** Whether a node with this exact content description exists. */
fun SemanticsNodeInteractionsProvider.hasDescribedNode(description: String): Boolean =
    exists(hasContentDescription(description))
