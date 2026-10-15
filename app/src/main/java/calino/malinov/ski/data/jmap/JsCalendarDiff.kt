package calino.malinov.ski.data.jmap

import org.json.JSONObject

/** Server-owned metadata stays untouched; calendar membership changes are writable. */
private val ServerOwned = setOf("id", "isOrigin", "isDraft", "updated", "created")

private fun escapePath(key: String): String = key.replace("~", "~0").replace("/", "~1")

/** Null in a PatchObject deletes. Replace the containing object to introduce a literal null. */
private fun needsReplacement(before: JSONObject, after: JSONObject): Boolean =
    after.keyList().any { key -> after.opt(key) == JSONObject.NULL && before.opt(key) != JSONObject.NULL }

internal fun diffJscalendar(before: JSONObject, after: JSONObject): JSONObject = diffObjects(before, after, ServerOwned)

/**
 * The PatchObject that turns [before] into [after] (RFC 8984 section 1.4.9).
 * Nested objects are diffed by path; arrays and `recurrenceRule` are replaced
 * whole. Also used for recurrence overrides, whose ignored properties differ.
 */
internal fun diffObjects(before: JSONObject, after: JSONObject, ignored: Set<String> = emptySet()): JSONObject {
    val patch = JSONObject()
    fun visit(old: JSONObject, next: JSONObject, prefix: String) {
        val keys = LinkedHashSet<String>().apply { addAll(old.keyList()); addAll(next.keyList()) }
        for (key in keys) {
            if (prefix.isEmpty() && key in ignored) continue
            val path = prefix + escapePath(key)
            if (!next.has(key)) {
                patch.put(path, JSONObject.NULL)
            } else if (!equalJson(old.opt(key), next.opt(key))) {
                val previous = old.opt(key)
                val value = next.opt(key)
                if (previous is JSONObject && value is JSONObject && key != "recurrenceRule" && !needsReplacement(previous, value)) {
                    visit(previous, value, "$path/")
                } else {
                    patch.put(path, deepCopy(value) ?: JSONObject.NULL)
                }
            }
        }
    }
    visit(before, after, "")
    return patch
}

// --- editing an event the server holds ------------------------------------------------

/** Applies [next] to [server] but only where the converter's [projected] view of the server changed. */
internal fun reconcile(server: Any?, projected: Any?, next: Any?): Any? {
    if (equalJson(projected, next) && server != null) return deepCopy(server)
    if (server !is JSONObject || projected !is JSONObject || next !is JSONObject) return deepCopy(next)
    val result = server.copy()
    val keys = LinkedHashSet<String>().apply { addAll(projected.keyList()); addAll(next.keyList()) }
    for (key in keys) {
        if (equalJson(projected.opt(key), next.opt(key))) continue
        if (!next.has(key)) {
            result.remove(key)
        } else {
            result.put(key, reconcile(server.opt(key), projected.opt(key), next.opt(key)) ?: JSONObject.NULL)
        }
    }
    return result
}

private fun addressOf(value: Any?): String? = (value as? JSONObject)?.let(JsCalendarToIcs::participantAddress)

/** Participant ids are synthetic in the converter's output; map them back to the server's. */
private fun alignParticipants(server: JSONObject, projected: JSONObject, next: JSONObject): Pair<JSONObject, JSONObject> {
    val ids = HashMap<String, String>()
    for ((id, participant) in server.entryList()) addressOf(participant)?.let { ids[it.lowercase()] = id }
    fun align(map: JSONObject): JSONObject = JSONObject().also { aligned ->
        for ((id, value) in map.entryList()) {
            val address = addressOf(value)
            aligned.put(address?.let { ids[it.lowercase()] } ?: id, value)
        }
    }
    return align(projected) to align(next)
}

/** Converter map ids are synthetic too (a0/l0/f0); map them back to server ids. */
private fun alignMap(event: JSONObject, field: String, projected: JSONObject, next: JSONObject): Pair<JSONObject, JSONObject> {
    val server = event.opt(field) as? JSONObject ?: return projected to next
    val ids = HashMap<String, String>()
    val used = HashSet<String>()
    for ((projectedId, value) in projected.entryList()) {
        for (id in server.keyList().sorted()) {
            if (id in used) continue
            val isolated = event.copy().put(field, jsonObject(id to server.opt(id)))
            if (field == "links") isolated.remove("virtualLocations")
            val normalized = roundTrip(isolated).opt(field) as? JSONObject
            if (normalized != null && normalized.entryList().any { equalJson(it.second, value) }) {
                ids[projectedId] = id
                used += id
                break
            }
        }
    }
    val oldMap = JSONObject()
    for ((id, value) in projected.entryList()) oldMap.put(ids[id] ?: id, value)
    val newMap = JSONObject()
    for ((id, value) in next.entryList()) {
        // Removing or reordering alarms or attachments shifts the converter's indices.
        val match = projected.entryList().firstOrNull { (oldId, oldValue) ->
            oldId in ids && !newMap.has(ids.getValue(oldId)) && equalJson(oldValue, value)
        }
        val serverId = (match?.let { ids[it.first] } ?: ids[id]) ?: id
        newMap.put(serverId, value ?: JSONObject.NULL)
    }
    return oldMap to newMap
}

/** Values the spec defaults when absent; the app writes them out explicitly, which is not an edit. */
private fun isDefault(key: String, projected: Any?, value: Any?): Boolean = projected == null && when (key) {
    "freeBusyStatus" -> value == "busy"
    "sequence" -> (value as? Number)?.toLong() == 0L
    else -> false
}

/** What this client can represent of [event]: JSCalendar to iCalendar and back. */
internal fun roundTrip(event: JSONObject): JSONObject = IcsToJsCalendar.convert(JsCalendarToIcs.convert(event)).event

/**
 * The PatchObject that applies the edit [next] to the server's [previous].
 * Represented data is diffed first, so server defaults and lossy mappings the
 * person did not touch are never rewritten merely by saving.
 */
internal fun eventPatch(previous: JSONObject, next: JSONObject): JSONObject {
    val projected = roundTrip(previous)
    val desired = previous.copy()
    val keys = LinkedHashSet<String>().apply { addAll(projected.keyList()); addAll(next.keyList()) }
    for (key in keys) {
        if (key in setOf("id", "created", "updated", "calendarIds")) continue
        if (equalJson(projected.opt(key), next.opt(key))) continue
        if (isDefault(key, projected.opt(key), next.opt(key))) continue
        val before = previous.opt(key)
        val represented = projected.opt(key)
        val after = next.opt(key)
        if (!next.has(key)) {
            desired.remove(key)
        } else if (key == "participants" && before is JSONObject && represented is JSONObject && after is JSONObject) {
            val (oldMap, newMap) = alignParticipants(before, represented, after)
            desired.put(key, reconcile(before, oldMap, newMap) ?: JSONObject.NULL)
        } else if (key in setOf("alerts", "locations", "links") && before is JSONObject && represented is JSONObject && after is JSONObject) {
            val (oldMap, newMap) = alignMap(previous, key, represented, after)
            // Several JSCalendar locations collapse to one LOCATION in the read
            // converter. An edit deliberately replaces that lossy representation.
            desired.put(
                key,
                if (key == "locations" && oldMap.keyList().any { !before.has(it) }) after
                else reconcile(before, oldMap, newMap) ?: JSONObject.NULL,
            )
        } else {
            desired.put(key, reconcile(before, represented, after) ?: JSONObject.NULL)
        }
    }
    next.opt("calendarIds")?.let { desired.put("calendarIds", it) }
    return diffJscalendar(previous, desired)
}

/** True when [patch] changes nothing but calendar membership. */
internal fun onlyMembership(patch: JSONObject): Boolean =
    patch.keyList().all { it == "calendarIds" || it.startsWith("calendarIds/") }
