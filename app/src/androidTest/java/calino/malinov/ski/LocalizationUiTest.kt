package calino.malinov.ski

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import calino.malinov.ski.state.SharedPreferencesPreferenceStore
import calino.malinov.ski.util.CalinoWeekStart
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise actual screens with Android's per-app locale selected before launch. */
abstract class LocalizationUiTest(private val language: String) : CalinoUiTest(localeTag = language) {
    @Test fun calendarAndSettingsUseSelectedLanguageAndKeepPreferencesOnRecreation() {
        compose.waitForIdle()
        capture("calendar")
        val context = compose.activity
        compose.onNodeWithContentDescription(context.getString(R.string.cal_open_navigation)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.cal_settings_route))
            .performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.set_section_settings, context.getString(R.string.set_display))).assertIsDisplayed()
        capture("settings")
        assertEquals(CalinoWeekStart.Monday, SharedPreferencesPreferenceStore(context).loadWeekStart())
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        val recreated = compose.activity
        assertEquals(language, recreated.resources.configuration.locales[0].toLanguageTag())
        assertEquals(CalinoWeekStart.Monday, SharedPreferencesPreferenceStore(recreated).loadWeekStart())
        compose.onNodeWithContentDescription(recreated.getString(R.string.cal_open_navigation)).assertIsDisplayed()
    }

    @Test fun taskEditorOpensAndCancelsWithTranslatedAccessibleControls() {
        val context = compose.activity
        compose.onNodeWithContentDescription(context.getString(R.string.cal_open_navigation)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.cal_route_tasks))
            .performScrollTo().performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(context.getString(R.string.cal_swipe_up_views,
            context.getString(R.string.host_new_task))).performClick()
        compose.mainClock.advanceTimeBy(96)
        capture("editor-enter")
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.ed_editor_cancel_description)).assertIsDisplayed()
        capture("editor")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(context.getString(R.string.ed_editor_cancel_description)).performClick()
        compose.mainClock.advanceTimeBy(160)
        capture("editor-exit")
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("task-list").assertIsDisplayed()
    }

    private fun capture(surface: String) {
        val file = File(compose.activity.getExternalFilesDir(null), "localization/$language-$surface.png")
        file.parentFile!!.mkdirs()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class DanishLocalizationUiTest : LocalizationUiTest("da")
@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class GermanLocalizationUiTest : LocalizationUiTest("de")
@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class ExpandedLocalizationUiTest : LocalizationUiTest("en-XA")
@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class RtlLocalizationUiTest : LocalizationUiTest("ar-XB")
