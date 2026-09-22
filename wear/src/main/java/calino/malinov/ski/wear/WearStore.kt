package calino.malinov.ski.wear

import android.content.Context
import android.util.AtomicFile
import calino.malinov.ski.wearcontract.WearAck
import calino.malinov.ski.wearcontract.WearCodec
import calino.malinov.ski.wearcontract.WearCommand
import calino.malinov.ski.wearcontract.WearReducedState
import calino.malinov.ski.wearcontract.WearSnapshot
import calino.malinov.ski.wearcontract.WearStateReducer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WearStateUpdates {
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()
    fun changed() { mutableRevision.value += 1 }
}

class WearStore(context: Context) {
    private val snapshotFile = AtomicFile(File(context.filesDir, "wear-snapshot.bin"))
    private val outboxFile = AtomicFile(File(context.filesDir, "wear-outbox.bin"))
    private val ackFile = AtomicFile(File(context.filesDir, "wear-acks.bin"))

    @Synchronized fun snapshot(): WearSnapshot? = runCatching {
        WearCodec.decodeSnapshot(snapshotFile.readFully())
    }.getOrNull()

    @Synchronized fun state(): WearReducedState = WearStateReducer.reduce(snapshot(), commands(), acknowledgements())

    @Synchronized fun saveSnapshot(bytes: ByteArray) {
        atomic(snapshotFile, bytes)
        val snapshot = snapshot()
        val acknowledged = acknowledgements().associateBy { it.uuid }
        if (snapshot != null) {
            writeCommands(commands().filter { command ->
                val ack = acknowledged[command.uuid]
                ack == null || ack.sourceEpoch != snapshot.sourceEpoch || ack.sourceSequence > snapshot.sequence
            })
        }
        WearStateUpdates.changed()
    }

    @Synchronized fun commands(): List<WearCommand> = readRecords(outboxFile, WearCodec::decodeCommand)

    @Synchronized fun acknowledgements(): List<WearAck> = readRecords(ackFile, WearCodec::decodeAck)

    @Synchronized fun enqueue(command: WearCommand) {
        writeCommands((commands() + command).distinctBy { it.uuid }.takeLast(100))
        WearStateUpdates.changed()
    }

    @Synchronized fun acknowledge(acknowledgement: WearAck) {
        writeAcks((acknowledgements() + acknowledgement).distinctBy { it.uuid }.takeLast(20))
        val snapshot = snapshot()
        val successCaughtUp = snapshot != null &&
            snapshot.sourceEpoch == acknowledgement.sourceEpoch &&
            snapshot.sequence >= acknowledgement.sourceSequence
        if (successCaughtUp || acknowledgement.result !in setOf(
                calino.malinov.ski.wearcontract.WearAckResult.APPLIED,
                calino.malinov.ski.wearcontract.WearAckResult.QUEUED,
                calino.malinov.ski.wearcontract.WearAckResult.NOOP,
            )) {
            writeCommands(commands().filterNot { it.uuid == acknowledgement.uuid })
        }
        WearStateUpdates.changed()
    }

    private fun writeCommands(values: List<WearCommand>) =
        writeRecords(outboxFile, values, WearCodec::encodeCommand)

    private fun writeAcks(values: List<WearAck>) =
        writeRecords(ackFile, values, WearCodec::encodeAck)

    private fun <T> readRecords(file: AtomicFile, decode: (ByteArray) -> T): List<T> = runCatching {
        DataInputStream(ByteArrayInputStream(file.readFully())).use { input ->
            List(input.readInt().also { require(it in 0..1000) }) {
                ByteArray(input.readInt().also { require(it in 0..1_000_000) })
                    .also(input::readFully)
                    .let(decode)
            }
        }
    }.getOrDefault(emptyList())

    private fun <T> writeRecords(file: AtomicFile, values: List<T>, encode: (T) -> ByteArray) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(values.size)
            values.forEach { value ->
                val bytes = encode(value)
                output.writeInt(bytes.size)
                output.write(bytes)
            }
        }
        atomic(file, buffer.toByteArray())
    }

    private fun atomic(file: AtomicFile, bytes: ByteArray) {
        val output = file.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            file.finishWrite(output)
        } catch (throwable: Throwable) {
            file.failWrite(output)
            throw throwable
        }
    }
}
