package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalendarProtocol
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps one JMAP event stream open per JMAP account while the app is in the
 * foreground, and asks for a normal sync when the server reports a change.
 *
 * A stream event is only a hint: nothing in it is applied. Background work stays
 * with the periodic job; there is no push service involved, and nothing runs
 * when the app is not visible. An account whose login the server refuses is
 * left alone until its credentials or the account change.
 */
class JmapPushController(
    private val scope: CoroutineScope,
    private val accounts: () -> List<CalDavAccount>,
    private val credentials: (accountId: String) -> DavCredentials?,
    private val clients: JmapClientProvider,
    private val requestSync: suspend () -> Unit,
    private val timing: EventStreamTiming = EventStreamTiming(),
    private val debounceMillis: Long = 750,
) {
    private class Running(val key: String, val job: Job)

    private val running = ConcurrentHashMap<String, Running>()
    private val hints = Channel<Unit>(Channel.CONFLATED)
    private var syncJob: Job? = null
    private var foreground = false

    fun setForeground(active: Boolean) {
        synchronized(this) { foreground = active }
        reconcile()
    }

    /** Starts streams for JMAP accounts that lack one and stops those that no longer apply. */
    fun reconcile() {
        synchronized(this) {
            val wanted = if (!foreground) emptyMap() else accounts()
                .filter { it.protocol == CalendarProtocol.Jmap }
                .mapNotNull { account -> credentials(account.id)?.let { account to it } }
                .associate { (account, credentials) ->
                    account.id to Triple(account, credentials, "${account.serverUrl}\u0000${credentials.username}\u0000${credentials.password.hashCode()}")
                }
            for ((id, entry) in running.entries.toList()) {
                if (wanted[id]?.third != entry.key) {
                    entry.job.cancel()
                    running.remove(id, entry)
                }
            }
            for ((id, wantedEntry) in wanted) {
                val (account, creds, key) = wantedEntry
                if (running.containsKey(id)) continue
                running[id] = Running(key, scope.launch { watch(account, creds) })
            }
            if (running.isNotEmpty() && syncJob?.isActive != true) {
                syncJob = scope.launch {
                    for (hint in hints) {
                        delay(debounceMillis)
                        hints.tryReceive()
                        runCatching { requestSync() }
                    }
                }
            } else if (running.isEmpty()) {
                syncJob?.cancel()
                syncJob = null
            }
        }
    }

    private suspend fun watch(account: CalDavAccount, creds: DavCredentials) {
        var attempts = 0
        while (scope.isActive) {
            try {
                val client = clients.client(account.serverUrl, creds)
                val calendarAccount = client.accountId
                client.watchStateChanges(listOf("Calendar", "CalendarEvent"), timing) { change ->
                    if (change.touches("CalendarEvent", calendarAccount) || change.touches("Calendar", calendarAccount)) {
                        hints.trySend(Unit)
                    }
                }
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val code = (error as? CalDavException)?.code
                if (code == CalDavErrorCode.Auth || code == CalDavErrorCode.Forbidden) return
            }
            delay(minOf(timing.initialRetryMillis shl minOf(attempts++, 6), timing.maxBackoffMillis))
        }
    }
}
