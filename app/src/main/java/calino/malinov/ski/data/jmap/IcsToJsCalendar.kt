package calino.malinov.ski.data.jmap

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

/**
 * iCalendar (RFC 5545) to JSCalendar (RFC 8984), the write-side counterpart of
 * [JsCalendarToIcs]. A port of Calino Web's `icsToJscalendar`, using the draft
 * (`calendarAddress`, singular `recurrenceRule`) vocabulary that Stalwart speaks.
 *
 * One resource becomes one `Event`: the master VEVENT plus its detached
 * instances, which turn into `recurrenceOverrides` patches. Anything JSCalendar
 * cannot hold (a VTODO, `RANGE=THISANDFUTURE`, several RRULEs, an unnamed zone)
 * throws [JsCalendarException], so the caller rejects the write instead of
 * sending a wrong event.
 */
internal object IcsToJsCalendar {
    class Converted(val uid: String, val event: JSONObject)

    fun convert(ics: String, calendarIds: JSONObject? = null): Converted {
        val calendar = IcsParser.parse(ics)
        if (calendar.name != "VCALENDAR") throw JsCalendarException("Expected VCALENDAR")
        val components = calendar.children.filter { it.name == "VEVENT" || it.name == "VTODO" }
        val masters = components.filter { !it.has("RECURRENCE-ID") }
        if (masters.size != 1) throw JsCalendarException("Expected exactly one master VEVENT or VTODO")
        val master = masters.single()
        if (master.name != "VEVENT") throw JsCalendarException("JMAP CalendarEvent supports VEVENT only")
        val context = Context(calendar)
        val event = componentToObject(master, context)
        val uid = event.getString("uid")
        if (components.any { it.text("UID") != uid || it.name != master.name }) {
            throw JsCalendarException("Detached components must share the master UID and type")
        }
        if (calendarIds != null) event.put("calendarIds", calendarIds.copy())
        val zone = stringOf(event.opt("timeZone"))
        val overrides = JSONObject()
        for (property in master.all("RDATE")) {
            for (item in splitValues(property.value)) {
                if (property.parameter("VALUE").equals("PERIOD", true) || '/' in item) {
                    val (startText, endText) = item.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    val start = point(startText, property)
                    val key = recurrenceTime(start, zone, context)
                    val length = if (endText.startsWith("P") || endText.startsWith("-P") || endText.startsWith("+P")) {
                        parseSignedDuration(endText).text()
                    } else {
                        elapsedDuration(epochSeconds(point(endText, property), context) - epochSeconds(start, context))
                    }
                    // `start` repeats the key (a no-op). Without it Stalwart writes a detached
                    // VEVENT that has a DURATION but no DTSTART, which is invalid iCalendar.
                    overrides.put(key, jsonObject("start" to key, "duration" to length))
                } else {
                    overrides.put(recurrenceTime(point(item, property), zone, context), JSONObject())
                }
            }
        }
        for (component in components.filter { it !== master }) {
            val property = component.first("RECURRENCE-ID")!!
            if (property.parameter("RANGE").equals("THISANDFUTURE", true)) {
                throw JsCalendarException("RECURRENCE-ID RANGE=THISANDFUTURE requires splitting the series")
            }
            val key = recurrenceTime(point(property.value, property), zone, context)
            if (component.text("STATUS")?.uppercase() == "CANCELLED") {
                overrides.put(key, jsonObject("excluded" to true))
            } else {
                val detached = componentToObject(component, context)
                // A recurrence's start is implicit at its key, not at the master's initial start.
                val occurrence = event.copy().put("start", key)
                overrides.put(key, diffObjects(occurrence, detached, OverrideIgnored))
            }
        }
        // EXDATE takes precedence over an RDATE or a detached instance at the same instant.
        for (property in master.all("EXDATE")) {
            for (item in splitValues(property.value)) {
                overrides.put(recurrenceTime(point(item, property), zone, context), jsonObject("excluded" to true))
            }
        }
        if (overrides.length() > 0) event.put("recurrenceOverrides", overrides)
        return Converted(uid, event)
    }

    private val OverrideIgnored = setOf(
        "@type", "uid", "id", "calendarIds", "created", "updated", "isDraft", "isOrigin",
        "recurrenceRule", "recurrenceRules", "recurrenceOverrides", "recurrenceId", "recurrenceIdTimeZone",
        "privacy", "relatedTo", "replyTo", "organizerCalendarAddress", "method", "prodId", "sentBy",
        "timeZones", "excludedRecurrenceRules",
    )

    private class Context(val calendar: IcsComponent)

    // --- components ------------------------------------------------------------

    private fun componentToObject(component: IcsComponent, context: Context): JSONObject {
        val uid = component.text("UID")?.takeIf { it.isNotEmpty() }
            ?: throw JsCalendarException("Calendar component requires UID")
        val event = jsonObject("@type" to "Event", "uid" to uid)
        val startProperty = component.first("DTSTART") ?: throw JsCalendarException("VEVENT requires DTSTART")
        val start = point(startProperty.value, startProperty)
        event.put("start", localText(start.local))
        event.put("timeZone", sourceZone(start, context) ?: JSONObject.NULL)
        if (start.dateOnly) event.put("showWithoutTime", true)
        val endProperty = component.first("DTEND")
        val durationProperty = component.first("DURATION")
        if (endProperty != null) {
            val end = point(endProperty.value, endProperty)
            if (end.dateOnly != start.dateOnly) throw JsCalendarException("DTSTART and DTEND value types differ")
            event.put(
                "duration",
                elapsedDuration(epochSeconds(end, context) - epochSeconds(start, context), start.dateOnly),
            )
        } else if (durationProperty != null) {
            val duration = parseSignedDuration(durationProperty.value)
            if (duration.negative && duration.seconds() != 0L) throw JsCalendarException("Negative event duration")
            event.put("duration", if (start.dateOnly) elapsedDuration(duration.seconds(), true) else duration.text())
        } else {
            event.put("duration", if (start.dateOnly) "P1D" else "PT0S")
        }
        component.text("STATUS")?.lowercase()?.takeIf { it.isNotEmpty() }?.let { event.put("status", it) }
        for ((property, field) in listOf("SUMMARY" to "title", "DESCRIPTION" to "description", "COLOR" to "color")) {
            component.text(property)?.let { event.put(field, it) }
        }
        for ((property, field) in listOf("PRIORITY" to "priority", "SEQUENCE" to "sequence")) {
            component.first(property)?.value?.trim()?.toLongOrNull()?.let { event.put(field, it) }
        }
        component.text("TRANSP")?.takeIf { it.isNotEmpty() }?.let {
            event.put("freeBusyStatus", if (it.uppercase() == "TRANSPARENT") "free" else "busy")
        }
        component.text("CLASS")?.uppercase()?.takeIf { it.isNotEmpty() }?.let {
            event.put("privacy", if (it == "CONFIDENTIAL") "secret" else it.lowercase())
        }
        component.first("CREATED")?.let { event.put("created", utcTime(point(it.value, it), context)) }
        (component.first("DTSTAMP") ?: component.first("LAST-MODIFIED"))?.let {
            event.put("updated", utcTime(point(it.value, it), context))
        }
        setMap(component, "CATEGORIES")?.let { event.put("keywords", it) }
        setMap(component, "CONCEPT")?.let { event.put("categories", it) }
        locations(component)?.let { event.put("locations", it) }
        links(component)?.let { event.put("links", it) }
        participants(component)?.let { event.put("participants", it) }
        alerts(component, context)?.let { event.put("alerts", it) }
        relatedTo(component)?.let { event.put("relatedTo", it) }
        component.first("ORGANIZER")?.value?.takeIf { it.isNotEmpty() }?.let {
            event.put("organizerCalendarAddress", normalizeAddress(it))
        }
        val rules = component.all("RRULE")
        if (rules.size > 1) throw JsCalendarException("Draft JSCalendar supports one RRULE")
        if (rules.isNotEmpty()) {
            event.put("recurrenceRule", recurrenceRule(rules.single().value, stringOf(event.opt("timeZone")), context))
        }
        return event
    }

    private fun normalizeAddress(address: String): String =
        if (address.length >= 7 && address.substring(0, 7).equals("mailto:", true)) "mailto:" + address.substring(7) else address

    // --- recurrence --------------------------------------------------------------

    private val Weekdays = setOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private val Frequencies = setOf("YEARLY", "MONTHLY", "WEEKLY", "DAILY", "HOURLY", "MINUTELY", "SECONDLY")
    private val ByDay = Regex("""^([+-]?\d+)?(MO|TU|WE|TH|FR|SA|SU)$""", RegexOption.IGNORE_CASE)

    private fun recurrenceRule(text: String, zone: String?, context: Context): JSONObject {
        val parts = linkedMapOf<String, String>()
        for (piece in text.split(';')) {
            val index = piece.indexOf('=')
            if (index <= 0) continue
            parts[piece.substring(0, index).trim().uppercase()] = piece.substring(index + 1).trim()
        }
        val frequency = parts["FREQ"]?.uppercase()
        if (frequency == null || frequency !in Frequencies) throw JsCalendarException("Invalid RRULE frequency")
        val interval = parts["INTERVAL"]?.toLongOrNull()?.takeIf { it > 0 } ?: 1L
        val result = jsonObject(
            "@type" to "RecurrenceRule",
            "frequency" to frequency.lowercase(),
            "interval" to interval,
            "firstDayOfWeek" to (parts["WKST"]?.uppercase()?.takeIf { it in Weekdays } ?: "MO").lowercase(),
        )
        parts["COUNT"]?.toLongOrNull()?.let { result.put("count", it) }
        parts["UNTIL"]?.let { result.put("until", recurrenceTime(point(it, null), zone, context)) }
        parts["BYDAY"]?.let { value ->
            result.put(
                "byDay",
                jsonArray(
                    value.split(',').filter { it.isNotBlank() }.map { day ->
                        val match = ByDay.matchEntire(day.trim()) ?: throw JsCalendarException("Invalid RRULE BYDAY: $day")
                        jsonObject("@type" to "NDay", "day" to match.groupValues[2].lowercase()).also { entry ->
                            match.groupValues[1].takeIf { it.isNotEmpty() }?.toLongOrNull()?.let { entry.put("nthOfPeriod", it) }
                        }
                    },
                ),
            )
        }
        for ((part, field) in listOf(
            "BYSECOND" to "bySecond",
            "BYMINUTE" to "byMinute",
            "BYHOUR" to "byHour",
            "BYMONTHDAY" to "byMonthDay",
            "BYYEARDAY" to "byYearDay",
            "BYWEEKNO" to "byWeekNo",
            "BYSETPOS" to "bySetPosition",
        )) {
            val value = parts[part] ?: continue
            result.put(
                field,
                jsonArray(
                    value.split(',').filter { it.isNotBlank() }.map {
                        it.trim().toLongOrNull() ?: throw JsCalendarException("Invalid RRULE $part: $it")
                    },
                ),
            )
        }
        parts["BYMONTH"]?.let { value ->
            result.put("byMonth", jsonArray(value.split(',').filter { it.isNotBlank() }.map { it.trim().toIntOrNull()?.toString() ?: it.trim() }))
        }
        parts["RSCALE"]?.let { result.put("rscale", it.lowercase()) }
        parts["SKIP"]?.let { result.put("skip", it.lowercase()) }
        return result
    }

    // --- people, alerts, locations, links -------------------------------------------

    private fun setMap(component: IcsComponent, property: String): JSONObject? {
        val result = JSONObject()
        for (item in component.all(property)) for (value in icsTextList(item.value)) result.put(value, true)
        return result.takeIf { it.length() > 0 }
    }

    /** FNV-1a over UTF-16: compact, deterministic, valid JSCalendar ids. */
    internal fun stableKey(prefix: String, value: String): String {
        var hash = 0x811C9DC5.toInt()
        for (char in value) hash = (hash xor char.code) * 16777619
        return prefix + hash.toUInt().toString(16).padStart(8, '0')
    }

    private val Kinds = mapOf("INDIVIDUAL" to "individual", "GROUP" to "group", "RESOURCE" to "resource", "ROOM" to "location")

    private fun participants(component: IcsComponent): JSONObject? {
        val result = JSONObject()
        val addresses = HashMap<String, String>()
        fun keyFor(address: String): String {
            val canonical = normalizeAddress(address)
            return addresses.getOrPut(canonical) {
                val base = stableKey("p", canonical)
                var key = base
                var suffix = 1
                while (result.has(key)) key = "${base}_${suffix++}"
                key
            }
        }
        fun ensure(address: String): JSONObject {
            val key = keyFor(address)
            if (!result.has(key)) result.put(key, jsonObject("@type" to "Participant", "calendarAddress" to normalizeAddress(address)))
            return result.getJSONObject(key)
        }
        // The organizer first, so an ATTENDEE for the same address adds to its roles.
        for (property in component.all("ORGANIZER") + component.all("ATTENDEE")) {
            val address = property.value.trim().takeIf { it.isNotEmpty() } ?: continue
            val participant = ensure(address)
            property.parameter("CN")?.takeIf { it.isNotEmpty() }?.let { participant.put("name", it) }
            val roles = participant.optJSONObject("roles") ?: JSONObject()
            if (property.name == "ORGANIZER") {
                roles.put("owner", true)
            } else {
                val role = property.parameter("ROLE")?.uppercase() ?: "REQ-PARTICIPANT"
                roles.put(
                    when (role) {
                        "CHAIR" -> "chair"
                        "NON-PARTICIPANT" -> "informational"
                        "OPT-PARTICIPANT" -> "optional"
                        else -> "required"
                    },
                    true,
                )
                property.parameter("PARTSTAT")?.takeIf { it.isNotEmpty() }?.let { participant.put("participationStatus", it.lowercase()) }
                property.parameter("RSVP")?.takeIf { it.isNotEmpty() }?.let { participant.put("expectReply", it.uppercase() == "TRUE") }
                property.parameter("CUTYPE")?.uppercase()?.let { Kinds[it] }?.let { participant.put("kind", it) }
                for ((parameter, field) in listOf("DELEGATED-TO" to "delegatedTo", "DELEGATED-FROM" to "delegatedFrom", "MEMBER" to "memberOf")) {
                    val refs = JSONObject()
                    for (target in property.parameterValues(parameter)) if (target.isNotEmpty()) refs.put(target, true)
                    if (refs.length() > 0) participant.put(field, refs)
                }
            }
            participant.put("roles", roles)
            property.parameter("SENT-BY")?.takeIf { it.isNotEmpty() }?.let {
                participant.put("sentBy", it.replace(Regex("^mailto:", RegexOption.IGNORE_CASE), ""))
            }
        }
        return result.takeIf { it.length() > 0 }
    }

    private val AlertKey = Regex("^[A-Za-z0-9_-]{1,255}$")

    private fun alerts(component: IcsComponent, context: Context): JSONObject? {
        val result = JSONObject()
        component.components("VALARM").forEachIndexed { index, alarm ->
            val triggerProperty = alarm.first("TRIGGER")
            val action = alarm.text("ACTION")?.lowercase()
            if (triggerProperty == null || (action != "display" && action != "email")) return@forEachIndexed
            val value = triggerProperty.value.trim()
            val trigger = if (triggerProperty.parameter("VALUE").equals("DATE-TIME", true) || Regex("""^\d{8}T""").containsMatchIn(value)) {
                jsonObject("@type" to "AbsoluteTrigger", "when" to utcTime(point(value, triggerProperty), context))
            } else {
                jsonObject(
                    "@type" to "OffsetTrigger",
                    "offset" to parseSignedDuration(value).text(),
                    "relativeTo" to if (triggerProperty.parameter("RELATED").equals("END", true)) "end" else "start",
                )
            }
            val alert = jsonObject("@type" to "Alert", "trigger" to trigger, "action" to action)
            alarm.first("ACKNOWLEDGED")?.let { alert.put("acknowledged", utcTime(point(it.value.trim(), it), context)) }
            // Alert has no uid property. RFC 9074 UID is represented by the map id.
            val uid = alarm.text("UID")?.takeIf { it.isNotEmpty() }
            val base = when {
                uid == null -> "a$index"
                AlertKey.matches(uid) -> uid
                else -> stableKey("a", uid)
            }
            var key = base
            var suffix = 1
            while (result.has(key)) key = "${base}_${suffix++}"
            result.put(key, alert)
        }
        return result.takeIf { it.length() > 0 }
    }

    private fun locations(component: IcsComponent): JSONObject? {
        val result = JSONObject()
        component.all("LOCATION").forEachIndexed { index, property ->
            result.put("l$index", jsonObject("@type" to "Location", "name" to icsUnescape(property.value)))
        }
        val geo = component.first("GEO")?.value?.split(';')
        if (geo != null && geo.size == 2) {
            val latitude = geo[0].trim().toBigDecimalOrNull()
            val longitude = geo[1].trim().toBigDecimalOrNull()
            if (latitude != null && longitude != null) {
                if (!result.has("l0")) result.put("l0", jsonObject("@type" to "Location"))
                result.getJSONObject("l0").put("coordinates", "geo:${plain(latitude)},${plain(longitude)}")
            }
        }
        return result.takeIf { it.length() > 0 }
    }

    private fun plain(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

    private fun links(component: IcsComponent): JSONObject? {
        val result = JSONObject()
        component.first("URL")?.value?.trim()?.takeIf { it.isNotEmpty() }?.let {
            result.put("url", jsonObject("@type" to "Link", "href" to it, "rel" to "describedby"))
        }
        component.all("ATTACH").forEachIndexed { index, property ->
            val contentType = property.parameter("FMTTYPE")?.takeIf { it.isNotEmpty() }
            val binary = property.parameter("ENCODING").equals("BASE64", true) || property.parameter("VALUE").equals("BINARY", true)
            val value = property.value.trim()
            if (value.isEmpty()) return@forEachIndexed
            val href = if (binary) "data:${contentType ?: "application/octet-stream"};base64,${value.filterNot { it.isWhitespace() }}" else value
            val link = jsonObject("@type" to "Link", "href" to href, "rel" to "enclosure")
            if (contentType != null) link.put("contentType", contentType)
            result.put("f$index", link)
        }
        return result.takeIf { it.length() > 0 }
    }

    private fun relatedTo(component: IcsComponent): JSONObject? {
        val result = JSONObject()
        for (property in component.all("RELATED-TO")) {
            val uid = icsUnescape(property.value)
            val relation = property.parameter("RELTYPE")?.lowercase() ?: "parent"
            val relations = result.optJSONObject(uid)?.optJSONObject("relation") ?: JSONObject()
            relations.put(relation, true)
            result.put(uid, jsonObject("@type" to "Relation", "relation" to relations))
        }
        return result.takeIf { it.length() > 0 }
    }

    // --- time ------------------------------------------------------------------------

    /** A parsed DATE or DATE-TIME with the TZID it was written in. */
    private class Point(val local: LocalDateTime, val dateOnly: Boolean, val utc: Boolean, val tzid: String?)

    private val PointPattern = Regex("""^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})(Z)?)?$""")

    private fun point(value: String, property: IcsProperty?): Point {
        val match = PointPattern.matchEntire(value.trim()) ?: throw JsCalendarException("Invalid iCalendar date-time: $value")
        val g = match.groupValues
        val dateOnly = g[4].isEmpty()
        val local = try {
            if (dateOnly) {
                LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), 0, 0, 0)
            } else {
                LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
            }
        } catch (_: DateTimeException) {
            throw JsCalendarException("Invalid iCalendar date-time: $value")
        }
        return Point(local, dateOnly, !dateOnly && g[7].isNotEmpty(), property?.parameter("TZID"))
    }

    private fun splitValues(value: String): List<String> = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private val ZoneAliases = mapOf(
        "W. Europe Standard Time" to "Europe/Berlin",
        "Romance Standard Time" to "Europe/Paris",
        "Central Europe Standard Time" to "Europe/Budapest",
        "Central European Standard Time" to "Europe/Warsaw",
        "GMT Standard Time" to "Europe/London",
        "Eastern Standard Time" to "America/New_York",
        "Central Standard Time" to "America/Chicago",
        "Mountain Standard Time" to "America/Denver",
        "Pacific Standard Time" to "America/Los_Angeles",
        "US Mountain Standard Time" to "America/Phoenix",
        "India Standard Time" to "Asia/Kolkata",
        "China Standard Time" to "Asia/Shanghai",
        "Tokyo Standard Time" to "Asia/Tokyo",
        "AUS Eastern Standard Time" to "Australia/Sydney",
        "UTC" to "UTC",
        "Etc/UTC" to "UTC",
        "Etc/GMT" to "UTC",
    )
    private val ZoneSuffix = Regex("""(?:^|/)((?:Africa|America|Antarctica|Arctic|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.+)$""")
    private val KnownZones: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { ZoneId.getAvailableZoneIds() }

    private fun ianaZone(name: String): String? {
        val mapped = ZoneAliases[name] ?: name
        if (mapped in KnownZones) return mapped
        val suffix = ZoneSuffix.find(name)?.groupValues?.get(1)
        return if (suffix != null && suffix != name) ianaZone(suffix) else null
    }

    /** The IANA zone a point is written in: `UTC` for Z, null for a date or a floating time. */
    private fun sourceZone(point: Point, context: Context): String? {
        if (point.dateOnly) return null
        if (point.utc) return "UTC"
        val tzid = point.tzid?.takeIf { it.isNotEmpty() } ?: return null
        val declared = context.calendar.components("VTIMEZONE").firstOrNull { it.text("TZID") == tzid }
        val location = declared?.text("X-LIC-LOCATION")
        return (location?.let(::ianaZone)) ?: ianaZone(tzid)
            ?: throw JsCalendarException("Cannot resolve TZID without VTIMEZONE offset data: $tzid")
    }

    private fun localText(local: LocalDateTime): String =
        "%04d-%02d-%02dT%02d:%02d:%02d".format(local.year, local.monthValue, local.dayOfMonth, local.hour, local.minute, local.second)

    private fun epochSeconds(point: Point, context: Context): Long {
        val civil = point.local.toEpochSecond(ZoneOffset.UTC)
        val zone = sourceZone(point, context) ?: return civil
        if (point.utc) return civil
        // RFC 8984: the first instant of a fold, and the pre-gap offset in a gap.
        return ZonedDateTime.ofLocal(point.local, zoneFor(zone), null).toEpochSecond()
    }

    /** UNTIL, EXDATE, RDATE and recurrence ids, converted to the master's local clock. */
    private fun recurrenceTime(point: Point, targetZone: String?, context: Context): String {
        if (point.dateOnly || sourceZone(point, context) == null || targetZone == null) return localText(point.local)
        val seconds = epochSeconds(point, context)
        return localText(LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), zoneFor(targetZone)))
    }

    private fun utcTime(point: Point, context: Context): String =
        localText(LocalDateTime.ofEpochSecond(epochSeconds(point, context), 0, ZoneOffset.UTC)) + "Z"

    internal fun elapsedDuration(seconds: Long, allDay: Boolean = false): String {
        if (seconds < 0) throw JsCalendarException("Event end precedes start")
        if (allDay) return "P${seconds / 86400}D"
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val rest = seconds % 60
        return "PT" + (if (hours > 0) "${hours}H" else "") + (if (minutes > 0) "${minutes}M" else "") +
            (if (rest > 0 || seconds == 0L) "${rest}S" else "")
    }

    private val DurationText = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")

    internal class SignedDuration(val negative: Boolean, val days: Long, val hours: Long, val minutes: Long, val secondsPart: Long) {
        fun seconds(): Long = days * 86400 + hours * 3600 + minutes * 60 + secondsPart

        /** Weeks fold into days; an empty duration is `PT0S`. */
        fun text(): String {
            val time = (if (hours > 0) "${hours}H" else "") + (if (minutes > 0) "${minutes}M" else "") +
                (if (secondsPart > 0) "${secondsPart}S" else "")
            val date = if (days > 0) "${days}D" else ""
            val body = if (date.isEmpty() && time.isEmpty()) "T0S" else date + if (time.isEmpty()) "" else "T$time"
            return (if (negative) "-" else "") + "P" + body
        }
    }

    internal fun parseSignedDuration(value: String): SignedDuration {
        val text = value.trim()
        val match = DurationText.matchEntire(text)
        if (match == null || match.groups.drop(2).all { it == null }) throw JsCalendarException("Invalid iCalendar duration: $value")
        fun number(index: Int) = match.groupValues[index].toLongOrNull() ?: 0L
        return SignedDuration(match.groupValues[1] == "-", number(2) * 7 + number(3), number(4), number(5), number(6))
    }
}
