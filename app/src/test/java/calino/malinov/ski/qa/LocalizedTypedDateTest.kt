package calino.malinov.ski.qa

import calino.malinov.ski.util.parseTypedDate
import calino.malinov.ski.util.parseTypedTime
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.CalinoWeekStart
import calino.malinov.ski.util.weekdayLetters
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalizedTypedDateTest {
    private val reference = LocalDate.of(2026, 5, 18)

    @Test fun weekdayHeadingsTranslateWhileKeepingTheChosenWeekOrder() {
        assertEquals(listOf("M", "T", "O", "T", "F", "L", "S"),
            weekdayLetters(CalinoWeekStart.Monday, Locale.forLanguageTag("da")))
        assertEquals(listOf("S", "M", "D", "M", "D", "F", "S"),
            weekdayLetters(CalinoWeekStart.Sunday, Locale.GERMAN))
    }

    @Test fun localizedClockLabelsCanBeTypedBackIntoTheEditor() {
        listOf(Locale.ENGLISH, Locale.forLanguageTag("da"), Locale.GERMAN).forEach { locale ->
            listOf(LocalTime.of(0, 0), LocalTime.of(9, 30), LocalTime.of(14, 45)).forEach { time ->
                val label = CalinoTimeFormat.TwelveHour.format(time, locale)
                assertEquals(label, time, parseTypedTime(label, locale))
            }
        }
        assertEquals(LocalTime.of(9, 30), parseTypedTime("9:30 vorm.", Locale.GERMAN))
        assertEquals(LocalTime.of(14, 30), parseTypedTime("2:30 nachm.", Locale.GERMAN))
        assertNull(parseTypedTime("14:30 nachm.", Locale.GERMAN))
    }

    @Test fun datesAcceptTheMonthNamesDisplayedInDanishAndGerman() {
        listOf("18 maj", "18. Mai", "18 May").forEach {
            assertEquals(reference, parseTypedDate(it, reference))
        }
        assertEquals(LocalDate.of(2026, 3, 20), parseTypedDate("20 März", reference))
        assertEquals(LocalDate.of(2026, 3, 20), parseTypedDate("20 marts", reference))
        assertEquals(LocalDate.of(2026, 10, 4), parseTypedDate("4 oktober", reference))
        assertEquals(LocalDate.of(2026, 12, 2), parseTypedDate("2 Dez.", reference))
    }

    @Test fun invalidLocalizedDatesAreRejectedAndNumericInputKeepsItsContract() {
        assertNull(parseTypedDate("31 februar", reference))
        assertEquals(LocalDate.of(2026, 10, 4), parseTypedDate("4/10", reference))
        assertEquals(LocalDate.of(2026, 10, 4), parseTypedDate("2026-10-04", reference))
    }
}
