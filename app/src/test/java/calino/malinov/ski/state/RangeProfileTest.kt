package calino.malinov.ski.state

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeProfileTest {
    @Test fun `phone choices are separated by orientation`() {
        assertEquals(
            CalinoRangeProfile.PhonePortrait,
            rangeProfileFor(
                CalinoDeviceDefaults.Fallback.copy(
                    windowWidthDp = 412,
                    windowHeightDp = 915,
                    orientation = CalinoOrientation.Portrait,
                ),
            ),
        )
        assertEquals(
            CalinoRangeProfile.PhoneLandscape,
            rangeProfileFor(
                CalinoDeviceDefaults.Fallback.copy(
                    windowWidthDp = 915,
                    windowHeightDp = 412,
                    orientation = CalinoOrientation.Landscape,
                ),
            ),
        )
    }

    @Test fun `large adaptive windows use tablet orientation choices`() {
        assertEquals(
            CalinoRangeProfile.TabletPortrait,
            rangeProfileFor(
                CalinoDeviceDefaults.Fallback.copy(
                    windowWidthDp = 700,
                    windowHeightDp = 1000,
                    orientation = CalinoOrientation.Portrait,
                ),
            ),
        )
        assertEquals(
            CalinoRangeProfile.TabletLandscape,
            rangeProfileFor(
                CalinoDeviceDefaults.Fallback.copy(
                    windowWidthDp = 1000,
                    windowHeightDp = 700,
                    orientation = CalinoOrientation.Landscape,
                ),
            ),
        )
    }
}
