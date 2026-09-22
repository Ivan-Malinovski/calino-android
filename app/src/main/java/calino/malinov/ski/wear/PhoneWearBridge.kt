package calino.malinov.ski.wear

import android.content.Context
import android.text.format.DateFormat
import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.wearcontract.SNAPSHOT_PATH
import calino.malinov.ski.wearcontract.WearCodec
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.Closeable
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PhoneWearBridge(context: Context) {
    private val application = context.applicationContext
    private val preferences = application.getSharedPreferences("wear_source", Context.MODE_PRIVATE)
    val sourceEpoch: String = preferences.getString("epoch", null)
        ?: UUID.randomUUID().toString().also { preferences.edit().putString("epoch", it).apply() }
    private val sequence = AtomicLong(preferences.getLong("sequence", 0))
    private val snapshots = MutableSharedFlow<CalinoSnapshot>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var subscription: Closeable? = null
    private var authoritative: (() -> Boolean)? = null
    private val publishMutex = Mutex()

    val sourceSequence: Long get() = sequence.get()

    fun attach(
        repository: CalinoRepository,
        scope: CoroutineScope,
        isAuthoritativeRealData: () -> Boolean,
    ): Closeable {
        detach()
        authoritative = isAuthoritativeRealData
        val job = scope.launch {
            snapshots.collectLatest { snapshot ->
                publishIfChanged(snapshot)
                PhoneWearCommandCoordinator.get(application).retry()
            }
        }
        val observed = repository.observe { snapshots.tryEmit(it) }
        subscription = Closeable {
            observed.close()
            job.cancel()
        }
        return Closeable(::detach)
    }

    suspend fun publishIfChanged(snapshot: CalinoSnapshot, force: Boolean = false): Long? = publishMutex.withLock {
        if (authoritative?.invoke() != true) return@withLock null
        val normalized = WearProjection.build(
            snapshot,
            LocalDate.now(),
            ZoneId.systemDefault(),
            sourceEpoch,
            sequence = 0,
            h24 = DateFormat.is24HourFormat(application),
            generatedAtMillis = 0,
        )
        val fingerprint = WearCodec.encodeSnapshotContent(normalized).sha256()
        if (!force && fingerprint.contentEquals(preferences.getString("fingerprint", null)?.hexBytes())) {
            return@withLock null
        }

        val nextSequence = sequence.get() + 1
        val projection = normalized.copy(
            sequence = nextSequence,
            generatedAtMillis = System.currentTimeMillis(),
        )
        val request = PutDataMapRequest.create(SNAPSHOT_PATH).apply {
            dataMap.putByteArray("payload", WearCodec.encodeSnapshot(projection))
            dataMap.putLong("sequence", nextSequence)
        }.asPutDataRequest().setUrgent()
        val published = runCatching {
            Wearable.getDataClient(application).putDataItem(request).awaitTask()
        }.isSuccess
        if (!published) return@withLock null
        sequence.set(nextSequence)
        preferences.edit()
            .putLong("sequence", nextSequence)
            .putString("fingerprint", fingerprint.hex())
            .apply()
        nextSequence
    }

    fun detach() {
        subscription?.close()
        subscription = null
        authoritative = null
    }
}

private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun String.hexBytes(): ByteArray? = runCatching {
    if (length % 2 != 0) return null
    ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}.getOrNull()

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
