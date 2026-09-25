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

private fun uriFileName(uri: String): String? =
    uri.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        .takeIf { it.isNotEmpty() && '.' in it }
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

/**
 * Makes the component's link `ATTACH`es match [attachments]: a link that left
 * the list is removed, a new one is added. A kept link stays byte-identical
 * with every foreign parameter (Nextcloud's `X-NC-FILE-ID`, say), and inline
 * data is never touched -- Calino does not author it.
 */
internal fun VEvent.writeAttachmentLinks(attachments: List<EventAttachment>) {
    val wanted = attachments.mapNotNull { it.uri?.trim()?.takeIf(String::isNotEmpty) }
    this.attachments.filter { it.uri != null && it.uri.trim() !in wanted }.forEach(::removeProperty)
    val present = this.attachments.mapNotNull { it.uri?.trim() }.toSet()
    attachments.filter { it.uri != null && it.uri.trim() !in present }.distinctBy { it.uri!!.trim() }.forEach { link ->
        addProperty(
            Attachment(link.mimeType, link.uri!!.trim()).apply {
                link.fileName?.let { parameters.put("FILENAME", it) }
            },
        )
    }
}
