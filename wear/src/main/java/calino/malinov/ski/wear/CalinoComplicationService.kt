package calino.malinov.ski.wear

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import calino.malinov.ski.wearcontract.WearEvent
import calino.malinov.ski.wearcontract.WearFormatting
import calino.malinov.ski.wearcontract.WearSelection
import calino.malinov.ski.wearcontract.WearTask

class CalinoComplicationService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        val snapshot = WearStore(this).state().snapshot
        val row = snapshot?.let {
            WearSelection.complication(
                it,
                WearFormatting.today(it),
                WearFormatting.minuteNow(it),
            )
        }
        val title = when (row) {
            is WearEvent -> row.title
            is WearTask -> row.title
            else -> "No plans"
        }
        val occurrenceId = when (row) {
            is WearEvent -> row.occurrenceId
            is WearTask -> row.occurrenceId
            else -> "empty"
        }
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("calino-wear://detail/${Uri.encode(occurrenceId)}"),
            this,
            WearActivity::class.java,
        )
        val tap = PendingIntent.getActivity(
            this,
            request.complicationInstanceId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        listener.onComplicationData(data(request.complicationType, title, tap))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        data(type, if (type == ComplicationType.LONG_TEXT) "Next plan" else "No plans", null)

    private fun data(type: ComplicationType, title: String, tap: PendingIntent?): ComplicationData {
        val text = PlainComplicationText.Builder(title).build()
        val contentDescription = PlainComplicationText.Builder("Calino").build()
        return if (type == ComplicationType.LONG_TEXT) {
            LongTextComplicationData.Builder(text, contentDescription).apply { tap?.let(::setTapAction) }.build()
        } else {
            ShortTextComplicationData.Builder(text, contentDescription).apply { tap?.let(::setTapAction) }.build()
        }
    }
}
