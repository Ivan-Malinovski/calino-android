package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.components.AddPill
import calino.malinov.ski.ui.components.CalinoPillFeedback
import calino.malinov.ski.ui.components.CalinoPillLane
import calino.malinov.ski.ui.components.LocalCalinoPillLane
import calino.malinov.ski.ui.components.ModalActionPill
import calino.malinov.ski.ui.components.PillSaveState
import calino.malinov.ski.ui.components.PillSaveTrace
import calino.malinov.ski.ui.components.rememberPillSaveTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Mid-transition checks: settled tap tests cannot detect width jumps or restarted outlines. */
class PillMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun replacementModalContractsFromItsActionsAndStillReturnsToNavigation() {
        val editor = mutableStateOf(false)
        val expanded = mutableStateOf(true)
        val lane = CalinoPillLane().apply {
            addPillLabel = "Month · Week · Agenda · Tasks · Add"
            claim()
        }
        compose.setContent {
            CalinoTheme {
                CompositionLocalProvider(LocalCalinoPillLane provides lane) {
                    CalinoPillFeedback {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (editor.value) {
                                ModalActionPill(
                                    primaryLabel = "Save", onPrimary = {}, primaryVisible = false,
                                    cancelLabel = "Cancel", onCancel = {}, cancelDescription = "Cancel editor",
                                    morphFromAddPill = true, inPillLane = true, expanded = expanded.value,
                                    modifier = Modifier.testTag("motion-pill"),
                                )
                            } else {
                                ModalActionPill(
                                    primaryLabel = "Save", onPrimary = {}, primaryVisible = false,
                                    cancelLabel = "Cancel", onCancel = {},
                                    deleteLabel = "Delete", onDelete = {},
                                    secondaryLabel = "Open", onSecondary = {
                                        lane.handoffModalActions()
                                        editor.value = true
                                    }, secondaryDescription = "Open event",
                                    morphFromAddPill = true, inPillLane = true,
                                    modifier = Modifier.testTag("motion-pill"),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val previewWidth = width()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Open event").performClick()
        val widths = mutableListOf<Float>()
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            widths += width()
        }
        assertTrue("Open briefly expanded through the navigation face", widths.all { it <= previewWidth + .5f })
        val editorWidth = widths.last()
        assertTrue("The editor should have only its down-arrow lane", editorWidth < previewWidth)
        assertTrue("The action face snapped shut instead of contracting", widths.any { it > editorWidth + 1f && it < previewWidth - 1f })
        compose.onNodeWithContentDescription("Cancel editor").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open event").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete").assertDoesNotExist()

        compose.runOnIdle { expanded.value = false }
        compose.mainClock.advanceTimeBy(250)
        assertTrue("A direct handoff lost its normal navigation return", width() > previewWidth)
    }

    @Test fun dirtySaveAndDeleteConfirmationAnimateTheirPaintedWidth() {
        val dirty = mutableStateOf(false)
        val confirming = mutableStateOf(false)
        val lane = CalinoPillLane()
        compose.setContent {
            CalinoTheme {
                CompositionLocalProvider(LocalCalinoPillLane provides lane) {
                    CalinoPillFeedback {
                        Box(Modifier.fillMaxSize().background(CalinoColors.Canvas), contentAlignment = Alignment.Center) {
                            ModalActionPill(
                                primaryLabel = "Save", onPrimary = {}, primaryVisible = dirty.value,
                                cancelLabel = "Cancel", onCancel = {},
                                deleteLabel = "Delete", onDelete = {}, deleteHoldToConfirm = true,
                                deleteConfirmationActive = confirming.value,
                                onDeleteConfirmationChange = { confirming.value = it },
                                modifier = Modifier.testTag("motion-pill"),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val cleanWidth = width()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dirty.value = true }
        compose.mainClock.advanceTimeBy(80)
        val growingWidth = width()
        compose.mainClock.advanceTimeBy(700)
        val dirtyWidth = width()
        assertTrue("Save jumped straight to its final width", growingWidth > cleanWidth && growingWidth < dirtyWidth)
        compose.onNodeWithContentDescription("Save").assertIsDisplayed()

        compose.onNodeWithContentDescription("Delete").performClick()
        compose.mainClock.advanceTimeBy(80)
        val askingWidth = width()
        compose.mainClock.advanceTimeBy(700)
        val confirmationWidth = width()
        assertTrue("Delete confirmation jumped to its final width", askingWidth in minOf(dirtyWidth, confirmationWidth)..maxOf(dirtyWidth, confirmationWidth))
        assertTrue("Delete confirmation did not animate", askingWidth != confirmationWidth)
        compose.onNodeWithContentDescription("Confirm Delete").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithContentDescription("Save").assertIsDisplayed()
        assertEquals(dirtyWidth, width(), .5f)
    }

    @Test fun theWriteClockSurvivesAChangeOfPillOwner() {
        val modal = mutableStateOf(false)
        val lane = CalinoPillLane()
        var renderedTrace: PillSaveTrace? = null
        compose.setContent {
            CalinoTheme {
                CompositionLocalProvider(LocalCalinoPillLane provides lane) {
                    CalinoPillFeedback {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            // These are deliberately separate compositions, as in the route host.
                            if (modal.value) {
                                val trace = rememberPillSaveTrace()
                                SideEffect { renderedTrace = trace }
                                ModalActionPill("Save", {}, inPillLane = true, modifier = Modifier.testTag("motion-pill"))
                            } else {
                                val trace = rememberPillSaveTrace()
                                SideEffect { renderedTrace = trace }
                                AddPill("Add event", modifier = Modifier.testTag("motion-pill"), onClick = {})
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { lane.saveStarted() }
        compose.mainClock.advanceTimeBy(300)
        val before = renderedTrace!!
        val phase = before.phase.value
        assertTrue(phase > 0f)
        compose.runOnIdle { modal.value = true }
        compose.mainClock.advanceTimeByFrame()
        assertSame("Modal restarted the root's outline", before, renderedTrace)
        assertTrue(renderedTrace!!.phase.value >= phase)
        compose.runOnIdle { modal.value = false }
        compose.mainClock.advanceTimeByFrame()
        assertSame("Root restarted the modal's outline", before, renderedTrace)
        // A failed write fades its existing segment without drawing a success ring.
        compose.runOnIdle { lane.saveFinished(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main), false) }
        compose.waitUntil(2_000) { lane.saveState == PillSaveState.Idle }
        compose.mainClock.advanceTimeBy(200)
        assertEquals(0f, renderedTrace!!.alpha.value, .001f)
        assertEquals(0f, renderedTrace!!.completion.value, .001f)
    }

    @Test fun aConfirmationActivatedAfterMountBlocksTheLabelSwipe() {
        val confirming = mutableStateOf(false)
        var navigations = 0
        compose.setContent {
            CalinoTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AddPill(
                        "Add event", confirmationActive = confirming.value,
                        canSwipe = { true }, onSwipe = { navigations++ },
                        onClick = {}, modifier = Modifier.testTag("motion-pill"),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { confirming.value = true }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Confirm delete event").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Confirm delete event").assertIsDisplayed()
        assertEquals("The drag handler used the state from before confirmation", 0, navigations)
    }

    private fun width(): Float {
        val bounds = compose.onNodeWithTag("motion-pill").getBoundsInRoot()
        return (bounds.right - bounds.left).value
    }
}
