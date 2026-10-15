package calino.malinov.ski.data.jmap

import org.json.JSONArray
import org.json.JSONObject

/**
 * Small helpers over `org.json`, which models JSON null as the `JSONObject.NULL`
 * sentinel and exposes absent keys as `null`. JMAP treats the two differently
 * (a patch value of null deletes), so every read here says which it means.
 */
internal fun isNull(value: Any?): Boolean = value == null || value == JSONObject.NULL

internal fun JSONObject.objectOrNull(key: String): JSONObject? = opt(key) as? JSONObject

internal fun JSONObject.arrayOrNull(key: String): JSONArray? = opt(key) as? JSONArray

internal fun JSONObject.stringOrNull(key: String): String? = opt(key) as? String

internal fun JSONObject.boolOrNull(key: String): Boolean? = opt(key) as? Boolean

internal fun JSONObject.longOrNull(key: String): Long? = (opt(key) as? Number)?.let { number ->
    val double = number.toDouble()
    if (double == Math.floor(double) && !double.isInfinite()) number.toLong() else null
}

internal fun JSONArray.toList(): List<Any?> = List(length()) { opt(it) }

internal fun JSONObject.entryList(): List<Pair<String, Any?>> = keys().asSequence().map { it to opt(it) }.toList()

internal fun JSONObject.keyList(): List<String> = keys().asSequence().toList()

/** A deep copy that cannot alias the original. */
internal fun deepCopy(value: Any?): Any? = when (value) {
    is JSONObject -> JSONObject().also { copy ->
        value.keys().forEach { key -> copy.put(key, deepCopy(value.opt(key)) ?: JSONObject.NULL) }
    }
    is JSONArray -> JSONArray().also { copy ->
        for (index in 0 until value.length()) copy.put(deepCopy(value.opt(index)) ?: JSONObject.NULL)
    }
    else -> value
}

internal fun JSONObject.copy(): JSONObject = deepCopy(this) as JSONObject

/** Builds an object from pairs, keeping Kotlin nulls as JSON null. */
internal fun jsonObject(vararg pairs: Pair<String, Any?>): JSONObject =
    JSONObject().also { json -> pairs.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) } }

internal fun jsonArray(values: Iterable<Any?>): JSONArray =
    JSONArray().also { array -> values.forEach { array.put(it ?: JSONObject.NULL) } }

/**
 * Structural equality that treats `1` and `1.0` as the same number and ignores
 * object key order, which JSON does not define.
 */
internal fun equalJson(left: Any?, right: Any?): Boolean {
    if (isNull(left) && isNull(right)) return left == null == (right == null)
    if (left == null || right == null) return false
    return when {
        left is JSONObject && right is JSONObject ->
            left.length() == right.length() &&
                left.keys().asSequence().all { key -> right.has(key) && equalJson(left.opt(key), right.opt(key)) }
        left is JSONArray && right is JSONArray ->
            left.length() == right.length() && (0 until left.length()).all { equalJson(left.opt(it), right.opt(it)) }
        left is Number && right is Number -> left.toDouble() == right.toDouble()
        else -> left == right
    }
}

/**
 * Deterministic serialization used as the content hash input for ETags:
 * object keys sorted, array order kept, scalars written as `JSON.stringify`
 * would. Never use `JSONObject.toString()` for this -- key order is
 * unspecified and Android escapes `/`.
 */
internal fun canonicalJson(value: Any?): String = StringBuilder().also { appendCanonical(it, value) }.toString()

private fun appendCanonical(out: StringBuilder, value: Any?) {
    when {
        isNull(value) -> out.append("null")
        value is JSONObject -> {
            out.append('{')
            value.keyList().sorted().forEachIndexed { index, key ->
                if (index > 0) out.append(',')
                appendQuoted(out, key)
                out.append(':')
                appendCanonical(out, value.opt(key))
            }
            out.append('}')
        }
        value is JSONArray -> {
            out.append('[')
            for (index in 0 until value.length()) {
                if (index > 0) out.append(',')
                appendCanonical(out, value.opt(index))
            }
            out.append(']')
        }
        value is String -> appendQuoted(out, value)
        value is Boolean -> out.append(value.toString())
        value is Number -> out.append(numberText(value))
        else -> appendQuoted(out, value.toString())
    }
}

private fun numberText(number: Number): String {
    val double = number.toDouble()
    return if (double == Math.floor(double) && Math.abs(double) < 1e15) {
        number.toLong().toString()
    } else {
        number.toString()
    }
}

private fun appendQuoted(out: StringBuilder, text: String) {
    out.append('"')
    for (char in text) {
        when (char) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (char < ' ') out.append("\\u%04x".format(char.code)) else out.append(char)
        }
    }
    out.append('"')
}
