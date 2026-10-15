package calino.malinov.ski.data.jmap

import org.json.JSONObject

/** RFC 9555 vCard to RFC 9553 JSContact. Vendor properties are not carried; no uid is invented. */
internal object VCardToJsContact {
    private val NameKinds = listOf("surname", "given", "given2", "title", "generation")
    private val AddressKinds = listOf("postOfficeBox", "extension", "street", "locality", "region", "postcode", "country")
    private val PhoneFeatures = listOf("voice", "fax", "cell", "video", "pager", "text", "textphone")
    private val FullDate = Regex("^(?:(\\d{4})-?)?(\\d{2})-?(\\d{2})$")
    private val PartialYearless = Regex("^--(\\d{2})-?(\\d{2})$")

    private fun date(value: String): JSONObject {
        FullDate.matchEntire(value)?.let { match ->
            return JSONObject().put("@type", "PartialDate").also { result ->
                match.groupValues[1].takeIf { it.isNotEmpty() }?.let { result.put("year", it.toInt()) }
                result.put("month", match.groupValues[2].toInt()).put("day", match.groupValues[3].toInt())
            }
        }
        PartialYearless.matchEntire(value)?.let { match ->
            return JSONObject().put("@type", "PartialDate")
                .put("month", match.groupValues[1].toInt()).put("day", match.groupValues[2].toInt())
        }
        return JSONObject().put("@type", "Timestamp").put("utc", value)
    }

    private fun components(value: String, kinds: List<String>, type: String, fallback: String): org.json.JSONArray =
        jsonArray(
            vCardSplit(value, ';').flatMapIndexed { index, component ->
                vCardSplit(component, ',').filter { it.isNotEmpty() }.map { part ->
                    jsonObject("@type" to type, "kind" to (kinds.getOrNull(index) ?: fallback), "value" to vCardUnescape(part))
                }
            },
        )

    fun convert(vcard: String): JSONObject {
        val card = JSONObject().put("@type", "Card").put("version", "1.0")
        val counters = HashMap<String, Int>()
        fun add(field: String, value: JSONObject): String {
            val id = field + counters.merge(field, 1, Int::plus)
            val map = card.optJSONObject(field) ?: JSONObject().also { card.put(field, it) }
            map.put(id, value)
            return id
        }
        fun withMeta(base: JSONObject, meta: JSONObject): JSONObject = base.also { target ->
            meta.keyList().forEach { target.put(it, meta.opt(it)) }
        }

        val name = JSONObject().put("@type", "Name")
        val organizations = mutableListOf<String>()
        for (property in parseVCardProperties(vcard)) {
            val key = property.name
            val value = property.value
            val params = property.params
            val text = vCardUnescape(value)
            val meta = contactMetadata(property)
            when (key) {
                "UID" -> card.put("uid", text)
                "KIND" -> card.put("kind", text.lowercase())
                "FN" -> {
                    params["LANGUAGE"]?.firstOrNull()?.let { language ->
                        val localizations = card.optJSONObject("localizations") ?: JSONObject().also { card.put("localizations", it) }
                        val localized = localizations.optJSONObject(language) ?: JSONObject().also { localizations.put(language, it) }
                        localized.put("name/full", text)
                    }
                    if (!name.has("full") || params["LANGUAGE"] == null) name.put("full", text)
                }
                "N" -> {
                    name.put("components", components(value, NameKinds, "NameComponent", "surname"))
                    params["SORT-AS"]?.let { sort ->
                        val sortAs = JSONObject()
                        sort.forEachIndexed { index, part -> NameKinds.getOrNull(index)?.let { sortAs.put(it, part) } }
                        name.put("sortAs", sortAs)
                    }
                }
                "NICKNAME" -> vCardSplit(value, ',').forEach { part ->
                    add("nicknames", withMeta(jsonObject("@type" to "Nickname", "name" to vCardUnescape(part)), meta))
                }
                "ORG" -> {
                    val parts = vCardSplit(value, ';').map(::vCardUnescape)
                    val units = parts.drop(1).filter { it.isNotEmpty() }
                    val organization = jsonObject("@type" to "Organization", "name" to parts.first())
                    if (units.isNotEmpty()) {
                        organization.put("units", jsonArray(units.map { jsonObject("@type" to "OrgUnit", "name" to it) }))
                    }
                    organizations += add("organizations", withMeta(organization, meta))
                }
                "TITLE", "ROLE" ->
                    add("titles", withMeta(jsonObject("@type" to "Title", "kind" to key.lowercase(), "name" to text), meta))
                "EMAIL" -> add("emails", withMeta(jsonObject("@type" to "EmailAddress", "address" to text), meta))
                "TEL" -> {
                    val features = JSONObject()
                    params["TYPE"].orEmpty().map { it.lowercase() }.filter { it in PhoneFeatures }
                        .forEach { features.put(if (it == "cell") "mobile" else it, true) }
                    val phone = jsonObject("@type" to "Phone", "number" to text)
                    if (features.length() > 0) phone.put("features", features)
                    add("phones", withMeta(phone, meta))
                }
                "ADR" -> {
                    val address = jsonObject(
                        "@type" to "Address",
                        "components" to components(value, AddressKinds, "AddressComponent", "street"),
                    )
                    params["LABEL"]?.let { address.put("full", it.joinToString(",")) }
                    params["CC"]?.firstOrNull()?.let { address.put("countryCode", it) }
                    add("addresses", withMeta(address, meta))
                }
                "URL" -> add("links", withMeta(jsonObject("@type" to "Link", "uri" to text), meta))
                "IMPP" -> {
                    val service = jsonObject("@type" to "OnlineService", "uri" to text)
                    params["X-SERVICE-TYPE"]?.firstOrNull()?.let { service.put("service", it) }
                    add("onlineServices", withMeta(service, meta))
                }
                "NOTE" -> add("notes", jsonObject("@type" to "Note", "note" to text))
                "BDAY", "ANNIVERSARY" ->
                    add(
                        "anniversaries",
                        jsonObject("@type" to "Anniversary", "kind" to if (key == "BDAY") "birth" else "wedding", "date" to date(text)),
                    )
                "CATEGORIES" -> {
                    val keywords = JSONObject()
                    vCardSplit(value, ',').filter { it.isNotEmpty() }.forEach { keywords.put(vCardUnescape(it), true) }
                    card.put("keywords", keywords)
                }
                "MEMBER" -> {
                    val members = card.optJSONObject("members") ?: JSONObject().also { card.put("members", it) }
                    members.put(text, true)
                    card.put("kind", "group")
                }
                "RELATED" -> {
                    val related = card.optJSONObject("relatedTo") ?: JSONObject().also { card.put("relatedTo", it) }
                    val relation = related.optJSONObject(text)?.optJSONObject("relation")?.copy() ?: JSONObject()
                    params["TYPE"].orEmpty().filter { !it.equals("pref", ignoreCase = true) }
                        .forEach { relation.put(it.lowercase(), true) }
                    related.put(text, jsonObject("@type" to "Relation", "relation" to relation))
                }
                "LANG" -> {
                    val languages = card.optJSONObject("preferredLanguages") ?: JSONObject().also { card.put("preferredLanguages", it) }
                    val list = languages.optJSONArray(text) ?: org.json.JSONArray().also { languages.put(text, it) }
                    list.put(withMeta(jsonObject("@type" to "LanguagePref"), meta))
                }
                "PHOTO" -> {
                    val encoding = params["ENCODING"]?.firstOrNull()?.lowercase()
                    val rawType = params["MEDIATYPE"]?.firstOrNull()
                        ?: params["TYPE"]?.firstOrNull { it.lowercase() !in listOf("pref", "home", "work") }
                        ?: "jpeg"
                    val mediaType = if ('/' in rawType) rawType.lowercase() else "image/${rawType.lowercase()}"
                    val uri = if (encoding == "b" || encoding == "base64") "data:$mediaType;base64,$value" else text
                    val media = jsonObject("@type" to "Media", "kind" to "photo", "uri" to uri)
                    if (uri.startsWith("data:")) {
                        media.put("mediaType", uri.substring(5).split(';', ',').first())
                    } else if (params["MEDIATYPE"] != null) {
                        media.put("mediaType", mediaType)
                    }
                    add("media", withMeta(media, meta))
                }
            }
        }
        if (name.length() > 1) card.put("name", name)
        if (organizations.size == 1) {
            card.optJSONObject("titles")?.let { titles ->
                titles.keyList().forEach { titles.optJSONObject(it)?.put("organizationId", organizations[0]) }
            }
        }
        return card
    }
}
