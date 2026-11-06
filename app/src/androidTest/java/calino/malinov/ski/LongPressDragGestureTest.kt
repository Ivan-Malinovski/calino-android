package calino.malinov.ski

import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.ui.components.calinoLongPressDrag
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The hold-to-open menu and drag-to-reschedule gesture, on its own.
 *
 * The recognizer is the part users were tripping over (issue #22): a press
 * that was a little slow, or drifted a little, armed the drag and moved a task
 * to another day. It is tested without the app around it because the app's
 * fixtures are frozen in May and every row sits behind navigation, none of
 * which has anything to do with the timing.
 *
 * Two clocks are in play and both are driven. The recognizer's deadline is a
 * coroutine timeout, which runs on the test's virtual clock; its "how long has
 * this been held" check reads the real one. [hold] advances both, so a case
 * means what it says.
 */
@RunWith(AndroidJUnit4::class)
class LongPressDragGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var clicks = 0
    private var menus = 0
    private var armed = 0
    private var dragStarts = 0
    private var dragEnds = 0
    private var dragged = Offset.Zero

    private val slop = ViewConfiguration.get(InstrumentationRegistry.getInstrumentation().targetContext)
        .scaledTouchSlop.toFloat()
    private val menuMillis = ViewConfiguration.getLongPressTimeout().toLong()

    @Before fun host() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(
                Modifier
                    .size(200.dp)
                    .testTag(Row)
                    .calinoLongPressDrag(
                        onClick = { clicks++ },
                        onLongPress = { menus++ },
                        onDragArmed = { armed++ },
                        onDragStart = { dragStarts++ },
                        onDrag = { dragged += it },
                        onDragEnd = { dragEnds++ },
                    ),
            ) {}
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun down() {
        compose.onNodeWithTag(Row).performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun hold(millis: Long) {
        compose.onNodeWithTag(Row).performTouchInput { advanceEventTime(millis) }
        Thread.sleep(millis)
        compose.mainClock.advanceTimeBy(millis)
    }

    private fun moveBy(x: Float, y: Float = 0f) {
        compose.onNodeWithTag(Row).performTouchInput { moveBy(Offset(x, y)) }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun up() {
        compose.onNodeWithTag(Row).performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun assertState(clicks: Int, menus: Int, armed: Int, dragStarts: Int, dragEnds: Int) {
        assertEquals("clicks", clicks, this.clicks)
        assertEquals("menus", menus, this.menus)
        assertEquals("armed", armed, this.armed)
        assertEquals("drag starts", dragStarts, this.dragStarts)
        assertEquals("drag ends", dragEnds, this.dragEnds)
    }

    @Test fun aQuickTapOnlyClicks() {
        down()
        hold(80)
        up()
        assertState(clicks = 1, menus = 0, armed = 0, dragStarts = 0, dragEnds = 0)
    }

    /** The reported bug: a tap held a beat too long used to arm the drag at 220 ms. */
    @Test fun aSlowTapStillClicksAndNeverArms() {
        down()
        hold(menuMillis - 100)
        up()
        assertState(clicks = 1, menus = 0, armed = 0, dragStarts = 0, dragEnds = 0)
    }

    /** The reported bug's worse half: the slow tap that also drifted moved the task. */
    @Test fun aSlowTapThatDriftsNeverDrags() {
        down()
        hold(menuMillis - 100)
        moveBy(0f, slop * 1.5f)
        hold(30)
        up()
        assertState(clicks = 0, menus = 0, armed = 0, dragStarts = 0, dragEnds = 0)
        assertEquals(Offset.Zero, dragged)
    }

    @Test fun aHeldPressOpensTheMenuAndArmsTogether() {
        down()
        hold(menuMillis + 60)
        assertState(clicks = 0, menus = 1, armed = 1, dragStarts = 0, dragEnds = 0)
        up()
        // The release after a menu is not also a tap on the row.
        assertState(clicks = 0, menus = 1, armed = 1, dragStarts = 0, dragEnds = 0)
    }

    /** A finger is never still: settling during the hold must not start the drag. */
    @Test fun driftWhileHoldingDoesNotDrag() {
        down()
        hold(menuMillis + 60)
        moveBy(0f, slop * 1.4f)
        hold(30)
        up()
        assertState(clicks = 0, menus = 1, armed = 1, dragStarts = 0, dragEnds = 0)
        assertEquals(Offset.Zero, dragged)
    }

    @Test fun aDeliberateMoveAfterTheHoldDrags() {
        down()
        hold(menuMillis + 60)
        moveBy(0f, slop * 4f)
        moveBy(0f, slop * 4f)
        up()
        assertState(clicks = 0, menus = 1, armed = 1, dragStarts = 1, dragEnds = 1)
        assertEquals(true, dragged.y > slop * 4f)
    }

    private companion object {
        const val Row = "long-press-drag-row"
    }
}
