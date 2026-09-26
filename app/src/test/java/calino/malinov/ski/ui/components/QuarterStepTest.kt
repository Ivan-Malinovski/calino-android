package calino.malinov.ski.ui.components

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class QuarterStepTest {
    @Test
    fun stepsLandOnQuarters() {
        assertEquals(LocalTime.of(14, 15), stepQuarter(LocalTime.of(14, 7), later = true))
        assertEquals(LocalTime.of(14, 0), stepQuarter(LocalTime.of(14, 7), later = false))
        assertEquals(LocalTime.of(14, 45), stepQuarter(LocalTime.of(14, 30), later = true))
        assertEquals(LocalTime.of(14, 15), stepQuarter(LocalTime.of(14, 30), later = false))
        assertEquals(LocalTime.of(0, 0), stepQuarter(LocalTime.of(23, 45), later = true))
        assertEquals(LocalTime.of(23, 45), stepQuarter(LocalTime.of(0, 0), later = false))
    }
}
