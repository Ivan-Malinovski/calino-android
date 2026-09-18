package calino.malinov.ski.qa

import calino.malinov.ski.state.calinoWeekStartFor
import calino.malinov.ski.state.localeFirstDayOfWeek
import calino.malinov.ski.state.weekStartLocale
import calino.malinov.ski.state.timeFormatFor
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.CalinoWeekStart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.util.Locale

class CalinoDeviceDefaultsTest {

    @Test
    fun `system time format resolves to the device format while overrides win`() {
        assertEquals(
            CalinoTimeFormat.TwentyFourHour,
            CalinoTimeFormat.System.resolved(CalinoTimeFormat.TwentyFourHour),
        )
        assertEquals(
            CalinoTimeFormat.TwelveHour,
            CalinoTimeFormat.TwelveHour.resolved(CalinoTimeFormat.TwentyFourHour),
        )
    }

    @Test
    fun `system week start maps supported device starts while overrides win`() {
        assertEquals(CalinoWeekStart.Sunday, calinoWeekStartFor(DayOfWeek.SUNDAY))
        assertEquals(CalinoWeekStart.Monday, calinoWeekStartFor(DayOfWeek.SATURDAY))
        assertEquals(
            CalinoWeekStart.Sunday,
            CalinoWeekStart.System.resolved(CalinoWeekStart.Sunday),
        )
        assertEquals(
            CalinoWeekStart.Monday,
            CalinoWeekStart.Monday.resolved(CalinoWeekStart.Sunday),
        )
    }

    @Test
    fun `regional locale extensions override language defaults`() {
        val englishWithDanishFormats = Locale.forLanguageTag("en-US-u-fw-mon-hc-h23")

        assertEquals(DayOfWeek.MONDAY, localeFirstDayOfWeek(englishWithDanishFormats))
        assertEquals(
            CalinoTimeFormat.TwentyFourHour,
            timeFormatFor(null, englishWithDanishFormats),
        )
    }

    @Test
    fun `the device region outranks the language locale's country`() {
        // English (United States) on a phone with a Danish SIM: the country in
        // the locale says which English, not where the phone is.
        assertEquals(
            Locale.forLanguageTag("en-DK"),
            weekStartLocale(Locale.US, "dk"),
        )
        // Nothing to go on, so the language locale stands.
        assertEquals(Locale.US, weekStartLocale(Locale.US, null))
        assertEquals(Locale.US, weekStartLocale(Locale.US, ""))
        assertEquals(Locale.US, weekStartLocale(Locale.US, "US"))
    }

    @Test
    fun `an explicit regional preference outranks the device region`() {
        // The user asked for Sunday while holding a Danish SIM; their word wins.
        assertEquals(
            DayOfWeek.SUNDAY,
            localeFirstDayOfWeek(Locale.forLanguageTag("en-US-u-fw-sun"), "dk"),
        )
    }

    @Test
    fun `a regional preference keeps its extensions when the region moves`() {
        val moved = weekStartLocale(Locale.forLanguageTag("en-US-u-hc-h23"), "dk")

        assertEquals("DK", moved.country)
        assertEquals("en", moved.language)
        assertEquals("h23", moved.getUnicodeLocaleType("hc"))
    }

    @Test
    fun `explicit Android clock choice overrides the regional locale`() {
        assertEquals(
            CalinoTimeFormat.TwentyFourHour,
            timeFormatFor("24", Locale.US),
        )
        assertEquals(
            CalinoTimeFormat.TwelveHour,
            timeFormatFor("12", Locale.forLanguageTag("da-DK")),
        )
    }
}
