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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsEnabled
import calino.malinov.ski.ui.components.UnsavedChangesPillContent
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import calino.malinov.ski.design.CalinoThemes
import calino.malinov.ski.ui.components.ModalPillActionTone
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.components.BottomDetailCard
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

    private fun pillHeight(): Float {
        val bounds = compose.onNodeWithTag("motion-pill").getBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    @Test fun saveKeepsItsPaintWhileItsEntranceBecomesClickable() {
        val dirty = mutableStateOf(false)
        compose.setContent {
            CalinoTheme {
                Box(Modifier.fillMaxSize().background(CalinoColors.Canvas), contentAlignment = Alignment.Center) {
                    ModalActionPill(
                        primaryLabel = "Save", onPrimary = {}, primaryVisible = dirty.value,
                        primaryTone = ModalPillActionTone.Save,
                        cancelLabel = "Close", onCancel = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dirty.value = true }
        // The lane's fade is finished before the size spring and click gate.
        compose.mainClock.advanceTimeBy(300)
        val arriving = saveFillSample()
        compose.mainClock.advanceTimeBy(700)
        val settled = saveFillSample()
        assertColorNear(CalinoThemes.PaperLight.SaveFill, arriving)
        assertColorNear(settled, arriving)
    }

    private fun saveFillSample(): Color {
        val image = compose.onNodeWithContentDescription("Save").captureToImage()
        return image.toPixelMap()[image.width / 2, image.height / 4]
    }

    private fun assertColorNear(expected: Color, actual: Color) {
        assertEquals("red", expected.red, actual.red, .025f)
        assertEquals("green", expected.green, actual.green, .025f)
        assertEquals("blue", expected.blue, actual.blue, .025f)
    }

    @Test fun unsavedConfirmationKeepsItsPaintWhenInputBecomesReady() {
        val interactive = mutableStateOf(false)
        compose.setContent {
            CalinoTheme {
                Box(Modifier.fillMaxSize().background(CalinoColors.FloatFill), contentAlignment = Alignment.Center) {
                    UnsavedChangesPillContent(onSave = {}, onDiscard = {}, onKeepEditing = {},
                        canSave = true, interactive = interactive.value)
                }
            }
        }
        // The content is intentionally hidden from accessibility until ready;
        // inspect its unmerged paint without making it prematurely tappable.
        val save = compose.onNodeWithText("Save changes", useUnmergedTree = true)
        val before = save.captureToImage()
        compose.runOnIdle { interactive.value = true }
        compose.waitForIdle()
        val after = save.captureToImage()
        // Text itself must not flash from disabled grey to sage at the gate.
        fun brightest(image: androidx.compose.ui.graphics.ImageBitmap): Float {
            val pixels = image.toPixelMap()
            var value = 0f
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val color = pixels[x, y]
                value = maxOf(value, (color.red + color.green + color.blue) / 3f)
            }
            return value
        }
        assertEquals(brightest(before), brightest(after), .025f)
        compose.onNodeWithText("Save changes").assertIsEnabled()
    }

    @Test fun deleteConfirmationDoesNotBlinkAfterItsOutgoingFaceHasFaded() {
        val confirming = mutableStateOf(false)
        val callbackVersion = mutableStateOf(0)
        val deletedVersion = java.util.concurrent.atomic.AtomicInteger(-1)
        compose.setContent {
            val version = callbackVersion.value
            CalinoTheme {
                Box(Modifier.fillMaxSize().background(CalinoColors.Canvas), contentAlignment = Alignment.Center) {
                    ModalActionPill(
                        primaryLabel = "Save", onPrimary = {},
                        cancelLabel = "Close", onCancel = {},
                        deleteLabel = "Delete", onDelete = { deletedVersion.set(version) }, deleteHoldToConfirm = true,
                        deleteConfirmationActive = confirming.value,
                        onDeleteConfirmationChange = { confirming.value = it },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.mainClock.advanceTimeBy(160)
        var previous = 0f
        repeat(24) {
            compose.runOnIdle { callbackVersion.value = it + 1 }
            compose.mainClock.advanceTimeByFrame()
            val image = compose.onNodeWithText("Are you sure?").captureToImage()
            val pixels = image.toPixelMap()
            var brightest = 0f
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val color = pixels[x, y]
                brightest = maxOf(brightest, (color.red + color.green + color.blue) / 3f)
            }
            assertTrue("Confirmation dimmed again at frame $it: $previous -> $brightest", brightest + .025f >= previous)
            previous = brightest
        }
        assertTrue("Confirmation never reached full opacity", previous > .9f)
        compose.onNodeWithContentDescription("Confirm Delete").performClick()
        compose.runOnIdle { assertEquals(24, deletedVersion.get()) }
    }

    @Test fun dismissQuestionLeavesTheCardInPlaceThroughoutItsMorph() {
        val asking = mutableStateOf(false)
        compose.setContent {
            CalinoTheme {
                BottomDetailCard(
                    visible = true, onDismiss = { asking.value = !asking.value },
                    dismissConfirmationActive = asking.value,
                    pill = {
                        ModalActionPill(
                            primaryLabel = "Save", onPrimary = {},
                            cancelLabel = "Close", onCancel = { asking.value = true },
                            cancelDescription = "Dismiss edited card",
                            dismissConfirmationActive = asking.value,
                            onKeepEditing = { asking.value = false },
                        )
                    },
                ) { Box(it.testTag("decision-card")) }
            }
        }
        val card = compose.onNodeWithTag("decision-card", useUnmergedTree = true)
        val initial = card.getBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Dismiss edited card").performClick()
        repeat(45) {
            compose.mainClock.advanceTimeByFrame()
            val bounds = card.getBoundsInRoot()
            assertEquals(initial.top.value, bounds.top.value, .5f)
            assertEquals(initial.bottom.value, bounds.bottom.value, .5f)
            assertEquals(initial.left.value, bounds.left.value, .5f)
            assertEquals(initial.right.value, bounds.right.value, .5f)
        }
        compose.onNodeWithText("Keep editing").performClick()
        compose.mainClock.advanceTimeBy(800)
        assertEquals(initial, card.getBoundsInRoot())
    }

    @Test fun dismissQuestionGrowsFromTheExistingPillAndReturnsToItsActions() {
        val asking = mutableStateOf(false)
        compose.setContent {
            CalinoTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    ModalActionPill(
                        primaryLabel = "Save", onPrimary = {},
                        cancelLabel = "Close", onCancel = { asking.value = true },
                        cancelDescription = "Dismiss edited card",
                        dismissConfirmationActive = asking.value,
                        onKeepEditing = { asking.value = false },
                        modifier = Modifier.testTag("motion-pill"),
                    )
                }
            }
        }
        val restingHeight = pillHeight()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Dismiss edited card").performClick()
        val heights = mutableListOf<Float>()
        repeat(45) {
            compose.mainClock.advanceTimeByFrame()
            heights += pillHeight()
        }
        val questionHeight = heights.last()
        assertTrue("The question never grew out of the pill", questionHeight > restingHeight * 2)
        assertTrue("The pill jumped straight into its question", heights.any { it > restingHeight + 2 && it < questionHeight - 2 })
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithContentDescription("Dismiss edited card").assertIsDisplayed()
        assertEquals(restingHeight, pillHeight(), .5f)
    }

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
