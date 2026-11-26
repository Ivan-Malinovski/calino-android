package calino.malinov.ski.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What counts as live data rather than the frozen sample.
 *
 * The regression these guard: an imported device calendar was not live data, so
 * the app kept the sample calendar and the pinned 18 May 2026 clock while the
 * imported calendars sat in the sidebar with nothing in them. Their events were
 * on their real dates, months away from the day the app opened on.
 */
class LiveDataSourceTest {
    @Test fun anImportedDeviceCalendarIsLiveOnItsOwn() {
        assertTrue(
            LiveDataSource.isLive(
                hasAccounts = false,
                hasWebcalSubscriptions = false,
                hasImportedCalendars = true,
            ),
        )
    }

    @Test fun anAccountOrASubscriptionIsLiveAsBefore() {
        assertTrue(
            LiveDataSource.isLive(
                hasAccounts = true,
                hasWebcalSubscriptions = false,
                hasImportedCalendars = false,
            ),
        )
        assertTrue(
            LiveDataSource.isLive(
                hasAccounts = false,
                hasWebcalSubscriptions = true,
                hasImportedCalendars = false,
            ),
        )
    }

    @Test fun withNoSourceAtAllTheSampleIsTheOnlyThingThereIs() {
        assertFalse(
            LiveDataSource.isLive(
                hasAccounts = false,
                hasWebcalSubscriptions = false,
                hasImportedCalendars = false,
            ),
        )
    }
}
