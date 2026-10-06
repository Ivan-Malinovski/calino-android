package calino.malinov.ski.util

import android.content.Context
import calino.malinov.ski.R
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.placementDate
import java.time.LocalDate
import java.time.format.TextStyle

/** Presentation overloads; pure/JVM formatters keep their deterministic English contract. */
fun formatCalinoDuration(context: Context, minutes: Int): String {
    fun unit(value: Int, id: Int) = context.getString(id, value)
    val minute = R.string.fmt_minutes
    val hour = R.string.fmt_hours
    val day = R.string.fmt_days
    return when {
        minutes <= 0 -> unit(0, minute)
        minutes < 60 -> unit(minutes, minute)
        minutes < 24 * 60 -> listOfNotNull(
            unit(minutes / 60, hour),
            (minutes % 60).takeIf { it != 0 }?.let { unit(it, minute) },
        ).joinToString(" ")
        else -> listOfNotNull(
            unit(minutes / (24 * 60), day),
            (minutes % (24 * 60)).takeIf { it != 0 }?.let {
                if (it < 60) unit(it, minute) else unit(it / 60, hour)
            },
        ).joinToString(" ")
    }
}

fun formatRecurrenceSummary(context: Context, event: CalEvent): String =
    formatRecurrenceRule(context, event.recurrence, event.placementDate())

fun formatRecurrenceRule(context: Context, recurrence: String?, anchor: LocalDate?): String {
    val fields = recurrenceFields(recurrence) ?: return ""
    val locale = context.resources.configuration.locales[0]
    val dateFormat = localizedDisplayFormatter("d MMM", locale)
    val frequency = when (fields["FREQ"]) {
        "DAILY" -> context.getString(R.string.fmt_every_day)
        "WEEKLY" -> {
            val weekdays = fields["BYDAY"]?.split(',')?.mapNotNull(::dayOfWeekForCode)
                ?.ifEmpty { null } ?: anchor?.let { listOf(it.dayOfWeek) }
            if (weekdays == null) context.getString(R.string.fmt_every_week)
            else context.getString(R.string.fmt_every_weekdays,
                weekdays.joinToString(", ") { it.getDisplayName(TextStyle.FULL, locale) })
        }
        "MONTHLY" -> if (anchor == null) context.getString(R.string.fmt_every_month)
            else context.getString(R.string.fmt_month_day, anchor.dayOfMonth)
        "YEARLY" -> if (anchor == null) context.getString(R.string.fmt_every_year)
            else context.getString(R.string.fmt_year_date, anchor.format(dateFormat))
        else -> return context.getString(R.string.fmt_repeating_event)
    }
    parseRecurrenceUntil(fields["UNTIL"])?.let {
        return context.getString(R.string.fmt_until, frequency, it.format(dateFormat))
    }
    fields["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }?.let {
        return context.resources.getQuantityString(R.plurals.fmt_repeat_times, it, frequency, it)
    }
    return frequency
}

/** Undo copy is presentation; the repository's reversible mutation stays locale-independent. */
fun localizedUndoDescription(context: Context, change: calino.malinov.ski.data.repository.UndoableChange): String {
    val before = (change.before as? calino.malinov.ski.data.repository.ChangeValue.Task)?.value ?: return change.description
    val after = (change.after as? calino.malinov.ski.data.repository.ChangeValue.Task)?.value ?: return change.description
    return when {
        before.done != after.done -> context.getString(if (after.done) R.string.fmt_completed else R.string.fmt_reopened, after.title)
        before.due != after.due -> context.getString(R.string.fmt_rescheduled, after.title,
            after.due?.toString() ?: context.getString(R.string.fmt_no_date))
        else -> change.description
    }
}
