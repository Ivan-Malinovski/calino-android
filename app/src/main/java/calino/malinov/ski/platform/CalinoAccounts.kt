package calino.malinov.ski.platform

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import calino.malinov.ski.R
import calino.malinov.ski.data.model.CalDavAccount

/**
 * The Android accounts that stand for Calino's connected CalDAV accounts.
 *
 * One Android account per CalDAV account, so the system calendar store can
 * attribute calendars to something a person recognises in Settings, and so the
 * sync adapter has an account to be scheduled against.
 *
 * No password is ever handed to [AccountManager]. Calino's credentials stay in
 * `KeystoreCredentialStore`, encrypted under an Android Keystore key, and
 * [AccountManager.addAccountExplicitly] is called with a null password on
 * purpose -- a second copy of a secret is a second thing to leak. The Android
 * account is an identity, not a credential.
 *
 * See `docs/calendar-provider.md` for the ownership rules this file enforces.
 */
object CalinoAccounts {

    /**
     * The account type this build owns, as declared in
     * `res/xml/authenticator.xml`.
     *
     * A build-type resource rather than a constant: debug installs beside
     * release, and two authenticators claiming one type would leave the
     * projection unable to say which app owns an account -- the boundary that
     * keeps it away from the person's Google and Exchange rows.
     */
    fun accountType(context: Context): String =
        context.getString(R.string.calino_account_type)

    /**
     * The Calino account id, carried in user data.
     *
     * The account *name* is for people to read and may collide between two
     * logins that differ only by collection path; this is what the code keys
     * off, and it is the same id [calino.malinov.ski.state.accountId] produces.
     */
    const val UserDataAccountId = "calino_account_id"

    private const val CalendarAuthority = CalendarContract.AUTHORITY

    /**
     * Makes the Android account list match [accounts].
     *
     * Reconciles rather than rebuilds: removing and re-adding an account would
     * drop its sync settings and, worse, make the provider discard the
     * calendars hanging off it, so a pass that changes nothing must write
     * nothing.
     *
     * Silently does nothing when the platform refuses us -- an account that
     * cannot be created means the projection never starts, which is the safe
     * direction to fail in.
     */
    fun sync(context: Context, accounts: List<CalDavAccount>) {
        val manager = AccountManager.get(context)
        val type = accountType(context)
        val existing = runCatching { manager.getAccountsByType(type) }
            .getOrElse { return }
            .associateBy { manager.getUserData(it, UserDataAccountId) }

        val wanted = accounts.associateBy { it.id }

        for ((id, account) in existing) {
            if (id == null || id !in wanted) remove(context, account)
        }

        for ((id, account) in wanted) {
            if (id in existing) continue
            add(manager, account, type)
        }
    }

    /** Removes every Android account this app owns. Used when projection is turned off. */
    fun clear(context: Context) {
        val manager = AccountManager.get(context)
        runCatching { manager.getAccountsByType(accountType(context)) }
            .getOrElse { return }
            .forEach { remove(context, it) }
    }

    /** The Android account standing for [accountId], or null when there is none. */
    fun find(context: Context, accountId: String): Account? {
        val manager = AccountManager.get(context)
        return runCatching { manager.getAccountsByType(accountType(context)) }
            .getOrElse { return null }
            .firstOrNull { manager.getUserData(it, UserDataAccountId) == accountId }
    }

    private fun add(manager: AccountManager, account: CalDavAccount, type: String) {
        val android = Account(displayNameFor(account), type)
        val userData = Bundle().apply { putString(UserDataAccountId, account.id) }

        val added = runCatching {
            manager.addAccountExplicitly(android, null, userData)
        }.getOrDefault(false)
        if (!added) return

        // Syncable, but not automatic: a projection pass is driven by a
        // snapshot change or an inbound edit, not by a timer we do not control.
        runCatching {
            ContentResolver.setIsSyncable(android, CalendarAuthority, 1)
            ContentResolver.setSyncAutomatically(android, CalendarAuthority, true)
        }
    }

    private fun remove(context: Context, account: Account) {
        runCatching {
            AccountManager.get(context).removeAccountExplicitly(account)
        }
    }

    /**
     * What the account is called in Settings.
     *
     * The username and host together, because a bare display name of "Home" is
     * unhelpful in a list that also holds a Google account, and because two
     * Calino accounts can share a display name while never sharing this.
     */
    private fun displayNameFor(account: CalDavAccount): String {
        val host = runCatching { java.net.URI(account.serverUrl).host }.getOrNull()
        return if (host.isNullOrEmpty()) {
            account.username.ifEmpty { account.displayName }
        } else {
            "${account.username}@$host"
        }
    }
}
