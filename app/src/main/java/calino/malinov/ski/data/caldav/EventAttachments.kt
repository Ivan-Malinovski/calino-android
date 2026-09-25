package calino.malinov.ski.data.caldav

import biweekly.Biweekly
import biweekly.component.VEvent
import biweekly.property.Attachment
import calino.malinov.ski.data.model.EventAttachment

/**
 * The event's `ATTACH` properties (RFC 5545 §3.8.1.1).
 *
 * A link keeps its URI. Inline `VALUE=BINARY` data is not copied into the
 * model: every occurrence of a series would carry it, and the cached raw
 * resource already holds the bytes. [EventAttachment.inlineIndex] names the
 * property so [readInlineAttachment] can decode it when the person opens it.
 */
internal fun VEvent.readAttachments(): List<EventAttachment> =
    attachments.mapIndexedNotNull { index, attach ->
        val uri = attach.uri?.trim()?.takeIf(String::isNotEmpty)
        val data = attach.data
        when {
            uri != null -> EventAttachment(
                uri = uri,
                fileName = attach.fileName() ?: uriFileName(uri),
                mimeType = attach.formatType,
            )
            data != null -> EventAttachment(
                fileName = attach.fileName(),
                mimeType = attach.formatType,
                inlineIndex = index,
                sizeBytes = data.size,
            )
            else -> null
        }
    }

/**
 * Decodes the inline attachment [attachment] of the event [uid] from the raw
 * resource [ics]. Null when the resource no longer holds matching data.
 */
fun readInlineAttachment(ics: String, uid: String, attachment: EventAttachment): ByteArray? {
    val index = attachment.inlineIndex ?: return null
    val calendar = runCatching { Biweekly.parse(ics).first() }.getOrNull() ?: return null
    return calendar.events
        .filter { it.uid?.value == uid }
        .firstNotNullOfOrNull { event ->
            event.attachments.getOrNull(index)
                ?.takeIf { it.fileName() == attachment.fileName && it.data?.size == attachment.sizeBytes }
                ?.data
        }
}

/**
 * `FILENAME` (RFC 8607, and what Nextcloud writes -- as a path in the owner's
 * Files, such as `/Talk/agenda.pdf`), then the older `X-` spellings Apple and
 * others used. Only the last path segment is a name.
 */
private fun Attachment.fileName(): String? =
    listOf("FILENAME", "X-FILENAME", "X-APPLE-FILENAME")
        .firstNotNullOfOrNull { parameters.first(it) }
        ?.substringAfterLast('/')
        ?.trim()
        ?.takeIf(String::isNotEmpty)

internal fun uriFileName(uri: String): String? =
    uri.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        .takeIf { it.isNotEmpty() && '.' in it }
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

/**
 * Applies an attachment edit as a delta: every attachment in [removed] is taken
 * off the component, and each link or picked file ([EventAttachment.data]) in
 * [added] is added unless the link is already there. Anything else -- including
 * an `ATTACH` another client added after the editor opened, which a patch onto
 * refreshed server bytes will meet -- is left byte-identical with its foreign
 * parameters (Nextcloud's `X-NC-FILE-ID`, say).
 *
 * A removed link matches by URI; a removed inline file by name and decoded
 * size, never by position, since the server's list may have shifted.
 */
internal fun VEvent.writeAttachments(added: List<EventAttachment>, removed: List<EventAttachment>) {
    removed.forEach { gone ->
        val uri = gone.uri?.trim()
        this.attachments.firstOrNull { attach ->
            if (uri != null) {
                attach.uri?.trim() == uri
            } else {
                attach.data != null && attach.fileName() == gone.fileName && attach.data.size == gone.sizeBytes
            }
        }?.let(::removeProperty)
    }
    val present = this.attachments.mapNotNull { it.uri?.trim() }.toSet()
    added.filter { it.uri != null && it.uri.trim() !in present }.distinctBy { it.uri!!.trim() }.forEach { link ->
        addProperty(
            Attachment(link.mimeType, link.uri!!.trim()).apply {
                link.fileName?.let { parameters.put("FILENAME", it) }
            },
        )
    }
    added.filter { it.uri == null && it.inlineIndex == null && it.data != null }.forEach { file ->
        addProperty(
            Attachment(file.mimeType ?: "application/octet-stream", file.data!!).apply {
                file.fileName?.let { parameters.put("FILENAME", it) }
            },
        )
    }
}
