package calino.malinov.ski.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the optional first-run tour stands. It is offered once: [Offered] is
 * written the moment the offer appears, so an app killed before the person
 * answers does not ask again. Swiping the offer away, or skipping the tour,
 * is [Declined]; reaching the end is [Completed]. Neither is ever re-offered,
 * and Settings can replay the tour at any time.
 */
enum class CalinoTourState {
    NotOffered,
    Offered,
    Declined,
    Completed;

    companion object {
        fun fromName(name: String?): CalinoTourState = entries.firstOrNull { it.name == name } ?: NotOffered
    }
}

/** What a gesture did, reported by the screen that owns the gesture. */
enum class TourEvent {
    /** A vertical swipe moved the calendar to another zoom level or edge. */
    CalendarZoomed,

    /** Holding empty space opened the editor. */
    EditorOpenedByHold,

    /** Sliding the numbers on a clock moved the time by a quarter hour. */
    ClockSlid,

    /** An upward swipe on the pill opened the views menu (or search). */
    PillSwipedUp,

    /** Holding the pill switched it between its resting and dock shapes. */
    PillModeSwitched,
}

/** The tour's cards, in the order they are shown. */
enum class TourStep(val completedBy: TourEvent?) {
    Zoom(TourEvent.CalendarZoomed),
    Hold(TourEvent.EditorOpenedByHold),
    Clock(TourEvent.ClockSlid),
    PillSwipe(TourEvent.PillSwipedUp),
    PillHold(TourEvent.PillModeSwitched),

    /** Journal and Contacts are told about, not practised: they have no gesture to report. */
    Journal(null),
    Contacts(null);

    val informational: Boolean get() = completedBy == null
}

/**
 * The tour's one state holder. The card, the ghost finger and the success
 * wash only read it; the gesture that finishes a step is reported by the
 * handler that already owns it (`HomeScreen`, the editor launch), never by a
 * second pointer watcher, so the tour cannot disagree with the app about
 * whether the gesture happened.
 *
 * Nothing here writes calendar data. A step that opens the editor is closed
 * by the host without saving.
 */
class CalinoTour {
    /** The card on screen, or null while no tour is running. */
    var step by mutableStateOf<TourStep?>(null)
        private set

    /** True once the person has done the current step's gesture. */
    var performed by mutableStateOf(false)
        private set

    /** The cards this run shows, in order: a card for a feature the app lacks is left out. */
    var steps by mutableStateOf(TourStep.entries.toList())
        private set

    /**
     * Where the pill's view button sits in window coordinates, published by the
     * pill so the hold card's finger lands on the button that takes the hold.
     */
    var pillButtonCenter by mutableStateOf<Offset?>(null)

    /** Where the start clock sits in window coordinates, published by the editor's clock. */
    var startClockCenter by mutableStateOf<Offset?>(null)

    /** Bumped by Settings; the host answers it by going to the calendar and starting the tour. */
    var replayRequests by mutableIntStateOf(0)
        private set

    fun requestReplay() {
        replayRequests += 1
    }

    val running: Boolean get() = step != null

    fun start(shown: List<TourStep> = TourStep.entries) {
        steps = shown.ifEmpty { TourStep.entries.take(1) }
        step = steps.first()
        performed = false
    }

    /** Journal and Contacts are detected after launch; a running tour follows them. */
    fun refreshSteps(shown: List<TourStep>) {
        val current = step ?: return
        if (current in shown) steps = shown
    }

    /** Called by gesture owners; ignored unless it is the running step's gesture. */
    fun report(event: TourEvent) {
        val current = step ?: return
        if (current.completedBy == event) performed = true
    }

    /** Moves to the next card, or returns false when the last one has been passed. */
    fun advance(): Boolean {
        val current = step ?: return false
        val next = steps.getOrNull(steps.indexOf(current) + 1)
        performed = false
        step = next
        return next != null
    }

    fun stop() {
        step = null
        performed = false
    }
}

val LocalCalinoTour = staticCompositionLocalOf { CalinoTour() }

/**
 * The cards a run shows for this calendar. The dock only exists on the menu
 * pill, and Journal and Contacts are mentioned only when they are switched on.
 */
fun tourSteps(menuPill: Boolean, journalEnabled: Boolean, contactsEnabled: Boolean): List<TourStep> =
    TourStep.entries.filter {
        when (it) {
            TourStep.PillHold -> menuPill
            TourStep.Journal -> journalEnabled
            TourStep.Contacts -> contactsEnabled
            else -> true
        }
    }
