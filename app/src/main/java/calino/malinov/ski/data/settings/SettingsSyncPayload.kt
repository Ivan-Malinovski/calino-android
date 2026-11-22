package calino.malinov.ski.data.settings

import calino.malinov.ski.data.model.CategoryStyleFont
import calino.malinov.ski.data.model.CategoryStyleRule
import calino.malinov.ski.data.model.CategoryStyleTreatment
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * The Calino Settings payload, as the web app defines it in its
 * `docs/CALINOSETTINGSSYNC.md`.
 *
 * The format is one JSON object carried inside the `ATTACH` of a single VEVENT
 * in a hidden `Calino Settings` calendar. It is deliberately a *shared* format:
 * the web app reads and writes the same bytes, so whatever this app does not
 * understand has to survive a round trip untouched. That is why the whole root
 * object is kept verbatim and only the keys this app owns are edited in place --
 * a phone with fewer settings than a browser must not delete the browser's, and
 * a phone must not drop the payload's top-level `categories` list either.
 *
 * Everything here is plain JVM code (org.json is a real library in the unit
 * suite), so the format is testable without an emulator.
 */
data class SettingsSyncPayload private constructor(private val root: JSONObject) {

    /** The payload's `settings` object; empty when the payload has none. */
    val settings: JSONObject get() = root.optJSONObject(KeySettings) ?: JSONObject()

    val version: Int get() = root.optInt(KeyVersion, CurrentVersion)

    val syncedAt: Instant
        get() = root.optString(KeySyncedAt).takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Instant.EPOCH

    /**
     * The category style rules the payload carries, or null when it does not
     * carry that key at all.
     *
     * Null and empty mean different things: a payload written before this app
     * synced styles has no key, and that must leave the device's own rules
     * alone rather than wiping them. An empty array is a deliberate "no rules".
     */
    fun categoryStyleRules(): List<CategoryStyleRule>? {
        val rules = settings.optJSONArray(KeyCategoryStyleRules) ?: return null
        return (0 until rules.length()).mapNotNull { index ->
            rules.optJSONObject(index)?.toCategoryStyleRule()
        }
    }

    /**
     * The same payload with this app's rules written into it.
     *
     * Everything else -- settings this app has no field for, the web app's
     * top-level `categories` and `autoCategoryRules`, and anything a future
     * version adds -- is carried over exactly as it was.
     */
    fun withCategoryStyleRules(rules: List<CategoryStyleRule>): SettingsSyncPayload {
        val next = JSONObject(root.toString())
        val settingsCopy = next.optJSONObject(KeySettings) ?: JSONObject().also { next.put(KeySettings, it) }
        settingsCopy.put(KeyCategoryStyleRules, JSONArray().apply { rules.forEach { put(it.toJson()) } })
        return SettingsSyncPayload(next)
    }

    /** The same payload stamped for a write, so readers can order the writes. */
    fun stamped(at: Instant): SettingsSyncPayload {
        val next = JSONObject(root.toString())
        next.put(KeyVersion, CurrentVersion)
        next.put(KeySyncedAt, at.toString())
        return SettingsSyncPayload(next)
    }

    fun toJson(): String = root.toString()

    companion object {
        const val KeyVersion = "version"
        const val KeySyncedAt = "syncedAt"
        const val KeySettings = "settings"
        const val KeyCategoryStyleRules = "categoryStyleRules"

        /** The format version this app writes. The web app's current version. */
        const val CurrentVersion = 1

        /**
         * A payload this app can edit: the JSON object the web app writes, with
         * a settings object. Anything else is treated as unreadable rather than
         * "empty", so a malformed remote cannot overwrite good local rules.
         */
        fun parse(json: String?): SettingsSyncPayload? {
            val root = runCatching { JSONObject(json ?: return null) }.getOrNull() ?: return null
            if (root.optJSONObject(KeySettings) == null) return null
            return SettingsSyncPayload(root)
        }

        /** A payload with no settings yet, for the first push from a device. */
        fun empty(now: Instant): SettingsSyncPayload = SettingsSyncPayload(
            JSONObject()
                .put(KeyVersion, CurrentVersion)
                .put(KeySyncedAt, now.toString())
                .put(KeySettings, JSONObject()),
        )
    }
}

/** One rule in the web app's shape: `{id, categories, treatment, font, color, enabled}`. */
private fun CategoryStyleRule.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("categories", categories)
    .put("treatment", treatment.key)
    .put("font", font.key)
    .put("color", hexColor(colorArgb))
    .put("enabled", enabled)

private fun JSONObject.toCategoryStyleRule(): CategoryStyleRule? {
    val treatment = CategoryStyleTreatment.fromKey(optString("treatment")) ?: return null
    val font = CategoryStyleFont.fromKey(optString("font")) ?: return null
    val color = parseHexColor(optString("color")) ?: return null
    val id = optString("id").takeIf { it.isNotBlank() } ?: return null
    return CategoryStyleRule(
        id = id,
        categories = optString("categories"),
        treatment = treatment,
        font = font,
        colorArgb = color,
        enabled = optBoolean("enabled", true),
    )
}

/** `#rrggbb` in, opaque ARGB out; the web writes the colour of an `<input type="color">`. */
internal fun parseHexColor(raw: String): Int? {
    val hex = raw.trim().removePrefix("#")
    if (hex.length != 6) return null
    val rgb = hex.toLongOrNull(16) ?: return null
    return (0xFF000000L or rgb).toInt()
}

internal fun hexColor(argb: Int): String = "#%06x".format(argb and 0xFFFFFF)
