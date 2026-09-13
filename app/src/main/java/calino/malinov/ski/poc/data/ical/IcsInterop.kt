package calino.malinov.ski.poc.data.ical

import biweekly.Biweekly
import biweekly.ICalendar
import calino.malinov.ski.poc.data.caldav.ICalMapper
import calino.malinov.ski.poc.data.caldav.ICalWriter
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.NewEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class IcsImportBatch(
    val events: List<CalEvent>,
    val unsupportedComponents: Int,
    val duplicateUids: Set<String> = emptySet(),
)

data class IcsImportSummary(val imported: Int, val queued: Int, val skipped: Int, val failed: Int)

/** Pure iCalendar boundary shared by document intents, Settings and Sharesheet. */
object IcsInterop {
    const val MaxBytes = 5 * 1024 * 1024

    fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MaxBytes) throw IllegalArgumentException("That calendar file is larger than 5 MB.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun parseEvents(text: String, zone: ZoneId = ZoneId.systemDefault()): IcsImportBatch {
        require(text.toByteArray().size <= MaxBytes) { "That calendar file is larger than 5 MB." }
        val calendars = runCatching { Biweekly.parse(text).all() }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: error("That file is not valid iCalendar data.")
        val unsupported = calendars.sumOf { it.todos.size + it.journals.size }
        val mapper = ICalMapper(zone)
        val parsed = mapper.parse(
            text,
            calendarId = "import",
            color = 0xFFC2697F,
            href = "import.ics",
            windowStart = LocalDate.of(1900, 1, 1),
            windowEnd = LocalDate.of(2200, 12, 31),
        )
        val events = parsed.events.distinctBy { it.uid ?: it.id }
        require(events.isNotEmpty()) { "That calendar file contains no events Calino can import." }
        return IcsImportBatch(events, unsupported)
    }

    fun withDuplicates(batch: IcsImportBatch, existing: List<CalEvent>): IcsImportBatch {
        val known = existing.mapNotNull { it.uid }.toSet()
        val seen = mutableSetOf<String>()
        val duplicates = batch.events.mapNotNull { it.uid }
            .filter { it in known || !seen.add(it) }.toSet()
        return batch.copy(duplicateUids = duplicates)
    }

    fun export(events: List<CalEvent>, zone: ZoneId = ZoneId.systemDefault()): String {
        val writer = ICalWriter(zone)
        val components = events.distinctBy { it.uid ?: it.id }.map {
            writer.writeEvent(it, now = Instant.now())
        }
        return writer.buildCalendar(components)
    }

    fun asNewEvent(event: CalEvent, calendarId: String): NewEvent {
        val date = event.date ?: event.start?.toLocalDate() ?: LocalDate.now()
        return NewEvent(
            title = event.title, date = date, endDate = event.endDate, startTime = event.start?.toLocalTime(),
            durationMinutes = event.durationMinutes, allDay = event.allDay, color = event.color,
            recurrence = event.recurrence, location = event.location, notes = event.notes,
            attendees = event.attendees, calendarId = calendarId, availability = event.availability,
            categories = event.categories, reminders = event.reminders, uid = event.uid,
            sequence = event.sequence,
        )
    }
}
