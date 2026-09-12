package calino.malinov.ski.poc.widget

import android.content.Context
import calino.malinov.ski.poc.data.repository.CalinoRepository
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the home screen widget level with the repository.
 *
 * The same shape, and the same reasoning, as
 * `notify/ReminderSchedulerBridge`: `CalDavRepository.publish()` is the one
 * funnel every change passes through -- an optimistic edit, a queue replay, a
 * sync reconciliation -- so observing the repository *is* "update on sync", and
 * no new hook is needed. And for the same reason it needs conflating: a single
 * reload publishes several times, and each publish would otherwise be a full
 * widget render.
 */
class CalinoWidgetBridge(context: Context) {

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
            snapshots.collectLatest { CalinoWidgets.update(application) }
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
}
