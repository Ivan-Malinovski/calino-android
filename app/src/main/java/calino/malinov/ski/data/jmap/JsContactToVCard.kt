package calino.malinov.ski.data.jmap

import org.json.JSONObject

/** RFC 9555 JSContact to vCard, 4.0 by default, folded at 75 octets. */
internal object JsContactToVCard {
    private val NameKinds = listOf("surname", "given", "given2", "title", "generation")
    private val AddressKinds = listOf("postOfficeBox", "extension", "street", "locality", "region", "postcode", "country")

    private fun components(value: JSONObject, kinds: List<String>): List<String> {
        val list = value.optJSONArray("components")?.toList()?.filterIsInstance<JSONObject>().orEmpty()
        return kinds.map { kind ->
            list.filter { it.opt("kind") == kind }.joinToString(",") { vCardEscape(textOf(it.opt("value"))) }
        }
    }

    private fun date(value: JSONObject): String {
        if (value.opt("@type") == "Timestamp") return textOf(value.opt("utc")).replace("-", "").replace(":", "")
        val month = (value.opt("month") as? Number)?.toInt()
        val day = (value.opt("day") as? Number)?.toInt()
        if (month == null || day == null) return ""
        val year = (value.opt("year") as? Number)?.toInt()?.let { "%04d".format(it) } ?: "--"
        return "$year%02d%02d".format(month, day)
    }

    fun convert(card: JSONObject, version: String = "4.0"): String {
        val name = card.optJSONObject("name") ?: JSONObject()
        val nameComponents = name.optJSONArray("components")?.toList()?.filterIsInstance<JSONObject>().orEmpty()
        val derivedName = listOf("given", "given2", "surname")
            .flatMap { kind -> nameComponents.filter { it.opt("kind") == kind }.map { textOf(it.opt("value")) } }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:$version")
        fun add(key: String, value: String, params: String = "") {
            if (value.isNotEmpty()) lines += "$key$params:$value"
        }
        add("UID", vCardEscape(textOf(card.opt("uid")).ifEmpty { textOf(card.opt("id")) }))
        lines += "FN:" + vCardEscape(
            textOf(name.opt("full")).ifEmpty { derivedName }
                .ifEmpty { objectValues(card.opt("organizations")).map { textOf(it.opt("name")) }.firstOrNull { it.isNotEmpty() }.orEmpty() }
                .ifEmpty { "Unnamed" },
        )
        lines += "N:" + components(name, NameKinds).joinToString(";")
        add("KIND", textOf(card.opt("kind")))
        add("NICKNAME", objectValues(card.opt("nicknames")).joinToString(",") { vCardEscape(textOf(it.opt("name"))) })
        for (organization in objectValues(card.opt("organizations"))) {
            val units = organization.optJSONArray("units")?.toList()?.map { vCardEscape(textOf((it as? JSONObject)?.opt("name"))) }.orEmpty()
            add("ORG", (listOf(vCardEscape(textOf(organization.opt("name")))) + units).joinToString(";"))
        }
        for (title in objectValues(card.opt("titles"))) {
            add(if (title.opt("kind") == "role") "ROLE" else "TITLE", vCardEscape(textOf(title.opt("name"))))
        }
        for (email in objectValues(card.opt("emails"))) {
            add("EMAIL", vCardEscape(textOf(email.opt("address"))), contactParameters(email, version))
        }
        for (phone in objectValues(card.opt("phones"))) {
            val features = (phone.optJSONObject("features") ?: JSONObject()).entryList()
                .filter { it.second == true }
                .map { if (it.first == "mobile") "cell" else it.first }
                .toMutableList()
            add(
                "TEL",
                vCardEscape(textOf(phone.opt("number")).ifEmpty { textOf(phone.opt("uri")) }),
                contactParameters(phone, version, features),
            )
        }
        for (address in objectValues(card.opt("addresses"))) {
            val parts = components(address, AddressKinds).toMutableList()
            val full = textOf(address.opt("full"))
            // Calino has no LABEL field: keep a full-only address visible as street text.
            if (parts.none { it.isNotEmpty() } && full.isNotEmpty()) parts[2] = vCardEscape(full)
            val label = full.replace("^", "^^").replace(Regex("\r?\n"), "^n").replace("\"", "^'")
            add("ADR", parts.joinToString(";"), contactParameters(address, version) + if (label.isNotEmpty()) ";LABEL=\"$label\"" else "")
        }
        for (link in objectValues(card.opt("links"))) add("URL", vCardEscape(textOf(link.opt("uri"))), contactParameters(link, version))
        for (service in objectValues(card.opt("onlineServices"))) {
            add(
                "IMPP",
                vCardEscape(textOf(service.opt("uri")).ifEmpty { textOf(service.opt("user")) }),
                contactParameters(service, version),
            )
        }
        for (note in objectValues(card.opt("notes"))) {
            add("NOTE", vCardEscape(textOf(note.opt("note")).ifEmpty { textOf(note.opt("text")) }))
        }
        for (anniversary in objectValues(card.opt("anniversaries"))) {
            val kind = anniversary.opt("kind")
            if (kind == "birth" || kind == "wedding") {
                add(if (kind == "birth") "BDAY" else "ANNIVERSARY", date(anniversary.optJSONObject("date") ?: JSONObject()))
            }
        }
        add(
            "CATEGORIES",
            (card.optJSONObject("keywords") ?: JSONObject()).entryList().filter { it.second == true }
                .joinToString(",") { vCardEscape(it.first) },
        )
        for ((uid, member) in (card.optJSONObject("members") ?: JSONObject()).entryList()) {
            if (member == true) add("MEMBER", vCardEscape(uid))
        }
        for ((uid, relation) in (card.optJSONObject("relatedTo") ?: JSONObject()).entryList()) {
            val types = ((relation as? JSONObject)?.optJSONObject("relation") ?: JSONObject()).entryList()
                .filter { it.second == true }.map { it.first }.toMutableList()
            add("RELATED", vCardEscape(uid), contactParameters(JSONObject(), version, types))
        }
        for ((language, prefs) in (card.optJSONObject("preferredLanguages") ?: JSONObject()).entryList()) {
            if (prefs is org.json.JSONArray) {
                prefs.toList().filterIsInstance<JSONObject>().forEach {
                    add("LANG", vCardEscape(language), contactParameters(it, version))
                }
            } else if (prefs is JSONObject) {
                add("LANG", vCardEscape(textOf(prefs.opt("language")).ifEmpty { language }), contactParameters(prefs, version))
            }
        }
        for (media in objectValues(card.opt("media"))) {
            if (media.opt("kind") != "photo") continue
            val uri = textOf(media.opt("uri"))
            if (version == "3.0" && Regex("^data:[^;,]+;base64,", RegexOption.IGNORE_CASE).containsMatchIn(uri)) {
                val type = uri.substring(5, uri.indexOf(';')).removePrefix("image/").uppercase()
                add("PHOTO", uri.substring(uri.indexOf(',') + 1), ";ENCODING=b;TYPE=$type")
            } else {
                add("PHOTO", uri, ";VALUE=URI")
            }
        }
        lines += "END:VCARD"
        return lines.joinToString("\r\n") { foldVCardLine(it) } + "\r\n"
    }
}
