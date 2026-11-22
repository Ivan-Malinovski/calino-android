package calino.malinov.ski.data.settings

import calino.malinov.ski.data.caldav.SettingsCalendarClient
import calino.malinov.ski.data.caldav.SettingsRemote
import calino.malinov.ski.data.caldav.SettingsCollection
import calino.malinov.ski.data.model.CategoryStyleRule
import java.time.Instant

/**
 * What a settings cycle did, for the UI and for tests.
 */
sealed interface SettingsSyncOutcome {
    /** The feature is off, or the account has nothing to sync with. */
    data object Skipped : SettingsSyncOutcome
    /** The remote had newer rules and they were applied. */
    data class Pulled(val rules: Int) : SettingsSyncOutcome
    /** Local rules were sent because the remote was behind (or absent). */
    data class Pushed(val rules: Int) : SettingsSyncOutcome
    /** Both sides already agreed; nothing was transferred. */
    data object Unchanged : SettingsSyncOutcome
    /** The server refused or could not be reached; local preferences stand. */
    data class Failed(val reason: String) : SettingsSyncOutcome
}

/**
 * Where the device keeps its half of the sync: the rules, and the two
 * timestamps the conflict rule needs. Supplied by the app so this file stays
 * plain JVM code and can be tested without Android.
 */
interface SettingsSyncState {
    val enabled: Boolean

    /** When the remote was last pulled successfully. Null until a first pull. */
    val lastPulledAt: Instant?

    /** When the person last changed the rules on this device, null if never. */
    val rulesChangedAt: Instant?

    /** Rules the person currently sees. */
    fun localRules(): List<CategoryStyleRule>

    fun rememberPulled(at: Instant, rules: List<CategoryStyleRule>)

    fun rememberPushed(at: Instant)

    /** The hidden collection, remembered so later cycles skip the lookup. */
    var collection: SettingsCollection?
}

/**
 * Category styles over the Calino Settings calendar the web app already uses.
 *
 * The rules are as Calino-only as any preference: an event carries its category
 * *names* in `CATEGORIES`, and a colour has nowhere else to go, so an extra
 * calendar collection is the least surprising place to keep them. Using the
 * web app's collection (rather than an `X-` property or a per-event colour) also
 * means the browser and the phone share one source of truth, and no other
 * calendar client ever sees a property it cannot interpret.
 *
 * The conflict rule is the web app's: a remote event stamped later than our last
 * pull wins, otherwise this device's rules do. A push is a read-modify-write, so
 * every setting this app does not own travels back to the server untouched.
 */
class SettingsSyncer(
    private val client: SettingsRemote,
    private val state: SettingsSyncState,
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun synchronize(homeUrl: String, credentials: calino.malinov.ski.data.caldav.DavCredentials): SettingsSyncOutcome {
        if (!state.enabled) return SettingsSyncOutcome.Skipped
        val collection = state.collection ?: client.ensure(homeUrl, credentials)?.also { state.collection = it }
            ?: return SettingsSyncOutcome.Failed("no settings collection")

        val remote = client.read(collection, credentials)
        val lastPulled = state.lastPulledAt
        val remoteStamp = remote?.stamp

        // Remote wins when it was written after we last looked. A remote with no
        // readable stamp is treated as ours to overwrite only if we have never
        // pulled: an unreadable payload must not silently replace good rules.
        if (remote != null && (lastPulled == null || (remoteStamp != null && remoteStamp.isAfter(lastPulled)))) {
            val payload = SettingsSyncPayload.parse(remote.payload)
            val rules = payload?.categoryStyleRules()
            if (payload != null && rules != null) {
                state.rememberPulled(remoteStamp ?: clock(), rules)
                return SettingsSyncOutcome.Pulled(rules.size)
            }
        }

        // A remote that has never been written is seeded from this device, even
        // when no rule was touched here: that is what makes a second phone find
        // the person's rules on its first sync.
        val changedAt = state.rulesChangedAt
        val localWins = remote == null || (changedAt != null && (lastPulled == null || changedAt.isAfter(lastPulled)))
        if (!localWins) return SettingsSyncOutcome.Unchanged

        return push(collection, credentials, remote)
    }

    /**
     * Sends this device's rules, merging into whatever the remote holds.
     *
     * A conflict (412: another device wrote since we read) is retried once with
     * a fresh read, which is what makes two phones editing different settings at
     * the same time end up with both -- each write carries the other's keys.
     */
    private suspend fun push(
        collection: SettingsCollection,
        credentials: calino.malinov.ski.data.caldav.DavCredentials,
        current: calino.malinov.ski.data.caldav.RemoteSettings?,
    ): SettingsSyncOutcome {
        var remote = current
        val rules = state.localRules()
        repeat(2) { attempt ->
            val now = clock()
            val base = SettingsSyncPayload.parse(remote?.payload) ?: SettingsSyncPayload.empty(now)
            val next = base.withCategoryStyleRules(rules).stamped(now)
            when (val outcome = client.write(collection, credentials, SettingsEvent.build(next.toJson(), now), remote?.etag)) {
                is SettingsCalendarClient.WriteOutcome.Written -> {
                    state.rememberPushed(now)
                    return SettingsSyncOutcome.Pushed(rules.size)
                }
                SettingsCalendarClient.WriteOutcome.Conflict -> {
                    if (attempt == 1) return SettingsSyncOutcome.Failed("conflict")
                    remote = client.read(collection, credentials)
                }
                is SettingsCalendarClient.WriteOutcome.Failed ->
                    return SettingsSyncOutcome.Failed("status ${outcome.status}")
            }
        }
        return SettingsSyncOutcome.Failed("conflict")
    }
}
