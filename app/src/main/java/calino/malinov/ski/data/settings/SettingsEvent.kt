package calino.malinov.ski.data.settings

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The VEVENT that carries the settings payload, byte-compatible with the web
 * app's `Calino Settings` event.
 *
 * A dedicated calendar holds exactly one event with the literal UID
 * `calino-settings`; the payload rides in `ATTACH;ENCODING=BASE64;FMTTYPE=app/json`.
 * The event is parked at the Unix epoch, transparent and private, so no calendar
 * view will ever draw it.
 *
 * The one part that needs care is folding. iCalendar folds content lines at 75
 * octets, and a base64 payload is always longer than that, so a reader that does
 * not unfold sees a truncated payload and a writer that does not fold gets lines
 * some servers reject. Base64 is ASCII, so octets and characters coincide here.
 */
object SettingsEvent {
    const val Uid = "calino-settings"
    const val FileName = "calino-settings.ics"
    const val CollectionName = "calino-settings"
    const val CollectionDisplayName = "Calino Settings"

    /** The dead property the web app sets on the collection; discovery reads it too. */
    const val MarkerPropertyLocalName = "X-CALINO-SETTINGS-CALENDAR"

    private const val AttachPrefix = "ATTACH;ENCODING=BASE64;FMTTYPE=app/json:"
    private const val FoldOctets = 75
    private val DtStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** The whole resource, folded and CRLF-terminated as RFC 5545 requires. */
    fun build(payloadJson: String, stamp: Instant): String {
        val encoded = java.util.Base64.getEncoder().encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        val lines = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Calino//Settings Sync//EN",
            "BEGIN:VEVENT",
            "UID:$Uid",
            "DTSTAMP:${DtStamp.format(stamp)}",
            "DTSTART:19700101T000000Z",
            "DTEND:19700101T000001Z",
            "SUMMARY:$CollectionDisplayName",
            "TRANSP:TRANSPARENT",
            "CLASS:PRIVATE",
            "X-CALINO-VERSION:${SettingsSyncPayload.CurrentVersion}",
            fold(AttachPrefix + encoded),
            "END:VEVENT",
            "END:VCALENDAR",
            "",
        )
        return lines.joinToString("\r\n")
    }

    /** The payload as written, or null when the resource has no readable ATTACH. */
    fun payload(ics: String): String? {
        val attach = unfold(ics).lineSequence()
            .firstOrNull { it.startsWith("ATTACH", ignoreCase = true) && it.contains(':') }
            ?: return null
        val encoded = attach.substringAfter(':').trim()
        if (encoded.isEmpty()) return null
        return runCatching {
            String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        }.getOrNull()
    }

    /** The remote timestamp the conflict rule compares, or null when absent. */
    fun stamp(ics: String): Instant? {
        val raw = unfold(ics).lineSequence()
            .firstOrNull { it.startsWith("DTSTAMP:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?: return null
        return runCatching { Instant.from(DtStamp.parse(raw)) }.getOrNull()
    }

    /**
     * Folds one content line at 75 octets, continuations starting with a single
     * space. The first line takes a full 75; each continuation takes 74 so the
     * leading space keeps every line within the limit.
     */
    private fun fold(line: String): String {
        if (line.length <= FoldOctets) return line
        val out = StringBuilder(line.length + line.length / 74 + 2)
        out.append(line, 0, FoldOctets)
        var index = FoldOctets
        while (index < line.length) {
            val end = minOf(index + FoldOctets - 1, line.length)
            out.append("\r\n ").append(line, index, end)
            index = end
        }
        return out.toString()
    }

    /** Rejoins folded lines; a continuation is a line starting with space or tab. */
    private fun unfold(ics: String): String =
        ics.replace("\r\n", "\n").replace("\r", "\n").replace("\n ", "").replace("\n\t", "")
}
