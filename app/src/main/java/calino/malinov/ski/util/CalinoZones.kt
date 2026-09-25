package calino.malinov.ski.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * Names and search for IANA zones, for the editor's zone picker, the detail
 * card's "(09:00 New York)" line and the secondary hour rail.
 */
object CalinoZones {

    private val Regions = setOf(
        "Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific",
    )

    /** Region/City zones plus UTC, sorted by city. Aliases like `US/Eastern` are left out. */
    val all: List<ZoneId> by lazy {
        (ZoneId.getAvailableZoneIds().filter { it.substringBefore('/') in Regions && it.contains('/') } + "UTC")
            .map(ZoneId::of)
            .sortedBy { city(it) }
    }

    /** "New York", "Buenos Aires", "UTC". */
    fun city(zone: ZoneId): String =
        zone.id.substringAfterLast('/').replace('_', ' ')

    /** "GMT−4", "GMT+5:30", "GMT". Uses a true minus sign. */
    fun offsetLabel(zone: ZoneId, at: Instant = Instant.now()): String =
        offsetLabel(zone.rules.getOffset(at))

    fun offsetLabel(offset: ZoneOffset): String {
        val seconds = offset.totalSeconds
        if (seconds == 0) return "GMT"
        val sign = if (seconds < 0) "−" else "+"
        val hours = Math.abs(seconds) / 3600
        val minutes = Math.abs(seconds) % 3600 / 60
        return if (minutes == 0) "GMT$sign$hours" else "GMT$sign$hours:%02d".format(minutes)
    }

    /** "New York · GMT−4". */
    fun label(zone: ZoneId, at: Instant = Instant.now()): String = "${city(zone)} · ${offsetLabel(zone, at)}"

    /** "Eastern Daylight Time", for search and screen readers. */
    fun longName(zone: ZoneId, at: Instant = Instant.now()): String {
        val daylight = zone.rules.isDaylightSavings(at)
        return java.util.TimeZone.getTimeZone(zone).getDisplayName(daylight, java.util.TimeZone.LONG, Locale.getDefault())
    }

    /** A short abbreviation for a rail header: "EDT" where the locale has one, else the offset. */
    fun abbreviation(zone: ZoneId, at: Instant = Instant.now()): String {
        val short = java.util.TimeZone.getTimeZone(zone)
            .getDisplayName(zone.rules.isDaylightSavings(at), java.util.TimeZone.SHORT, Locale.getDefault())
        return if (short.startsWith("GMT") || short.startsWith("UTC")) offsetLabel(zone, at) else short
    }

    /** True when the two zones put [at] on the same wall clock. */
    fun sameOffset(a: ZoneId, b: ZoneId, at: Instant): Boolean = a.rules.getOffset(at) == b.rules.getOffset(at)

    /**
     * Zones whose city, id, region, long name or offset contains every word
     * of [query]. City prefix matches sort first.
     */
    fun search(query: String, at: Instant = Instant.now()): List<ZoneId> {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.isEmpty()) return all
        return all.filter { zone ->
            val haystack = listOf(
                city(zone), zone.id.replace('_', ' '), longName(zone, at), offsetLabel(zone, at),
                zone.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            ).joinToString(" ").lowercase(Locale.ROOT)
            words.all { it in haystack }
        }.sortedByDescending { city(it).lowercase(Locale.ROOT).startsWith(words.first()) }
    }
}
