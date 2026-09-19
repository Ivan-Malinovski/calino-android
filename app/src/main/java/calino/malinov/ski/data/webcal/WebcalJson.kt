package calino.malinov.ski.data.webcal

import calino.malinov.ski.data.model.WebcalSubscription
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** JSON encoding for the persisted subscription list. Pure, so the round trip is unit-testable. */
object WebcalJson {
    fun encode(subscriptions: List<WebcalSubscription>): String {
        val array = JSONArray()
        subscriptions.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("calendarId", item.calendarId)
                    .put("name", item.name)
                    .put("color", item.color)
                    .put("url", item.url)
                    .put("refreshIntervalMinutes", item.refreshIntervalMinutes)
                    .put("notifyReminders", item.notifyReminders)
                    .put("visible", item.visible)
                    .put("lastFetchedAt", item.lastFetchedAt?.toString() ?: JSONObject.NULL),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<WebcalSubscription> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                val id = obj.getString("id")
                add(
                    WebcalSubscription(
                        id = id,
                        calendarId = obj.optString("calendarId").ifBlank { WebcalSubscription.calendarIdFor(id) },
                        name = obj.getString("name"),
                        color = obj.getLong("color"),
                        url = obj.getString("url"),
                        refreshIntervalMinutes = obj.optInt(
                            "refreshIntervalMinutes",
                            WebcalSubscription.DefaultRefreshMinutes,
                        ),
                        notifyReminders = obj.optBoolean("notifyReminders", false),
                        visible = obj.optBoolean("visible", true),
                        lastFetchedAt = obj.optString("lastFetchedAt", "")
                            .takeIf { it.isNotBlank() }
                            ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                    ),
                )
            }
        }
    }
}
