package calino.malinov.ski.data.repository

import java.io.IOException

/**
 * A write that was never attempted because the device had no network.
 *
 * It is an [IOException] on purpose: the pending-change policy already treats a
 * transport failure as a retryable, uncounted one, so an offline save lands in
 * the queue exactly like a write whose connection dropped — it just does so
 * without waiting for a socket to give up first.
 */
internal class OfflineWriteException :
    IOException("Offline: the change is saved here and syncs when the network is back.")
