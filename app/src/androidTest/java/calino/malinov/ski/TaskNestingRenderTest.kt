package calino.malinov.ski

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoPalette
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.design.CalinoThemes
import calino.malinov.ski.ui.components.taskNestIndent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/** Crossings and the overlap between neighbouring task rows keep one rail tone. */
class TaskNestingRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightRailsKeepTheirToneAcrossJoins() = checkRails(CalinoThemes.PaperLight)
    @Test fun darkRailsKeepTheirToneAcrossJoins() = checkRails(CalinoThemes.PaperDark)

    private fun checkRails(palette: CalinoPalette) {
        compose.setContent {
            CalinoTheme(palette) {
                Column(Modifier.width(100.dp).background(CalinoColors.Canvas).testTag("rails")) {
                    Box(Modifier.taskNestIndent(1, listOf(true)).height(44.dp))
                    Box(Modifier.taskNestIndent(1, listOf(false), railOverhang = 10.dp).height(44.dp))
                }
            }
        }
        val image = compose.onNodeWithTag("rails").captureToImage()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/rails-${palette.id}.png").use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
        val pixels = image.toPixelMap()
        val scale = pixels.width / 100f
        fun rail(y: Int) = pixels[(10 * scale).roundToInt(), (y * scale).roundToInt()]
        val plain = rail(12)
        assertNotEquals("the rail must be visible against its background", pixels[(80 * scale).roundToInt(), (12 * scale).roundToInt()], plain)
        assertEquals("an elbow must not darken its rail", plain, rail(22))
        assertEquals("the next row's overhang must not darken the rail", plain, rail(38))
        assertEquals("the final elbow must keep the same tone", plain, rail(66))
    }
}
