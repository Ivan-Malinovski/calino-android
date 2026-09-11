package calino.malinov.ski.poc.data.repository

import androidx.compose.runtime.mutableStateOf
import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.model.CalDavCalendar
import calino.malinov.ski.poc.data.model.CalDavForm
import calino.malinov.ski.poc.data.model.ContactAddressBook
import calino.malinov.ski.poc.state.accountId
import calino.malinov.ski.poc.state.defaultDisplayName
import calino.malinov.ski.poc.state.normalizeServerUrl
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Persistence for the account list.
 *
 * Passwords never travel through here -- they live in the credential store,
 * encrypted. This holds only what is safe to write as plain JSON: server URL,
 * username, display name, and the discovered collections with their enabled
 * flags.
 */
interface CalDavAccountPersistence {
    fun load(): List<CalDavAccount>
    fun save(accounts: List<CalDavAccount>)

    /** Keeps the store in-memory, as it was before accounts persisted. */
    object None : CalDavAccountPersistence {
        override fun load(): List<CalDavAccount> = emptyList()
        override fun save(accounts: List<CalDavAccount>) = Unit
    }
}

/**
 * Holder for connected CalDAV accounts, kept deliberately apart from
 * [CalinoRepository] so the frozen fixture contract stays untouched. It mirrors
 * that repository's `mutableStateOf` plus observer shape so the UI reads both
 * the same way.
 *
 * No password is ever stored here: only the URL, username, and discovered
 * collections. The password goes to the credential store, encrypted under an
 * Android Keystore key.
 */
class CalDavAccountStore(
    private val persistence: CalDavAccountPersistence = CalDavAccountPersistence.None,
) {
    private val state = mutableStateOf<List<CalDavAccount>>(persistence.load())
    private val listeners = CopyOnWriteArrayList<(List<CalDavAccount>) -> Unit>()

    fun accounts(): List<CalDavAccount> = state.value

    fun observe(listener: (List<CalDavAccount>) -> Unit): Closeable {
        listeners += listener
        listener(accounts())
        return Closeable { listeners.remove(listener) }
    }

    /**
     * Adds the account, or replaces one already connected to the same server and
     * username, so reconnecting corrects a selection rather than duplicating it.
     */
    fun addAccount(
        form: CalDavForm,
        calendars: List<CalDavCalendar>,
        addressBooks: List<ContactAddressBook> = emptyList(),
    ): CalDavAccount {
        val account = CalDavAccount(
            id = accountId(form),
            displayName = defaultDisplayName(form),
            serverUrl = normalizeServerUrl(form.serverUrl) ?: form.serverUrl.trim(),
            username = form.username.trim(),
            calendars = calendars,
            addressBooks = addressBooks,
        )
        update { existing ->
            val without = existing.filterNot { it.id == account.id }
            without + account
        }
        return account
    }

    fun setCalendarEnabled(accountId: String, calendarId: String, enabled: Boolean) {
        update { accounts ->
            accounts.map { account ->
                if (account.id != accountId) {
                    account
                } else {
                    account.copy(
                        calendars = account.calendars.map { calendar ->
                            if (calendar.id == calendarId) calendar.copy(enabled = enabled) else calendar
                        },
                    )
                }
            }
        }
    }

    fun updateCalendarPresentation(
        accountId: String,
        calendarId: String,
        visible: Boolean? = null,
        showTasksInViews: Boolean? = null,
        name: String? = null,
        color: Long? = null,
    ) {
        update { accounts ->
            accounts.map { account ->
                if (account.id != accountId) account else account.copy(
                    calendars = account.calendars.map { calendar ->
                        if (calendar.id != calendarId) calendar else calendar.copy(
                            visible = visible ?: calendar.visible,
                            showTasksInViews = showTasksInViews ?: calendar.showTasksInViews,
                            name = name ?: calendar.name,
                            color = color ?: calendar.color,
                        )
                    },
                )
            }
        }
    }

    /** Replaces an account's collections after a rediscovery. */
    fun replaceCalendars(accountId: String, calendars: List<CalDavCalendar>) {
        update { accounts ->
            accounts.map { account ->
                if (account.id == accountId) account.copy(calendars = calendars) else account
            }
        }
    }

    fun setAddressBookEnabled(accountId: String, addressBookId: String, enabled: Boolean) {
        update { accounts ->
            accounts.map { account ->
                if (account.id != accountId) account else account.copy(
                    addressBooks = account.addressBooks.map { book ->
                        if (book.id == addressBookId) book.copy(enabled = enabled) else book
                    },
                )
            }
        }
    }

    fun replaceAddressBooks(accountId: String, addressBooks: List<ContactAddressBook>) {
        update { accounts ->
            accounts.map { account -> if (account.id == accountId) account.copy(addressBooks = addressBooks) else account }
        }
    }

    fun removeAccount(accountId: String) {
        update { accounts -> accounts.filterNot { it.id == accountId } }
    }

    private fun update(transform: (List<CalDavAccount>) -> List<CalDavAccount>) {
        state.value = transform(state.value)
        persistence.save(state.value)
        listeners.forEach { it(state.value) }
    }
}
