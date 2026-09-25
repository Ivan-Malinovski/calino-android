package calino.malinov.ski.qa

import calino.malinov.ski.util.MeetingLink
import calino.malinov.ski.util.meetingLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingLinkTest {

    @Test
    fun `a conference value wins over links in the location and notes`() {
        assertEquals(
            MeetingLink("https://video.example.org/room/42", "Meeting"),
            meetingLink(
                conferenceUrl = "https://video.example.org/room/42",
                location = "https://meet.google.com/abc-defg-hij",
                notes = "https://meet.jit.si/other",
            ),
        )
    }

    @Test
    fun `the location is searched before the notes`() {
        assertEquals(
            "https://meet.google.com/abc-defg-hij",
            meetingLink(null, "Room 2 / https://meet.google.com/abc-defg-hij", "https://meet.jit.si/x")?.url,
        )
        assertEquals(
            "https://meet.jit.si/calino-standup",
            meetingLink(null, "Studio", "Dial in at https://meet.jit.si/calino-standup.")?.url,
        )
    }

    @Test
    fun `each known service is recognised by name`() {
        mapOf(
            "https://meet.jit.si/room" to "Jitsi",
            "https://acme.zoom.us/j/9876543210?pwd=abc" to "Zoom",
            "https://meet.google.com/abc-defg-hij" to "Google Meet",
            "https://teams.microsoft.com/l/meetup-join/19%3ameeting" to "Teams",
            "https://teams.live.com/meet/9381234" to "Teams",
            "https://acme.webex.com/meet/ada" to "Webex",
            "https://cloud.example.org/index.php/call/k3x9p2" to "Nextcloud Talk",
        ).forEach { (url, service) ->
            assertEquals(url, MeetingLink(url, service), meetingLink(null, null, "Join: $url"))
        }
    }

    @Test
    fun `ordinary links and insecure links are not meetings`() {
        listOf(
            "https://example.org/agenda",
            "https://zoom.us/pricing",
            "https://meet.google.com/",
            "http://meet.jit.si/room",
            "https://notzoom.us.example.com/j/1",
            "https://cloud.example.org/call/",
        ).forEach { text -> assertNull(text, meetingLink(null, text, text)) }
        assertNull(meetingLink(null, null, null))
    }
}
