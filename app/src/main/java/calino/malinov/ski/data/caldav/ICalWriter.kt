package calino.malinov.ski.data.caldav

import biweekly.Biweekly
import biweekly.ICalDataType
import biweekly.ICalVersion
import biweekly.io.scribe.property.ConferenceScribe
import biweekly.ICalendar
import biweekly.component.ICalComponent
import biweekly.component.VEvent
import biweekly.component.VJournal
import biweekly.component.VTodo
import biweekly.property.Categories
import biweekly.property.Completed
import biweekly.property.Conference
import biweekly.property.Created
import biweekly.property.DateDue
import biweekly.property.DateEnd
import biweekly.property.DateStart
import biweekly.property.DateTimeStamp
import biweekly.property.Description
import biweekly.property.DurationProperty
import biweekly.property.LastModified
import biweekly.property.Location
import biweekly.property.PercentComplete
import biweekly.property.Priority
import biweekly.property.RecurrenceRule
import biweekly.property.RecurrenceId
import biweekly.property.RelatedTo
import biweekly.parameter.RelationshipType
import biweekly.property.Sequence
import biweekly.property.Status
import biweekly.property.Summary
import biweekly.property.Transparency
import biweekly.property.Url
import biweekly.util.DateTimeComponents
import biweekly.util.ICalDate
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.JournalEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import biweekly.property.Attendee as ICalAttendee

/**
 * Turns the app's models back into iCalendar components.
 *
 * The mirror of [ICalMapper], and deliberately shaped as its mirror: every rule
 * the reader applies has its inverse here, at the same level of detail, because
 * a write that does not undo exactly what the read did is a silent data loss.
 *
 * Two properties of this writer matter more than the field mapping:
 *
 * 1. **It writes into an existing component when given one.** [ICalPatcher]
 *    hands over the component parsed from the server's own bytes, so anything
 *    Calino does not model -- `ORGANIZER`, `CLASS`, `X-` properties, another
 *    client's parameters -- survives an edit untouched. Alarms are the one
 *    place that needs a rule rather than an omission, because Calino now owns
 *    some of them: `ICalAlarms.kt` partitions a component's `VALARM`s into
 *    ours, which are rewritten, and foreign, which are left alone.
 * 2. **Every field Calino owns is written *or explicitly removed*.** Clearing a
 *    location in the editor has to delete `LOCATION` from the resource; leaving
 *    the old property in place would make the clear silently fail to save.
 *
 * [now] is threaded through rather than read per-property so `DTSTAMP` and
 * `LAST-MODIFIED` cannot disagree by the milliseconds between two calls.
 */
class ICalWriter(private val zone: ZoneId = ZoneId.systemDefault()) {

    // --- VEVENT ---------------------------------------------------------------

    fun writeEvent(
        event: CalEvent,
        original: VEvent? = null,
        now: Instant,
        preserveRecurrenceIfMissing: Boolean = false,
    ): VEvent {
        val vevent = original ?: VEvent().also { it.properties.clear() }
        val originalRecurrenceRange = original?.recurrenceId?.getParameter("RANGE")

        vevent.setUidValue(event.uid ?: event.id)
        vevent.setSummaryValue(event.title)

        // VEVENT permits DTEND or DURATION, never both. We normalize writes to
        // DTEND, so remove a legal source DURATION before replacing its dates.
        vevent.removeProperties(DurationProperty::class.java)
        if (event.allDay) {
            val startDate = event.date ?: LocalDate.now(zone)
            vevent.replace(DateStart(startDate.toDateOnly()))
            // DTEND is exclusive, and `endDate` is the inclusive last day: the
            // exact inverse of the conversion in ICalMapper.mapEvent. A single
            // all-day event still needs a DTEND of the following day.
            vevent.replace(DateEnd((event.endDate ?: startDate).plusDays(1).toDateOnly()))
        } else {
            val start = event.start?.atZone(zone)?.toInstant() ?: now
            val minutes = (event.durationMinutes ?: DefaultDurationMinutes).coerceAtLeast(0)
            val end = start.plusSeconds(minutes * 60L)
            // Keep the original value frame when the instant is unchanged.
            // This preserves an existing TZID or floating DTSTART on unrelated
            // edits; recurrence edits also restore the series frame in
            // RecurrenceEdit when the start itself changes.
            val originalStart = original?.dateStart?.takeIf { property ->
                property.value?.hasTime() == true &&
                    runCatching { property.value.toInstant() == start }.getOrDefault(false)
            }
            val originalEnd = original?.dateEnd?.takeIf { property ->
                property.value?.hasTime() == true &&
                    runCatching { property.value.toInstant() == end }.getOrDefault(false)
            }
            if (originalStart == null) vevent.replace(DateStart(start.toDateTime()))
            if (originalEnd == null) vevent.replace(DateEnd(end.toDateTime()))
        }

        vevent.replaceOrRemove(event.location?.trim()?.takeIf(String::isNotEmpty)) { Location(it) }
        vevent.replaceOrRemove(event.notes?.trim()?.takeIf(String::isNotEmpty)) { Description(it) }
        vevent.replaceOrRemove(event.url?.trim()?.takeIf(String::isNotEmpty)) { Url(it) }
        vevent.writeCategories(event.categories)
        vevent.writeReminders(event.reminders, event.title)
        vevent.writeAppleTravelTimeMinutes(event.travelTimeMinutes)

        vevent.removeProperties(RecurrenceId::class.java)
        event.recurrenceDate?.let { date ->
            vevent.addProperty(RecurrenceId(date.toDateOnly()))
        } ?: event.recurrenceId?.let { instant ->
            vevent.addProperty(RecurrenceId(instant.toDateTime()))
        }
        event.sequence?.let { sequence -> vevent.replace(Sequence(sequence)) }

        vevent.replace(
            if (event.availability == Availability.Free) {
                Transparency.transparent()
            } else {
                Transparency.opaque()
            },
        )

        val unmatchedOriginalAttendees = vevent.attendees.toMutableList()
        val attendeesToWrite = event.attendees.map { attendee ->
            val existing = unmatchedOriginalAttendees.firstOrNull { originalAttendee ->
                attendeeAddress(originalAttendee).equals(attendee.email.normalizedAttendeeAddress(), ignoreCase = true)
            }
            if (existing == null) {
                ICalAttendee(attendee.name.takeIf { it != attendee.email }, attendee.email)
            } else {
                unmatchedOriginalAttendees.remove(existing)
                existing.copy().also { copy ->
                    val oldName = existing.commonName?.trim()?.takeIf(String::isNotEmpty)
                        ?: attendeeAddress(existing)
                    if (oldName != attendee.name) {
                        copy.commonName = attendee.name.takeIf { it != attendee.email }
                    }
                }
            }
        }
        vevent.removeProperties(ICalAttendee::class.java)
        attendeesToWrite.forEach(vevent::addProperty)

        if (!(preserveRecurrenceIfMissing && event.recurrence == null && original?.recurrenceRule != null)) {
            vevent.writeRecurrenceRule(event.recurrence)
        }
        vevent.stamp(now)
        // RANGE=THISANDFUTURE changes the meaning of the override and is not
        // represented in CalEvent. Carry it over when the recurrence id is
        // recreated from the model.
        if (!originalRecurrenceRange.isNullOrBlank() && vevent.recurrenceId != null) {
            vevent.recurrenceId.setParameter("RANGE", originalRecurrenceRange)
        }
        return vevent
    }

    // --- VTODO ----------------------------------------------------------------

    fun writeTask(task: CalTask, original: VTodo? = null, now: Instant): VTodo {
        val vtodo = original ?: VTodo().also { it.properties.clear() }

        vtodo.setUidValue(task.uid ?: task.id)
        vtodo.setSummaryValue(task.title)

        val previousReminder = original?.readReminders()?.firstOrNull()
        val originalStart = vtodo.dateStart?.copy()
        val originalStartLocal = originalStart?.value?.let { value ->
            if (!value.hasTime()) value.rawComponents?.let { LocalDate.of(it.year, it.month, it.date) }?.let { it to null }
            else value.toInstant().atZone(zone).toLocalDateTime().let { it.toLocalDate() to it.toLocalTime() }
        }
        val preserveStartOnly = original?.dateDue == null && originalStartLocal != null &&
            task.due == originalStartLocal.first && task.dueTime == originalStartLocal.second
        vtodo.removeProperties(DateStart::class.java)
        vtodo.removeProperties(DateDue::class.java)
        val startDate = task.startDate ?: originalStartLocal?.first.takeIf { preserveStartOnly }
        // RFC 5545 requires matching DTSTART/DUE value types and DUE strictly
        // later. Reading malformed external data is tolerant; writing it back
        // must not reproduce the malformed pair. A date-only start preceding a
        // timed due can safely become midnight on that same start date.
        val startTime = task.startTime ?: originalStartLocal?.second.takeIf { preserveStartOnly }
            ?: java.time.LocalTime.MIDNIGHT.takeIf {
            startDate != null && task.dueTime != null
        }
        val validStart = startDate != null && (task.due == null || preserveStartOnly || when {
            (startTime == null) != (task.dueTime == null) -> false
            startTime == null -> startDate.isBefore(task.due)
            else -> startDate.atTime(startTime).isBefore(task.due.atTime(task.dueTime))
        })
        if (startDate == null && originalStart != null && task.due == null && original?.recurrenceRule != null) {
            vtodo.addProperty(originalStart)
        } else if (validStart) startDate.let { date ->
            val value = startTime?.let { date.atTime(it).atZone(zone).toInstant().toDateTime() }
                ?: date.toDateOnly()
            vtodo.addProperty(DateStart(value))
        }
        task.due?.takeUnless { preserveStartOnly }?.let { due ->
            // RFC 5545 permits DUE or DURATION on a VTODO, never both.
            vtodo.removeProperties(DurationProperty::class.java)
            task.dueTime?.let { time ->
                val value = due.atTime(time).atZone(zone).toInstant().toDateTime()
                vtodo.addProperty(DateDue(value))
            } ?: run {
                val value = due.toDateOnly()
                vtodo.addProperty(DateDue(value))
            }
        }

        vtodo.replaceOrRemove(task.notes?.trim()?.takeIf(String::isNotEmpty)) { Description(it) }
        val existingCategories = vtodo.getProperties(Categories::class.java)
            .flatMap { it.values.orEmpty() }
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (original == null || task.category != existingCategories.firstOrNull()) {
            // Calino edits one task category; Nextcloud can store several tags.
            // Keep every tag beyond the one Calino exposes.
            vtodo.writeCategories(listOfNotNull(task.category) + existingCategories.drop(1))
        }
        val parentId = task.parentTaskId?.trim()?.takeIf(String::isNotEmpty)
        val parentLinks = vtodo.relatedTo.filter {
            it.relationshipType == null || it.relationshipType == RelationshipType.PARENT
        }
        if (parentLinks.firstOrNull()?.value != parentId || parentLinks.size > 1) {
            parentLinks.forEach(vtodo::removeProperty)
            parentId?.let { vtodo.addRelatedTo(RelatedTo(it)) }
        }
        // Per-occurrence VALARM is deliberately unsupported. A generated
        // occurrence inherits the master's reminder for display, but must not
        // copy it into a detached VTODO when completed or edited.
        if (task.recurrenceId == null && task.recurrenceDate == null) {
            vtodo.writeTaskReminder(task.reminder, previousReminder, task.title)
        }
        vtodo.removeProperties(Priority::class.java)
        vtodo.addProperty(Priority(task.priority.coerceIn(0, 9)))
        vtodo.removeProperties(RecurrenceId::class.java)
        task.recurrenceDate?.let { vtodo.addProperty(RecurrenceId(it.toDateOnly())) }
            ?: task.recurrenceId?.let { vtodo.addProperty(RecurrenceId(it.toDateTime())) }
        if (task.recurrenceChanged || task.recurrence != null || original?.recurrenceRule == null) {
            vtodo.removeProperties(RecurrenceRule::class.java)
            if (task.recurrenceId == null && task.recurrenceDate == null) {
                task.recurrence?.trim()?.takeIf(String::isNotEmpty)?.let(::parseRecurrenceRule)?.let(vtodo::addProperty)
            }
        }
        vtodo.writeCompletion(task, now)
        vtodo.stamp(now)
        return vtodo
    }

    /**
     * Writes STATUS, PERCENT-COMPLETE and COMPLETED as one consistent set.
     *
     * `CANCELLED` is checked before anything else, and the direction of the
     * guard follows this app's reader rather than the web app's. `ICalMapper`
     * reads `done` from `STATUS:COMPLETED` or a percentage of 100 only, so a
     * cancelled task arrives as **not done**. Writing that back off `done` alone
     * would turn another client's cancellation into `NEEDS-ACTION` -- quietly
     * resurrecting a task somebody had abandoned.
     *
     * So a cancelled task keeps its status unless the person actually completes
     * it, which is the one signal that could not have come from the round trip.
     */
    private fun VTodo.writeCompletion(task: CalTask, now: Instant) {
        val existing = status?.value?.uppercase()
        if (existing == "CANCELLED" && !task.done && task.status == "CANCELLED") return

        removeProperties(Status::class.java)
        removeProperties(PercentComplete::class.java)
        val explicitReopen = !task.done && task.status.equals("COMPLETED", ignoreCase = true)
        val percent = when {
            task.done -> 100
            explicitReopen -> 0
            else -> task.percentComplete
        }.coerceIn(0, 100)
        if (percent > 0) addProperty(PercentComplete(percent))
        if (percent == 100 || task.done) {
            addProperty(Status.completed())
            // Keep the moment the task was first completed; only stamp a new one
            // when there is none, so reopening and re-closing is not required to
            // rewrite history.
            removeProperties(Completed::class.java)
            addProperty(Completed(Date.from(task.completedAt ?: now)))
        } else {
            val preserved = task.status.uppercase().takeIf { !explicitReopen && it in setOf("NEEDS-ACTION", "IN-PROCESS", "CANCELLED") }
            addProperty(Status(preserved ?: if (percent > 0) "IN-PROCESS" else "NEEDS-ACTION"))
            removeProperties(Completed::class.java)
        }
    }

    // --- VJOURNAL -------------------------------------------------------------

    fun writeJournal(entry: JournalEntry, original: VJournal? = null, now: Instant): VJournal {
        val vjournal = original ?: VJournal().also { it.properties.clear() }

        vjournal.setUidValue(entry.uid ?: entry.id)
        vjournal.setSummaryValue(entry.title)
        // A journal entry is a dated note, so DTSTART is written date-only --
        // matching how ICalMapper.mapJournal reads it back.
        vjournal.replace(DateStart(entry.date.toDateOnly()))

        // The reader joins several DESCRIPTIONs into one body; writing it back as
        // a single property is the honest inverse, since the split points are not
        // preserved anywhere in the model.
        vjournal.removeProperties(Description::class.java)
        entry.body.trim().takeIf(String::isNotEmpty)?.let { vjournal.addProperty(Description(it)) }

        vjournal.stamp(now)
        return vjournal
    }

    // --- serialisation --------------------------------------------------------

    /**
     * Wraps components in a fresh VCALENDAR.
     *
     * Only for a resource Calino is creating. An edit to an existing resource
     * goes through [ICalPatcher] instead, so the origin server's own `PRODID`
     * and `VTIMEZONE` definitions are not replaced with ours.
     */
    fun buildCalendar(components: List<ICalComponent>): String {
        val calendar = ICalendar()
        calendar.productId = biweekly.property.ProductId(ProductId)
        components.forEach { calendar.addComponent(it) }
        return write(calendar)
    }

    private fun ICalComponent.stamp(now: Instant) {
        replace(DateTimeStamp(Date.from(now)))
        replace(LastModified(Date.from(now)))
        if (getProperty(Created::class.java) == null) addProperty(Created(Date.from(now)))
        // SEQUENCE is written but never incremented here. The caller owns it: a
        // bump on every save makes a conflict out of an edit that changed
        // nothing, which is a bug the web app shipped and had to undo.
        if (getProperty(Sequence::class.java) == null) addProperty(Sequence(0))
    }

    private fun ICalComponent.writeCategories(categories: List<String>) {
        removeProperties(Categories::class.java)
        val values = categories.map(String::trim).filter(String::isNotEmpty)
        if (values.isEmpty()) return
        addProperty(Categories(values))
    }

    private fun attendeeAddress(attendee: ICalAttendee): String? =
        attendee.email?.trim()?.takeIf(String::isNotEmpty)?.normalizedAttendeeAddress()
            ?: attendee.uri?.trim()?.takeIf(String::isNotEmpty)?.normalizedAttendeeAddress()

    private fun String.normalizedAttendeeAddress(): String =
        removePrefix("mailto:").removePrefix("MAILTO:").trim()

    /**
     * Replaces RRULE, or removes it when the record no longer recurs.
     *
     * The rule is round-tripped through the parser rather than assembled from a
     * builder: [CalEvent.recurrence] holds whatever the server sent, which is a
     * far wider language than the editor can produce, and re-parsing is the only
     * way to write back a rule the app does not itself model.
     */
    private fun VEvent.writeRecurrenceRule(rule: String?) {
        removeProperties(RecurrenceRule::class.java)
        val parsed = rule?.trim()?.takeIf(String::isNotEmpty)?.let(::parseRecurrenceRule) ?: return
        addProperty(parsed)
    }

    private fun Instant.toDateTime() = ICalDate(Date.from(this), true)

    /**
     * A `VALUE=DATE` value built from the written digits.
     *
     * Constructed from [DateTimeComponents] rather than from a [Date] because a
     * date-only value has no zone: handing biweekly an instant makes it anchor
     * the value at midnight in the JVM's default zone, which slides the date by
     * a day either side of UTC. This is the inverse of the same care taken in
     * `ICalMapper.toLocalDateOnly`.
     */
    private fun LocalDate.toDateOnly() =
        ICalDate(DateTimeComponents(year, monthValue, dayOfMonth, 0, 0, 0, false), false)

    companion object {
        const val ProductId = "-//Calino//Calino Android//EN"
        private const val DefaultDurationMinutes = 60

        /** Parses an `RRULE` value, returning null if the server sent nonsense. */
        fun parseRecurrenceRule(rule: String): RecurrenceRule? = runCatching {
            val probe = buildString {
                append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Calino//probe//EN\r\n")
                append("BEGIN:VEVENT\r\nUID:probe\r\nDTSTART:20200101T000000Z\r\n")
                append("RRULE:").append(rule.removePrefix("RRULE:")).append("\r\n")
                append("END:VEVENT\r\nEND:VCALENDAR\r\n")
            }
            Biweekly.parse(probe).first()?.events?.firstOrNull()?.recurrenceRule
        }.getOrNull()

        internal fun write(calendar: ICalendar): String =
            Biweekly.write(calendar).register(ExplicitUriConferenceScribe()).go()
    }
}

/** Sets a single-valued property, replacing any the component already carries. */
private fun <T : biweekly.property.ICalProperty> ICalComponent.replace(property: T) {
    removeProperties(property.javaClass)
    addProperty(property)
}

/**
 * Writes a property from [value], or removes it entirely when the value is gone.
 *
 * The removal half is the point. Clearing a field in the editor has to reach the
 * server as a deleted property, not as an unchanged one.
 */
private inline fun <T : biweekly.property.ICalProperty> ICalComponent.replaceOrRemove(
    value: String?,
    build: (String) -> T,
) {
    val property = value?.let(build)
    if (property == null) {
        // The class to remove is only knowable from a built instance, so build a
        // throwaway one with a placeholder to name the type.
        removeProperties(build("").javaClass)
    } else {
        replace(property)
    }
}

private fun ICalComponent.setUidValue(value: String) {
    removeProperties(biweekly.property.Uid::class.java)
    addProperty(biweekly.property.Uid(value))
}

private fun ICalComponent.setSummaryValue(value: String) {
    removeProperties(Summary::class.java)
    addProperty(Summary(value))
}

/**
 * RFC 7986 requires `VALUE=URI` on `CONFERENCE`, but biweekly treats URI as
 * the property's default type and omits it. Declaring no default makes the
 * parameter explicit, so a foreign client's conference line survives a patch.
 */
private class ExplicitUriConferenceScribe : ConferenceScribe() {
    override fun _defaultDataType(version: ICalVersion?): ICalDataType? = null

    override fun _dataType(property: Conference?, version: ICalVersion?): ICalDataType =
        if (property?.uri != null) ICalDataType.URI else ICalDataType.TEXT
}
