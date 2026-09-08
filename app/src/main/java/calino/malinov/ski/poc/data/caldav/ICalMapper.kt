package calino.malinov.ski.poc.data.caldav

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.component.VJournal
import biweekly.component.VTodo
import biweekly.property.DateOrDateTimeProperty
import calino.malinov.ski.poc.data.model.Attendee
import calino.malinov.ski.poc.data.model.Availability
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turns iCalendar text from a CalDAV response into the app's models.
 *
 * The traps handled here were all found the hard way in the Calino web app;
 * each one is called out at its site rather than left to be rediscovered.
 */
class ICalMapper(private val zone: ZoneId = ZoneId.systemDefault()) {

    data class Parsed(
        val events: List<CalEvent> = emptyList(),
        val tasks: List<CalTask> = emptyList(),
        val journals: List<JournalEntry> = emptyList(),
        /** True when a VEVENT still carried an RRULE, i.e. expand was ignored. */
        val sawUnexpandedRecurrence: Boolean = false,
    )

    fun parse(
        icalText: String,
        calendarId: String,
        color: Long,
        href: String,
        etag: String? = null,
    ): Parsed {
        val calendars = parseCalendars(icalText) ?: return Parsed()
        val events = mutableListOf<CalEvent>()
        val tasks = mutableListOf<CalTask>()
        val journals = mutableListOf<JournalEntry>()
        var unexpanded = false

        calendars.forEach { calendar ->
            calendar.events.forEach { vevent ->
                if (vevent.recurrenceRule != null) unexpanded = true
                runCatching { mapEvent(vevent, calendarId, color, href, etag) }
                    .getOrNull()?.let(events::add)
            }
            calendar.todos.forEach { vtodo ->
                runCatching { mapTask(vtodo, color, href) }.getOrNull()?.let(tasks::add)
            }
            calendar.journals.forEach { vjournal ->
                runCatching { mapJournal(vjournal, href) }.getOrNull()?.let(journals::add)
            }
        }
        return Parsed(events, tasks, journals, unexpanded)
    }

    /**
     * Parses possibly-concatenated VCALENDAR blocks.
     *
     * A leading UTF-8 BOM is stripped first. With the BOM in place the parser
     * never matches `BEGIN:VCALENDAR` and silently yields zero components --
     * an empty calendar rather than an error, which is the worst failure shape.
     */
    private fun parseCalendars(icalText: String): List<ICalendar>? {
        val cleaned = icalText.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return null
        return runCatching { Biweekly.parse(cleaned).all() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    // --- VEVENT ---------------------------------------------------------------

    private fun mapEvent(
        vevent: VEvent,
        calendarId: String,
        color: Long,
        href: String,
        etag: String?,
    ): CalEvent? {
        val uid = vevent.uid?.value ?: return null
        val start = vevent.dateStart ?: return null
        val allDay = start.isDateOnly()

        // An expanded instance is identified by its RECURRENCE-ID, so every
        // occurrence of a series gets a distinct, stable id.
        val recurrenceId = vevent.recurrenceId?.value?.let { formatInstantId(it.toInstant()) }
        val id = if (recurrenceId != null) "$uid-$recurrenceId" else uid

        val summary = vevent.summary?.value?.trim().orEmpty().ifEmpty { "(no title)" }

        return if (allDay) {
            val startDate = start.toLocalDateOnly() ?: return null
            // DTEND is exclusive: a span of 11th -> 14th covers the 11th, 12th
            // and 13th. Storing the inclusive last day here keeps every
            // renderer from having to remember the off-by-one.
            val lastDay = vevent.dateEnd?.toLocalDateOnly()
                ?.minusDays(1)
                ?.takeIf { it.isAfter(startDate) }
            CalEvent(
                id = id,
                title = summary,
                color = color,
                start = null,
                durationMinutes = null,
                allDay = true,
                date = startDate,
                endDate = lastDay,
                calendarId = calendarId,
                location = vevent.location?.value?.trim()?.takeIf(String::isNotEmpty),
                notes = vevent.description?.value?.trim()?.takeIf(String::isNotEmpty),
                attendees = mapAttendees(vevent),
                categories = readCategories(vevent),
                uid = uid,
                href = href,
                etag = etag,
            )
        } else {
            val startLocal = toLocalDateTime(start.value.toInstant())
            val endInstant = vevent.dateEnd?.value?.toInstant()
            val duration = when {
                endInstant != null -> Duration.between(start.value.toInstant(), endInstant).toMinutes().toInt()
                vevent.duration?.value != null -> vevent.duration.value.toMillis().toInt() / 60_000
                else -> DefaultDurationMinutes
            }.coerceAtLeast(0)
            CalEvent(
                id = id,
                title = summary,
                color = color,
                start = startLocal,
                durationMinutes = duration.takeIf { it > 0 } ?: DefaultDurationMinutes,
                allDay = false,
                date = null,
                calendarId = calendarId,
                location = vevent.location?.value?.trim()?.takeIf(String::isNotEmpty),
                notes = vevent.description?.value?.trim()?.takeIf(String::isNotEmpty),
                attendees = mapAttendees(vevent),
                categories = readCategories(vevent),
                availability = if (vevent.transparency?.isTransparent == true) {
                    Availability.Free
                } else {
                    Availability.Busy
                },
                uid = uid,
                href = href,
                etag = etag,
            )
        }
    }

    private fun mapAttendees(vevent: VEvent): List<Attendee> =
        vevent.attendees.mapNotNull { attendee ->
            val email = attendee.email?.trim()
                ?: attendee.uri?.trim()?.removePrefix("mailto:")?.removePrefix("MAILTO:")
                ?: return@mapNotNull null
            Attendee(name = attendee.commonName?.trim()?.takeIf(String::isNotEmpty) ?: email, email = email)
        }

    /**
     * Reads every CATEGORIES property and every value within each.
     *
     * Both loops are needed: a file may repeat the property, and a single
     * property may hold several comma-separated values. Reading only the first
     * of either silently drops categories.
     */
    private fun readCategories(component: biweekly.component.ICalComponent): List<String> =
        component.getProperties(biweekly.property.Categories::class.java)
            .flatMap { it.values.orEmpty() }
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    // --- VTODO ----------------------------------------------------------------

    private fun mapTask(vtodo: VTodo, color: Long, href: String): CalTask? {
        val uid = vtodo.uid?.value ?: return null
        val summary = vtodo.summary?.value?.trim().orEmpty().ifEmpty { "(no title)" }

        val start = vtodo.dateStart
        val due = vtodo.dateDue

        // DTSTART decides the value type when both are present. Clients
        // routinely emit a date-time DTSTART alongside a date-only DUE; reading
        // the type off DUE flips a timed task to all-day.
        val typeCarrier = start ?: due
        val dateOnly = typeCarrier?.isDateOnly() ?: true

        val anchor = due ?: start ?: return null
        val dueDate: LocalDate
        val dueTime: LocalTime?
        if (dateOnly) {
            dueDate = anchor.toLocalDateOnly() ?: return null
            dueTime = null
        } else {
            val local = toLocalDateTime(anchor.value.toInstant())
            dueDate = local.toLocalDate()
            dueTime = local.toLocalTime()
        }

        val status = vtodo.status?.value?.uppercase()
        val percent = vtodo.percentComplete?.value ?: 0
        val done = status == "COMPLETED" || percent >= 100

        return CalTask(
            id = uid,
            title = summary,
            color = color,
            due = dueDate,
            dueTime = dueTime,
            done = done,
            category = readCategories(vtodo).firstOrNull(),
            notes = vtodo.description?.value?.trim()?.takeIf(String::isNotEmpty),
            uid = uid,
            href = href,
        )
    }

    // --- VJOURNAL -------------------------------------------------------------

    private fun mapJournal(vjournal: VJournal, href: String): JournalEntry? {
        val uid = vjournal.uid?.value ?: return null
        // A journal entry is a dated note: DTSTART is read as a date even when
        // the server sends a date-time.
        val date = vjournal.dateStart?.let { property ->
            property.toLocalDateOnly() ?: toLocalDateTime(property.value.toInstant()).toLocalDate()
        } ?: return null
        return JournalEntry(
            id = uid,
            date = date,
            title = vjournal.summary?.value?.trim().orEmpty(),
            // A VJOURNAL may carry several DESCRIPTION properties; the entry
            // body is all of them, in order.
            body = vjournal.descriptions
                .mapNotNull { it.value?.trim()?.takeIf(String::isNotEmpty) }
                .joinToString("\n\n"),
            uid = uid,
            href = href,
        )
    }

    // --- date handling --------------------------------------------------------

    /**
     * Converts an absolute instant to local wall-clock time.
     *
     * This is its own named function, and directly tested, because it is where
     * the day-bucketing bug lives. Server-side expansion returns instances in
     * UTC: a 23:00 Europe/Copenhagen event arrives as `20260907T210000Z`.
     * Reading the date off the UTC value puts late-evening events on the
     * following day and, for a weekday series, onto the weekend.
     */
    fun toLocalDateTime(instant: Instant): LocalDateTime =
        LocalDateTime.ofInstant(instant, zone)

    private companion object {
        const val DefaultDurationMinutes = 60
        fun formatInstantId(instant: Instant): String = instant.toString()
    }
}

/**
 * Whether a date property is date-only (`VALUE=DATE`) rather than a date-time.
 * This is what makes an event all-day.
 */
private fun DateOrDateTimeProperty.isDateOnly(): Boolean = value?.hasTime() == false

/**
 * The calendar date of a date-only property.
 *
 * Read from the raw `yyyyMMdd` components rather than by converting the parsed
 * instant. A `VALUE=DATE` has no zone, and biweekly anchors it at midnight in
 * the JVM's default zone; converting that instant through any other zone slides
 * the date a day in one direction or the other. The written digits are the only
 * zone-free truth available.
 */
private fun DateOrDateTimeProperty.toLocalDateOnly(): LocalDate? {
    val date = value ?: return null
    date.rawComponents?.let { raw ->
        return runCatching { LocalDate.of(raw.year, raw.month, raw.date) }.getOrNull()
    }
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
}
