package calino.malinov.ski.poc.data.caldav

import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.property.VCardProperty

/**
 * Three-way merge for a queued CardDAV update.
 *
 * The queued body is a complete vCard, but it was produced from an older
 * server snapshot. Replacing a newer server card with that body can erase a
 * field another client added in the meantime. Comparing the original and
 * local cards by property class lets locally changed properties win while
 * retaining every property that was unchanged by the local edit from the
 * current server card.
 */
class VCardPatcher {

    /** Returns a rebased card, or null when any of the three cards is unsafe. */
    fun rebaseResource(
        currentVcf: String,
        localVcf: String,
        baseVcf: String,
    ): String? = runCatching {
        val current = parseSingle(currentVcf) ?: return@runCatching null
        val local = parseSingle(localVcf) ?: return@runCatching null
        val base = parseSingle(baseVcf) ?: return@runCatching null
        val baseUid = base.uid?.value?.trim().orEmpty()
        val localUid = local.uid?.value?.trim().orEmpty()
        val currentUid = current.uid?.value?.trim().orEmpty()
        if (baseUid.isEmpty() || localUid.isEmpty() || currentUid.isEmpty() ||
            baseUid != localUid || baseUid != currentUid
        ) {
            return@runCatching null
        }

        val merged = VCard(current)
        val baseProperties = propertiesByClass(base)
        val localProperties = propertiesByClass(local)
        val propertyClasses = (baseProperties.keys + localProperties.keys).toSet()
        propertyClasses.forEach { propertyClass ->
            val baseValues = baseProperties[propertyClass].orEmpty()
            val localValues = localProperties[propertyClass].orEmpty()
            if (baseValues != localValues) {
                @Suppress("UNCHECKED_CAST")
                val typedClass = propertyClass as Class<out VCardProperty>
                merged.removeProperties(typedClass)
                localValues.forEach { merged.addProperty(it.copy()) }
            }
        }

        val version = merged.version
        Ezvcard.write(merged)
            .version(version)
            .caretEncoding(true)
            .prodId(false)
            .go()
    }.getOrNull()

    private fun parseSingle(vcf: String): VCard? {
        val cleaned = vcf.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return null
        val cards = runCatching { Ezvcard.parse(cleaned).caretDecoding(true).all() }.getOrNull()
            ?: return null
        return cards.singleOrNull()
    }

    private fun propertiesByClass(card: VCard): Map<Class<out VCardProperty>, List<VCardProperty>> =
        card.properties.groupBy { it.javaClass }
}
