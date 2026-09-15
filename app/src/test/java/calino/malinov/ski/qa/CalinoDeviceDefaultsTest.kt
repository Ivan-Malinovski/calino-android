package calino.malinov.ski.qa

import calino.malinov.ski.state.calinoWeekStartFor
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.CalinoWeekStart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

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
}
