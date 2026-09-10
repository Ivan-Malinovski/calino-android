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
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.property.Address
import ezvcard.property.DateOrTimeProperty
import ezvcard.property.Email
import ezvcard.property.Impp
import ezvcard.property.RawProperty
import ezvcard.property.Related
import ezvcard.property.Telephone
import ezvcard.property.Url
import java.time.LocalDate
import java.time.temporal.Temporal
import java.util.Base64 as JvmBase64
import java.util.Locale

/** Maps one raw CardDAV resource. The mapper intentionally owns no network state. */
class VCardMapper {

    fun map(
        vcf: String,
        addressBookId: String,
        accountId: String,
        href: String? = null,
        etag: String? = null,
    ): Contact? {
        val cleaned = vcf.removePrefix("\uFEFF").trim()
        val card = parseSingleVCard(cleaned) ?: return null
        return map(card, addressBookId, accountId, href, etag, cleaned)
    }

    fun map(
        card: VCard,
        addressBookId: String,
        accountId: String,
        href: String? = null,
        etag: String? = null,
        rawVCard: String? = null,
    ): Contact {
        val name = card.structuredName
        val given = name?.given.orEmpty()
        val family = name?.family.orEmpty()
        val additional = name?.additionalNames.orEmpty().joinToString(" ")
        val prefixes = name?.prefixes.orEmpty().joinToString(" ")
        val suffixes = name?.suffixes.orEmpty().joinToString(" ")
        val labels = card.extendedProperties
            .filter { it.propertyName.equals("X-ABLabel", ignoreCase = true) }
            .mapNotNull { property -> property.group?.let { it.lowercase(Locale.US) to property.value.orEmpty() } }
            .toMap()

        val emails = card.emails.map { property ->
            val label = labels[property.group?.lowercase(Locale.US)]
            ContactEmail(
                value = property.value.orEmpty().trim(),
                type = typeOf(property.types.map { it.value } + label),
                isPrimary = isPrimary(property.pref, property.types.map { it.value }),
            )
        }.filter { it.value.isNotEmpty() }.let(::markFirstEmailPrimary)

        val phones = card.telephoneNumbers.map { property ->
            val label = labels[property.group?.lowercase(Locale.US)]
            ContactPhone(
                value = property.text.orEmpty().trim(),
                type = phoneTypeOf(property.types.map { it.value } + label),
                isPrimary = isPrimary(property.pref, property.types.map { it.value }),
            )
        }.filter { it.value.isNotEmpty() }.let(::markFirstPhonePrimary)

        val urls = card.urls.map { property ->
            val label = labels[property.group?.lowercase(Locale.US)]
            ContactUrl(
                value = property.value.orEmpty().trim(),
                type = typeOf(listOfNotNull(property.type, label)),
                isPrimary = isPrimary(property.pref, property.parameters.getTypes()),
            )
        }.filter { it.value.isNotEmpty() }.let(::markFirstUrlPrimary)

        val ims = card.impps.map { property ->
            val label = labels[property.group?.lowercase(Locale.US)]
            ContactIm(
                value = property.uri?.toString()?.trim().orEmpty(),
                type = ContactImType.fromName(property.types.firstOrNull()?.value ?: label),
                protocol = property.protocol?.lowercase(Locale.US) ?: "other",
                isPrimary = isPrimary(property.pref, property.types.map { it.value }),
            )
        }.filter { it.value.isNotEmpty() }.let(::markFirstImPrimary)

        val addresses = card.addresses.map { property ->
            ContactAddress(
                type = typeOf(property.types.map { it.value }),
                isPrimary = isPrimary(property.pref, property.types.map { it.value }),
                poBox = property.poBox.orEmpty(),
                extended = property.extendedAddress.orEmpty(),
                street = property.streetAddress.orEmpty(),
                city = property.locality.orEmpty(),
                region = property.region.orEmpty(),
                postalCode = property.postalCode.orEmpty(),
                country = property.country.orEmpty(),
            )
        }.filter { it.toSearchText().isNotBlank() }.let(::markFirstAddressPrimary)

        val categories = card.categoriesList.flatMap { it.values }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val display = card.formattedName?.value.orEmpty()
        val organization = card.organization?.values?.firstOrNull().orEmpty()
        val uid = card.uid?.value?.trim()?.takeIf { it.isNotEmpty() }
        val memberUids = card.members.mapNotNull { it.uri?.trim()?.takeIf(String::isNotEmpty) }
        val opaque = card.extendedProperties.map(::opaqueLine)
        val resolvedName = deriveDisplayName(
            displayName = display,
            givenName = given,
            familyName = family,
            organization = organization,
            firstEmail = emails.firstOrNull()?.value,
        )

        return Contact(
            id = uid ?: href ?: "local-contact",
            addressBookId = addressBookId,
            accountId = accountId,
            familyName = family,
            givenName = given,
            additionalNames = additional,
            prefixes = prefixes,
            suffixes = suffixes,
            organization = organization,
            department = card.organization?.values?.getOrNull(1).orEmpty(),
            title = card.titles.firstOrNull()?.value.orEmpty(),
            role = card.roles.firstOrNull()?.value.orEmpty(),
            emails = emails,
            phones = phones,
            addresses = addresses,
            urls = urls,
            ims = ims,
            birthday = localDate(card.birthday),
            anniversary = localDate(card.anniversary),
            gender = card.gender?.gender ?: card.gender?.text.orEmpty(),
            nickname = card.nickname?.values?.joinToString(", ").orEmpty(),
            note = card.notes.joinToString("\n") { it.value.orEmpty() }.trim(),
            categories = categories,
            photo = photo(card),
            isGroup = card.kind?.isGroup == true || memberUids.isNotEmpty(),
            memberUids = memberUids,
            langs = card.languages.map { property ->
                ContactLang(property.value.orEmpty(), typeOf(listOfNotNull(property.type)), property.pref != null)
            },
            related = card.relations.map { property ->
                ContactRelated(
                    value = property.text ?: property.uri.orEmpty(),
                    type = ContactRelatedType.fromName(property.types.firstOrNull()?.value),
                    isPrimary = isPrimary(property.pref, property.types.map { it.value }),
                )
            },
            opaqueLines = opaque,
            rawVCard = rawVCard,
            uid = uid,
            href = href,
            etag = etag,
            displayName = resolvedName,
        )
    }

    private fun photo(card: VCard): String? = card.photos.firstOrNull()?.let { photo ->
        photo.url?.takeIf { it.isNotBlank() } ?: photo.data?.let { bytes ->
            val mime = photo.contentType?.mediaType ?: "image/jpeg"
            val encoded = JvmBase64.getEncoder().encodeToString(bytes)
            "data:$mime;base64,$encoded"
        }
    }

    private fun localDate(property: DateOrTimeProperty?): LocalDate? {
        property?.date?.let { date ->
            if (date is LocalDate) return date
            val partial = property.partialDate
            if (partial?.year != null && partial.month != null && partial.date != null) {
                return runCatching { LocalDate.of(partial.year, partial.month, partial.date) }.getOrNull()
            }
        }
        val raw = property?.text?.trim().orEmpty()
        val digits = raw.replace("-", "")
        return if (digits.matches(Regex("\\d{8}"))) runCatching {
            LocalDate.of(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt())
        }.getOrNull() else null
    }

    private fun typeOf(values: List<String?>): ContactType = values.asSequence()
        .filterNotNull()
        .flatMap { it.split(',', ';').asSequence() }
        .map { it.trim().lowercase(Locale.US) }
        .firstNotNullOfOrNull { token ->
            when (token) {
                "home", "personal" -> ContactType.Home
                "work", "business" -> ContactType.Work
                "pref", "preferred", "primary" -> ContactType.Pref
                // INTERNET is the vCard 3.0 transport marker, not a user
                // visible label. Do not let it win before HOME/WORK.
                "other" -> ContactType.Other
                else -> null
            }
        } ?: ContactType.Other

    private fun phoneTypeOf(values: List<String?>): ContactPhoneType = values.asSequence()
        .filterNotNull()
        .flatMap { it.split(',', ';').asSequence() }
        .map { it.trim().lowercase(Locale.US) }
        .firstNotNullOfOrNull { token ->
            when (token) {
                "home", "personal" -> ContactPhoneType.Home
                "work", "business" -> ContactPhoneType.Work
                "cell", "mobile", "car", "pcs", "text" -> ContactPhoneType.Cell
                "fax" -> ContactPhoneType.Fax
                "pref", "preferred", "primary" -> ContactPhoneType.Pref
                "other" -> ContactPhoneType.Other
                else -> null
            }
        } ?: ContactPhoneType.Other

    private fun isPrimary(pref: Int?, types: List<String?>): Boolean =
        pref != null || types.any { it?.equals("pref", ignoreCase = true) == true || it?.equals("primary", ignoreCase = true) == true }

    private fun markFirstEmailPrimary(values: List<ContactEmail>): List<ContactEmail> =
        if (values.any { it.isPrimary }) values else values.mapIndexed { index, value -> value.copy(isPrimary = index == 0) }

    private fun markFirstPhonePrimary(values: List<ContactPhone>): List<ContactPhone> =
        if (values.any { it.isPrimary }) values else values.mapIndexed { index, value -> value.copy(isPrimary = index == 0) }

    private fun markFirstUrlPrimary(values: List<ContactUrl>): List<ContactUrl> =
        if (values.any { it.isPrimary }) values else values.mapIndexed { index, value -> value.copy(isPrimary = index == 0) }

    private fun markFirstImPrimary(values: List<ContactIm>): List<ContactIm> =
        if (values.any { it.isPrimary }) values else values.mapIndexed { index, value -> value.copy(isPrimary = index == 0) }

    private fun markFirstAddressPrimary(values: List<ContactAddress>): List<ContactAddress> =
        if (values.any { it.isPrimary }) values else values.mapIndexed { index, value -> value.copy(isPrimary = index == 0) }

    private fun opaqueLine(property: RawProperty): String =
        "${property.propertyName}:${property.value.orEmpty()}"

    private fun ContactAddress.toSearchText() = listOf(poBox, extended, street, city, region, postalCode, country).joinToString(" ")

}

/** A CardDAV resource must contain exactly one vCard, never a concatenated feed. */
internal fun parseSingleVCard(raw: String): VCard? {
    val cleaned = raw.removePrefix("\uFEFF").trim()
    if (cleaned.isEmpty()) return null
    return runCatching {
        Ezvcard.parse(cleaned).caretDecoding(true).all().singleOrNull()
    }.getOrNull()
}

fun deriveDisplayName(
    displayName: String?,
    givenName: String?,
    familyName: String?,
    organization: String?,
    firstEmail: String?,
): String = displayName?.trim()?.takeIf(String::isNotEmpty)
    ?: listOfNotNull(givenName?.trim(), familyName?.trim()).joinToString(" ").trim().takeIf(String::isNotEmpty)
    ?: organization?.trim()?.takeIf(String::isNotEmpty)
    ?: firstEmail?.trim()?.takeIf(String::isNotEmpty)
    ?: "Unknown"
