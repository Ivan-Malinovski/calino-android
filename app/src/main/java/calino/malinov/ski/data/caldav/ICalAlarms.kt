package calino.malinov.ski.data.caldav

import biweekly.component.ICalComponent
import biweekly.component.VAlarm
import biweekly.parameter.Related
import biweekly.property.Trigger
import calino.malinov.ski.data.model.Reminder
import kotlin.math.abs
import biweekly.util.Duration as ICalDuration

/**
 * The one definition of which `VALARM`s belong to Calino.
 *
 * A resource is not ours. Another client's alarm may be an email to a mailing
 * list, a repeating nag, or a trigger anchored to the end of the event -- none
 * of which [Reminder] can express, and all of which would be destroyed by a
 * writer that simply cleared the alarm list before writing its own.
 *
 * So alarms are *partitioned* rather than replaced. An alarm Calino can state
 * exactly is read into the model and rewritten from it; everything else is
 * foreign and is never read, never rewritten, and never removed. The reader,
 * the writer and the patcher all ask this file the same question so that the
 * three cannot drift into disagreeing about who owns what.
 */

/**
 * The reminder this alarm represents, or null when it is somebody else's.
 *
 * Every clause here is a thing [Reminder] cannot say. `minutesBefore` is a lead
 * time before the start and nothing more: no action, no anchor, no repeat.
 */
internal fun VAlarm.calinoReminder(): Reminder? {
    val action = action?.value?.uppercase() ?: return null
    if (action != "DISPLAY" && action != "AUDIO") return null

    // A repeating alarm, or one with its own duration, says something about
    // delivery that the model would silently drop.
    if (repeat?.value != null || duration?.value != null) return null

    val trigger = trigger ?: return null
    // An absolute trigger is a moment, not a lead time. It does not move when
    // the event moves, and rewriting it from `minutesBefore` would change that.
    val lead = trigger.duration ?: return null
    val related = trigger.related
    if (related != null && related != Related.START) return null

    // Magnitude only: the direction lives in `isPrior`, and biweekly's sign
    // convention for it is not something to depend on.
    val millis = abs(lead.toMillis())
    // Prior-or-zero only: an alarm *after* the start is not a reminder here.
    if (millis != 0L && !lead.isPrior) return null
    if (millis % 60_000L != 0L) return null

    val minutes = millis / 60_000L
    if (minutes > Int.MAX_VALUE) return null
    return Reminder(minutesBefore = minutes.toInt())
}

/**
 * The reminders Calino can show for this component.
 *
 * Sorted by lead time and de-duplicated so that reading, writing and reading
 * again cannot reorder the editor's chips.
 */
internal fun ICalComponent.readReminders(): List<Reminder> =
    getComponents(VAlarm::class.java)
        .mapNotNull { it.calinoReminder() }
        .distinct()
        .sortedByDescending { it.minutesBefore }

/**
 * Rewrites Calino's alarms, leaving every foreign alarm exactly as it arrived.
 *
 * The removal half is the point, and it is the same rule the rest of
 * [ICalWriter] follows: clearing the reminder chips has to delete our `VALARM`s
 * from the resource, or the clear silently fails to save. What it must not do
 * is widen that deletion to an alarm Calino never authored.
 *
 * **This function must stay idempotent**, and `ICalPatcher.mergeAlarms` is the
 * reason. Rebuilding an unchanged alarm would look like a simplification and
 * would quietly break the rebase: the merge decides whether the local edit
 * touched the alarms by comparing their rendered form, so a delete-and-recreate
 * makes every unrelated edit claim the reminders moved and win over the
 * server's. If you change the shape of this function, run `ICalPatcherTest`.
 */
internal fun ICalComponent.writeReminders(reminders: List<Reminder>, summary: String) {
    val wanted = reminders.map { it.minutesBefore.coerceAtLeast(0) }.distinct().sortedDescending()

    // Copied before removing: the component list biweekly hands back is a live
    // view, and mutating it mid-iteration skips entries.
    val ours = getComponents(VAlarm::class.java)
        .mapNotNull { alarm -> alarm.calinoReminder()?.let { alarm to it.minutesBefore } }
        .toList()

    // An alarm whose lead time is unchanged is left exactly as it is rather
    // than rebuilt. Rewriting it would churn its DESCRIPTION and any parameter
    // another client put on it, and would defeat ICalPatcher.mergeAlarms as
    // described above.
    val kept = mutableSetOf<Int>()
    ours.forEach { (alarm, minutes) ->
        if (minutes in wanted && kept.add(minutes)) return@forEach
        removeComponent(alarm)
    }

    val description = summary.trim().takeIf(String::isNotEmpty) ?: "Reminder"
    wanted.filterNot { it in kept }.forEach { minutes ->
        val duration = ICalDuration.builder().prior(true).minutes(minutes).build()
        addComponent(VAlarm.display(Trigger(duration, null), description))
    }
}
