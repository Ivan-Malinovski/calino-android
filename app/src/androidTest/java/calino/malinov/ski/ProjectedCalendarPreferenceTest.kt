package calino.malinov.ski

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.state.SharedPreferencesPreferenceStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The opt-in has to survive a restart, because the projection it describes
 * does: an Android account and its calendars outlive the process that made
 * them, and a forgotten opt-in would leave them orphaned in the provider.
 *
 * Against the real `SharedPreferences` rather than the in-memory fake, since
 * the thing worth asserting is a string-set round trip -- `getStringSet`
 * hands back the live stored set, and storing a set derived from it is the
 * documented way to lose it.
 */
@RunWith(AndroidJUnit4::class)
class ProjectedCalendarPreferenceTest {

    private val store = SharedPreferencesPreferenceStore(
        ApplicationProvider.getApplicationContext(),
    )

    @Before fun clear() = store.saveProjectedCalendarIds(emptySet())

    @After fun tidy() = store.saveProjectedCalendarIds(emptySet())

    @Test fun nothingIsPublishedByDefault() {
        assertEquals(emptySet<String>(), store.loadProjectedCalendarIds())
    }

    @Test fun theOptInSurvivesAReload() {
        val ids = setOf("https://dav.invalid/cal/one/", "https://dav.invalid/cal/two/")
        store.saveProjectedCalendarIds(ids)

        assertEquals(
            ids,
            SharedPreferencesPreferenceStore(ApplicationProvider.getApplicationContext())
                .loadProjectedCalendarIds(),
        )
    }

    @Test fun droppingOneLeavesTheRest() {
        store.saveProjectedCalendarIds(setOf("a", "b"))
        // Derived from what was just read, which is exactly the shape the
        // surface produces and exactly what aliasing the stored set breaks.
        store.saveProjectedCalendarIds(store.loadProjectedCalendarIds() - "a")

        assertEquals(setOf("b"), store.loadProjectedCalendarIds())
    }

    @Test fun optingEverythingOutEmptiesIt() {
        store.saveProjectedCalendarIds(setOf("a"))
        store.saveProjectedCalendarIds(emptySet())

        assertEquals(emptySet<String>(), store.loadProjectedCalendarIds())
    }
}
