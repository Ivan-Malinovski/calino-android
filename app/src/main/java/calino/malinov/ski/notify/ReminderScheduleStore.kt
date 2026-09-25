package calino.malinov.ski.notify

import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/**
 * The reminder schedule, as it survives the process.
 *
 * A boot receiver has no Activity, no ViewModel and no repository, so it cannot
 * recompute what is due -- it can only read what the app last worked out. This
 * file is that record: the planned firings, plus the two things the planner
 * cannot know because they are history rather than data, namely which firings
 * have already been delivered and which the user snoozed.
 *
 * [zoneId] is stored with the plan because the anchors were computed in it. A
 * receiver woken by `TIMEZONE_CHANGED` can then tell that the plan is stale
 * without being able to fix it, and say so, instead of quietly notifying at the
 * old zone's times forever.
 */
data class ReminderSchedule(
    val generatedAt: Instant,
    val zoneId: String,
    val firings: List<ReminderFiring> = emptyList(),
    /** Firing key to the moment it was posted. */
    val delivered: Map<String, Instant> = emptyMap(),
    /** Firing key to the moment it should be raised again. */
    val snoozed: Map<String, Instant> = emptyMap(),
) {
    companion object {
        val Empty = ReminderSchedule(Instant.EPOCH, ZoneId.systemDefault().id)
    }
}

interface ReminderScheduleStore {

    fun load(): ReminderSchedule

    /** Replace the planned firings, keeping the delivery and snooze history. */
    fun replace(firings: List<ReminderFiring>, generatedAt: Instant, zone: ZoneId)

    fun markDelivered(key: String, at: Instant)

    fun snooze(key: String, until: Instant)

    /**
     * The next firing to arm an alarm for, or null when there is nothing left.
     *
     * A snoozed firing is reported at its snooze time, not its original one.
     */
    fun next(now: Instant): ReminderFiring?

    /**
     * Everything that should have fired by [now] and has not been delivered.
     *
     * [grace] bounds how far back this reaches. A device that was off for a
     * week must not wake up and post sixty stale reminders; one that missed an
     * alarm by two minutes should still deliver it.
     */
    fun due(now: Instant, grace: Duration): List<ReminderFiring>

    /** Process-local implementation for tests and previews. */
    class InMemory : ReminderScheduleStore {
        private var schedule = ReminderSchedule.Empty
        private val lock = Any()

        override fun load(): ReminderSchedule = synchronized(lock) { schedule }

        override fun replace(firings: List<ReminderFiring>, generatedAt: Instant, zone: ZoneId) =
            synchronized(lock) {
                schedule = schedule.replacing(firings, generatedAt, zone)
            }

        override fun markDelivered(key: String, at: Instant) = synchronized(lock) {
            schedule = schedule.copy(
                delivered = schedule.delivered + (key to at),
                snoozed = schedule.snoozed - key,
            )
        }

        override fun snooze(key: String, until: Instant) = synchronized(lock) {
            schedule = schedule.copy(
                snoozed = schedule.snoozed + (key to until),
                delivered = schedule.delivered - key,
            )
        }

        override fun next(now: Instant): ReminderFiring? = load().next(now)

        override fun due(now: Instant, grace: Duration): List<ReminderFiring> = load().due(now, grace)
    }
}

/**
 * File-backed schedule. Synchronous on purpose: callers already own an IO
 * context, and a receiver needs to read this without a coroutine at all.
 */
class FileReminderScheduleStore(
    file: File,
    private val clock: Clock = Clock.systemUTC(),
    private val historyRetention: Duration = Duration.ofDays(7),
) : ReminderScheduleStore {

    private val document = AtomicJsonFile(file)
    private val lock = Any()
    private var cached: ReminderSchedule? = null

    override fun load(): ReminderSchedule = synchronized(lock) { loadLocked() }

    override fun replace(firings: List<ReminderFiring>, generatedAt: Instant, zone: ZoneId) =
        synchronized(lock) {
            persist(loadLocked().replacing(firings, generatedAt, zone).pruned(clock.instant(), historyRetention))
        }

    override fun markDelivered(key: String, at: Instant) = synchronized(lock) {
        val current = loadLocked()
        persist(current.copy(delivered = current.delivered + (key to at), snoozed = current.snoozed - key))
    }

    override fun snooze(key: String, until: Instant) = synchronized(lock) {
        val current = loadLocked()
        persist(current.copy(snoozed = current.snoozed + (key to until), delivered = current.delivered - key))
    }

    override fun next(now: Instant): ReminderFiring? = load().next(now)

    override fun due(now: Instant, grace: Duration): List<ReminderFiring> = load().due(now, grace)

    private fun loadLocked(): ReminderSchedule {
        cached?.let { return it }
        val text = document.read()
        val parsed = text?.let { ReminderScheduleJson.decode(it) } ?: ReminderSchedule.Empty
        cached = parsed
        return parsed
    }

    private fun persist(schedule: ReminderSchedule) {
        document.write(ReminderScheduleJson.encode(schedule))
        cached = schedule
    }
}

/**
 * The next firing to arm an alarm for: the earliest undelivered one still in
 * the future. Anything already past is [due]'s business, not the alarm's.
 */
internal fun ReminderSchedule.next(now: Instant): ReminderFiring? =
    pending().filter { effectiveTime(it).isAfter(now) }.minByOrNull { effectiveTime(it) }

internal fun ReminderSchedule.due(now: Instant, grace: Duration): List<ReminderFiring> {
    val floor = now.minus(grace)
    return pending()
        .filter { firing ->
            val at = effectiveTime(firing)
            !at.isAfter(now) && !at.isBefore(floor)
        }
        .sortedBy { effectiveTime(it) }
}

/**
 * Firings still owed to the user. A snooze re-opens one that was already
 * delivered -- that is exactly what the button means.
 */
private fun ReminderSchedule.pending(): List<ReminderFiring> =
    firings.filter { firing -> firing.key in snoozed || firing.key !in delivered }

private fun ReminderSchedule.effectiveTime(firing: ReminderFiring): Instant =
    snoozed[firing.key] ?: firing.at

private fun ReminderSchedule.replacing(
    firings: List<ReminderFiring>,
    generatedAt: Instant,
    zone: ZoneId,
): ReminderSchedule {
    val keys = firings.map { it.key }.toSet()
    return copy(
        generatedAt = generatedAt,
        zoneId = zone.id,
        firings = firings,
        // History for a firing that no longer exists is dead weight, but a
        // firing that is merely outside the new horizon may come back; keeping
        // the watermark only for live keys is what stops a re-plan from
        // re-notifying, and `pruned` bounds the rest by age.
        delivered = delivered.filterKeys { it in keys },
        snoozed = snoozed.filterKeys { it in keys },
    )
}

private fun ReminderSchedule.pruned(now: Instant, retention: Duration): ReminderSchedule {
    val floor = now.minus(retention)
    return copy(
        delivered = delivered.filterValues { it.isAfter(floor) },
        snoozed = snoozed.filterValues { it.isAfter(floor) },
    )
}

/**
 * Versioned JSON. An unreadable or future-version document decodes to null so
 * the caller starts from an empty schedule: losing the plan costs one
 * regeneration, while throwing here would take a boot receiver down with it.
 */
internal object ReminderScheduleJson {

    const val VERSION = 1

    fun encode(schedule: ReminderSchedule): String = JSONObject()
        .put("version", VERSION)
        .put("generatedAt", schedule.generatedAt.toString())
        .put("zoneId", schedule.zoneId)
        .put("firings", JSONArray().apply { schedule.firings.forEach { put(encodeFiring(it)) } })
        .put("delivered", encodeMoments(schedule.delivered))
        .put("snoozed", encodeMoments(schedule.snoozed))
        .toString()

    fun decode(raw: String): ReminderSchedule? = runCatching {
        val root = JSONObject(raw)
        if (root.optInt("version", -1) != VERSION) return null
        val firings = root.optJSONArray("firings")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::decodeFiring)
            }
        } ?: emptyList()
        ReminderSchedule(
            generatedAt = Instant.parse(root.getString("generatedAt")),
            zoneId = root.getString("zoneId"),
            firings = firings,
            delivered = decodeMoments(root.optJSONObject("delivered")),
            snoozed = decodeMoments(root.optJSONObject("snoozed")),
        )
    }.getOrNull()

    private fun encodeFiring(firing: ReminderFiring): JSONObject = JSONObject()
        .put("key", firing.key)
        .put("at", firing.at.toString())
        .put("kind", firing.kind.name)
        .put("recordId", firing.recordId)
        .putNullable("uid", firing.uid)
        .putNullable("occurrenceDay", firing.occurrenceDay)
        .put("title", firing.title)
        .put("subtitle", firing.subtitle)
        .putNullable("location", firing.location)
        .putNullable("meetingUrl", firing.meetingUrl)
        .put("minutesBefore", firing.minutesBefore)
        .put("anchor", firing.anchor.toString())

    private fun decodeFiring(json: JSONObject): ReminderFiring? = runCatching {
        ReminderFiring(
            key = json.getString("key"),
            at = Instant.parse(json.getString("at")),
            kind = ReminderKind.valueOf(json.getString("kind")),
            recordId = json.getString("recordId"),
            uid = json.optionalString("uid"),
            occurrenceDay = if (json.isNull("occurrenceDay")) null else json.getLong("occurrenceDay"),
            title = json.getString("title"),
            subtitle = json.optString("subtitle", ""),
            location = json.optionalString("location"),
            meetingUrl = json.optionalString("meetingUrl"),
            minutesBefore = json.getInt("minutesBefore"),
            anchor = Instant.parse(json.getString("anchor")),
        )
    }.getOrNull()

    private fun encodeMoments(values: Map<String, Instant>): JSONObject = JSONObject().apply {
        values.forEach { (key, at) -> put(key, at.toString()) }
    }

    private fun decodeMoments(json: JSONObject?): Map<String, Instant> {
        if (json == null) return emptyMap()
        val result = mutableMapOf<String, Instant>()
        json.keys().forEach { key ->
            runCatching { Instant.parse(json.getString(key)) }.getOrNull()?.let { result[key] = it }
        }
        return result
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)
}
