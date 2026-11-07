package calino.malinov.ski.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PillWriteFeedbackTest {
    @Test fun fastWritesHaveOneReadableSavingBeatThenReturnToIdle() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        lane.saveStarted()
        lane.saveFinished(this, success = true)
        advanceTimeBy(MinSavingMillis - 1)
        assertEquals(PillSaveState.Saving, lane.saveState)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PillSaveState.Saved, lane.saveState)
        advanceTimeBy(SavedHoldMillis)
        runCurrent()
        assertEquals(PillSaveState.Idle, lane.saveState)
    }

    @Test fun overlappingWritesWaitForBothAndNeverReportSuccessAfterOneFails() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        lane.saveStarted()
        lane.saveStarted(PillWriteKind.Remove)
        lane.saveFinished(this, success = false)
        advanceTimeBy(MinSavingMillis)
        assertEquals(PillSaveState.Saving, lane.saveState)
        lane.saveFinished(this, success = true)
        runCurrent()
        assertEquals(PillSaveState.Idle, lane.saveState)
    }

    @Test fun aNewWriteDuringTheFailureTailCanReportItsOwnSuccess() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        lane.saveStarted()
        lane.saveFinished(this, success = false)
        advanceTimeBy(100)
        lane.saveStarted(PillWriteKind.Remove)
        lane.saveFinished(this, success = true)
        advanceTimeBy(MinSavingMillis)
        runCurrent()
        assertEquals(PillSaveState.Saved, lane.saveState)
        assertEquals(PillWriteKind.Remove, lane.writeKind)
        advanceTimeBy(SavedHoldMillis)
        runCurrent()
        assertEquals(PillSaveState.Idle, lane.saveState)
    }

    @Test fun aSaveDoesNotCancelAStandingUndoAndAnEarlierResultCannotClearTheNewOne() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        lane.showUndo("Task completed", this) { }
        lane.saveStarted()
        assertEquals("Task completed", lane.undo?.message)
        lane.saveFinished(this, success = true)
        advanceTimeBy(MinSavingMillis)
        runCurrent()
        advanceTimeBy(SavedHoldMillis - 100)
        lane.saveStarted(PillWriteKind.Remove)
        lane.saveFinished(this, success = true)
        advanceTimeBy(MinSavingMillis)
        runCurrent()
        assertEquals(PillSaveState.Saved, lane.saveState)
        assertEquals(PillWriteKind.Remove, lane.writeKind)
        advanceTimeBy(SavedHoldMillis)
        runCurrent()
        assertEquals(PillSaveState.Idle, lane.saveState)
        assertEquals("Task completed", lane.undo?.message)
    }

    @Test fun anOfferWithdrawsItselfAfterTheHold() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        lane.showUndo("Deleted Milk", this) { }
        advanceTimeBy(UndoHoldMillis - 1)
        assertEquals("Deleted Milk", lane.undo?.message)
        advanceTimeBy(1)
        runCurrent()
        assertNull(lane.undo)
    }

    @Test fun offersStackAndEachUndoBringsTheNextOneUp() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        val taken = mutableListOf<String>()
        lane.showUndo("one", this) { taken += "one" }
        lane.showUndo("two", this) { taken += "two" }
        assertEquals("two", lane.undo?.message)
        lane.undo?.onUndo?.invoke()
        assertEquals("one", lane.undo?.message)
        lane.undo?.onUndo?.invoke()
        assertNull(lane.undo)
        assertEquals(listOf("two", "one"), taken)
    }

    @Test fun anOlderOfferKeepsItsOwnClock() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        lane.showUndo("old", this) { }
        advanceTimeBy(6_000)
        lane.showUndo("new", this) { }
        advanceTimeBy(UndoHoldMillis - 6_000)
        runCurrent()
        assertEquals("new", lane.undo?.message)
        advanceTimeBy(6_000)
        runCurrent()
        assertNull(lane.undo)
    }

    @Test fun theStackKeepsOnlyTheNewestFive() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        repeat(UndoStackLimit + 2) { lane.showUndo("n$it", this) { } }
        val seen = mutableListOf<String>()
        while (lane.undo != null) {
            seen += lane.undo!!.message
            lane.undo!!.onUndo()
        }
        assertEquals((UndoStackLimit + 1 downTo 2).map { "n$it" }, seen)
    }

    @Test fun theWindowDrainsFromFullToEmptyOnTheLanesClock() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        lane.showUndo("Deleted Milk", this) { }
        val remaining = lane.undo!!.remaining
        assertEquals(1f, remaining(), 0.001f)
        advanceTimeBy(UndoHoldMillis / 4)
        assertEquals(.75f, remaining(), 0.001f)
        advanceTimeBy(UndoHoldMillis / 4)
        assertEquals(.5f, remaining(), 0.001f)
        advanceTimeBy(UndoHoldMillis / 2)
        assertEquals(0f, remaining(), 0.001f)
    }

    @Test fun anOfferRaisedDuringASaveStartsItsWindowWhenTheLaneIsAtRest() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        lane.saveStarted(PillWriteKind.Remove)
        lane.showUndo("Deleted Milk", this) { }
        lane.saveFinished(this, success = true)
        val remaining = lane.undo!!.remaining
        advanceTimeBy(MinSavingMillis + SavedHoldMillis - 1)
        runCurrent()
        assertEquals(1f, remaining(), 0.001f)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PillSaveState.Idle, lane.saveState)
        advanceTimeBy(UndoHoldMillis - 1)
        assertEquals("Deleted Milk", lane.undo?.message)
        advanceTimeBy(1)
        runCurrent()
        assertNull(lane.undo)
    }

    @Test fun dismissingLetsAnOfferGoWithoutTakingItAndBringsTheNextUp() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        val taken = mutableListOf<String>()
        lane.showUndo("one", this) { taken += "one" }
        lane.showUndo("two", this) { taken += "two" }
        lane.undo!!.onDismiss()
        assertEquals("one", lane.undo?.message)
        lane.undo!!.onDismiss()
        assertNull(lane.undo)
        assertEquals(emptyList<String>(), taken)
    }
}
