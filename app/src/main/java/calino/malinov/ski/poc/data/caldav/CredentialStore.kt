package calino.malinov.ski.poc.data.caldav

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where an account's password lives between launches.
 *
 * An interface so the repository can be exercised on the JVM, where the Android
 * Keystore does not exist.
 */
interface CredentialStore {
    fun save(accountId: String, credentials: DavCredentials)
    fun load(accountId: String): DavCredentials?
    fun clear(accountId: String)
}

/** In-memory, for tests and for the no-account case. */
class InMemoryCredentialStore : CredentialStore {
    private val entries = mutableMapOf<String, DavCredentials>()
    override fun save(accountId: String, credentials: DavCredentials) { entries[accountId] = credentials }
    override fun load(accountId: String): DavCredentials? = entries[accountId]
    override fun clear(accountId: String) { entries.remove(accountId) }
}

/**
 * Password storage encrypted with an Android Keystore key.
 *
 * The key is generated inside the Keystore and never leaves it, so the
 * ciphertext in SharedPreferences is useless on its own -- reading the prefs
 * file off a rooted device or a backup does not yield the password.
 *
 * The Keystore is used directly rather than through `androidx.security-crypto`,
 * which Google has deprecated.
 */
class KeystoreCredentialStore(context: Context) : CredentialStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(accountId: String, credentials: DavCredentials) {
        val encrypted = encrypt(credentials.password) ?: return
        prefs.edit()
            .putString(userKey(accountId), credentials.username)
            .putString(passwordKey(accountId), encrypted)
            .apply()
    }

    override fun load(accountId: String): DavCredentials? {
        val username = prefs.getString(userKey(accountId), null) ?: return null
        val stored = prefs.getString(passwordKey(accountId), null) ?: return null
        val password = decrypt(stored) ?: return null
        return DavCredentials(username, password)
    }

    override fun clear(accountId: String) {
        prefs.edit().remove(userKey(accountId)).remove(passwordKey(accountId)).apply()
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // The GCM IV is generated per encryption and prefixed to the payload;
        // reusing one across encryptions would break the cipher's guarantees.
        val iv = cipher.iv
        Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ciphertext)
    }.getOrNull()

    private fun decrypt(stored: String): String? = runCatching {
        val (ivPart, bodyPart) = stored.split(":", limit = 2).let { it[0] to it[1] }
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TagLengthBits, Base64.getDecoder().decode(ivPart)),
        )
        String(cipher.doFinal(Base64.getDecoder().decode(bodyPart)), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        generator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately not requiring user authentication: the app
                // refetches in the background, which a locked key would block.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun userKey(accountId: String) = "user:$accountId"
    private fun passwordKey(accountId: String) = "pass:$accountId"

    private companion object {
        const val PrefsName = "calino_caldav_credentials"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "calino_caldav_key"
        const val Transformation = "AES/GCM/NoPadding"
        const val TagLengthBits = 128
    }
}
