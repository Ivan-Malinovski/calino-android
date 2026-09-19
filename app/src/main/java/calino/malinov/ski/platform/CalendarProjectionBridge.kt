package calino.malinov.ski.platform

import android.content.Context
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.data.repository.CalinoSnapshot
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the calendar provider level with the repository.
 *
 * The same shape and the same reasoning as `widget/CalinoWidgetBridge`:
 * `CalDavRepository.publish()` is the funnel every change passes through, so
 * observing the repository *is* "project on sync". Conflating matters more
 * here than for the widget -- one reload publishes several times, and each
 * publish would otherwise be a full provider reconcile.
 *
 * The pass runs off the main thread: a first projection of a large account is
 * thousands of provider operations.
 */
class CalendarProjectionBridge(
    context: Context,
    private val accounts: () -> List<CalDavAccount>,
    /** The opted-in calendar ids, or null while every visible one is projected. */
    private val optedIn: () -> Set<String>?,
) {

    private val application = context.applicationContext

    private val snapshots = MutableSharedFlow<CalinoSnapshot>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var subscription: Closeable? = null

    /** Follow [repository] until the returned handle is closed. */
    fun attach(repository: CalinoRepository, scope: CoroutineScope): Closeable {
        detach()
        val job = scope.launch {
            snapshots.collectLatest { snapshot ->
                withContext(Dispatchers.IO) { project(snapshot) }
            }
        }
        val handle = repository.observe { snapshot -> snapshots.tryEmit(snapshot) }
        subscription = Closeable {
            handle.close()
            job.cancel()
        }
        return Closeable { detach() }
    }

    fun detach() {
        subscription?.close()
        subscription = null
    }

    /**
     * Run a pass now, off whatever the repository currently holds.
     *
     * For the sync adapter, which has just ingested foreign edits and must
     * put the provider back in agreement even when the repository refused
     * them and therefore published nothing.
     */
    fun projectNow(snapshot: CalinoSnapshot) = project(snapshot)

    private fun project(snapshot: CalinoSnapshot) {
        val connected = accounts()
        val visible = snapshot.calendars.filter { it.visible }.map { it.id }.toSet()
        CalendarProjection.sync(
            application,
            ProviderIdentity.project(
                snapshot = snapshot,
                accounts = connected,
                optedIn = optedIn() ?: visible,
            ),
            // Editable, now that a foreign edit is read back rather than
            // discarded. A collection Calino itself cannot write stays
            // read-only regardless -- CalendarProjection.accessLevel decides.
            writable = true,
        )
    }
}
