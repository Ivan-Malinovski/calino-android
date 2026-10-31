package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.blankEditorDraft
import calino.malinov.ski.data.parser.PocQuickAddKind
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.components.SwipeEndDismiss
import calino.malinov.ski.ui.components.SwipeEndDismissTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import calino.malinov.ski.ui.components.SwipeDownDismissTag
import calino.malinov.ski.ui.surfaces.ContactsSurface
import calino.malinov.ski.ui.surfaces.JournalSurface
import calino.malinov.ski.ui.surfaces.EditorSurface
import calino.malinov.ski.ui.surfaces.EventDetailSurface
import calino.malinov.ski.ui.surfaces.TaskDetailSurface
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/** Draft decisions exercise real surface exits, not just the pill's callbacks. */
class UnsavedChangesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 5, 18)

    @Test fun cancelledEndPanelDragDoesNotCommit() {
        val closed = AtomicInteger()
        compose.setContent {
            CalinoTheme {
                SwipeEndDismiss(visible = true, onDismiss = { closed.incrementAndGet() },
                    modifier = Modifier.fillMaxSize()) { Box(it) }
            }
        }
        compose.onNodeWithTag(SwipeEndDismissTag).performTouchInput {
            down(Offset(20f, 100f)); moveBy(Offset(400f, 0f)); cancel()
        }
        compose.waitForIdle()
        assertEquals(0, closed.get())
        compose.onNodeWithTag(SwipeEndDismissTag).performTouchInput {
            swipe(Offset(20f, 100f), Offset(500f, 100f), 200)
        }
        compose.waitForIdle()
        assertEquals(1, closed.get())
    }

    @Test fun unchangedEditorClosesWithoutAQuestion() {
        val closed = AtomicInteger()
        compose.setContent {
            CalinoTheme {
                EditorSurface(blankEditorDraft(PocQuickAddKind.Event, date), date,
                    onDismiss = { closed.incrementAndGet() })
            }
        }
        compose.onNodeWithContentDescription("Cancel editor").performClick()
        compose.waitUntil(5_000) { closed.get() == 1 }
        compose.onNodeWithText("Save your changes?").assertDoesNotExist()
    }

    @Test fun editorKeepsDraftOnBackAndOnlyClosesAfterAnAcceptedSave() {
        val closed = AtomicInteger()
        val writes = AtomicInteger()
        val firstWrite = CompletableDeferred<Boolean>()
        compose.setContent {
            CalinoTheme {
                EditorSurface(blankEditorDraft(PocQuickAddKind.Event, date), date,
                    onDismiss = { closed.incrementAndGet() },
                    onSave = { if (writes.incrementAndGet() == 1) firstWrite.await() else true },
                    onSaved = { closed.incrementAndGet() })
            }
        }
        compose.onNodeWithContentDescription("Title, event").performTextInput("A deliberate edit")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Cancel editor").performClick()
        compose.onNodeWithText("Save your changes?").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithContentDescription("Title, event").assertIsFocused().assertTextContains("A deliberate edit")
        compose.onNodeWithContentDescription("Cancel editor").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(5_000) { writes.get() == 1 }
        assertEquals(0, closed.get())
        firstWrite.complete(false)
        compose.waitUntil(5_000) { compose.hasDescribedNode("Title, event") }
        compose.onNodeWithContentDescription("Title, event").assertTextContains("A deliberate edit")
        compose.onNodeWithContentDescription("Cancel editor").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(5_000) { closed.get() == 1 }
        assertEquals(2, writes.get())
    }

    @Test fun rejectedContactCreateKeepsItsDraftForRetry() {
        val writes = AtomicInteger()
        compose.setContent {
            CalinoTheme {
                ContactsSurface(contacts = emptyList(), addressBooks = emptyList(), startEntryRequest = 1,
                    onCreate = { input ->
                        assertEquals("A new contact", input.displayName)
                        writes.incrementAndGet() > 1
                    })
            }
        }
        compose.onNodeWithContentDescription("Display name").performTextInput("A new contact")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Cancel contact editing").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(5_000) { writes.get() == 1 && compose.hasDescribedNode("Display name") }
        compose.onNodeWithContentDescription("Display name").assertTextContains("A new contact")
        compose.onNodeWithContentDescription("Cancel contact editing").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(5_000) { !compose.hasDescribedNode("Cancel contact editing") }
        assertEquals(2, writes.get())
    }

    @Test fun rejectedJournalCreateKeepsItsDraftForRetry() {
        val writes = AtomicInteger()
        compose.setContent {
            CalinoTheme {
                JournalSurface(entries = emptyList(), newEntryDate = date, startEntryRequest = 1,
                    onCreate = { entry ->
                        assertEquals("A new thought", entry.title)
                        writes.incrementAndGet() > 1
                    })
            }
        }
        compose.onNodeWithContentDescription("Journal title").performTextInput("A new thought")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Cancel journal editing").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(5_000) { writes.get() == 1 && compose.hasDescribedNode("Journal title") }
        compose.onNodeWithContentDescription("Journal title").assertTextContains("A new thought")
        compose.onNodeWithContentDescription("Cancel journal editing").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(5_000) { !compose.hasDescribedNode("Cancel journal editing") }
        assertEquals(2, writes.get())
    }

    @Test fun eventDismissalDoesNotWriteUntilSaveIsChosen() {
        val closed = AtomicInteger()
        val writes = AtomicInteger()
        compose.setContent {
            CalinoTheme {
                EventDetailSurface(
                    event = CalEvent(id = "decision-event", title = "Original", color = 0L, start = null, durationMinutes = null, calendarId = "work", date = date, allDay = true),
                    onBack = { closed.incrementAndGet() },
                    onInlineSave = { _, input, _ ->
                        assertEquals("Edited", input.title)
                        writes.incrementAndGet()
                        true
                    },
                )
            }
        }
        compose.onNodeWithContentDescription("Event title").performTextReplacement("Edited")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Close event preview").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithContentDescription("Event title").assertTextContains("Edited")
        assertEquals(0, writes.get())
        assertEquals(0, closed.get())
        compose.onNodeWithContentDescription("Close event preview").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(5_000) { closed.get() == 1 }
        assertEquals(1, writes.get())
    }

    @Test fun recurringEventSaveOnDismissalStillRequiresScopeConfirmation() {
        val writes = AtomicInteger()
        val closed = AtomicInteger()
        compose.setContent {
            CalinoTheme {
                EventDetailSurface(
                    event = CalEvent(id = "repeat-decision", title = "Weekly", color = 0L,
                        start = null, durationMinutes = null, calendarId = "work", date = date,
                        allDay = true, recurrence = "FREQ=WEEKLY"),
                    onBack = { closed.incrementAndGet() },
                    onInlineSave = { _, _, scope ->
                        assertEquals(RecurrenceEditScope.This, scope)
                        writes.incrementAndGet(); true
                    },
                )
            }
        }
        compose.onNodeWithContentDescription("Event title").performTextReplacement("Edited weekly")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Close event preview").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithText("Apply changes to").assertIsDisplayed()
        assertEquals(0, writes.get())
        // Cancelling this second question must not turn a later Save into an
        // implicit whole-series write.
        compose.onNodeWithContentDescription("Close event preview").performClick()
        compose.onNodeWithText("Apply changes to").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close event preview").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithText("Apply changes to").assertIsDisplayed()
        compose.onNodeWithText("This event").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.waitUntil(5_000) { closed.get() == 1 }
        assertEquals(1, writes.get())
    }

    @Test fun cancelledSlowAndFastTaskDragsPreserveTheDraftUntilDiscard() {
        val closed = AtomicInteger()
        val writes = AtomicInteger()
        compose.setContent {
            CalinoTheme {
                TaskDetailSurface(
                    task = CalTask(id = "decision-task", title = "Original", color = 0L, due = date),
                    onBack = { closed.incrementAndGet() },
                    onSave = { _, _ -> writes.incrementAndGet(); true },
                )
            }
        }
        compose.onNodeWithContentDescription("Task title").performTextReplacement("Edited")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag(SwipeDownDismissTag).performTouchInput {
            down(Offset(centerX, 20f)); moveBy(Offset(0f, 400f)); cancel()
        }
        compose.onNodeWithText("Save your changes?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Task title").assertTextContains("Edited")
        for (duration in listOf(900L, 150L)) {
            compose.onNodeWithTag(SwipeDownDismissTag).performTouchInput {
                swipe(Offset(centerX, 20f), Offset(centerX, 450f), duration)
            }
            compose.onNodeWithText("Save your changes?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithContentDescription("Task title").assertTextContains("Edited")
        }
        compose.onNodeWithContentDescription("Cancel task editing").performClick()
        compose.onNodeWithContentDescription("Discard changes and close").performClick()
        compose.waitUntil(5_000) { closed.get() == 1 }
        assertEquals(0, writes.get())
    }
}
