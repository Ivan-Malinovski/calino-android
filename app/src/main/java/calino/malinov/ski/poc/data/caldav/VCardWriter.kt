package calino.malinov.ski.poc.data.caldav

import calino.malinov.ski.poc.data.model.Contact
import calino.malinov.ski.poc.data.model.ContactAddress
import calino.malinov.ski.poc.data.model.ContactEmail
import calino.malinov.ski.poc.data.model.ContactIm
import calino.malinov.ski.poc.data.model.ContactImType
import calino.malinov.ski.poc.data.model.ContactLang
import calino.malinov.ski.poc.data.model.ContactPhone
import calino.malinov.ski.poc.data.model.ContactPhoneType
import calino.malinov.ski.poc.data.model.ContactRelated
import calino.malinov.ski.poc.data.model.ContactRelatedType
import calino.malinov.ski.poc.data.model.ContactType
import calino.malinov.ski.poc.data.model.ContactUrl
import calino.malinov.ski.poc.data.model.NewContact
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.AddressType
import ezvcard.parameter.EmailType
import ezvcard.parameter.ImppType
import ezvcard.parameter.RelatedType
import ezvcard.parameter.TelephoneType
import ezvcard.property.Address
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.property.Categories
import ezvcard.property.Email
import ezvcard.property.FormattedName
import ezvcard.property.Gender
import ezvcard.property.Impp
import ezvcard.property.Kind
import ezvcard.property.Language
import ezvcard.property.Member
import ezvcard.property.Nickname
import ezvcard.property.Note
import ezvcard.property.Organization
import ezvcard.property.RawProperty
import ezvcard.property.Related
import ezvcard.property.Revision
import ezvcard.property.Role
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import ezvcard.property.Title
import ezvcard.property.Uid
import ezvcard.property.Url
import ezvcard.property.VCardProperty
import java.time.Instant

/**
 * Serializes the contact model to one vCard resource.
 *
 * A cached card is copied before the modeled properties are replaced. That is
 * intentional: properties Calino does not understand (and their parameters)
 * must survive a contact edit. A card built without an original has only the
 * fields represented by the model, plus any explicitly supplied opaque lines.
 */
class VCardWriter {

    fun writeContact(
        contact: Contact,
        uid: String = contact.uid ?: contact.id,
        original: VCard? = null,
        now: Instant = Instant.now(),
    ): String = write(contact, uid, original, now)

    fun writeContact(
        contact: NewContact,
        uid: String,
        original: VCard? = null,
        now: Instant = Instant.now(),
    ): String = write(contact, uid, original, now)

    fun write(
        contact: Contact,
        uid: String = contact.uid ?: contact.id,
        original: VCard? = null,
        now: Instant = Instant.now(),
    ): String = writeValues(contact.values(), uid, original, now)

    fun write(
        contact: NewContact,
        uid: String,
        original: VCard? = null,
        now: Instant = Instant.now(),
    ): String = writeValues(contact.values(), uid, original, now)

    /** Returns null when the cached payload is not exactly one readable card. */
    fun patch(
        vcf: String,
        contact: Contact,
        uid: String = contact.uid ?: contact.id,
        now: Instant = Instant.now(),
    ): String? = runCatching {
        val cards = Ezvcard.parse(vcf).caretDecoding(true).all()
        if (cards.size != 1) return@runCatching null
        write(contact, uid = uid, original = cards.single(), now = now)
    }.getOrNull()

    /** Returns null when the cached payload is not exactly one readable card. */
    fun patch(
        vcf: String,
        contact: NewContact,
        uid: String,
        now: Instant = Instant.now(),
    ): String? = runCatching {
        val cards = Ezvcard.parse(vcf).caretDecoding(true).all()
        if (cards.size != 1) return@runCatching null
        write(contact, uid = uid, original = cards.single(), now = now)
    }.getOrNull()

    fun patchContact(
        vcf: String,
        contact: Contact,
        uid: String = contact.uid ?: contact.id,
        now: Instant = Instant.now(),
    ): String? = patch(vcf, contact, uid, now)

    fun patchContact(
        vcf: String,
        contact: NewContact,
        uid: String,
        now: Instant = Instant.now(),
    ): String? = patch(vcf, contact, uid, now)

    private fun writeValues(
        values: ContactValues,
        uid: String,
        original: VCard?,
        now: Instant,
    ): String {
        require(uid.isNotBlank()) { "A vCard UID is required." }
        val card = original?.let(::VCard) ?: VCard()
        val version = original?.version ?: VCardVersion.V3_0
        card.version = version
        apply(card, values, uid, now, original != null)
        return Ezvcard.write(card)
            .version(version)
            .caretEncoding(true)
            .prodId(false)
            .go()
    }

    private fun apply(
        card: VCard,
        values: ContactValues,
        uid: String,
        now: Instant,
        hadOriginal: Boolean,
    ) {
        val oldName = card.structuredName
        val name = copyMetadata(oldName, StructuredName())
        name.family = values.familyName.nullIfBlank()
        name.given = values.givenName.nullIfBlank()
        name.additionalNames.clear()
        name.additionalNames += values.additionalNames
        name.prefixes.clear()
        name.prefixes += values.prefixes
        name.suffixes.clear()
        name.suffixes += values.suffixes
        card.setStructuredName(name)

        card.removeProperties(FormattedName::class.java)
        values.displayNameOrFallback().nullIfBlank()?.let { card.setFormattedName(FormattedName(it)) }

        card.removeProperties(Organization::class.java)
        if (values.organization.isNotBlank() || values.department.isNotBlank()) {
            val organization = Organization()
            organization.values += listOf(values.organization, values.department).filter(String::isNotBlank)
            card.setOrganization(organization)
        }

        replaceSimple(card, Title::class.java, values.title) { Title(it) }
        replaceSimple(card, Role::class.java, values.role) { Role(it) }

        val oldNicknames = card.nicknames.toList()
        card.removeProperties(Nickname::class.java)
        values.nickname.split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .takeIf { it.isNotEmpty() }
            ?.let { nicknameValues ->
                val nickname = copyMetadata(oldNicknames.firstOrNull(), Nickname())
                nickname.values += nicknameValues
                card.setNickname(nickname)
            }

        replaceEmails(card, values.emails)
        replacePhones(card, values.phones)
        replaceUrls(card, values.urls)
        replaceAddresses(card, values.addresses)
        replaceIms(card, values.ims)
        replaceDates(card, values.birthday, values.anniversary)
        replaceGender(card, values.gender)
        replaceNotes(card, values.note)
        replaceCategories(card, values.categories)
        replaceLanguages(card, values.langs)
        replaceRelations(card, values.related)

        card.removeProperties(Member::class.java)
        card.removeProperties(Kind::class.java)
        if (values.isGroup || values.memberUids.isNotEmpty()) {
            card.setKind(Kind.group())
            values.memberUids.filter(String::isNotBlank).forEach { card.addMember(Member(it)) }
        }

        // A mapped Contact with no photo was not editable in v1. Leave a
        // cached photo alone in that case; a newly built card has no photo.
        if (values.photo != null || !hadOriginal) replacePhoto(card, values.photo)

        card.setUid(Uid(uid))
        card.setRevision(Revision(now))

        if (!hadOriginal) {
            card.setProductId("-//Calino//Calino Android//EN")
            values.opaqueLines.mapNotNull(::rawProperty).forEach(card::addProperty)
        }
    }

    private fun replaceEmails(card: VCard, values: List<ContactEmail>) {
        val old = card.emails.toList()
        card.removeProperties(Email::class.java)
        values.filter { it.value.isNotBlank() }
            .forEachIndexed { index, item ->
                val value = item.value.trim()
                val source = old.getOrNull(index)
                val property = copyMetadata(source, Email(value))
                property.types.clear()
                when (item.type) {
                    ContactType.Home -> property.types += EmailType.HOME
                    ContactType.Work -> property.types += EmailType.WORK
                    ContactType.Pref -> property.types += EmailType.PREF
                    ContactType.Other -> Unit
                }
                property.pref = if (item.isPrimary) 1 else null
                card.addEmail(property)
            }
    }

    private fun replacePhones(card: VCard, values: List<ContactPhone>) {
        val old = card.telephoneNumbers.toList()
        card.removeProperties(Telephone::class.java)
        values.filter { it.value.isNotBlank() }
            .forEachIndexed { index, item ->
                val value = item.value.trim()
                val source = old.getOrNull(index)
                val property = copyMetadata(source, Telephone(value))
                property.types.clear()
                when (item.type) {
                    ContactPhoneType.Home -> property.types += TelephoneType.HOME
                    ContactPhoneType.Work -> property.types += TelephoneType.WORK
                    ContactPhoneType.Cell -> property.types += TelephoneType.CELL
                    ContactPhoneType.Fax -> property.types += TelephoneType.FAX
                    ContactPhoneType.Pref -> property.types += TelephoneType.PREF
                    ContactPhoneType.Other -> Unit
                }
                property.pref = if (item.isPrimary) 1 else null
                card.addTelephoneNumber(property)
            }
    }

    private fun replaceUrls(card: VCard, values: List<ContactUrl>) {
        val old = card.urls.toList()
        card.removeProperties(Url::class.java)
        values.filter { it.value.isNotBlank() }
            .forEachIndexed { index, item ->
                val value = item.value.trim()
                val source = old.getOrNull(index)
                val property = copyMetadata(source, Url(value))
                property.type = item.type.vCardName()
                    .takeIf { item.type != ContactType.Other }
                property.pref = if (item.isPrimary) 1 else null
                card.addUrl(property)
            }
    }

    private fun replaceAddresses(card: VCard, values: List<ContactAddress>) {
        val old = card.addresses.toList()
        card.removeProperties(Address::class.java)
        values.filter { it.hasContent() }.forEachIndexed { index, value ->
            val property = copyMetadata(old.getOrNull(index), Address())
            property.poBox = value.poBox.nullIfBlank()
            property.extendedAddress = value.extended.nullIfBlank()
            property.streetAddress = value.street.nullIfBlank()
            property.locality = value.city.nullIfBlank()
            property.region = value.region.nullIfBlank()
            property.postalCode = value.postalCode.nullIfBlank()
            property.country = value.country.nullIfBlank()
            property.types.clear()
            when (value.type) {
                ContactType.Home -> property.types += AddressType.HOME
                ContactType.Work -> property.types += AddressType.WORK
                ContactType.Pref -> property.types += AddressType.PREF
                ContactType.Other -> Unit
            }
            property.pref = if (value.isPrimary) 1 else null
            card.addAddress(property)
        }
    }

    private fun replaceIms(card: VCard, values: List<ContactIm>) {
        val old = card.impps.toList()
        card.removeProperties(Impp::class.java)
        values.filter { it.value.isNotBlank() }
            .forEachIndexed { index, item ->
                val property = copyMetadata(old.getOrNull(index), Impp(item.value.trim()))
                property.types.clear()
                when (item.type) {
                    ContactImType.Home -> property.types += ImppType.HOME
                    ContactImType.Work -> property.types += ImppType.WORK
                    ContactImType.Pref -> property.types += ImppType.PREF
                    ContactImType.Other -> Unit
                }
                property.pref = if (item.isPrimary) 1 else null
                card.addImpp(property)
            }
    }

    private fun replaceDates(card: VCard, birthday: java.time.LocalDate?, anniversary: java.time.LocalDate?) {
        card.removeProperties(Birthday::class.java)
        birthday?.let { card.setBirthday(it) }
        card.removeProperties(Anniversary::class.java)
        anniversary?.let { card.setAnniversary(it) }
    }

    private fun replaceGender(card: VCard, gender: String) {
        card.removeProperties(Gender::class.java)
        gender.nullIfBlank()?.let { card.setGender(Gender(it)) }
    }

    private fun replaceNotes(card: VCard, note: String) {
        card.removeProperties(Note::class.java)
        note.nullIfBlank()?.let { card.addNote(Note(it)) }
    }

    private fun replaceCategories(card: VCard, categories: List<String>) {
        card.removeProperties(Categories::class.java)
        val values = categories.map(String::trim).filter(String::isNotBlank).distinct()
        if (values.isNotEmpty()) {
            val property = Categories()
            property.values += values
            card.addCategories(property)
        }
    }

    private fun replaceLanguages(card: VCard, values: List<ContactLang>) {
        val old = card.languages.toList()
        card.removeProperties(Language::class.java)
        values.filter { it.value.isNotBlank() }
            .forEachIndexed { index, item ->
                val property = copyMetadata(old.getOrNull(index), Language(item.value.trim()))
                property.type = item.type.vCardName()
                    .takeIf { item.type != ContactType.Other }
                property.pref = if (item.isPrimary) 1 else null
                card.addLanguage(property)
            }
    }

    private fun replaceRelations(card: VCard, values: List<ContactRelated>) {
        val old = card.relations.toList()
        card.removeProperties(Related::class.java)
        values.filter { it.value.isNotBlank() }
            .forEachIndexed { index, item ->
                val property = copyMetadata(old.getOrNull(index), Related(item.value.trim()))
                property.types.clear()
                relatedType(item.type)?.let(property.types::add)
                property.pref = if (item.isPrimary) 1 else null
                card.addRelated(property)
            }
    }

    private fun replacePhoto(card: VCard, photo: String?) {
        // Photos are intentionally preserved during a patch unless the model
        // carries one. NewContact has no photo editor, so URL/data URI support
        // here is limited to the full Contact write path.
        if (photo == null) {
            card.removeProperties(ezvcard.property.Photo::class.java)
            return
        }
        if (photo.startsWith("data:", ignoreCase = true)) return
        val old = card.photos.firstOrNull()
        if (old != null) {
            old.setUrl(photo, old.contentType)
        } else {
            // Keep this conservative: a URL photo can be represented without
            // downloading bytes, while a data URI is left untouched above.
            card.addPhoto(ezvcard.property.Photo(photo, ezvcard.parameter.ImageType.JPEG))
        }
    }

    private fun <T : VCardProperty> replaceSimple(
        card: VCard,
        type: Class<T>,
        value: String,
        create: (String) -> T,
    ) {
        val old = card.getProperty(type)
        card.removeProperties(type)
        value.nullIfBlank()?.let { card.addProperty(copyMetadata(old, create(it))) }
    }

    private fun <T : VCardProperty> copyMetadata(source: T?, target: T): T {
        source?.let {
            target.group = it.group
            target.parameters = ezvcard.parameter.VCardParameters(it.parameters)
        }
        return target
    }

    private fun rawProperty(line: String): RawProperty? {
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        val name = line.substring(0, separator).substringBefore(';').trim()
        val value = line.substring(separator + 1)
        return name.takeIf(String::isNotBlank)?.let { RawProperty(it, value) }
    }

    private fun relatedType(type: ContactRelatedType): RelatedType? = when (type) {
        ContactRelatedType.Friend -> RelatedType.FRIEND
        ContactRelatedType.Coworker -> RelatedType.CO_WORKER
        ContactRelatedType.Family -> RelatedType.KIN
        ContactRelatedType.Child -> RelatedType.CHILD
        ContactRelatedType.Spouse -> RelatedType.SPOUSE
        ContactRelatedType.Agent -> RelatedType.AGENT
        ContactRelatedType.Emergency -> RelatedType.EMERGENCY
        ContactRelatedType.Other -> null
    }

    private fun ContactType.vCardName(): String = when (this) {
        ContactType.Home -> "home"
        ContactType.Work -> "work"
        ContactType.Pref -> "pref"
        ContactType.Other -> "other"
    }

    private fun ContactAddress.hasContent(): Boolean = listOf(
        poBox, extended, street, city, region, postalCode, country,
    ).any(String::isNotBlank)

    private fun String.nullIfBlank(): String? = trim().takeIf(String::isNotBlank)

    private fun ContactValues.displayNameOrFallback(): String = displayName.nullIfBlank()
        ?: listOf(givenName, familyName).filter(String::isNotBlank).joinToString(" ").nullIfBlank()
        ?: organization.nullIfBlank()
        ?: emails.firstOrNull()?.value?.nullIfBlank()
        ?: "Unknown"

    private data class ContactValues(
        val displayName: String,
        val givenName: String,
        val familyName: String,
        val additionalNames: List<String>,
        val prefixes: List<String>,
        val suffixes: List<String>,
        val organization: String,
        val department: String,
        val title: String,
        val role: String,
        val emails: List<ContactEmail>,
        val phones: List<ContactPhone>,
        val addresses: List<ContactAddress>,
        val urls: List<ContactUrl>,
        val ims: List<ContactIm>,
        val birthday: java.time.LocalDate?,
        val anniversary: java.time.LocalDate?,
        val gender: String,
        val nickname: String,
        val note: String,
        val categories: List<String>,
        val photo: String?,
        val isGroup: Boolean,
        val memberUids: List<String>,
        val langs: List<ContactLang>,
        val related: List<ContactRelated>,
        val opaqueLines: List<String>,
    )

    private fun Contact.values() = ContactValues(
        displayName = displayName,
        givenName = givenName,
        familyName = familyName,
        additionalNames = additionalNames.split(' ').filter(String::isNotBlank),
        prefixes = prefixes.split(' ').filter(String::isNotBlank),
        suffixes = suffixes.split(' ').filter(String::isNotBlank),
        organization = organization,
        department = department,
        title = title,
        role = role,
        emails = emails,
        phones = phones,
        addresses = addresses,
        urls = urls,
        ims = ims,
        birthday = birthday,
        anniversary = anniversary,
        gender = gender,
        nickname = nickname,
        note = note,
        categories = categories,
        photo = photo,
        isGroup = isGroup,
        memberUids = memberUids,
        langs = langs,
        related = related,
        opaqueLines = opaqueLines,
    )

    private fun NewContact.values() = ContactValues(
        displayName = displayName,
        givenName = givenName,
        familyName = familyName,
        additionalNames = emptyList(),
        prefixes = emptyList(),
        suffixes = emptyList(),
        organization = organization,
        department = department,
        title = title,
        role = "",
        emails = emails,
        phones = phones,
        addresses = emptyList(),
        urls = urls,
        ims = emptyList(),
        birthday = birthday,
        anniversary = anniversary,
        gender = "",
        nickname = nickname,
        note = note,
        categories = categories,
        photo = null,
        isGroup = false,
        memberUids = emptyList(),
        langs = emptyList(),
        related = emptyList(),
        opaqueLines = emptyList(),
    )
}
