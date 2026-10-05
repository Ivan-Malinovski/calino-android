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

    @Test fun aSaveInterruptsUndoAndAnEarlierResultCannotClearTheNewOne() = runTest {
        val lane = CalinoPillLane { testScheduler.currentTime }
        lane.showUndo("Task completed", this) { }
        lane.saveStarted()
        assertNull(lane.undo)
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
    }
}
