package calino.malinov.ski

import android.content.Context
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.state.SharedPreferencesPreferenceStore
import calino.malinov.ski.util.CalinoDefaultView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * The base every device test extends.
 *
 * Instrumented tests share one process, and therefore share one
 * [CalinoContainer] -- which owns the fixture repository, the preference store
 * and the account store. Without a reset, a test that completes a task or
 * switches the week start is visible to every test that runs after it, in
 * whatever order the runner happens to pick. [CalinoResetRule] puts the app
 * back to a first-launch state before each one.
 *
 * Every test here runs on the fixture repository, and that is what makes the
 * assertions below possible at all:
 *
 * - With no account connected `CalinoContainer.activeRepository` is the frozen
 *   May 2026 sample data.
 * - `MainActivity` binds `rememberCalinoNow(live = hasAccounts)`, so "today" is
 *   pinned to [FixtureDate] rather than the wall clock. A live clock would make
 *   every date assertion here expire.
 * - `PagerEpoch` in `HomeScreen.kt` is the same date, so all three calendar
 *   pagers start on their centre page.
 */
abstract class CalinoUiTest(
    defaultView: CalinoDefaultView = CalinoDefaultView.Default,
) {

    private val reset = CalinoResetRule(defaultView)

    protected val compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule()

    /**
     * Order matters and is the whole point of the chain: the reset has to run
     * before `createAndroidComposeRule` launches the Activity, because the
     * Activity reads preferences during its first composition. A `@Before`
     * method would be too late.
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(reset).around(compose)

    /**
     * Wait for a node to appear or disappear.
     *
     * `waitForIdle` is not enough after an action that closes behind its own
     * exit animation: surfaces like the journal card use a `closeAnimated {}`
     * helper that runs the real write *after* a real-time `delay`, which
     * Compose's idleness does not track. Polling for the outcome is honest
     * about that; a bare `waitForIdle` would be a race that usually passes.
     */
    protected fun awaitDescribed(description: String) = compose.waitUntil(AwaitTimeoutMillis) {
        compose.hasDescribedNode(description)
    }

    /** As [awaitDescribed], for a node that should go away. */
    protected fun awaitNoDescribed(description: String) = compose.waitUntil(AwaitTimeoutMillis) {
        !compose.hasDescribedNode(description)
    }

    /** The zoom level the calendar is currently reporting, 0 (week) to 2 (month). */
    protected fun currentZoomLevel(): Int =
        (0..2).first { compose.hasDescribedNode(CalinoTestActions.zoomHandleLabel(it)) }

    /**
     * Drive the calendar to a zoom level through the handle, deterministically.
     *
     * The vertical drag is the other way to zoom, but it settles on a
     * velocity-dependent target, which is exactly what a test should not depend
     * on. The handle is a plain click and steps 0 -> 1 -> 2, then wraps 2 -> 1
     * and never returns to 0, so collapsing all the way uses the back handler
     * instead.
     *
     * The app opens on `CalinoDefaultView.Default`, which is Week -- zoom 0.
     * Anything asserting against the month grid has to come through here first.
     */
    protected fun zoomTo(level: Int) {
        require(level in 0..2)
        repeat(MaxZoomSteps) {
            val current = currentZoomLevel()
            if (current == level) return
            if (level < current && level == 0) {
                // The back handler collapses to 0 from anywhere below 2.
                if (current == 2) compose.onNodeWithContentDescription(zoomLabel(2)).performClick()
                Espresso.pressBack()
            } else {
                compose.onNodeWithContentDescription(zoomLabel(currentZoomLevel())).performClick()
            }
            compose.waitForIdle()
        }
        assertEquals("zoom did not settle on level $level", level, currentZoomLevel())
    }

    private fun zoomLabel(level: Int) = CalinoTestActions.zoomHandleLabel(level)

    private companion object {
        const val MaxZoomSteps = 6
        const val AwaitTimeoutMillis = 5_000L
    }
}

/** Returns the app to a first-launch state: default preferences, no account, fixture data. */
class CalinoResetRule(
    private val defaultView: CalinoDefaultView = CalinoDefaultView.Default,
) : ExternalResource() {
    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(PreferencesFile, AccountsFile).forEach { name ->
            // commit, not apply: the Activity launches on the next statement.
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        // Clearing the preferences also cleared the "we have already asked"
        // flag, which would let the notification permission prompt fire on the
        // second resume. That is a system dialog: no Compose matcher can see it
        // and no test can dismiss it. Claim it as already shown instead.
        SharedPreferencesPreferenceStore(context).apply {
            saveNotificationPromptShown(true)
            saveDefaultView(defaultView)
        }
        CalinoContainer.get(context).fixtureRepository.resetToFixtures()
    }

    private companion object {
        const val PreferencesFile = "calino_preferences"
        const val AccountsFile = "calino_caldav_accounts"
    }
}
