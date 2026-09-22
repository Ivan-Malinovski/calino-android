package calino.malinov.ski.wear

import android.content.Context
import calino.malinov.ski.wearcontract.COMMAND_PATH_PREFIX
import calino.malinov.ski.wearcontract.WearCodec
import calino.malinov.ski.wearcontract.WearCommand
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearCommands {
    fun send(context: Context, command: WearCommand) {
        val application = context.applicationContext
        WearStore(application).enqueue(command)
        publish(application, command)
    }

    /** DataItems are idempotent by UUID; failed sends stay durably queued. */
    fun replay(context: Context) {
        val application = context.applicationContext
        val store = WearStore(application)
        val acknowledged = store.acknowledgements().mapTo(hashSetOf()) { it.uuid }
        store.commands().filterNot { it.uuid in acknowledged }.forEach { publish(application, it) }
    }

    private fun publish(context: Context, command: WearCommand) {
        val request = PutDataMapRequest.create(COMMAND_PATH_PREFIX + command.uuid).apply {
            dataMap.putByteArray("payload", WearCodec.encodeCommand(command))
            dataMap.putLong("retryAt", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        // The command is removed only by an ACK, never by this asynchronous send.
        Wearable.getDataClient(context).putDataItem(request)
    }
}
