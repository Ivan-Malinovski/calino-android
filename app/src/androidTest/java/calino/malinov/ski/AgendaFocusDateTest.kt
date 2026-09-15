package calino.malinov.ski

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.getOrNull
import org.junit.Test

/** The Agenda reading line, rather than the route-entry date, owns the root add pill. */
class AgendaFocusDateTest : CalinoUiTest() {

    @Test fun scrollingAcrossTheFocusLineUpdatesTheAddPillInBothDirections() {
        compose.openRoute("Agenda")
        compose.waitForIdle()

        val list = compose.onNode(hasTestTag("agenda-month-list"))
        fun rootPillLabel(): String? = compose
            .onAllNodes(hasContentDescription("Add on ", substring = true))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty() }
            .firstOrNull { it.endsWith("Swipe up to search") }

        val initial = checkNotNull(rootPillLabel())

        list.performTouchInput { swipeUp() }
        compose.waitUntil(5_000) {
            rootPillLabel()?.let { it != initial } == true
        }
        val forward = checkNotNull(rootPillLabel())

        list.performTouchInput { swipeDown() }
        compose.waitUntil(5_000) {
            rootPillLabel()?.let { it != forward } == true
        }
    }
}
