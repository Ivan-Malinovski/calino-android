package calino.malinov.ski.platform

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import calino.malinov.ski.MainActivity

/**
 * The account authenticator Android requires before an app may own accounts of
 * its own type, and therefore before it may own calendars in
 * `CalendarContract`.
 *
 * It is deliberately hollow. Calino does not use auth tokens: a CalDAV account
 * authenticates per request with a password kept in `KeystoreCredentialStore`,
 * encrypted under an Android Keystore key. Handing that password to
 * [AccountManager] would create a second copy of a secret for no gain, so the
 * token methods here answer "not supported" rather than inventing a token
 * nothing consumes.
 *
 * The one method that does something real is [addAccount], and what it does is
 * refuse to be a second front door: it sends the person into Calino's existing
 * add-account flow, so there is one place that validates a server URL,
 * discovers collections, and stores a credential.
 */
class CalinoAuthenticator(private val context: Context) : AbstractAccountAuthenticator(context) {

    /**
     * Android asking us to present an add-account UI, from Settings.
     *
     * Returns an intent into [MainActivity] rather than a result, which is how
     * the framework is told "ask the user". The account itself is created by
     * `CalinoAccounts.sync` once the CalDAV flow actually succeeds; an account
     * row created here would stand for a connection that may never exist.
     */
    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?,
    ): Bundle {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ActionAddAccount
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        }
        return Bundle().apply { putParcelable(AccountManager.KEY_INTENT, intent) }
    }

    /**
     * Nothing to confirm: possession of the account row is not what grants
     * access, the stored credential is.
     */
    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle?,
    ): Bundle = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true) }

    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String,
    ): Bundle = unsupported("Calino accounts have no editable properties")

    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle?,
    ): Bundle = unsupported("Calino does not issue auth tokens")

    override fun getAuthTokenLabel(authTokenType: String): String? = null

    override fun updateCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle = unsupported("Change the password in Calino's account list")

    /** Only the calendar authority, and only because we project into it. */
    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<out String>,
    ): Bundle = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, features.isEmpty()) }

    private fun unsupported(message: String): Bundle = Bundle().apply {
        putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
        putString(AccountManager.KEY_ERROR_MESSAGE, message)
    }
}

/**
 * The bound service Android resolves the authenticator through. It holds no
 * state; the framework may bind it in a process with no Activity.
 */
class CalinoAuthenticatorService : Service() {
    private val authenticator by lazy { CalinoAuthenticator(this) }

    override fun onBind(intent: Intent?): IBinder? = authenticator.iBinder
}
