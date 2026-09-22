package calino.malinov.ski.wear

import android.content.Context
import android.util.Base64
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.data.repository.WriteResult
import calino.malinov.ski.wearcontract.ACK_PATH_PREFIX
import calino.malinov.ski.wearcontract.COMMAND_PATH_PREFIX
import calino.malinov.ski.wearcontract.WearAck
import calino.malinov.ski.wearcontract.WearCodec
import calino.malinov.ski.wearcontract.WearCommand
import calino.malinov.ski.wearcontract.WearCommandOp
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.time.LocalDate
import kotlinx.coroutines.launch

class PhoneWearListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.asSequence()
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .filter { it.dataItem.uri.path?.startsWith(COMMAND_PATH_PREFIX) == true }
            .forEach { event ->
                val bytes = DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray("payload")
                    ?: return@forEach
                PhoneWearCommandCoordinator.get(this).receive(bytes)
            }
    }
}

class PhoneWearCommandCoordinator private constructor(context: Context) {
    private val application = context.applicationContext
    private val inbox = WearCommandInbox(application)
    private val ledger = WearCommandLedger(application)
    private val processor = WearCommandProcessor()

    fun receive(bytes: ByteArray) {
        val command = runCatching { WearCodec.decodeCommand(bytes) }.getOrNull() ?: return
        inbox.put(command)
        retry()
    }

    fun retry() {
        val container = CalinoContainer.get(application)
        container.ensureConnected()
        container.scope.launch {
            inbox.all().forEach { command -> process(container, command) }
        }
    }

    private suspend fun process(container: CalinoContainer, command: WearCommand) {
        ledger.get(command.uuid)?.let {
            publish(it)
            inbox.remove(command.uuid)
            return
        }
        val snapshot = container.activeRepository.snapshot()
        val bridge = container.wearBridge
        val decision = processor.process(
            command = command,
            nowMillis = System.currentTimeMillis(),
            hasAccount = container.hasAccounts,
            authoritative = container.isWearDataAuthoritative(),
            loading = snapshot.sync is SyncState.Loading,
            sourceEpoch = bridge.sourceEpoch,
            sourceSequence = bridge.sourceSequence,
            tasks = snapshot.tasks.map {
                CommandTaskState(
                    occurrenceId = WearProjection.occurrenceIdentity(
                        it.id,
                        it.recurrenceId?.toEpochMilli(),
                        it.recurrenceDate ?: it.due,
                    ),
                    recordId = it.id,
                    done = it.done,
                    dueEpochDay = it.due?.toEpochDay(),
                )
            },
            mutation = CommandMutation {
                val result = when (command.op) {
                    WearCommandOp.SET_TASK_DONE -> container.activeRepository.setTaskDone(command.recordId, true)
                    WearCommandOp.RESCHEDULE_TASK -> container.activeRepository.rescheduleTask(
                        command.recordId,
                        LocalDate.ofEpochDay(requireNotNull(command.targetEpochDay)),
                    )
                    WearCommandOp.UNSUPPORTED -> error("Unsupported commands are rejected before mutation")
                }
                when (result) {
                    is WriteResult.Applied<*> -> CommandWriteResult.APPLIED to null
                    is WriteResult.Queued<*> -> CommandWriteResult.QUEUED to null
                    is WriteResult.Rejected -> CommandWriteResult.REJECTED to result.reason
                }
            },
        )
        if (decision is CommandDecision.Terminal) {
            val expectedSequence = bridge.sourceSequence + 1
            val freshSequence = bridge.publishIfChanged(container.activeRepository.snapshot(), force = true)
            val acknowledgement = decision.acknowledgement.copy(
                sourceSequence = freshSequence ?: expectedSequence,
            )
            ledger.put(acknowledgement)
            inbox.remove(command.uuid)
            publish(acknowledgement)
        }
    }

    private fun publish(acknowledgement: WearAck) {
        val request = PutDataMapRequest.create(ACK_PATH_PREFIX + acknowledgement.uuid).apply {
            dataMap.putByteArray("payload", WearCodec.encodeAck(acknowledgement))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(application).putDataItem(request)
    }

    companion object {
        @Volatile private var instance: PhoneWearCommandCoordinator? = null
        fun get(context: Context): PhoneWearCommandCoordinator = instance ?: synchronized(this) {
            instance ?: PhoneWearCommandCoordinator(context).also { instance = it }
        }
    }
}

private class WearCommandInbox(context: Context) {
    private val preferences = context.getSharedPreferences("wear_command_inbox", Context.MODE_PRIVATE)
    fun put(command: WearCommand) = preferences.edit().putString(command.uuid, WearCodec.encodeCommand(command).base64()).apply()
    fun remove(uuid: String) = preferences.edit().remove(uuid).apply()
    fun all(): List<WearCommand> = preferences.all.values.mapNotNull { value ->
        (value as? String)?.decodeBase64()?.let { runCatching { WearCodec.decodeCommand(it) }.getOrNull() }
    }
}

private class WearCommandLedger(context: Context) : CommandAckLedger {
    private val preferences = context.getSharedPreferences("wear_command_ledger", Context.MODE_PRIVATE)
    override fun get(uuid: String): WearAck? = preferences.getString(uuid, null)?.decodeBase64()
        ?.let { runCatching { WearCodec.decodeAck(it) }.getOrNull() }
    override fun put(acknowledgement: WearAck) {
        val retained = preferences.all.entries.sortedBy { it.key }.takeLast(255)
            .associate { it.key to it.value as String }.toMutableMap()
        retained[acknowledgement.uuid] = WearCodec.encodeAck(acknowledgement).base64()
        preferences.edit().clear().apply { retained.forEach(::putString) }.apply()
    }
}

private fun ByteArray.base64() = Base64.encodeToString(this, Base64.NO_WRAP)
private fun String.decodeBase64() = runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()
