package calino.malinov.ski.wear

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.protolayout.TimelineBuilders
import com.google.common.util.concurrent.Futures
import calino.malinov.ski.wearcontract.*

class CalinoTileService : TileService() {
    override fun onTileRequest(request: RequestBuilders.TileRequest) = Futures.immediateFuture(
        TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder().addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder().setLayout(
                        LayoutElementBuilders.Layout.Builder().setRoot(tile()).build(),
                    ).build(),
                ).build(),
            ).build(),
    )
    override fun onTileResourcesRequest(request: RequestBuilders.ResourcesRequest) =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion("1").build())

    private fun tile(): LayoutElementBuilders.LayoutElement {
        val s = WearStore(this).state().snapshot
        val col = LayoutElementBuilders.Column.Builder().addContent(
            LayoutElementBuilders.Text.Builder().setText("Calino").build(),
        )
        if (s == null) {
            return col.addContent(
                LayoutElementBuilders.Text.Builder().setText("Open phone to sync").build(),
            ).build()
        }
        WearSelection.tile(s, WearFormatting.today(s)).forEach { row ->
            val line = LayoutElementBuilders.Row.Builder()
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(when (row) { is WearEvent -> row.title; is WearTask -> row.title; else -> "" })
                        .setMaxLines(1)
                        .setModifiers(clickable("detail", rowId(row)))
                        .build(),
                )
            if (row is WearTask) {
                line.addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(if (row.writeState == WearWriteState.PENDING) "…" else "✓")
                        .setModifiers(clickable("complete", row.occurrenceId))
                        .build(),
                )
            }
            col.addContent(line.build())
        }
        return col.build()
    }

    private fun clickable(mode: String, occurrenceId: String): ModifiersBuilders.Modifiers {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(TileActionActivity::class.java.name)
            .addKeyToExtraMapping("mode", ActionBuilders.AndroidStringExtra.Builder().setValue(mode).build())
            .addKeyToExtraMapping("occurrenceId", ActionBuilders.AndroidStringExtra.Builder().setValue(occurrenceId).build())
            .build()
        val launch = ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build()
        return ModifiersBuilders.Modifiers.Builder().setClickable(
            ModifiersBuilders.Clickable.Builder()
                .setId("$mode:$occurrenceId")
                .setOnClick(launch)
                .build(),
        ).build()
    }

    private fun rowId(row: Any) = when (row) {
        is WearEvent -> row.occurrenceId
        is WearTask -> row.occurrenceId
        else -> "unknown"
    }
}
