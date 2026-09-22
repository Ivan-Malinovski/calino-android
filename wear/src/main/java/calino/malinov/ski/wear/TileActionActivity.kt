package calino.malinov.ski.wear

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.wear.tiles.TileService
import calino.malinov.ski.wearcontract.WearCommand
import calino.malinov.ski.wearcontract.WearCommandOp

class TileActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val occurrenceId = intent.getStringExtra("occurrenceId")
        val mode = intent.getStringExtra("mode")
        val state = WearStore(this).state()
        val snapshot = state.snapshot
        val task = snapshot?.tasks?.firstOrNull { it.occurrenceId == occurrenceId }
        if (mode == "complete" && task != null) {
            WearCommands.send(
                this,
                WearCommand(
                    occurrenceId = task.occurrenceId,
                    recordId = task.recordId,
                    op = WearCommandOp.SET_TASK_DONE,
                    observedDone = task.done,
                    observedDueEpochDay = task.dueEpochDay,
                    sourceEpoch = snapshot.sourceEpoch,
                    sourceSequence = snapshot.sequence,
                ),
            )
            TileService.getUpdater(this).requestUpdate(CalinoTileService::class.java)
        } else if (occurrenceId != null) {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("calino-wear://detail/${Uri.encode(occurrenceId)}"), this, WearActivity::class.java),
            )
        }
        finish()
    }
}
