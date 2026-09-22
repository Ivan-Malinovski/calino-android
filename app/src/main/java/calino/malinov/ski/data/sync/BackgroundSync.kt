package calino.malinov.ski.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import calino.malinov.ski.data.CalinoContainer
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** The requested cadence. WorkManager and Android may run later than requested. */
enum class BackgroundSyncCadence(
    val label: String,
    val shortLabel: String,
    val intervalHours: Long?,
) {
    Hourly("Hourly", "1 h", 1),
    FourHours("Every 4 hours", "4 h", 4),
    TwelveHours("Every 12 hours", "12 h", 12),
    Daily("Every 24 hours", "24 h", 24),
    Off("Off", "Off", null),
}

data class BackgroundSyncStatus(
    val lastAttemptEpochMs: Long? = null,
    val lastSuccessEpochMs: Long? = null,
    val lastFailure: String? = null,
)

/**
 * Small durable settings/status store shared by Settings and the WorkManager
 * worker. It contains no account identifiers or calendar content.
 */
class BackgroundSyncStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun cadence(): BackgroundSyncCadence = runCatching {
        BackgroundSyncCadence.valueOf(preferences.getString(KeyCadence, null).orEmpty())
    }.getOrDefault(BackgroundSyncCadence.Hourly)

    fun setCadence(cadence: BackgroundSyncCadence) {
        preferences.edit().putString(KeyCadence, cadence.name).commit()
    }

    fun status(): BackgroundSyncStatus = BackgroundSyncStatus(
        lastAttemptEpochMs = preferences.getLong(KeyLastAttempt, 0L).takeIf { it > 0 },
        lastSuccessEpochMs = preferences.getLong(KeyLastSuccess, 0L).takeIf { it > 0 },
        lastFailure = preferences.getString(KeyLastFailure, null),
    )

    fun recordAttempt(at: Instant = Instant.now()) {
        preferences.edit().putLong(KeyLastAttempt, at.toEpochMilli()).commit()
    }

    fun recordSuccess(at: Instant = Instant.now()) {
        preferences.edit()
            .putLong(KeyLastSuccess, at.toEpochMilli())
            .remove(KeyLastFailure)
            .commit()
    }

    fun recordFailure(message: String) {
        preferences.edit().putString(KeyLastFailure, message.trim().take(240)).commit()
    }

    fun observe(listener: (BackgroundSyncStore) -> Unit): AutoCloseable {
        val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            listener(this)
        }
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        listener(this)
        return AutoCloseable { preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener) }
    }

    companion object {
        private const val PreferencesName = "background_sync"
        private const val KeyCadence = "cadence"
        private const val KeyLastAttempt = "last_attempt"
        private const val KeyLastSuccess = "last_success"
        private const val KeyLastFailure = "last_failure"
    }
}

/** Scheduling rules are kept pure so cadence and opt-out behavior are testable. */
object BackgroundSyncScheduleRules {
    fun shouldSchedulePeriodic(hasAccounts: Boolean, cadence: BackgroundSyncCadence): Boolean =
        hasAccounts && cadence.intervalHours != null

    fun shouldEnqueueImmediate(hasAccounts: Boolean, cadence: BackgroundSyncCadence): Boolean =
        hasAccounts && cadence != BackgroundSyncCadence.Off
}

/** One scheduler owns the names, constraints, and account/cadence policy. */
class BackgroundSyncScheduler(context: Context) {
    private val application = context.applicationContext
    private val workManager = WorkManager.getInstance(application)

    fun reconcile(hasAccounts: Boolean, cadence: BackgroundSyncCadence) {
        if (!BackgroundSyncScheduleRules.shouldSchedulePeriodic(hasAccounts, cadence)) {
            cancel()
            return
        }
        val hours = checkNotNull(cadence.intervalHours)
        workManager.enqueueUniquePeriodicWork(
            PeriodicWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BackgroundSyncWorker>(hours, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .addTag(WorkTag)
                .build(),
        )
    }

    fun enqueueImmediate(hasAccounts: Boolean, cadence: BackgroundSyncCadence) {
        if (!BackgroundSyncScheduleRules.shouldEnqueueImmediate(hasAccounts, cadence)) return
        workManager.enqueueUniqueWork(
            ImmediateWorkName,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setConstraints(networkConstraints())
                .addTag(WorkTag)
                .build(),
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(PeriodicWorkName)
        workManager.cancelUniqueWork(ImmediateWorkName)
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        const val PeriodicWorkName = "calino.background-sync.periodic"
        const val ImmediateWorkName = "calino.background-sync.immediate"
        private const val WorkTag = "calino.background-sync"
    }
}

/** Executes through the process-wide container and its existing stores. */
class BackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = CalinoContainer.get(applicationContext)
        val status = BackgroundSyncStore(applicationContext)
        val cadence = status.cadence()
        if (container.accountStore.accounts().isEmpty()) {
            BackgroundSyncScheduler(applicationContext).reconcile(false, cadence)
            return Result.success()
        }
        if (cadence == BackgroundSyncCadence.Off) return Result.success()

        status.recordAttempt()
        return try {
            container.startBackgroundSyncBridges()
            when (val result = container.syncConnectedAccounts()) {
                is calino.malinov.ski.data.repository.RepositorySyncResult.Success -> {
                    if (result.retryNeeded) {
                        status.recordFailure(result.message ?: "Some changes still need to sync.")
                        Result.retry()
                    } else {
                        status.recordSuccess()
                        result.warnings.firstOrNull()?.let(status::recordFailure)
                        Result.success()
                    }
                }
                is calino.malinov.ski.data.repository.RepositorySyncResult.Failed -> {
                    status.recordFailure(result.message)
                    if (result.retryable) Result.retry() else Result.failure()
                }
                calino.malinov.ski.data.repository.RepositorySyncResult.NoSources -> {
                    status.recordSuccess()
                    Result.success()
                }
                calino.malinov.ski.data.repository.RepositorySyncResult.AccountsRemoved -> Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            status.recordFailure(error.message ?: "Background sync stopped unexpectedly.")
            Result.failure()
        }
    }
}
