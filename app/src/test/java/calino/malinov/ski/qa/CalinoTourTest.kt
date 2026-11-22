package calino.malinov.ski.qa

import calino.malinov.ski.state.CalinoTour
import calino.malinov.ski.state.CalinoTourState
import calino.malinov.ski.state.TourEvent
import calino.malinov.ski.state.TourStep
import calino.malinov.ski.state.tourSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first-run tour's state machine. The card, the ghost finger and the
 * success wash only read this, so what it accepts and refuses is the whole
 * contract: a step completes from its own gesture and nothing else, and a card
 * for a feature the app does not have never appears.
 */
class CalinoTourTest {

    @Test fun anUnknownStoredNameIsStillOffered() {
        assertEquals(CalinoTourState.NotOffered, CalinoTourState.fromName(null))
        assertEquals(CalinoTourState.NotOffered, CalinoTourState.fromName("TourFinished"))
        CalinoTourState.entries.forEach { assertEquals(it, CalinoTourState.fromName(it.name)) }
    }

    @Test fun everyPractisedCardNamesAGestureAndInformationalCardsDoNot() {
        assertEquals(
            listOf(TourStep.Zoom, TourStep.Hold, TourStep.Clock, TourStep.PillSwipe, TourStep.PillHold),
            TourStep.entries.filterNot { it.informational },
        )
        assertEquals(listOf(TourStep.Journal, TourStep.Contacts), TourStep.entries.filter { it.informational })
        assertTrue(TourStep.entries.filterNot { it.informational }.all { it.completedBy != null })
    }

    @Test fun theMenuPillAndTheOptionalSurfacesDecideWhichCardsRun() {
        assertEquals(
            listOf(TourStep.Zoom, TourStep.Hold, TourStep.Clock, TourStep.PillSwipe),
            tourSteps(menuPill = false, journalEnabled = false, contactsEnabled = false),
        )
        assertEquals(TourStep.entries, tourSteps(menuPill = true, journalEnabled = true, contactsEnabled = true))
        assertEquals(
            listOf(TourStep.Zoom, TourStep.Hold, TourStep.Clock, TourStep.PillSwipe, TourStep.Journal),
            tourSteps(menuPill = false, journalEnabled = true, contactsEnabled = false),
        )
    }

    @Test fun aRunStartsOnItsFirstCardAndOnlyItsOwnGestureCompletesIt() {
        val tour = CalinoTour()
        assertFalse(tour.running)
        tour.start(tourSteps(menuPill = true, journalEnabled = false, contactsEnabled = false))

        assertTrue(tour.running)
        assertEquals(TourStep.Zoom, tour.step)
        assertFalse(tour.performed)

        // Another card's gesture, and a gesture no card owns, change nothing.
        tour.report(TourEvent.ClockSlid)
        tour.report(TourEvent.PillSwipedUp)
        assertFalse(tour.performed)

        tour.report(TourEvent.CalendarZoomed)
        assertTrue(tour.performed)
    }

    @Test fun advancingClearsThePerformedFlagAndStopsAfterTheLastCard() {
        val tour = CalinoTour()
        tour.start(listOf(TourStep.Zoom, TourStep.Hold))
        tour.report(TourEvent.CalendarZoomed)
        assertTrue(tour.performed)

        assertTrue(tour.advance())
        assertEquals(TourStep.Hold, tour.step)
        assertFalse(tour.performed)

        // The last card answers false so the host knows to finish, and the
        // tour is then no longer running.
        assertFalse(tour.advance())
        assertNull(tour.step)
        assertFalse(tour.running)
    }

    @Test fun aStoppedTourIgnoresGesturesAndCanStartAgainFromTheTop() {
        val tour = CalinoTour()
        tour.start(listOf(TourStep.Zoom, TourStep.Clock))
        tour.stop()
        assertFalse(tour.running)

        tour.report(TourEvent.CalendarZoomed)
        assertFalse(tour.performed)

        tour.start(listOf(TourStep.Zoom, TourStep.Clock))
        assertEquals(TourStep.Zoom, tour.step)
    }

    @Test fun anEmptyCardListStillShowsOneCardRatherThanNone() {
        val tour = CalinoTour()
        tour.start(emptyList())
        assertEquals(1, tour.steps.size)
        assertEquals(tour.steps.first(), tour.step)
    }

    @Test fun detectedFeaturesJoinARunningTourButNeverReplaceItsCurrentCard() {
        val tour = CalinoTour()
        tour.start(tourSteps(menuPill = true, journalEnabled = false, contactsEnabled = false))
        // Journal is enabled while the person is still on the first card.
        tour.refreshSteps(tourSteps(menuPill = true, journalEnabled = true, contactsEnabled = false))
        assertTrue(tour.steps.contains(TourStep.Journal))
        assertEquals(TourStep.Zoom, tour.step)

        // A card that is no longer offered leaves the run alone instead of
        // jumping the person somewhere else.
        tour.refreshSteps(tourSteps(menuPill = false, journalEnabled = false, contactsEnabled = false))
        assertEquals(TourStep.Zoom, tour.step)
    }

    @Test fun replayRequestsCountUpSoTheHostCanAnswerThem() {
        val tour = CalinoTour()
        assertEquals(0, tour.replayRequests)
        tour.requestReplay()
        tour.requestReplay()
        assertEquals(2, tour.replayRequests)
    }
}
