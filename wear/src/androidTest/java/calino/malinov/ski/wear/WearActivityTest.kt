package calino.malinov.ski.wear

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.wearcontract.WearCodec
import calino.malinov.ski.wearcontract.WearEvent
import calino.malinov.ski.wearcontract.WearSnapshot
import calino.malinov.ski.wearcontract.WearTask
import calino.malinov.ski.wearcontract.WearTimeFormat
import calino.malinov.ski.wearcontract.WearWriteState
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class WearActivityTest {
    private val fixture = WearFixtureRule()
    private val compose = createAndroidComposeRule<WearActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test
    fun noPhoneSnapshotExplainsSetup() {
        compose.onNodeWithText("Set up Calino on your phone").assertIsDisplayed()
        compose.onNodeWithText("The watch will sync automatically").assertIsDisplayed()
        capture("wear-empty.png")
        compose.onRoot().performTouchInput { swipeUp() }
        capture("wear-setup.png")
    }

    @Test
    fun agendaOpensEventDetailsAndReturns() {
        fixture.install(fixtureSnapshot())
        compose.activityRule.scenario.recreate()

        capture("wear-agenda.png")
        compose.onRoot().performTouchInput { swipeUp() }
        capture("wear-agenda-record.png")
        compose.onNodeWithContentDescription("Design review, 10:00–10:45", substring = true)
            .performClick()
        compose.onNodeWithText("Event").assertIsDisplayed()
        compose.onNodeWithText("Studio · Room 4").assertIsDisplayed()
        capture("wear-event-detail.png")
        Espresso.pressBack()
        compose.onNodeWithContentDescription("Design review, 10:00–10:45", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun tasksModeGroupsTasksAndOpensActions() {
        fixture.install(fixtureSnapshot())
        compose.activityRule.scenario.recreate()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNodeWithText("Tasks").assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsDisplayed()
        capture("wear-tasks.png")
        repeat(2) { compose.onRoot().performTouchInput { swipeUp() } }
        capture("wear-tasks-record.png")
        compose.onNodeWithContentDescription(
            "Submit report, Due ${calino.malinov.ski.wearcontract.WearFormatting.date(fixture.today)}, Open details",
        ).performClick()
        compose.onNodeWithText("Complete").performScrollTo().assertIsDisplayed()
        capture("wear-task-detail.png")
        compose.onNodeWithText("Tomorrow").performScrollTo().assertIsDisplayed()
        capture("wear-task-actions.png")
        compose.onRoot().performTouchInput { swipeUp() }
        compose.onNodeWithText("Phone").assertIsDisplayed()
        capture("wear-phone-edge-action.png")
    }

    @Test
    fun completingATaskConfirmsAndReturnsToTheList() {
        fixture.install(fixtureSnapshot())
        compose.activityRule.scenario.recreate()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Submit report", substring = true).performClick()
        compose.onNodeWithText("Complete").performScrollTo().performClick()
        capture("wear-task-completed.png")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("No open tasks").fetchSemanticsNodes().isNotEmpty()
        }
        capture("wear-tasks-empty.png")
    }

    @Test
    fun emptyTodayStillShowsTheDay() {
        fixture.install(fixtureSnapshot().copy(events = emptyList(), tasks = emptyList()))
        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Nothing planned").assertIsDisplayed()
    }

    @Test
    fun alreadyDueTomorrowHidesTheTomorrowAction() {
        val snapshot = fixtureSnapshot()
        fixture.install(snapshot.copy(tasks = snapshot.tasks.map { it.copy(dueEpochDay = fixture.today + 1) }))
        compose.activityRule.scenario.recreate()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Submit report", substring = true).performClick()
        compose.onNodeWithText("Complete").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Tomorrow").assertCountEquals(0)
    }

    @Test
    fun agendaLandsOnTheNextEventAndDimsEndedOnes() {
        // A zone where it is currently 12:xx, so "earlier today" always exists.
        val zone = java.time.ZoneOffset.ofHours(12 - java.time.LocalTime.now(java.time.ZoneOffset.UTC).hour)
        val today = LocalDate.now(zone).toEpochDay()
        val base = fixtureSnapshot()
        val template = base.events.first().copy(startEpochDay = today, endEpochDay = today, durationMinutes = 30)
        val ended = (0 until 4).map { template.copy(occurrenceId = "past-$it", title = "Past $it", startMinute = 8 * 60 + it * 40) }
        fixture.install(
            base.copy(
                phoneZone = zone.id,
                events = ended + listOf(
                    template.copy(occurrenceId = "next", title = "Standup", startMinute = 13 * 60 + 30),
                    template.copy(occurrenceId = "later", title = "Design review", startMinute = 15 * 60, color = 0xff4caf50),
                    template.copy(occurrenceId = "tomorrow", title = "Dentist", startMinute = 9 * 60, startEpochDay = today + 1, endEpochDay = today + 1, color = 0xffe57373),
                ),
                tasks = base.tasks.map { it.copy(dueEpochDay = today) },
            ),
        )
        compose.activityRule.scenario.recreate()

        compose.onNodeWithContentDescription("Standup", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Past 0", substring = true).assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Past 3, 10:00–10:30, ended", substring = true).assertExists()
        // The column fades in after recreation; an immediate capture is blank.
        Thread.sleep(1500)
        capture("wear-agenda-now.png")
    }

    private fun fixtureSnapshot() = WearSnapshot(
        sourceEpoch = "test",
        sequence = 7,
        generatedAtMillis = System.currentTimeMillis(),
        phoneZone = "UTC",
        stale = false,
        truncated = false,
        syncStatus = "idle",
        timeFormat = WearTimeFormat.H24,
        events = listOf(
            WearEvent(
                occurrenceId = "event-1",
                recordId = "event-record",
                title = "Design review",
                calendar = "Studio",
                color = 0xff8f7cff,
                startEpochDay = fixture.today,
                endEpochDay = fixture.today,
                startMinute = 600,
                durationMinutes = 45,
                allDay = false,
                location = "Room 4",
                writeState = WearWriteState.NONE,
            ),
        ),
        tasks = listOf(
            WearTask(
                occurrenceId = "task-1",
                recordId = "task-record",
                title = "Submit report",
                calendar = "Work",
                color = 0xff4caf50,
                dueEpochDay = fixture.today,
                dueMinute = null,
                category = "Admin",
                done = false,
                progress = 0,
                writeState = WearWriteState.NONE,
            ),
        ),
    )

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = requireNotNull(instrumentation.targetContext.getExternalFilesDir("screenshots"))
            .resolve(name)
        output.outputStream().use { stream ->
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}

private class WearFixtureRule : ExternalResource() {
    val today: Long = LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()

    override fun before() {
        clearFiles()
    }

    /** `-e keepFixture true` leaves the snapshot behind for driving the Tile/complication by hand. */
    override fun after() {
        if (InstrumentationRegistry.getArguments().getString("keepFixture") != "true") clearFiles()
    }

    fun install(snapshot: WearSnapshot) {
        WearStore(context()).saveSnapshot(WearCodec.encodeSnapshot(snapshot))
    }

    private fun clearFiles() {
        listOf("wear-snapshot.bin", "wear-outbox.bin", "wear-acks.bin").forEach {
            File(context().filesDir, it).delete()
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()
}
