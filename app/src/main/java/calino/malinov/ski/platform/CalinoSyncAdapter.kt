package calino.malinov.ski.platform

import android.accounts.Account
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import calino.malinov.ski.data.CalinoContainer
import kotlinx.coroutines.runBlocking

/**
 * The sync adapter Android schedules when a calendar app has edited one of
 * Calino's rows.
 *
 * It is the *inbound* trigger only. Outbound projection is driven by the
 * repository publishing, through [CalendarProjectionBridge], because that is
 * where a change to Calino's data actually becomes known -- not on a timer the
 * app does not control.
 *
 * The order of the two things it does matters and is not interchangeable:
 * ingest first, project second. Projecting first would rewrite the very rows
 * carrying the edits that have not been read yet, and the person's change
 * would disappear with no trace that it ever existed.
 */
class CalinoSyncAdapter(context: Context) : AbstractThreadedSyncAdapter(
    context,
    /* autoInitialize = */ true,
    /* allowParallelSyncs = */ false,
) {

    /**
     * Runs on a background thread the framework owns, so blocking here is
     * what the contract asks for: returning early would tell Android the sync
     * finished while the writes were still in flight.
     */
    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult,
    ) {
        val container = CalinoContainer.get(context)
        if (!container.calendarProjectionEnabled) return

        // The repository has to be able to reach the server for a write to be
        // applied rather than queued, and this may be a process the framework
        // woke with nothing else running in it.
        container.ensureConnected()

        val changes = CalendarIngest.collect(context, account)
        if (changes.isNotEmpty()) {
            val report = runBlocking {
                CalendarIngest.apply(context, account, container.activeRepository, changes)
            }
            // A rejected write leaves the provider holding something Calino
            // refused. Counting it tells the framework the sync was not
            // wholly clean without asking for a retry that would refuse again.
            syncResult.stats.numSkippedEntries += report.rejected.toLong()
        }

        // Unconditional, and deliberately not gated on having ingested
        // something: a rejected edit changes no snapshot, so nothing else
        // would ever put the row back.
        container.projectCalendars()
    }
}

/**
 * The bound service the framework resolves the adapter through. The adapter is
 * shared across binds because Android may sync several accounts at once and
 * `AbstractThreadedSyncAdapter` is built for exactly that.
 */
class CalinoSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = adapter(this).syncAdapterBinder

    companion object {
        @Volatile
        private var instance: CalinoSyncAdapter? = null

        private fun adapter(context: Context): CalinoSyncAdapter =
            instance ?: synchronized(this) {
                instance ?: CalinoSyncAdapter(context.applicationContext).also { instance = it }
            }
    }
}
