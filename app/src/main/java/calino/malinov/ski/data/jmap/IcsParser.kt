package calino.malinov.ski.data.jmap

/** One content line: an upper-cased name, its parameters (RFC 6868 decoded) and the raw value. */
internal class IcsProperty(val name: String, val parameters: Map<String, List<String>>, val value: String) {
    fun parameter(name: String): String? = parameters[name.uppercase()]?.firstOrNull()

    fun parameterValues(name: String): List<String> = parameters[name.uppercase()].orEmpty()
}

internal class IcsComponent(val name: String) {
    val properties = mutableListOf<IcsProperty>()
    val children = mutableListOf<IcsComponent>()

    fun all(name: String): List<IcsProperty> = properties.filter { it.name == name.uppercase() }

    fun first(name: String): IcsProperty? = properties.firstOrNull { it.name == name.uppercase() }

    fun has(name: String): Boolean = first(name) != null

    fun components(name: String): List<IcsComponent> = children.filter { it.name == name.uppercase() }

    /** The first value of [name] as TEXT, unescaped; absent when the property is missing. */
    fun text(name: String): String? = first(name)?.let { icsUnescape(it.value) }
}

/** Parse failures are reported like any other unconvertible event. */
internal object IcsParser {
    fun parse(ics: String): IcsComponent {
        val root = IcsComponent("ROOT")
        val stack = ArrayDeque<IcsComponent>()
        stack.addLast(root)
        for (line in unfold(ics)) {
            if (line.isEmpty()) continue
            val property = parseLine(line)
            when (property.name) {
                "BEGIN" -> {
                    val component = IcsComponent(property.value.trim().uppercase())
                    stack.last().children += component
                    stack.addLast(component)
                }
                "END" -> {
                    if (stack.size <= 1 || stack.last().name != property.value.trim().uppercase()) {
                        throw JsCalendarException("Unbalanced iCalendar END:${property.value}")
                    }
                    stack.removeLast()
                }
                else -> stack.last().properties += property
            }
        }
        if (stack.size != 1) throw JsCalendarException("Unterminated iCalendar component ${stack.last().name}")
        return root.children.singleOrNull() ?: throw JsCalendarException("Expected a single VCALENDAR")
    }

    private fun unfold(text: String): List<String> {
        val lines = mutableListOf<StringBuilder>()
        for (raw in text.removePrefix("\uFEFF").split("\r\n", "\n", "\r")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines.last().append(raw, 1, raw.length)
            } else {
                lines += StringBuilder(raw)
            }
        }
        return lines.map { it.toString() }
    }

    private fun parseLine(line: String): IcsProperty {
        var index = 0
        while (index < line.length && line[index] != ':' && line[index] != ';') index++
        if (index >= line.length) throw JsCalendarException("Invalid iCalendar line: $line")
        val name = line.substring(0, index).trim().uppercase()
        val parameters = linkedMapOf<String, MutableList<String>>()
        while (index < line.length && line[index] == ';') {
            index++
            val start = index
            while (index < line.length && line[index] != '=' && line[index] != ':' && line[index] != ';') index++
            val key = line.substring(start, index).trim().uppercase()
            val values = mutableListOf<String>()
            if (index < line.length && line[index] == '=') {
                index++
                while (true) {
                    val builder = StringBuilder()
                    if (index < line.length && line[index] == '"') {
                        index++
                        while (index < line.length && line[index] != '"') builder.append(line[index++])
                        if (index < line.length) index++
                    } else {
                        while (index < line.length && line[index] !in ",:;") builder.append(line[index++])
                    }
                    values += decodeParameter(builder.toString())
                    if (index < line.length && line[index] == ',') index++ else break
                }
            }
            if (key.isNotEmpty()) parameters.getOrPut(key) { mutableListOf() } += values
        }
        if (index >= line.length || line[index] != ':') throw JsCalendarException("Invalid iCalendar line: $line")
        return IcsProperty(name, parameters, line.substring(index + 1))
    }

    /** RFC 6868: `^n`, `^'` and `^^`. */
    private fun decodeParameter(value: String): String {
        if ('^' !in value) return value
        val out = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '^' && index + 1 < value.length) {
                when (value[index + 1]) {
                    'n', 'N' -> { out.append('\n'); index += 2; continue }
                    '\'' -> { out.append('"'); index += 2; continue }
                    '^' -> { out.append('^'); index += 2; continue }
                }
            }
            out.append(char)
            index++
        }
        return out.toString()
    }
}

/** RFC 5545 TEXT unescaping. */
internal fun icsUnescape(value: String): String {
    if ('\\' !in value) return value
    val out = StringBuilder()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '\\' && index + 1 < value.length) {
            when (value[index + 1]) {
                'n', 'N' -> out.append('\n')
                else -> out.append(value[index + 1])
            }
            index += 2
        } else {
            out.append(char)
            index++
        }
    }
    return out.toString()
}

/** The unescaped elements of a comma-separated TEXT list such as CATEGORIES. */
internal fun icsTextList(value: String): List<String> {
    val items = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char == '\\' && index + 1 < value.length -> {
                current.append(char).append(value[index + 1])
                index += 2
                continue
            }
            char == ',' -> {
                items += icsUnescape(current.toString())
                current.setLength(0)
            }
            else -> current.append(char)
        }
        index++
    }
    items += icsUnescape(current.toString())
    return items.map { it.trim() }.filter { it.isNotEmpty() }
}
