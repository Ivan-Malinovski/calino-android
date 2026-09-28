package calino.malinov.ski.state

import calino.malinov.ski.data.repository.SyncState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WriteQueueBadgeTest {
    private val now = Instant.parse("2026-05-18T10:00:00Z")
    private val fresh = SyncState.Ready(now)

    @Test fun `a change that needs the person turns the marker red over a clean read`() {
        assertEquals(
            SyncBadge.WritesFailed,
            syncBadgeFor(fresh, now, writes = WriteQueueHealth(waiting = 2, attentionIds = setOf("a"))),
        )
    }

    @Test fun `queued changes alone show the quiet waiting marker`() {
        assertEquals(SyncBadge.WritesWaiting, syncBadgeFor(fresh, now, writes = WriteQueueHealth(waiting = 3)))
    }

    @Test fun `a clean read with an empty queue shows nothing`() {
        assertEquals(SyncBadge.None, syncBadgeFor(fresh, now, writes = WriteQueueHealth()))
    }
}
