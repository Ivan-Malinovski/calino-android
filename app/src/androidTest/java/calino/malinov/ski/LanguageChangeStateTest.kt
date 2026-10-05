package calino.malinov.ski

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import calino.malinov.ski.util.localizedDisplayFormatter
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class LanguageChangeStateTest : CalinoUiTest() {
    @Test fun changingAppLanguagePreservesTheCommittedCalendarDay() {
        val selected = CalinoTestActions.FixtureDate.plusDays(1)
        compose.selectDayIn(CalinoTestActions.WeekPager, selected)
        compose.waitForIdle()
        compose.assertDaySelected(CalinoTestActions.WeekPager, selected)

        compose.activity.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags("da")
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.locales[0].language == "da"
        }
        compose.waitForIdle()
        val context = compose.activity
        val date = selected.format(localizedDisplayFormatter("EEEE, MMMM d", context.resources.configuration.locales[0]))
        compose.onNode(
            hasAnyAncestor(hasTestTag(CalinoTestActions.WeekPager)) and hasContentDescription(date, substring = true),
        ).assertContentDescriptionContains(", ${context.getString(R.string.cal_selected)}", substring = true)
    }
}
