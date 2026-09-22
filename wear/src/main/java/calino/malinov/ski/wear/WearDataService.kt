package calino.malinov.ski.wear

import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import calino.malinov.ski.wearcontract.ACK_PATH_PREFIX
import calino.malinov.ski.wearcontract.SNAPSHOT_PATH
import calino.malinov.ski.wearcontract.WearCodec
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService

class WearDataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val store = WearStore(this)
        var changed = false
        events.asSequence().filter { it.type == DataEvent.TYPE_CHANGED }.forEach { event ->
            val path = event.dataItem.uri.path.orEmpty()
            val bytes = DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray("payload")
                ?: return@forEach
            when {
                path == SNAPSHOT_PATH -> {
                    store.saveSnapshot(bytes)
                    WearCommands.replay(this)
                    changed = true
                }
                path.startsWith(ACK_PATH_PREFIX) -> {
                    store.acknowledge(WearCodec.decodeAck(bytes))
                    changed = true
                }
            }
        }
        if (changed) refreshSurfaces()
    }

    override fun onPeerConnected(peer: Node) {
        WearCommands.replay(this)
    }

    private fun refreshSurfaces() {
        TileService.getUpdater(this).requestUpdate(CalinoTileService::class.java)
        ComplicationDataSourceUpdateRequester.create(
            this,
            android.content.ComponentName(this, CalinoComplicationService::class.java),
        ).requestUpdateAll()
    }
}
