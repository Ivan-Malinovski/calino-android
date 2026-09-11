package calino.malinov.ski.poc.data.caldav

import android.content.Context
import calino.malinov.ski.poc.data.model.CalDavAccount
import calino.malinov.ski.poc.data.model.CalDavCalendar
import calino.malinov.ski.poc.data.model.ContactAddressBook
import calino.malinov.ski.poc.data.repository.CalDavAccountPersistence
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON encoding for the persisted account list.
 *
 * Pure functions, so the round trip is unit-testable. Nothing secret passes
 * through here by construction: [CalDavAccount] has no password field.
 */
object CalDavAccountJson {

    fun encode(accounts: List<CalDavAccount>): String {
        val array = JSONArray()
        accounts.forEach { account ->
            val calendars = JSONArray()
            account.calendars.forEach { calendar ->
                calendars.put(
                    JSONObject()
                        .put("id", calendar.id)
                        .put("name", calendar.name)
                        .put("color", calendar.color)
                        .put("enabled", calendar.enabled)
                        .put("readOnly", calendar.readOnly)
                        .put("visible", calendar.visible)
                        .put("showTasksInViews", calendar.showTasksInViews)
                        .put("ctag", calendar.ctag ?: JSONObject.NULL)
                        .put("syncToken", calendar.syncToken ?: JSONObject.NULL),
                )
            }
            val addressBooks = JSONArray()
            account.addressBooks.forEach { book ->
                addressBooks.put(
                    JSONObject()
                        .put("id", book.id)
                        .put("accountId", book.accountId)
                        .put("url", book.url)
                        .put("name", book.name)
                        .put("description", book.description ?: JSONObject.NULL)
                        .put("ctag", book.ctag ?: JSONObject.NULL)
                        .put("syncToken", book.syncToken ?: JSONObject.NULL)
                        .put("enabled", book.enabled)
                        .put("readOnly", book.readOnly),
                )
            }
            array.put(
                JSONObject()
                    .put("id", account.id)
                    .put("displayName", account.displayName)
                    .put("serverUrl", account.serverUrl)
                    .put("username", account.username)
                    .put("calendars", calendars)
                    .put("addressBooks", addressBooks),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<CalDavAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        // A malformed or older payload yields no accounts rather than a crash
        // on launch; the user reconnects, which is recoverable.
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::decodeAccount)
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeAccount(json: JSONObject): CalDavAccount? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val calendarsJson = json.optJSONArray("calendars") ?: JSONArray()
        val calendars = (0 until calendarsJson.length()).mapNotNull { index ->
            calendarsJson.optJSONObject(index)?.let { calendar ->
                val calendarId = calendar.optString("id").takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                CalDavCalendar(
                    id = calendarId,
                    name = calendar.optString("name", calendarId),
                    color = calendar.optLong("color", DefaultCalendarColor),
                    enabled = calendar.optBoolean("enabled", true),
                    readOnly = calendar.optBoolean("readOnly", false),
                    visible = calendar.optBoolean("visible", true),
                    showTasksInViews = calendar.optBoolean("showTasksInViews", true),
                    ctag = calendar.optString("ctag").takeUnless { it.isBlank() || it == "null" },
                    syncToken = calendar.optString("syncToken")
                        .takeUnless { it.isBlank() || it == "null" },
                )
            }
        }
        val booksJson = json.optJSONArray("addressBooks") ?: JSONArray()
        val addressBooks = (0 until booksJson.length()).mapNotNull { index ->
            booksJson.optJSONObject(index)?.let { book ->
                val bookId = book.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                ContactAddressBook(
                    id = bookId,
                    accountId = book.optString("accountId", id),
                    url = book.optString("url", bookId),
                    name = book.optString("name", bookId),
                    description = book.optString("description").takeIf { it.isNotEmpty() },
                    ctag = book.optString("ctag").takeIf { it.isNotEmpty() },
                    syncToken = book.optString("syncToken").takeIf { it.isNotEmpty() },
                    enabled = book.optBoolean("enabled", true),
                    readOnly = book.optBoolean("readOnly", false),
                )
            }
        }
        return CalDavAccount(
            id = id,
            displayName = json.optString("displayName", id),
            serverUrl = json.optString("serverUrl"),
            username = json.optString("username"),
            calendars = calendars,
            addressBooks = addressBooks,
        )
    }
}

/** Stores the account list in private SharedPreferences as JSON. */
class SharedPreferencesAccountPersistence(context: Context) : CalDavAccountPersistence {

    private val prefs = context.applicationContext
        .getSharedPreferences("calino_caldav_accounts", Context.MODE_PRIVATE)

    override fun load(): List<CalDavAccount> =
        CalDavAccountJson.decode(prefs.getString(Key, null))

    override fun save(accounts: List<CalDavAccount>) {
        prefs.edit().putString(Key, CalDavAccountJson.encode(accounts)).apply()
    }

    private companion object { const val Key = "accounts" }
}
