package calino.malinov.ski

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.state.SharedPreferencesPreferenceStore
import calino.malinov.ski.ui.range.RangePagerTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RangeHeaderSettingTest : CalinoUiTest() {
    @Test fun defaultHeaderPlacementCanBeDisabledAndPersistsAcrossRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(SharedPreferencesPreferenceStore(context).loadRangeMultiDayEventsInHeader())
        compose.runOnIdle {
            runBlocking {
                CalinoContainer.get(context).fixtureRepository.addEvent(
                    NewEvent("Header trip", LocalDate.of(2026, 5, 18), LocalTime.of(16, 15), 49 * 60),
                )
            }
        }
        compose.openRoute("Range")
        if (compose.hasDescribedNode("Dismiss sample calendar notice")) {
            compose.onNodeWithContentDescription("Dismiss sample calendar notice").performClick()
        }
        assertInHeader()
        capture("range-header-default")
        listOf("1", "7", "3").forEach { size ->
            compose.selectRangeDays(size.toInt())
            assertInHeader()
            capture("range-header-$size-days")
        }
        headers()[0].performClick()
        awaitDescribed("Open event")
        compose.onNodeWithText("4:15", substring = true).assertIsDisplayed()
        compose.onNodeWithText("5:15", substring = true).assertIsDisplayed()
        capture("range-header-detail")
        compose.onNodeWithContentDescription("Close event preview").performClick()
        awaitNoDescribed("Close event preview")

        openSetting()
        toggle().assertIsOn()
        capture("range-header-setting-on")
        toggle().performClick()
        toggle().assertIsOff()
        assertFalse(SharedPreferencesPreferenceStore(context).loadRangeMultiDayEventsInHeader())
        compose.openRoute("Range")
        compose.onAllNodesWithContentDescription("Header trip, 12:00 AM").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Header trip, 4:15 PM").assertCountEquals(1)
        capture("range-header-setting-off-grid")
        compose.activityRule.scenario.recreate()
        openSetting()
        toggle().assertIsOff()
        capture("range-header-setting-off")
        toggle().performClick()
        compose.openRoute("Range")
        assertInHeader()
    }

    private fun assertInHeader() {
        headers().assertCountEquals(1)
        headers()[0].assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Header trip, 12:00 AM").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Header trip, 4:15 PM").assertCountEquals(0)
    }

    private fun headers(): SemanticsNodeInteractionCollection {
        val viewport = compose.onNodeWithTag(RangePagerTag).fetchSemanticsNode().boundsInRoot
        // The pager mounts adjacent days before a swipe. Count only the header
        // in the visible page, rather than that neighbour's offscreen copy.
        return compose.onAllNodesWithContentDescription("Header trip", substring = true)
            .filter(SemanticsMatcher("intersects the visible Range page") { it.boundsInRoot.overlaps(viewport) })
    }

    private fun openSetting() {
        compose.openRoute("Settings")
        compose.onNodeWithContentDescription("Events & tasks settings").performClick()
        toggle().performScrollTo()
    }

    private fun toggle() = compose.onNodeWithContentDescription("Multi-day events in Range header toggle")

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(120)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
    }
}
