package calino.malinov.ski.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSyncScheduleRulesTest {
    @Test
    fun cadenceOffersRequestedHourlyDefaultAndLongerIntervals() {
        assertEquals(BackgroundSyncCadence.Hourly, BackgroundSyncCadence.entries.first())
        assertEquals(
            listOf(1L, 4L, 12L, 24L, null),
            BackgroundSyncCadence.entries.map { it.intervalHours },
        )
    }

    @Test
    fun periodicAndImmediateWorkRequireAnAccountAndAnEnabledCadence() {
        assertTrue(BackgroundSyncScheduleRules.shouldSchedulePeriodic(true, BackgroundSyncCadence.Hourly))
        assertTrue(BackgroundSyncScheduleRules.shouldEnqueueImmediate(true, BackgroundSyncCadence.Hourly))
        assertFalse(BackgroundSyncScheduleRules.shouldSchedulePeriodic(false, BackgroundSyncCadence.Hourly))
        assertFalse(BackgroundSyncScheduleRules.shouldEnqueueImmediate(false, BackgroundSyncCadence.Hourly))
        assertFalse(BackgroundSyncScheduleRules.shouldSchedulePeriodic(true, BackgroundSyncCadence.Off))
        assertFalse(BackgroundSyncScheduleRules.shouldEnqueueImmediate(true, BackgroundSyncCadence.Off))
    }
}
