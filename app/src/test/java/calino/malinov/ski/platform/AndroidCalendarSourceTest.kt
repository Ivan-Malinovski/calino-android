package calino.malinov.ski.platform

import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.Reminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping half of the inbound read: an `Instances` row turned into a
 * [calino.malinov.ski.data.model.CalEvent].
 *
 * Written as the mirror of [ProviderIdentityTest]. The assertions that matter
 * are the two conversions that are silently wrong rather than loudly broken --
 * the UTC anchor for all-day rows, and the exclusive-to-inclusive end date.
 * Both look fine on a device set to UTC and move a birthday by a day
 * everywhere else, which is exactly the sort of bug a device test finds late.
 */
class AndroidCalendarSourceTest {

    private val zone = ZoneId.of("Europe/Copenhagen")

    private val calendar = AndroidCalendarSource.ImportableCalendar(
        id = AndroidCalendarId.calendar(7),
        rowId = 7,
        accountName = "someone@gmail.com",
        accountType = "com.google",
        name = "Work",
        color = 0xFF3366CCL,
    )

    private fun row(
        eventRowId: Long = 42,
        beginMillis: Long,
        endMillis: Long? = null,
        duration: String? = null,
        title: String? = "Standup",
        description: String? = null,
        location: String? = null,
        allDay: Boolean = false,
        availability: Int? = null,
    ) = AndroidCalendarSource.InstanceRow(
        eventRowId, beginMillis, endMillis, duration,
        title, description, location, allDay, availability,
    )

    private fun utcMidnight(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun map(row: AndroidCalendarSource.InstanceRow, reminders: List<Reminder> = emptyList()) =
        AndroidCalendarSource.toEvent(row, calendar, reminders, zone)

    @Test
    fun providerAccessStillNeedsRuntimeWritePermission() {
        assertTrue(AndroidCalendarSource.providerWriteCapability(600, true))
        assertTrue(AndroidCalendarSource.providerWriteCapability(700, true))
        assertFalse(AndroidCalendarSource.providerWriteCapability(500, true))
        assertFalse(AndroidCalendarSource.providerWriteCapability(700, false))
    }

    @Test
    fun aTimedRowKeepsItsWallClockTimeInTheDeviceZone() {
        val begin = LocalDateTime.of(2026, 9, 18, 9, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val event = map(row(beginMillis = begin, endMillis = begin + 45 * 60_000L))!!

        assertEquals(LocalDateTime.of(2026, 9, 18, 9, 30), event.start)
        assertEquals(45, event.durationMinutes)
        assertNull(event.date)
        assertNull(event.endDate)
    }

    @Test
    fun anAllDayRowIsReadInUtcNotTheDeviceZone() {
        // Copenhagen is ahead of UTC, so reading this midnight locally would
        // land on the 18th and move the event a day earlier.
        val begin = utcMidnight(LocalDate.of(2026, 9, 18))
        val event = map(
            row(beginMillis = begin, endMillis = begin + 86_400_000L, allDay = true),
        )!!

        assertTrue(event.allDay)
        assertEquals(LocalDate.of(2026, 9, 18), event.date)
        assertNull("A single day carries no separate end", event.endDate)
        assertNull(event.start)
    }

    @Test
    fun aMultiDayAllDayRowEndsOnTheLastCoveredDay() {
        // The provider's END is exclusive: the 21st means the event covers
        // through the 20th. Calino's endDate is inclusive.
        val event = map(
            row(
                beginMillis = utcMidnight(LocalDate.of(2026, 9, 18)),
                endMillis = utcMidnight(LocalDate.of(2026, 9, 21)),
                allDay = true,
            ),
        )!!

        assertEquals(LocalDate.of(2026, 9, 18), event.date)
        assertEquals(LocalDate.of(2026, 9, 20), event.endDate)
    }

    @Test
    fun aRowWithNoEndFallsBackToItsDuration() {
        val begin = LocalDateTime.of(2026, 9, 18, 14, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val event = map(row(beginMillis = begin, endMillis = null, duration = "PT90M"))!!

        assertEquals(90, event.durationMinutes)
    }

    @Test
    fun anUnparsableDurationBecomesAZeroLengthEventRatherThanNothing() {
        val begin = LocalDateTime.of(2026, 9, 18, 14, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val event = map(row(beginMillis = begin, endMillis = null, duration = "nonsense"))!!

        assertEquals(0, event.durationMinutes)
    }

    @Test
    fun freeTimeIsCarriedAcrossAndAnythingElseIsBusy() {
        val begin = utcMidnight(LocalDate.of(2026, 9, 18))
        assertEquals(
            Availability.Free,
            map(row(beginMillis = begin, endMillis = begin, availability = 1))!!.availability,
        )
        assertEquals(
            Availability.Busy,
            map(row(beginMillis = begin, endMillis = begin, availability = 0))!!.availability,
        )
        assertEquals(
            "A row that says nothing is busy, not free",
            Availability.Busy,
            map(row(beginMillis = begin, endMillis = begin, availability = null))!!.availability,
        )
    }

    @Test
    fun anImportedEventCarriesNoCalDavIdentity() {
        val begin = utcMidnight(LocalDate.of(2026, 9, 18))
        val event = map(row(beginMillis = begin, endMillis = begin))!!

        // A synthetic uid or href would make this indistinguishable from a
        // real CalDAV record to the write pipeline, which must never touch it.
        assertNull(event.uid)
        assertNull(event.href)
        assertNull(event.etag)
        assertNull(event.recurrenceId)
        assertNull(event.sequence)
    }

    @Test
    fun theIdNamesBothTheRowAndTheOccurrence() {
        val first = utcMidnight(LocalDate.of(2026, 9, 18))
        val second = utcMidnight(LocalDate.of(2026, 9, 19))

        // Two occurrences of one series share a provider row id and are told
        // apart only by when they begin.
        val a = map(row(eventRowId = 42, beginMillis = first, endMillis = first))!!
        val b = map(row(eventRowId = 42, beginMillis = second, endMillis = second))!!

        assertTrue(a.id != b.id)
        assertEquals(42L to first, AndroidCalendarId.eventRow(a.id))
        assertEquals(42L to second, AndroidCalendarId.eventRow(b.id))
    }

    @Test
    fun anUntitledRowGetsThePlaceholderRatherThanAnEmptyLine() {
        val begin = utcMidnight(LocalDate.of(2026, 9, 18))
        assertEquals("(no title)", map(row(beginMillis = begin, title = null))!!.title)
        assertEquals("(no title)", map(row(beginMillis = begin, title = "  "))!!.title)
    }

    @Test
    fun blankTextFieldsBecomeNullRatherThanEmptyStrings() {
        val begin = utcMidnight(LocalDate.of(2026, 9, 18))
        val event = map(row(beginMillis = begin, description = "", location = "   "))!!

        assertNull(event.notes)
        assertNull(event.location)
    }

    @Test
    fun theEventTakesItsCalendarsColourAndId() {
        val begin = utcMidnight(LocalDate.of(2026, 9, 18))
        val event = map(row(beginMillis = begin))!!

        assertEquals(calendar.id, event.calendarId)
        assertEquals(calendar.color, event.color)
    }
}
