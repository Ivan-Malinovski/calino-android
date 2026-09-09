package calino.malinov.ski.poc.data.caldav

import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.model.CalDavCalendar
import calino.malinov.ski.poc.data.repository.CalDavAccountStore
import calino.malinov.ski.poc.data.repository.CalDavRepository
import calino.malinov.ski.poc.data.repository.CalDavSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Turns the account list into the collections the repository reads.
 *
 * This is the wire that was missing: until now `CalDavCalendar.enabled` toggled
 * a checkbox and nothing else. Enabling or disabling a collection here changes
 * what the next snapshot contains.
 */
class CalDavConnectionManager(
    private val accountStore: CalDavAccountStore,
    private val credentialStore: CredentialStore,
    private val repository: CalDavRepository,
    private val discovery: CalDavDiscovery,
    private val scope: CoroutineScope,
) {

    /** Discovered collections per account, so a toggle needs no round trip. */
    private val discovered = mutableMapOf<String, List<DiscoveredCalendar>>()

    /**
     * Records the password for a newly connected account and starts reading it.
     * Called once, from the add-account sheet, with the only copy of the
     * password that ever exists outside the sheet's own draft state.
     */
    fun onAccountConnected(account: CalDavAccount, password: String) {
        credentialStore.save(account.id, DavCredentials(account.username, password))
        scope.launch { rediscover(account) }
    }

    fun onAccountRemoved(accountId: String) {
        credentialStore.clear(accountId)
        discovered.remove(accountId)
        applySources()
    }

    /** Re-reads the enabled set without touching the network. */
    fun onCalendarsToggled() = applySources()

    /**
     * Restores every persisted account after a cold start.
     *
     * The stored collection list seeds the sources *first*, synchronously, so
     * the repository can render its cache without waiting on a network round
     * trip. Discovery then runs anyway, because the stored list is the last
     * thing seen rather than the truth: a calendar renamed, recoloured, or
     * deleted on the server is picked up when it returns.
     */
    fun restore() {
        val accounts = accountStore.accounts()
        if (accounts.isEmpty()) return
        accounts.forEach { account ->
            discovered[account.id] = account.calendars.map { calendar ->
                DiscoveredCalendar(
                    url = calendar.id,
                    displayName = calendar.name,
                    color = calendar.color,
                    readOnly = calendar.readOnly,
                    // Unknown until discovery answers, and unused: the fetcher
                    // queries every component regardless of what a collection
                    // advertises. See CalDavFetcher.fetch.
                    components = emptySet(),
                )
            }
        }
        applySources()
        scope.launch { accounts.forEach { rediscover(it) } }
    }

    private suspend fun rediscover(account: CalDavAccount) {
        val credentials = credentialStore.load(account.id) ?: return
        val result = withContext(Dispatchers.IO) {
            runCatching { discovery.discoverAccount(account.serverUrl, credentials) }
        }
        result.getOrNull()?.let { found ->
            discovered[account.id] = found.calendars
            syncStoredCalendars(account, found.calendars)
        }
        applySources()
    }

    /**
     * Reconciles the stored collection list with what the server now reports,
     * preserving each calendar's enabled flag across the refresh.
     */
    private fun syncStoredCalendars(account: CalDavAccount, found: List<DiscoveredCalendar>) {
        val enabledById = account.calendars.associate { it.id to it.enabled }
        val merged = found.map { calendar ->
            CalDavCalendar(
                id = calendar.url,
                name = calendar.displayName,
                color = calendar.color,
                // A calendar the user has not seen yet defaults to on.
                enabled = enabledById[calendar.url] ?: true,
                readOnly = calendar.readOnly,
            )
        }
        if (merged != account.calendars) accountStore.replaceCalendars(account.id, merged)
    }

    private fun applySources() {
        val sources = accountStore.accounts().flatMap { account ->
            val credentials = credentialStore.load(account.id) ?: return@flatMap emptyList()
            val enabled = account.calendars.filter { it.enabled }.map { it.id }.toSet()
            discovered[account.id].orEmpty()
                .filter { it.url in enabled }
                .map { CalDavSource(it, credentials, account.id) }
        }
        repository.setSources(sources)
    }
}
