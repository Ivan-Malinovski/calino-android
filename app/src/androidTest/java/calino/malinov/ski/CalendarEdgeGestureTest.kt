package calino.malinov.ski

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Exercise the real axis owner, including live-preview reversal and cancellation. */
class CalendarEdgeGestureTest : CalinoUiTest() {
    private fun pullMonth(distance: Float, duration: Long = 650) {
        compose.onNodeWithTag("calendar-zoom-surface").performTouchInput {
            val start = Offset(width * .95f, height * if (distance > 0f) .20f else .80f)
            swipe(start, start + Offset(0f, distance), duration)
        }
        compose.waitForIdle()
    }

    private fun revealAgenda() {
        compose.onNodeWithContentDescription(CalinoTestActions.zoomHandleLabel(0)).performTouchInput {
            swipe(center, center - Offset(0f, 390f), 650)
        }
        compose.waitUntil(5_000) { compose.exists(hasTestTag("agenda-month-list")) }
        compose.waitForIdle()
    }

    private fun selectCompactDay(day: java.time.LocalDate) {
        zoomTo(1)
        compose.selectDayIn(CalinoTestActions.MonthPager, day)
        compose.waitForIdle()
        zoomTo(0)
        compose.assertDaySelected(CalinoTestActions.WeekPager, day)
    }

    private fun assertAgendaStartsOn(description: String) {
        val headers = compose.onAllNodes(
            hasAnyAncestor(hasTestTag("agenda-month-list")) and hasContentDescription("Add on ", substring = true),
        ).fetchSemanticsNodes().filter { it.boundsInRoot.height > 0f }
        val first = checkNotNull(headers.minByOrNull { it.boundsInRoot.top })
        val labels = first.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
        org.junit.Assert.assertTrue("First visible agenda section: $labels", description in labels)
    }

    @Test fun disablingEdgeSwipesSurvivesRecreationAndPreservesCalendarZoom() {
        compose.openRoute("Settings")
        compose.onNodeWithContentDescription("Display settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Swipe to Year and Agenda toggle")
            .performScrollTo().assertIsOn().performClick()
        compose.waitForIdle()
        compose.openRoute("Month")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        // Both a slow pull and an outward fling stop at the week endpoint.
        for (duration in listOf(650L, 100L)) {
            compose.onNodeWithContentDescription(CalinoTestActions.zoomHandleLabel(0)).performTouchInput {
                swipe(center, center - Offset(0f, 390f), duration)
            }
            compose.waitForIdle()
            assertEquals(0, currentZoomLevel())
            compose.onNodeWithTag("agenda-month-list").assertDoesNotExist()
        }
        compose.onNodeWithText("↓ MONTH").assertIsDisplayed()
        compose.assertDaySelected(CalinoTestActions.WeekPager, CalinoTestActions.FixtureDate)

        zoomTo(2)
        for (duration in listOf(650L, 100L)) {
            pullMonth(400f, duration)
            assertEquals(2, currentZoomLevel())
            assertFalse(compose.hasDescribedNode("Previous year"))
        }
        compose.onNodeWithText("↑ COLLAPSE").assertIsDisplayed()
        // Ordinary inward zoom and its reverse continue to work.
        pullMonth(-500f)
        assertEquals(1, currentZoomLevel())
        pullMonth(500f)
        assertEquals(2, currentZoomLevel())
        compose.assertDaySelected(CalinoTestActions.MonthPager, CalinoTestActions.FixtureDate)

        compose.openRoute("Settings")
        compose.onNodeWithContentDescription("Display settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Swipe to Year and Agenda toggle")
            .performScrollTo().assertIsOff().performClick()
        compose.waitForIdle()
        compose.openRoute("Month")
        zoomTo(2)
        pullMonth(400f)
        awaitDescribed("Previous year")
        compose.openRoute("Month")
        zoomTo(0)
        revealAgenda()
        assertAgendaStartsOn("Add on May 18, 2026")
    }

    @Test fun settingsSearchFindsTheEnabledByDefaultEdgeSwipeSwitch() {
        compose.openRoute("Settings")
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNodeWithTag("Search settings").performTextInput("Swipe to Year")
        compose.onNodeWithContentDescription("Open Swipe to Year and Agenda in Display settings")
            .performClick()
        compose.onNodeWithContentDescription("Display settings").assertIsSelected()
        compose.onNodeWithContentDescription("Swipe to Year and Agenda toggle")
            .performScrollTo().assertIsOn()
    }

    @Test fun agendaRevealStartsOnTheSelectedMaySixth() {
        selectCompactDay(java.time.LocalDate.of(2026, 5, 6))
        revealAgenda()
        assertAgendaStartsOn("Add on May 6, 2026")
    }

    @Test fun agendaRevealSupersedesItsSavedTodayPositionEvenOnTheFirstOfMay() {
        compose.openRoute("Agenda")
        compose.waitForIdle()
        compose.openRoute("Month")
        selectCompactDay(java.time.LocalDate.of(2026, 5, 6))
        compose.onNodeWithTag(CalinoTestActions.WeekPager).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.selectDayIn(CalinoTestActions.WeekPager, java.time.LocalDate.of(2026, 5, 1))
        compose.waitForIdle()
        revealAgenda()
        assertAgendaStartsOn("Add on May 1, 2026")
    }

    @Test fun agendaRevealPositionsBothTheSavedMonthPagerAndItsDayList() {
        compose.openRoute("Agenda")
        compose.waitForIdle()
        compose.openRoute("Month")
        zoomTo(1)
        compose.onNodeWithTag(CalinoTestActions.MonthPager).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.selectDayIn(CalinoTestActions.MonthPager, java.time.LocalDate.of(2026, 6, 6))
        compose.waitForIdle()
        zoomTo(0)
        revealAgenda()
        assertAgendaStartsOn("Add on Jun 6, 2026")
    }

    @Test fun slowDownwardPullBeyondMonthOpensYearAndBackKeepsMonthDate() {
        zoomTo(2)
        pullMonth(400f)
        awaitDescribed("Previous year")
        compose.onNodeWithContentDescription("May 2026, ", substring = true).assertIsDisplayed()
        Espresso.pressBack()
        awaitDescribed(CalinoTestActions.zoomHandleLabel(2))
        compose.assertDaySelected(CalinoTestActions.MonthPager, CalinoTestActions.FixtureDate)
    }

    @Test fun oneContinuousPullCanPassThroughMonthAndContinueIntoYear() {
        zoomTo(1)
        pullMonth(1150f, 800)
        awaitDescribed("Previous year")
        Espresso.pressBack()
        awaitDescribed(CalinoTestActions.zoomHandleLabel(2))
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(1, currentZoomLevel())
        compose.assertDaySelected(CalinoTestActions.MonthPager, CalinoTestActions.FixtureDate)
    }

    @Test fun shortMonthPullReturnsWithoutChangingDateOrRoute() {
        zoomTo(2)
        pullMonth(90f, 800)
        assertEquals(2, currentZoomLevel())
        assertFalse(compose.hasDescribedNode("Previous year"))
        compose.assertDaySelected(CalinoTestActions.MonthPager, CalinoTestActions.FixtureDate)
    }

    @Test fun reversingMonthPullReturnsAlongTheSameGeometry() {
        zoomTo(2)
        compose.onNodeWithTag("calendar-zoom-surface").performTouchInput {
            val start = Offset(width * .95f, height * .2f)
            down(start)
            moveTo(start + Offset(0f, 280f), 400)
            moveTo(start + Offset(0f, 30f), 400)
            advanceEventTime(120)
            up()
        }
        compose.waitForIdle()
        assertEquals(2, currentZoomLevel())
        assertFalse(compose.hasDescribedNode("Previous year"))
    }

    @Test fun cancellingMonthPullNeverNavigates() {
        zoomTo(2)
        compose.onNodeWithTag("calendar-zoom-surface").performTouchInput {
            val start = Offset(width * .95f, height * .2f)
            down(start)
            moveTo(start + Offset(0f, 350f), 400)
            cancel()
        }
        compose.waitForIdle()
        assertEquals(2, currentZoomLevel())
        assertFalse(compose.hasDescribedNode("Previous year"))
    }

    @Test fun upwardPullBeyondCompactWeekOpensAgenda() {
        compose.onNodeWithContentDescription(CalinoTestActions.zoomHandleLabel(0)).performTouchInput {
            swipe(center, center - Offset(0f, 390f), 650)
        }
        compose.waitUntil(5_000) { compose.exists(androidx.compose.ui.test.hasTestTag("agenda-month-list")) }
        compose.onNodeWithTag("agenda-month-list").assertIsDisplayed()
        assertFalse(compose.hasDescribedNode(CalinoTestActions.zoomHandleLabel(0)))
    }

    @Test fun shortUpwardPullKeepsTheWeekAndItsSelectedDate() {
        compose.onNodeWithContentDescription(CalinoTestActions.zoomHandleLabel(0)).performTouchInput {
            swipe(center, center - Offset(0f, 80f), 800)
        }
        compose.waitForIdle()
        assertEquals(0, currentZoomLevel())
        compose.assertDaySelected(CalinoTestActions.WeekPager, CalinoTestActions.FixtureDate)
    }

    @Test fun cancellingAgendaRevealKeepsTheWeek() {
        compose.onNodeWithContentDescription(CalinoTestActions.zoomHandleLabel(0)).performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 350f), 400)
            cancel()
        }
        compose.waitForIdle()
        assertEquals(0, currentZoomLevel())
        compose.onNodeWithContentDescription("Agenda for Monday, May 18").assertIsDisplayed()
    }

    @Test fun fastOutwardFlingOpensYear() {
        zoomTo(2)
        pullMonth(220f, 100)
        awaitDescribed("Previous year")
    }

    @Test fun inwardPullStillCollapsesMonthInsteadOfOpeningYear() {
        zoomTo(2)
        pullMonth(-500f)
        assertEquals(1, currentZoomLevel())
        assertFalse(compose.hasDescribedNode("Previous year"))
    }
}
