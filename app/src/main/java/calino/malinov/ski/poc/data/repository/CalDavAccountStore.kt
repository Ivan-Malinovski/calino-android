package calino.malinov.ski.poc.data.repository

import androidx.compose.runtime.mutableStateOf
import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.model.CalDavCalendar
import calino.malinov.ski.poc.data.model.CalDavForm
import calino.malinov.ski.poc.state.accountId
import calino.malinov.ski.poc.state.defaultDisplayName
import calino.malinov.ski.poc.state.normalizeServerUrl
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-local holder for connected CalDAV accounts, kept deliberately apart
 * from [CalinoRepository] so the frozen fixture contract stays untouched. It
 * mirrors that repository's `mutableStateOf` plus observer shape so the UI
 * reads both the same way. Nothing here is persisted, and no password is ever
 * stored: only the URL, username, and discovered collections survive the sheet.
 */
class CalDavAccountStore {
    private val state = mutableStateOf<List<CalDavAccount>>(emptyList())
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
    fun addAccount(form: CalDavForm, calendars: List<CalDavCalendar>): CalDavAccount {
        val account = CalDavAccount(
            id = accountId(form),
            displayName = defaultDisplayName(form),
            serverUrl = normalizeServerUrl(form.serverUrl) ?: form.serverUrl.trim(),
            username = form.username.trim(),
            calendars = calendars,
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

    fun removeAccount(accountId: String) {
        update { accounts -> accounts.filterNot { it.id == accountId } }
    }

    private fun update(transform: (List<CalDavAccount>) -> List<CalDavAccount>) {
        state.value = transform(state.value)
        listeners.forEach { it(state.value) }
    }
}
