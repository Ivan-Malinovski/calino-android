package calino.malinov.ski.util

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypedDateTimeTest {
    @Test
    fun readsTypedTimes() {
        val cases = mapOf(
            "14" to LocalTime.of(14, 0),
            "1400" to LocalTime.of(14, 0),
            "14:00" to LocalTime.of(14, 0),
            "9" to LocalTime.of(9, 0),
            "930" to LocalTime.of(9, 30),
            "09:05" to LocalTime.of(9, 5),
            "14.30" to LocalTime.of(14, 30),
            "0" to LocalTime.MIDNIGHT,
            "2pm" to LocalTime.of(14, 0),
            "2:30 PM" to LocalTime.of(14, 30),
            "12am" to LocalTime.MIDNIGHT,
            "12 p" to LocalTime.NOON,
        )
        cases.forEach { (typed, expected) -> assertEquals(typed, expected, parseTypedTime(typed)) }
    }

    @Test
    fun rejectsNonTimes() {
        listOf("", "24", "2460", "14:7", "13pm", "abc", "14:00:00", "12345").forEach {
            assertNull(it, parseTypedTime(it))
        }
    }

    @Test
    fun readsTypedDates() {
        val reference = LocalDate.of(2026, 5, 18)
        val cases = mapOf(
            "20" to LocalDate.of(2026, 5, 20),
            "3/6" to LocalDate.of(2026, 6, 3),
            "3.6.27" to LocalDate.of(2027, 6, 3),
            "3-6-2027" to LocalDate.of(2027, 6, 3),
            "2027-06-03" to LocalDate.of(2027, 6, 3),
            "3 June" to LocalDate.of(2026, 6, 3),
            "June 3, 2027" to LocalDate.of(2027, 6, 3),
            "3 jun 27" to LocalDate.of(2027, 6, 3),
        )
        cases.forEach { (typed, expected) -> assertEquals(typed, expected, parseTypedDate(typed, reference)) }
    }

    @Test
    fun rejectsNonDates() {
        val reference = LocalDate.of(2026, 5, 18)
        listOf("", "32", "31/4", "30 feb", "tomorrow", "3 june july", "1/2/3/4").forEach {
            assertNull(it, parseTypedDate(it, reference))
        }
    }
}
