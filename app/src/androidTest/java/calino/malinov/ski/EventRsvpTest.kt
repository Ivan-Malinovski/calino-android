package calino.malinov.ski

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.EventOrganizer
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.design.CalinoTheme
import calino.malinov.ski.ui.surfaces.EventDetailSurface
import calino.malinov.ski.ui.surfaces.AgendaScreen
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventRsvpTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val invite = CalEvent(
        id = "invite", title = "Planning", color = 0xff5b7fb5,
        start = LocalDateTime.of(2026, 5, 18, 10, 0), durationMinutes = 60,
        calendarId = "work", recurrence = "FREQ=DAILY;COUNT=3",
        organizer = EventOrganizer("Alice", "alice@example.com"),
        attendees = listOf(
            Attendee("Bob", "bob@example.com", "NEEDS-ACTION", rsvp = true),
            Attendee("Carol", "carol@example.com", "ACCEPTED"),
        ),
    )

    @Test fun attendeeCanChooseScopeAndRespond() {
        val sent = AtomicReference<Pair<String, RecurrenceEditScope>?>()
        compose.setContent {
            CalinoTheme {
                EventDetailSurface(
                    event = invite, readOnly = true,
                    selfAddresses = setOf("mailto:bob@example.com"), canRespond = true,
                    onRespond = { _, status, scope -> sent.set(status to scope); true },
                )
            }
        }
        compose.onNodeWithText("Your response").performScrollTo()
        compose.onNodeWithContentDescription("This occurrence, Respond to This occurrence").performScrollTo().performClick()
        compose.onNodeWithTag("event-detail-list")
            .performScrollToNode(hasContentDescription("Maybe, Maybe invitation"))
        compose.onNodeWithContentDescription("Maybe, Maybe invitation").performClick()
        compose.waitUntil(5_000) { sent.get() != null }
        assertEquals("TENTATIVE" to RecurrenceEditScope.This, sent.get())
    }

    @Test fun unknownIdentityHasNoResponseControls() {
        compose.setContent {
            CalinoTheme {
                EventDetailSurface(event = invite, readOnly = true, canRespond = false)
            }
        }
        compose.onNodeWithText("Your response").assertDoesNotExist()
    }

    @Test fun pendingInvitationOpensItsEvent() {
        val opened = AtomicReference<String?>()
        compose.setContent {
            CalinoTheme {
                AgendaScreen(
                    events = listOf(invite), tasks = emptyList(), pendingInvitations = listOf(invite),
                    initialDate = LocalDate.of(2026, 5, 18),
                    onEventClick = { _, event -> opened.set(event.id) },
                )
            }
        }
        compose.onNodeWithText("Pending invitations (1)").assertExists()
        compose.onNodeWithContentDescription("Open invitation Planning").performClick()
        assertEquals("invite", opened.get())
    }
}
