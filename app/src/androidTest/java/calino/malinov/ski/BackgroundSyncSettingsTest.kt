package calino.malinov.ski

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.data.sync.BackgroundSyncCadence
import calino.malinov.ski.data.sync.BackgroundSyncScheduleRules
import calino.malinov.ski.data.sync.BackgroundSyncStore
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundSyncSettingsTest : CalinoUiTest() {

    @Test
    fun cadenceAndStatusPersistAcrossSettingsNavigation() {
        openSyncSettings()
        compose.onNodeWithContentDescription("Background sync: 1 h")
            .performScrollTo()
            .assertIsSelected()
        compose.onNodeWithContentDescription("Background sync: 4 h")
            .performScrollTo()
            .performClick()
            .assertIsSelected()

        compose.openRoute("Month")
        compose.waitForIdle()
        openSyncSettings()
        compose.onNodeWithContentDescription("Background sync: 4 h")
            .performScrollTo()
            .assertIsSelected()

        val context = ApplicationProvider.getApplicationContext<Context>()
        BackgroundSyncStore(context).apply {
            recordAttempt(Instant.ofEpochMilli(1_789_740_000_000))
            recordSuccess(Instant.ofEpochMilli(1_789_740_000_000))
            recordFailure("The account server could not be reached.")
        }
        compose.waitUntil(5_000) {
            compose.hasTextNode("Last sync issue: The account server could not be reached.")
        }
        compose.onNodeWithText("Last background attempt:", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Last successful sync:", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Last sync issue: The account server could not be reached.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun offSuppressesPeriodicAndImmediateWorkEvenWhenAnAccountExists() {
        assertTrue(BackgroundSyncScheduleRules.shouldSchedulePeriodic(true, BackgroundSyncCadence.Hourly))
        assertTrue(BackgroundSyncScheduleRules.shouldEnqueueImmediate(true, BackgroundSyncCadence.Hourly))
        assertFalse(BackgroundSyncScheduleRules.shouldSchedulePeriodic(true, BackgroundSyncCadence.Off))
        assertFalse(BackgroundSyncScheduleRules.shouldEnqueueImmediate(true, BackgroundSyncCadence.Off))
        assertFalse(BackgroundSyncScheduleRules.shouldSchedulePeriodic(false, BackgroundSyncCadence.Hourly))
        assertFalse(BackgroundSyncScheduleRules.shouldEnqueueImmediate(false, BackgroundSyncCadence.Hourly))

        openSyncSettings()
        compose.onNodeWithContentDescription("Background sync: Off")
            .performScrollTo()
            .performClick()
            .assertIsSelected()
    }

    private fun openSyncSettings() {
        compose.openRoute("Settings")
        compose.waitForIdle()
        compose.onNodeWithTag("Settings section rail").performScrollToIndex(3)
        compose.onNodeWithContentDescription("Calendars & sync settings")
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
    }
}
