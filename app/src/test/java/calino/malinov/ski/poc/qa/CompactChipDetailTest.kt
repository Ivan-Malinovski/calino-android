package calino.malinov.ski.poc.qa

import androidx.compose.ui.unit.dp
import calino.malinov.ski.poc.ui.home.CompactChipDetail
import calino.malinov.ski.poc.ui.home.compactChipDetail
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The 7-day chip earns its lines back one at a time. These pin the steps so a
 * later spacing tweak cannot quietly start clipping the title.
 */
class CompactChipDetailTest {

    @Test fun `a short chip keeps only its title`() {
        assertEquals(CompactChipDetail.TitleOnly, compactChipDetail(24.dp, 51.dp, hasLocation = true))
    }

    @Test fun `a narrow chip keeps only its title`() {
        assertEquals(CompactChipDetail.TitleOnly, compactChipDetail(60.dp, 30.dp, hasLocation = true))
    }

    @Test fun `a half-hour chip earns the time`() {
        assertEquals(CompactChipDetail.Time, compactChipDetail(27.dp, 51.dp, hasLocation = true))
    }

    @Test fun `a tall chip with no location stops at the time`() {
        assertEquals(CompactChipDetail.Time, compactChipDetail(90.dp, 51.dp, hasLocation = false))
    }

    @Test fun `a tall chip earns the place too`() {
        assertEquals(CompactChipDetail.TimeAndPlace, compactChipDetail(37.dp, 44.dp, hasLocation = true))
    }

    @Test fun `a tall but narrow chip withholds the place`() {
        assertEquals(CompactChipDetail.Time, compactChipDetail(90.dp, 40.dp, hasLocation = true))
    }
}
