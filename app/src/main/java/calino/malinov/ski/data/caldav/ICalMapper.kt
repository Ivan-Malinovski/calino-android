package calino.malinov.ski.data.caldav

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.component.VJournal
import biweekly.component.VTodo
import biweekly.property.DateOrDateTimeProperty
import biweekly.property.ExceptionDates
import biweekly.property.ICalProperty
import biweekly.util.ICalDate
import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.JournalEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

/**
 * Turns iCalendar text from a CalDAV response into the app's models.
 *
 * The traps handled here were all found the hard way in the Calino web app;
 * each one is called out at its site rather than left to be rediscovered.
 *
 * Alarms are read, but only the ones [Reminder] can state exactly -- see
 * `ICalAlarms.kt`. Whole-minute repeats and task `RELATED=END` alarms are
 * modelled. Absolute triggers, sub-minute repeats, and actions this app cannot
 * present stay on the raw resource untouched.
 */
class ICalMapper(private val zone: ZoneId = ZoneId.systemDefault()) {

    data class Parsed(
        val events: List<CalEvent> = emptyList(),
        val tasks: List<CalTask> = emptyList(),
        val journals: List<JournalEntry> = emptyList(),
        /** Resource hrefs whose iCalendar text could not be parsed. */
        val failedResourceHrefs: List<String> = emptyList(),
    )

    /**
     * Maps a whole collection's worth of resources.
     *
     * Fetching and mapping are separate on purpose: [resources] are the
     * server's own text, so the same list can be mapped again later against a
     * different window -- which is what a cached copy does after the window
     * has moved with the calendar date.
     *
     * A resource that will not parse is reported in [Parsed.failedResourceHrefs]
     * and skipped rather than failing the collection; one malformed record must
     * not empty a calendar.
     */
    fun mapAll(
        resources: List<CalendarResource>,
        calendarId: String,
        color: Long,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): Parsed {
        val events = mutableListOf<CalEvent>()
        val tasks = mutableListOf<CalTask>()
        val journals = mutableListOf<JournalEntry>()
        val failures = mutableListOf<String>()
        resources.forEach { resource ->
            // [parse] intentionally returns an empty Parsed for unreadable
            // text so one bad resource cannot crash a collection. At the
            // collection boundary, however, that empty result is not enough:
            // incremental sync must not advance past a resource that was
            // never safely interpreted.
            if (parseCalendars(resource.ics) == null) {
                failures += resource.href
                return@forEach
            }
            val parsed = runCatching {
                parse(
                    icalText = resource.ics,
                    calendarId = calendarId,
                    color = color,
                    href = resource.href,
                    etag = resource.etag,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                )
            }.getOrElse {
                // A resource that parsed as XML/iCalendar but could not be
                // mapped (for example a broken RRULE or invalid date value)
                // is still an unsafe replacement for the cached resource.
                // Report it to the collection owner so it can retain the last
                // good bytes and withhold the cursor.
                failures += resource.href
                return@forEach
            }
            events += parsed.events
            tasks += parsed.tasks
            journals += parsed.journals
        }
        return Parsed(events, tasks, journals, failures)
    }

    /**
     * @param windowStart first day the caller wants occurrences for.
     * @param windowEnd last day, inclusive.
     *
     * The window is not decoration. A rule like `FREQ=DAILY` with no `UNTIL`
     * or `COUNT` is infinite, so expansion has to be bounded by something the
     * caller chooses.
     */
    fun parse(
        icalText: String,
        calendarId: String,
        color: Long,
        href: String,
        etag: String? = null,
        windowStart: LocalDate = LocalDate.MIN,
        windowEnd: LocalDate = LocalDate.MAX,
    ): Parsed {
        val calendars = parseCalendars(icalText) ?: return Parsed()
        val events = mutableListOf<CalEvent>()
        val tasks = mutableListOf<CalTask>()
        val journals = mutableListOf<JournalEntry>()

        calendars.forEach { calendar ->
            events += mapEvents(calendar, calendarId, color, href, etag, windowStart, windowEnd)
            tasks += mapTasks(calendar, calendarId, color, href, etag, windowStart, windowEnd)
            calendar.journals.forEach { vjournal ->
                mapJournal(vjournal, href, etag)?.let(journals::add)
            }
        }
        return Parsed(events, tasks, journals)
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

    /**
     * Expands every VEVENT series in one calendar object.
     *
     * Recurrence is expanded here rather than by the server. `<c:expand>` is not
     * dependable: sabre (Baikal) throws a 500 while expanding any collection
     * whose `calendar-timezone` property holds a bare zone id instead of a
     * VCALENDAR, and other servers ignore the element entirely. Expanding on
     * the client is the same work on every server.
     *
     * Grouping by UID is what makes overrides possible: one `.ics` resource
     * carries the master VEVENT and its `RECURRENCE-ID` detached instances
     * together, and neither can be interpreted without the other.
     */
    private fun mapEvents(
        calendar: ICalendar,
        calendarId: String,
        color: Long,
        href: String,
        etag: String?,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<CalEvent> {
        val out = mutableListOf<CalEvent>()
        calendar.events
            .filter { it.uid?.value != null && it.dateStart != null }
            .groupBy { it.uid.value }
            .forEach { (_, group) ->
                val master = group.firstOrNull { it.recurrenceId == null }
                val overrides = group.filter { it.recurrenceId != null }

                // An override wins over everything, including an EXDATE naming
                // the same instant (RFC 5545 3.8.5.1): moving an occurrence and
                // cancelling it are different acts, and the move is the later
                // statement of intent.
                overrides.forEach { override ->
                    mapEvent(override, calendarId, color, href, etag)
                        ?.takeIf { it.withinWindow(windowStart, windowEnd) }
                        ?.let(out::add)
                }

                if (master == null) return@forEach
                if (master.recurrenceRule == null && master.recurrenceDates.isEmpty()) {
                    mapEvent(master, calendarId, color, href, etag)?.let(out::add)
                    return@forEach
                }
                out += expandSeries(
                    calendar, master, overrides, calendarId, color, href, etag,
                    windowStart, windowEnd,
                )
            }
        return out
    }

    private fun expandSeries(
        calendar: ICalendar,
        master: VEvent,
        overrides: List<VEvent>,
        calendarId: String,
        color: Long,
        href: String,
        etag: String?,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<CalEvent> {
        val base = mapEvent(master, calendarId, color, href, etag) ?: return emptyList()
        val timeZone = timeZoneFor(calendar, master.dateStart)
        val iterationZone = timeZone.toZoneId()

        // EXDATEs are lifted off the component and applied below instead of by
        // the iterator, so that an EXDATE which matches no instance exactly can
        // still be honoured. See applyExceptions.
        val exceptions = master.getProperties(ExceptionDates::class.java)
            .flatMap { it.values.orEmpty() }
            .mapNotNull { runCatching { it.toInstant() }.getOrNull() }
        master.removeProperties(ExceptionDates::class.java)

        val from = windowStart.atStartOfDay(iterationZone).toInstant()
        val until = windowEnd.plusDays(1).atStartOfDay(iterationZone).toInstant()

        val instants = mutableListOf<Instant>()
        val iterator = master.getDateIterator(timeZone)
        // advanceTo skips a long-running series forward without materialising
        // the years before the window.
        iterator.advanceTo(Date.from(from))
        while (iterator.hasNext() && instants.size < MaxOccurrencesPerSeries) {
            val instant = iterator.next().toInstant()
            if (instant >= until) break
            instants += instant
        }

        val overrideInstants = overrides
            .mapNotNull { runCatching { it.recurrenceId?.value?.toInstant() }.getOrNull() }
            .toSet()

        val seriesStart = master.dateStart.value.toInstant()
        return applyExceptions(instants, exceptions, iterationZone, from, until)
            .asSequence()
            .filterNot { it in overrideInstants }
            .map { instant -> base.occurrenceAt(instant, seriesStart, iterationZone) }
            .toList()
    }

    /**
     * Removes the EXDATEd occurrences, exact matches first.
     *
     * RFC 5545 says an EXDATE cancels the instance whose start it equals, and
     * that is tried first. But real files carry EXDATEs written at the wrong
     * time of day -- the maintainer's own weekday series is stored at 06:00Z
     * with eight of its fourteen EXDATEs stamped `T000000Z` -- and under a
     * strict reading those cancelled days reappear on the calendar. So an
     * EXDATE inside the window that cancelled nothing exactly is treated as
     * naming a whole day instead.
     */
    private fun applyExceptions(
        instants: List<Instant>,
        exceptions: List<Instant>,
        iterationZone: ZoneId,
        from: Instant,
        until: Instant,
    ): List<Instant> {
        if (exceptions.isEmpty()) return instants
        val exact = exceptions.toSet()
        val generated = instants.toSet()
        val unmatchedDays = exceptions
            // Only judge an EXDATE the window could have shown. One outside it
            // matched nothing here for the trivial reason that nothing was
            // generated there.
            .filter { it !in generated && it >= from && it < until }
            .map { it.atZone(iterationZone).toLocalDate() }
            .toSet()
        return instants.filterNot {
            it in exact || it.atZone(iterationZone).toLocalDate() in unmatchedDays
        }
    }

    /** The occurrence of a series that starts at [instant]. */
    private fun CalEvent.occurrenceAt(
        instant: Instant,
        seriesStart: Instant,
        iterationZone: ZoneId,
    ): CalEvent {
        val id = occurrenceId(uid ?: id, instant)
        return if (allDay) {
            // The iterator yields each occurrence's start; a multi-day all-day
            // event keeps its length by carrying the same span forward.
            val day = instant.atZone(iterationZone).toLocalDate()
            val span = date?.let { start -> endDate?.let { java.time.temporal.ChronoUnit.DAYS.between(start, it) } }
            copy(
                id = id,
                date = day,
                endDate = span?.let { day.plusDays(it) },
                recurrenceDate = recurrenceDate ?: day,
            )
        } else {
            copy(id = id, start = toLocalDateTime(instant), recurrenceId = recurrenceId ?: instant)
        }
    }

    private fun CalEvent.withinWindow(windowStart: LocalDate, windowEnd: LocalDate): Boolean {
        val day = start?.toLocalDate() ?: date ?: return true
        val last = endDate ?: day
        return !day.isAfter(windowEnd) && !last.isBefore(windowStart)
    }

    /**
     * The zone a component's times are written in.
     *
     * A `TZID` resolves through the calendar's own VTIMEZONE definitions;
     * anything else -- UTC, or a floating time -- falls back to the app's zone,
     * which is what the rest of the mapper reads times in.
     */
    private fun timeZoneFor(calendar: ICalendar, property: ICalProperty?): TimeZone =
        property?.let { calendar.timezoneInfo.getTimezone(it)?.timeZone }
            ?: TimeZone.getTimeZone(zone)

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

        // A detached instance is identified by the occurrence it replaces, not
        // by its own (possibly moved) start, so its id stays stable across a
        // reschedule.
        val recurrenceInstant = vevent.recurrenceId?.value?.toInstant()
        val id = if (recurrenceInstant != null) occurrenceId(uid, recurrenceInstant) else uid
        val recurrenceDate = vevent.recurrenceId?.value
            ?.takeIf { !it.hasTime() }
            ?.rawComponents
            ?.let { runCatching { LocalDate.of(it.year, it.month, it.date) }.getOrNull() }

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
                reminders = vevent.readReminders(),
                travelTimeMinutes = vevent.readAppleTravelTimeMinutes(),
                uid = uid,
                href = href,
                etag = etag,
                recurrenceId = recurrenceInstant,
                recurrenceDate = recurrenceDate,
                sequence = vevent.sequence?.value,
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
                reminders = vevent.readReminders(),
                travelTimeMinutes = vevent.readAppleTravelTimeMinutes(),
                uid = uid,
                href = href,
                etag = etag,
                recurrenceId = recurrenceInstant,
                recurrenceDate = recurrenceDate,
                sequence = vevent.sequence?.value,
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

    private fun mapTasks(
        calendar: ICalendar,
        calendarId: String,
        color: Long,
        href: String,
        etag: String?,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<CalTask> = calendar.todos.filter { it.uid?.value != null }.groupBy { it.uid.value }.flatMap { (_, group) ->
        val master = group.firstOrNull { it.recurrenceId == null }
        val overrides = group.filter { it.recurrenceId != null }
        val detached = overrides.mapNotNull { mapTask(it, calendarId, color, href, etag) }
        if (master == null) return@flatMap detached
        if (master.recurrenceRule == null && master.recurrenceDates.isEmpty()) {
            return@flatMap detached + listOfNotNull(mapTask(master, calendarId, color, href, etag))
        }
        val base = mapTask(master, calendarId, color, href, etag) ?: return@flatMap detached
        val carrier = master.dateStart ?: master.dateDue ?: return@flatMap detached
        val tz = timeZoneFor(calendar, carrier)
        val iterationZone = tz.toZoneId()
        // Public parse callers historically use MIN/MAX for an unbounded
        // single-resource read. Those sentinels cannot be converted to an
        // Instant (MAX.plusDays(1) overflows), so anchor such reads at the
        // VTODO and retain the ordinary hard occurrence cap.
        val anchorDay = if (carrier.isDateOnly()) {
            carrier.toLocalDateOnly() ?: return@flatMap detached
        } else {
            carrier.value.toInstant().atZone(iterationZone).toLocalDate()
        }
        val effectiveStart = if (windowStart == LocalDate.MIN) anchorDay else windowStart
        val effectiveEnd = if (windowEnd == LocalDate.MAX) effectiveStart.plusYears(10) else windowEnd
        val from = effectiveStart.atStartOfDay(iterationZone).toInstant()
        val until = effectiveEnd.plusDays(1).atStartOfDay(iterationZone).toInstant()
        val dueOffset = if (
            !carrier.isDateOnly() && master.dateStart?.value?.hasTime() == true && master.dateDue?.value?.hasTime() == true
        ) {
            Duration.between(master.dateStart.value.toInstant(), master.dateDue.value.toInstant())
        } else {
            Duration.ZERO
        }
        val overrideKeys = overrides.mapNotNull { it.recurrenceId?.value?.let(::recurrenceKey) }.toSet()
        val exceptionKeys = master.getProperties(ExceptionDates::class.java)
            .flatMap { it.values.orEmpty() }.map(::recurrenceKey).toSet()
        val generated = mutableListOf<CalTask>()
        val iterator = seriesIterationComponent(master, carrier).getDateIterator(tz)
        iterator.advanceTo(Date.from(from))
        while (iterator.hasNext() && generated.size < MaxOccurrencesPerSeries) {
            val occurrence = iterator.next()
            val instant = occurrence.toInstant()
            if (instant >= until) break
            val key = if (carrier.isDateOnly()) instant.atZone(iterationZone).toLocalDate().toString() else instant.toString()
            if (key in overrideKeys || key in exceptionKeys) continue
            val day = instant.atZone(iterationZone).toLocalDate()
            val time = if (carrier.isDateOnly()) null else instant.atZone(zone).toLocalTime()
            val dueLocal = if (carrier.isDateOnly()) null else toLocalDateTime(instant.plus(dueOffset))
            generated += base.copy(
                id = taskOccurrenceId(base.uid ?: base.id, if (carrier.isDateOnly()) day else null, instant),
                due = dueLocal?.toLocalDate() ?: day,
                dueTime = dueLocal?.toLocalTime(),
                startDate = master.dateStart?.let { day },
                startTime = master.dateStart?.takeUnless { carrier.isDateOnly() }?.let { time },
                recurrenceDate = if (carrier.isDateOnly()) day else null,
                recurrenceId = if (carrier.isDateOnly()) null else instant,
                recurrenceScope = calino.malinov.ski.data.model.RecurrenceEditScope.This,
            )
        }
        detached + generated
    }

    /**
     * The component biweekly may iterate for a recurring VTODO.
     *
     * `getDateIterator` reads DTSTART and nothing else, but RFC 5545 lets a
     * VTODO carry only DUE -- which is what tasks.org and other task clients
     * emit. Without this, such a series produced no occurrences at all and the
     * task vanished from every surface. The copy exists purely to feed the
     * iterator; the mapped fields all come from the real master.
     */
    private fun seriesIterationComponent(master: VTodo, carrier: DateOrDateTimeProperty): VTodo {
        if (master.dateStart != null) return master
        val copy = master.copy()
        copy.dateStart = biweekly.property.DateStart(carrier.value).also { start ->
            carrier.getParameter("TZID")?.let { start.setParameter("TZID", it) }
        }
        return copy
    }

    private fun recurrenceKey(value: ICalDate): String = if (value.hasTime()) {
        value.toInstant().toString()
    } else {
        value.rawComponents?.let { LocalDate.of(it.year, it.month, it.date).toString() }
            ?: value.toInstant().atZone(zone).toLocalDate().toString()
    }

    private fun mapTask(vtodo: VTodo, calendarId: String, color: Long, href: String, etag: String?): CalTask? {
        val uid = vtodo.uid?.value ?: return null
        val summary = vtodo.summary?.value?.trim().orEmpty().ifEmpty { "(no title)" }

        val start = vtodo.dateStart
        val due = vtodo.dateDue

        val anchor = due ?: start
        val dueDate: LocalDate?
        val dueTime: LocalTime?
        if (anchor == null) {
            dueDate = null
            dueTime = null
        } else if (anchor.isDateOnly()) {
            dueDate = anchor.toLocalDateOnly() ?: return null
            dueTime = null
        } else {
            val local = toLocalDateTime(anchor.value.toInstant())
            dueDate = local.toLocalDate()
            dueTime = local.toLocalTime()
        }
        val startLocal = start?.let { property ->
            if (property.isDateOnly()) property.toLocalDateOnly()?.let { it to null }
            else toLocalDateTime(property.value.toInstant()).let { it.toLocalDate() to it.toLocalTime() }
        }

        val status = vtodo.status?.value?.uppercase()
        val percent = vtodo.percentComplete?.value ?: 0
        val done = status == "COMPLETED" || percent >= 100
        val recurrenceDate = vtodo.recurrenceId?.takeIf { !it.value.hasTime() }?.let { rid ->
            rid.value.rawComponents?.let { LocalDate.of(it.year, it.month, it.date) }
                ?: rid.value.toInstant().atZone(zone).toLocalDate()
        }
        val recurrenceInstant = vtodo.recurrenceId?.takeIf { it.value.hasTime() }?.value?.toInstant()

        return CalTask(
            id = if (recurrenceDate != null || recurrenceInstant != null) {
                taskOccurrenceId(uid, recurrenceDate, recurrenceInstant)
            } else {
                uid
            },
            title = summary,
            color = color,
            due = dueDate,
            dueTime = dueTime,
            startDate = startLocal?.first,
            startTime = startLocal?.second,
            done = done,
            priority = (vtodo.priority?.value ?: 0).coerceIn(0, 9),
            percentComplete = percent.coerceIn(0, 100),
            status = status ?: if (done) "COMPLETED" else "NEEDS-ACTION",
            completedAt = vtodo.completed?.value?.toInstant(),
            category = readCategories(vtodo).firstOrNull(),
            notes = vtodo.description?.value?.trim()?.takeIf(String::isNotEmpty),
            // The model holds one task reminder, so the longest lead time wins
            // and any further alarm stays foreign passthrough.
            reminder = vtodo.readReminders().firstOrNull(),
            uid = uid,
            href = href,
            etag = etag,
            calendarId = calendarId,
            parentTaskId = vtodo.relatedTo.firstOrNull()?.value?.trim()?.takeIf(String::isNotEmpty),
            recurrence = recurrenceRuleText(vtodo),
            recurrenceId = recurrenceInstant,
            recurrenceDate = recurrenceDate,
            sequence = vtodo.sequence?.value,
            recurrenceScope = if (recurrenceInstant != null || recurrenceDate != null) {
                calino.malinov.ski.data.model.RecurrenceEditScope.This
            } else {
                calino.malinov.ski.data.model.RecurrenceEditScope.All
            },
        )
    }

    private fun recurrenceRuleText(vtodo: VTodo): String? {
        if (vtodo.recurrenceRule == null) return null
        val calendar = ICalendar().also { it.addComponent(vtodo.copy()) }
        return ICalWriter.write(calendar)
            .replace("\r\n ", "")
            .lineSequence()
            .firstOrNull { it.startsWith("RRULE:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    // --- VJOURNAL -------------------------------------------------------------

    private fun mapJournal(vjournal: VJournal, href: String, etag: String?): JournalEntry? {
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
            etag = etag,
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

        /**
         * A hard stop on one series, in case a rule the app has not seen
         * before iterates far more densely than a calendar ever should. The
         * window normally bounds expansion long before this does.
         */
        const val MaxOccurrencesPerSeries = 2000

        /**
         * Occurrence identity: the series UID plus the instant this occurrence
         * belongs to. Stable across refetches, and distinct per occurrence, so
         * the calendar can address one instance while `CalEvent.uid` still
         * names the series for editing.
         */
        fun occurrenceId(uid: String, instant: Instant): String = "$uid@$instant"

        fun taskOccurrenceId(uid: String, date: LocalDate?, instant: Instant?): String =
            "$uid@${date?.toString() ?: requireNotNull(instant)}"
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
