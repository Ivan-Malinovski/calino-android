package calino.malinov.ski.wear

import calino.malinov.ski.wearcontract.WearAck
import calino.malinov.ski.wearcontract.WearAckResult
import calino.malinov.ski.wearcontract.WearCommand
import calino.malinov.ski.wearcontract.WearCommandOp
import java.time.Duration

data class CommandTaskState(
    val occurrenceId: String,
    val recordId: String,
    val done: Boolean,
    val dueEpochDay: Long?,
)

enum class CommandWriteResult { APPLIED, QUEUED, REJECTED }

sealed interface CommandDecision {
    data object Pending : CommandDecision
    data class Terminal(val acknowledgement: WearAck) : CommandDecision
}

fun interface CommandMutation {
    suspend fun apply(command: WearCommand): Pair<CommandWriteResult, String?>
}

interface CommandAckLedger {
    fun get(uuid: String): WearAck?
    fun put(acknowledgement: WearAck)
}

class WearCommandExecutor(
    private val ledger: CommandAckLedger,
) {
    suspend fun execute(command: WearCommand, block: suspend () -> CommandDecision): CommandDecision {
        ledger.get(command.uuid)?.let { return CommandDecision.Terminal(it) }
        return block().also { decision ->
            if (decision is CommandDecision.Terminal) ledger.put(decision.acknowledgement)
        }
    }
}

/** Android-free policy core. Durable storage and Data Layer I/O stay in the coordinator. */
class WearCommandProcessor(
    private val expiresAfterMillis: Long = Duration.ofHours(48).toMillis(),
) {
    suspend fun process(
        command: WearCommand,
        nowMillis: Long,
        hasAccount: Boolean,
        authoritative: Boolean,
        loading: Boolean,
        sourceEpoch: String,
        sourceSequence: Long,
        tasks: List<CommandTaskState>,
        mutation: CommandMutation,
    ): CommandDecision {
        fun terminal(result: WearAckResult, message: String? = null) = CommandDecision.Terminal(
            WearAck(command.uuid, result, message, nowMillis, sourceEpoch, sourceSequence),
        )

        if (nowMillis - command.createdAtMillis > expiresAfterMillis) return terminal(WearAckResult.EXPIRED, "Command expired")
        if (!hasAccount) return terminal(WearAckResult.NO_ACCOUNT, "Connect Calino on phone")
        if (!authoritative || loading) return CommandDecision.Pending
        if (command.sourceEpoch != sourceEpoch || command.sourceSequence > sourceSequence) {
            return terminal(WearAckResult.CONFLICT, "Watch data is no longer current")
        }
        if (command.op == WearCommandOp.UNSUPPORTED) return terminal(WearAckResult.UNSUPPORTED, "Unsupported command")
        if (command.op == WearCommandOp.RESCHEDULE_TASK && command.targetEpochDay == null) {
            return terminal(WearAckResult.REJECTED, "Missing target day")
        }

        val task = tasks.firstOrNull {
            it.recordId == command.recordId && it.occurrenceId == command.occurrenceId
        } ?: return terminal(WearAckResult.NOT_FOUND, "Task changed or was removed")

        if (task.done != command.observedDone || task.dueEpochDay != command.observedDueEpochDay) {
            return terminal(WearAckResult.CONFLICT, "Task changed on phone")
        }
        if (command.op == WearCommandOp.SET_TASK_DONE && task.done) return terminal(WearAckResult.NOOP)
        if (command.op == WearCommandOp.RESCHEDULE_TASK && task.dueEpochDay == command.targetEpochDay) {
            return terminal(WearAckResult.NOOP)
        }

        val (result, reason) = mutation.apply(command)
        return terminal(
            when (result) {
                CommandWriteResult.APPLIED -> WearAckResult.APPLIED
                CommandWriteResult.QUEUED -> WearAckResult.QUEUED
                CommandWriteResult.REJECTED -> WearAckResult.REJECTED
            },
            reason,
        )
    }
}
