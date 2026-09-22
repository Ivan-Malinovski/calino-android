package calino.malinov.ski.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import calino.malinov.ski.wearcontract.WearEvent
import calino.malinov.ski.wearcontract.WearFormatting
import calino.malinov.ski.wearcontract.WearSelection
import calino.malinov.ski.wearcontract.WearSnapshot
import calino.malinov.ski.wearcontract.WearTask
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Terse "what's next": the time is the primary datum, the title its label. A timeline carries the
 * selection across event starts/ends and midnight, so the face changes without a phone publish.
 */
class CalinoComplicationService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        val snapshot = WearStore(this).state().snapshot
        val now = System.currentTimeMillis()
        val type = request.complicationType
        if (snapshot == null) {
            listener.onComplicationData(data(type, Glance.empty(), openApp(request)))
            return
        }
        val points = listOf(now) + WearSelection.boundaries(snapshot, now)
        val entries = points.zipWithNext().flatMap { (start, end) ->
            val glance = glance(snapshot, start)
            steps(glance, type, start, end).map { (from, to) ->
                TimelineEntry(
                    TimeInterval(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to)),
                    data(type, glance.at(from), tap(request, glance)),
                )
            }
        }.take(MAX_ENTRIES)
        val current = glance(snapshot, now)
        listener.onComplicationDataTimeline(
            ComplicationDataTimeline(data(type, current.at(now), tap(request, current)), entries),
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData = data(
        type,
        Glance("10:00", "Design review", "Next · 10:00", progress = 0.4f, occurrenceId = null),
        null,
    )

    private fun glance(snapshot: WearSnapshot, atMillis: Long): Glance {
        val today = WearFormatting.today(snapshot, atMillis)
        val minute = WearFormatting.minuteNow(snapshot, atMillis)
        val format = snapshot.timeFormat
        return when (val row = WearSelection.complication(snapshot, today, minute)) {
            is WearTask -> {
                val due = row.dueEpochDay
                val overdue = due != null && due < today
                val short = when {
                    overdue -> "Late"
                    due == today -> row.dueMinute?.let { WearFormatting.time(it, format, compact = true) } ?: "Today"
                    due != null -> weekday(due)
                    else -> "Task"
                }
                Glance(short, row.title, if (overdue) "Overdue task" else "Task · $short", null, row.occurrenceId)
            }
            is WearEvent -> {
                val start = row.startMinute
                if (row.allDay || start == null) {
                    val short = if (row.startEpochDay <= today) "Today" else weekday(row.startEpochDay)
                    Glance(short, row.title, "All day", null, row.occurrenceId)
                } else {
                    val startMillis = WearFormatting.instantMillis(snapshot, row.startEpochDay, start)
                    val endMillis = startMillis + (row.durationMinutes ?: 0) * 60_000L
                    val startLabel = WearFormatting.time(start, format, compact = true)
                    when {
                        atMillis >= startMillis -> Glance(
                            "Now",
                            row.title,
                            "Now · until ${WearFormatting.time(start + (row.durationMinutes ?: 0), format, compact = true)}",
                            progress = 0f,
                            occurrenceId = row.occurrenceId,
                            startMillis = startMillis,
                            endMillis = endMillis,
                        )
                        row.startEpochDay == today -> Glance(
                            startLabel,
                            row.title,
                            "Next · $startLabel",
                            progress = 0f,
                            occurrenceId = row.occurrenceId,
                            countdownTo = startMillis,
                        )
                        else -> Glance(weekday(row.startEpochDay), row.title, "${weekday(row.startEpochDay)} · $startLabel", null, row.occurrenceId)
                    }
                }
            }
            else -> Glance.empty()
        }
    }

    /** Ranged progress is static per entry, so a running event is split into small steps. */
    private fun steps(glance: Glance, type: ComplicationType, start: Long, end: Long): List<Pair<Long, Long>> {
        if (type != ComplicationType.RANGED_VALUE || glance.startMillis == null) return listOf(start to end)
        val step = maxOf(PROGRESS_STEP_MILLIS, (end - start) / 12)
        return (start until end step step).map { it to minOf(it + step, end) }
    }

    private fun data(type: ComplicationType, glance: Glance, tap: PendingIntent?): ComplicationData {
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_complication)).build()
        val description = plain("${glance.longTitle}: ${glance.title}")
        val shortText: ComplicationText = glance.countdownTo?.let { target ->
            TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.SHORT_DUAL_UNIT,
                CountDownTimeReference(Instant.ofEpochMilli(target)),
            ).setMinimumTimeUnit(TimeUnit.MINUTES).build()
        } ?: plain(glance.short)
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(plain(glance.title), description)
                .setTitle(plain(glance.longTitle))
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                (glance.progress ?: 0f) * 100f, 0f, 100f, description,
            )
                .setText(shortText)
                .setTitle(plain(glance.title))
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
            else -> ShortTextComplicationData.Builder(shortText, description)
                .setTitle(plain(glance.title))
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
        }
    }

    private fun tap(request: ComplicationRequest, glance: Glance): PendingIntent {
        val id = glance.occurrenceId ?: return openApp(request)
        return pending(request, Intent(Intent.ACTION_VIEW, Uri.parse("calino-wear://detail/${Uri.encode(id)}"), this, WearActivity::class.java))
    }

    private fun openApp(request: ComplicationRequest) = pending(request, Intent(this, WearActivity::class.java))

    private fun pending(request: ComplicationRequest, intent: Intent) = PendingIntent.getActivity(
        this,
        request.complicationInstanceId,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun plain(text: String) = PlainComplicationText.Builder(text).build()

    private fun weekday(epochDay: Long) =
        LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    private data class Glance(
        val short: String,
        val title: String,
        val longTitle: String,
        val progress: Float?,
        val occurrenceId: String?,
        val countdownTo: Long? = null,
        val startMillis: Long? = null,
        val endMillis: Long? = null,
    ) {
        /** Progress through a running event at [millis]; other glances are time-independent. */
        fun at(millis: Long): Glance {
            if (startMillis == null || endMillis == null || endMillis <= startMillis) return this
            return copy(progress = ((millis - startMillis).toFloat() / (endMillis - startMillis)).coerceIn(0f, 1f))
        }

        companion object {
            fun empty() = Glance("Free", "No plans", "Calino", null, null)
        }
    }

    private companion object {
        const val MAX_ENTRIES = 100
        const val PROGRESS_STEP_MILLIS = 5 * 60_000L
    }
}
