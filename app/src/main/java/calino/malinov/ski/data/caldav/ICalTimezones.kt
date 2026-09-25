package calino.malinov.ski.data.caldav

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.ICalComponent
import biweekly.io.TimezoneAssignment
import biweekly.property.DateOrDateTimeProperty
import biweekly.property.ExceptionDates
import biweekly.property.ICalProperty
import biweekly.property.RecurrenceDates
import biweekly.util.ICalDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

/**
 * Keeps `TZID` values honest on the way in and on the way out.
 *
 * biweekly records a time's zone by *property identity* in
 * [ICalendar.getTimezoneInfo] and strips the `TZID` parameter at parse time.
 * Calino copies properties and components all the time -- recurrence edits,
 * three-way rebases, moving overrides between parsed objects -- and every
 * copy silently lost its zone and was written back as UTC. So Calino keeps
 * the zone *on the property*:
 *
 *  * [parse] turns each identity binding back into a `TZID` parameter, and a
 *    floating time into the private [FloatingMarker] parameter. A TZID naming
 *    neither a `VTIMEZONE` in the object nor a Java zone (Outlook's "Eastern
 *    Standard Time" without its definition) is resolved and its wall time
 *    re-read in that zone.
 *  * [prepareForWrite] binds each parameter back just before serialization:
 *    to the object's own `VTIMEZONE` with that TZID when there is one, else to
 *    one built offline from tzdb by [VTimezoneBuilder]. RFC 5545 3.2.19
 *    requires a definition for every TZID, and without the binding biweekly
 *    writes `TZID=X:...Z`. A server's own definitions are never replaced.
 */
object ICalTimezones {

    /** Private parameter marking a floating (zone-less) local time between parse and write. */
    const val FloatingMarker = "X-CALINO-FLOATING"

    /** Parses every VCALENDAR in [text], moving zones onto their properties. */
    fun parse(text: String): List<ICalendar> =
        Biweekly.parse(text).all().onEach(::materialize)

    /**
     * The IANA zone a TZID names, or null.
     *
     * Accepts an IANA id, biweekly's `/`-prefixed global form, a vendor-prefixed
     * id (`/mozilla.org/20050126_1/America/New_York`,
     * `/citadel.org/.../Europe/Berlin`), and a Windows zone name.
     */
    fun resolve(tzid: String?): ZoneId? {
        val raw = tzid?.trim()?.trim('"')?.takeIf(String::isNotEmpty) ?: return null
        of(raw)?.let { return it }
        val segments = raw.split('/').filter(String::isNotEmpty)
        for (count in minOf(3, segments.size) downTo 1) {
            of(segments.takeLast(count).joinToString("/"))?.let { return it }
        }
        return WindowsZones[raw.lowercase()]?.let(::of)
    }

    /** The IANA id of the zone [property]'s wall time is in; null for UTC, floating or date values. */
    fun zoneIdOf(calendar: ICalendar, property: DateOrDateTimeProperty?): String? {
        val tzid = property?.takeIf { it.value?.hasTime() == true }?.let { tzidOf(calendar, it) } ?: return null
        return (resolve(tzid) ?: resolve(calendar.timezoneInfo.getTimezoneById(tzid)?.timeZone?.id))?.id
    }

    /**
     * The zone [property]'s times are computed in, or null for UTC and
     * floating values. The object's own definition wins over tzdb, since that
     * is what the value was read with.
     */
    fun timeZoneOf(calendar: ICalendar?, property: ICalProperty?): TimeZone? {
        val tzid = property?.let { tzidOf(calendar, it) } ?: return null
        return calendar?.timezoneInfo?.getTimezoneById(tzid)?.timeZone
            ?: resolve(tzid)?.let(TimeZone::getTimeZone)
    }

    /**
     * The TZID [property] is written in: its parameter, or for a calendar
     * parsed by plain biweekly, its identity binding. Null for UTC/floating.
     */
    fun tzidOf(calendar: ICalendar?, property: ICalProperty): String? =
        property.getParameter("TZID")
            ?: calendar?.timezoneInfo
                ?.takeUnless { it.isFloating(property) }
                ?.getTimezone(property)
                ?.tzid()

    /** True when [property] is a floating (zone-less) local time. */
    fun isFloating(property: ICalProperty?): Boolean =
        property?.getParameter(FloatingMarker) != null

    private fun materialize(calendar: ICalendar) {
        val info = calendar.timezoneInfo
        calendar.timedProperties().forEach { property ->
            val assignment = info.getTimezone(property)
            // The identity binding stays: clearing it drops the object's own
            // VTIMEZONE from biweekly's assignment set, and the parameter
            // alone already survives a copy.
            when {
                info.isFloating(property) -> property.setParameter(FloatingMarker, "TRUE")
                assignment != null -> property.setParameter("TZID", assignment.tzid())
                else -> {
                    // biweekly could not resolve this TZID and read the digits
                    // in the JVM zone; re-read them in the zone it names.
                    val tzid = property.getParameter("TZID") ?: return@forEach
                    resolve(tzid)?.let { property.rereadIn(it) }
                }
            }
        }
    }

    /**
     * Binds every zone parameter in [calendar] for serialization.
     *
     * A `TZID` naming one of the object's own definitions binds to it as-is.
     * Any other resolvable TZID has its written wall time re-read in that zone
     * -- the digits are authoritative -- and binds to a matching own
     * definition or to a built one. An unresolvable TZID with no definition
     * is written as UTC, which keeps the instant exact.
     */
    fun prepareForWrite(calendar: ICalendar) {
        val info = calendar.timezoneInfo
        val pending = mutableListOf<Pair<ICalProperty, ZoneId>>()
        calendar.timedProperties().forEach { property ->
            if (property.getParameter(FloatingMarker) != null) {
                property.removeParameter(FloatingMarker)
                property.removeParameter("TZID")
                info.setFloating(property, true)
                return@forEach
            }
            val parameter = property.getParameter("TZID")
            if (parameter != null) {
                property.removeParameter("TZID")
                val exact = info.getTimezoneById(parameter)?.takeIf { it.component != null }
                if (exact != null) {
                    info.setTimezone(property, exact)
                    return@forEach
                }
                val zone = resolve(parameter)
                if (zone == null) {
                    info.setTimezone(property, null)
                    return@forEach
                }
                property.rereadIn(zone)
                val own = info.timezones.firstOrNull { it.component != null && resolve(it.tzid()) == zone }
                if (own != null) info.setTimezone(property, own) else pending += property to zone
                return@forEach
            }
            // A global assignment has no definition to write.
            val assignment = info.getTimezone(property) ?: return@forEach
            if (assignment.component != null) return@forEach
            val zone = resolve(assignment.globalId) ?: return@forEach
            pending += property to zone
        }
        if (pending.isEmpty()) return
        pending.groupBy({ it.second }, { it.first }).forEach { (zone, properties) ->
            val earliest = properties.flatMap { it.instants() }.minOrNull() ?: Instant.now()
            val assignment = TimezoneAssignment(
                TimeZone.getTimeZone(zone),
                VTimezoneBuilder.build(zone, earliest),
            )
            properties.forEach { info.setTimezone(it, assignment) }
        }
    }

    private fun ICalendar.timedProperties(): List<ICalProperty> =
        components.values().flatMap { it.timedProperties() }

    private fun ICalComponent.timedProperties(): List<ICalProperty> =
        properties.values().filter { property ->
            when (property) {
                is DateOrDateTimeProperty -> property.value?.hasTime() == true
                is ExceptionDates -> property.values.orEmpty().any { it.hasTime() }
                is RecurrenceDates -> property.dates.orEmpty().any { it.hasTime() }
                else -> false
            }
        }

    private fun ICalProperty.instants(): List<Instant> = values().map { it.toInstant() }

    private fun ICalProperty.values(): List<ICalDate> = when (this) {
        is DateOrDateTimeProperty -> listOfNotNull(value)
        is ExceptionDates -> values.orEmpty()
        is RecurrenceDates -> dates.orEmpty()
        else -> emptyList()
    }

    /** Re-reads each value's written wall time in [zone], keeping the digits. */
    private fun ICalProperty.rereadIn(zone: ZoneId) {
        fun ICalDate.inZone(): ICalDate {
            val raw = rawComponents?.takeUnless { it.isUtc } ?: return this
            if (!hasTime()) return this
            val local = LocalDateTime.of(raw.year, raw.month, raw.date, raw.hour, raw.minute, raw.second)
            return ICalDate(Date.from(local.atZone(zone).toInstant()), raw, true)
        }
        when (this) {
            is DateOrDateTimeProperty -> value = value?.inZone()
            is ExceptionDates -> {
                val replaced = values.map { it.inZone() }
                values.clear()
                values.addAll(replaced)
            }
            is RecurrenceDates -> {
                val replaced = dates.map { it.inZone() }
                dates.clear()
                dates.addAll(replaced)
            }
        }
    }

    private fun TimezoneAssignment.tzid(): String? =
        component?.timezoneId?.value ?: globalId

    private fun of(id: String): ZoneId? = runCatching { ZoneId.of(id) }.getOrNull()

    /**
     * Windows zone names to IANA, the CLDR `windowsZones` "001" territory.
     * Outlook and Exchange write these as TZIDs.
     */
    private val WindowsZones: Map<String, String> = mapOf(
        "Dateline Standard Time" to "Etc/GMT+12",
        "UTC-11" to "Etc/GMT+11",
        "Aleutian Standard Time" to "America/Adak",
        "Hawaiian Standard Time" to "Pacific/Honolulu",
        "Marquesas Standard Time" to "Pacific/Marquesas",
        "Alaskan Standard Time" to "America/Anchorage",
        "UTC-09" to "Etc/GMT+9",
        "Pacific Standard Time (Mexico)" to "America/Tijuana",
        "UTC-08" to "Etc/GMT+8",
        "Pacific Standard Time" to "America/Los_Angeles",
        "US Mountain Standard Time" to "America/Phoenix",
        "Mountain Standard Time (Mexico)" to "America/Mazatlan",
        "Mountain Standard Time" to "America/Denver",
        "Yukon Standard Time" to "America/Whitehorse",
        "Central America Standard Time" to "America/Guatemala",
        "Central Standard Time" to "America/Chicago",
        "Easter Island Standard Time" to "Pacific/Easter",
        "Central Standard Time (Mexico)" to "America/Mexico_City",
        "Canada Central Standard Time" to "America/Regina",
        "SA Pacific Standard Time" to "America/Bogota",
        "Eastern Standard Time (Mexico)" to "America/Cancun",
        "Eastern Standard Time" to "America/New_York",
        "Haiti Standard Time" to "America/Port-au-Prince",
        "Cuba Standard Time" to "America/Havana",
        "US Eastern Standard Time" to "America/Indiana/Indianapolis",
        "Turks And Caicos Standard Time" to "America/Grand_Turk",
        "Paraguay Standard Time" to "America/Asuncion",
        "Atlantic Standard Time" to "America/Halifax",
        "Venezuela Standard Time" to "America/Caracas",
        "Central Brazilian Standard Time" to "America/Cuiaba",
        "SA Western Standard Time" to "America/La_Paz",
        "Pacific SA Standard Time" to "America/Santiago",
        "Newfoundland Standard Time" to "America/St_Johns",
        "Tocantins Standard Time" to "America/Araguaina",
        "E. South America Standard Time" to "America/Sao_Paulo",
        "SA Eastern Standard Time" to "America/Cayenne",
        "Argentina Standard Time" to "America/Argentina/Buenos_Aires",
        "Greenland Standard Time" to "America/Nuuk",
        "Montevideo Standard Time" to "America/Montevideo",
        "Magallanes Standard Time" to "America/Punta_Arenas",
        "Saint Pierre Standard Time" to "America/Miquelon",
        "Bahia Standard Time" to "America/Bahia",
        "UTC-02" to "Etc/GMT+2",
        "Azores Standard Time" to "Atlantic/Azores",
        "Cape Verde Standard Time" to "Atlantic/Cape_Verde",
        "UTC" to "Etc/UTC",
        "GMT Standard Time" to "Europe/London",
        "Greenwich Standard Time" to "Atlantic/Reykjavik",
        "Sao Tome Standard Time" to "Africa/Sao_Tome",
        "Morocco Standard Time" to "Africa/Casablanca",
        "W. Europe Standard Time" to "Europe/Berlin",
        "Central Europe Standard Time" to "Europe/Budapest",
        "Romance Standard Time" to "Europe/Paris",
        "Central European Standard Time" to "Europe/Warsaw",
        "W. Central Africa Standard Time" to "Africa/Lagos",
        "Jordan Standard Time" to "Asia/Amman",
        "GTB Standard Time" to "Europe/Bucharest",
        "Middle East Standard Time" to "Asia/Beirut",
        "Egypt Standard Time" to "Africa/Cairo",
        "E. Europe Standard Time" to "Europe/Chisinau",
        "Syria Standard Time" to "Asia/Damascus",
        "West Bank Standard Time" to "Asia/Hebron",
        "South Africa Standard Time" to "Africa/Johannesburg",
        "FLE Standard Time" to "Europe/Kiev",
        "Israel Standard Time" to "Asia/Jerusalem",
        "South Sudan Standard Time" to "Africa/Juba",
        "Kaliningrad Standard Time" to "Europe/Kaliningrad",
        "Sudan Standard Time" to "Africa/Khartoum",
        "Libya Standard Time" to "Africa/Tripoli",
        "Namibia Standard Time" to "Africa/Windhoek",
        "Arabic Standard Time" to "Asia/Baghdad",
        "Turkey Standard Time" to "Europe/Istanbul",
        "Arab Standard Time" to "Asia/Riyadh",
        "Belarus Standard Time" to "Europe/Minsk",
        "Russian Standard Time" to "Europe/Moscow",
        "E. Africa Standard Time" to "Africa/Nairobi",
        "Volgograd Standard Time" to "Europe/Volgograd",
        "Iran Standard Time" to "Asia/Tehran",
        "Arabian Standard Time" to "Asia/Dubai",
        "Astrakhan Standard Time" to "Europe/Astrakhan",
        "Azerbaijan Standard Time" to "Asia/Baku",
        "Russia Time Zone 3" to "Europe/Samara",
        "Mauritius Standard Time" to "Indian/Mauritius",
        "Saratov Standard Time" to "Europe/Saratov",
        "Georgian Standard Time" to "Asia/Tbilisi",
        "Caucasus Standard Time" to "Asia/Yerevan",
        "Afghanistan Standard Time" to "Asia/Kabul",
        "West Asia Standard Time" to "Asia/Tashkent",
        "Ekaterinburg Standard Time" to "Asia/Yekaterinburg",
        "Pakistan Standard Time" to "Asia/Karachi",
        "Qyzylorda Standard Time" to "Asia/Qyzylorda",
        "India Standard Time" to "Asia/Kolkata",
        "Sri Lanka Standard Time" to "Asia/Colombo",
        "Nepal Standard Time" to "Asia/Kathmandu",
        "Central Asia Standard Time" to "Asia/Bishkek",
        "Bangladesh Standard Time" to "Asia/Dhaka",
        "Omsk Standard Time" to "Asia/Omsk",
        "Myanmar Standard Time" to "Asia/Yangon",
        "SE Asia Standard Time" to "Asia/Bangkok",
        "Altai Standard Time" to "Asia/Barnaul",
        "W. Mongolia Standard Time" to "Asia/Hovd",
        "North Asia Standard Time" to "Asia/Krasnoyarsk",
        "N. Central Asia Standard Time" to "Asia/Novosibirsk",
        "Tomsk Standard Time" to "Asia/Tomsk",
        "China Standard Time" to "Asia/Shanghai",
        "North Asia East Standard Time" to "Asia/Irkutsk",
        "Singapore Standard Time" to "Asia/Singapore",
        "W. Australia Standard Time" to "Australia/Perth",
        "Taipei Standard Time" to "Asia/Taipei",
        "Ulaanbaatar Standard Time" to "Asia/Ulaanbaatar",
        "Aus Central W. Standard Time" to "Australia/Eucla",
        "Transbaikal Standard Time" to "Asia/Chita",
        "Tokyo Standard Time" to "Asia/Tokyo",
        "North Korea Standard Time" to "Asia/Pyongyang",
        "Korea Standard Time" to "Asia/Seoul",
        "Yakutsk Standard Time" to "Asia/Yakutsk",
        "Cen. Australia Standard Time" to "Australia/Adelaide",
        "AUS Central Standard Time" to "Australia/Darwin",
        "E. Australia Standard Time" to "Australia/Brisbane",
        "AUS Eastern Standard Time" to "Australia/Sydney",
        "West Pacific Standard Time" to "Pacific/Port_Moresby",
        "Tasmania Standard Time" to "Australia/Hobart",
        "Vladivostok Standard Time" to "Asia/Vladivostok",
        "Lord Howe Standard Time" to "Australia/Lord_Howe",
        "Bougainville Standard Time" to "Pacific/Bougainville",
        "Russia Time Zone 10" to "Asia/Srednekolymsk",
        "Magadan Standard Time" to "Asia/Magadan",
        "Norfolk Standard Time" to "Pacific/Norfolk",
        "Sakhalin Standard Time" to "Asia/Sakhalin",
        "Central Pacific Standard Time" to "Pacific/Guadalcanal",
        "Russia Time Zone 11" to "Asia/Kamchatka",
        "New Zealand Standard Time" to "Pacific/Auckland",
        "UTC+12" to "Etc/GMT-12",
        "Fiji Standard Time" to "Pacific/Fiji",
        "Chatham Islands Standard Time" to "Pacific/Chatham",
        "UTC+13" to "Etc/GMT-13",
        "Tonga Standard Time" to "Pacific/Tongatapu",
        "Samoa Standard Time" to "Pacific/Apia",
        "Line Islands Standard Time" to "Pacific/Kiritimati",
    ).mapKeys { it.key.lowercase() }
}
