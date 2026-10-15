package calino.malinov.ski.data.jmap

import org.json.JSONArray
import org.json.JSONObject

/** Spread into arguments: `#ids` pointing at an earlier call's result. */
fun resultReference(argument: String, resultOf: String, name: String, path: String): Pair<String, JSONObject> =
    "#$argument" to jsonObject("resultOf" to resultOf, "name" to name, "path" to path)

private fun invalidReference(message: String) =
    JmapException(calino.malinov.ski.data.caldav.CalDavErrorCode.Rejected, message, status = 400, type = "invalidResultReference")

/** JSON Pointer (RFC 6901) with the JMAP `*` wildcard extension. */
internal fun pointer(value: Any?, path: String): Any? {
    if (path.isEmpty()) return value
    if (!path.startsWith("/")) throw invalidReference("Invalid result-reference path")
    val parts = path.substring(1).split("/").map { it.replace("~1", "/").replace("~0", "~") }
    fun visit(current: Any?, index: Int): Any? {
        if (index == parts.size) return current
        val key = parts[index]
        if (key == "*") {
            val array = current as? JSONArray ?: throw invalidReference("Result-reference wildcard requires an array")
            val out = JSONArray()
            for (item in 0 until array.length()) {
                val next = visit(array.opt(item), index + 1)
                if (next is JSONArray) for (i in 0 until next.length()) out.put(next.opt(i)) else out.put(next)
            }
            return out
        }
        val next: Any? = when (current) {
            is JSONArray -> if (Regex("0|[1-9]\\d*").matches(key)) current.opt(key.toInt()) else null
            is JSONObject -> if (current.has(key)) current.opt(key) else null
            else -> null
        }
        if (next == null) throw invalidReference("Result-reference path not found")
        return visit(next, index + 1)
    }
    return visit(value, 0)
}

/**
 * Resolves references whose producer already ran; those still pending in the
 * current batch stay on the wire for the server to resolve.
 */
internal fun resolveReferences(arguments: JSONObject, previous: List<JmapInvocation>): JSONObject {
    val resolved = arguments.copy()
    for (key in arguments.keyList()) {
        if (!key.startsWith("#")) continue
        val ref = arguments.objectOrNull(key)
        val resultOf = ref?.stringOrNull("resultOf")
        val name = ref?.stringOrNull("name")
        val path = ref?.stringOrNull("path")
        if (resultOf == null || name == null || path == null) throw invalidReference("Invalid JMAP result reference")
        val response = previous.firstOrNull { it.callId == resultOf && it.name == name } ?: continue
        val plain = key.substring(1)
        if (arguments.has(plain)) {
            throw JmapException(
                calino.malinov.ski.data.caldav.CalDavErrorCode.Rejected,
                "Duplicate result-reference argument", status = 400, type = "invalidArguments",
            )
        }
        resolved.put(plain, pointer(response.arguments, path) ?: JSONObject.NULL)
        resolved.remove(key)
    }
    return resolved
}

internal fun positiveLimit(value: Any?): Int =
    (value as? Number)?.toLong()?.takeIf { it > 0 && it < Int.MAX_VALUE }?.toInt() ?: Int.MAX_VALUE

/** Splits an oversized `/get` or `/set` into server-sized parts. */
internal fun splitArguments(name: String, arguments: JSONObject, getLimit: Int, setLimit: Int): List<JSONObject> {
    val ids = arguments.arrayOrNull("ids")
    if (name.endsWith("/get") && ids != null && ids.length() > getLimit) {
        return (0 until ids.length() step getLimit).map { start ->
            arguments.copy().also { part ->
                part.put("ids", JSONArray().also { slice ->
                    for (index in start until minOf(start + getLimit, ids.length())) slice.put(ids.opt(index))
                })
            }
        }
    }
    if (!name.endsWith("/set")) return listOf(arguments)
    val operations = mutableListOf<Triple<String, String, Any?>>()
    for (field in listOf("create", "update")) {
        arguments.objectOrNull(field)?.let { map -> map.keyList().forEach { operations += Triple(field, it, map.opt(it)) } }
    }
    arguments.arrayOrNull("destroy")?.let { destroy ->
        for (index in 0 until destroy.length()) {
            val id = destroy.opt(index) as? String ?: throw JmapException(
                calino.malinov.ski.data.caldav.CalDavErrorCode.Rejected,
                "Invalid destroy id", status = 400, type = "invalidArguments",
            )
            operations += Triple("destroy", id, id)
        }
    }
    if (operations.size <= setLimit) return listOf(arguments)
    if (arguments.keyList().any { it.startsWith("onSuccess") }) {
        throw JmapException(
            calino.malinov.ski.data.caldav.CalDavErrorCode.Rejected,
            "Cannot split /set with onSuccess arguments safely", status = 413, type = "limit",
        )
    }
    return operations.chunked(setLimit).map { chunk ->
        val part = arguments.copy().also { it.remove("create"); it.remove("update"); it.remove("destroy") }
        for ((field, id, value) in chunk) {
            if (field == "destroy") {
                val list = part.arrayOrNull("destroy") ?: JSONArray().also { part.put("destroy", it) }
                list.put(id)
            } else {
                val map = part.objectOrNull(field) ?: JSONObject().also { part.put(field, it) }
                map.put(id, value ?: JSONObject.NULL)
            }
        }
        part
    }
}

private val SetResultMaps = setOf("created", "updated", "destroyed", "notCreated", "notUpdated", "notDestroyed")

/** Recombines the parts of a split call into the single logical response the caller asked for. */
internal fun mergeResponses(name: String, arguments: JSONObject, parts: List<JSONObject>): JSONObject {
    val merged = parts.first().copy()
    for (part in parts.drop(1)) {
        if (name.endsWith("/get") && part.opt("state") != merged.opt("state")) {
            throw JmapException(
                calino.malinov.ski.data.caldav.CalDavErrorCode.PreconditionFailed,
                "JMAP state changed while splitting /get; retry the read", status = 412, type = "stateMismatch",
            )
        }
        for (key in part.keyList()) {
            val value = part.opt(key)
            if (isNull(value) && key in SetResultMaps) continue
            val existing = merged.opt(key)
            when {
                value is JSONArray && existing is JSONArray ->
                    for (index in 0 until value.length()) existing.put(value.opt(index))
                value is JSONObject && existing is JSONObject ->
                    for (inner in value.keyList()) existing.put(inner, value.opt(inner))
                key != "oldState" -> merged.put(key, value)
            }
        }
    }
    arguments.stringOrNull("accountId")?.takeIf { name.endsWith("/set") }?.let { merged.put("accountId", it) }
    return merged
}
