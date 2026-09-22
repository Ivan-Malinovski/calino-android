package calino.malinov.ski.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositorySyncRetryPolicyTest {
    @Test
    fun pendingOrRetryingWritesKeepTheWorkerEligible() {
        assertTrue(
            RepositorySyncRetryPolicy.retryNeeded(
                transientReadFailure = false,
                pendingStates = listOf(PendingChangeState.RETRY, PendingChangeState.PENDING),
            ),
        )
    }

    @Test
    fun deadLettersAloneDoNotCauseUnboundedWorkerRetries() {
        assertFalse(
            RepositorySyncRetryPolicy.retryNeeded(
                transientReadFailure = false,
                pendingStates = listOf(PendingChangeState.DEAD_LETTER),
            ),
        )
    }

    @Test
    fun aDeadLetterDoesNotMaskAnotherPendingWrite() {
        assertTrue(
            RepositorySyncRetryPolicy.retryNeeded(
                transientReadFailure = false,
                pendingStates = listOf(PendingChangeState.DEAD_LETTER, PendingChangeState.PENDING),
            ),
        )
    }

    @Test
    fun transientReadFailuresRetryEvenWhenTheQueueHasOnlyDeadLetters() {
        assertTrue(
            RepositorySyncRetryPolicy.retryNeeded(
                transientReadFailure = true,
                pendingStates = listOf(PendingChangeState.DEAD_LETTER),
            ),
        )
    }
}
