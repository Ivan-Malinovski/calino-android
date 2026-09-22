package calino.malinov.ski.wear

import calino.malinov.ski.wearcontract.WearAck
import calino.malinov.ski.wearcontract.WearAckResult
import calino.malinov.ski.wearcontract.WearCommand
import calino.malinov.ski.wearcontract.WearCommandOp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WearCommandProcessorTest {
    private val now = 1_000_000_000L
    private val task = CommandTaskState("task@10", "task", false, 10)

    private fun command(
        op: WearCommandOp = WearCommandOp.SET_TASK_DONE,
        occurrence: String = task.occurrenceId,
        due: Long? = task.dueEpochDay,
        target: Long? = null,
        createdAt: Long = now,
    ) = WearCommand(
        uuid = "uuid",
        occurrenceId = occurrence,
        recordId = task.recordId,
        op = op,
        observedDone = false,
        observedDueEpochDay = due,
        sourceEpoch = "epoch",
        sourceSequence = 4,
        createdAtMillis = createdAt,
        targetEpochDay = target,
    )

    private suspend fun decide(
        command: WearCommand = command(),
        tasks: List<CommandTaskState> = listOf(task),
        authoritative: Boolean = true,
        loading: Boolean = false,
        write: CommandWriteResult = CommandWriteResult.APPLIED,
    ) = WearCommandProcessor().process(
        command, now, true, authoritative, loading, "epoch", 5, tasks,
        CommandMutation { write to if (write == CommandWriteResult.REJECTED) "refused" else null },
    )

    @Test fun expired() = runTest {
        assertResult(WearAckResult.EXPIRED, decide(command(createdAt = now - 48 * 60 * 60 * 1000L - 1)))
    }

    @Test fun coldAndLoadingRemainPending() = runTest {
        assertSame(CommandDecision.Pending, decide(authoritative = false))
        assertSame(CommandDecision.Pending, decide(loading = true))
    }

    @Test fun exactOccurrenceMismatchIsNotFound() = runTest {
        assertResult(WearAckResult.NOT_FOUND, decide(command(occurrence = "task@11")))
    }

    @Test fun observedStateConflict() = runTest {
        assertResult(WearAckResult.CONFLICT, decide(command(due = 9)))
    }

    @Test fun mapsAppliedQueuedAndRejected() = runTest {
        assertResult(WearAckResult.APPLIED, decide(write = CommandWriteResult.APPLIED))
        assertResult(WearAckResult.QUEUED, decide(write = CommandWriteResult.QUEUED))
        assertResult(WearAckResult.REJECTED, decide(write = CommandWriteResult.REJECTED))
    }

    @Test fun bothOperationsReachMutation() = runTest {
        assertResult(WearAckResult.APPLIED, decide(command()))
        assertResult(
            WearAckResult.APPLIED,
            decide(command(op = WearCommandOp.RESCHEDULE_TASK, target = 11)),
        )
    }

    @Test fun rescheduleRequiresTargetAndUnsupportedIsExplicit() = runTest {
        assertResult(
            WearAckResult.REJECTED,
            decide(command(op = WearCommandOp.RESCHEDULE_TASK, target = null)),
        )
        assertResult(WearAckResult.UNSUPPORTED, decide(command(op = WearCommandOp.UNSUPPORTED)))
    }

    @Test fun duplicateUuidReturnsStoredAckWithoutExecuting() = runTest {
        val stored = WearAck("uuid", WearAckResult.QUEUED, null, now, "epoch", 6)
        val ledger = FakeLedger(stored)
        var called = false
        val result = WearCommandExecutor(ledger).execute(command()) {
            called = true
            CommandDecision.Pending
        }
        assertEquals(false, called)
        assertEquals(stored, (result as CommandDecision.Terminal).acknowledgement)
    }

    private fun assertResult(expected: WearAckResult, decision: CommandDecision) {
        assertEquals(expected, (decision as CommandDecision.Terminal).acknowledgement.result)
    }

    private class FakeLedger(private var value: WearAck?) : CommandAckLedger {
        override fun get(uuid: String) = value
        override fun put(acknowledgement: WearAck) { value = acknowledgement }
    }
}
