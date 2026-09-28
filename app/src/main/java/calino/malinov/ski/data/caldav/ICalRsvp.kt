package calino.malinov.ski.data.caldav

import biweekly.component.VEvent
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.property.DateEnd
import biweekly.property.DateStart
import biweekly.property.DateTimeStamp
import biweekly.property.LastModified
import biweekly.property.ICalProperty
import biweekly.property.RecurrenceId
import biweekly.util.ICalDate
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import java.time.Instant
import java.util.Date
import java.util.Locale

/** Case-insensitive mailbox identity only; non-mailto principal URIs stay distinct. */
fun normalizedCalendarAddress(value: String): String =
    value.trim().let { if (it.startsWith("mailto:", ignoreCase = true)) it.substring(7) else it }
        .lowercase(Locale.ROOT)

/** Narrow RSVP transformation over raw iCalendar, preserving every other property. */
internal object ICalRsvp {
    private fun <T : ICalProperty> VEvent.replaceOne(property: T) {
        @Suppress("UNCHECKED_CAST")
        removeProperties(property.javaClass as Class<T>)
        addProperty(property)
    }
    private val statuses = mapOf(
        "ACCEPTED" to ParticipationStatus.ACCEPTED,
        "TENTATIVE" to ParticipationStatus.TENTATIVE,
        "DECLINED" to ParticipationStatus.DECLINED,
    )

    fun status(original: String, event: CalEvent, address: String, scope: RecurrenceEditScope): String? =
        runCatching {
            val calendar = ICalTimezones.parse(original.removePrefix("\uFEFF").trim()).singleOrNull()
                ?: return@runCatching null
            val group = calendar.events.filter { it.uid?.value == event.uid }
            val master = group.singleOrNull { it.recurrenceId == null } ?: return@runCatching null
            val target = if (scope == RecurrenceEditScope.This) {
                group.firstOrNull { it.recurrenceId?.value?.let { rid -> sameTarget(rid, event) } == true } ?: master
            } else master
            val attendee = target.attendees.singleOrNull {
                normalizedCalendarAddress(it.email ?: it.uri.orEmpty()) == normalizedCalendarAddress(address)
            } ?: return@runCatching null
            attendee.participationStatus?.value ?: "NEEDS-ACTION"
        }.getOrNull()

    fun patch(
        original: String,
        event: CalEvent,
        address: String,
        status: String,
        expectedStatus: String,
        scope: RecurrenceEditScope,
        now: Instant,
    ): String? = runCatching {
        val calendar = ICalTimezones.parse(original.removePrefix("\uFEFF").trim()).singleOrNull()
            ?: return@runCatching null
        val uid = event.uid ?: return@runCatching null
        val group = calendar.events.filter { it.uid?.value == uid }
        val master = group.singleOrNull { it.recurrenceId == null }
            ?: return@runCatching null
        val target = if (scope == RecurrenceEditScope.This) {
            group.firstOrNull { it.recurrenceId?.value?.let { rid -> sameTarget(rid, event) } == true }
                ?: createOverride(master, event)?.also(calendar::addEvent)
                ?: return@runCatching null
        } else master
        val matching = target.attendees.filter {
            normalizedCalendarAddress(it.email ?: it.uri.orEmpty()) == normalizedCalendarAddress(address)
        }
        if (matching.size != 1) return@runCatching null
        val attendee = matching.single()
        val currentStatus = attendee.participationStatus?.value ?: "NEEDS-ACTION"
        if (!currentStatus.equals(expectedStatus, ignoreCase = true) &&
            !currentStatus.equals(status, ignoreCase = true)
        ) return@runCatching null
        val replacement = attendee.copy().also {
            it.participationStatus = statuses[status] ?: return@runCatching null
        }
        target.removeProperty(attendee)
        target.addProperty(replacement)
        target.replaceOne(DateTimeStamp(Date.from(now)))
        target.replaceOne(LastModified(Date.from(now)))
        ICalWriter.write(calendar)
    }.getOrNull()

    private fun sameTarget(value: ICalDate, event: CalEvent): Boolean =
        if (event.recurrenceDate != null) {
            val raw = value.rawComponents
            !value.hasTime() && raw != null &&
                raw.year == event.recurrenceDate.year && raw.month == event.recurrenceDate.monthValue &&
                raw.date == event.recurrenceDate.dayOfMonth
        } else value.hasTime() && event.recurrenceId == value.toInstant()

    private fun createOverride(master: VEvent, event: CalEvent): VEvent? {
        val target = event.recurrenceDate?.let { RecurrenceEdit.Target.allDay(it).value }
            ?: event.recurrenceId?.let { RecurrenceEdit.Target.timed(it).value }
            ?: return null
        val start = master.dateStart?.value ?: return null
        val moved = master.copy()
        moved.removeProperties(biweekly.property.RecurrenceRule::class.java)
        moved.removeProperties(biweekly.property.RecurrenceDates::class.java)
        moved.removeProperties(biweekly.property.ExceptionDates::class.java)
        moved.replaceOne(RecurrenceId(target))
        moved.replaceOne(DateStart(target))
        master.dateEnd?.value?.let { end ->
            val duration = end.time - start.time
            moved.replaceOne(DateEnd(ICalDate(Date(target.time + duration), target.hasTime())))
        }
        return moved
    }
}
