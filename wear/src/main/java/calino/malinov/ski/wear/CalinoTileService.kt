package calino.malinov.ski.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import calino.malinov.ski.wearcontract.WearEvent
import calino.malinov.ski.wearcontract.WearFormatting
import calino.malinov.ski.wearcontract.WearSelection
import calino.malinov.ski.wearcontract.WearSnapshot
import calino.malinov.ski.wearcontract.WearTask
import com.google.common.util.concurrent.Futures

/**
 * Glanceable agenda. One timeline entry per event start/end and midnight, so ended events drop off
 * on schedule. Rows open the watch detail screen; task actions live there, not behind a small target.
 */
class CalinoTileService : TileService() {
    override fun onTileRequest(request: RequestBuilders.TileRequest) = Futures.immediateFuture(
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(timeline(request.deviceConfiguration))
            .build(),
    )

    override fun onTileResourcesRequest(request: RequestBuilders.ResourcesRequest) =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun timeline(device: DeviceParameters): TimelineBuilders.Timeline {
        val snapshot = WearStore(this).state().snapshot
        val now = System.currentTimeMillis()
        val points = listOf(now) + (snapshot?.let { WearSelection.boundaries(it, now) }.orEmpty()).take(MAX_ENTRIES - 1)
        val builder = TimelineBuilders.Timeline.Builder()
        points.forEachIndexed { index, start ->
            val validity = TimelineBuilders.TimeInterval.Builder().setStartMillis(start)
            points.getOrNull(index + 1)?.let(validity::setEndMillis)
            builder.addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setValidity(validity.build())
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout(device, snapshot, start)).build())
                    .build(),
            )
        }
        return builder.build()
    }

    private fun layout(device: DeviceParameters, snapshot: WearSnapshot?, atMillis: Long) =
        materialScope(this, device) {
            if (snapshot == null) {
                return@materialScope primaryLayout(
                    titleSlot = { text("Calino".layoutString) },
                    mainSlot = { text("Open Calino on your phone to sync".layoutString, maxLines = 3) },
                    bottomSlot = { textEdgeButton(onClick = openApp(), labelContent = { text("Open".layoutString) }) },
                )
            }
            val today = WearFormatting.today(snapshot, atMillis)
            val upcoming = WearSelection.upcoming(snapshot, today, WearFormatting.minuteNow(snapshot, atMillis))
            val limit = if (device.screenHeightDp < LARGE_SCREEN_DP) 2 else 3
            val rows = upcoming.take(limit)
            val more = upcoming.size - rows.size
            primaryLayout(
                titleSlot = { text(title(rows.firstOrNull(), today).layoutString) },
                mainSlot = {
                    if (rows.isEmpty()) {
                        text("Nothing else planned".layoutString, maxLines = 2)
                    } else {
                        val column = LayoutElementBuilders.Column.Builder().setWidth(expand())
                        rows.forEachIndexed { index, row ->
                            if (index > 0) column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
                            column.addContent(row(row, snapshot, today))
                        }
                        column.build()
                    }
                },
                bottomSlot = {
                    textEdgeButton(
                        onClick = openApp(),
                        labelContent = { text((if (more > 0) "+$more more" else "Open").layoutString) },
                    )
                },
            )
        }

    private fun MaterialScope.row(row: Any, snapshot: WearSnapshot, today: Long) = button(
        onClick = openApp(rowId(row)),
        width = expand(),
        // One line per row: two-line buttons do not fit twice on a 192dp (Pixel Watch 2) tile.
        labelContent = { text("${schedule(row, snapshot, today)}  ${rowTitle(row)}".layoutString, maxLines = 1) },
        iconContent = { dot(rowColor(row)) },
    )

    private fun dot(color: Int) = LayoutElementBuilders.Box.Builder()
        .setWidth(dp(DOT_DP))
        .setHeight(dp(DOT_DP))
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder().setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(androidx.wear.protolayout.ColorBuilders.argb(color))
                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(DOT_DP / 2)).build())
                    .build(),
            ).build(),
        )
        .build()

    private fun title(first: Any?, today: Long) = when (val day = first?.let(::rowDay)) {
        null, today -> "Today"
        today + 1 -> "Tomorrow"
        else -> WearFormatting.date(day)
    }

    /** Terse lead-in: a start time today, otherwise the weekday; untimed rows say what they are. */
    private fun schedule(row: Any, snapshot: WearSnapshot, today: Long): String {
        val day = rowDay(row)
        val minute = when (row) {
            is WearEvent -> row.startMinute.takeUnless { row.allDay }
            is WearTask -> row.dueMinute
            else -> null
        }
        if (day != today) {
            return java.time.LocalDate.ofEpochDay(day).dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        }
        return minute?.let { WearFormatting.time(it, snapshot.timeFormat, compact = true) }
            ?: if (row is WearTask) "Task" else "All day"
    }

    private fun rowDay(row: Any): Long = when (row) {
        is WearEvent -> row.startEpochDay
        is WearTask -> row.dueEpochDay ?: Long.MAX_VALUE
        else -> Long.MAX_VALUE
    }

    /** Launches the exported [WearActivity]; a tile cannot start this app's non-exported activities. */
    private fun openApp(occurrenceId: String? = null): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(WearActivity::class.java.name)
        occurrenceId?.let {
            activity.addKeyToExtraMapping(EXTRA_OCCURRENCE_ID, ActionBuilders.AndroidStringExtra.Builder().setValue(it).build())
        }
        return ModifiersBuilders.Clickable.Builder()
            .setId(occurrenceId?.let { "detail:$it" } ?: "open")
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "2"
        const val FRESHNESS_MILLIS = 6 * 60 * 60 * 1000L
        const val MAX_ENTRIES = 40
        const val LARGE_SCREEN_DP = 225
        const val DOT_DP = 10f
    }
}
