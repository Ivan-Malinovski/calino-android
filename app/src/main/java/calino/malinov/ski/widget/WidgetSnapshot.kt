package calino.malinov.ski.widget

import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.repository.CalinoSnapshot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** How long a widget render waits for the cache to be read off disk. */
private const val CacheWaitMillis = 2_000L

/**
 * The first snapshot that has anything in it, or whatever there is after
 * [CacheWaitMillis].
 *
 * `CalDavRepository` publishes its disk cache asynchronously, and
 * `CalinoRepository.observe` calls back immediately with the *current*
 * snapshot -- which on a cold start is the empty one it was constructed with.
 * Taking that first callback would render a blank widget every time the
 * launcher woke a fresh process, so this waits for a publish that actually
 * carries records.
 *
 * It gives up rather than hanging: an account whose cache is genuinely empty,
 * or a first run before any sync, must still draw something. The timeout is the
 * honest answer there, not a bug to tune away.
 */
suspend fun awaitCachedSnapshot(container: CalinoContainer): CalinoSnapshot {
    val repository = container.activeRepository
    val immediate = repository.snapshot()
    if (immediate.hasRecords) return immediate

    val settled = withTimeoutOrNull(CacheWaitMillis) {
        var handle: Closeable? = null
        try {
            suspendCancellableCoroutine { continuation ->
                // `observe` fires synchronously on registration, so the
                // continuation can be resumed before `handle` is even assigned.
                // The flag keeps that from resuming twice; the `finally` below
                // is what actually unsubscribes, on every path.
                val resumed = AtomicBoolean(false)
                handle = repository.observe { snapshot ->
                    if (snapshot.hasRecords && resumed.compareAndSet(false, true)) {
                        continuation.resume(snapshot)
                    }
                }
            }
        } finally {
            handle?.close()
        }
    }
    return settled ?: repository.snapshot()
}

private val CalinoSnapshot.hasRecords: Boolean
    get() = events.isNotEmpty() || tasks.isNotEmpty()
