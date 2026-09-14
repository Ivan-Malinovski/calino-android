package calino.malinov.ski.data.model

/**
 * Editor-only contact draft. A blank draft has no repository representation;
 * keeping the identity here lets an edit retain the contact id while the
 * editor is rotated or reopened.
 */
data class ContactDraft(
    val id: String,
    val addressBookId: String = "local",
) {
    fun commit(
        displayName: String = "",
        givenName: String = "",
        familyName: String = "",
        organization: String = "",
        email: String = "",
        phone: String = "",
        note: String = "",
    ): NewContact? {
        val normalized = NewContact(
            displayName = displayName.trim(),
            givenName = givenName.trim(),
            familyName = familyName.trim(),
            organization = organization.trim(),
            emails = email.trim().takeIf { it.isNotEmpty() }
                ?.let { listOf(ContactEmail(it, ContactType.Other, isPrimary = true)) }
                ?: emptyList(),
            phones = phone.trim().takeIf { it.isNotEmpty() }
                ?.let { listOf(ContactPhone(it, ContactPhoneType.Other, isPrimary = true)) }
                ?: emptyList(),
            note = note.trim(),
            addressBookId = addressBookId,
        )
        return normalized.takeIf {
            listOf(it.displayName, it.givenName, it.familyName, it.organization, it.note)
                .any(String::isNotBlank) || it.emails.isNotEmpty() || it.phones.isNotEmpty()
        }
    }
}

fun List<ContactEmail>.primaryContactEmail(): ContactEmail? = firstOrNull { it.isPrimary } ?: firstOrNull()
fun List<ContactPhone>.primaryContactPhone(): ContactPhone? = firstOrNull { it.isPrimary } ?: firstOrNull()
fun List<ContactUrl>.primaryContactUrl(): ContactUrl? = firstOrNull { it.isPrimary } ?: firstOrNull()
