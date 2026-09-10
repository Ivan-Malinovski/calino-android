package calino.malinov.ski.poc.data.model

import java.time.LocalDate
import java.util.Locale

/** The label vocabulary shared by email, URL, address, and language fields. */
enum class ContactType {
    Home, Work, Other, Pref;

    companion object {
        fun fromName(value: String?): ContactType = when (value?.trim()?.lowercase(Locale.US)) {
            "home", "personal" -> Home
            "work", "business" -> Work
            "pref", "preferred", "primary" -> Pref
            else -> Other
        }
    }
}

enum class ContactPhoneType {
    Home, Work, Cell, Fax, Other, Pref;

    companion object {
        fun fromName(value: String?): ContactPhoneType = when (value?.trim()?.lowercase(Locale.US)) {
            "home", "personal" -> Home
            "work", "business" -> Work
            "cell", "mobile", "car", "pcs", "text" -> Cell
            "fax" -> Fax
            "pref", "preferred", "primary" -> Pref
            else -> Other
        }
    }
}

enum class ContactImType {
    Home, Work, Other, Pref;

    companion object {
        fun fromName(value: String?): ContactImType = ContactType.fromName(value).let {
            when (it) {
                ContactType.Home -> Home
                ContactType.Work -> Work
                ContactType.Pref -> Pref
                ContactType.Other -> Other
            }
        }
    }
}

enum class ContactRelatedType {
    Friend, Coworker, Family, Child, Spouse, Agent, Emergency, Other;

    companion object {
        fun fromName(value: String?): ContactRelatedType = when (value?.trim()?.lowercase(Locale.US)) {
            "friend" -> Friend
            "co-worker", "coworker", "colleague" -> Coworker
            "family", "kin", "parent", "sibling" -> Family
            "child" -> Child
            "spouse", "partner" -> Spouse
            "agent" -> Agent
            "emergency" -> Emergency
            else -> Other
        }
    }
}

data class ContactEmail(val value: String, val type: ContactType = ContactType.Other, val isPrimary: Boolean = false)
data class ContactPhone(val value: String, val type: ContactPhoneType = ContactPhoneType.Other, val isPrimary: Boolean = false)
data class ContactUrl(val value: String, val type: ContactType = ContactType.Other, val isPrimary: Boolean = false)
data class ContactIm(
    val value: String,
    val type: ContactImType = ContactImType.Other,
    val protocol: String = "other",
    val isPrimary: Boolean = false,
)

data class ContactAddress(
    val type: ContactType = ContactType.Other,
    val isPrimary: Boolean = false,
    val poBox: String = "",
    val extended: String = "",
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
)

data class ContactLang(val value: String, val type: ContactType = ContactType.Other, val isPrimary: Boolean = false)
data class ContactRelated(val value: String, val type: ContactRelatedType = ContactRelatedType.Other, val isPrimary: Boolean = false)

/** An address book discovered independently of the calendar home. */
data class ContactAddressBook(
    val id: String,
    val accountId: String = "",
    val url: String = id,
    val name: String = id.substringAfterLast('/').ifBlank { "Contacts" },
    val description: String? = null,
    val ctag: String? = null,
    val syncToken: String? = null,
    val enabled: Boolean = true,
    val readOnly: Boolean = false,
)

/** The mapped vCard contract used by both fixture and CardDAV repositories. */
data class Contact(
    val id: String,
    val addressBookId: String,
    val accountId: String = "",
    val familyName: String = "",
    val givenName: String = "",
    val additionalNames: String = "",
    val prefixes: String = "",
    val suffixes: String = "",
    val displayName: String = "",
    val organization: String = "",
    val department: String = "",
    val title: String = "",
    val role: String = "",
    val emails: List<ContactEmail> = emptyList(),
    val phones: List<ContactPhone> = emptyList(),
    val addresses: List<ContactAddress> = emptyList(),
    val urls: List<ContactUrl> = emptyList(),
    val ims: List<ContactIm> = emptyList(),
    val birthday: LocalDate? = null,
    val anniversary: LocalDate? = null,
    val gender: String = "",
    val nickname: String = "",
    val note: String = "",
    val categories: List<String> = emptyList(),
    /** A data URI, remote URL, or null. The directory never requires a photo. */
    val photo: String? = null,
    val isGroup: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val langs: List<ContactLang> = emptyList(),
    val related: List<ContactRelated> = emptyList(),
    val opaqueLines: List<String> = emptyList(),
    val rawVCard: String? = null,
    /** Null identity fields mean this record was created locally. */
    val uid: String? = null,
    val href: String? = null,
    val etag: String? = null,
)

/** Fields the editor is allowed to create or change in v1. */
data class NewContact(
    val displayName: String = "",
    val givenName: String = "",
    val familyName: String = "",
    val organization: String = "",
    val department: String = "",
    val title: String = "",
    val nickname: String = "",
    val emails: List<ContactEmail> = emptyList(),
    val phones: List<ContactPhone> = emptyList(),
    val urls: List<ContactUrl> = emptyList(),
    val birthday: LocalDate? = null,
    val anniversary: LocalDate? = null,
    val note: String = "",
    val categories: List<String> = emptyList(),
    val addressBookId: String = "local",
)

fun Contact.toNewContact(): NewContact = NewContact(
    displayName = displayName,
    givenName = givenName,
    familyName = familyName,
    organization = organization,
    department = department,
    title = title,
    nickname = nickname,
    emails = emails,
    phones = phones,
    urls = urls,
    birthday = birthday,
    anniversary = anniversary,
    note = note,
    categories = categories,
    addressBookId = addressBookId,
)

/** The web directory's deterministic alphabetic bucketing rule. */
fun Contact.alphaKey(): String {
    val first = displayName.trim().firstOrNull()?.uppercaseChar() ?: '#'
    return if (first in 'A'..'Z') first.toString() else "#"
}

/** Stable directory ordering: alphabetic sections first, then the # bucket. */
fun groupContactsByAlpha(contacts: List<Contact>): List<Pair<String, List<Contact>>> =
    contacts.groupBy { it.alphaKey() }
        .toSortedMap(compareBy<String> { if (it == "#") 1 else 0 }.thenBy { it })
        .map { (key, values) ->
            key to values.sortedWith(compareBy<Contact> { it.derivedDisplayName().lowercase(Locale.US) }.thenBy { it.id })
        }

fun Contact.derivedDisplayName(): String = deriveDisplayName(
    displayName = displayName,
    givenName = givenName,
    familyName = familyName,
    organization = organization,
    firstEmail = emails.firstOrNull()?.value,
)

fun deriveDisplayName(
    displayName: String?,
    givenName: String? = null,
    familyName: String? = null,
    organization: String? = null,
    firstEmail: String? = null,
): String = displayName?.trim()?.takeIf { it.isNotEmpty() }
    ?: listOfNotNull(givenName?.trim(), familyName?.trim()).joinToString(" ").trim().takeIf { it.isNotEmpty() }
    ?: organization?.trim()?.takeIf { it.isNotEmpty() }
    ?: firstEmail?.trim()?.takeIf { it.isNotEmpty() }
    ?: "Unknown"

fun deriveDisplayName(contact: Contact): String = deriveDisplayName(
    contact.displayName,
    contact.givenName,
    contact.familyName,
    contact.organization,
    contact.emails.firstOrNull()?.value,
)
