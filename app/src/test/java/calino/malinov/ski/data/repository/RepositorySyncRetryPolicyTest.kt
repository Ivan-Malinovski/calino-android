package calino.malinov.ski.data.repository

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositorySyncRetryPolicyTest {
    @Test
    fun pendingOrRetryingWritesKeepTheWorkerEligible() {
        assertTrue(
            RepositorySyncRetryPolicy.retryNeeded(
                transientReadFailure = false,
                changes = listOf(change("a", PendingChangeState.RETRY), change("b", PendingChangeState.PENDING)),
            ),
        )
    }

    @Test
    fun deadLettersAloneDoNotCauseUnboundedWorkerRetries() {
        assertFalse(
            RepositorySyncRetryPolicy.retryNeeded(
                transientReadFailure = false,
                changes = listOf(change("a", PendingChangeState.DEAD_LETTER)),
            ),
        )
    }

    @Test
    fun deadLetterHoldsOnlyLaterWritesForTheSameItem() {
        val sameItem = listOf(
            change("a", PendingChangeState.DEAD_LETTER),
            change("a", PendingChangeState.PENDING),
        )
        assertEquals(
            "A failed change is holding up later changes to the same item. Review queued changes.",
            RepositorySyncRetryPolicy.pendingQueueMessage(sameItem),
        )
        assertFalse(RepositorySyncRetryPolicy.retryNeeded(transientReadFailure = false, changes = sameItem))

        val otherItem = listOf(
            change("a", PendingChangeState.DEAD_LETTER),
            change("b", PendingChangeState.PENDING),
        )
        assertEquals(
            "A saved change needs attention. Review queued changes.",
            RepositorySyncRetryPolicy.pendingQueueMessage(otherItem),
        )
        assertTrue(RepositorySyncRetryPolicy.retryNeeded(transientReadFailure = false, changes = otherItem))
    }

    @Test
    fun transientReadFailuresRetryEvenWhenTheQueueHasOnlyDeadLetters() {
        assertTrue(
            RepositorySyncRetryPolicy.retryNeeded(
                transientReadFailure = true,
                changes = listOf(change("a", PendingChangeState.DEAD_LETTER)),
            ),
        )
    }

    private var nextId = 0

    private fun change(eventId: String, state: PendingChangeState) = PendingChange(
        id = "change-${nextId++}",
        type = PendingChangeType.UPDATE,
        eventId = eventId,
        accountId = "account-1",
        calendarId = "calendar-1",
        component = "VEVENT",
        uid = "uid-$eventId",
        timestamp = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        state = state,
    )
}
