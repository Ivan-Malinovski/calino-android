package calino.malinov.ski.data.jmap

import org.json.JSONObject

/** Server-owned or derived properties, and membership, which the writer patches itself. */
private val ContactIgnored = setOf("id", "blobId", "size", "created", "updated", "addressBookIds")

private val MapFields = setOf(
    "nicknames", "organizations", "titles", "emails", "phones", "addresses",
    "links", "onlineServices", "notes", "anniversaries", "media",
)

/** What this client can represent of [card]: JSContact to vCard and back. */
internal fun contactRoundTrip(card: JSONObject): JSONObject =
    VCardToJsContact.convert(JsContactToVCard.convert(card))

internal fun diffJscontact(before: JSONObject, after: JSONObject): JSONObject = diffObjects(before, after, ContactIgnored)

/**
 * The PatchObject that applies the edit [next] to the server's [previous].
 * Only represented fields that changed are written; whatever the vCard model
 * cannot carry (and the person did not touch) survives untouched.
 */
internal fun contactPatch(
    previous: JSONObject,
    next: JSONObject,
    project: (JSONObject) -> JSONObject = ::contactRoundTrip,
): JSONObject {
    val projected = project(previous)
    val desired = previous.copy()
    val keys = LinkedHashSet<String>().apply { addAll(projected.keyList()); addAll(next.keyList()) }
    for (key in keys) {
        if (key in ContactIgnored) continue
        if (equalJson(projected.opt(key), next.opt(key))) continue
        if (!next.has(key)) {
            desired.remove(key)
            continue
        }
        var before = projected.opt(key)
        var after = next.opt(key)
        val server = previous.opt(key)
        if (key in MapFields && server is JSONObject && before is JSONObject && after is JSONObject) {
            val ids = HashMap<String, String>()
            val used = HashSet<String>()
            for ((id, value) in before.entryList()) {
                for (serverId in server.keyList()) {
                    if (serverId in used) continue
                    val isolated = project(previous.copy().put(key, jsonObject(serverId to server.opt(serverId)))).opt(key)
                    if (isolated is JSONObject && isolated.entryList().any { equalJson(it.second, value) }) {
                        ids[id] = serverId
                        used += serverId
                        break
                    }
                }
            }
            val aligned = JSONObject()
            val assigned = HashSet<String>()
            val nextIds = HashMap<String, String>()
            // Match unchanged values first, so removals and reordering do not rename them.
            for ((id, value) in after.entryList()) {
                val match = before.entryList().firstOrNull { (oldId, oldValue) ->
                    oldId in ids && ids.getValue(oldId) !in assigned && equalJson(value, oldValue)
                }
                if (match != null) {
                    nextIds[id] = ids.getValue(match.first)
                    assigned += ids.getValue(match.first)
                }
            }
            for ((id, value) in after.entryList()) {
                var target = nextIds[id] ?: ids[id]?.takeIf { it !in assigned }
                if (target == null) {
                    target = id
                    while (server.has(target!!) || target in assigned) target += "_"
                }
                aligned.put(target!!, value ?: JSONObject.NULL)
                assigned += target
            }
            val renamed = JSONObject()
            for ((id, value) in before.entryList()) renamed.put(ids[id] ?: id, value ?: JSONObject.NULL)
            before = renamed
            after = aligned
        }
        desired.put(key, reconcile(server, before, after) ?: JSONObject.NULL)
    }
    return diffJscontact(previous, desired)
}
