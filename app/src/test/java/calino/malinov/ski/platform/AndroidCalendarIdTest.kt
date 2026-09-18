package calino.malinov.ski.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The id scheme that tells an imported record from one Calino owns.
 *
 * Worth its own test because two things lean on it that fail quietly. The
 * read-only guard in the UI asks [AndroidCalendarId.isImported] before it
 * offers an edit, so a false negative offers an edit that cannot work. And a
 * later write-back recovers the provider row and occurrence start by parsing
 * alone, so a round-trip that loses either would edit the wrong occurrence.
 */
class AndroidCalendarIdTest {

    @Test
    fun anEventIdRoundTripsThroughBothHalves() {
        val id = AndroidCalendarId.event(eventRowId = 91, beginMillis = 1_789_000_000_000L)

        assertTrue(AndroidCalendarId.isImported(id))
        assertEquals(91L to 1_789_000_000_000L, AndroidCalendarId.eventRow(id))
    }

    @Test
    fun aCalendarIdRoundTrips() {
        val id = AndroidCalendarId.calendar(7)

        assertTrue(AndroidCalendarId.isImported(id))
        assertEquals(7L, AndroidCalendarId.calendarRowId(id))
    }

    @Test
    fun aCalDavIdIsNeverMistakenForAnImportedOne() {
        // The two shapes CalDAV records actually take: a collection URL, and
        // an occurrence id of uid@instant.
        val url = "https://radicale.malinov.ski/ivan/personal/"
        val occurrence = "fixture-coffee@2026-09-18T07:30:00Z"

        assertFalse(AndroidCalendarId.isImported(url))
        assertFalse(AndroidCalendarId.isImported(occurrence))
        assertNull(AndroidCalendarId.eventRow(occurrence))
        assertNull(AndroidCalendarId.calendarRowId(url))
    }

    @Test
    fun aMangledIdParsesToNullRatherThanGuessing() {
        // Acting on a half-understood id could edit the wrong occurrence of
        // somebody's Exchange calendar, so refusing is the safe answer.
        assertNull(AndroidCalendarId.eventRow("android:91"))
        assertNull(AndroidCalendarId.eventRow("android:@123"))
        assertNull(AndroidCalendarId.eventRow("android:abc@123"))
        assertNull(AndroidCalendarId.eventRow("android:91@later"))
        assertNull(AndroidCalendarId.calendarRowId("android:not-a-number"))
    }

    @Test
    fun theLastSeparatorWins() {
        // Provider row ids are numeric, so an '@' can only be in the instant
        // half -- but splitting on the first one would still be wrong, and
        // this is the rule a future write-back depends on.
        assertEquals(5L to 10L, AndroidCalendarId.eventRow("android:5@10"))
    }
}
