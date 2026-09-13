package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.repository.SyncState
import calino.malinov.ski.poc.state.SyncBadge
import calino.malinov.ski.poc.state.SyncStaleAfter
import calino.malinov.ski.poc.state.syncBadgeFor
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncIndicatorRulesTest {

    private val now: Instant = Instant.parse("2026-09-13T10:00:00Z")

    @Test
    fun `no account shows nothing`() {
        assertEquals(SyncBadge.None, syncBadgeFor(SyncState.Idle, now))
    }

    @Test
    fun `a fresh clean read shows nothing`() {
        val state = SyncState.Ready(now.minus(Duration.ofMinutes(2)))
        assertEquals(SyncBadge.None, syncBadgeFor(state, now))
    }

    @Test
    fun `a read older than the stale window is marked stale`() {
        val state = SyncState.Ready(now.minus(SyncStaleAfter).minusSeconds(1))
        assertEquals(SyncBadge.Stale, syncBadgeFor(state, now))
    }

    @Test
    fun `the stale boundary itself counts as stale`() {
        val state = SyncState.Ready(now.minus(SyncStaleAfter))
        assertEquals(SyncBadge.Stale, syncBadgeFor(state, now))
    }

    @Test
    fun `a warning outranks age`() {
        // Recent, so the age rule alone would stay quiet -- but part of the
        // calendar is missing, and that is the more important thing to say.
        val state = SyncState.Ready(now, warnings = listOf("Work could not be read"))
        assertEquals(SyncBadge.Incomplete, syncBadgeFor(state, now))
    }

    @Test
    fun `an old partial read is still reported as incomplete`() {
        val state = SyncState.Ready(
            now.minus(SyncStaleAfter).minus(Duration.ofHours(4)),
            warnings = listOf("Work could not be read"),
        )
        assertEquals(SyncBadge.Incomplete, syncBadgeFor(state, now))
    }

    @Test
    fun `a failed read is reported even when data is on screen`() {
        val state = SyncState.Failed("No network", hadPreviousData = true)
        assertEquals(SyncBadge.Failed, syncBadgeFor(state, now))
    }

    @Test
    fun `both shapes of loading report refreshing`() {
        assertEquals(SyncBadge.Refreshing, syncBadgeFor(SyncState.Loading(), now))
        assertEquals(
            SyncBadge.Refreshing,
            syncBadgeFor(SyncState.Loading(cachedAt = now.minus(Duration.ofHours(9))), now),
        )
    }

    @Test
    fun `a timestamp from the future is treated as fresh, not stale`() {
        // Clock skew, or a device whose time moved backwards. Claiming
        // staleness from a negative age would be a guess.
        val state = SyncState.Ready(now.plus(Duration.ofHours(2)))
        assertEquals(SyncBadge.None, syncBadgeFor(state, now))
    }
}
