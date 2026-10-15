package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SseEvent(val event: String, val data: String, val id: String?)

/** Incremental server-sent-events framing, including CR/LF split across chunks and multi-line data. */
class SseParser(
    private val onEvent: (SseEvent) -> Unit,
    private val onRetry: (Long) -> Unit = {},
) {
    private val buffer = StringBuilder()
    private val data = mutableListOf<String>()
    private var event = ""
    private var id: String? = null
    private var first = true

    fun feed(chunk: String) {
        var text = chunk
        if (first && text.isNotEmpty()) {
            text = text.removePrefix("\uFEFF")
            first = false
        }
        buffer.append(text)
        while (true) {
            val index = buffer.indexOfFirst { it == '\r' || it == '\n' }
            // A trailing CR may be the first half of CRLF; wait for the next chunk.
            if (index < 0 || (buffer[index] == '\r' && index == buffer.length - 1)) return
            val line = buffer.substring(0, index)
            val length = if (buffer[index] == '\r' && buffer[index + 1] == '\n') 2 else 1
            buffer.delete(0, index + length)
            line(line)
        }
    }

    private fun line(line: String) {
        if (line.isEmpty()) {
            if (data.isNotEmpty()) onEvent(SseEvent(event.ifEmpty { "message" }, data.joinToString("\n"), id))
            data.clear()
            event = ""
            return
        }
        if (line.startsWith(":")) return
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
        when {
            field == "data" -> data += value
            field == "event" -> event = value
            field == "id" && '\u0000' !in value -> id = value
            field == "retry" && value.isNotEmpty() && value.all { it in '0'..'9' } ->
                value.toLongOrNull()?.let { onRetry(minOf(it, MaxBackoffMillis)) }
        }
    }
}

/** A validated `StateChange`: account id to (data type to state). */
data class JmapStateChange(val changed: Map<String, Map<String, String>>) {
    /** True when [type] moved for [accountId], or for any account when it is null. */
    fun touches(type: String, accountId: String? = null): Boolean =
        changed.any { (account, states) -> (accountId == null || account == accountId) && type in states }
}

internal fun parseStateChange(data: String): JmapStateChange? {
    val json = runCatching { JSONObject(data) }.getOrNull() ?: return null
    if (json.stringOrNull("@type") != "StateChange") return null
    val changed = json.objectOrNull("changed") ?: return null
    val result = linkedMapOf<String, Map<String, String>>()
    for (account in changed.keyList()) {
        val states = changed.objectOrNull(account) ?: return null
        result[account] = states.keyList().associateWith { states.stringOrNull(it) ?: return null }
    }
    return JmapStateChange(result)
}

private const val MaxBackoffMillis = 60_000L

/** Tunables, replaceable in tests. */
data class EventStreamTiming(
    val pingSeconds: Int = 30,
    val initialRetryMillis: Long = 1_000,
    val maxBackoffMillis: Long = MaxBackoffMillis,
    val watchdogMillis: (pingSeconds: Int) -> Long = { (it * 2L + 10L) * 1000L },
)

/**
 * Holds the event stream open until cancelled, reporting each state change as a
 * hint. Reconnects with exponential back-off (to a minute), honours
 * `Retry-After`, resumes with `Last-Event-ID` and abandons a stream that has
 * been silent for twice the ping interval plus ten seconds. A rejected login or
 * a forbidden stream ends the loop: retrying would only repeat the failure.
 * Nothing in an event is applied; the caller runs its normal sync.
 */
internal suspend fun JmapClient.watchStateChanges(
    types: List<String>,
    timing: EventStreamTiming = EventStreamTiming(),
    onStateChange: (JmapStateChange) -> Unit,
) {
    var attempts = 0
    var retryMillis = timing.initialRetryMillis
    var lastId: String? = null
    while (currentCoroutineContext().isActive) {
        var retryAfterMillis = 0L
        try {
            val stream = openEventStream(types, lastId, timing.pingSeconds)
            stream.use { open ->
                when {
                    open.status == 401 || open.status == 403 -> throw httpError(open.status, "", null)
                    open.status != 200 -> throw httpError(open.status, open.readBounded(), open.header("Retry-After"))
                    open.header("Content-Type")?.contains("text/event-stream", ignoreCase = true) != true ->
                        throw JmapException(CalDavErrorCode.NotCalDav, "The server did not return an event stream.", type = "invalidResponse")
                }
                var intervalSeconds = timing.pingSeconds
                var lastActivity = System.nanoTime()
                val parser = SseParser(
                    onEvent = { event ->
                        lastId = event.id ?: lastId
                        attempts = 0
                        when (event.event) {
                            "ping" -> runCatching { JSONObject(event.data).optInt("interval", 0) }.getOrDefault(0)
                                .takeIf { it > 0 }?.let { intervalSeconds = it }
                            "state", "message" -> parseStateChange(event.data)?.let(onStateChange)
                        }
                    },
                    onRetry = { retryMillis = maxOf(timing.initialRetryMillis, it) },
                )
                coroutineScope {
                    val watchdog = launch {
                        while (true) {
                            val limit = timing.watchdogMillis(intervalSeconds)
                            val idle = (System.nanoTime() - lastActivity) / 1_000_000
                            if (idle >= limit) {
                                open.cancel()
                                return@launch
                            }
                            delay(limit - idle)
                        }
                    }
                    try {
                        withContext(Dispatchers.IO) {
                            val buffer = ByteArray(4096)
                            val decoder = Utf8ChunkDecoder()
                            while (true) {
                                val count = open.read(buffer)
                                if (count < 0) break
                                lastActivity = System.nanoTime()
                                parser.feed(decoder.decode(buffer, count))
                            }
                        }
                    } finally {
                        watchdog.cancel()
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val status = (error as? CalDavException)?.status
            if (status == 401 || status == 403 || (error as? CalDavException)?.code in setOf(CalDavErrorCode.Auth, CalDavErrorCode.Forbidden)) {
                return
            }
            retryAfterMillis = ((error as? JmapException)?.retryAfter ?: 0) * 1000L
        }
        val backoff = minOf(retryMillis shl minOf(attempts++, 6), timing.maxBackoffMillis)
        delay(maxOf(retryAfterMillis, backoff))
    }
}

/** Decodes UTF-8 split across reads without corrupting a character at a chunk boundary. */
internal class Utf8ChunkDecoder {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    fun decode(bytes: ByteArray, count: Int): String {
        val input = java.nio.ByteBuffer.wrap(pending + bytes.copyOf(count))
        val output = java.nio.CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, output, false)
        pending = ByteArray(input.remaining()).also { input.get(it) }
        output.flip()
        return output.toString()
    }
}
