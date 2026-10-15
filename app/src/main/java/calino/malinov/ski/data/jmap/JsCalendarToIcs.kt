package calino.malinov.ski.data.jmap

import java.io.ByteArrayOutputStream
import java.util.Base64
import org.json.JSONObject

/**
 * JSCalendar (RFC 8984) to iCalendar (RFC 5545), at the JMAP backend boundary.
 *
 * The rest of the app stays iCalendar-shaped, so a JMAP event is converted
 * once on read and cached as ordinary `.ics` text. Output is deterministic --
 * fixed property order, sorted map keys, CRLF, folded at 75 octets -- so two
 * reads of the same event produce the same bytes. No `VTIMEZONE` is written;
 * `ICalWriter.prepareForWrite` builds those from the TZIDs.
 *
 * Anything that cannot be expressed throws [JsCalendarException]: a custom
 * `timeZones` table, a non-Gregorian `rscale`, a malformed duration, a bad
 * PatchObject. The caller skips that event instead of caching a wrong one.
 */
internal object JsCalendarToIcs {

    const val DefaultProdId = "-//Calino//JMAP Calendar//EN"

    fun convert(event: JSONObject, prodId: String = DefaultProdId, calendarName: String? = null): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:${icsText(prodId)}", "CALSCALE:GREGORIAN")
        if (calendarName != null) lines += "X-WR-CALNAME:${icsText(calendarName)}"
        lines += component(event)
        for ((id, value) in sortedEntries(event.opt("recurrenceOverrides"))) {
            val patch = objectOf(value)
            if (patch.opt("excluded") == true) continue
            val effective = patch.entryList().filter { it.first != "excluded" }
            // An empty patch only adds an RDATE; it does not modify the occurrence.
            if (effective.isEmpty()) continue
            val instance = JSONObject().also { copy -> effective.forEach { (key, v) -> copy.put(key, v) } }
            val base = event.copy().put("start", id)
            lines += component(applyPatch(base, instance), recurrenceId = id, master = event)
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { foldLine(it) } + "\r\n"
    }

    private fun component(event: JSONObject, recurrenceId: String? = null, master: JSONObject = event): List<String> {
        val uid = stringOf(master.opt("uid"))?.takeIf { it.isNotEmpty() }
            ?: throw JsCalendarException("JSCalendar uid is required")
        val lines = mutableListOf("BEGIN:VEVENT", "UID:${icsText(uid)}")
        val updated = stringOf(event.opt("updated"))
        val created = stringOf(event.opt("created"))
        // A missing timestamp must not make otherwise identical reads differ.
        lines += "DTSTAMP:${utcStamp(updated ?: created ?: "1970-01-01T00:00:00Z")}"
        if (created != null) lines += "CREATED:${utcStamp(created)}"
        if (updated != null) lines += "LAST-MODIFIED:${utcStamp(updated)}"
        (event.opt("sequence") as? Number)?.let { lines += "SEQUENCE:${it.toLong()}" }

        val start = stringOf(event.opt("start")) ?: throw JsCalendarException("JSCalendar Event start is required")
        val tzid = stringOf(event.opt("timeZone"))
        val allDay = event.opt("showWithoutTime") == true
        lines += dateProperty("DTSTART", start, tzid, allDay)
        val endLocation = sortedEntries(event.opt("locations")).map { objectOf(it.second) }
            .firstOrNull { it.opt("rel") == "end" || it.opt("relativeTo") == "end" }
        val endZone = stringOf(event.opt("endTimeZone")) ?: stringOf(endLocation?.opt("timeZone")) ?: tzid
        val length = stringOf(event.opt("duration")) ?: "PT0S"
        val parsed = parseDuration(length)
        if (!allDay && parsed.days == 0L && parsed.seconds.toLong() == 0L) {
            // RFC 5545 requires DTEND to be later than DTSTART. A zero-duration
            // event is valid with DURATION, and Calino preserves its equal times.
            lines += "DURATION:PT0S"
        } else {
            lines += dateProperty("DTEND", endTime(start, length, tzid, endZone, allDay), endZone, allDay)
        }

        stringOf(event.opt("status"))?.uppercase()?.takeIf { it in setOf("CONFIRMED", "CANCELLED", "TENTATIVE") }
            ?.let { lines += "STATUS:$it" }
        (event.opt("priority") as? Number)?.let { lines += "PRIORITY:${it.toLong()}" }
        when (event.opt("freeBusyStatus")) {
            "free" -> lines += "TRANSP:TRANSPARENT"
            "busy" -> lines += "TRANSP:OPAQUE"
        }
        stringOf(event.opt("privacy"))?.uppercase()?.takeIf { it in setOf("PUBLIC", "PRIVATE", "SECRET", "CONFIDENTIAL") }
            ?.let { lines += "CLASS:${if (it == "SECRET") "CONFIDENTIAL" else it}" }

        if (recurrenceId != null) {
            lines += recurrenceDate("RECURRENCE-ID", recurrenceId, master)
        } else {
            for (rule in recurrenceRulesOf(event)) lines += "RRULE:${recurrenceRuleText(rule, event)}"
            lines += exclusionLines(event)
            val overrides = sortedEntries(event.opt("recurrenceOverrides"))
            val additions = generatedOccurrences(
                event,
                overrides.filter { objectOf(it.second).opt("excluded") != true }.map { it.first },
            )
            for ((id, patch) in overrides) {
                if (objectOf(patch).opt("excluded") == true) {
                    lines += recurrenceDate("EXDATE", id, event)
                } else if (id !in additions) {
                    lines += recurrenceDate("RDATE", id, event)
                }
            }
        }
        lines += content(event)
        lines += people(event)
        lines += alarms(event)
        lines += "END:VEVENT"
        return lines
    }

    // --- content ---------------------------------------------------------------

    private fun content(event: JSONObject): List<String> {
        val lines = mutableListOf<String>()
        for ((key, name) in listOf("title" to "SUMMARY", "description" to "DESCRIPTION", "color" to "COLOR")) {
            stringOf(event.opt(key))?.let { lines += "$name:${icsText(it)}" }
        }
        val locations = sortedEntries(event.opt("locations")).map { objectOf(it.second) }
        val names = locations.mapNotNull { stringOf(it.opt("name"))?.takeIf(String::isNotEmpty) }
        if (names.isNotEmpty()) lines += "LOCATION:${icsText(names.joinToString(", "))}"
        val coordinates = Regex("""^(?:geo:)?(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        for (location in locations) {
            val match = stringOf(location.opt("coordinates"))?.let(coordinates::find) ?: continue
            lines += "GEO:${match.groupValues[1]};${match.groupValues[2]}"
            break // RFC 5545 allows one GEO property per event.
        }
        val keywords = enabledKeys(event.opt("keywords"))
        if (keywords.isNotEmpty()) lines += "CATEGORIES:${keywords.joinToString(",") { icsText(it) }}"
        for (category in enabledKeys(event.opt("categories"))) lines += "CONCEPT:${icsUri(category)}"

        var hasUrl = false
        fun addUrl(value: String) {
            if (!hasUrl) {
                lines += "URL:${icsUri(value)}"
                hasUrl = true
            }
        }
        for ((_, value) in sortedEntries(event.opt("virtualLocations"))) {
            val virtual = objectOf(value)
            val href = stringOf(virtual.opt("uri")) ?: stringOf(virtual.opt("href")) ?: continue
            if (href.isEmpty()) continue
            addUrl(href)
            val name = stringOf(virtual.opt("name"))?.takeIf(String::isNotEmpty)
            lines += "CONFERENCE;VALUE=URI${if (name != null) ";LABEL=${icsParameter(name)}" else ""}:${icsUri(href)}"
        }
        for ((_, value) in sortedEntries(event.opt("links"))) {
            val link = objectOf(value)
            val href = stringOf(link.opt("href"))?.takeIf(String::isNotEmpty) ?: continue
            val type = stringOf(link.opt("contentType"))?.takeIf(String::isNotEmpty)
            val rel = stringOf(link.opt("rel"))
            if (rel == "enclosure") {
                lines += if (href.startsWith("data:")) {
                    inlineAttachment(href, type)
                } else {
                    "ATTACH${if (type != null) ";FMTTYPE=${icsParameter(type)}" else ""}:${icsUri(href)}"
                }
            } else if (rel.isNullOrEmpty() || rel in setOf("alternate", "describedby", "self")) {
                addUrl(href)
            }
        }
        for ((uid, value) in sortedEntries(event.opt("relatedTo"))) {
            val relations = enabledKeys(objectOf(value).opt("relation"))
            if (relations.isEmpty()) lines += "RELATED-TO:${icsText(uid)}"
            for (relation in relations) {
                lines += "RELATED-TO;RELTYPE=${icsParameter(relation.uppercase())}:${icsText(uid)}"
            }
        }
        return lines
    }

    /** A `data:` link as a base64 ATTACH; the bytes are validated, never trusted. */
    private fun inlineAttachment(href: String, contentType: String?): String {
        val match = Regex("""^data:([^,]*),(.*)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(href) ?: throw JsCalendarException("Invalid JSCalendar data URI attachment")
        val metadata = match.groupValues[1].split(';')
        val type = contentType ?: metadata[0].ifEmpty { "text/plain" }
        val body = match.groupValues[2]
        val encoded = if (metadata.any { it.equals("base64", ignoreCase = true) }) {
            val text = String(percentDecode(body), Charsets.ISO_8859_1).filterNot { it.isWhitespace() }
            try {
                Base64.getDecoder().decode(text)
            } catch (_: IllegalArgumentException) {
                throw JsCalendarException("Invalid JSCalendar data URI attachment")
            }
            text
        } else {
            // Percent escapes encode bytes, not necessarily UTF-8 (e.g. image data).
            Base64.getEncoder().encodeToString(percentDecode(body))
        }
        return "ATTACH;VALUE=BINARY;ENCODING=BASE64;FMTTYPE=${icsParameter(type)}:$encoded"
    }

    private fun percentDecode(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        var index = 0
        while (index < text.length) {
            val hex = if (text[index] == '%' && index + 2 < text.length) text.substring(index + 1, index + 3) else null
            if (hex != null && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                out.write(hex.toInt(16))
                index += 3
            } else {
                val codePoint = text.codePointAt(index)
                out.write(String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8))
                index += Character.charCount(codePoint)
            }
        }
        return out.toByteArray()
    }

    // --- alarms ----------------------------------------------------------------

    private fun alarms(event: JSONObject): List<String> {
        val lines = mutableListOf<String>()
        for ((_, value) in sortedEntries(event.opt("alerts"))) {
            val alert = objectOf(value)
            val trigger = objectOf(alert.opt("trigger"))
            val offset = stringOf(trigger.opt("offset"))?.takeIf(String::isNotEmpty)
            val whenAt = stringOf(trigger.opt("when"))?.takeIf(String::isNotEmpty)
            val action = (stringOf(alert.opt("action")) ?: "display").uppercase()
            if (action != "DISPLAY" && action != "EMAIL") continue
            val type = stringOf(trigger.opt("@type"))
            val triggerLine = when {
                whenAt != null && (type == "AbsoluteTrigger" || type == null) ->
                    "TRIGGER;VALUE=DATE-TIME:${utcStamp(whenAt)}"
                offset != null && (type == "OffsetTrigger" || type == null) -> {
                    // iCalendar has second precision; JSCalendar allows fractions.
                    val normalized = offset.replace(Regex("""(\d+)\.\d+S$"""), "$1S")
                    if (!OffsetPattern.matches(normalized)) throw JsCalendarException("Invalid JSCalendar alarm offset")
                    val related = if (trigger.opt("relativeTo") == "end" || trigger.opt("rel") == "end") "END" else "START"
                    "TRIGGER;RELATED=$related:$normalized"
                }
                else -> continue
            }
            lines += listOf("BEGIN:VALARM", "ACTION:$action", triggerLine)
            val title = stringOf(alert.opt("title")) ?: stringOf(event.opt("title")) ?: "Reminder"
            val description = stringOf(alert.opt("description")) ?: stringOf(event.opt("description")) ?: title
            lines += "DESCRIPTION:${icsText(description)}"
            if (action == "EMAIL" || alert.opt("title") is String) lines += "SUMMARY:${icsText(title)}"
            if (action == "EMAIL") {
                // Alert recipients are not standardized by RFC 8984; prefer them
                // when supplied, otherwise use the event's participants.
                val source = alert.opt("recipients") ?: event.opt("participants")
                val recipients = sortedEntries(source)
                for ((_, recipient) in recipients) {
                    val target = stringOf(recipient) ?: participantAddress(objectOf(recipient)) ?: continue
                    lines += "ATTENDEE:${icsUri(if (':' in target) target else "mailto:$target")}"
                }
                val organizer = stringOf(event.opt("organizerCalendarAddress"))
                if (recipients.isEmpty() && organizer != null) lines += "ATTENDEE:${icsUri(organizer)}"
            }
            stringOf(alert.opt("acknowledged"))?.takeIf(String::isNotEmpty)?.let { lines += "ACKNOWLEDGED:${utcStamp(it)}" }
            lines += "END:VALARM"
        }
        return lines
    }

    private val OffsetPattern = Regex("""^[+-]?P(?=\d|T\d)(?:\d+W)?(?:\d+D)?(?:T(?:\d+H)?(?:\d+M)?(?:\d+S)?)?$""")

    // --- people ----------------------------------------------------------------

    /** `calendarAddress`, then `sendTo`, then the `mailto:` of `email`. */
    fun participantAddress(participant: JSONObject): String? {
        val sendTo = objectOf(participant.opt("sendTo"))
        val explicit = stringOf(participant.opt("calendarAddress"))?.takeIf(String::isNotEmpty)
            ?: stringOf(sendTo.opt("imip"))?.takeIf(String::isNotEmpty)
            ?: stringOf(sendTo.opt("other"))?.takeIf(String::isNotEmpty)
        val email = stringOf(participant.opt("email"))?.takeIf(String::isNotEmpty)
        return explicit ?: email?.let { if (it.startsWith("mailto:")) it else "mailto:$it" }
    }

    private class Person(val id: String, val data: JSONObject)

    private fun people(event: JSONObject): List<String> {
        val participants = sortedEntries(event.opt("participants")).map { Person(it.first, objectOf(it.second)) }
        val owner = participants.firstOrNull { objectOf(it.data.opt("roles")).opt("owner") == true && participantAddress(it.data) != null }
        val replyTo = objectOf(event.opt("replyTo"))
        val organizerAddress = stringOf(event.opt("organizerCalendarAddress"))?.takeIf(String::isNotEmpty)
            ?: stringOf(replyTo.opt("imip"))?.takeIf(String::isNotEmpty)
            ?: stringOf(replyTo.opt("other"))?.takeIf(String::isNotEmpty)
            ?: owner?.let { participantAddress(it.data) }
        val organizer = participants.firstOrNull { participantAddress(it.data) == organizerAddress }
        val lines = mutableListOf<String>()
        if (organizerAddress != null) {
            val name = stringOf(organizer?.data?.opt("name"))?.takeIf(String::isNotEmpty)
            lines += "ORGANIZER${if (name != null) ";CN=${icsParameter(name)}" else ""}:${icsUri(organizerAddress)}"
        }
        for (person in participants) {
            val p = person.data
            val address = participantAddress(p) ?: continue
            val roles = objectOf(p.opt("roles"))
            fun has(role: String) = roles.opt(role) == true
            // A bare owner is the organizer, not an attendee.
            if (has("owner") && listOf("attendee", "required", "optional", "chair", "informational").none(::has)) continue
            val params = mutableListOf<String>()
            stringOf(p.opt("name"))?.takeIf(String::isNotEmpty)?.let { params += "CN=${icsParameter(it)}" }
            params += "ROLE=" + when {
                has("chair") -> "CHAIR"
                has("optional") -> "OPT-PARTICIPANT"
                has("informational") && !has("attendee") && !has("required") -> "NON-PARTICIPANT"
                else -> "REQ-PARTICIPANT"
            }
            stringOf(p.opt("participationStatus"))?.uppercase()?.takeIf {
                it in setOf("NEEDS-ACTION", "ACCEPTED", "DECLINED", "TENTATIVE", "DELEGATED", "COMPLETED", "IN-PROCESS")
            }?.let { params += "PARTSTAT=$it" }
            (p.opt("expectReply") as? Boolean)?.let { params += "RSVP=${if (it) "TRUE" else "FALSE"}" }
            val kind = stringOf(p.opt("kind"))
            val cutype = if (kind == "location") "ROOM" else kind?.uppercase()
            if (cutype != null && cutype in setOf("INDIVIDUAL", "GROUP", "RESOURCE", "ROOM", "UNKNOWN")) params += "CUTYPE=$cutype"
            for ((field, name) in listOf("delegatedTo" to "DELEGATED-TO", "delegatedFrom" to "DELEGATED-FROM")) {
                // RFC 8984 keys the map by participant id; Stalwart sends the
                // calendar address, as a plain string or as the key of a map.
                val raw = p.opt(field)
                val references = if (raw is String) listOf(raw) else enabledKeys(raw)
                val delegates = references.mapNotNull { reference ->
                    val target = participants.firstOrNull { it.id == reference }
                    val value = if (target != null) {
                        participantAddress(target.data)
                    } else if (Regex("""^[a-z][a-z0-9+.-]*:|@""", RegexOption.IGNORE_CASE).containsMatchIn(reference)) {
                        reference
                    } else {
                        null
                    }
                    value?.let { "\"${icsUri(if (':' in it) it else "mailto:$it").replace("\"", "%22")}\"" }
                }
                if (delegates.isNotEmpty()) params += "$name=${delegates.joinToString(",")}"
            }
            (p.opt("scheduleSequence") as? Number)?.let { params += "SCHEDULE-SEQUENCE=${it.toLong()}" }
            lines += "ATTENDEE;${params.joinToString(";")}:${icsUri(address)}"
        }
        return lines
    }
}
