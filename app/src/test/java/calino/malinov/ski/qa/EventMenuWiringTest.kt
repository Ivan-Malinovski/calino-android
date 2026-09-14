package calino.malinov.ski.qa

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day surface showed an event's long-press menu whose every item did
 * nothing, because the host never passed a handler and the parameter defaulted
 * to a no-op. Both halves of that are guarded here: the wiring itself, and the
 * default that let a missing wiring look like a working menu.
 */
class EventMenuWiringTest {

    private fun source(path: String) = File(path).readText()

    @Test
    fun `every route that draws events wires the event menu`() {
        val host = source("src/main/java/calino/malinov/ski/MainActivity.kt")
        val wired = Regex("onEventAction = ::handleEventAction").findAll(host).count()
        assertTrue(
            "the Day, Agenda and Detail routes must each pass handleEventAction, found $wired",
            wired >= 3,
        )
    }

    @Test
    fun `an unwired event menu hides rather than showing dead items`() {
        for (path in listOf(
            "src/main/java/calino/malinov/ski/ui/home/HomeScreen.kt",
            "src/main/java/calino/malinov/ski/ui/surfaces/AgendaScreen.kt",
        )) {
            assertFalse(
                "$path must not default onEventAction to a no-op; use null so the menu is gated",
                source(path).contains("onEventAction: (EventMenuAction, CalEvent) -> Unit = { _, _ -> }"),
            )
        }
    }
}
