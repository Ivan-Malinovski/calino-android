package calino.malinov.ski.data.update

import android.content.Context
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** A stable GitHub release that is newer than the installed Calino build. */
data class AppUpdate(
    val version: String,
    val title: String,
    val releaseUrl: String,
)

data class AppUpdateState(
    val available: AppUpdate? = null,
    val promptVisible: Boolean = false,
)

/**
 * Checks only Calino's fixed GitHub repository. Calendar data, account details,
 * and device identifiers are never included in the request.
 */
class AppUpdateChecker(
    context: Context,
    private val installedVersion: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val store = AppUpdateStore(context.applicationContext)

    fun cachedState(): AppUpdateState {
        val update = store.loadRelease()?.takeIf { isNewerVersion(it.version, installedVersion) }
        return AppUpdateState(
            available = update,
            promptVisible = update != null && store.dismissedVersion() != update.version,
        )
    }

    suspend fun check(force: Boolean = false): AppUpdateState = withContext(Dispatchers.IO) {
        val cached = cachedState()
        val lastCheck = store.lastCheck()
        if (!force && lastCheck != null && Duration.between(lastCheck, clock.instant()) < CheckInterval) {
            return@withContext cached
        }

        // Count an attempted check even if GitHub is unavailable. Update
        // discovery should stay quiet and must not turn every foreground into
        // a retry loop on an offline phone.
        store.saveLastCheck(clock.instant())
        val request = Request.Builder()
            .url(LatestReleaseApi)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Calino-Android/$installedVersion")
            .apply { store.etag()?.let { header("If-None-Match", it) } }
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> cached
                    !response.isSuccessful -> cached
                    else -> {
                        val release = parseGitHubRelease(response.body?.string().orEmpty())
                        store.saveRelease(release)
                        store.saveEtag(response.header("ETag"))
                        if (isNewerVersion(release.version, installedVersion)) {
                            AppUpdateState(
                                available = release,
                                promptVisible = store.dismissedVersion() != release.version,
                            )
                        } else {
                            AppUpdateState()
                        }
                    }
                }
            }
        }.getOrDefault(cached)
    }

    fun dismiss(update: AppUpdate): AppUpdateState {
        store.saveDismissedVersion(update.version)
        return AppUpdateState(available = update, promptVisible = false)
    }

    companion object {
        const val LatestReleaseApi =
            "https://api.github.com/repos/Ivan-Malinovski/calino-android/releases/latest"
        val CheckInterval: Duration = Duration.ofHours(24)
    }
}

internal fun parseGitHubRelease(json: String): AppUpdate {
    val root = JSONObject(json)
    check(!root.optBoolean("draft", false)) { "The latest release is a draft." }
    check(!root.optBoolean("prerelease", false)) { "The latest release is a prerelease." }
    val version = normalizeVersion(root.getString("tag_name"))
    val url = root.getString("html_url")
    check(isCalinoReleaseUrl(url)) { "Unexpected release URL." }
    val title = root.optString("name").trim().ifEmpty { "Calino $version" }
    return AppUpdate(version = version, title = title, releaseUrl = url)
}

internal fun normalizeVersion(value: String): String =
    value.trim().removePrefix("v").substringBefore('-')

internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    val left = semanticVersion(normalizeVersion(candidate)) ?: return false
    val right = semanticVersion(normalizeVersion(installed)) ?: return false
    return compareValuesBy(left, right, { it[0] }, { it[1] }, { it[2] }) > 0
}

private fun semanticVersion(value: String): List<Int>? {
    val parts = value.split('.')
    if (parts.size != 3) return null
    return parts.map { it.toIntOrNull() ?: return null }
}

internal fun isCalinoReleaseUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    uri.scheme == "https" &&
        uri.host.equals("github.com", ignoreCase = true) &&
        uri.path.startsWith("/Ivan-Malinovski/calino-android/releases/")
}.getOrDefault(false)

private class AppUpdateStore(context: Context) {
    private val prefs = context.getSharedPreferences("calino_app_updates", Context.MODE_PRIVATE)

    fun loadRelease(): AppUpdate? {
        val version = prefs.getString(ReleaseVersion, null) ?: return null
        val title = prefs.getString(ReleaseTitle, null) ?: return null
        val url = prefs.getString(ReleaseUrl, null)?.takeIf(::isCalinoReleaseUrl) ?: return null
        return AppUpdate(version, title, url)
    }

    fun saveRelease(release: AppUpdate) {
        prefs.edit()
            .putString(ReleaseVersion, release.version)
            .putString(ReleaseTitle, release.title)
            .putString(ReleaseUrl, release.releaseUrl)
            .apply()
    }

    fun dismissedVersion(): String? = prefs.getString(DismissedVersion, null)
    fun saveDismissedVersion(version: String) = prefs.edit().putString(DismissedVersion, version).apply()
    fun etag(): String? = prefs.getString(Etag, null)
    fun saveEtag(value: String?) = prefs.edit().putString(Etag, value).apply()
    fun lastCheck(): Instant? = prefs.getLong(LastCheck, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
    fun saveLastCheck(value: Instant) = prefs.edit().putLong(LastCheck, value.toEpochMilli()).apply()

    private companion object {
        const val ReleaseVersion = "release_version"
        const val ReleaseTitle = "release_title"
        const val ReleaseUrl = "release_url"
        const val DismissedVersion = "dismissed_version"
        const val Etag = "etag"
        const val LastCheck = "last_check"
    }
}
