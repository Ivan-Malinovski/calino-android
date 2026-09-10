package calino.malinov.ski.poc.state

import calino.malinov.ski.poc.data.model.Contact
import calino.malinov.ski.poc.data.model.derivedDisplayName
import java.util.Locale

data class ContactSearchOptions(
    val query: String = "",
    val addressBookId: String? = null,
    val tag: String? = null,
)

/** Deterministic, weighted local contact search; no fuzzy-search dependency. */
fun searchContacts(
    contacts: List<Contact>,
    query: String = "",
    addressBookId: String? = null,
    tag: String? = null,
): List<Contact> {
    val needle = query.trim().lowercase(Locale.US)
    val digits = needle.filter(Char::isDigit)
    return contacts.asSequence()
        .filter { addressBookId == null || it.addressBookId == addressBookId }
        .filter { tag == null || it.categories.any { category -> category.equals(tag, ignoreCase = true) } }
        .mapNotNull { contact ->
            val fields = listOf(
                3.0 to contact.derivedDisplayName(),
                2.0 to contact.emails.joinToString(" ") { it.value },
                2.0 to contact.phones.joinToString(" ") { it.value },
                2.0 to contact.nickname,
                1.5 to contact.organization,
                1.0 to listOf(contact.department, contact.title).joinToString(" "),
                1.0 to contact.addresses.joinToString(" ") { listOf(it.city, it.street, it.region, it.postalCode).joinToString(" ") },
                0.8 to contact.note,
                0.8 to contact.addresses.joinToString(" ") { it.country },
                0.5 to contact.categories.joinToString(" "),
                0.5 to contact.urls.joinToString(" ") { it.value },
            )
            val phoneMatch = digits.length >= 3 && contact.phones.any { phone ->
                phone.value.filter(Char::isDigit).contains(digits)
            }
            if (needle.isEmpty()) {
                contact to 0.0
            } else {
                val score = fields.mapNotNull { (weight, value) ->
                    val normalized = value.lowercase(Locale.US)
                    if (!normalized.contains(needle)) null
                    else weight * if (normalized == needle) 1.5 else if (normalized.startsWith(needle)) 1.2 else 1.0
                }.maxOrNull() ?: if (phoneMatch) 2.0 else null
                score?.let { contact to it }
            }
        }
        .sortedWith(compareByDescending<Pair<Contact, Double>> { it.second }
            .thenBy { it.first.derivedDisplayName().lowercase(Locale.US) }
            .thenBy { it.first.id })
        .map { it.first }
        .toList()
}
