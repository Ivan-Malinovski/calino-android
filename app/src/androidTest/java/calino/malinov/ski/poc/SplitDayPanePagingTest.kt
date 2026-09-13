package calino.malinov.ski.poc

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.poc.CalinoTestActions.DayPanePager
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The landscape Month split keeps the shared day pager usable in its sidebar. */
@RunWith(AndroidJUnit4::class)
class SplitDayPanePagingTest : CalinoUiTest() {

    @Test
    fun dayPaneSwipeAdvancesItsSharedDayPager() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()

        compose.onNodeWithTag(DayPanePager).assertIsDisplayed()
        compose.onNodeWithContentDescription("Day sidebar page May 18, 2026", substring = true)
            .assertIsDisplayed()

        compose.onNodeWithTag(DayPanePager).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Day sidebar page May 19, 2026", substring = true)
            .assertIsDisplayed()
    }

    @After
    fun restorePortraitOrientation() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
