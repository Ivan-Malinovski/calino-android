package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.ContactAddressBook
import calino.malinov.ski.data.repository.CalDavAccountStore
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.CalDavSource
import calino.malinov.ski.data.repository.CardDavSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AccountRediscoveryResult {
    data object Ready : AccountRediscoveryResult
    data object AccountsRemoved : AccountRediscoveryResult
    data class CredentialsUnavailable(val accountName: String) : AccountRediscoveryResult
}

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
    private val cardDiscovery: CardDavDiscovery = CardDavDiscovery(),
) {

    /** Discovered collections per account, so a toggle needs no round trip. */
    private val discovered = mutableMapOf<String, List<DiscoveredCalendar>>()
    private val discoveredAddressBooks = mutableMapOf<String, List<DiscoveredAddressBook>>()

    /** Cursor that was committed before the most recent discovery metadata. */
    private val committedCursors = mutableMapOf<String, Map<String, CollectionCursor>>()
    private val committedAddressBookCursors = mutableMapOf<String, Map<String, CollectionCursor>>()

    /** Which discovered metadata has not yet been consumed by a read. */
    private val freshCalendarMetadata = mutableMapOf<String, Set<String>>()
    private val freshAddressBookMetadata = mutableMapOf<String, Set<String>>()

    init {
        repository.setCalendarCursorListener { accountId, calendarUrl, cursor ->
            // Repository reads run on its I/O context. Account state is a UI
            // store, so marshal the persistence update back through the
            // manager's lifecycle scope.
            scope.launch { commitCalendarCursor(accountId, calendarUrl, cursor) }
        }
        repository.setAddressBookCursorListener { accountId, addressBookUrl, cursor ->
            scope.launch { commitAddressBookCursor(accountId, addressBookUrl, cursor) }
        }
    }

    /**
     * Records the password for a newly connected account and starts reading it.
     * Called once, from the add-account sheet, with the only copy of the
     * password that ever exists outside the sheet's own draft state.
     */
    fun onAccountConnected(account: CalDavAccount, password: String) {
        credentialStore.save(account.id, DavCredentials(account.username, password))
        committedCursors.remove(account.id)
        committedAddressBookCursors.remove(account.id)
        freshCalendarMetadata.remove(account.id)
        freshAddressBookMetadata.remove(account.id)
    }

    fun onAccountRemoved(accountId: String) {
        credentialStore.clear(accountId)
        discovered.remove(accountId)
        discoveredAddressBooks.remove(accountId)
        committedCursors.remove(accountId)
        committedAddressBookCursors.remove(accountId)
        freshCalendarMetadata.remove(accountId)
        freshAddressBookMetadata.remove(accountId)
        applySources(refreshAfterSourceChange = false)
    }

    /** Re-reads the enabled set without touching the network. */
    fun onCalendarsToggled() = applySources(refreshAfterSourceChange = false)

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
        val accounts = restoreFromCache()
        scope.launch { accounts.forEach { rediscover(it) } }
    }

    /** Refreshes discovery metadata for the selected accounts, then adopts it without starting a read. */
    suspend fun rediscoverAccounts(
        accountIds: Set<String>,
        isCurrent: () -> Boolean,
    ): AccountRediscoveryResult {
        if (!isCurrent()) return AccountRediscoveryResult.AccountsRemoved
        val accounts = accountStore.accounts().filter { it.id in accountIds }
        if (accounts.size != accountIds.size) return AccountRediscoveryResult.AccountsRemoved
        accounts.forEach { account ->
            if (!isCurrent()) return AccountRediscoveryResult.AccountsRemoved
            val credentials = credentialStore.load(account.id)
            if (credentials == null) {
                // Do not let in-memory discovery survive a lost Keystore key
                // or missing encrypted credential and feed a stale source
                // back into the repository.
                discovered.remove(account.id)
                discoveredAddressBooks.remove(account.id)
                committedCursors.remove(account.id)
                committedAddressBookCursors.remove(account.id)
                freshCalendarMetadata.remove(account.id)
                freshAddressBookMetadata.remove(account.id)
                applySources(refreshAfterSourceChange = false)
                return AccountRediscoveryResult.CredentialsUnavailable(account.displayName)
            }
            rediscover(
                account,
                applyAfterDiscovery = false,
                credentials = credentials,
                isCurrent = isCurrent,
            )
            if (!isCurrent()) return AccountRediscoveryResult.AccountsRemoved
        }
        if (!isCurrent()) return AccountRediscoveryResult.AccountsRemoved
        applySources(refreshAfterSourceChange = false)
        return AccountRediscoveryResult.Ready
    }

    /**
     * The first half of [restore], and nothing else: seed the sources from the
     * stored collection list so the repository publishes its disk cache, with
     * no network traffic at all.
     *
     * Split out for the home screen widget, which is rendered in whatever
     * process the launcher wakes and must not put that process on the network
     * to draw a list it already has on disk. Returns the accounts it seeded so
     * [restore] can then go and check them against the server.
     */
    fun restoreFromCache(): List<CalDavAccount> {
        val accounts = accountStore.accounts()
        if (accounts.isEmpty()) return emptyList()
        accounts.forEach { account ->
            discovered[account.id] = account.calendars.map { calendar ->
                DiscoveredCalendar(
                    url = calendar.id,
                    displayName = calendar.name,
                    color = calendar.color,
                    readOnly = calendar.readOnly,
                    ctag = calendar.ctag,
                    syncToken = calendar.syncToken,
                    // Unknown until discovery answers, and unused: the fetcher
                    // queries every component regardless of what a collection
                    // advertises. See CalDavFetcher.fetch.
                    components = emptySet(),
                )
            }
            discoveredAddressBooks[account.id] = account.addressBooks.map { book ->
                DiscoveredAddressBook(
                    url = book.url,
                    displayName = book.name,
                    description = book.description,
                    readOnly = book.readOnly,
                    ctag = book.ctag,
                    syncToken = book.syncToken,
                )
            }
        }
        applySources(restoreCacheImmediately = true, refreshAfterSourceChange = false)
        return accounts
    }

    private suspend fun rediscover(
        account: CalDavAccount,
        applyAfterDiscovery: Boolean = true,
        credentials: DavCredentials? = credentialStore.load(account.id),
        isCurrent: () -> Boolean = { true },
    ) {
        credentials ?: return
        if (accountStore.accounts().none { it.id == account.id } || !isCurrent()) return
        val calendarResult = withContext(Dispatchers.IO) {
            runCatching { discovery.discoverAccount(account.serverUrl, credentials) }
        }
        if (!isCurrent()) return
        calendarResult.getOrNull()?.let { found ->
            val stillStored = accountStore.accounts().firstOrNull { it.id == account.id } ?: return
            discovered[account.id] = found.calendars
            val previous = stillStored.calendars.associate { calendar ->
                calendar.id to CollectionCursor(calendar.ctag, calendar.syncToken)
            }
            committedCursors[account.id] = found.calendars.associate { calendar ->
                // An empty cursor is a deliberate sentinel for a newly
                // discovered URL: its fresh token must not be used as if it
                // described the old cache.
                calendar.url to (previous[calendar.url] ?: CollectionCursor())
            }
            freshCalendarMetadata[account.id] = found.calendars.map { it.url }.toSet()
            syncStoredCalendars(stillStored, found.calendars)
        }
        if (!isCurrent()) return
        withContext(Dispatchers.IO) {
            runCatching { cardDiscovery.discoverAccount(account.serverUrl, credentials) }
        }.getOrNull()?.let { found ->
            if (!isCurrent()) return
            val stillStored = accountStore.accounts().firstOrNull { it.id == account.id } ?: return
            discoveredAddressBooks[account.id] = found.addressBooks
            val previous = stillStored.addressBooks.associate { book ->
                book.url to CollectionCursor(book.ctag, book.syncToken)
            }
            committedAddressBookCursors[account.id] = found.addressBooks.associate { book ->
                book.url to (previous[book.url] ?: CollectionCursor())
            }
            freshAddressBookMetadata[account.id] = found.addressBooks.map { it.url }.toSet()
            syncStoredAddressBooks(stillStored, found.addressBooks)
        }
        if (applyAfterDiscovery && isCurrent()) applySources()
    }

    /**
     * Reconciles the stored collection list with what the server now reports,
     * preserving each calendar's enabled flag across the refresh.
     */
    private fun syncStoredCalendars(account: CalDavAccount, found: List<DiscoveredCalendar>) {
        val merged = mergeDiscoveredCalendars(account, found)
        if (merged != account.calendars) accountStore.replaceCalendars(account.id, merged)
    }

    private fun syncStoredAddressBooks(account: CalDavAccount, found: List<DiscoveredAddressBook>) {
        val enabledById = account.addressBooks.associate { it.id to it.enabled }
        val merged = found.map { book ->
            ContactAddressBook(
                id = book.url,
                accountId = account.id,
                url = book.url,
                name = book.displayName,
                description = book.description,
                ctag = book.ctag,
                syncToken = book.syncToken,
                enabled = enabledById[book.url] ?: true,
                readOnly = book.readOnly,
            )
        }
        if (merged != account.addressBooks) accountStore.replaceAddressBooks(account.id, merged)
    }

    private fun applySources(
        restoreCacheImmediately: Boolean = false,
        refreshAfterSourceChange: Boolean = true,
    ) {
        val calendarSources = accountStore.accounts().flatMap { account ->
            val credentials = credentialStore.load(account.id) ?: return@flatMap emptyList()
            val enabled = account.calendars.filter { it.enabled }.map { it.id }.toSet()
            discovered[account.id].orEmpty()
                .filter { it.url in enabled }
                .map {
                    val stored = account.calendars.firstOrNull { calendar -> calendar.id == it.url }
                    CalDavSource(
                        calendar = it,
                        credentials = credentials,
                        accountId = account.id,
                        committedCursor = committedCursors[account.id]?.get(it.url),
                        metadataFresh = it.url in freshCalendarMetadata[account.id].orEmpty(),
                        visible = stored?.visible ?: true,
                        showTasksInViews = stored?.showTasksInViews ?: true,
                    )
                }
        }
        val cardSources = accountStore.accounts().flatMap { account ->
            val credentials = credentialStore.load(account.id) ?: return@flatMap emptyList()
            val enabled = account.addressBooks.filter { it.enabled }.map { it.url }.toSet()
            discoveredAddressBooks[account.id].orEmpty()
                .filter { it.url in enabled }
                .map {
                    CardDavSource(
                        addressBook = it,
                        credentials = credentials,
                        accountId = account.id,
                        committedCursor = committedAddressBookCursors[account.id]?.get(it.url),
                        metadataFresh = it.url in freshAddressBookMetadata[account.id].orEmpty(),
                    )
                }
        }
        repository.setSources(
            calendarSources,
            cardSources,
            restoreCacheImmediately = restoreCacheImmediately,
            refreshAfterSourceChange = refreshAfterSourceChange,
        )
    }

    /** Persists and adopts the cursor only after the repository accepted it. */
    private fun commitCalendarCursor(
        accountId: String,
        calendarUrl: String,
        cursor: CollectionCursor,
    ) {
        committedCursors[accountId] = committedCursors[accountId].orEmpty() + (calendarUrl to cursor)
        freshCalendarMetadata[accountId] = freshCalendarMetadata[accountId].orEmpty() - calendarUrl
        discovered[accountId] = discovered[accountId].orEmpty().map { calendar ->
            if (calendar.url == calendarUrl) {
                calendar.copy(ctag = cursor.ctag, syncToken = cursor.syncToken)
            } else {
                calendar
            }
        }
        val account = accountStore.accounts().firstOrNull { it.id == accountId } ?: return
        val updated = account.calendars.map { calendar ->
            if (calendar.id == calendarUrl) {
                calendar.copy(ctag = cursor.ctag, syncToken = cursor.syncToken)
            } else {
                calendar
            }
        }
        if (updated != account.calendars) accountStore.replaceCalendars(accountId, updated)
    }

    /** Persists and adopts an address-book cursor only after its read commits. */
    private fun commitAddressBookCursor(
        accountId: String,
        addressBookUrl: String,
        cursor: CollectionCursor,
    ) {
        committedAddressBookCursors[accountId] =
            committedAddressBookCursors[accountId].orEmpty() + (addressBookUrl to cursor)
        freshAddressBookMetadata[accountId] =
            freshAddressBookMetadata[accountId].orEmpty() - addressBookUrl
        discoveredAddressBooks[accountId] = discoveredAddressBooks[accountId].orEmpty().map { book ->
            if (book.url == addressBookUrl) {
                book.copy(ctag = cursor.ctag, syncToken = cursor.syncToken)
            } else {
                book
            }
        }
        val account = accountStore.accounts().firstOrNull { it.id == accountId } ?: return
        val updated = account.addressBooks.map { book ->
            if (book.url == addressBookUrl) {
                book.copy(ctag = cursor.ctag, syncToken = cursor.syncToken)
            } else {
                book
            }
        }
        if (updated != account.addressBooks) accountStore.replaceAddressBooks(accountId, updated)
    }
}

/**
 * Combines server-owned calendar metadata with the user's stored visibility.
 *
 * Kept as a pure seam because a rediscovery must update both change cursors at
 * once: persisting only the ctag or only the sync token would make the next
 * incremental decision ambiguous. A newly discovered calendar has no prior
 * visibility preference and is enabled by default.
 */
internal fun mergeDiscoveredCalendars(
    account: CalDavAccount,
    found: List<DiscoveredCalendar>,
): List<CalDavCalendar> {
    val enabledById = account.calendars.associate { it.id to it.enabled }
    val visibleById = account.calendars.associate { it.id to it.visible }
    val showTasksById = account.calendars.associate { it.id to it.showTasksInViews }
    return found.map { calendar ->
        CalDavCalendar(
            id = calendar.url,
            name = calendar.displayName,
            color = calendar.color,
            enabled = enabledById[calendar.url] ?: true,
            readOnly = calendar.readOnly,
            ctag = calendar.ctag,
            syncToken = calendar.syncToken,
            visible = visibleById[calendar.url] ?: true,
            showTasksInViews = showTasksById[calendar.url] ?: true,
        )
    }
}
