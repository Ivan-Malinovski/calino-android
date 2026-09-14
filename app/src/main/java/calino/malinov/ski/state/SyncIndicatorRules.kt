package calino.malinov.ski.state

import androidx.compose.runtime.staticCompositionLocalOf
import calino.malinov.ski.data.repository.SyncState
import java.time.Duration
import java.time.Instant

/**
 * What the calendar surfaces have to say about the last read, if anything.
 *
 * [SyncState] is richer than this on purpose: the Calendars screen shows the
 * full story, with a timestamp, the warnings, and a Refresh button. The month,
 * range, day and agenda headings get only this -- the smallest thing that is
 * still honest -- because a calendar is not a sync console.
 */
enum class SyncBadge {
    /** Nothing worth interrupting a calendar for: current, or no account. */
    None,
    Refreshing,
    /** Read cleanly, but long enough ago that it may no longer be true. */
    Stale,
    /** Read, but part of the calendar is missing and the user should know. */
    Incomplete,
    Failed,
}

/**
 * How old a clean read has to be before the calendar admits it is a cache.
 *
 * Nothing refreshes on a timer; a read happens at launch, on connect, and when
 * a person asks for one. So an app left open overnight is showing yesterday's
 * calendar with no outward difference from a fresh one, which is exactly the
 * failure this indicator exists for. Half an hour is long enough that ordinary
 * use never sees the marker and short enough that a forgotten window does.
 */
val SyncStaleAfter: Duration = Duration.ofMinutes(30)

/**
 * [state] reduced to what a calendar heading should show.
 *
 * A clock read from the future (skew, or a device whose time moved backwards)
 * counts as fresh rather than stale: claiming staleness from a negative age
 * would be a guess, and the quiet default is the honest one.
 */
fun syncBadgeFor(
    state: SyncState,
    now: Instant,
    staleAfter: Duration = SyncStaleAfter,
): SyncBadge = when (state) {
    // Idle is the fixture repository with no account connected. It has nothing
    // to read and cannot go stale, so the sample app shows no marker at all.
    SyncState.Idle -> SyncBadge.None
    is SyncState.Loading -> SyncBadge.Refreshing
    is SyncState.Failed -> SyncBadge.Failed
    is SyncState.Ready -> when {
        state.partial -> SyncBadge.Incomplete
        Duration.between(state.fetchedAt, now) >= staleAfter -> SyncBadge.Stale
        else -> SyncBadge.None
    }
}

/**
 * The sync story as the calendar surfaces see it, provided once at the app
 * root. It is a composition local rather than a parameter because every
 * calendar heading needs it and none of the layers between the root and the
 * heading have any business carrying it.
 */
data class CalinoSyncStatus(
    val state: SyncState = SyncState.Idle,
    /** Open the Calendars screen, where the detail and Refresh live. */
    val onOpenDetail: () -> Unit = {},
)

val LocalCalinoSync = staticCompositionLocalOf { CalinoSyncStatus() }
